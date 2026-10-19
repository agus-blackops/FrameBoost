"""Jenny Dweller (Jenny and the Cave Dweller, fused).

Two whole bodies in one model; the render controller shows one or the other
(`hl:revealed`).

The girl (GIRL bones): a friendly girl with brown hair in a long ponytail, a
red hoodie, jeans and white trainers, big green eyes and a smile. She waves.
She wants you to follow her.

The Dweller (DWELLER bones): what was wearing her. A head taller, gaunt and
hunched, skin the grey-white of something that lives in caves. What is left
of her clothes hangs off it: the hoodie torn short and ragged, the sleeves
gone, the jeans shredded to the thigh. The neck is far too long. Her brown
hair and ponytail are still there, on a long pale face with two big black
hollows for eyes and a tiny white point in each, and the lower face is two
halves (`dRightJaw`, `dLeftJaw`) that split apart sideways into a mouth full
of thin teeth. The arms hang to the knees and end in long black claws."""

from models.kit import *

GIRL = ("girl", "gRightLeg", "gLeftLeg", "gBody", "gHead", "gPonytail", "gRightArm", "gLeftArm")
DWELLER = ("dweller", "dRightLeg", "dRightShin", "dLeftLeg", "dLeftShin", "dHips", "dChest", "dNeck", "dHead",
           "dRightJaw", "dLeftJaw", "dPonytail", "dRightArm", "dRightForearm", "dLeftArm", "dLeftForearm")
PREVIEW = {"dweller": {"scale": (0, 0, 0)}}
PREVIEW_MONSTER = {"girl": {"scale": (0, 0, 0)}}


def ponytail(name, parent, at, rotation=(18, 0, 0)):
    x, y, z = at
    return B(name, parent, at, rotation=rotation, cubes=[
        C((x - 1, y - 1, z), (2, 2, 1.5), "tie"),
        C((x - 1.25, y - 10, z + 0.25), (2.5, 9.5, 2), "hair"),
        C((x - 0.75, y - 12, z + 0.5), (1.5, 2.5, 1.5), "hair")])


def girl():
    bones = [B("girl", None, (0, 0, 0))]
    for side, name in ((-1, "Right"), (1, "Left")):
        sx = lambda a, b: side_x(side, a, b)
        bones.append(B(f"g{name}Leg", "girl", (1.9 * side, 12, 0), cubes=[
            C((sx(0, 3.75), 2, -1.9), (3.75, 10.5, 3.8), "jeans"),
            C((sx(-0.25, 4), 0, -2.6), (4.25, 2.25, 4.75), "shoe")]))
    bones += [
        B("gBody", "girl", (0, 12, 0), cubes=[
            C((-3.75, 12, -2), (7.5, 11.5, 4), "hoodie"),
            C((-4, 11.5, -2.25), (8, 2, 4.5), "hem"),
            C((-2.75, 14, -2.4), (5.5, 3, 0.5), "pocket"),
            C((-3.5, 21.5, 1.75), (7, 3, 1.5), "hood")]),
        B("gHead", "gBody", (0, 23.5, 0), cubes=[
            C((-3.75, 23.5, -3.75), (7.5, 7.5, 7.5), "face"),
            C((-4, 28.5, -4), (8, 3, 8), "hair"),
            C((-4, 24, 0), (8, 5, 4), "hair"),
            C((-4.1, 24.5, -4), (0.75, 5, 4), "hair"), C((3.35, 24.5, -4), (0.75, 5, 4), "hair")]),
        ponytail("gPonytail", "gHead", (0, 29, 3.5)),
    ]
    for side, name in ((-1, "Right"), (1, "Left")):
        sx = lambda a, b: side_x(side, a, b)
        bones.append(B(f"g{name}Arm", "gBody", (4.75 * side, 22.5, 0), rotation=(0, 0, 3 * -side), cubes=[
            C((sx(3.75, 6.75), 12.5, -1.5), (3, 10.5, 3), "hoodie"),
            C((sx(3.9, 6.6), 10.5, -1.35), (2.7, 2.5, 2.7), "hand")]))
    return bones


