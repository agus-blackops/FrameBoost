"""Soup.AVI: the one with the big head.

He stands behind the man with the soup and rubs his back, slowly, round and
round, the whole time. He is a person in a costume: an ordinary dark sweater
and trousers, and on his shoulders a huge foam mascot head, pale and dented,
far too big, with two flat black eyes and a wide painted smile that does not
change. When you watch for long enough, the head turns, all of it at once,
and looks at you. There is more than one of him."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones.append(B(f"{name}Leg", "root", (2 * side, 12, 0), cubes=[
            C((sx(0, 4), 1.5, -2), (4, 10.5, 4), "pants"),
            C((sx(-0.25, 4.25), 0, -3), (4.5, 2, 5.5), "shoe")]))
    bones += [
        B("body", "root", (0, 12, 0), cubes=[
            C((-4, 12, -2), (8, 12, 4), "sweater"),
            C((-4.25, 11.5, -2.25), (8.5, 2, 4.5), "hem"),
            C((-2, 23.5, -1.5), (4, 1.5, 3), "neck")]),
        # The foam head: wider than the shoulders, sitting low on them.
        B("head", "body", (0, 24, 0), rotation=(0, 0, 4), cubes=[
            C((-7, 24, -6.5), (14, 13, 13), "foam"),
            C((-6, 37, -5.5), (12, 1.5, 11), "foam"),
            C((-6, 22.5, -5.5), (12, 1.5, 11), "foam"),
            C((-8, 25, -5.5), (1, 11, 11), "foam"),
            C((7, 25, -5.5), (1, 11, 11), "foam"),
            C((-6, 25, -7.5), (12, 11, 1), "face"),
            # Ears, a tuft on top, and a round nose.
            C((-9.5, 29, -1.5), (1.5, 4, 3), "foam"), C((8, 29, -1.5), (1.5, 4, 3), "foam"),
            C((-1, 38.5, -1), (2, 2, 2), "tuft"), C((0.5, 39.5, -0.5), (1.5, 1.5, 1.5), "tuft"),
            C((-1.25, 29, -8.75), (2.5, 2.5, 1.25), "nose")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (5 * side, 22.5, 0), rotation=(-10, 0, 4 * -side), cubes=[
                C((sx(4, 7), 12, -1.5), (3, 11, 3), "sweater")]),
            B(f"{name}Forearm", f"{name}Arm", (5.5 * side, 12.5, 0), rotation=(-20, 0, 0), cubes=[
                C((sx(4.25, 6.75), 5.5, -1.25), (2.5, 7, 2.5), "sweater"),
                C((sx(4.25, 6.75), 2.5, -1.25), (2.5, 3, 2.5), "glove")]),
        ]
    return hd_model("geometry.hl.soup_mascot", bones, bounds=(1.4, 2.8, (0, 1.2, 0)))


def paint(seed, m, **_):
    foam = Ramp(colors=["#8a7a62", "#a8977c", "#c4b396", "#d8c9ac", "#e8dcc4", "#f4ecda"])
    sweater = Ramp(colors=["#121418", "#1c1f26", "#282c36", "#343a46", "#424a58"])
    pants = Ramp(colors=["#1a1a1c", "#262628", "#343436", "#444446"])
    eye = (0x06, 0x06, 0x08)
    shine = (0xe8, 0xe8, 0xe8)
    mouth = (0x1a, 0x06, 0x08)
    tongue = (0x9a, 0x34, 0x3a)
    tooth = (0xf0, 0xec, 0xe0)

    # The face, 24x22 design pixels: flat black eyes, a wide painted smile.
    face = {
        4: "....######......######....",
        5: "...########....########...",
        6: "...##w#####....##w#####...",
        7: "...########....########...",
        8: "...########....########...",
        9: "....######......######....",
        14: "..#....................#..",
        15: "..##..................##..",
        16: "...#ttttttttttttttttt#...",
        17: "...##tttttttttttttttt##...",
        18: "....#mmmmmmmmmmmmmmmm#....",
        19: ".....##mmmmrrrrrmmm##.....",
        20: "......###mrrrrrrrm###.....",
        21: "........##########........",
    }
    colours = {"#": eye, "w": shine, "m": mouth, "r": tongue, "t": tooth}

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "face" and f == "front":
            row = face.get(t.y)
            if row and t.x < len(row) and row[t.x] in colours:
                return colours[row[t.x]]
            if 15 <= t.y <= 18 and 4 <= t.x <= 20 and t.n2(1.5) > 0.72:
                return t.paint(foam, 0.3, 0.3)  # grime round the mouth
            return t.paint(foam, 0.6, 0.35, 2.0)
        if tag in ("foam", "face"):
            # Dented, dirty foam.
            if t.ridge(3.0, 0.05) > 0.6:
                return t.paint(foam, 0.32, 0.2)
            if t.n2(2.5) > 0.76:
                return t.paint(foam, 0.38, 0.3)
            return t.paint(foam, 0.6, 0.35, 2.0)
        if tag == "tuft":
            return t.paint(HAIR, 0.45, 0.4)
        if tag == "nose":
            return t.paint(Ramp(colors=["#4a1416", "#6e2024", "#922e32", "#b2443e"]), 0.6, 0.2)
        if tag in ("sweater", "hem", "neck"):
            if tag == "sweater" and t.y % 4 == 0 and t.side:
                return t.paint(sweater, 0.3, 0.2)  # knit rows
            return t.paint(sweater, 0.5, 0.3)
        if tag == "glove":
            return t.paint(foam, 0.75, 0.15)
        if tag == "pants":
            return t.paint(pants, 0.45, 0.3)
        if tag == "shoe":
            return t.paint(CLOTH_DARK, 0.4, 0.2)
        return None

    return paint_model(m, seed, fn)
