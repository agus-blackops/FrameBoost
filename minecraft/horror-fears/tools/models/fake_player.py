"""HerobrineGamer788: someone else in your world.

He has to pass for a player, so he is built exactly like one (the standard
skin, with its outer layer) and only the skin gives him away: a gaming
headset with a red light that never blinks, eyes a shade too far apart,
bitten nails, a sleeve stained at the cuff. When he stops pretending the
eyes go white and black runs from them (paint(..., white_eyes=True)). The
pickaxe only shows while he mines or builds."""

from models.kit import *

PREVIEW = {"rightArm": (-20, 0, 0), "leftArm": (10, 0, 0)}


def model():
    return hd_model("geometry.hl.fake_player", [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 24, 0), cubes=[
            C((-4, 12, -2), (8, 12, 4), "body"), C((-4, 12, -2), (8, 12, 4), "body+", 0.25),
            # The hood, down: a bunched ring of cloth on his shoulders.
            C((-4.75, 22.5, -2.5), (9.5, 2.5, 6.5), "hood"), C((-4.5, 21, 1.75), (9, 3.5, 2.5), "hood_back"),
            C((-1.75, 18, -2.75), (0.5, 4, 0.5), "string"), C((1.25, 18, -2.75), (0.5, 4, 0.5), "string"),
            C((-3, 12.5, -2.6), (6, 3.5, 0.6), "pocket")]),
        B("head", "body", (0, 24, 0), cubes=[
            C((-4, 24, -4), (8, 8, 8), "head"), C((-4, 24, -4), (8, 8, 8), "head+", 0.5),
            # The headset.
            C((-4.5, 32.5, -0.75), (9, 1, 1.5), "band"),
            C((-5, 29, -0.75), (1, 3.5, 1.5), "band_side"), C((4, 29, -0.75), (1, 3.5, 1.5), "band_side"),
            C((-5.5, 25.5, -1.75), (1.5, 3.5, 3.5), "cup"), C((4, 25.5, -1.75), (1.5, 3.5, 3.5), "cup"),
            C((5, 26, -6), (0.5, 0.5, 4.5), "boom"), C((3.5, 25.5, -6.5), (2, 1, 1), "mic")]),
        B("rightArm", "body", (-5, 22, 0), cubes=[
            C((-8, 12, -2), (4, 12, 4), "arm"), C((-8, 12, -2), (4, 12, 4), "arm+", 0.25)]),
        B("leftArm", "body", (5, 22, 0), cubes=[
            C((4, 12, -2), (4, 12, 4), "arm"), C((4, 12, -2), (4, 12, 4), "arm+", 0.25)]),
        B("tool", "rightArm", (-6, 13, 0), cubes=[
            C((-6.5, 12.5, -9), (1, 1, 8), "tool_handle"),
            C((-6.5, 10.5, -10), (1, 5, 1), "tool_head"),
            C((-6.5, 10, -10.5), (1, 1, 1), "tool_tip"), C((-6.5, 15, -10.5), (1, 1, 1), "tool_tip")]),
        B("rightLeg", "root", (-2, 12, 0), cubes=[
            C((-4, 0, -2), (4, 12, 4), "leg"), C((-4, 0, -2), (4, 12, 4), "leg+", 0.25)]),
        B("leftLeg", "root", (2, 12, 0), cubes=[
            C((0, 0, -2), (4, 12, 4), "leg"), C((0, 0, -2), (4, 12, 4), "leg+", 0.25)]),
    ], bounds=(2.0, 2.2, (0, 1.1, 0)))


