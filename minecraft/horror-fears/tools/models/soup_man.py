"""Soup.AVI: the man eating soup.

A man in a blank white room, sitting hunched on a white stool with a bowl of
chunky soup in his lap, lifting the spoon to his mouth again and again. His
face is censored: a block of coarse grey and pink squares where a face should
be, the way it is in the tape. His shoulders shake. He is crying.

The stool, the bowl and the spoon are part of the model, so he can be put
down anywhere and the scene comes with him."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [
        B("root", None, (0, 0, 0)),
        # The stool: a white seat on four thin legs.
        B("stool", "root", (0, 0, 0), cubes=[
            C((-4.5, 9.5, -2), (9, 1.5, 7), "seat"),
            *[C((x, 0, z), (1, 9.5, 1), "stool_leg") for x in (-4, 3) for z in (-1.5, 3.5)],
            C((-4, 3, -1.5), (8, 0.75, 0.75), "stool_leg"), C((-4, 3, 3.75), (8, 0.75, 0.75), "stool_leg")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Sitting: the thighs go forward over the seat, the shins down.
            B(f"{name}Leg", "root", (2 * side, 11, 1), cubes=[
                C((sx(0.25, 4.25), 11, -6), (4, 4, 8), "pants")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 13, -5), cubes=[
                C((sx(0.25, 4.25), 1.5, -7), (4, 11.5, 4), "pants"),
                C((sx(0, 4.5), 0, -8.5), (4.5, 2, 5.5), "shoe")]),
        ]
    bones += [
        # Hunched over the bowl.
        B("body", "root", (0, 11, 1), rotation=(14, 0, 0), cubes=[
            C((-4, 11, -1), (8, 12, 4), "shirt"),
            C((-4.25, 21.5, -1.25), (8.5, 1.5, 4.5), "collar")]),
        B("head", "body", (0, 23, 1), rotation=(10, 0, 0), cubes=[
            C((-4, 23, -3), (8, 8, 8), "head"),
            C((-4.25, 29, -3.25), (8.5, 2.5, 8.5), "hair"),
            C((-4.25, 26.5, 2.75), (8.5, 2.5, 2.5), "hair"),
            # The censor block, floating just in front of the face.
            C((-4.75, 22.5, -3.75), (9.5, 9, 0.5), "censor")]),
        # The bowl in his lap, steaming.
        B("bowl", "root", (0, 15, -4), cubes=[
            C((-3, 15, -7), (6, 2.5, 5), "bowl"),
            C((-2.5, 14.5, -6.5), (5, 0.5, 4), "bowl"),
            C((-2.5, 17.25, -6.5), (5, 0.5, 4), "soup"),
            *[C((x, 17.5, z), (1, 0.75, 1), "chunk") for x, z in ((-1.75, -6), (0.5, -5.25), (-0.5, -4), (1.25, -6.25))]]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (5 * side, 22, 1), rotation=(-28, 0, 4 * side), cubes=[
                C((sx(4, 7), 13, -0.5), (3, 9, 3), "skin"),
                C((sx(3.75, 7.25), 18.5, -0.75), (3.5, 3.75, 3.5), "sleeve")]),
            # Left: holding the bowl. Right: the spoon, up to the mouth.
            B(f"{name}Forearm", f"{name}Arm", (5.5 * side, 13.5, 1),
              rotation=(-128, 0, 18) if side < 0 else (-70, 45, 0), cubes=[
                C((sx(4.25, 6.75), 6.5, -0.25), (2.5, 7.5, 2.5), "skin"),
                C((sx(4, 7), 4, -0.5), (3, 2.5, 3), "skin")]),
        ]
    # The spoon, in the right hand.
    bones.append(B("spoon", "rightForearm", (-5.5, 5, 1), cubes=[
        C((-6, 0.5, 0.75), (0.5, 5, 0.5), "spoon"),
        C((-6.5, -1, 0.25), (1.5, 1.75, 1.5), "spoon")]))
    return hd_model("geometry.hl.soup_man", bones, bounds=(1.6, 2.4, (0, 1.0, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#5a4034", "#7e5a48", "#a07a62", "#bf977a", "#d6b096", "#e8c8ae"])
    shirt = Ramp(colors=["#8e8a80", "#aaa69a", "#c4c0b4", "#d8d4c8", "#e8e6dc", "#f4f2ea"])
    pants = Ramp(colors=["#14161c", "#1e222a", "#2a2f3a", "#363c4a", "#444b5c"])
    white = Ramp(colors=["#b8b8b4", "#ccccc8", "#dcdcd8", "#eaeae6", "#f6f6f2"])
    soup = Ramp(colors=["#5a3412", "#7a4a1a", "#9a6224", "#b87a32", "#d09444"])
    mosaic = [(0x9a, 0x86, 0x7e), (0xc4, 0xa0, 0x90), (0x7a, 0x6a, 0x66), (0xb0, 0x92, 0x84), (0x5a, 0x50, 0x52),
              (0xd8, 0xb8, 0xa6), (0x86, 0x76, 0x74), (0xa8, 0x7a, 0x70)]

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "censor":
            if f != "front" and f != "back":
                return CLEAR
            # Coarse squares, three design pixels across, never the same twice.
            cx, cy = t.x // 3, t.y // 3
            k = (cx * 7 + cy * 13 + cx * cy * 3 + seed) % len(mosaic)
            r, g, b = mosaic[k]
            d = int((t.n(2.0) - 0.5) * 18)
            return (max(0, r + d), max(0, g + d), max(0, b + d))
        if tag == "head":
            return t.paint(skin, 0.5, 0.25)
        if tag == "hair":
            return t.paint(HAIR, 0.4, 0.5, 1.5)
        if tag in ("skin",):
            return t.paint(skin, 0.5, 0.25)
        if tag in ("shirt", "collar", "sleeve"):
            # A dirty white t-shirt, stained down the front with soup.
            if tag == "shirt" and f == "front" and t.n2(2.0) > 0.7:
                return t.paint(soup, 0.45, 0.3)
            if tag == "shirt" and f == "front" and 3 <= t.x <= 13 and 4 <= t.y <= 9 and t.n2(1.2) > 0.6:
                return t.paint(shirt, 0.15, 0.2)  # wet from the tears
            return t.paint(shirt, 0.55 if tag != "collar" else 0.4, 0.3)
        if tag == "pants":
            return t.paint(pants, 0.5, 0.3)
        if tag == "shoe":
            return t.paint(CLOTH_DARK, 0.4, 0.2)
        if tag in ("seat", "stool_leg"):
            return t.paint(white, 0.6, 0.15)
        if tag == "bowl":
            return t.paint(white, 0.55 if f != "front" else 0.5, 0.15)
        if tag == "soup":
            return t.paint(soup, 0.55, 0.5, 1.5)
        if tag == "chunk":
            return t.paint(soup if t.n(1.0) > 0.5 else skin, 0.75, 0.3)
        if tag == "spoon":
            return t.paint(IRON, 0.75, 0.2)
        return None

    return paint_model(m, seed, fn)
