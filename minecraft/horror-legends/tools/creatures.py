"""The creatures of Horror Legends: models and textures, at double detail.

Every model is built at twice its in-game size, painted against that (box
UVs map one texel per unit, so a head gets a 16x16 face instead of 8x8) and
written out at its real size with the texture size in the JSON halved (see
Model.detail). In game only the textures gain detail: sizes, bone pivots and
animations stay in ordinary pixels.

Model coordinates below are written in ordinary in-game pixels (half-pixel
steps allowed) and doubled by the B() and C() helpers.
"""

import random

from geometry import Bone, Cube, Model
from pixels import Canvas, box_faces, jitter, mix, paint_box, shade

K = 2  # texels per in-game pixel

GLOW_WHITE = (0xFF, 0xFF, 0xFF, 40)  # alpha < 255 glows under entity_emissive_alpha
BLACK = (0x07, 0x07, 0x09)
CLEAR = (0, 0, 0, 0)


def _k(v):
    return tuple(x * K for x in v) if v is not None else None


def B(name, parent, pivot, rotation=None, cubes=()):
    return Bone(name, parent, _k(pivot), rotation, cubes)


def C(origin, size, tag, inflate=0.0, pivot=None, rotation=None):
    return Cube(_k(origin), _k(size), None, inflate * K, _k(pivot), rotation, share=tag)


def hd_model(identifier, bones, bounds):
    return Model(identifier, bones, bounds=bounds, detail=K).pack_uv(256)


def side_x(side, a, b):
    """Origin X of a cube spanning |x| in [a, b] on the model's right (-1) or
    left (+1) side."""
    return -b if side < 0 else a


def paint_model(cv, model, fn):
    """fn(tag, face, x, y, fw, fh) -> colour or None, for every cube."""
    for bone in model.bones:
        for cube in bone.cubes:
            w, h, d = (int(s) for s in cube.size)
            paint_box(cv, *cube.uv, w, h, d, lambda face, x, y, fw, fh, t=cube.share: fn(t, face, x, y, fw, fh))


def grad(c, y, fh, top=1.06, bottom=0.88):
    return shade(c, top + (bottom - top) * y / max(1, fh - 1))


def rows(pattern, palette, x, y, fallback):
    c = palette.get(pattern[y][x])
    return fallback() if c is None else c


def bake(cv, model):
    """Soft pixel-art shading: a two-texel darkening round the rim of every
    face, top faces lifted, bottoms sunk. Outer layers and emissive or
    transparent texels are left alone."""
    done = set()
    for bone in model.bones:
        for cube in bone.cubes:
            if cube.inflate:
                continue
            w, h, d = (int(v) for v in cube.size)
            for face, (x0, y0, fw, fh) in box_faces(*cube.uv, w, h, d).items():
                if (x0, y0, fw, fh) in done or fw < 4 or fh < 4:
                    continue
                done.add((x0, y0, fw, fh))
                lift = {"top": 1.05, "bottom": 0.88}.get(face, 1.0)
                for y in range(fh):
                    for x in range(fw):
                        c = cv.get(x0 + x, y0 + y)
                        if c[3] != 255:
                            continue
                        edge = min(x, fw - 1 - x, y, fh - 1 - y)
                        k = {0: 0.88, 1: 0.95}.get(edge, 1.0)
                        cv.set(x0 + x, y0 + y, shade(c, lift * k))
    return cv


# =========================================================================== #
# HerobrineGamer788: just another player
# =========================================================================== #

def fake_player_model():
    """Ordinary player proportions with the outer skin layer. The pickaxe in
    his right hand only shows while he mines or builds."""
    return hd_model("geometry.hl.fake_player", [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 24, 0), cubes=[
            C((-4, 12, -2), (8, 12, 4), "body"), C((-4, 12, -2), (8, 12, 4), "body+", 0.25)]),
        B("head", "body", (0, 24, 0), cubes=[
            C((-4, 24, -4), (8, 8, 8), "head"), C((-4, 24, -4), (8, 8, 8), "head+", 0.5)]),
        B("rightArm", "body", (-5, 22, 0), cubes=[
            C((-8, 12, -2), (4, 12, 4), "arm"), C((-8, 12, -2), (4, 12, 4), "arm+", 0.25)]),
        B("leftArm", "body", (5, 22, 0), cubes=[
            C((4, 12, -2), (4, 12, 4), "arm"), C((4, 12, -2), (4, 12, 4), "arm+", 0.25)]),
        B("tool", "rightArm", (-6, 13, 0), cubes=[
            C((-6.5, 12.5, -9), (1, 1, 8), "tool_handle"),
            C((-6.5, 10.5, -10), (1, 5, 1), "tool_head"),
            C((-6.5, 10, -10.5), (1, 1, 1), "tool_tip"), C((-6.5, 15, -10.5), (1, 1, 1), "tool_tip")]),
        B("rightLeg", "root", (-2, 12, 0), cubes=[
            C((-4, 0, -2), (4, 12, 4), "leg"), C((-4, 0, -2), (4, 12, 4), "leg+", 0.25)]),
        B("leftLeg", "root", (2, 12, 0), cubes=[
            C((0, 0, -2), (4, 12, 4), "leg"), C((0, 0, -2), (4, 12, 4), "leg+", 0.25)]),
    ], bounds=(2.0, 2.2, (0, 1.1, 0)))


