#!/usr/bin/env python3
"""
Generador de la skin "Demonio de Obsidiana" (Minecraft Java 64x64, brazos clasicos).

Las texturas procedurales (obsidiana facetada + grietas de lava) se calculan en
coordenadas 3D reales del modelo, asi las grietas continuan sin cortes entre
caras, hombros, codos, etc.  Encima se pintan a mano los detalles (ojos,
emblema, cinturon, armadura, botas, llamas).

Uso:  python3 generate_skin.py [carpeta_salida]
"""
import os
import sys
import numpy as np
from PIL import Image

# ------------------------------------------------------------------ paleta
K0 = (0, 0, 0)
OBS = [(5, 4, 9), (11, 10, 16), (17, 15, 27), (24, 22, 32), (30, 28, 38),
       (43, 41, 46), (59, 58, 65), (80, 78, 86)]
DR1, DR2, DR3, DR4 = (79, 3, 13), (55, 2, 9), (40, 1, 7), (24, 1, 5)
O1, O2, O3 = (255, 162, 0), (255, 111, 0), (255, 68, 0)
Y1, RD = (255, 214, 64), (255, 0, 0)

# ------------------------------------------------------------------ ruido 3D
def _h(ix, iy, iz, seed):
    n = (ix * 374761393 + iy * 668265263 + iz * 1274126177 + seed * 362437) & 0x7FFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0x7FFFFFFF
    n = n ^ (n >> 16)
    return (n & 0xFFFF) / 65535.0


def vnoise(P, scale, seed):
    x, y, z = (P[..., k] * scale for k in range(3))
    x0, y0, z0 = (np.floor(a).astype(np.int64) for a in (x, y, z))
    fx, fy, fz = x - x0, y - y0, z - z0
    sx, sy, sz = (f * f * (3 - 2 * f) for f in (fx, fy, fz))
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (sx if dx else 1 - sx) * (sy if dy else 1 - sy) * (sz if dz else 1 - sz)
                out = out + w * _h(x0 + dx, y0 + dy, z0 + dz, seed)
    return out


def fbm(P, scale, seed, octaves=3):
    tot, amp, norm = 0, 1.0, 0
    for o in range(octaves):
        tot = tot + amp * vnoise(P, scale * 2 ** o, seed + o * 17)
        norm += amp
        amp *= 0.5
    return tot / norm


def voronoi(P, scale, seed):
    x, y, z = (P[..., k] * scale for k in range(3))
    bx, by, bz = (np.floor(a).astype(np.int64) for a in (x, y, z))
    f1 = np.full(x.shape, 9.0)
    f2 = np.full(x.shape, 9.0)
    cid = np.zeros(x.shape)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = bx + dx, by + dy, bz + dz
                px = cx + _h(cx, cy, cz, seed)
                py = cy + _h(cx, cy, cz, seed + 1)
                pz = cz + _h(cx, cy, cz, seed + 2)
                d = np.sqrt((x - px) ** 2 + (y - py) ** 2 + (z - pz) ** 2)
                closer = d < f1
                f2 = np.where(closer, f1, np.minimum(f2, d))
                cid = np.where(closer, _h(cx, cy, cz, seed + 3), cid)
                f1 = np.where(closer, d, f1)
    return f1, f2, cid


# ------------------------------------------------------------------ geometria
FACES = ["right", "front", "left", "back", "top", "bottom"]
PARTS = {
    #          tamano (w,h,d)  esquina minima   uv base   uv overlay
    "head":  dict(size=(8, 8, 8), pos=(-4, 24, -4), uv=(0, 0),   ouv=(32, 0)),
    "torso": dict(size=(8, 12, 4), pos=(-4, 12, -2), uv=(16, 16), ouv=(16, 32)),
    "rarm":  dict(size=(4, 12, 4), pos=(-8, 12, -2), uv=(40, 16), ouv=(40, 32)),
    "larm":  dict(size=(4, 12, 4), pos=(4, 12, -2),  uv=(32, 48), ouv=(48, 48)),
    "rleg":  dict(size=(4, 12, 4), pos=(-4, 0, -2),  uv=(0, 16),  ouv=(0, 32)),
    "lleg":  dict(size=(4, 12, 4), pos=(0, 0, -2),   uv=(16, 48), ouv=(0, 48)),
}


