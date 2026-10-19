"""The Stalker (LCrandom's The Parallel, as Twixxel brought it to Minecraft).

Far too tall and thin as a dead tree. The body is completely, pitch black:
skeletal, every rib standing out of a chest that is hardly there, a spine
for a waist, hips like a coat hanger, and legs that curve wrongly near the
knee. The arms hang almost to the ground and end in pale, bony hands with
long spindly fingers.

The head is the only thing that isn't black: an egg-shaped, oval head the
colour of old beige, like a mask. Its eye sockets are far bigger than its
eyes, two black egg-shaped hollows with a tiny white pupil sitting high up in
each one. It has a small nose, and a permanent smile far too wide, full of
human teeth, pitch black inside.

The lower part of the face is its own bone (`jaw`): when it drops onto all
fours to hunt, the mouth comes open, unnaturally long."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Thighs angled forward, shins sweeping back: knees bent wrongly.
            B(f"{name}Leg", "root", (2 * side, 33, 0), rotation=(-14, 0, 2 * -side), cubes=[
                C((sx(0.75, 3.25), 18.5, -1.25), (2.5, 15, 2.5), "black"),
                C((sx(0.5, 3.5), 17.5, -1.75), (3, 2.75, 3), "joint")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 19, 0), rotation=(28, 0, 0), cubes=[
                C((sx(1, 3), 2.5, -1), (2, 16.5, 2), "black"),
                C((sx(0.75, 3.25), 2, -1.25), (2.5, 2, 2.5), "joint")]),
            B(f"{name}Foot", f"{name}Shin", (2 * side, 2.5, 0), rotation=(-14, 0, 0), cubes=[
                C((sx(0.75, 3.25), -0.8, -3.5), (2.5, 2, 4.5), "black"),
                *[C((sx(0.5 + k * 1.0, 1.1 + k * 1.0), -0.8, -6), (0.6, 0.8, 2.5), "black") for k in range(3)]]),
        ]
    ribs = []
    for k in range(5):
        y = 43.5 + k * 1.7
        w = 9 - abs(k - 1.5) * 0.9
        ribs += [C((-w / 2, y, -2.6), (w, 0.8, 0.8), "rib"),
                 C((-w / 2, y, -2.2), (0.8, 0.8, 4.2), "rib"), C((w / 2 - 0.8, y, -2.2), (0.8, 0.8, 4.2), "rib")]
    bones += [
        B("hips", "root", (0, 33, 0), cubes=[
            C((-3.75, 32.5, -1.25), (7.5, 2, 2.5), "black"),
            C((-1, 34.5, -0.75), (2, 1.5, 1.5), "black")]),
        B("chest", "hips", (0, 35, 0), rotation=(-8, 0, 0), cubes=[
            # A spine for a waist.
            *[C((-0.9, 35 + k * 1.6, -0.4 + 0.1 * k), (1.8, 1.2, 1.8), "spine") for k in range(5)],
            C((-2.5, 43, -1.5), (5, 9.5, 3), "black"),  # what is inside the ribs
            *ribs,
            # Collarbones and shoulders, wide and sharp.
            C((-5.75, 51.5, -1.25), (11.5, 1.5, 2.5), "black"),
            C((-6, 50.5, -1.5), (2, 2.5, 3), "joint"), C((4, 50.5, -1.5), (2, 2.5, 3), "joint")]),
        B("neck", "chest", (0, 53, 0), rotation=(-14, 0, 0), cubes=[
            C((-0.9, 52.5, -0.9), (1.8, 5.5, 1.8), "spine")]),
        # The egg-shaped head, built in layers so it reads as an oval.
        B("head", "neck", (0, 58, 0), rotation=(12, 0, -8), cubes=[
            C((-3.5, 60, -3.25), (7, 6, 6.5), "mask"),
            C((-3, 58.5, -2.75), (6, 9, 5.5), "mask"),
            C((-2.25, 67.5, -2), (4.5, 1, 4), "mask"),
            C((-2.75, 66.5, -2.5), (5.5, 1, 5), "mask"),
            # The face: upper half (eyes, nose, the top teeth).
            C((-3.25, 60, -3.75), (6.5, 6.5, 0.5), "face"),
            C((-0.5, 61.5, -4.5), (1, 1.75, 0.75), "nose"),
            # Black inside: what you see when the mouth opens.
            C((-2.75, 57, -3.4), (5.5, 3.25, 0.3), "inside")]),
        # The lower face, with the bottom row of the grin.
        B("jaw", "head", (0, 60, -3), cubes=[
            C((-3.25, 57.5, -3.75), (6.5, 2.5, 0.5), "chin"),
            C((-2.5, 57, -3.25), (5, 1, 4.5), "mask")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "chest", (5 * side, 51.5, 0), rotation=(2, 0, 5 * -side), cubes=[
                C((sx(4.25, 6), 31, -0.9), (1.75, 20.5, 1.75), "black")]),
            B(f"{name}Forearm", f"{name}Arm", (5.1 * side, 31.5, 0), rotation=(-10, 0, 0), cubes=[
                C((sx(4, 6.25), 30.5, -1.1), (2.25, 2, 2.25), "joint"),
                C((sx(4.4, 5.85), 11, -0.7), (1.45, 20, 1.45), "black")]),
            B(f"{name}Hand", f"{name}Forearm", (5.1 * side, 11, 0), cubes=[
                C((sx(4.1, 6.1), 8, -1.1), (2, 3, 2.2), "hand"),
                # Long spindly fingers, four and a thumb.
                *[C((sx(4.2 + k * 0.5, 4.65 + k * 0.5), 1 + (k in (0, 3)) * 1.25, -0.9 + k * 0.55),
                    (0.45, 7 - (k in (0, 3)) * 1.25, 0.45), "finger") for k in range(4)],
                C((sx(4.0, 4.45), 4.5, 0.9), (0.45, 4, 0.45), "finger")]),
        ]
    return hd_model("geometry.hl.stalker", bones, bounds=(1.6, 4.6, (0, 2.1, 0)))


def paint(seed, m, **_):
    black = Ramp(colors=["#020203", "#040406", "#08080b", "#0d0d11", "#141419"])
    mask = Ramp(colors=["#7e6e56", "#9c8a6c", "#b8a684", "#ccbb98", "#ddcdaa", "#ebdfc0"])
    bony = Ramp(colors=["#6e6a62", "#8e887c", "#aaa496", "#c2bcac", "#d8d2c2"])
    hole = (0x00, 0x00, 0x00)
    pupil = glow("#ffffff", 95)
    tooth = Ramp(colors=["#9a9078", "#bcb296", "#d8d0b4", "#ece6cc"])

    # The upper face, 13x13 design pixels: two egg-shaped hollows with a tiny
    # white pupil high up in each, and the top row of the grin at the bottom.
    upper = {
        1: ".###....###..",
        2: "#####..#####.",
        3: "##o##..##o##.",
        4: "#####..#####.",
        5: "#####..#####.",
        6: "#####..#####.",
        7: ".###....###..",
        8: "#...........#",
        9: "#...........#",
        10: "##.........##",
        11: ".#K.......K#.",
        12: ".TTTTTTTTTTT.",
    }
    # The chin: the bottom row of teeth under the dark of the mouth.
    lower = {
        0: ".KTTTTTTTTTK.",
        1: "..KKTTTTTKK..",
        2: "....KKKKK....",
    }

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "face":
            if f != "front":
                return t.paint(mask, 0.4, 0.2)
            row = upper.get(t.y)
            c = row[t.x] if row and t.x < len(row) else "."
            if c == "o":
                return pupil
            if c in "#K":
                return hole
            if c == "T":
                return t.paint(tooth, 0.6 + (0.25 if t.x % 2 else 0), 0.1) if t.x % 2 or t.n(1.0) > 0.3 else hole
            return t.paint(mask, 0.62, 0.22, 2.5)
        if tag == "chin":
            if f != "front":
                return t.paint(mask, 0.4, 0.2)
            row = lower.get(t.y)
            c = row[t.x] if row and t.x < len(row) else "."
            if c == "T":
                return t.paint(tooth, 0.65 + (0.2 if t.x % 2 else 0), 0.1)
            if c == "K":
                return hole
            return t.paint(mask, 0.55, 0.22, 2.5)
        if tag == "nose":
            return t.paint(mask, 0.45 if f == "front" else 0.3, 0.15)
        if tag == "inside":
            return hole
        if tag == "mask":
            return t.paint(mask, 0.55 - (0.15 if f == "bottom" else 0), 0.3, 2.0)
        if tag == "rib":
            return t.paint(black, 0.9, 0.1)
        if tag in ("black", "spine", "joint"):
            return t.paint(black, 0.55 if tag == "joint" else 0.4, 0.3, 1.5)
        if tag == "hand":
            return t.paint(bony, 0.5, 0.25)
        if tag == "finger":
            return t.paint(bony, 0.6 - 0.35 * t.v, 0.15)
        return None

    return paint_model(m, seed, fn)
