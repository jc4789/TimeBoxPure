#!/usr/bin/env python3
"""
audio_lut_analyze.py

Generate and analyze waveform LUTs for procedural audio.

Usage:
  python tools/math_oracles/audio_lut_analyze.py --kind sin --size 1024 --amp 0.8
"""

from __future__ import annotations

import argparse, math, statistics


def sample(kind, t):
    if kind == "sin":
        return math.sin(2*math.pi*t)
    if kind == "triangle":
        return 4*abs(t-0.5)-1
    if kind == "saw":
        return 2*t-1
    if kind == "square":
        return 1 if t < 0.5 else -1
    raise ValueError(kind)


def main(argv=None):
    p = argparse.ArgumentParser()
    p.add_argument("--kind", choices=["sin","triangle","saw","square"], default="sin")
    p.add_argument("--size", type=int, default=1024)
    p.add_argument("--amp", type=float, default=1.0)
    args = p.parse_args(argv)

    vals = [sample(args.kind, i/args.size)*args.amp for i in range(args.size)]
    peak = max(abs(v) for v in vals)
    mean = statistics.fmean(vals)
    discontinuity = abs(vals[0] - vals[-1])

    print("AUDIO LUT ANALYZE")
    print(f"kind: {args.kind}")
    print(f"size: {args.size}")
    print(f"amp: {args.amp}")
    print(f"peak_abs: {peak:.9f}")
    print(f"mean: {mean:.9f}")
    print(f"wrap_discontinuity: {discontinuity:.9f}")
    print(f"clipping: {'FAIL' if peak > 1.0 else 'PASS'}")


if __name__ == "__main__":
    main()