def face_rect(part, layer, face):
    w, h, d = PARTS[part]["size"]
    ox, oy = PARTS[part]["uv" if layer == 0 else "ouv"]
    return {
        "top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d),
        "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
        "left": (ox + d + w, oy + d, d, h), "back": (ox + 2 * d + w, oy + d, w, h),
    }[face]


def face_points(part, layer, face):
    """Coordenadas 3D (h,w,3) de cada pixel de una cara."""
    w, h, d = PARTS[part]["size"]
    x0, y0, z0 = PARTS[part]["pos"]
    g = 0.0 if layer == 0 else 0.5
    _, _, fw, fh = face_rect(part, layer, face)
    I, J = np.meshgrid(np.arange(fw) + 0.5, np.arange(fh) + 0.5)
    X = Y = Z = None
    if face == "front":
        X, Y, Z = x0 + I, y0 + h - J, np.full_like(I, z0 + d + g)
    elif face == "back":
        X, Y, Z = x0 + w - I, y0 + h - J, np.full_like(I, z0 - g)
    elif face == "right":
        X, Y, Z = np.full_like(I, x0 - g), y0 + h - J, z0 + I
    elif face == "left":
        X, Y, Z = np.full_like(I, x0 + w + g), y0 + h - J, z0 + d - I
    elif face == "top":
        X, Y, Z = x0 + I, np.full_like(I, y0 + h + g), z0 + J
    else:
        X, Y, Z = x0 + I, np.full_like(I, y0 - g), z0 + d - J
    return np.stack([X, Y, Z], -1)


# ------------------------------------------------------------------ superficie procedural
SHADE = {"top": 1, "front": 0, "right": 0, "left": -1, "back": -1, "bottom": -2}


def surface(P, face, seed, heat_fn, tone=0):
    """Obsidiana facetada con grietas de lava. Devuelve (h,w,3) uint8."""
    warp = np.stack([fbm(P, 0.11, seed + 5 + k, 2) - 0.5 for k in range(3)], -1) * 3.2
    Q = P + warp
    # --- obsidiana
    f1, f2, cid = voronoi(Q, 0.23, seed)
    e = f2 - f1
    n = fbm(P, 0.45, seed + 40, 2)
    idx = np.floor(1.0 + cid ** 1.4 * 3.4 + (n - 0.5) * 1.4 + tone).astype(int)
    idx = np.where(e < 0.07, idx - 1, idx)                       # grietas oscuras entre facetas
    idx = np.where((e > 0.07) & (e < 0.15) & (n > 0.55), idx + 1, idx)  # brillo en filos
    idx = np.clip(idx + SHADE[face], 0, len(OBS) - 1)
    rgb = np.array(OBS, dtype=np.float64)[idx]
    # --- lava
    c1, c2, _ = voronoi(Q, 0.19, seed + 91)
    ce = c2 - c1
    heat = heat_fn(P, seed)
    w = np.where(heat > 0.16, 0.05 + 0.26 * heat, 0.0)
    with np.errstate(divide="ignore", invalid="ignore"):
        t = np.where(w > 0, ce / np.maximum(w, 1e-6), 9.0)
    ramp = [(0.22, Y1), (0.50, O1), (0.80, O2), (1.05, O3), (1.35, DR1), (1.75, DR2), (2.2, DR3)]
    for lim, col in reversed(ramp):
        rgb = np.where((t < lim)[..., None], np.array(col, dtype=np.float64), rgb)
    return rgb.astype(np.uint8)


