"""The Eyes.

Not a creature you can see: only two eyes, low down in the dark of a cave,
that blink and look at you. Everything else on the texture is clear."""

from models.kit import *

PREVIEW = {}


def model():
    return hd_model("geometry.hl.eyes", [
        B("root", None, (0, 0, 0)),
        B("eyes", "root", (0, 10, 0), cubes=[
            C((-2.5, 9.5, -0.25), (2, 1, 0.5), "eye"), C((0.5, 9.5, -0.25), (2, 1, 0.5), "eye")]),
    ], bounds=(1.0, 1.0, (0, 0.6, 0)), width=64)


def paint(seed, m, **_):
    white = glow("#fff6e8", 95)
    iris = glow("#ffd040", 90)

    def fn(t):
        if t.face not in ("front", "back"):
            return CLEAR
        if t.fy in (0, t.ffh - 1) and t.fx in (0, t.ffw - 1):
            return CLEAR  # rounded corners
        mid = abs(t.fx - (t.ffw - 1) / 2) <= 1.0
        if mid:
            return BLACK if abs(t.fy - (t.ffh - 1) / 2) <= 1.0 else iris
        return white

    return paint_model(m, seed, fn)
