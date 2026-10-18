"""The False Tree.

It lives in the jungle and in the taiga and it looks like another trunk:
tall, thin and dark, and it stands among the trees until you look at it
properly. Then it straightens up. Nearly four blocks of long black limbs,
twigs growing all down its legs and round its raised forearm, a body that
is just three boxes, one arm hanging to the knee and the other lifted, hand
open, as if it were waving. The head is a flat white plate far wider than
the body, with a stepped crown of three points, two fat crosses for eyes
and a wide grin of square teeth.

While it is hiding (`hl:revealed` false) only the `trunk` bone is drawn: a
thin dead tree with a few leaves, the way it looks from far away."""

from models.kit import *

PREVIEW = {"trunk": {"scale": (0, 0, 0)}}


def sprig(x, y, z, sd, ln, along="x", tag="twig"):
    """One pixel twig, the way they grow on it: a stalk sticking out sideways
    with a nub up and a nub down at its end, and from its middle a second,
    shorter stalk one step higher with its own nub, like a little fern."""
    w = 0.5

    def stalk(x, y, z, ln):
        if along == "x":
            x0 = x if sd > 0 else x - ln
            return C((x0, y, z), (ln, w, w), tag), (x + sd * ln - (w if sd > 0 else 0), y, z)
        z0 = z if sd > 0 else z - ln
        return C((x, y, z0), (w, w, ln), tag), (x, y, z + sd * ln - (w if sd > 0 else 0))

    main, tip = stalk(x, y, z, ln)
    cubes = [main, C((tip[0], y + w, tip[2]), (w, 1.25, w), tag), C((tip[0], y - 1, tip[2]), (w, 1, w), tag)]
    if ln >= 3:
        mx = x + sd * ln * 0.45 if along == "x" else x
        mz = z + sd * ln * 0.45 if along != "x" else z
        cubes.append(C((mx, y + w, mz), (w, 0.75, w), tag))
        second, tip2 = stalk(mx, y + 1.25, mz, ln * 0.55)
        cubes += [second, C((tip2[0], y + 1.75, tip2[2]), (w, 0.75, w), tag)]
    return cubes


def leg_twigs(cx, y0, y1, seed, r):
    """Twigs all the way down a leg, alternating sides, a few towards the
    front and back."""
    out = []
    lens = (3, 4.5, 2.5, 4, 3.5, 5, 3)
    y, k = y0, seed
    while y < y1:
        sd = 1 if k % 2 == 0 else -1
        ln = lens[k % len(lens)]
        if k % 4 == 3:
            out += sprig(cx - 0.25, y, sd * r, sd, ln * 0.7, "z")
        else:
            out += sprig(cx + sd * r, y, -0.25, sd, ln, "x")
        y += 2.2 + (k % 3) * 0.4
        k += 1
    return out


