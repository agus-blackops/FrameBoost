"""The Knocker.

The thing that learns where you sleep. A pale, starved man, a head taller
than you and bent forward so it can look in through windows: bald, grey-white
skin pulled tight over every bone, grey trousers gone to rags, bare feet.
Its knuckles are swollen and split from knocking. The eyes are sunk deep in
dark sockets with a pin point of pupil, and the mouth has no lips left:
just teeth, in a grin it can't stop making."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2 * side, 19, 0), rotation=(-6, 0, 0), cubes=[
                C((sx(0.5, 3.5), 10, -1.5), (3, 9.5, 3), "rags"),
                C((sx(0.75, 3.25), 10, -1.25), (2.5, 9, 2.5), "skin")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 10, 0), rotation=(10, 0, 0), cubes=[
                C((sx(0.9, 3.1), 1.25, -1.1), (2.2, 9, 2.2), "skin"),
                C((sx(0.5, 3.5), 0, -3.25), (3, 1.5, 4.5), "skin"),
                *[C((sx(0.6 + k * 0.85, 1.2 + k * 0.85), 0, -3.75), (0.6, 0.75, 0.75), "nail") for k in range(3)]]),
        ]
    ribs = [C((-3.75, 25 + k * 1.6, -2.15), (7.5, 0.7, 0.4), "rib") for k in range(5)]
    bones += [
        B("body", "root", (0, 19, 0), rotation=(-16, 0, 0), cubes=[
            C((-3.5, 19, -1.75), (7, 4.5, 3.5), "rags"),
            C((-2.75, 22.5, -1.5), (5.5, 4, 3), "skin"),
            C((-4, 26, -2), (8, 8, 4), "skin"), *ribs,
            C((-4.5, 32.5, -1.75), (9, 2, 3.5), "skin"),
            C((-0.75, 28, 1.6), (1.5, 6, 0.75), "spine")]),
        B("neck", "body", (0, 34, 0), rotation=(-14, 0, 0), cubes=[C((-1, 33.5, -1), (2, 4, 2), "skin")]),
        B("head", "neck", (0, 37.5, 0), rotation=(22, 0, 8), cubes=[
            C((-3.5, 37.5, -3.75), (7, 8.5, 7), "head"),
            C((-2.5, 46, -2.75), (5, 0.75, 5), "head")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (5 * side, 33, 0), rotation=(-10, 0, 6 * -side), cubes=[
                C((sx(4, 6.25), 21, -1.1), (2.25, 12.5, 2.2), "skin")]),
            B(f"{name}Forearm", f"{name}Arm", (5.1 * side, 21.5, 0), rotation=(-20, 0, 0), cubes=[
                C((sx(4.1, 6.1), 11.5, -1), (2, 10.5, 2), "skin")]),
            B(f"{name}Hand", f"{name}Forearm", (5.1 * side, 11.5, 0), cubes=[
                C((sx(3.8, 6.4), 8.5, -1.3), (2.6, 3, 2.6), "knuckles"),
                *[C((sx(3.9 + k * 0.65, 4.4 + k * 0.65), 3.5 + (k in (0, 3)) * 1.0, -1.2 + k * 0.5), (0.5, 5 - (k in (0, 3)), 0.5),
                    "finger") for k in range(4)]]),
        ]
    return hd_model("geometry.hb.knocker", bones, bounds=(2.0, 3.0, (0, 1.4, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#5a5c60", "#76787a", "#929290", "#aaa8a2", "#c2beb4", "#d8d2c6"])
    rags = Ramp(colors=["#24221e", "#34312b", "#46423a", "#5a554a"])
    sore = Ramp(colors=["#3a0c0e", "#5c1618", "#7a2622", "#8e3a30"])
    hole = (0x02, 0x02, 0x03)
    pin = glow("#e8e8e8", 70)

    def starved(t, base=0.55):
        if t.ridge(4.0, 0.04) > 0.7:
            return t.paint(Ramp(colors=["#4a5262", "#5c6474", "#707888"]), 0.4, 0.1)  # veins
        if t.n2(2.2) > 0.8:
            return t.paint(skin, base - 0.3, 0.2)  # bruises
        return t.paint(skin, base, 0.3, 2.0)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front" and t.fh >= 14:
                # 14x17: deep dark sockets with a pin point, a lipless grin.
                for cx in (3.5, 10):
                    dx, dy = t.x - cx, t.y - 6
                    if dx * dx / 6 + dy * dy / 5 <= 1:
                        return pin if (round(dx), round(dy)) == (0, -1) else hole
                    if dx * dx / 10 + dy * dy / 8 <= 1:
                        return t.paint(skin, 0.15, 0.1)  # the sunken dark round the eyes
                if t.y in (10, 11) and 5 <= t.x <= 8:
                    return t.paint(skin, 0.2, 0.1)  # nose slits
                if 12 <= t.y <= 14 and 2 <= t.x <= 11:
                    if t.y in (12, 14):
                        return hole
                    return t.paint(TEETH, 0.45, 0.2) if t.x % 2 else hole
            return starved(t, 0.55)
        if tag in ("skin", "spine"):
            return starved(t, 0.5 if tag == "skin" else 0.35)
        if tag == "rib":
            return t.paint(skin, 0.75, 0.1)
        if tag == "rags":
            if t.side and t.y > t.fh - 3 and t.n(1.2) > 0.4:
                return CLEAR
            return t.paint(rags, 0.45, 0.4)
        if tag == "knuckles":
            return t.paint(sore, 0.55, 0.3) if t.n(1.5) > 0.4 else starved(t, 0.45)
        if tag == "finger":
            return starved(t, 0.5 - 0.3 * t.v)
        if tag == "nail":
            return t.paint(Ramp(colors=["#5a5040", "#7a6c50"]), 0.4, 0.1)
        return None

    return paint_model(m, seed, fn)
