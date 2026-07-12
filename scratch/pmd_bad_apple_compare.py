"""Offline ST02.M86 vs production-MML event audit; never used by runtime code."""

from pathlib import Path
import argparse
import hashlib
import re
import struct

ROOT = Path(__file__).resolve().parents[1]
MML_BANK = ROOT / "shared-engine/src/commonMain/kotlin/com/example/timeboxvibe/engine/audio/mml/MmlSongBank.kt"
REFERENCE_DIRECTORY = Path(
    r"D:\Shit Games\PC-98 Games\The Touhou98 Experience v3.0.0\(TH04) Touhou Gensoukyou ~ Lotus Land Story\disks\main\th04"
)
REFERENCE_SHA256 = "60e0e4e9742db3d97bd02238f2602ad7f671c71077479d71593e69710be8f130"
WINDOW_START = 288
WINDOW_END = 5280
PMD_TO_MML_TICKS = 20
SONG_TICKS = (WINDOW_END - WINDOW_START) * PMD_TO_MML_TICKS


def production_tracks():
    text = MML_BANK.read_text()
    source = text.split('const val BAD_APPLE_LLS_MML = """', 1)[1].split('"""', 1)[0]
    tracks = {}
    current = None
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


def reference_bytes():
    candidates = [
        path for path in REFERENCE_DIRECTORY.iterdir()
        if path.is_file() and 1_050_000 <= path.stat().st_size < 1_060_000
    ]
    if len(candidates) != 1:
        raise ValueError(f"Expected one TH04 main archive, found {candidates}")
    archive = candidates[0].read_bytes()
    entries_size, _unknown, _entry_count, key = struct.unpack_from("<HHHH", archive, 0)
    entries = bytearray(archive[16:16 + entries_size])
    rolling_key = key & 0xFF
    for index, encoded in enumerate(entries):
        entries[index] = encoded ^ rolling_key
        rolling_key = (rolling_key - entries[index]) & 0xFF
    for offset in range(0, len(entries), 32):
        file_type, file_key, name, packed_size, _original_size, position = struct.unpack_from(
            "<HB13sHHI", entries, offset
        )
        if file_type == 0:
            break
        filename = name.split(b"\0", 1)[0].decode("ascii")
        if filename.upper() == "ST02.M86":
            payload = bytearray(archive[position:position + packed_size])
            if file_key:
                for index in range(len(payload)):
                    payload[index] ^= file_key
            digest = hashlib.sha256(payload).hexdigest()
            if digest != REFERENCE_SHA256:
                raise ValueError(f"Unexpected ST02.M86 SHA-256: {digest}")
            return bytes(payload)
    raise ValueError("ST02.M86 is missing from the TH04 archive")


def parse_mml(source):
    index = 0
    octave = 4
    default_length = 4
    tick = 0
    patch = None
    events = []
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
            raise ValueError(f"Unsupported production token {command!r} at {index}")
    return events, tick


FIXED_PARAMETER_COUNTS = {
    0xFE: 1, 0xFD: 1, 0xFA: 2, 0xF4: 0, 0xF3: 0, 0xF2: 4, 0xF1: 1, 0xF0: 4,
    0xEF: 2, 0xEE: 1, 0xED: 1, 0xEC: 1, 0xEB: 1, 0xEA: 1, 0xE9: 1, 0xE8: 1,
    0xE6: 1, 0xE5: 1, 0xE4: 1, 0xE3: 1, 0xE2: 1, 0xE1: 1, 0xE0: 1, 0xDF: 1,
    0xDE: 1, 0xDD: 1, 0xDC: 1, 0xDB: 1, 0xD9: 1, 0xD8: 1, 0xD7: 1, 0xD6: 2,
    0xD5: 2, 0xD4: 1, 0xD3: 1, 0xD2: 1, 0xD1: 1, 0xD0: 1, 0xCF: 1, 0xCE: 6,
    0xCD: 5, 0xCC: 1, 0xCB: 1, 0xCA: 1, 0xC9: 1, 0xC8: 3, 0xC7: 3, 0xC6: 6,
    0xC5: 1, 0xC4: 1, 0xC3: 2, 0xC2: 1, 0xC1: 0, 0xC0: 1, 0xBF: 4, 0xBE: 1,
    0xBD: 2, 0xBC: 1, 0xBB: 1, 0xBA: 1, 0xB9: 1, 0xB8: 2, 0xB7: 1, 0xB6: 1,
    0xB5: 2, 0xB4: 16, 0xB3: 1, 0xB1: 1,
}


