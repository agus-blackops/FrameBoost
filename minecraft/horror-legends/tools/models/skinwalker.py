"""The Skinwalker.

Two forms in one model; the render controller shows one or the other
(hl:revealed). As a cow it is almost right: the patches, the horns, the
slow walk; only the eyes are wrong (a human's, white, always on you) and
when it opens its mouth the teeth are human too. Revealed, it stands up: a
tall, starved, hairless thing the colour of old leather, with the cow's hide
worn over its back like a cloak and the cow's skull over its face, two
white points of light in the sockets. Its arms reach its knees."""

from models.kit import *

PREVIEW = {}
COW = ("cow", "cowHead", "cowLegFR", "cowLegFL", "cowLegBR", "cowLegBL")
WALKER = ("walker", "hips", "torso", "hide", "skull", "jaw", "rightArm", "rightForearm", "leftArm", "leftForearm",
          "rightLeg", "rightShin", "leftLeg", "leftShin")


def cow_bones():
    bones = [
        B("cow", "root", (0, 0, 0)),
        B("cowBody", "cow", (0, 12, 0), cubes=[
            C((-6, 10, -8), (12, 10, 18), "cow_body"), C((-2, 9, 4), (4, 1, 4), "udder")]),
        B("cowHead", "cowBody", (0, 18, -8), cubes=[
            C((-4, 14, -14), (8, 8, 6), "cow_head"),
            C((-2, 14, -15), (4, 3, 1), "cow_muzzle"),
            C((-5, 21, -12), (1, 3, 1), "cow_horn"), C((4, 21, -12), (1, 3, 1), "cow_horn")]),
    ]
    for name, x, z in (("cowLegFR", -4, -6), ("cowLegFL", 4, -6), ("cowLegBR", -4, 7), ("cowLegBL", 4, 7)):
        bones.append(B(name, "cowBody", (x, 12, z), cubes=[C((x - 2, 0, z - 2), (4, 12, 4), "cow_leg")]))
    return bones