def make_heat(y_hot, y_cold, floor):
    def fn(P, seed):
        y = P[..., 1]
        base = np.clip((y_cold - y) / (y_cold - y_hot), 0, 1) ** 1.6
        return np.clip(floor + (0.9 - floor) * base + 0.30 * (fbm(P, 0.18, seed + 70) - 0.5), 0, 1)
    return fn


heat_leg = make_heat(0, 10, 0.0)
heat_torso = make_heat(8, 22, 0.03)
heat_arm = make_heat(8, 24, 0.08)


def heat_head(P, seed):
    return np.clip(0.02 + 0.45 * (fbm(P, 0.22, seed + 70, 2) - 0.42), 0, 1)


# ------------------------------------------------------------------ lienzo
TEX = {}   # (part, layer, face) -> array (h,w,4)


def new_faces():
    for part in PARTS:
        for layer in (0, 1):
            for face in FACES:
                _, _, fw, fh = face_rect(part, layer, face)
                TEX[(part, layer, face)] = np.zeros((fh, fw, 4), np.uint8)


def procedural(part, layer, face, seed, heat_fn, tone=0, rect=None, alpha=255):
    P = face_points(part, layer, face)
    rgb = surface(P, face, seed, heat_fn, tone)
    arr = TEX[(part, layer, face)]
    h, w = arr.shape[:2]
    x0, y0, x1, y1 = rect if rect else (0, 0, w, h)
    arr[y0:y1, x0:x1, :3] = rgb[y0:y1, x0:x1]
    arr[y0:y1, x0:x1, 3] = alpha


def put(part, layer, face, x, y, col, a=255):
    arr = TEX[(part, layer, face)]
    if 0 <= y < arr.shape[0] and 0 <= x < arr.shape[1]:
        arr[y, x] = (*col, a)


def rect(part, layer, face, x0, y0, x1, y1, col, a=255):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(part, layer, face, x, y, col, a)


def clear(part, layer, face, x0, y0, x1, y1):
    rect(part, layer, face, x0, y0, x1, y1, (0, 0, 0), 0)


def pattern(part, layer, face, x0, y0, rows):
    """rows: lista de cadenas; '.' = no tocar, ' ' = transparente, letra = color."""
    key = {"Y": Y1, "1": O1, "2": O2, "3": O3, "R": RD, "a": DR1, "b": DR2, "c": DR3, "d": DR4,
           "k": K0, "n": OBS[1], "N": OBS[2], "g": OBS[4], "G": OBS[5], "h": OBS[6], "H": OBS[7],
           "v": OBS[0]}
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == ".":
                continue
            if ch == " ":
                put(part, layer, face, x0 + i, y0 + j, (0, 0, 0), 0)
            else:
                put(part, layer, face, x0 + i, y0 + j, key[ch])


def flame_cols(part, layer, faces, base_row, heights):
    """Llamas que suben desde base_row; mas cortas = mas rojas. heights = alturas por columna alrededor de la pieza."""
    ramp = [Y1, O1, O2, O3, RD]
    n = 0
    for f in faces:
        for i in range(face_rect(part, layer, f)[2]):
            h = heights[n % len(heights)]
            n += 1
            cols = ramp[max(0, 5 - h - 1):][:h] if h < 5 else ramp
            cols = ramp[4 - h:4] if h <= 4 else ramp
            for k, col in enumerate(cols):
                put(part, layer, f, i, base_row - k, col)


# ------------------------------------------------------------------ construccion
def build():
    new_faces()
    rng = np.random.default_rng(1337)
    build_head(rng)
    build_torso(rng)
    for side in "rl":
        build_arm(side, rng)
        build_leg(side, rng)


