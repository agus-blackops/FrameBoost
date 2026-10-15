#!/usr/bin/env python3
"""
Obsidian Demon - FASE 2 ("Senor del Infierno"), Minecraft Java 64x64, brazos clasicos.
La capucha se quema, la armadura se parte y el nucleo de lava se desborda.
Reutiliza el motor de texturas de generate_skin.py.

Uso:  python3 generate_skin_phase2.py [carpeta_salida]
"""
import os
import sys
import numpy as np
from PIL import Image

import generate_skin as g
from generate_skin import (FACES, OBS, DR1, DR2, DR3, DR4, O1, O2, O3, Y1, RD, K0,
                           procedural, put, rect, clear, pattern, make_heat, fbm)

WH = (255, 244, 200)   # blanco incandescente (nuevo en fase 2)


def flames2(part, faces, base_row, hts, layer=1):
    """Llamas altas: blanco-amarillo en la base, rojo en la punta."""
    n = 0
    for f in faces:
        for i in range(g.face_rect(part, layer, f)[2]):
            h = hts[n % len(hts)]
            n += 1
            for k in range(h):
                fr = k / max(h - 1, 1)
                col = (WH if (k == 0 and h >= 5) else Y1) if fr < .2 else O1 if fr < .45 else \
                    O2 if fr < .7 else O3 if fr < .9 else RD
                put(part, layer, f, i, base_row - k, col)


def heat_head2(P, seed):
    return np.clip(0.05 + 0.7 * (fbm(P, 0.22, seed + 70, 2) - 0.5), 0, 1)


def build():
    g.new_faces()
    g.RAMP = [(0.12, WH), (0.28, Y1), (0.52, O1), (0.82, O2), (1.08, O3), (1.45, DR1), (1.9, DR2), (2.4, DR3)]
    rng = np.random.default_rng(2024)
    head(rng)
    torso(rng)
    for side in "rl":
        arm(side)
        leg(side)


def head(rng):
    for f in FACES:
        procedural("head", 0, f, 111, heat_head2)
        procedural("head", 1, f, 112, heat_head2, tone=-0.2)
    # rostro: vacio con ojos de rojo blanco y colmillos
    rect("head", 0, "front", 1, 2, 7, 8, K0)
    pattern("head", 0, "front", 1, 2, [
        "  cc  ",
        "32  23",     # ojos (fila 1)
        "1W  W1",     # ojos (fila 2)
        "cc  cc",
        "bkkkkb",
        "WkWWkW",     # colmillos
    ])
    # grieta en la frente
    # capucha: marco con borde de lava
    clear("head", 1, "front", 1, 2, 7, 8)
    for i in range(8):
        put("head", 1, "front", i, 0, OBS[7] if i in (0, 7) else OBS[6])
        put("head", 1, "front", i, 1, O3 if i % 3 == 0 else DR1)
    for j in range(2, 8):
        for x in (0, 7):
            put("head", 1, "front", x, j, O3 if j in (3, 6) else OBS[3 + (j % 2)])
    # llamas altas en lados y nuca
    for f in ("right", "left", "back"):
        clear("head", 1, f, 0, 0, 8, 4)
    flames2("head", ["right", "back", "left"], 3,
            [4, 2, 3, 1, 4, 3, 2, 4, 1, 3, 4, 2, 3, 1, 4, 2, 3, 4, 1, 3, 4, 2, 3, 1])
    for f in ("right", "left", "back"):
        for i in range(8):
            put("head", 1, f, i, 4, DR1 if i % 2 else O3)
    # corona de fuego (arriba)
    for j in range(8):
        for i in range(8):
            r = np.hypot(i - 3.5, j - 3.5)
            v = 1.35 - r / 3.6 + (rng.random() - 0.5) * 0.4
            col = WH if v > 1.05 else Y1 if v > 0.85 else O1 if v > 0.62 else O2 if v > 0.45 else \
                O3 if v > 0.3 else RD if v > 0.18 else DR1 if v > 0.08 else None
            if col:
                put("head", 1, "top", i, j, col)
    # runa de fuego en la nuca
    pattern("head", 1, "back", 3, 6, ["WY"])
    pattern("head", 1, "back", 2, 7, ["a31a"])


