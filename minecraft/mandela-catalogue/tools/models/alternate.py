"""The Alternate.

Two forms in one model; the render controller shows one or the other
(md:revealed). Disguised it is a neighbour: shirt, jeans, a face you almost
know. Almost: the eyes are a little uneven and the pupils are pin points, and
the smile is wider than a smile should be. Revealed, the head stretches up
on a long neck into a face that is mostly mouth, black sockets with a point
of light in each, the jaw dropping to the chest, and the fingers grow long.

paint(variant=0..2) gives three different neighbours."""

from models.kit import *

PREVIEW = {}
DISGUISE = ("head",)
TRUE = ("trueHead", "trueJaw", "rightClaw", "leftClaw")

VARIANTS = [
    # skin, hair, shirt, pants
    (["#6a4230", "#8e5e46", "#b07a5a", "#c69070", "#d8a888", "#e8c0a0"],
     ["#1a0f08", "#2a1a0e", "#3c2614", "#50331c", "#664224"],
     ["#3a1414", "#561e1e", "#722a28", "#8e3a34", "#a85046"],
     ["#141c2c", "#1e2a40", "#2a3a56", "#384c6c", "#4a6084"]),
    (["#3e2418", "#5a3624", "#764a32", "#8e5e42", "#a67454", "#ba8a68"],
     ["#060504", "#0e0b08", "#18130e", "#221b14", "#2e251c"],
     ["#1c3a24", "#285034", "#346644", "#447c56", "#5a946a"],
     ["#2a2620", "#3a352c", "#4c463a", "#5e574a", "#72695a"]),
    (["#8a6a52", "#a8846a", "#c49e82", "#d8b49a", "#e8c8b0", "#f4dcc8"],
     ["#5a3a10", "#7a5418", "#9a6e22", "#ba8a30", "#d4a642"],
     ["#4e4a40", "#6a655a", "#868070", "#a29c8a", "#bcb6a2"],
     ["#101014", "#1a1a20", "#26262e", "#32323c", "#40404c"]),
]


def model():
    return hd_model("geometry.md.alternate", [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 24, 0), cubes=[
            C((-4, 12, -2), (8, 12, 4), "body"), C((-4, 12, -2), (8, 12, 4), "body+", 0.25)]),
        B("head", "body", (0, 24, 0), cubes=[
            C((-4, 24, -4), (8, 8, 8), "head"), C((-4, 24, -4), (8, 8, 8), "head+", 0.5)]),
        # The true head: up on a long neck, mostly mouth.
        B("trueHead", "body", (0, 24, 0), cubes=[
            C((-1.25, 24, -1.25), (2.5, 7, 2.5), "neck"),
            C((-3.5, 30, -3.5), (7, 12, 7), "skull"),
            C((-3.75, 38, -3.75), (7.5, 4.5, 7.5), "hair_true", 0.3)]),
        B("trueJaw", "trueHead", (0, 31, 3), rotation=(20, 0, 0), cubes=[
            C((-3, 24, -3.25), (6, 7, 6), "jaw")]),
        B("rightArm", "body", (-5, 22, 0), cubes=[
            C((-8, 12, -2), (4, 12, 4), "arm"), C((-8, 12, -2), (4, 12, 4), "arm+", 0.25)]),
        B("leftArm", "body", (5, 22, 0), cubes=[
            C((4, 12, -2), (4, 12, 4), "arm"), C((4, 12, -2), (4, 12, 4), "arm+", 0.25)]),
        B("rightClaw", "rightArm", (-6, 12, 0), cubes=[
            *[C((-7.75 + k, 2, -1 + (k % 2) * 0.5), (0.75, 10, 0.75), "claw") for k in range(4)]]),
        B("leftClaw", "leftArm", (6, 12, 0), cubes=[
            *[C((4.5 + k, 2, -1 + (k % 2) * 0.5), (0.75, 10, 0.75), "claw") for k in range(4)]]),
        B("rightLeg", "root", (-2, 12, 0), cubes=[
            C((-4, 0, -2), (4, 12, 4), "leg"), C((-4, 0, -2), (4, 12, 4), "leg+", 0.25)]),
        B("leftLeg", "root", (2, 12, 0), cubes=[
            C((0, 0, -2), (4, 12, 4), "leg"), C((0, 0, -2), (4, 12, 4), "leg+", 0.25)]),
    ], bounds=(2.0, 3.0, (0, 1.4, 0)))


