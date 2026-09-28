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

from animations import ANIMATIONS
from geometry import Bone, Cube, Model, render
from pixels import Canvas, box_faces, jitter, mix, paint_box, shade

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
    """fn(bone, tag, face, x, y, fw, fh) -> colour or None, for every cube.
    `tag` is the cube's share key: what part of the body it is."""
    for bone in model.bones:
        for cube in bone.cubes:
            w, h, d = (int(s) for s in cube.size)
            paint_box(cv, *cube.uv, w, h, d,
                      lambda face, x, y, fw, fh, b=bone.name, t=cube.share: fn(b, t, face, x, y, fw, fh))


def C(origin, size, tag, **kw):
    return Cube(origin, size, share=tag, **kw)


def grad(c, y, fh, top=1.06, bottom=0.88):
    return shade(c, top + (bottom - top) * y / max(1, fh - 1))


def bake(cv, model):
    """Pixel-art ambient occlusion: darken the rim of every face a little,
    lift top faces and sink bottom ones. Outer skin layers and anything
    emissive or transparent are left alone."""
    done = set()
    for bone in model.bones:
        for cube in bone.cubes:
            if cube.inflate:
                continue
            w, h, d = (int(v) for v in cube.size)
            for face, (x0, y0, fw, fh) in box_faces(*cube.uv, w, h, d).items():
                if (x0, y0, fw, fh) in done or fw < 3 or fh < 3:
                    continue
                done.add((x0, y0, fw, fh))
                lift = {"top": 1.05, "bottom": 0.9}.get(face, 1.0)
                for y in range(fh):
                    for x in range(fw):
                        c = cv.get(x0 + x, y0 + y)
                        if c[3] != 255:
                            continue
                        edges = (x in (0, fw - 1)) + (y in (0, fh - 1))
                        cv.set(x0 + x, y0 + y, shade(c, lift * (1.0 - 0.07 * edges)))
    return cv


def face_rows(rows, palette, x, y, fallback):
    c = palette.get(rows[y][x])
    return fallback() if c is None else c


# =========================================================================== #
# Herobrine: what the mine left of a miner
# =========================================================================== #

def herobrine_model():
    """Almost three blocks of what the mine left of a miner. Stooped and
    twitching. The stomach is torn open and stitched badly, guts spilling out
    and one loop hanging. The burned left half of the face has fallen away to
    the skull, with an empty socket where something still glows; the jaw is
    dislocated, hanging sideways under a row of fangs, dripping. Broken ribs
    jut out of the chest, the spine runs outside the body from the waist to
    the skull, a pickaxe head is buried in his back, nails are driven into his
    scalp, and his burned left hand ends in claws. He still drags his own
    pickaxe."""
    o = 2  # everything above the legs sits two pixels higher than a player
    blister = lambda x, y, z: C((x, y + o, z), (1, 1, 1), "blister")
    vertebra = lambda y, z=2: C((-1, y + o, z), (2, 1, 2), "vertebra")
    spike = lambda y: C((-0.5, y + o, 4), (1, 1, 1), "spinous")
    rib = lambda x, y, z, length: C((x, y + o, z), (1, 1, length), "rib")
    return Model("geometry.hl.herobrine", [
        Bone("root"),
        Bone("rightLeg", "root", (-2, 20, 0), cubes=[
            C((-4, 0, -2), (4, 20, 4), "leg"), C((-4, 0, -2), (4, 20, 4), "leg+", inflate=0.25)]),
        Bone("leftLeg", "root", (2, 20, 0), cubes=[
            C((0, 0, -2), (4, 20, 4), "leg"), C((0, 0, -2), (4, 20, 4), "leg+", inflate=0.25)]),
        # The belly is hollow: its front sits a pixel back, framed by torn flaps.
        Bone("body", "root", (0, 18 + o, 0), rotation=(7, 0, 0), cubes=[
            C((-4, 18 + o, -1), (8, 6, 3), "belly"),
            C((-4, 18 + o, -2), (1, 6, 1), "wound_edge"), C((3, 18 + o, -2), (1, 6, 1), "wound_edge"),
            C((-3, 23 + o, -2), (6, 1, 1), "wound_top"),
            C((-2.5, 19 + o, -1.7), (2, 1, 1), "gut"), C((0.5, 19.5 + o, -1.8), (2, 1, 1), "gut"),
            C((-1, 20.8 + o, -2.1), (2, 1, 1), "gut"), C((-2, 22 + o, -1.6), (2, 1, 1), "gut"), C((1, 22 + o, -1.9), (2, 1, 1), "gut"),
            vertebra(19), vertebra(21), vertebra(23)]),
        Bone("gutHang", "body", (0, 19 + o, -1.5), cubes=[C((-0.5, 13 + o, -2), (1, 6, 1), "gut_hang")]),
        Bone("chest", "body", (0, 24 + o, 0), rotation=(9, 0, -3), cubes=[
            C((-4, 24 + o, -2), (8, 8, 4), "chest"), C((-4, 24 + o, -2), (8, 8, 4), "chest+", inflate=0.25),
            vertebra(25), vertebra(27), vertebra(29), vertebra(31),
            spike(25.5), spike(27.5), spike(29.5),
            rib(1, 25, -4.5, 3), rib(2.5, 26.5, -4, 2), rib(-2.5, 25.5, -4, 2),
            # A pickaxe head buried in his back, handle snapped off.
            C((0.5, 29 + o, 2.5), (7, 1, 1), "lodged_pick", pivot=(4, 29.5 + o, 3), rotation=(0, 0, 32)),
            C((3.5, 29 + o, 3), (1, 1, 4), "lodged_handle", pivot=(4, 29.5 + o, 3), rotation=(25, 0, 0)),
            blister(3, 31.3, -1), blister(4.1, 29, 0.5)]),
        Bone("head", "chest", (0, 32 + o, 0), rotation=(12, 0, 6), cubes=[
            C((-4, 32 + o, -4), (8, 8, 8), "head"), C((-4, 32 + o, -4), (8, 8, 8), "head+", inflate=0.5),
            vertebra(32.5, 3.5),
            C((1.5, 35 + o, -4.4), (2, 2, 1), "cheekbone"),  # skull showing through
            C((-2, 30.5 + o, -4.3), (1, 2, 1), "fang"), C((1, 30.5 + o, -4.3), (1, 2, 1), "fang"),
            C((-2.5, 40 + o, -1), (1, 1, 1), "nail"), C((-1, 40 + o, 1.5), (1, 1, 1), "nail"),
            blister(3, 37.5, -4.5), blister(4.1, 35.5, -2), blister(4.1, 38, 1)]),
        # Dislocated: hanging open and off to one side.
        Bone("jaw", "head", (0, 33 + o, 1), rotation=(22, 0, 14), cubes=[C((-3, 31 + o, -4.5), (6, 2, 4), "jaw")]),
        Bone("dripL", "jaw", (1.5, 31 + o, -4), cubes=[C((1, 27 + o, -4.2), (1, 4, 1), "drip")]),
        Bone("dripR", "jaw", (-1.5, 31 + o, -4), cubes=[C((-2, 28.5 + o, -4.2), (1, 2.5, 1), "drip")]),
        Bone("rightArm", "chest", (-5.5, 31 + o, 0), rotation=(-5, 0, 0), cubes=[
            C((-8, 15 + o, -2), (4, 16, 4), "arm_r"), C((-8, 15 + o, -2), (4, 16, 4), "sleeve_r", inflate=0.25)]),
        Bone("leftArm", "chest", (5.5, 31 + o, 0), rotation=(-8, 0, -4), cubes=[
            C((4, 15 + o, -2), (4, 16, 4), "arm_l"),
            *[C((a, 11 + o, -1.5), (1, 4, 1), "claw") for a in (4.2, 5.6, 7)],
            blister(8, 24, -1), blister(8, 19, 1), blister(5, 27, -2.6), blister(8, 28, 0.5)]),
        Bone("pickaxe", "rightArm", (-6, 16 + o, 0), rotation=(-18, 0, 0), cubes=[
            C((-6.5, 3, -0.5), (1, 15, 1), "handle"),
            C((-6.5, 1, -4.5), (1, 2, 9), "pick"),
            C((-6.5, 3, -4.5), (1, 1, 1), "tip"), C((-6.5, 3, 3.5), (1, 1, 1), "tip")]),
    ], bounds=(2.5, 3.2, (0, 1.6, 0))).pack_uv(128)


