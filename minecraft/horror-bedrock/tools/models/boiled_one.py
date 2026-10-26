"""The Boiled One (Doctor Nowhere's Boiled One Phenomenon), after its
full-body picture.

A tall black shape, hunched, standing in a field: no legs to speak of, just
a long dark mass from the ground to the shoulders, its edges ragged and
lit white like an overexposed photograph, a few white bristles standing off
it. Through the black of the chest, faintly, a ribcage and the dark red of
what is inside. On a thin black neck, the head: small, red as something
boiled, misshapen and wider at the top, two hollow eyes and a mouth open in
a scream, and white spikes standing out all round it like static. One arm,
thin as a stick and far too long, comes out low on its side and reaches down
to the ground, where a red hand with long fingers rests in the grass."""

from models.kit import *

PREVIEW = {}

HEAD = (0, 65, -6)  # the middle of the head, for the spikes


def spikes():
    """White spikes standing out all round the head, like static."""
    out = []
    cx, cy, cz = HEAD
    for i, a in enumerate(range(-80, 81, 16)):
        length = 5.5 + (i % 3) * 1.5
        out.append(C((cx - 0.125, cy, cz - 0.125), (0.25, length, 0.25), "spike",
                     pivot=(cx, cy, cz), rotation=((i % 2) * 14 - 7, 0, a)))
    return out


def bristles(side, ys):
    """A few white bristles standing off the side of the body."""
    out = []
    for k, (y, z) in enumerate(ys):
        x = side * (7.4 - y / 40)
        out.append(C((x - 0.125, y, z - 0.125), (0.25, 4 + (k % 3) * 1.5, 0.25), "spike",
                     pivot=(x, y, z), rotation=(0, 0, side * (70 + (k % 3) * 15))))
    return out


def model():
    bones = [B("root", None, (0, 0, 0))]
    bones += [
        # The long black mass, wider low down, ragged at the hem.
        B("body", "root", (0, 0, 0), cubes=[
            C((-8, 0, -6), (16, 14, 12), "shroud"),
            C((-7.5, 14, -5.5), (15, 17, 11), "shroud"),
            # It bulges out to one side, half way up.
            C((6.5, 16, -4.5), (3, 14, 8), "shroud"),
            C((-8.75, 0, -3), (1.5, 6, 5), "shroud"), C((7.25, 0, -2), (1.5, 8, 4), "shroud"),
            *bristles(-1, [(8, -1), (18, 2), (27, -2)]), *bristles(1, [(5, 1), (14, -2), (24, 1), (33, -1)]),
        ]),
        # The chest and the hunched shoulders, leaning forward.
        B("chest", "body", (0, 31, 0), rotation=(-12, 0, 2), cubes=[
            C((-7, 31, -5), (14, 14, 10), "shroud"),
            C((-4.5, 32.5, -5.4), (9, 11, 0.5), "ribs"),
            C((-6.5, 45, -4.5), (13, 8, 9), "shroud"),
            C((-5, 52.5, -4), (10, 4, 8), "shroud"),
            *bristles(1, [(40, 0), (47, -2)]), *bristles(-1, [(37, 1), (49, -1)]),
        ]),
        B("neck", "chest", (0, 56, -2), rotation=(-24, 0, 0), cubes=[
            C((-1, 55.5, -3), (2, 7, 2), "neck")]),
        # The head: small, red, wider at the top, screaming.
        B("head", "neck", (0, 62, -4), rotation=(10, 0, -8), cubes=[
            C((-2.5, 61, -8.5), (5, 3, 5), "jaw"),
            C((-3.5, 63.5, -9), (7, 5, 6), "head"),
            C((-4.75, 67.5, -8.5), (9.5, 3, 5.5), "head"),
            C((1.5, 70, -7.5), (3, 1.5, 3.5), "head"),
            *spikes(),
        ]),
        # The one long arm, out of its side low down, to the ground.
        B("rightArm", "body", (-7, 24, -3), rotation=(-10, 0, 42), cubes=[
            C((-7.6, 10, -3.6), (1.2, 14, 1.2), "limb")]),
        B("rightForearm", "rightArm", (-7, 10.5, -3), rotation=(10, 0, -64), cubes=[
            C((-7.5, 0, -3.5), (1, 11, 1), "limb"),
            C((-8.5, -1.5, -5), (3, 2, 3.5), "hand"),
            *[C((-8.8 + k * 0.9, -1.5, -9), (0.5, 0.6, 4.5), "claw", pivot=(-8.5 + k * 0.9, -1.2, -5),
                rotation=(0, (k - 1.5) * 16, 0)) for k in range(4)],
            C((-6, -1.5, -5.5), (0.5, 0.6, 3), "claw", pivot=(-5.75, -1.2, -4.5), rotation=(0, -50, 0)),
        ]),
        # The other arm, hanging hidden against its side.
        B("leftArm", "body", (6.5, 44, -1), rotation=(0, 0, 6), cubes=[
            C((6, 26, -1.5), (1, 18, 1), "limb")]),
    ]
    return hd_model("geometry.hb.boiled_one", bones, bounds=(2.2, 4.8, (0, 2.3, 0)), width=512)