def torso(rng):
    heat = make_heat(2, 24, 0.06)
    for f in FACES:
        procedural("torso", 0, f, 121, heat)
    # nucleo desbordado (6x6)
    pattern("torso", 0, "front", 1, 1, [
        " a33a ",
        "a3YY3a",
        "3YWWY3",
        "3YWWY3",
        "a3YY3a",
        " a33a ",
    ])
    for i in range(1, 7):
        put("torso", 0, "front", i, 7, OBS[6]); put("torso", 0, "front", i, 8, OBS[3])
    for i in (3, 4):
        put("torso", 0, "front", i, 7, O2)
    for f, wd in (("front", 8), ("back", 8), ("right", 4), ("left", 4)):
        for i in range(wd):
            put("torso", 0, f, i, 9, OBS[6]); put("torso", 0, f, i, 10, OBS[2])
            put("torso", 0, f, i, 11, DR2 if i % 2 else DR3)
    pattern("torso", 0, "front", 2, 9, ["HW1H", "H1YH"])
    for j in range(0, 9):
        put("torso", 0, "back", 3, j, OBS[6]); put("torso", 0, "back", 4, j, OBS[5])
    for j in (1, 3, 5, 7):
        put("torso", 0, "back", 3, j, O2); put("torso", 0, "back", 4, j, Y1)
    # overlay: collar de pinchos
    for i in range(8):
        put("torso", 1, "front", i, 0, OBS[7] if i in (0, 1, 6, 7) else OBS[6])
        put("torso", 1, "back", i, 0, OBS[7] if i in (0, 1, 6, 7) else OBS[6])
    for x in (0, 7):
        for j in range(1, 8):
            put("torso", 1, "front", x, j, OBS[5] if j % 2 else OBS[4])
        for j in (2, 5):
            put("torso", 1, "front", x, j, O2)
    for f in ("right", "left"):
        for i in range(4):
            put("torso", 1, f, i, 0, OBS[7])
    # capa quemada en la espalda
    P = g.face_points("torso", 1, "back")
    rgb = g.surface(P, "back", 122, make_heat(0, 22, 0.02), tone=-0.6)
    for j in range(1, 11):
        for i in range(8):
            put("torso", 1, "back", i, j, tuple(int(c) for c in rgb[j, i]))
    for j in range(1, 11):
        put("torso", 1, "back", 3, j, O3 if j % 2 else O2)
        put("torso", 1, "back", 4, j, O2 if j % 2 else Y1)
    for i in range(8):
        put("torso", 1, "back", i, 11, DR1 if i % 2 else DR3)
    flames2("torso", ["back"], 11, [3, 1, 4, 2, 3, 0, 4, 2], layer=1)
    # faldon lateral/frontal quemado
    for f, wd in (("front", 8), ("right", 4), ("left", 4)):
        for i in range(wd):
            put("torso", 1, f, i, 10, OBS[2])
            r = rng.random()
            if r < .55:
                put("torso", 1, f, i, 11, DR1 if rng.random() < .5 else OBS[1])
            elif r < .8:
                put("torso", 1, f, i, 11, O3 if rng.random() < .5 else DR2)


