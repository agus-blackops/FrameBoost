#!/usr/bin/env python3
"""
Obsidian Demon - FASE 2 REMASTER (Minecraft Java / Bedrock 64x64, brazos clasicos).
Mejoras sobre la Fase 2: placas de armadura con borde y luz, cuernos, rostro mas
expresivo, facetas de obsidiana mas grandes y limpias, llamas controladas (sin
bandas planas) y lava concentrada en nucleo, manos y suelas.

Uso:  python3 generate_skin_phase2_remaster.py [carpeta_salida]
"""
import os
import sys
import numpy as np
from PIL import Image

import generate_skin as g
from generate_skin import FACES, OBS, DR1, DR2, DR3, DR4, O1, O2, O3, Y1, RD, K0, put, rect, clear, pattern, make_heat, fbm

WH = (255, 244, 200)
RAMP = [(0.10, WH), (0.24, Y1), (0.46, O1), (0.74, O2), (1.0, O3), (1.3, DR1), (1.7, DR2), (2.1, DR3)]
OUTL, SIDE = OBS[0], 4

heat_head = lambda P, s: np.clip(0.5 * (fbm(P, 0.22, s + 70, 2) - 0.5), 0, 1)
heat_torso = make_heat(2, 24, 0.04)
heat_arm = make_heat(6, 24, 0.04)
heat_leg = make_heat(0, 12, 0.0)


def surf(part, layer, face, seed, heat, tone=0):
    g.FACET, g.CRACK = 0.17, 0.15
    procedural(part, layer, face, seed, heat, tone)


def procedural(part, layer, face, seed, heat, tone=0):
    g.procedural(part, layer, face, seed, heat, tone)


