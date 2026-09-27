#!/usr/bin/env python3
"""Build the "Horror Legends" add-on.

    python3 tools/build.py                  # models, textures, validate, pack
    python3 tools/build.py --textures       # only regenerate models and textures
    python3 tools/build.py --preview DIR    # also render model previews into DIR

Models are defined in Python (see geometry.py) and written to
resource_pack/models/entity/hl.geo.json; every texture is painted
procedurally against the same cube list, so the add-on ships no third-party
art. Only the standard library is used.

Produces dist/HorrorLegends.mcaddon and dist/HorrorLegends_WhatsApp.zip (the
same .mcaddon wrapped in a .zip, which messaging apps accept as an attachment).
"""

import json
import random
import sys
import zipfile
from pathlib import Path

from geometry import Bone, Cube, Model, render
from pixels import Canvas, jitter, mix, paint_box, shade

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "behavior_pack"
RP = ROOT / "resource_pack"
DIST = ROOT / "dist" / "HorrorLegends.mcaddon"
DIST_ZIP = ROOT / "dist" / "HorrorLegends_WhatsApp.zip"

GLOW_WHITE = (0xFF, 0xFF, 0xFF, 40)  # alpha < 255 glows under entity_emissive_alpha
BLACK = (0x07, 0x07, 0x09)
CLEAR = (0, 0, 0, 0)


def side_x(side, a, b):
    """Origin X of a cube spanning |x| in [a, b] on the model's right (-1) or
    left (+1) side."""
    return -b if side < 0 else a


def paint_model(cv, model, fn):
    """fn(bone, index, face, x, y, fw, fh) -> colour or None, for every cube."""
    for bone in model.bones:
        for i, cube in enumerate(bone.cubes):
            w, h, d = (int(s) for s in cube.size)
            paint_box(cv, *cube.uv, w, h, d,
                      lambda face, x, y, fw, fh, b=bone.name, i=i: fn(b, i, face, x, y, fw, fh))


def rows_face(rows, palette, x, y):
    return palette[rows[y][x]]


# =========================================================================== #
# Models
# =========================================================================== #

def player_model(identifier="geometry.hl.player", extra=()):
    """The classic player shape, with the outer skin layer on every part."""
    return Model(identifier, [
        Bone("root"),
        Bone("body", "root", (0, 24, 0), cubes=[
            Cube((-4, 12, -2), (8, 12, 4), (16, 16)),
            Cube((-4, 12, -2), (8, 12, 4), (16, 32), 0.25)]),
        Bone("head", "body", (0, 24, 0), cubes=[
            Cube((-4, 24, -4), (8, 8, 8), (0, 0)),
            Cube((-4, 24, -4), (8, 8, 8), (32, 0), 0.5)]),
        Bone("rightArm", "body", (-5, 22, 0), cubes=[
            Cube((-8, 12, -2), (4, 12, 4), (40, 16)),
            Cube((-8, 12, -2), (4, 12, 4), (40, 32), 0.25)]),
        Bone("leftArm", "body", (5, 22, 0), cubes=[
            Cube((4, 12, -2), (4, 12, 4), (32, 48)),
            Cube((4, 12, -2), (4, 12, 4), (48, 48), 0.25)]),
        Bone("rightLeg", "root", (-1.9, 12, 0), cubes=[
            Cube((-3.9, 0, -2), (4, 12, 4), (0, 16)),
            Cube((-3.9, 0, -2), (4, 12, 4), (0, 32), 0.25)]),
        Bone("leftLeg", "root", (1.9, 12, 0), cubes=[
            Cube((-0.1, 0, -2), (4, 12, 4), (16, 48)),
            Cube((-0.1, 0, -2), (4, 12, 4), (0, 48), 0.25)]),
        *extra,
    ], bounds=(2.5, 2.5, (0, 1.25, 0)))


def null_model():
    # Fragments of missing texture that hang in the air around it. They use
    # the unused corner of the head's texture.
    return player_model("geometry.hl.null", extra=[
        Bone("glitch", "body", (0, 24, 0), cubes=[
            Cube((5, 27, -1), (2, 2, 2), (0, 0)),
            Cube((-9, 17, 1), (2, 2, 2), (0, 0)),
            Cube((2, 33, 2), (2, 2, 2), (0, 0))]),
    ])


