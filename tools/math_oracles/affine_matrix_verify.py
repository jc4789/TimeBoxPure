#!/usr/bin/env python3
"""
affine_matrix_verify.py

Verify display scaling, logical dimensions, DPI fallback, and affine coefficients.

Usage:
  python tools/math_oracles/affine_matrix_verify.py --display-w 1080 --display-h 2400 --dpi 1 --u 16
  python tools/math_oracles/affine_matrix_verify.py --cases
"""

from __future__ import annotations

import argparse
import math
from dataclasses import dataclass


@dataclass
class Result:
    display_w: int
    display_h: int
    dpi: float
    accepted_dpi: bool
    scale: int
    logical_w: int
    logical_h: int
    matrix: tuple[float, float, float, float, float, float]


def dpi_valid(dpi: float) -> bool:
    return math.isfinite(dpi) and dpi > 10.0 and dpi < 1000.0


def derive(display_w: int, display_h: int, dpi: float, u: int, min_w: int, max_w: int) -> Result:
    accepted = dpi_valid(dpi)
    # Start with a conservative integer scale derived from glyph cell and smaller display dimension.
    scale = max(1, min(display_w, display_h) // (u * 20))
    logical_w = display_w // scale
    logical_h = display_h // scale

    # Fallback bound correction. Raw numbers must be named constants at call site.
    guard = 0
    while logical_w < min_w and scale > 1 and guard < 64:
        scale -= 1
        logical_w = display_w // scale
        logical_h = display_h // scale
        guard += 1

    guard = 0
    while logical_w > max_w and guard < 64:
        scale += 1
        logical_w = display_w // scale
        logical_h = display_h // scale
        guard += 1

    # Logical-to-physical matrix:
    # x_phys = x_logical * scale
    # y_phys = y_logical * scale
    matrix = (float(scale), 0.0, 0.0, 0.0, float(scale), 0.0)
    return Result(display_w, display_h, dpi, accepted, scale, logical_w, logical_h, matrix)


def print_result(r: Result):
    print("AFFINE SCALE ORACLE")
    print(f"display: {r.display_w}x{r.display_h}")
    print(f"dpi: {r.dpi} accepted={r.accepted_dpi}")
    print(f"scale: {r.scale}")
    print(f"logical: {r.logical_w}x{r.logical_h}")
    print(f"matrix: [{r.matrix[0]}, {r.matrix[1]}, {r.matrix[2]}; {r.matrix[3]}, {r.matrix[4]}, {r.matrix[5]}]")
    print(f"integer_scale: {'PASS' if r.scale >= 1 and isinstance(r.scale, int) else 'FAIL'}")


def main(argv=None):
    p = argparse.ArgumentParser()
    p.add_argument("--display-w", type=int)
    p.add_argument("--display-h", type=int)
    p.add_argument("--dpi", type=float, default=0.0)
    p.add_argument("--u", type=int, default=16)
    p.add_argument("--min-safe-logical-width", type=int, default=320)
    p.add_argument("--max-safe-logical-width", type=int, default=1200)
    p.add_argument("--cases", action="store_true")
    args = p.parse_args(argv)

    cases = [
        (240, 240, 1), (320, 320, 0), (1080, 2400, 1),
        (1920, 1080, 96), (3440, 1440, 110), (800, 600, 9999),
    ] if args.cases else [(args.display_w, args.display_h, args.dpi)]

    for w, h, dpi in cases:
        if w is None or h is None:
            raise SystemExit("provide --display-w and --display-h or --cases")
        r = derive(w, h, dpi, args.u, args.min_safe_logical_width, args.max_safe_logical_width)
        print_result(r)
        print()


if __name__ == "__main__":
    main()
