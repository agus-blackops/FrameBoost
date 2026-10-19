"""The Stalker (The Parallel / Twixxel's Stalker).

Very tall, four blocks and more, and thin as a dead tree: a pitch-black body
with a skeletal torso, every rib showing, and legs that bend wrongly at the
knee. The arms are far too long and almost brush the ground, ending in pale,
bony hands with slender claws. The face is the worst of it: a pale, beige,
mask-like face, flat and smooth against all that black, with two large
hollow eye sockets and a wide smile that never changes.

When it hunts it drops onto all fours (the "crawl" animation), the long arms
becoming front legs."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Thighs angled forward, shins back: the knee bends the wrong way.
            B(f"{name}Leg", "root", (2 * side, 34, 0), rotation=(-14, 0, 2 * -side), cubes=[
                C((sx(0.75, 3.25), 19, -1.25), (2.5, 15.5, 2.5), "black"),
                C((sx(0.5, 3.5), 18, -1.5), (3, 2.5, 3), "knee")]),
            B(f"{name}Shin", f"{name}Leg", (2 * side, 19.5, 0), rotation=(26, 0, 0), cubes=[
                C((sx(1, 3), 2, -1), (2, 18, 2), "black")]),
            B(f"{name}Foot", f"{name}Shin", (2 * side, 2, 0), rotation=(-12, 0, 0), cubes=[
                C((sx(0.75, 3.25), 0, -4), (2.5, 1.5, 5), "black"),
                *[C((sx(0.75 + k * 0.9, 1.25 + k * 0.9), 0, -5.5), (0.5, 0.75, 1.5), "claw") for k in range(3)]]),
        ]
    bones += [
        B("hips", "root", (0, 34, 0), cubes=[
            C((-3.5, 33, -1.75), (7, 3, 3.5), "black")]),
        B("chest", "hips", (0, 36, 0), rotation=(-8, 0, 0), cubes=[
            C((-1.5, 36, -1), (3, 7, 2), "black"),  # a spine for a waist
            C((-4.5, 43, -2), (9, 9, 4), "ribcage"),
            C((-5.5, 50.5, -1.5), (11, 2, 3), "black")]),
        B("neck", "chest", (0, 52, 0), rotation=(-12, 0, 0), cubes=[
            C((-1, 52, -1), (2, 4.5, 2), "black")]),
        B("head", "neck", (0, 56, 0), rotation=(10, 0, -6), cubes=[
            C((-4, 56, -3), (8, 10, 6), "black"),
            # The mask-face, flat, a little too far forward.
            C((-4.5, 55, -3.75), (9, 11.5, 1), "face")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "chest", (5.25 * side, 51, 0), rotation=(0, 0, 4 * -side), cubes=[
                C((sx(4.5, 6.5), 29, -1), (2, 22.5, 2), "black"),
                C((sx(4.25, 6.75), 49, -1.25), (2.5, 2.5, 2.5), "black")]),
            B(f"{name}Forearm", f"{name}Arm", (5.5 * side, 29.5, 0), rotation=(-8, 0, 0), cubes=[
                C((sx(4.75, 6.25), 9, -0.75), (1.5, 21, 1.5), "black")]),
            B(f"{name}Hand", f"{name}Forearm", (5.5 * side, 9, 0), cubes=[
                C((sx(4.5, 6.5), 6, -1), (2, 3.5, 2), "hand"),
                *[C((sx(4.5 + k * 0.75, 5 + k * 0.75), 0.5 + (k in (0, 2)), -1 + k * 0.6), (0.5, 6 - (k in (0, 2)), 0.5),
                    "finger") for k in range(3)]]),
        ]
    return hd_model("geometry.hl.stalker", bones, bounds=(1.6, 4.6, (0, 2.1, 0)))


def paint(seed, m, **_):
    black = Ramp(colors=["#020203", "#050507", "#09090c", "#0e0e12", "#15151a"])
    mask = Ramp(colors=["#8a7a62", "#a8977a", "#c2b192", "#d6c6a6", "#e6d8ba", "#f0e6cc"])
    hole = (0x00, 0x00, 0x00)

    # The mask-face, 18x23 design pixels: big hollow sockets running dark
    # at the bottom, and a smile from one side of the face to the other.
    face = {
        3: "..####....####....",
        4: ".######..######...",
        5: ".######..######...",
        6: ".######..######...",
        7: ".######..######...",
        8: "..####....####....",
        9: "..#..#....#..#....",
        10: "...........#......",
        13: "#................#",
        14: "##..............##",
        15: ".##.#.#.#.#.#.#.#.",
        16: "..##############..",
        17: "...#.#.#.#.#.##...",
        18: "....##########....",
    }

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "face":
            if f != "front":
                return t.paint(mask, 0.35, 0.2)
            row = face.get(t.y)
            if row and t.x < len(row) and row[t.x] == "#":
                return hole
            if t.y >= 17 and t.n(1.2) > 0.6:
                return t.paint(mask, 0.3, 0.2)  # a dirty chin
            return t.paint(mask, 0.62, 0.25, 2.5)
        if tag == "ribcage":
            if f in ("front", "back") and t.y % 3 == 1 and 1 <= t.x <= t.fw - 2:
                return t.paint(black, 0.85, 0.1)  # every rib showing
            return t.paint(black, 0.4, 0.3)
        if tag in ("black", "knee"):
            return t.paint(black, 0.45, 0.35, 1.5)
        if tag in ("hand", "finger", "claw"):
            return t.paint(BONE, 0.55 - (0.3 * t.v if tag == "finger" else 0), 0.25)
        return None

    return paint_model(m, seed, fn)
