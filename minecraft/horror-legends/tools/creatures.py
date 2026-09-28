"""The creatures of Horror Legends: models and textures, at double detail.

Every model is built at twice its in-game size, painted against that (box
UVs map one texel per unit, so a head gets a 16x16 face instead of 8x8) and
written out at its real size with the texture size in the JSON halved (see
Model.detail). In game only the textures gain detail: sizes, bone pivots and
animations stay in ordinary pixels.

Model coordinates below are written in ordinary in-game pixels (half-pixel
steps allowed) and doubled by the B() and C() helpers.
"""

import math
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


AT = {"x": 0, "y": 0}  # absolute texel being painted, for noise that runs across faces


def paint_model(cv, model, fn):
    """fn(tag, face, x, y, fw, fh) -> colour or None, for every cube. While
    fn runs, AT holds the absolute texture coordinates of the texel."""
    for bone in model.bones:
        for cube in bone.cubes:
            w, h, d = (int(s) for s in cube.size)
            origins = box_faces(*cube.uv, w, h, d)

            def paint(face, x, y, fw, fh, t=cube.share, o=origins):
                AT["x"], AT["y"] = o[face][0] + x, o[face][1] + y
                return fn(t, face, x, y, fw, fh)
            paint_box(cv, *cube.uv, w, h, d, paint)


class Noise:
    """Smooth value noise in 0..1, for mottled skin, stains and weave instead
    of per-pixel speckle."""

    def __init__(self, seed):
        rng = random.Random(seed)
        self.table = [rng.random() for _ in range(2048)]

    def _at(self, ix, iy):
        return self.table[hash((ix, iy, 7)) % 2048]

    def __call__(self, x=None, y=None, scale=4.0, octaves=2):
        x = AT["x"] if x is None else x
        y = AT["y"] if y is None else y
        total, amp, norm = 0.0, 1.0, 0.0
        for _ in range(octaves):
            fx, fy = x / scale, y / scale
            ix, iy = math.floor(fx), math.floor(fy)
            tx, ty = fx - ix, fy - iy
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            top = self._at(ix, iy) * (1 - tx) + self._at(ix + 1, iy) * tx
            bottom = self._at(ix, iy + 1) * (1 - tx) + self._at(ix + 1, iy + 1) * tx
            total += (top * (1 - ty) + bottom * ty) * amp
            norm += amp
            amp *= 0.5
            scale /= 2
        return total / norm


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
    n = Noise(seed)
    cv = Canvas(*model.texture)

    def soft(c, _amount=0):
        """Gentle cloth and skin texture from smooth noise, not speckle."""
        return jitter(rng, shade(c, 0.9 + 0.17 * n(scale=3)), 1)

    skin, skin_d = (0xC6, 0x94, 0x76), (0xA8, 0x78, 0x5C)
    hair, hair_l = (0x3A, 0x26, 0x14), (0x52, 0x37, 0x1E)
    shirt, pants, shoe = (0x1C, 0xA2, 0xA6), (0x3E, 0x38, 0x9C), (0x5A, 0x5A, 0x60)
    eye = GLOW_WHITE if white_eyes else None
    face = [
        "HHHHHHHHHHHHHHHH",
        "HHhHHHHHHHhhHHHH",
        "HHHhhHHHHhhHHHhH",
        "HHHHHhHHhHHHHHHH",
        "HHSSSHHSSSHHSSHH",
        "HSSSSSSSSSSSSSSH",
        "SSSSSSSSSSSSSSSS",
        "SSbbbbSSSSbbbbSS",
        "SSWwPPSSSSPPwWSS",
        "SSWWPpSSSSpPWWSS",
        "SSSSSSSnnSSSSSSS",
        "SSSSSSnNNnSSSSSS",
        "SSSSmmmmmmmmSSSS",
        "SSSmmMrrrrMmmSSS",
        "SSSSmmmmmmmmSSSS",
        "SSSSSmmmmmmSSSSS",
    ]
    fpal = {"H": hair, "h": hair_l, "b": shade(hair, 1.15), "W": eye or (0xF2, 0xF2, 0xF2),
            "w": eye or (0xFF, 0xFF, 0xFF), "P": eye or (0x3A, 0x4E, 0x9E), "p": eye or (0x22, 0x2E, 0x66),
            "n": skin_d, "N": shade(skin_d, 0.8), "m": (0x7A, 0x50, 0x38), "M": (0x52, 0x32, 0x22),
            "r": (0x8E, 0x4A, 0x44)}

    def fn(tag, face_, x, y, fw, fh):
        if tag == "head":
            if face_ == "front":
                return rows(face, fpal, x, y, lambda: soft(grad(skin, y, fh, 1.03, 0.94), 2))
            if face_ == "top":
                return soft(hair_l if n(scale=1.5) > 0.6 else hair, 3)  # tousled
            if face_ == "bottom":
                return shade(skin, 0.85)
            if face_ == "back":
                return soft(hair if y < 13 - (x % 3 == 0) else skin_d, 3)
            back = x < 8 if face_ == "right" else x >= 8
            if y < 5 or (back and y < 11):
                return soft(hair, 3)
            if 8 <= y <= 11 and not back and x in ((9, 10) if face_ == "right" else (5, 6)):
                return skin_d  # ear
            return soft(grad(skin, y, fh, 1.0, 0.92), 2)
        if tag == "head+":
            if face_ == "top" and (x in (0, 1, fw - 2, fw - 1) or y in (0, 1, fh - 2, fh - 1)) and rng.random() < 0.35:
                return soft(hair_l, 4)
            if face_ in ("right", "left", "back") and y < 4 and rng.random() < 0.2:
                return soft(hair, 4)
            return CLEAR
        if tag == "body":
            if face_ == "bottom":
                return pants
            if face_ == "front" and y < 4 and abs(x - 7.5) < 4 - y:
                return grad(skin, y, 4)  # open collar
            if y >= fh - 2 and face_ != "top":
                return (0x5C, 0x40, 0x26) if not (face_ == "front" and x in (7, 8)) else (0xB8, 0xB0, 0x90)  # belt
            c = grad(shirt, y, fh - 2, 1.05, 0.86)
            if face_ == "front" and x in (7, 8) and y >= 4:
                c = shade(c, 0.88)  # placket
            if face_ == "front" and x == 8 and y > 4 and y % 4 == 1:
                return (0xE6, 0xE6, 0xE0)  # buttons
            if face_ == "front" and 2 <= x <= 5 and 6 <= y <= 9:
                c = shade(c, 0.9 if y == 6 or x in (2, 5) else 0.97)  # pocket
            if face_ in ("front", "back") and x in (4, 11) and 10 < y < 20:
                c = shade(c, 0.94)  # folds
            return soft(c, 2)
        if tag == "body+":
            return shade(shirt, 0.8) if y >= fh - 2 and face_ not in ("top", "bottom") else CLEAR
        if tag == "arm":
            if face_ == "top" or (face_ != "bottom" and y < 9):
                if y in (7, 8):
                    return soft(shade(shirt, 1.12 if y == 7 else 0.8), 2)  # rolled-up sleeve
                return soft(grad(shirt, y, 8, 1.05, 0.95), 2)
            if face_ == "bottom" or y >= fh - 3:
                return soft(skin_d, 2)
            return soft(grad(skin, y, fh, 1.0, 0.9), 2)
        if tag == "arm+":
            return shade(shirt, 0.78) if y in (6, 7) and face_ not in ("top", "bottom") else CLEAR
        if tag == "leg":
            if face_ == "bottom" or (face_ != "top" and y >= fh - 4):
                if y >= fh - 2 or face_ == "bottom":
                    return (0xE0, 0xDE, 0xD6) if face_ != "bottom" else (0xB0, 0xAE, 0xA6)  # white soles
                if face_ == "front" and y == fh - 4 and x % 3 == 1:
                    return (0xDA, 0xDA, 0xDA)  # laces
                return soft(shoe, 2)
            c = grad(pants, y, fh - 4, 1.04, 0.88)
            if face_ == "front" and y < 4 and (x == 2 or (x == 13)):
                c = shade(c, 0.8)  # pocket seams
            if face_ in ("front", "back") and x in (1, fw - 2):
                c = shade(c, 1.06)  # denim stitching
            if face_ == "front" and 9 <= y <= 11:
                c = shade(c, 1.06)  # knees
            if face_ in ("right", "left") and x in (3, 4):
                c = shade(c, 0.9)  # side seam
            return soft(c, 2)
        if tag == "leg+":
            return shade(pants, 0.85) if y == fh - 5 and face_ not in ("top", "bottom") else CLEAR
        if tag == "tool_handle":
            return soft((0x6A, 0x4C, 0x2E) if y % 2 else (0x58, 0x3E, 0x24), 3)
        if tag in ("tool_head", "tool_tip"):
            return soft(grad((0xD4, 0xD4, 0xDC), y, fh, 1.1, 0.8), 3)
        return None

    paint_model(cv, model, fn)
    return bake(cv, model)


