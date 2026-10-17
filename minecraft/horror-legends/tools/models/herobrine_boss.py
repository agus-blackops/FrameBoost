"""Herobrine, the Hollow Miner: what comes when you say his name.

He dug so deep the mountain grew into him. Four and a half blocks of cracked
deepslate held together by iron bands, lava showing through every crack and
ore still stuck in the rock. His miner's helmet has sunk into his skull; the
lamp on it is the only light he needs, and under the brim two white eyes. His
chest is split open round a molten core, the ribs are stone. The right arm
ends in the pickaxe it fused to, taller than a man; the left is a fist the
size of a cart. His spine has broken out of his back and arches over him
like Herobrine's own, ending in a sting of glowing obsidian.

paint(..., phase=2): enraged, the cracks widen and burn white-hot, the lamp
turns red."""

from models.kit import *

PREVIEW = {}


def model():
    vertebra = lambda y, z: C((-2, y, z), (4, 2.5, 4), "vertebra")
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (5 * side, 30, 0), cubes=[
                C((sx(2, 8), 16, -3), (6, 14, 6), "thigh"),
                C((sx(1.5, 8.5), 22, -3.5), (7, 3, 7), "band")]),
            B(f"{name}Shin", f"{name}Leg", (5 * side, 16, 0), cubes=[
                C((sx(2.5, 7.5), 4, -2.5), (5, 12, 5), "shin"),
                C((sx(2, 8), 0, -6.5), (6, 4, 9), "boot"),
                C((sx(2.5, 7.5), 13, -3), (5, 3, 1), "knee")]),
        ]
    bones += [
        B("body", "root", (0, 30, 0), cubes=[
            C((-7, 30, -4), (14, 6, 8), "pelvis"), C((-7.5, 34, -4.5), (15, 2, 9), "belt")]),
        B("waist", "body", (0, 36, 0), rotation=(6, 0, 0), cubes=[
            C((-6, 36, -3.5), (12, 8, 7), "waist")]),
        B("chest", "waist", (0, 44, 0), rotation=(12, 0, 0), cubes=[
            C((-9, 44, -1), (18, 14, 7), "chest_back"),
            C((-9, 44, -5), (4, 14, 4), "chest_side"), C((5, 44, -5), (4, 14, 4), "chest_side"),
            C((-5, 54, -5), (10, 4, 4), "collar"),
            C((-3, 46, -4), (6, 6, 3), "core"),
            *[C((-5, y, -6), (3.5, 1.5, 1.5), "rib") for y in (45, 48, 51)],
            *[C((1.5, y, -6), (3.5, 1.5, 1.5), "rib") for y in (45, 48, 51)],
            C((-11, 52, -4.5), (7, 7, 9), "pauldron"), C((4, 52, -4.5), (7, 7, 9), "pauldron"),
            *[C((x, 59, z), (1.5, h, 1.5), "crystal", pivot=(x + 0.75, 59, z + 0.75), rotation=(rx, 0, rz))
              for x, z, h, rx, rz in ((-9.5, -2, 4, 10, 15), (-7.5, 1, 3, -15, -10), (6.5, -1, 4.5, 12, -18), (8.5, 2, 3, -10, 10))],
            C((-10, 55, -3), (2, 3, 2), "ore"), C((8, 56, 0), (2, 2, 2), "ore"), C((-6, 50, 5.5), (3, 3, 2), "ore")]),
        B("neck", "chest", (0, 58, -2), rotation=(-10, 0, 0), cubes=[
            C((-2.5, 57, -4.5), (5, 3, 5), "neck")]),
        B("head", "neck", (0, 60, -2.5), cubes=[
            C((-4.5, 60, -7), (9, 8, 9), "head"),
            C((-6, 66, -8.5), (12, 1.5, 12), "brim"), C((-5, 67.5, -7), (10, 3, 9), "helmet"),
            C((-1, 70.5, -6), (2, 1, 7), "crest"),
            C((-1.5, 67, -8.25), (3, 2.5, 1.5), "lamp")]),
        B("jaw", "head", (0, 61, 1), rotation=(25, 0, 0), cubes=[
            C((-4, 57.5, -6.5), (8, 3.5, 8), "jaw"),
            *[C((x, 60.5, -6.25), (1.5, 2, 1.5), "fang") for x in (-3.5, -1.5, 0.5, 2.5)]]),
        # The spine, arching over his head.
        B("tail1", "chest", (0, 48, 5), rotation=(-20, 0, 0), cubes=[vertebra(y, 4.5) for y in range(46, 62, 3)] + [
            C((-0.5, y, 8.5), (1, 1.5, 3), "spur") for y in (47, 53, 59)]),
        B("tail2", "tail1", (0, 63, 6), rotation=(35, 0, 0), cubes=[vertebra(y, 4.5) for y in range(63, 72, 3)] + [
            C((-0.5, 66, 8.5), (1, 1.5, 3), "spur")]),
        B("tail3", "tail2", (0, 72, 6), rotation=(40, 0, 0), cubes=[vertebra(y, 4.5) for y in range(72, 81, 3)]),
        B("sting", "tail3", (0, 81, 6), rotation=(55, 0, 0), cubes=[
            C((-2.5, 81, 4), (5, 3, 5), "vertebra_big"), C((-1.5, 84, 5), (3, 4, 3), "sting"),
            C((-1, 88, 5.5), (2, 3, 2), "sting"), C((-0.5, 91, 6), (1, 2, 1), "sting_tip")]),
        # Right arm: fused round the pickaxe.
        B("rightArm", "chest", (-11, 55, 0), rotation=(-8, 0, 8), cubes=[
            C((-15, 40, -3), (6, 15, 6), "arm"), C((-15.5, 46, -3.5), (7, 3, 7), "band")]),
        B("rightForearm", "rightArm", (-12, 40, 0), rotation=(-25, 0, 0), cubes=[
            C((-14.5, 26, -2.5), (5, 14, 5), "forearm"),
            C((-13, 14, -1), (2, 12, 2), "handle")]),
        B("pick", "rightForearm", (-12, 14, 0), cubes=[
            C((-13.5, 10.5, -3), (3, 4.5, 6), "pick_head"),
            C((-13, 11, -13), (2, 3, 10), "pick_arm", pivot=(-12, 12.5, -3), rotation=(14, 0, 0)),
            C((-12.5, 11.5, -17), (1, 2, 4), "pick_tip", pivot=(-12, 12.5, -3), rotation=(24, 0, 0)),
            C((-13, 11, 3), (2, 3, 10), "pick_arm", pivot=(-12, 12.5, 3), rotation=(-14, 0, 0)),
            C((-12.5, 11.5, 13), (1, 2, 4), "pick_tip", pivot=(-12, 12.5, 3), rotation=(-24, 0, 0))]),
        # Left arm: a fist of stone.
        B("leftArm", "chest", (11, 55, 0), rotation=(-4, 0, -10), cubes=[
            C((9, 40, -3), (6, 15, 6), "arm"), C((8.5, 46, -3.5), (7, 3, 7), "band")]),
        B("leftForearm", "leftArm", (12, 40, 0), rotation=(-12, 0, 0), cubes=[
            C((9, 27, -3.5), (7, 13, 7), "forearm_big"), C((10, 32, 3.5), (3, 3, 2), "ore")]),
        B("fist", "leftForearm", (12.5, 27, 0), cubes=[
            C((8, 18, -4.5), (9, 9, 9), "fist"),
            *[C((8.5 + k * 2, 21, -5.25), (2, 5.5, 1), "knuckle") for k in range(4)]]),
    ]
    return hd_model("geometry.hl.herobrine_boss", bones, bounds=(5.0, 6.5, (0, 3.0, 0)))


