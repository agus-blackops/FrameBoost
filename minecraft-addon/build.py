#!/usr/bin/env python3
"""Genera las texturas 16x16 y empaqueta el addon en .zip y .mcaddon."""
import zlib, struct, zipfile, os
ROOT = os.path.dirname(os.path.abspath(__file__))

def png(path, px):
    raw = b"".join(b"\x00" + b"".join(bytes(c) for c in row) for row in px)
    def ch(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                           + ch(b"IDAT", zlib.compress(raw)) + ch(b"IEND", b""))

T = (0, 0, 0, 0)
def canvas(): return [[T] * 16 for _ in range(16)]
def line(c, pts, col):
    for x, y in pts: c[y][x] = col
def diag(c, col, w=1):
    for i in range(14):
        for k in range(w): c[15 - i - 1][i + 1 - k if i + 1 - k >= 0 else 0] = col

def mace(): # mango diagonal + cabeza esfera cian
    c = canvas(); diag(c, (110, 80, 40, 255))
    for y in range(1, 8):
        for x in range(8, 15):
            if (x - 11) ** 2 + (y - 4) ** 2 <= 11: c[y][x] = (60, 220, 255, 255)
    c[3][10] = (255, 255, 255, 255); return c
def sword(): # hoja dorada
    c = canvas(); diag(c, (255, 190, 40, 255), 2)
    for i in range(4, 9): c[15 - i][i - 3] = (120, 70, 20, 255)
    return c
def axe():
    c = canvas(); diag(c, (150, 110, 60, 255))
    for y in range(1, 7):
        for x in range(6, 13):
            if x - 6 + (6 - y) <= 7: c[y][x] = (255, 235, 60, 255)
    return c
def heart():
    c = canvas(); r = (255, 60, 150, 255)
    for y in range(16):
        for x in range(16):
            u, v = (x - 7.5) / 6.5, (7 - y) / 6.5
            if (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0: c[y][x] = r
    c[4][4] = (255, 255, 255, 255); return c
def core():
    c = canvas()
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d <= 7: c[y][x] = (170, 60, 255, 255) if d > 3 else (255, 255, 255, 255)
    return c

def orb():
    c = canvas()
    for y in range(16):
        for x in range(16):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if d <= 49: c[y][x] = (80, 255, 140, 255) if d > 20 else (220, 255, 230, 255)
    return c

def antidote(): # frasco verde
    c = canvas()
    for y in range(7, 15):
        for x in range(4, 12): c[y][x] = (60, 200, 80, 255)
    for y in range(3, 7):
        for x in range(6, 10): c[y][x] = (200, 220, 230, 255)
    for x in range(5, 11): c[2][x] = (140, 90, 40, 255)
    c[9][6] = (200, 255, 200, 255); return c

if __name__ == "__main__":
    for n, f in [("aim_mace", mace), ("titan_sword", sword), ("storm_axe", axe), ("divine_heart", heart), ("immortal_core", core), ("restoration_orb", orb), ("antidote", antidote)]:
        png(f"{ROOT}/RP/textures/items/fb_{n}.png", f())
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
