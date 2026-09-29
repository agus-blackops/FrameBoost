"""The creatures of Horror Legends: models and textures.

Every model is built at twice its in-game size and painted against that (box
UVs map one texel per unit, so a head gets a 16x16 face instead of 8x8), then
written out at its real size with the texture size in the JSON halved (see
Model.detail). Coordinates below are ordinary in-game pixels (half-pixel
steps allowed); the B() and C() helpers double them.

Textures are painted with material ramps (see paint.py): each painter says
which material a texel is made of and how much light it catches, and the
ramp picks the shade.
"""

from geometry import Bone, Cube, Model
from paint import Noise, Ramp, form, glow
from pixels import Canvas, box_faces, paint_box

K = 2  # texels per in-game pixel
CLEAR = (0, 0, 0, 0)
BLACK = (0x08, 0x07, 0x09)
EYE = glow("#ffffff", 40)


def _k(v):
    return tuple(x * K for x in v) if v is not None else None


def B(name, parent, pivot, rotation=None, cubes=()):
    return Bone(name, parent, _k(pivot), rotation, cubes)


def C(origin, size, tag, inflate=0.0, pivot=None, rotation=None):
    return Cube(_k(origin), _k(size), None, inflate * K, _k(pivot), rotation, share=tag)


def hd_model(identifier, bones, bounds, width=256):
    return Model(identifier, bones, bounds=bounds, detail=K).pack_uv(width)


def side_x(side, a, b):
    """Origin X of a cube spanning |x| in [a, b] on the model's right (-1) or
    left (+1) side."""
    return -b if side < 0 else a


class Texel:
    """One texel being painted: which part (tag), which face, where on the
    face and on the sheet, and helpers to paint it with a ramp."""

    __slots__ = ("tag", "face", "x", "y", "fw", "fh", "X", "Y", "noise")

    def lit(self):
        return form(self.face, self.x, self.y, self.fw, self.fh)

    def n(self, scale=3.0, octaves=2):
        return self.noise(self.X, self.Y, scale, octaves)

    def ridge(self, scale=4.0, width=0.06):
        return self.noise.ridge(self.X, self.Y, scale, width)

    def paint(self, ramp, base=0.55, rough=0.3, scale=3.0, extra=0.0):
        v = base + self.lit() + (self.n(scale) - 0.5) * rough + extra
        return ramp(v, self.X, self.Y)

    @property
    def side(self):
        return self.face in ("right", "left", "front", "back")


def paint_model(model, seed, fn):
    """fn(t: Texel) -> colour or None for every texel of every cube."""
    cv = Canvas(*model.texture)
    t = Texel()
    t.noise = Noise(seed)
    for bone in model.bones:
        for cube in bone.cubes:
            w, h, d = (int(s) for s in cube.size)
            origins = box_faces(*cube.uv, w, h, d)

            def paint(face, x, y, fw, fh, tag=cube.share, o=origins):
                t.tag, t.face, t.x, t.y, t.fw, t.fh = tag, face, x, y, fw, fh
                t.X, t.Y = o[face][0] + x, o[face][1] + y
                return fn(t)
            paint_box(cv, *cube.uv, w, h, d, paint)
    return cv


def rows(pattern, palette, t, fallback):
    c = palette.get(pattern[t.y][t.x])
    return fallback() if c is None else c


# Shared materials.
BONE = Ramp(colors=["#5e5242", "#8a7c64", "#b3a384", "#d2c4a2", "#e9dfc2", "#f6f0dc"])
BLOOD = Ramp(colors=["#1e0203", "#3c0507", "#5c0a0c", "#7e1414", "#9c2420"])
GUT = Ramp(colors=["#4a141c", "#72283a", "#9a3e4c", "#be5a64", "#d8808a", "#eaa6ac"])
MUSCLE = Ramp(colors=["#3a0a0c", "#5e1418", "#86222a", "#a8363a", "#c8584e", "#e08a78"])
BURN = Ramp(colors=["#1a0605", "#3a0c08", "#64160e", "#8e2416", "#b43c26", "#d46a4c"])
BLISTER = Ramp(colors=["#a88c5a", "#cdb47e", "#e8d4a0", "#f6eac4"])
TEETH = Ramp(colors=["#6a5c44", "#a8966e", "#cfbf98", "#e8dcbc", "#f8f0dc"])
IRON = Ramp(colors=["#2a2a2e", "#46464c", "#66666e", "#8c8c94", "#b4b4bc", "#dcdce2"])
WOOD = Ramp(colors=["#2e1e10", "#4a3018", "#664424", "#80582e", "#9a6e3c"])
HAIR = Ramp(colors=["#0e0906", "#1c120a", "#2c1c10", "#402a18", "#563a22"])


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
    skin = Ramp(colors=["#6e4430", "#96644a", "#b8805e", "#cc9676", "#deae8e", "#ecc6a6"])
    hair = Ramp(colors=["#1c1008", "#2e1c0e", "#402814", "#54361c", "#6a4626"])
    shirt = Ramp(colors=["#0a4a5a", "#0e6874", "#12868e", "#1ca2a6", "#3cbcb8", "#72d4cc"])
    pants = Ramp(colors=["#1a1650", "#262070", "#322c8c", "#3e389c", "#5450b4", "#6e6cc8"])
    shoe = Ramp(colors=["#26262c", "#3a3a42", "#50505a", "#686872", "#80808a"])
    eye = EYE if white_eyes else None
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
    fpal = {"H": hair.colors[1], "h": hair.colors[3], "b": hair.colors[2],
            "W": eye or (0xF0, 0xF0, 0xF0), "w": eye or (0xFF, 0xFF, 0xFF),
            "P": eye or (0x3A, 0x4E, 0x9E), "p": eye or (0x22, 0x2E, 0x66),
            "n": skin.colors[2], "N": skin.colors[1], "m": (0x7A, 0x50, 0x38), "M": (0x52, 0x32, 0x22),
            "r": (0x8E, 0x4A, 0x44)}

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                return rows(face, fpal, t, lambda: t.paint(skin, 0.62, 0.12))
            if f == "top":
                return t.paint(hair, 0.5, 0.6, 1.5)
            if f == "bottom":
                return t.paint(skin, 0.35, 0.1)
            if f == "back":
                return t.paint(hair, 0.45, 0.5, 1.5) if t.y < 13 - (t.x % 3 == 0) else t.paint(skin, 0.4, 0.1)
            back = t.x < 8 if f == "right" else t.x >= 8
            if t.y < 5 or (back and t.y < 11):
                return t.paint(hair, 0.45, 0.5, 1.5)
            if 8 <= t.y <= 11 and not back and t.x in ((9, 10) if f == "right" else (5, 6)):
                return skin.colors[1]  # ear
            return t.paint(skin, 0.55, 0.12)
        if tag == "head+":
            if f == "top" and (t.x in (0, 1, t.fw - 2, t.fw - 1) or t.y in (0, 1, t.fh - 2, t.fh - 1)) and t.n(1.2) > 0.55:
                return hair.colors[3]
            if f in ("right", "left", "back") and t.y < 4 and t.n(1.2) > 0.62:
                return hair.colors[2]
            return CLEAR
        if tag == "body":
            if f == "bottom":
                return pants.colors[2]
            if t.y >= t.fh - 2 and f != "top":
                return (0x5C, 0x40, 0x26) if not (f == "front" and t.x in (7, 8)) else (0xB8, 0xB0, 0x90)  # belt
            if f == "front" and t.y < 4 and abs(t.x - 7.5) < 4 - t.y:
                return t.paint(skin, 0.6, 0.1)  # open collar
            v = 0.62
            if f == "front" and t.x in (7, 8) and t.y >= 4:
                v -= 0.15  # placket
                if t.x == 8 and t.y % 4 == 1:
                    return (0xE6, 0xE6, 0xE0)  # buttons
            if f == "front" and 2 <= t.x <= 5 and 6 <= t.y <= 9 and (t.y == 6 or t.x in (2, 5)):
                v -= 0.12  # pocket
            if f in ("front", "back") and t.x in (4, 11) and 10 < t.y < 20:
                v -= 0.08  # folds
            return t.paint(shirt, v, 0.2)
        if tag == "body+":
            return shirt.colors[1] if t.y >= t.fh - 3 and t.y < t.fh - 2 and t.side else CLEAR
        if tag == "arm":
            if f == "top" or (f != "bottom" and t.y < 9):
                if t.y in (7, 8):
                    return shirt.colors[4] if t.y == 7 else shirt.colors[1]  # rolled-up sleeve
                return t.paint(shirt, 0.62, 0.2)
            if f == "bottom" or t.y >= t.fh - 3:
                return t.paint(skin, 0.38, 0.1)
            return t.paint(skin, 0.52, 0.12)
        if tag == "arm+":
            return shirt.colors[2] if t.y in (6, 7) and t.side else CLEAR
        if tag == "leg":
            if f == "bottom" or (f != "top" and t.y >= t.fh - 4):
                if t.y >= t.fh - 2 or f == "bottom":
                    return (0xE0, 0xDE, 0xD6) if f != "bottom" else (0xB0, 0xAE, 0xA6)  # white soles
                if f == "front" and t.y == t.fh - 4 and t.x % 3 == 1:
                    return (0xDA, 0xDA, 0xDA)  # laces
                return t.paint(shoe, 0.5, 0.2)
            v = 0.55
            if f == "front" and t.y < 4 and t.x in (2, 13):
                v -= 0.18  # pocket seams
            if f in ("front", "back") and t.x in (1, t.fw - 2):
                v += 0.1  # denim stitching
            if f == "front" and 12 <= t.y <= 14:
                v += 0.06  # knees
            return t.paint(pants, v, 0.2)
        if tag == "leg+":
            return pants.colors[1] if t.y == t.fh - 5 and t.side else CLEAR
        if tag == "tool_handle":
            return t.paint(WOOD, 0.5 + 0.15 * (t.y % 2), 0.2)
        if tag in ("tool_head", "tool_tip"):
            return t.paint(IRON, 0.7, 0.2)
        return None

    return paint_model(model, seed, fn)


