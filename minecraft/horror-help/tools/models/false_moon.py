"""The thing in the sky (Local 58, Weather Service).

The emergency broadcast tells you not to look at the moon. This is why: it
isn't the moon. Hung in the night sky it is a pale disc, cratered and grey,
like it always is. When it notices you looking (`hh:face`), the face comes
up through the craters: two black holes for eyes, staring down, and a long
black mouth, open.

Built as a flat disc three blocks across (the script scales it up and puts
it high in the sky); `face` is a thin layer just in front of the moon."""

from models.kit import *

PREVIEW = {}


def disc(z, depth, tag, r=24):
    """A round plate built from horizontal strips."""
    out = []
    for i in range(-r, r, 4):
        y0 = i + 2
        w = 2 * math.sqrt(max(0.0, r * r - y0 * y0))
        w = max(4, round(w / 2) * 2)
        out.append(C((-w / 2, 26 + i, z), (w, 4, depth), tag))
    return out


def model():
    return hd_model("geometry.hh.false_moon", [
        B("root", None, (0, 0, 0)),
        B("moon", "root", (0, 26, 0), cubes=disc(0, 2, "moon")),
        B("face", "moon", (0, 26, 0), cubes=[
            C((-10.5, 30, -0.5), (7, 9, 0.5), "eye"), C((3.5, 30, -0.5), (7, 9, 0.5), "eye"),
            C((-8, 10, -0.5), (16, 12, 0.5), "mouth")]),
    ], bounds=(4.0, 4.0, (0, 1.6, 0)), width=512)


def paint(seed, m, **_):
    grey = Ramp(colors=["#5c5c60", "#7a7a7e", "#98989a", "#b4b4b4", "#cccbc6", "#e0dfd8"])
    hole = (0x00, 0x00, 0x00)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "moon":
            if f != "front":
                return t.paint(grey, 0.35, 0.2)
            c = t.n(5.0)
            if t.ridge(6.0, 0.05) > 0.66:
                return t.paint(grey, 0.25, 0.1)  # crater rims
            return t.paint(grey, 0.55 + (c - 0.5) * 0.5, 0.15, 6.0)
        if tag == "eye":
            # Ragged black holes, rounded at the corners.
            cx, cy = (t.fw - 1) / 2, (t.fh - 1) / 2
            if ((t.x - cx) / (cx + 0.5)) ** 2 + ((t.y - cy) / (cy + 0.5)) ** 2 > 1.0:
                return CLEAR
            return hole
        if tag == "mouth":
            cx, cy = (t.fw - 1) / 2, (t.fh - 1) / 2
            dx, dy = (t.x - cx) / (cx + 0.5), (t.y - cy) / (cy + 0.5)
            if dx * dx + dy * dy * 1.6 > 1.0:
                return CLEAR
            return hole
        return None

    return paint_model(m, seed, fn)
