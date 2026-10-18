"""The Cave Dweller.

Something that has lived without light for a very long time: a starved,
hairless body the colour of a fish belly, carried low on four long limbs
whose elbows and knees stick up above its back like a spider's. It has no
eyes. The long skull ends in a snout of four lips; when it hunts they peel
back like petals from a round mouth with ring after ring of teeth. Veins show
through the skin; the hands and feet are black with cave mud."""

from models.kit import *

PREVIEW = {}

LIPS = ("lipTop", "lipBottom", "lipRight", "lipLeft")


def model():
    """v3: it crawls like something that used to walk upright. A long, narrow
    ribcage hunched forward, a bald skull on a neck too long for it, arms
    that reach the floor and knuckle along it, legs folded under it with the
    heel up like a dog's."""
    bones = [
        B("root", None, (0, 0, 0)),
        B("pelvis", "root", (0, 14, 3), rotation=(-10, 0, 0), cubes=[
            C((-3, 11, 1), (6, 5, 5), "pelvis"),
            C((-0.75, 12, 6), (1.5, 1.5, 4), "tail"), C((-0.5, 12, 10), (1, 1, 5), "tail_tip"),
            *[C((-0.5, 16, 1.5 + 1.5 * k), (1, 1, 1.5), "vertebra_s") for k in range(3)]]),
        B("ribcage", "pelvis", (0, 16, 1), rotation=(-38, 0, 0), cubes=[
            C((-3, 16, -1), (6, 9, 5.5), "ribcage"),
            *[C((-3.25, 17 + 2 * k, -1.25), (6.5, 0.5, 6), "rib_out") for k in range(4)],
            *[C((-0.75, 25, -0.5 + 1.5 * k), (1.5, 1.5, 1.5), "vertebra") for k in range(3)],
            *[C((-0.25, 26, -0.25 + 1.5 * k), (0.5, 1.5, 0.5), "spike") for k in range(3)]]),
        B("neck", "ribcage", (0, 24, 0), rotation=(50, 0, 0), cubes=[
            C((-1.5, 23, -1.5), (3, 7, 3), "neck")]),
        B("head", "neck", (0, 30, -0.5), rotation=(-10, 0, 0), cubes=[
            C((-3, 28, -5), (6, 7.5, 8), "head"),
            C((-2.5, 34.5, -4), (5, 1.5, 6), "crest"),
            C((-2.5, 28.5, -2), (5, 6, 3.5), "cranium"),
            C((-2.5, 28.5, -5.25), (5, 5, 0.25), "mouth"),
            *[C((x, 28 - h, -6.5 + z), (0.25, h, 0.25), "slime") for x, z, h in ((-2, 0, 4), (-0.5, -0.5, 6), (1.25, 0.25, 3), (2.25, -0.25, 5))]]),
        B("lipTop", "head", (0, 34, -5), cubes=[C((-3, 32.5, -8.5), (6, 1.5, 3.5), "lip_top")]),
        B("lipBottom", "head", (0, 28, -5), cubes=[C((-3, 28, -8.5), (6, 1.5, 3.5), "lip_bottom")]),
        B("lipRight", "head", (-3, 31, -5), cubes=[C((-3, 29.5, -8.5), (1.5, 3, 3.5), "lip_right")]),
        B("lipLeft", "head", (3, 31, -5), cubes=[C((1.5, 29.5, -8.5), (1.5, 3, 3.5), "lip_left")]),
    ]
    for side, s in ((-1, "Right"), (1, "Left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Arms from the shoulders straight down to the floor, a little forward.
            B(f"arm{s}", "ribcage", (4 * side, 23, 0.5), rotation=(34, 0, 4 * -side), cubes=[
                C((sx(3, 5), 11, -0.5), (2, 12, 2), "upper")]),
            B(f"fore{s}", f"arm{s}", (4 * side, 11.5, 0.5), rotation=(-8, 0, 0), cubes=[
                C((sx(3.25, 4.75), 3.5, -0.25), (1.5, 8, 1.5), "fore"),
                C((sx(3, 5), 10, -0.5), (2, 2, 2), "joint")]),
            B(f"hand{s}", f"fore{s}", (4 * side, 3.5, 0.5), rotation=(20, 0, 0), cubes=[
                C((sx(2.75, 5.25), 2.5, -2), (2.5, 1.5, 3.5), "palm"),
                *[C((sx(2.75 + k * 0.75, 3.25 + k * 0.75), 2.5, -7), (0.5, 0.5, 5), "claw") for k in range(4)]]),
            # Legs folded: thigh forward, shin back and down, long heel.
            B(f"thigh{s}", "pelvis", (2.5 * side, 14, 4), rotation=(-55, 0, 6 * side), cubes=[
                C((sx(1, 3.5), 5.5, 3), (2.5, 9, 2.5), "upper")]),
            B(f"shin{s}", f"thigh{s}", (2.5 * side, 6, 4), rotation=(80, 0, 0), cubes=[
                C((sx(1.25, 3.25), -3, 3.25), (2, 9, 2), "fore"),
                C((sx(1, 3.5), 5, 3), (2.5, 2.5, 2.5), "joint")]),
            B(f"foot{s}", f"shin{s}", (2.5 * side, -3, 4), rotation=(-35, 0, 0), cubes=[
                C((sx(1, 3.5), -4, 1), (2.5, 1.5, 4.5), "palm"),
                *[C((sx(1 + k * 0.75, 1.5 + k * 0.75), -4, -1.5), (0.5, 0.5, 3), "claw_s") for k in range(3)]]),
        ]
    return hd_model("geometry.hl.cave_dweller", bones, bounds=(2.5, 2.4, (0, 1.0, 0)))


def paint(seed, m, **_):
    skin = Ramp(colors=["#5a5c62", "#7e8086", "#a2a4a4", "#bec0bc", "#d6d6ce", "#eae8de"])
    vein = Ramp(colors=["#3e3448", "#56486a", "#6c5c84"])
    mud = Ramp(colors=["#120e0c", "#1e1814", "#2c241c", "#3a3024"])
    gum = Ramp(colors=["#2a0a10", "#48121c", "#6a2230", "#8c3a48", "#ae5a66"])

    def pale(t, base=0.55):
        if t.ridge(7.0, 0.035) > 0.5:
            return t.paint(vein, 0.5, 0.3)
        return t.paint(skin, base + (-0.12 if t.n2(4) > 0.72 else 0.0), 0.28, 2.5)

    def muddy(t, amount):
        """Mud climbs the limbs from the ground: amount 0..1 up the face."""
        return t.v > 1 - amount + (t.n(1.5) - 0.5) * 0.3

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "ribcage":
            if f in ("right", "left") and 2 <= t.y <= t.fh - 2 and t.x % 3 == 0 and t.x > 1:
                return t.paint(skin, 0.78, 0.1)  # ribs under the skin
            if f in ("right", "left") and t.x % 3 == 1:
                return t.paint(skin, 0.3, 0.2)
            if f == "bottom":
                return t.paint(skin, 0.4, 0.2)
            return pale(t, 0.55)
        if tag == "rib_out":
            return t.paint(skin, 0.75, 0.15) if f in ("right", "left") else CLEAR
        if tag == "pelvis":
            if f in ("right", "left") and 2 <= t.x <= 6 and 2 <= t.y <= 5:
                return t.paint(skin, 0.75 - 0.08 * abs(t.x - 4), 0.1)  # hip bone
            return pale(t, 0.5)
        if tag in ("vertebra", "vertebra_s"):
            return t.paint(BONE, 0.62, 0.2)
        if tag in ("tail", "tail_tip"):
            return pale(t, 0.4 if tag == "tail" else 0.3)
        if tag == "neck":
            if f == "bottom" and t.x % 2 == 0:
                return t.paint(skin, 0.3, 0.1)  # throat folds
            return pale(t, 0.45)
        if tag == "head":
            if f == "front":
                return t.paint(gum, 0.3, 0.3)
            if f in ("right", "left") and 3 <= t.y <= 5 and 3 <= (t.x if f == "left" else t.fw - 1 - t.x) <= 5:
                return t.paint(skin, 0.22, 0.1)  # where eyes should be: a shallow pit
            return pale(t, 0.55)
        if tag == "cranium":
            return pale(t, 0.45)
        if tag == "crest":
            return t.paint(BONE, 0.55, 0.3) if f == "top" and (t.x + t.y) % 3 == 0 else pale(t, 0.6)
        if tag == "mouth":
            if f != "front":
                return CLEAR
            # Rings of teeth closing on a black throat.
            cx, cy = (t.fw - 1) / 2, (t.fh - 1) / 2
            r = max(abs(t.x - cx), abs(t.y - cy)) + 0.3 * min(abs(t.x - cx), abs(t.y - cy))
            if r < 1.2:
                return BLACK
            ring = int(r)
            if ring % 2 == 1:
                return t.paint(TEETH, 0.75 - 0.08 * ring, 0.15) if (t.x + t.y) % 2 == 0 else t.paint(gum, 0.3, 0.2)
            return t.paint(gum, 0.45 + 0.05 * ring, 0.2)
        if tag.startswith("lip_"):
            inner = {"lip_top": "bottom", "lip_bottom": "top", "lip_right": "left", "lip_left": "right"}[tag]
            if f == "front":
                # Puckered: pale and wrinkled, raw only along the seam.
                seam = {"lip_top": t.y == t.fh - 1, "lip_bottom": t.y == 0,
                        "lip_right": t.x == t.fw - 1, "lip_left": t.x == 0}[tag]
                return t.paint(gum, 0.4, 0.2) if seam else t.paint(skin, 0.45 - 0.08 * (t.x % 2), 0.2)
            if f == inner:
                # Inside of the lips: rows of needle teeth.
                if t.x % 2 == 0 and t.y % 3 != 2:
                    return t.paint(TEETH, 0.75 - 0.2 * (t.y % 3), 0.1)
                return t.paint(gum, 0.25, 0.3)
            return pale(t, 0.5)
        if tag in ("upper", "fore", "joint"):
            if tag == "joint":
                return t.paint(skin, 0.7, 0.2)
            if tag == "fore" and muddy(t, 0.4):
                return t.paint(mud, 0.5, 0.4)
            return pale(t, 0.5 if tag == "upper" else 0.45)
        if tag == "palm":
            return t.paint(mud, 0.45, 0.4)
        if tag == "slime":
            return (0xC8, 0xC4, 0xB0, 150) if t.n(2) > 0.3 else CLEAR
        if tag == "spike":
            return t.paint(BONE, 0.7 - 0.3 * t.v, 0.15)
        if tag in ("claw", "claw_s") and t.n(2) > 0.55:
            return t.paint(BLOOD, 0.35, 0.2)
        if tag in ("claw", "claw_s"):
            return BLACK if t.x == t.fw - 1 or t.v > 0.8 else t.paint(mud, 0.25, 0.2)
        return None

    return paint_model(m, seed, fn)
