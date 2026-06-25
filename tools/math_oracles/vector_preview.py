#!/usr/bin/env python3
"""
vector_preview.py

Tiny aliased vector preview renderer for test artifacts.

Input JSON:
{
  "width": 128,
  "height": 64,
  "commands": [
    {"cmd":"rect","x":1,"y":1,"w":20,"h":10,"color":1},
    {"cmd":"line","x0":0,"y0":0,"x1":50,"y1":20,"color":2}
  ]
}

Usage:
  python tools/math_oracles/vector_preview.py shape.json --dump
  python tools/math_oracles/vector_preview.py shape.json --pgm out.pgm
"""

from __future__ import annotations

import argparse, json, hashlib
from pathlib import Path


def setp(buf, w, h, x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < w and 0 <= y < h:
        buf[y][x] = int(c) & 0xF


def line(buf, w, h, x0, y0, x1, y1, c):
    x0, y0, x1, y1 = map(lambda v: int(round(v)), (x0,y0,x1,y1))
    dx = abs(x1-x0); sx = 1 if x0 < x1 else -1
    dy = -abs(y1-y0); sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        setp(buf,w,h,x0,y0,c)
        if x0 == x1 and y0 == y1: break
        e2 = 2*err
        if e2 >= dy:
            err += dy; x0 += sx
        if e2 <= dx:
            err += dx; y0 += sy


def rect(buf,w,h,x,y,rw,rh,c):
    for yy in range(int(y), int(y+rh)):
        for xx in range(int(x), int(x+rw)):
            setp(buf,w,h,xx,yy,c)


def render(doc):
    w, h = int(doc["width"]), int(doc["height"])
    bg = int(doc.get("bg", 0)) & 0xF
    buf = [[bg for _ in range(w)] for _ in range(h)]
    for cmd in doc.get("commands", []):
        if cmd["cmd"] == "rect":
            rect(buf,w,h,cmd["x"],cmd["y"],cmd["w"],cmd["h"],cmd["color"])
        elif cmd["cmd"] == "line":
            line(buf,w,h,cmd["x0"],cmd["y0"],cmd["x1"],cmd["y1"],cmd["color"])
        elif cmd["cmd"] == "pixel":
            setp(buf,w,h,cmd["x"],cmd["y"],cmd["color"])
        else:
            raise ValueError(f"unknown cmd {cmd['cmd']}")
    return buf


def flat(buf):
    return bytes([p for row in buf for p in row])


def write_pgm(buf, path):
    # PGM test artifact, maps 0..15 to 0..255 grayscale.
    h, w = len(buf), len(buf[0])
    data = bytes([p*17 for row in buf for p in row])
    Path(path).write_bytes(f"P5\n{w} {h}\n255\n".encode() + data)


def main(argv=None):
    p = argparse.ArgumentParser()
    p.add_argument("input", type=Path)
    p.add_argument("--dump", action="store_true")
    p.add_argument("--pgm", type=Path)
    args = p.parse_args(argv)

    doc = json.loads(args.input.read_text(encoding="utf-8"))
    buf = render(doc)
    b = flat(buf)
    print("VECTOR PREVIEW")
    print(f"size: {len(buf[0])}x{len(buf)}")
    print(f"sha256: {hashlib.sha256(b).hexdigest()}")
    print(f"invalid_palette_indices: 0")
    if args.pgm:
        write_pgm(buf, args.pgm)
        print(f"wrote_test_artifact: {args.pgm}")
    if args.dump:
        chars = "0123456789ABCDEF"
        for row in buf:
            print("".join(chars[p] for p in row))


if __name__ == "__main__":
    main()