def arm(side):
    p = side + "arm"
    outer, inner = ("right", "left") if side == "r" else ("left", "right")
    procedural(p, 0, "front", 131 + (side == "l") * 10, make_heat(6, 24, 0.07))
    for f in FACES:
        procedural(p, 0, f, 131 + (side == "l") * 10, make_heat(6, 24, 0.07))
    for f in ("front", "back", "right", "left"):
        for i in range(4):
            put(p, 0, f, i, 7, OBS[6]); put(p, 0, f, i, 8, OBS[3])
            put(p, 0, f, i, 9, [DR1, O3, O2, DR1][(i + (f in (outer, "back"))) % 4])
            put(p, 0, f, i, 10, [O2, O1, Y1, O2][(i + 2 * (f == "front")) % 4])
            put(p, 0, f, i, 11, [O1, Y1, WH, Y1][(i + (f == "back")) % 4])
    rect(p, 0, "bottom", 0, 0, 4, 4, Y1)
    pattern(p, 0, "bottom", 1, 1, ["WW", "WW"])
    # hombrera con pinchos
    for f in ("right", "front", "left", "back"):
        for i in range(4):
            put(p, 1, f, i, 0, OBS[7]); put(p, 1, f, i, 1, OBS[6])
            put(p, 1, f, i, 2, OBS[5] if i % 2 else OBS[4])
            put(p, 1, f, i, 3, O3 if i in (1, 2) else DR1)
    for i in range(4):
        for j in range(4):
            put(p, 1, "top", i, j, OBS[7] if (i + j) % 2 == 0 else OBS[6])
    for (x, y) in ((0, 0), (3, 0), (0, 3), (3, 3)):
        put(p, 1, "top", x, y, O2)
    put(p, 1, "top", 1, 1, Y1); put(p, 1, "top", 2, 2, Y1)
    # brazalete + llamas desde las manos
    for f in ("front", "back", "right", "left"):
        for i in range(4):
            put(p, 1, f, i, 6, OBS[6]); put(p, 1, f, i, 7, OBS[4])
    flames2(p, ["right", "front", "left", "back"], 11,
            [4, 5, 2, 5, 3, 1, 5, 4, 5, 2, 3, 5, 4, 2, 5, 3] if side == "r" else [3, 5, 4, 2, 5, 4, 1, 5, 3, 5, 2, 4, 5, 3, 2, 5])


def leg(side):
    p = side + "leg"
    seed = 141 + (side == "l") * 10
    heat = make_heat(0, 11, 0.0)
    for f in FACES:
        procedural(p, 0, f, seed, heat)
    for f in ("front", "back", "right", "left"):
        for i in range(4):
            put(p, 0, f, i, 0, OBS[4])
            put(p, 0, f, i, 4, OBS[5]); put(p, 0, f, i, 5, OBS[4])
            put(p, 0, f, i, 8, OBS[6]); put(p, 0, f, i, 9, OBS[3])
            put(p, 0, f, i, 10, [DR2, O3, O2, DR1][(i + (f == "front")) % 4])
            put(p, 0, f, i, 11, [O2, O1, Y1, O1][(i + 2 * (f == "back")) % 4])
    pattern(p, 0, "front", 1, 5, ["2W", "a3"][:1])
    put(p, 0, "front", 1, 5, O2); put(p, 0, "front", 2, 5, Y1)
    rect(p, 0, "bottom", 0, 0, 4, 4, Y1)
    pattern(p, 0, "bottom", 1, 1, ["WW", "WW"])
    for f in ("front", "back", "right", "left"):
        for i in range(4):
            put(p, 1, f, i, 0, OBS[7]); put(p, 1, f, i, 1, OBS[5])
            put(p, 1, f, i, 2, O3 if i in (1, 2) else DR1)
    flames2(p, ["right", "front", "left", "back"], 11,
            [5, 3, 6, 2, 4, 6, 3, 5, 2, 6, 4, 3, 5, 2, 6, 4] if side == "r" else [4, 6, 3, 5, 2, 6, 4, 3, 6, 2, 5, 4, 3, 6, 2, 5])


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out, exist_ok=True)
    build()
    sheet = g.compose()
    skin = Image.fromarray(sheet, "RGBA")
    skin.save(os.path.join(out, "obsidian_demon_phase2_skin.png"))
    big = skin.resize((640, 640), Image.NEAREST)
    chk = Image.new("RGBA", (640, 640))
    px = chk.load()
    for y in range(640):
        for x in range(640):
            px[x, y] = (96, 96, 112, 255) if ((x // 10) + (y // 10)) % 2 else (72, 72, 88, 255)
    chk.alpha_composite(big)
    chk.save(os.path.join(out, "obsidian_demon_phase2_texture_x10.png"))
    views = [g.render(sheet, yaw=-32), g.render(sheet, yaw=0, pitch=6),
             g.render(sheet, yaw=180, pitch=6), g.render(sheet, yaw=148)]
    sh = Image.new("RGBA", (views[0].width * len(views), views[0].height))
    for i, v in enumerate(views):
        sh.paste(v, (i * v.width, 0))
    sh.save(os.path.join(out, "obsidian_demon_phase2_preview.png"))
    print("ok ->", out)


if __name__ == "__main__":
    main()
