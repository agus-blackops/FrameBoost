"""The Faceless Enderman.

An Enderman that never learned what a face was. Taller than the ones in the
game, nearly three and a half blocks, stretched thin, black as the inside of
a closed eye, with arms down to the floor and fingers that go on too long. The
head is smooth. Where the eyes should be there is only a seam, a hairline
crack of violet that opens a little when it is looking at you. It moves in
blinks (the scripts teleport it), and a ring of violet motes hangs around it."""

from models.kit import *

PREVIEW = {}


def model():
    mote = lambda x, y, z: C((x, y, z), (1, 1, 1), "mote")
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2 * side, 30, 0), cubes=[
                C((sx(0.5, 2.5), 0, -1), (2, 30, 2), "leg"),
                C((sx(0.25, 2.75), 0, -2), (2.5, 1.5, 3.5), "foot")]),
        ]
    bones += [
        B("body", "root", (0, 30, 0), cubes=[
            C((-4, 30, -2), (8, 12, 4), "torso"),
            *[C((-3.25 + 1.6 * k, 31 + 2.5 * (k % 2), -2.25), (0.5, 7, 0.25), "rib") for k in range(5)]]),
        B("neck", "body", (0, 42, 0), cubes=[C((-1.25, 41.5, -1.25), (2.5, 4.5, 2.5), "neck")]),
        B("head", "neck", (0, 46, 0), rotation=(0, 0, 8), cubes=[
            C((-4, 46, -4), (8, 9, 8), "head"),
            C((-4.1, 46, -4.1), (8.2, 9, 0.2), "seam_plate")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (5 * side, 41, 0), rotation=(0, 0, 4 * -side), cubes=[
                C((sx(4, 6), 11, -1), (2, 30, 2), "arm")]),
            B(f"{name}Hand", f"{name}Arm", (5 * side, 11, 0), cubes=[
                C((sx(3.75, 6.25), 8, -1.25), (2.5, 3, 2.5), "hand"),
                *[C((sx(3.75 + k * 0.6, 4.25 + k * 0.6), -1, -0.75), (0.5, 9, 0.5), "finger") for k in range(4)]]),
        ]
    bones.append(B("motes", "root", (0, 24, 0), cubes=[
        mote(-9, 40, 2), mote(8, 50, -3), mote(-6, 24, -8), mote(10, 18, 5), mote(-10, 8, -2),
        mote(6, 34, 9), mote(-3, 56, 5), mote(3, 4, -9), mote(9, 44, 8), mote(-8, 30, 7)]))
    return hd_model("geometry.hl.faceless_enderman", bones, bounds=(1.2, 3.6, (0, 1.6, 0)))


def paint(seed, m, **_):
    void = Ramp(colors=["#010102", "#040406", "#08080c", "#0e0e14", "#16161e"])
    pores = glow("#8a2be2", 70)
    seam = glow("#d8a0ff", 95)
    seam_dim = glow("#6a1fb8", 80)

    def black(t, base=0.35):
        if t.n2(2.0) > 0.86:
            return t.paint(void, base + 0.3, 0.1)  # a sheen, like wet
        return t.paint(void, base, 0.3, 2.5)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "seam_plate":
            if f != "front":
                return CLEAR
            # A hairline where the eyes would be, brightest in the middle.
            mid = abs(t.x - (t.fw - 1) / 2)
            if t.y in (7, 8) and mid <= (t.fw - 1) / 2 - 2:
                return seam if mid < 2.5 else seam_dim
            if t.y in (6, 9) and mid < 1.5:
                return seam_dim
            return CLEAR
        if tag == "head":
            if f == "front" and t.n2(2.5) > 0.88:
                return pores
            return black(t, 0.3)
        if tag == "rib":
            return t.paint(void, 0.8, 0.1)
        if tag == "mote":
            return pores if t.n(2) > 0.4 else seam
        if tag in ("torso", "neck", "arm", "leg", "hand", "foot"):
            if tag in ("arm", "leg") and t.v > 0.95:
                return pores
            return black(t, 0.3 if tag != "torso" else 0.34)
        if tag == "finger":
            return pores if t.v > 0.9 else t.paint(void, 0.5 - 0.35 * t.v, 0.1)
        return None

    return paint_model(m, seed, fn)