def model():
    bones = [B("root", None, (0, 0, 0))]
    # Legs: long and very thin, with twigs all the way down both of them.
    for side, name in ((-1, "right"), (1, "left")):
        cx = side * 1.75  # centre of the leg
        bones += [
            B(f"{name}Leg", "root", (cx, 31.5, 0), cubes=[
                C((cx - 0.75, 16, -0.75), (1.5, 15.5, 1.5), "leg"),
                *leg_twigs(cx, 17, 30, 0 if side < 0 else 1, 0.75)]),
            B(f"{name}Shin", f"{name}Leg", (cx, 16, 0), cubes=[
                C((cx - 0.6, 1, -0.6), (1.25, 15, 1.25), "leg"),
                C((cx - 0.75, 15, -0.85), (1.5, 1.5, 1.5), "knee"),
                C((cx - 0.75, 0, -2.75), (1.5, 1, 3.5), "foot"),
                *leg_twigs(cx, 2.5, 14.5, 2 if side < 0 else 3, 0.6)]),
        ]
    bones += [
        # The body is three plain boxes: hips, a narrower waist, a tall chest.
        B("body", "root", (0, 31.5, 0), cubes=[
            C((-3.25, 31.5, -1.75), (6.5, 3.5, 3.5), "hips")]),
        B("chest", "body", (0, 35, 0), cubes=[
            C((-2.75, 35, -1.5), (5.5, 6, 3), "waist"),
            C((-3.25, 41, -1.75), (6.5, 7.5, 3.5), "torso")]),
        B("neck", "chest", (0, 48.5, 0), cubes=[C((-0.75, 48.5, -0.75), (1.5, 2.5, 1.5), "neck")]),
        # The head: a big flat white plate, much wider than the body, with a
        # stepped crown of three points along the top.
        B("head", "neck", (0, 51, 0), rotation=(-8, 10, -6), cubes=[
            C((-6.5, 51, -1.25), (13, 8.5, 2.5), "head"),
            # Three points, each in steps, the outer two leaning outwards.
            C((-6.5, 59.5, -1.25), (3, 2, 2.5), "crown"), C((-7.25, 61.5, -1.25), (2.5, 1.5, 2.5), "crown"),
            C((-7.75, 63, -1.25), (1.5, 1.25, 2.5), "crown"),
            C((-2, 59.5, -1.25), (4, 2.5, 2.5), "crown"), C((-1.5, 62, -1.25), (3, 1.5, 2.5), "crown"),
            C((-0.75, 63.5, -1.25), (1.5, 1.5, 2.5), "crown"),
            C((3.5, 59.5, -1.25), (3, 2, 2.5), "crown"), C((4.75, 61.5, -1.25), (2.5, 1.5, 2.5), "crown"),
            C((6.25, 63, -1.25), (1.5, 1.25, 2.5), "crown"),
            C((6.5, 56.5, -1.25), (1, 1.5, 2.5), "crown"), C((-7.5, 55, -1.25), (1, 1.5, 2.5), "crown")]),
        # Its right arm hangs to the knee, long fingers curled.
        B("rightArm", "chest", (-3.75, 48, 0), rotation=(0, 0, 9), cubes=[
            C((-4.5, 32.5, -0.75), (1.5, 15.5, 1.5), "arm")]),
        B("rightForearm", "rightArm", (-3.75, 33, 0), rotation=(-8, 0, -4), cubes=[
            C((-4.4, 20, -0.65), (1.3, 13, 1.3), "arm"),
            C((-4.5, 32, -0.75), (1.5, 1.5, 1.5), "knee")]),
        B("rightHand", "rightForearm", (-3.75, 20, 0), cubes=[
            C((-4.75, 17.5, -1), (2, 2.5, 2), "hand"),
            *[C((-5.25 + k * 0.75, 7.5 + (k in (0, 3)) * 1.5, -0.4), (0.55, 10 - (k in (0, 3)) * 1.5, 0.55), "finger",
                pivot=(-4.75 + k * 0.75, 17.5, 0), rotation=(-12 + 4 * k, 0, (k - 1.5) * 13)) for k in range(4)]]),
        # Its left arm is raised: the upper arm out from the shoulder, the
        # forearm up, the hand open, twigs growing all round the forearm.
        B("leftArm", "chest", (3.75, 48, 0), rotation=(0, 0, -30), cubes=[
            C((3, 32.5, -0.75), (1.5, 15.5, 1.5), "arm")]),
        B("leftForearm", "leftArm", (3.75, 33, 0), rotation=(0, 0, -135), cubes=[
            C((3.1, 20, -0.65), (1.3, 13, 1.3), "arm"),
            C((3, 32, -0.75), (1.5, 1.5, 1.5), "knee"),
            *[c for y, sd, ln, ax in ((20.5, 1, 3, "x"), (21.5, -1, 4, "x"), (23, 1, 5, "x"), (24, -1, 3, "x"),
                                      (25.5, 1, 4, "x"), (26.5, -1, 5, "x"), (28, 1, 3.5, "x"), (29.5, -1, 3, "x"),
                                      (31, 1, 2.5, "x"), (22, 1, 3, "z"), (24.5, -1, 3.5, "z"), (27, 1, 3, "z"),
                                      (29, -1, 2.5, "z"))
              for c in (sprig(3.75 + sd * 0.65, y, -0.25, sd, ln, "x") if ax == "x" else sprig(3.5, y, sd * 0.65, sd, ln, "z"))]]),
        B("leftHand", "leftForearm", (3.75, 20, 0), cubes=[
            C((2.5, 17, -1.1), (2.5, 3, 2.2), "hand"),
            *[C((2.6 + k * 0.85, 8, -0.4), (0.6, 9, 0.6), "finger",
                pivot=(2.9 + k * 0.85, 17, 0), rotation=(0, 0, (k - 1) * 16)) for k in range(3)],
            C((2.3, 13.5, -0.4), (0.6, 4, 0.6), "finger", pivot=(2.6, 17, 0), rotation=(0, 0, -40)),
            *sprig(3.75, 16, -0.25, 1, 3), *sprig(3.75, 14, -0.25, -1, 2.5)]),
    ]
    # What it looks like while it hides: a thin trunk with a few leaves.
    bones.append(B("trunk", "root", (0, 0, 0), cubes=[
        C((-2, 0, -2), (4, 52, 4), "trunk"),
        C((-3, 0, -3), (6, 3, 6), "trunk_base"),
        C((-5, 36, -1), (3, 1, 1), "trunk_branch"), C((2, 42, -1), (4, 1, 1), "trunk_branch"),
        C((-4, 47, 0), (2, 1, 1), "trunk_branch"),
        *[C((x, y, z), (4, 3, 4), "leaf") for x, y, z in ((-7.5, 35, -1.5), (5.5, 41, -1.5), (-5.5, 46, -0.5),
                                                          (-2.5, 52, -2.5), (0.5, 49, 1))]]))
    return hd_model("geometry.hl.false_tree", bones, bounds=(2.8, 4.2, (0, 1.9, 0)))


