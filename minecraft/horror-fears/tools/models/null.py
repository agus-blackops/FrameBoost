"""null.

A player the game could not draw. Too tall and too thin, black as an empty
render, and torn into horizontal slices that don't line up, like a picture
that lost its sync: every slice is shifted a little and fringed red on one
side and cyan on the other. Two white eyes sit just under the tear through
the head, a blue line staring out of them. The left forearm has come loose
and floats below the elbow, wearing the missing texture. Around him, the
debug hitbox you'd see with F3+B and a halo of stray pixels. "null" is
written faintly across the chest."""

from models.kit import *

PREVIEW = {}


def model():
    px = lambda x, y, z: C((x, y, z), (1, 1, 1), "px")
    edge = lambda o, s, tag="hitbox": C(o, s, tag)
    box = [edge((x, 0, z), (0.5, 53, 0.5)) for x in (-5, 4.5) for z in (-5, 4.5)]
    for y, tag in ((0, "hitbox"), (52.5, "hitbox"), (46, "eyeline")):
        box += [edge((-5, y, z), (10, 0.5, 0.5), tag) for z in (-5, 4.5)]
        box += [edge((x, y, -4.5), (0.5, 0.5, 9), tag) for x in (-5, 4.5)]
    return hd_model("geometry.hl.null", [
        B("root", None, (0, 0, 0)),
        B("hitbox", "root", (0, 0, 0), cubes=box),
        B("rightLeg", "root", (-2, 22, 0), cubes=[
            C((-3.5, 0, -1.5), (3, 7.5, 3), "slice_leg"), C((-3, 7.5, -1.5), (3, 7, 3), "slice_leg2"),
            C((-4, 14.5, -1.5), (3, 7.5, 3), "slice_leg")]),
        B("leftLeg", "root", (2, 22, 0), cubes=[
            C((0.5, 0, -1.5), (3, 7.5, 3), "slice_leg"), C((0, 7.5, -1.5), (3, 7, 3), "slice_leg2"),
            C((1, 14.5, -1.5), (3, 7.5, 3), "slice_leg")]),
        B("torso", "root", (0, 22, 0), cubes=[
            C((-4, 22, -2), (8, 3.5, 4), "slice_a"), C((-2.5, 25.5, -2), (8, 3.5, 4), "slice_b"),
            C((-5, 29, -2), (8, 3.5, 4), "slice_c"), C((-3.5, 32.5, -2), (8, 3.5, 4), "slice_d"),
            C((-5.5, 36, -2), (8, 3.5, 4), "slice_a")]),
        # The head has come loose: it hangs a hand's width above the body.
        B("head", "torso", (0, 47, 0), rotation=(0, 0, -9), cubes=[
            C((-4, 44, -4), (8, 4, 8), "head_low"), C((-3, 48, -4), (8, 4, 8), "head_high"),
            C((-0.25, 46.5, -16), (0.5, 0.5, 12), "look")]),
        B("rightArm", "torso", (-7.5, 36, 0), rotation=(0, 0, 14), cubes=[
            C((-10.5, 25, -1.5), (3, 9, 3), "slice_arm"), C((-11.5, 15, -1.5), (3, 9, 3), "slice_arm2"),
            *[C((-11.5 + k, 5, -1), (1, 10, 1), "finger") for k in (0, 1, 2)]]),
        B("leftArm", "torso", (7.5, 36, 0), rotation=(0, 0, -14), cubes=[
            C((7.5, 29, -1.5), (3, 7, 3), "slice_arm")]),
        # Came loose at the elbow.
        B("leftForearm", "leftArm", (9, 28, 0), cubes=[
            C((9, 17, -1.5), (3, 8, 3), "missing"),
            *[C((9 + k, 8, -1), (1, 8, 1), "finger") for k in (0, 1, 2)]]),
        B("halo", "head", (0, 36, 0), cubes=[
            px(-8, 38, -1), px(5, 41, 2), px(-3, 43, -3), px(6, 34, -2), px(-9, 33, 2), px(1, 42, 3),
            px(-6, 42, 3), px(7, 38, 1),
            C((2, 43, -1), (2, 2, 2), "missing_s"), C((-10, 36, -2), (2, 2, 2), "missing_s")]),
    ], bounds=(3.0, 4.2, (0, 1.9, 0)))