def walker_bones():
    bones = [
        B("walker", "root", (0, 0, 0)),
        B("hips", "walker", (0, 25, 0), cubes=[C((-3.5, 24, -2.25), (7, 4, 4.5), "sw_hips")]),
        B("torso", "hips", (0, 28, 0), rotation=(24, 0, 0), cubes=[
            C((-3.5, 28, -2), (7, 12, 4), "sw_torso"),
            C((-4.5, 37, -2.5), (9, 3, 5), "sw_shoulders")]),
        # The hide, worn like a cloak.
        B("hide", "torso", (0, 40, 2), rotation=(-18, 0, 0), cubes=[
            C((-5.5, 20, 2), (11, 20, 1), "hide_back"),
            C((-6, 22, -2.5), (0.75, 18, 4.5), "hide_side"), C((5.25, 22, -2.5), (0.75, 18, 4.5), "hide_side")]),
        B("skull", "torso", (0, 40, -0.5), rotation=(-20, 0, 0), cubes=[
            C((-1.25, 39.5, -1.5), (2.5, 2.5, 2.5), "sw_neck"),
            C((-3, 41.5, -4.5), (6, 5.5, 6), "skull"),
            C((-1.5, 41.5, -9.5), (3, 3, 5), "skull_snout"),
            *[C((side_x(s, 3, 6.5), 46, -3), (3.5, 1, 1), "skull_horn") for s in (-1, 1)],
            *[C((side_x(s, 6, 7), 46.5, -3), (1, 2.5, 1), "skull_horn") for s in (-1, 1)]]),
        B("jaw", "skull", (0, 42, -2), rotation=(12, 0, 0), cubes=[
            C((-1.25, 40, -9), (2.5, 1.5, 6.5), "sw_jaw")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "hips", (2 * side, 25, 0), rotation=(-18, 0, 0), cubes=[
                C((sx(0.75, 3.25), 12, -1.25), (2.5, 13, 2.5), "sw_thigh")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 12.5, 0), rotation=(26, 0, 0), cubes=[
                C((sx(1, 3), 1, -1), (2, 12, 2), "sw_shin"),
                C((sx(0.75, 3.25), 0, -5), (2.5, 1.5, 6), "sw_foot")]),
            B(f"{name}Arm", "torso", (4.5 * side, 38.5, 0), rotation=(-26, 0, 5 * -side), cubes=[
                C((sx(3.5, 5.5), 25, -1), (2, 14, 2), "sw_arm")]),
            B(f"{name}Forearm", f"{name}Arm", (4.5 * side, 25, 0), rotation=(-10, 0, 0), cubes=[
                C((sx(3.75, 5.25), 12, -0.75), (1.5, 13, 1.5), "sw_fore"),
                *[C((sx(3.5 + k * 0.5, 4 + k * 0.5), 4, -0.5 + (k % 2) * 0.5), (0.5, 8, 0.5), "sw_claw")
                  for k in range(4)]]),
        ]
    return bones


def model():
    return hd_model("geometry.hl.skinwalker", [B("root", None, (0, 0, 0))] + cow_bones() + walker_bones(),
                    bounds=(2.5, 3.2, (0, 1.4, 0)))


def paint(seed, m, **_):
    brown = Ramp(colors=["#1e120a", "#2e1c10", "#402818", "#543620", "#68462c"])
    white = Ramp(colors=["#8a8680", "#a8a49c", "#c4c0b6", "#dcd8ce", "#eeebe2"])
    leather = Ramp(colors=["#2a221c", "#3e3228", "#544436", "#6c5a48", "#84705a", "#9c8870"])
    nose = Ramp(colors=["#5a3a38", "#7a5250", "#9a6c68"])
    pin = glow("#f8f8f0", 85)

    def cowhide(t, base=0.5):
        if t.noise(t.sx, t.sy, 7.0, 2) > 0.55:
            return t.paint(white, base + 0.1, 0.2, 2.0)
        return t.paint(brown, base, 0.3, 2.0)

    def skin(t, base=0.5):
        if t.ridge(4.5, 0.05) > 0.55:
            return t.paint(leather, base - 0.3, 0.1)
        return t.paint(leather, base, 0.3, 2.0)

    def fn(t):
        tag, f = t.tag, t.face
        # The cow.
        if tag in ("cow_body", "cow_leg"):
            if tag == "cow_leg" and t.y >= t.fh - 3:
                return t.paint(brown, 0.15, 0.1)  # hooves
            return cowhide(t)
        if tag == "udder":
            return t.paint(nose, 0.6, 0.2)
        if tag == "cow_head":
            if f == "front":
                # Human eyes: white, with small pupils that follow you.
                if t.y in (5, 6) and t.x in (2, 3, 12, 13):
                    return (0xF2, 0xF0, 0xEA) if not (t.y == 6 and t.x in (3, 12)) else (0x10, 0x0C, 0x0A)
                if t.y == 4 and t.x in (2, 3, 12, 13):
                    return t.paint(brown, 0.1, 0.1)
                if t.y == 7 and t.x in (2, 13):
                    return BLOOD.colors[3]  # red at the corner of each eye
                if t.y >= 12:
                    return (0x1A, 0x10, 0x0C) if t.y == 13 and 4 <= t.x <= 11 else cowhide(t, 0.45)
            return cowhide(t, 0.5)
        if tag == "cow_muzzle":
            if f == "front" and t.y == t.fh - 1 and 1 <= t.x <= t.fw - 2:
                return t.paint(TEETH, 0.7, 0.1) if t.x % 2 else (0x2A, 0x10, 0x0E)  # human teeth
            if f == "front" and t.y == 1 and t.x in (1, t.fw - 2):
                return (0x1A, 0x0E, 0x0C)
            return t.paint(nose, 0.5, 0.2)
        if tag == "cow_horn":
            return t.paint(white, 0.6 - 0.3 * t.v, 0.1)
        # The walker.
        if tag in ("hide_back", "hide_side"):
            if f in ("top", "bottom"):
                return BLOOD.colors[1]
            if tag == "hide_back" and f == "front":
                return t.paint(BLOOD, 0.35, 0.4)  # the raw inside of the skin
            if t.y >= t.fh - 5 and t.n(1.4) > 0.5:
                return CLEAR  # torn edge
            if t.n2(2.0) > 0.78:
                return t.paint(BLOOD, 0.4, 0.2)
            return cowhide(t, 0.45)
        if tag in ("skull", "skull_snout"):
            if tag == "skull" and f == "front":
                # Sockets, a point of light in each.
                for ex in (2, 9):
                    if ex <= t.x <= ex + 2 and 3 <= t.y <= 6:
                        return pin if (t.x == ex + 1 and t.y == 4) else BLACK
                    if t.x == ex + 1 and 7 <= t.y <= 9 + (ex % 3):
                        return t.paint(BLOOD, 0.4, 0.2)  # it bleeds into the bone
            if tag == "skull_snout" and f == "front" and t.y in (1, 2) and t.x in (1, 4):
                return BLACK  # nostrils
            return t.paint(BONE, 0.6 - 0.1 * t.v, 0.25)
        if tag == "skull_horn":
            return t.paint(white, 0.5, 0.2)
        if tag == "sw_jaw":
            if f == "top":
                return t.paint(TEETH, 0.6, 0.1) if t.x % 2 == 0 else BLACK
            return t.paint(BONE, 0.45, 0.25)
        if tag == "sw_torso":
            if f == "front" and t.y % 3 == 0 and t.y >= 3 and abs(t.x - 6.5) > 1:
                return t.paint(leather, 0.85, 0.1)  # ribs
            if f == "front" and t.y % 3 == 1 and t.y >= 3:
                return t.paint(leather, 0.2, 0.1)
            return skin(t, 0.5)
        if tag in ("sw_hips", "sw_shoulders", "sw_neck", "sw_thigh", "sw_shin", "sw_foot", "sw_arm", "sw_fore"):
            return skin(t, {"sw_hips": 0.4, "sw_foot": 0.35, "sw_neck": 0.35}.get(tag, 0.5))
        if tag == "sw_claw":
            return BLACK if t.v > 0.5 else t.paint(leather, 0.2, 0.1)
        return None

    return paint_model(m, seed, fn)
