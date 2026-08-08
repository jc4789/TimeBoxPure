from argparse import ArgumentParser
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BDF_PATH = ROOT.parent / "shinonome-0.9.10" / "bdf" / "shnmk16.bdf"
OUT_PATH = ROOT / "shared-engine" / "src" / "commonMain" / "kotlin" / "com" / "example" / "timeboxvibe" / "engine" / "ShinonomeGeneratedGlyphs.kt"
TEXT_PATHS = [
    ROOT / "shared-engine" / "src" / "commonMain" / "kotlin" / "com" / "example" / "timeboxvibe" / "engine" / "Strings.kt",
    ROOT / "shared-engine" / "src" / "commonMain" / "kotlin" / "com" / "example" / "timeboxvibe" / "engine" / "DefaultPresets.kt",
    ROOT / "shared-engine" / "src" / "commonMain" / "kotlin" / "com" / "example" / "timeboxvibe" / "engine" / "SongCatalog.kt",
    ROOT / "shared-engine" / "src" / "commonMain" / "kotlin" / "com" / "example" / "timeboxvibe" / "engine" / "core" / "Scenes.kt",
]


def collect_chars():
    chars = set()
    literal_pattern = re.compile(r'"((?:[^"\\]|\\.)*)"')
    for path in TEXT_PATHS:
        text = path.read_text(encoding="utf-8")
        for match in literal_pattern.finditer(text):
            for char in match.group(1):
                if ord(char) > 0x7F:
                    chars.add(char)
    return chars


def jis_code(char):
    try:
        encoded = char.encode("iso2022_jp")
    except UnicodeEncodeError:
        return None
    if len(encoded) >= 8 and encoded[0:3] == b"\x1b$B":
        return (encoded[3] << 8) | encoded[4]
    return None


def parse_bdf(path):
    glyphs = {}
    current_code = None
    rows = None
    in_bitmap = False
    for raw_line in path.read_text(encoding="ascii", errors="ignore").splitlines():
        line = raw_line.strip()
        if line.startswith("ENCODING "):
            current_code = int(line.split()[1])
        elif line == "BITMAP":
            rows = []
            in_bitmap = True
        elif line == "ENDCHAR":
            if current_code is not None and rows is not None and len(rows) == 16:
                glyphs[current_code] = rows
            current_code = None
            rows = None
            in_bitmap = False
        elif in_bitmap and rows is not None:
            rows.append(int(line, 16))
    return glyphs


def kotlin_char(char):
    if char == "\\":
        return "'\\\\'"
    if char == "'":
        return "'\\''"
    return "'\\u%04X'" % ord(char)


def main():
    parser = ArgumentParser()
    parser.add_argument("--bdf", type=Path, default=DEFAULT_BDF_PATH)
    args = parser.parse_args()
    if not args.bdf.is_file():
        raise FileNotFoundError("Shinonome BDF not found: %s" % args.bdf)

    chars = collect_chars()
    bdf_glyphs = parse_bdf(args.bdf)
    generated = []
    missing = []

    for char in sorted(chars, key=ord):
        code = jis_code(char)
        rows = bdf_glyphs.get(code) if code is not None else None
        if rows is None:
            missing.append(char)
            continue
        generated.append((char, rows))

    if missing:
        missing_codes = " ".join("U+%04X" % ord(char) for char in missing)
        raise ValueError("Shinonome glyphs missing: " + missing_codes)

    lines = [
        "package com.example.timeboxvibe.engine",
        "",
        "/**",
        " * Generated from the Shinonome JIS X 0208 16x16 BDF.",
        " * Only non-ASCII full-width source characters are generated.",
        " * Runtime code does not read the BDF; this is a compact ROM-glyph subset.",
        " */",
        "internal object ShinonomeGeneratedGlyphs {",
        "    fun populate(cache: Array<IntArray?>) {",
    ]
    for char, rows in generated:
        lines.append("        cache[%s.code] = intArrayOf(%s)" % (kotlin_char(char), ", ".join("0x%04X" % row for row in rows)))
    lines.extend([
        "    }",
        "",
        "    fun hasGeneratedGlyph(char: Char): Boolean {",
        "        return when (char) {",
    ])
    for char, _ in generated:
        lines.append("            %s -> true" % kotlin_char(char))
    lines.extend([
        "            else -> false",
        "        }",
        "    }",
        "",
        "    const val DIRECT_GLYPH_COUNT = %d" % len(generated),
        "    const val MISSING_GLYPH_COUNT = %d" % len(missing),
        "}",
        "",
    ])
    OUT_PATH.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    print("direct=%d missing=0" % len(generated))


if __name__ == "__main__":
    main()
