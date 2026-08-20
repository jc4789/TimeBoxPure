#!/usr/bin/env python3
"""One-off indexed preview of SMALL/PANEL frames + seigaiha. Test artifact only."""
from __future__ import annotations
import struct
import zlib
from pathlib import Path

W, H = 200, 220
U = 16
buf = [[0 for _ in range(W)] for _ in range(H)]

def setp(x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < W and 0 <= y < H:
        buf[y][x] = c & 15

def line(x0, y0, x1, y1, c):
    x0, y0, x1, y1 = map(lambda v: int(round(v)), (x0, y0, x1, y1))
    dx, sx = abs(x1 - x0), 1 if x0 < x1 else -1
    dy, sy = -abs(y1 - y0), 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        setp(x0, y0, c)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy

def flat(p0, p1, p2, p3, tol=1.0):
    def d(ax, ay, bx, by):
        return ((ax - bx) ** 2 + (ay - by) ** 2) ** 0.5
    chord = d(p0[0], p0[1], p3[0], p3[1])
    if chord < 1e-6:
        return d(p0[0], p0[1], p1[0], p1[1]) <= tol and d(p0[0], p0[1], p2[0], p2[1]) <= tol
    return d(p0[0], p0[1], p1[0], p1[1]) + d(p1[0], p1[1], p2[0], p2[1]) + d(p2[0], p2[1], p3[0], p3[1]) - chord <= tol

def mid(a, b):
    return ((a[0] + b[0]) * 0.5, (a[1] + b[1]) * 0.5)

def cubic(p0, p1, p2, p3, c, depth=0):
    if depth >= 16 or flat(p0, p1, p2, p3):
        line(p0[0], p0[1], p3[0], p3[1], c)
        return
    p01, p12, p23 = mid(p0, p1), mid(p1, p2), mid(p2, p3)
    p012, p123 = mid(p01, p12), mid(p12, p23)
    p0123 = mid(p012, p123)
    cubic(p0, p01, p012, p0123, c, depth + 1)
    cubic(p0123, p123, p23, p3, c, depth + 1)

def xform(px, py, ox, oy, xx, xy, yx, yy):
    return (ox + px * xx + py * yx, oy + px * xy + py * yy)

arch = [
    (0.00, 0.00),
    (0.22, 0.00), (0.28, 0.55), (0.50, 0.62),
    (0.72, 0.55), (0.78, 0.00), (1.00, 0.00),
]
wave = [
    (0.00, 0.00),
    (0.00, 0.55), (0.22, 1.00), (0.50, 1.00),
    (0.78, 1.00), (1.00, 0.55), (1.00, 0.00),
]
hook = [
    (0.00, 0.90),
    (0.00, 0.28), (0.28, 0.00), (0.90, 0.00),
    (1.00, 0.00),
]
corner = [
    (0.00, 1.00),
    (0.00, 0.70),
    (0.00, 0.38), (0.04, 0.14), (0.26, 0.10),
    (0.52, 0.06), (0.68, 0.26), (0.52, 0.46),
    (0.34, 0.66), (0.08, 0.58), (0.10, 0.30),
    (0.12, 0.08), (0.40, 0.02), (0.74, 0.00),
    (1.00, 0.00),
]

def stroke_arch(ox, oy, xx, xy, yx, yy, c):
    cubic(xform(*arch[0], ox, oy, xx, xy, yx, yy),
          xform(*arch[1], ox, oy, xx, xy, yx, yy),
          xform(*arch[2], ox, oy, xx, xy, yx, yy),
          xform(*arch[3], ox, oy, xx, xy, yx, yy), c)
    cubic(xform(*arch[3], ox, oy, xx, xy, yx, yy),
          xform(*arch[4], ox, oy, xx, xy, yx, yy),
          xform(*arch[5], ox, oy, xx, xy, yx, yy),
          xform(*arch[6], ox, oy, xx, xy, yx, yy), c)

def stroke_wave(ox, oy, xx, xy, yx, yy, c):
    cubic(xform(*wave[0], ox, oy, xx, xy, yx, yy),
          xform(*wave[1], ox, oy, xx, xy, yx, yy),
          xform(*wave[2], ox, oy, xx, xy, yx, yy),
          xform(*wave[3], ox, oy, xx, xy, yx, yy), c)
    cubic(xform(*wave[3], ox, oy, xx, xy, yx, yy),
          xform(*wave[4], ox, oy, xx, xy, yx, yy),
          xform(*wave[5], ox, oy, xx, xy, yx, yy),
          xform(*wave[6], ox, oy, xx, xy, yx, yy), c)

def stroke_hook(ox, oy, xx, xy, yx, yy, c):
    cubic(xform(*hook[0], ox, oy, xx, xy, yx, yy),
          xform(*hook[1], ox, oy, xx, xy, yx, yy),
          xform(*hook[2], ox, oy, xx, xy, yx, yy),
          xform(*hook[3], ox, oy, xx, xy, yx, yy), c)
    p = xform(*hook[3], ox, oy, xx, xy, yx, yy)
    q = xform(*hook[4], ox, oy, xx, xy, yx, yy)
    line(p[0], p[1], q[0], q[1], c)

def rect(x, y, w, h, c):
    r, b = x + w - 1, y + h - 1
    line(x, y, r, y, c)
    line(r, y, r, b, c)
    line(r, b, x, b, c)
    line(x, b, x, y, c)

def fill(x0, y0, x1, y1, c):
    for y in range(int(y0), int(y1)):
        for x in range(int(x0), int(x1)):
            setp(x, y, c)

# field
tile, row = U * 4, U * 2
gy = 0
ri = 0
while gy < H:
    xoff = tile / 2 if ri & 1 else 0
    gx = -xoff
    while gx < W:
        baseline = gy + tile / 2
        stroke_wave(gx, baseline, tile, 0, 0, -tile / 2, 1)
        iw = tile * 2 / 3
        stroke_wave(gx + (tile - iw) / 2, baseline, iw, 0, 0, -iw / 2, 1)
        gx += tile
    gy += row
    ri += 1

# small stepper
fill(12, 12, 12 + 20, 12 + 48, 3)
rect(12, 12, 20, 48, 10)
hs = min(U / 4, 20 / 6)
stroke_hook(12, 12, hs, 0, 0, hs, 10)
stroke_hook(12 + 19, 12, -hs, 0, 0, hs, 10)
stroke_hook(12 + 19, 12 + 47, -hs, 0, 0, -hs, 10)
stroke_hook(12, 12 + 47, hs, 0, 0, -hs, 10)

# small forge
fill(40, 16, 40 + 72, 16 + 24, 3)
rect(40, 16, 72, 24, 10)
hs = min(U / 4, 24 / 6)
stroke_hook(40, 16, hs, 0, 0, hs, 10)
stroke_hook(40 + 71, 16, -hs, 0, 0, hs, 10)
stroke_hook(40 + 71, 16 + 23, -hs, 0, 0, -hs, 10)
stroke_hook(40, 16 + 23, hs, 0, 0, -hs, 10)

# panel row
fill(12, 72, 12 + 176, 72 + 40, 3)
rect(12, 72, 176, 40, 10)
cs = min(U, 40 / 5)
for ox, oy, xx, yy in (
    (12, 72, cs, cs),
    (12 + 175, 72, -cs, cs),
    (12 + 175, 72 + 39, -cs, -cs),
    (12, 72 + 39, cs, -cs),
):
    # just the outer hook-like first cubic of corner is enough for preview density
    stroke_hook(ox, oy, xx, 0, 0, yy, 10)

# hud bar
fill(0, 180, W, H, 3)
line(0, 180, W - 1, 180, 10)
stroke_arch(76, 180, 48, 0, 0, -24, 10)

def write_png(path: Path):
    palette = [
        (18, 18, 20), (40, 48, 56), (70, 52, 52), (28, 40, 48),
        (80, 80, 88), (240, 240, 240), (250, 240, 220), (240, 180, 170),
        (90, 90, 90), (180, 40, 50), (230, 180, 40), (180, 50, 40),
        (50, 180, 140), (40, 80, 140), (100, 60, 120), (160, 20, 40),
    ]
    raw = bytearray()
    for y in range(H):
        raw.append(0)
        for x in range(W):
            r, g, b = palette[buf[y][x]]
            raw.extend((r, g, b))
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)

out = Path(__file__).with_name("preview.png")
write_png(out)
print(out)