def dweller():
    bones = [B("dweller", None, (0, 0, 0))]
    for side, name in ((-1, "Right"), (1, "Left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"d{name}Leg", "dweller", (2 * side, 24, 0), rotation=(-8, 0, 2 * -side), cubes=[
                C((sx(0.75, 3.25), 13, -1.25), (2.5, 11, 2.5), "pale"),
                C((sx(0.5, 3.5), 17.5, -1.5), (3, 6.75, 3), "torn_jeans")]),
            B(f"d{name}Shin", f"d{name}Leg", (2 * side, 13.5, 0), rotation=(14, 0, 0), cubes=[
                C((sx(1, 3), 1, -1), (2, 13, 2), "pale"),
                C((sx(0.75, 3.25), 0, -3.75), (2.5, 1.5, 4.5), "pale"),
                *[C((sx(0.6 + k * 0.85, 1.1 + k * 0.85), 0, -5.25), (0.5, 0.75, 1.5), "claw") for k in range(3)]]),
        ]
    bones += [
        B("dHips", "dweller", (0, 24, 0), cubes=[
            C((-3.5, 23, -1.75), (7, 3, 3.5), "torn_jeans")]),
        # Hunched, all of it leaning towards you.
        B("dChest", "dHips", (0, 26, 0), rotation=(-22, 0, 0), cubes=[
            C((-1.75, 26, -1.25), (3.5, 5.5, 2.5), "belly"),
            C((-4, 31, -2), (8, 9, 4), "rag_hoodie"),
            C((-3.5, 38.5, 1.5), (7, 2.5, 2), "rag_hoodie"),  # the hood, bunched
            C((-4.25, 38.5, -2.25), (8.5, 1.5, 4.5), "pale")]),
        B("dNeck", "dChest", (0, 40, 0), rotation=(-26, 0, 0), cubes=[
            C((-0.9, 39.5, -0.9), (1.8, 6.5, 1.8), "pale")]),
        B("dHead", "dNeck", (0, 46, 0), rotation=(30, 0, 10), cubes=[
            C((-3, 47, -3), (6, 7, 6), "dface"),
            C((-3.5, 53, -3.5), (7, 2.5, 7), "hair"),
            C((-3.5, 46.5, 0.5), (7, 7.5, 3), "hair"),
            C((-3.75, 44, -3.25), (0.75, 9.5, 3.75), "hair"), C((3, 44, -3.25), (0.75, 9.5, 3.75), "hair")]),
        # The lower face, in two halves that split apart.
        B("dRightJaw", "dHead", (-0.1, 47, 1), rotation=(0, -6, 0), cubes=[
            C((-3, 42.5, -3.25), (2.9, 4.75, 4.25), "djaw"),
            *[C((-0.6, 43 + k * 0.85, -3.1 + 0.4 * (k % 2)), (0.9, 0.5, 3), "tooth") for k in range(5)]]),
        B("dLeftJaw", "dHead", (0.1, 47, 1), rotation=(0, 6, 0), cubes=[
            C((0.1, 42.5, -3.25), (2.9, 4.75, 4.25), "djaw"),
            *[C((-0.3, 43.4 + k * 0.85, -2.9 + 0.4 * ((k + 1) % 2)), (0.9, 0.5, 3), "tooth") for k in range(5)]]),
        ponytail("dPonytail", "dHead", (0, 53.5, 3.5), rotation=(-10, 0, 0)),
    ]
    for side, name in ((-1, "Right"), (1, "Left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"d{name}Arm", "dChest", (4.75 * side, 39, 0), rotation=(12, 0, 7 * -side), cubes=[
                C((sx(4, 7), 35, -1.5), (3, 4, 3), "rag_hoodie"),
                C((sx(4.75, 6.25), 25.5, -0.75), (1.5, 13.5, 1.5), "pale")]),
            B(f"d{name}Forearm", f"d{name}Arm", (5.5 * side, 26, 0), rotation=(-8, 0, 0), cubes=[
                C((sx(4.8, 6.2), 13, -0.7), (1.4, 13, 1.4), "pale"),
                C((sx(4.5, 6.5), 10.5, -1), (2, 3, 2), "pale"),
                *[C((sx(4.55 + k * 0.6, 5.0 + k * 0.6), 4.5 + (k in (0, 2)) * 0.75, -0.9 + k * 0.7), (0.45, 6.5 - (k in (0, 2)) * 0.75, 0.45),
                    "claw") for k in range(3)]]),
        ]
    return bones