def build_head(rng):
    for f in FACES:
        procedural("head", 0, f, 11, heat_head)
        procedural("head", 1, f, 12, heat_head, tone=-0.3)
    # ---- cara: vacio negro con ojos de fuego
    rect("head", 0, "front", 1, 2, 7, 8, K0)
    rect("head", 0, "front", 1, 6, 7, 8, (6, 3, 9))
    pattern("head", 0, "front", 1, 3, [
        "a    a",     # cejas
        "2Y  Y2",     # ojos
        "cc  cc",     # halo
        " cbbc ",     # sonrisa
    ])
    put("head", 0, "front", 3, 6, DR3); put("head", 0, "front", 4, 6, DR3)
    # ---- capucha (overlay): marco alrededor del rostro
    clear("head", 1, "front", 1, 2, 7, 8)
    for i in range(8):
        put("head", 1, "front", i, 0, OBS[6]); put("head", 1, "front", i, 1, OBS[4])
    for i in (1, 3, 4, 6):
        put("head", 1, "front", i, 1, DR1)
    put("head", 1, "front", 2, 1, O3); put("head", 1, "front", 5, 1, O3)
    for j in range(2, 8):
        put("head", 1, "front", 0, j, OBS[3] if j % 2 else OBS[4])
        put("head", 1, "front", 7, j, OBS[3] if j % 2 else OBS[4])
    put("head", 1, "front", 0, 5, DR1); put("head", 1, "front", 7, 4, DR1)
    # ---- llamas coronando la capucha (filas altas de los lados)
    hts = [3, 1, 2, 0, 3, 2, 1, 3, 0, 2, 3, 1, 2, 3, 0, 2, 3, 1, 3, 2]
    for f in ("right", "left", "back"):
        rect("head", 1, f, 0, 0, 8, 3, (0, 0, 0), 0)
    flame_cols("head", 1, ["right", "back", "left"], 2, hts)
    for f in ("right", "left", "back"):
        for i in range(8):
            put("head", 1, f, i, 3, DR1 if i % 2 else DR2)
    # ---- parte superior: corona de fuego
    for j in range(8):
        for i in range(8):
            r = max(abs(i - 3.5), abs(j - 3.5)) * 0.6 + np.hypot(i - 3.5, j - 3.5) * 0.4
            v = 1.15 - r / 4.1 + (rng.random() - 0.5) * 0.35
            col = Y1 if v > 0.92 else O1 if v > 0.68 else O2 if v > 0.5 else O3 if v > 0.33 else \
                RD if v > 0.22 else DR1 if v > 0.12 else None
            if col:
                put("head", 1, "top", i, j, col)
    # ---- gema/runa de fuego en la nuca
    pattern("head", 1, "back", 3, 3, ["Y1"])
    pattern("head", 1, "back", 2, 4, ["a21a"])
    pattern("head", 1, "back", 3, 5, ["33"])