def paint(seed, m, phase=1, **_):
    rock = Ramp(colors=["#0e0e12", "#1a1a20", "#26262e", "#34343c", "#44444c", "#56565e"])
    lava = Ramp(colors=["#6a1404", "#b43008", "#e8640c", "#ffa028", "#ffd872"])
    helmet = Ramp(colors=["#4a2e04", "#7a4c06", "#a8700c", "#d09818", "#eab832", "#f6d466"])
    rust = Ramp(colors=["#2a1408", "#44220e", "#5e3216", "#7a4420"])
    ores = [(0x3A, 0xE8, 0xE0), (0xE0, 0x1A, 0x1A), (0xF0, 0xC8, 0x30)]
    hot = phase == 2
    crack_w = 0.09 if hot else 0.045
    lamp = glow("#ff3020", 30) if hot else glow("#fff4c0", 30)

    def burning(v, t):
        c = lava(v + (0.25 if hot else 0.0), t.FX, t.FY)
        return glow(c, 70)

    def stone(t, base=0.5, cracks=True):
        if cracks:
            r = t.ridge(5.0, crack_w)
            if r > 0.45:
                return burning(0.35 + r * 0.5, t)
            if r > 0.2:
                return t.paint(rust, 0.3 + r, 0.2)  # heat round the crack
        o = t.n2(1.2)
        if o > 0.93:
            return ores[int(t.X * 7 + t.Y * 13) % 3]  # ore still in the rock
        return t.paint(rock, base, 0.45, 2.5)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                # Under the brim: two white eyes in a black face.
                if 4 <= t.y <= 6 and t.x in (4, 5, 12, 13):
                    return EYE
                if 3 <= t.y <= 7 and (3 <= t.x <= 6 or 11 <= t.x <= 14):
                    return BLACK
                if t.y >= t.fh - 3:
                    return t.paint(TEETH, 0.55, 0.2) if t.x % 2 == 0 else BLACK
                return stone(t, 0.35)
            return stone(t, 0.4)
        if tag in ("brim", "helmet", "crest"):
            if t.n(2) > 0.62:
                return t.paint(rust, 0.4, 0.3)
            return t.paint(helmet, 0.5 if tag == "helmet" else 0.4, 0.35, 2.0)
        if tag == "lamp":
            return lamp if f == "front" else t.paint(IRON, 0.5, 0.2)
        if tag == "jaw":
            if f == "top":
                return burning(0.1, t)
            return stone(t, 0.35)
        if tag == "fang":
            return t.paint(TEETH, 0.7 - 0.3 * t.v, 0.1)
        if tag == "core":
            if f == "front":
                cx, cy = (t.fw - 1) / 2, (t.fh - 1) / 2
                d = math.hypot(t.x - cx, t.y - cy) / max(cx, cy)
                return burning(1.0 - d * 0.8 + (t.n(1.5) - 0.5) * 0.3, t)
            return burning(0.2, t)
        if tag == "crystal":
            return glow((0x3A, 0xE8, 0xE0) if (t.x + t.y) % 3 else (0xA0, 0xFF, 0xF8), 60) if hot else (
                (0x3A, 0xE8, 0xE0) if (t.x + t.y) % 3 else (0xA0, 0xFF, 0xF8))
        if tag == "rib":
            return stone(t, 0.55, cracks=False)
        if tag in ("chest_back", "chest_side", "collar", "pauldron", "pelvis", "waist", "neck",
                   "thigh", "shin", "arm", "forearm", "forearm_big", "fist"):
            if tag == "chest_back" and f == "front":
                return burning(0.25, t)  # the inside of the chest glows
            return stone(t, {"pauldron": 0.55, "fist": 0.5, "neck": 0.35}.get(tag, 0.45))
        if tag == "knuckle":
            return stone(t, 0.6, cracks=False)
        if tag in ("band", "belt", "knee"):
            if t.side and t.x % 4 == 1 and t.y == t.fh // 2:
                return t.paint(IRON, 0.85, 0.1)  # rivets
            return t.paint(IRON, 0.35, 0.3) if t.n(2) > 0.35 else t.paint(rust, 0.45, 0.3)
        if tag == "boot":
            return t.paint(rust, 0.4, 0.4) if f != "bottom" else BLACK
        if tag == "ore":
            return ores[(t.X // 3 + t.Y // 3) % 3] if (t.x + t.y) % 2 == 0 else t.paint(rock, 0.5, 0.2)
        if tag in ("vertebra", "vertebra_big"):
            if f == "bottom":
                return burning(0.2, t)
            return stone(t, 0.55)
        if tag == "spur":
            return t.paint(rock, 0.6 - 0.3 * t.v, 0.2)
        if tag in ("sting", "sting_tip"):
            # Obsidian, glowing from inside.
            if t.ridge(2.5, 0.12) > 0.4:
                return glow((0xB0, 0x40, 0xFF), 60)
            return t.paint(Ramp(colors=["#08040e", "#140a22", "#221236", "#301c4a"]), 0.5, 0.4)
        if tag == "handle":
            return t.paint(WOOD, 0.4, 0.4) if t.n(2) > 0.3 else burning(0.1, t)
        if tag in ("pick_head", "pick_arm", "pick_tip"):
            if t.ridge(4.0, crack_w) > 0.5:
                return burning(0.5, t)
            return t.paint(IRON, 0.45 + 0.2 * (f == "top"), 0.35, 2.0)
        return None

    return paint_model(m, seed, fn)
