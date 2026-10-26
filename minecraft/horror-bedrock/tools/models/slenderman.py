"""Slenderman.

Very tall, very thin, standing among the trees at the edge of what you can
see. A black suit that fits too well, a white shirt, a black tie, black
shoes. Arms far too long. And no face: just a smooth white blank where a
face should be. From behind his back, when he wants them to, come long black
tendrils, four of them, moving slowly in the air (`tendril*` bones, folded
away by the animations until he is close)."""

from models.kit import *

PREVIEW = {}


def tendril(n, side, up):
    """A chain of four segments out of the back, each its own bone."""
    bones = []
    parent = "body"
    x0, y0, z = side * 2, 46 + up * 4, 2
    for k in range(4):
        name = f"tendril{n}_{k}"
        y = y0 + k * 6
        bones.append(B(name, parent, (x0 + side * k * 3, y, z + k), rotation=(-20, 0, side * -25),
                       cubes=[C((x0 + side * k * 3 - 0.6, y, z + k - 0.6), (1.2 - k * 0.15, 6.5, 1.2 - k * 0.15), "black")]))
        parent = name
    return bones


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2 * side, 30, 0), cubes=[
                C((sx(0.25, 3.25), 15, -1.5), (3, 15.5, 3), "suit")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 15, 0), cubes=[
                C((sx(0.35, 3.15), 1.5, -1.4), (2.8, 13.5, 2.8), "suit"),
                C((sx(0.1, 3.4), 0, -2.75), (3.3, 1.75, 4.5), "shoe")]),
        ]
    bones += [
        B("body", "root", (0, 30, 0), cubes=[
            C((-4, 30, -2), (8, 17, 4), "jacket"),
            C((-1.5, 39, -2.2), (3, 8, 0.3), "shirt"),
            C((-0.5, 40, -2.4), (1, 7, 0.3), "tie"),
            C((-4.25, 29, -2.25), (8.5, 4, 4.5), "jacket")]),
        B("head", "body", (0, 47, 0), cubes=[
            C((-0.9, 47, -0.9), (1.8, 2, 1.8), "skin"),
            C((-3.25, 49, -3.25), (6.5, 9, 6.5), "skin")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (5 * side, 46, 0), cubes=[
                C((sx(4, 6.75), 30, -1.4), (2.75, 16.5, 2.8), "jacket")]),
            B(f"{name}Forearm", f"{name}Arm", (5.4 * side, 30.5, 0), cubes=[
                C((sx(4.1, 6.65), 15, -1.3), (2.55, 15.5, 2.6), "jacket"),
                C((sx(4.2, 6.55), 14, -1.2), (2.35, 1.5, 2.4), "shirt"),
                C((sx(4.25, 6.5), 10, -1.1), (2.25, 4, 2.2), "skin"),
                *[C((sx(4.3 + k * 0.6, 4.75 + k * 0.6), 5.5, -0.9 + k * 0.55), (0.45, 4.5, 0.45), "skin") for k in range(4)]]),
        ]
    for n, (side, up) in enumerate(((-1, 0), (1, 0), (-1, 1), (1, 1))):
        bones += tendril(n, side, up)
    return hd_model("geometry.hb.slenderman", bones, bounds=(3.0, 4.6, (0, 2.0, 0)))


def paint(seed, m, **_):
    suit = Ramp(colors=["#050506", "#0b0b0e", "#121216", "#1a1a20", "#24242c"])
    white = Ramp(colors=["#a8a8a6", "#c4c4c0", "#dcdcd6", "#ececE6".lower(), "#f8f8f4"])
    tie = Ramp(colors=["#2a0204", "#420608", "#5e0c10"])

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "skin":
            # Blank: no eyes, no mouth, just the faintest shadow of a brow.
            if f == "front" and t.fw >= 12 and t.y == 5 and 2 <= t.x <= 10:
                return t.paint(white, 0.5, 0.05)
            return t.paint(white, 0.7, 0.1, 3.0)
        if tag == "shirt":
            return t.paint(white, 0.75, 0.05)
        if tag == "tie":
            return t.paint(tie, 0.5, 0.2)
        if tag in ("jacket", "suit"):
            if tag == "jacket" and f == "front" and t.fw >= 14 and t.x in (5, t.fw - 6) and t.y < 16:
                return t.paint(suit, 0.15, 0.05)  # lapels
            return t.paint(suit, 0.45, 0.2, 2.0)
        if tag == "shoe":
            return t.paint(suit, 0.75, 0.1)
        if tag == "black":
            return t.paint(suit, 0.3, 0.3)
        return None

    return paint_model(m, seed, fn)