def build_torso(rng):
    for f in FACES:
        procedural("torso", 0, f, 21, heat_torso)
    # ---- emblema de nucleo de lava
    pattern("torso", 0, "front", 2, 1, [
        " aa ",
        "a33a",
        "3Y12",
        "3123",
        "a33a",
        " aa ",
    ])
    # marco de placas alrededor del emblema
    for (x, y) in [(1, 2), (6, 2), (1, 5), (6, 5)]:
        put("torso", 0, "front", x, y, OBS[7])
    for (x, y) in [(1, 3), (1, 4), (6, 3), (6, 4)]:
        put("torso", 0, "front", x, y, OBS[6])
    # costillas
    for j in (7, 8):
        for i in (1, 2, 3, 4, 5, 6):
            put("torso", 0, "front", i, j, OBS[5] if j == 7 else OBS[3])
    for i in (3, 4):
        put("torso", 0, "front", i, 7, DR1)
    # cinturon
    for f, wd in (("front", 8), ("back", 8), ("right", 4), ("left", 4)):
        for i in range(wd):
            put("torso", 0, f, i, 9, OBS[6]); put("torso", 0, f, i, 10, OBS[2])
            put("torso", 0, f, i, 11, DR3 if (i + (f == "back")) % 2 else DR4)
    pattern("torso", 0, "front", 2, 9, ["HY1H", "H21H"])
    # espina dorsal
    for j in range(0, 9):
        put("torso", 0, "back", 3, j, OBS[6]); put("torso", 0, "back", 4, j, OBS[5])
    for j in (1, 4, 7):
        put("torso", 0, "back", 2, j, DR1); put("torso", 0, "back", 5, j, DR1)
        put("torso", 0, "back", 3, j, O2); put("torso", 0, "back", 4, j, O3)
    # ---- overlay: collar, correas y faldon quemado
    for i in range(8):
        put("torso", 1, "front", i, 0, OBS[6] if i not in (0, 7) else OBS[7])
        put("torso", 1, "back", i, 0, OBS[6] if i not in (0, 7) else OBS[7])
    for f in ("right", "left"):
        for i in range(4):
            put("torso", 1, f, i, 0, OBS[6])
    for j in range(1, 8):
        for x in (0, 7):
            put("torso", 1, "front", x, j, OBS[5] if j % 2 else OBS[4])
            put("torso", 1, "back", x, j, OBS[5] if j % 2 else OBS[4])
    for (x, y) in [(0, 2), (7, 2), (0, 5), (7, 5)]:
        put("torso", 1, "front", x, y, O3)
        put("torso", 1, "back", x, y, O3)
    # faldon quemado: dientes irregulares con brasas en el borde
    for f, wd in (("front", 8), ("back", 8), ("right", 4), ("left", 4)):
        for i in range(wd):
            put("torso", 1, f, i, 10, OBS[2])
            if rng.random() < 0.62:
                put("torso", 1, f, i, 11, DR1 if rng.random() < 0.4 else OBS[1])
            elif rng.random() < 0.5:
                put("torso", 1, f, i, 11, O3 if rng.random() < 0.4 else DR2)


def arm_faces(side):
    return ("right", "left") if side == "r" else ("left", "right")   # (exterior, interior)


def build_arm(side, rng):
    p = side + "arm"
    outer, inner = arm_faces(side)
    seed = 51 if side == "r" else 61
    for f in FACES:
        procedural(p, 0, f, seed, heat_arm)
    # mangas / antebrazo con bandas
    for f in ("front", "back", "right", "left"):
        for i in range(4):
            put(p, 0, f, i, 8, OBS[5]); put(p, 0, f, i, 9, OBS[3])
            # manos de lava
            put(p, 0, f, i, 10, [DR1, O3, O2, DR1][(i + (f in (outer, "back"))) % 4])
            put(p, 0, f, i, 11, [O2, O1, Y1, O2][(i + 2 * (f == "front")) % 4])
    for x, y, c in [(1, 10, O2), (2, 10, O3)]:
        put(p, 0, "front", x, y, c)
    rect(p, 0, "bottom", 0, 0, 4, 4, O1)
    pattern(p, 0, "bottom", 1, 1, ["Y1", "1Y"])
    # hombrera: placa en overlay
    flames = []
    for f in ("right", "front", "left", "back"):
        for i in range(4):
            put(p, 1, f, i, 0, OBS[7])
            put(p, 1, f, i, 1, OBS[6])
            put(p, 1, f, i, 2, OBS[5] if i % 2 else OBS[4])
            put(p, 1, f, i, 3, DR1 if i in (1, 2) else OBS[3])
    for i in range(4):
        for j in range(4):
            put(p, 1, "top", i, j, OBS[7] if (i + j) % 2 == 0 else OBS[6])
    put(p, 1, "top", 1, 1, O2); put(p, 1, "top", 2, 2, O3)
    for f in (outer, "front", "back"):
        put(p, 1, f, 1, 2, O3)
    # brazalete elevado
    for f in ("front", "back", "right", "left"):
        for i in range(4):
            put(p, 1, f, i, 7, OBS[6]); put(p, 1, f, i, 8, OBS[4])
    put(p, 1, "front", 1, 7, O3); put(p, 1, "front", 2, 7, O3)
    # llamas saliendo de las manos
    hts = [2, 3, 0, 3, 1, 0, 3, 2, 3, 0, 2, 3, 1, 3, 0, 2]
    if side == "l":
        hts = hts[5:] + hts[:5]
    flame_cols(p, 1, ["right", "front", "left", "back"], 11, hts)


