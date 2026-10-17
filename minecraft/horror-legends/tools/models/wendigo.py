"""The Wendigo.

Hunger that learned to walk. Over three blocks of skin stretched blue-white
over bones, every rib showing, a mane of filthy fur on the shoulders, the
fingers and feet black with frostbite and ending in long claws. Its head is
a deer's skull with a crown of antlers wider than a door, two cold points of
light in the sockets and the long jaw stained red to the throat."""

from models.kit import *

PREVIEW = {}


def antler(side):
    s = side
    out = []
    for (x0, y, w, h) in ((1.5, 47.5, 1.5, 5), (2, 51.5, 4, 1.5), (5, 53, 1.5, 5), (5, 57, 3.5, 1), (7.5, 58, 1, 4),
                          (3.5, 53, 1, 3), (6.5, 58, 1, 2.5), (6.5, 54, 3, 1), (8.5, 55, 1, 2.5)):
        out.append(C((side_x(s, x0, x0 + w), y, -2.5), (w, h, 1), "antler"))
    return out


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2.25 * side, 24, 0), rotation=(-22, 0, 0), cubes=[
                C((sx(1, 3.5), 13, -1.25), (2.5, 11, 2.5), "thigh"),
                C((sx(0.75, 3.75), 18, -1.5), (3, 6, 3), "fur", 0.2)]),
            B(f"{name}Shin", f"{name}Leg", (2.25 * side, 13.5, 0), rotation=(55, 0, 0), cubes=[
                C((sx(1.25, 3.25), 5, -1), (2, 9, 2), "shin")]),
            B(f"{name}Foot", f"{name}Shin", (2.25 * side, 5.5, 0), rotation=(-33, 0, 0), cubes=[
                C((sx(1.25, 3.25), -1, -1), (2, 7, 2), "ankle"),
                C((sx(1, 3.5), -2, -3), (2.5, 1.5, 4), "foot"),
                *[C((sx(1 + k, 1.5 + k), -2, -5), (0.5, 0.5, 2), "claw_s") for k in range(3)]]),
        ]
    bones += [
        B("hips", "root", (0, 24, 0), cubes=[C((-3, 23, -2), (6, 4, 4), "hips")]),
        B("torso", "hips", (0, 27, 0), rotation=(30, 0, 0), cubes=[
            C((-3, 27, -2), (6, 12, 4), "torso"),
            *[C((-3.25, y, -2.25), (6.5, 0.5, 4.5), "rib_out") for y in (31, 33, 35)],
            C((-4.5, 36, -3), (9, 4, 6), "shoulders"),
            C((-5, 33, -3.25), (10, 7.5, 6.5), "mane", 0.3),
            *[C((-0.5, y, 2), (1, 1, 1), "spine") for y in (29, 31, 33, 35)]]),
        B("neck", "torso", (0, 39, -0.5), rotation=(-30, 0, 0), cubes=[
            C((-1.25, 38.5, -1.75), (2.5, 4.5, 2.5), "neck")]),
        B("skull", "neck", (0, 43, -1), rotation=(-12, 0, 0), cubes=[
            C((-2.5, 43, -4), (5, 5.5, 5.5), "skull"),
            C((-1.5, 43.5, -10), (3, 3, 6), "snout"),
            *antler(-1), *antler(1)]),
        B("jaw", "skull", (0, 44, -2), rotation=(18, 0, 0), cubes=[
            C((-1.25, 42, -9.5), (2.5, 1.5, 6.5), "jaw")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "torso", (4.25 * side, 38, 0), rotation=(-28, 0, 6 * -side), cubes=[
                C((sx(3.5, 5.5), 24, -1), (2, 14, 2), "arm")]),
            B(f"{name}Forearm", f"{name}Arm", (4.5 * side, 24, 0), rotation=(-12, 0, 0), cubes=[
                C((sx(3.75, 5.25), 11, -0.75), (1.5, 13, 1.5), "forearm"),
                C((sx(3.5, 5.5), 9, -1.25), (2, 2.5, 2.5), "hand"),
                *[C((sx(3.5 + k * 0.75, 4 + k * 0.75), 3 - (k % 2), -0.75 + (k % 2) * 0.5), (0.5, 6.5 + (k % 2), 0.5),
                    "claw") for k in range(3)]]),
        ]
    return hd_model("geometry.hl.wendigo", bones, bounds=(3.0, 4.0, (0, 1.8, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#3a4048", "#56606a", "#76808a", "#96a0a8", "#b6bec4", "#d2d8dc"])
    frost = Ramp(colors=["#060608", "#0e0e14", "#1a1a24", "#2a2a36"])
    fur = Ramp(colors=["#0e0a08", "#1a140f", "#2a2018", "#3a2e22", "#4c3e2e"])
    horn = Ramp(colors=["#2a2218", "#443828", "#62523a", "#80704e", "#a09068"])
    cold = glow("#c8e8ff", 40)

    def gaunt(t, base=0.5):
        if t.ridge(5.0, 0.045) > 0.55:
            return t.paint(skin, base - 0.3, 0.1)
        return t.paint(skin, base, 0.25, 2.5)

    def frostbitten(t, amount, base=0.5):
        """Black from the ends of the limbs upwards (amount of the face, from the bottom)."""
        if t.side and t.v > 1 - amount + (t.n(1.5) - 0.5) * 0.3:
            return t.paint(frost, 0.5, 0.4)
        return gaunt(t, base)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "torso":
            if f == "front" and t.y % 3 == 0 and t.y >= 2 and abs(t.x - 5.5) > 0.5:
                return t.paint(skin, 0.85, 0.1)
            if f == "front" and t.y % 3 == 1 and t.y >= 2:
                return t.paint(skin, 0.15, 0.1)
            if f == "front" and t.y >= t.fh - 5 and t.n(2) > 0.55:
                return t.paint(BLOOD, 0.45, 0.3)  # what it last ate, down its chest
            return gaunt(t, 0.45)
        if tag == "rib_out":
            return t.paint(skin, 0.8, 0.15) if f in ("front", "right", "left") else CLEAR
        if tag in ("mane", "fur"):
            if f in ("bottom",) or (f == "front" and tag == "mane" and t.y > 3):
                return CLEAR
            if t.y >= t.fh - 4 and t.n(1.2) > 0.4:
                return CLEAR
            v = 0.45 + (t.noise(t.sx * 2.5, t.sy * 0.4, 2.0, 1) - 0.5) * 0.5
            return t.paint(fur, v, 0.3)
        if tag == "spine":
            return t.paint(BONE, 0.55, 0.2)
        if tag in ("hips", "shoulders", "neck", "thigh", "arm"):
            return gaunt(t, {"hips": 0.4, "neck": 0.4}.get(tag, 0.5))
        if tag in ("shin", "ankle"):
            return frostbitten(t, 0.5 if tag == "shin" else 1.0, 0.45)
        if tag == "forearm":
            return frostbitten(t, 0.35, 0.45)
        if tag in ("hand", "foot"):
            return t.paint(frost, 0.45, 0.4)
        if tag in ("claw", "claw_s"):
            return t.paint(frost, 0.7 - 0.5 * t.v, 0.1)
        if tag == "skull":
            if f == "front":
                for ex in (1, 7):
                    if ex <= t.x <= ex + 1 and 3 <= t.y <= 5:
                        return cold if t.y == 4 else BLACK
            return t.paint(BONE, 0.62 - 0.12 * t.v, 0.25)
        if tag == "snout":
            if f == "front":
                return BLACK if t.y in (1, 2) and t.x in (1, 4) else t.paint(BONE, 0.45, 0.2)
            if f == "bottom" or (t.side and t.y >= t.fh - 2):
                return t.paint(BLOOD, 0.5, 0.3) if t.x % 2 else t.paint(TEETH, 0.6, 0.1)
            return t.paint(BONE, 0.6, 0.25)
        if tag == "jaw":
            if f == "top":
                return t.paint(TEETH, 0.65, 0.1) if t.x % 2 == 0 else t.paint(BLOOD, 0.3, 0.2)
            return t.paint(BLOOD, 0.4, 0.3) if t.n(2) > 0.4 else t.paint(BONE, 0.5, 0.25)
        if tag == "antler":
            return t.paint(horn, 0.55 - 0.1 * t.v + 0.15 * (f == "top"), 0.3)
        return None

    return paint_model(m, seed, fn)
