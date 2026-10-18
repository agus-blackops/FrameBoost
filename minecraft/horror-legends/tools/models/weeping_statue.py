"""The Weeping Statue.

A weathered stone figure in a long robe with folded stone wings, its face
hidden in its hands as if it were crying. It never moves while anyone looks
at it. Each time you look back it is closer, and the pose is different: the
hands lowered from a face with hollow eyes and a mouth full of fangs, then
both arms reaching for you (hl:pose 0, 1, 2, driven by animations; the
geometry is built weeping). Moss climbs the hem, tear stains run down the
cheeks."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [
        B("root", None, (0, 0, 0)),
        B("robe", "root", (0, 0, 0), cubes=[
            C((-4.5, 0, -3.5), (9, 12, 7), "hem"),
            C((-4, 12, -3), (8, 8, 6), "skirt"),
            *[C((x, 0, -4), (1, 12, 0.5), "fold") for x in (-3, -0.5, 2)]]),
        B("body", "robe", (0, 20, 0), cubes=[
            C((-4, 20, -2.25), (8, 11, 4.5), "torso"),
            C((-4.25, 20, -2.5), (8.5, 1.5, 5), "sash")]),
        B("head", "body", (0, 31, 0), rotation=(28, 0, 0), cubes=[
            C((-1.5, 31, -1.5), (3, 1.5, 3), "neck"),
            C((-3.5, 32.5, -3.75), (7, 7.5, 7), "head"),
            C((-4, 32, -4), (8, 8.5, 7.5), "veil", 0.3),
            C((-4, 26, 2.5), (8, 7, 1), "veil_back"),
            C((-2, 39.5, -2), (2.5, 0.75, 2.5), "rubble")]),
        B("wingRight", "body", (-2, 29, 2.25), rotation=(10, -25, 0), cubes=[
            C((-3.5, 17, 2.25), (3, 14, 1.5), "wing"), C((-5.5, 19, 2.5), (2, 11, 1), "wing_tip")]),
        B("wingLeft", "body", (2, 29, 2.25), rotation=(10, 25, 0), cubes=[
            C((0.5, 17, 2.25), (3, 14, 1.5), "wing"), C((3.5, 19, 2.5), (2, 11, 1), "wing_tip")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Weeping: upper arms forward and in, forearms up, hands over the face.
            B(f"{name}Arm", "body", (5 * side, 30, 0), rotation=(-30, 0, 5 * side), cubes=[
                C((sx(4, 6.5), 20, -1.25), (2.5, 10, 2.5), "arm"),
                C((sx(3.75, 6.75), 23, -1.5), (3, 7, 3), "sleeve", 0.25)]),
            B(f"{name}Forearm", f"{name}Arm", (5.25 * side, 20.5, 0), rotation=(-140, 0, -20 * side), cubes=[
                C((sx(4.25, 6.25), 12, -1), (2, 9, 2), "forearm")]),
            B(f"{name}Hand", f"{name}Forearm", (5.25 * side, 12, 0), rotation=(0, 0, 0), cubes=[
                C((sx(4, 6.5), 8.5, -1.25), (2.5, 3.5, 2.5), "hand"),
                *[C((sx(4 + k * 0.75, 4.5 + k * 0.75), 3.5, -0.75), (0.5, 5, 0.5), "finger") for k in range(3)],
                C((sx(3.75, 4.25), 4.5, -1), (0.5, 4, 0.5), "thumb")]),
        ]
    return hd_model("geometry.hl.weeping_statue", bones, bounds=(2.0, 3.0, (0, 1.3, 0)))


def paint(seed, m, **_):
    stone = Ramp(colors=["#3a3834", "#56534c", "#747068", "#928d84", "#aca69c", "#c6c0b4"])
    moss = Ramp(colors=["#1a2410", "#2a3a18", "#3c5222", "#52682e"])
    lichen = (0x9E, 0x94, 0x52)
    tear = (0x5a, 0x06, 0x08)  # it weeps blood
    ember = glow("#ff2a1a", 85)

    def carved(t, base=0.55):
        if t.ridge(3.5, 0.045) > 0.5:
            return t.paint(stone, 0.12, 0.1)  # cracks
        if t.n2(2.5) > 0.86:
            return lichen
        return t.paint(stone, base, 0.18, 4.0)

    def mossy(t, amount, base=0.55):
        if t.side and t.v > 1 - amount + (t.n(1.5) - 0.5) * 0.5:
            return t.paint(moss, 0.5, 0.4)
        return carved(t, base)

    # The face under the hands: hollow eyes, black tear stains down to the
    # jaw, a mouth stretched open over stone fangs.
    face = [
        "..............",
        "..............",
        ".OOOO....OOOO.",
        ".OOOO....OOOO.",
        ".OOXO....OXOO.",
        ".OOOO....OOOO.",
        "..tO......Ot..",
        "..t...nn...t..",
        "..t........t..",
        ".tMMMMMMMMMMt.",
        ".MFFMFFFMFFFM.",
        ".MFMMFMFMMFMM.",
        ".MMMMMMMMMMMM.",
        ".MMFMMMFMMFMM.",
        "..MFFMFFFMFM..",
    ]

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                ch = face[t.y][t.x] if t.y < len(face) and t.x < len(face[t.y]) else "."
                if ch in "OX":
                    return ember if ch == "X" else BLACK
                if ch in "tM":
                    return tear if ch == "t" else BLACK
                if ch == "F":
                    return t.paint(stone, 0.8, 0.1)
                if ch == "n":
                    return t.paint(stone, 0.3, 0.1)
                return carved(t, 0.62)
            return carved(t, 0.5)
        if tag in ("veil", "veil_back"):
            if tag == "veil" and (f in ("front", "bottom") or (f in ("right", "left") and t.x < 3 if f == "left" else False)):
                return CLEAR
            if tag == "veil" and f in ("right", "left"):
                front = t.x >= t.fw - 3 if f == "right" else t.x < 3
                return CLEAR if front and t.y > 3 else carved(t, 0.5)
            return carved(t, 0.48 - 0.1 * t.v)
        if tag in ("hem", "skirt", "fold"):
            return mossy(t, 0.35 if tag != "skirt" else 0.0, 0.5 if tag != "fold" else 0.6)
        if tag in ("torso", "sash"):
            if tag == "torso" and f == "front" and t.x in (5, 10) and t.v < 0.2 + 0.5 * t.n(0.8) and t.fx % 2 == 0:
                return tear  # runs down from where the hands are
            if tag == "torso" and f == "front" and (t.x - 3) % 5 == 0:
                return t.paint(stone, 0.35, 0.1)  # the folds of the robe
            return carved(t, 0.55 if tag == "torso" else 0.42)
        if tag == "neck":
            return carved(t, 0.4)
        if tag in ("wing", "wing_tip"):
            # Carved feathers: rows of scallops.
            if (t.y + (t.x // 2) % 2) % 3 == 0:
                return t.paint(stone, 0.3, 0.1)
            return mossy(t, 0.15, 0.55)
        if tag == "rubble":
            return t.paint(stone, 0.2, 0.2) if f != "top" else BLACK
        if tag in ("finger", "thumb"):
            if t.v > 0.7:
                return t.paint(stone, 0.25, 0.1)  # tips worn to points, dark with old blood
            if f == "front" and t.n(2) > 0.6:
                return tear
            return carved(t, 0.6)
        if tag in ("arm", "sleeve", "forearm", "hand"):
            if tag == "sleeve":
                return carved(t, 0.5) if t.side else CLEAR
            return carved(t, 0.58)
        return None

    return paint_model(m, seed, fn)
