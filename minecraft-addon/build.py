#!/usr/bin/env python3
"""Genera las texturas 16x16 y empaqueta el addon en .zip y .mcaddon."""
import zlib, struct, zipfile, os
ROOT = os.path.dirname(os.path.abspath(__file__))

# ───────── texturas 32x32 con sombreado y contorno ─────────
import math
N = 32
OUT = (22, 14, 30, 255)
def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)
def render(f):
    px = [[f(x + .5, y + .5) for x in range(N)] for y in range(N)]
    res = [row[:] for row in px]
    for y in range(N):
        for x in range(N):
            if px[y][x] is None and any(0 <= x + dx < N and 0 <= y + dy < N and px[y + dy][x + dx]
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                res[y][x] = OUT
    return [[c or (0, 0, 0, 0) for c in row] for row in res]
def axes(x, y):  # a = perpendicular a la diagonal, b = a lo largo (0 abajo-izq .. 44 arriba-der)
    return (x - (N - y)) / math.sqrt(2), (x + (N - y)) / math.sqrt(2)

def sword(x, y):
    a, b = axes(x, y)
    if 15.5 <= b <= 43:
        w = 3.4 if b < 37 else 3.4 * (43 - b) / 6
        if abs(a) <= w:
            if abs(a) < .7: return (250, 255, 255, 255)           # filo central
            return mix((255, 215, 70), (190, 120, 20), min(1, max(0, (a + w) / (2 * w) + (b - 15) * .004)))
    if 12.5 <= b < 15.5 and abs(a) <= 8:                           # guarda
        return mix((235, 170, 40), (140, 80, 15), (a + 8) / 16)
    if math.hypot(a, b - 14) < 1.7: return (255, 60, 70, 255) if a < .3 else (190, 20, 40, 255)  # gema
    if 6 <= b < 12.5 and abs(a) <= 1.8:
        return (95, 55, 25, 255) if int(b) % 3 else (60, 35, 15, 255)  # empuñadura
    if math.hypot(a, b - 5) <= 2.2: return mix((255, 220, 90), (160, 100, 20), (a + 3) / 6)
def axe(x, y):
    a, b = axes(x, y)
    if 4 <= b <= 38 and abs(a) <= 1.6: return (130, 85, 40, 255) if a < .2 else (90, 55, 25, 255)
    if 26 <= b <= 40 and 1.2 <= a <= 11 - (b - 33) ** 2 * .22:
        if abs(a - 9.5 + (b - 33) ** 2 * .22) < .9: return (255, 255, 235, 255)  # filo
        return mix((255, 235, 80), (210, 150, 20), (b - 26) / 14)
    if 26 <= b <= 40 and -2.5 <= a < -1.2 and b < 36: return (170, 170, 185, 255)
def heart(x, y):
    u, v = (x - 16) / 12.5, (15 - y) / 12.5
    if (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0:
        if math.hypot(x - 10.5, y - 9.5) < 2.3: return (255, 255, 255, 255)
        if math.hypot(x - 13.5, y - 8) < 1.2: return (255, 220, 235, 255)
        return mix((255, 120, 190), (140, 10, 80), min(1, max(0, (y - 5) / 22 + (x - 10) / 60)))
def core(x, y):
    d = abs(x - 16) / 11 + abs(y - 16) / 14.5
    if d <= 1:
        if d < .32: return mix((255, 255, 255), (230, 190, 255), d / .32)
        if y < 16 and abs(x - 16) < (16 - y) * .6: return mix((215, 140, 255), (170, 80, 240), d)
        return mix((150, 60, 230), (60, 15, 130), d) if x >= 16 else mix((120, 40, 200), (45, 10, 100), d)
def orb(x, y):
    r = math.hypot(x - 16, y - 16)
    if r <= 12.5:
        if math.hypot(x - 11.5, y - 11) < 2.6: return (255, 255, 255, 255)
        ang = math.atan2(y - 16, x - 16) + r * .35
        if 5.5 < r < 8 and math.sin(ang * 2) > .55: return (200, 255, 215, 255)  # remolino
        return mix((150, 255, 190), (10, 120, 70), r / 12.5 * .9 + (y - 16) / 80)
def bottle(x, y):
    if 3 <= y < 6 and 12 <= x <= 20: return (150, 100, 50, 255)                      # corcho
    if 6 <= y < 11 and 13.5 <= x <= 18.5: return (200, 230, 240, 255) if x < 16 else (160, 195, 215, 255)  # cuello
    if math.hypot(x - 16, y - 20) <= 10.5 and y >= 10:
        if math.hypot(x - 11.5, y - 17) < 1.8: return (255, 255, 255, 255)
        if y > 17 + math.sin(x * .8) * .8: return mix((120, 255, 110), (20, 150, 50), (y - 17) / 13)  # líquido
        if math.hypot(x - 19, y - 22) < 1.2: return (210, 255, 200, 255)
        return (205, 232, 242, 255)

TEXTURAS = {"titan_sword": sword, "storm_axe": axe, "divine_heart": heart,
            "immortal_core": core, "restoration_orb": orb, "antidote": bottle}

def png(path, px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + b"".join(bytes(c) for c in row) for row in px)
    def ch(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                           + ch(b"IDAT", zlib.compress(raw)) + ch(b"IEND", b""))

if __name__ == "__main__":
    for n, f in TEXTURAS.items():
        png(f"{ROOT}/RP/textures/items/fb_{n}.png", render(f))
    out = os.path.join(ROOT, "dist"); os.makedirs(out, exist_ok=True)
    mc = f"{out}/PoderSupremo.mcaddon"
    with zipfile.ZipFile(mc, "w", zipfile.ZIP_DEFLATED) as z:
        for pack in ("BP", "RP"):
            for d, _, fs in os.walk(f"{ROOT}/{pack}"):
                for f in fs:
                    p = os.path.join(d, f); z.write(p, os.path.relpath(p, ROOT))
    # el .zip contiene el .mcaddon (+ README)
    with zipfile.ZipFile(f"{out}/PoderSupremo.zip", "w", zipfile.ZIP_DEFLATED) as z:
        z.write(mc, "PoderSupremo.mcaddon")
        z.write(f"{ROOT}/README.md", "README.md")
    print("OK ->", out)
