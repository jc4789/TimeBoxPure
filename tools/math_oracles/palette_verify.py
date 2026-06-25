#!/usr/bin/env python3
"""
palette_verify.py

Validate 16-color 12-bit palette definitions.

Input formats:
  JSON list of hex strings: ["#000", "#fff", "#f00", ...]
  Kotlin-ish lines containing 0xRGB or 0xRRGGBB are also scanned.

Usage:
  python tools/math_oracles/palette_verify.py palette.json
  python tools/math_oracles/palette_verify.py Palette.kt
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


def parse(path: Path):
    text = path.read_text(encoding="utf-8", errors="replace")
    try:
        data = json.loads(text)
        if isinstance(data, list):
            return [str(x) for x in data]
    except Exception:
        pass
    vals = []
    vals += re.findall(r"#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\b", text)
    vals += re.findall(r"0x([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\b", text)
    return vals


def to_rgb12(s: str):
    s = s.strip().lstrip("#")
    if s.lower().startswith("0x"):
        s = s[2:]
    if len(s) == 3:
        return tuple(int(ch, 16) for ch in s)
    if len(s) == 6:
        vals = tuple(int(s[i:i+2], 16) for i in (0,2,4))
        # Valid 12-bit expanded color has identical high and low nibbles per channel, e.g. 0xAA.
        if all((v >> 4) == (v & 0xF) for v in vals):
            return tuple(v >> 4 for v in vals)
        return None
    return None


def contrast(a, b):
    # simple luma spread over 4-bit channels
    la = 0.2126*a[0] + 0.7152*a[1] + 0.0722*a[2]
    lb = 0.2126*b[0] + 0.7152*b[1] + 0.0722*b[2]
    return abs(la - lb)


def main(argv=None):
    p = argparse.ArgumentParser()
    p.add_argument("palette", type=Path)
    args = p.parse_args(argv)

    raw = parse(args.palette)
    rgb = [to_rgb12(x) for x in raw]
    invalid = [(i, raw[i]) for i, v in enumerate(rgb) if v is None]
    rgb = [v for v in rgb if v is not None]

    print("PALETTE VERIFY")
    print(f"entries_found: {len(raw)}")
    print(f"valid_12bit_entries: {len(rgb)}")
    print(f"palette_size_16: {'PASS' if len(rgb) == 16 else 'FAIL'}")
    print(f"invalid_entries: {invalid}")

    if len(rgb) >= 2:
        # assume darkest/lightest by luma
        ordered = sorted(rgb, key=lambda c: 0.2126*c[0] + 0.7152*c[1] + 0.0722*c[2])
        c = contrast(ordered[0], ordered[-1])
        print(f"max_luma_contrast_0_to_15scale: {c:.2f}")
        print(f"high_contrast_available: {'PASS' if c >= 10 else 'WARN'}")

    raise SystemExit(0 if len(rgb) == 16 and not invalid else 1)


if __name__ == "__main__":
    main()
