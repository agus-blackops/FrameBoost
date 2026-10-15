#!/usr/bin/env python3
"""
Obsidian Demon - LAVA / HIELO / FUEGO (Minecraft Java 64x64, brazos clasicos).
Mitad del personaje (lado derecho del personaje = izquierda de pantalla) en lava y fuego,
la otra mitad en obsidiana helada con cristales de hielo; el nucleo del pecho fusiona ambos.
Reutiliza el motor de texturas de generate_skin.py.

Uso:  python3 generate_skin_lava_ice_fire.py [carpeta_salida]
"""
import os
import sys
import numpy as np
from PIL import Image

import generate_skin as g
from generate_skin import FACES, OBS, DR1, DR2, DR3, O1, O2, O3, Y1, RD, K0, put, rect, clear, make_heat, fbm

WH = (255, 244, 200)
FIRE_OBS = list(OBS)
FIRE_RAMP = [(0.12, WH), (0.28, Y1), (0.52, O1), (0.82, O2), (1.08, O3), (1.45, DR1), (1.9, DR2), (2.4, DR3)]
ICE_OBS = [(4, 7, 18), (8, 14, 32), (12, 24, 50), (18, 36, 70), (26, 54, 100), (40, 80, 140), (70, 120, 180), (120, 170, 225)]
ICE_RAMP = [(0.12, (255, 255, 255)), (0.28, (200, 245, 255)), (0.52, (120, 215, 250)), (0.82, (50, 160, 235)),
            (1.08, (30, 100, 200)), (1.45, (20, 60, 140)), (1.9, (14, 40, 100)), (2.4, (10, 26, 70))]
IC = dict(w=(255, 255, 255), i=(200, 245, 255), I=(120, 215, 250), j=(50, 160, 235), J=(30, 100, 200), z=(20, 60, 140))
KEY = {"Y": Y1, "1": O1, "2": O2, "3": O3, "R": RD, "a": DR1, "b": DR2, "c": DR3, "k": K0, "W": WH,
       "n": OBS[1], "N": OBS[2], "g": OBS[4], "G": OBS[5], "h": OBS[6], "H": OBS[7], **IC}

heat_fire = make_heat(2, 24, 0.06)
heat_ice = make_heat(-10, 40, 0.2)


def pat(part, layer, face, x0, y0, rows):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == ".":
                continue
            put(part, layer, face, x0 + i, y0 + j, (0, 0, 0), 0) if ch == " " else \
                put(part, layer, face, x0 + i, y0 + j, KEY[ch])


def set_pal(kind):
    g.OBS, g.RAMP = (ICE_OBS, ICE_RAMP) if kind == "ice" else (FIRE_OBS, FIRE_RAMP)


def surf(P, face, seed, kind, tone):
    set_pal(kind)
    return g.surface(P, face, seed, heat_ice if kind == "ice" else heat_fire, tone)


def solid(part, layer, face, seed, kind, tone=0):
    P = g.face_points(part, layer, face)
    arr = g.TEX[(part, layer, face)]
    arr[..., :3] = surf(P, face, seed, kind, tone)
    arr[..., 3] = 255


def dual(part, layer, face, seed, tone=0):
    """Fuego a un lado (X<0, izquierda de pantalla), hielo al otro, con frontera irregular."""
    P = g.face_points(part, layer, face)
    fire = surf(P, face, seed, "fire", tone)
    ice = surf(P, face, seed + 500, "ice", tone)
    xw = P[..., 0] + (fbm(P, 0.3, seed + 200) - 0.5) * 3.2
    arr = g.TEX[(part, layer, face)]
    arr[..., :3] = np.where((xw < 0)[..., None], fire, ice)
    arr[..., 3] = 255


def spikes(part, face, cols, base_row, hts, kind, layer=1):
    for n, i in enumerate(cols):
        h = hts[n % len(hts)]
        for k in range(h):
            fr = k / max(h - 1, 1)
            if kind == "fire":
                col = (WH if (k == 0 and h >= 5) else Y1) if fr < .2 else O1 if fr < .45 else O2 if fr < .7 else O3 if fr < .9 else RD
            else:
                col = IC["j"] if fr < .25 else IC["I"] if fr < .5 else IC["i"] if fr < .85 else IC["w"]
            put(part, layer, face, i, base_row - k, col)


