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
    bones = [
        B("root", None, (0, 0, 0)),
        B("ribcage", "root", (0, 9, 0), rotation=(-6, 0, 0), cubes=[
            C((-3.5, 7, -7), (7, 5, 8.5), "ribcage"),
            *[C((-3.75, 7.5, z), (7.5, 4, 0.5), "rib_out") for z in (-6, -4.5, -3, -1.5)],
            *[C((-0.75, 12, z), (1.5, 1, 1.5), "vertebra") for z in (-6.5, -4.5, -2.5, -0.5)]]),
        B("pelvis", "ribcage", (0, 9.5, 1.5), rotation=(10, 0, 0), cubes=[
            C((-3, 7.5, 1.5), (6, 4, 5), "pelvis"),
            *[C((-0.5, 11.5, z), (1, 1, 1.5), "vertebra_s") for z in (2, 4)],
            C((-0.75, 8.5, 6.5), (1.5, 1.5, 3), "tail"), C((-0.5, 8.25, 9.5), (1, 1, 2.5), "tail_tip")]),
        B("neck", "ribcage", (0, 10, -7), rotation=(-10, 0, 0), cubes=[
            C((-1.5, 8.5, -11), (3, 3, 4.5), "neck")]),
        B("head", "neck", (0, 10, -11), rotation=(14, 0, 0), cubes=[
            C((-3, 7, -17), (6, 6, 6.5), "head"),
            C((-2.5, 12.5, -16), (5, 1, 5), "crest"),
            C((-2.5, 8, -11), (5, 5.5, 2), "cranium"),
            C((-2.5, 7.5, -17.25), (5, 5, 0.25), "mouth")]),
        # The four lips, closed into a snout.
        B("lipTop", "head", (0, 13, -17), cubes=[C((-3, 11.5, -20.5), (6, 1.5, 3.5), "lip_top")]),
        B("lipBottom", "head", (0, 7, -17), cubes=[C((-3, 7, -20.5), (6, 1.5, 3.5), "lip_bottom")]),
        B("lipRight", "head", (-3, 10, -17), cubes=[C((-3, 8.5, -20.5), (1.5, 3, 3.5), "lip_right")]),
        B("lipLeft", "head", (3, 10, -17), cubes=[C((1.5, 8.5, -20.5), (1.5, 3, 3.5), "lip_left")]),
    ]
    for side, s in ((-1, "Right"), (1, "Left")):
        sx = lambda a, b: side_x(side, a, b)
        bones += [
            # Front limbs: the upper arm rises from the shoulder to an elbow
            # above the back, the forearm comes down to a long hand.
            B(f"arm{s}", "ribcage", (3 * side, 10, -5.5), rotation=(-10, 0, 40 * side), cubes=[
                C((sx(2, 4), 10, -6.5), (2, 10, 2), "upper")]),
            B(f"fore{s}", f"arm{s}", (3 * side, 20, -5.5), rotation=(10, 0, 62 * -side), cubes=[
                C((sx(2.25, 3.75), 3, -6.25), (1.5, 17, 1.5), "fore"),
                C((sx(2, 4), 18.5, -6.5), (2, 2, 2), "joint")]),
            B(f"hand{s}", f"fore{s}", (3 * side, 3, -5.5), rotation=(0, 0, 22 * side), cubes=[
                C((sx(1.75, 4.25), 1.5, -7), (2.5, 1.5, 3), "palm"),
                *[C((sx(1.75 + k * 0.75, 2.25 + k * 0.75), 1.5, -11), (0.5, 0.5, 4), "claw") for k in range(4)]]),
            # Hind legs: the same, folded like a grasshopper's.
            B(f"thigh{s}", "pelvis", (2.5 * side, 9, 4), rotation=(20, 0, 35 * side), cubes=[
                C((sx(1.5, 3.5), 9, 3), (2, 9, 2), "upper")]),
            B(f"shin{s}", f"thigh{s}", (2.5 * side, 18, 4), rotation=(-22, 0, 58 * -side), cubes=[
                C((sx(1.75, 3.25), 3, 3.25), (1.5, 15, 1.5), "fore"),
                C((sx(1.5, 3.5), 16.5, 3), (2, 2, 2), "joint")]),
            B(f"foot{s}", f"shin{s}", (2.5 * side, 3, 4), rotation=(-8, 0, 23 * side), cubes=[
                C((sx(1.5, 3.5), 1.5, 1.5), (2, 1.5, 3.5), "palm"),
                *[C((sx(1.5 + k * 0.75, 2 + k * 0.75), 1.5, -1), (0.5, 0.5, 2.5), "claw_s") for k in range(3)]]),
        ]
    return hd_model("geometry.hl.cave_dweller", bones, bounds=(2.5, 2.0, (0, 0.8, 0)))


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
        if tag in ("claw", "claw_s"):
            return BLACK if t.x == t.fw - 1 or t.v > 0.8 else t.paint(mud, 0.25, 0.2)
        return None

    return paint_model(m, seed, fn)
