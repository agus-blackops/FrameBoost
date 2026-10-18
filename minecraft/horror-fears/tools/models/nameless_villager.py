"""The Nameless Villager.

Every villager has a job and a name. This one has neither. It stands in the
fields at the edge of what you can see, in a robe the colour of old ash, its
arms far too long, hanging to the knees, and its head tipped a little too far
to one side. Villager-sized, with the big villager nose, but the skin is grey
and dry, the brow is one heavy ridge, and the eyes are two blank white
holes with nothing in them. It never trades. It only hums. When it sees you
look at it, it raises one arm and points."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2 * side, 12, 0), cubes=[
                C((sx(0, 4), 0, -2), (4, 12, 4), "leg"),
                C((sx(-0.25, 4.25), 0, -2.25), (4.5, 1.5, 4.75), "foot")]),
        ]
    bones += [
        B("body", "root", (0, 12, 0), rotation=(4, 0, 0), cubes=[
            C((-4, 12, -3), (8, 12, 6), "torso"),
            C((-4.5, 3, -3.5), (9, 18, 7), "robe", 0.1),
            C((-4.75, 22, -3.5), (9.5, 3, 7), "collar"),
            C((-1.5, 14, -3.75), (3, 8, 0.5), "sash")]),
        B("head", "body", (0, 24, 0), rotation=(6, 0, -14), cubes=[
            C((-4, 24, -4), (8, 10, 8), "head"),
            C((-1, 22.5, -6), (2, 4, 2), "nose"),
            C((-4.25, 32.5, -4.25), (8.5, 1.5, 8.5), "brow", 0.1),
            C((-4.25, 30.5, -4.5), (8.5, 1.5, 1), "brow_ridge")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Arms far too long: to the knees.
            B(f"{name}Arm", "body", (5 * side, 22.5, 0), rotation=(0, 0, 3 * -side), cubes=[
                C((sx(4, 6), 5, -1), (2, 17.5, 2), "arm"),
                C((sx(3.75, 6.25), 15, -1.25), (2.5, 7.5, 2.5), "sleeve")]),
            B(f"{name}Hand", f"{name}Arm", (5 * side, 5, 0), cubes=[
                C((sx(3.75, 6.25), 1.5, -1.25), (2.5, 3.5, 2.5), "hand"),
                *[C((sx(3.75 + k * 0.75, 4.25 + k * 0.75), -2.5, -0.75), (0.5, 4, 0.5), "finger") for k in range(3)]]),
        ]
    return hd_model("geometry.hl.nameless_villager", bones, bounds=(1.4, 2.6, (0, 1.1, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#2e302e", "#4a4c4a", "#666866", "#808280", "#9a9c98", "#b4b6b0"])
    robe = Ramp(colors=["#1c1a18", "#2e2a26", "#443e38", "#5c544a", "#766c5e"])
    hollow = glow("#ffffff", 95)
    sunk = (0x06, 0x06, 0x08)

    def dry(t, base=0.5):
        if t.ridge(4.5, 0.045) > 0.55:
            return t.paint(skin, base - 0.28, 0.1)  # cracks
        return t.paint(skin, base, 0.3, 2.5)

    def cloth(t, base=0.5):
        if t.y % 7 == 3 and t.side:
            base -= 0.1
        if t.n2(2.5) > 0.78:
            return t.paint(robe, base - 0.25, 0.2)  # stains
        return t.paint(robe, base, 0.3, 3.0)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                # Two blank white holes under the heavy brow, nothing in them.
                for ex in (2, 9):
                    if ex <= t.x <= ex + 4 and 8 <= t.y <= 13:
                        if ex + 1 <= t.x <= ex + 3 and 9 <= t.y <= 12:
                            return hollow
                        return sunk
                if t.y == 17 and 4 <= t.x <= 11:
                    return t.paint(skin, 0.18, 0.1)  # a mouth line that never opens
                return dry(t, 0.55 - 0.012 * t.y)
            return dry(t, 0.5)
        if tag in ("brow", "brow_ridge"):
            return dry(t, 0.3) if f in ("front", "top") else t.paint(skin, 0.2, 0.1)
        if tag == "nose":
            return dry(t, 0.42)
        if tag in ("torso", "robe", "collar", "sleeve"):
            if tag == "robe" and f in ("top", "bottom"):
                return CLEAR
            if tag == "robe" and t.y >= t.fh - 3 and t.n(1.3) > 0.5:
                return CLEAR  # ragged hem
            return cloth(t, {"collar": 0.3, "torso": 0.4}.get(tag, 0.5))
        if tag == "sash":
            return t.paint(robe, 0.15, 0.2)
        if tag in ("arm", "leg"):
            return dry(t, 0.45) if tag == "arm" else cloth(t, 0.3)
        if tag in ("hand", "foot"):
            return dry(t, 0.35)
        if tag == "finger":
            return t.paint(skin, 0.5 - 0.4 * t.v, 0.1)
        return None

    return paint_model(m, seed, fn)