def paint(seed, m, **_):
    black = Ramp(colors=["#020203", "#060607", "#0b0b0d", "#121215", "#1a1a1e"])
    flesh = Ramp(colors=["#3a0605", "#5a0c0a", "#7c1410", "#9c2218", "#b8342a", "#cc4a3a"])
    gut = Ramp(colors=["#140203", "#260506", "#3a0a0a", "#4e1210", "#621a16"])
    edge = glow("#e8e8e0", 85)
    dim_edge = glow("#a0a098", 60)
    spike = glow("#d6d6ce", 80)
    hole = (0x05, 0x01, 0x01)
    pupil = glow("#ff2010", 70)

    def outline(t, fill):
        """The ragged white edge of the silhouette, like an overexposed photo."""
        if t.side:
            # How far into the face from its left and right edges.
            d = min(t.fx, t.ffw - 1 - t.fx)
            ragged = 1 + int(t.n(1.2) * 4)
            if d < ragged * 0.6:
                return edge if t.n2(0.8) > 0.35 else dim_edge
            if d < ragged and t.n2(0.6) > 0.7:
                return dim_edge
        return fill()

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "neck":
            return t.paint(black, 0.3, 0.3)
        if tag == "limb":
            # Thin as a stick: black, a hairline of white down the edges.
            if t.side and min(t.fx, t.ffw - 1 - t.fx) == 0 and t.n2(0.7) > 0.4:
                return edge
            return t.paint(black, 0.3, 0.3)
        if tag == "shroud":
            if f == "bottom":
                return t.paint(black, 0.1, 0.1)
            return outline(t, lambda: t.paint(black, 0.35, 0.4, 2.0))
        if tag == "ribs":
            if f != "front":
                return t.paint(black, 0.3, 0.2)
            # A ribcage seen through the black: curved ribs, the spine, and
            # the dark red of what is inside, low down.
            cx = (t.fw - 1) / 2
            dx = abs(t.x - cx) / max(1, cx)
            if dx < 0.12:
                return t.paint(gut, 0.7, 0.2)  # the spine
            rib = (t.y + int(dx * dx * 3)) % 3 == 0 and t.y < t.fh * 0.7
            if rib:
                return t.paint(gut, 0.72 - dx * 0.3, 0.2)
            if t.y >= t.fh * 0.7 and t.n(1.5) > 0.45:
                return t.paint(gut, 0.55, 0.3)
            return t.paint(black, 0.25, 0.3)
        if tag in ("head", "jaw"):
            if f == "front" and tag == "head" and t.fw == 14:
                # Two hollow eyes, wide apart, a little uneven.
                for cx, cy in ((3, 3), (10, 4)):
                    dd = (t.x - cx) ** 2 + (t.y - cy) ** 2 * 1.4
                    if dd <= 4.2:
                        return pupil if dd <= 0.3 else hole
            if f == "front" and tag == "jaw":
                # The mouth, open in a scream, down to the chin.
                if 1 <= t.x <= t.fw - 2 and t.y <= t.fh - 2:
                    return hole if (t.x, t.y) not in ((1, 0), (t.fw - 2, 0)) else t.paint(flesh, 0.3, 0.2)
            if f == "front" and tag == "head" and t.fw == 14 and t.y >= t.fh - 2 and 3 <= t.x <= 10:
                return hole  # the top of the open mouth
            if t.ridge(3.0, 0.05) > 0.75:
                return t.paint(flesh, 0.15, 0.1)  # splits in the skin
            return t.paint(flesh, 0.5, 0.5, 1.8)
        if tag == "spike":
            return spike
        if tag == "hand":
            return t.paint(flesh, 0.45, 0.4)
        if tag == "claw":
            return t.paint(flesh, 0.3 + 0.3 * t.u, 0.2)
        return None

    return paint_model(m, seed, fn)