# =========================================================================== #
# Herobrine: the hollow miner
# =========================================================================== #

def herobrine_model():
    """Three blocks of dead miner folded over his own hunch. Long legs, a
    narrow tipped pelvis, and a huge bowed back with the spine torn out of it
    in an arc of bone from the waist over the shoulders to the skull; the head
    hangs out in front on a stretched neck. The chest has burst open, ribs
    splayed like a second jaw round a dark heart; below it the stomach is an
    open, stitched cavity spilling guts. The mouth is torn to the ears over a
    jaw that hangs loose and drips, the left half of the face is burned to the
    skull, blisters cover the burned side, and the arms reach his knees: a
    flayed right arm with a bone through the wrist, the fingers locked round
    his pickaxe, and a burned left one ending in bone claws."""
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
            B(f"{name}Shin", f"{name}Thigh", (2 * side, 12, 0), rotation=(4, 0, 0), cubes=[
                C((sx(0.75, 3.25), 1.5, -1.25), (2.5, 10.5, 2.5), "shin"),
                C((sx(0.5, 3.5), 9.5, -1.75), (3, 2.5, 3), "knee"),
                C((sx(0.25, 3.75), 0.5, -3.5), (3.5, 2, 5), "boot"),
                C((sx(0.5, 3.5), 0.5, -4), (3, 1, 0.5), "boot_toe")]),
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
            C((-0.5, 19, -2), (1, 7, 1), "gut_hang"), C((-1, 18, -2.5), (2, 1.5, 1.5), "gut_knot")]),
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
            C((-6, 38, -2), (2, 2, 4), "shoulder_bone"),
            C((1, 36.5, 3), (7, 1, 1), "lodged_pick", pivot=(4.5, 37, 3.5), rotation=(0, 0, 32)),
            C((4, 36.5, 3.5), (1, 1, 4), "lodged_handle", pivot=(4.5, 37, 3.5), rotation=(25, 0, 0)),
            blister(4.5, 38, -1.5), blister(5, 35.5, 0.5), blister_s(5, 33.5, -2), blister_s(3.5, 40, 1)]),
        B("neck", "chest", (0, 40, -1), rotation=(48, 0, 0), cubes=[
            C((-1.5, 40, -2.5), (3, 5, 3), "neck"), C((-1, 40.5, -3), (0.5, 4, 0.5), "tendon"),
            C((0.5, 40.5, -3), (0.5, 4, 0.5), "tendon"),
            C((-0.75, 40.5, 0.25), (1.5, 1.5, 1.5), "vertebra_neck"), C((-0.75, 42.5, 0.25), (1.5, 1.5, 1.5), "vertebra_neck")]),
        B("head", "neck", (0, 45, -1), rotation=(-72, 0, 12), cubes=[
            C((-4, 45, -5), (8, 8, 8), "head"), C((-4, 45, -5), (8, 8, 8), "head+", 0.5),
            C((1, 48, -5.5), (3, 3.5, 1), "skull"),
            C((-3.5, 50, -5.5), (3, 0.5, 0.5), "brow"),
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
        # Right arm: skinned from the elbow down, a bone through the wrist,
        # the fingers locked round the handle.
        B("rightUpperArm", "chest", (-6, 39, 0), rotation=(-30, 0, 4), cubes=[
            C((-8, 28, -2), (4, 11, 4), "upper_r"), C((-8, 28, -2), (4, 11, 4), "sleeve", 0.3)]),
        B("rightForearm", "rightUpperArm", (-6, 28, 0), rotation=(-10, 0, 0), cubes=[
            C((-7.75, 17, -1.75), (3.5, 11, 3.5), "fore_r"), C((-6.5, 18, -2.5), (1, 5, 1), "bone_out")]),
        B("rightHand", "rightForearm", (-6, 17, 0), cubes=[
            C((-8, 14, -2), (4, 3, 4), "fist"),
            *[C((-8.25, 14 + 0.75 * k, -2.25), (0.5, 0.5, 4.5), "knuckle") for k in range(3)]]),
        B("pickaxe", "rightHand", (-6, 15.5, 0), rotation=(-12, 0, 0), cubes=[
            C((-6.5, 5, -0.5), (1, 11, 1), "handle"),
            C((-6.5, 3.5, -4.5), (1, 2, 9), "pick"),
            C((-6.5, 5.5, -4.5), (1, 1, 1), "pick_tip"), C((-6.5, 5.5, 3.5), (1, 1, 1), "pick_tip")]),
        # Left arm: burned black and blistered, the bone showing at the elbow,
        # ending in bone claws.
        B("leftUpperArm", "chest", (6, 39, 0), rotation=(-22, 0, -6), cubes=[
            C((4, 28, -2), (4, 11, 4), "upper_l"),
            blister(8, 34, -1), blister(8, 30.5, 1), blister_s(4.5, 36, -2.5), blister_s(7.5, 37, 1.5)]),
        B("leftForearm", "leftUpperArm", (6, 28, 0), rotation=(-16, 0, 0), cubes=[
            C((4.25, 17, -1.75), (3.5, 11, 3.5), "fore_l"), C((5.5, 26, 1.5), (1, 2.5, 1), "bone_out"),
            blister(7.75, 22, 0), blister_s(5.5, 24, -2.25), blister_s(4, 19, 0.5)]),
        B("leftHand", "leftForearm", (6, 17, 0), cubes=[
            C((4, 15, -2), (4, 2, 4), "palm"),
            *[C((a, 9, -1.5), (1, 6, 1), "claw", pivot=(a + 0.5, 15, -1), rotation=(-20, 0, (a - 5.7) * 5))
              for a in (4.1, 5.2, 6.3, 7.4)]]),
    ]
    return hd_model("geometry.hl.herobrine", bones, bounds=(3.0, 3.8, (0, 1.8, 0)))


def paint_herobrine(seed, model):
    flesh = Ramp(colors=["#3e3430", "#5e4e44", "#7c6a5a", "#9a8672", "#b4a28a", "#cabaa2"])
    bruise = Ramp(colors=["#2e2230", "#4a3848", "#6a4e5e", "#86687a"])
    shirt = Ramp(colors=["#07262a", "#0c3a40", "#125058", "#1a6a70", "#2a8484", "#4a9e98"])
    pants = Ramp(colors=["#0e0c24", "#16143a", "#201e50", "#2c2a68", "#3c3a80"])
    boot = Ramp(colors=["#140e0a", "#261c14", "#3a2c20", "#4e3e2e", "#64523e"])
    cavity = Ramp(colors=["#120203", "#220406", "#34080a", "#4c1014"])

    def burned(t, base=0.5):
        if t.ridge(2.5, 0.08) > 0.4:
            return BURN.colors[0]  # cracks in the char
        if t.n(1.5) > 0.88:
            return t.paint(BLISTER, 0.6, 0.3)
        return t.paint(BURN, base, 0.6, 2.0)

    def dead(t, base=0.55):
        m = t.n(5)
        if m > 0.68:
            return t.paint(bruise, base, 0.3)
        return t.paint(flesh, base - (0.12 if m < 0.25 else 0.0), 0.25)

    def cloth(t, ramp, base=0.55, soak=1.0):
        b = t.noise(t.X + 500, t.Y, 6) * soak
        if b > 0.66:
            return t.paint(BLOOD, 0.35 + (b - 0.66) * 1.5, 0.2)
        return t.paint(ramp, base, 0.35, 2.5)

    def left_side(t):
        f = t.face
        return f == "left" or (f in ("front", "top", "bottom") and t.x >= t.fw // 2) or (f == "back" and t.x < t.fw // 2)

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
    fpal = {"H": HAIR.colors[1], "h": HAIR.colors[3], "b": bruise.colors[1], "E": (0x2A, 0x16, 0x18), "W": EYE,
            "n": flesh.colors[2], "N": (0x22, 0x0C, 0x0C), "s": (0x16, 0x10, 0x0C), "T": TEETH.colors[3],
            "K": (0x0E, 0x03, 0x03), "G": MUSCLE.colors[4], "c": BURN.colors[0], "B": BONE.colors[4],
            "g": glow("#ffe0e0")}

    def fn(t):
        tag, f = t.tag, t.face
        left = left_side(t)
        if tag == "head":
            if f == "front":
                ch = face[t.y][t.x]
                if ch == "r":
                    return burned(t)
                if ch == "S":
                    return dead(t, 0.58)
                return fpal.get(ch) or dead(t)
            if left:
                if f == "left" and 5 <= t.x <= 10 and 5 <= t.y <= 10 and t.n(2) > 0.35:
                    return t.paint(BONE, 0.7, 0.2)
                return burned(t)
            if f == "top" or t.y < 5 or (f == "back" and t.y < 12):
                return t.paint(HAIR, 0.45, 0.6, 1.2)
            if f in ("right", "left") and t.y >= t.fh - 4 and t.x >= t.fw - 5:
                return MUSCLE.colors[3] if t.y == t.fh - 4 else (0x0E, 0x03, 0x03)  # the tear runs back to the ear
            return dead(t)
        if tag == "head+":
            if left or f == "bottom":
                return CLEAR
            if f == "top":
                return HAIR.colors[3] if t.n(1.5) > 0.5 else CLEAR
            if f == "front":
                return HAIR.colors[1] if t.y < 2 or (t.y == 2 and t.x % 3 == 0) else CLEAR
            return HAIR.colors[2] if t.y < 6 and t.n(1.5) > 0.4 else CLEAR
        if tag == "brow":
            return dead(t, 0.4)
        if tag == "skull":
            if f == "front" and 2 <= t.x <= 3 and 2 <= t.y <= 4:
                return (0x0E, 0x03, 0x03)
            return t.paint(BONE, 0.62, 0.3) if f != "back" else MUSCLE.colors[3]
        if tag in ("fang", "fang_low"):
            return t.paint(TEETH, 0.75 - 0.3 * (t.y == t.fh - 1 and tag == "fang"), 0.15)
        if tag == "nail":
            return IRON.colors[3] if f == "top" else IRON.colors[1]
        if tag == "jaw":
            if f == "front":
                if t.y < 2:
                    return (0x0E, 0x03, 0x03)
                return burned(t) if t.x >= t.fw // 2 else dead(t)
            if f == "top":
                return t.paint(MUSCLE, 0.25, 0.4)
            return burned(t) if left else dead(t, 0.45)
        if tag == "tongue":
            return t.paint(GUT, 0.5, 0.3)
        if tag in ("drip", "drip_s"):
            return t.paint(BLOOD, 0.7 - 0.5 * t.y / t.fh, 0.1)
        if tag in ("blister", "blister_s"):
            return MUSCLE.colors[4] if f == "bottom" else t.paint(BLISTER, 0.7, 0.2)
        if tag == "rib":
            return t.paint(BONE, 0.45 + 0.35 * t.x / max(1, t.fw - 1), 0.15) if f != "back" else BLOOD.colors[2]
        if tag == "chest_hole":
            return t.paint(cavity, 0.4, 0.4) if f == "front" else CLEAR
        if tag == "heart":
            return t.paint(MUSCLE, 0.45, 0.5, 1.5)
        if tag == "lodged_pick":
            return t.paint(BLOOD, 0.5, 0.2) if t.n(2) > 0.62 else t.paint(IRON, 0.5, 0.3)
        if tag == "lodged_handle":
            return WOOD.colors[3] if f == "back" else t.paint(WOOD, 0.45, 0.2)
        if tag == "cavity":
            if f == "front":  # the inside of the open stomach
                k = t.n(2.5)
                return t.paint(GUT, 0.2 + k * 0.6, 0.2) if k > 0.4 else t.paint(cavity, 0.4, 0.3)
            return cloth(t, shirt, 0.45) if f == "back" else t.paint(cavity, 0.35, 0.3)
        if tag == "flank":
            if f in ("left", "right"):
                return t.paint(MUSCLE, 0.4, 0.3) if (f == "left") == (t.x < t.fw // 2) else cloth(t, shirt, 0.45)
            if f == "front":
                return (0x16, 0x10, 0x0C) if t.y % 3 == 0 else dead(t)  # stitches across the wound's edge
            return cloth(t, shirt, 0.45)
        if tag == "wound_lip":
            return (0x16, 0x10, 0x0C) if t.x % 3 == 0 else t.paint(BLOOD, 0.55, 0.2)
        if tag in ("gut", "gut_big", "gut_s", "gut_hang", "gut_knot"):
            if tag == "gut_hang" and t.y >= t.fh - 2:
                return BLOOD.colors[2]
            return t.paint(GUT, 0.5, 0.45, 1.5)
        if tag in ("vertebra", "vertebra_top", "vertebra_neck"):
            if f == "bottom":
                return BLOOD.colors[2]
            return t.paint(BONE, 0.55, 0.3)
        if tag in ("spur", "shoulder_bone"):
            return t.paint(BONE, 0.62, 0.2) if t.y < t.fh - 1 or tag == "shoulder_bone" else BLOOD.colors[2]
        if tag == "neck":
            return burned(t) if left else dead(t, 0.45 if f == "front" and t.y % 3 == 0 else 0.55)
        if tag == "tendon":
            return t.paint(MUSCLE, 0.65, 0.2)
        if tag == "pelvis":
            return cloth(t, pants, 0.5, 0.8)
        if tag == "belt":
            if f in ("top", "bottom"):
                return CLEAR
            return (0x92, 0x88, 0x6C) if f == "front" and t.x in (6, 7) else t.paint(boot, 0.5, 0.2)
        if tag == "chest":
            if f == "back" and 7 <= t.x <= 12:
                return t.paint(MUSCLE, 0.3, 0.3)  # where the spine tore out
            if f == "front":
                return dead(t) if abs(t.x - 9.5) > 7 or t.y < 2 else t.paint(MUSCLE, 0.35, 0.3)
            if left and t.n(3) > 0.6:
                return burned(t)
            return cloth(t, shirt, 0.55)
        if tag == "chest+":  # the shirt, hanging in rags
            if f in ("top", "bottom") or (f == "back" and 5 <= t.x <= 14) or (f == "front" and 3 <= t.x <= 16):
                return CLEAR
            return cloth(t, shirt, 0.4) if t.y >= 10 and t.n(1.5) > 0.45 else CLEAR
        if tag == "upper_r":
            return cloth(t, shirt, 0.55) if f != "bottom" else dead(t)
        if tag == "sleeve":
            return cloth(t, shirt, 0.35) if t.y >= t.fh - 4 and t.side and t.n(1.5) > 0.4 else CLEAR
        if tag == "fore_r":  # flayed: bare muscle in strips, tendons, bone at the wrist
            if t.y >= t.fh - 2:
                return t.paint(BONE, 0.6, 0.2)
            if t.ridge(1.8, 0.05) > 0.5:
                return t.paint(MUSCLE, 0.85, 0.1)  # tendons
            return t.paint(MUSCLE, 0.35 + 0.2 * ((t.x // 2) % 2), 0.3, 1.5)
        if tag == "bone_out":
            return t.paint(BONE, 0.7, 0.2) if t.y < t.fh - 1 else BLOOD.colors[2]
        if tag in ("fist", "knuckle"):
            return t.paint(BLOOD, 0.45, 0.3) if t.n(2) > 0.55 else dead(t, 0.5)
        if tag in ("upper_l", "fore_l", "palm"):
            if tag == "upper_l" and (f == "top" or t.y < 4):
                return cloth(t, shirt, 0.45)
            return burned(t) if f != "bottom" else BURN.colors[0]
        if tag == "claw":
            return t.paint(BONE, 0.75 - 0.5 * t.y / t.fh, 0.15)
        if tag == "knee":
            if f == "front" and 3 <= t.x <= 7 and 1 <= t.y <= 3:
                return t.paint(BONE, 0.6, 0.2)  # through the torn cloth
            return cloth(t, pants, 0.5)
        if tag in ("thigh", "shin"):
            if f == "bottom":
                return pants.colors[1]
            if tag == "shin" and t.noise(t.X, t.Y + 300, 3) > 0.68:
                return dead(t)  # torn through to the leg
            return cloth(t, pants, 0.55)
        if tag == "thigh+":
            return pants.colors[2] if t.y < 3 and t.side else CLEAR
        if tag in ("boot", "boot_toe"):
            if f == "bottom" or t.y >= t.fh - 1:
                return boot.colors[0]
            return t.paint(boot, 0.5 + (0.2 if tag == "boot_toe" else 0.0), 0.3)
        if tag == "handle":
            if t.y < 4:
                return t.paint(WOOD, 0.25, 0.2)  # worn dark by his grip
            return t.paint(WOOD, 0.5, 0.35, 1.5)
        if tag in ("pick", "pick_tip"):
            k = t.n(2)
            if k > 0.62:
                return t.paint(BLOOD, 0.5, 0.2)
            return t.paint(IRON, 0.6 - 0.25 * (k < 0.2), 0.3)
        return None

    return paint_model(model, seed, fn)


# =========================================================================== #
# The Man From The Fog
# =========================================================================== #

def fog_man_model():
    """A figure you mistake for a dead tree in the fog. Stilt legs with knotted
    knees, a pelvis wrapped in rags, a waist you could close a hand round and
    a narrow chest under a shawl of rotten cloth; one bony shoulder higher
    than the other. A long neck carries a long head tipped hard to one side:
    a heavy brow over two tall black eyes, slits for a nose, and a jaw that
    hangs open far below it. His arms reach his shins and end in four long
    black fingers. A tattered shroud hangs down his back."""
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
            C((-5.5, 36, -3.25), (3, 4, 0.5), "shawl_tail"),
            C((-6.5, 36.5, -1.5), (3, 3, 3), "shoulder"), C((3.5, 35.5, -1.5), (3, 3, 3), "shoulder"),
            C((-4, 37.5, -2.5), (3.5, 0.5, 0.5), "collarbone"), C((0.5, 37.5, -2.5), (3.5, 0.5, 0.5), "collarbone"),
            vertebra(33, 2), vertebra(35, 2), vertebra(37, 2)]),
        B("coatBack", "chest", (0, 39, 2.5), cubes=[C((-5.5, 16, 2), (11, 23, 0.5), "shroud")]),
        B("coatRight", "chest", (-3, 39, -2.5), cubes=[C((-5, 26, -3), (3, 13, 0.5), "rag")]),
        B("coatLeft", "chest", (3, 39, -2.5), cubes=[C((2, 28, -3), (3, 11, 0.5), "rag")]),
        B("neck", "chest", (0, 39, 0), rotation=(14, 0, 0), cubes=[
            C((-0.75, 39, -0.75), (1.5, 7, 1.5), "neck"), C((-1, 40, -1), (0.5, 5, 0.5), "tendon"),
            C((0.5, 40, -1), (0.5, 5, 0.5), "tendon"), C((-0.5, 40, 0.5), (1, 5, 1), "neck_spine")]),
        B("head", "neck", (0, 46, 0), rotation=(-8, 0, 22), cubes=[
            C((-3, 46, -3), (6, 8, 6), "head"),
            C((-3, 51, -3.5), (6, 1, 1), "brow"),
            C((-3, 48, -3.25), (1.5, 1, 0.5), "cheek"), C((1.5, 48, -3.25), (1.5, 1, 0.5), "cheek")]),
        # Unhinged: the mouth hangs open to the middle of his chest.
        B("jaw", "head", (0, 47, 1.5), rotation=(20, 0, 0), cubes=[
            C((-2.5, 41.5, -3), (5, 5.5, 4.5), "jaw"), C((-1.5, 40.5, -2.5), (3, 1, 3), "chin")]),
        B("hair", "head", (0, 54, 2.5), rotation=(14, 0, 0), cubes=[
            C((-2.5, 46, 2.5), (0.5, 8, 0.5), "strand"), C((-0.5, 44, 2.5), (0.5, 10, 0.5), "strand_l"),
            C((1, 47, 2.5), (0.5, 7, 0.5), "strand_s"), C((2.5, 48, 1), (0.5, 6, 0.5), "strand_side"),
            C((-3, 49, 0), (0.5, 5, 0.5), "strand_side")]),
    ]
    for side, name, shoulder in ((-1, "right", 38), (1, "left", 37)):
        sx = lambda a, b: side_x(side, a, b)
        fingers = [C((sx(3.25 + 0.75 * k, 3.75 + 0.75 * k), shoulder - 34.5, -0.25), (0.5, 6, 0.5), "finger",
                      pivot=(side * (3.5 + 0.75 * k), shoulder - 28.5, 0), rotation=(-12 + 5 * k, 0, side * (k - 1.5) * 4))
                   for k in range(4)]
        bones += [
            B(f"{name}Arm", "chest", (5 * side, shoulder, 0), rotation=(-16, 0, -3 * side), cubes=[
                C((sx(4, 6), shoulder - 13, -1), (2, 13, 2), "upper_arm"),
                C((sx(4, 6), shoulder - 6, -1), (2, 6, 2), "sleeve_rag", 0.4)]),
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
    ash = Ramp(colors=["#2e3238", "#4a4f56", "#6a7076", "#8c9296", "#aab0b2", "#c6cac8", "#dcdedb"])
    vein = Ramp(colors=["#34405a", "#48587a", "#5e7294"])
    rag = Ramp(colors=["#100e0c", "#1e1a16", "#2e2822", "#403830", "#544a3e", "#6a5e4e"])
    black = (0x0A, 0x0A, 0x0C)
    mouth = Ramp(colors=["#0e0406", "#1e080a", "#300c10"])

    def skin(t, base=0.6):
        if t.ridge(3.5, 0.035) > 0.5:
            return t.paint(vein, 0.6, 0.2)
        return t.paint(ash, base, 0.28, 3.5)

    def dying(t, base=0.55):
        """Extremities going black from the tips."""
        k = t.y / max(1, t.fh - 1)
        return black if k > 0.8 else t.paint(ash, base - k * 0.7, 0.2)

    def cloth(t, base=0.5):
        v = base + (0.08 if (t.X + t.Y) % 2 == 0 else 0.0)  # burlap weave
        if t.noise(t.X + 900, t.Y, 5) > 0.7:
            v -= 0.2  # damp stains
        return t.paint(rag, v, 0.35, 2.5)

    def tattered(t, depth):
        """Rags ending in long torn strips with holes through them."""
        if t.y >= t.fh - depth and t.noise(t.x * 3.0, 0, 2.5, 1) < 0.3 + (t.y - (t.fh - depth)) / depth * 0.7:
            return True
        return t.y < t.fh - depth and t.n(2) > 0.8

    # Front of the head, 12x16: two tall black eyes leaking black, slits for a
    # nose, and the stubs of an upper jaw with no lips.
    head = [
        "SSSSSSSSSSSS",
        "SSSSSSSSSSSS",
        "SSSSSSSSSSSS",
        "SSSSSSSSSSSS",
        "SsKKKSSKKKsS",
        "sKKKKKsKKKKs",
        "sKKgKKsKKgKs",
        "sKKKKKSKKKKs",
        "SsKKKsSsKKKS",
        "SSdKsSSSdKSS",
        "SSdSSnnSSdSS",
        "SSdSSSSSSdSS",
        "SSSSSSSSSdSS",
        "sKKKKKKKKKKs",
        "KTKTTKTTKTTK",
        "KTTKTTKTTKTK",
    ]
    hpal = {"s": ash.colors[2], "K": black, "g": (0x5A, 0x58, 0x5E), "d": (0x14, 0x14, 0x18),
            "n": ash.colors[1], "T": TEETH.colors[3]}

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                return rows(head, hpal, t, lambda: skin(t, 0.66))
            if f == "bottom":
                return t.paint(mouth, 0.5, 0.3)
            near_front = (f == "right" and t.x >= t.fw - 3) or (f == "left" and t.x <= 2)
            if near_front and t.y >= t.fh - 3:
                return black  # the grin wraps round the face
            if f == "top" and t.n(1.2) > 0.72:
                return rag.colors[0]  # a few hairs
            return skin(t, 0.6)
        if tag == "brow":
            return skin(t, 0.7) if f in ("top", "front") and t.y == 0 else ash.colors[1]
        if tag == "cheek":
            return skin(t, 0.72) if f != "bottom" else ash.colors[0]
        if tag == "jaw":
            if f == "front":
                if t.y < 2:
                    return TEETH.colors[3] if t.x % 2 else black
                return skin(t, 0.55 - 0.15 * (t.y > t.fh - 3))
            if f == "top":
                return t.paint(mouth, 0.5, 0.4)
            if f in ("right", "left") and t.y < 2:
                return TEETH.colors[2] if t.x % 2 else black
            return skin(t, 0.48)
        if tag == "chin":
            return skin(t, 0.42)
        if tag.startswith("strand"):
            return t.paint(rag, 0.15 + 0.2 * ((t.y // 3) % 2), 0.1)
        if tag in ("neck", "neck_spine"):
            if tag == "neck_spine":
                return t.paint(BONE, 0.6, 0.2) if f in ("back", "top", "left", "right") else CLEAR
            return skin(t, 0.5 if f == "front" and t.x in (0, t.fw - 1) else 0.58)
        if tag == "tendon":
            return skin(t, 0.75)
        if tag == "chest":
            if f in ("front", "right", "left") and t.y % 3 == 2 and t.y > 1:
                return skin(t, 0.35)  # ribs
            if f == "front" and t.x in (8, 9) and t.y < 10:
                return skin(t, 0.72)  # sternum
            if f == "back" and t.x in (8, 9):
                return ash.colors[1]
            return skin(t, 0.6)
        if tag == "waist":
            return skin(t, 0.42 if f in ("right", "left") else 0.56)  # sucked in
        if tag == "collarbone":
            return skin(t, 0.8)
        if tag in ("shoulder", "elbow", "knee"):
            return skin(t, 0.68)
        if tag == "vertebra":
            return t.paint(BONE, 0.6, 0.2) if f != "bottom" else ash.colors[0]
        if tag in ("shawl", "shawl_tail"):
            if tag == "shawl" and f == "bottom" and 5 <= t.x <= 14 and 2 <= t.y <= 8:
                return CLEAR  # the hole for the neck
            if tag == "shawl_tail" and tattered(t, 6):
                return CLEAR
            if tag == "shawl" and t.side and t.y == t.fh - 1 and t.n(1.5) > 0.55:
                return CLEAR
            return cloth(t, 0.35)
        if tag == "shroud":
            if f in ("top", "bottom"):
                return cloth(t, 0.3) if f == "top" else CLEAR
            if tattered(t, 22):
                return CLEAR
            return cloth(t, 0.5 if f == "back" else 0.3)
        if tag == "rag":
            if f in ("top", "bottom"):
                return CLEAR
            return CLEAR if tattered(t, 12) else cloth(t, 0.62)
        if tag == "sleeve_rag":
            if f in ("top", "bottom") or t.y >= t.fh - 3 and t.n(1.5) > 0.4:
                return CLEAR
            return cloth(t, 0.45) if t.n(2) > 0.35 else CLEAR
        if tag in ("loincloth", "loincloth_b"):
            if f in ("top", "bottom"):
                return CLEAR
            return CLEAR if tattered(t, 6) else cloth(t, 0.5)
        if tag == "pelvis":
            return cloth(t, 0.35) if f != "bottom" else ash.colors[0]
        if tag in ("thigh", "upper_arm"):
            return skin(t, 0.6)
        if tag in ("shin", "forearm"):
            return skin(t, 0.66 if f == "front" and t.x == 1 else 0.56)  # the bone under the skin
        if tag == "foot":
            if f == "front" or (f == "top" and t.y < 3):
                return black if t.x % 2 == 0 else skin(t, 0.35)  # long toes
            return skin(t, 0.4)
        if tag == "toenail":
            return black
        if tag in ("palm", "thumb"):
            return dying(t, 0.5)
        if tag == "finger":
            return dying(t) if f != "bottom" else black
        return None

    return paint_model(model, seed, fn)


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
    above its back; the neck thrusts forward to a long, flat skull with a
    crown of bony spikes, four sunken eyes that glow, mandibles at the corners
    and a jaw lined with needles that drops open far too wide. Its hands and
    feet end in long hooked claws."""
    limbs = dict(DWELLER_LIMBS, **(limbs or {}))
    ridge = lambda y, z, h=2: C((-0.5, y, z), (1, h, 1), "ridge")
    needle = lambda x, y, z: C((x, y, z), (0.5, 1.5, 0.5), "needle")
    bones = [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 12, 6), cubes=[
            C((-3, 10, 4), (6, 4, 5), "pelvis"), C((-2.5, 10.5, 8.5), (5, 3, 2), "rump"),
            ridge(14, 5.5), ridge(14, 7.5, 1.5)]),
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
            *[needle(x, 10, -22.25) for x in (-3.5, -2.25, -1, 0.5, 1.75, 3)],
            C((-5, 10.5, -23), (1, 1, 3), "mandible", pivot=(-4.5, 11, -21), rotation=(0, -20, 0)),
            C((4, 10.5, -23), (1, 1, 3), "mandible", pivot=(4.5, 11, -21), rotation=(0, 20, 0))]),
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
                C((sx(3, 5), 23, -8), (2, 2.5, 2), "elbow"),
                C((sx(3.5, 4.5), 25, -7.5), (1, 1.5, 1), "elbow_spur")]),
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
    hide = Ramp(colors=["#2c2e26", "#484a3e", "#686a5a", "#8a8c78", "#a8aa94", "#c4c4ae", "#dedcc8"])
    belly = Ramp(colors=["#5a4a48", "#7c6a66", "#9e8c86", "#bcaca4", "#d6c8c0"])
    vein = Ramp(colors=["#4a3246", "#664660", "#825a78"])
    mouth = Ramp(colors=["#1a0406", "#300a0e", "#4a1016", "#661a22"])
    gum = Ramp(colors=["#5a1a22", "#7a2630", "#9a3a44"])
    claw_c = Ramp(colors=["#0e0c0a", "#1e1a16", "#302a22", "#443c32"])
    glowing = glow("#ffe68c", 60)

    def skin(t, base=0.55):
        k = t.noise(t.X + 700, t.Y, 3)
        if k > 0.8:
            return t.paint(hide, 0.9, 0.1)  # a wet sheen
        return t.paint(hide, base, 0.3, 3.0)

    def under(t, base=0.55):
        if t.ridge(2.5, 0.05) > 0.5:
            return t.paint(vein, 0.6, 0.2)
        return t.paint(belly, base, 0.25)

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
    spal = {"s": hide.colors[1], "K": BLACK, "g": glowing, "G": gum.colors[1]}

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "skull":
            if f == "front":
                return rows(skull, spal, t, lambda: skin(t, 0.6))
            if f == "bottom":
                return t.paint(mouth, 0.5, 0.3)
            if f in ("right", "left") and t.y >= t.fh - 2:
                return BLACK  # the mouth runs back along the sides
            return skin(t)
        if tag == "brow":
            return skin(t, 0.8) if f in ("top", "front") else hide.colors[0]
        if tag in ("spike", "elbow_spur"):
            return t.paint(BONE, 0.75 - 0.4 * t.y / t.fh, 0.15)
        if tag in ("needle", "mandible"):
            return t.paint(TEETH, 0.75, 0.15) if f != "top" or tag == "mandible" else gum.colors[1]
        if tag == "jaw":
            if f == "front":
                return BLACK if t.y == 0 else skin(t, 0.5)
            if f == "top":
                return t.paint(gum, 0.6, 0.3) if 5 <= t.x <= 10 and t.y > 3 else t.paint(mouth, 0.4, 0.4)
            if f == "bottom":
                return under(t)
            return skin(t, 0.45)
        if tag == "tongue":
            return t.paint(GUT, 0.55, 0.3)
        if tag == "neck":
            if f == "bottom":
                return under(t)
            return skin(t, 0.4 if f in ("right", "left") and t.x % 3 == 0 else 0.55)
        if tag == "ribcage":
            if f == "bottom":
                return under(t)
            if f in ("right", "left") and t.x % 3 == 1:
                return skin(t, 0.35)  # ribs pressing through the skin
            if f == "top" and t.x in (7, 8):
                return hide.colors[1]
            return skin(t)
        if tag == "ribs_side":
            return skin(t, 0.75) if t.y % 3 == 0 else hide.colors[0]
        if tag == "blade":
            return skin(t, 0.8) if f == "top" else hide.colors[2]
        if tag in ("abdomen", "pelvis", "rump"):
            if f == "bottom":
                return under(t)
            return skin(t, 0.42 if f in ("right", "left") and tag == "abdomen" else 0.55)
        if tag == "ridge":
            return BONE.colors[1] if f == "top" else t.paint(BONE, 0.7, 0.2)
        if tag in ("upper_arm", "thigh"):
            return skin(t, 0.55)
        if tag in ("shoulder", "elbow", "knee"):
            return skin(t, 0.7)
        if tag in ("forearm", "shin"):
            return skin(t, 0.6 - 0.35 * t.y / max(1, t.fh - 1))
        if tag in ("palm", "sole"):
            return skin(t, 0.3)
        if tag in ("claw", "toe_claw"):
            return t.paint(claw_c, 0.7 - 0.5 * t.y / max(1, t.fh - 1), 0.1)
        return None

    return paint_model(model, seed, fn)


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
            C((-3, 24.5, -4.5), (8, 1.5, 8), "head_strip"),
            C((-1.5, 31.5, -16), (0.5, 0.5, 12), "look")]),
        B("rightArm", "torsoHigh", (-6.5, 25.5, 0), cubes=[
            C((-8.5, 19, -1.5), (3, 6, 3), "arm"), C((-9, 11.5, -1.5), (3, 6.5, 3), "arm_low")]),
        B("leftArm", "torsoHigh", (4.5, 25.5, 0), cubes=[
            C((3.5, 19, -1.5), (3, 6.5, 3), "arm"), C((4, 11, -1.5), (3, 7, 3), "arm_low"),
            C((3.5, 3.5, -1.5), (3, 6.5, 3), "arm_end"),
            *[C((3.5 + k, 1.5, -1), (1, 3.5, 1), "null_finger", pivot=(4 + k, 3.5, -0.5), rotation=(-8 * k, 0, (k - 1) * 8))
              for k in (0, 1, 2)]]),
        B("halo", "head", (-1, 32, 0), cubes=[
            px(-8, 34, -1), px(4, 37, 2), px(-3, 39, -3), px(5, 30, -2), px(-9, 29, 2), px(1, 38, 3),
            px(-6, 38, 3), px(6, 34, 1),
            C((2, 39, -1), (2, 2, 2), "missing"), C((-10, 32, -2), (2, 2, 2), "missing"),
            C((5.5, 32, 2.5), (1.5, 1.5, 1.5), "missing_s")]),
    ], bounds=(2.5, 2.8, (0, 1.4, 0)))


def paint_null(seed, model):
    import random
    rng = random.Random(seed)
    void = Ramp(colors=["#030305", "#08080c", "#0e0e14", "#16161e", "#20202a", "#2c2c38"])
    magenta, cyan, white = (0xF8, 0x00, 0xF8), (0x00, 0xE0, 0xE0), (0xFF, 0xFF, 0xFF)
    # What is left of a default skin, drained of colour, bleeds through the
    # dark in places.
    ghost = {"head": Ramp(colors=["#2a2018", "#3e2e22", "#523e2e"]),
             "slice": Ramp(colors=["#08282a", "#0e3a3c", "#144c4e"]),
             "arm": Ramp(colors=["#2a2018", "#3e2e22", "#523e2e"]),
             "leg": Ramp(colors=["#10102c", "#18183e", "#222250"])}

    def dark(t, tag):
        if t.x in (0, t.fw - 1) or t.y in (0, t.fh - 1):
            return (0x26, 0x2A, 0x34)  # faint outlines, like a model that never got its texture
        base = ghost.get(tag.split("_")[0])
        if base and t.n(3) > 0.64:
            return t.paint(base, 0.5, 0.4)
        return t.paint(void, 0.35 + (0.12 if t.Y % 2 else 0.0), 0.4, 6)  # dim scanlines

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

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "hitbox":
            return EYE
        if tag == "eyeline":
            return glow("#ff3030")
        if tag == "look":
            return glow("#3060ff")
        if tag == "head+":  # a band of static that crawls across the face
            if f == "front" and 5 <= t.y <= 11:
                return CLEAR
            tr = tear((f, t.y // 2), t.fw, 0.16)
            return tr[2] if tr and tr[0] <= t.x < tr[1] else CLEAR
        if tag == "head_strip":  # a loose slice of the same head, offset
            if f == "front" and t.y == 1 and 2 <= t.x <= 8:
                return rng.choice([(0x30, 0x30, 0x36), magenta])  # the mouth, dragged along with it
            return dark(t, "head") if rng.random() < 0.8 else CLEAR
        if tag in ("missing", "missing_s"):
            k = max(1, t.fw // 2)
            return magenta if (t.x // k + t.y // k) % 2 == 0 else (0, 0, 0)
        if tag == "px":
            return rng.choice([magenta, cyan, white])
        if tag == "head" and f == "front":
            ch = face[t.y][t.x]
            if ch == "W":
                return EYE
            if ch == "w":
                return (0xFF, 0xFF, 0xFF, 120)
            if ch == "b":
                return (0x3A, 0x3A, 0x46)
            if ch == "m":
                return rng.choice([(0x30, 0x30, 0x36), magenta, (0x30, 0x30, 0x36)])
        if tag.startswith("slice") and t.side and t.y >= t.fh - 2:
            return rng.choice([magenta, cyan, dark(t, tag)])  # torn edges
        if tag in ("arm_end", "null_finger") and t.y >= t.fh - 3 and rng.random() < 0.4:
            return rng.choice([magenta, cyan, white])
        if t.side:
            tr = tear((tag, f, t.y), t.fw, 0.02)
            if tr and tr[0] <= t.x < tr[1]:
                return tr[2]  # stray lines of corrupted pixels
        return dark(t, tag)

    return paint_model(model, seed, fn)


# =========================================================================== #
# The boss: Herobrine, the Hollow Miner, in his true form
# =========================================================================== #

def herobrine_boss_model():
    """What comes when you call him by his name. Four blocks tall and no
    longer hiding: upright, burned black from the inside with light leaking
    through the cracks. His miner's helmet is still on his head, its lamp
    glowing red. The ribcage is thrown open round a burning heart, the spine
    runs up his back and rises behind his head in a crown of vertebrae and
    spikes, broken pickaxes jut from his shoulders. His right forearm has
    fused round a pickaxe the size of a man; his left hand is five bone
    claws. The jaw hangs open over two rows of fangs."""
    vertebra = lambda y, z=3.5: C((-1.5, y, z), (3, 2, 2), "b_vertebra")
    spur = lambda y: C((-0.5, y + 0.5, 5.5), (1, 1, 2.5), "b_spur")
    crown = []
    import math
    for a in range(-90, 91, 10):
        r = math.radians(a)
        cx, cy = 12 * math.sin(r), 56 + 12 * math.cos(r)
        crown.append(C((cx - 1, cy - 1, 6), (2, 2, 2), "b_crown"))
        if a % 30 == 0:
            sx, sy = 14 * math.sin(r), 56 + 14 * math.cos(r)
            crown.append(C((sx - 0.5, sy - 1, 6.5), (1, 4, 1), "b_crown_spike",
                           pivot=(sx, sy - 1, 7), rotation=(0, 0, -a)))
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Thigh", "root", (3 * side, 30, 0), cubes=[
                C((sx(1, 5), 17, -2), (4, 13, 4), "b_thigh"), C((sx(1, 5), 17, -2), (4, 13, 4), "b_thigh+", 0.3)]),
            B(f"{name}Shin", f"{name}Thigh", (3 * side, 17, 0), rotation=(4, 0, 0), cubes=[
                C((sx(1.5, 4.5), 2.5, -1.5), (3, 14.5, 3), "b_shin"),
                C((sx(1, 5), 14, -2.25), (4, 3, 3.5), "b_knee"),
                C((sx(0.5, 5.5), 0.5, -5), (5, 2.5, 7.5), "b_boot"),
                *[C((sx(1 + 1.4 * k, 2 + 1.4 * k), 0.5, -6), (1, 1, 1.5), "b_toe") for k in range(3)]]),
        ]
    bones += [
        B("body", "root", (0, 30, 0), cubes=[
            C((-5, 30, -2.5), (10, 4, 5), "b_pelvis"), C((-5, 33, -2.5), (10, 1, 5), "b_belt", 0.15),
            C((-3, 23, -3), (6, 8, 0.5), "b_rag"), C((-3.5, 24, 2.5), (7, 7, 0.5), "b_rag_back"),
            vertebra(30.5), vertebra(33)]),
        B("belly", "body", (0, 34, 0), cubes=[
            C((-4, 34, 0.5), (8, 7, 2), "b_cavity"),
            C((-4.5, 34, -2.5), (1, 7, 4), "b_flank"), C((3.5, 34, -2.5), (1, 7, 4), "b_flank"),
            C((-3, 35, -1.5), (3, 1.5, 1.5), "b_gut"), C((0.5, 36.5, -1.8), (3, 1.5, 1.5), "b_gut"),
            C((-2, 38.5, -1.5), (4, 1.5, 1.5), "b_gut"),
            vertebra(35.5), vertebra(38)]),
        B("gutHang", "belly", (0, 35, -1.5), cubes=[
            C((-0.5, 26, -2), (1, 9, 1), "b_gut_hang"), C((-1, 25, -2.5), (2, 1.5, 1.5), "b_gut_knot")]),
        B("chest", "belly", (0, 41, 0), rotation=(6, 0, 0), cubes=[
            C((-6, 41, -3.5), (12, 10, 7), "b_chest"),
            *[C((-6.5, y, -4.5), (5, 1, 1), "b_rib", pivot=(-1.5, y + 0.5, -4), rotation=(0, -50, 0)) for y in (42, 44, 46, 48)],
            *[C((1.5, y, -4.5), (5, 1, 1), "b_rib", pivot=(1.5, y + 0.5, -4), rotation=(0, 50, 0)) for y in (42, 44, 46, 48)],
            C((-9.5, 47, -2.5), (4, 4, 5), "b_pauldron"), C((5.5, 47, -2.5), (4, 4, 5), "b_pauldron"),
            *[C((x, 50.5, -0.5), (1, 3, 1), "b_spike", pivot=(x + 0.5, 51, 0), rotation=(0, 0, r))
              for x, r in ((-9, 25), (-7, 10), (6, -10), (8, -25))],
            *[vertebra(y) for y in (41.5, 44, 46.5, 49)],
            *[spur(y) for y in (41.5, 44, 46.5, 49)],
            *[C((x, 44, 4), (1, 1, 9), "b_haft", pivot=(x + 0.5, 44.5, 4), rotation=(rx, ry, 0))
              for x, rx, ry in ((-4, -35, -20), (3, -45, 25), (-0.5, -60, 5))],
            *[C((x - 2.5, 44 + h, 4 + d), (6, 1.5, 1), "b_pickhead", pivot=(x + 0.5, 44.5, 4), rotation=(rx, ry, 0))
              for x, rx, ry, h, d in ((-4, -35, -20, 0, 8), (3, -45, 25, 0, 8), (-0.5, -60, 5, 0, 8))]]),
        B("core", "chest", (0, 45.5, -1), cubes=[C((-1.5, 44, -2.5), (3, 3, 3), "b_core")]),
        B("spineCrown", "chest", (0, 51, 4), cubes=[
            *crown, C((-1, 50, 4.5), (2, 6, 2), "b_crown"), C((-1, 42, 5.5), (2, 9, 1.5), "b_crown")]),
        B("neck", "chest", (0, 51, -0.5), cubes=[
            C((-2, 51, -2.5), (4, 4.5, 4), "b_neck"), C((-1.5, 51.5, -3), (0.5, 3.5, 0.5), "b_tendon"),
            C((1, 51.5, -3), (0.5, 3.5, 0.5), "b_tendon")]),
        B("head", "neck", (0, 55.5, -0.5), rotation=(6, 0, -6), cubes=[
            C((-4.5, 55.5, -5), (9, 9, 9), "b_head"),
            C((-5, 62.5, -5.5), (10, 3, 10), "b_helmet"),
            C((-5.5, 62.5, -6.5), (11, 0.5, 11.5), "b_brim"),
            C((-1, 63, -6.5), (2, 2, 1), "b_lamp"),
            *[C((x, 54.5, -5.25), (1, 2, 1), "b_fang") for x in (-3.5, -2, -0.5, 1, 2.5)]]),
        B("jaw", "head", (0, 56, 2), rotation=(28, 0, -8), cubes=[
            C((-4, 53, -5.5), (8, 3, 7.5), "b_jaw"),
            *[C((x, 56, -5.25), (1, 1.5, 1), "b_fang") for x in (-3, -1.5, 0, 1.5)]]),
        B("dripL", "jaw", (2, 53, -5), cubes=[C((1.5, 47, -5.25), (1, 6, 1), "b_drip")]),
        # Right arm: flayed, fused round a pickaxe the size of a man.
        B("rightArm", "chest", (-8, 49, 0), rotation=(-8, 0, 6), cubes=[
            C((-10, 37, -2), (4, 12, 4), "b_upper_r"), C((-10, 43, -2), (4, 6, 4), "b_sleeve", 0.3)]),
        B("rightForearm", "rightArm", (-8, 37, 0), rotation=(-20, 0, 0), cubes=[
            C((-9.75, 25, -1.75), (3.5, 12, 3.5), "b_fore_r"),
            C((-10.25, 30, -0.5), (1, 3, 1), "b_spike"), C((-8.5, 33, 1.5), (1, 3, 1), "b_spike")]),
        B("blade", "rightForearm", (-8, 25, 0), cubes=[
            C((-9, 18, -1), (2, 7.5, 2), "b_blade_haft"),
            C((-9.5, 14.5, -10), (3, 4, 20), "b_blade"),
            C((-9.25, 13, -13.5), (2.5, 3, 4), "b_blade_tip", pivot=(-8, 16.5, -10), rotation=(-28, 0, 0)),
            C((-9.25, 13, 9.5), (2.5, 3, 4), "b_blade_tip", pivot=(-8, 16.5, 10), rotation=(28, 0, 0))]),
        # Left arm: burned to the bone, five claws.
        B("leftArm", "chest", (8, 49, 0), rotation=(-8, 0, -6), cubes=[
            C((6, 37, -2), (4, 12, 4), "b_upper_l")]),
        B("leftForearm", "leftArm", (8, 37, 0), rotation=(-20, 0, 0), cubes=[
            C((6.25, 25, -1.75), (3.5, 12, 3.5), "b_fore_l"), C((9.25, 31, -0.5), (1, 3, 1), "b_spike")]),
        B("leftHand", "leftForearm", (8, 25, 0), cubes=[
            C((6, 22, -2), (4, 3, 4), "b_palm"),
            *[C((a, 14, -1.5), (1, 8, 1), "b_claw", pivot=(a + 0.5, 22, -1), rotation=(-18, 0, (a - 8) * 5))
              for a in (6.1, 7.2, 8.3, 9.4)],
            C((5, 16, -2), (1, 6, 1), "b_claw_s", pivot=(5.5, 22, -1.5), rotation=(-30, 0, 20))]),
    ]
    return hd_model("geometry.hl.herobrine_boss", bones, bounds=(5.0, 5.0, (0, 2.2, 0)))


def paint_herobrine_boss(seed, model, phase=1):
    """Phase 2 burns hotter: the cracks, heart and eyes flare."""
    char = Ramp(colors=["#0a0808", "#161212", "#221c1a", "#302826", "#403634", "#524644"])
    ember = glow("#ff5a1e", 60) if phase == 1 else glow("#fff0b4", 30)
    ember_dim = glow("#b0200c", 90) if phase == 1 else glow("#ff7a2a", 50)
    helmet = Ramp(colors=["#2e2208", "#4e3a0e", "#76581a", "#9a7626", "#b89236", "#d2ae52"])
    rags = Ramp(colors=["#04080a", "#0a1216", "#101c22", "#18282e", "#223640"])
    core = glow("#ffffff", 20) if phase == 2 else glow("#ffd0a0", 30)

    def charred(t, base=0.5, cracks=1.0):
        k = t.ridge(7.0, 0.035 * cracks * (1.6 if phase == 2 else 1.0))
        if k > 0.5:
            return ember
        if k > 0.15:
            return ember_dim
        return t.paint(char, base, 0.35, 2.5)

    def cloth(t, base=0.5):
        if t.noise(t.X + 400, t.Y, 5) > 0.7:
            return t.paint(BLOOD, 0.4, 0.2)
        return t.paint(rags, base, 0.35, 2.0)

    # Front of the head, 18x18: both eyes blazing, light bleeding down the
    # face, cracks, and the upper jaw full of fangs.
    face = [
        "cccccccccccccccccc",
        "cccccccccccccccccc",
        "cccccccccccccccccc",
        "ccCcccccccccccCccc",
        "cccKKKKccccKKKKccc",
        "ccKKWWWKccKWWWKKcc",
        "ccKWWWWKccKWWWWKcc",
        "ccKKWWKKccKKWWKKcc",
        "cccKwKKccccKKwKccc",
        "ccccwccccccccwcccc",
        "ccccwcccnncccwcccc",
        "cccccccnNNnccccccc",
        "cccccccccccccccccc",
        "cKKKKKKKKKKKKKKKKc",
        "KKKKKKKKKKKKKKKKKK",
        "KKKKKKKKKKKKKKKKKK",
        "KKKKKKKKKKKKKKKKKK",
        "KKKKKKKKKKKKKKKKKK",
    ]
    eye = EYE if phase == 1 else glow("#ffe0c0", 20)
    fpal = {"K": (0x04, 0x02, 0x02), "W": eye, "w": glow("#ffffff", 110), "C": ember,
            "n": char.colors[1], "N": (0x02, 0x01, 0x01)}

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "b_head":
            if f == "front":
                return rows(face, fpal, t, lambda: charred(t, 0.55))
            if f == "bottom":
                return (0x04, 0x02, 0x02)
            return charred(t, 0.45)
        if tag == "b_helmet":
            if f == "bottom":
                return CLEAR
            if t.ridge(4.0, 0.04) > 0.5:
                return helmet.colors[0]  # cracked
            if t.side and t.y == t.fh - 2:
                return helmet.colors[1]  # the band
            return t.paint(helmet, 0.55, 0.2, 3.0)
        if tag == "b_brim":
            return t.paint(helmet, 0.35, 0.2) if f != "bottom" else helmet.colors[0]
        if tag == "b_lamp":
            return glow("#ff2a1a", 30) if f == "front" else IRON.colors[1]
        if tag == "b_fang":
            return t.paint(TEETH, 0.7 - 0.35 * t.y / t.fh, 0.15)
        if tag == "b_jaw":
            if f == "top":
                return t.paint(MUSCLE, 0.2, 0.3)
            if f == "front" and t.y < 1:
                return (0x04, 0x02, 0x02)
            return charred(t, 0.45)
        if tag == "b_drip":
            return glow("#ff5a1e", 80) if t.y < t.fh - 2 else ember
        if tag in ("b_chest", "b_neck", "b_upper_l", "b_fore_l", "b_palm", "b_thigh", "b_shin"):
            if tag == "b_chest" and f == "front":
                # the open ribcage: a dark hollow lit from the heart
                d = abs(t.x - t.fw / 2) / (t.fw / 2) + abs(t.y - t.fh / 2) / (t.fh / 2)
                return ember_dim if d < 0.5 and t.n(1.5) > 0.5 else t.paint(char, 0.1 + 0.2 * (1 - d), 0.2)
            if tag in ("b_thigh", "b_shin") and t.side and t.n(3) > 0.5:
                return cloth(t, 0.5)
            return charred(t, 0.5)
        if tag == "b_thigh+":
            return cloth(t, 0.35) if t.side and t.y < 5 and t.n(1.5) > 0.4 else CLEAR
        if tag == "b_knee":
            return t.paint(BONE, 0.55, 0.3)
        if tag in ("b_boot", "b_toe"):
            return t.paint(char, 0.3, 0.3) if tag == "b_boot" else t.paint(BONE, 0.6, 0.2)
        if tag in ("b_pelvis", "b_rag", "b_rag_back", "b_sleeve"):
            if tag in ("b_rag", "b_rag_back"):
                if f in ("top", "bottom"):
                    return CLEAR
                if t.y > t.fh - 6 and t.noise(t.x * 3.0, 0, 2.5, 1) < 0.2 + (t.y - (t.fh - 6)) / 6 * 0.7:
                    return CLEAR
            if tag == "b_sleeve" and (not t.side or t.n(1.5) < 0.4):
                return CLEAR
            return cloth(t, 0.45)
        if tag == "b_belt":
            if f in ("top", "bottom"):
                return CLEAR
            return (0x8A, 0x7E, 0x60) if f == "front" and t.x in (9, 10) else t.paint(char, 0.35, 0.2)
        if tag == "b_cavity":
            if f == "front":
                k = t.n(2.5)
                return t.paint(GUT, 0.15 + k * 0.5, 0.2) if k > 0.45 else (0x10, 0x02, 0x02)
            return charred(t, 0.4)
        if tag == "b_flank":
            if f == "front":
                return (0x16, 0x10, 0x0C) if t.y % 3 == 0 else charred(t, 0.5)
            return charred(t, 0.45)
        if tag in ("b_gut", "b_gut_hang", "b_gut_knot"):
            return t.paint(GUT, 0.4, 0.45, 1.5)
        if tag in ("b_rib", "b_vertebra", "b_crown", "b_knee"):
            if tag == "b_crown" and t.n(2) > 0.7:
                return ember_dim
            return t.paint(BONE, 0.5 + (0.3 * t.x / max(1, t.fw - 1) if tag == "b_rib" else 0), 0.3)
        if tag in ("b_spur", "b_spike", "b_crown_spike", "b_claw", "b_claw_s"):
            return t.paint(BONE, 0.75 - 0.5 * t.y / max(1, t.fh - 1), 0.15)
        if tag == "b_pauldron":
            return t.paint(BONE, 0.45, 0.4, 2.0) if f in ("top", "front", "back") else charred(t, 0.4)
        if tag == "b_core":
            return core if f != "bottom" else ember
        if tag == "b_tendon":
            return ember_dim
        if tag == "b_haft":
            return t.paint(WOOD, 0.4, 0.3)
        if tag == "b_pickhead":
            return t.paint(IRON, 0.45, 0.3) if t.n(2) < 0.6 else t.paint(BLOOD, 0.4, 0.2)
        if tag in ("b_upper_r", "b_fore_r"):
            if tag == "b_fore_r" or t.y > t.fh // 2:
                if t.ridge(1.8, 0.05) > 0.5:
                    return t.paint(MUSCLE, 0.85, 0.1)
                return t.paint(MUSCLE, 0.3 + 0.2 * ((t.x // 2) % 2), 0.3, 1.5)
            return charred(t, 0.5)
        if tag == "b_blade_haft":
            return t.paint(BONE, 0.5, 0.3)
        if tag in ("b_blade", "b_blade_tip"):
            k = t.n(2)
            if k > 0.7:
                return t.paint(BLOOD, 0.45, 0.2)
            if tag == "b_blade" and f in ("top", "bottom") and t.ridge(2, 0.05) > 0.5:
                return ember_dim  # it glows along the edge
            return t.paint(IRON, 0.5 + (0.2 if f == "top" else 0.0), 0.3)
        return None

    return paint_model(model, seed, fn)
