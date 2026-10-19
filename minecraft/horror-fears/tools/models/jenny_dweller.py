"""Jenny Dweller.

From far away, and for a while from up close too, a friendly girl: brown
hair in a long ponytail, a red hoodie, jeans and white trainers, a big smile
and big eyes. She waves. She wants you to follow her.

Underneath is a Dweller. When it stops pretending (`hl:revealed`), the
girl's head and arms are put away: the head becomes a long pale skull that
splits down the middle into two jaws full of teeth, the ponytail still
hanging off the back of it, and two thin grey arms twice as long as hers
unfold from the hoodie, with hooked claws.

Bones that belong to only one of the two shapes are listed in HUMAN and
MONSTER (the render controller shows one set or the other)."""

from models.kit import *

HUMAN = ("humanHead", "ponytail", "rightArm", "leftArm")
MONSTER = ("monsterHead", "monsterTail", "rightJaw", "leftJaw", "rightClaw", "leftClaw")
PREVIEW = {b: {"scale": (0, 0, 0)} for b in MONSTER}
PREVIEW_MONSTER = {b: {"scale": (0, 0, 0)} for b in HUMAN}


def ponytail(name, parent, dy):
    return B(name, parent, (0, 29 + dy, 3.5), rotation=(18, 0, 0), cubes=[
        C((-1, 28 + dy, 3.5), (2, 2, 1.5), "tie"),
        C((-1.25, 19 + dy, 3.75), (2.5, 9.5, 2), "hair"),
        C((-0.75, 17 + dy, 4), (1.5, 2.5, 1.5), "hair")])


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones.append(B(f"{name}Leg", "root", (1.9 * side, 12, 0), cubes=[
            C((sx(0, 3.75), 2, -1.9), (3.75, 10.5, 3.8), "jeans"),
            C((sx(-0.25, 4), 0, -2.6), (4.25, 2.25, 4.75), "shoe")]))
    bones += [
        B("body", "root", (0, 12, 0), cubes=[
            C((-3.75, 12, -2), (7.5, 11.5, 4), "hoodie"),
            C((-4, 11.5, -2.25), (8, 2, 4.5), "hem"),
            C((-2.75, 14, -2.4), (5.5, 3, 0.5), "pocket"),
            C((-3.5, 21.5, 1.75), (7, 3, 1.5), "hood")]),
        B("neck", "body", (0, 23.5, 0)),
        # The girl's head.
        B("humanHead", "neck", (0, 23.5, 0), cubes=[
            C((-3.75, 23.5, -3.75), (7.5, 7.5, 7.5), "face"),
            C((-4, 28.5, -4), (8, 3, 8), "hair"),
            C((-4, 24, 0), (8, 5, 4), "hair"),
            C((-4.1, 24.5, -4), (0.75, 5, 4), "hair"), C((3.35, 24.5, -4), (0.75, 5, 4), "hair")]),
        # Underneath: a long pale skull split into two jaws.
        B("monsterHead", "neck", (0, 23.5, 0), cubes=[
            C((-1, 23.5, -0.5), (2, 4, 2), "skin"),  # a neck that keeps going
            C((-3, 27, -2), (6, 6, 5.5), "skin"),
            C((-4, 32, -1), (8, 2.5, 5), "hair"),
            C((-0.5, 32.5, -2.25), (1, 1, 1), "eye")]),
        B("rightJaw", "monsterHead", (-0.25, 27.5, -2), rotation=(0, -30, 0), cubes=[
            C((-3.25, 27, -6), (3, 8.5, 4), "jaw"),
            *[C((-0.5, 28 + k * 1.6, -5.75 + 0.5 * (k % 2)), (1.5, 0.75, 3.5), "tooth") for k in range(5)]]),
        B("leftJaw", "monsterHead", (0.25, 27.5, -2), rotation=(0, 30, 0), cubes=[
            C((0.25, 27, -6), (3, 8.5, 4), "jaw"),
            *[C((-1, 28.75 + k * 1.6, -5.75 + 0.5 * ((k + 1) % 2)), (1.5, 0.75, 3.5), "tooth") for k in range(4)]]),
        # The ponytail hangs off whichever head is showing.
        ponytail("ponytail", "humanHead", 0),
        ponytail("monsterTail", "monsterHead", 3.5),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (4.75 * side, 22.5, 0), rotation=(0, 0, 3 * -side), cubes=[
                C((sx(3.75, 6.75), 12.5, -1.5), (3, 10.5, 3), "hoodie"),
                C((sx(3.9, 6.6), 10.5, -1.35), (2.7, 2.5, 2.7), "hand")]),
            B(f"{name}Claw", "body", (4.75 * side, 22.5, 0), rotation=(-6, 0, 10 * -side), cubes=[
                C((sx(3.75, 6.75), 18, -1.5), (3, 5, 3), "hoodie"),
                C((sx(4.5, 6), 6.5, -0.75), (1.5, 12, 1.5), "skin"),
                C((sx(4.25, 6.25), 4, -1), (2, 3, 2), "skin"),
                *[C((sx(4.25 + k * 0.75, 4.75 + k * 0.75), 0.25, -1 + k * 0.7), (0.5, 4, 0.5), "claw") for k in range(3)]]),
        ]
    return hd_model("geometry.hl.jenny_dweller", bones, bounds=(1.4, 2.6, (0, 1.1, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#8a5e48", "#ad7a5e", "#c99878", "#ddb294", "#ecc8aa", "#f6dcc4"])
    hair = Ramp(colors=["#2a160c", "#3e2414", "#56341e", "#6e462a", "#885a36"])
    red = Ramp(colors=["#3c080c", "#5e1016", "#841a20", "#a8262a", "#c43a36", "#d85448"])
    jeans = Ramp(colors=["#1a2440", "#243256", "#30426c", "#3e5484", "#506a9c"])
    pale = Ramp(colors=["#4e4c4a", "#6c6966", "#8a8682", "#a8a39e", "#c4beb8"])
    white = Ramp(colors=["#9a9a9a", "#bcbcbc", "#d8d8d8", "#ececec", "#fafafa"])
    eye_white = (0xf4, 0xf2, 0xee)
    iris = (0x3a, 0x6e, 0x3a)
    pupil = (0x10, 0x10, 0x14)
    lips = (0xc0, 0x4a, 0x52)
    mouth = (0x28, 0x06, 0x0a)

    def girl_face(t):
        # 15x15 design pixels: big friendly eyes, a smile.
        x, y = t.x, t.y
        for ex in (3, 9):
            if ex <= x <= ex + 2 and 6 <= y <= 8:
                if y == 6:
                    return t.paint(hair, 0.3, 0.1)  # lashes
                if x == ex + (1 if ex == 3 else 1) and y == 7:
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

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "face":
            if f == "front":
                return girl_face(t)
            if f == "top":
                return t.paint(hair, 0.5, 0.3)
            return t.paint(skin, 0.55, 0.15)
        if tag in ("hair",):
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
        # The Dweller underneath.
        if tag == "skin":
            if t.ridge(4.0, 0.05) > 0.6:
                return t.paint(pale, 0.25, 0.1)
            return t.paint(pale, 0.6, 0.3)
        if tag == "eye":
            return glow("#ffffff", 80)
        if tag == "jaw":
            # Inside the two halves of the face: wet and red; outside: pale.
            if f in ("left", "right"):
                return t.paint(GUT, 0.35, 0.4) if t.n(1.5) > 0.3 else mouth
            return t.paint(pale, 0.55, 0.3)
        if tag == "tooth":
            return t.paint(TEETH, 0.55, 0.3)
        if tag == "claw":
            return t.paint(BONE, 0.4 - 0.25 * t.v, 0.2)
        return None

    return paint_model(m, seed, fn)
