"""Jeff the Killer.

A young man in a white hoodie gone grey and stained, black trousers, black
shoes, long black hair hanging round his face. The face is white, chalk
white, and it doesn't move: the eyes are wide open and ringed black, as if
they have never closed, and the smile is drawn wide across the cheeks in
dark red, a smile that never stops. In his right hand, a kitchen knife.
"Go to sleep.\""""

from models.kit import *
from models.humanoid import humanoid

PREVIEW = {}


def model():
    bones, h = humanoid(head=8, body=12, arm=12, leg=12, width=8, limb=3.75)
    neck = h["neck"]
    bones += [
        # Long black hair hanging down round the face and the back of the hood.
        B("hair", "head", (0, neck + 8, 0), cubes=[
            C((-4.25, neck + 2, -4.25), (1, 6.5, 8.5), "hair"), C((3.25, neck + 2, -4.25), (1, 6.5, 8.5), "hair"),
            C((-4.25, neck + 1, 3.25), (8.5, 7.5, 1), "hair"), C((-4.25, neck + 7.75, -4.25), (8.5, 0.75, 8.5), "hair")]),
        B("hood", "body", (0, neck, 2), cubes=[C((-4, neck - 1.5, 2), (8, 3, 2), "hood")]),
        # The knife in his right hand, held point down.
        B("knife", "rightForearm", (-5.9, 11, 0), rotation=(-20, 0, 0), cubes=[
            C((-6.4, 9.5, -0.5), (1, 3.5, 1), "handle"),
            C((-6.15, 3.5, -0.6), (0.5, 6, 1.2), "blade"),
            C((-6.15, 2.75, -0.2), (0.5, 0.75, 0.6), "blade")]),
    ]
    return hd_model("geometry.hb.jeff", bones, bounds=(2.0, 2.2, (0, 1.1, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#9a9a98", "#b8b8b4", "#d2d2cc", "#e6e6e0", "#f6f6f2"])
    hoodie = Ramp(colors=["#7a7a76", "#96968e", "#b0b0a8", "#c8c8c0", "#dcdcd4"])
    stain = Ramp(colors=["#2a0406", "#46080c", "#5e1014"])
    black = Ramp(colors=["#040405", "#0a0a0c", "#121216", "#1a1a20", "#24242a"])
    red = (0x6e, 0x0a, 0x10)
    hole = (0x02, 0x02, 0x02)

    # 16x16: wide-open eyes ringed in black, a smile drawn up the cheeks.
    face = [
        "................",
        "................",
        "................",
        "..KKKKK..KKKKK..",
        ".KKWWWKK.KWWWKK.",
        ".KWWpWWK.KWWpWK.",
        ".KKWWWKK.KKWWWK.",
        "..KKKKK..KKKKK..",
        "................",
        ".R............R.",
        ".RR..........RR.",
        "..RRR......RRR..",
        "....RRRRRRRR....",
        "......RRRR......",
        "................",
        "................",
    ]
    pal = {"K": hole, "W": (0xf0, 0xf0, 0xec), "p": (0x18, 0x02, 0x04), "R": red}

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                return rows(face, pal, t, lambda: t.paint(skin, 0.7, 0.12))
            if f == "top" or f == "back":
                return t.paint(black, 0.45, 0.3)
            return t.paint(skin, 0.6, 0.12)
        if tag == "hair":
            if f == "bottom" or t.side and t.y > t.fh - 2 and t.n(1.0) > 0.5:
                return CLEAR
            return t.paint(black, 0.45 + (0.1 if t.x % 3 == 0 else 0), 0.3)
        if tag.endswith("+"):
            return CLEAR
        if tag in ("body", "arm", "hood"):
            if t.n2(1.8) > 0.8:
                return t.paint(stain, 0.5, 0.3)  # old stains
            if tag == "body" and f == "front" and 3 <= t.y <= 9 and 3 <= t.x <= 12 and t.y == 3:
                return t.paint(hoodie, 0.3, 0.1)  # the pocket seam
            return t.paint(hoodie, 0.5, 0.3)
        if tag == "forearm":
            if t.y >= t.fh - 3:
                return t.paint(skin, 0.6, 0.12)  # the hands
            return t.paint(hoodie, 0.45, 0.3)
        if tag in ("leg", "shin"):
            return t.paint(black, 0.4 if tag == "leg" or t.y < t.fh - 3 else 0.6, 0.3)
        if tag == "handle":
            return t.paint(WOOD, 0.4, 0.2)
        if tag == "blade":
            return t.paint(IRON, 0.75, 0.1)
        return None

    return paint_model(m, seed, fn)
