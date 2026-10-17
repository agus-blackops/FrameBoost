"""Herobrine: the white-eyed miner, grown wrong.

Three blocks tall and leaning to one side. His spine has torn out of his
lower back and rises over his shoulders like a scorpion's tail, curling
forward over his head to a bone sting. The chest is burst open, the ribs
splayed out like fingers; the stomach is an open, stitched cavity with the
guts sliding out. His face is stretched: the jaw hangs open halfway down his
chest, two rows of teeth, the left half burned red to the skull and covered
in blisters, one white eye burning. Barefoot on long toes, arms to his
knees; a broken pickaxe is still buried in his right shoulder."""

from models.kit import *

PREVIEW = {}


def model():
    blister = lambda x, y, z, s=1: C((x, y, z), (s, s, s), "blister" if s >= 1 else "blister_s")
    vertebra = lambda y, z, tag="vertebra": C((-1.25, y, z), (2.5, 1.5, 2.5), tag)
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Thigh", "root", (2.25 * side, 24, 0), cubes=[
                C((sx(0.5, 4), 13, -1.75), (3.5, 11, 3.5), "thigh"),
                C((sx(0.5, 4), 17, -1.75), (3.5, 7, 3.5), "thigh_rag", 0.35)]),
            B(f"{name}Shin", f"{name}Thigh", (2.25 * side, 13, 0), cubes=[
                C((sx(0.75, 3.75), 2, -1.5), (3, 11, 3), "shin"),
                C((sx(1, 3.5), 11, -2), (2.5, 2, 1), "kneecap"),
                C((sx(0.5, 4), 0, -4.5), (3.5, 2, 6), "foot"),
                *[C((sx(0.75 + k * 1.1, 1.75 + k * 1.1), 0, -6.5), (1, 1, 2), "toe") for k in range(3)]]),
        ]
    bones += [
        B("body", "root", (0, 24, 0), rotation=(4, 0, 0), cubes=[
            C((-4, 24, -2.25), (8, 4, 4.5), "pelvis"), C((-4, 27, -2.25), (8, 1, 4.5), "rope", 0.2)]),
        B("belly", "body", (0, 28, 0), cubes=[
            C((-3.5, 28, 0.5), (7, 6, 2), "cavity"),
            C((-4, 28, -2.5), (1, 6, 3), "flank"), C((3, 28, -2.5), (1, 6, 3), "flank"),
            C((-3, 33.5, -2.5), (6, 0.5, 2), "stitch"), C((-3, 28, -2.5), (6, 0.5, 2), "stitch"),
            C((-2.5, 28.5, -1.5), (2.5, 1.5, 2), "gut"), C((0.5, 29.5, -1.8), (2.5, 1.5, 2), "gut"),
            C((-2, 31, -2), (3.5, 1.5, 2), "gut"), C((-1, 32.5, -1.5), (2, 1, 1.5), "gut_s")]),
        B("gutHang", "belly", (-0.5, 29, -2), cubes=[
            C((-1, 21, -2.5), (1, 8, 1), "gut_hang"), C((-1.5, 20, -3), (2, 1.5, 1.5), "gut_knot")]),
        B("chest", "belly", (0, 34, 0), rotation=(8, 0, -8), cubes=[
            C((-4.5, 34, -2.5), (9, 9, 5), "chest"), C((-4.5, 36, -2.5), (9, 7, 5), "shirt", 0.3),
            C((-2.5, 35, -3), (5, 6, 0.5), "chest_hole"), C((-1, 36.5, -2.75), (2, 2.5, 1.5), "heart"),
            *[C((1, y, -3.5), (4, 1, 1), "rib", pivot=(1, y + 0.5, -3), rotation=(0, 55, 10)) for y in (35, 37, 39)],
            *[C((-5, y, -3.5), (4, 1, 1), "rib", pivot=(-1, y + 0.5, -3), rotation=(0, -55, -10)) for y in (35, 37, 39)],
            C((-7, 40, -1), (1, 1, 5), "pick_handle", pivot=(-5, 41, 0), rotation=(30, 0, 25)),
            C((-7.5, 43, 1), (1, 6, 1), "pick_head", pivot=(-5, 41, 0), rotation=(30, 0, 25)),
            blister(3.5, 41, -2), blister(4, 38.5, 0), blister(3.5, 42.5, 1, 0.5), blister(4.25, 36, -1.5, 0.5)]),
        # The spine: out of the back, up and over the head like a scorpion's tail.
        B("tail1", "body", (0, 26, 2.5), rotation=(-15, 0, 0), cubes=[vertebra(y, 1.75) for y in range(26, 48, 2)] + [
            C((-0.5, y + 0.25, 4.25), (1, 1, 2), "spur") for y in (28, 32, 36, 40, 44)]),
        B("tail2", "tail1", (0, 48, 3), rotation=(20, 0, 0), cubes=[vertebra(y, 1.75) for y in range(48, 56, 2)] + [
            C((-0.5, 50.25, 4.25), (1, 1, 2), "spur")]),
        B("tail3", "tail2", (0, 56, 3), rotation=(30, 0, 0), cubes=[vertebra(y, 1.75) for y in range(56, 64, 2)] + [
            C((-0.5, 58.25, 4.25), (1, 1, 2), "spur")]),
        B("tail4", "tail3", (0, 64, 3), rotation=(45, 0, 0), cubes=[vertebra(y, 1.75) for y in range(64, 70, 2)]),
        B("sting", "tail4", (0, 70, 3), rotation=(50, 0, 0), cubes=[
            C((-1.5, 70, 1.5), (3, 2.5, 3), "vertebra_big"), C((-0.75, 72.5, 2.25), (1.5, 3, 1.5), "sting"),
            C((-0.5, 75.5, 2.5), (1, 2, 1), "sting"), C((-0.25, 77.5, 2.75), (0.5, 1.5, 0.5), "sting_tip"),
            C((-0.5, 72, 3.5), (1, 1, 2), "spur")]),
        B("neck", "chest", (0, 43, -0.5), rotation=(12, 0, 0), cubes=[
            C((-1.5, 43, -2), (3, 3.5, 3), "neck"), C((-1, 43.5, -2.5), (0.5, 2.5, 0.5), "tendon"),
            C((0.5, 43.5, -2.5), (0.5, 2.5, 0.5), "tendon")]),
        B("head", "neck", (0, 46, -0.5), rotation=(-8, 0, 20), cubes=[
            C((-4, 46, -4.5), (8, 8, 8), "head"), C((-4, 46, -4.5), (8, 8, 8), "hair", 0.4),
            C((1, 48.5, -5), (2.5, 3.5, 0.75), "skull"),
            *[C((x, 45, -4.25), (1, 1.5, 1), "fang") for x in (-3.5, -2, -0.5, 1, 2.5)],
            blister(3.5, 51, -3), blister(4, 48, -1), blister(3.5, 52, 1, 0.5), blister(1.5, 54, -2, 0.5)]),
        # Unhinged: the jaw hangs open to the collarbone, strings of flesh
        # still joining it to the cheeks.
        B("jaw", "head", (0, 46.5, 2), rotation=(34, 0, -4), cubes=[
            C((-3.5, 43, -4.5), (7, 3, 6.5), "jaw"),
            *[C((x, 45.5, -4.25), (1, 1.5, 1), "fang_low") for x in (-3, -1.5, 0, 1.5)],
            C((-1, 43.5, -6.5), (2, 1, 3), "tongue"),
            C((-3.75, 45, -3), (0.5, 4, 0.5), "string"), C((3.25, 45, -2.5), (0.5, 4, 0.5), "string")]),
        B("drip", "jaw", (0, 43.5, -6), cubes=[C((-0.5, 38, -6.25), (1, 5.5, 1), "drip")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        burned = side > 0
        bones += [
            B(f"{name}Arm", "chest", (5.75 * side, 42, 0), rotation=(-12, 0, 6 * -side), cubes=[
                C((sx(4, 7.5), 31, -1.75), (3.5, 11, 3.5), "arm_burn" if burned else "arm"),
                *([blister(7.5, 37, -0.5), blister(7.5, 34, 1, 0.5), blister(4.5, 39.5, -2.25, 0.5)] if burned else
                  [C((sx(4, 7.5), 36, -1.75), (3.5, 6, 3.5), "sleeve", 0.3)])]),
            B(f"{name}Forearm", f"{name}Arm", (5.75 * side, 31, 0), rotation=(-16, 0, 0), cubes=[
                C((sx(4.25, 7.25), 19, -1.5), (3, 12, 3), "fore_burn" if burned else "fore"),
                C((sx(5.25, 6.25), 20, -2.25), (1, 4, 1), "bone_out")]),
            B(f"{name}Hand", f"{name}Forearm", (5.75 * side, 19, 0), cubes=[
                C((sx(4, 7.5), 16, -1.75), (3.5, 3, 3.5), "palm_burn" if burned else "palm"),
                *[C((sx(4.1 + k * 0.9, 4.9 + k * 0.9), 10 + (k % 2), -1.25), (0.8 if False else 1, 6, 1),
                    "claw" if burned else "finger", pivot=(side * (4.5 + k * 0.9), 16, -0.75),
                    rotation=(-22 + k * 4, 0, side * (k - 1.5) * 4)) for k in range(4)]]),
        ]
    return hd_model("geometry.hl.herobrine", bones, bounds=(3.0, 4.2, (0, 2.0, 0)))


def paint(seed, m, **_):
    flesh = Ramp(colors=["#3a302c", "#58483e", "#766454", "#92806c", "#ae9c84", "#c6b69e"])
    bruise = Ramp(colors=["#2a2030", "#443446", "#624a5c", "#7e6476"])
    shirt = Ramp(colors=["#062428", "#0b383e", "#114e56", "#19686e", "#2a8282", "#4a9c96"])
    pants = Ramp(colors=["#0c0a22", "#141238", "#1e1c4e", "#2a2866", "#3a387e"])
    cavity = Ramp(colors=["#100203", "#200406", "#32080a", "#4a1014"])
    rope = Ramp(colors=["#2e2416", "#4a3c24", "#665434", "#806c46"])
    char = Ramp(colors=["#0a0706", "#16100c", "#241a14", "#34261c", "#463426"])
    dark = (0x0E, 0x03, 0x03)

    def burned(t, base=0.5):
        if t.ridge(2.5, 0.08) > 0.4:
            return BURN.colors[0]
        if t.n2(1.5) > 0.86:
            return t.paint(BLISTER, 0.6, 0.3)
        return t.paint(BURN, base, 0.6, 2.0)

    def skin(t, base=0.56):
        m_ = t.n(5)
        if m_ > 0.7:
            return t.paint(bruise, base, 0.3)
        return t.paint(flesh, base - (0.1 if m_ < 0.25 else 0), 0.25)

    def cloth(t, ramp, base=0.55):
        b = t.n2(6)
        if b > 0.74:
            return t.paint(BLOOD, 0.35 + (b - 0.74) * 1.5, 0.2)
        return t.paint(ramp, base, 0.35, 2.5)

    def left(t):
        f = t.face
        return f == "left" or (f in ("front", "top", "bottom") and t.x >= t.fw // 2) or (f == "back" and t.x < t.fw // 2)

    # 16x16: dead skin on his right with one burning white eye and stitches
    # across the cheek; burned to the skull on his left, the socket empty
    # but for a glint far inside. The upper teeth run all the way across.
    face = [
        "HHHHHHHhHrrrrrrr",
        "HhHHHHHHHrcrrBrr",
        "HHHHhHHHrrrBBBBr",
        "HSSSHHHSrrBBBBBc",
        "SSSSSSSSrBBBBBBr",
        "SbbbbSSSrBBKKBBr",
        "bEWWEbSSrBKKKKBr",
        "bEWWEbSSrBKgKKBr",
        "SbbbbSSSrBBKKBBr",
        "SSSSSSnSrrBBBBrr",
        "SsSsSnNnrBBBBBcr",
        "SSSSSSSSrrBrrrrr",
        "SSSSSSSSrrrrrrrr",
        "GKKKKKKKKKKKKKKG",
        "KTKTKTKTKTKTKTKT",
        "KKKKKKKKKKKKKKKK",
    ]
    pal = {"H": HAIR.colors[1], "h": HAIR.colors[3], "b": bruise.colors[1], "E": (0x2A, 0x16, 0x18), "W": EYE,
           "n": flesh.colors[2], "N": dark, "s": (0x16, 0x10, 0x0C), "T": TEETH.colors[3], "K": dark,
           "G": MUSCLE.colors[4], "c": BURN.colors[0], "B": BONE.colors[4], "g": glow("#ffe0e0")}

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                ch = face[t.y][t.x]
                return burned(t) if ch == "r" else skin(t, 0.58) if ch == "S" else pal[ch]
            if left(t):
                return t.paint(BONE, 0.65, 0.2) if f == "left" and 5 <= t.x <= 10 and 5 <= t.y <= 10 and t.n(2) > 0.4 else burned(t)
            if f == "top" or t.y < 5 or (f == "back" and t.y < 11):
                return t.paint(HAIR, 0.45, 0.6, 1.2)
            if f in ("right", "left") and t.y >= t.fh - 3 and t.x >= t.fw - 6:
                return dark  # the mouth is torn back to the ear
            return skin(t)
        if tag == "hair":
            if left(t) or f == "bottom":
                return CLEAR
            if f == "top":
                return HAIR.colors[3] if t.n(1.5) > 0.5 else CLEAR
            return HAIR.colors[2] if t.y < 6 and t.n(1.5) > 0.45 else CLEAR
        if tag == "skull":
            return dark if f == "front" and 1 <= t.x <= 3 and 2 <= t.y <= 4 else t.paint(BONE, 0.62, 0.3)
        if tag in ("fang", "fang_low"):
            return t.paint(TEETH, 0.78 - 0.3 * t.v, 0.12)
        if tag == "jaw":
            if f == "top":
                return t.paint(MUSCLE, 0.25, 0.4)
            if f == "front":
                return burned(t) if t.x >= t.fw // 2 else skin(t, 0.5)
            return burned(t) if left(t) else skin(t, 0.45)
        if tag == "string":
            return t.paint(GUT, 0.55, 0.3) if t.n(2) > 0.3 else CLEAR
        if tag == "tongue":
            return t.paint(GUT, 0.5, 0.3)
        if tag == "drip":
            return t.paint(BLOOD, 0.75 - 0.5 * t.v, 0.1)
        if tag in ("blister", "blister_s"):
            return MUSCLE.colors[4] if f == "bottom" else t.paint(BLISTER, 0.7, 0.2)
        if tag == "rib":
            return t.paint(BONE, 0.45 + 0.35 * t.u, 0.15) if f != "back" else BLOOD.colors[2]
        if tag == "chest_hole":
            return t.paint(cavity, 0.4, 0.4) if f == "front" else CLEAR
        if tag == "heart":
            return t.paint(MUSCLE, 0.5, 0.5, 1.5)
        if tag == "cavity":
            if f == "front":
                k = t.n(2.5)
                return t.paint(GUT, 0.2 + k * 0.6, 0.2) if k > 0.4 else t.paint(cavity, 0.4, 0.3)
            return cloth(t, shirt, 0.45)
        if tag == "flank":
            if f == "front":
                return (0x16, 0x10, 0x0C) if t.y % 3 == 0 else skin(t)
            return cloth(t, shirt, 0.45)
        if tag == "stitch":
            return (0x16, 0x10, 0x0C) if t.x % 3 == 0 else t.paint(BLOOD, 0.55, 0.2)
        if tag.startswith("gut"):
            return BLOOD.colors[2] if tag == "gut_hang" and t.y >= t.fh - 2 else t.paint(GUT, 0.5, 0.45, 1.5)
        if tag in ("vertebra", "vertebra_big"):
            return BLOOD.colors[2] if f == "bottom" else t.paint(BONE, 0.55, 0.3)
        if tag in ("spur", "sting", "sting_tip", "kneecap", "bone_out", "claw"):
            return t.paint(BONE, 0.8 - 0.45 * t.v, 0.15)
        if tag == "neck":
            return burned(t) if left(t) else skin(t, 0.48)
        if tag == "tendon":
            return t.paint(MUSCLE, 0.65, 0.2)
        if tag == "pelvis":
            return cloth(t, pants, 0.5)
        if tag == "rope":
            return CLEAR if f in ("top", "bottom") else t.paint(rope, 0.5 + 0.2 * ((t.x + t.y) % 3 == 0), 0.2)
        if tag == "chest":
            if f == "back" and 7 <= t.x <= 11:
                return t.paint(MUSCLE, 0.3, 0.3)  # where the spine tore out
            if f == "front":
                return skin(t) if abs(t.x - 8.5) > 6 or t.y < 2 else t.paint(MUSCLE, 0.35, 0.3)
            return burned(t) if left(t) and t.n(3) > 0.55 else cloth(t, shirt, 0.55)
        if tag == "shirt":
            if f in ("top", "bottom") or (f == "front" and 3 <= t.x <= 14) or (f == "back" and 5 <= t.x <= 13):
                return CLEAR
            return cloth(t, shirt, 0.42) if t.y >= 6 and t.n(1.5) > 0.45 else CLEAR
        if tag in ("arm", "sleeve"):
            if tag == "sleeve":
                return cloth(t, shirt, 0.35) if t.side and t.y >= t.fh - 4 and t.n(1.5) > 0.4 else CLEAR
            return cloth(t, shirt, 0.55) if t.y < 12 else skin(t)
        if tag == "fore":
            if t.ridge(1.8, 0.05) > 0.5:
                return t.paint(MUSCLE, 0.85, 0.1)
            return t.paint(MUSCLE, 0.35 + 0.2 * ((t.x // 2) % 2), 0.3, 1.5)  # flayed
        if tag in ("arm_burn", "fore_burn", "palm_burn"):
            # Charred black, split open along red cracks.
            if f == "bottom" or t.ridge(2.2, 0.07) > 0.45:
                return t.paint(BURN, 0.75, 0.2)
            if t.n2(1.5) > 0.87:
                return t.paint(BLISTER, 0.55, 0.3)
            return t.paint(char, 0.5, 0.5, 2.0)
        if tag in ("palm", "finger"):
            return t.paint(BLOOD, 0.45, 0.3) if t.n(2) > 0.6 else skin(t, 0.5 - 0.2 * t.v * (tag == "finger"))
        if tag in ("thigh", "thigh_rag"):
            if tag == "thigh_rag":
                return cloth(t, pants, 0.42) if t.side and t.n(1.5) > 0.35 else CLEAR
            return cloth(t, pants, 0.55) if t.n2(3) < 0.72 else skin(t)
        if tag == "shin":
            return skin(t, 0.52)
        if tag == "foot":
            return skin(t, 0.42 if f != "top" else 0.52)
        if tag == "toe":
            return BLACK if f == "front" or (f == "top" and t.y >= t.fh - 1) else skin(t, 0.45)
        if tag == "pick_handle":
            return t.paint(WOOD, 0.45, 0.3)
        if tag == "pick_head":
            return t.paint(BLOOD, 0.5, 0.2) if t.n(2) > 0.62 else t.paint(IRON, 0.55, 0.3)
        return None

    return paint_model(m, seed, fn)