def paint(seed, m, **_):
    void = Ramp(colors=["#020203", "#060608", "#0b0b10", "#121218", "#1a1a22"])
    red, cyan = (0x7A, 0x06, 0x1E), (0x06, 0x6E, 0x7A)
    magenta = (0xF8, 0x00, 0xF8)
    letters = {  # 3x5, lower case
        "n": ["...", "XX.", "X.X", "X.X", "X.X"],
        "u": ["...", "X.X", "X.X", "X.X", ".XX"],
        "l": ["X..", "X..", "X..", "X..", ".X."],
    }
    word = "null"

    def written(t):
        """The faint word across the chest (slice_b's front, 16x7)."""
        col, row = t.x - 1, t.y - 1
        if not (0 <= row < 5 and 0 <= col < 15) or col % 4 == 3:
            return False
        return letters[word[col // 4]][row][col % 4] == "X"

    def black(t, base=0.35):
        # Scanlines: every other row a touch lighter; now and then a bright
        # line of noise across a face.
        v = base + (0.08 if t.Y % 2 == 0 else 0.0)
        if t.noise(t.sx * 0.2, t.sy * 3.0, 1.0, 1) > 0.93:
            return (0x3A, 0x3A, 0x48)
        return t.paint(void, v, 0.15)

    def fringe(t):
        """Red on one edge of a slice, cyan on the other."""
        if f_side(t) and t.fw >= 4:
            if t.x == 0:
                return red if t.face in ("front", "right") else cyan
            if t.x == t.fw - 1:
                return cyan if t.face in ("front", "right") else red
        return None

    def f_side(t):
        return t.face in ("front", "back", "right", "left")

    def checker(t):
        return magenta if ((t.x // 2) + (t.y // 2)) % 2 == 0 else (0, 0, 0)

    def fn(t):
        tag, f = t.tag, t.face
        if tag in ("hitbox", "eyeline"):
            return (0xF0, 0xF0, 0xF0) if tag == "hitbox" else (0xE8, 0x20, 0x20)
        if tag == "look":
            return (0x30, 0x60, 0xF8)
        if tag in ("missing", "missing_s"):
            return checker(t)
        if tag == "px":
            return magenta if (t.X + t.Y) % 3 == 0 else (0xFF, 0xFF, 0xFF) if (t.X + t.Y) % 3 == 1 else (0, 0, 0)
        if tag == "head_low":
            if f == "front":
                # Two white eyes right under the tear.
                if 1 <= t.y <= 2 and t.x in (3, 4, 11, 12):
                    return EYE
                if t.y == 3 and t.x in (3, 4, 11, 12):
                    return (0x60, 0x60, 0x70)
                # A mouth that renders wrong: too wide, white teeth on black,
                # one side slipped down a pixel.
                lo = 1 if t.x >= 9 else 0
                if 2 <= t.x <= 14 and 5 + lo <= t.y <= 7 + lo:
                    if t.y in (5 + lo, 7 + lo):
                        return (0xF4, 0xF4, 0xF4) if t.x % 2 == 0 else BLACK
                    return (0xB0, 0x00, 0x10) if t.x in (6, 7) else BLACK
            return fringe(t) or black(t, 0.3)
        if tag == "head_high":
            return fringe(t) or black(t, 0.3)
        if tag == "slice_b" and f == "front" and written(t):
            return (0x2E, 0x2E, 0x3A)
        if tag.startswith("slice") or tag == "neck":
            return fringe(t) or black(t)
        if tag == "finger":
            return black(t, 0.2)
        return None

    return paint_model(m, seed, fn)