def paint_herobrine(seed):
    rng = random.Random(seed)
    cv = Canvas(*MODELS["herobrine"].texture)
    skin, hair, hair_l = (0x96, 0x80, 0x68), (0x22, 0x17, 0x0D), (0x38, 0x27, 0x16)
    burn, crust, raw = (0xA6, 0x2A, 0x22), (0x56, 0x14, 0x10), (0xD2, 0x4C, 0x3C)
    blister_c = (0xE6, 0xD4, 0xA4)
    shirt, dirt, blood = (0x1E, 0x84, 0x86), (0x5A, 0x48, 0x34), (0x6A, 0x0E, 0x10)
    pants, boot = (0x2E, 0x2A, 0x72), (0x44, 0x3A, 0x32)
    gut, gut_d, cavity = (0xC8, 0x6A, 0x70), (0x94, 0x3E, 0x46), (0x3A, 0x08, 0x0A)
    bone_c, muscle = (0xE0, 0xD8, 0xC2), (0xB0, 0x3A, 0x3A)
    thread = (0x1A, 0x14, 0x10)

    def grime(c, p=0.12):
        r = rng.random()
        if r < p:
            return mix(c, dirt, rng.uniform(0.3, 0.6))
        if r < p + 0.02:
            return mix(c, blood, 0.6)
        return jitter(rng, c, 4)

    def burned():
        r = rng.random()
        if r < 0.12:
            return crust
        if r < 0.22:
            return raw
        if r < 0.25:
            return blister_c
        return jitter(rng, burn, 10)

    def flesh(y, fh):
        return jitter(rng, grad(skin, y, fh, 1.0, 0.84), 4)

    # Front of the head. The model's left (burned) side is on the right here:
    # skull showing (B), an empty socket (K) with a glint deep inside (g).
    face = [
        "HHHHrrrr",
        "HhHHrBBr",
        "SSSSrBBc",
        "SbbSrKKr",
        "SWWSrKgr",
        "SddScKKr",
        "TKTTKTTK",
        "KKKKKKKK",
    ]
    fpal = {"H": hair, "h": hair_l, "b": shade(skin, 0.7), "W": GLOW_WHITE, "d": shade(skin, 0.7),
            "T": (0xD8, 0xCC, 0xA8), "K": (0x10, 0x04, 0x04), "c": crust, "B": bone_c, "g": (0xFF, 0xE8, 0xE8, 40)}

    def fn(bone, tag, face_, x, y, fw, fh):
        left_side = (face_ == "left") or (face_ in ("front", "top", "bottom") and x >= fw // 2) or (face_ == "back" and x < fw // 2)
        if tag == "head":
            if face_ == "front":
                ch = face[y][x]
                if ch == "r":
                    return burned()
                c = fpal.get(ch)
                return c if c is not None else flesh(y, fh)
            if left_side:
                return bone_c if (face_ == "left" and 2 <= x <= 4 and 2 <= y <= 4) else burned()
            if face_ == "top" or y < 3 or face_ == "back" and y < 6:
                return jitter(rng, hair, 4)
            return flesh(y, fh)
        if tag == "head+":  # matted hair, only where it didn't burn off
            if left_side or face_ == "bottom":
                return CLEAR
            if face_ == "top":
                return jitter(rng, hair_l, 5) if rng.random() < 0.4 else CLEAR
            if face_ == "front":
                return jitter(rng, hair, 4) if y == 0 else CLEAR
            return jitter(rng, hair, 4) if y < 3 and rng.random() < 0.5 else CLEAR
        if tag == "cheekbone":
            return jitter(rng, bone_c, 5) if face_ != "back" else raw
        if tag == "fang":
            return (0xD8, 0xCC, 0xA8) if y < fh - 1 else (0xB8, 0xA8, 0x80)
        if tag == "nail":
            return (0x6A, 0x6A, 0x70) if face_ == "top" else (0x4A, 0x3A, 0x34)
        if tag == "jaw":
            if face_ == "front":
                if y == 0:
                    return (0xD8, 0xCC, 0xA8) if x % 3 != 2 else (0x10, 0x04, 0x04)
                return burned() if x >= fw // 2 else flesh(y, fh)
            if face_ == "top":
                return (0x4A, 0x10, 0x12)
            return burned() if left_side else flesh(y, fh)
        if tag == "drip":
            return jitter(rng, blood if y < fh - 1 else (0x4A, 0x06, 0x08), 6)
        if tag == "blister":
            return blister_c if face_ != "bottom" else raw
        if tag == "rib":
            return jitter(rng, bone_c, 5) if face_ != "back" else blood
        if tag in ("lodged_pick",):
            c = jitter(rng, (0x6A, 0x6A, 0x6E), 8)
            return mix(c, blood, 0.6) if rng.random() < 0.3 else c
        if tag == "lodged_handle":
            return jitter(rng, (0x4E, 0x38, 0x24), 5) if face_ != "back" else (0x8A, 0x70, 0x50)  # splintered end
        if tag == "belly":
            if face_ == "front":
                if (x + 2 * y) % 5 in (0, 1) and 0 < y < fh - 1:
                    return jitter(rng, gut if (x + y) % 2 else gut_d, 8)
                return jitter(rng, cavity, 6)
            if face_ == "back":
                return jitter(rng, (0x7A, 0x1A, 0x18), 6) if x in (3, 4) else grime(shade(shirt, 0.85))
            return grime(shade(shirt, 0.85)) if face_ != "bottom" else pants
        if tag == "wound_edge":
            if face_ in ("left", "right"):
                return jitter(rng, (0x8A, 0x1E, 0x1C), 8)
            if face_ == "front":
                if y % 2 == 0:
                    return thread  # crude stitches across the wound's edge
                return grime(shirt, 0.3) if y < 2 else flesh(y, fh)
            return grime(shade(shirt, 0.85))
        if tag == "wound_top":
            if face_ == "front" and x % 2 == 0:
                return thread
            return jitter(rng, blood, 8) if face_ in ("bottom", "front") else grime(shirt)
        if tag in ("gut", "gut_hang"):
            c = gut if (x + y) % 3 else gut_d
            return jitter(rng, c, 10) if not (tag == "gut_hang" and y == fh - 1) else blood
        if tag == "vertebra":
            return jitter(rng, bone_c, 6) if face_ != "bottom" else (0x7A, 0x1A, 0x18)
        if tag == "spinous":
            return jitter(rng, shade(bone_c, 0.92), 5)
        if tag == "chest":
            if face_ == "back" and x in (3, 4):
                return jitter(rng, (0x7A, 0x1A, 0x18), 8)
            if face_ == "front" and y >= 5:
                return shade(flesh(y, fh), 0.78) if y % 2 == 0 and x not in (3, 4) else flesh(y, fh)
            if face_ == "front" and y < 2 and 3 <= x <= 4:
                return flesh(y, fh)
            if left_side and rng.random() < 0.3:
                return burned()
            return grime(grad(shirt, y, fh, 1.02, 0.8))
        if tag == "chest+":
            if face_ in ("top", "bottom"):
                return CLEAR
            if face_ == "back" and 2 <= x <= 5:
                return CLEAR
            if y >= 5 and rng.random() < 0.6:
                return grime(shade(shirt, 0.75), 0.3)
            return CLEAR
        if tag == "arm_r":
            if face_ == "top" or (face_ != "bottom" and y < 4):
                return grime(grad(shirt, y, 4, 1.0, 0.85))
            if face_ == "bottom" or y >= fh - 2:
                return grime(shade(skin, 0.7), 0.3)
            if y >= 9:  # the forearm is flayed: bare muscle in strips
                return jitter(rng, muscle if x % 2 else shade(muscle, 0.7), 8)
            return flesh(y, fh)
        if tag == "sleeve_r":
            return grime(shade(shirt, 0.72), 0.3) if y == 3 and face_ not in ("top", "bottom") and rng.random() < 0.8 else CLEAR
        if tag == "arm_l":
            if face_ == "top" or (face_ != "bottom" and y < 2):
                return grime(shade(shirt, 0.8), 0.4)
            return burned() if face_ != "bottom" else crust
        if tag == "claw":
            return (0x16, 0x10, 0x0E) if y >= fh - 2 else (0x3A, 0x2A, 0x22)
        if tag == "leg":
            if face_ == "bottom":
                return shade(boot, 0.8)
            if face_ != "top" and y >= fh - 3:
                return jitter(rng, boot if y < fh - 1 else shade(boot, 0.75), 4)
            c = grad(pants, y, fh - 3, 1.02, 0.86)
            return mix(c, blood, 0.5) if (y < 5 and rng.random() < 0.06) else jitter(rng, c, 4)
        if tag == "leg+":
            return shade(boot, 1.1) if y == fh - 4 and face_ not in ("top", "bottom") else CLEAR
        if tag == "handle":
            if y < 3:
                return jitter(rng, (0x3A, 0x2E, 0x24), 4)
            return jitter(rng, shade((0x5C, 0x42, 0x2A), 0.85) if (x + y) % 4 == 0 else (0x5C, 0x42, 0x2A), 5)
        if tag == "pick":
            c = jitter(rng, grad((0x70, 0x70, 0x74), y, fh, 1.1, 0.8), 8)
            if rng.random() < 0.18:
                return mix(c, blood, 0.7)
            return shade(c, 0.6) if rng.random() < 0.12 else c
        if tag == "tip":
            return (0x48, 0x48, 0x4C)
        return None

    paint_model(cv, MODELS["herobrine"], fn)
    return cv


# =========================================================================== #
# null: a player-shaped render error
# =========================================================================== #

def null_model():
    """Sliced into three torso bands that don't line up, one arm longer than
    the other, head cocked, and a halo of stray pixels and missing texture."""
    return Model("geometry.hl.null", [
        Bone("root"),
        Bone("rightLeg", "root", (-2, 12, 0), cubes=[C((-4, 0, -2), (4, 12, 4), "leg")]),
        Bone("leftLeg", "root", (2, 12, 0), cubes=[C((0, 0, -2), (4, 12, 4), "leg")]),
        Bone("torsoLow", "root", (0, 12, 0), cubes=[C((-4, 12, -2), (8, 4, 4), "slice_low")]),
        Bone("torsoMid", "torsoLow", (0, 16, 0), cubes=[C((-2, 16, -2), (8, 4, 4), "slice_mid")]),
        Bone("torsoHigh", "torsoMid", (0, 20, 0), cubes=[C((-5, 20, -2), (8, 4, 4), "slice_high")]),
        Bone("head", "torsoHigh", (-1, 24, 0), rotation=(0, 0, -9), cubes=[
            C((-5, 24, -4), (8, 8, 8), "head"), C((-5, 24, -4), (8, 8, 8), "head+", inflate=0.5)]),
        Bone("rightArm", "torsoHigh", (-6.5, 23, 0), cubes=[C((-8, 11, -1.5), (3, 12, 3), "arm")]),
        Bone("leftArm", "torsoHigh", (4.5, 23, 0), cubes=[C((3, 5, -1.5), (3, 18, 3), "longarm")]),
        Bone("halo", "head", (-1, 28, 0), cubes=[
            C((-8, 30, -1), (1, 1, 1), "px"), C((4, 33, 2), (1, 1, 1), "px"), C((-3, 35, -3), (1, 1, 1), "px"),
            C((5, 26, -2), (1, 1, 1), "px"), C((-9, 25, 2), (1, 1, 1), "px"), C((1, 34, 3), (1, 1, 1), "px"),
            C((2, 35, -1), (2, 2, 2), "missing"), C((-10, 28, -2), (2, 2, 2), "missing")]),
    ], bounds=(2.5, 2.8, (0, 1.4, 0))).pack_uv(64)


def paint_null(seed):
    rng = random.Random(seed)
    cv = Canvas(*MODELS["null"].texture)
    magenta, cyan = (0xF8, 0x00, 0xF8), (0x00, 0xE0, 0xE0)

    def void(y):
        g = rng.randint(4, 12) + (6 if y % 2 else 0)  # scanlines
        return (g, g, g + 2)

    tears = {}

    def fn(bone, tag, face_, x, y, fw, fh):
        if tag == "head+":  # a band of static that crawls across the face
            if face_ == "front" and 3 <= y <= 5:
                return CLEAR
            key = (face_, y)
            if key not in tears:
                start = rng.randint(0, fw - 2)
                tears[key] = (start, start + rng.randint(1, 4), rng.choice([magenta, cyan])) if rng.random() < 0.18 else None
            t = tears[key]
            return t[2] if t and t[0] <= x < t[1] else CLEAR
        if tag == "missing":
            return magenta if (x + y) % 2 == 0 else (0, 0, 0)
        if tag == "px":
            return rng.choice([magenta, cyan, (0xFF, 0xFF, 0xFF)])
        if tag == "head" and face_ == "front":
            if y == 4 and x in (2, 5):
                return GLOW_WHITE
            if y > 4 and x in (2, 5) and rng.random() < 0.5:
                return (0x1E, 0x1E, 0x24)
        if tag.startswith("slice") and face_ not in ("top", "bottom") and y == fh - 1 and rng.random() < 0.3:
            return rng.choice([magenta, cyan])  # the seams where the slices tore apart
        if tag == "longarm" and y >= fh - 4 and rng.random() < 0.3:
            return rng.choice([magenta, cyan, (0xFF, 0xFF, 0xFF)])
        return void(y)

    paint_model(cv, MODELS["null"], fn)
    return cv


# =========================================================================== #
# The Man From The Fog
# =========================================================================== #

def fog_man_model():
    """Three blocks of starved, crooked man in a long rotten coat. Hunched,
    one shoulder higher than the other, head tilted, a hinged jaw, vertebrae
    breaking through the back of the coat, and four long fingers on hands
    that hang below his knees."""
    bones = [
        Bone("root"),
        Bone("body", "root", (0, 21, 0), cubes=[C((-3, 20, -1.5), (6, 4, 3), "pelvis")]),
        Bone("spine", "body", (0, 24, 0), cubes=[C((-2.5, 24, -1.5), (5, 4, 3), "abdomen")]),
        Bone("chest", "spine", (0, 28, 0), rotation=(12, 0, -4), cubes=[
            C((-4, 28, -2), (8, 9, 4), "chest"),
            C((-4.5, 34, -2.5), (9, 3, 5), "collar"),
            *[C((-0.5, 29 + 2 * k, 2), (1, 1, 2), "vertebra") for k in range(4)]]),
        Bone("coatBack", "chest", (0, 36, 2.5), cubes=[
            C((-4.5, 14, 2), (9, 22, 1), "coat_back"),
            *[C((x, 10, 2), (1, 4, 1), "fringe") for x in (-4, -1.5, 1, 3.5)]]),
        Bone("coatRight", "chest", (-3, 36, -2.5), cubes=[C((-4.5, 14, -3), (3, 22, 1), "coat_front")]),
        Bone("coatLeft", "chest", (3, 36, -2.5), cubes=[C((1.5, 14, -3), (3, 22, 1), "coat_front")]),
        Bone("neck", "chest", (0, 37, 0), rotation=(-10, 0, 0), cubes=[C((-1, 37, -1), (2, 3, 2), "neck")]),
        Bone("head", "neck", (0, 40, 0), rotation=(0, 0, 16), cubes=[C((-3, 40, -3), (6, 9, 6), "head")]),
        Bone("jaw", "head", (0, 40.5, 2), rotation=(5, 0, 0), cubes=[C((-2.5, 38, -3.5), (5, 3, 5), "jaw")]),
        # A few long, lank strands of black hair from the back of the scalp.
        Bone("hair", "head", (0, 48, 3), rotation=(12, 0, 0), cubes=[
            C((-2, 39, 2.5), (1, 9, 1), "strand"), C((0.5, 37, 2.5), (1, 11, 1), "strand"), C((2, 40, 2.2), (1, 8, 1), "strand")]),
    ]
    for side, name, shoulder in ((-1, "right", 35), (1, "left", 36)):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            Bone(f"{name}Arm", "chest", (5 * side, shoulder, 0), rotation=(0, 0, -3 * side), cubes=[
                C((sx(4, 6), shoulder - 12, -1), (2, 12, 2), "upper_arm"),
                C((sx(4, 6), shoulder - 10, -1), (2, 10, 2), "sleeve", inflate=0.4)]),
            Bone(f"{name}Forearm", f"{name}Arm", (5 * side, shoulder - 12, 0), rotation=(-8, 0, 0), cubes=[
                C((sx(4, 6), shoulder - 24, -1), (2, 12, 2), "forearm")]),
            Bone(f"{name}Hand", f"{name}Forearm", (5 * side, shoulder - 24, 0), cubes=[
                C((sx(3, 7), shoulder - 27, -0.5), (4, 3, 1), "palm"),
                *[C((sx(3 + k, 4 + k), shoulder - 33, -0.5), (1, 6, 1), "finger") for k in range(4)]]),
            Bone(f"{name}Leg", "root", (2 * side, 21, 0), cubes=[C((sx(0.5, 3.5), 11, -1.5), (3, 10, 3), "thigh")]),
            Bone(f"{name}Shin", f"{name}Leg", (2 * side, 11, 0), cubes=[
                C((sx(1, 3), 1, -1), (2, 10, 2), "shin"),
                C((sx(0.5, 3.5), 0, -3.5), (3, 1, 4), "foot")]),
        ]
    return Model("geometry.hl.fog_man", bones, bounds=(3.0, 3.5, (0, 1.6, 0))).pack_uv(128)