def fog_man_model():
    """Two and three-quarter blocks of pale, starved man: jointed legs, a
    hunched chest on a narrow waist, a long skull with a hinged jaw, and arms
    that reach past his knees."""
    bones = [
        Bone("root"),
        Bone("body", "root", (0, 20, 0), cubes=[Cube((-3, 20, -1.5), (6, 5, 3), (24, 16))]),
        Bone("chest", "body", (0, 25, 0), rotation=(6, 0, 0), cubes=[Cube((-4, 25, -2), (8, 9, 4), (0, 16))]),
        Bone("neck", "chest", (0, 34, 0), rotation=(-4, 0, 0), cubes=[Cube((-1, 34, -1), (2, 2, 2), (44, 0))]),
        Bone("head", "neck", (0, 36, 0), cubes=[Cube((-3, 36, -3), (6, 8, 6), (0, 0))]),
        Bone("jaw", "head", (0, 36.5, 1.5), rotation=(4, 0, 0), cubes=[Cube((-2.5, 34.5, -3.5), (5, 2, 5), (24, 0))]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        bones += [
            Bone(f"{name}Arm", "chest", (5 * side, 33, 0), rotation=(0, 0, -3 * side), cubes=[
                Cube((side_x(side, 4, 6), 22, -1), (2, 11, 2), (24, 32))]),
            Bone(f"{name}Forearm", f"{name}Arm", (5 * side, 22, 0), rotation=(-6, 0, 0), cubes=[
                Cube((side_x(side, 4, 6), 11, -1), (2, 11, 2), (32, 32))]),
            Bone(f"{name}Hand", f"{name}Forearm", (5 * side, 11, 0), cubes=[
                Cube((side_x(side, 3.5, 6.5), 8, -1.5), (3, 3, 2), (40, 32)),
                *[Cube((side_x(side, a, a + 1), 4, -1), (1, 4, 1), (50, 32)) for a in (3.5, 4.5, 5.5)]]),
            Bone(f"{name}Leg", "root", (2 * side, 20, 0), cubes=[
                Cube((side_x(side, 0.5, 3.5), 10, -1.5), (3, 10, 3), (0, 32))]),
            Bone(f"{name}Shin", f"{name}Leg", (2 * side, 10, 0), cubes=[
                Cube((side_x(side, 0.5, 3.5), 0, -1.5), (3, 10, 3), (12, 32)),
                Cube((side_x(side, 0.5, 3.5), 0, -3.5), (3, 1, 2), (52, 0))]),
        ]
    return Model("geometry.hl.fog_man", bones, bounds=(3.0, 3.5, (0, 1.6, 0)))


def cave_dweller_model():
    """A pale thing that moves on four long, jointed limbs, elbows higher than
    its spine, with a ridged back and a many-eyed head on a long neck."""
    spike = lambda x, y, z: Cube((x, y, z), (2, 2, 2), (52, 0))
    bones = [
        Bone("root"),
        Bone("body", "root", (0, 12, 4), cubes=[
            Cube((-3, 10, 0), (6, 5, 9), (0, 16)),
            spike(-1, 14.5, 1.5), spike(-1, 14.5, 5)]),
        Bone("chest", "body", (0, 12.5, 0), rotation=(-8, 0, 0), cubes=[
            Cube((-4, 10, -9), (8, 6, 9), (0, 32)),
            spike(-1, 15.5, -3), spike(-1, 15.5, -6.5)]),
        Bone("neck", "chest", (0, 13.5, -9), rotation=(-12, 0, 0), cubes=[Cube((-1.5, 12, -14), (3, 3, 5), (30, 16))]),
        Bone("head", "neck", (0, 13.5, -14), rotation=(16, 0, 0), cubes=[Cube((-3.5, 11, -22), (7, 6, 8), (0, 0))]),
        Bone("jaw", "head", (0, 11.5, -16), rotation=(6, 0, 0), cubes=[Cube((-2.5, 9, -21.5), (5, 2, 6), (30, 0))]),
    ]
    limbs = [("front", "chest", 5, 14, -6, 118, 22), ("back", "body", 4, 13, 6, 112, -22)]
    for pos, parent, px, py, pz, splay, yaw in limbs:
        for side, name in ((-1, "Right"), (1, "Left")):
            bones += [
                Bone(f"{pos}{name}Upper", parent, (px * side, py, pz), rotation=(0, yaw * side, -splay * side), cubes=[
                    Cube((side_x(side, px - 1, px + 1), py - 10, pz - 1), (2, 10, 2), (48, 16))]),
                Bone(f"{pos}{name}Lower", f"{pos}{name}Upper", (px * side, py - 10, pz), rotation=(0, 0, (splay - 8) * side), cubes=[
                    Cube((side_x(side, px - 1, px + 1), py - 28, pz - 1), (2, 18, 2), (56, 16)),
                    Cube((side_x(side, px - 0.5, px + 0.5), py - 10, pz - 0.5), (1, 3, 1), (60, 0)),
                    *[Cube((side_x(side, px - 1 + a, px + a), py - 28, pz - 4), (1, 1, 4), (34, 32)) for a in (-0.5, 0.5, 1.5)]]),
            ]
    return Model("geometry.hl.cave_dweller", bones, bounds=(3.0, 2.0, (0, 0.8, 0)))


MODELS = {
    "herobrine": player_model(),
    "null": null_model(),
    "fog_man": fog_man_model(),
    "cave_dweller": cave_dweller_model(),
}


# =========================================================================== #
# Textures
# =========================================================================== #

def grad(c, y, fh, top=1.06, bottom=0.88):
    return shade(c, top + (bottom - top) * y / max(1, fh - 1))


# ---------------------------- Herobrine ------------------------------------ #

def paint_herobrine(seed):
    """An ordinary miner, except for the eyes."""
    rng = random.Random(seed)
    cv = Canvas(64, 64)
    skin, hair, hair_l = (0xB4, 0x85, 0x68), (0x2B, 0x1D, 0x10), (0x42, 0x2E, 0x1A)
    shirt, shirt_d = (0x00, 0x9E, 0xA2), (0x00, 0x74, 0x78)
    pants, pants_d = (0x3E, 0x36, 0x96), (0x2C, 0x26, 0x70)
    shoe, shoe_d = (0x5C, 0x5C, 0x62), (0x3E, 0x3E, 0x44)
    face = [
        "HHHHHHHH",
        "HHHHHHHH",
        "HSSSSSSH",
        "SbbSSbbS",
        "SWWSSWWS",
        "SSSnnSSS",
        "SSmMMmSS",
        "SSSmmSSS",
    ]
    fpal = {"H": hair, "S": skin, "b": shade(skin, 0.88), "W": GLOW_WHITE, "n": shade(skin, 0.8),
            "m": (0x7A, 0x52, 0x3A), "M": (0x5A, 0x38, 0x26)}

    def fn(bone, i, face_, x, y, fw, fh):
        outer = i == 1
        if bone == "head":
            if outer:  # hair volume
                if face_ == "top" and (x in (0, fw - 1) or y in (0, fh - 1)) and rng.random() < 0.5:
                    return jitter(rng, hair_l, 6)
                if face_ in ("right", "left", "back") and y < 3 and rng.random() < 0.35:
                    return jitter(rng, hair_l, 6)
                if face_ == "front" and y == 0 and x in (0, 1, 6, 7):
                    return jitter(rng, hair_l, 6)
                return CLEAR
            if face_ == "front":
                c = fpal[face[y][x]]
                return c if len(c) == 4 else jitter(rng, c, 4)
            if face_ == "top":
                return jitter(rng, hair_l if rng.random() < 0.2 else hair, 4)
            if face_ == "bottom":
                return shade(skin, 0.8)
            if face_ == "back":
                return jitter(rng, hair if y < 7 else shade(skin, 0.9), 4)
            back_half = x < 4 if face_ == "right" else x >= 4
            if y < 3 or (back_half and y < 6):
                return jitter(rng, hair, 4)
            if y == 4 and not back_half and x in (3, 4):
                return shade(skin, 0.82)  # ear
            return jitter(rng, grad(skin, y, fh), 3)
        if bone == "body":
            if outer:
                if y == fh - 1 and face_ not in ("top", "bottom"):
                    return shirt_d
                if face_ == "front" and y == 0 and x in (2, 5):
                    return shirt_d
                return CLEAR
            if face_ == "front" and y < 2 and 3 <= x <= 4:
                return shade(skin, 0.95)  # open collar
            if face_ == "bottom":
                return pants
            c = grad(shirt, y, fh)
            if face_ == "front" and x in (2, 5) and 3 <= y <= 10:
                c = shade(c, 0.9)
            return jitter(rng, c, 3)
        if bone in ("rightArm", "leftArm"):
            if outer:
                return shirt_d if y == 3 and face_ not in ("top", "bottom") else CLEAR
            if face_ == "top" or (face_ != "bottom" and y < 4):
                return jitter(rng, grad(shirt, y, 4), 3)
            if face_ == "bottom" or y >= 10:
                return jitter(rng, shade(skin, 0.86), 3)
            return jitter(rng, grad(skin, y, fh), 3)
        if bone in ("rightLeg", "leftLeg"):
            if outer:
                return pants_d if y == 9 and face_ not in ("top", "bottom") else CLEAR
            if face_ == "bottom":
                return shoe_d
            if face_ != "top" and y >= 10:
                return jitter(rng, shoe if y == 10 else shoe_d, 3)
            c = grad(pants, y, 10)
            if face_ == "front" and x == (3 if bone == "rightLeg" else 0):
                c = shade(c, 0.88)  # inner seam
            return jitter(rng, c, 3)
        return None

    paint_model(cv, MODELS["herobrine"], fn)
    return cv


# ------------------------------- null -------------------------------------- #

def paint_null(seed):
    """A hole in the world shaped like a player, shedding broken pixels."""
    rng = random.Random(seed)
    cv = Canvas(64, 64)
    glitch = [(0xF8, 0x00, 0xF8), (0x00, 0xE0, 0xE0), (0x00, 0x00, 0x00)]
    tears = {}

    def fn(bone, i, face_, x, y, fw, fh):
        if bone == "glitch":
            return (0xF8, 0x00, 0xF8) if (x + y) % 2 == 0 else (0, 0, 0)
        if i == 1:  # outer layer: scanline tears
            if bone == "head" and face_ == "front" and 2 <= y <= 6:
                return CLEAR  # keep the eyes visible
            key = (bone, face_, y)
            if key not in tears:
                tears[key] = None
                if rng.random() < 0.13:
                    start = rng.randint(0, max(0, fw - 2))
                    tears[key] = (start, start + rng.randint(1, 4), rng.choice(glitch))
            t = tears[key]
            return t[2] if t and t[0] <= x < t[1] else CLEAR
        if bone == "head" and face_ == "front":
            if y == 4 and x in (1, 6):
                return GLOW_WHITE
            if y in (5, 6) and x in (1, 6) and rng.random() < 0.6:
                return (0x26, 0x26, 0x2A)  # something leaking from the eyes
        g = rng.randint(5, 20)
        return (g, g, g + 2)

    paint_model(cv, MODELS["null"], fn)
    return cv


# ------------------------ The Man From The Fog ----------------------------- #

def paint_fog_man(seed):
    rng = random.Random(seed)
    cv = Canvas(64, 64)
    S = (0xC8, 0xC5, 0xBC)
    S_d = shade(S, 0.78)
    vein = (0x9A, 0xA4, 0xB2)
    cloth, cloth_l = (0x2A, 0x2A, 0x2F), (0x3B, 0x3B, 0x42)
    teeth, mouth = (0xDB, 0xD4, 0xC0), (0x2C, 0x08, 0x0B)
    rope = (0x5E, 0x50, 0x3C)

    def pale(y, fh, top=1.04, bottom=0.86):
        c = jitter(rng, grad(S, y, fh, top, bottom), 4)
        r = rng.random()
        if r < 0.04:
            return shade(c, 0.88)
        if r < 0.055:
            return mix(c, vein, 0.6)
        return c

    head_front = [
        "SSSSSS",
        "sSSSSs",
        "KKssKK",
        "KKSSKK",
        "KkSSkK",
        "SkNNkS",
        "SSSSSS",
        "KWKKWK",
    ]
    hpal = {"S": None, "s": S_d, "K": BLACK, "k": (0x3A, 0x38, 0x3A), "N": (0x22, 0x1C, 0x1E), "W": teeth}

    def fn(bone, i, face_, x, y, fw, fh):
        if bone == "head":
            if face_ == "front":
                c = hpal[head_front[y][x]]
                return c or pale(y, fh)
            if face_ == "bottom":
                return mouth
            # The grin wraps round the sides of the face.
            near_front = (face_ == "right" and x >= fw - 2) or (face_ == "left" and x <= 1)
            if y == fh - 1 and near_front:
                return BLACK
            return pale(y, fh)
        if bone == "jaw":
            if face_ == "front":
                return (teeth if x % 2 == 0 else BLACK) if y == 0 else pale(y, fh)
            if face_ == "top":
                return mouth
            if face_ in ("right", "left") and y == 0:
                return BLACK
            return shade(pale(y, fh), 0.9)
        if bone == "neck":
            if face_ == "front" and x in (0, fw - 1):
                return S_d  # tendons
            return pale(y, fh)
        if bone == "chest":
            c = pale(y, fh)
            if face_ == "front":
                if y == 0 and x in (1, 2, 5, 6):
                    return S_d  # collarbones
                if y in (2, 4, 6) and x not in (3, 4):
                    return S_d  # ribs
                if y == 8:
                    return shade(c, 0.85)
            if face_ in ("right", "left") and y in (2, 4, 6):
                return S_d
            if face_ == "back" and x in (3, 4) and y % 2 == 0:
                return S_d  # spine
            return c
        if bone == "body":
            if y < 2 or face_ in ("top",):
                return pale(y, fh)
            if y == 2 or face_ == "bottom":
                return jitter(rng, rope, 6) if face_ != "bottom" else cloth
            return jitter(rng, cloth_l if rng.random() < 0.2 else cloth, 4)
        if bone.endswith("Leg"):
            if face_ in ("top",):
                return cloth
            if rng.random() < 0.07:
                return pale(y, fh)  # torn through
            return jitter(rng, cloth_l if x == 1 else cloth, 4)
        if bone.endswith("Shin"):
            if i == 1:  # bare foot, grey toes
                return (0x4A, 0x44, 0x3E) if face_ == "front" else pale(1, 2, 0.9, 0.8)
            if face_ == "bottom":
                return (0x55, 0x50, 0x4A)
            if face_ == "top" or y < 3:
                return jitter(rng, cloth, 4)
            if y == 3:
                return jitter(rng, cloth, 4) if rng.random() < 0.5 else pale(y, fh)  # ragged hem
            c = pale(y, fh, 1.02, 0.8)
            if face_ == "front" and x == 1:
                c = shade(c, 1.06)  # shin bone
            return shade(c, 0.8) if y == fh - 1 else c
        if bone.endswith("Forearm"):
            return S_d if y < 2 and face_ != "top" else pale(y, fh)
        if bone.endswith("Arm"):
            return pale(y, fh)
        if bone.endswith("Hand"):
            if i == 0:
                return pale(y, fh, 0.96, 0.86)
            return (0x3A, 0x36, 0x30) if (y == fh - 1 or face_ == "bottom") else pale(y, fh, 0.95, 0.85)
        return None

    paint_model(cv, MODELS["fog_man"], fn)
    return cv


# ------------------------- The Cave Dweller -------------------------------- #

def paint_cave_dweller(seed):
    rng = random.Random(seed)
    cv = Canvas(64, 64)
    D = (0xA8, 0xA6, 0x9B)
    D_d = shade(D, 0.72)
    wet = (0xCE, 0xCC, 0xC2)
    vein = (0x8C, 0x94, 0x98)
    teeth, mouth, bone_c = (0xE0, 0xD8, 0xC4), (0x3C, 0x0C, 0x10), (0xDE, 0xD6, 0xC2)

    def skin(y, fh, top=1.04, bottom=0.84):
        c = jitter(rng, grad(D, y, fh, top, bottom), 6)
        r = rng.random()
        if r < 0.05:
            return wet
        if r < 0.1:
            return shade(c, 0.84)
        if r < 0.13:
            return vein
        return c

    skull_front = [
        "SkSSSkS",
        "KKSSSKK",
        "KKSkSKK",
        "SSSSSSS",
        "SSnSnSS",
        "WKWKWKW",
    ]
    spal = {"S": None, "k": BLACK, "K": BLACK, "n": (0x30, 0x2A, 0x2A), "W": teeth}

    def fn(bone, i, face_, x, y, fw, fh):
        if bone == "head":
            if face_ == "front":
                return spal[skull_front[y][x]] or skin(y, fh)
            if face_ == "top" and x in (2, 3):
                return D_d  # ridge
            if face_ in ("right", "left") and x % 2 == 0 and y < 3:
                return D_d
            if face_ == "bottom":
                return mouth
            return skin(y, fh)
        if bone == "jaw":
            if face_ == "front":
                return (BLACK if x % 2 == 0 else teeth) if y == 0 else skin(y, fh)
            if face_ == "top":
                return mouth
            if face_ in ("right", "left") and rng.random() < 0.3:
                return (0x4A, 0x16, 0x18)  # something it ate
            return skin(y, fh, 0.9, 0.8)
        if bone == "neck":
            return D_d if (face_ in ("right", "left", "top", "bottom") and x % 2 == 0) else skin(y, fh)
        if bone in ("body", "chest"):
            if i > 0:  # spine spikes
                return (0x6A, 0x62, 0x54) if face_ == "top" else jitter(rng, bone_c, 5)
            if face_ == "top":
                return D_d if x in (fw // 2 - 1, fw // 2) and y % 2 == 0 else skin(y, fh)
            if face_ == "bottom":
                return jitter(rng, shade(wet, 0.95), 4)
            if face_ in ("right", "left") and bone == "chest" and x % 2 == 0 and 1 <= y <= 4:
                return D_d  # ribs
            if face_ in ("right", "left") and bone == "body" and y == 2 and rng.random() < 0.7:
                return D_d  # skin folds
            return skin(y, fh)
        if bone.endswith("Upper"):
            return D_d if y >= fh - 2 else skin(y, fh)
        if bone.endswith("Lower"):
            if i == 1:  # elbow spur
                return (0x5A, 0x52, 0x46) if face_ == "top" else jitter(rng, bone_c, 5)
            if i > 1:  # claws
                return BLACK if face_ == "front" else jitter(rng, shade(bone_c, 0.9), 4)
            if y >= fh - 2 or face_ == "bottom":
                return (0x2A, 0x26, 0x24)
            return skin(y, fh, 1.02, 0.55)
        return None

    paint_model(cv, MODELS["cave_dweller"], fn)
    return cv


# ------------------------- Blocks and items -------------------------------- #

def paint_corrupted(seed):
    """The missing-texture checkerboard, with scanline tears."""
    rng = random.Random(seed)
    cv = Canvas(16, 16)
    for y in range(16):
        shift = rng.choice([0, 0, 0, 0, 2, 3, -2]) if y % 4 == 1 else 0
        for x in range(16):
            sx = (x + shift) % 16
            magenta = ((sx // 8) + (y // 8)) % 2 == 0
            c = (0xF8, 0x00, 0xF8) if magenta else (0x00, 0x00, 0x00)
            if rng.random() < 0.03:
                c = (0x00, 0xE0, 0xE0)
            cv.set(x, y, c)
    return cv


def paint_journal(seed):
    rng = random.Random(seed)
    cv = Canvas(16, 16)
    leather, edge = (0x5A, 0x36, 0x22), (0x36, 0x20, 0x12)
    for y in range(1, 15):
        for x in range(2, 14):
            c = jitter(rng, grad(leather, y - 1, 14, 1.1, 0.85), 6)
            if x in (2, 13) or y in (1, 14):
                c = edge
            cv.set(x, y, c)
    for y in range(2, 14):
        cv.set(13, y, (0xE8, 0xDF, 0xC8) if y % 2 else (0xC8, 0xBE, 0xA4))  # page edges
        cv.set(3, y, (0x2A, 0x18, 0x0E))                                    # spine
        if y % 2 == 0:
            cv.set(4, y, (0xB8, 0xA8, 0x88))                                # stitching
    cv.rect(12, 7, 3, 2, (0x2A, 0x18, 0x0E))                                # strap
    cv.set(14, 7, (0xD0, 0xA8, 0x48))                                       # buckle
    cv.set(14, 8, (0x9A, 0x78, 0x30))
    eye = [
        "..####..",
        ".#....#.",
        "#..rr..#",
        ".#.rr.#.",
        "..####..",
    ]
    for y, row in enumerate(eye):
        for x, ch in enumerate(row):
            if ch == "#":
                cv.set(5 + x, 5 + y, (0xE0, 0xD6, 0xBC))
            elif ch == "r":
                cv.set(5 + x, 5 + y, (0xC0, 0x14, 0x14))
    cv.set(8, 7, (0x10, 0x04, 0x04))
    for (x, y) in [(6, 12), (7, 11), (11, 3)]:
        cv.set(x, y, shade(leather, 1.35))                                  # scratches
    return cv


def paint_flashlight():
    cv = Canvas(16, 16)
    body, hi, lo = (0x92, 0x94, 0x9C), (0xCC, 0xCE, 0xD4), (0x46, 0x48, 0x50)
    for i in range(8):                               # diagonal barrel, bottom-left to top-right
        x, y = 2 + i, 12 - i
        cv.set(x, y, hi)
        cv.set(x + 1, y, body)
        cv.set(x, y + 1, body)
        cv.set(x + 1, y + 1, lo)
        if i % 2 == 0 and i < 5:
            cv.set(x + 1, y, lo)                     # grip rings
    cv.set(5, 10, (0xD0, 0x22, 0x22))                # switch
    cv.rect(9, 2, 4, 4, (0x30, 0x32, 0x38))          # head
    cv.set(9, 2, (0x60, 0x62, 0x6A))
    cv.rect(10, 3, 2, 2, (0xFF, 0xE8, 0x70))         # lens
    cv.set(11, 3, (0xFF, 0xFF, 0xE0))
    for (x, y, a) in [(13, 1, 160), (14, 0, 110), (13, 2, 120), (12, 0, 110), (15, 0, 70), (14, 2, 70)]:
        cv.set(x, y, (0xFF, 0xF6, 0xB8, a))          # beam
    return cv


# ----------------------------- Pack icon ----------------------------------- #

FOG_WATCH_POSE = {"head": (0, 0, 18), "jaw": (6, 0, 0), "chest": (6, 0, 0)}


def paint_pack_icon(textures, seed):
    rng = random.Random(seed)
    cv = Canvas(128, 128)
    for y in range(128):
        for x in range(128):
            fog = int(16 + 72 * (y / 127) ** 1.5 + rng.randint(-4, 4))
            cv.set(x, y, (fog, fog + 2, fog + 4))
    man = render(MODELS["fog_man"], textures["fog_man"], size=(60, 112), yaw=18, pitch=-6, pose=FOG_WATCH_POSE)
    cv.paste(man, 40, 10)
    # Fog swallowing his legs.
    for y in range(64, 128):
        t = min(1.0, (y - 64) / 56) * 0.75
        for x in range(128):
            cv.blend(x, y, (0x5C, 0x60, 0x64), t)
    # White eyes in the dark, off to the left.
    cv.rect(12, 44, 3, 2, (0xFF, 0xFF, 0xFF))
    cv.rect(19, 44, 3, 2, (0xFF, 0xFF, 0xFF))
    # A tear of missing texture in the corner.
    for y in range(0, 22):
        for x in range(102 + (y % 5), 128):
            magenta = ((x // 6) + (y // 6)) % 2 == 0
            cv.set(x, y, (0xF8, 0x00, 0xF8) if magenta else (0, 0, 0))
    for y in range(128):
        for x in range(128):
            if rng.random() < 0.05:
                cv.set(x, y, jitter(rng, cv.get(x, y), 22))
    return cv


# =========================================================================== #
# Output
# =========================================================================== #

def write_models():
    errors = [e for m in MODELS.values() for e in m.check_uv()]
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [m.to_json() for m in MODELS.values()]}
    path = RP / "models" / "entity" / "hl.geo.json"
    path.write_text(json.dumps(geo, indent=2) + "\n", encoding="utf-8")


def paint_textures():
    textures = {
        "herobrine": paint_herobrine(10),
        "null": paint_null(20),
        "fog_man": paint_fog_man(30),
        "cave_dweller": paint_cave_dweller(40),
    }
    ent = RP / "textures" / "entity" / "hl"
    for name, tex in textures.items():
        tex.save(ent / f"{name}.png")
    paint_corrupted(50).save(RP / "textures" / "blocks" / "hl" / "corrupted_block.png")
    items = RP / "textures" / "items" / "hl"
    paint_journal(60).save(items / "journal.png")
    paint_flashlight().save(items / "flashlight.png")
    icon = paint_pack_icon(textures, 70)
    icon.save(BP / "pack_icon.png")
    icon.save(RP / "pack_icon.png")
    return textures


PREVIEW_POSES = {
    "herobrine": [("", {})],
    "null": [("", {})],
    "fog_man": [("watch", FOG_WATCH_POSE),
                ("sprint", {"body": (30, 0, 0), "chest": (10, 0, 0), "neck": (-20, 0, 0), "head": (-25, 0, 0),
                            "jaw": (30, 0, 0), "rightArm": (70, 0, 0), "leftArm": (20, 0, 0),
                            "rightLeg": (-35, 0, 0), "rightShin": (30, 0, 0), "leftLeg": (25, 0, 0), "leftShin": (50, 0, 0)})],
    "cave_dweller": [("stalk", {}), ("chase", {"jaw": (30, 0, 0), "head": (-10, 0, 0)})],
}


def write_previews(textures, directory):
    """One sheet per model: front three-quarter, side and back views."""
    directory.mkdir(parents=True, exist_ok=True)
    for name, model in MODELS.items():
        views = []
        for label, pose in PREVIEW_POSES[name]:
            for yaw, pitch in ((25, -12), (90, -8), (200, -12)):
                views.append(render(model, textures[name], size=(200, 240), yaw=yaw, pitch=pitch, pose=pose,
                                    background=(0x3A, 0x3C, 0x44, 255)))
        sheet = Canvas(200 * 3, 240 * (len(views) // 3), (0x20, 0x20, 0x24, 255))
        for k, view in enumerate(views):
            sheet.paste(view, (k % 3) * 200, (k // 3) * 240)
        big = Canvas(64 * 4, 64 * 4, (0x50, 0x50, 0x58, 255))
        tex = textures[name]
        for y in range(256):
            for x in range(256):
                c = tex.get(x * tex.w // 256, y * tex.h // 256)
                if c[3]:
                    big.set(x, y, c[:3])
        sheet.save(directory / f"{name}.png")
        big.save(directory / f"{name}_texture.png")
    print(f"previews in {directory}")


def validate():
    errors = 0
    for path in sorted(list(BP.rglob("*.json")) + list(RP.rglob("*.json"))):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            print(f"invalid JSON: {path.relative_to(ROOT)}: {e}")
            errors += 1
    for lang in sorted(list(BP.rglob("*.lang")) + list(RP.rglob("*.lang"))):
        for n, text in enumerate(lang.read_text(encoding="utf-8").splitlines(), 1):
            if text.strip() and not text.startswith("##") and "=" not in text:
                print(f"bad lang line: {lang.relative_to(ROOT)}:{n}")
                errors += 1
    if errors:
        sys.exit(1)


def package():
    DIST.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(DIST, "w", zipfile.ZIP_DEFLATED) as z:
        for src, name in ((BP, "HorrorLegends_BP"), (RP, "HorrorLegends_RP")):
            for path in sorted(src.rglob("*")):
                if path.is_file():
                    z.write(path, f"{name}/{path.relative_to(src).as_posix()}")
    with zipfile.ZipFile(DIST_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(DIST, DIST.name)
    for f in (DIST, DIST_ZIP):
        print(f"wrote {f.relative_to(ROOT)} ({f.stat().st_size} bytes)")


if __name__ == "__main__":
    write_models()
    textures = paint_textures()
    if "--preview" in sys.argv:
        write_previews(textures, Path(sys.argv[sys.argv.index("--preview") + 1]))
    if "--textures" not in sys.argv and "--preview" not in sys.argv:
        validate()
        package()
