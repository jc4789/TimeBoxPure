"""Offline PMD corpus inventory and lossless semantic trace verifier.

This tool reads user-supplied Touhou archives through THTK, scans PMD M86/M26
part streams, reports conservative opcode/subcommand capabilities, and
optionally verifies the production Bad Apple MML lanes. It is never imported by
runtime code and never writes extracted music into the repository.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import struct
import subprocess
import tempfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from pmd_corpus_format import (
    CAPABILITY_EXACT,
    COMMAND_CAPABILITIES,
    COMMAND_NAMES,
    FEATURE_OPCODE_GROUPS,
    FIXED_PARAMETER_COUNTS,
    capability_for_command,
)


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MML_BANK = (
    ROOT
    / "shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/mml/MmlSongBank.kt"
)
PMD_PART_NAMES = ("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")
PMD_POINTER_COUNT = 13
PMD_PART_POINTER_COUNT = len(PMD_PART_NAMES)
BAD_APPLE_SHA256 = "60e0e4e9742db3d97bd02238f2602ad7f671c71077479d71593e69710be8f130"
BAD_APPLE_WINDOW_START = 288
BAD_APPLE_WINDOW_END = 5280
PMD_TO_MML_TICKS = 20
# Small non-expressive ST02 aggregate fixed by repair_plan.md and confirmed
# against the independently hashed M86 payload in both supplied archives.
BAD_APPLE_EXPECTED_SEMANTIC_COUNTS = {
    "pitched_volume_transition_count": 28,
    "gate_change_count": 29,
    "detune_change_count": 17,
    "target_portamento_count": 4,
    "tie_count": 4,
    "tie_without_retrigger_count": 4,
    "envelope_definition_count": 8,
    "lfo_clock_declaration_count": 20,
    "active_software_lfo_count": 0,
}
BAD_APPLE_EXPECTED_SEMANTIC_IDENTITY_SHA256 = (
    "a9585db265e8e3b1ceb1ab4c4b1a1dc0728391d371b2385a10f1b862a4d942e1"
)
MAX_TRACE_STEPS = 100_000
REPORT_SCHEMA_VERSION = 2
SEMANTIC_TRACE_SCHEMA_VERSION = 3
STRUCTURAL_INVENTORY_EVIDENCE = "STRUCTURAL_ONLY_NOT_SEMANTIC_PARITY_EVIDENCE"
REQUIRED_INDEPENDENT_CHECKPOINT_TRACES = 4
INDEPENDENT_CHECKPOINT_DERIVATION_KIND = "INDEPENDENT_REGISTER_OR_STATE_TRACE"
SHA256_PATTERN = re.compile(r"^[0-9a-fA-F]{64}$")

# R0 does not yet have the required independent state/register checkpoint
# fixtures. Keep this explicit and empty instead of relabeling semantic traces.
INDEPENDENT_CHECKPOINT_TRACES: tuple[dict, ...] = ()


@dataclass(frozen=True)
class ListedEntry:
    name: str
    size: int
    stored_size: int


class PmdScanError(ValueError):
    pass


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while True:
            block = stream.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def decode_process_output(value: bytes) -> str:
    for encoding in ("cp932", "utf-8"):
        try:
            return value.decode(encoding)
        except UnicodeDecodeError:
            pass
    return value.decode("latin-1", errors="replace")


def run_thdat(thdat: Path, arguments: list[str], cwd: Path | None = None) -> str:
    completed = subprocess.run(
        [str(thdat), *arguments],
        cwd=str(cwd) if cwd is not None else None,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    output = decode_process_output(completed.stdout)
    if completed.returncode != 0:
        raise PmdScanError(f"THTK failed ({completed.returncode}): {' '.join(arguments)}\n{output}")
    return output


def parse_thdat_listing(output: str) -> list[ListedEntry]:
    entries: list[ListedEntry] = []
    for line in output.splitlines():
        match = re.match(r"^\s*(\S+)\s+(\d+)\s+(\d+)\s*$", line)
        if match is None:
            continue
        name = match.group(1)
        if not name.upper().endswith((".M86", ".M26")):
            continue
        if Path(name).name != name or "/" in name or "\\" in name:
            raise PmdScanError(f"Unsafe archive entry name: {name!r}")
        entries.append(ListedEntry(name, int(match.group(2)), int(match.group(3))))
    return entries


def list_archive(thdat: Path, archive: Path) -> list[ListedEntry]:
    return parse_thdat_listing(run_thdat(thdat, ["-l4", str(archive)]))


def extract_entries(thdat: Path, archive: Path, entries: list[ListedEntry]) -> dict[str, bytes]:
    if not entries:
        return {}
    seen_names: set[str] = set()
    for entry in entries:
        normalized = entry.name.casefold()
        if normalized in seen_names:
            raise PmdScanError(
                f"Archive contains a duplicate music entry name: {entry.name!r}"
            )
        seen_names.add(normalized)
    with tempfile.TemporaryDirectory(prefix="timebox-pmd-corpus-") as directory:
        root = Path(directory).resolve()
        run_thdat(thdat, ["-x4", str(archive), *(entry.name for entry in entries)], cwd=root)
        payloads: dict[str, bytes] = {}
        for entry in entries:
            extracted = (root / entry.name).resolve()
            if extracted.parent != root:
                raise PmdScanError(f"THTK extraction escaped temporary directory: {entry.name}")
            if not extracted.is_file():
                raise PmdScanError(f"THTK did not extract {entry.name}")
            payload = extracted.read_bytes()
            if len(payload) != entry.size:
                raise PmdScanError(
                    f"Extracted size mismatch for {entry.name}: expected {entry.size}, got {len(payload)}"
                )
            payloads[entry.name] = payload
        return payloads


def command_parameter_count(data: bytes | bytearray, pointer: int, opcode: int) -> int:
    if opcode == 0xFC:
        if pointer >= len(data):
            raise PmdScanError("Truncated tempo command")
        return 2 if data[pointer] >= 0xFB else 1
    if opcode == 0xC0:
        if pointer >= len(data):
            raise PmdScanError("Truncated special-control command")
        return 2 if data[pointer] >= 0xF5 else 1
    count = FIXED_PARAMETER_COUNTS.get(opcode)
    if count is None:
        raise PmdScanError(f"Unknown compiled PMD opcode 0x{opcode:02X}")
    return count


def scan_part(data: bytes, start: int, part: str) -> dict:
    pointer = start
    commands: list[dict] = []
    notes = 0
    rests = 0
    rhythm_pattern_selects = 0
    error: str | None = None
    while pointer < len(data):
        command_offset = pointer
        opcode = data[pointer]
        pointer += 1
        if opcode < 0x80:
            if part == "K":
                rhythm_pattern_selects += 1
                continue
            if pointer >= len(data):
                error = f"truncated note at 0x{command_offset:04X}"
                break
            duration = data[pointer]
            pointer += 1
            if opcode & 0x0F == 0x0F:
                rests += 1
            else:
                notes += 1
            continue
        if opcode == 0x80:
            return {
                "part": part,
                "start_offset": start,
                "end_offset": pointer - 1,
                "notes": notes,
                "rests": rests,
                "rhythm_pattern_selects": rhythm_pattern_selects,
                "commands": commands,
                "error": error,
            }
        try:
            parameter_count = command_parameter_count(data, pointer, opcode)
        except PmdScanError as exc:
            error = f"{exc} at 0x{command_offset:04X}"
            break
        if pointer + parameter_count > len(data):
            error = f"truncated {COMMAND_NAMES[opcode]} at 0x{command_offset:04X}"
            break
        parameters = list(data[pointer:pointer + parameter_count])
        commands.append(
            {
                "offset": command_offset,
                "opcode": opcode,
                "name": COMMAND_NAMES[opcode],
                "parameters": parameters,
            }
        )
        pointer += parameter_count
    if error is None:
        error = f"part {part} has no end marker"
    return {
        "part": part,
        "start_offset": start,
        "end_offset": pointer,
        "notes": notes,
        "rests": rests,
        "rhythm_pattern_selects": rhythm_pattern_selects,
        "commands": commands,
        "error": error,
    }


def scan_rhythm_table(
    data: bytes,
    table_offset: int,
    voice_offset: int,
    selected_pattern_count: int,
) -> dict:
    result = {"offset": table_offset, "pattern_count": 0, "patterns": [], "error": None}
    if table_offset == voice_offset:
        return result
    if not (0 <= table_offset < voice_offset <= len(data)):
        result["error"] = "invalid rhythm/voice offsets"
        return result
    if table_offset + 2 > voice_offset:
        result["error"] = "truncated rhythm pointer table"
        return result
    first_pattern = struct.unpack_from("<H", data, table_offset)[0]
    table_bytes = first_pattern - table_offset
    if table_bytes < 0 or table_bytes % 2 != 0:
        if selected_pattern_count == 0:
            result["absent"] = True
            return result
        result["error"] = "invalid rhythm pointer-table size"
        return result
    pattern_count = table_bytes // 2
    if pattern_count == 0:
        return result
    if table_offset + table_bytes > voice_offset:
        result["error"] = "rhythm pointer table overlaps voice data"
        return result
    pointers = [struct.unpack_from("<H", data, table_offset + index * 2)[0] for index in range(pattern_count)]
    if pointers != sorted(pointers) or pointers[0] != first_pattern:
        result["error"] = "rhythm pattern pointers are not monotonic"
        return result
    for index, start in enumerate(pointers):
        end = pointers[index + 1] if index + 1 < len(pointers) else voice_offset
        if not (first_pattern <= start <= end <= voice_offset):
            result["error"] = f"invalid rhythm pattern {index} bounds"
            return result
        payload = data[start:end]
        terminator = payload.find(b"\xFF")
        if terminator < 0:
            result["error"] = f"rhythm pattern {index} has no terminator"
            return result
        authored = payload[:terminator + 1]
        result["patterns"].append(
            {"index": index, "offset": start, "size": len(authored), "sha256": sha256_bytes(authored)}
        )
    result["pattern_count"] = pattern_count
    return result


def profile_for_name(name: str) -> str:
    upper = name.upper()
    if upper.endswith(".M86"):
        return "PMD86_YM2608_86PCM"
    if upper.endswith(".M26"):
        return "PMD_OPN_26K"
    raise PmdScanError(f"Unsupported PMD extension: {name}")


def scan_song(name: str, payload: bytes) -> dict:
    if len(payload) < 1 + PMD_POINTER_COUNT * 2:
        raise PmdScanError(f"{name} is too short for a PMD header")
    data = payload[1:]
    pointers = list(struct.unpack_from(f"<{PMD_POINTER_COUNT}H", data, 0))
    for index, pointer in enumerate(pointers):
        if pointer < PMD_POINTER_COUNT * 2 or pointer > len(data):
            raise PmdScanError(f"{name} pointer {index} is outside the payload: {pointer}")
    if pointers != sorted(pointers):
        raise PmdScanError(f"{name} pointer table is not monotonic")
    parts = [scan_part(data, pointers[index], PMD_PART_NAMES[index]) for index in range(PMD_PART_POINTER_COUNT)]
    rhythm = scan_rhythm_table(
        data,
        pointers[11],
        pointers[12],
        parts[10]["rhythm_pattern_selects"],
    )
    command_counts: Counter[int] = Counter()
    capability_counts: Counter[str] = Counter()
    capability_by_key: dict[str, dict] = {}
    capability_locations: dict[str, list[dict]] = defaultdict(list)
    non_exact_locations: list[dict] = []
    errors: list[str] = []
    for part in parts:
        if part["error"] is not None:
            errors.append(f"part {part['part']}: {part['error']}")
        for command in part["commands"]:
            opcode = command["opcode"]
            command_counts[opcode] += 1
            capability = capability_for_command(opcode, command["parameters"])
            key = capability["key"]
            capability_counts[key] += 1
            capability_by_key[key] = capability
            location = {
                "part": part["part"],
                "offset": command["offset"],
                "opcode": opcode,
                "name": command["name"],
                "capability_key": key,
                "capability_state": capability["state"],
            }
            if len(capability_locations[key]) < 12:
                capability_locations[key].append(location)
            if capability["state"] != CAPABILITY_EXACT:
                non_exact_locations.append(location)
    if rhythm["error"] is not None:
        errors.append(rhythm["error"])
    return {
        "name": name,
        "format": Path(name).suffix.upper().lstrip("."),
        "profile": profile_for_name(name),
        "size": len(payload),
        "sha256": sha256_bytes(payload),
        "header_byte": payload[0],
        "pointers": pointers,
        "parts": parts,
        "rhythm": rhythm,
        "voice_data_offset": pointers[12],
        "voice_and_metadata_bytes": len(data) - pointers[12],
        "command_counts": {f"0x{opcode:02X}": count for opcode, count in sorted(command_counts.items())},
        "capability_usages": [
            {
                **capability_by_key[key],
                "occurrences": capability_counts[key],
                "sample_locations": capability_locations[key],
            }
            for key in sorted(capability_counts)
        ],
        "non_exact_locations": non_exact_locations,
        "errors": errors,
    }


def signed_byte(value: int) -> int:
    return value - 256 if value >= 128 else value


def pmd_pitch(value: int, shift: int, default_shift: int) -> int | None:
    if value & 0x0F == 0x0F:
        return None
    octave = value >> 4
    pitch = (value & 0x0F) + shift + default_shift
    return (octave + 1 + pitch // 12) * 12 + pitch % 12


def part_owner(part_index: int) -> str:
    if part_index < 6:
        return "fm_part"
    if part_index < 9:
        return "ssg_part"
    if part_index == 9:
        return "adpcm_part"
    return "rhythm_part"


def semantic_family(name: str) -> str:
    if name in {
        "volume", "volume_up", "volume_down", "fine_volume_up", "fine_volume_down",
        "volume_step_up", "volume_step_down",
    }:
        return "volume"
    if name in {"gate_q", "gate_minimum", "gate_random_range", "gate_random_mode"}:
        return "gate"
    if name in {"detune", "detune_extended", "fm3_slot_detune", "fm3_slot_detune_relative"}:
        return "detune"
    if name.startswith("software_envelope"):
        return "envelope"
    if "lfo" in name:
        return "lfo"
    if name.startswith("rhythm_"):
        return "rhythm"
    if name in {
        "tempo", "bar_length", "master_transpose", "status_write", "status_add",
        "special_control",
    }:
        return "shared_state"
    if name in {"tie", "slur"}:
        return "note_lifecycle"
    if name.startswith("loop_") or name == "part_loop":
        return "loop"
    return "part_state"


def semantic_control_payload(name: str, parameters: list[int], effective_value: int | None = None) -> dict:
    payload: dict = {"parameters": parameters}
    if effective_value is not None:
        payload["effective_value"] = effective_value
    if name == "tempo" and parameters:
        payload["mode"] = f"0x{parameters[0]:02X}" if len(parameters) == 2 else "absolute"
        if len(parameters) == 2 and parameters[0] == 0xFD:
            payload["relative_bpm"] = signed_byte(parameters[1])
        elif len(parameters) == 2 and parameters[0] == 0xFF:
            payload["timer_or_bpm_value"] = parameters[1]
    elif name == "software_envelope" and len(parameters) == 4:
        payload["definition"] = {
            "attack_level": parameters[0],
            "decay_delta": signed_byte(parameters[1]),
            "sustain_rate": parameters[2],
            "release_rate": parameters[3],
        }
    elif name == "detune" and len(parameters) == 2:
        payload["signed_value"] = struct.unpack("<h", bytes(parameters))[0]
    return payload


def trace_part(
    payload: bytes,
    part_index: int,
    note_limit: int,
    *,
    follow_part_loop: bool = True,
    source_provenance: dict | None = None,
) -> dict:
    data = bytearray(payload[1:])
    pointer = struct.unpack_from("<H", data, part_index * 2)[0]
    tick = 0
    patch: int | None = None
    volume = 108 if part_index < 6 else 8 if part_index < 9 else 0
    gate_tail = 0
    shift = 0
    default_shift = 0
    detune = 0
    part_loop: int | None = None
    part_loop_tick: int | None = None
    part_loop_end_tick: int | None = None
    previous_midi: int | None = None
    notes: list[dict] = []
    controls: list[dict] = []
    events: list[dict] = []
    pending_tie = False
    pending_slur = False
    last_note_event: dict | None = None
    note_id = 0
    steps = 0

    def emit(clock: int, offset: int, family: str, event_type: str, event_payload: dict) -> dict:
        source = {
            "part": PMD_PART_NAMES[part_index],
            "stream_offset": offset,
        }
        if source_provenance is not None:
            for key in (
                "archive_id", "archive_sha256", "entry_sha256", "format", "driver_profile",
            ):
                if key in source_provenance:
                    source[key] = source_provenance[key]
        row = {
            "clock": clock,
            "sequence": len(events),
            "part": PMD_PART_NAMES[part_index],
            "owner": part_owner(part_index),
            "family": family,
            "type": event_type,
            "payload": event_payload,
            "source": source,
        }
        events.append(row)
        return row

    def emit_control(offset: int, name: str, parameters: list[int], effective_value: int | None = None) -> None:
        control = {"tick": tick, "offset": offset, "name": name}
        if len(parameters) == 1 and name in {
            "instrument", "volume", "gate_q", "transpose", "master_transpose",
            "transpose_relative", "bar_length",
        }:
            control["value"] = effective_value if effective_value is not None else parameters[0]
        else:
            control["parameters"] = parameters
        controls.append(control)
        emit(
            tick,
            offset,
            semantic_family(name),
            name,
            semantic_control_payload(name, parameters, effective_value),
        )

    while len(notes) < note_limit and steps < MAX_TRACE_STEPS:
        steps += 1
        if not (0 <= pointer < len(data)):
            raise PmdScanError(f"Trace pointer escaped data at {pointer}")
        offset = pointer
        opcode = data[pointer]
        pointer += 1
        if opcode < 0x80:
            if part_index == 10:
                emit(tick, offset, "rhythm", "rhythm_pattern_select", {"pattern": opcode})
                continue
            if pointer >= len(data):
                raise PmdScanError(f"Truncated trace note at 0x{offset:04X}")
            duration = data[pointer]
            pointer += 1
            midi = previous_midi if opcode & 0x0F == 0x0C else pmd_pitch(opcode, shift, default_shift)
            if midi is not None:
                previous_midi = midi
                note = {
                    "tick": tick,
                    "duration": duration,
                    "midi": midi,
                    "patch": patch,
                    "volume": volume,
                    "gate_tail": gate_tail,
                    "shift": shift + default_shift,
                    "detune": detune,
                }
                notes.append(note)
                action = "tie_continue" if pending_tie else "slur_retrigger" if pending_slur else "key_on"
                last_note_event = emit(
                    tick,
                    offset,
                    "note",
                    "note",
                    {
                        "note_id": note_id,
                        "midi": midi,
                        "written_duration": duration,
                        "gate_end_clock": max(tick, tick + duration - gate_tail),
                        "key_off_clock": max(tick, tick + duration - gate_tail),
                        "key_action": action,
                        "patch": patch,
                        "effective_volume": volume,
                        "transpose": shift + default_shift,
                        "detune": detune,
                        "portamento_target_midi": None,
                        "portamento_duration": None,
                    },
                )
                note_id += 1
                pending_tie = False
                pending_slur = False
            else:
                emit(tick, offset, "note", "rest", {"written_duration": duration})
            tick += duration
        elif opcode == 0x80:
            if part_loop is None:
                emit(tick, offset, "loop", "part_end", {})
                break
            if not follow_part_loop:
                part_loop_end_tick = tick
                emit(tick, offset, "loop", "part_loop_end", {})
                break
            pointer = part_loop
        elif opcode == 0xDA:
            if pointer + 3 > len(data):
                raise PmdScanError("Truncated portamento in trace")
            source, target, duration = data[pointer:pointer + 3]
            pointer += 3
            midi = pmd_pitch(source, shift, default_shift)
            target_midi = pmd_pitch(target, shift, default_shift)
            if midi is not None:
                previous_midi = midi
                note = {
                    "tick": tick, "duration": duration, "midi": midi, "target_midi": target_midi,
                    "patch": patch, "volume": volume, "gate_tail": gate_tail,
                    "shift": shift + default_shift, "detune": detune,
                }
                notes.append(note)
                action = "tie_continue" if pending_tie else "slur_retrigger" if pending_slur else "key_on"
                last_note_event = emit(
                    tick,
                    offset,
                    "note",
                    "portamento",
                    {
                        "note_id": note_id,
                        "midi": midi,
                        "written_duration": duration,
                        "gate_end_clock": max(tick, tick + duration - gate_tail),
                        "key_off_clock": max(tick, tick + duration - gate_tail),
                        "key_action": action,
                        "patch": patch,
                        "effective_volume": volume,
                        "transpose": shift + default_shift,
                        "detune": detune,
                        "portamento_target_midi": target_midi,
                        "portamento_duration": duration,
                    },
                )
                note_id += 1
                pending_tie = False
                pending_slur = False
            tick += duration
        elif opcode == 0xF9:
            target = struct.unpack_from("<H", data, pointer)[0]
            data[target + 1] = 0
            pointer += 2
            emit(tick, offset, "loop", "counted_loop_start", {})
        elif opcode == 0xF8:
            repeats = data[pointer]
            data[pointer + 1] = (data[pointer + 1] + 1) & 0xFF
            emit(tick, offset, "loop", "counted_loop_end", {"repeat_count": repeats})
            if repeats and repeats == data[pointer + 1]:
                pointer += 4
            else:
                pointer = struct.unpack_from("<H", data, pointer + 2)[0] + 2
        elif opcode == 0xF7:
            target = struct.unpack_from("<H", data, pointer)[0]
            pointer += 2
            emit(tick, offset, "loop", "counted_loop_exit", {})
            if (data[target] - 1) & 0xFF == data[target + 1]:
                pointer = target + 4
        elif opcode == 0xF6:
            part_loop = pointer
            part_loop_tick = tick
            emit(tick, offset, "loop", "part_loop_start", {})
        elif opcode == 0xFF:
            patch = data[pointer]
            emit_control(offset, "instrument", [patch], patch)
            pointer += 1
        elif opcode == 0xFD:
            volume = data[pointer]
            emit_control(offset, "volume", [volume], volume)
            pointer += 1
        elif opcode == 0xFE:
            gate_tail = data[pointer]
            emit_control(offset, "gate_q", [gate_tail], gate_tail)
            pointer += 1
        elif opcode == 0xF5:
            shift = signed_byte(data[pointer])
            emit_control(offset, "transpose", [data[pointer]], shift)
            pointer += 1
        elif opcode == 0xB2:
            default_shift = signed_byte(data[pointer])
            emit_control(offset, "master_transpose", [data[pointer]], default_shift)
            pointer += 1
        elif opcode == 0xE7:
            shift += signed_byte(data[pointer])
            emit_control(offset, "transpose_relative", [data[pointer]], shift)
            pointer += 1
        elif opcode == 0xFA:
            detune = struct.unpack_from("<h", data, pointer)[0]
            emit_control(offset, "detune", list(data[pointer:pointer + 2]), detune)
            pointer += 2
        elif opcode == 0xF4:
            volume = min(127 if part_index < 6 else 15, volume + (4 if part_index < 6 else 1))
            emit_control(offset, "volume_up", [], volume)
        elif opcode == 0xF3:
            volume = max(0, volume - (4 if part_index < 6 else 1))
            emit_control(offset, "volume_down", [], volume)
        elif opcode == 0xFB:
            pending_tie = True
            if last_note_event is not None:
                last_note_event["payload"]["tie_to_next"] = True
                last_note_event["payload"]["key_off_clock"] = None
            emit_control(offset, "tie", [])
        elif opcode == 0xC1:
            pending_slur = True
            emit_control(offset, "slur", [])
        else:
            count = command_parameter_count(data, pointer, opcode)
            if pointer + count > len(data):
                raise PmdScanError(f"Truncated {COMMAND_NAMES[opcode]} in trace")
            parameters = list(data[pointer:pointer + count])
            emit_control(offset, COMMAND_NAMES[opcode], parameters)
            pointer += count
    if steps >= MAX_TRACE_STEPS:
        raise PmdScanError(f"Trace exceeded {MAX_TRACE_STEPS} steps")
    loop = None
    if part_loop is not None:
        loop = {
            "start_offset": part_loop,
            "start_tick": part_loop_tick,
            "end_tick": part_loop_end_tick,
        }
    return {
        "part": PMD_PART_NAMES[part_index],
        "notes": notes,
        "controls": controls,
        "events": events,
        "loop": loop,
        "steps": steps,
    }


def decode_fm_voice(payload: bytes, patch_id: int) -> dict | None:
    """Decode one referenced 26-byte PMD FM voice record, never archive metadata."""
    data = payload[1:]
    if len(data) < PMD_POINTER_COUNT * 2:
        return None
    voice_offset = struct.unpack_from("<H", data, 12 * 2)[0]
    offset = voice_offset
    while offset + 26 <= len(data):
        record = data[offset:offset + 26]
        if record[0] == patch_id:
            operators = []
            for operator in range(4):
                dt_mul = record[1 + operator]
                ks_ar = record[9 + operator]
                am_dr = record[13 + operator]
                sl_rr = record[21 + operator]
                operators.append(
                    {
                        "mul": dt_mul & 0x0F,
                        "detune": (dt_mul >> 4) & 0x07,
                        "tl": record[5 + operator] & 0x7F,
                        "ks": (ks_ar >> 6) & 0x03,
                        "ar": ks_ar & 0x1F,
                        "am": (am_dr >> 7) & 0x01,
                        "dr": am_dr & 0x1F,
                        "sr": record[17 + operator] & 0x1F,
                        "sl": (sl_rr >> 4) & 0x0F,
                        "rr": sl_rr & 0x0F,
                    }
                )
            algorithm_feedback = record[25]
            return {
                "id": patch_id,
                "source_offset": offset,
                "algorithm": algorithm_feedback & 0x07,
                "feedback": (algorithm_feedback >> 3) & 0x07,
                "operators": operators,
            }
        offset += 26
    return None


def build_semantic_song_trace(name: str, payload: bytes, provenance: dict) -> dict:
    """Build one deterministic event stream; no source/archive bytes are retained."""
    entry_sha256 = sha256_bytes(payload)
    if provenance["entry_sha256"] != entry_sha256:
        raise PmdScanError(
            f"Semantic trace provenance hash mismatch for {name}: "
            f"expected {provenance['entry_sha256']}, got {entry_sha256}"
        )
    scan = scan_song(name, payload)
    if scan["errors"]:
        raise PmdScanError(f"Cannot trace {name}: {'; '.join(scan['errors'])}")
    parts: list[dict] = []
    merged_events: list[dict] = []
    patch_ids: set[int] = set()
    for part_index in range(PMD_PART_POINTER_COUNT):
        trace = trace_part(
            payload,
            part_index,
            100_000,
            follow_part_loop=False,
            source_provenance=provenance,
        )
        for note in trace["notes"]:
            if note["patch"] is not None:
                patch_ids.add(note["patch"])
        meaningful = [event for event in trace["events"] if event["type"] != "part_end"]
        if not meaningful:
            continue
        for event in trace["events"]:
            event["source"]["song"] = name
            event["source"]["entry_sha256"] = entry_sha256
        merged_events.extend(trace["events"])
        part_summary = {
            "part": trace["part"],
            "owner": part_owner(part_index),
            "note_count": len(trace["notes"]),
            "event_count": len(trace["events"]),
            "end_clock": max((event["clock"] for event in trace["events"]), default=0),
        }
        if trace["loop"] is not None:
            part_summary["loop"] = trace["loop"]
        parts.append(part_summary)

    part_order = {part: index for index, part in enumerate(PMD_PART_NAMES)}
    merged_events.sort(key=lambda event: (event["clock"], part_order[event["part"]], event["sequence"]))
    previous_clock: int | None = None
    same_clock_order = 0
    for event in merged_events:
        if event["clock"] != previous_clock:
            previous_clock = event["clock"]
            same_clock_order = 0
        event["order"] = same_clock_order
        same_clock_order += 1

    patches = []
    for patch_id in sorted(patch_ids):
        decoded = decode_fm_voice(payload, patch_id)
        if decoded is not None:
            patches.append(decoded)
    family_counts = Counter(event["family"] for event in merged_events)
    type_counts = Counter(event["type"] for event in merged_events)
    trace = {
        "schema_version": SEMANTIC_TRACE_SCHEMA_VERSION,
        "song": name,
        "source_sha256": entry_sha256,
        "source_size": len(payload),
        "parts": parts,
        "events": merged_events,
        "patches": patches,
        "summary": {
            "part_count": len(parts),
            "event_count": len(merged_events),
            "note_count": sum(part["note_count"] for part in parts),
            "family_counts": dict(sorted(family_counts.items())),
            "type_counts": dict(sorted(type_counts.items())),
        },
    }
    for key in ("archive_id", "archive_sha256", "entry_sha256", "format", "driver_profile"):
        trace[key] = provenance[key]
    canonical = json.dumps(trace, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    trace["semantic_sha256"] = sha256_bytes(canonical)
    return trace


def build_normalized_song_oracle(name: str, payload: bytes, provenance: dict) -> dict:
    """Compatibility name for callers; the returned schema is the lossless semantic stream."""
    return build_semantic_song_trace(name, payload, provenance)


def production_tracks(mml_bank: Path) -> dict[str, str]:
    text = mml_bank.read_text(encoding="utf-8")
    source = text.split('const val BAD_APPLE_LLS_MML = """', 1)[1].split('"""', 1)[0]
    tracks: dict[str, str] = {}
    current: str | None = None
    for raw in source.splitlines():
        line = raw.split(";", 1)[0]
        match = re.match(r"^([A-IR])\s+(.*)$", line)
        if match:
            current = match.group(1)
            if current != "R":
                tracks[current] = match.group(2) + " "
        elif current and current != "R" and line.strip() and not line.lstrip().startswith("#"):
            tracks[current] += line.strip() + " "
    return tracks


def parse_production_mml(
    source: str,
    semantic_events: list[dict] | None = None,
    part: str = "A",
) -> tuple[list[tuple], int]:
    index = 0
    octave = 4
    default_length = 4
    tick = 0
    patch: str | None = None
    events: list[tuple] = []
    pending_tie = False

    def emit(offset: int, family: str, event_type: str, payload: dict) -> None:
        if semantic_events is None:
            return
        part_index = PMD_PART_NAMES.index(part) if part in PMD_PART_NAMES else 0
        semantic_events.append(
            {
                "clock": tick,
                "sequence": len(semantic_events),
                "part": part,
                "owner": part_owner(part_index),
                "family": family,
                "type": event_type,
                "payload": payload,
                "source": {"part": part, "mml_offset": offset},
            }
        )

    def signed_integer(start: int) -> tuple[int, int]:
        cursor = start
        sign = 1
        if cursor < len(source) and source[cursor] in "+-":
            if source[cursor] == "-":
                sign = -1
            cursor += 1
        digits = cursor
        while cursor < len(source) and source[cursor].isdigit():
            cursor += 1
        if cursor == digits:
            raise PmdScanError(f"Production control requires an integer at {start}")
        return sign * int(source[digits:cursor]), cursor

    def integer_list(start: int, maximum: int) -> tuple[list[int], int]:
        values = []
        cursor = start
        while len(values) < maximum:
            value, cursor = signed_integer(cursor)
            values.append(value)
            if cursor >= len(source) or source[cursor] != ",":
                break
            cursor += 1
        return values, cursor

    def inline_pitch(token: str) -> int:
        match = re.fullmatch(r"([a-gA-G])([+#-]?)", token)
        if match is None:
            raise PmdScanError(f"Invalid production portamento pitch {token!r}")
        letter = match.group(1).lower()
        accidental = 1 if match.group(2) in ("+", "#") else -1 if match.group(2) == "-" else 0
        pitch = {"c": 0, "d": 2, "e": 4, "f": 5, "g": 7, "a": 9, "b": 11}[letter]
        return (octave + 1) * 12 + pitch + accidental

    while index < len(source):
        command = source[index]
        if command.isspace() or command == "|":
            index += 1
        elif command == "@":
            offset = index
            index += 1
            start = index
            while index < len(source) and (source[index].isalnum() or source[index] == "_"):
                index += 1
            patch = source[start:index]
            emit(offset, "part_state", "instrument", {"value": patch})
        elif command in "VvQpq":
            offset = index
            raw = command
            index += 1
            value, index = signed_integer(index)
            if raw in "Qq":
                emit(offset, "gate", "gate_q", {"effective_value": value})
            elif raw == "V":
                emit(offset, "volume", "volume", {"effective_value": value})
            elif raw == "v":
                emit(offset, "volume", "coarse_volume", {"effective_value": value})
            else:
                emit(offset, "part_state", "pan", {"effective_value": value})
        elif command == "E":
            offset = index
            index += 1
            if index < len(source) and source[index] in "xX":
                index += 1
                mode, index = signed_integer(index)
                emit(offset, "envelope", "envelope_clock_mode", {"mode": mode})
            else:
                values, index = integer_list(index, 6)
                if len(values) not in (4, 5, 6):
                    raise PmdScanError("Production E control requires four, five, or six parameters")
                payload = {"parameters": values}
                if len(values) == 4:
                    payload["definition"] = {
                        "attack_level": values[0],
                        "decay_delta": values[1],
                        "sustain_rate": values[2],
                        "release_rate": values[3],
                    }
                emit(offset, "envelope", "software_envelope", payload)
        elif command == "D":
            offset = index
            index += 1
            value, index = signed_integer(index)
            emit(offset, "detune", "detune", {"signed_value": value})
        elif command == "M":
            offset = index
            index += 1
            kind = "define"
            if index < len(source) and source[index] == "M":
                kind = "tl_mask"
                index += 1
            elif index < len(source) and source[index] in "WXD":
                kind = {
                    "W": "wave", "X": "clock_mode", "D": "depth_evolution",
                }[source[index]]
                index += 1
            lfo_index = 0
            if index < len(source) and source[index] in "AB":
                lfo_index = 1 if source[index] == "B" else 0
                index += 1
            maximum = 4 if kind in {"define", "depth_evolution"} else 1
            values, index = integer_list(index, maximum)
            event_type = (
                f"software_lfo_{lfo_index + 1}"
                if kind == "define"
                else f"software_lfo_{lfo_index + 1}_{kind}"
            )
            emit(
                offset,
                "lfo",
                event_type,
                {"parameters": values},
            )
        elif command == "*":
            offset = index
            index += 1
            lfo_index = 0
            if index < len(source) and source[index] in "AB":
                lfo_index = 1 if source[index] == "B" else 0
                index += 1
            value, index = signed_integer(index)
            emit(offset, "lfo", f"software_lfo_{lfo_index + 1}_switch", {"parameters": [value]})
        elif command in "ol":
            index += 1
            start = index
            while index < len(source) and source[index].isdigit():
                index += 1
            value = int(source[start:index])
            if command == "o":
                octave = value
            else:
                default_length = value
        elif command in "<>":
            octave += 1 if command == ">" else -1
            index += 1
        elif command in "()":
            offset = index
            increase = command == ")"
            index += 1
            fine = index < len(source) and source[index] == "%"
            if fine:
                index += 1
            start = index
            while index < len(source) and source[index].isdigit():
                index += 1
            amount = int(source[start:index]) if index > start else 1
            emit(
                offset,
                "volume",
                "relative_volume",
                {"delta": amount if increase else -amount, "fine": fine},
            )
        elif command == "{":
            offset = index
            close = source.find("}", index + 1)
            if close < 0:
                raise PmdScanError("Unclosed production portamento")
            content = source[index + 1:close].replace(" ", "")
            pitches = re.findall(r"[a-gA-G][+#-]?", content)
            if len(pitches) != 2 or "".join(pitches) != content:
                raise PmdScanError("Production portamento requires exactly two pitches")
            source_midi = inline_pitch(pitches[0])
            target_midi = inline_pitch(pitches[1])
            index = close + 1
            start = index
            while index < len(source) and source[index].isdigit():
                index += 1
            denominator = int(source[start:index]) if index > start else default_length
            dotted = index < len(source) and source[index] == "."
            if dotted:
                index += 1
            duration = 1920 // denominator
            if dotted:
                duration += duration // 2
            action = "tie_continue" if pending_tie else "key_on"
            voice = int(patch) if patch is not None and patch.isdigit() else None
            events.append((tick, duration, source_midi, voice, target_midi))
            emit(
                offset,
                "note",
                "portamento",
                {
                    "midi": source_midi,
                    "portamento_target_midi": target_midi,
                    "portamento_duration": duration,
                    "key_action": action,
                    "patch": voice,
                },
            )
            pending_tie = False
            tick += duration
        elif command.lower() in "abcdefgr":
            offset = index
            letter = command.lower()
            index += 1
            accidental = 0
            if index < len(source) and source[index] in "+#-":
                accidental = 1 if source[index] in "+#" else -1
                index += 1
            start = index
            while index < len(source) and source[index].isdigit():
                index += 1
            denominator = int(source[start:index]) if index > start else default_length
            dotted = index < len(source) and source[index] == "."
            if dotted:
                index += 1
            duration = 1920 // denominator
            if dotted:
                duration += duration // 2
            linked = index < len(source) and source[index] == "&"
            if linked:
                index += 1
            if letter != "r":
                pitch = {"c": 0, "d": 2, "e": 4, "f": 5, "g": 7, "a": 9, "b": 11}[letter]
                midi = (octave + 1) * 12 + pitch + accidental
                was_tied = pending_tie
                if events and events[-1][4] and events[-1][2] == midi and events[-1][0] + events[-1][1] == tick:
                    previous = events[-1]
                    events[-1] = (previous[0], previous[1] + duration, midi, previous[3], linked)
                else:
                    voice = int(patch) if patch is not None and patch.isdigit() else None
                    events.append((tick, duration, midi, voice, linked))
                emit(
                    offset,
                    "note",
                    "note",
                    {
                        "midi": midi,
                        "written_duration": duration,
                        "key_action": "tie_continue" if was_tied else "key_on",
                        "tie_to_next": linked,
                        "patch": int(patch) if patch is not None and patch.isdigit() else None,
                    },
                )
                if linked:
                    emit(offset, "note_lifecycle", "tie", {})
                pending_tie = linked
            else:
                emit(offset, "note", "rest", {"written_duration": duration})
                pending_tie = False
            tick += duration
        else:
            raise PmdScanError(f"Unsupported production MML token {command!r} at {index}")
    return events, tick


def decode_bad_apple_part(payload: bytes, channel_index: int) -> list[tuple]:
    trace = trace_part_window(payload, channel_index, BAD_APPLE_WINDOW_END)
    return [
        (
            (event[0] - BAD_APPLE_WINDOW_START) * PMD_TO_MML_TICKS,
            event[1] * PMD_TO_MML_TICKS,
            event[2], event[3], event[4], event[5], event[6] * PMD_TO_MML_TICKS,
        )
        for event in trace
        if BAD_APPLE_WINDOW_START <= event[0] < BAD_APPLE_WINDOW_END
    ]


def summarize_bad_apple_semantics(events: list[dict]) -> dict:
    pitched_volume = [
        event for event in events
        if event["type"] == "volume" and event["part"] != "K"
    ]
    gates = [event for event in events if event["type"] == "gate_q"]
    detunes = [event for event in events if event["type"] == "detune"]
    portamentos = [
        event for event in events
        if event["type"] == "portamento"
        and event["payload"].get("portamento_target_midi") is not None
        and event["payload"].get("portamento_duration", 0) > 0
    ]
    ties = [event for event in events if event["type"] == "tie"]
    valid_ties = []
    for tie in ties:
        part_notes = [
            event for event in events
            if event["part"] == tie["part"] and event["family"] == "note"
            and event["type"] in {"note", "portamento"}
        ]
        previous = next(
            (event for event in reversed(part_notes) if event["sequence"] < tie["sequence"]),
            None,
        )
        following = next(
            (event for event in part_notes if event["sequence"] > tie["sequence"]),
            None,
        )
        if (
            previous is not None
            and following is not None
            and previous["payload"].get("tie_to_next") is True
            and previous["payload"].get("key_off_clock") is None
            and following["payload"].get("key_action") == "tie_continue"
        ):
            valid_ties.append(tie)
    envelopes = [event for event in events if event["type"] == "software_envelope"]
    lfo_clocks = [
        event for event in events
        if event["type"] in {"software_lfo_1_clock_mode", "software_lfo_2_clock_mode"}
    ]
    active_lfos = []
    for event in events:
        if event["type"] in {"software_lfo_1", "software_lfo_2"}:
            active_lfos.append(event)
        elif event["type"] in {"software_lfo_1_switch", "software_lfo_2_switch"}:
            parameters = event["payload"].get("parameters", [])
            if parameters and parameters[0] != 0:
                active_lfos.append(event)

    counts = {
        "pitched_volume_transition_count": len(pitched_volume),
        "gate_change_count": len(gates),
        "detune_change_count": len(detunes),
        "target_portamento_count": len(portamentos),
        "tie_count": len(ties),
        "tie_without_retrigger_count": len(valid_ties),
        "envelope_definition_count": len(envelopes),
        "lfo_clock_declaration_count": len(lfo_clocks),
        "active_software_lfo_count": len(active_lfos),
    }
    mismatches = {
        key: {"expected": expected, "actual": counts[key]}
        for key, expected in BAD_APPLE_EXPECTED_SEMANTIC_COUNTS.items()
        if counts[key] != expected
    }

    def transition_values(selected: list[dict], value_key: str) -> list[dict]:
        return [
            {
                "part": event["part"],
                "clock": event["clock"],
                "value": event["payload"].get(value_key),
            }
            for event in selected
        ]

    source_domain = {
        "pitched_volume_transitions": transition_values(pitched_volume, "effective_value"),
        "gate_changes": transition_values(gates, "effective_value"),
        "detune_changes": transition_values(detunes, "signed_value"),
        "portamentos": [
            {
                "part": event["part"],
                "clock": event["clock"],
                "source_midi": event["payload"]["midi"],
                "target_midi": event["payload"]["portamento_target_midi"],
                "duration": event["payload"]["portamento_duration"],
                "key_action": event["payload"]["key_action"],
            }
            for event in portamentos
        ],
        "ties": [
            {"part": event["part"], "clock": event["clock"]}
            for event in ties
        ],
        "envelope_definitions": [
            {
                "part": event["part"],
                "clock": event["clock"],
                "definition": event["payload"].get("definition"),
            }
            for event in envelopes
        ],
        "lfo_clock_declarations": [
            {
                "part": event["part"],
                "clock": event["clock"],
                "type": event["type"],
                "parameters": event["payload"].get("parameters", []),
            }
            for event in lfo_clocks
        ],
    }
    semantic_identity = sha256_bytes(
        json.dumps(source_domain, sort_keys=True, separators=(",", ":")).encode("utf-8")
    )
    if semantic_identity != BAD_APPLE_EXPECTED_SEMANTIC_IDENTITY_SHA256:
        mismatches["semantic_identity_sha256"] = {
            "expected": BAD_APPLE_EXPECTED_SEMANTIC_IDENTITY_SHA256,
            "actual": semantic_identity,
        }

    return {
        "passed": not mismatches,
        "counts": counts,
        "mismatches": mismatches,
        "semantic_identity_sha256": semantic_identity,
        "expected_semantic_identity_sha256": BAD_APPLE_EXPECTED_SEMANTIC_IDENTITY_SHA256,
        "source_domain": source_domain,
    }


def build_bad_apple_semantic_gate(payload: bytes) -> dict:
    events: list[dict] = []
    for part_index in range(PMD_PART_POINTER_COUNT):
        trace = trace_part(payload, part_index, 100_000, follow_part_loop=False)
        events.extend(trace["events"])
    part_order = {part: index for index, part in enumerate(PMD_PART_NAMES)}
    events.sort(key=lambda event: (event["clock"], part_order[event["part"]], event["sequence"]))
    return summarize_bad_apple_semantics(events)


def normalize_effective_note_semantics(
    events: Iterable[dict],
    *,
    window_start: int = 0,
    window_end: int | None = None,
    tick_scale: int = 1,
    pmd_control_tick_scale: int = PMD_TO_MML_TICKS,
) -> list[dict]:
    """Reduce authored controls to effective note state without retaining a full trace."""
    state = {
        "volume": None,
        "gate_tail": 0,
        "detune": 0,
        "envelope": None,
        "lfo_clock_modes": [0, 0],
        "lfo_switches": [0, 0],
    }
    rows: list[dict] = []
    for event in events:
        clock = event["clock"]
        event_type = event["type"]
        payload = event["payload"]
        if event_type in {"volume", "volume_up", "volume_down"}:
            state["volume"] = payload.get("effective_value")
        elif event_type == "relative_volume":
            delta = payload["delta"]
            if not payload.get("fine", False) and event.get("owner") == "fm_part":
                delta *= 4
            if state["volume"] is None:
                raise PmdScanError("Relative production volume precedes an absolute volume")
            maximum = 127 if event.get("owner") == "fm_part" else 15
            state["volume"] = max(0, min(maximum, state["volume"] + delta))
        elif event_type == "gate_q":
            state["gate_tail"] = payload["effective_value"]
        elif event_type == "detune":
            state["detune"] = payload["signed_value"]
        elif event_type == "software_envelope":
            definition = payload.get("definition")
            state["envelope"] = (
                None if definition is None else {
                    "attack_level": definition["attack_level"],
                    "decay_delta": definition["decay_delta"],
                    "sustain_rate": definition["sustain_rate"],
                    "release_rate": definition["release_rate"],
                }
            )
        elif event_type in {"software_lfo_1_clock_mode", "software_lfo_2_clock_mode"}:
            lfo_index = 0 if "_1_" in event_type else 1
            parameters = payload.get("parameters", [])
            if parameters:
                state["lfo_clock_modes"][lfo_index] = parameters[0]
        elif event_type in {"software_lfo_1_switch", "software_lfo_2_switch"}:
            lfo_index = 0 if "_1_" in event_type else 1
            parameters = payload.get("parameters", [])
            if parameters:
                state["lfo_switches"][lfo_index] = parameters[0]

        if event_type not in {"note", "portamento"}:
            continue
        if clock < window_start or window_end is not None and clock >= window_end:
            continue
        active_lfos = [
            {"index": index + 1, "clock_mode": state["lfo_clock_modes"][index]}
            for index in range(2)
            if state["lfo_switches"][index] != 0
        ]
        written_duration = payload.get(
            "written_duration", payload.get("portamento_duration")
        )
        if written_duration is None:
            raise PmdScanError("A semantic note event is missing its written duration")
        duration = written_duration * tick_scale
        row = {
            "tick": (clock - window_start) * tick_scale,
            "duration": duration,
            "midi": payload["midi"],
            "patch": payload.get("patch"),
            "volume": state["volume"],
            "gate_tail": state["gate_tail"] * pmd_control_tick_scale,
            "detune": state["detune"],
            "target_midi": payload.get("portamento_target_midi"),
            "portamento_duration": (
                None
                if payload.get("portamento_duration") is None
                else payload["portamento_duration"] * tick_scale
            ),
            "key_action": payload.get("key_action", "key_on"),
            "tie_to_next": payload.get("tie_to_next") is True,
            "envelope": state["envelope"],
            # MX is behaviorally relevant only while the corresponding LFO is active.
            "active_lfos": active_lfos,
        }
        previous = rows[-1] if rows else None
        same_effective_state = previous is not None and all(
            previous[key] == row[key]
            for key in (
                "midi", "patch", "volume", "gate_tail", "detune", "envelope", "active_lfos",
            )
        )
        if (
            previous is not None
            and previous["tie_to_next"]
            and row["key_action"] == "tie_continue"
            and previous["target_midi"] is None
            and row["target_midi"] is None
            and previous["tick"] + previous["duration"] == row["tick"]
            and same_effective_state
        ):
            previous["duration"] += duration
            previous["tie_to_next"] = row["tie_to_next"]
        else:
            rows.append(row)
    return rows


def semantic_rows_sha256(rows: list[dict]) -> str:
    return sha256_bytes(
        json.dumps(rows, sort_keys=True, separators=(",", ":")).encode("utf-8")
    )


def trace_part_window(payload: bytes, channel_index: int, end_tick: int) -> list[tuple]:
    data = bytearray(payload[1:])
    pointer = struct.unpack_from("<H", data, channel_index * 2)[0]
    tick = 0
    patch = None
    volume = 108 if channel_index < 6 else 8
    gate_tail = 0
    shift = 0
    default_shift = 0
    part_loop = None
    tie = False
    previous_midi = None
    events: list[tuple] = []
    steps = 0
    while tick < end_tick and steps < MAX_TRACE_STEPS:
        steps += 1
        opcode = data[pointer]
        pointer += 1
        if opcode < 0x80:
            duration = data[pointer]
            pointer += 1
            midi = previous_midi if opcode & 0x0F == 0x0C else pmd_pitch(opcode, shift, default_shift)
            if midi is not None:
                previous_midi = midi
                if events and tie and events[-1][2] == midi and events[-1][0] + events[-1][1] == tick:
                    previous = events[-1]
                    events[-1] = (previous[0], previous[1] + duration, midi, previous[3], False, previous[5], previous[6])
                else:
                    events.append((tick, duration, midi, patch, False, volume, gate_tail))
            tick += duration
            tie = pointer < len(data) and data[pointer] == 0xFB
        elif opcode == 0x80:
            if part_loop is None:
                break
            pointer = part_loop
        elif opcode == 0xDA:
            source, _target, duration = data[pointer:pointer + 3]
            pointer += 3
            midi = pmd_pitch(source, shift, default_shift)
            if midi is not None:
                events.append((tick, duration, midi, patch, False, volume, gate_tail))
            tick += duration
        elif opcode == 0xF6:
            part_loop = pointer
        elif opcode == 0xF5:
            shift = signed_byte(data[pointer]); pointer += 1
        elif opcode == 0xB2:
            default_shift = signed_byte(data[pointer]); pointer += 1
        elif opcode == 0xE7:
            shift += signed_byte(data[pointer]); pointer += 1
        elif opcode == 0xFF:
            patch = data[pointer]; pointer += 1
        elif opcode == 0xFD:
            volume = data[pointer]; pointer += 1
        elif opcode == 0xFE:
            gate_tail = data[pointer]; pointer += 1
        elif opcode == 0xF4:
            volume = min(127 if channel_index < 6 else 15, volume + (4 if channel_index < 6 else 1))
        elif opcode == 0xF3:
            volume = max(0, volume - (4 if channel_index < 6 else 1))
        elif opcode == 0xF9:
            target = struct.unpack_from("<H", data, pointer)[0]
            data[target + 1] = 0
            pointer += 2
        elif opcode == 0xF8:
            repeats = data[pointer]
            data[pointer + 1] = (data[pointer + 1] + 1) & 0xFF
            if repeats and repeats == data[pointer + 1]:
                pointer += 4
            else:
                pointer = struct.unpack_from("<H", data, pointer + 2)[0] + 2
        elif opcode == 0xF7:
            target = struct.unpack_from("<H", data, pointer)[0]
            pointer += 2
            if (data[target] - 1) & 0xFF == data[target + 1]:
                pointer = target + 4
        elif opcode == 0xFB:
            tie = True
        else:
            pointer += command_parameter_count(data, pointer, opcode)
    if steps >= MAX_TRACE_STEPS:
        raise PmdScanError("Bad Apple trace exceeded step guard")
    return events


def audit_bad_apple(payload: bytes, mml_bank: Path) -> dict:
    digest = sha256_bytes(payload)
    if digest != BAD_APPLE_SHA256:
        raise PmdScanError(f"Unexpected ST02.M86 SHA-256: {digest}")
    tracks = production_tracks(mml_bank)
    lanes = []
    failed = False
    for channel, source_channel in (("A", 0), ("B", 1), ("C", 2), ("D", 3), ("E", 4), ("G", 6), ("H", 7)):
        production_semantics: list[dict] = []
        actual, total_ticks = parse_production_mml(
            tracks[channel], production_semantics, channel
        )
        expected = decode_bad_apple_part(payload, source_channel)
        mismatches = []
        for index in range(min(len(actual), len(expected))):
            if actual[index][:4] != expected[index][:4]:
                mismatches.append(
                    {"index": index, "mml": list(actual[index]), "pmd": list(expected[index])}
                )
        if len(actual) != len(expected) or mismatches:
            failed = True
        source_semantics = trace_part(
            payload, source_channel, 100_000, follow_part_loop=False
        )["events"]
        expected_effective = normalize_effective_note_semantics(
            source_semantics,
            window_start=BAD_APPLE_WINDOW_START,
            window_end=BAD_APPLE_WINDOW_END,
            tick_scale=PMD_TO_MML_TICKS,
        )
        actual_effective = normalize_effective_note_semantics(production_semantics)
        effective_mismatches = []
        for index in range(min(len(actual_effective), len(expected_effective))):
            if actual_effective[index] != expected_effective[index]:
                differing_fields = [
                    key for key in expected_effective[index]
                    if actual_effective[index].get(key) != expected_effective[index][key]
                ]
                effective_mismatches.append(
                    {"index": index, "fields": differing_fields}
                )
        effective_mismatch_count = (
            len(effective_mismatches)
            + abs(len(actual_effective) - len(expected_effective))
        )
        if effective_mismatch_count:
            failed = True
        lanes.append(
            {
                "channel": channel,
                "mml_events": len(actual),
                "pmd_events": len(expected),
                "ticks": total_ticks,
                "mismatch_count": len(mismatches) + abs(len(actual) - len(expected)),
                "mismatches": mismatches[:5],
                "pmd_volumes": sorted({event[5] for event in expected}),
                "pmd_gate_tails": sorted({event[6] for event in expected}),
                "effective_semantic_event_count": len(actual_effective),
                "effective_semantic_mismatch_count": effective_mismatch_count,
                "effective_semantic_mismatches": effective_mismatches[:5],
                "production_effective_semantic_sha256": semantic_rows_sha256(actual_effective),
                "source_effective_semantic_sha256": semantic_rows_sha256(expected_effective),
            }
        )
    semantic_gate = build_bad_apple_semantic_gate(payload)
    return {
        "sha256": digest,
        "passed": not failed and semantic_gate["passed"],
        "lanes": lanes,
        "semantic_aggregate_gate": semantic_gate,
        "owned_production_effective_semantics": {
            "passed": not failed,
            "domains": [
                "volume", "gate", "detune", "portamento", "tie_lifecycle",
                "software_envelope", "active_software_lfo_clock_mode",
                "shared_clock_and_transpose_projection", "note_order",
            ],
            "normalization": (
                "Authored declaration counts are collapsed to effective note state; "
                "inactive software-LFO clock declarations do not create behavior."
            ),
        },
    }


def build_non_exact_ranking(songs: Iterable[dict]) -> list[dict]:
    occurrences: Counter[str] = Counter()
    song_hashes: dict[str, set[str]] = defaultdict(set)
    locations: dict[str, list[dict]] = defaultdict(list)
    details: dict[str, dict] = {}
    seen_payloads: set[str] = set()
    for song in songs:
        digest = song["sha256"]
        if digest in seen_payloads:
            continue
        seen_payloads.add(digest)
        for location in song["non_exact_locations"]:
            key = location["capability_key"]
            occurrences[key] += 1
            song_hashes[key].add(digest)
            details[key] = location
            if len(locations[key]) < 12:
                locations[key].append(
                    {
                        "song": song["name"],
                        "sha256": digest,
                        "part": location["part"],
                        "offset": location["offset"],
                    }
                )
    ranking = []
    for key, count in occurrences.items():
        detail = details[key]
        ranking.append(
            {
                "capability_key": key,
                "opcode": f"0x{detail['opcode']:02X}",
                "name": detail["name"],
                "state": detail["capability_state"],
                "unique_songs": len(song_hashes[key]),
                "occurrences": count,
                "sample_locations": locations[key],
            }
        )
    ranking.sort(
        key=lambda item: (-item["unique_songs"], -item["occurrences"], item["capability_key"])
    )
    return ranking


def build_import_assessments(
    songs: Iterable[dict],
    product_candidates: Iterable[str],
    research_fixtures: Iterable[str],
    independent_checkpoint_audit: dict | None = None,
) -> list[dict]:
    """Assess requested entries without making offline reports runtime dependencies."""
    supplied_checkpoint_traces = (
        independent_checkpoint_audit.get("traces", [])
        if isinstance(independent_checkpoint_audit, dict)
        else []
    )
    revalidated_checkpoint_audit = build_independent_checkpoint_audit(supplied_checkpoint_traces)
    checkpoint_ready = bool(
        revalidated_checkpoint_audit["passed"]
        and isinstance(independent_checkpoint_audit, dict)
        and not independent_checkpoint_audit.get("validation_errors", [])
    )
    requested: list[tuple[str, bool]] = []
    seen_requests: set[str] = set()
    product_upper = {name.upper() for name in product_candidates}
    research_upper = {name.upper() for name in research_fixtures}
    overlap = product_upper & research_upper
    if overlap:
        raise PmdScanError(
            "An entry cannot be both a product candidate and a non-catalog research fixture: "
            + ", ".join(sorted(overlap))
        )
    for name in product_candidates:
        upper = name.upper()
        if upper not in seen_requests:
            requested.append((name, True))
            seen_requests.add(upper)
    for name in research_fixtures:
        upper = name.upper()
        if upper not in seen_requests:
            requested.append((name, False))
            seen_requests.add(upper)

    unique_matches: dict[str, dict[str, dict]] = defaultdict(dict)
    for song in songs:
        unique_matches[song["name"].upper()].setdefault(song["sha256"], song)

    assessments = []
    for requested_name, is_product in requested:
        matches = list(unique_matches.get(requested_name.upper(), {}).values())
        if not matches:
            assessments.append(
                {
                    "song": requested_name,
                    "mode": "PRODUCT_CANDIDATE" if is_product else "NON_CATALOG_RESEARCH",
                    "catalog_eligible": False,
                    "passed": False,
                    "error": "not found",
                }
            )
            continue
        if len(matches) != 1:
            assessments.append(
                {
                    "song": requested_name,
                    "mode": "PRODUCT_CANDIDATE" if is_product else "NON_CATALOG_RESEARCH",
                    "catalog_eligible": False,
                    "passed": False,
                    "error": "multiple distinct entry payloads; select an unambiguous archive/input",
                    "entry_sha256s": sorted(song["sha256"] for song in matches),
                }
            )
            continue
        song = matches[0]
        non_exact = [usage for usage in song["capability_usages"] if usage["state"] != CAPABILITY_EXACT]
        provenance = {
            "archive_id": song["archive_id"],
            "archive_sha256": song["archive_sha256"],
            "entry_sha256": song["sha256"],
            "format": song["format"],
            "driver_profile": song["profile"],
        }
        if is_product:
            capabilities_exact = not non_exact and not song["errors"]
            candidate_traces = [
                trace for trace in revalidated_checkpoint_audit["traces"]
                if trace["song"].strip().casefold() == requested_name.strip().casefold()
                and trace["entry_sha256"].lower() == song["sha256"].lower()
            ]
            candidate_checkpoint_ready = checkpoint_ready and bool(candidate_traces)
            passed = capabilities_exact and candidate_checkpoint_ready
            assessments.append(
                {
                    "song": requested_name,
                    "mode": "PRODUCT_CANDIDATE",
                    "catalog_eligible": passed,
                    "passed": passed,
                    "capabilities_exact": capabilities_exact,
                    "global_checkpoint_ready": checkpoint_ready,
                    "candidate_checkpoint_ready": candidate_checkpoint_ready,
                    "candidate_checkpoint_trace_count": len(candidate_traces),
                    "admission_blockers": [
                        blocker
                        for blocker, blocked in (
                            ("candidate uses non-EXACT capabilities or has scan errors", not capabilities_exact),
                            ("global four-trace independent checkpoint gate is incomplete", not checkpoint_ready),
                            (
                                "no independent checkpoint trace matches the candidate song and payload identity",
                                checkpoint_ready and not candidate_traces,
                            ),
                        )
                        if blocked
                    ],
                    **provenance,
                    "non_exact_capabilities": [
                        {
                            "capability_key": usage["key"],
                            "name": usage["name"],
                            "state": usage["state"],
                            "occurrences": usage["occurrences"],
                        }
                        for usage in non_exact
                    ],
                }
            )
        else:
            assessments.append(
                {
                    "song": requested_name,
                    "mode": "NON_CATALOG_RESEARCH",
                    "catalog_eligible": False,
                    "passed": not song["errors"],
                    "exception": "Non-EXACT capabilities are permitted only for this explicit non-catalog fixture.",
                    **provenance,
                    "non_exact_capabilities": [
                        {
                            "capability_key": usage["key"],
                            "name": usage["name"],
                            "state": usage["state"],
                            "occurrences": usage["occurrences"],
                        }
                        for usage in non_exact
                    ],
                }
            )
    return assessments


def _is_nonempty_string(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _checkpoint_trace_schema_errors(trace: object, index: int) -> list[str]:
    prefix = f"trace[{index}]"
    if not isinstance(trace, dict):
        return [f"{prefix} must be an object"]
    errors: list[str] = []
    for field in (
        "name", "song", "part", "archive_id", "archive_sha256", "entry_sha256",
        "format", "driver_profile",
    ):
        if not _is_nonempty_string(trace.get(field)):
            errors.append(f"{prefix}.{field} must be a nonempty string")
    for field in ("archive_sha256", "entry_sha256"):
        value = trace.get(field)
        if _is_nonempty_string(value) and SHA256_PATTERN.fullmatch(value) is None:
            errors.append(f"{prefix}.{field} must be a 64-digit SHA-256")
    archive_id = trace.get("archive_id")
    archive_hash = trace.get("archive_sha256")
    if (
        _is_nonempty_string(archive_id)
        and _is_nonempty_string(archive_hash)
        and archive_id.lower() != f"sha256:{archive_hash.lower()}"
    ):
        errors.append(f"{prefix}.archive_id must be the matching sha256: archive identity")
    part = trace.get("part")
    if _is_nonempty_string(part) and part.upper() not in PMD_PART_NAMES:
        errors.append(f"{prefix}.part must be a PMD part A through K")
    source_format = trace.get("format")
    song = trace.get("song")
    if _is_nonempty_string(source_format) and source_format.upper() not in ("M86", "M26"):
        errors.append(f"{prefix}.format must be M86 or M26")
    elif (
        _is_nonempty_string(source_format)
        and _is_nonempty_string(song)
        and not song.upper().endswith(f".{source_format.upper()}")
    ):
        errors.append(f"{prefix}.song extension must match format")
    driver_profile = trace.get("driver_profile")
    expected_driver_profile = {
        "M86": "PMD86_YM2608_86PCM",
        "M26": "PMD_OPN_26K",
    }.get(source_format.upper() if _is_nonempty_string(source_format) else "")
    if (
        expected_driver_profile is not None
        and _is_nonempty_string(driver_profile)
        and driver_profile != expected_driver_profile
    ):
        errors.append(
            f"{prefix}.driver_profile must be {expected_driver_profile} for {source_format.upper()}"
        )

    derivation = trace.get("derivation")
    if not isinstance(derivation, dict):
        errors.append(f"{prefix}.derivation must be an object")
    else:
        if derivation.get("kind") != INDEPENDENT_CHECKPOINT_DERIVATION_KIND:
            errors.append(
                f"{prefix}.derivation.kind must be {INDEPENDENT_CHECKPOINT_DERIVATION_KIND}"
            )
        if not _is_nonempty_string(derivation.get("method")):
            errors.append(f"{prefix}.derivation.method must be a nonempty string")
        if not _is_nonempty_string(derivation.get("source")):
            errors.append(f"{prefix}.derivation.source must be a nonempty string")
        if derivation.get("independent_from_timebox_runtime") is not True:
            errors.append(
                f"{prefix}.derivation.independent_from_timebox_runtime must be true"
            )

    checkpoints = trace.get("checkpoints")
    if not isinstance(checkpoints, list) or not checkpoints:
        errors.append(f"{prefix}.checkpoints must be a nonempty list")
    else:
        for checkpoint_index, checkpoint in enumerate(checkpoints):
            checkpoint_prefix = f"{prefix}.checkpoints[{checkpoint_index}]"
            if not isinstance(checkpoint, dict):
                errors.append(f"{checkpoint_prefix} must be an object")
                continue
            tick = checkpoint.get("tick")
            if isinstance(tick, bool) or not isinstance(tick, int) or tick < 0:
                errors.append(f"{checkpoint_prefix}.tick must be a nonnegative integer")
            registers = checkpoint.get("registers")
            state = checkpoint.get("state")
            if not (
                isinstance(registers, dict) and bool(registers)
                or isinstance(state, dict) and bool(state)
            ):
                errors.append(
                    f"{checkpoint_prefix} must contain nonempty registers or state"
                )
    return errors


def build_independent_checkpoint_audit(traces: Iterable[dict] | None = None) -> dict:
    declared = tuple(INDEPENDENT_CHECKPOINT_TRACES if traces is None else traces)
    accepted: list[dict] = []
    validation_errors: list[str] = []
    seen_names: set[str] = set()
    seen_songs: set[str] = set()
    seen_parts: set[str] = set()
    seen_source_hashes: set[str] = set()
    for index, trace in enumerate(declared):
        errors = _checkpoint_trace_schema_errors(trace, index)
        if errors:
            validation_errors.extend(errors)
            continue
        name_key = trace["name"].strip().casefold()
        song_key = trace["song"].strip().casefold()
        part_key = trace["part"].strip().casefold()
        source_hash = trace["entry_sha256"].lower()
        if name_key in seen_names:
            validation_errors.append(f"trace[{index}].name duplicates an earlier trace")
            continue
        if song_key in seen_songs:
            validation_errors.append(
                f"trace[{index}].song duplicates an earlier named song"
            )
            continue
        if part_key in seen_parts:
            validation_errors.append(f"trace[{index}].part duplicates an earlier part")
            continue
        if source_hash in seen_source_hashes:
            validation_errors.append(
                f"trace[{index}].entry_sha256 duplicates an earlier source identity"
            )
            continue
        seen_names.add(name_key)
        seen_songs.add(song_key)
        seen_parts.add(part_key)
        seen_source_hashes.add(source_hash)
        accepted.append(trace)
    available = len(accepted)
    passed = available >= REQUIRED_INDEPENDENT_CHECKPOINT_TRACES and not validation_errors
    return {
        "required_trace_count": REQUIRED_INDEPENDENT_CHECKPOINT_TRACES,
        "declared_trace_count": len(declared),
        "available_trace_count": available,
        "passed": passed,
        "traces": accepted,
        "validation_errors": validation_errors,
        "missing_evidence": (
            None
            if passed
            else "Four diverse, named, independently derived state/register checkpoint traces are not present."
        ),
    }


def build_feature_summary(songs: Iterable[dict]) -> list[dict]:
    unique_songs: dict[str, dict] = {}
    for song in songs:
        unique_songs.setdefault(song["sha256"], song)
    summary = []
    for feature, opcodes in FEATURE_OPCODE_GROUPS.items():
        song_count = 0
        occurrences = 0
        for song in unique_songs.values():
            counts = {
                int(opcode_text, 16): count
                for opcode_text, count in song["command_counts"].items()
            }
            feature_occurrences = sum(counts.get(opcode, 0) for opcode in opcodes)
            if feature_occurrences > 0:
                song_count += 1
                occurrences += feature_occurrences
        summary.append({"feature": feature, "unique_songs": song_count, "occurrences": occurrences})
    rhythm_songs = [song for song in unique_songs.values() if song["rhythm"]["pattern_count"] > 0]
    summary.append(
        {
            "feature": "pmd_k_r_ssg_rhythm_patterns",
            "unique_songs": len(rhythm_songs),
            "occurrences": sum(song["rhythm"]["pattern_count"] for song in rhythm_songs),
        }
    )
    summary.sort(key=lambda item: (-item["unique_songs"], -item["occurrences"], item["feature"]))
    return summary


def audit_corpus(
    thdat: Path,
    archives: list[Path],
    trace_names: list[str],
    trace_note_limit: int,
    mml_bank: Path,
    run_bad_apple: bool,
    oracle_names: list[str] | None = None,
    product_candidates: list[str] | None = None,
    research_fixtures: list[str] | None = None,
) -> dict:
    archive_reports = []
    songs = []
    source_by_name_and_hash: dict[tuple[str, str], dict] = {}
    bad_apple_payload: bytes | None = None
    for archive in archives:
        entries = list_archive(thdat, archive)
        payloads = extract_entries(thdat, archive, entries)
        archive_hash = sha256_file(archive)
        archive_id = f"sha256:{archive_hash}"
        archive_song_reports = []
        for entry in entries:
            payload = payloads[entry.name]
            song = scan_song(entry.name, payload)
            song["archive_id"] = archive_id
            song["archive_sha256"] = archive_hash
            song["stored_size"] = entry.stored_size
            songs.append(song)
            archive_song_reports.append(song)
            source_by_name_and_hash.setdefault(
                (entry.name.upper(), song["sha256"]),
                {
                    "payload": payload,
                    "provenance": {
                        "archive_id": archive_id,
                        "archive_sha256": archive_hash,
                        "entry_sha256": song["sha256"],
                        "format": song["format"],
                        "driver_profile": song["profile"],
                    },
                },
            )
            if entry.name.upper() == "ST02.M86" and song["sha256"] == BAD_APPLE_SHA256:
                bad_apple_payload = payload
        archive_reports.append(
            {
                "archive_id": archive_id,
                "path": str(archive),
                "sha256": archive_hash,
                "m86_entries": sum(1 for entry in entries if entry.name.upper().endswith(".M86")),
                "m26_entries": sum(1 for entry in entries if entry.name.upper().endswith(".M26")),
                "songs": archive_song_reports,
            }
        )
    unique_payloads: dict[str, dict] = {}
    for song in songs:
        unique_payloads.setdefault(song["sha256"], song)
    traces = []
    for requested_name in trace_names:
        matches = sorted(
            (
                (digest, source)
                for (name, digest), source in source_by_name_and_hash.items()
                if name == requested_name.upper()
            ),
            key=lambda item: item[0],
        )
        if not matches:
            traces.append({"song": requested_name, "error": "not found"})
            continue
        if len(matches) != 1:
            traces.append(
                {
                    "song": requested_name,
                    "error": "ambiguous entry name; specify a unique archive/hash identity",
                    "matching_sha256": [digest for digest, _ in matches],
                }
            )
            continue
        digest, source = matches[0]
        traced = trace_part(
            source["payload"],
            0,
            trace_note_limit,
            source_provenance=source["provenance"],
        )
        traces.append(
            {
                "song": requested_name,
                "sha256": digest,
                **source["provenance"],
                **traced,
            }
        )
    oracles = []
    for requested_name in oracle_names or []:
        matches = sorted(
            (
                (digest, source)
                for (name, digest), source in source_by_name_and_hash.items()
                if name == requested_name.upper()
            ),
            key=lambda item: item[0],
        )
        if not matches:
            oracles.append({"song": requested_name, "error": "not found"})
            continue
        if len(matches) != 1:
            oracles.append(
                {
                    "song": requested_name,
                    "error": "ambiguous entry name; specify a unique archive/hash identity",
                    "matching_sha256": [digest for digest, _ in matches],
                }
            )
            continue
        _, source = matches[0]
        oracles.append(
            build_semantic_song_trace(
                requested_name.upper(), source["payload"], source["provenance"]
            )
        )
    errors = []
    for song in songs:
        for error in song["errors"]:
            errors.append({"song": song["name"], "sha256": song["sha256"], "error": error})
    checkpoint_audit = build_independent_checkpoint_audit()
    report = {
        "schema_version": REPORT_SCHEMA_VERSION,
        "structural_inventory_evidence": STRUCTURAL_INVENTORY_EVIDENCE,
        "archives": archive_reports,
        "archive_entry_count": len(songs),
        "unique_payload_count": len(unique_payloads),
        "unique_m86_count": len({song["sha256"] for song in songs if song["format"] == "M86"}),
        "unique_m26_count": len({song["sha256"] for song in songs if song["format"] == "M26"}),
        "scan_errors": errors,
        "capability_table": [COMMAND_CAPABILITIES[key] for key in sorted(COMMAND_CAPABILITIES)],
        "independent_checkpoint_audit": checkpoint_audit,
        "feature_summary": build_feature_summary(songs),
        "non_exact_ranking": build_non_exact_ranking(songs),
        "import_assessments": build_import_assessments(
            songs,
            product_candidates or [],
            research_fixtures or [],
            checkpoint_audit,
        ),
        "traces": traces,
        "normalized_oracles": oracles,
        "bad_apple_audit": None,
    }
    if run_bad_apple:
        if bad_apple_payload is None:
            raise PmdScanError("The expected ST02.M86 payload was not found in the supplied archives")
        report["bad_apple_audit"] = audit_bad_apple(bad_apple_payload, mml_bank)
    return report


def write_json(path: Path, report: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_csv(path: Path, ranking: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=("capability_key", "opcode", "name", "state", "unique_songs", "occurrences"),
        )
        writer.writeheader()
        for item in ranking:
            writer.writerow({key: item[key] for key in writer.fieldnames})


def print_report(report: dict) -> None:
    print("PMD CORPUS AUDIT")
    print(f"inventory_evidence={report['structural_inventory_evidence']}")
    for archive in report["archives"]:
        print(
            f"archive={archive['path']} sha256={archive['sha256']} "
            f"M86={archive['m86_entries']} M26={archive['m26_entries']}"
        )
    print(
        f"entries={report['archive_entry_count']} unique={report['unique_payload_count']} "
        f"unique_M86={report['unique_m86_count']} unique_M26={report['unique_m26_count']} "
        f"scan_errors={len(report['scan_errors'])}"
    )
    checkpoint_audit = report["independent_checkpoint_audit"]
    print(
        f"INDEPENDENT CHECKPOINT TRACES passed={checkpoint_audit['passed']} "
        f"available={checkpoint_audit['available_trace_count']} "
        f"required={checkpoint_audit['required_trace_count']}"
    )
    print("NON-EXACT CAPABILITY RANKING")
    for item in report["non_exact_ranking"]:
        print(
            f"{item['capability_key']} {item['name']} state={item['state']} "
            f"songs={item['unique_songs']} "
            f"occurrences={item['occurrences']}"
        )
    print("FEATURE SUMMARY")
    for item in report["feature_summary"]:
        print(
            f"{item['feature']} songs={item['unique_songs']} "
            f"occurrences={item['occurrences']}"
        )
    print("PART TRACES")
    for trace in report["traces"]:
        if "error" in trace:
            print(f"{trace['song']}: ERROR {trace['error']}")
            continue
        print(f"{trace['song']} sha256={trace['sha256']} part={trace['part']}")
        for note in trace["notes"]:
            target = f"->{note['target_midi']}" if "target_midi" in note else ""
            print(
                f"  tick={note['tick']} dur={note['duration']} midi={note['midi']}{target} "
                f"patch={note['patch']} volume={note['volume']} q={note['gate_tail']} shift={note['shift']}"
            )
    for oracle in report["normalized_oracles"]:
        if "error" in oracle:
            print(f"SEMANTIC {oracle['song']}: ERROR {oracle['error']}")
        else:
            print(
                f"SEMANTIC {oracle['song']} sha256={oracle['source_sha256']} "
                f"parts={oracle['summary']['part_count']} events={oracle['summary']['event_count']} "
                f"semantic_sha256={oracle['semantic_sha256']}"
            )
    for assessment in report["import_assessments"]:
        if "error" in assessment:
            print(
                f"IMPORT {assessment['song']} mode={assessment['mode']} "
                f"passed=False error={assessment['error']}"
            )
        else:
            print(
                f"IMPORT {assessment['song']} mode={assessment['mode']} "
                f"passed={assessment['passed']} catalog_eligible={assessment['catalog_eligible']} "
                f"non_exact={len(assessment['non_exact_capabilities'])}"
            )
    audit = report["bad_apple_audit"]
    if audit is not None:
        print(f"BAD APPLE AUDIT passed={audit['passed']} sha256={audit['sha256']}")
        aggregate = audit["semantic_aggregate_gate"]
        print(
            "  semantic_aggregate="
            f"{'PASS' if aggregate['passed'] else 'FAIL'} "
            + " ".join(f"{key}={value}" for key, value in aggregate["counts"].items())
        )
        for lane in audit["lanes"]:
            print(
                f"  {lane['channel']}: MML={lane['mml_events']} PMD={lane['pmd_events']} "
                f"ticks={lane['ticks']} tuple_mismatches={lane['mismatch_count']} "
                f"effective_mismatches={lane['effective_semantic_mismatch_count']}"
            )


def validate_command_table() -> None:
    expected = set(range(0xB1, 0x100))
    available = set(COMMAND_NAMES)
    missing = expected - available
    if missing:
        raise PmdScanError(f"Internal command-name table is incomplete: {sorted(missing)}")
    variable = {0xFC, 0xC0}
    missing_width = expected - set(FIXED_PARAMETER_COUNTS) - variable
    if missing_width:
        raise PmdScanError(f"Internal command-width table is incomplete: {sorted(missing_width)}")


def main(argv: list[str] | None = None) -> int:
    validate_command_table()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--thdat", type=Path, required=True, help="Path to THTK thdat.exe")
    parser.add_argument("--archive", type=Path, action="append", required=True, help="Trusted TH04 archive")
    parser.add_argument("--mml-bank", type=Path, default=DEFAULT_MML_BANK)
    parser.add_argument("--trace-song", action="append", default=[])
    parser.add_argument("--trace-notes", type=int, default=8)
    parser.add_argument(
        "--oracle-song", action="append", default=[],
        help="Emit a full normalized semantic oracle for the named archive entry",
    )
    parser.add_argument(
        "--product-candidate", action="append", default=[],
        help="Require every used opcode/subcommand to be EXACT before catalog admission",
    )
    parser.add_argument(
        "--research-fixture", action="append", default=[],
        help="Explicitly allow non-EXACT commands for a non-catalog research fixture",
    )
    parser.add_argument("--bad-apple-audit", action="store_true")
    parser.add_argument("--json-out", type=Path)
    parser.add_argument("--csv-out", type=Path)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args(argv)
    if args.trace_notes <= 0:
        parser.error("--trace-notes must be positive")
    report = audit_corpus(
        args.thdat,
        args.archive,
        args.trace_song,
        args.trace_notes,
        args.mml_bank,
        args.bad_apple_audit,
        args.oracle_song,
        args.product_candidate,
        args.research_fixture,
    )
    print_report(report)
    if args.json_out is not None:
        write_json(args.json_out, report)
    if args.csv_out is not None:
        write_csv(args.csv_out, report["non_exact_ranking"])
    failed = bool(report["scan_errors"])
    if any("error" in trace for trace in report["traces"]):
        failed = True
    if any("error" in oracle for oracle in report["normalized_oracles"]):
        failed = True
    if report["bad_apple_audit"] is not None and not report["bad_apple_audit"]["passed"]:
        failed = True
    if any(not assessment["passed"] for assessment in report["import_assessments"]):
        failed = True
    if report["independent_checkpoint_audit"]["validation_errors"]:
        failed = True
    return 1 if args.strict and failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
