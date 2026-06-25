#!/usr/bin/env python3
"""
glyph_metric_oracle.py

Compute ROM glyph text metrics and centered positions.

Usage:
  python tools/math_oracles/glyph_metric_oracle.py --text "開始" --u 16 --scale 2 --box-w 160 --box-h 48
"""

from __future__ import annotations

import argparse
import json
import math


def main(argv=None):
    p = argparse.ArgumentParser()
    p.add_argument("--text", required=True)
    p.add_argument("--u", type=int, default=16)
    p.add_argument("--scale", type=int, default=1)
    p.add_argument("--box-w", type=float, required=True)
    p.add_argument("--box-h", type=float, required=True)
    p.add_argument("--box-x", type=float, default=0)
    p.add_argument("--box-y", type=float, default=0)
    args = p.parse_args(argv)

    text_w = len(args.text) * args.u * args.scale
    text_h = args.u * args.scale
    x = args.box_x + (args.box_w - text_w) / 2
    y = args.box_y + (args.box_h - text_h) / 2
    snapped_x = round(x)
    snapped_y = round(y)

    result = {
        "text": args.text,
        "chars": len(args.text),
        "U": args.u,
        "scale": args.scale,
        "textW": text_w,
        "textH": text_h,
        "box": {"x": args.box_x, "y": args.box_y, "w": args.box_w, "h": args.box_h},
        "centered": {"x": x, "y": y},
        "snapped": {"x": snapped_x, "y": snapped_y},
        "fits": text_w <= args.box_w and text_h <= args.box_h,
        "integerScale": args.scale >= 1 and int(args.scale) == args.scale,
    }
    print(json.dumps(result, indent=2, ensure_ascii=False))
    raise SystemExit(0 if result["fits"] and result["integerScale"] else 1)


if __name__ == "__main__":
    main()
