#!/usr/bin/env python3
"""
framebuffer_hash.py

Hash and compare indexed framebuffers.

Accepts raw bytes or text hex/index dumps.

Usage:
  python tools/math_oracles/framebuffer_hash.py frame.bin
  python tools/math_oracles/framebuffer_hash.py actual.bin --expected expected.bin
"""

from __future__ import annotations

import argparse, hashlib
from pathlib import Path


def read_bytes(path: Path) -> bytes:
    data = path.read_bytes()
    # If looks like ASCII hex/index dump, normalize.
    try:
        text = data.decode("ascii")
        stripped = "".join(ch for ch in text if ch in "0123456789ABCDEFabcdef")
        if stripped and len(stripped) >= len(text.replace("\n","").strip()) * 0.8:
            return bytes(int(ch, 16) for ch in stripped)
    except Exception:
        pass
    return data


def main(argv=None):
    p = argparse.ArgumentParser()
    p.add_argument("actual", type=Path)
    p.add_argument("--expected", type=Path)
    p.add_argument("--first", type=int, default=20)
    args = p.parse_args(argv)

    actual = read_bytes(args.actual)
    print("FRAMEBUFFER HASH")
    print(f"actual_bytes: {len(actual)}")
    print(f"actual_sha256: {hashlib.sha256(actual).hexdigest()}")

    if args.expected:
        expected = read_bytes(args.expected)
        print(f"expected_bytes: {len(expected)}")
        print(f"expected_sha256: {hashlib.sha256(expected).hexdigest()}")
        diffs = []
        for i, (a, e) in enumerate(zip(actual, expected)):
            if a != e:
                diffs.append((i,e,a))
                if len(diffs) >= args.first:
                    break
        extra = abs(len(actual)-len(expected))
        total_changed = sum(1 for a,e in zip(actual, expected) if a != e) + extra
        print(f"changed_bytes: {total_changed}")
        for i,e,a in diffs:
            print(f"diff[{i}]: expected={e} actual={a}")
        raise SystemExit(0 if total_changed == 0 else 1)


if __name__ == "__main__":
    main()