def build_leg(side, rng):
    p = side + "leg"
    seed = 71 if side == "r" else 81
    for f in FACES:
        procedural(p, 0, f, seed, heat_leg)
    for f, wd in (("front", 4), ("back", 4), ("right", 4), ("left", 4)):
        # cadera
        for i in range(wd):
            put(p, 0, f, i, 0, OBS[4])
        # rodillera
        for i in range(wd):
            put(p, 0, f, i, 4, OBS[5]); put(p, 0, f, i, 5, OBS[4])
        # bota
        for i in range(wd):
            put(p, 0, f, i, 8, OBS[6]); put(p, 0, f, i, 9, OBS[3])
            put(p, 0, f, i, 10, [DR2, DR1, O3, DR1][(i + (f == "front")) % 4])
            put(p, 0, f, i, 11, [O3, O2, O1, O2][(i + 2 * (f == "back")) % 4])
    put(p, 0, "front", 1, 5, O2); put(p, 0, "front", 2, 5, O3)
    put(p, 0, "front", 1, 6, DR1); put(p, 0, "front", 2, 6, DR1)
    rect(p, 0, "bottom", 0, 0, 4, 4, O1)
    pattern(p, 0, "bottom", 0, 0, ["3223", "2Y12", "21Y2", "3223"])
    # overlay: placa de cadera + llamas en las botas
    for f in ("front", "back", "right", "left"):
        for i in range(4):
            put(p, 1, f, i, 0, OBS[6]); put(p, 1, f, i, 1, OBS[4])
            put(p, 1, f, i, 2, DR1 if i in (1, 2) else OBS[3])
    put(p, 1, "front", 0, 1, O3); put(p, 1, "front", 3, 1, O3)
    for i in range(4):
        put(p, 1, "top", i, 0, OBS[5])
    hts = [3, 1, 4, 0, 2, 4, 1, 3, 0, 4, 2, 1, 3, 0, 4, 2]
    hts = hts[3:] + hts[:3] if side == "l" else hts
    flame_cols(p, 1, ["right", "front", "left", "back"], 11, hts)


# ------------------------------------------------------------------ salida
def compose():
    sheet = np.zeros((64, 64, 4), np.uint8)
    for layer in (0, 1):
        for part in PARTS:
            for f in FACES:
                x, y, w, h = face_rect(part, layer, f)
                sheet[y:y + h, x:x + w] = TEX[(part, layer, f)]
    return sheet


