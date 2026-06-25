#!/usr/bin/env python3
"""
fixed_point_oracle.py

Check fixed-point format range, resolution, and multiplication safety.

Usage:
  python tools/math_oracles/fixed_point_oracle.py --int-bits 16 --frac-bits 16 --max-value 4096 --mul-a 4096 --mul-b 4096
  python tools/math_oracles/fixed_point_oracle.py --frac-bits 8 --storage-bits 32 --signed --max-value 1200
"""

from __future__ import annotations

import argparse
from decimal import Decimal, getcontext
from fractions import Fraction

getcontext().prec = 60


def dec(x) -> Decimal:
    return Decimal(str(x))


def main(argv=None):
    p = argparse.ArgumentParser()
    p.add_argument("--storage-bits", type=int, default=32)
    p.add_argument("--frac-bits", type=int, required=True)
    p.add_argument("--signed", action="store_true", default=True)
    p.add_argument("--unsigned", action="store_true")
    p.add_argument("--max-value", type=Decimal, default=Decimal("0"))
    p.add_argument("--mul-a", type=Decimal, default=None)
    p.add_argument("--mul-b", type=Decimal, default=None)
    args = p.parse_args(argv)

    signed = not args.unsigned
    value_bits = args.storage_bits - (1 if signed else 0)
    int_bits = value_bits - args.frac_bits
    if int_bits <= 0:
        raise SystemExit("invalid format: no integer bits remain")

    scale = 1 << args.frac_bits
    resolution = Fraction(1, scale)
    if signed:
        min_raw = -(1 << (args.storage_bits - 1))
        max_raw = (1 << (args.storage_bits - 1)) - 1
    else:
        min_raw = 0
        max_raw = (1 << args.storage_bits) - 1

    min_val = Fraction(min_raw, scale)
    max_val = Fraction(max_raw, scale)

    print("FIXED POINT ORACLE")
    print(f"storage_bits: {args.storage_bits}")
    print(f"signed: {signed}")
    print(f"format: Q{int_bits}.{args.frac_bits}")
    print(f"resolution: {resolution} = {float(resolution)}")
    print(f"range_min: {min_val} = {float(min_val)}")
    print(f"range_max: {max_val} = {float(max_val)}")

    if args.max_value:
        mv = Fraction(str(args.max_value))
        print(f"max_value_check: {mv} -> {'PASS' if min_val <= mv <= max_val else 'FAIL'}")

    if args.mul_a is not None and args.mul_b is not None:
        a = Fraction(str(args.mul_a))
        b = Fraction(str(args.mul_b))
        prod = a * b
        raw_prod = prod * scale
        print(f"mul_check: {a} * {b} = {prod}")
        print(f"raw_scaled_product: {raw_prod}")
        print(f"result_range: {'PASS' if min_val <= prod <= max_val else 'FAIL'}")
        # Intermediate multiply of encoded raw values generally needs double width.
        a_raw = a * scale
        b_raw = b * scale
        inter = a_raw * b_raw
        inter_bits = abs(int(inter)).bit_length() + (1 if inter < 0 else 0)
        print(f"encoded_intermediate_bits_needed: {inter_bits}")
        print(f"needs_64bit_intermediate: {'YES' if inter_bits > args.storage_bits else 'NO'}")


if __name__ == "__main__":
    main()
