"""Gabriel.

It says it is an angel. Five blocks of it float above the ground in a long
robe that frays into rags where the legs should be. Four wings open behind
it, the feathers dotted with eyes, a ring of gold turns over its head, and
two arms hang down far too long. Its face is the worst of it: seven eyes,
none of them where eyes go, and a mouth that runs from the top of the face
to the chin."""

from models.kit import *

PREVIEW = {}


def wing(side, upper):
    """A fan of long feathers out from the shoulder (built flat in XY)."""
    out = []
    n = 6 if upper else 4
    for k in range(n):
        length = (26 - 3 * k) if upper else (18 - 3 * k)
        angle = (55 - k * 13) if upper else (-12 - k * 15)
        y = 44 if upper else 40
        out.append(C((side_x(side, 4, 4 + length), y, 3 + (k % 2) * 0.5), (length, 3, 1), "feather",
                     pivot=(side * 4, y + 1, 3.5), rotation=(0, 0, angle * side)))
    return out


def model():
    halo = []
    for i in range(14):
        a = math.radians(i * 360 / 14)
        halo.append(C((math.cos(a) * 6 - 0.75, 64, math.sin(a) * 6 - 0.75), (1.5, 1, 1.5), "halo"))
    bones = [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 32, 0), cubes=[
            C((-5, 32, -3), (10, 14, 6), "robe_top"),
            C((-6, 14, -4), (12, 18, 8), "robe"),
            C((-6.5, 33, -3.5), (13, 2, 7), "sash")]),
        B("rags", "body", (0, 14, 0), cubes=[C((-6, 8, -4), (12, 6, 8), "rags")]),
        B("head", "body", (0, 46, 0), rotation=(8, 0, 0), cubes=[
            C((-1.5, 46, -1.5), (3, 3, 3), "neck"),
            C((-4.5, 49, -4.5), (9, 11, 9), "face")]),
        B("halo", "head", (0, 64, 0), cubes=halo),
        B("wingRightUpper", "body", (-4, 45, 3.5), cubes=wing(-1, True)),
        B("wingLeftUpper", "body", (4, 45, 3.5), cubes=wing(1, True)),
        B("wingRightLower", "body", (-4, 41, 3.5), cubes=wing(-1, False)),
        B("wingLeftLower", "body", (4, 41, 3.5), cubes=wing(1, False)),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (6 * side, 45, 0), rotation=(-6, 0, 6 * -side), cubes=[
                C((sx(5, 7.5), 29, -1.25), (2.5, 16, 2.5), "sleeve")]),
            B(f"{name}Forearm", f"{name}Arm", (6.25 * side, 29, 0), rotation=(-10, 0, 0), cubes=[
                C((sx(5.25, 7.25), 13, -1), (2, 16, 2), "arm"),
                *[C((sx(5 + k * 0.75, 5.5 + k * 0.75), 6 - (k % 2), -0.75 + (k % 2) * 0.5), (0.5, 7 + (k % 2), 0.5),
                    "finger") for k in range(3)]]),
        ]
    return hd_model("geometry.hh.gabriel", bones, bounds=(6.0, 6.0, (0, 2.8, 0)))


def paint(seed, m, **_):
    robe = Ramp(colors=["#6a645a", "#8a8476", "#aaa494", "#c8c2b0", "#e0dbca", "#f2eee2"])
    skin = Ramp(colors=["#7a7470", "#9a948e", "#b8b2aa", "#d0cac2", "#e6e0d8"])
    gold = Ramp(colors=["#6a4a08", "#9a7010", "#c89a1a", "#ecc232", "#ffe070"])
    gum = Ramp(colors=["#1a0406", "#2e080c", "#481016"])
    eye_w = glow("#fffef0", 50)
    halo_c = lambda t: glow(t.paint(gold, 0.7, 0.3), 60)
    # Seven eyes on the 18x22 face, (x, y, radius), and the mouth down the middle.
    eyes = [(4, 4, 2), (13, 4, 2), (9, 2, 1), (2, 10, 1), (15, 10, 1), (5, 15, 1), (13, 16, 2)]

    def fabric(t, base=0.55):
        if t.side and t.x % 4 == 1:
            base -= 0.1  # folds
        if t.n2(3) > 0.8:
            return t.paint(robe, base - 0.25, 0.1)  # stains
        return t.paint(robe, base, 0.2, 3.0)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "face":
            if f == "front":
                for ex, ey, r in eyes:
                    d = max(abs(t.x - ex), abs(t.y - ey))
                    if d == 0:
                        return BLACK
                    if d <= r:
                        return eye_w
                    if d == r + 1 and t.y >= ey:
                        return t.paint(skin, 0.15, 0.05)  # lower lid shadow
                if abs(t.x - 8.5) <= 1.5 - (t.y < 7 or t.y > 19) and 6 <= t.y <= 20:
                    if abs(t.x - 8.5) > 0.5:
                        return t.paint(TEETH, 0.7, 0.1) if t.y % 2 else t.paint(gum, 0.4, 0.1)
                    return BLACK
                return t.paint(skin, 0.6, 0.2, 2.5)
            if f == "top":
                return t.paint(skin, 0.5, 0.2)
            return t.paint(skin, 0.45, 0.25)
        if tag == "halo":
            return halo_c(t)
        if tag == "neck":
            return t.paint(skin, 0.4, 0.2)
        if tag in ("robe_top", "robe"):
            return fabric(t, 0.6 if tag == "robe_top" else 0.55)
        if tag == "sash":
            return t.paint(gold, 0.45, 0.3) if f not in ("top", "bottom") else CLEAR
        if tag == "rags":
            if f in ("top", "bottom"):
                return CLEAR
            return fabric(t, 0.45) if t.n(1.2) > 0.25 + 0.6 * t.v else CLEAR
        if tag == "feather":
            # Barbs along the feather, and here and there an eye.
            if f in ("front", "back") and t.n2(1.5) > 0.86:
                return BLACK if (t.X + t.Y) % 3 == 0 else eye_w
            if f in ("front", "back") and t.y == 0:
                return t.paint(robe, 0.35, 0.1)
            return t.paint(robe, 0.85 - 0.3 * (abs(t.x - t.fw / 2) / max(1, t.fw)), 0.15)
        if tag == "sleeve":
            return fabric(t, 0.5)
        if tag == "arm":
            return t.paint(skin, 0.45, 0.25) if t.y > 2 else fabric(t, 0.45)
        if tag == "finger":
            return t.paint(skin, 0.4 - 0.25 * t.v, 0.1) if t.v < 0.8 else BLACK
        return None

    return paint_model(m, seed, fn)
