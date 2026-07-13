"""Offline PMD corpus inventory and normalized trace oracle.

This tool reads user-supplied Touhou archives through THTK, scans PMD M86/M26
part streams, ranks commands not preserved by the current TH04 import path, and
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
    COMMAND_NAMES,
    CURRENT_IMPORT_PRESERVED,
    CURRENT_IMPORT_STRUCTURAL,
    FEATURE_OPCODE_GROUPS,
    FIXED_PARAMETER_COUNTS,
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
MAX_TRACE_STEPS = 100_000
REPORT_SCHEMA_VERSION = 1
NORMALIZED_ORACLE_SCHEMA_VERSION = 1


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
    locations: list[dict] = []
    errors: list[str] = []
    for part in parts:
        if part["error"] is not None:
            errors.append(f"part {part['part']}: {part['error']}")
        for command in part["commands"]:
            opcode = command["opcode"]
            command_counts[opcode] += 1
            if opcode not in CURRENT_IMPORT_PRESERVED and opcode not in CURRENT_IMPORT_STRUCTURAL:
                locations.append(
                    {
                        "part": part["part"],
                        "offset": command["offset"],
                        "opcode": opcode,
                        "name": command["name"],
                    }
                )
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
        "unpreserved_locations": locations,
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


def trace_part(payload: bytes, part_index: int, note_limit: int) -> dict:
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
    previous_midi: int | None = None
    notes: list[dict] = []
    controls: list[dict] = []
    steps = 0
    while len(notes) < note_limit and steps < MAX_TRACE_STEPS:
        steps += 1
        if not (0 <= pointer < len(data)):
            raise PmdScanError(f"Trace pointer escaped data at {pointer}")
        offset = pointer
        opcode = data[pointer]
        pointer += 1
        if opcode < 0x80:
            if pointer >= len(data):
                raise PmdScanError(f"Truncated trace note at 0x{offset:04X}")
            duration = data[pointer]
            pointer += 1
            midi = previous_midi if opcode & 0x0F == 0x0C else pmd_pitch(opcode, shift, default_shift)
            if midi is not None:
                previous_midi = midi
                notes.append(
                    {
                        "tick": tick,
                        "duration": duration,
                        "midi": midi,
                        "patch": patch,
                        "volume": volume,
                        "gate_tail": gate_tail,
                        "shift": shift + default_shift,
                        "detune": detune,
                    }
                )
            tick += duration
        elif opcode == 0x80:
            if part_loop is None:
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
                notes.append(
                    {
                        "tick": tick, "duration": duration, "midi": midi, "target_midi": target_midi,
                        "patch": patch, "volume": volume, "gate_tail": gate_tail,
                        "shift": shift + default_shift, "detune": detune,
                    }
                )
            tick += duration
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
        elif opcode == 0xF6:
            part_loop = pointer
        elif opcode == 0xFF:
            patch = data[pointer]
            controls.append({"tick": tick, "offset": offset, "name": "instrument", "value": patch})
            pointer += 1
        elif opcode == 0xFD:
            volume = data[pointer]
            controls.append({"tick": tick, "offset": offset, "name": "volume", "value": volume})
            pointer += 1
        elif opcode == 0xFE:
            gate_tail = data[pointer]
            controls.append({"tick": tick, "offset": offset, "name": "gate_q", "value": gate_tail})
            pointer += 1
        elif opcode == 0xF5:
            shift = signed_byte(data[pointer])
            controls.append({"tick": tick, "offset": offset, "name": "transpose", "value": shift})
            pointer += 1
        elif opcode == 0xB2:
            default_shift = signed_byte(data[pointer])
            controls.append({"tick": tick, "offset": offset, "name": "master_transpose", "value": default_shift})
            pointer += 1
        elif opcode == 0xE7:
            shift += signed_byte(data[pointer])
            controls.append({"tick": tick, "offset": offset, "name": "transpose_relative", "value": shift})
            pointer += 1
        elif opcode == 0xFA:
            detune = struct.unpack_from("<h", data, pointer)[0]
            controls.append({"tick": tick, "offset": offset, "name": "detune", "value": detune})
            pointer += 2
        elif opcode == 0xF4:
            volume = min(127 if part_index < 6 else 15, volume + (4 if part_index < 6 else 1))
        elif opcode == 0xF3:
            volume = max(0, volume - (4 if part_index < 6 else 1))
        else:
            count = command_parameter_count(data, pointer, opcode)
            controls.append(
                {
                    "tick": tick,
                    "offset": offset,
                    "name": COMMAND_NAMES[opcode],
                    "parameters": list(data[pointer:pointer + count]),
                }
            )
            pointer += count
    if steps >= MAX_TRACE_STEPS:
        raise PmdScanError(f"Trace exceeded {MAX_TRACE_STEPS} steps")
    return {"part": PMD_PART_NAMES[part_index], "notes": notes, "controls": controls, "steps": steps}


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
                sl_rr = record[21 + operator]
                operators.append(
                    {
                        "mul": dt_mul & 0x0F,
                        "detune": (dt_mul >> 4) & 0x07,
                        "tl": record[5 + operator] & 0x7F,
                        "ks": (ks_ar >> 6) & 0x03,
                        "ar": ks_ar & 0x1F,
                        "dr": record[13 + operator] & 0x1F,
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


def normalized_control(control: dict) -> dict:
    result = {"tick": control["tick"], "offset": control["offset"], "name": control["name"]}
    if "value" in control:
        result["value"] = control["value"]
    else:
        parameters = control.get("parameters", [])
        result["parameters"] = parameters
        if control["name"] == "tempo" and len(parameters) == 2:
            result["tempo_mode"] = f"0x{parameters[0]:02X}"
            if parameters[0] == 0xFD:
                result["relative_bpm"] = signed_byte(parameters[1])
            elif parameters[0] == 0xFF:
                result["timer_or_bpm_value"] = parameters[1]
        elif control["name"] == "software_envelope" and len(parameters) == 4:
            result["decoded"] = {
                "attack_level": parameters[0],
                "decay_delta": signed_byte(parameters[1]),
                "sustain_rate": parameters[2],
                "release_rate": parameters[3],
            }
    return result


def build_normalized_song_oracle(name: str, payload: bytes) -> dict:
    """Build compact deterministic semantic data; no source/archive bytes are retained."""
    scan = scan_song(name, payload)
    used_part_indices = [index for index, part in enumerate(scan["parts"]) if part["notes"] > 0]
    parts = []
    patch_ids: set[int] = set()
    control_counts: Counter[str] = Counter()
    for part_index in used_part_indices:
        trace = trace_part(payload, part_index, 100_000)
        notes = []
        for note in trace["notes"]:
            if note["patch"] is not None:
                patch_ids.add(note["patch"])
            notes.append(
                [
                    note["tick"], note["duration"], note["midi"], note["volume"],
                    note["patch"], note["gate_tail"], note["shift"], note["detune"],
                    note.get("target_midi"),
                ]
            )
        controls = [normalized_control(control) for control in trace["controls"]]
        control_counts.update(control["name"] for control in controls)
        parts.append(
            {
                "part": trace["part"],
                "note_count": len(notes),
                "end_tick": max((note[0] + note[1] for note in notes), default=0),
                "note_columns": [
                    "tick", "duration", "midi", "volume", "patch", "gate_tail",
                    "transpose", "detune", "target_midi",
                ],
                "notes": notes,
                "controls": controls,
            }
        )
    patches = []
    for patch_id in sorted(patch_ids):
        decoded = decode_fm_voice(payload, patch_id)
        if decoded is not None:
            patches.append(decoded)
    oracle = {
        "schema_version": NORMALIZED_ORACLE_SCHEMA_VERSION,
        "song": name,
        "source_sha256": sha256_bytes(payload),
        "source_size": len(payload),
        "parts": parts,
        "patches": patches,
        "summary": {
            "part_count": len(parts),
            "note_count": sum(part["note_count"] for part in parts),
            "control_counts": dict(sorted(control_counts.items())),
        },
    }
    canonical = json.dumps(oracle, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    oracle["oracle_sha256"] = sha256_bytes(canonical)
    return oracle


def compact_normalized_oracle(oracle: dict) -> dict:
    """Replace repetitive full note rows with deterministic, readable runs."""
    compact = {key: value for key, value in oracle.items() if key not in ("parts", "oracle_sha256")}
    compact_parts = []
    for part in oracle["parts"]:
        notes = part["notes"]
        runs = []
        index = 0
        while index < len(notes):
            maximum_period = min(16, len(notes) - index)
            best_period = 1
            best_repeats = 1
            period = 1
            while period <= maximum_period:
                repeats = 1
                while index + (repeats + 1) * period <= len(notes):
                    matched = True
                    compare = 0
                    while compare < period:
                        first = notes[index + compare]
                        other = notes[index + repeats * period + compare]
                        if first[1:] != other[1:] or other[0] != first[0] + repeats * period * first[1]:
                            matched = False
                            break
                        compare += 1
                    if not matched:
                        break
                    repeats += 1
                if repeats * period > best_repeats * best_period:
                    best_period = period
                    best_repeats = repeats
                period += 1
            first = notes[index]
            pitches = [notes[index + item][2] for item in range(best_period)]
            runs.append(
                {
                    "tick": first[0], "duration": first[1], "pitches": pitches,
                    "repeats": best_repeats, "volume": first[3], "patch": first[4],
                    "gate_tail": first[5], "transpose": first[6], "detune": first[7],
                    "target_midi": first[8],
                }
            )
            index += best_period * best_repeats
        compact_parts.append(
            {
                "part": part["part"], "note_count": part["note_count"],
                "end_tick": part["end_tick"], "note_runs": runs, "controls": part["controls"],
            }
        )
    compact["parts"] = compact_parts
    canonical = json.dumps(compact, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    compact["oracle_sha256"] = sha256_bytes(canonical)
    return compact


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


def parse_production_mml(source: str) -> tuple[list[tuple], int]:
    index = 0
    octave = 4
    default_length = 4
    tick = 0
    patch: str | None = None
    events: list[tuple] = []
    while index < len(source):
        command = source[index]
        if command.isspace() or command == "|":
            index += 1
        elif command == "@":
            index += 1
            start = index
            while index < len(source) and (source[index].isalnum() or source[index] == "_"):
                index += 1
            patch = source[start:index]
        elif command in "VvQpq":
            index += 1
            if index < len(source) and source[index] in "+-":
                index += 1
            while index < len(source) and source[index].isdigit():
                index += 1
        elif command == "E":
            index += 1
            if index < len(source) and source[index] in "xX":
                index += 1
                start = index
                while index < len(source) and source[index].isdigit():
                    index += 1
                if index == start:
                    raise PmdScanError("Production EX control requires a mode")
            else:
                value_count = 0
                while value_count < 6:
                    if index < len(source) and source[index] in "+-":
                        index += 1
                    start = index
                    while index < len(source) and source[index].isdigit():
                        index += 1
                    if index == start:
                        raise PmdScanError("Production E control requires numeric parameters")
                    value_count += 1
                    if index >= len(source) or source[index] != ",":
                        break
                    index += 1
                if value_count not in (4, 5, 6):
                    raise PmdScanError("Production E control requires four, five, or six parameters")
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
        elif command.lower() in "abcdefgr":
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
                if events and events[-1][4] and events[-1][2] == midi and events[-1][0] + events[-1][1] == tick:
                    previous = events[-1]
                    events[-1] = (previous[0], previous[1] + duration, midi, previous[3], linked)
                else:
                    voice = int(patch) if patch is not None and patch.isdigit() else None
                    events.append((tick, duration, midi, voice, linked))
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
        actual, total_ticks = parse_production_mml(tracks[channel])
        expected = decode_bad_apple_part(payload, source_channel)
        mismatches = []
        for index in range(min(len(actual), len(expected))):
            if actual[index][:4] != expected[index][:4]:
                mismatches.append(
                    {"index": index, "mml": list(actual[index]), "pmd": list(expected[index])}
                )
        if len(actual) != len(expected) or mismatches:
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
            }
        )
    return {"sha256": digest, "passed": not failed, "lanes": lanes}


def build_unsupported_ranking(songs: Iterable[dict]) -> list[dict]:
    occurrences: Counter[int] = Counter()
    song_hashes: dict[int, set[str]] = defaultdict(set)
    locations: dict[int, list[dict]] = defaultdict(list)
    seen_payloads: set[str] = set()
    for song in songs:
        digest = song["sha256"]
        if digest in seen_payloads:
            continue
        seen_payloads.add(digest)
        for location in song["unpreserved_locations"]:
            opcode = location["opcode"]
            occurrences[opcode] += 1
            song_hashes[opcode].add(digest)
            if len(locations[opcode]) < 12:
                locations[opcode].append(
                    {
                        "song": song["name"],
                        "sha256": digest,
                        "part": location["part"],
                        "offset": location["offset"],
                    }
                )
    ranking = []
    for opcode, count in occurrences.items():
        ranking.append(
            {
                "opcode": f"0x{opcode:02X}",
                "name": COMMAND_NAMES[opcode],
                "unique_songs": len(song_hashes[opcode]),
                "occurrences": count,
                "sample_locations": locations[opcode],
            }
        )
    ranking.sort(key=lambda item: (-item["unique_songs"], -item["occurrences"], item["opcode"]))
    return ranking


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
) -> dict:
    archive_reports = []
    songs = []
    payload_by_name_and_hash: dict[tuple[str, str], bytes] = {}
    bad_apple_payload: bytes | None = None
    for archive in archives:
        entries = list_archive(thdat, archive)
        payloads = extract_entries(thdat, archive, entries)
        archive_hash = sha256_file(archive)
        archive_song_reports = []
        for entry in entries:
            payload = payloads[entry.name]
            song = scan_song(entry.name, payload)
            song["archive_sha256"] = archive_hash
            song["stored_size"] = entry.stored_size
            songs.append(song)
            archive_song_reports.append(song)
            payload_by_name_and_hash[(entry.name.upper(), song["sha256"])] = payload
            if entry.name.upper() == "ST02.M86" and song["sha256"] == BAD_APPLE_SHA256:
                bad_apple_payload = payload
        archive_reports.append(
            {
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
                (digest, payload)
                for (name, digest), payload in payload_by_name_and_hash.items()
                if name == requested_name.upper()
            ),
            key=lambda item: item[0],
        )
        if not matches:
            traces.append({"song": requested_name, "error": "not found"})
            continue
        digest, payload = matches[0]
        traced = trace_part(payload, 0, trace_note_limit)
        traces.append({"song": requested_name, "sha256": digest, **traced})
    oracles = []
    for requested_name in oracle_names or []:
        matches = sorted(
            (
                (digest, payload)
                for (name, digest), payload in payload_by_name_and_hash.items()
                if name == requested_name.upper()
            ),
            key=lambda item: item[0],
        )
        if not matches:
            oracles.append({"song": requested_name, "error": "not found"})
            continue
        _, payload = matches[0]
        oracles.append(compact_normalized_oracle(build_normalized_song_oracle(requested_name.upper(), payload)))
    errors = []
    for song in songs:
        for error in song["errors"]:
            errors.append({"song": song["name"], "sha256": song["sha256"], "error": error})
    report = {
        "schema_version": REPORT_SCHEMA_VERSION,
        "archives": archive_reports,
        "archive_entry_count": len(songs),
        "unique_payload_count": len(unique_payloads),
        "unique_m86_count": len({song["sha256"] for song in songs if song["format"] == "M86"}),
        "unique_m26_count": len({song["sha256"] for song in songs if song["format"] == "M26"}),
        "scan_errors": errors,
        "feature_summary": build_feature_summary(songs),
        "unsupported_ranking": build_unsupported_ranking(songs),
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
        writer = csv.DictWriter(stream, fieldnames=("opcode", "name", "unique_songs", "occurrences"))
        writer.writeheader()
        for item in ranking:
            writer.writerow({key: item[key] for key in writer.fieldnames})


def print_report(report: dict) -> None:
    print("PMD CORPUS AUDIT")
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
    print("UNPRESERVED COMMAND RANKING")
    for item in report["unsupported_ranking"]:
        print(
            f"{item['opcode']} {item['name']} songs={item['unique_songs']} "
            f"occurrences={item['occurrences']}"
        )
    print("FEATURE SUMMARY")
    for item in report["feature_summary"]:
        print(
            f"{item['feature']} songs={item['unique_songs']} "
            f"occurrences={item['occurrences']}"
        )
    print("NORMALIZED TRACES")
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
            print(f"ORACLE {oracle['song']}: ERROR {oracle['error']}")
        else:
            print(
                f"ORACLE {oracle['song']} sha256={oracle['source_sha256']} "
                f"parts={oracle['summary']['part_count']} notes={oracle['summary']['note_count']} "
                f"oracle_sha256={oracle['oracle_sha256']}"
            )
    audit = report["bad_apple_audit"]
    if audit is not None:
        print(f"BAD APPLE AUDIT passed={audit['passed']} sha256={audit['sha256']}")
        for lane in audit["lanes"]:
            print(
                f"  {lane['channel']}: MML={lane['mml_events']} PMD={lane['pmd_events']} "
                f"ticks={lane['ticks']} mismatches={lane['mismatch_count']}"
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
    parser.add_argument("--bad-apple-audit", action="store_true")
    parser.add_argument("--json-out", type=Path)
    parser.add_argument(
        "--oracle-out", type=Path,
        help="Write the single --oracle-song result as a standalone compact fixture",
    )
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
    )
    print_report(report)
    if args.json_out is not None:
        write_json(args.json_out, report)
    if args.oracle_out is not None:
        if len(report["normalized_oracles"]) != 1:
            parser.error("--oracle-out requires exactly one --oracle-song")
        write_json(args.oracle_out, report["normalized_oracles"][0])
    if args.csv_out is not None:
        write_csv(args.csv_out, report["unsupported_ranking"])
    failed = bool(report["scan_errors"])
    if any("error" in trace for trace in report["traces"]):
        failed = True
    if any("error" in oracle for oracle in report["normalized_oracles"]):
        failed = True
    if report["bad_apple_audit"] is not None and not report["bad_apple_audit"]["passed"]:
        failed = True
    return 1 if args.strict and failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