def ring(part, faces_kind, base_row, hts, layer=1, ncols=4):
    """faces_kind: lista de (cara, tipo, columnas)."""
    for f, kind, cols in faces_kind:
        spikes(part, f, cols, base_row, hts, kind, layer)


def build():
    g.new_faces()
    rng = np.random.default_rng(777)
    head(rng)
    torso(rng)
    limb("rarm", "fire"); limb("rleg", "fire")
    limb("larm", "ice"); limb("lleg", "ice")


H4 = [4, 2, 3, 1, 4, 3, 2, 4, 1, 3, 4, 2]


def head(rng):
    for f in FACES:
        dual("head", 0, f, 211)
        dual("head", 1, f, 212, tone=-0.2)
    rect("head", 0, "front", 1, 2, 7, 8, K0)
    pat("head", 0, "front", 1, 2, [
        "  cc  ",
        "3Y  Ij",     # ojos (fila 1): fuego | hielo
        "1W  WI",
        "cc  zz",
        "bkkkkJ",
        "WkWWkW",     # colmillos
        "k1kkIk",     # brillo bajo los colmillos
    ])
    pat("head", 0, "front", 2, 2, ["  "])
    # marco de capucha
    clear("head", 1, "front", 1, 2, 7, 8)
    for i in range(8):
        fire = i < 4
        put("head", 1, "front", i, 0, OBS[7] if fire else ICE_OBS[7])
        put("head", 1, "front", i, 1, (O3 if i % 3 == 0 else DR1) if fire else (IC["I"] if i % 3 == 1 else IC["J"]))
    for j in range(2, 8):
        put("head", 1, "front", 0, j, O3 if j in (3, 6) else OBS[3 + (j % 2)])
        put("head", 1, "front", 7, j, IC["I"] if j in (3, 6) else ICE_OBS[4 + (j % 2)])
    # llamas (lado fuego) y cristales (lado hielo) en el borde de la capucha
    for f in ("right", "left", "back"):
        clear("head", 1, f, 0, 0, 8, 4)
    ring("head", [("right", "fire", range(8)), ("left", "ice", range(8)),
                  ("back", "ice", range(0, 4)), ("back", "fire", range(4, 8))], 3, H4)
    for f, fire_cols in (("right", range(8)), ("left", ()), ("back", range(4, 8))):
        for i in range(8):
            put("head", 1, f, i, 4, (DR1 if i % 2 else O3) if i in fire_cols else (IC["J"] if i % 2 else IC["z"]))
    # corona: fuego a la izquierda de pantalla, hielo a la derecha
    for j in range(8):
        for i in range(8):
            r = np.hypot(i - 3.5, j - 3.5)
            v = 1.35 - r / 3.6 + (rng.random() - 0.5) * 0.4
            if i < 4:
                col = WH if v > 1.05 else Y1 if v > 0.85 else O1 if v > 0.62 else O2 if v > 0.45 else O3 if v > 0.3 else RD if v > 0.18 else DR1 if v > 0.08 else None
            else:
                col = IC["w"] if v > 1.05 else IC["i"] if v > 0.85 else IC["I"] if v > 0.62 else IC["j"] if v > 0.45 else IC["J"] if v > 0.3 else IC["z"] if v > 0.12 else None
            if col:
                put("head", 1, "top", i, j, col)
    pat("head", 1, "back", 2, 6, ["IjY3"])
    pat("head", 1, "back", 2, 7, ["zJ2a"])


