from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BDF_PATH = ROOT / "AI_REF" / "shnmk16.bdf"
CMEX_BDF_PATH = ROOT / "AI_REF" / "cmex16m.bdf"
OUT_PATH = ROOT / "shared-engine" / "src" / "commonMain" / "kotlin" / "com" / "example" / "timeboxvibe" / "engine" / "ShinonomeGeneratedGlyphs.kt"
TEXT_PATHS = [
    ROOT / "shared-engine" / "src" / "commonMain" / "kotlin" / "com" / "example" / "timeboxvibe" / "engine" / "Strings.kt",
    ROOT / "shared-engine" / "src" / "commonMain" / "kotlin" / "com" / "example" / "timeboxvibe" / "engine" / "DefaultPresets.kt",
]

SIMPLIFIED_ALIASES = {
    "专": "専", "为": "為", "么": "麼", "义": "義", "习": "習", "书": "書", "严": "厳",
    "传": "傳", "值": "値", "关": "關", "减": "減", "击": "撃", "办": "辦",
    "务": "務", "动": "動", "单": "単", "启": "啓", "响": "響", "块": "塊",
    "增": "増", "复": "復", "对": "対", "帮": "幇", "应": "應", "开": "開", "丽": "麗",
    "弹": "弾", "归": "帰", "总": "総", "执": "執", "时": "時", "暂": "暫",
    "极": "極", "桌": "卓", "槛": "檻", "步": "歩", "渐": "漸", "热": "熱",
    "环": "環", "现": "現", "琐": "瑣", "种": "種", "简": "簡", "线": "線",
    "经": "経", "结": "結", "统": "統", "续": "続", "缩": "縮", "脑": "脳",
    "丝": "絲", "节": "節", "观": "観", "览": "覧", "计": "計", "让": "讓", "论": "論",
    "设": "設", "课": "課", "轮": "輪", "输": "輸", "过": "過", "迈": "邁",
    "进": "進", "选": "選", "递": "逓", "邮": "郵", "钟": "鐘", "铃": "鈴",
    "锁": "鎖", "长": "長", "门": "門", "闭": "閉", "间": "間", "闹": "鬧",
    "阶": "階", "预": "預", "题": "題", "骤": "驟", "权": "権", "测": "測",
    "灵": "霊", "爱": "愛", "确": "確", "红": "紅", "蓝": "藍", "试": "試",
    "语": "語", "辉": "輝", "频": "頻",
}


def collect_chars():
    chars = set()
    literal_pattern = re.compile(r'"((?:[^"\\]|\\.)*)"')
    for path in TEXT_PATHS:
        text = path.read_text(encoding="utf-8")
        for match in literal_pattern.finditer(text):
            literal = match.group(1)
            for char in literal:
                if ord(char) > 0x7F:
                    chars.add(char)
    for char in list(chars):
        alias = SIMPLIFIED_ALIASES.get(char)
        if alias is not None:
            chars.add(alias)
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
    code = ord(char)
    if char == "\\":
        return "'\\\\'"
    if char == "'":
        return "'\\''"
    return "'\\u%04X'" % code


def main():
    chars = collect_chars()
    bdf_glyphs = parse_bdf(BDF_PATH)
    cmex_glyphs = parse_bdf(CMEX_BDF_PATH)
    direct = []
    aliases = []
    cmex_direct = []
    missing = []

    for char in sorted(chars, key=ord):
        code = jis_code(char)
        rows = bdf_glyphs.get(code) if code is not None else None
        if rows is not None:
            direct.append((char, rows))
            continue
        alias = SIMPLIFIED_ALIASES.get(char)
        alias_code = jis_code(alias) if alias is not None else None
        alias_rows = bdf_glyphs.get(alias_code) if alias_code is not None else None
        if alias_rows is not None:
            aliases.append((char, alias, alias_rows))
            continue

        cmex_rows = cmex_glyphs.get(ord(char)) if code is None else None
        if cmex_rows is not None:
            cmex_direct.append((char, cmex_rows))
        else:
            missing.append(char)

    lines = [
        "package com.example.timeboxvibe.engine",
        "",
        "/**",
        " * Generated from AI_REF/shnmk16.bdf plus Simplified Chinese gaps from AI_REF/cmex16m.bdf.",
        " * Runtime code does not read the BDF; this is a compact ROM-glyph subset.",
        " */",
        "internal object ShinonomeGeneratedGlyphs {",
        "    fun populate(cache: Array<IntArray?>) {",
    ]
    for char, rows in direct:
        lines.append("        cache[%s.code] = intArrayOf(%s)" % (kotlin_char(char), ", ".join("0x%04X" % row for row in rows)))
    for char, alias, rows in aliases:
        lines.append("        cache[%s.code] = intArrayOf(%s) // %s form" % (kotlin_char(char), ", ".join("0x%04X" % row for row in rows), alias))
    for char, rows in cmex_direct:
        lines.append("        cache[%s.code] = intArrayOf(%s) // cmex simplified form" % (kotlin_char(char), ", ".join("0x%04X" % row for row in rows)))
    lines.extend([
        "    }",
        "",
        "    fun hasGeneratedGlyph(char: Char): Boolean {",
        "        return when (char) {",
    ])
    for char, _ in direct:
        lines.append("            %s -> true" % kotlin_char(char))
    for char, _, _ in aliases:
        lines.append("            %s -> true" % kotlin_char(char))
    for char, _ in cmex_direct:
        lines.append("            %s -> true" % kotlin_char(char))
    lines.extend([
        "            else -> false",
        "        }",
        "    }",
        "",
        "    const val DIRECT_GLYPH_COUNT = %d" % len(direct),
        "    const val ALIAS_GLYPH_COUNT = %d" % len(aliases),
        "    const val CMEX_GLYPH_COUNT = %d" % len(cmex_direct),
        "    const val MISSING_GLYPH_COUNT = %d" % len(missing),
        "}",
        "",
    ])
    OUT_PATH.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    print("direct=%d aliases=%d cmex=%d missing=%d" % (len(direct), len(aliases), len(cmex_direct), len(missing)))
    if missing:
        print("missing=" + " ".join("U+%04X" % ord(char) for char in missing))


if __name__ == "__main__":
    main()