def paint_fog_man(seed):
    rng = random.Random(seed)
    cv = Canvas(*MODELS["fog_man"].texture)
    S = (0xBE, 0xBC, 0xB4)
    S_d = shade(S, 0.76)
    coat, coat_l, coat_d = (0x2C, 0x2A, 0x2E), (0x3C, 0x39, 0x3E), (0x1C, 0x1B, 0x1E)
    teeth, mouth, bone_c = (0xD8, 0xD0, 0xBC), (0x2A, 0x08, 0x0A), (0xD6, 0xD0, 0xC0)

    def pale(y, fh, top=1.03, bottom=0.84):
        c = jitter(rng, grad(S, y, fh, top, bottom), 4)
        r = rng.random()
        if r < 0.04:
            return shade(c, 0.88)
        if r < 0.055:
            return mix(c, (0x98, 0xA2, 0xB0), 0.6)  # veins
        return c

    def cloth(y, fh, base=coat):
        c = jitter(rng, grad(base, y, fh, 1.1, 0.85), 4)
        return coat_l if rng.random() < 0.08 else c

    head = [
        "SSSSSS",
        "SsSSsS",
        "KKSSKK",
        "KKSSKK",
        "KKSSKK",
        "kSnnSk",
        "SSSSSS",
        "SKKKKS",
        "KWKWKW",
    ]
    hpal = {"s": S_d, "K": BLACK, "k": (0x3C, 0x3A, 0x3C), "n": (0x22, 0x1C, 0x1E), "W": teeth}

    def fn(bone, tag, face_, x, y, fw, fh):
        if tag == "head":
            if face_ == "front":
                return face_rows(head, hpal, x, y, lambda: pale(y, fh))
            if face_ == "bottom":
                return mouth
            near_front = (face_ == "right" and x >= fw - 2) or (face_ == "left" and x <= 1)
            if y >= fh - 2 and near_front:
                return BLACK  # the grin wraps round the face
            return pale(y, fh)
        if tag == "jaw":
            if face_ == "front":
                return [lambda: teeth if x % 2 else BLACK, lambda: pale(y, fh), lambda: shade(pale(y, fh), 0.85)][y]()
            if face_ == "top":
                return mouth
            return shade(pale(y, fh), 0.9)
        if tag == "neck":
            return S_d if face_ == "front" and x in (0, fw - 1) else pale(y, fh)
        if tag == "chest":
            c = pale(y, fh)
            if face_ == "front":
                if y in (1, 3, 5, 7) and x not in (3, 4):
                    return S_d  # ribs, seen between the coat flaps
                if x in (3, 4) and y < 7:
                    return shade(c, 1.05)  # sternum
            return c
        if tag == "collar":
            return cloth(y, fh, coat_d)
        if tag == "vertebra":
            return jitter(rng, bone_c, 5)
        if tag == "sleeve":
            if face_ == "bottom" or (y >= fh - 2 and rng.random() < 0.5):
                return CLEAR  # frayed cuff
            return cloth(y, fh)
        if tag == "strand":
            return jitter(rng, (0x14, 0x13, 0x16) if y % 3 else (0x24, 0x22, 0x26), 3)
        if tag == "fringe":
            return CLEAR if (y == fh - 1 and rng.random() < 0.5) else cloth(y, fh)
        if tag in ("coat_back", "coat_front"):
            if face_ in ("top",):
                return coat_d
            # Ragged hem and moth holes.
            if y >= fh - 3 and rng.random() < (y - (fh - 4)) * 0.28:
                return CLEAR
            if 4 < y < fh - 4 and rng.random() < 0.02:
                return CLEAR
            return cloth(y, fh)
        if tag in ("pelvis", "abdomen"):
            if tag == "pelvis":
                return jitter(rng, (0x26, 0x26, 0x2A), 4)
            return S_d if face_ == "front" and y % 2 == 0 else pale(y, fh)
        if tag == "thigh":
            return jitter(rng, (0x30, 0x2F, 0x35) if x == 1 else (0x26, 0x26, 0x2A), 4)
        if tag == "shin":
            c = pale(y, fh, 1.0, 0.78)
            return shade(c, 1.06) if face_ == "front" and x == 0 else c
        if tag == "foot":
            return (0x4A, 0x44, 0x3E) if face_ == "front" else pale(1, 2, 0.88, 0.8)
        if tag == "upper_arm":
            return pale(y, fh)
        if tag == "forearm":
            return S_d if y < 2 and face_ != "top" else pale(y, fh)
        if tag == "palm":
            return pale(y, fh, 0.94, 0.84)
        if tag == "finger":
            return (0x2E, 0x2A, 0x26) if (y >= fh - 2 or face_ == "bottom") else pale(y, fh, 0.95, 0.82)
        return None

    paint_model(cv, MODELS["fog_man"], fn)
    return cv


