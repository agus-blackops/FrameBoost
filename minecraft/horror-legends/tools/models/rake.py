"""The Rake.

Something seen in fields at night and at the foot of beds: a hairless,
starved thing the colour of a corpse, the size of a man but never standing
like one. It crouches on its haunches with its knees up by its ears and its
arms hanging to the ground between them, long fingers ending in claws like
the tines of a rake. The head is round and smooth, with two big wet black
eyes and a small, lipless mouth full of little teeth. Every vertebra shows.
It runs on all fours (animations), faster than you."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [
        B("root", None, (0, 0, 0)),
        B("hips", "root", (0, 6, 1), cubes=[
            C((-3, 4, -1), (6, 4, 4), "pelvis")]),
        B("torso", "hips", (0, 8, 1), rotation=(26, 0, 0), cubes=[
            C((-2.25, 8, -0.75), (4.5, 5, 3.5), "waist"),
            C((-3.5, 13, -1.25), (7, 7, 4.5), "chest"),
            *[C((-3.75, y, -1.5), (7.5, 0.5, 3), "rib_out") for y in (14, 15.5, 17)],
            *[C((-0.5, y, 3), (1, 1, 1), "vertebra") for y in (9, 11, 13, 15, 17, 19)],
            C((-4.5, 18, -1.25), (9, 2.5, 4), "shoulders")]),
        B("neck", "torso", (0, 20.5, 1), rotation=(-40, 0, 0), cubes=[
            C((-1, 20, 0), (2, 3, 2), "neck")]),
        B("head", "neck", (0, 23, 1), rotation=(10, 0, 0), cubes=[
            C((-3, 23, -2.5), (6, 6.5, 6), "head"),
            C((-2.5, 29.5, -2), (5, 0.5, 5), "crown"),
            C((-1, 23.75, -2.75), (2, 1.25, 0.25), "mouth")]),
    ]
    for side, s in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Arms hang straight down between the knees to the ground.
            B(f"{s}Arm", "torso", (4.25 * side, 19, 0.75), rotation=(-40, 0, 4 * -side), cubes=[
                C((sx(3.5, 5), 9, 0), (1.5, 10, 1.5), "arm"),
                C((sx(3.25, 5.25), 8.5, -0.25), (2, 1.5, 2), "elbow")]),
            B(f"{s}Forearm", f"{s}Arm", (4.25 * side, 9.5, 0.75), rotation=(-26, 0, 0), cubes=[
                C((sx(3.6, 4.9), 0.5, 0.15), (1.25, 9, 1.25), "forearm")]),
            B(f"{s}Hand", f"{s}Forearm", (4.25 * side, 1, 0.75), rotation=(40, 0, 0), cubes=[
                C((sx(3.25, 5.25), -0.5, -0.75), (2, 1.5, 2.5), "palm"),
                *[C((sx(3.25 + k * 0.75, 3.75 + k * 0.75), -0.5, -6.25 + (k % 2)), (0.5, 0.5, 5.5 - (k % 2)), "claw")
                  for k in range(3)]]),
            # Legs folded tight: thigh forward and up to a knee by the
            # shoulders, shin straight down to a long foot.
            B(f"{s}Thigh", "hips", (2 * side, 6, 1), rotation=(40, 0, 12 * -side), cubes=[
                C((sx(1, 3), 6, 0), (2, 11, 2.5), "thigh")]),
            B(f"{s}Shin", f"{s}Thigh", (2 * side, 16.5, 1.25), rotation=(8, 0, 0), cubes=[
                C((sx(1.15, 2.85), 6.5, 0.4), (1.7, 10.5, 1.7), "shin"),
                C((sx(1, 3), 15.5, 0.25), (2, 2, 2), "knee")]),
            B(f"{s}Foot", f"{s}Shin", (2 * side, 7, 1.25), rotation=(-55, 0, 0), cubes=[
                C((sx(1, 3), 2.5, 0.25), (2, 5, 1.5), "foot"),
                *[C((sx(1 + k * 0.75, 1.5 + k * 0.75), 1, 0.5), (0.5, 2, 0.5), "toe") for k in range(3)]]),
        ]
    return hd_model("geometry.hl.rake", bones, bounds=(2.0, 2.0, (0, 0.9, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#5c5a56", "#7e7a74", "#9c968e", "#b8b2a8", "#d0cac0", "#e4e0d6"])
    vein = Ramp(colors=["#40384a", "#564a60", "#6a5e74"])
    dirt = Ramp(colors=["#1a1410", "#2a2018", "#3a2e22"])
    gloss = glow("#c8d0e0", 60)

    def corpse(t, base=0.55):
        if t.ridge(6.0, 0.035) > 0.55:
            return t.paint(vein, 0.5, 0.2)
        if t.n2(2.0) > 0.8:
            return t.paint(skin, base - 0.22, 0.15)  # blotches, like lividity
        return t.paint(skin, base, 0.25, 2.5)

    def soiled(t, amount, base=0.5):
        if t.side and t.v > 1 - amount + (t.n(1.5) - 0.5) * 0.3:
            return t.paint(dirt, 0.5, 0.4)
        return corpse(t, base)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            if f == "front":
                # Two huge black eyes, wet, with a pale point of light.
                for ex in (2, 8):
                    dx, dy = t.x - (ex + 0.5), t.y - 4.5
                    if dx * dx / 3.2 + dy * dy / 4.6 <= 1.0:
                        if t.x == ex and t.y == 3:
                            return gloss
                        return BLACK if dy > -1.5 else (0x14, 0x12, 0x16)
                    if dx * dx / 5.0 + dy * dy / 6.5 <= 1.0:
                        return t.paint(skin, 0.25, 0.1)  # sunken lids
                if t.y in (7, 8) and t.x in (5, 6):
                    return t.paint(skin, 0.3, 0.1)  # two slits for a nose
                return corpse(t, 0.62 - 0.02 * t.y)
            if f == "top":
                return corpse(t, 0.6)
            return corpse(t, 0.55 - 0.15 * t.v)
        if tag == "crown":
            return corpse(t, 0.62) if f == "top" else CLEAR
        if tag == "mouth":
            if f != "front":
                return CLEAR
            # A small round hole full of little teeth.
            if t.y in (0, t.fh - 1):
                return t.paint(TEETH, 0.6, 0.1) if t.x % 2 == 0 else BLACK
            return BLACK if 1 <= t.x <= t.fw - 2 else t.paint(BLOOD, 0.3, 0.1)
        if tag == "chest":
            if f in ("front", "right", "left") and t.y % 3 == 1 and 2 <= t.y <= 11:
                return t.paint(skin, 0.82, 0.1)  # ribs
            if f in ("front", "right", "left") and t.y % 3 == 2 and 2 <= t.y <= 11:
                return t.paint(skin, 0.18, 0.1)
            return corpse(t, 0.5)
        if tag == "rib_out":
            return t.paint(skin, 0.78, 0.1) if f in ("front", "right", "left") else CLEAR
        if tag == "waist":
            if f == "front" and t.n(2) > 0.6:
                return t.paint(skin, 0.3, 0.1)  # sunken belly
            return corpse(t, 0.42)
        if tag == "vertebra":
            return t.paint(BONE, 0.62, 0.2)
        if tag in ("pelvis", "shoulders", "neck", "arm", "thigh"):
            return corpse(t, {"pelvis": 0.42, "neck": 0.45}.get(tag, 0.52))
        if tag in ("elbow", "knee"):
            return t.paint(skin, 0.75, 0.15)
        if tag in ("forearm", "shin"):
            return soiled(t, 0.3, 0.48)
        if tag in ("palm", "foot"):
            return t.paint(dirt, 0.45, 0.4) if t.n(2) > 0.4 else corpse(t, 0.35)
        if tag == "claw":
            if t.v > 0.75 or t.n(2) > 0.7:
                return t.paint(BLOOD, 0.3, 0.2)
            return t.paint(TEETH, 0.4 - 0.2 * t.u, 0.15)
        if tag == "toe":
            return t.paint(dirt, 0.25, 0.2)
        return None

    return paint_model(m, seed, fn)
