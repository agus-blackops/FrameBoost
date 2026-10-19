"""The Evil Hunter.

What answers the ritual. A corrupted man, taller than you and hunched, made
of shadow from the waist down: legs and hips almost entirely black, smoke
that holds a shape. From the chest up it is burned. The flesh is charred,
deformed and swollen on one side, split all over with cracks that glow
crimson and molten red, as if it were still burning inside. The head is a
lumpy, melted skull with a jaw that hangs open, and two red eyes that
never close. The hands are black, with long hooked claws."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2.25 * side, 17, 0), cubes=[
                C((sx(0.5, 4), 9, -1.75), (3.5, 8.5, 3.5), "shadow"),
                C((sx(0.25, 4.25), 13, -2), (4, 4, 4), "shadow", 0.1)]),
            B(f"{name}Shin", f"{name}Leg", (2.25 * side, 9, 0), cubes=[
                C((sx(0.75, 3.75), 1, -1.5), (3, 8.5, 3), "shadow"),
                C((sx(0.5, 4), 0, -3), (3.5, 1.5, 4.5), "shadow")]),
        ]
    bones += [
        B("hips", "root", (0, 17, 0), cubes=[
            C((-4.5, 16, -2.25), (9, 4, 4.5), "shadow"),
            # Smoke rags hanging from the waist.
            *[C((x, 11 - k, -2.5), (1.5, 5 + k, 0.5), "rag") for k, x in enumerate((-4, -1.5, 1, 3))]]),
        # Hunched forward, the chest heavy.
        B("chest", "hips", (0, 20, 0), rotation=(-20, 0, 3), cubes=[
            C((-4.25, 20, -2.25), (8.5, 5, 4.5), "waist"),
            C((-5.5, 25, -3), (11, 8, 6), "chest"),
            C((-6.5, 29, -2.5), (3, 4, 5), "chest", 0.2),  # a swelling on the right shoulder
            *[C((-4.5, 24.5 + k * 1.75, -3.4), (9, 0.75, 0.5), "rib") for k in range(4)]]),
        B("neck", "chest", (0, 33, 0), rotation=(-6, 0, 0), cubes=[
            C((-1.75, 32.5, -2), (3.5, 3, 3.5), "flesh")]),
        B("head", "neck", (0, 35, 0), rotation=(14, -8, 10), cubes=[
            C((-4, 35, -4.5), (8, 8.5, 8), "skull"),
            C((-5.25, 38, -3.5), (2, 5, 6), "skull"),  # melted, bulging on one side
            C((-3, 43.5, -3.5), (5, 1.5, 6), "skull"),
            C((-3.5, 38.75, -5), (7, 2.25, 1), "brow"),
            C((-1, 36.5, -5.5), (2, 2.5, 1.25), "flesh")]),  # what is left of the nose
        B("jaw", "head", (0, 36, -1), rotation=(22, 0, -4), cubes=[
            C((-3.5, 33, -5), (7, 3, 6.5), "jaw"),
            *[C((-2.75 + k * 1.25, 35.6, -4.9), (0.75, 1.1, 0.75), "tooth") for k in range(5)]]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "chest", (6 * side, 31, 0), rotation=(14, 0, 8 * -side), cubes=[
                C((sx(5, 8.5), 20, -1.75), (3.5, 11.5, 3.5), "flesh_arm")]),
            B(f"{name}Forearm", f"{name}Arm", (6.75 * side, 20.5, 0), rotation=(-14, 0, 0), cubes=[
                C((sx(5.25, 8.25), 10, -1.5), (3, 11, 3), "shadow")]),
            B(f"{name}Hand", f"{name}Forearm", (6.75 * side, 10, 0), cubes=[
                C((sx(5, 8.5), 7, -1.75), (3.5, 3.5, 3.5), "shadow"),
                *[C((sx(5.25 + k * 1.1, 5.75 + k * 1.1), 1.5 - (k == 1), -1.4 + k * 0.9), (0.5, 6 + (k == 1), 0.5), "claw")
                  for k in range(3)]]),
        ]
    return hd_model("geometry.hl.evil_hunter", bones, bounds=(1.6, 3.0, (0, 1.3, 0)))


def paint(seed, m, **_):
    shadow = Ramp(colors=["#020203", "#060507", "#0b090c", "#120e12", "#1c1418"])
    ember = glow("#ff3a12", 55)
    molten = glow("#ffb040", 70)
    eye = glow("#ff1a10", 85)
    dark_red = (0x3a, 0x06, 0x06)

    def cracks(t, base_ramp, base):
        r = t.ridge(3.5, 0.05)
        if r > 0.72:
            return molten if t.grain() > 0.55 else ember
        if r > 0.55:
            return t.paint(BURN, 0.62, 0.2)
        return t.paint(base_ramp, base, 0.45, 2.0)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "shadow":
            # Almost all black, a dark red smoke moving under it, and here and
            # there a vein still glowing.
            if t.ridge(5.0, 0.02) > 0.9 and t.n(2.0) > 0.55:
                return ember
            if t.n2(1.8) > 0.78:
                return dark_red
            return t.paint(shadow, 0.45, 0.4, 1.6)
        if tag == "rag":
            if t.y >= t.fh - 2 and t.n(1.0) > 0.45:
                return CLEAR
            return t.paint(shadow, 0.35, 0.3)
        if tag == "waist":
            # Where the burning ends and the shadow begins.
            if t.side and t.v < 0.5 and t.n(2.0) > 0.35:
                return cracks(t, BURN, 0.3)
            return t.paint(shadow, 0.5, 0.4)
        if tag in ("chest", "flesh", "flesh_arm"):
            if tag == "flesh_arm" and t.v > 0.6:
                return t.paint(shadow, 0.5, 0.4)  # charred to black at the elbow
            return cracks(t, BURN, 0.45)
        if tag == "rib":
            return t.paint(BONE, 0.3, 0.3) if f == "front" else t.paint(BURN, 0.2, 0.2)
        if tag in ("skull", "brow"):
            if tag == "skull" and f == "front":
                # Two red eyes in sunken holes.
                for ex in (3, 10):
                    if ex <= t.x <= ex + 3 and 10 <= t.y <= 13:
                        return eye if ex + 1 <= t.x <= ex + 2 and 11 <= t.y <= 12 else (0x08, 0x02, 0x02)
            return cracks(t, BURN, 0.4)
        if tag == "jaw":
            if f == "top":
                return (0x14, 0x02, 0x03)  # the inside of the mouth
            return cracks(t, BURN, 0.35)
        if tag == "tooth":
            return t.paint(TEETH, 0.4, 0.3)
        if tag == "claw":
            return t.paint(BONE, 0.35 - 0.2 * t.v, 0.2)
        return None

    return paint_model(m, seed, fn)
