"""The Shadow (the Hat Man).

The thing people see standing at the foot of the bed when they can't move:
a tall, flat black shape in a wide-brimmed hat and a long coat that doesn't
reach the floor, because below the knees he frays into smoke. Two dim red
points under the brim. Arms like coat hangers, fingers like twigs. Wisps of
black drift off him all the time."""

from models.kit import *

PREVIEW = {}


def model():
    wisp = lambda x, y, z, s=1: C((x, y, z), (s, s, s), "wisp" if s >= 1 else "wisp_s")
    bones = [
        B("root", None, (0, 0, 0)),
        B("smoke", "root", (0, 14, 0), cubes=[
            C((-3.5, 4, -2.5), (7, 10, 5), "smoke"),
            C((-2.5, 0, -2), (5, 5, 4), "smoke_low"),
            wisp(-4.5, 3, -1), wisp(3.5, 6, 1), wisp(-1, 0.5, 2.5, 0.5), wisp(2, 1, -2.5, 0.5)]),
        B("body", "smoke", (0, 14, 0), cubes=[
            C((-4, 14, -2.5), (8, 16, 5), "coat"),
            C((-4.5, 12, -3), (9, 6, 6), "coat_hem", 0.2),
            C((-5, 27, -3), (10, 3.5, 6), "shoulders")]),
        B("head", "body", (0, 30.5, 0), rotation=(8, 0, 0), cubes=[
            C((-1, 30, -1), (2, 2, 2), "neck"),
            C((-3, 32, -3), (6, 7, 6), "head"),
            C((-7, 38, -7), (14, 1, 14), "brim"),
            C((-3.5, 39, -3.5), (7, 4.5, 7), "crown"),
            C((-3.75, 39, -3.75), (7.5, 1, 7.5), "band")]),
        B("wisps", "body", (0, 30, 0), cubes=[
            wisp(-7, 33, 2), wisp(6, 36, -1, 0.5), wisp(5, 29, 3), wisp(-6, 25, -2, 0.5), wisp(-3, 45, 1, 0.5)]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (5 * side, 29.5, 0), rotation=(0, 0, 3 * -side), cubes=[
                C((sx(4, 6), 17, -1), (2, 13, 2), "arm")]),
            B(f"{name}Forearm", f"{name}Arm", (5 * side, 17, 0), rotation=(-6, 0, 0), cubes=[
                C((sx(4.25, 5.75), 7, -0.75), (1.5, 10, 1.5), "forearm"),
                *[C((sx(4 + k * 0.5, 4.5 + k * 0.5), 1.5 - (k % 2), -0.5 + (k % 2) * 0.5), (0.5, 5.5 + (k % 2), 0.5),
                    "finger") for k in range(4)]]),
        ]
    return hd_model("geometry.hl.shadow", bones, bounds=(2.0, 3.2, (0, 1.4, 0)))


def paint(seed, m, **_):
    black = Ramp(colors=["#000000", "#030304", "#060608", "#0a0a0d", "#101014"])
    red = glow("#b01010", 90)

    def smoke(t, keep):
        """Solid at the top, breaking into holes towards the bottom."""
        if t.n(1.6) < 1 - keep:
            return CLEAR
        return t.paint(black, 0.4, 0.4)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front" and t.y in (5, 6) and t.x in (3, 8):
                return red if t.y == 5 else (0x40, 0x04, 0x04)
            return t.paint(black, 0.3, 0.2)
        if tag in ("brim", "crown", "neck", "shoulders", "arm", "forearm", "finger", "coat"):
            if tag == "brim" and f == "top" and t.n(3) > 0.7:
                return (0x16, 0x16, 0x1C)
            return t.paint(black, 0.35 if tag != "brim" else 0.5, 0.25)
        if tag == "band":
            return CLEAR if f in ("top", "bottom") else (0x1C, 0x10, 0x10)
        if tag == "coat_hem":
            if f in ("top", "bottom"):
                return CLEAR
            return smoke(t, 1.0 - 0.9 * t.v)
        if tag == "smoke":
            if f in ("top", "bottom"):
                return CLEAR if f == "bottom" else t.paint(black, 0.3, 0.2)
            return smoke(t, 0.95 - 0.5 * t.v)
        if tag == "smoke_low":
            if f in ("top", "bottom"):
                return CLEAR
            return smoke(t, 0.45 - 0.4 * t.v)
        if tag in ("wisp", "wisp_s"):
            return smoke(t, 0.7)
        return None

    return paint_model(m, seed, fn)
