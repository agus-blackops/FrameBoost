"""A Strobe (The Sun Vanished).

What came when the sun went out. Nobody has seen one clearly, because all
you can see of one is its light: a machine the size of a person hanging in
the air above the street, dark metal plates bolted round a core, with a
great round lamp on the front and smaller lamps all round it, ticking and
throbbing. The light strobes white while it searches; when it finds someone
it turns red, and that is the light you must never look into.

The lamps are three bones, one per colour (`white`, `red`, `blue`); the
render controller shows the one that matches `hh:light` and makes it strobe.
`hull` is the dark machine itself."""

from models.kit import *

PREVIEW = {"red": {"scale": (0, 0, 0)}, "blue": {"scale": (0, 0, 0)}}
LIGHTS = ("white", "red", "blue")


def lamps(colour):
    """The big front lens and the small lamps round the rim, in one colour."""
    cubes = [C((-3.5, 6.5, -6.25), (7, 7, 1), colour)]
    for k in range(8):
        a = math.radians(k * 45)
        x, y = math.cos(a) * 7.0, 10 + math.sin(a) * 7.0
        cubes.append(C((x - 0.75, y - 0.75, -4.75), (1.5, 1.5, 1), colour))
    return cubes


def model():
    bones = [
        B("root", None, (0, 0, 0)),
        # The machine hangs in the air: everything is from 2 to 18 px up.
        B("hull", "root", (0, 10, 0), cubes=[
            C((-5, 5, -5), (10, 10, 10), "metal"),
            C((-6, 6.5, -4), (12, 7, 8), "metal"),
            C((-4, 4, -4), (8, 12, 8), "plate"),
            C((-4.5, 6, -5.5), (9, 8, 1), "plate"),
            # Bolted fins round the rim and a spine of antennae on top.
            *[C((x, 9, -2), (1, 2, 6), "plate") for x in (-8, 7)],
            C((-0.5, 15, -0.5), (1, 5, 1), "metal"), C((2, 15, 1), (0.75, 3, 0.75), "metal"),
            # What hangs underneath: three cables, cut off.
            *[C((x, 0, z), (0.6, 4.5, 0.6), "cable") for x, z in ((-2, -1), (1.5, 0.5), (-0.5, 2))]]),
    ]
    for colour in LIGHTS:
        bones.append(B(colour, "hull", (0, 10, 0), cubes=lamps(colour)))
    return hd_model("geometry.hh.strobe", bones, bounds=(2.5, 2.5, (0, 0.6, 0)))


def paint(seed, m, **_):
    metal = Ramp(colors=["#060607", "#0c0c0f", "#141418", "#1e1e24", "#2a2a32", "#383842"])
    lit = {"white": glow("#ffffff", 95), "red": glow("#ff1010", 95), "blue": glow("#3c6cff", 95)}
    rim = {"white": glow("#c8c8d0", 70), "red": glow("#901010", 70), "blue": glow("#1c2c90", 70)}

    def fn(t):
        tag, f = t.tag, t.face
        if tag in lit:
            # A bright lens with a darker rim and a hot centre.
            if t.x in (0, t.fw - 1) or t.y in (0, t.fh - 1):
                return rim[tag]
            return lit[tag]
        if tag == "metal":
            if t.ridge(4.0, 0.04) > 0.7:
                return t.paint(metal, 0.2, 0.05)  # seams
            return t.paint(metal, 0.45, 0.35)
        if tag == "plate":
            if (t.x % 6 == 1 and t.y % 6 == 1) and t.side:
                return t.paint(IRON, 0.5, 0.1)  # rivets
            return t.paint(metal, 0.6 if t.side else 0.4, 0.3)
        if tag == "cable":
            return t.paint(metal, 0.3, 0.2)
        return None

    return paint_model(m, seed, fn)
