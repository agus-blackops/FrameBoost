"""The Goatman.

Something that walks on goat's legs at the edge of the woods: hunched, with
a matted black pelt from the waist down and bare grey skin above, arms too
human and too long, fingers ending in black nails. The head is a goat's
skull with the skin still on it, ram's horns curling back past the ears,
yellow eyes with the flat pupil of a goat. It screams like one, too."""

from models.kit import *

PREVIEW = {}


def horn(side, n=11, r=3.0, center=(52.5, 1.5), x=3.0):
    """A ram's horn curling back and down beside the head (side view circle)."""
    cy, cz = center
    out = []
    for i in range(n):
        a = math.radians(-30 + 260 * i / (n - 1))
        s = 2.5 - 1.5 * i / (n - 1)
        s = round(s * 2) / 2
        y, z = cy + r * math.cos(a), cz + r * math.sin(a)
        xo = side_x(side, x + 0.2 * i, x + 0.2 * i + s)
        out.append(C((xo, y - s / 2, z - s / 2), (s, s, s), f"horn{int(s * 2)}"))
    return out


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Goat legs: the thigh forward, the long shin back, the cannon bone
            # down to the hoof.
            B(f"{name}Leg", "root", (2.5 * side, 22, 0), rotation=(-25, 0, 0), cubes=[
                C((sx(0.75, 4.25), 13, -2), (3.5, 9, 4), "thigh"),
                C((sx(0.5, 4.5), 15, -2.25), (4, 7, 4.5), "pelt", 0.2)]),
            B(f"{name}Shin", f"{name}Leg", (2.5 * side, 13.5, 0), rotation=(60, 0, 0), cubes=[
                C((sx(1.25, 3.75), 5.5, -1.25), (2.5, 8, 2.5), "shin"),
                C((sx(1, 4), 9, -1.5), (3, 4.5, 3), "pelt_s", 0.2)]),
            B(f"{name}Foot", f"{name}Shin", (2.5 * side, 6, 0), rotation=(-35, 0, 0), cubes=[
                C((sx(1.5, 3.5), -1, -1), (2, 7.5, 2), "cannon"),
                C((sx(1.25, 3.75), -2, -2), (2.5, 2, 3), "hoof")]),
        ]
    bones += [
        B("body", "root", (0, 22, 0), cubes=[
            C((-3.5, 21, -2.5), (7, 4, 5), "hips"), C((-3.75, 20, -2.75), (7.5, 4, 5.5), "pelt", 0.3),
            C((-0.5, 22, 2.5), (1, 1, 3), "tail")]),
        B("torso", "body", (0, 25, 0), rotation=(22, 0, 0), cubes=[
            C((-3.5, 25, -2.25), (7, 9, 4.5), "torso"),
            C((-4.5, 32, -3), (9, 4, 6), "shoulders"),
            C((-4, 30, -3.25), (8, 3, 1), "ruff"),
            *[C((x, 29 - (i % 2), -3.75), (1, 1.5 + (i % 2), 0.5), "charm") for i, x in enumerate((-3, -1.5, 0, 1.5, 2.75))]]),
        B("neck", "torso", (0, 36, -0.5), rotation=(-25, 0, 0), cubes=[
            C((-1.5, 35, -2), (3, 4, 3), "neck")]),
        B("head", "neck", (0, 39, -1), rotation=(-8, 0, 0), cubes=[
            C((-2.5, 39, -4), (5, 5.5, 6), "head"),
            C((-1.75, 38.5, -8), (3.5, 3.5, 4), "snout"),
            C((-1.5, 38.75, -8.25), (3, 1, 0.5), "nose"),
            *[C((side_x(side, 2.5, 3.5), 42, -2), (1, 1.5, 3), "ear", pivot=(side * 2.5, 42.5, -1),
                rotation=(10, 0, -40 * side)) for side in (-1, 1)],
            *horn(-1, n=13, r=3.6, center=(44.5, 1), x=1.75), *horn(1, n=13, r=3.6, center=(44.5, 1), x=1.75),
            *[C((x, 39.25, -8), (0.5, 1, 0.5), "fang") for x in (-1.5, 1)]]),
        B("jaw", "head", (0, 39, -2), rotation=(6, 0, 0), cubes=[
            C((-1.5, 37, -7.75), (3, 1.5, 5.5), "jaw"),
            *[C((x, 38.5, -7.5), (0.5, 0.75, 0.5), "tooth") for x in (-1.25, -0.5, 0.25, 1.0)],
            *[C((x, 34 - k, -6.5 + k * 0.5), (0.5, 3 + k, 0.5), "drip") for k, x in enumerate((-0.75, 0.75))],
            C((-1, 34.5, -6), (2, 3, 1.5), "beard")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "torso", (4.25 * side, 34.5, 0), rotation=(-20, 0, 6 * -side), cubes=[
                C((sx(3.25, 5.75), 23, -1.25), (2.5, 11.5, 2.5), "arm")]),
            B(f"{name}Forearm", f"{name}Arm", (4.5 * side, 23, 0), rotation=(-20, 0, 0), cubes=[
                C((sx(3.5, 5.5), 12, -1), (2, 11, 2), "forearm"),
                C((sx(3.25, 5.75), 8.5, -1.5), (2.5, 3.5, 3), "hand"),
                *[C((sx(3.5 + k * 0.75, 4 + k * 0.75), 2, -1.25 + (k % 2) * 0.5), (0.5, 6.5, 0.5), "nail")
                  for k in range(3)]]),
        ]
    return hd_model("geometry.hl.goatman", bones, bounds=(2.5, 3.2, (0, 1.4, 0)))


def paint(seed, m, **_):
    fur = Ramp(colors=["#0a0806", "#140f0b", "#1f1812", "#2c231a", "#3a2f24", "#4a3e30"])
    skin = Ramp(colors=["#2c2624", "#403634", "#564a46", "#6e605a", "#86766e", "#9e8c82"])
    horn_r = Ramp(colors=["#1e1a14", "#342c22", "#4c4232", "#665a44", "#807258"])
    gold = glow("#ffd21a", 85)

    def pelt(t, base=0.45):
        # Long matted locks: streaks down the fur, ragged at the ends.
        v = base + (t.noise(t.sx * 2.5, t.sy * 0.4, 2.0, 1) - 0.5) * 0.5
        return t.paint(fur, v, 0.3)

    def bare(t, base=0.5):
        if t.ridge(4.0, 0.05) > 0.55:
            return t.paint(skin, base - 0.25, 0.1)  # veins, scars
        return t.paint(skin, base, 0.3, 2.0)

    def fn(t):
        tag, f = t.tag, t.face
        if tag in ("pelt", "pelt_s"):
            if f in ("top", "bottom"):
                return CLEAR
            if t.y >= t.fh - 3 and t.n(1.2) > 0.45:
                return CLEAR  # ragged ends
            return pelt(t, 0.5)
        if tag in ("thigh", "shin", "hips"):
            return pelt(t, 0.4)
        if tag == "cannon":
            return pelt(t, 0.3) if t.y < t.fh // 2 else t.paint(skin, 0.3, 0.2)
        if tag == "hoof":
            return t.paint(horn_r, 0.2, 0.2) if f != "bottom" else BLACK
        if tag == "tail":
            return pelt(t, 0.35)
        if tag in ("torso", "shoulders", "neck"):
            if f in ("back", "top") or tag == "shoulders" or (tag == "neck" and f != "front"):
                return pelt(t, 0.45)  # the pelt runs up his back
            if tag == "torso" and f == "front" and t.y % 3 == 0 and 2 <= t.y <= 12 and abs(t.x - 6.5) > 1:
                return t.paint(skin, 0.75, 0.1)  # ribs
            if f in ("right", "left") and t.n(2) > 0.45:
                return pelt(t, 0.4)
            return bare(t, 0.45 if tag != "neck" else 0.4)
        if tag == "ruff":
            return pelt(t, 0.55) if t.n(1.5) > 0.3 else CLEAR
        if tag == "head":
            if f in ("right", "left"):
                # Goat eyes on the sides of the skull: yellow, a flat black bar.
                ex = t.fw - 4 if f == "right" else 3
                if abs(t.x - ex) <= 1 and 3 <= t.y <= 4:
                    return BLACK if t.y == 4 or t.x == ex else gold
                if abs(t.x - ex) <= 2 and 2 <= t.y <= 5:
                    return t.paint(skin, 0.15, 0.1)
            if f == "front":
                if t.y in (4, 5) and (t.x <= 1 or t.x >= t.fw - 2):
                    return BLACK if t.y == 5 else gold
                return bare(t, 0.35)
            if f in ("top", "back"):
                return pelt(t, 0.45)
            return bare(t, 0.5)
        if tag == "snout":
            if f == "front":
                return t.paint(skin, 0.3, 0.2)
            if t.side and t.v > 0.55 and t.n(2) > 0.3:
                return t.paint(BLOOD, 0.45, 0.25)
            if f == "left" and 1 <= t.x <= 4 and 1 <= t.y <= 3:
                return t.paint(BONE, 0.5, 0.2) if t.y != 2 or t.x % 2 else t.paint(TEETH, 0.7, 0.1)
            return bare(t, 0.55 - 0.1 * t.v)
        if tag == "nose":
            return BLACK if t.x in (1, t.fw - 2) else t.paint(skin, 0.2, 0.1)
        if tag == "ear":
            return t.paint(skin, 0.35, 0.2) if f in ("right", "left") else pelt(t, 0.4)
        if tag.startswith("horn"):
            ridged = (t.y if f in ("right", "left", "front", "back") else t.x) % 2 == 0
            return t.paint(horn_r, 0.55 + (0.12 if ridged else -0.1), 0.2)
        if tag == "jaw":
            if f == "top":
                return t.paint(TEETH, 0.5, 0.2) if t.x % 2 == 0 else BLOOD.colors[1]
            return bare(t, 0.4)
        if tag == "fang":
            return t.paint(TEETH, 0.7 - 0.3 * t.v, 0.1)
        if tag == "drip":
            return t.paint(BLOOD, 0.5 - 0.3 * t.v, 0.25) if t.n(2) > 0.2 else CLEAR
        if tag == "charm":
            return t.paint(BONE, 0.6, 0.2) if t.n(3) > 0.3 else t.paint(TEETH, 0.4, 0.1)
        if tag == "tooth":
            return t.paint(TEETH, 0.6 - 0.2 * t.v, 0.1)
        if tag == "beard":
            return pelt(t, 0.4) if t.n(1.2) > 0.25 else CLEAR
        if tag in ("arm", "forearm", "hand"):
            return bare(t, 0.45 if tag == "arm" else 0.4)
        if tag == "nail":
            return t.paint(horn_r, 0.4 - 0.35 * t.v, 0.1)
        return None

    return paint_model(m, seed, fn)
