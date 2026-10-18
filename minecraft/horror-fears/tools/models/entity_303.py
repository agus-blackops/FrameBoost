"""Entity 303.

The other legend: a hacker who was banned and never left. Player-sized and
floating a little off the ground, in a white hooded robe that ends in rags
instead of legs, a black mask over the lower face with a stitched grin, and
under the hood two red eyes with no whites. A red sash, and a ring of red
code characters circling him slowly (the `ring` bone)."""

from models.kit import *

PREVIEW = {}


def model():
    glyph = lambda x, y, z: C((x, y, z), (1, 1.5, 0.5), "glyph")
    return hd_model("geometry.hl.entity_303", [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 12, 0), cubes=[
            C((-4, 12, -2), (8, 12, 4), "robe"),
            C((-4.5, 3, -2.5), (9, 10, 5), "robe_low"),
            C((-4.25, 12, -2.25), (8.5, 2, 4.5), "sash")]),
        B("tatters", "body", (0, 3, 0), cubes=[C((-4.5, 0, -2.5), (9, 3, 5), "tatters")]),
        B("head", "body", (0, 24, 0), cubes=[
            C((-4, 24, -4), (8, 8, 8), "head"),
            C((-4, 24, -4), (8, 8, 8), "hood", 0.6)]),
        B("rightArm", "body", (-5, 22, 0), rotation=(0, 0, 8), cubes=[
            C((-8, 12, -2), (4, 12, 4), "sleeve"), C((-7.5, 10, -1.5), (3, 2.5, 3), "hand"),
            *[C((-7.25 + k, 5.5, -1), (0.5, 4.5, 0.5), "claw") for k in range(3)]]),
        B("leftArm", "body", (5, 22, 0), rotation=(0, 0, -8), cubes=[
            C((4, 12, -2), (4, 12, 4), "sleeve"), C((4.5, 10, -1.5), (3, 2.5, 3), "hand"),
            *[C((4.75 + k, 5.5, -1), (0.5, 4.5, 0.5), "claw") for k in range(3)]]),
        B("ring", "root", (0, 16, 0), cubes=[
            glyph(9, 15, -3), glyph(-10, 17, 2), glyph(3, 19, 8), glyph(-5, 14, -9), glyph(8, 18, 5),
            glyph(-9, 20, -5), glyph(-2, 16, 9), glyph(6, 13, -8)]),
    ], bounds=(2.0, 2.5, (0, 1.2, 0)))


def paint(seed, m, **_):
    cloth = Ramp(colors=["#6c6a66", "#8e8c86", "#aeaca6", "#c8c6c0", "#dcdad4", "#ecebe6"])
    mask = Ramp(colors=["#050506", "#0c0c0e", "#141418", "#1e1e24"])
    sash = Ramp(colors=["#3a0406", "#5c080c", "#7e1014", "#a01c1e"])
    skin = Ramp(colors=["#5a4234", "#765646", "#8e6a58", "#a8806a"])
    red = glow("#ff1818", 90)

    def robe(t, base=0.55):
        if t.side and t.x % 5 == 2:
            base -= 0.12  # folds
        if t.n2(3) > 0.8:
            return t.paint(cloth, base - 0.25, 0.1)  # grime
        if t.noise(t.sx * 0.5 + 70, t.sy * 0.35 + 20, 1.0, 1) > 0.88 - 0.1 * t.v:
            return t.paint(BLOOD, 0.45, 0.3)  # not his
        return t.paint(cloth, base, 0.12, 4.0)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                if t.y <= 4:
                    return t.paint(mask, 0.2, 0.1)  # the shadow under the hood
                if t.y in (5, 6, 7) and t.x in (3, 4, 11, 12):
                    return red if t.y != 5 else BLACK
                if t.y == 8 and t.x in (4, 11):
                    return sash.colors[2]  # it bleeds from the eyes
                if t.y <= 8:
                    return t.paint(skin, 0.3, 0.1)
                # The mask, a grin stitched across it in red.
                if t.y == 11 and 2 <= t.x <= 13 or (t.y == 10 and t.x in (1, 14)) or (t.y == 9 and t.x in (0, 15)):
                    return red
                if t.y in (10, 12) and 3 <= t.x <= 12 and t.x % 2 == 0:
                    return sash.colors[2]
                if t.y in (13, 14) and t.x in (5, 9) and t.n(2) > 0.3:
                    return sash.colors[1]
                return t.paint(mask, 0.5, 0.3)
            return t.paint(mask, 0.3, 0.2)
        if tag == "hood":
            if f == "front" and 2 <= t.x <= t.fw - 3 and t.y >= 3:
                return CLEAR
            if f == "bottom":
                return CLEAR
            return robe(t, 0.6)
        if tag in ("robe", "robe_low", "sleeve"):
            if tag == "robe" and f == "front" and 7 <= t.x <= 8:
                return t.paint(cloth, 0.35, 0.1)  # the seam
            return robe(t, 0.55 if tag != "robe_low" else 0.5)
        if tag == "tatters":
            if f in ("top", "bottom"):
                return CLEAR
            return robe(t, 0.4) if t.n(1.2) > 0.3 + 0.6 * t.v else CLEAR
        if tag == "sash":
            return CLEAR if f in ("top", "bottom") else t.paint(sash, 0.6, 0.3)
        if tag == "claw":
            return t.paint(mask, 0.6 - 0.4 * t.v, 0.1)
        if tag == "hand":
            return t.paint(mask, 0.4, 0.2)  # black gloves
        if tag == "glyph":
            # Characters of red code: a few lit texels in a 2x3 cell.
            return red if (t.X * 7 + t.Y * 3) % 5 < 3 else CLEAR
        return None

    return paint_model(m, seed, fn)