def paint(seed, m, white_eyes=False, **_):
    skin = Ramp(colors=["#6a4230", "#8e5e46", "#b07a5a", "#c69070", "#d8a888", "#e8c0a0"])
    hair = Ramp(colors=["#1a0f08", "#2a1a0e", "#3c2614", "#50331c", "#664224"])
    shirt = Ramp(colors=["#2a0608", "#4a0c10", "#6e141a", "#92202a", "#b43642", "#d25a62"])  # a maroon hoodie
    pants = Ramp(colors=["#0e0e12", "#16161c", "#202028", "#2c2c36", "#3a3a46", "#4c4c5a"])  # black joggers
    shoe = Ramp(colors=["#222228", "#34343c", "#4a4a54", "#62626c", "#7a7a84"])
    plastic = Ramp(colors=["#0a0a0c", "#141418", "#202026", "#2e2e36", "#40404a"])
    led = glow("#ff2020", 60)
    eye = EYE if white_eyes else None
    tear = (0x0A, 0x06, 0x06) if white_eyes else None
    # His face: the eyes sit one texel wider apart than they should, the
    # pupils look slightly off to the side, and the mouth is a flat line.
    face = [
        "HHHHHHHHHHHHHHHH",
        "HHhHHHHHHHhhHHHH",
        "HHHhhHHHHhhHHHhH",
        "HHHHHhHHhHHHHHHH",
        "HHSSSHHSSSHHSSHH",
        "HSSSSSSSSSSSSSSH",
        "SSSSSSSSSSSSSSSS",
        "SbbbbSSSSSSbbbbS",
        "SWwPPSSSSSSPPwWS",
        "SWWPpSSSSSSpPWWS",
        "STSSSSSnnSSSSTSS",
        "STSSSSnNNnSSSTSS",
        "SSTSmmmmmmmmSTSS",
        "SSSmmMMMMMMmmTSS",
        "SSSTmmmmmmmmSSSS",
        "SSSSSmmmmmmSSSSS",
    ]
    fpal = {"H": hair.colors[1], "h": hair.colors[3], "b": hair.colors[2],
            "W": eye or (0xEE, 0xEE, 0xEA), "w": eye or (0xFF, 0xFF, 0xFF),
            "P": eye or (0x3A, 0x4E, 0x9E), "p": eye or (0x20, 0x2A, 0x60),
            "n": skin.colors[2], "N": skin.colors[1], "m": (0x76, 0x4C, 0x36), "M": (0x4A, 0x2C, 0x1E),
            "T": tear}

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                return rows(face, fpal, t, lambda: t.paint(skin, 0.62, 0.12))
            if f == "top":
                return t.paint(hair, 0.5, 0.6, 1.5)
            if f == "bottom":
                return t.paint(skin, 0.35, 0.1)
            if f == "back":
                return t.paint(hair, 0.45, 0.5, 1.5) if t.y < 13 - (t.x % 3 == 0) else t.paint(skin, 0.4, 0.1)
            back = t.x < 8 if f == "right" else t.x >= 8
            if t.y < 5 or (back and t.y < 11):
                return t.paint(hair, 0.45, 0.5, 1.5)
            return t.paint(skin, 0.55, 0.12)
        if tag == "head+":
            if f == "top" and (t.x in (0, 1, t.fw - 2, t.fw - 1) or t.y in (0, 1, t.fh - 2, t.fh - 1)) and t.n(1.2) > 0.55:
                return hair.colors[3]
            if f in ("right", "left", "back") and t.y < 4 and t.n(1.2) > 0.62:
                return hair.colors[2]
            return CLEAR
        if tag in ("band", "band_side", "boom"):
            return t.paint(plastic, 0.55, 0.2)
        if tag == "cup":
            if f in ("right", "left") and t.fw > 4 and 1 <= t.x <= t.fw - 2 and 1 <= t.y <= t.fh - 2:
                if abs(t.x - t.fw / 2 + 0.5) < 1 and abs(t.y - t.fh / 2 + 0.5) < 1:
                    return led  # it is always on
                return t.paint(plastic, 0.35, 0.1)
            return t.paint(plastic, 0.6, 0.15)
        if tag == "mic":
            return (0x30, 0x30, 0x36) if f != "front" else (0x12, 0x12, 0x14)
        if tag == "body":
            if f == "bottom":
                return pants.colors[2]
            if t.y >= t.fh - 2 and f != "top":
                return (0x5C, 0x40, 0x26) if not (f == "front" and t.x in (7, 8)) else (0xB8, 0xB0, 0x90)  # belt
            if f == "front" and t.y < 3 and abs(t.x - 7.5) < 3 - t.y:
                return t.paint(skin, 0.6, 0.1)  # the neckline
            v = 0.62
            if f == "front" and t.y >= t.fh - 8 and (t.y == t.fh - 8 or t.x in (2, t.fw - 3)):
                v -= 0.12  # the kangaroo pocket seam
            if f in ("front", "back") and t.x in (4, 11) and 10 < t.y < 20:
                v -= 0.08  # folds
            if white_eyes and f == "front" and 5 <= t.y <= 14 and t.n(2.5) > 0.66:
                return t.paint(BLOOD, 0.5, 0.2)
            return t.paint(shirt, v, 0.2)
        if tag == "body+":
            return shirt.colors[1] if t.y == t.fh - 3 and t.side else CLEAR
        if tag == "arm":
            if f == "top" or (f != "bottom" and t.y < 9):
                if t.y in (7, 8):
                    return shirt.colors[4] if t.y == 7 else shirt.colors[1]  # rolled-up sleeve
                return t.paint(shirt, 0.62, 0.2)
            if f == "bottom" or t.y >= t.fh - 3:
                if f == "bottom" and t.x % 2 == 0:
                    return (0x8A, 0x3E, 0x36)  # bitten nails
                return t.paint(skin, 0.38, 0.1)
            return t.paint(skin, 0.52, 0.12)
        if tag == "arm+":
            if t.y in (6, 7) and t.side:
                return shirt.colors[2]
            return CLEAR
        if tag == "leg":
            if f == "bottom" or (f != "top" and t.y >= t.fh - 4):
                if t.y >= t.fh - 2 or f == "bottom":
                    return (0xE0, 0xDE, 0xD6) if f != "bottom" else (0xB0, 0xAE, 0xA6)  # white soles
                if f == "front" and t.y == t.fh - 4 and t.x % 3 == 1:
                    return (0xDA, 0xDA, 0xDA)  # laces
                return t.paint(shoe, 0.5, 0.2)
            v = 0.55
            if f == "front" and t.y < 4 and t.x in (2, 13):
                v -= 0.18  # pocket seams
            if f in ("front", "back") and t.x in (1, t.fw - 2):
                v += 0.1  # denim stitching
            if f == "front" and 12 <= t.y <= 14:
                v += 0.06  # knees
            return t.paint(pants, v, 0.22, 2.0)
        if tag == "leg+":
            return pants.colors[1] if t.side and t.y == t.fh - 5 else CLEAR
        if tag in ("hood", "hood_back"):
            if f in ("top", "bottom") and tag == "hood" and t.fh <= 6:
                return t.paint(shirt, 0.3, 0.2)
            return t.paint(shirt, 0.5 - 0.08 * (t.y % 3 == 0), 0.3, 2.0)
        if tag == "string":
            return (0xE8, 0xE4, 0xD8) if t.y < t.fh - 1 else (0xB0, 0xAC, 0xA0)
        if tag == "pocket":
            return t.paint(shirt, 0.3, 0.15)
        if tag == "tool_handle":
            return t.paint(WOOD, 0.55, 0.2)
        if tag in ("tool_head", "tool_tip"):
            return t.paint(IRON, 0.6 + 0.15 * (f == "top"), 0.2)
        return None

    return paint_model(m, seed, fn)
