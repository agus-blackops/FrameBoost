"""The Man From The Fog.

Over three blocks of starved, grey body in a soaked black raincoat that hangs
open to the ribs. The hood is up; inside it the face is long and pale, two
pinprick white eyes and a grin that runs from ear to ear, packed with thin
teeth. When he opens it the whole lower face drops away. His arms are far too
long: the sleeves end at his elbows and the forearms and fingers carry on down
to his knees. Barefoot, the toes grey with mud."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2 * side, 24, 0), cubes=[
                C((sx(0.75, 3.25), 12, -1.25), (2.5, 12, 2.5), "thigh")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 12, 0), cubes=[
                C((sx(1, 3), 1, -1), (2, 11, 2), "shin"),
                C((sx(0.75, 3.25), 0, -3.5), (2.5, 1.5, 4.5), "foot")]),
        ]
    bones += [
        B("body", "root", (0, 24, 0), rotation=(6, 0, 0), cubes=[
            C((-3.5, 24, -2), (7, 4, 4), "hips")]),
        B("spine", "body", (0, 28, 0), cubes=[
            C((-3, 28, -1.75), (6, 7, 3.5), "waist")]),
        B("chest", "spine", (0, 35, 0), rotation=(8, 0, 0), cubes=[
            C((-4, 35, -2.25), (8, 8, 4.5), "chest"),
            C((-4.5, 41, -2.5), (9, 2.5, 5), "shoulders")]),
        # The raincoat: a back panel and two open front panels, all hanging
        # from the shoulders so they can swing on their own.
        B("coatBack", "chest", (0, 43, 2.5), rotation=(-14, 0, 0), cubes=[C((-4.5, 14, 2.25), (9, 29, 1), "coat_back")]),
        B("coatRight", "chest", (-4, 43, -2.5), rotation=(-14, 0, 0), cubes=[C((-4.75, 15, -2.75), (2.5, 28, 0.75), "coat_front")]),
        B("coatLeft", "chest", (4, 43, -2.5), rotation=(-14, 0, 0), cubes=[C((2.25, 15, -2.75), (2.5, 28, 0.75), "coat_front")]),
        B("coatSideR", "chest", (-4.5, 43, 0), rotation=(-14, 0, 0), cubes=[C((-5, 15, -2.5), (0.75, 28, 5), "coat_side")]),
        B("coatSideL", "chest", (4.5, 43, 0), rotation=(-14, 0, 0), cubes=[C((4.25, 15, -2.5), (0.75, 28, 5), "coat_side")]),
        B("neck", "chest", (0, 43, 0), rotation=(8, 0, 0), cubes=[
            C((-1.25, 43, -1.5), (2.5, 2.5, 2.5), "neck"),
            C((-4, 42.5, -2), (8, 2.5, 4.5), "collar", 0.3)]),
        B("head", "neck", (0, 45, -0.5), rotation=(-10, 0, 0), cubes=[
            C((-3.5, 49, -3.5), (7, 6, 7), "head"),
            C((-3, 45.5, -2.75), (6, 3.5, 5), "maw")]),
        B("jaw", "head", (0, 49, 3), cubes=[
            C((-3.5, 45, -3.5), (7, 4, 7), "jaw")]),
        B("hood", "head", (0, 55, 0), cubes=[
            C((-4.5, 55, -4.5), (9, 1.5, 9), "hood_top"),
            C((-4.5, 43.5, 3.5), (9, 11.5, 1), "hood_back"),
            C((-4.5, 44, -4.5), (1, 11, 8), "hood_side"), C((3.5, 44, -4.5), (1, 11, 8), "hood_side"),
            C((-3.5, 54, -4.75), (7, 1, 1), "hood_brim"),
            C((-2, 54, 4.5), (4, 2, 2), "hood_tip"), C((-1, 53, 6.5), (2, 2, 1.5), "hood_tip2")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "chest", (4.75 * side, 42, 0), rotation=(0, 0, 4 * -side), cubes=[
                C((sx(4, 6), 30, -1), (2, 13, 2), "arm"),
                C((sx(3.75, 6.25), 31, -1.25), (2.5, 12, 2.5), "sleeve", 0.3)]),
            B(f"{name}Forearm", f"{name}Arm", (5 * side, 30.5, 0), rotation=(-10, 0, 0), cubes=[
                C((sx(4.25, 5.75), 18, -0.75), (1.5, 13, 1.5), "forearm"),
                C((sx(4, 6), 28.5, -1), (2, 2, 2), "elbow")]),
            B(f"{name}Hand", f"{name}Forearm", (5 * side, 18, 0), cubes=[
                C((sx(4, 6), 15.5, -1.25), (2, 2.5, 2.5), "palm"),
                *[C((sx(4.25 + k * 0.5, 4.75 + k * 0.5), 9.5 - (k % 2), -1 + (k % 2) * 0.5), (0.5, 6.5 + (k % 2), 0.5),
                    "finger", pivot=(side * 5, 15.5, 0), rotation=(-10 + k * 5, 0, 0)) for k in range(4)]]),
        ]
    return hd_model("geometry.hl.fog_man", bones, bounds=(2.5, 4.0, (0, 1.8, 0)))


def paint(seed, m, **_):
    coat = Ramp(colors=["#060807", "#0d110e", "#161b17", "#212822", "#2e3630", "#46524a"])
    skin = Ramp(colors=["#34363a", "#4c4f52", "#66696a", "#808382", "#9c9e9a", "#b8b8b2"])
    mud = Ramp(colors=["#1a140e", "#2a2016", "#3c2e20", "#4e3e2c"])
    gum = Ramp(colors=["#0c0204", "#1a0508", "#2c0a0e", "#40121a"])
    pin = glow("#f4f4ff", 30)

    def wet(t, base=0.45):
        # Rain streaks run down the coat: thin light lines and dark runs.
        s = t.noise(t.sx * 4.0, t.sy * 0.35, 3.0, 1)
        v = base + (0.22 if s > 0.78 else -0.1 if s < 0.22 else 0.0)
        return t.paint(coat, v, 0.3, 2.5)

    def gaunt(t, base=0.5):
        return t.paint(skin, base + (-0.12 if t.n2(3) > 0.7 else 0.0), 0.3, 2.0)

    # The face (14 wide, 12 tall on the front of the head), looking out of
    # the dark of the hood: shadowed brow, sunken sockets with a white point
    # of light in each, and the top half of the grin on the bottom rows.
    face = [
        "dddddddddddddd",
        "dddddddddddddd",
        "ssssssssssssss",
        "SSSSSSSSSSSSSS",
        "SsooosSSsooosS",
        "SoooPoSSoPooos",
        "SsooosSSsooosS",
        "SSSSSSnnSSSSSS",
        "gSSSSSNNSSSSSg",
        "TgSSSSSSSSSSgT",
        "TTgggggggggggT",
        "TTTTTTTTTTTTTT",
    ]
    jaw = [
        "TTTTTTTTTTTTTT",
        "gTgTgTgTgTgTgT",
        "ggggggggggggTT",
        "sSSSSSSSSSSgTs",
        "SSSSSSSSSSSSgS",
        "SSSSSSSSSSSSSS",
        "sSSSSSSSSSSSSs",
        "ssssssssssssss",
    ]

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                ch = face[t.y][t.x] if t.x < len(face[t.y]) else "S"
                if ch == "d":
                    return t.paint(skin, 0.1, 0.2)
                if ch == "s":
                    return t.paint(skin, 0.3, 0.2)
                if ch == "o":
                    return BLACK
                if ch == "P":
                    return pin
                if ch == "n":
                    return t.paint(skin, 0.3, 0.1)
                if ch == "N":
                    return BLACK
                if ch == "T":
                    return t.paint(TEETH, 0.65, 0.2)
                if ch == "g":
                    return t.paint(gum, 0.5, 0.3)
                return gaunt(t, 0.55 - 0.02 * t.y)
            if f in ("right", "left") and t.y >= t.fh - 2:
                # The grin keeps going round to the ears.
                ear = t.x < 9 if f == "right" else t.x >= t.fw - 9
                if ear:
                    return t.paint(TEETH, 0.55, 0.2) if (t.x + t.y) % 2 else t.paint(gum, 0.4, 0.3)
            return gaunt(t, 0.3)
        if tag == "jaw":
            if f == "front":
                ch = jaw[t.y][t.x]
                if ch == "T":
                    return t.paint(TEETH, 0.6, 0.2)
                if ch == "g":
                    return t.paint(gum, 0.5, 0.3)
                if ch == "s":
                    return t.paint(skin, 0.3, 0.2)
                return gaunt(t, 0.45)
            if f == "top":
                return t.paint(gum, 0.3, 0.4)
            if f in ("right", "left") and t.y < 2:
                ear = t.x < 9 if f == "right" else t.x >= t.fw - 9
                if ear:
                    return t.paint(TEETH, 0.5, 0.2) if (t.x + t.y) % 2 == 0 else t.paint(gum, 0.4, 0.3)
            return gaunt(t, 0.3)
        if tag == "maw":
            return t.paint(gum, 0.15, 0.4)
        if tag.startswith("hood"):
            if tag == "hood_tip2":
                return wet(t, 0.35)
            if tag == "hood_top" and f == "bottom":
                return t.paint(coat, 0.05, 0.1)
            return wet(t, 0.4 if tag != "hood_brim" else 0.55)
        if tag in ("coat_back", "coat_front", "coat_side"):
            if t.y >= t.fh - 6 and t.n(1.5) > 0.55 and f != "top":
                return CLEAR  # the hem is torn
            if tag == "coat_front" and f == "front" and t.x in (0, 1) and t.y % 8 == 4:
                return (0x5A, 0x5E, 0x58)  # toggles
            if f == "back" and tag == "coat_front":
                return t.paint(coat, 0.2, 0.2)
            return wet(t, 0.45)
        if tag == "collar":
            return wet(t, 0.5) if f != "top" else CLEAR
        if tag == "shoulders":
            return wet(t, 0.5)
        if tag in ("chest", "waist"):
            if f == "front":
                # Open coat: starved grey skin, every rib showing.
                if t.x < 3 or t.x > t.fw - 4:
                    return wet(t, 0.35)
                if abs(t.x - t.fw / 2 + 0.5) <= 0.5:
                    return t.paint(skin, 0.25, 0.1)  # breastbone groove
                if tag == "chest" and t.y >= 2 and t.y % 3 == 0:
                    return t.paint(skin, 0.8, 0.1)
                if tag == "chest" and t.y >= 2 and t.y % 3 == 1:
                    return t.paint(skin, 0.12, 0.1)
                if tag == "waist" and t.y < 4 and t.y % 3 == 0:
                    return t.paint(skin, 0.7, 0.1)
                return gaunt(t, 0.45)
            return wet(t, 0.4)
        if tag == "hips":
            return wet(t, 0.35)
        if tag == "neck":
            return gaunt(t, 0.3)
        if tag == "sleeve":
            return wet(t, 0.5) if t.y < t.fh - 3 or t.n(1.5) > 0.45 else CLEAR
        if tag == "arm":
            return gaunt(t, 0.4)
        if tag in ("forearm", "elbow", "palm"):
            return gaunt(t, 0.5 if tag != "elbow" else 0.6)
        if tag == "finger":
            return BLACK if t.y >= t.fh - 2 else gaunt(t, 0.45 - 0.2 * t.v)
        if tag in ("thigh", "shin"):
            return t.paint(mud, 0.4, 0.3) if tag == "shin" and t.y >= t.fh - 6 and t.n(2) > 0.4 else gaunt(t, 0.42)
        if tag == "foot":
            return t.paint(mud, 0.5 if f == "top" else 0.3, 0.4)
        return None

    return paint_model(m, seed, fn)
