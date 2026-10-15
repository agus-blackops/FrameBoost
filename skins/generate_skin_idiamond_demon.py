#!/usr/bin/env python3
"""
IDiamondDemon - demonio de obsidiana con armadura y cristales de diamante
(Minecraft Java 64x64, brazos clasicos). Reutiliza el motor de generate_skin.py.

Uso:  python3 generate_skin_idiamond_demon.py [carpeta_salida]
"""
import os
import sys
import numpy as np
from PIL import Image

import generate_skin as g
from generate_skin import FACES, put, rect, clear, make_heat, fbm

K0 = (0, 0, 0)
D_OBS = [(3, 10, 12), (6, 20, 24), (10, 34, 40), (16, 54, 60), (24, 78, 84), (36, 110, 112), (62, 160, 158), (120, 225, 215)]
D_RAMP = [(0.14, (255, 255, 255)), (0.30, (200, 255, 250)), (0.55, (74, 237, 217)), (0.85, (44, 186, 168)),
          (1.10, (33, 126, 123)), (1.45, (20, 80, 84)), (1.9, (12, 52, 56)), (2.4, (8, 34, 38))]
DW, DL, DM, DD, DE, DF = (255, 255, 255), (200, 255, 250), (74, 237, 217), (44, 186, 168), (33, 126, 123), (20, 80, 84)
KEY = {"w": DW, "l": DL, "m": DM, "d": DD, "e": DE, "f": DF, "k": K0,
       "n": D_OBS[1], "N": D_OBS[2], "g": D_OBS[4], "G": D_OBS[5], "h": D_OBS[6], "H": D_OBS[7], "c": D_OBS[2], "b": D_OBS[3]}
heat = make_heat(0, 24, 0.05)
heat_head = make_heat(-10, 30, 0.0)


def pat(part, layer, face, x0, y0, rows):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == ".":
                continue
            if ch == " ":
                put(part, layer, face, x0 + i, y0 + j, (0, 0, 0), 0)
            else:
                put(part, layer, face, x0 + i, y0 + j, KEY[ch])


def solid(part, layer, face, seed, tone=0, hf=None):
    g.OBS, g.RAMP = D_OBS, D_RAMP
    P = g.face_points(part, layer, face)
    arr = g.TEX[(part, layer, face)]
    arr[..., :3] = g.surface(P, face, seed, hf or heat, tone)
    arr[..., 3] = 255


def crystals(part, face, cols, base_row, hts, layer=1):
    for n, i in enumerate(cols):
        h = hts[n % len(hts)]
        for k in range(h):
            fr = k / max(h - 1, 1)
            put(part, layer, face, i, base_row - k, DE if fr < .15 and h > 2 else DD if fr < .4 else DM if fr < .75 else DL if fr < 1 else DW)


