"""The Evil Hunter (DregIr's Evil Hunter).

What answers the ritual: a corrupted man, a head taller than you and
hunched. He still wears what he died in: a worn red flannel jacket, open,
over a dark shirt and dark trousers, all of it scorched and holed. From the
chest up he is burned: the flesh is charred, deformed and swollen, split all
over with cracks that glow crimson and molten red, as if he were still
burning inside, and the jacket is burned through where the cracks are. From
the waist down he is going to shadow: the trousers darken to black and the
legs end in smoke. His face is hard to make out at all, a melted shape in
the dark with two red points for eyes. The hands are burned to the bone,
with long black claws. (The red aura round him is particles, from the
script.)"""

from models.kit import *

PREVIEW = {}


def model():
    bones = [B("root", None, (0, 0, 0))]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Leg", "root", (2.25 * side, 18, 0), cubes=[
                C((sx(0.5, 4.25), 9.5, -1.9), (3.75, 9, 3.75), "trousers"),
                C((sx(0.25, 4.5), 16, -2.1), (4.25, 2.5, 4.2), "trousers")]),
            B(f"{name}Shin", f"{name}Leg", (2.25 * side, 10, 0), cubes=[
                C((sx(0.75, 4), 1.5, -1.6), (3.25, 8.75, 3.25), "shadow_leg"),
                # The legs end in smoke: wisps where the feet should be.
                C((sx(0.5, 4.25), 0, -2.75), (3.75, 1.75, 4.5), "smoke"),
                *[C((sx(0.5 + k * 1.2, 1.25 + k * 1.2), 0, 1.5 + (k % 2) * 0.5), (0.75, 2.5 - k * 0.5, 1.25), "smoke")
                  for k in range(3)]]),
        ]
    bones += [
        B("hips", "root", (0, 18, 0), cubes=[
            C((-4.5, 17.5, -2.25), (9, 3.5, 4.5), "trousers"),
            C((-4.75, 20, -2.5), (9.5, 1, 5), "belt")]),
        # Hunched forward over you.
        B("chest", "hips", (0, 21, 0), rotation=(-16, 0, 3), cubes=[
            C((-4, 21, -2), (8, 10.5, 4), "shirt"),
            # The open flannel jacket: back, sides, two front panels and a collar.
            C((-4.75, 17.5, 1.75), (9.5, 14, 0.75), "flannel"),
            C((-4.75, 17.5, -2.4), (0.75, 14, 4.15), "flannel"),
            C((4, 17.5, -2.4), (0.75, 14, 4.15), "flannel"),
            C((-4.75, 17.5, -2.75), (3.25, 14, 0.75), "flannel"),
            C((1.5, 17.5, -2.75), (3.25, 14, 0.75), "flannel"),
            C((-5, 30.5, -2.75), (10, 1.75, 5.25), "collar"),
            C((-1.5, 31.5, -1.25), (3, 1, 2.5), "flesh"),
            # One shoulder swollen and split, burned through the jacket.
            C((-6.25, 27.5, -2.25), (3, 4.5, 4.5), "flesh", 0.2)]),
        B("neck", "chest", (0, 32, 0), rotation=(-10, 0, 0), cubes=[
            C((-1.75, 31.5, -1.75), (3.5, 3, 3.5), "flesh")]),
        B("head", "neck", (0, 34.5, 0), rotation=(16, -10, 12), cubes=[
            C((-3.5, 34.5, -3.5), (7, 8, 7), "skull"),
            C((-4.75, 37, -2.75), (1.75, 4.5, 5), "skull"),  # melted, bulging on one side
            C((-2.75, 42.5, -2.75), (5, 1.25, 5), "skull"),
            C((1.5, 42, -2), (2, 1.25, 3.5), "skull"),
            C((-3, 38, -4.25), (6, 1.75, 1), "brow"),
            C((0.5, 33.25, -3.75), (2.5, 1.5, 2.5), "skull")]),
        # What's left of a face, sagging to one side: it works like a jaw.
        B("jaw", "head", (0, 36.5, -2.5), rotation=(6, 0, -6), cubes=[
            C((-3.25, 34, -4), (5, 2.75, 1), "face_low")]),
    ]
    for side, name in ((-1, "right"), (1, "left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(f"{name}Arm", "chest", (5.75 * side, 30, 0), rotation=(14, 0, 9 * -side), cubes=[
                C((sx(4.75, 8.25), 19.5, -1.75), (3.5, 11, 3.5), "sleeve")]),
            B(f"{name}Forearm", f"{name}Arm", (6.5 * side, 20, 0), rotation=(-12, 0, 0), cubes=[
                C((sx(4.75, 8.25), 15, -1.75), (3.5, 5, 3.5), "sleeve"),
                C((sx(4.5, 8.5), 14, -2), (4, 1.25, 4), "cuff"),
                C((sx(5, 8), 9, -1.5), (3, 5.5, 3), "flesh")]),
            B(f"{name}Hand", f"{name}Forearm", (6.5 * side, 9.5, 0), cubes=[
                C((sx(4.75, 8.25), 6.5, -1.75), (3.5, 3, 3.5), "flesh"),
                *[C((sx(4.9 + k * 1.05, 5.5 + k * 1.05), 0.5 + (k == 1) * -0.75, -1.5 + k * 0.8), (0.6, 6.5 + (k == 1), 0.6),
                    "claw") for k in range(3)],
                C((sx(4.75, 5.4), 3.5, 1.2), (0.6, 3.5, 0.6), "claw")]),
        ]
    return hd_model("geometry.hl.evil_hunter", bones, bounds=(1.6, 3.0, (0, 1.3, 0)))


