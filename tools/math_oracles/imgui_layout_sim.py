#!/usr/bin/env python3
"""
imgui_layout_sim.py

Simulate row-based IMGUI label/control layout to prevent diagonal staircase bugs.

Usage:
  python tools/math_oracles/imgui_layout_sim.py --logical-w 800 --u 16 --labels Start Settings "Very long label"
  python tools/math_oracles/imgui_layout_sim.py --json '[{"label":"Volume","control_w":160,"control_h":32}]'
"""

from __future__ import annotations

import argparse
import json


def simulate(rows, logical_w, u, label_ratio_num=2, label_ratio_den=5):
    padding = u / 2
    usable_w = logical_w - (u * 2)
    x0 = u
    current_y = u
    label_col_w = usable_w * label_ratio_num / label_ratio_den
    control_col_w = usable_w - label_col_w - padding

    out = []
    for idx, row in enumerate(rows):
        label = row["label"]
        label_scale = row.get("label_scale", 1)
        control_w = row.get("control_w", control_col_w)
        control_h = row.get("control_h", u * 2)
        label_w = len(label) * u * label_scale
        label_h = u * label_scale

        if label_w <= label_col_w:
            mode = "side_by_side"
            label_rect = (x0, current_y, label_col_w, label_h)
            control_rect = (x0 + label_col_w + padding, current_y, min(control_w, control_col_w), control_h)
            max_row_h = max(label_h, control_h)
        else:
            mode = "vertical_stack"
            label_rect = (x0, current_y, usable_w, label_h)
            control_rect = (x0, current_y + label_h + padding, min(control_w, usable_w), control_h)
            max_row_h = label_h + padding + control_h

        overlap = rects_overlap(label_rect, control_rect)
        out.append({
            "row": idx,
            "label": label,
            "mode": mode,
            "requiredLabelW": label_w,
            "labelColumnW": label_col_w,
            "label": rect_dict(label_rect),
            "control": rect_dict(control_rect),
            "maxRowHeight": max_row_h,
            "overlap": overlap,
        })
        current_y += max_row_h + padding
    return {"padding": padding, "usableWidth": usable_w, "finalY": current_y, "rows": out}


def rect_dict(r):
    x, y, w, h = r
    return {"x": x, "y": y, "w": w, "h": h}


def rects_overlap(a, b):
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax < bx + bw and ax + aw > bx and ay < by + bh and ay + ah > by


def main(argv=None):
    p = argparse.ArgumentParser()
    p.add_argument("--logical-w", type=float, default=800)
    p.add_argument("--u", type=float, default=16)
    p.add_argument("--labels", nargs="*")
    p.add_argument("--json", help="JSON list of row objects.")
    args = p.parse_args(argv)

    if args.json:
        rows = json.loads(args.json)
    else:
        labels = args.labels or ["Start", "Settings", "Very long label that must stack"]
        rows = [{"label": label} for label in labels]

    result = simulate(rows, args.logical_w, args.u)
    print(json.dumps(result, indent=2, ensure_ascii=False))
    bad = [r for r in result["rows"] if r["overlap"]]
    raise SystemExit(1 if bad else 0)


if __name__ == "__main__":
    main()