# =========================================================================== #
# Herobrine: the hollow miner
# =========================================================================== #

def herobrine_model():
    """Three blocks of dead miner folded over his own hunch. Long legs, a
    narrow tipped pelvis, and a huge bowed back with the spine torn out of it
    in an arc of bone from the waist over the shoulders; the head hangs out in
    front on a stretched neck. The chest has burst open, ribs splayed like a
    second jaw round a dark heart; below it the stomach is an open, stitched
    cavity spilling guts. The mouth is torn to the ears over a jaw that hangs
    loose and drips, the left half of the face is burned to the skull, blisters
    cover the burned side, and the arms reach his knees: a flayed right arm
    dragging his pickaxe, a burned left one ending in bone claws."""
    blister = lambda x, y, z: C((x, y, z), (1, 1, 1), "blister")
    blister_s = lambda x, y, z: C((x, y, z), (0.5, 0.5, 0.5), "blister_s")
    vertebra = lambda y, z: C((-1, y, z), (2, 1.5, 1.5), "vertebra")
    spur = lambda y, z: C((-0.5, y, z), (1, 1, 1.5), "spur")
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Thigh", "root", (2 * side, 22, 0), cubes=[
                C((sx(0.5, 3.5), 12, -1.5), (3, 10, 3), "thigh"), C((sx(0.5, 3.5), 12, -1.5), (3, 10, 3), "thigh+", 0.3)]),
            B(f"{name}Shin", f"{name}Thigh", (2 * side, 12, 0), rotation=(6, 0, 0), cubes=[
                C((sx(0.75, 3.25), 1.5, -1.25), (2.5, 10.5, 2.5), "shin"),
                C((sx(0.5, 3.5), 9.5, -1.75), (3, 2.5, 3), "knee"),
                C((sx(0.25, 3.75), 0, -3.5), (3.5, 2, 5), "boot")]),
        ]
    bones += [
        B("body", "root", (0, 22, 0), rotation=(12, 0, 0), cubes=[
            C((-3.5, 22, -2), (7, 3, 4), "pelvis"), C((-3.5, 24, -2), (7, 1, 4), "belt", 0.15),
            vertebra(22.5, 2), vertebra(24, 2)]),
        # The stomach: a stitched-open cavity, flesh at the sides, guts inside.
        B("belly", "body", (0, 25, 0), cubes=[
            C((-3, 25, 0), (6, 6, 2), "cavity"),
            C((-3.5, 25, -2), (1, 6, 4), "flank"), C((2.5, 25, -2), (1, 6, 4), "flank"),
            C((-3, 30.5, -2), (6, 0.5, 2), "wound_lip"), C((-3, 25, -2), (6, 0.5, 2), "wound_lip"),
            C((-2.5, 25.5, -1.5), (2.5, 1.5, 1.5), "gut"), C((0.5, 26.5, -1.5), (2, 1.5, 1.5), "gut"),
            C((-1.5, 28, -1.8), (3, 1.5, 1.5), "gut_big"), C((-2.5, 29.5, -1.3), (2, 1, 1), "gut_s"),
            C((1, 29.5, -1.3), (1.5, 1, 1), "gut_s"),
            vertebra(25.5, 2), vertebra(27.5, 2), vertebra(29.5, 2)]),
        B("gutHang", "belly", (0, 26, -1.5), cubes=[
            C((-0.5, 19.5, -2), (1, 6.5, 1), "gut_hang"), C((-1, 18.5, -2.5), (2, 1.5, 1.5), "gut_knot")]),
        # The hunch. The spine runs up the outside of the back and arches over
        # the shoulders; the ribcage has burst open at the front.
        B("chest", "belly", (0, 31, 0), rotation=(26, 0, -6), cubes=[
            C((-5, 31, -3), (10, 9, 6), "chest"), C((-5, 31, -3), (10, 9, 6), "chest+", 0.3),
            C((-3, 32, -3.5), (6, 6, 0.5), "chest_hole"),
            C((-1.5, 33.5, -3.75), (2.5, 2.5, 1.5), "heart"),
            *[C((0.5, y, -4), (4, 1, 1), "rib", pivot=(0.5, y + 0.5, -3.5), rotation=(0, 40, 0)) for y in (32, 34, 36)],
            *[C((-4.5, y, -4), (4, 1, 1), "rib", pivot=(-0.5, y + 0.5, -3.5), rotation=(0, -40, 0)) for y in (32, 34, 36)],
            *[vertebra(y, 3) for y in (31.5, 33.5, 35.5, 37.5)],
            *[spur(y, 4.5) for y in (32, 34, 36, 38)],
            C((-1, 39.5, 2), (2, 1.5, 2), "vertebra_top"), C((-1, 40.5, 0.5), (2, 1.5, 2), "vertebra_top"),
            C((-1, 41, -1.2), (2, 1.5, 2), "vertebra_top"),
            C((1, 36.5, 3), (7, 1, 1), "lodged_pick", pivot=(4.5, 37, 3.5), rotation=(0, 0, 32)),
            C((4, 36.5, 3.5), (1, 1, 4), "lodged_handle", pivot=(4.5, 37, 3.5), rotation=(25, 0, 0)),
            blister(4.5, 38, -1.5), blister(5, 35.5, 0.5), blister_s(5, 33.5, -2), blister_s(3.5, 40, 1)]),
        B("neck", "chest", (0, 40, -1), rotation=(48, 0, 0), cubes=[
            C((-1.5, 40, -2.5), (3, 5, 3), "neck"), C((-1, 40.5, -3), (0.5, 4, 0.5), "tendon"),
            C((0.5, 40.5, -3), (0.5, 4, 0.5), "tendon")]),
        B("head", "neck", (0, 45, -1), rotation=(-72, 0, 12), cubes=[
            C((-4, 45, -5), (8, 8, 8), "head"), C((-4, 45, -5), (8, 8, 8), "head+", 0.5),
            C((1, 48, -5.5), (3, 3.5, 1), "skull"),
            *[C((x, 44, -5.25), (1, 1.5, 1), "fang") for x in (-3, -1, 1, 2.5)],
            C((-2.5, 53, -2), (1, 1, 1), "nail"), C((-0.5, 53, 1), (1, 1, 1), "nail"), C((2, 53, -0.5), (1, 1, 1), "nail"),
            blister(3.5, 50.5, -5.5), blister(4, 47.5, -3), blister(4, 51, 0), blister_s(2, 53, -4), blister_s(4, 49, 1.5)]),
        # Hanging off its hinges, lolling to one side.
        B("jaw", "head", (0, 45.5, 1), rotation=(30, 0, 12), cubes=[
            C((-3.5, 42.5, -5.5), (7, 3, 5.5), "jaw"),
            *[C((x, 45.5, -5.25), (1, 1, 1), "fang_low") for x in (-3, -1.5, 0, 1.5)],
            C((-1, 42, -6), (2, 1, 3), "tongue")]),
        B("dripL", "jaw", (1.5, 42.5, -5), cubes=[C((1, 37.5, -5.25), (1, 5, 1), "drip")]),
        B("dripR", "jaw", (-1.5, 42.5, -5), cubes=[C((-2, 39, -5.25), (1, 3.5, 1), "drip_s")]),
        # Right arm: skinned from the elbow down, a bone through the wrist.
        B("rightUpperArm", "chest", (-6, 39, 0), rotation=(-30, 0, 4), cubes=[
            C((-8, 28, -2), (4, 11, 4), "upper_r"), C((-8, 28, -2), (4, 11, 4), "sleeve", 0.3)]),
        B("rightForearm", "rightUpperArm", (-6, 28, 0), rotation=(-10, 0, 0), cubes=[
            C((-7.75, 17, -1.75), (3.5, 11, 3.5), "fore_r"), C((-6.5, 18, -2.5), (1, 5, 1), "bone_out")]),
        B("rightHand", "rightForearm", (-6, 17, 0), cubes=[C((-8, 14, -2), (4, 3, 4), "fist")]),
        B("pickaxe", "rightHand", (-6, 15.5, 0), rotation=(-12, 0, 0), cubes=[
            C((-6.5, 5, -0.5), (1, 11, 1), "handle"),
            C((-6.5, 3.5, -4.5), (1, 2, 9), "pick"),
            C((-6.5, 5.5, -4.5), (1, 1, 1), "pick_tip"), C((-6.5, 5.5, 3.5), (1, 1, 1), "pick_tip")]),
        # Left arm: burned black and blistered, ending in bone claws.
        B("leftUpperArm", "chest", (6, 39, 0), rotation=(-22, 0, -6), cubes=[
            C((4, 28, -2), (4, 11, 4), "upper_l"),
            blister(8, 34, -1), blister(8, 30.5, 1), blister_s(4.5, 36, -2.5), blister_s(7.5, 37, 1.5)]),
        B("leftForearm", "leftUpperArm", (6, 28, 0), rotation=(-16, 0, 0), cubes=[
            C((4.25, 17, -1.75), (3.5, 11, 3.5), "fore_l"), blister(7.75, 22, 0), blister_s(5.5, 24, -2.25),
            blister_s(4, 19, 0.5)]),
        B("leftHand", "leftForearm", (6, 17, 0), cubes=[
            C((4, 15, -2), (4, 2, 4), "palm"),
            *[C((a, 9, -1.5), (1, 6, 1), "claw", pivot=(a + 0.5, 15, -1), rotation=(-20, 0, (a - 5.7) * 5))
              for a in (4.1, 5.2, 6.3, 7.4)]]),
    ]
    return hd_model("geometry.hl.herobrine", bones, bounds=(3.0, 3.8, (0, 1.8, 0)))