# =========================================================================== #
# The Cave Dweller
# =========================================================================== #

def cave_dweller_model():
    """Something that was a person once, now on all fours: long arms planted
    ahead like a runner's, knees bent the wrong way, a ribcage lifted off the
    ground, spine ridge, a stretched neck, sunken glowing eyes and a jaw that
    drops open far too wide."""
    bones = [
        Bone("root"),
        Bone("body", "root", (0, 13, 6), cubes=[
            C((-3, 10.5, 4), (6, 5, 5), "pelvis"),
            C((-0.5, 15, 5.5), (1, 2, 1), "ridge")]),
        Bone("abdomen", "body", (0, 13, 4), cubes=[
            C((-2.5, 11, -2), (5, 4, 6), "abdomen"),
            C((-0.5, 14.5, -0.5), (1, 2, 1), "ridge"), C((-0.5, 14.5, 2), (1, 2, 1), "ridge")]),
        Bone("ribcage", "abdomen", (0, 13, -2), rotation=(-14, 0, 0), cubes=[
            C((-4, 10, -10), (8, 7, 8), "ribcage"),
            C((-0.5, 16.5, -4.5), (1, 2, 1), "ridge"), C((-0.5, 16.5, -7.5), (1, 2, 1), "ridge")]),
        Bone("neck", "ribcage", (0, 15, -10), rotation=(-20, 0, 0), cubes=[C((-1.5, 13.5, -16), (3, 3, 6), "neck")]),
        Bone("head", "neck", (0, 15, -16), rotation=(26, 0, 0), cubes=[C((-3.5, 14, -23), (7, 5, 7), "skull")]),
        Bone("jaw", "head", (0, 14.5, -17), rotation=(10, 0, 0), cubes=[C((-3.5, 11, -23), (7, 3, 6), "jaw")]),
    ]
    for side, name in ((-1, "Right"), (1, "Left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Front limbs: long arms planted ahead, elbows out.
            Bone(f"arm{name}", "ribcage", (4.5 * side, 15, -7), rotation=(-30, 0, -38 * side), cubes=[
                C((sx(3.5, 5.5), 2, -8), (2, 13, 2), "upper_arm")]),
            Bone(f"forearm{name}", f"arm{name}", (4.5 * side, 2, -7), rotation=(58, 0, 34 * side), cubes=[
                C((sx(3.5, 5.5), -7, -8), (2, 9, 2), "forearm")]),
            Bone(f"hand{name}", f"forearm{name}", (4.5 * side, -7, -7), rotation=(-28, 0, 4 * side), cubes=[
                C((sx(3, 6), -8, -9), (3, 1, 3), "palm"),
                *[C((sx(a, a + 1), -8, -14), (1, 1, 5), "claw") for a in (3, 4, 5)]]),
            # Hind legs: knees forward, shins back, long feet.
            Bone(f"thigh{name}", "body", (3.5 * side, 12.5, 7), rotation=(-62, 0, -12 * side), cubes=[
                C((sx(2, 5), 2.5, 5.5), (3, 10, 3), "thigh")]),
            Bone(f"shin{name}", f"thigh{name}", (3.5 * side, 2.5, 7), rotation=(100, 0, 8 * side), cubes=[
                C((sx(2.5, 4.5), -6.5, 6), (2, 9, 2), "shin")]),
            Bone(f"foot{name}", f"shin{name}", (3.5 * side, -6.5, 7), rotation=(-38, 0, 0), cubes=[
                C((sx(2, 5), -7.5, 2), (3, 1, 6), "foot")]),
        ]
    bones += [
        Bone("tail1", "body", (0, 12.5, 9), rotation=(-18, 0, 0), cubes=[C((-1, 11.5, 9), (2, 2, 6), "tail1")]),
        Bone("tail2", "tail1", (0, 12.5, 15), rotation=(22, 0, 0), cubes=[C((-0.5, 12, 15), (1, 1, 7), "tail2")]),
    ]
    return Model("geometry.hl.cave_dweller", bones, bounds=(3.0, 2.0, (0, 0.8, 0))).pack_uv(128)


def paint_cave_dweller(seed):
    rng = random.Random(seed)
    cv = Canvas(*MODELS["cave_dweller"].texture)
    D = (0x9E, 0x9A, 0x8E)
    D_d = shade(D, 0.68)
    wet = (0xC6, 0xC2, 0xB6)
    teeth, mouth = (0xDE, 0xD6, 0xC0), (0x44, 0x0E, 0x12)
    glow = (0xFF, 0xF0, 0xB0, 60)

    def skin(y, fh, top=1.05, bottom=0.8):
        c = jitter(rng, grad(D, y, fh, top, bottom), 5)
        r = rng.random()
        if r < 0.06:
            return wet
        if r < 0.11:
            return shade(c, 0.82)
        if r < 0.13:
            return mix(c, (0x6E, 0x7C, 0x80), 0.5)
        return c

    skull = [
        "SkSSSkS",
        "KKSSSKK",
        "KgSSSgK",
        "SSnSnSS",
        "WKWKWKW",
    ]
    spal = {"s": D_d, "K": BLACK, "k": (0x1A, 0x16, 0x14), "g": glow, "n": (0x2E, 0x28, 0x28), "W": teeth}

    def fn(bone, tag, face_, x, y, fw, fh):
        if tag == "skull":
            if face_ == "front":
                return face_rows(skull, spal, x, y, lambda: skin(y, fh))
            if face_ == "bottom":
                return teeth if (x % 2 == 0 and y == fh - 1) else mouth
            if face_ == "top" and x == fw // 2:
                return D_d
            return skin(y, fh)
        if tag == "jaw":
            if face_ == "front":
                return [lambda: BLACK if x % 2 == 0 else teeth, lambda: skin(y, fh), lambda: shade(skin(y, fh), 0.85)][y]()
            if face_ == "top":
                return (0x6A, 0x1E, 0x22) if 2 <= x <= 4 and y > 1 else mouth  # tongue
            if face_ in ("right", "left") and y == 0 and rng.random() < 0.5:
                return (0x4A, 0x16, 0x18)
            return skin(y, fh, 0.9, 0.78)
        if tag == "neck":
            return D_d if face_ in ("right", "left", "top") and x % 2 == 0 else skin(y, fh)
        if tag == "ribcage":
            if face_ in ("right", "left") and x % 2 == 0 and 1 <= y <= 5:
                return D_d  # ribs pressing through the skin
            if face_ == "bottom":
                return jitter(rng, shade(wet, 0.9), 4)
            if face_ == "top" and x in (3, 4):
                return D_d
            return skin(y, fh)
        if tag == "abdomen":
            if face_ in ("right", "left"):
                return shade(skin(y, fh), 0.8)  # sunken belly
            return skin(y, fh)
        if tag == "pelvis":
            return D_d if face_ in ("right", "left") and y == 1 else skin(y, fh)
        if tag == "ridge":
            return (0x5A, 0x52, 0x46) if face_ == "top" else jitter(rng, (0xD6, 0xCE, 0xBA), 5)
        if tag in ("tail1", "tail2"):
            return D_d if face_ == "top" and y % 2 == 0 else skin(y, fh, 1.0, 0.7)
        if tag in ("upper_arm", "thigh"):
            return D_d if y >= fh - 2 else skin(y, fh)
        if tag in ("forearm", "shin"):
            return skin(y, fh, 1.0, 0.55)
        if tag in ("palm", "foot"):
            return skin(0, 2, 0.7, 0.6)
        if tag == "claw":
            return BLACK if face_ == "front" or rng.random() < 0.3 else jitter(rng, (0x3A, 0x34, 0x2E), 4)
        return None

    paint_model(cv, MODELS["cave_dweller"], fn)
    return cv


# =========================================================================== #
# HerobrineGamer788: just another player
# =========================================================================== #

def fake_player_model():
    """An ordinary player, outer skin layer and all. The pickaxe in his right
    hand only shows while he is mining or building."""
    P = lambda o, s, uv, tag, inflate=0.0: Cube(o, s, uv, inflate, share=tag)
    return Model("geometry.hl.fake_player", [
        Bone("root"),
        Bone("body", "root", (0, 24, 0), cubes=[
            P((-4, 12, -2), (8, 12, 4), (16, 16), "body"), P((-4, 12, -2), (8, 12, 4), (16, 32), "body+", 0.25)]),
        Bone("head", "body", (0, 24, 0), cubes=[
            P((-4, 24, -4), (8, 8, 8), (0, 0), "head"), P((-4, 24, -4), (8, 8, 8), (32, 0), "head+", 0.5)]),
        Bone("rightArm", "body", (-5, 22, 0), cubes=[
            P((-8, 12, -2), (4, 12, 4), (40, 16), "arm"), P((-8, 12, -2), (4, 12, 4), (40, 32), "arm+", 0.25)]),
        Bone("leftArm", "body", (5, 22, 0), cubes=[
            P((4, 12, -2), (4, 12, 4), (32, 48), "arm"), P((4, 12, -2), (4, 12, 4), (48, 48), "arm+", 0.25)]),
        Bone("tool", "rightArm", (-6, 13, 0), cubes=[
            P((-6.5, 12.5, -9), (1, 1, 8), (64, 0), "tool_handle"),
            P((-6.5, 10, -10), (1, 6, 1), (82, 0), "tool_head")]),
        Bone("rightLeg", "root", (-1.9, 12, 0), cubes=[
            P((-3.9, 0, -2), (4, 12, 4), (0, 16), "leg"), P((-3.9, 0, -2), (4, 12, 4), (0, 32), "leg+", 0.25)]),
        Bone("leftLeg", "root", (1.9, 12, 0), cubes=[
            P((-0.1, 0, -2), (4, 12, 4), (16, 48), "leg"), P((-0.1, 0, -2), (4, 12, 4), (0, 48), "leg+", 0.25)]),
    ], texture=(128, 64), bounds=(2.0, 2.2, (0, 1.1, 0)))


def paint_fake_player(seed, white_eyes=False):
    """A plain default-looking miner. The second texture is the same player
    with the eyes gone white."""
    rng = random.Random(seed)
    cv = Canvas(128, 64)
    skin, hair, hair_l = (0xC6, 0x94, 0x76), (0x3A, 0x26, 0x14), (0x4E, 0x34, 0x1C)
    shirt, pants, shoe = (0x1C, 0xA2, 0xA6), (0x3E, 0x38, 0x9C), (0x5E, 0x5E, 0x64)
    face = [
        "HHHHHHHH",
        "HHHHHHHH",
        "HSSSSSSH",
        "SSSSSSSS",
        "SWPSSPWS",
        "SSSnnSSS",
        "SSmMMmSS",
        "SSSmmSSS",
    ]
    eye = GLOW_WHITE if white_eyes else None
    fpal = {"H": hair, "S": None, "W": eye or (0xF0, 0xF0, 0xF0), "P": eye or (0x3C, 0x50, 0xA0),
            "n": shade(skin, 0.84), "m": (0x7E, 0x52, 0x3A), "M": (0x5C, 0x38, 0x26)}

    def fn(bone, tag, face_, x, y, fw, fh):
        if tag == "head":
            if face_ == "front":
                return face_rows(face, fpal, x, y, lambda: jitter(rng, grad(skin, y, fh, 1.02, 0.94), 2))
            if face_ == "top":
                return jitter(rng, hair_l if rng.random() < 0.2 else hair, 3)
            if face_ == "bottom":
                return shade(skin, 0.85)
            if face_ == "back":
                return jitter(rng, hair if y < 7 else shade(skin, 0.9), 3)
            back_half = x < 4 if face_ == "right" else x >= 4
            return jitter(rng, hair if (y < 3 or (back_half and y < 5)) else skin, 3)
        if tag == "head+":
            if face_ == "top" and (x in (0, fw - 1) or y in (0, fh - 1)) and rng.random() < 0.4:
                return jitter(rng, hair_l, 4)
            return CLEAR
        if tag == "body":
            if face_ == "bottom":
                return pants
            return jitter(rng, grad(shirt, y, fh, 1.04, 0.88), 3)
        if tag == "body+":
            return shade(shirt, 0.8) if y == fh - 1 and face_ not in ("top", "bottom") else CLEAR
        if tag == "arm":
            if face_ == "top" or (face_ != "bottom" and y < 4):
                return jitter(rng, shirt, 3)
            return jitter(rng, grad(skin, y, fh, 1.0, 0.9), 2)
        if tag == "arm+":
            return shade(shirt, 0.8) if y == 3 and face_ not in ("top", "bottom") else CLEAR
        if tag == "leg":
            if face_ == "bottom" or (face_ != "top" and y >= 10):
                return jitter(rng, shoe if y == 10 else shade(shoe, 0.8), 3)
            return jitter(rng, grad(pants, y, 10, 1.04, 0.88), 3)
        if tag == "leg+":
            return CLEAR
        if tag == "tool_handle":
            return jitter(rng, (0x6A, 0x4C, 0x2E), 4)
        if tag == "tool_head":
            return jitter(rng, grad((0xD0, 0xD0, 0xD8), y, fh, 1.1, 0.8), 4)
        return None

    paint_model(cv, MODELS["fake_player"], fn)
    return cv


MODELS = {}
MODELS["herobrine"] = herobrine_model()
MODELS["null"] = null_model()
MODELS["fog_man"] = fog_man_model()
MODELS["cave_dweller"] = cave_dweller_model()
MODELS["fake_player"] = fake_player_model()


# =========================================================================== #
# Blocks, items, pack icon
# =========================================================================== #

def paint_corrupted(seed):
    """The missing-texture checkerboard, four frames tall: the block's tears
    crawl and it flickers inverted (see flipbook_textures.json)."""
    rng = random.Random(seed)
    frames = 4
    cv = Canvas(16, 16 * frames)
    for f in range(frames):
        inverted = f == 2
        for y in range(16):
            shift = rng.choice([0, 0, 0, 2, 3, -2, -3]) if rng.random() < 0.3 else 0
            for x in range(16):
                sx = (x + shift) % 16
                magenta = ((sx // 8) + (y // 8)) % 2 == 0
                c = (0xF8, 0x00, 0xF8) if magenta else (0x00, 0x00, 0x00)
                if inverted:
                    c = (0x00, 0xE0, 0xE0) if magenta else (0x10, 0x00, 0x10)
                if rng.random() < 0.03:
                    c = (0xFF, 0xFF, 0xFF)
                cv.set(x, f * 16 + y, c)
    return cv


def paint_journal(seed):
    """A scuffed black notebook with a pale handprint on the cover and a red
    string bookmark."""
    rng = random.Random(seed)
    cv = Canvas(16, 16)
    cover, edge = (0x26, 0x24, 0x28), (0x12, 0x11, 0x14)
    for y in range(1, 15):
        for x in range(2, 14):
            c = jitter(rng, grad(cover, y - 1, 14, 1.2, 0.85), 5)
            if x in (2, 13) or y in (1, 14):
                c = edge
            elif rng.random() < 0.05:
                c = shade(c, 1.5)  # scuffs
            cv.set(x, y, c)
    for y in range(2, 14):
        cv.set(12, y, (0xE4, 0xDA, 0xC2) if y % 2 else (0xB8, 0xAE, 0x96))  # page edges
        cv.set(3, y, (0x3A, 0x38, 0x3E))  # spine
    hand = [
        ".#.#.#",
        ".#.#.#",
        "######",
        "#####.",
        ".####.",
        "..##..",
    ]
    for y, row in enumerate(hand):
        for x, ch in enumerate(row):
            if ch == "#":
                cv.set(5 + x, 4 + y, jitter(rng, (0xD8, 0xD2, 0xC8), 10))
    for y in range(9, 16):
        cv.set(10, y, (0xB0, 0x18, 0x18))  # bookmark
    return cv


def paint_flashlight():
    """A black rubber torch, lit, seen side-on."""
    cv = Canvas(16, 16)
    body, hi, lo = (0x2A, 0x2A, 0x30), (0x4E, 0x4E, 0x58), (0x16, 0x16, 0x1A)
    for x in range(1, 10):
        cv.set(x, 7, hi)
        cv.set(x, 8, body)
        cv.set(x, 9, lo)
    for x in (3, 5):
        cv.set(x, 8, lo)  # grip ribs
    cv.set(6, 6, (0xD0, 0x22, 0x22))  # switch
    cv.rect(10, 5, 3, 6, body)  # head
    cv.set(10, 5, hi)
    cv.rect(11, 6, 1, 4, (0xE0, 0xB0, 0x20))  # yellow ring
    cv.rect(12, 6, 1, 4, (0xFF, 0xF4, 0xB0))  # lens
    for x in range(13, 16):
        spread = x - 12
        for y in range(8 - spread - 1, 8 + spread + 1):
            cv.set(x, y, (0xFF, 0xF4, 0xB8, max(40, 150 - spread * 35)))  # beam
    return cv


def render_view(name, textures, size, yaw, pitch, pose=None, ppu=None):
    return render(MODELS[name], textures[name], size=size, yaw=yaw, pitch=pitch, pose=pose, ppu=ppu)


FOG_WATCH_POSE = {"body": (4, 0, 0), "jaw": (4, 0, 0)}


def paint_pack_icon(textures, seed):
    rng = random.Random(seed)
    cv = Canvas(128, 128)
    for y in range(128):
        for x in range(128):
            fog = int(14 + 70 * (y / 127) ** 1.4 + rng.randint(-4, 4))
            cv.set(x, y, (fog, fog + 2, fog + 5))
    # Dead trees in the background.
    for tx, w in ((6, 5), (30, 4), (96, 6), (118, 4)):
        for y in range(0, 110):
            for x in range(tx, tx + w):
                cv.blend(x, y, (0x0C, 0x0D, 0x10), 0.8)
    # Herobrine, far off between the trees; null glitching at the edge.
    cv.paste(render_view("herobrine", textures, (20, 30), 15, -4), 12, 52)
    cv.paste(render_view("null", textures, (28, 44), -30, -4), 98, 44)
    # The Man From The Fog, close.
    cv.paste(render_view("fog_man", textures, (64, 116), 22, -6, FOG_WATCH_POSE), 34, 6)
    # Fog rolling over everything below the waist.
    for y in range(62, 128):
        t = min(1.0, (y - 62) / 60) * 0.7
        for x in range(128):
            cv.blend(x, y, (0x5A, 0x5E, 0x64), t)
    # The Cave Dweller crawling out of the dark in the corner.
    for y in range(92, 128):
        for x in range(0, 60):
            if (x / 60) ** 2 + ((128 - y) / 36) ** 2 < 1.0:
                cv.blend(x, y, (0x06, 0x06, 0x08), 0.85)
    cv.paste(render_view("cave_dweller", textures, (52, 34), 35, -10), 2, 92)
    for y in range(128):
        for x in range(128):
            if rng.random() < 0.05:
                cv.set(x, y, jitter(rng, cv.get(x, y), 20))
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
    anims = {"format_version": "1.8.0", "animations": ANIMATIONS}
    path = RP / "animations" / "hl.animation.json"
    path.write_text(json.dumps(anims, indent=2) + "\n", encoding="utf-8")


def paint_textures():
    textures = {
        "herobrine": paint_herobrine(10),
        "null": paint_null(20),
        "fog_man": paint_fog_man(30),
        "cave_dweller": paint_cave_dweller(40),
        "fake_player": paint_fake_player(80),
    }
    for name, tex in textures.items():
        bake(tex, MODELS[name])
    bake(paint_fake_player(80, white_eyes=True), MODELS["fake_player"]).save(
        RP / "textures" / "entity" / "hl" / "fake_player_eyes.png")
    ent = RP / "textures" / "entity" / "hl"
    for name, tex in textures.items():
        tex.save(ent / f"{name}.png")
    paint_corrupted(50).save(RP / "textures" / "blocks" / "hl" / "corrupted_block.png")
    flipbook = [{"flipbook_texture": "textures/blocks/hl/corrupted_block", "atlas_tile": "hl_corrupted",
                 "ticks_per_frame": 3, "blend_frames": False}]
    (RP / "textures" / "flipbook_textures.json").write_text(json.dumps(flipbook, indent=2) + "\n", encoding="utf-8")
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
                            "coatBack": (40, 0, 0), "coatRight": (30, 0, 0), "coatLeft": (35, 0, 0),
                            "rightLeg": (-35, 0, 0), "rightShin": (30, 0, 0), "leftLeg": (25, 0, 0), "leftShin": (50, 0, 0)})],
    "cave_dweller": [("stalk", {}), ("chase", {"jaw": (38, 0, 0), "head": (-14, 0, 0), "neck": (-6, 0, 0)})],
    "fake_player": [("", {}), ("mining", {"rightArm": (-75, 0, 0), "head": (15, 0, 0)}),
                    ("sneaking", {"body": (28, 0, 0), "rightLeg": (0, 0, 0), "head": (-10, 0, 0),
                                  "rightArm": (20, 0, 0), "leftArm": (20, 0, 0)})],
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