def paint(seed, m, variant=0, **_):
    s_, h_, sh_, p_ = VARIANTS[variant]
    skin, hair, shirt, pants = Ramp(colors=s_), Ramp(colors=h_), Ramp(colors=sh_), Ramp(colors=p_)
    shoe = Ramp(colors=["#1a1a1e", "#2a2a30", "#3c3c44", "#50505a"])
    gum = Ramp(colors=["#1a0406", "#2e080c", "#481016", "#621c22"])
    pin = glow("#ffffff", 40)
    # The neighbour's face, 16x16. Something is off: the left eye sits a
    # row lower, the pupils are single black points, the smile runs almost
    # ear to ear.
    face = [
        "HHHHHHHHHHHHHHHH",
        "HHhHHHHHHHhhHHHH",
        "HHHhhHHHHhhHHHhH",
        "HHHHHhHHhHHHHHHH",
        "HHSSSHHSSSHHSSHH",
        "HSSSSSSSSSSSSSSH",
        "SSbbbbSSSSSSSSSS",
        "SSWWPWSSSSbbbbSS",
        "SSWWWWSSSSWPWWSS",
        "SSSSSSSSSSWWWWSS",
        "SSSSSSSnnSSSSSSS",
        "SSSSSSnNNnSSSSSS",
        "SmSSSSSSSSSSSSmS",
        "SSmMMMMMMMMMMmSS",
        "SSSmTTTTTTTTmSSS",
        "SSSSSSSSSSSSSSSS",
    ]
    fpal = {"H": hair.colors[1], "h": hair.colors[3], "b": hair.colors[2], "W": (0xEE, 0xEC, 0xE6),
            "P": (0x06, 0x06, 0x06), "n": skin.colors[2], "N": skin.colors[1], "m": skin.colors[1],
            "M": (0x2A, 0x10, 0x10), "T": (0xE8, 0xE0, 0xCC)}
    # The true face on the 14x24 front of the stretched skull: sunken black
    # sockets with a point of light, and a mouth that takes up the rest.
    true_face = []
    for y in range(24):
        row = ""
        for x in range(14):
            if 3 <= y <= 6 and (2 <= x <= 4 or 9 <= x <= 11):
                row += "P" if y == 4 and x in (3, 10) else "O"
            elif y >= 9:
                cx = 6.5
                half = 5.5 - abs(y - 17) * 0.35
                if abs(x - cx) < half - 1:
                    row += "K"
                elif abs(x - cx) < half:
                    row += "T" if (x + y) % 2 == 0 else "G"
                else:
                    row += "S"
            else:
                row += "S"
        true_face.append(row)

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
                return t.paint(hair, 0.45, 0.5, 1.5) if t.y < 13 else t.paint(skin, 0.4, 0.1)
            back = t.x < 8 if f == "right" else t.x >= 8
            if t.y < 5 or (back and t.y < 11):
                return t.paint(hair, 0.45, 0.5, 1.5)
            return t.paint(skin, 0.55, 0.12)
        if tag == "head+":
            if f in ("right", "left", "back", "top") and t.y < 4 and t.n(1.2) > 0.6:
                return hair.colors[2]
            return CLEAR
        if tag == "skull":
            if f == "front":
                ch = true_face[t.y][t.x]
                if ch == "O":
                    return BLACK
                if ch == "P":
                    return pin
                if ch == "K":
                    return t.paint(gum, 0.15, 0.2)
                if ch == "T":
                    return t.paint(TEETH, 0.7, 0.1)
                if ch == "G":
                    return t.paint(gum, 0.5, 0.2)
                return t.paint(skin, 0.5 - 0.15 * t.v, 0.2)
            if f == "top":
                return t.paint(hair, 0.45, 0.5)
            # The skin stretched thin: pale and veined.
            if t.ridge(3.0, 0.05) > 0.55:
                return t.paint(skin, 0.15, 0.1)
            return t.paint(skin, 0.42, 0.25)
        if tag == "hair_true":
            if f in ("front", "bottom"):
                return CLEAR
            return t.paint(hair, 0.4, 0.5) if t.y < 5 or t.n(1.2) > 0.5 else CLEAR
        if tag == "neck":
            return t.paint(skin, 0.3 + 0.2 * (t.y % 3 == 0), 0.2)
        if tag == "jaw":
            if f == "top":
                return t.paint(gum, 0.2, 0.2)
            if f == "front" and t.y < 3:
                return t.paint(TEETH, 0.7, 0.1) if t.x % 2 == 0 else t.paint(gum, 0.4, 0.2)
            return t.paint(skin, 0.4, 0.2)
        if tag == "claw":
            return t.paint(skin, 0.4 - 0.3 * t.v, 0.2) if t.v < 0.85 else BLACK
        if tag == "body":
            if f == "bottom":
                return pants.colors[2]
            if t.y >= t.fh - 2 and f != "top":
                return (0x3A, 0x2A, 0x1A)  # belt
            if f == "front" and t.y < 3 and abs(t.x - 7.5) < 3 - t.y:
                return t.paint(skin, 0.6, 0.1)
            return t.paint(shirt, 0.6 - (0.08 if t.x in (4, 11) else 0), 0.2, 3.0)
        if tag == "body+":
            return shirt.colors[1] if t.y == t.fh - 3 and t.side else CLEAR
        if tag == "arm":
            if f == "top" or (f != "bottom" and t.y < 10):
                return t.paint(shirt, 0.6, 0.2, 3.0)
            return t.paint(skin, 0.5 if f != "bottom" else 0.35, 0.12)
        if tag == "arm+":
            return shirt.colors[2] if t.y in (8, 9) and t.side else CLEAR
        if tag == "leg":
            if f == "bottom" or (f != "top" and t.y >= t.fh - 3):
                return t.paint(shoe, 0.5, 0.2)
            return t.paint(pants, 0.55 + (0.1 if t.x in (1, t.fw - 2) else 0), 0.25, 2.0)
        if tag == "leg+":
            return CLEAR
        return None

    return paint_model(m, seed, fn)
