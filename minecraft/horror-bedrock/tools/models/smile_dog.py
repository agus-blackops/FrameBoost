"""Smile Dog (smile.jpg).

A Siberian husky, grey and white, sitting in the dark of a room. A dog,
nothing more, until you see the face: the muzzle is pulled back into a human
smile, wide, full of human teeth, rows of them, and the eyes are wet and
black and catch the light like a photograph's flash. It sits, it watches,
and it smiles. "Spread the word.\""""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}FrontLeg", "root", (2 * side, 9, -5), cubes=[
                C((sx(1, 3), 0, -6), (2, 9.5, 2), "leg"), C((sx(0.75, 3.25), 0, -6.5), (2.5, 1.25, 3), "paw")]),
            B(f"{name}BackLeg", "root", (2 * side, 9, 5), cubes=[
                C((sx(0.75, 3.25), 4, 4), (2.5, 6, 3), "fur"),
                C((sx(1, 3), 0, 4.5), (2, 5, 2), "leg"), C((sx(0.75, 3.25), 0, 4), (2.5, 1.25, 3), "paw")]),
        ]
    bones += [
        B("body", "root", (0, 9, 0), cubes=[
            C((-3.5, 8, -6.5), (7, 6.5, 13), "fur"),
            C((-3.75, 9.5, -7.5), (7.5, 6, 3), "chest_fur")]),
        B("head", "body", (0, 14, -7), cubes=[
            C((-3.25, 12.5, -12), (6.5, 6, 5.5), "fur"),
            # The muzzle, where the smile is.
            C((-2.25, 12.5, -15.5), (4.5, 3.5, 3.5), "muzzle"),
            *[C((x, 18.5, -9.5), (1.75, 3, 1.25), "ear") for x in (-3, 1.25)]]),
        B("tail", "body", (0, 14, 6.5), rotation=(-50, 0, 0), cubes=[
            C((-1, 13.5, 6.5), (2, 2, 6), "fur")]),
    ]
    return hd_model("geometry.hb.smile_dog", bones, bounds=(1.6, 1.6, (0, 0.6, 0)))


def paint(seed, m, **_):
    grey = Ramp(colors=["#2a2c30", "#3e4046", "#54565c", "#6c6e74", "#86888c"])
    white = Ramp(colors=["#9a9a96", "#b6b6b0", "#cecec8", "#e2e2dc", "#f0f0ea"])
    gum = Ramp(colors=["#3a0a0e", "#5a1218", "#7a1e24", "#962e32"])
    eye = glow("#101418", 85)
    shine = glow("#ffffff", 90)
    hole = (0x04, 0x02, 0x02)

    def husky(t, top_dark=True):
        # Grey on the back and the top of the head, white underneath.
        upper = t.face == "top" or (t.side and t.v < 0.5 and top_dark)
        return t.paint(grey if upper else white, 0.5, 0.35, 1.5)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "muzzle":
            if f == "front" or t.side and f != "back":
                # The human smile, wrapping round the muzzle: rows of teeth.
                if 2 <= t.y <= 5:
                    if t.y in (2, 5):
                        return t.paint(gum, 0.5, 0.2)
                    return t.paint(TEETH, 0.62 + (0.12 if t.x % 2 else 0), 0.15) if t.x % 3 else hole
                if f == "front" and t.y < 2 and 3 <= t.x <= 5:
                    return (0x08, 0x08, 0x0a)  # the nose
            return t.paint(white, 0.6, 0.3)
        if tag == "fur" and f == "front" and t.fw == 13:
            # The eyes on the head: wet and black, a flash in each.
            for cx in (3, 9):
                if abs(t.x - cx) <= 1 and 3 <= t.y <= 4:
                    return shine if (t.x - cx, t.y) == (-1, 3) else eye
            # The husky mask: dark over the eyes, white below.
            if t.y < 2 or (t.y < 6 and abs(t.x - 6) > 2):
                return t.paint(grey, 0.45, 0.3)
            return t.paint(white, 0.6, 0.3)
        if tag in ("fur", "chest_fur"):
            return husky(t, tag == "fur")
        if tag == "ear":
            return t.paint(grey, 0.45, 0.2) if f != "front" else t.paint(grey, 0.2, 0.2)
        if tag == "leg":
            return t.paint(white, 0.55, 0.2)
        if tag == "paw":
            return t.paint(white, 0.45, 0.2)
        return None

    return paint_model(m, seed, fn)