def torso(rng):
    for f in FACES:
        dual("torso", 0, f, 221)
    # nucleo fusionado: fuego (izq.) | hielo (der.), centro blanco
    pat("torso", 0, "front", 1, 1, [
        " a33Jz ",
        "a3YwiIj",
        "3YWwiIj",
        "3YWwiIJ",
        "a3YiIjz",
        " a33JJ ",
    ][0:0] or [
        " a3Jz ",
        "a3YIjz",
        "3YWwIj",
        "3YWwIj",
        "a3YIjz",
        " a3Jz ",
    ])
    for i in range(1, 7):
        put("torso", 0, "front", i, 7, OBS[6] if i < 4 else ICE_OBS[6])
        put("torso", 0, "front", i, 8, OBS[3] if i < 4 else ICE_OBS[3])
    for f, wd in (("front", 8), ("back", 8), ("right", 4), ("left", 4)):
        for i in range(wd):
            fire = (f == "right") or (f == "front" and i < 4) or (f == "back" and i >= 4)
            put("torso", 0, f, i, 9, OBS[6] if fire else ICE_OBS[6])
            put("torso", 0, f, i, 10, OBS[2] if fire else ICE_OBS[2])
            put("torso", 0, f, i, 11, (DR2 if i % 2 else DR3) if fire else (IC["z"] if i % 2 else (14, 40, 100)))
    pat("torso", 0, "front", 2, 9, ["H1iH", "H2IH"][:0] or ["H3IH", "H1jH"])
    for j in range(9):
        put("torso", 0, "back", 3, j, ICE_OBS[6]); put("torso", 0, "back", 4, j, OBS[6])
    for j in (1, 3, 5, 7):
        put("torso", 0, "back", 3, j, IC["I"]); put("torso", 0, "back", 4, j, O2)
    # overlay: collar y correas
    for f in ("front", "back"):
        for i in range(8):
            put("torso", 1, f, i, 0, OBS[7] if i in (0, 1, 6, 7) else OBS[6])
    for j in range(1, 8):
        put("torso", 1, "front", 0, j, OBS[5] if j % 2 else OBS[4])
        put("torso", 1, "front", 7, j, ICE_OBS[6] if j % 2 else ICE_OBS[5])
    for j in (2, 5):
        put("torso", 1, "front", 0, j, O2); put("torso", 1, "front", 7, j, IC["I"])
    for i in range(4):
        put("torso", 1, "right", i, 0, OBS[7]); put("torso", 1, "left", i, 0, ICE_OBS[7])
    # capa en la espalda (dual) con llamas/cristales abajo
    P = g.face_points("torso", 1, "back")
    fire = surf(P, "back", 222, "fire", -0.6)
    ice = surf(P, "back", 722, "ice", -0.6)
    for j in range(1, 11):
        for i in range(8):
            put("torso", 1, "back", i, j, tuple(int(c) for c in (ice if i < 4 else fire)[j, i]))
    for j in range(1, 11):
        put("torso", 1, "back", 3, j, IC["I"] if j % 2 else IC["i"])
        put("torso", 1, "back", 4, j, O3 if j % 2 else O2)
    for i in range(8):
        put("torso", 1, "back", i, 11, (IC["z"] if i % 2 else IC["J"]) if i < 4 else (DR1 if i % 2 else DR3))
    ring("torso", [("back", "ice", range(0, 4)), ("back", "fire", range(4, 8))], 11, [3, 1, 4, 2, 3, 0, 4, 2])
    # faldon lateral/frontal
    for f, wd in (("front", 8), ("right", 4), ("left", 4)):
        for i in range(wd):
            put("torso", 1, f, i, 10, OBS[2] if (f == "right" or (f == "front" and i < 4)) else ICE_OBS[2])
            r = rng.random()
            fire = (f == "right") or (f == "front" and i < 4)
            if r < .55:
                put("torso", 1, f, i, 11, OBS[1] if fire else ICE_OBS[1])
            elif r < .8:
                put("torso", 1, f, i, 11, O3 if fire else IC["I"])