# ------------------------------------------------------------------ preview 3D
def render(sheet, yaw=-32, pitch=18, scale=11, size=(420, 600), bg=((18, 14, 26), (52, 24, 18))):
    W, H = size
    cy, sy_ = np.cos(np.radians(yaw)), np.sin(np.radians(yaw))
    cp, sp = np.cos(np.radians(pitch)), np.sin(np.radians(pitch))

    def proj(p):
        X, Y, Z = p
        xr = X * cy - Z * sy_
        zr = X * sy_ + Z * cy
        yr = Y * cp - zr * sp
        dr = zr * cp + Y * sp
        return np.array([W / 2 + xr * scale, H - 40 - yr * scale]), dr

    img = np.zeros((H, W, 4), np.float64)
    top, bot = np.array(bg[0], float), np.array(bg[1], float)
    for y in range(H):
        img[y, :, :3] = top + (bot - top) * (y / H) ** 1.6
        img[y, :, 3] = 255
    zbuf = np.full((H, W), -1e9)
    for layer in (0, 1):
        for part, spec in PARTS.items():
            w, h, d = spec["size"]
            x0, y0, z0 = spec["pos"]
            g = 0.0 if layer == 0 else 0.5
            x0, y0, z0, x1, y1, z1 = x0 - g, y0 - g, z0 - g, x0 + w + g, y0 + h + g, z0 + d + g
            quads = {
                "front": ((x0, y1, z1), (x1 - x0, 0, 0), (0, y0 - y1, 0)),
                "back": ((x1, y1, z0), (x0 - x1, 0, 0), (0, y0 - y1, 0)),
                "right": ((x0, y1, z0), (0, 0, z1 - z0), (0, y0 - y1, 0)),
                "left": ((x1, y1, z1), (0, 0, z0 - z1), (0, y0 - y1, 0)),
                "top": ((x0, y1, z0), (x1 - x0, 0, 0), (0, 0, z1 - z0)),
                "bottom": ((x0, y0, z1), (x1 - x0, 0, 0), (0, 0, z0 - z1)),
            }
            for f, (c, u, v) in quads.items():
                tx, ty, tw, th = face_rect(part, layer, f)
                tex = sheet[ty:ty + th, tx:tx + tw]
                c = np.array(c, float); u = np.array(u, float); v = np.array(v, float)
                a, da = proj(c)
                b, db = proj(c + u)
                e, de = proj(c + v)
                su, sv = b - a, e - a
                M = np.array([su, sv]).T
                if abs(np.linalg.det(M)) < 1e-6:
                    continue
                Mi = np.linalg.inv(M)
                pts = np.array([a, b, e, b + sv])
                xa, ya = np.floor(pts.min(0)).astype(int), np.ceil(pts.max(0)).astype(int)
                xs = np.arange(max(xa[0], 0), min(ya[0], W))
                ys = np.arange(max(xa[1], 0), min(ya[1], H))
                if len(xs) == 0 or len(ys) == 0:
                    continue
                XX, YY = np.meshgrid(xs + 0.5, ys + 0.5)
                uu = Mi[0, 0] * (XX - a[0]) + Mi[0, 1] * (YY - a[1])
                vv = Mi[1, 0] * (XX - a[0]) + Mi[1, 1] * (YY - a[1])
                ok = (uu >= 0) & (uu < 1) & (vv >= 0) & (vv < 1)
                ti = np.clip((uu * tw).astype(int), 0, tw - 1)
                tj = np.clip((vv * th).astype(int), 0, th - 1)
                col = tex[tj, ti]
                depth = da + uu * (db - da) + vv * (de - da)
                sub = zbuf[ys[0]:ys[-1] + 1, xs[0]:xs[-1] + 1]
                mask = ok & (col[..., 3] > 0) & (depth > sub)
                sub[mask] = depth[mask]
                region = img[ys[0]:ys[-1] + 1, xs[0]:xs[-1] + 1]
                region[mask] = np.concatenate([col[..., :3], np.full(col.shape[:2] + (1,), 255)], -1)[mask]
    return Image.fromarray(img.astype(np.uint8), "RGBA")


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    os.makedirs(out, exist_ok=True)
    build()
    sheet = compose()
    skin = Image.fromarray(sheet, "RGBA")
    skin.save(os.path.join(out, "obsidian_demon_skin.png"))

    # hoja de textura ampliada sobre fondo de ajedrez (para ver la transparencia)
    big = skin.resize((640, 640), Image.NEAREST)
    chk = Image.new("RGBA", (640, 640))
    px = chk.load()
    for y in range(640):
        for x in range(640):
            px[x, y] = (96, 96, 112, 255) if ((x // 10) + (y // 10)) % 2 else (72, 72, 88, 255)
    chk.alpha_composite(big)
    chk.save(os.path.join(out, "obsidian_demon_texture_x10.png"))

    views = [render(sheet, yaw=-32), render(sheet, yaw=0, pitch=6), render(sheet, yaw=180, pitch=6), render(sheet, yaw=148)]
    sheet_img = Image.new("RGBA", (views[0].width * len(views), views[0].height))
    for i, v in enumerate(views):
        sheet_img.paste(v, (i * v.width, 0))
    sheet_img.save(os.path.join(out, "obsidian_demon_preview.png"))
    print("ok ->", out)


if __name__ == "__main__":
    main()
