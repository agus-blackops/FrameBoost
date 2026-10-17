"""The Mimic.

A chest. Exactly a chest, until you go to open it: the lid is a jaw lined
with teeth, the inside is wet red flesh with three yellow eyes at the back,
a long tongue slides out, and six thin black legs unfold from underneath
(they are hidden while it pretends; the animations lift the body onto
them)."""

from models.kit import *

PREVIEW = {"body": {"position": (0, 12, 0)}, "lid": (-45, 0, 0), "tongue": (25, 0, 0), "tongueTip": (20, 0, 0)}
LEGS = ("legFrontRight", "legFrontLeft", "legMidRight", "legMidLeft", "legBackRight", "legBackLeft")


def model():
    bones = [
        B("root", None, (0, 0, 0)),
        B("body", "root", (0, 0, 0), cubes=[
            C((-7, 0, -7), (14, 10, 14), "base"),
            C((-6, 1, -6), (12, 8.75, 12), "flesh"),
            *[C((x, 9.5, -6.5), (1, 1.5, 0.5), "tooth") for x in (-5.5, -3.5, -1.5, 0.5, 2.5, 4.5)],
            *[C((x, 9.5, 6), (1, 1.5, 0.5), "tooth") for x in (-4.5, -0.5, 3.5)],
            *[C((x, 9.5, z), (0.5, 1.5, 1), "tooth_side") for x in (-6.5, 6) for z in (-3, 1)]]),
        B("lid", "body", (0, 10, 7), cubes=[
            C((-7, 10, -7), (14, 4, 14), "lid"),
            C((-1, 7, -8), (2, 4, 1), "latch"),
            *[C((x, 9, -6.5), (1, 1.5, 0.5), "tooth") for x in (-4.5, -2.5, -0.5, 1.5, 3.5, 5)],
            *[C((x, 9, z), (0.5, 1.5, 1), "tooth_side") for x in (-6.5, 6) for z in (-5, -1, 3)]]),
        B("tongue", "body", (0, 9, 0), cubes=[C((-1.5, 8.5, -8), (3, 1, 8), "tongue")]),
        B("tongueTip", "tongue", (0, 9, -8), rotation=(30, 0, 0), cubes=[C((-1, 8.5, -13), (2, 1, 5), "tongue_tip")]),
    ]
    for name, x, z, yaw in (("legFrontRight", -6, -5, 30), ("legFrontLeft", 6, -5, -30),
                            ("legMidRight", -6, 0, 0), ("legMidLeft", 6, 0, 0),
                            ("legBackRight", -6, 5, -30), ("legBackLeft", 6, 5, 30)):
        side = 1 if x > 0 else -1
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            B(name, "body", (x, 1, z), rotation=(0, yaw, 70 * -side), cubes=[
                C((sx(abs(x), abs(x) + 1), -9, z - 0.5), (1, 10, 1), "leg"),
                C((sx(abs(x) - 0.25, abs(x) + 1.25), 0, z - 0.75), (1.5, 1.5, 1.5), "joint")]),
            B(name + "Tip", name, (x, -9, z), rotation=(0, 0, 60 * side), cubes=[
                C((sx(abs(x) + 0.25, abs(x) + 0.75), -19, z - 0.25), (0.5, 10, 0.5), "leg_tip")]),
        ]
    return hd_model("geometry.hl.mimic", bones, bounds=(3.0, 2.0, (0, 0.5, 0)))


def paint(seed, m, **_):
    oak = Ramp(colors=["#3c2812", "#56391a", "#6e4a22", "#8a5e2c", "#a47438", "#ba8a48"])
    frame = Ramp(colors=["#24180a", "#34220e", "#463014"])
    flesh = Ramp(colors=["#2a0608", "#44100e", "#661a18", "#862a24", "#a83e34"])
    chitin = Ramp(colors=["#060506", "#100c0e", "#1c1618", "#2a2224", "#3a3032"])
    yellow = glow("#f0d020", 50)

    def planks(t, base=0.55):
        # Horizontal boards with dark seams, the frame darker at the edges,
        # like the chest it is copying.
        if t.x in (0, t.fw - 1) or t.y in (0, t.fh - 1):
            return t.paint(frame, 0.5, 0.3)
        if t.y % 8 == 0:
            return t.paint(oak, 0.1, 0.1)
        grain = t.noise(t.X * 0.3, t.Y * 2.0, 2.0, 1)
        return t.paint(oak, base + (grain - 0.5) * 0.4, 0.15)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "base":
            if f == "top":
                return CLEAR  # open at the top: the flesh shows inside
            return planks(t, 0.55)
        if tag == "lid":
            if f == "bottom":
                # Three eyes in the roof of its mouth.
                for ex, ey in ((8, 15), (14, 12), (20, 15)):
                    if abs(t.x - ex) <= 1 and abs(t.y - ey) <= 1:
                        return BLACK if t.x == ex else yellow
                    if abs(t.x - ex) <= 2 and abs(t.y - ey) <= 2:
                        return t.paint(flesh, 0.1, 0.1)
                return t.paint(flesh, 0.3, 0.4)
            return planks(t, 0.6)
        if tag == "latch":
            if f == "front" and 2 <= t.y <= 4 and 1 <= t.x <= 2:
                return (0x14, 0x14, 0x16)
            return t.paint(IRON, 0.6, 0.2)
        if tag == "flesh":
            if f == "top":
                return t.paint(flesh, 0.35 + t.n(2.0) * 0.3, 0.2)
            return t.paint(flesh, 0.3, 0.4, 2.0)
        if tag in ("tooth", "tooth_side"):
            return t.paint(TEETH, 0.75 - 0.35 * t.v, 0.12)
        if tag in ("tongue", "tongue_tip"):
            if f == "top" and t.x == t.fw // 2:
                return t.paint(GUT, 0.2, 0.1)
            return t.paint(GUT, 0.5, 0.3)
        if tag in ("leg", "leg_tip", "joint"):
            if tag == "joint":
                return t.paint(chitin, 0.6, 0.2)
            if tag == "leg" and t.y % 5 == 0:
                return t.paint(chitin, 0.75, 0.1)  # segments
            return t.paint(chitin, 0.4, 0.4)
        return None

    return paint_model(m, seed, fn)
