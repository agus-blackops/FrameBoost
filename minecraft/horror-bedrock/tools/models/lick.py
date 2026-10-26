"""The Lick.

A figure in black standing perfectly still at the back of a dark cave,
facing you. A man's shape, thin, in a long black coat, black gloves, black
boots, so that in the dark all you can really see is the mask: a smooth
white face like old porcelain, two black eye holes, a wide painted smile,
and out of the smile a long tongue, too long and too red, hanging down
over the chin. Around where it waits, the cave floor is marked with
crosses."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2 * side, 14, 0), cubes=[
                C((sx(0.25, 3.75), 7, -1.75), (3.5, 7.5, 3.5), "coat")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 7, 0), cubes=[
                C((sx(0.4, 3.6), 1.5, -1.6), (3.2, 6, 3.2), "black"),
                C((sx(0.25, 3.75), 0, -2.6), (3.5, 2, 4.5), "boot")]),
        ]
    bones += [
        B("body", "root", (0, 14, 0), cubes=[
            C((-4, 14, -2), (8, 13, 4), "coat"),
            # The long coat, down past the knees, and its collar turned up.
            C((-4.4, 6, -2.4), (8.8, 9, 4.8), "coat_tail"),
            C((-4.5, 25, -2.5), (9, 3, 5), "collar")]),
        B("head", "body", (0, 27, 0), cubes=[
            C((-3.75, 27, -3.75), (7.5, 8, 7.5), "black"),
            C((-4, 33.5, -4), (8, 2, 8), "hood"),
            C((-3.5, 27.25, -4.25), (7, 7.5, 0.5), "mask")]),
        # The tongue, hanging out of the smile, swinging.
        B("tongue", "head", (0, 29.5, -4.5), rotation=(-8, 0, 0), cubes=[
            C((-1, 25, -4.75), (2, 5, 0.6), "tongue"),
            C((-0.75, 23.5, -4.6), (1.5, 1.75, 0.5), "tongue")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (5 * side, 26, 0), rotation=(0, 0, 2 * -side), cubes=[
                C((sx(4, 7), 18, -1.5), (3, 8.5, 3), "coat")]),
            B(f"{name}Forearm", f"{name}Arm", (5.5 * side, 18.5, 0), cubes=[
                C((sx(4.1, 6.9), 11, -1.4), (2.8, 8, 2.8), "coat"),
                C((sx(4.2, 6.8), 8, -1.3), (2.6, 3.5, 2.6), "glove")]),
        ]
    return hd_model("geometry.hb.lick", bones, bounds=(1.6, 2.6, (0, 1.1, 0)))


def paint(seed, m, **_):
    black = Ramp(colors=["#030304", "#08080a", "#0e0e11", "#15151a", "#1e1e24"])
    porcelain = Ramp(colors=["#9e9a92", "#bab6ac", "#d2cec4", "#e4e0d6", "#f2eee6", "#fbf9f4"])
    tongue = Ramp(colors=["#4a0810", "#701018", "#961c24", "#b82c30", "#d04a46"])
    hole = (0x00, 0x00, 0x00)
    paint_red = (0x9a, 0x14, 0x1c)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "mask":
            if f != "front":
                return t.paint(porcelain, 0.4, 0.1)
            # 14x15: two tall black holes that slant down at the middle, a
            # thin painted smile from cheek to cheek, cracks.
            mask = {
                2: "..####..####..",
                3: ".#####..#####.",
                4: ".######.#####.",
                5: ".#####...####.",
                6: "..###.....##..",
                9: "r............r",
                10: ".rr........rr.",
                11: "...rrrHHHHrr..",
            }
            c = mask.get(t.y, "")[t.x:t.x + 1]
            if c == "#" or c == "H":
                return hole
            if c == "r":
                return paint_red
            if t.ridge(3.0, 0.03) > 0.82:
                return t.paint(porcelain, 0.25, 0.05)  # hairline cracks
            return t.paint(porcelain, 0.68, 0.12, 2.5)
        if tag == "tongue":
            if f == "front" and t.x in (0, t.fw - 1):
                return t.paint(tongue, 0.3, 0.1)
            return t.paint(tongue, 0.55 + 0.15 * (1 - t.v), 0.3)
        if tag in ("black", "hood", "glove", "boot"):
            return t.paint(black, 0.5 if tag != "boot" else 0.6, 0.3)
        if tag in ("coat", "coat_tail", "collar"):
            if tag == "coat_tail" and f in ("top", "bottom"):
                return CLEAR
            if tag == "coat" and f == "front" and t.fw >= 14 and t.x == t.fw // 2 and t.y % 4 == 1:
                return t.paint(IRON, 0.35, 0.1)  # buttons
            return t.paint(black, 0.45 if tag != "collar" else 0.3, 0.35, 2.0)
        return None

    return paint_model(m, seed, fn)
