"""The Boiled One (Doctor Nowhere's Boiled One Phenomenon).

It stands in a field, far off, five blocks tall and impossibly thin. Its
skin is the red of something that has been boiled: raw crimson, blistered,
darker where it has split. Everything about it is withered: a ribcage you
could count, arms like dead branches hanging to the knees, legs bent
slightly as if it can barely stand. Its head is wrong, lumpy and swollen to
one side and tipped over, and in it two small red eyes glow, and they can
be seen from very far away in the dark."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2 * side, 40, 0), rotation=(-8, 0, 2 * -side), cubes=[
                C((sx(0.75, 3.25), 21, -1.25), (2.5, 19.5, 2.5), "flesh"),
                C((sx(0.5, 3.5), 20, -1.5), (3, 3, 3), "joint")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 21.5, 0), rotation=(14, 0, 0), cubes=[
                C((sx(1, 3), 1.5, -1), (2, 20, 2), "flesh"),
                C((sx(0.75, 3.25), 0, -2.75), (2.5, 1.5, 3.5), "flesh")]),
        ]
    ribs = [C((-3.5, 47 + k * 1.6, -1.9), (7, 0.7, 0.5), "rib") for k in range(6)]
    bones += [
        B("body", "root", (0, 40, 0), rotation=(-6, 0, 2), cubes=[
            C((-3, 39.5, -1.5), (6, 3, 3), "flesh"),
            C((-1.75, 42.5, -1.25), (3.5, 4.5, 2.5), "flesh"),
            C((-3.75, 46.5, -1.75), (7.5, 10, 3.5), "flesh"), *ribs,
            C((-5, 55.5, -1.5), (10, 2, 3), "flesh")]),
        B("neck", "body", (0, 57, 0), rotation=(-10, 0, 0), cubes=[C((-0.9, 56.5, -0.9), (1.8, 4, 1.8), "flesh")]),
        # The head: swollen to one side, lumpy, tipped over.
        B("head", "neck", (0, 60.5, 0), rotation=(14, 0, 24), cubes=[
            C((-3, 60.5, -3), (6, 7, 6), "head"),
            C((-4.75, 62, -2.5), (2.5, 5.5, 5), "head"),
            C((1, 66.5, -2), (3, 2.5, 4), "head"),
            C((-2, 67.5, -1.5), (3.5, 1.5, 3.5), "head")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (5 * side, 56, 0), rotation=(4, 0, 4 * -side), cubes=[
                C((sx(4.1, 5.9), 37, -0.9), (1.8, 19.5, 1.8), "flesh")]),
            B(f"{name}Forearm", f"{name}Arm", (5 * side, 37.5, 0), rotation=(-6, 0, 0), cubes=[
                C((sx(4.2, 5.8), 21, -0.8), (1.6, 17, 1.6), "flesh"),
                C((sx(4, 6), 18, -1), (2, 3.5, 2), "flesh"),
                *[C((sx(4.05 + k * 0.6, 4.5 + k * 0.6), 13.5, -0.9 + k * 0.6), (0.45, 5, 0.45), "finger") for k in range(3)]]),
        ]
    return hd_model("geometry.hb.boiled_one", bones, bounds=(2.0, 5.0, (0, 2.3, 0)))


def paint(seed, m, **_):
    flesh = Ramp(colors=["#2a0405", "#480809", "#6a1010", "#8c1c18", "#ac2c22", "#c84230"])
    blister = Ramp(colors=["#8a3a26", "#b25a3a", "#d48660", "#e8aa84"])
    eye = glow("#ff1408", 95)
    socket = (0x10, 0x01, 0x01)

    def boiled(t, base=0.5):
        r = t.ridge(3.5, 0.05)
        if r > 0.7:
            return t.paint(flesh, base - 0.35, 0.1)  # splits
        if t.n2(1.6) > 0.8:
            return t.paint(blister, 0.5, 0.3)  # blisters
        return t.paint(flesh, base, 0.45, 1.8)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front" and t.fw == 12:
                for cx in (3, 8):
                    if abs(t.x - cx) <= 1 and 5 <= t.y <= 7:
                        return eye if abs(t.x - cx) == 0 and t.y == 6 else socket
                if 10 <= t.y <= 11 and 3 <= t.x <= 8:
                    return socket
            return boiled(t, 0.45)
        if tag in ("flesh", "joint"):
            return boiled(t, 0.5 if tag == "flesh" else 0.35)
        if tag == "rib":
            return t.paint(flesh, 0.85, 0.1)
        if tag == "finger":
            return boiled(t, 0.4 - 0.2 * t.v)
        return None

    return paint_model(m, seed, fn)