def plate(part, layer, face, x0, y0, x1, y1, base=OBS[4], hi=OBS[7], mid=OBS[6], lo=OBS[2], ol=OBS[1]):
    """Placa con luz arriba-izquierda, sombra abajo-derecha y borde inferior oscuro."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            if y == y1 - 1:
                c = ol
            elif y == y0:
                c = hi
            elif x == x0:
                c = mid
            elif x == x1 - 1 or y == y1 - 2:
                c = lo
            else:
                c = base
            put(part, layer, face, x, y, c)


def flames(part, faces, base_row, hts, layer=1, cols=4):
    """Llamas con base roja->amarilla->punta roja; alturas 0 dejan huecos."""
    n = 0
    for f in faces:
        for i in range(g.face_rect(part, layer, f)[2]):
            h = hts[n % len(hts)]
            n += 1
            for k in range(h):
                fr = k / max(h - 1, 1)
                col = (O1 if h < 4 else Y1) if fr < .25 else O1 if fr < .5 else O2 if fr < .78 else O3
                if k == h - 1 and h >= 3:
                    col = RD if (i + k) % 2 else O3
                put(part, layer, f, i, base_row - k, col)


def build():
    g.new_faces()
    g.RAMP = RAMP
    rng = np.random.default_rng(31337)
    head(rng)
    torso(rng)
    for side in "rl":
        arm(side)
        leg(side)
    g.FACET, g.CRACK = 0.23, 0.19


def head(rng):
    for f in FACES:
        surf("head", 0, f, 411, heat_head)
        surf("head", 1, f, 412, heat_head, tone=-0.3)
    # rostro: vacio con ojos de rojo blanco, cejas furiosas y colmillos
    rect("head", 0, "front", 1, 2, 7, 8, K0)
    pattern("head", 0, "front", 1, 2, [
        "a3  3a",          # cejas
        "3a  a3",
        "1W  W1",          # ojos
        "cb  bc",
        "kkkkkk",
        "WkWWkW",          # colmillos
        "k1kk1k",
    ])
    put("head", 0, "front", 2, 3, O1); put("head", 0, "front", 5, 3, O1)
    put("head", 0, "front", 1, 3, O3); put("head", 0, "front", 6, 3, O3)
    # capucha (overlay): marco con luz y borde de lava
    clear("head", 1, "front", 1, 2, 7, 8)
    for i in range(8):
        put("head", 1, "front", i, 0, OBS[7] if i in (0, 7) else OBS[6])
        put("head", 1, "front", i, 1, O3 if i in (2, 5) else DR1 if i in (1, 3, 4, 6) else OBS[3])
    for j in range(2, 8):
        put("head", 1, "front", 0, j, OBS[5] if j % 2 else OBS[4]); put("head", 1, "front", 7, j, OBS[4] if j % 2 else OBS[3])
    put("head", 1, "front", 0, 4, O3); put("head", 1, "front", 7, 5, O3)
    # llamas bajas en el borde + cuernos
    hts = [3, 1, 2, 0, 3, 2, 1, 3, 0, 2, 3, 1, 2, 3, 0, 2, 3, 1, 3, 2, 1, 3, 2, 0]
    for f in ("right", "left", "back"):
        clear("head", 1, f, 0, 0, 8, 4)
    flames("head", ["right", "back", "left"], 3, hts)
    for f in ("right", "left", "back"):
        for i in range(8):
            put("head", 1, f, i, 4, DR1 if i % 2 else O3)
    # cuernos: diagonales de obsidiana con punta de lava (cerca del frente)
    horn_r = {(7, 3): OBS[5], (6, 3): OBS[4], (6, 2): OBS[6], (5, 2): OBS[5], (5, 1): OBS[7], (4, 1): OBS[6], (4, 0): O2, (3, 0): O3}
    for (x, y), c in horn_r.items():
        put("head", 1, "right", x, y, c)
        put("head", 1, "left", 7 - x, y, c)
    # corona de fuego (arriba), mas compacta
    for j in range(8):
        for i in range(8):
            v = 1.3 - np.hypot(i - 3.5, j - 3.5) / 3.4 + (rng.random() - 0.5) * 0.3
            col = WH if v > 1.08 else Y1 if v > 0.88 else O1 if v > 0.66 else O2 if v > 0.48 else \
                O3 if v > 0.32 else DR1 if v > 0.16 else None
            if col:
                put("head", 1, "top", i, j, col)
    for (x, y) in ((0, 0), (7, 0), (0, 7), (7, 7)):
        put("head", 1, "top", x, y, OBS[6])
    pattern("head", 1, "back", 3, 6, ["WY"])
    pattern("head", 1, "back", 2, 7, ["a32a"])


def torso(rng):
    for f in FACES:
        surf("torso", 0, f, 421, heat_torso, tone=0.6)
    # nucleo desbordado
    pattern("torso", 0, "front", 1, 1, [
        " a33a ",
        "a3YY3a",
        "3YWWY3",
        "3YWWY3",
        "a3YY3a",
        " a33a ",
    ])
    # placas laterales del peto, costillas y abdomen
    for y in range(0, 8):
        put("torso", 0, "front", 0, y, OBS[5] if y % 2 else OBS[4]); put("torso", 0, "front", 7, y, OBS[4] if y % 2 else OBS[3])
    for i in range(1, 7):
        put("torso", 0, "front", i, 7, OBS[6]); put("torso", 0, "front", i, 8, OBS[3])
    put("torso", 0, "front", 3, 7, O2); put("torso", 0, "front", 4, 7, O2)
    # cinturon con hebilla de gema
    for f, wd in (("front", 8), ("back", 8), ("right", 4), ("left", 4)):
        for i in range(wd):
            put("torso", 0, f, i, 9, OBS[6]); put("torso", 0, f, i, 10, OBS[2])
            put("torso", 0, f, i, 11, DR2 if i % 2 else DR3)
    plate("torso", 0, "front", 2, 9, 6, 11, base=O2, hi=Y1, mid=O1, lo=O3, ol=OUTL)
    # espina
    for j in range(0, 9):
        put("torso", 0, "back", 3, j, OBS[6]); put("torso", 0, "back", 4, j, OBS[5])
    for j in (1, 3, 5, 7):
        put("torso", 0, "back", 3, j, O2); put("torso", 0, "back", 4, j, Y1)
    # overlay: collar de pinchos, correas, capa
    for f in ("front", "back"):
        for i in range(8):
            put("torso", 1, f, i, 0, OBS[7] if i in (0, 1, 6, 7) else OBS[6])
    for x in (0, 7):
        for j in range(1, 8):
            put("torso", 1, "front", x, j, OBS[5] if j % 2 else OBS[4])
        for j in (2, 5):
            put("torso", 1, "front", x, j, O2)
    for f in ("right", "left"):
        for i in range(4):
            put("torso", 1, f, i, 0, OBS[7])
    g.FACET, g.CRACK = 0.17, 0.15
    P = g.face_points("torso", 1, "back")
    cape = g.surface(P, "back", 422, make_heat(0, 22, 0.02), -0.6)
    for j in range(1, 11):
        for i in range(8):
            put("torso", 1, "back", i, j, tuple(int(c) for c in cape[j, i]))
        put("torso", 1, "back", 3, j, O3 if j % 2 else O2)
        put("torso", 1, "back", 4, j, O2 if j % 2 else Y1)
    for i in range(8):
        put("torso", 1, "back", i, 11, DR1 if i % 2 else DR3)
    flames("torso", ["back"], 11, [3, 1, 4, 2, 3, 0, 4, 2])
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
    outer = "right" if side == "r" else "left"
    seed = 431 + (side == "l") * 10
    for f in FACES:
        surf(p, 0, f, seed, heat_arm, tone=0.9)
    for f in ("front", "back", "right", "left"):
        for i in range(4):
            put(p, 0, f, i, 9, [DR1, O3, DR1, O3][(i + (f in (outer, "back"))) % 4])
            put(p, 0, f, i, 10, [O2, O1, O2, O1][(i + (f == "front")) % 4])
            put(p, 0, f, i, 11, [O1, Y1, O1, O2][(i + 2 * (f == "back")) % 4])
        # brazalete con luz
        plate(p, 1, f, 0, 6, 4, 9, base=OBS[4], hi=OBS[7], mid=OBS[6], lo=OBS[2])
        # hombrera
        plate(p, 1, f, 0, 0, 4, 4, base=OBS[5], hi=OBS[7], mid=OBS[6], lo=OBS[3], ol=OBS[2])
        put(p, 1, f, 1, 2, O3); put(p, 1, f, 2, 2, DR1)
    for i in range(4):
        for j in range(4):
            put(p, 1, "top", i, j, OBS[7] if (i + j) % 2 == 0 else OBS[6])
    put(p, 1, "top", 1, 1, O2); put(p, 1, "top", 2, 2, Y1)
    for (x, y) in ((0, 0), (3, 0), (0, 3), (3, 3)):
        put(p, 1, "top", x, y, OUTL)
    rect(p, 0, "bottom", 0, 0, 4, 4, Y1)
    hts = [3, 0, 4, 2, 3, 0, 4, 1, 3, 2, 0, 4, 2, 3, 1, 4]
    if side == "l":
        hts = hts[5:] + hts[:5]
    flames(p, ["right", "front", "left", "back"], 11, hts)


def leg(side):
    p = side + "leg"
    seed = 441 + (side == "l") * 10
    for f in FACES:
        surf(p, 0, f, seed, heat_leg, tone=0.9)
    for f in ("front", "back", "right", "left"):
        for i in range(4):
            # greba y bota
            put(p, 0, f, i, 8, OBS[6]); put(p, 0, f, i, 9, OBS[3]); put(p, 0, f, i, 10, [DR2, DR1, O3, DR1][(i + (f == "front")) % 4])
            put(p, 0, f, i, 11, [O3, O2, O1, O2][(i + 2 * (f == "back")) % 4])
        # cadera y rodillera (overlay)
        plate(p, 1, f, 0, 0, 4, 3, base=OBS[4], hi=OBS[7], mid=OBS[6], lo=OBS[2])
        put(p, 1, f, 1, 1, O3); put(p, 1, f, 2, 1, DR1)
        plate(p, 1, f, 0, 4, 4, 7, base=OBS[5], hi=OBS[7], mid=OBS[6], lo=OBS[3])
    put(p, 1, "front", 1, 5, O2); put(p, 1, "front", 2, 5, Y1)
    rect(p, 0, "bottom", 0, 0, 4, 4, Y1)
    hts = [4, 2, 0, 3, 4, 0, 2, 4, 3, 0, 4, 2, 3, 4, 0, 2]
    if side == "l":
        hts = hts[3:] + hts[:3]
    flames(p, ["right", "front", "left", "back"], 11, hts)
    # sin llamas sobre la rodillera: se recortan por encima de la fila 7
    for f in ("front", "back", "right", "left"):
        clear(p, 1, f, 0, 7, 4, 7)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out, exist_ok=True)
    build()
    sheet = g.compose()
    skin = Image.fromarray(sheet, "RGBA")
    skin.save(os.path.join(out, "obsidian_demon_phase2_remaster_skin.png"))
    big = skin.resize((640, 640), Image.NEAREST)
    chk = Image.new("RGBA", (640, 640))
    px = chk.load()
    for y in range(640):
        for x in range(640):
            px[x, y] = (96, 96, 112, 255) if ((x // 10) + (y // 10)) % 2 else (72, 72, 88, 255)
    chk.alpha_composite(big)
    chk.save(os.path.join(out, "obsidian_demon_phase2_remaster_texture_x10.png"))
    views = [g.render(sheet, yaw=-32), g.render(sheet, yaw=0, pitch=6),
             g.render(sheet, yaw=180, pitch=6), g.render(sheet, yaw=148)]
    sh = Image.new("RGBA", (views[0].width * len(views), views[0].height))
    for i, v in enumerate(views):
        sh.paste(v, (i * v.width, 0))
    sh.save(os.path.join(out, "obsidian_demon_phase2_remaster_preview.png"))
    S = os.environ.get("DEBUG_DIR")
    if S:
        g.render(sheet, yaw=0, pitch=4, scale=20, size=(560, 800)).save(os.path.join(S, "rm_front.png"))
        g.render(sheet, yaw=-35, pitch=14, scale=20, size=(560, 800)).save(os.path.join(S, "rm_34.png"))
    print("ok ->", out)


if __name__ == "__main__":
    main()
