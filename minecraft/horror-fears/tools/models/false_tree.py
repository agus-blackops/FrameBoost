"""The False Tree.

It lives in the jungle and in the taiga and it looks like another trunk:
tall, thin and dark, and it stands among the trees until you look at it
properly. Then it straightens up. Nearly four blocks of long black limbs
with twigs growing out of them, a body that is just boxes and sticks, arms
that go past the knees, and hands with fingers a forearm long. The head is
a flat white plate with a crown of points and a face drawn on it like a
child's: two crosses for eyes and a huge grid-toothed grin. The plate floats
a little above the neck and never quite faces you.

While it is hiding (`hl:revealed` false) only the `trunk` bone is drawn: a
thin dead tree with a few leaves, the way it looks from far away."""

from models.kit import *

PREVIEW = {"trunk": {"scale": (0, 0, 0)}}


def twigs(x0, y0, z0, side, n, up, seed):
    """A little branching growth: a stalk with side twigs, pointing outwards."""
    cubes = []
    for k in range(n):
        y = y0 + k * up
        sd = side if (k + seed) % 2 == 0 else -side
        ln = 2.5 + ((k * 7 + seed * 3) % 4)
        cubes.append(C((x0 + (0 if sd > 0 else -ln), y, z0 + ((k + seed) % 3 - 1) * 0.5), (ln, 0.5, 0.5), "twig",
                       pivot=(x0, y, z0), rotation=(0, 0, sd * (-14 + 10 * (k % 3)))))
        if ln > 3.4:
            cubes.append(C((x0 + sd * (ln - 1) - 0.25, y + 0.5, z0 - 0.25), (0.5, 2, 0.5), "twig"))
    return cubes


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (1.75 * side, 29, 0), cubes=[
                C((sx(0.5, 3), 14, -1), (2.5, 15, 2), "leg"),
                *twigs(side * 2.5, 16, 0.5, side, 5, 2.4, 1 if side > 0 else 2)]),
            B(f"{name}Shin", f"{name}Leg", (1.75 * side, 14, 0), cubes=[
                C((sx(0.75, 2.75), 2, -0.75), (2, 12, 1.5), "leg"),
                C((sx(0.5, 3), 0, -3.5), (2.5, 1.5, 4.5), "foot"),
                *twigs(side * 2.5, 3, 0.5, side, 4, 2.6, 3 if side > 0 else 4)]),
        ]
    bones += [
        B("body", "root", (0, 29, 0), cubes=[
            C((-3, 29, -1.5), (6, 4, 3), "hips")]),
        B("chest", "body", (0, 33, 0), cubes=[
            C((-3.5, 33, -1.75), (7, 12, 3.5), "torso"),
            C((-4.25, 44, -2), (8.5, 2.5, 4), "shoulders")]),
        B("neck", "chest", (0, 46, 0), cubes=[C((-1, 46, -1), (2, 4, 2), "neck")]),
        B("head", "neck", (0, 50, 0), rotation=(0, 0, 3), cubes=[
            # The plate: flat and wide, thicker than a sign.
            C((-4.5, 50.5, -1.75), (9, 7.5, 3.5), "head"),
            # A crown of points along the top.
            C((-4.5, 58, -1.75), (2.5, 2.5, 3.5), "crown"), C((-1.25, 58, -1.75), (2.5, 3.5, 3.5), "crown"),
            C((2, 58, -1.75), (2.5, 2.5, 3.5), "crown"),
            C((-5, 58.5, -1), (1, 3, 2), "crown"), C((4, 58.5, -1), (1, 3, 2), "crown"),
            C((-0.25, 61.5, -0.75), (1.5, 1.5, 1.5), "crown"),
            *twigs(4.5, 52, 0, 1, 2, 3, 5)]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "chest", (4.5 * side, 44, 0), rotation=(0, 0, 3 * -side), cubes=[
                C((sx(3.5, 5.5), 28, -1), (2, 16.5, 2), "arm"),
                *twigs(side * 5.5, 30, 0.5, side, 5, 2.8, 6 if side > 0 else 7)]),
            B(f"{name}Forearm", f"{name}Arm", (4.5 * side, 28, 0), cubes=[
                C((sx(3.75, 5.25), 13, -0.75), (1.5, 15.5, 1.5), "arm"),
                *twigs(side * 5.25, 15, 0, side, 5, 2.6, 8 if side > 0 else 9)]),
            B(f"{name}Hand", f"{name}Forearm", (4.5 * side, 13, 0), cubes=[
                C((sx(3.5, 5.75), 10.5, -1), (2.25, 2.5, 2), "hand"),
                *[C((sx(3.5 + k * 0.7, 4.1 + k * 0.7), 2.5, -0.5 + (k % 2) * 0.25), (0.6, 8, 0.6), "finger",
                    pivot=(side * (3.8 + k * 0.7), 10.5, 0), rotation=(-6 + 5 * k, 0, side * (k - 1.5) * 5))
                  for k in range(4)]]),
        ]
    # What it looks like while it hides: a thin trunk with a few leaves.
    bones.append(B("trunk", "root", (0, 0, 0), cubes=[
        C((-2, 0, -2), (4, 52, 4), "trunk"),
        C((-3, 0, -3), (6, 3, 6), "trunk_base"),
        C((-5, 36, -1), (3, 1, 1), "trunk_branch"), C((2, 42, -1), (4, 1, 1), "trunk_branch"),
        C((-4, 47, 0), (2, 1, 1), "trunk_branch"),
        *[C((x, y, z), (4, 3, 4), "leaf") for x, y, z in ((-7.5, 35, -1.5), (5.5, 41, -1.5), (-5.5, 46, -0.5),
                                                          (-2.5, 52, -2.5), (0.5, 49, 1))]]))
    return hd_model("geometry.hl.false_tree", bones, bounds=(2.5, 4.2, (0, 1.9, 0)))


