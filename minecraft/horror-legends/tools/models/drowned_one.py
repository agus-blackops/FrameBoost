"""The Drowned One.

Whoever went into the water and didn't come out. Swollen, blue-grey skin
wrinkled by the water, a torn nightdress gone green at the hem, kelp tangled
round the arms and legs, barnacles on the shoulders. Black hair hangs in a
wet curtain over the face and down to the waist; through a gap in it, one
pale eye. It walks with its head tipped to one side and its arms held out,
dripping."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2 * side, 13, 0), cubes=[
                C((sx(0.5, 3.5), 1, -1.5), (3, 12, 3), "leg"),
                C((sx(0.25, 3.75), 0, -3), (3.5, 1.5, 4.5), "foot"),
                C((sx(0.25, 3.75), 3, -1.75), (3.5, 1, 3.5), "kelp_band", 0.1)]),
        ]
    bones += [
        B("body", "root", (0, 13, 0), rotation=(4, 0, 0), cubes=[
            C((-4, 13, -2.25), (8, 11, 4.5), "torso"),
            C((-4.5, 7, -2.75), (9, 9, 5.5), "dress_low"),
            C((-4.25, 13, -2.5), (8.5, 11, 5), "dress", 0.1),
            C((-4.5, 21.5, -2), (2, 1.5, 2), "barnacle"), C((3, 22, -1), (1.5, 1, 1.5), "barnacle"),
            C((2.5, 17, -2.75), (1, 1, 1), "barnacle")]),
        B("head", "body", (0, 24, 0), rotation=(10, 0, -18), cubes=[
            C((-3.5, 24, -3.5), (7, 7.5, 7), "head"),
            C((-4, 24.5, -4), (8, 7.5, 8), "hair", 0.2),
            C((-4, 31.5, -4), (8, 0.5, 8), "hair_top", 0.2)]),
        B("hairFront", "head", (0, 31, -4), cubes=[
            C((-3.5, 14, -4.75), (7, 17, 0.5), "hair_curtain")]),
        B("hairBack", "head", (0, 30, 4), cubes=[
            C((-4, 13, 3.75), (8, 17, 0.75), "hair_back")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (5 * side, 23, 0), rotation=(-55, 0, 10 * -side), cubes=[
                C((sx(4, 6.5), 12, -1.25), (2.5, 11, 2.5), "arm"),
                C((sx(3.75, 6.75), 15, -1.5), (3, 1, 3), "kelp_band", 0.1)]),
            B(f"{name}Forearm", f"{name}Arm", (5.25 * side, 12.5, 0), rotation=(-20, 0, 0), cubes=[
                C((sx(4.25, 6.25), 3, -1), (2, 10, 2), "forearm"),
                C((sx(4, 6.5), 1, -1.25), (2.5, 2.5, 2.5), "hand"),
                *[C((sx(4.25 + k * 0.75, 4.75 + k * 0.75), -3, -0.75), (0.5, 4.5, 0.5), "finger") for k in range(3)]]),
            B(f"{name}Kelp", f"{name}Forearm", (5.25 * side, 8, 0), cubes=[
                C((sx(4.75, 5.75), -2, 1), (1, 10, 0.5), "kelp")]),
        ]
    return hd_model("geometry.hl.drowned_one", bones, bounds=(2.0, 2.6, (0, 1.2, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#2e3c44", "#44565e", "#5e7278", "#7a8e90", "#96a8a6", "#b2c0bc"])
    hair = Ramp(colors=["#010203", "#050709", "#0b0e12", "#12171c", "#1c242a"])
    gown = Ramp(colors=["#4a5046", "#6a7062", "#8a8e7e", "#a8aa98", "#c2c2ae"])
    algae = Ramp(colors=["#10200e", "#1c3418", "#2a4a22", "#3c602c"])
    kelp = Ramp(colors=["#14220a", "#22380e", "#325014", "#44681c"])
    eye = glow("#d8f0f0", 40)

    def swollen(t, base=0.5):
        if t.ridge(3.0, 0.05) > 0.55:
            return t.paint(skin, base - 0.2, 0.1)  # wrinkles
        if t.n2(2.5) > 0.78:
            return t.paint(algae, 0.4, 0.2)  # slime
        return t.paint(skin, base, 0.3, 2.5)

    def wet_hair(t, base=0.45):
        # Long strands: vertical streaks with a wet shine on some.
        s = t.noise(t.X * 3.0, t.Y * 0.2, 2.0, 1)
        if s > 0.8:
            return t.paint(hair, 0.9, 0.1)
        return t.paint(hair, base + (s - 0.5) * 0.4, 0.2)

    def dress(t, base=0.55):
        if t.side and t.v > 0.55 + (t.n(1.5) - 0.5) * 0.4:
            return t.paint(algae, 0.5, 0.4)  # gone green from the water line down
        if t.n2(3) > 0.72:
            return t.paint(gown, base - 0.25, 0.2)
        return t.paint(gown, base, 0.3, 2.5)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "head":
            return swollen(t, 0.5)
        if tag in ("hair", "hair_top"):
            if tag == "hair_top":
                return wet_hair(t, 0.35) if f == "top" else CLEAR
            if f in ("front", "bottom", "top"):
                return CLEAR
            return wet_hair(t, 0.4)
        if tag == "hair_curtain":
            if f == "front" or f == "back":
                # A gap in the curtain, and the eye behind it.
                if 3 <= t.x <= 5 and 5 <= t.y <= 9:
                    if t.x == 4 and t.y in (7, 8):
                        return eye
                    return CLEAR if f == "front" else t.paint(skin, 0.2, 0.1)
                if t.y >= t.fh - 4 and t.n(1.2) > 0.4:
                    return CLEAR  # ragged ends
                return wet_hair(t, 0.45)
            return wet_hair(t, 0.3)
        if tag == "hair_back":
            if t.y >= t.fh - 5 and t.n(1.2) > 0.4:
                return CLEAR
            return wet_hair(t, 0.4)
        if tag == "torso":
            return swollen(t, 0.45)
        if tag == "dress":
            if f in ("top", "bottom"):
                return CLEAR
            if f == "front" and t.y < 3 and 4 <= t.x <= 12:
                return CLEAR  # neckline
            if t.n(1.5) > 0.8:
                return CLEAR  # tears in the cloth
            return dress(t, 0.55)
        if tag == "dress_low":
            if f == "top":
                return CLEAR
            if f == "bottom":
                return t.paint(algae, 0.2, 0.2)
            if t.y >= t.fh - 4 and t.n(1.2) > 0.45:
                return CLEAR
            return dress(t, 0.5)
        if tag == "barnacle":
            if f == "top" and t.x == t.fw // 2 and t.y == t.fh // 2:
                return BLACK
            return t.paint(BONE, 0.45, 0.4)
        if tag in ("leg", "arm", "forearm", "hand", "foot"):
            return swollen(t, 0.48 if tag not in ("hand", "foot") else 0.4)
        if tag == "finger":
            return t.paint(skin, 0.25, 0.2) if t.v < 0.7 else BLACK
        if tag in ("kelp_band", "kelp"):
            if tag == "kelp_band" and f in ("top", "bottom"):
                return CLEAR
            if tag == "kelp" and t.n(1.2) > 0.75:
                return CLEAR
            return t.paint(kelp, 0.45, 0.4, 1.5)
        return None

    return paint_model(m, seed, fn)