def limb(p, kind):
    ice = kind == "ice"
    side = p[0]
    outer = "right" if side == "r" else "left"
    seed = {"rarm": 231, "larm": 241, "rleg": 251, "lleg": 261}[p]
    pal = ICE_OBS if ice else OBS
    hand = [(IC["J"], IC["j"], IC["I"], IC["J"]), (IC["j"], IC["I"], IC["i"], IC["I"]), (IC["I"], IC["i"], IC["w"], IC["i"])] if ice else \
        [(DR1, O3, O2, DR1), (O2, O1, Y1, O2), (O1, Y1, WH, Y1)]
    for f in FACES:
        solid(p, 0, f, seed, kind)
    if p.endswith("arm"):
        rows = (9, 10, 11)
        for f in ("front", "back", "right", "left"):
            for i in range(4):
                put(p, 0, f, i, 7, pal[6]); put(p, 0, f, i, 8, pal[3])
                for r, rowc in zip(rows, hand):
                    put(p, 0, f, i, r, rowc[(i + (f in (outer, "back")) + 2 * (f == "front" and r == 10)) % 4])
        for f in ("front", "back", "right", "left"):
            for i in range(4):
                put(p, 1, f, i, 0, pal[7]); put(p, 1, f, i, 1, pal[6])
                put(p, 1, f, i, 2, pal[5] if i % 2 else pal[4])
                put(p, 1, f, i, 3, (IC["I"] if i in (1, 2) else ICE_OBS[4]) if ice else (O3 if i in (1, 2) else DR1))
                put(p, 1, f, i, 6, pal[6]); put(p, 1, f, i, 7, pal[4])
        for i in range(4):
            for j in range(4):
                put(p, 1, "top", i, j, pal[7] if (i + j) % 2 == 0 else pal[6])
        for (x, y) in ((0, 0), (3, 0), (0, 3), (3, 3)):
            put(p, 1, "top", x, y, IC["I"] if ice else O2)
        put(p, 1, "top", 1, 1, IC["w"] if ice else Y1); put(p, 1, "top", 2, 2, IC["w"] if ice else Y1)
        hts = [4, 5, 2, 5, 3, 1, 5, 4, 5, 2, 3, 5, 4, 2, 5, 3]
        base = 11
    else:
        for f in ("front", "back", "right", "left"):
            for i in range(4):
                put(p, 0, f, i, 0, pal[4])
                put(p, 0, f, i, 4, pal[5]); put(p, 0, f, i, 5, pal[4])
                put(p, 0, f, i, 8, pal[6]); put(p, 0, f, i, 9, pal[3])
                put(p, 0, f, i, 10, hand[0][(i + (f == "front")) % 4])
                put(p, 0, f, i, 11, hand[1][(i + 2 * (f == "back")) % 4])
                put(p, 1, f, i, 0, pal[7]); put(p, 1, f, i, 1, pal[5])
                put(p, 1, f, i, 2, (IC["I"] if i in (1, 2) else ICE_OBS[4]) if ice else (O3 if i in (1, 2) else DR1))
        put(p, 0, "front", 1, 5, IC["I"] if ice else O2); put(p, 0, "front", 2, 5, IC["w"] if ice else Y1)
        hts = [5, 3, 6, 2, 4, 6, 3, 5, 2, 6, 4, 3, 5, 2, 6, 4]
        base = 11
    rect(p, 0, "bottom", 0, 0, 4, 4, IC["i"] if ice else Y1)
    rect(p, 0, "bottom", 1, 1, 3, 3, IC["w"] if ice else WH)
    if side == "l":
        hts = hts[4:] + hts[:4]
    for f in ("right", "front", "left", "back"):
        spikes(p, f, range(4), base, hts[{"right": 0, "front": 4, "left": 8, "back": 12}[f]:] + hts[:4], kind)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out, exist_ok=True)
    build()
    sheet = g.compose()
    skin = Image.fromarray(sheet, "RGBA")
    skin.save(os.path.join(out, "obsidian_demon_lava_ice_fire_skin.png"))
    big = skin.resize((640, 640), Image.NEAREST)
    chk = Image.new("RGBA", (640, 640))
    px = chk.load()
    for y in range(640):
        for x in range(640):
            px[x, y] = (96, 96, 112, 255) if ((x // 10) + (y // 10)) % 2 else (72, 72, 88, 255)
    chk.alpha_composite(big)
    chk.save(os.path.join(out, "obsidian_demon_lava_ice_fire_texture_x10.png"))
    views = [g.render(sheet, yaw=-32), g.render(sheet, yaw=0, pitch=6),
             g.render(sheet, yaw=180, pitch=6), g.render(sheet, yaw=148)]
    sh = Image.new("RGBA", (views[0].width * len(views), views[0].height))
    for i, v in enumerate(views):
        sh.paste(v, (i * v.width, 0))
    sh.save(os.path.join(out, "obsidian_demon_lava_ice_fire_preview.png"))
    print("ok ->", out)


if __name__ == "__main__":
    main()