def paint(seed, m, **_):
    ink = Ramp(colors=["#04050a", "#0a0c14", "#12161f", "#1c222e", "#283040", "#364054"])
    white = Ramp(colors=["#7e8088", "#a6a8b0", "#cacbd2", "#e2e3e8", "#f4f4f8"])
    bark = Ramp(colors=["#14100c", "#221a12", "#34281a", "#483826", "#5e4a32"])
    moss = Ramp(colors=["#10200e", "#1c3418", "#2a4a22", "#3c602c"])
    leaf = Ramp(colors=["#0e2610", "#173a1a", "#235426", "#32703a"])
    black = (0x06, 0x07, 0x0C)

    # Its face, as drawn on the plate (18 wide, 15 tall on the front): two
    # crosses for eyes and a grid-toothed grin that nearly reaches the ears.
    face = [
        "..................",
        "..................",
        "....#.......#.....",
        "...###.....###....",
        "....#.......#.....",
        "..................",
        "..................",
        ".##############...",
        ".#.#.#.#.#.#.#.#..",
        "..##############..",
        "...#.#.#.#.#.#.#..",
        "....############..",
        "..................",
        "..................",
        "..................",
    ]

    def wood(t, base=0.45):
        if t.ridge(2.0, 0.07) > 0.5:
            return t.paint(bark, base - 0.2, 0.1)
        return t.paint(bark, base, 0.35, 3.0)

    def dark(t, base=0.4):
        if t.n2(2.5) > 0.88:
            return t.paint(ink, base + 0.22, 0.1)  # a sheen
        return t.paint(ink, base, 0.25, 2.5)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                ch = face[t.y][t.x] if t.y < len(face) and t.x < len(face[t.y]) else "."
                if ch == "#":
                    return black
                return t.paint(white, 0.72, 0.1, 4.0)
            if f in ("right", "left", "top", "bottom"):
                return t.paint(white, 0.45, 0.15, 3.0)
            return t.paint(white, 0.6, 0.12, 4.0)
        if tag == "crown":
            return t.paint(white, 0.62 + 0.1 * (f == "top"), 0.12, 3.0)
        if tag in ("hips", "torso", "shoulders", "neck", "leg", "arm", "hand"):
            return dark(t, {"torso": 0.32, "hips": 0.28}.get(tag, 0.38))
        if tag == "foot":
            return dark(t, 0.25)
        if tag == "finger":
            return t.paint(ink, 0.5 - 0.35 * t.v, 0.12)
        if tag == "twig":
            return t.paint(ink, 0.45 + 0.1 * t.n(2), 0.15)
        if tag in ("trunk", "trunk_base", "trunk_branch"):
            if tag == "trunk" and t.side and t.v > 0.82:
                return t.paint(moss, 0.5, 0.4)
            if tag == "trunk_base":
                return t.paint(moss, 0.4, 0.4) if t.n(2) > 0.4 else wood(t, 0.3)
            return wood(t, 0.42)
        if tag == "leaf":
            if t.n2(1.5) > 0.82:
                return CLEAR
            return t.paint(leaf, 0.5, 0.4, 1.5)
        return None

    return paint_model(m, seed, fn)
