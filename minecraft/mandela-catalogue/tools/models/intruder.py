"""The Intruder.

What the Intruder Alert warns you about: someone in your house who does not
live there. Too tall for the room, it stands stooped in a corner in a dark
suit, arms hanging to its knees. The neck is too long and the face is too
big: two wide white eyes that never blink, with pupils like pin pricks, and
a grin pulled back over every tooth, ear to ear. It is very pale, the
colour of a television left on in the dark."""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones.append(B(f"{name}Leg", "root", (1.75 * side, 17, 0), cubes=[
            C((sx(0.5, 3), 1, -1.25), (2.5, 16, 2.5), "leg"),
            C((sx(0.25, 3.25), 0, -2.5), (3, 1.5, 4), "shoe")]))
    bones += [
        B("body", "root", (0, 17, 0), rotation=(10, 0, 0), cubes=[
            C((-3.5, 17, -2), (7, 13, 4), "suit"),
            C((-1, 22, -2.25), (2, 8, 0.5), "tie")]),
        B("neck", "body", (0, 30, 0), rotation=(14, 0, 0), cubes=[
            C((-1, 30, -1), (2, 6, 2), "neck")]),
        B("head", "neck", (0, 36, 0), rotation=(-12, 0, 8), cubes=[
            C((-4.5, 36, -4.5), (9, 10, 8), "face"),
            C((-4.75, 43, -4.75), (9.5, 3.5, 8.5), "hair", 0.25),
            C((-5.25, 39, -1.5), (0.75, 2.5, 1.5), "ear"), C((4.5, 39, -1.5), (0.75, 2.5, 1.5), "ear")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "body", (4.5 * side, 29.5, 0), rotation=(0, 0, 3 * -side), cubes=[
                C((sx(3.5, 5.5), 17, -1), (2, 13, 2), "sleeve")]),
            B(f"{name}Forearm", f"{name}Arm", (4.5 * side, 17, 0), rotation=(-8, 0, 0), cubes=[
                C((sx(3.75, 5.25), 6, -0.75), (1.5, 11, 1.5), "forearm"),
                *[C((sx(3.5 + k * 0.5, 4 + k * 0.5), 1 - (k % 2), -0.5 + (k % 2) * 0.5), (0.5, 5 + (k % 2), 0.5),
                    "finger") for k in range(4)]]),
        ]
    return hd_model("geometry.md.intruder", bones, bounds=(2.0, 3.2, (0, 1.4, 0)))


def paint(seed, m, **_):
    pale = Ramp(colors=["#6a6c74", "#8a8c92", "#a8a8ac", "#c4c2c0", "#dcd8d2", "#eeeae2"])
    suit = Ramp(colors=["#060608", "#0c0c10", "#141418", "#1c1c22", "#26262e"])
    hair = Ramp(colors=["#060504", "#0c0a08", "#14100c", "#1e1812"])
    gum = Ramp(colors=["#2a0a0e", "#441218", "#5e1e24"])
    white = (0xF4, 0xF2, 0xEE)
    # The face, 18 wide and 20 tall: eyes far too big, pupils far too
    # small, and the grin.
    face = []
    for y in range(20):
        row = ""
        for x in range(18):
            ex = (4.5, 13.0)
            if 4 <= y <= 9 and any(abs(x - c) <= 3 - (y in (4, 9)) for c in ex):
                pupil = y in (6, 7) and any(abs(x - c) < 0.6 for c in ex)
                row += "P" if pupil else "W"
            elif 13 <= y <= 16:
                curve = 1 if x in (1, 16) else 0
                top, bottom = 13 - curve, 16 - (2 if x in (1, 16) else 1 if x in (2, 15) else 0)
                if 1 <= x <= 16 and top <= y <= bottom:
                    row += "K" if y in (top, bottom) else ("T" if x % 2 else "t")
                else:
                    row += "S"
            elif y == 11 and x in (8, 9):
                row += "N"
            else:
                row += "S"
        face.append(row)

    def tv(t, base):
        """Pale, with the faint colour fringe of a bad signal."""
        v = base + (0.06 if t.Y % 2 == 0 else -0.02)
        c = t.paint(pale, v, 0.2, 3.0)
        if t.noise(t.X * 0.15, t.Y * 4.0, 1.0, 1) > 0.95:
            return (min(255, c[0] + 30), c[1], min(255, c[2] + 40))
        return c

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "face":
            if f == "front":
                ch = face[t.y][t.x]
                if ch == "W":
                    return white
                if ch == "P":
                    return BLACK
                if ch == "K":
                    return t.paint(gum, 0.3, 0.1)
                if ch in "Tt":
                    return t.paint(TEETH, 0.8 if ch == "T" else 0.6, 0.08)
                if ch == "N":
                    return t.paint(pale, 0.25, 0.05)
                return tv(t, 0.55)
            if f == "top":
                return t.paint(hair, 0.5, 0.4)
            return tv(t, 0.45)
        if tag == "hair":
            if f in ("front", "bottom"):
                return CLEAR
            return t.paint(hair, 0.45, 0.5) if t.y < 5 or t.n(1.5) > 0.4 else CLEAR
        if tag == "ear":
            return tv(t, 0.4)
        if tag == "neck":
            return tv(t, 0.4 + 0.1 * (t.y % 4 == 0))
        if tag == "suit":
            if f == "front" and t.y < 4 and abs(t.x - 6.5) < 3 - t.y * 0.6:
                return white  # the shirt collar
            if f == "front" and t.x in (4, 9) and t.y > 3:
                return t.paint(suit, 0.2, 0.1)  # lapels
            return t.paint(suit, 0.5, 0.3, 3.0)
        if tag == "tie":
            return (0x5A, 0x08, 0x0C) if f == "front" else CLEAR
        if tag == "sleeve":
            return t.paint(suit, 0.5, 0.3, 3.0)
        if tag in ("forearm",):
            return t.paint(suit, 0.45, 0.3) if t.y < 3 else tv(t, 0.45)
        if tag == "finger":
            return tv(t, 0.4 - 0.2 * t.v)
        if tag == "leg":
            return t.paint(suit, 0.45, 0.3, 3.0)
        if tag == "shoe":
            return t.paint(suit, 0.2, 0.2) if f != "top" else t.paint(suit, 0.7, 0.1)
        return None

    return paint_model(m, seed, fn)
