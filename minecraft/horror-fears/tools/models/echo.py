"""The Echo.

What lives in the Ancient City. Nearly three blocks of something that was a
person once, grown through and through with sculk: black-blue skin cracked
like deepslate, glowing veins, the chest split open down the middle and the
ribs folded back like doors around a heart that pulses cyan. It has no eyes,
only a smooth skull with sculk tendrils growing out of it like antlers, tips
glowing, always listening. The jaw hangs open on long needle teeth. It walks
slowly, arms far too long, head tilted towards the last thing it heard."""

from models.kit import *

PREVIEW = {}


def tendril(x, y, z, h, lean, tag="tendril"):
    """A sculk tendril: a thin stalk and a glowing bud on top."""
    return [C((x, y, z), (0.75, h, 0.75), tag, pivot=(x + 0.375, y, z + 0.375), rotation=lean),
            C((x - 0.125, y + h, z - 0.125), (1, 1, 1), "bud", pivot=(x + 0.375, y, z + 0.375), rotation=lean)]


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2.25 * side, 24, 0), rotation=(-6, 0, 0), cubes=[
                C((sx(1, 3.5), 12, -1.25), (2.5, 12, 2.5), "thigh"),
                C((sx(0.75, 3.75), 20, -1.5), (3, 3, 3), "growth", 0.15)]),
            B(f"{name}Shin", f"{name}Leg", (2.25 * side, 12.5, 0), rotation=(10, 0, 0), cubes=[
                C((sx(1.25, 3.25), 1, -1), (2, 12, 2), "shin"),
                C((sx(1, 3.5), 0, -3.5), (2.5, 1.5, 4.5), "foot"),
                *[C((sx(1 + k * 0.75, 1.5 + k * 0.75), 0, -5), (0.5, 0.5, 1.5), "toe") for k in range(3)]]),
        ]
    bones += [
        B("body", "root", (0, 24, 0), cubes=[C((-3.5, 22, -2), (7, 4, 4), "hips")]),
        B("torso", "body", (0, 26, 0), rotation=(12, 0, 0), cubes=[
            C((-1, 26, -0.5), (2, 7, 2), "spine"),
            C((-2.5, 26, -1), (5, 6, 2.5), "gut"),
            C((-4.5, 32, 0), (9, 9, 2.5), "back"),
            C((-4.5, 32, -2.5), (1.5, 9, 2.5), "chest_side"), C((3, 32, -2.5), (1.5, 9, 2.5), "chest_side"),
            # The ribs, folded open like doors.
            *[C((-4.5, y, -3), (4, 0.75, 0.75), "rib", pivot=(-4.5, y, -2.5), rotation=(0, 40, 0)) for y in (33, 35, 37, 39)],
            *[C((0.5, y, -3), (4, 0.75, 0.75), "rib", pivot=(4.5, y, -2.5), rotation=(0, -40, 0)) for y in (33, 35, 37, 39)],
            C((-6, 40, -2.5), (12, 3, 5), "shoulders"),
            C((-6.25, 41, -2.75), (4, 2.5, 5.5), "growth", 0.2), C((2.75, 39.5, -2.75), (3.5, 3, 5.5), "growth", 0.2),
            *tendril(-4.5, 43, 0, 3, (0, 0, 20)), *tendril(3.75, 43, 1, 2.5, (10, 0, -25))]),
        B("heart", "torso", (0, 35.5, 0), cubes=[C((-1.5, 34, -1.5), (3, 3, 3), "heart")]),
        B("neck", "torso", (0, 43, 0), rotation=(-14, 0, 0), cubes=[
            C((-1, 42.5, -1), (2, 3, 2), "neck")]),
        B("head", "neck", (0, 45, -0.5), rotation=(0, 0, 14), cubes=[
            C((-3, 46, -3.5), (6, 6, 6.5), "skull"),
            C((-2.5, 45, -4), (5, 1.5, 3), "upper_teeth"),
            *tendril(-2.5, 52, -1, 4, (-10, 0, 25)), *tendril(1.75, 52, -1.5, 5, (-15, 0, -20)),
            *tendril(-0.5, 52, 1.5, 3.5, (20, 0, 5)), *tendril(2, 51, 2, 2.5, (25, 0, -35))]),
        B("jaw", "head", (0, 46, 1), rotation=(22, 0, 0), cubes=[
            C((-2.5, 43, -4), (5, 2, 5.5), "jaw"),
            *[C((x, 45, -3.75), (0.5, 1.5, 0.5), "needle") for x in (-2.25, -1, 0.5, 1.75)]]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "torso", (5.25 * side, 41.5, 0), rotation=(-6, 0, 6 * -side), cubes=[
                C((sx(4.25, 6.75), 27, -1.25), (2.5, 14.5, 2.5), "arm")]),
            B(f"{name}Forearm", f"{name}Arm", (5.5 * side, 27.5, 0), rotation=(-12, 0, 0), cubes=[
                C((sx(4.5, 6.5), 13, -1), (2, 14.5, 2), "forearm"),
                C((sx(4.25, 6.75), 18, -1.25), (2.5, 3, 2.5), "growth", 0.15)]),
            B(f"{name}Hand", f"{name}Forearm", (5.5 * side, 13, 0), cubes=[
                C((sx(4.25, 6.75), 10.5, -1.25), (2.5, 2.5, 2.5), "palm"),
                *[C((sx(4.25 + k * 0.75, 4.75 + k * 0.75), 3.5 - (k % 2), -1 + (k % 2) * 0.5), (0.5, 7 + (k % 2), 0.5),
                    "finger") for k in range(3)]]),
        ]
    return hd_model("geometry.hl.echo", bones, bounds=(2.5, 3.6, (0, 1.6, 0)))


