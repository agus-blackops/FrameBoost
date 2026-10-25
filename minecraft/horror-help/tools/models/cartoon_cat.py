"""Cartoon Cat (Trevor Henderson).

A 1930s cartoon that got out. Pitch black and far too tall, three and a half
blocks of rubber-hose limbs that bend like noodles, a little round body, big
round feet, and white cartoon gloves with fat fingers on the ends of arms
that hang to the knees. The head is enormous, with pointed ears, a pale
muzzle, two huge white eyes with pie-cut pupils that never quite look at
the same thing, and a grin that runs from ear to ear, full of teeth that are
human, not cartoon. A thin tail curls up behind it."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Noodle legs, slightly bowed, and big round cartoon feet.
            B(f"{name}Leg", "root", (2 * side, 26, 0), rotation=(0, 0, 4 * -side), cubes=[
                C((sx(1.25, 2.75), 13.5, -0.75), (1.5, 13, 1.5), "hose")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 14, 0), rotation=(4, 0, 6 * side), cubes=[
                C((sx(1.25, 2.75), 2.5, -0.75), (1.5, 12, 1.5), "hose"),
                C((sx(0, 4), 0, -4.5), (4, 3, 6.5), "foot"),
                C((sx(0.25, 3.75), 0.5, -5.25), (3.5, 2.25, 1), "foot")]),
        ]
    bones += [
        B("body", "root", (0, 26, 0), cubes=[
            # A little pear of a body: round at the bottom, narrow on top.
            C((-3.5, 25, -3), (7, 6, 6), "black"),
            C((-2.5, 31, -2), (5, 6, 4), "black"),
            C((-2, 37, -1.5), (4, 3, 3), "black")]),
        B("neck", "body", (0, 40, 0), rotation=(-6, 0, 0), cubes=[
            C((-0.75, 39.5, -0.75), (1.5, 6, 1.5), "hose")]),
        B("head", "neck", (0, 45.5, 0), rotation=(6, 0, -8), cubes=[
            C((-6.5, 45.5, -5), (13, 10, 10), "black"),
            C((-5.5, 55.5, -4), (11, 1.5, 8), "black"),
            # The pale muzzle the grin is drawn on.
            C((-5, 45.75, -5.75), (10, 4.5, 1), "muzzle"),
            C((-1.25, 50, -6.25), (2.5, 1.75, 1.25), "nose"),
            # Pointed ears.
            *[C((sx * 6.5 - (3 - k) / 2 - (0 if sx < 0 else 0) + (-1 if sx < 0 else 1) * -1.25, 56.5 + k * 1.75, -1.5 + k * 0.25),
                (3 - k, 1.75, 2.5 - k * 0.5), "black") for sx in (-1, 1) for k in range(3)]]),
        # A thin tail curling up behind.
        B("tail1", "body", (0, 27, 3), rotation=(40, 0, 0), cubes=[C((-0.5, 27, 3), (1, 1, 7), "black")]),
        B("tail2", "tail1", (0, 27.5, 10), rotation=(30, 0, 0), cubes=[C((-0.5, 27, 10), (1, 1, 6), "black")]),
        B("tail3", "tail2", (0, 27.5, 16), rotation=(40, 0, 0), cubes=[C((-0.6, 27, 16), (1.2, 1.2, 4), "black")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (2.5 * side, 38.5, 0), rotation=(0, 0, 8 * -side), cubes=[
                C((sx(2.5, 4), 24, -0.75), (1.5, 15, 1.5), "hose")]),
            B(f"{name}Forearm", f"{name}Arm", (3.25 * side, 24.5, 0), rotation=(-10, 0, 4 * side), cubes=[
                C((sx(2.5, 4), 11, -0.75), (1.5, 14, 1.5), "hose")]),
            # Cartoon gloves: a cuff, a puffy palm and three fat fingers and a thumb.
            B(f"{name}Hand", f"{name}Forearm", (3.25 * side, 11, 0), cubes=[
                C((sx(1.5, 5), 10, -1.75), (3.5, 1.75, 3.5), "cuff"),
                C((sx(1.25, 5.25), 6, -2), (4, 4, 4), "glove"),
                *[C((sx(1.4 + k * 1.3, 2.5 + k * 1.3), 2.5, -1.25), (1.1, 3.75, 1.75), "glove") for k in range(3)],
                C((sx(4.75, 6), 6.5, -1.5), (1.25, 2.5, 1.5), "glove")]),
        ]
    return hd_model("geometry.hh.cartoon_cat", bones, bounds=(2.0, 4.0, (0, 1.8, 0)))


def paint(seed, m, **_):
    black = Ramp(colors=["#030304", "#08080a", "#0e0e12", "#16161b", "#202027"])
    pale = Ramp(colors=["#8c8476", "#a8a090", "#c2baa8", "#d8d0bc", "#e8e2d0"])
    glove = Ramp(colors=["#9a968c", "#b8b4aa", "#d2cec4", "#e6e2d8", "#f6f4ec"])
    white = (0xf2, 0xf0, 0xe8)
    pupil = (0x02, 0x02, 0x03)
    gum = Ramp(colors=["#3a0608", "#5a0e12", "#7a1a1e"])

    def eye(t, cx, cy, rx, ry, cut):
        """A tall white oval with a black pupil, a wedge cut out of it."""
        dx, dy = (t.x - cx) / rx, (t.y - cy) / ry
        if dx * dx + dy * dy > 1.0:
            return None
        px, py = t.x - cx, t.y - (cy + 1)
        if (px / (rx * 0.6)) ** 2 + (py / (ry * 0.55)) ** 2 <= 1.0:
            wedge = (px * cut > 0) and abs(py) < abs(px) * 0.7  # the pie-cut
            return white if wedge else pupil
        if dx * dx + dy * dy > 0.82:
            return (0x2a, 0x2a, 0x2a)
        return white

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "black" and f == "front" and t.fw >= 24:
            # The two huge eyes on the front of the head (26x20 design px).
            for cx, cut in ((7.0, 1), (19.0, -1)):
                c = eye(t, cx, 6.0, 5.5, 6.0, cut)
                if c is not None:
                    return c
            return t.paint(black, 0.45, 0.25)
        if tag == "muzzle":
            if f != "front":
                return t.paint(pale, 0.45, 0.2)
            # A grin of human teeth from one side of the muzzle to the other.
            if t.y in (2, 7):
                return pupil
            if 3 <= t.y <= 6:
                if t.y in (4, 5) and t.x % 3 == 0:
                    return pupil  # the gaps between teeth
                if t.y == 3 and t.x % 6 == 1:
                    return t.paint(gum, 0.6, 0.1)
                return t.paint(TEETH, 0.65 - (0.1 if t.y >= 5 else 0), 0.2)
            return t.paint(pale, 0.6, 0.2)
        if tag == "nose":
            return t.paint(black, 0.7, 0.1)
        if tag in ("black", "hose", "foot"):
            # Old black ink, with a faint printed sheen.
            if t.side and t.y % 9 == 0 and t.n(2.0) > 0.6:
                return t.paint(black, 0.75, 0.1)
            return t.paint(black, 0.45 if tag != "foot" else 0.55, 0.25)
        if tag in ("glove", "cuff"):
            if tag == "glove" and t.side and t.x % 4 == 2 and t.y < t.fh // 2 and f == "back":
                return t.paint(black, 0.5, 0.1)  # the three stitched lines on the back
            return t.paint(glove, 0.62 if tag == "glove" else 0.45, 0.2)
        return None

    return paint_model(m, seed, fn)