def paint(seed, m, **_):
    shadow = Ramp(colors=["#020203", "#050406", "#09070a", "#0f0b0e", "#171013"])
    trousers = Ramp(colors=["#08080b", "#0f0f14", "#17171e", "#20212a", "#2a2c36"])
    shirt = Ramp(colors=["#0a0a0c", "#121216", "#1a1a20", "#24242a"])
    flannel = Ramp(colors=["#1c0606", "#3a0a0c", "#5e1014", "#8a1a1c", "#a82424", "#c4342c"])
    char = Ramp(colors=["#060203", "#100406", "#1c0808", "#2a0c0a", "#3a120c"])
    ember = glow("#ff3a12", 55)
    molten = glow("#ffb040", 70)
    eye = glow("#ff1a10", 90)
    soot = (0x0c, 0x08, 0x08)

    def cracks(t, base=0.45, ramp=BURN):
        r = t.ridge(3.5, 0.05)
        if r > 0.72:
            return molten if t.grain() > 0.55 else ember
        if r > 0.55:
            return t.paint(BURN, 0.62, 0.2)
        return t.paint(ramp, base, 0.45, 2.0)

    def plaid(t, base):
        # Red flannel: dark stripes across and down, black where they cross,
        # worn pale at the edges, and burned through in holes that still glow.
        burn = t.n2(1.7)
        if burn > 0.84:
            return ember if burn > 0.89 and t.grain() > 0.4 else soot
        if burn > 0.79:
            return t.paint(char, 0.5, 0.1)
        sx, sy = int(t.sx) % 8, int(t.sy) % 8
        across, down = sy in (0, 1, 2), sx in (0, 1, 2)
        worn = (t.n(3.0) - 0.5) * 0.12
        if across and down:
            return t.paint(flannel, base - 0.5 + worn, 0.05)
        if across or down:
            return t.paint(flannel, base - 0.28 + worn, 0.08)
        if sx == 5 or sy == 5:
            return t.paint(flannel, base - 0.55, 0.05)  # the thin black line
        return t.paint(flannel, base + worn, 0.1, 2.5)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "smoke":
            if t.n(1.2) > 0.62:
                return CLEAR
            return t.paint(shadow, 0.3, 0.4)
        if tag == "shadow_leg":
            # Trousers going to shadow, darker towards the ground.
            if t.ridge(5.0, 0.02) > 0.9 and t.n(2.0) > 0.6:
                return ember
            return t.paint(trousers if t.v < 0.3 else shadow, 0.45 - 0.2 * t.v, 0.4, 1.6)
        if tag == "trousers":
            if t.n2(2.0) > 0.8:
                return soot  # scorched
            return t.paint(trousers, 0.45, 0.35)
        if tag == "belt":
            return t.paint(Ramp(colors=["#120a06", "#1e120a", "#2c1a0e"]), 0.5, 0.2)
        if tag == "shirt":
            # The shirt is burned open over the chest: flesh and cracks above.
            if f == "front" and (t.v < 0.55 or t.n(1.5) > 0.62):
                return cracks(t, 0.4)
            return t.paint(shirt, 0.45, 0.3)
        if tag in ("flannel", "sleeve"):
            return plaid(t, 0.62)
        if tag in ("collar", "cuff"):
            return plaid(t, 0.45)
        if tag in ("flesh",):
            return cracks(t, 0.42)
        if tag in ("skull", "brow"):
            # Charred nearly black, the face lost in its own shadow, a few
            # cracks still glowing.
            r = t.ridge(3.0, 0.04)
            if r > 0.8:
                return molten if t.grain() > 0.6 else ember
            if r > 0.66:
                return t.paint(BURN, 0.45, 0.2)
            return t.paint(char, 0.45 if f != "front" else 0.3, 0.45, 1.8)
        if tag == "face_low":
            if f == "top":
                return (0x14, 0x02, 0x03)
            return t.paint(char, 0.35, 0.4)
        if tag == "claw":
            return t.paint(shadow, 0.6 - 0.3 * t.v, 0.2)
        return None

    def with_eyes(t):
        # Two red points in the dark under the brow, one higher than the other.
        if t.tag == "skull" and t.face == "front" and t.fw >= 12:
            if (t.x, t.y) in ((3, 9), (4, 9), (3, 10), (4, 10)) or (t.x, t.y) in ((9, 8), (10, 8), (9, 9), (10, 9)):
                return eye
            if 2 <= t.x <= 5 and 8 <= t.y <= 11 or 8 <= t.x <= 11 and 7 <= t.y <= 10:
                return (0x04, 0x01, 0x01)
        return fn(t)

    return paint_model(m, seed, with_eyes)