def paint_herobrine(seed, model):
    rng = random.Random(seed)
    n, n2 = Noise(seed), Noise(seed + 1)
    cv = Canvas(*model.texture)
    skin, hair, hair_l = (0x9A, 0x86, 0x72), (0x1E, 0x14, 0x0C), (0x3A, 0x28, 0x16)
    bruise, rot = (0x6A, 0x4E, 0x5E), (0x6E, 0x74, 0x4E)
    burn, crust, raw = (0xA8, 0x2C, 0x22), (0x3E, 0x0E, 0x0A), (0xD8, 0x5A, 0x48)
    blister_c, pus = (0xEE, 0xDC, 0xAE), (0xD8, 0xC8, 0x6A)
    shirt, pants, boot = (0x1A, 0x7C, 0x80), (0x2A, 0x28, 0x6A), (0x3E, 0x33, 0x2A)
    blood, dried = (0x76, 0x0C, 0x0E), (0x3A, 0x08, 0x08)
    gut, gut_d, cavity = (0xC4, 0x66, 0x6E), (0x86, 0x34, 0x40), (0x2C, 0x04, 0x06)
    bone_c, muscle, thread, tooth = (0xE2, 0xD8, 0xBE), (0xB2, 0x36, 0x38), (0x16, 0x10, 0x0C), (0xDC, 0xCE, 0xA4)

    def dead(y, fh):
        """Waxy dead skin, mottled with bruises and rot."""
        c = shade(grad(skin, y, fh, 1.02, 0.82), 0.86 + 0.3 * n(scale=5))
        m = n2(scale=6)
        if m > 0.7:
            c = mix(c, bruise, (m - 0.7) * 2.2)
        elif m < 0.22:
            c = mix(c, rot, (0.22 - m) * 2.0)
        return jitter(rng, c, 2)

    def stained(c, amount=1.0):
        """Cloth, grimy, soaked with old blood in patches."""
        c = shade(c, 0.84 + 0.26 * n(scale=3))
        b = n2(scale=7) * amount
        if b > 0.62:
            c = mix(c, dried if b > 0.8 else blood, min(0.85, (b - 0.62) * 3))
        return jitter(rng, c, 2)

    def burned():
        k = n(scale=2.5, octaves=3)
        if k > 0.72:
            return jitter(rng, raw, 6)
        if k < 0.3:
            return jitter(rng, crust, 4)
        c = jitter(rng, shade(burn, 0.8 + 0.4 * n2(scale=3)), 4)
        return c

    def wet(c, amount=8):
        return jitter(rng, shade(c, 0.8 + 0.4 * n(scale=2)), amount)

    # Right half: dead skin, one white eye in a bruised socket, stitches
    # across the cheek. Left half: burned to the skull, an empty socket (K)
    # with something glinting deep inside (g). The mouth is torn to the ears.
    face = [
        "HHHHHHHHHrrrrrrr",
        "HHhHHHHHHrcrrBrr",
        "HHHHHhHHrrrBBBBr",
        "HSSSHHHSrrBBBBBc",
        "SSSSSSSSrBBBBBBr",
        "SbbbbSSSrBBKKBBr",
        "bEWWEbSSrBKKKKBr",
        "bEWWEbSSrBKgKKBr",
        "SbbbbSSSrBBKKBBr",
        "SSSSSSnSrrBBBBrr",
        "SsSsSnNnrBBBBBcr",
        "SSSSSSSSrrBrrrrr",
        "GTKTKTKTKTKTKTKG",
        "GKTKTKTKTKTKTKTG",
        "SGKKKKKKKKKKKKGr",
        "SSGGKKKKKKKKGGrr",
    ]
    fpal = {"H": hair, "h": hair_l, "b": shade(bruise, 0.8), "E": (0x2A, 0x16, 0x18), "W": GLOW_WHITE,
            "n": shade(skin, 0.7), "N": (0x22, 0x0C, 0x0C), "s": thread, "T": tooth, "K": (0x0E, 0x03, 0x03),
            "G": raw, "c": crust, "B": bone_c, "g": (0xFF, 0xE0, 0xE0, 40)}

    def left_side(face_, x, fw):
        return face_ == "left" or (face_ in ("front", "top", "bottom") and x >= fw // 2) or (face_ == "back" and x < fw // 2)

    def fn(tag, face_, x, y, fw, fh):
        left = left_side(face_, x, fw)
        if tag == "head":
            if face_ == "front":
                return burned() if face[y][x] == "r" else rows(face, fpal, x, y, lambda: dead(y, fh))
            if left:
                return bone_c if (face_ == "left" and 5 <= x <= 10 and 5 <= y <= 10 and n(scale=2) > 0.35) else burned()
            if face_ == "top" or y < 5 or (face_ == "back" and y < 12):
                return jitter(rng, hair_l if n(scale=1.5) > 0.62 else hair, 3)
            if face_ in ("right", "left") and y >= fh - 4 and x >= fw - 5:
                return raw if y == fh - 4 else (0x0E, 0x03, 0x03)  # the tear runs back to the ear
            return dead(y, fh)
        if tag == "head+":
            if left or face_ == "bottom":
                return CLEAR
            if face_ == "top":
                return jitter(rng, hair_l, 4) if n(scale=2) > 0.5 else CLEAR
            if face_ == "front":
                return jitter(rng, hair, 3) if y < 2 or (y == 2 and x % 3 == 0) else CLEAR
            return jitter(rng, hair, 3) if y < 6 and n(scale=2) > 0.4 else CLEAR
        if tag == "skull":
            if face_ == "front" and 2 <= x <= 3 and 2 <= y <= 4:
                return (0x0E, 0x03, 0x03)
            return jitter(rng, shade(bone_c, 0.85 + 0.2 * n(scale=2)), 3) if face_ != "back" else raw
        if tag in ("fang", "fang_low"):
            return tooth if y < fh - 1 or tag == "fang_low" else (0xA8, 0x94, 0x6C)
        if tag == "nail":
            return (0x74, 0x74, 0x7A) if face_ == "top" else (0x4A, 0x3A, 0x34)
        if tag == "jaw":
            if face_ == "front":
                if y < 2:
                    return (0x0E, 0x03, 0x03)
                return burned() if x >= fw // 2 else dead(y, fh)
            if face_ == "top":
                return wet((0x52, 0x10, 0x14))
            if face_ == "bottom":
                return burned() if left else dead(y, fh)
            return burned() if left else dead(y, fh)
        if tag == "tongue":
            return wet((0x9A, 0x3A, 0x44), 6)
        if tag in ("drip", "drip_s"):
            return jitter(rng, blood if y < fh - 3 else dried, 4)
        if tag in ("blister", "blister_s"):
            if face_ == "bottom":
                return raw
            return pus if (x + y) % 3 == 0 and tag == "blister" else blister_c
        if tag == "rib":
            return jitter(rng, shade(bone_c, 0.8 + 0.2 * x / max(1, fw - 1)), 3) if face_ != "back" else blood
        if tag == "chest_hole":
            return wet(cavity, 4) if face_ == "front" else CLEAR
        if tag == "heart":
            return wet((0x7A, 0x14, 0x1A) if n(scale=1.5) > 0.4 else (0x5A, 0x0C, 0x12), 6)
        if tag == "lodged_pick":
            c = jitter(rng, grad((0x6C, 0x6C, 0x70), y, fh, 1.1, 0.8), 5)
            return mix(c, blood, 0.6) if n(scale=2) > 0.6 else c
        if tag == "lodged_handle":
            return (0x8A, 0x70, 0x50) if face_ == "back" else jitter(rng, (0x4E, 0x38, 0x24), 4)
        if tag == "cavity":
            if face_ == "front":  # the inside of the open stomach
                k = n(scale=2.5)
                return wet(gut if k > 0.55 else gut_d if k > 0.35 else cavity)
            return stained(shade(shirt, 0.85)) if face_ == "back" else wet(cavity, 4)
        if tag == "flank":
            if face_ in ("left", "right"):
                return wet((0x8A, 0x1E, 0x1C)) if (face_ == "left") == (x < fw // 2) else stained(shade(shirt, 0.85))
            if face_ == "front":
                return thread if y % 3 == 0 else dead(y, fh)  # stitches across the wound's edge
            return stained(shade(shirt, 0.85))
        if tag == "wound_lip":
            return thread if x % 3 == 0 else wet(blood, 5)
        if tag in ("gut", "gut_big", "gut_s", "gut_hang", "gut_knot"):
            if tag == "gut_hang" and y >= fh - 2:
                return blood
            c = gut if n(scale=1.5) > 0.45 else gut_d
            return wet(shade(c, 1.1) if face_ == "top" else c)
        if tag in ("vertebra", "vertebra_top"):
            if face_ == "bottom":
                return wet((0x6A, 0x16, 0x16), 4)
            c = shade(bone_c, 0.82 + 0.25 * n(scale=2))
            return mix(c, blood, 0.5) if face_ in ("right", "left") and y == fh - 1 else jitter(rng, c, 3)
        if tag == "spur":
            return jitter(rng, shade(bone_c, 0.9), 3) if y < fh - 1 else blood
        if tag == "neck":
            return burned() if left else (shade(dead(y, fh), 0.85) if face_ == "front" and y % 3 == 0 else dead(y, fh))
        if tag == "tendon":
            return jitter(rng, (0xC0, 0x5A, 0x56), 5)
        if tag == "pelvis":
            return stained(grad(pants, y, fh, 1.0, 0.9), 0.8)
        if tag == "belt":
            if face_ in ("top", "bottom"):
                return CLEAR
            return (0x92, 0x88, 0x6C) if face_ == "front" and x in (6, 7) else jitter(rng, (0x3A, 0x2A, 0x1C), 3)
        if tag == "chest":
            if face_ == "back" and 7 <= x <= 12:
                return wet((0x72, 0x16, 0x16))  # where the spine tore out
            if face_ == "front":
                return dead(y, fh) if abs(x - 9.5) > 7 or y < 2 else wet((0x8A, 0x1C, 0x1E))
            if left and n(scale=3) > 0.6:
                return burned()
            return stained(grad(shirt, y, fh, 1.04, 0.78))
        if tag == "chest+":  # the shirt, hanging in rags
            if face_ in ("top", "bottom") or (face_ == "back" and 5 <= x <= 14):
                return CLEAR
            if face_ == "front" and 3 <= x <= 16:
                return CLEAR
            return stained(shade(shirt, 0.74)) if y >= 10 and n(scale=2) > 0.45 else CLEAR
        if tag == "upper_r":
            return stained(grad(shirt, y, fh, 1.04, 0.84)) if face_ != "bottom" else dead(0, 1)
        if tag == "sleeve":
            return stained(shade(shirt, 0.72)) if y >= fh - 4 and face_ not in ("top", "bottom") and n(scale=1.5) > 0.4 else CLEAR
        if tag == "fore_r":  # flayed: bare muscle in strips, tendons, bone at the wrist
            if y >= fh - 2:
                return jitter(rng, bone_c, 3)
            if n2(scale=1.2) > 0.78:
                return jitter(rng, (0xE0, 0xC0, 0xB0), 5)  # tendons
            return wet(muscle if (x // 2) % 2 else shade(muscle, 0.72))
        if tag == "bone_out":
            return jitter(rng, bone_c, 4) if y < fh - 1 else blood
        if tag == "fist":
            return mix(dead(y, fh), blood, 0.35) if n(scale=2) > 0.5 else dead(y, fh)
        if tag in ("upper_l", "fore_l", "palm"):
            if tag == "upper_l" and (face_ == "top" or y < 4):
                return stained(shade(shirt, 0.8))
            return burned() if face_ != "bottom" else crust
        if tag == "claw":
            return jitter(rng, grad((0xC4, 0xB2, 0x8E), y, fh, 1.0, 0.55), 3)
        if tag == "knee":
            return mix(stained(pants), bone_c, 0.7) if face_ == "front" and 3 <= x <= 7 and 1 <= y <= 3 else stained(pants)
        if tag in ("thigh", "shin"):
            if face_ == "bottom":
                return shade(pants, 0.7)
            c = stained(grad(pants, y, fh, 1.04, 0.84))
            if tag == "shin" and n2(scale=3) > 0.7:
                return dead(y, fh)  # torn through to the leg
            return c
        if tag == "thigh+":
            return shade(pants, 0.78) if y < 3 and face_ not in ("top", "bottom") else CLEAR
        if tag == "boot":
            if face_ == "bottom" or y >= fh - 1:
                return shade(boot, 0.55)
            return stained(boot if face_ != "front" or x % 4 else shade(boot, 1.25), 0.6)
        if tag == "handle":
            if y < 4:
                return jitter(rng, (0x3A, 0x2E, 0x24), 3)
            return jitter(rng, shade((0x5C, 0x42, 0x2A), 0.85 + 0.25 * n(scale=1.5)), 3)
        if tag in ("pick", "pick_tip"):
            c = jitter(rng, grad((0x72, 0x72, 0x76), y, fh, 1.14, 0.76), 4)
            k = n(scale=2)
            return mix(c, blood, 0.7) if k > 0.62 else shade(c, 0.6) if k < 0.2 else c
        return None

    paint_model(cv, model, fn)
    return bake(cv, model)


# =========================================================================== #
# The Man From The Fog
# =========================================================================== #

def fog_man_model():
    """A figure you mistake for a dead tree in the fog. Stilt legs with knotted
    knees, a pelvis wrapped in rags, a waist you could close a hand round and
    a narrow chest under a shawl of rotten cloth; one bony shoulder higher
    than the other. A long neck carries a small, long head tipped hard to one
    side: two tall black eyes, no nose to speak of, and a jaw that hangs open
    far below it. His arms reach his shins and end in four long black
    fingers. A tattered shroud hangs down his back."""
    vertebra = lambda y, z=1.25: C((-0.5, y, z), (1, 1, 1), "vertebra")
    bones = [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 24, 0), cubes=[
            C((-2.5, 24, -1.5), (5, 3, 3), "pelvis"),
            C((-2, 19, -2), (3, 6, 0.5), "loincloth"), C((-1, 20, 1.5), (3, 5, 0.5), "loincloth_b")]),
        B("spine", "body", (0, 27, 0), cubes=[
            C((-1.75, 27, -1.25), (3.5, 5, 2.5), "waist"), vertebra(27.5), vertebra(29.5), vertebra(31)]),
        B("chest", "spine", (0, 32, 0), rotation=(18, 0, -6), cubes=[
            C((-4.5, 32, -2), (9, 7, 4), "chest"),
            C((-5, 38, -3), (10, 2, 5.5), "shawl"),
            C((-6.5, 36.5, -1.5), (3, 3, 3), "shoulder"), C((3.5, 35.5, -1.5), (3, 3, 3), "shoulder"),
            C((-4, 37.5, -2.5), (3.5, 0.5, 0.5), "collarbone"), C((0.5, 37.5, -2.5), (3.5, 0.5, 0.5), "collarbone"),
            vertebra(33, 2), vertebra(35, 2), vertebra(37, 2)]),
        B("coatBack", "chest", (0, 39, 2.5), cubes=[C((-5.5, 16, 2), (11, 23, 0.5), "shroud")]),
        B("coatRight", "chest", (-3, 39, -2.5), cubes=[C((-5, 26, -3), (3, 13, 0.5), "rag")]),
        B("coatLeft", "chest", (3, 39, -2.5), cubes=[C((2, 28, -3), (3, 11, 0.5), "rag")]),
        B("neck", "chest", (0, 39, 0), rotation=(14, 0, 0), cubes=[
            C((-0.75, 39, -0.75), (1.5, 7, 1.5), "neck"), C((-1, 40, -1), (0.5, 5, 0.5), "tendon"),
            C((0.5, 40, -1), (0.5, 5, 0.5), "tendon")]),
        B("head", "neck", (0, 46, 0), rotation=(-8, 0, 22), cubes=[C((-2.5, 46, -2.5), (5, 7, 5), "head")]),
        # Unhinged: the mouth hangs open to the middle of his chest.
        B("jaw", "head", (0, 47, 1.5), rotation=(20, 0, 0), cubes=[
            C((-2, 42, -2.5), (4, 5, 4), "jaw"), C((-1.5, 41, -2), (3, 1, 3), "chin")]),
        B("hair", "head", (0, 53, 2), rotation=(14, 0, 0), cubes=[
            C((-2, 46, 2), (0.5, 7, 0.5), "strand"), C((-0.5, 44, 2), (0.5, 9, 0.5), "strand_l"),
            C((1, 47, 2), (0.5, 6, 0.5), "strand_s"), C((2, 48, 1), (0.5, 5, 0.5), "strand_side")]),
    ]
    for side, name, shoulder in ((-1, "right", 38), (1, "left", 37)):
        sx = lambda a, b: side_x(side, a, b)
        fingers = [C((sx(3.25 + 0.75 * k, 3.75 + 0.75 * k), shoulder - 34.5, -0.25), (0.5, 6, 0.5), "finger",
                      pivot=(side * (3.5 + 0.75 * k), shoulder - 28.5, 0), rotation=(-12 + 5 * k, 0, side * (k - 1.5) * 4))
                   for k in range(4)]
        bones += [
            B(f"{name}Arm", "chest", (5 * side, shoulder, 0), rotation=(-16, 0, -3 * side), cubes=[
                C((sx(4, 6), shoulder - 13, -1), (2, 13, 2), "upper_arm")]),
            B(f"{name}Forearm", f"{name}Arm", (5 * side, shoulder - 13, 0), rotation=(-6, 0, 0), cubes=[
                C((sx(4.25, 5.75), shoulder - 25.5, -0.75), (1.5, 12.5, 1.5), "forearm"),
                C((sx(4, 6), shoulder - 14, -1), (2, 2, 2), "elbow")]),
            B(f"{name}Hand", f"{name}Forearm", (5 * side, shoulder - 25.5, 0), cubes=[
                C((sx(3, 6), shoulder - 28.5, -0.5), (3, 3, 1), "palm"),
                C((sx(2.5, 3) if side > 0 else sx(6, 6.5), shoulder - 30.5, -0.75), (0.5, 3, 0.5), "thumb"),
                *fingers]),
            B(f"{name}Leg", "root", (2 * side, 24, 0), cubes=[
                C((sx(0.75, 3.25), 12, -1.25), (2.5, 12, 2.5), "thigh")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 12, 0), cubes=[
                C((sx(1, 3), 1, -1), (2, 11, 2), "shin"),
                C((sx(0.5, 3.5), 10, -1.75), (3, 3, 3), "knee"),
                C((sx(0.75, 3.25), 0, -4.5), (2.5, 1, 6), "foot"),
                C((sx(1.5, 2.5), 0, -5.5), (1, 0.5, 1), "toenail")]),
        ]
    return hd_model("geometry.hl.fog_man", bones, bounds=(3.0, 3.8, (0, 1.8, 0)))


def paint_fog_man(seed, model):
    rng = random.Random(seed)
    n, n2 = Noise(seed), Noise(seed + 1)
    cv = Canvas(*model.texture)
    S = (0xB4, 0xB8, 0xB8)
    S_d, S_dd = shade(S, 0.72), shade(S, 0.52)
    rag, rag_d, rag_l = (0x46, 0x40, 0x38), (0x2A, 0x26, 0x22), (0x5E, 0x56, 0x4A)
    teeth, mouth, bone_c = (0xD4, 0xCC, 0xB4), (0x1E, 0x06, 0x08), (0xD2, 0xCE, 0xC0)
    vein, black = (0x6E, 0x7E, 0x94), (0x0C, 0x0C, 0x0E)

    def ash(y, fh, top=1.04, bottom=0.82):
        """Grey, damp skin: blotchy, with blue veins running through it."""
        c = shade(grad(S, y, fh, top, bottom), 0.88 + 0.22 * n(scale=4))
        k = n2(scale=3, octaves=1)
        if abs(k - 0.5) < 0.035:
            c = mix(c, vein, 0.6)
        return jitter(rng, c, 2)

    def dying(y, fh):
        """Extremities going black from the tips."""
        t = y / max(1, fh - 1)
        return mix(ash(y, fh), black, max(0.0, min(1.0, (t - 0.35) * 1.6)))

    def cloth(x, y, base=rag):
        c = shade(base, 0.8 + 0.35 * n(scale=3))
        if (x + y) % 2 == 0:
            c = shade(c, 1.08)  # burlap weave
        if n2(scale=5) > 0.7:
            c = mix(c, (0x3A, 0x30, 0x1C), 0.5)  # damp stains
        return jitter(rng, c, 2)

    def tattered(x, y, fh, depth):
        """Rags ending in long torn strips with holes through them."""
        if y >= fh - depth and n(x * 3.0, 0, scale=2.5, octaves=1) < 0.3 + (y - (fh - depth)) / depth * 0.7:
            return True
        return y < fh - depth and n(scale=2) > 0.8

    # Front of the head, 10x14: two tall black eyes and the stubs of teeth
    # in an upper jaw with no lips.
    head = [
        "SSSSSSSSSS",
        "SSSSSSSSSS",
        "SsSSSSSSsS",
        "sKKKSSKKKs",
        "KKKKSSKKKK",
        "KKKKsSKKKK",
        "KKKKSSKKKK",
        "KKKKSsKKKK",
        "sKKdSSdKKs",
        "SSdSnnSdSS",
        "SSdSSSSdSS",
        "SsSSSSSSsS",
        "KKKKKKKKKK",
        "TKTKTTKTKT",
    ]
    hpal = {"s": S_d, "K": black, "d": (0x16, 0x16, 0x18), "n": S_dd, "T": teeth}

    def fn(tag, face_, x, y, fw, fh):
        if tag == "head":
            if face_ == "front":
                return rows(head, hpal, x, y, lambda: ash(y, fh))
            if face_ == "bottom":
                return mouth
            near_front = (face_ == "right" and x >= fw - 3) or (face_ == "left" and x <= 2)
            if near_front and y >= fh - 2:
                return black
            return ash(y, fh, 1.08, 0.9)
        if tag == "jaw":
            if face_ == "front":
                if y == 0:
                    return teeth if x % 2 else black
                return ash(y, fh, 0.95, 0.78)
            if face_ == "top":
                return mouth if n(scale=1.5) > 0.3 else (0x40, 0x10, 0x14)
            if face_ in ("right", "left") and y == 0:
                return teeth if x % 2 else black
            return ash(y, fh, 0.92, 0.76)
        if tag == "chin":
            return shade(ash(y, fh), 0.8)
        if tag.startswith("strand"):
            return jitter(rng, (0x16, 0x15, 0x18) if (y // 3) % 3 else (0x2A, 0x28, 0x2C), 2)
        if tag == "neck":
            return shade(ash(y, fh), 0.9) if face_ != "back" or y % 3 else S_dd
        if tag == "tendon":
            return ash(y, fh, 1.1, 0.95)
        if tag == "chest":
            c = ash(y, fh)
            if face_ in ("front", "right", "left") and y % 3 == 2 and y > 1:
                return shade(c, 0.66)  # ribs
            if face_ == "front" and x in (8, 9) and y < 10:
                return shade(c, 1.08)  # sternum
            if face_ == "back" and x in (8, 9):
                return S_dd
            return c
        if tag == "waist":
            c = ash(y, fh)
            return shade(c, 0.72) if face_ in ("right", "left") else c  # sucked in
        if tag == "collarbone":
            return ash(y, fh, 1.15, 1.05)
        if tag in ("shoulder", "elbow", "knee"):
            return ash(y, fh, 1.1, 0.86)
        if tag == "vertebra":
            return jitter(rng, bone_c, 3) if face_ != "bottom" else S_dd
        if tag == "shawl":
            if face_ == "bottom" and 5 <= x <= 14 and 2 <= y <= 8:
                return CLEAR  # the hole for the neck
            if face_ in ("front", "back", "right", "left") and y == fh - 1 and n(scale=1.5) > 0.55:
                return CLEAR
            return cloth(x, y, rag_d)
        if tag == "shroud":
            if face_ in ("top", "bottom"):
                return cloth(x, y, rag_d) if face_ == "top" else CLEAR
            if tattered(x, y, fh, 22):
                return CLEAR
            return cloth(x, y, rag if face_ == "back" else rag_d)
        if tag == "rag":
            if face_ in ("top", "bottom"):
                return CLEAR
            return CLEAR if tattered(x, y, fh, 12) else cloth(x, y, rag_l)
        if tag in ("loincloth", "loincloth_b"):
            if face_ in ("top", "bottom"):
                return CLEAR
            return CLEAR if tattered(x, y, fh, 6) else cloth(x, y, rag)
        if tag == "pelvis":
            return cloth(x, y, rag_d) if face_ != "bottom" else S_dd
        if tag in ("thigh", "upper_arm"):
            return ash(y, fh)
        if tag in ("shin", "forearm"):
            c = ash(y, fh, 1.0, 0.8)
            return shade(c, 1.08) if face_ == "front" and x == 1 else c  # the bone under the skin
        if tag == "foot":
            if face_ == "front" or (face_ == "top" and y < 3):
                return black if x % 2 == 0 else dying(3, 4)  # long toes
            return dying(y, fh + 2)
        if tag == "toenail":
            return black
        if tag in ("palm", "thumb"):
            return dying(y, fh + 4)
        if tag == "finger":
            return dying(y, fh) if face_ != "bottom" else black
        return None

    paint_model(cv, model, fn)
    return bake(cv, model)


# =========================================================================== #
# The Cave Dweller
# =========================================================================== #

DWELLER_LIMBS = {
    # Rest rotations of the limbs (x, y, z), mirrored in y and z for the right
    # side; solved so the elbows and knees stand above the back and the hands
    # and feet land flat on the ground.
    "arm": (30, 0, 45), "forearm": (-20, 0, -55), "hand": (0, 0, 0),
    "thigh": (-25, 0, 40), "shin": (30, 0, -55), "foot": (0, 0, 0),
}


def cave_dweller_model(limbs=None):
    """Something that was a person once and learned to move like a spider.
    The body slung low between long jointed limbs whose elbows and knees rise
    above its back; the neck thrusts forward to a long, flat, eyeless-looking
    skull with a crown of bony spikes, four sunken eyes that glow, and a jaw
    lined with needles that drops open far too wide. Its hands and feet end
    in long hooked claws."""
    limbs = dict(DWELLER_LIMBS, **(limbs or {}))
    ridge = lambda y, z, h=2: C((-0.5, y, z), (1, h, 1), "ridge")
    needle = lambda x, y, z: C((x, y, z), (0.5, 1.5, 0.5), "needle")
    bones = [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 12, 6), cubes=[
            C((-3, 10, 4), (6, 4, 5), "pelvis"), ridge(14, 5.5), ridge(14, 7.5, 1.5)]),
        B("abdomen", "body", (0, 12, 4), cubes=[
            C((-2.5, 10.5, -2), (5, 3.5, 6), "abdomen"), ridge(14, -0.5, 1.5), ridge(14, 2, 1.5)]),
        B("ribcage", "abdomen", (0, 12, -2), rotation=(-8, 0, 0), cubes=[
            C((-4, 9, -10), (8, 6, 8), "ribcage"),
            C((-4.5, 10, -9), (0.5, 4, 6), "ribs_side"), C((4, 10, -9), (0.5, 4, 6), "ribs_side"),
            C((-3.5, 14.5, -8), (2.5, 1, 4), "blade", pivot=(-2, 15, -6), rotation=(0, 0, 14)),
            C((1, 14.5, -8), (2.5, 1, 4), "blade", pivot=(2, 15, -6), rotation=(0, 0, -14)),
            ridge(14.5, -3.5, 2.5), ridge(14.5, -6, 3), ridge(14.5, -9, 2)]),
        B("neck", "ribcage", (0, 13, -10), rotation=(-6, 0, 0), cubes=[
            C((-1.5, 11.5, -14.5), (3, 3, 4.5), "neck"), ridge(14, -13, 1.5)]),
        B("head", "neck", (0, 13, -14.5), rotation=(12, 0, 0), cubes=[
            C((-4, 11.5, -22.5), (8, 5, 8), "skull"),
            C((-4, 16, -22.5), (8, 1, 1), "brow"),
            *[C((x, 16.5, z), (1, h, 1), "spike", pivot=(x + 0.5, 16.5, z + 0.5), rotation=(-25, 0, 0))
              for x, z, h in ((-3, -19, 2.5), (-1.5, -17, 3.5), (0.5, -17, 3.5), (2, -19, 2.5), (-0.5, -20.5, 2))],
            *[needle(x, 10, -22.25) for x in (-3.5, -2.25, -1, 0.5, 1.75, 3)]]),
        B("jaw", "head", (0, 12, -15.5), rotation=(14, 0, 0), cubes=[
            C((-4, 9.5, -22.5), (8, 2.5, 7), "jaw"),
            *[needle(x, 12, -22.25) for x in (-3, -1.75, -0.5, 0.75, 2.25)],
            C((-1, 9.5, -24.5), (2, 1, 3), "tongue")]),
    ]
    for side, name in ((-1, "Right"), (1, "Left")):
        sx = lambda a, b: side_x(side, a, b)
        rot = lambda k: (limbs[k][0], limbs[k][1] * side, limbs[k][2] * side)
        claw = lambda x0, y, z, n: [C((sx(x0 + a, x0 + a + 0.5), y - 4, z), (0.5, 4, 0.5), n,
                                      pivot=(side * (x0 + a + 0.25), y, z + 0.25), rotation=(-30, 0, side * (a - 0.75) * 12))
                                    for a in (0, 0.75, 1.5)]
        bones += [
            # Arms: up and out from the shoulders, elbows above the back, then
            # a long forearm down to the ground.
            B(f"arm{name}", "ribcage", (4 * side, 13.5, -7), rotation=rot("arm"), cubes=[
                C((sx(3, 5), 13.5, -8), (2, 11, 2), "upper_arm"),
                C((sx(2.75, 5.25), 12.5, -8.25), (2.5, 3, 2.5), "shoulder")]),
            B(f"forearm{name}", f"arm{name}", (4 * side, 24.5, -7), rotation=rot("forearm"), cubes=[
                C((sx(3.25, 4.75), 8.5, -7.75), (1.5, 16, 1.5), "forearm"),
                C((sx(3, 5), 23, -8), (2, 2.5, 2), "elbow")]),
            B(f"hand{name}", f"forearm{name}", (4 * side, 8.5, -7), rotation=rot("hand"), cubes=[
                C((sx(3, 5), 7, -8), (2, 1.5, 2), "palm"), *claw(3.25, 7, -7.5, "claw")]),
            # Legs: knees high behind, shins down, clawed feet.
            B(f"thigh{name}", "body", (3 * side, 12, 7), rotation=rot("thigh"), cubes=[
                C((sx(2, 4), 12, 6), (2, 10, 2), "thigh")]),
            B(f"shin{name}", f"thigh{name}", (3 * side, 22, 7), rotation=rot("shin"), cubes=[
                C((sx(2.25, 3.75), 8, 6.25), (1.5, 14, 1.5), "shin"),
                C((sx(2, 4), 20.5, 6), (2, 2.5, 2), "knee")]),
            B(f"foot{name}", f"shin{name}", (3 * side, 8, 7), rotation=rot("foot"), cubes=[
                C((sx(2, 4), 6.5, 5.5), (2, 1.5, 2.5), "sole"), *claw(2.25, 6.5, 5.5, "toe_claw")]),
        ]
    return hd_model("geometry.hl.cave_dweller", bones, bounds=(3.5, 2.2, (0, 0.9, 0)))


def paint_cave_dweller(seed, model):
    rng = random.Random(seed)
    n, n2 = Noise(seed), Noise(seed + 1)
    cv = Canvas(*model.texture)
    D = (0xA2, 0xA4, 0x94)
    D_d, D_dd = shade(D, 0.66), shade(D, 0.46)
    belly, vein = (0xC8, 0xC0, 0xB4), (0x7A, 0x5E, 0x6E)
    teeth, mouth, gum = (0xE4, 0xDC, 0xC6), (0x3A, 0x0A, 0x10), (0x7A, 0x22, 0x2A)
    glow = (0xFF, 0xE6, 0x8C, 60)
    bone_c, claw_c = (0xD8, 0xD0, 0xBA), (0x24, 0x20, 0x1C)

    def hide(y, fh, top=1.06, bottom=0.78):
        """Wet, pale cave skin, darker along the back, with a sheen."""
        c = shade(grad(D, y, fh, top, bottom), 0.84 + 0.3 * n(scale=4))
        k = n2(scale=3)
        if k > 0.76:
            c = mix(c, (0xDE, 0xE2, 0xD6), (k - 0.76) * 3)  # slime sheen
        elif k < 0.2:
            c = mix(c, (0x6A, 0x70, 0x60), (0.2 - k) * 3)
        return jitter(rng, c, 2)

    def underside(y, fh):
        c = shade(belly, 0.86 + 0.2 * n(scale=3))
        return mix(c, vein, 0.5) if abs(n2(scale=2.5, octaves=1) - 0.5) < 0.04 else jitter(rng, c, 2)

    # Front of the skull, 16x10: two big sunken eyes and two small ones below
    # them, all glowing far back, over the upper row of the mouth.
    skull = [
        "ssssssssssssssss",
        "SsKKKsSSSSsKKKsS",
        "sKKgKKsSSsKKgKKs",
        "sKggKKsSSsKKggKs",
        "SsKKKsKsSKsKKKsS",
        "SSsSSKgSSgKSSsSS",
        "SSSSSsKSSKsSSSSS",
        "SSSSSSSSSSSSSSSS",
        "KKKKKKKKKKKKKKKK",
        "GKGKGKGKGKGKGKGK",
    ]
    spal = {"s": D_d, "K": (0x08, 0x06, 0x06), "g": glow, "G": gum}

    def fn(tag, face_, x, y, fw, fh):
        if tag == "skull":
            if face_ == "front":
                return rows(skull, spal, x, y, lambda: hide(y, fh))
            if face_ == "bottom":
                return mouth
            if face_ in ("right", "left") and y >= fh - 2:
                return (0x08, 0x06, 0x06)  # the mouth runs back along the sides
            return hide(y, fh)
        if tag == "brow":
            return hide(y, fh, 1.12, 1.0) if face_ in ("top", "front") else D_dd
        if tag == "spike":
            return jitter(rng, grad(bone_c, y, fh, 1.0, 0.7), 3)
        if tag == "needle":
            return teeth if face_ != "top" else gum
        if tag == "jaw":
            if face_ == "front":
                return (0x08, 0x06, 0x06) if y == 0 else hide(y, fh, 0.95, 0.8)
            if face_ == "top":
                return gum if 5 <= x <= 10 and y > 3 else jitter(rng, mouth, 4)
            if face_ == "bottom":
                return underside(y, fh)
            return hide(y, fh, 0.9, 0.78)
        if tag == "tongue":
            return jitter(rng, shade((0x9E, 0x44, 0x50), 0.8 + 0.3 * n(scale=1.5)), 4)
        if tag == "neck":
            if face_ == "bottom":
                return underside(y, fh)
            return D_d if face_ in ("right", "left") and x % 3 == 0 else hide(y, fh)
        if tag == "ribcage":
            if face_ == "bottom":
                return underside(y, fh)
            if face_ in ("right", "left") and x % 3 == 1:
                return D_d  # ribs pressing through the skin
            if face_ == "top" and x in (7, 8):
                return D_dd
            return hide(y, fh)
        if tag == "ribs_side":
            return hide(y, fh, 1.12, 1.0) if y % 3 == 0 else D_dd
        if tag == "blade":
            return hide(y, fh, 1.14, 1.0) if face_ == "top" else D_d
        if tag == "abdomen":
            if face_ == "bottom":
                return underside(y, fh)
            return shade(hide(y, fh), 0.8) if face_ in ("right", "left") else hide(y, fh)
        if tag == "pelvis":
            return underside(y, fh) if face_ == "bottom" else hide(y, fh)
        if tag == "ridge":
            return (0x5A, 0x52, 0x46) if face_ == "top" else jitter(rng, bone_c, 4)
        if tag in ("upper_arm", "thigh"):
            return hide(y, fh, 1.0, 0.85)
        if tag in ("shoulder", "elbow", "knee"):
            return hide(y, fh, 1.14, 0.92)
        if tag in ("forearm", "shin"):
            c = hide(y, fh, 1.0, 0.62)
            return shade(c, 1.1) if face_ == "front" and x == 1 else c
        if tag in ("palm", "sole"):
            return hide(0, 2, 0.7, 0.6)
        if tag in ("claw", "toe_claw"):
            return jitter(rng, mix(D_dd, claw_c, y / max(1, fh - 1)), 3)
        return None

    paint_model(cv, model, fn)
    return bake(cv, model)


# =========================================================================== #
# null: a player-shaped render error
# =========================================================================== #

def null_model():
    """A player the game failed to put back together. Every part floats a
    little apart from the next: legs, three torso slices that don't line up,
    arms broken into segments, and a head hanging above the neck, turned the
    wrong way, with a blue line staring out of its eyes. One arm is three
    segments long and drags three fingers on the ground. Round it all, the
    debug hitbox you'd see with F3+B: a white wireframe box with the red line
    at eye height. A halo of stray pixels and missing texture circles the
    head."""
    px = lambda x, y, z: C((x, y, z), (1, 1, 1), "px")
    edge = lambda o, s, tag="hitbox": C(o, s, tag)
    box = [edge((x, 0, z), (0.5, 29, 0.5)) for x in (-5, 4.5) for z in (-5, 4.5)]
    for y, tag in ((0, "hitbox"), (28.5, "hitbox"), (26, "eyeline")):
        box += [edge((-5, y, z), (10, 0.5, 0.5), tag) for z in (-5, 4.5)]
        box += [edge((x, y, -4.5), (0.5, 0.5, 9), tag) for x in (-5, 4.5)]
    return hd_model("geometry.hl.null", [
        B("root", None, (0, 0, 0)),
        B("hitbox", "root", (0, 0, 0), cubes=box),
        B("rightLeg", "root", (-2, 12, 0), cubes=[C((-4, 0, -2), (4, 12, 4), "leg")]),
        B("leftLeg", "root", (2, 12, 0), cubes=[
            C((0, 0, -2), (4, 6, 4), "leg_low"), C((0.5, 7, -2), (4, 5, 4), "leg_high")]),
        B("torsoLow", "root", (0, 13, 0), cubes=[C((-4, 13, -2), (8, 4, 4), "slice_low")]),
        B("torsoMid", "torsoLow", (0, 17.5, 0), cubes=[C((-3, 17.5, -2), (8, 4, 4), "slice_mid")]),
        B("torsoHigh", "torsoMid", (0, 22, 0), cubes=[C((-5, 22, -2.5), (8, 4, 4.5), "slice_high")]),
        B("head", "torsoHigh", (-1, 28, 0), rotation=(0, 20, -10), cubes=[
            C((-5, 28, -4), (8, 8, 8), "head"), C((-5, 28, -4), (8, 8, 8), "head+", 0.5),
            C((-3, 28.5, -4.5), (8, 1.5, 8), "head_strip"),
            C((-1.5, 31.5, -16), (0.5, 0.5, 12), "look")]),
        B("rightArm", "torsoHigh", (-6.5, 25.5, 0), cubes=[
            C((-8.5, 19, -1.5), (3, 6, 3), "arm"), C((-9, 11.5, -1.5), (3, 6.5, 3), "arm_low")]),
        B("leftArm", "torsoHigh", (4.5, 25.5, 0), cubes=[
            C((3.5, 19, -1.5), (3, 6.5, 3), "arm"), C((4, 11, -1.5), (3, 7, 3), "arm_low"),
            C((3.5, 3.5, -1.5), (3, 6.5, 3), "arm_end"),
            *[C((3.5 + k, 0.5, -1), (1, 3.5, 1), "null_finger", pivot=(4 + k, 3.5, -0.5), rotation=(-8 * k, 0, (k - 1) * 8))
              for k in (0, 1, 2)]]),
        B("halo", "head", (-1, 32, 0), cubes=[
            px(-8, 34, -1), px(4, 37, 2), px(-3, 39, -3), px(5, 30, -2), px(-9, 29, 2), px(1, 38, 3),
            px(-6, 38, 3), px(6, 34, 1),
            C((2, 39, -1), (2, 2, 2), "missing"), C((-10, 32, -2), (2, 2, 2), "missing"),
            C((5.5, 32, 2.5), (1.5, 1.5, 1.5), "missing_s")]),
    ], bounds=(2.5, 2.8, (0, 1.4, 0)))


def paint_null(seed, model):
    rng = random.Random(seed)
    n = Noise(seed)
    cv = Canvas(*model.texture)
    magenta, cyan, white = (0xF8, 0x00, 0xF8), (0x00, 0xE0, 0xE0), (0xFF, 0xFF, 0xFF)
    # What is left of a default skin, drained of colour, bleeds through the
    # dark in places.
    ghost = {"head": (0x5A, 0x44, 0x36), "slice": (0x10, 0x4A, 0x4C), "arm": (0x5A, 0x44, 0x36),
             "leg": (0x22, 0x20, 0x56), "null": (0x5A, 0x44, 0x36)}

    def void(tag, face_, x, y, fw, fh):
        g = 6 + int(8 * n(scale=6)) + (3 if y % 2 else 0)  # dim scanlines
        c = (g, g, g + 3)
        if x in (0, fw - 1) or y in (0, fh - 1):
            c = (0x26, 0x2A, 0x34)  # every face drawn with a faint outline, like a model that never got its texture
        base = ghost.get(tag.split("_")[0])
        k = n(scale=3)
        if base and k > 0.64:
            c = mix(c, base, min(0.7, (k - 0.64) * 3))
        return jitter(rng, c, 2)

    tears = {}

    def tear(key, fw, p):
        if key not in tears:
            start = rng.randint(0, max(0, fw - 2))
            tears[key] = (start, start + rng.randint(2, 7), rng.choice([magenta, cyan, white])) if rng.random() < p else None
        return tears[key]

    # Front of the head, 16x16: two square white eyes, one leaking light down
    # the face, and the shape of a mouth.
    face = [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..WWW.....WWW...",
        "..WWW.....WWW...",
        "..WWW.....WWW...",
        "...w......b.....",
        "...w............",
        "...b............",
        "................",
        "....mmmmmmm.....",
        "................",
        "................",
    ]

    def fn(tag, face_, x, y, fw, fh):
        if tag == "hitbox":
            return GLOW_WHITE
        if tag == "eyeline":
            return (0xFF, 0x30, 0x30, 40)
        if tag == "look":
            return (0x30, 0x60, 0xFF, 40)
        if tag == "head+":  # a band of static that crawls across the face
            if face_ == "front" and 5 <= y <= 11:
                return CLEAR
            t = tear((face_, y // 2), fw, 0.16)
            return t[2] if t and t[0] <= x < t[1] else CLEAR
        if tag == "head_strip":  # a loose slice of the same head, offset
            if face_ == "front" and y == 1 and 2 <= x <= 8:
                return rng.choice([(0x30, 0x30, 0x36), magenta])  # the mouth, dragged along with it
            return void("head", face_, x, y, fw, fh) if rng.random() < 0.8 else CLEAR
        if tag in ("missing", "missing_s"):
            k = max(1, fw // 2)
            return magenta if (x // k + y // k) % 2 == 0 else (0, 0, 0)
        if tag == "px":
            return rng.choice([magenta, cyan, white])
        if tag == "head" and face_ == "front":
            ch = face[y][x]
            if ch == "W":
                return GLOW_WHITE
            if ch == "w":
                return (0xFF, 0xFF, 0xFF, 120)
            if ch == "b":
                return (0x3A, 0x3A, 0x46)
            if ch == "m":
                return rng.choice([(0x30, 0x30, 0x36), magenta, (0x30, 0x30, 0x36)])
        if tag.startswith("slice") and face_ not in ("top", "bottom") and y >= fh - 2:
            return rng.choice([magenta, cyan, void(tag, face_, x, y, fw, fh)])  # torn edges
        if tag in ("arm_end", "null_finger") and y >= fh - 3 and rng.random() < 0.4:
            return rng.choice([magenta, cyan, white])
        if face_ not in ("top", "bottom"):
            t = tear((tag, face_, y), fw, 0.02)
            if t and t[0] <= x < t[1]:
                return t[2]  # stray lines of corrupted pixels
        return void(tag, face_, x, y, fw, fh)

    paint_model(cv, model, fn)
    return cv