def pmd_pitch(value, shift, default_shift):
    if value & 0x0F == 0x0F:
        return None
    octave = value >> 4
    pitch = (value & 0x0F) + shift + default_shift
    return (octave + 1 + pitch // 12) * 12 + pitch % 12


def decode_pmd(channel_index):
    raw = reference_bytes()[1:]
    data = bytearray(raw)
    pointer = struct.unpack_from("<H", data, channel_index * 2)[0]
    tick = 0
    patch = None
    volume = 108 if channel_index < 6 else 8
    gate_tail = 0
    shift = 0
    default_shift = 0
    part_loop = None
    tie = False
    events = []
    while tick < WINDOW_END:
        command = data[pointer]
        pointer += 1
        if command < 0x80:
            duration = data[pointer]
            pointer += 1
            midi = pmd_pitch(command, shift, default_shift)
            if midi is not None:
                if events and tie and events[-1][2] == midi and events[-1][0] + events[-1][1] == tick:
                    previous = events[-1]
                    events[-1] = (previous[0], previous[1] + duration, midi, previous[3], False, previous[5], previous[6])
                else:
                    events.append((tick, duration, midi, patch, False, volume, gate_tail))
            tick += duration
            tie = pointer < len(data) and data[pointer] == 0xFB
        elif command == 0x80:
            if part_loop is None:
                break
            pointer = part_loop
        elif command == 0xDA:
            source, _target, duration = data[pointer:pointer + 3]
            pointer += 3
            midi = pmd_pitch(source, shift, default_shift)
            if midi is not None:
                events.append((tick, duration, midi, patch, False, volume, gate_tail))
            tick += duration
        elif command == 0xF6:
            part_loop = pointer
        elif command == 0xF5:
            shift = struct.unpack_from("<b", data, pointer)[0]
            pointer += 1
        elif command == 0xB2:
            default_shift = struct.unpack_from("<b", data, pointer)[0]
            pointer += 1
        elif command == 0xE7:
            shift += struct.unpack_from("<b", data, pointer)[0]
            pointer += 1
        elif command == 0xFF:
            patch = data[pointer]
            pointer += 1
        elif command == 0xFD:
            volume = data[pointer]
            pointer += 1
        elif command == 0xFE:
            gate_tail = data[pointer]
            pointer += 1
        elif command == 0xF4:
            volume = min(127 if channel_index < 6 else 15, volume + (4 if channel_index < 6 else 1))
        elif command == 0xF3:
            volume = max(0, volume - (4 if channel_index < 6 else 1))
        elif command == 0xFC:
            pointer += 2 if data[pointer] >= 0xFB else 1
        elif command == 0xF9:
            target = struct.unpack_from("<H", data, pointer)[0]
            data[target + 1] = 0
            pointer += 2
        elif command == 0xF8:
            repeats = data[pointer]
            data[pointer + 1] = (data[pointer + 1] + 1) & 0xFF
            if repeats and repeats == data[pointer + 1]:
                pointer += 4
            else:
                pointer = struct.unpack_from("<H", data, pointer + 2)[0] + 2
        elif command == 0xF7:
            target = struct.unpack_from("<H", data, pointer)[0]
            pointer += 2
            if (data[target] - 1) & 0xFF == data[target + 1]:
                pointer = target + 4
        elif command == 0xFB:
            tie = True
        else:
            count = FIXED_PARAMETER_COUNTS.get(command)
            if count is None:
                raise ValueError(f"Unsupported PMD command 0x{command:02X} at {pointer - 1}")
            pointer += count
    return [
        (
            (start - WINDOW_START) * PMD_TO_MML_TICKS,
            duration * PMD_TO_MML_TICKS,
            midi,
            voice,
            linked,
            volume,
            gate_tail * PMD_TO_MML_TICKS,
        )
        for start, duration, midi, voice, linked, volume, gate_tail in events
        if WINDOW_START <= start < WINDOW_END
    ]


def duration_candidates():
    preferred = {1: 0, 2: 1, 4: 2, 8: 3, 16: 4, 32: 5, 64: 6, 96: 7}
    by_ticks = {}
    for denominator in range(1, 193):
        if 1920 % denominator:
            continue
        base = 1920 // denominator
        if base % PMD_TO_MML_TICKS == 0:
            choice = (denominator, False)
            rank = (preferred.get(denominator, 100 + denominator), 1)
            if base not in by_ticks or rank < by_ticks[base][0]:
                by_ticks[base] = (rank, choice)
        if base % 2 == 0 and (base + base // 2) % PMD_TO_MML_TICKS == 0:
            ticks = base + base // 2
            choice = (denominator, True)
            rank = (preferred.get(denominator, 100 + denominator), 0)
            if ticks not in by_ticks or rank < by_ticks[ticks][0]:
                by_ticks[ticks] = (rank, choice)
    return [(ticks, value[1]) for ticks, value in sorted(by_ticks.items(), reverse=True)]


DURATION_CANDIDATES = duration_candidates()


def split_duration(total):
    scale = PMD_TO_MML_TICKS
    best = [None] * (total // scale + 1)
    best[0] = []
    for scaled in range(1, len(best)):
        ticks = scaled * scale
        selected = None
        for value, notation in DURATION_CANDIDATES:
            if value > ticks or best[(ticks - value) // scale] is None:
                continue
            candidate = best[(ticks - value) // scale] + [notation]
            if selected is None or len(candidate) < len(selected):
                selected = candidate
        best[scaled] = selected
    result = best[total // scale]
    if result is None or sum((1920 // denominator) * (3 if dotted else 2) // 2 for denominator, dotted in result) != total:
        raise ValueError(f"Cannot represent {total} MML ticks")
    return result


def duration_text(notation):
    denominator, dotted = notation
    return f"{denominator}{'.' if dotted else ''}"


def pitch_text(midi):
    names = ("c", "c+", "d", "e-", "e", "f", "f+", "g", "a-", "a", "b-", "b")
    return midi // 12 - 1, names[midi % 12]


def scaled_volume(channel, pmd_volume):
    if channel == "G":
        fine = (pmd_volume * 127 + 7) // 15
        return fine * 64 // 100
    return pmd_volume * 64 // 100


def append_span(tokens, ticks, note=None):
    parts = split_duration(ticks)
    for index, notation in enumerate(parts):
        if note is None:
            tokens.append("r" + duration_text(notation))
        else:
            linked = "&" if index + 1 < len(parts) else ""
            tokens.append(note + duration_text(notation) + linked)


def render_track(channel, source_channel):
    events = decode_pmd(source_channel)
    tokens = ["@square"] if channel == "G" else []
    cursor = 0
    octave = None
    patch = "square" if channel == "G" else None
    volume = None
    gate_tail = None
    for event in events:
        start, duration, midi, selected_patch, _linked, pmd_volume, selected_gate_tail = event
        if start > cursor:
            append_span(tokens, start - cursor)
        if selected_patch is not None and selected_patch != patch:
            tokens.append(f"@{selected_patch}")
            patch = selected_patch
        selected_volume = scaled_volume(channel, pmd_volume)
        if selected_volume != volume:
            tokens.append(f"V{selected_volume}")
            volume = selected_volume
        if selected_gate_tail != gate_tail:
            tokens.append(f"q{selected_gate_tail}")
            gate_tail = selected_gate_tail
        selected_octave, note = pitch_text(midi)
        if selected_octave != octave:
            tokens.append(f"o{selected_octave}")
            octave = selected_octave
        append_span(tokens, duration, note)
        cursor = start + duration
    if cursor < SONG_TICKS:
        append_span(tokens, SONG_TICKS - cursor)

    lines = []
    current = channel
    for token in tokens:
        candidate = current + (" " if current else "") + token
        if len(candidate) > 116 and current:
            lines.append(current)
            current = "    " + token
        else:
            current = candidate
    if current:
        lines.append(current)
    return "\n".join(lines)


def generated_production_tracks():
    return "\n\n".join(
        render_track(channel, source_channel)
        for channel, source_channel in (("A", 0), ("B", 1), ("C", 2), ("D", 3), ("E", 4), ("G", 7))
    )


def rewrite_song_bank(generated):
    text = MML_BANK.read_text()
    end_marker = "\nR @drum V93 l8 p3"
    production_start = text.index('const val BAD_APPLE_LLS_MML = """')
    start = text.index("\nA ", production_start) + 1
    end = text.index(end_marker, start)
    MML_BANK.write_text(text[:start] + generated + text[end:])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    if not REFERENCE_DIRECTORY.exists():
        raise SystemExit(f"Missing offline TH04 reference directory: {REFERENCE_DIRECTORY}")
    if args.generate or args.write:
        generated = generated_production_tracks()
        if args.write:
            rewrite_song_bank(generated)
        else:
            print(generated)
        return
    tracks = production_tracks()
    failed = False
    for channel, source_channel in (("A", 0), ("B", 1), ("C", 2), ("D", 3), ("E", 4), ("G", 7)):
        actual, total_ticks = parse_mml(tracks[channel])
        expected = decode_pmd(source_channel)
        mismatches = []
        for index in range(min(len(actual), len(expected))):
            if actual[index][:4] != expected[index][:4]:
                mismatches.append((index, actual[index], expected[index]))
        if len(actual) != len(expected) or mismatches:
            failed = True
        print(f"{channel}: MML={len(actual)} PMD={len(expected)} ticks={total_ticks} mismatches={len(mismatches)}")
        for mismatch in mismatches[:5]:
            print(f"  #{mismatch[0]} MML={mismatch[1]} PMD={mismatch[2]}")
        print(f"  PMD volumes={sorted(set(event[5] for event in expected))} gate_tails={sorted(set(event[6] for event in expected))}")
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()