def build():
    g.new_faces()
    rng = np.random.default_rng(99)
    head(rng); torso(rng)
    for p in ("rarm", "larm", "rleg", "lleg"):
        limb(p)
    # destellos de diamante
    for (part, layer, face), arr in g.TEX.items():
        if layer:
            continue
        h, w = arr.shape[:2]
        for _ in range(max(1, h * w // 90)):
            y, x = rng.integers(0, h), rng.integers(0, w)
            if arr[y, x, 3] and arr[y, x, 1] > 190:
                arr[y, x, :3] = DW


def head(rng):
    for f in FACES:
        solid("head", 0, f, 311, hf=heat_head)
        solid("head", 1, f, 312, tone=-0.4, hf=heat_head)
    rect("head", 0, "front", 1, 2, 7, 8, K0)
    pat("head", 0, "front", 1, 2, [
        "  cc  ",
        "dm  md",      # ojos
        "mw  wm",
        "ff  ff",
        "bkkkkb",
        "wkwwkw",      # colmillos
        "kmkkmk",
    ])
    clear("head", 1, "front", 1, 2, 7, 8)
    for i in range(8):
        put("head", 1, "front", i, 0, D_OBS[7] if i in (0, 7) else D_OBS[6])
        put("head", 1, "front", i, 1, DM if i % 3 == 0 else DE)
    for j in range(2, 8):
        for x in (0, 7):
            put("head", 1, "front", x, j, DM if j in (3, 6) else D_OBS[4 + (j % 2)])
    for f in ("right", "left", "back"):
        clear("head", 1, f, 0, 0, 8, 4)
        crystals("head", f, range(8), 3, [4, 2, 3, 1, 4, 3, 2, 4])
        for i in range(8):
            put("head", 1, f, i, 4, DE if i % 2 else DM)
    # corona de cristal
    for j in range(8):
        for i in range(8):
            v = 1.35 - np.hypot(i - 3.5, j - 3.5) / 3.6 + (rng.random() - 0.5) * 0.4
            col = DW if v > 1.05 else DL if v > 0.85 else DM if v > 0.62 else DD if v > 0.45 else DE if v > 0.3 else DF if v > 0.12 else None
            if col:
                put("head", 1, "top", i, j, col)
    pat("head", 1, "back", 3, 6, ["lm"])
    pat("head", 1, "back", 2, 7, ["edde"])


def torso(rng):
    for f in FACES:
        solid("torso", 0, f, 321)
    pat("torso", 0, "front", 1, 1, [
        " edde ",
        "emllme",
        "dlwwld",
        "dlwwld",
        "emllme",
        " edde ",
    ])
    for i in range(1, 7):
        put("torso", 0, "front", i, 7, D_OBS[6]); put("torso", 0, "front", i, 8, D_OBS[3])
    for f, wd in (("front", 8), ("back", 8), ("right", 4), ("left", 4)):
        for i in range(wd):
            put("torso", 0, f, i, 9, D_OBS[6]); put("torso", 0, f, i, 10, D_OBS[2])
            put("torso", 0, f, i, 11, DF if i % 2 else D_OBS[1])
    pat("torso", 0, "front", 2, 9, ["Hmlh", "Hdmh"])
    for j in range(9):
        put("torso", 0, "back", 3, j, D_OBS[6]); put("torso", 0, "back", 4, j, D_OBS[5])
    for j in (1, 3, 5, 7):
        put("torso", 0, "back", 3, j, DM); put("torso", 0, "back", 4, j, DL)
    for f in ("front", "back"):
        for i in range(8):
            put("torso", 1, f, i, 0, D_OBS[7] if i in (0, 1, 6, 7) else D_OBS[6])
    for x in (0, 7):
        for j in range(1, 8):
            put("torso", 1, "front", x, j, D_OBS[5] if j % 2 else D_OBS[4])
        for j in (2, 5):
            put("torso", 1, "front", x, j, DM)
    for f in ("right", "left"):
        for i in range(4):
            put("torso", 1, f, i, 0, D_OBS[7])
    P = g.face_points("torso", 1, "back")
    g.OBS, g.RAMP = D_OBS, D_RAMP
    cape = g.surface(P, "back", 322, make_heat(0, 22, 0.02), -0.6)
    for j in range(1, 11):
        for i in range(8):
            put("torso", 1, "back", i, j, tuple(int(c) for c in cape[j, i]))
        put("torso", 1, "back", 3, j, DM if j % 2 else DD)
        put("torso", 1, "back", 4, j, DL if j % 2 else DM)
    for i in range(8):
        put("torso", 1, "back", i, 11, DF if i % 2 else DE)
    crystals("torso", "back", range(8), 11, [3, 1, 4, 2, 3, 0, 4, 2])
    for f, wd in (("front", 8), ("right", 4), ("left", 4)):
        for i in range(wd):
            put("torso", 1, f, i, 10, D_OBS[2])
            r = rng.random()
            if r < .55:
                put("torso", 1, f, i, 11, D_OBS[1])
            elif r < .8:
                put("torso", 1, f, i, 11, DM if rng.random() < .4 else DE)


def limb(p):
    side = p[0]
    outer = "right" if side == "r" else "left"
    seed = {"rarm": 331, "larm": 341, "rleg": 351, "lleg": 361}[p]
    hand = [(DE, DD, DM, DE), (DD, DM, DL, DM), (DM, DL, DW, DL)]
    for f in FACES:
        solid(p, 0, f, seed)
    if p.endswith("arm"):
        for f in ("front", "back", "right", "left"):
            for i in range(4):
                put(p, 0, f, i, 7, D_OBS[6]); put(p, 0, f, i, 8, D_OBS[3])
                for r, rowc in zip((9, 10, 11), hand):
                    put(p, 0, f, i, r, rowc[(i + (f in (outer, "back")) + 2 * (f == "front" and r == 10)) % 4])
                put(p, 1, f, i, 0, D_OBS[7]); put(p, 1, f, i, 1, D_OBS[6])
                put(p, 1, f, i, 2, D_OBS[5] if i % 2 else D_OBS[4])
                put(p, 1, f, i, 3, DM if i in (1, 2) else DF)
                put(p, 1, f, i, 6, D_OBS[6]); put(p, 1, f, i, 7, D_OBS[4])
        for i in range(4):
            for j in range(4):
                put(p, 1, "top", i, j, D_OBS[7] if (i + j) % 2 == 0 else D_OBS[6])
        for (x, y) in ((0, 0), (3, 0), (0, 3), (3, 3)):
            put(p, 1, "top", x, y, DM)
        put(p, 1, "top", 1, 1, DW); put(p, 1, "top", 2, 2, DW)
        hts = [4, 5, 2, 5, 3, 1, 5, 4, 5, 2, 3, 5, 4, 2, 5, 3]
    else:
        for f in ("front", "back", "right", "left"):
            for i in range(4):
                put(p, 0, f, i, 0, D_OBS[4])
                put(p, 0, f, i, 4, D_OBS[5]); put(p, 0, f, i, 5, D_OBS[4])
                put(p, 0, f, i, 8, D_OBS[6]); put(p, 0, f, i, 9, D_OBS[3])
                put(p, 0, f, i, 10, hand[0][(i + (f == "front")) % 4])
                put(p, 0, f, i, 11, hand[1][(i + 2 * (f == "back")) % 4])
                put(p, 1, f, i, 0, D_OBS[7]); put(p, 1, f, i, 1, D_OBS[5])
                put(p, 1, f, i, 2, DM if i in (1, 2) else DF)
        put(p, 0, "front", 1, 5, DM); put(p, 0, "front", 2, 5, DW)
        hts = [5, 3, 6, 2, 4, 6, 3, 5, 2, 6, 4, 3, 5, 2, 6, 4]
    rect(p, 0, "bottom", 0, 0, 4, 4, DL)
    rect(p, 0, "bottom", 1, 1, 3, 3, DW)
    if side == "l":
        hts = hts[4:] + hts[:4]
    for k, f in enumerate(("right", "front", "left", "back")):
        crystals(p, f, range(4), 11, hts[k * 4:] + hts[:k * 4])


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out, exist_ok=True)
    build()
    sheet = g.compose()
    skin = Image.fromarray(sheet, "RGBA")
    skin.save(os.path.join(out, "idiamond_demon_skin.png"))
    big = skin.resize((640, 640), Image.NEAREST)
    chk = Image.new("RGBA", (640, 640))
    px = chk.load()
    for y in range(640):
        for x in range(640):
            px[x, y] = (96, 96, 112, 255) if ((x // 10) + (y // 10)) % 2 else (72, 72, 88, 255)
    chk.alpha_composite(big)
    chk.save(os.path.join(out, "idiamond_demon_texture_x10.png"))
    views = [g.render(sheet, yaw=-32), g.render(sheet, yaw=0, pitch=6),
             g.render(sheet, yaw=180, pitch=6), g.render(sheet, yaw=148)]
    sh = Image.new("RGBA", (views[0].width * len(views), views[0].height))
    for i, v in enumerate(views):
        sh.paste(v, (i * v.width, 0))
    sh.save(os.path.join(out, "idiamond_demon_preview.png"))
    print("ok ->", out)


if __name__ == "__main__":
    main()