def model():
    return hd_model("geometry.hl.jenny_dweller", girl() + dweller(), bounds=(1.6, 3.2, (0, 1.4, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#8a5e48", "#ad7a5e", "#c99878", "#ddb294", "#ecc8aa", "#f6dcc4"])
    hair = Ramp(colors=["#2a160c", "#3e2414", "#56341e", "#6e462a", "#885a36"])
    red = Ramp(colors=["#3c080c", "#5e1016", "#841a20", "#a8262a", "#c43a36", "#d85448"])
    jeans = Ramp(colors=["#1a2440", "#243256", "#30426c", "#3e5484", "#506a9c"])
    pale = Ramp(colors=["#5a5a5c", "#7c7c7e", "#9c9c9c", "#bcbab6", "#d6d4ce", "#e8e6e0"])
    white = Ramp(colors=["#9a9a9a", "#bcbcbc", "#d8d8d8", "#ececec", "#fafafa"])
    eye_white = (0xf4, 0xf2, 0xee)
    iris = (0x3a, 0x6e, 0x3a)
    pupil = (0x10, 0x10, 0x14)
    lips = (0xc0, 0x4a, 0x52)
    hole = (0x02, 0x02, 0x03)
    pin = glow("#ffffff", 95)
    gums = Ramp(colors=["#3a060c", "#5c0e18", "#7e1a24", "#9c2a32"])

    def girl_face(t):
        # 15x15 design pixels: big friendly eyes, a smile.
        x, y = t.x, t.y
        for ex in (3, 9):
            if ex <= x <= ex + 2 and 6 <= y <= 8:
                if y == 6:
                    return t.paint(hair, 0.3, 0.1)  # lashes
                if x == ex + 1 and y == 7:
                    return pupil
                return iris if x != (ex if ex == 3 else ex + 2) else eye_white
            if ex <= x <= ex + 2 and y == 4:
                return t.paint(hair, 0.4, 0.1)  # brows
        if y == 11 and 5 <= x <= 9:
            return lips
        if y == 10 and x in (4, 10):
            return lips
        if y in (8, 9) and x in (2, 12):
            return t.paint(red, 0.8, 0.1)  # blush
        return t.paint(skin, 0.6, 0.15)

    # The Dweller's face, 12x14 design pixels: two big black hollows with a
    # white point in each, set too far apart, and two slits for a nose.
    dface = {
        3: "............",
        4: ".####..####.",
        5: "#####..#####",
        6: "##o##..##o##",
        7: "#####..#####",
        8: "#####..#####",
        9: ".####..####.",
        10: "..##....##..",
        12: ".....##.....",
    }

    def cave_skin(t, base=0.6):
        if t.ridge(4.5, 0.035) > 0.72:
            return t.paint(Ramp(colors=["#4a5464", "#5e6878", "#76808e"]), 0.5, 0.1)  # veins
        if t.n2(2.0) > 0.78:
            return t.paint(pale, base - 0.3, 0.2)  # grime from the caves
        return t.paint(pale, base, 0.3, 2.0)

    def ragged(t, ramp, base):
        if t.side and t.y >= t.fh - 3 and t.n(1.0) > 0.4:
            return CLEAR
        if t.n2(1.6) > 0.8:
            return CLEAR  # torn through
        if t.n(2.5) > 0.7:
            return t.paint(ramp, base - 0.3, 0.2)  # stains
        return t.paint(ramp, base, 0.35)

    def fn(t):
        tag, f = t.tag, t.face
        # The girl.
        if tag == "face":
            if f == "front":
                return girl_face(t)
            if f == "top":
                return t.paint(hair, 0.5, 0.3)
            return t.paint(skin, 0.55, 0.15)
        if tag == "hair":
            return t.paint(hair, 0.5 + (0.08 if t.x % 3 == 0 else 0), 0.35, 1.5)
        if tag == "tie":
            return t.paint(red, 0.7, 0.1)
        if tag in ("hoodie", "hem", "hood", "pocket"):
            if tag == "hoodie" and f == "front" and t.y <= 3 and t.x in (6, 8):
                return t.paint(white, 0.7, 0.1)  # drawstrings
            return t.paint(red, {"hem": 0.35, "pocket": 0.4, "hood": 0.4}.get(tag, 0.55), 0.3)
        if tag == "jeans":
            return t.paint(jeans, 0.5, 0.35)
        if tag == "shoe":
            return t.paint(white, 0.6 if f != "bottom" else 0.2, 0.15)
        if tag == "hand":
            return t.paint(skin, 0.55, 0.15)
        # The Dweller.
        if tag == "pale":
            return cave_skin(t)
        if tag == "belly":
            if t.side and t.y % 3 == 0:
                return t.paint(pale, 0.3, 0.1)  # ribs under the skin
            return cave_skin(t, 0.55)
        if tag == "rag_hoodie":
            if f in ("top", "bottom"):
                return CLEAR if f == "bottom" else t.paint(red, 0.3, 0.2)
            return ragged(t, red, 0.45)
        if tag == "torn_jeans":
            if f in ("top", "bottom"):
                return t.paint(jeans, 0.3, 0.2) if f == "top" else CLEAR
            return ragged(t, jeans, 0.45)
        if tag == "dface":
            if f == "front":
                row = dface.get(t.y)
                c = row[t.x] if row and t.x < len(row) else "."
                if c == "o":
                    return pin
                if c == "#":
                    return hole
            return cave_skin(t, 0.62)
        if tag == "djaw":
            # Inside, where the two halves meet: wet and red.
            if f in ("left", "right"):
                return t.paint(gums, 0.5, 0.4) if t.n(1.5) > 0.35 else hole
            if f == "front":
                # Half of a grin that goes from ear to ear: thin teeth in the dark.
                edge = t.x if t.x < t.fw // 2 else t.fw - 1 - t.x
                top = 1 + (1 if edge <= 1 else 0)
                if t.y == top or t.y == top + 3:
                    return hole
                if top < t.y < top + 3:
                    return t.paint(TEETH, 0.65, 0.2) if t.x % 2 == 0 else hole
            return cave_skin(t, 0.58)
        if tag == "tooth":
            return t.paint(TEETH, 0.6, 0.3)
        if tag == "claw":
            return t.paint(CLOTH_DARK, 0.5 - 0.3 * t.v, 0.15)
        return None

    return paint_model(m, seed, fn)