def paint(seed, m, **_):
    slate = Ramp(colors=["#07090c", "#0d1116", "#141a20", "#1c242c", "#26303a", "#323e4a"])
    sculk = Ramp(colors=["#020a10", "#04141e", "#08202e", "#0c2c3e", "#12384e"])
    cyan = glow("#38f0f0", 80)
    pale = glow("#a8fff8", 95)
    dim = glow("#1a8a96", 55)

    def skin(t, base=0.45):
        # Deepslate cracks and, along them, veins that glow.
        r = t.ridge(4.0, 0.045)
        if r > 0.6:
            return dim if t.n(3) > 0.55 else t.paint(slate, 0.1, 0.1)
        if t.n2(2.2) > 0.72:
            return t.paint(sculk, 0.45, 0.3)  # sculk growing over it
        return t.paint(slate, base, 0.3, 2.5)

    def growth(t):
        if t.n(1.5) > 0.82:
            return cyan
        if t.n2(1.2) > 0.7 and t.face != "top":
            return CLEAR
        return t.paint(sculk, 0.45, 0.4, 1.5)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "heart":
            # It beats (the render pulses it); the middle is brightest.
            edge = t.x in (0, t.fw - 1) or t.y in (0, t.fh - 1)
            return cyan if edge else pale
        if tag == "back":
            if f == "front":
                return t.paint(sculk, 0.15 + 0.2 * t.n(2), 0.2)  # the inside of the chest
            return skin(t, 0.42)
        if tag == "rib":
            return t.paint(BONE, 0.35, 0.25) if t.n(2) > 0.35 else t.paint(sculk, 0.4, 0.2)
        if tag == "growth":
            return growth(t)
        if tag in ("tendril",):
            return t.paint(sculk, 0.5 - 0.2 * t.v, 0.3)
        if tag == "bud":
            return pale if f == "top" else cyan
        if tag == "skull":
            if f == "front":
                # No eyes: smooth, with two shallow pits where they would be.
                if t.y in (4, 5, 6) and t.x in (2, 3, 8, 9):
                    return t.paint(slate, 0.08, 0.05)
                return t.paint(BONE, 0.25 - 0.01 * t.y, 0.2) if t.n(2) > 0.45 else skin(t, 0.5)
            if f == "top":
                return skin(t, 0.4)
            return skin(t, 0.45)
        if tag == "upper_teeth":
            if f == "bottom":
                return t.paint(TEETH, 0.55, 0.1) if t.x % 2 == 0 else BLACK
            return skin(t, 0.35)
        if tag == "jaw":
            if f == "top":
                return t.paint(sculk, 0.1, 0.1)
            return skin(t, 0.38)
        if tag == "needle":
            return t.paint(TEETH, 0.65 - 0.3 * t.v, 0.1)
        if tag in ("chest_side", "shoulders", "hips", "neck", "spine", "gut", "thigh", "shin", "arm", "forearm"):
            if tag == "gut" and f == "front" and t.n(2) > 0.5:
                return t.paint(sculk, 0.2, 0.2)
            return skin(t, {"spine": 0.3, "gut": 0.35, "neck": 0.38}.get(tag, 0.45))
        if tag in ("palm", "foot", "toe"):
            return t.paint(slate, 0.25, 0.3)
        if tag == "finger":
            return dim if t.v > 0.85 else t.paint(slate, 0.35 - 0.2 * t.v, 0.15)
        return None

    return paint_model(m, seed, fn)
