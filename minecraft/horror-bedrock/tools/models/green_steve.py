"""Green Steve.

He is Steve. Same hair, same shirt, same trousers, same everything, except
that all of it is green: green skin, green hair, a green shirt and green
trousers, the green of something left too long underwater. And the eyes
are blank: two flat pale-green squares with no pupils at all, that glow a
little in the dark. "Hello, brother.\""""

from models.kit import *
from models.humanoid import humanoid

PREVIEW = {}


def model():
    bones, _ = humanoid()
    return hd_model("geometry.hb.green_steve", bones, bounds=(2.0, 2.2, (0, 1.1, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#14361a", "#1e4a24", "#2a602e", "#367638", "#468c46", "#5aa458"])
    hair = Ramp(colors=["#061408", "#0a1e0c", "#102a12", "#163818", "#1e461e"])
    shirt = Ramp(colors=["#0a2a24", "#0e3c32", "#145042", "#1c6452", "#287a64", "#389276"])
    pants = Ramp(colors=["#081a0e", "#0e2614", "#14321a", "#1c4022", "#244e2a"])
    shoe = Ramp(colors=["#0a0e0a", "#121a12", "#1a261a", "#243424"])
    eye = glow("#b8ffb0", 75)
    eye_edge = glow("#6ad864", 60)
    # Steve's face, 16x16 design pixels, every colour turned green, and the
    # eyes two blank glowing squares.
    face = [
        "HHHHHHHHHHHHHHHH",
        "HHHHHHHHHHHHHHHH",
        "HhHHHHHHHHHHHhHH",
        "HSSSSSSSSSSSSSSH",
        "SSSSSSSSSSSSSSSS",
        "SSSSSSSSSSSSSSSS",
        "SSSSSSSSSSSSSSSS",
        "SSEEeeSSSSeeEESS",
        "SSEEeeSSSSeeEESS",
        "SSSSSSnnnnSSSSSS",
        "SSSSSSnNNnSSSSSS",
        "SSSSmmSSSSmmSSSS",
        "SSSSmmmmmmmmSSSS",
        "SSSSSSSSSSSSSSSS",
        "SSSSSSSSSSSSSSSS",
        "SSSSSSSSSSSSSSSS",
    ]
    fpal = {"H": hair.colors[1], "h": hair.colors[3], "E": eye, "e": eye_edge,
            "n": skin.colors[2], "N": skin.colors[1], "m": skin.colors[0]}

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                return rows(face, fpal, t, lambda: t.paint(skin, 0.6, 0.2))
            if f == "top" or (t.side and t.y < 4) or (f == "back" and t.y < 12):
                return t.paint(hair, 0.5, 0.35, 1.5)
            return t.paint(skin, 0.55, 0.2)
        if tag.endswith("+"):
            # The outer layer: a few clumps of hair and a damp, ragged collar.
            if tag == "head+" and (f == "top" or (t.side and t.y < 3)) and t.n(1.5) > 0.55:
                return t.paint(hair, 0.6, 0.3)
            return CLEAR
        if tag == "body":
            if f == "front" and t.y < 2 and 5 <= t.x <= 10:
                return t.paint(skin, 0.5, 0.2)  # the neck of the shirt
            return t.paint(shirt, 0.55, 0.3)
        if tag == "arm":
            # Short sleeves: shirt at the top, green skin below.
            if t.y < 8 or f == "top":
                return t.paint(shirt, 0.5, 0.3)
            return t.paint(skin, 0.5, 0.2)
        if tag == "forearm":
            return t.paint(skin, 0.5, 0.2)
        if tag == "leg":
            return t.paint(pants, 0.5, 0.3)
        if tag == "shin":
            if t.y >= t.fh - 4 or f == "bottom":
                return t.paint(shoe, 0.5, 0.2)
            return t.paint(pants, 0.5, 0.3)
        return None

    return paint_model(m, seed, fn)
