"""Siren Head.

Taller than the trees: nearly six blocks of dried, leathery skin stretched
over a skeleton, legs like stilts, arms that reach to its knees. Where the
head should be, the spine keeps going up as a pole of dried flesh, and at the
top it carries two rusted loudspeakers on a crossbar. It stands among the
trees without moving until the sirens turn towards you."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (3 * side, 44, 0), cubes=[
                C((sx(1.5, 4.5), 22, -1.5), (3, 22, 3), "thigh"),
                C((sx(1.25, 4.75), 22, -1.75), (3.5, 3, 3.5), "knee")]),
            B(f"{name}Shin", f"{name}Leg", (3 * side, 22, 0), cubes=[
                C((sx(1.75, 4.25), 2, -1.25), (2.5, 20, 2.5), "shin"),
                C((sx(1.5, 4.5), 0, -5), (3, 2, 6.5), "foot"),
                *[C((sx(1.5 + k, 2.5 + k), 0, -7), (1, 1, 2), "toe") for k in range(3)]]),
        ]
    bones += [
        B("body", "root", (0, 44, 0), cubes=[C((-3.5, 44, -2), (7, 4, 4), "pelvis")]),
        B("waist", "body", (0, 48, 0), rotation=(4, 0, 0), cubes=[
            C((-1, 48, -1), (2, 8, 2), "spine"),
            C((-2.5, 48, -1.5), (5, 8, 3), "belly")]),
        B("chest", "waist", (0, 56, 0), rotation=(6, 0, 0), cubes=[
            C((-4, 56, -2.25), (8, 12, 4.5), "chest"),
            *[C((-4.25, y, -2.5), (8.5, 0.5, 0.25), "rib_out") for y in (58, 60, 62, 64)],
            C((-5, 65, -2.5), (10, 3, 5), "shoulders")]),
        B("pole", "chest", (0, 68, 0), rotation=(-6, 0, 0), cubes=[
            C((-1, 68, -1), (2, 16, 2), "pole"),
            C((-1.5, 70, -1.5), (3, 1.5, 3), "sinew"), C((-1.5, 76, -1.5), (3, 1.5, 3), "sinew")]),
        B("sirens", "pole", (0, 84, 0), cubes=[
            C((-8, 84, -1), (16, 2, 2), "crossbar"),
            *[C((x, 82.5, -4), (6, 5, 6), "speaker") for x in (-9, 3)],
            *[C((x, 82, -5), (7, 6, 1), "horn") for x in (-9.5, 2.5)],
            *[C((x + 0.75, y, -5.25), (5.5, 0.25, 0.25), "grille") for x in (-9.5, 2.5) for y in (83.5, 85, 86.5)],
            C((-1.5, 86, -1.5), (3, 3, 3), "bracket")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "chest", (5.25 * side, 66, 0), rotation=(0, 0, 5 * -side), cubes=[
                C((sx(4, 6.5), 45, -1.25), (2.5, 21, 2.5), "arm")]),
            B(f"{name}Forearm", f"{name}Arm", (5.25 * side, 45, 0), rotation=(-8, 0, 0), cubes=[
                C((sx(4.25, 6.25), 25, -1), (2, 20, 2), "forearm"),
                C((sx(4, 6.5), 43.5, -1.25), (2.5, 2.5, 2.5), "knee")]),
            B(f"{name}Hand", f"{name}Forearm", (5.25 * side, 25, 0), cubes=[
                C((sx(4, 6.5), 22, -1.5), (2.5, 3, 3), "palm"),
                *[C((sx(4 + k * 0.75, 4.5 + k * 0.75), 13 - (k % 2), -1 + (k % 2) * 0.5), (0.5, 9 + (k % 2), 0.5),
                    "finger", pivot=(side * 5.25, 22, 0), rotation=(-8 + k * 4, 0, 0)) for k in range(4)]]),
        ]
    return hd_model("geometry.hl.siren_head", bones, bounds=(4.0, 6.5, (0, 3.0, 0)))


def paint(seed, m, **_):
    hide = Ramp(colors=["#1e1410", "#2e2018", "#402e22", "#54402e", "#6a523c", "#80684c"])
    sinew = Ramp(colors=["#3a1210", "#58201a", "#763026", "#904436"])
    rust = Ramp(colors=["#2a140a", "#46220e", "#643214", "#82461c", "#a05c28"])
    metal = Ramp(colors=["#1c1c1e", "#2e2e32", "#44444a", "#5c5c62", "#76767c"])

    def leather(t, base=0.5):
        # Dried skin pulled tight: long vertical creases and dark patches.
        if t.noise(t.sx * 3.0, t.sy * 0.25, 3.0, 1) > 0.8:
            return t.paint(hide, base - 0.25, 0.1)
        if t.ridge(4.0, 0.05) > 0.6:
            return t.paint(sinew, 0.5, 0.2)
        return t.paint(hide, base, 0.35, 2.5)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "chest":
            if f in ("front", "back") and t.y >= 2 and t.y % 3 == 0 and abs(t.x - 7.5) > 1:
                return t.paint(hide, 0.8, 0.1)  # ribs
            if f in ("front", "back") and t.y >= 2 and t.y % 3 == 1:
                return t.paint(hide, 0.15, 0.1)
            return leather(t, 0.45)
        if tag == "rib_out":
            return t.paint(BONE, 0.45, 0.25) if f != "back" else CLEAR
        if tag == "belly":
            return leather(t, 0.3)
        if tag == "spine":
            return t.paint(BONE, 0.4, 0.3)
        if tag in ("pole", "sinew"):
            if tag == "sinew":
                return t.paint(sinew, 0.25, 0.3) if t.n(1.5) > 0.4 else leather(t, 0.3)
            return leather(t, 0.4)
        if tag in ("crossbar", "bracket"):
            return t.paint(rust, 0.45, 0.4) if t.n(2) > 0.4 else t.paint(metal, 0.4, 0.3)
        if tag == "speaker":
            return t.paint(rust, 0.5, 0.45, 2.0) if t.n(2.5) > 0.35 else t.paint(metal, 0.5, 0.3)
        if tag == "grille":
            return t.paint(metal, 0.3, 0.1)
        if tag == "horn":
            if f == "front":
                cx, cy = (t.fw - 1) / 2, (t.fh - 1) / 2
                d = math.hypot((t.x - cx) / cx, (t.y - cy) / cy)
                if d > 0.9:
                    return t.paint(rust, 0.55, 0.3)
                if d < 0.25:
                    return BLACK
                return t.paint(metal, 0.2 + 0.25 * ((t.x + t.y) % 2), 0.1)  # the grille
            return t.paint(rust, 0.4, 0.4)
        if tag == "shoulders":
            return leather(t, 0.5)
        if tag == "knee":
            return leather(t, 0.62)
        if tag == "finger":
            return BLACK if t.y >= t.fh - 2 else leather(t, 0.4)
        if tag in ("toe",):
            return BLACK if f == "front" else leather(t, 0.4)
        if tag in ("thigh", "shin", "arm", "forearm", "palm", "foot", "pelvis"):
            return leather(t, {"pelvis": 0.35, "foot": 0.35}.get(tag, 0.5))
        return None

    return paint_model(m, seed, fn)