def paint_fake_player(seed, model, white_eyes=False):
    rng = random.Random(seed)
    cv = Canvas(*model.texture)
    skin, skin_d = (0xC6, 0x94, 0x76), (0xA8, 0x78, 0x5C)
    hair, hair_l = (0x3A, 0x26, 0x14), (0x52, 0x37, 0x1E)
    shirt, pants, shoe = (0x1C, 0xA2, 0xA6), (0x3E, 0x38, 0x9C), (0x5A, 0x5A, 0x60)
    eye = GLOW_WHITE if white_eyes else None
    face = [
        "HHHHHHHHHHHHHHHH",
        "HHHHHHHHHHHHHHHH",
        "HHHHHHHHHHHHHHHH",
        "HHHHhHHHHHHhHHHH",
        "HHSSSHHSSSSSSSHH",
        "HSSSSSSSSSSSSSSH",
        "SSSSSSSSSSSSSSSS",
        "SSbbbSSSSSSbbbSS",
        "SSWWPPSSSSPPWWSS",
        "SSWWPPSSSSPPWWSS",
        "SSSSSSSnnSSSSSSS",
        "SSSSSSnNNnSSSSSS",
        "SSSSmmmmmmmmSSSS",
        "SSSmmMMMMMMmmSSS",
        "SSSSmmmmmmmmSSSS",
        "SSSSSmmmmmmSSSSS",
    ]
    fpal = {"H": hair, "h": hair_l, "b": shade(hair, 1.2), "W": eye or (0xF2, 0xF2, 0xF2),
            "P": eye or (0x3A, 0x4E, 0x9E), "n": skin_d, "N": shade(skin_d, 0.8),
            "m": (0x7A, 0x50, 0x38), "M": (0x52, 0x32, 0x22)}

    def fn(tag, face_, x, y, fw, fh):
        if tag == "head":
            if face_ == "front":
                return rows(face, fpal, x, y, lambda: jitter(rng, grad(skin, y, fh, 1.03, 0.94), 2))
            if face_ == "top":
                return jitter(rng, hair_l if (x + 2 * y) % 7 == 0 else hair, 3)
            if face_ == "bottom":
                return shade(skin, 0.85)
            if face_ == "back":
                return jitter(rng, hair if y < 13 - (x % 3 == 0) else skin_d, 3)
            back = x < 8 if face_ == "right" else x >= 8
            if y < 5 or (back and y < 11):
                return jitter(rng, hair, 3)
            if 8 <= y <= 11 and not back and x in ((9, 10) if face_ == "right" else (5, 6)):
                return skin_d  # ear
            return jitter(rng, grad(skin, y, fh, 1.0, 0.92), 2)
        if tag == "head+":
            if face_ == "top" and (x in (0, 1, fw - 2, fw - 1) or y in (0, 1, fh - 2, fh - 1)) and rng.random() < 0.35:
                return jitter(rng, hair_l, 4)
            if face_ in ("right", "left", "back") and y < 4 and rng.random() < 0.2:
                return jitter(rng, hair, 4)
            return CLEAR
        if tag == "body":
            if face_ == "bottom":
                return pants
            if face_ == "front" and y < 4 and abs(x - 7.5) < 4 - y:
                return grad(skin, y, 4)  # open collar
            c = grad(shirt, y, fh, 1.05, 0.86)
            if face_ == "front" and x in (7, 8) and y >= 4:
                c = shade(c, 0.9)  # placket
            if face_ in ("front", "back") and x in (3, 12) and 6 < y < 20:
                c = shade(c, 0.95)  # folds
            return jitter(rng, c, 2)
        if tag == "body+":
            return shade(shirt, 0.8) if y >= fh - 2 and face_ not in ("top", "bottom") else CLEAR
        if tag == "arm":
            if face_ == "top" or (face_ != "bottom" and y < 8):
                return jitter(rng, shade(shirt, 0.88) if y == 7 else grad(shirt, y, 8, 1.05, 0.95), 2)
            if face_ == "bottom" or y >= fh - 3:
                return jitter(rng, skin_d, 2)
            return jitter(rng, grad(skin, y, fh, 1.0, 0.9), 2)
        if tag == "arm+":
            return shade(shirt, 0.78) if y in (6, 7) and face_ not in ("top", "bottom") else CLEAR
        if tag == "leg":
            if face_ == "bottom" or (face_ != "top" and y >= fh - 4):
                return jitter(rng, shade(shoe, 0.7) if (y == fh - 1 or face_ == "bottom") else shoe, 2)
            c = grad(pants, y, fh - 4, 1.04, 0.88)
            if face_ == "front" and 9 <= y <= 11:
                c = shade(c, 1.06)  # knees
            if face_ in ("right", "left") and x in (3, 4):
                c = shade(c, 0.9)  # side seam
            return jitter(rng, c, 2)
        if tag == "leg+":
            return shade(pants, 0.85) if y == fh - 5 and face_ not in ("top", "bottom") else CLEAR
        if tag == "tool_handle":
            return jitter(rng, (0x6A, 0x4C, 0x2E) if y % 2 else (0x58, 0x3E, 0x24), 3)
        if tag in ("tool_head", "tool_tip"):
            return jitter(rng, grad((0xD4, 0xD4, 0xDC), y, fh, 1.1, 0.8), 3)
        return None

    paint_model(cv, model, fn)
    return bake(cv, model)


# =========================================================================== #
# Herobrine: what the mine left of a miner
# =========================================================================== #

def herobrine_model():
    """Three blocks of ruined miner, stooped and twitching. Jointed legs and
    arms, a narrow hollowed-out belly under a broad chest. The stomach is torn
    open and crudely stitched, guts spilling out and one loop hanging; the
    burned left half of the face has fallen away to the skull, one socket
    empty with something still glowing in it; fangs over a dislocated, dripping
    jaw; broken ribs through the chest; the spine outside the body from the
    waist to the skull; a pickaxe buried in his back; nails in the scalp; a
    flayed right forearm and a burned, blistered left arm ending in claws. He
    still drags his own pickaxe."""
    blister = lambda x, y, z: C((x, y, z), (1, 1, 1), "blister")
    blister_s = lambda x, y, z: C((x, y, z), (0.5, 0.5, 0.5), "blister_s")
    vertebra = lambda y, z=2: C((-1, y, z), (2, 1, 1.5), "vertebra")
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Thigh", "root", (2 * side, 20, 0), cubes=[
                C((sx(0, 4), 10, -2), (4, 10, 4), "thigh"), C((sx(0, 4), 10, -2), (4, 10, 4), "thigh+", 0.25)]),
            B(f"{name}Shin", f"{name}Thigh", (2 * side, 10, 0), rotation=(4, 0, 0), cubes=[
                C((sx(0.25, 3.75), 1, -1.75), (3.5, 9, 3.5), "shin"),
                C((sx(0, 4), 0, -3), (4, 1.5, 5), "boot")]),
        ]
    bones += [
        B("body", "root", (0, 20, 0), rotation=(8, 0, 0), cubes=[C((-4, 20, -2), (8, 4, 4), "pelvis")]),
        # The belly is hollow: flesh walls at the sides, the gut cavity behind.
        B("belly", "body", (0, 24, 0), cubes=[
            C((-3, 24, -1), (6, 6, 3), "cavity"),
            C((-4, 24, -2), (1, 6, 4), "flank"), C((3, 24, -2), (1, 6, 4), "flank"),
            C((-3, 29.5, -2), (6, 0.5, 1), "wound_lip"), C((-3, 24, -2), (6, 0.5, 1), "wound_lip"),
            C((-2.5, 25, -1.6), (2, 1, 1), "gut"), C((0.5, 25.5, -1.8), (2, 1, 1), "gut"),
            C((-1, 26.5, -2.1), (2, 1.5, 1), "gut_big"), C((-2.5, 28, -1.6), (1.5, 1, 1), "gut_s"),
            C((1, 28, -1.9), (1.5, 1, 1), "gut_s"),
            vertebra(24.5), vertebra(26.5), vertebra(28.5)]),
        B("gutHang", "belly", (0, 25, -1.5), cubes=[
            C((-0.5, 18.5, -2), (1, 6.5, 1), "gut_hang"), C((-0.75, 18, -2.25), (1.5, 1, 1.5), "gut_knot")]),
        B("chest", "belly", (0, 30, 0), rotation=(10, 0, -4), cubes=[
            C((-4.5, 30, -2.5), (9, 8, 5), "chest"), C((-4.5, 30, -2.5), (9, 8, 5), "chest+", 0.25),
            vertebra(30.5, 2.5), vertebra(32.5, 2.5), vertebra(34.5, 2.5), vertebra(36.5, 2.5),
            *[C((-0.5, y, 4), (1, 1, 1), "spur") for y in (31, 33, 35)],
            C((1, 30.5, -4.5), (1, 1, 2.5), "rib_long"), C((2.5, 32, -4), (1, 1, 1.5), "rib"), C((-3, 31, -4), (1, 1, 1.5), "rib"),
            C((0.5, 35, 3), (7, 1, 1), "lodged_pick", pivot=(4, 35.5, 3.5), rotation=(0, 0, 32)),
            C((3.5, 35, 3.5), (1, 1, 4), "lodged_handle", pivot=(4, 35.5, 3.5), rotation=(25, 0, 0)),
            blister(3.5, 37.5, -1), blister(4.5, 35, 0.5), blister_s(4.5, 33, -1.5)]),
        B("neck", "chest", (0, 38, 0), rotation=(-6, 0, 0), cubes=[
            C((-1.5, 38, -1.5), (3, 2, 3), "neck"), vertebra(38.5, 1.5)]),
        B("head", "neck", (0, 40, 0), rotation=(14, 0, 8), cubes=[
            C((-4, 40, -4), (8, 8, 8), "head"), C((-4, 40, -4), (8, 8, 8), "head+", 0.5),
            C((1, 43, -4.5), (3, 3, 1), "skull"),
            C((-2.5, 39, -4.25), (1, 1.5, 1), "fang"), C((1.5, 39, -4.25), (1, 1.5, 1), "fang"),
            C((-2.5, 48, -1), (1, 1, 1), "nail"), C((-1, 48, 1.5), (1, 1, 1), "nail"),
            blister(3, 45.5, -4.5), blister(4, 43.5, -2), blister(4, 46, 1), blister_s(2.5, 47.5, -3)]),
        # Dislocated: hanging open and off to one side.
        B("jaw", "head", (0, 41, 1), rotation=(24, 0, 14), cubes=[C((-3, 38.5, -4.5), (6, 2.5, 4.5), "jaw")]),
        B("dripL", "jaw", (1.5, 38.5, -4), cubes=[C((1, 34, -4.25), (1, 4.5, 1), "drip")]),
        B("dripR", "jaw", (-1.5, 38.5, -4), cubes=[C((-2, 35.5, -4.25), (1, 3, 1), "drip_s")]),
        # Right arm: flayed forearm, fist round the pickaxe.
        B("rightUpperArm", "chest", (-5.5, 37, 0), rotation=(-6, 0, 0), cubes=[
            C((-7.5, 28, -2), (4, 9, 4), "upper_r"), C((-7.5, 28, -2), (4, 9, 4), "sleeve", 0.25)]),
        B("rightForearm", "rightUpperArm", (-5.5, 28, 0), rotation=(-10, 0, 0), cubes=[
            C((-7.25, 19, -1.75), (3.5, 9, 3.5), "fore_r")]),
        B("rightHand", "rightForearm", (-5.5, 19, 0), cubes=[C((-7.5, 16, -2), (4, 3, 4), "fist")]),
        B("pickaxe", "rightHand", (-5.5, 17, 0), rotation=(-15, 0, 0), cubes=[
            C((-6, 3.5, -0.5), (1, 14, 1), "handle"),
            C((-6, 2, -4.5), (1, 2, 9), "pick"),
            C((-6, 4, -4.5), (1, 1, 1), "pick_tip"), C((-6, 4, 3.5), (1, 1, 1), "pick_tip")]),
        # Left arm: burned and blistered, ending in claws.
        B("leftUpperArm", "chest", (5.5, 37, 0), rotation=(-10, 0, -5), cubes=[
            C((3.5, 28, -2), (4, 9, 4), "upper_l"),
            blister(7.5, 33, -1), blister(7.5, 30, 1), blister_s(4, 35, -2.5)]),
        B("leftForearm", "leftUpperArm", (5.5, 28, 0), rotation=(-14, 0, 0), cubes=[
            C((3.75, 19, -1.75), (3.5, 9, 3.5), "fore_l"), blister(7.25, 23, 0), blister_s(5, 25, -2.25)]),
        B("leftHand", "leftForearm", (5.5, 19, 0), cubes=[
            C((3.5, 17, -2), (4, 2, 4), "palm"),
            *[C((a, 12, -1.5), (1, 5, 1), "claw", pivot=(a + 0.5, 17, -1), rotation=(-18, 0, 0)) for a in (3.6, 4.7, 5.8, 6.9)]]),
    ]
    return hd_model("geometry.hl.herobrine", bones, bounds=(3.0, 3.6, (0, 1.7, 0)))


