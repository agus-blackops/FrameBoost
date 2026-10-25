"""Flowers (פרחים), the Whistler (VibingLeaf's The Whistle Occurrence).

What came out of a corrupted game: a Roblox R15 avatar, stretched. Every one
of the fifteen blocky parts is there (the round head, the two halves of the
torso, upper and lower arms and legs, hands and feet), but pulled out far
too long, so it stands five blocks tall on legs like stilts with arms down
to its shins. It is black all over, the flat black of a character whose
texture failed to load, and it has no face at all. Now and then the black
glitches: a stripe of missing-texture colour where a part didn't load.

It can stretch further (the `stretch` animation pulls it up and up)."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}UpperLeg", "root", (2 * side, 40, 0), cubes=[
                C((sx(0, 4), 21, -1.75), (4, 19.5, 3.5), "part")]),
            B(f"{name}LowerLeg", f"{name}UpperLeg", (2 * side, 21, 0), cubes=[
                C((sx(0.25, 3.75), 2, -1.5), (3.5, 19, 3), "part")]),
            B(f"{name}Foot", f"{name}LowerLeg", (2 * side, 2, 0), cubes=[
                C((sx(0, 4), 0, -2.25), (4, 2, 4.5), "part")]),
        ]
    bones += [
        B("lowerTorso", "root", (0, 40, 0), cubes=[
            C((-4, 39.5, -2), (8, 3.5, 4), "part")]),
        B("upperTorso", "lowerTorso", (0, 43, 0), cubes=[
            C((-4, 43, -2), (8, 12, 4), "part"),
            C((-4.25, 52.5, -2.25), (8.5, 2.5, 4.5), "part")]),
        # The neck stretched out, the round Roblox head on top, no face.
        B("neck", "upperTorso", (0, 55, 0), cubes=[
            C((-1, 55, -1), (2, 6, 2), "part")]),
        B("head", "neck", (0, 61, 0), rotation=(0, 0, 8), cubes=[
            C((-3, 61, -2.5), (6, 5, 5), "part"),
            C((-2.5, 60.5, -3), (5, 6, 6), "part"),
            C((-2, 66, -2), (4, 0.75, 4), "part")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}UpperArm", "upperTorso", (5 * side, 53.5, 0), rotation=(0, 0, 3 * -side), cubes=[
                C((sx(4, 7.5), 34, -1.5), (3.5, 21, 3), "part")]),
            B(f"{name}LowerArm", f"{name}UpperArm", (5.75 * side, 34.5, 0), cubes=[
                C((sx(4.25, 7.25), 15, -1.25), (3, 20, 2.5), "part")]),
            B(f"{name}Hand", f"{name}LowerArm", (5.75 * side, 15, 0), cubes=[
                C((sx(4, 7.5), 11.5, -1.5), (3.5, 4, 3), "part")]),
        ]
    return hd_model("geometry.hh.flowers", bones, bounds=(2.0, 6.0, (0, 2.8, 0)))


def paint(seed, m, **_):
    black = Ramp(colors=["#010102", "#040406", "#08080b", "#0d0d11", "#131318"])
    missing = [(0xf8, 0x00, 0xf8), (0x00, 0x00, 0x00)]  # the missing-texture checkerboard

    def fn(t):
        # Each part: flat black, the edges a touch lighter like Roblox's
        # plastic, and once in a while a band where the texture never loaded.
        if t.n2(1.0) > 0.86 and t.side:
            return missing[(t.x // 2 + t.y // 2) % 2]
        edge = t.x == 0 or t.y == 0 or t.x == t.fw - 1 or t.y == t.fh - 1
        return t.paint(black, 0.6 if edge else 0.42, 0.18, 1.5)

    return paint_model(m, seed, fn)