def paint(seed, m, **_):
    ink = Ramp(colors=["#0c0e14", "#13161e", "#1a1e28", "#232834", "#2e3442", "#3c4352"])
    white = Ramp(colors=["#9a9ba2", "#bcbdc4", "#d8d9de", "#ececf0", "#fafafc"])
    bark = Ramp(colors=["#14100c", "#221a12", "#34281a", "#483826", "#5e4a32"])
    moss = Ramp(colors=["#10200e", "#1c3418", "#2a4a22", "#3c602c"])
    leaf = Ramp(colors=["#0e2610", "#173a1a", "#235426", "#32703a"])
    black = (0x0A, 0x0B, 0x10)
    rim = (0xA8, 0xA9, 0xB0)

    def eye(t, cx, cy):
        """A thin cross."""
        dx, dy = t.x - cx, t.y - cy
        return (dx == 0 and abs(dy) <= 1) or (dy == 0 and abs(dx) <= 1)

    # The grin (on the 26 x 17 front of the plate): the corners turned right
    # up, a straight top with a row of square teeth, a black line, a lower
    # row of teeth, and a rounded bottom.
    grin = {
        7: ".....#..............#.....",
        8: ".....##............##.....",
        9: "......##############......",
        10: "......#WW#WW#WW#WW##......",
        11: "......#WW#WW#WW#WW##......",
        12: "......##############......",
        13: ".......#WW#WW#WW#W#.......",
        14: "........##########........",
    }

    def mouth(t):
        row = grin.get(t.y)
        if not row or t.x >= len(row):
            return None
        ch = row[t.x]
        return ch if ch in "#W" else None

    def dark(t, base=0.4):
        if t.n2(2.5) > 0.9:
            return t.paint(ink, base + 0.18, 0.08)  # a dull sheen
        return t.paint(ink, base, 0.18, 3.0)

    def wood(t, base=0.45):
        if t.ridge(2.0, 0.07) > 0.5:
            return t.paint(bark, base - 0.2, 0.1)
        return t.paint(bark, base, 0.35, 3.0)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                if t.x in (0, t.fw - 1) or t.y in (0, t.fh - 1):
                    return rim  # the grey edge round the plate
                if eye(t, 9, 4) or eye(t, 16, 4):
                    return black
                ch = mouth(t)
                if ch == "#":
                    return black
                if ch == "W":
                    return t.paint(white, 0.8, 0.05, 4.0)
                return t.paint(white, 0.72, 0.08, 4.0)
            if f == "back":
                return t.paint(white, 0.55, 0.1, 4.0)
            return t.paint(white, 0.3 + 0.15 * (f == "top"), 0.1, 3.0)
        if tag == "crown":
            if f == "front":
                return rim if (t.x in (0, t.fw - 1) or t.y == 0) else t.paint(white, 0.72, 0.08, 4.0)
            return t.paint(white, 0.32 + 0.15 * (f == "top"), 0.1, 3.0)
        if tag in ("hips", "waist", "torso", "neck", "leg", "arm", "hand", "knee"):
            return dark(t, {"torso": 0.36, "hips": 0.3, "waist": 0.28, "knee": 0.45}.get(tag, 0.4))
        if tag == "foot":
            return dark(t, 0.3)
        if tag == "finger":
            return dark(t, 0.42)
        if tag == "twig":
            return t.paint(ink, 0.36 + 0.12 * (f in ("top", "front")), 0.1)
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