def paint_herobrine(seed, model):
    rng = random.Random(seed)
    cv = Canvas(*model.texture)
    skin, hair, hair_l = (0x96, 0x80, 0x68), (0x20, 0x16, 0x0C), (0x36, 0x26, 0x15)
    burn, crust, raw = (0xA6, 0x2A, 0x22), (0x56, 0x14, 0x10), (0xD2, 0x4C, 0x3C)
    blister_c = (0xE6, 0xD4, 0xA4)
    shirt, dirt, blood, dried = (0x1E, 0x84, 0x86), (0x5A, 0x48, 0x34), (0x6A, 0x0E, 0x10), (0x3E, 0x0A, 0x0A)
    pants, boot = (0x2E, 0x2A, 0x72), (0x42, 0x38, 0x30)
    gut, gut_d, cavity = (0xC8, 0x6A, 0x70), (0x94, 0x3E, 0x46), (0x34, 0x06, 0x08)
    bone_c, muscle, thread = (0xE0, 0xD8, 0xC2), (0xB0, 0x3A, 0x3A), (0x18, 0x12, 0x0E)
    tooth = (0xD8, 0xCC, 0xA8)

    def grime(c, p=0.1):
        r = rng.random()
        if r < p:
            return mix(c, dirt, rng.uniform(0.3, 0.6))
        if r < p + 0.02:
            return mix(c, blood, 0.6)
        return jitter(rng, c, 3)

    def burned():
        r = rng.random()
        if r < 0.07:
            return crust
        if r < 0.13:
            return raw
        if r < 0.145:
            return blister_c
        return jitter(rng, burn, 5)

    def flesh(y, fh):
        return jitter(rng, grad(skin, y, fh, 1.0, 0.84), 3)

    # Right half: what is left of a face, one white eye. Left half: burned
    # away to the skull (B), an empty socket (K) with a glint deep inside (g).
    face = [
        "HHHHHHHHrrrrrrrr",
        "HHHHHHHHrcrrBrrr",
        "HHHhHHHHrrBBBBrr",
        "HHSSSHHSrBBBBBBr",
        "SSSSSSSSrBBBBBBr",
        "SbbbSSSSrrBKKBBc",
        "SdWWWbSSrBKKKKBr",
        "SdWWWdSSrBKgKKBr",
        "SSdddSSSrBKKKBBr",
        "SSSSSSnSrrBBBBrr",
        "SSSSSnnNrBBBBrcr",
        "SSSSSSSSrrBrrrrr",
        "TTKTTKTTKTTKTTKT",
        "TTKTTKTTKTTKTTKT",
        "KKKKKKKKKKKKKKKK",
        "KKKKKKKKKKKKKKKK",
    ]
    fpal = {"H": hair, "h": hair_l, "b": shade(skin, 0.66), "W": GLOW_WHITE, "d": shade(skin, 0.7),
            "n": shade(skin, 0.8), "N": (0x2A, 0x10, 0x0E), "T": tooth, "K": (0x10, 0x04, 0x04),
            "c": crust, "B": bone_c, "g": (0xFF, 0xE8, 0xE8, 40)}

    def left_side(face_, x, fw):
        return face_ == "left" or (face_ in ("front", "top", "bottom") and x >= fw // 2) or (face_ == "back" and x < fw // 2)

    def fn(tag, face_, x, y, fw, fh):
        left = left_side(face_, x, fw)
        if tag == "head":
            if face_ == "front":
                ch = face[y][x]
                return burned() if ch == "r" else rows(face, fpal, x, y, lambda: flesh(y, fh))
            if left:
                return bone_c if (face_ == "left" and 5 <= x <= 10 and 4 <= y <= 9 and rng.random() < 0.8) else burned()
            if face_ == "top" or y < 5 or (face_ == "back" and y < 12):
                return jitter(rng, hair_l if rng.random() < 0.15 else hair, 3)
            return flesh(y, fh)
        if tag == "head+":
            if left or face_ == "bottom":
                return CLEAR
            if face_ == "top":
                return jitter(rng, hair_l, 4) if rng.random() < 0.4 else CLEAR
            if face_ == "front":
                return jitter(rng, hair, 3) if y < 2 or (y == 2 and x % 3 == 0) else CLEAR
            return jitter(rng, hair, 3) if y < 5 and rng.random() < 0.5 else CLEAR
        if tag == "skull":
            return jitter(rng, bone_c, 4) if face_ != "back" else raw
        if tag == "fang":
            return tooth if y < fh - 1 else (0xB0, 0xA0, 0x7C)
        if tag == "nail":
            return (0x70, 0x70, 0x76) if face_ == "top" else (0x4A, 0x3A, 0x34)
        if tag == "jaw":
            if face_ == "front":
                if y < 2:
                    return tooth if x % 3 != 2 else (0x10, 0x04, 0x04)
                if y == 2:
                    return (0x5A, 0x14, 0x16)
                return burned() if x >= fw // 2 else flesh(y, fh)
            if face_ == "top":
                return (0x4A, 0x10, 0x12) if (x + y) % 4 else (0x6A, 0x1E, 0x22)
            return burned() if left else flesh(y, fh)
        if tag in ("drip", "drip_s"):
            return jitter(rng, blood if y < fh - 2 else dried, 5)
        if tag in ("blister", "blister_s"):
            return blister_c if face_ != "bottom" else raw
        if tag in ("rib", "rib_long"):
            return jitter(rng, bone_c, 4) if face_ != "back" else blood
        if tag == "lodged_pick":
            c = jitter(rng, grad((0x6C, 0x6C, 0x70), y, fh, 1.1, 0.8), 6)
            return mix(c, blood, 0.6) if rng.random() < 0.3 else c
        if tag == "lodged_handle":
            return (0x8A, 0x70, 0x50) if face_ == "back" else jitter(rng, (0x4E, 0x38, 0x24), 4)
        if tag == "cavity":
            if face_ == "front":  # the inside of the open stomach: coils of gut
                if (x + 2 * y) % 5 in (0, 1) and 0 < y < fh - 1:
                    return jitter(rng, gut if (x + y) % 2 else gut_d, 8)
                return jitter(rng, cavity, 5)
            return grime(shade(shirt, 0.85)) if face_ in ("back",) else jitter(rng, cavity, 5)
        if tag == "flank":
            if face_ in ("left", "right"):
                return jitter(rng, (0x8A, 0x1E, 0x1C), 7) if (face_ == "left") == (x < fw // 2) else grime(shade(shirt, 0.85))
            if face_ == "front":
                return thread if y % 3 == 0 else flesh(y, fh)  # stitches across the wound's edge
            return grime(shade(shirt, 0.85))
        if tag == "wound_lip":
            return thread if x % 3 == 0 else jitter(rng, blood, 6)
        if tag in ("gut", "gut_big", "gut_s", "gut_hang", "gut_knot"):
            c = gut if (x + y) % 3 else gut_d
            if tag == "gut_hang" and y >= fh - 2:
                return blood
            return jitter(rng, c, 9)
        if tag == "vertebra":
            return jitter(rng, bone_c, 5) if face_ != "bottom" else (0x7A, 0x1A, 0x18)
        if tag == "spur":
            return jitter(rng, shade(bone_c, 0.92), 4)
        if tag == "neck":
            return burned() if left else flesh(y, fh)
        if tag == "pelvis":
            return grime(grad(pants, y, fh, 1.0, 0.9), 0.06)
        if tag == "chest":
            if face_ == "back" and 7 <= x <= 10:
                return jitter(rng, (0x7A, 0x1A, 0x18), 7)  # where the spine tore out
            if face_ == "front" and y >= 9:
                return shade(flesh(y, fh), 0.76) if y % 3 == 0 and not 8 <= x <= 9 else flesh(y, fh)  # ribs
            if face_ == "front" and y < 4 and abs(x - 8.5) < 4 - y:
                return flesh(y, fh)
            if left and rng.random() < 0.3:
                return burned()
            return grime(grad(shirt, y, fh, 1.02, 0.8))
        if tag == "chest+":  # the shirt, hanging in rags
            if face_ in ("top", "bottom") or (face_ == "back" and 5 <= x <= 12):
                return CLEAR
            return grime(shade(shirt, 0.72), 0.3) if y >= 10 and rng.random() < 0.55 else CLEAR
        if tag == "upper_r":
            return grime(grad(shirt, y, fh, 1.02, 0.85)) if face_ != "bottom" else flesh(0, 1)
        if tag == "sleeve":
            return grime(shade(shirt, 0.7), 0.3) if y >= fh - 3 and face_ not in ("top", "bottom") and rng.random() < 0.7 else CLEAR
        if tag == "fore_r":  # flayed: bare muscle in strips, bone at the wrist
            if y >= fh - 2:
                return jitter(rng, bone_c, 4)
            return jitter(rng, muscle if (x // 2) % 2 else shade(muscle, 0.7), 7)
        if tag == "fist":
            return grime(shade(skin, 0.72), 0.3)
        if tag in ("upper_l", "fore_l", "palm"):
            if tag == "upper_l" and (face_ == "top" or y < 3):
                return grime(shade(shirt, 0.8), 0.4)
            return burned() if face_ != "bottom" else crust
        if tag == "claw":
            return (0x14, 0x0E, 0x0C) if y >= fh - 4 else (0x3A, 0x2A, 0x22)
        if tag in ("thigh", "shin"):
            if face_ == "bottom":
                return shade(pants, 0.7)
            c = grad(pants, y, fh, 1.02, 0.86)
            if tag == "shin" and face_ == "front" and y < 3:
                c = shade(c, 1.08)  # knee
            return mix(c, blood, 0.5) if rng.random() < 0.04 else jitter(rng, c, 3)
        if tag == "thigh+":
            return shade(pants, 0.8) if y < 2 and face_ not in ("top", "bottom") else CLEAR
        if tag == "boot":
            if face_ == "bottom" or y >= fh - 1:
                return shade(boot, 0.6)
            return jitter(rng, boot if face_ != "front" or x % 4 else shade(boot, 1.2), 3)
        if tag == "handle":
            if y < 6:
                return jitter(rng, (0x3A, 0x2E, 0x24), 3)
            return jitter(rng, (0x5C, 0x42, 0x2A) if (x + y) % 5 else (0x4A, 0x34, 0x20), 4)
        if tag in ("pick", "pick_tip"):
            c = jitter(rng, grad((0x70, 0x70, 0x74), y, fh, 1.12, 0.78), 6)
            if rng.random() < 0.18:
                return mix(c, blood, 0.7)
            return shade(c, 0.6) if rng.random() < 0.1 else c
        return None

    paint_model(cv, model, fn)
    return bake(cv, model)


# =========================================================================== #
# The Man From The Fog
# =========================================================================== #

def fog_man_model():
    """Three blocks of starved, crooked man in a long rotten coat. Hunched,
    one shoulder higher than the other, the long head tilted; a heavy brow
    over two black holes, cheekbones pushing through the skin and a grin that
    runs round to the ears on a hinged jaw. Vertebrae break through the back
    of the coat, the knees are knobs on bare stick shins, and the hands hang
    below the knees on four jointed fingers."""
    vertebra = lambda y: C((-0.5, y, 2), (1, 1, 1.5), "vertebra")
    bones = [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 21, 0), cubes=[
            C((-3, 20, -1.5), (6, 4, 3), "pelvis"), C((-3, 23, -1.5), (6, 1, 3), "belt", 0.1)]),
        B("spine", "body", (0, 24, 0), cubes=[
            C((-2.5, 24, -1.5), (5, 4, 3), "abdomen"), vertebra(24.5), vertebra(26.5)]),
        B("chest", "spine", (0, 28, 0), rotation=(12, 0, -4), cubes=[
            C((-4, 28, -2), (8, 9, 4), "chest"),
            C((-4.5, 34, -2.5), (9, 3, 5), "collar"),
            C((-5, 35, -2), (2, 2, 4), "shoulder"), C((3, 34.5, -2), (2, 2, 4), "shoulder"),
            vertebra(29), vertebra(31), vertebra(33), vertebra(35)]),
        B("coatBack", "chest", (0, 36, 2.5), cubes=[
            C((-5, 13, 2), (10, 23, 1), "coat_back"),
            *[C((x, 10, 2), (1, 3, 1), "fringe") for x in (-4.5, -2, 0.5, 3)]]),
        B("coatRight", "chest", (-3, 36, -2.5), cubes=[
            C((-5, 13, -3), (3.5, 23, 1), "coat_front"), C((-5, 30, -3.5), (1.5, 6, 0.5), "lapel")]),
        B("coatLeft", "chest", (3, 36, -2.5), cubes=[
            C((1.5, 13, -3), (3.5, 23, 1), "coat_front"), C((3.5, 30, -3.5), (1.5, 6, 0.5), "lapel")]),
        B("neck", "chest", (0, 37, 0), rotation=(-10, 0, 0), cubes=[
            C((-1, 37, -1), (2, 3.5, 2), "neck"), C((-0.5, 37.5, 0.5), (1, 2.5, 1), "neck_bone", 0.1)]),
        B("head", "neck", (0, 40, 0), rotation=(0, 0, 16), cubes=[
            C((-3, 40, -3), (6, 9, 6), "head"),
            C((-3, 47, -3.5), (6, 1, 1), "brow"),
            C((-3, 43, -3.5), (1.5, 1, 0.5), "cheek"), C((1.5, 43, -3.5), (1.5, 1, 0.5), "cheek"),
            C((-0.5, 42.5, -3.5), (1, 2, 0.5), "nose")]),
        B("jaw", "head", (0, 40.5, 2), rotation=(5, 0, 0), cubes=[
            C((-2.5, 38, -3.5), (5, 3, 5), "jaw"), C((-2, 37, -2.5), (4, 1, 3), "chin")]),
        # Long, lank strands of black hair from the back of the scalp.
        B("hair", "head", (0, 48, 3), rotation=(12, 0, 0), cubes=[
            C((-2.5, 39, 2.5), (1, 9, 1), "strand"), C((-0.5, 36, 2.5), (1, 12, 1), "strand_l"),
            C((1.5, 40, 2.2), (1, 8, 1), "strand_s"), C((-3, 43, 1), (1, 5, 1), "strand_side")]),
    ]
    for side, name, shoulder in ((-1, "right", 35), (1, "left", 36)):
        sx = lambda a, b: side_x(side, a, b)
        fingers = [C((sx(3 + k, 4 + k), shoulder - 32.5, -0.5), (1, 5.5, 1), "finger",
                      pivot=(side * (3.5 + k), shoulder - 27, 0), rotation=(-10 + 4 * k, 0, side * (k - 1.5) * 3))
                   for k in range(4)]
        bones += [
            B(f"{name}Arm", "chest", (5 * side, shoulder, 0), rotation=(0, 0, -3 * side), cubes=[
                C((sx(4, 6), shoulder - 12, -1), (2, 12, 2), "upper_arm"),
                C((sx(4, 6), shoulder - 10, -1), (2, 10, 2), "sleeve", 0.5)]),
            B(f"{name}Forearm", f"{name}Arm", (5 * side, shoulder - 12, 0), rotation=(-8, 0, 0), cubes=[
                C((sx(4.25, 5.75), shoulder - 24, -0.75), (1.5, 12, 1.5), "forearm"),
                C((sx(4, 6), shoulder - 13, -1), (2, 1.5, 2), "elbow")]),
            B(f"{name}Hand", f"{name}Forearm", (5 * side, shoulder - 24, 0), cubes=[
                C((sx(3, 7), shoulder - 27, -1), (4, 3, 1.5), "palm"),
                C((sx(2.5, 3.5) if side > 0 else sx(6.5, 7.5), shoulder - 29, -1.5), (1, 3.5, 1), "thumb"),
                *fingers]),
            B(f"{name}Leg", "root", (2 * side, 21, 0), cubes=[
                C((sx(0.5, 3.5), 11, -1.5), (3, 10, 3), "thigh"),
                C((sx(0.25, 3.75), 11, -1.75), (3.5, 2, 3.5), "cuff")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 11, 0), cubes=[
                C((sx(1, 3), 1, -1), (2, 10, 2), "shin"),
                C((sx(0.75, 3.25), 9, -1.5), (2.5, 2, 2), "knee"),
                C((sx(0.5, 3.5), 0, -4), (3, 1, 5), "foot"),
                C((sx(1, 3), 1, -1.5), (2, 1, 3), "ankle")]),
        ]
    return hd_model("geometry.hl.fog_man", bones, bounds=(3.0, 3.5, (0, 1.6, 0)))


def paint_fog_man(seed, model):
    rng = random.Random(seed)
    cv = Canvas(*model.texture)
    S = (0xBE, 0xBC, 0xB4)
    S_d, S_dd = shade(S, 0.76), shade(S, 0.58)
    coat, coat_l, coat_d = (0x2C, 0x2A, 0x2E), (0x3E, 0x3B, 0x40), (0x1A, 0x19, 0x1C)
    teeth, mouth, bone_c = (0xD8, 0xD0, 0xBC), (0x2A, 0x08, 0x0A), (0xD6, 0xD0, 0xC0)
    vein, nail = (0x8C, 0x98, 0xA8), (0x2E, 0x2A, 0x26)

    def pale(y, fh, top=1.03, bottom=0.84):
        c = jitter(rng, grad(S, y, fh, top, bottom), 3)
        r = rng.random()
        if r < 0.035:
            return shade(c, 0.9)
        if r < 0.045:
            return mix(c, vein, 0.55)
        return c

    def cloth(x, y, fh, base=coat):
        c = jitter(rng, grad(base, y, fh, 1.1, 0.82), 3)
        if (x + y) % 11 == 0:
            c = shade(c, 0.85)  # weave
        return coat_l if rng.random() < 0.05 else c

    # Front of the head, 12x18: a long pale face under a heavy brow, two
    # black holes weeping black, slits for a nose and a grin full of teeth.
    head = [
        "SShSSShSShSS",
        "hSShSSSShSSh",
        "SSSSSSSSSSSS",
        "SSSSSSSSSSSS",
        "sKKKKssKKKKs",
        "KKKKKKsKKKKK",
        "KKKgKKsKKgKK",
        "KKKKKKsKKKKK",
        "sKKKKSSKKKKs",
        "SsdKsSSsKdsS",
        "SSdSSSSSSdSS",
        "SSdSSnnSSdSS",
        "SSSSSSSSSdSS",
        "SSSSSSSSSSSS",
        "sKKKKKKKKKKs",
        "KTKTTKTTKTTK",
        "KTTKTTKTTKTK",
        "KKKKKKKKKKKK",
    ]
    hpal = {"h": (0x22, 0x20, 0x24), "s": S_d, "K": BLACK, "g": (0x5A, 0x58, 0x5E), "d": (0x16, 0x14, 0x16),
            "n": (0x22, 0x1C, 0x1E), "T": teeth}

    def fn(tag, face_, x, y, fw, fh):
        if tag == "head":
            if face_ == "front":
                return rows(head, hpal, x, y, lambda: pale(y, fh))
            if face_ == "bottom":
                return mouth
            if face_ == "top":
                return jitter(rng, (0x22, 0x20, 0x24), 3) if rng.random() < 0.05 + 0.3 * (y > fh * 0.6) else pale(y, fh)
            near_front = (face_ == "right" and x >= fw - 5) or (face_ == "left" and x <= 4)
            if y >= fh - 4 and near_front:
                return teeth if y in (fh - 3, fh - 2) and x % 2 else BLACK  # the grin wraps round the face
            if face_ == "back" and y < 6:
                return jitter(rng, (0x1C, 0x1A, 0x1E), 3)
            return pale(y, fh)
        if tag == "brow":
            return shade(pale(y, fh), 1.05) if face_ in ("top", "front") and y == 0 else S_d
        if tag == "cheek":
            return shade(pale(y, fh), 1.06) if face_ != "bottom" else S_dd
        if tag == "nose":
            if face_ == "bottom":
                return (0x22, 0x1C, 0x1E)  # nostril slits
            return S_d if face_ != "front" else pale(y, fh, 1.08, 0.9)
        if tag == "jaw":
            if face_ == "front":
                if y < 2:
                    return teeth if x % 3 else BLACK
                if y == 2:
                    return BLACK
                return pale(y, fh) if y < 5 else shade(pale(y, fh), 0.85)
            if face_ == "top":
                return mouth if (x + y) % 5 else (0x4A, 0x14, 0x18)
            near_front = (face_ == "right" and x >= fw - 4) or (face_ == "left" and x <= 3)
            if near_front and y < 2:
                return teeth if x % 2 else BLACK
            return shade(pale(y, fh), 0.9)
        if tag == "chin":
            return shade(pale(y, fh), 0.82)
        if tag in ("neck", "neck_bone"):
            if tag == "neck_bone":
                return jitter(rng, bone_c, 4) if face_ in ("back", "top") else CLEAR
            return S_d if face_ == "front" and x in (1, fw - 2) else pale(y, fh, 0.95, 0.8)  # tendons
        if tag == "chest":
            c = pale(y, fh)
            if face_ == "front":
                if y % 3 == 1 and y > 1 and x not in (7, 8):
                    return S_dd  # ribs, seen between the coat flaps
                if x in (7, 8) and y < 14:
                    return shade(c, 1.06)  # sternum
            if face_ in ("right", "left") and y % 3 == 1:
                return S_d
            return c
        if tag in ("collar", "shoulder"):
            return cloth(x, y, fh, coat_d if tag == "collar" else coat)
        if tag == "lapel":
            return cloth(x, y, fh, coat_l)
        if tag == "vertebra":
            return jitter(rng, bone_c, 4) if face_ != "bottom" else (0x5A, 0x22, 0x22)
        if tag == "sleeve":
            if face_ in ("bottom", "top"):
                return CLEAR
            if y >= fh - 4 and rng.random() < (y - (fh - 5)) * 0.24:
                return CLEAR  # frayed cuff
            if 3 < y < fh - 5 and rng.random() < 0.015:
                return CLEAR  # moth holes
            return cloth(x, y, fh)
        if tag.startswith("strand"):
            return jitter(rng, (0x14, 0x13, 0x16) if (y // 2) % 3 else (0x26, 0x24, 0x28), 3)
        if tag == "fringe":
            return CLEAR if (y >= fh - 2 and rng.random() < 0.5) else cloth(x, y, fh, coat_d)
        if tag in ("coat_back", "coat_front"):
            if face_ == "top":
                return coat_d
            if face_ == "bottom":
                return CLEAR
            # Ragged hem, moth holes, and old stains.
            if y >= fh - 6 and rng.random() < (y - (fh - 7)) * 0.13:
                return CLEAR
            if 6 < y < fh - 8 and rng.random() < 0.012:
                return CLEAR
            c = cloth(x, y, fh)
            if tag == "coat_back" and face_ == "back" and x in (9, 10) and y > 8:
                c = shade(c, 0.8)  # seam
            if face_ == "front" and tag == "coat_front" and y % 8 == 3 and x == fw // 2:
                return (0x5C, 0x56, 0x4C)  # buttons
            return c
        if tag == "pelvis":
            return jitter(rng, (0x26, 0x26, 0x2A), 3)
        if tag == "belt":
            return (0x3A, 0x2A, 0x1E) if face_ != "front" or x not in (5, 6) else (0x8A, 0x80, 0x6A)
        if tag == "abdomen":
            if face_ == "front":
                return S_d if y % 3 == 0 or x in (4, 5) and y > 3 else pale(y, fh)
            return pale(y, fh)
        if tag == "thigh":
            c = (0x30, 0x2F, 0x35) if x % 6 == 1 else (0x26, 0x26, 0x2A)
            return jitter(rng, shade(c, 0.9) if y > fh - 4 else c, 3)
        if tag == "cuff":
            if face_ in ("top", "bottom") or rng.random() < 0.3:
                return CLEAR
            return jitter(rng, (0x22, 0x22, 0x26), 3)
        if tag == "shin":
            c = pale(y, fh, 1.0, 0.76)
            return shade(c, 1.07) if face_ == "front" and x in (1, 2) else c
        if tag == "knee":
            return pale(y, fh, 1.02, 0.86)
        if tag == "ankle":
            return pale(y, fh, 0.9, 0.84)
        if tag == "foot":
            if face_ == "front" or (face_ == "top" and y < 2):
                return nail if x % 2 == 0 else pale(1, 2, 0.9, 0.84)  # long toes
            return pale(1, 2, 0.88, 0.8) if face_ != "bottom" else S_dd
        if tag == "upper_arm":
            return pale(y, fh)
        if tag == "elbow":
            return pale(y, fh, 1.02, 0.9)
        if tag == "forearm":
            c = pale(y, fh, 0.98, 0.84)
            return mix(c, vein, 0.4) if face_ == "front" and x == 1 and y % 5 < 3 else c
        if tag in ("palm", "thumb"):
            return pale(y, fh, 0.94, 0.84)
        if tag == "finger":
            if y >= fh - 2 or face_ == "bottom":
                return nail
            return S_d if y in (3, 7) else pale(y, fh, 0.95, 0.82)  # knuckles
        return None

    paint_model(cv, model, fn)
    return bake(cv, model)


# =========================================================================== #
# The Cave Dweller
# =========================================================================== #

def cave_dweller_model():
    """Something that was a person once, now on all fours: long arms planted
    ahead like a runner's, shoulder blades jutting, knees bent the wrong way,
    a ribcage lifted off the ground and a ridge of spines down the back. The
    neck is stretched, the head a flat skull with a heavy brow over sunken,
    glowing eyes, a short snout and a jaw full of needles that drops open far
    too wide. A long whip of a tail."""
    ridge = lambda y, z, h=2: C((-0.5, y, z), (1, h, 1), "ridge")
    fang = lambda x, y, z: C((x, y, z), (0.5, 1.5, 0.5), "needle")
    bones = [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 13, 6), cubes=[
            C((-3, 10.5, 4), (6, 5, 5), "pelvis"),
            C((-3.5, 12, 5), (1, 3, 3), "hip"), C((2.5, 12, 5), (1, 3, 3), "hip"),
            ridge(15, 5.5), ridge(15, 7.5, 1.5)]),
        B("abdomen", "body", (0, 13, 4), cubes=[
            C((-2.5, 11, -2), (5, 4, 6), "abdomen"),
            ridge(14.5, -0.5), ridge(14.5, 2)]),
        B("ribcage", "abdomen", (0, 13, -2), rotation=(-14, 0, 0), cubes=[
            C((-4, 10, -10), (8, 7, 8), "ribcage"),
            C((-4.5, 11, -9), (0.5, 5, 6), "ribs_side"), C((4, 11, -9), (0.5, 5, 6), "ribs_side"),
            C((-3.5, 16.5, -8), (2.5, 1, 4), "blade", pivot=(-2, 17, -6), rotation=(0, 0, 12)),
            C((1, 16.5, -8), (2.5, 1, 4), "blade", pivot=(2, 17, -6), rotation=(0, 0, -12)),
            ridge(16.5, -3.5), ridge(16.5, -6, 2.5), ridge(16.5, -9)]),
        B("neck", "ribcage", (0, 15, -10), rotation=(-20, 0, 0), cubes=[
            C((-1.5, 13.5, -16), (3, 3, 6), "neck"), ridge(16, -13, 1.5)]),
        B("head", "neck", (0, 15, -16), rotation=(26, 0, 0), cubes=[
            C((-3.5, 14, -22), (7, 5, 6), "skull"),
            C((-3.5, 17.5, -22.5), (7, 1, 1), "brow"),
            C((-1.5, 14, -24), (3, 2.5, 2), "snout"),
            C((-4, 15, -19), (0.5, 3, 2), "ear"), C((3.5, 15, -19), (0.5, 3, 2), "ear"),
            *[fang(x, 12.5, -23.5) for x in (-1.5, -0.25, 1)]]),
        B("jaw", "head", (0, 14.5, -17), rotation=(10, 0, 0), cubes=[
            C((-3.5, 11.5, -23), (7, 2.5, 6), "jaw"),
            *[fang(x, 14, -22.75) for x in (-3, -1.75, -0.5, 0.75, 2)]]),
    ]
    for side, name in ((-1, "Right"), (1, "Left")):
        sx = lambda a, b: side_x(side, a, b)
        claws = [C((sx(a, a + 1), -8, -13.5), (1, 1, 4.5), "claw", pivot=(side * (a + 0.5), -7.5, -9.5),
                   rotation=(-12, side * (a - 4) * 8, 0)) for a in (3, 4, 5)]
        bones += [
            # Front limbs: long arms planted ahead, elbows out.
            B(f"arm{name}", "ribcage", (4.5 * side, 15, -7), rotation=(-30, 0, -38 * side), cubes=[
                C((sx(3.5, 5.5), 2, -8), (2, 13, 2), "upper_arm"),
                C((sx(3.25, 5.75), 11, -8.25), (2.5, 4, 2.5), "shoulder")]),
            B(f"forearm{name}", f"arm{name}", (4.5 * side, 2, -7), rotation=(58, 0, 34 * side), cubes=[
                C((sx(3.75, 5.25), -7, -7.75), (1.5, 9, 1.5), "forearm"),
                C((sx(3.5, 5.5), 0.5, -8), (2, 2, 2), "elbow")]),
            B(f"hand{name}", f"forearm{name}", (4.5 * side, -7, -7), rotation=(-28, 0, 4 * side), cubes=[
                C((sx(3, 6), -7.5, -9.5), (3, 1.5, 3), "palm"),
                *claws]),
            # Hind legs: knees forward, shins back, long feet.
            B(f"thigh{name}", "body", (3.5 * side, 12.5, 7), rotation=(-62, 0, -12 * side), cubes=[
                C((sx(2, 5), 2.5, 5.5), (3, 10, 3), "thigh")]),
            B(f"shin{name}", f"thigh{name}", (3.5 * side, 2.5, 7), rotation=(100, 0, 8 * side), cubes=[
                C((sx(2.5, 4.5), -6.5, 6), (2, 9, 2), "shin"),
                C((sx(2.25, 4.75), 1, 5.75), (2.5, 2, 2.5), "knee")]),
            B(f"foot{name}", f"shin{name}", (3.5 * side, -6.5, 7), rotation=(-38, 0, 0), cubes=[
                C((sx(2, 5), -7.5, 2), (3, 1, 6), "foot"),
                *[C((sx(a, a + 0.5), -7, 1), (0.5, 1, 1), "toe_claw") for a in (2.25, 3.25, 4.25)]]),
        ]
    bones += [
        B("tail1", "body", (0, 12.5, 9), rotation=(-18, 0, 0), cubes=[
            C((-1, 11.5, 9), (2, 2, 6), "tail1"), ridge(13.5, 11, 1)]),
        B("tail2", "tail1", (0, 12.5, 15), rotation=(16, 0, 0), cubes=[C((-0.75, 11.75, 15), (1.5, 1.5, 6), "tail2")]),
        B("tail3", "tail2", (0, 12.5, 21), rotation=(12, 0, 0), cubes=[
            C((-0.5, 12, 21), (1, 1, 6), "tail3"), C((-0.5, 12.5, 26.5), (1, 1.5, 1), "tail_barb")]),
    ]
    return hd_model("geometry.hl.cave_dweller", bones, bounds=(3.5, 2.0, (0, 0.8, 0)))


def paint_cave_dweller(seed, model):
    rng = random.Random(seed)
    cv = Canvas(*model.texture)
    D = (0x9E, 0x9A, 0x8E)
    D_d, D_dd = shade(D, 0.68), shade(D, 0.5)
    wet = (0xC6, 0xC2, 0xB6)
    teeth, mouth, gum = (0xDE, 0xD6, 0xC0), (0x44, 0x0E, 0x12), (0x6A, 0x1E, 0x22)
    glow = (0xFF, 0xF0, 0xB0, 60)
    vein = (0x6E, 0x7C, 0x80)

    def skin(y, fh, top=1.05, bottom=0.8):
        c = jitter(rng, grad(D, y, fh, top, bottom), 4)
        r = rng.random()
        if r < 0.022:
            return wet
        if r < 0.06:
            return shade(c, 0.84)
        if r < 0.1:
            return mix(c, vein, 0.5)
        return c

    # Front of the skull, 14x10: a brow shadow, two deep sockets with a glow
    # far inside, and the upper row of needles.
    skull = [
        "SkSSSSSSSSSSkS",
        "ssssssSSssssss",
        "sKKKKsSSsKKKKs",
        "KKKKKKSSKKKKKK",
        "KKKggKSSKggKKK",
        "KKKggKSSKggKKK",
        "sKKKKsSSsKKKKs",
        "SsKKsSSSSsKKsS",
        "SSSSSSSSSSSSSS",
        "SSSSSSSSSSSSSS",
    ]
    spal = {"s": D_d, "K": BLACK, "k": (0x1A, 0x16, 0x14), "g": glow}

    def fn(tag, face_, x, y, fw, fh):
        if tag == "skull":
            if face_ == "front":
                return rows(skull, spal, x, y, lambda: skin(y, fh))
            if face_ == "bottom":
                return mouth
            if face_ == "top" and x in (fw // 2 - 1, fw // 2):
                return D_d
            if face_ in ("right", "left") and y > fh - 3:
                return BLACK  # the mouth runs back along the sides
            return skin(y, fh)
        if tag == "brow":
            return skin(y, fh, 1.1, 1.0) if face_ in ("top", "front") else D_dd
        if tag == "snout":
            if face_ == "front":
                return (0x2E, 0x28, 0x28) if y == 1 and x in (1, 4) else skin(y, fh, 1.0, 0.86)
            if face_ == "bottom":
                return gum
            return skin(y, fh)
        if tag == "ear":
            return D_d if face_ in ("right", "left") and 1 <= y <= 4 and x > 0 else skin(y, fh, 0.95, 0.8)
        if tag == "needle":
            return teeth if face_ != "top" else gum
        if tag == "jaw":
            if face_ == "front":
                return BLACK if y == 0 else skin(y, fh, 0.95, 0.8)
            if face_ == "top":
                return gum if 4 <= x <= 9 and y > 2 else mouth  # tongue
            if face_ in ("right", "left") and y == 0:
                return (0x4A, 0x16, 0x18)
            return skin(y, fh, 0.9, 0.78)
        if tag == "neck":
            return D_d if face_ in ("right", "left", "top") and x % 3 == 0 else skin(y, fh)
        if tag == "ribcage":
            if face_ in ("right", "left") and x % 3 == 0 and 2 <= y <= 11:
                return D_d  # ribs pressing through the skin
            if face_ == "bottom":
                return jitter(rng, shade(wet, 0.9), 4)
            if face_ == "top" and x in (7, 8):
                return D_d
            return skin(y, fh)
        if tag == "ribs_side":
            if face_ in ("right", "left"):
                return shade(skin(y, fh), 1.08) if y % 3 == 0 else D_dd
            return D_d
        if tag == "blade":
            return skin(y, fh, 1.12, 0.95) if face_ == "top" else D_d
        if tag == "abdomen":
            if face_ in ("right", "left"):
                return shade(skin(y, fh), 0.8)  # sunken belly
            if face_ == "bottom":
                return mix(skin(y, fh), vein, 0.3) if rng.random() < 0.3 else skin(y, fh)
            return skin(y, fh)
        if tag in ("pelvis", "hip"):
            return D_d if face_ in ("right", "left") and y == 2 else skin(y, fh)
        if tag == "ridge":
            return (0x5A, 0x52, 0x46) if face_ == "top" else jitter(rng, (0xD6, 0xCE, 0xBA), 5)
        if tag in ("tail1", "tail2", "tail3"):
            return D_d if face_ == "top" and y % 3 == 0 else skin(y, fh, 1.0, 0.7)
        if tag == "tail_barb":
            return jitter(rng, (0xD6, 0xCE, 0xBA), 5)
        if tag in ("upper_arm", "thigh"):
            if face_ in ("right", "left") and x == fw // 2 and y % 4 < 2:
                return mix(skin(y, fh), vein, 0.5)
            return D_d if y >= fh - 3 else skin(y, fh)
        if tag in ("shoulder", "elbow", "knee"):
            return skin(y, fh, 1.08, 0.9)
        if tag in ("forearm", "shin"):
            return skin(y, fh, 1.0, 0.55)
        if tag in ("palm", "foot"):
            return skin(0, 2, 0.7, 0.6)
        if tag in ("claw", "toe_claw"):
            return BLACK if face_ == "front" or y == fh - 1 else jitter(rng, (0x3A, 0x34, 0x2E), 4)
        return None

    paint_model(cv, model, fn)
    return bake(cv, model)


# =========================================================================== #
# null: a player-shaped render error
# =========================================================================== #

def null_model():
    """A player that failed to load. The torso is sliced into three bands that
    don't line up, a strip of the head has come loose and hangs offset, one
    arm is far longer than the other and ends in three long fingers, and a
    halo of stray pixels and missing texture circles the head."""
    px = lambda x, y, z: C((x, y, z), (1, 1, 1), "px")
    return hd_model("geometry.hl.null", [
        B("root", None, (0, 0, 0)),
        B("rightLeg", "root", (-2, 12, 0), cubes=[C((-4, 0, -2), (4, 12, 4), "leg")]),
        B("leftLeg", "root", (2, 12, 0), cubes=[C((0, 0, -2), (4, 12, 4), "leg"), C((0, 5, -2), (4, 2, 4), "leg_cut", 0.3)]),
        B("torsoLow", "root", (0, 12, 0), cubes=[C((-4, 12, -2), (8, 4, 4), "slice_low")]),
        B("torsoMid", "torsoLow", (0, 16, 0), cubes=[C((-2, 16, -2), (8, 4, 4), "slice_mid")]),
        B("torsoHigh", "torsoMid", (0, 20, 0), cubes=[C((-5, 20, -2), (8, 4, 4), "slice_high")]),
        B("head", "torsoHigh", (-1, 24, 0), rotation=(0, 0, -9), cubes=[
            C((-5, 24, -4), (8, 8, 8), "head"), C((-5, 24, -4), (8, 8, 8), "head+", 0.5),
            C((-3, 24.5, -4.5), (8, 1.5, 8), "head_strip")]),
        B("rightArm", "torsoHigh", (-6.5, 23, 0), cubes=[C((-8, 11, -1.5), (3, 12, 3), "arm")]),
        B("leftArm", "torsoHigh", (4.5, 23, 0), cubes=[
            C((3, 5, -1.5), (3, 18, 3), "longarm"),
            *[C((3 + k, 1.5, -1), (1, 3.5, 1), "null_finger", pivot=(3.5 + k, 5, -0.5), rotation=(-6 * k, 0, (k - 1) * 6))
              for k in (0, 1, 2)]]),
        B("halo", "head", (-1, 28, 0), cubes=[
            px(-8, 30, -1), px(4, 33, 2), px(-3, 35, -3), px(5, 26, -2), px(-9, 25, 2), px(1, 34, 3),
            px(-6, 34, 3), px(6, 30, 1),
            C((2, 35, -1), (2, 2, 2), "missing"), C((-10, 28, -2), (2, 2, 2), "missing"),
            C((5.5, 28, 2.5), (1.5, 1.5, 1.5), "missing_s")]),
    ], bounds=(2.5, 2.8, (0, 1.4, 0)))


def paint_null(seed, model):
    rng = random.Random(seed)
    cv = Canvas(*model.texture)
    magenta, cyan, white = (0xF8, 0x00, 0xF8), (0x00, 0xE0, 0xE0), (0xFF, 0xFF, 0xFF)
    # What is left of a default skin, drained of colour, shows through the
    # static in places.
    ghost = {"head": (0x4A, 0x3A, 0x30), "slice": (0x14, 0x40, 0x42), "arm": (0x14, 0x40, 0x42),
             "longarm": (0x3E, 0x30, 0x28), "leg": (0x1E, 0x1C, 0x46)}

    def void(y, tag):
        g = rng.randint(4, 12) + (6 if y % 2 else 0)  # scanlines
        c = (g, g, g + 2)
        base = ghost.get(tag.split("_")[0])
        if base and rng.random() < 0.3:
            return mix(c, base, rng.uniform(0.3, 0.6))
        return c

    tears = {}

    def tear(key, fw, p):
        if key not in tears:
            start = rng.randint(0, fw - 2)
            tears[key] = (start, start + rng.randint(2, 7), rng.choice([magenta, cyan, white])) if rng.random() < p else None
        return tears[key]

    # Front of the head, 16x16: two white eyes, one of them bleeding its
    # light down the face, and a mouth that is only a row of dead pixels.
    face = [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..WWW.....WWW...",
        "..WWW.....WWW...",
        "..wbw......w....",
        "...b............",
        "...w............",
        "...b............",
        "................",
        "....mmmmmmm.....",
        "................",
        "................",
    ]

    def fn(tag, face_, x, y, fw, fh):
        if tag == "head+":  # a band of static that crawls across the face
            if face_ == "front" and 5 <= y <= 9:
                return CLEAR
            t = tear((face_, y // 2), fw, 0.16)
            return t[2] if t and t[0] <= x < t[1] else CLEAR
        if tag == "head_strip":  # a loose slice of the same head, offset
            if face_ == "front":
                if y == 1 and 2 <= x <= 8:
                    return rng.choice([(0x30, 0x30, 0x36), magenta])  # the mouth, dragged along with it
                return rng.choice([magenta, cyan]) if rng.random() < 0.08 else void(y, "head")
            return void(y, "head") if rng.random() < 0.7 else CLEAR
        if tag in ("missing", "missing_s"):
            n = max(1, fw // 2)
            return magenta if (x // n + y // n) % 2 == 0 else (0, 0, 0)
        if tag == "px":
            return rng.choice([magenta, cyan, white])
        if tag == "head" and face_ == "front":
            ch = face[y][x]
            if ch == "W":
                return GLOW_WHITE
            if ch == "w":
                return (0xFF, 0xFF, 0xFF, 120)
            if ch == "b":
                return (0x2A, 0x2A, 0x34)
            if ch == "m":
                return rng.choice([(0x30, 0x30, 0x36), magenta, (0x30, 0x30, 0x36)])
        if tag.startswith("slice") and face_ not in ("top", "bottom") and y >= fh - 2:
            return rng.choice([magenta, cyan, void(y, tag)])  # the seams where the slices tore apart
        if tag == "leg_cut":
            return rng.choice([magenta, cyan, CLEAR, CLEAR]) if face_ not in ("top", "bottom") else CLEAR
        if tag == "longarm" and y >= fh - 6 and rng.random() < 0.3:
            return rng.choice([magenta, cyan, white])
        if tag == "null_finger":
            return (0x04, 0x04, 0x06) if y >= fh - 3 else void(y, "longarm")
        if face_ not in ("top", "bottom"):
            t = tear((tag, face_, y), fw, 0.022)
            if t and t[0] <= x < t[1]:
                return t[2]  # stray lines of corrupted pixels
        return void(y, tag)

    paint_model(cv, model, fn)
    return cv
