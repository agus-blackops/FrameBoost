"""Your Double.

It wears your name over its head and stands like a player: the standard
skin, plain clothes, a face you see in the mirror every day. Disguised, the
only thing wrong is that it never blinks. paint(revealed=True) is the face it
shows when it stops pretending: black eyes with a white point, black
running down the cheeks, the smile split open."""

from models.kit import *

PREVIEW = {}


def model():
    return hd_model("geometry.md.double", [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 24, 0), cubes=[
            C((-4, 12, -2), (8, 12, 4), "body"), C((-4, 12, -2), (8, 12, 4), "body+", 0.25)]),
        B("head", "body", (0, 24, 0), cubes=[
            C((-4, 24, -4), (8, 8, 8), "head"), C((-4, 24, -4), (8, 8, 8), "head+", 0.5)]),
        B("rightArm", "body", (-5, 22, 0), cubes=[
            C((-8, 12, -2), (4, 12, 4), "arm"), C((-8, 12, -2), (4, 12, 4), "arm+", 0.25)]),
        B("leftArm", "body", (5, 22, 0), cubes=[
            C((4, 12, -2), (4, 12, 4), "arm"), C((4, 12, -2), (4, 12, 4), "arm+", 0.25)]),
        B("rightLeg", "root", (-2, 12, 0), cubes=[
            C((-4, 0, -2), (4, 12, 4), "leg"), C((-4, 0, -2), (4, 12, 4), "leg+", 0.25)]),
        B("leftLeg", "root", (2, 12, 0), cubes=[
            C((0, 0, -2), (4, 12, 4), "leg"), C((0, 0, -2), (4, 12, 4), "leg+", 0.25)]),
    ], bounds=(2.0, 2.2, (0, 1.1, 0)))


def paint(seed, m, revealed=False, **_):
    skin = Ramp(colors=["#6a4230", "#8e5e46", "#b07a5a", "#c69070", "#d8a888", "#e8c0a0"])
    hair = Ramp(colors=["#1a0f08", "#2a1a0e", "#3c2614", "#50331c", "#664224"])
    shirt = Ramp(colors=["#08404e", "#0c5c6a", "#107884", "#18949c", "#30b0b0", "#62c8c2"])
    pants = Ramp(colors=["#181448", "#221e66", "#2e2882", "#3a3496", "#4e4aac", "#6864c0"])
    shoe = Ramp(colors=["#222228", "#34343c", "#4a4a54", "#62626c"])
    pin = glow("#ffffff", 40)
    face = [
        "HHHHHHHHHHHHHHHH",
        "HHhHHHHHHHhhHHHH",
        "HHHhhHHHHhhHHHhH",
        "HHHHHhHHhHHHHHHH",
        "HHSSSHHSSSHHSSHH",
        "HSSSSSSSSSSSSSSH",
        "SSSSSSSSSSSSSSSS",
        "SSbbbbSSSSbbbbSS",
        "SSWwPPSSSSPPwWSS",
        "SSWWPpSSSSpPWWSS",
        "SSSRSSSnnSSSRSSS",
        "SSSRSSnNNnSSRSSS",
        "SSSSmmmmmmmmSSSS",
        "SSSmmMrrrrMmmSSS",
        "SSSSmmmmmmmmSSSS",
        "SSSSSmmmmmmSSSSS",
    ]
    if revealed:
        face = [
            "HHHHHHHHHHHHHHHH",
            "HHhHHHHHHHhhHHHH",
            "HHHhhHHHHhhHHHhH",
            "HHHHHhHHhHHHHHHH",
            "HHSSSHHSSSHHSSHH",
            "HSSSSSSSSSSSSSSH",
            "SSSSSSSSSSSSSSSS",
            "SSbbbbSSSSbbbbSS",
            "SSKKKKSSSSKKKKSS",
            "SSKXKKSSSSKKXKSS",
            "SSSRSSSnnSSSRSSS",
            "SSSRSSnNNnSSRSSS",
            "SmRRRRRRRRRRRRmS",
            "SSmKTKTKTKTKmRSS",
            "SSSmRRRRRRRRmRSS",
            "SSSSSSRSSSSSSRSS",
        ]
    fpal = {"H": hair.colors[1], "h": hair.colors[3], "b": hair.colors[2], "W": (0xEE, 0xEE, 0xEA),
            "w": (0xFF, 0xFF, 0xFF), "P": (0x3A, 0x4E, 0x9E), "p": (0x20, 0x2A, 0x60), "n": skin.colors[2],
            "N": skin.colors[1], "m": (0x76, 0x4C, 0x36), "M": (0x4A, 0x2C, 0x1E), "r": (0x8E, 0x4A, 0x44),
            "R": (0x06, 0x04, 0x04) if revealed else None, "K": (0x02, 0x02, 0x02), "X": pin,
            "T": (0xE8, 0xE0, 0xCC)}

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                return rows(face, fpal, t, lambda: t.paint(skin, 0.62 - (0.08 if revealed else 0), 0.12))
            if f == "top":
                return t.paint(hair, 0.5, 0.6, 1.5)
            if f == "bottom":
                return t.paint(skin, 0.35, 0.1)
            if f == "back":
                return t.paint(hair, 0.45, 0.5, 1.5) if t.y < 13 else t.paint(skin, 0.4, 0.1)
            back = t.x < 8 if f == "right" else t.x >= 8
            if t.y < 5 or (back and t.y < 11):
                return t.paint(hair, 0.45, 0.5, 1.5)
            return t.paint(skin, 0.55, 0.12)
        if tag == "head+":
            if f == "top" and (t.x in (0, 1, t.fw - 2, t.fw - 1) or t.y in (0, 1, t.fh - 2, t.fh - 1)) and t.n(1.2) > 0.55:
                return hair.colors[3]
            return CLEAR
        if tag == "body":
            if f == "bottom":
                return pants.colors[2]
            if t.y >= t.fh - 2 and f != "top":
                return (0x5C, 0x40, 0x26)
            if f == "front" and t.y < 4 and abs(t.x - 7.5) < 4 - t.y:
                return t.paint(skin, 0.6, 0.1)
            if revealed and f == "front" and 3 <= t.y <= 12 and t.n(2.5) > 0.62:
                return (0x0A, 0x06, 0x06)  # what ran down from its face
            return t.paint(shirt, 0.62, 0.2)
        if tag == "body+":
            return CLEAR
        if tag == "arm":
            if f == "top" or (f != "bottom" and t.y < 9):
                return shirt.colors[4] if t.y == 7 else shirt.colors[1] if t.y == 8 else t.paint(shirt, 0.62, 0.2)
            return t.paint(skin, 0.52 if f != "bottom" else 0.38, 0.12)
        if tag == "arm+":
            return CLEAR
        if tag == "leg":
            if f == "bottom" or (f != "top" and t.y >= t.fh - 4):
                return (0xE0, 0xDE, 0xD6) if t.y >= t.fh - 2 or f == "bottom" else t.paint(shoe, 0.5, 0.2)
            return t.paint(pants, 0.55, 0.22, 2.0)
        if tag == "leg+":
            return CLEAR
        return None

    return paint_model(m, seed, fn)
