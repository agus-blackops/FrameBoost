"""3D models for the items, seen in your hand (attachables).

Bedrock draws a held custom item as its flat icon pushed out into a thin
slab. An attachable replaces that with a real model: each item here is a
small entity model parented to the player's `rightItem` bone (the player
skeleton is repeated without cubes, so the game can match the bones), with
one animation for first person and one for third person that put it in the
hand the right way round. The inventory keeps the pixel-art icon.

Every item is built at double detail like the creatures, around its grip,
then moved to the hand.
"""

import math

from geometry import Model
from models.kit import B, C, K, SUB, CLEAR, BLACK, IRON, WOOD, Ramp, glow, paint_model

HAND = (-6, 13, 0)  # where the fist closes (rightItem pivot is (-6, 15, 1))
SKELETON = [
    ("root", None, (0, 0, 0)),
    ("waist", "root", (0, 12, 0)),
    ("body", "waist", (0, 24, 0)),
    ("head", "body", (0, 24, 0)),
    ("leftArm", "body", (5, 22, 0)),
    ("leftItem", "leftArm", (6, 15, 1)),
    ("rightArm", "body", (-5, 22, 0)),
    ("rightItem", "rightArm", (-6, 15, 1)),
    ("leftLeg", "root", (1.9, 12, 0)),
    ("rightLeg", "root", (-1.9, 12, 0)),
]


def at_hand(cubes):
    """Move cubes built around the grip (0, 0, 0) to the hand."""
    hx, hy, hz = (v * K for v in HAND)
    for c in cubes:
        x, y, z = c.origin
        c.origin = (x + hx, y + hy, z + hz)
        if c.pivot:
            px, py, pz = c.pivot
            c.pivot = (px + hx, py + hy, pz + hz)
    return cubes


def item_model(name, cubes):
    bones = [B(n, p, pv) for n, p, pv in SKELETON]
    bones.append(B(name, "rightItem", HAND, cubes=at_hand(cubes)))
    return Model(f"geometry.hh.item.{name}", bones, bounds=(2.0, 2.5, (0, 1.0, 0)), detail=K).pack_uv(64 * SUB)


# --------------------------------------------------------------------------- #
# The models (around the grip; -z is forward, the way you point it)
# --------------------------------------------------------------------------- #

def flashlight():
    """An old metal flashlight that has been through a lot: an eight-sided
    body with rubber grip rings and a strip of grey tape (bloody), a side
    switch between two guards with a small charge light, a pocket clip, a
    flared head with a toothed bezel round a deep reflector, and a leather
    strap swinging from the tail cap."""
    return item_model("flashlight", [
        C((-1.25, -1, -4.5), (2.5, 2, 8), "body"),
        C((-1, -1.25, -4.5), (2, 2.5, 8), "body"),
        *[C((-1.5, -1.5, z), (3, 3, 0.5), "grip") for z in (0, 1.25, 2.5)],
        C((-1.5, -1.5, -2.75), (3, 3, 1), "tape"),
        C((-1.75, -1.75, 3.5), (3.5, 3.5, 1.25), "tailcap"),
        C((-0.75, -0.75, 4.75), (1.5, 1.5, 0.5), "button"),
        C((-0.25, -2.25, 4.5), (0.5, 0.5, 1), "ring"),
        C((-0.25, -5.75, 4.75), (0.5, 3.5, 0.5), "strap", pivot=(0, -2.25, 5), rotation=(-12, 0, 6)),
        C((-1.75, -1.75, -5.75), (3.5, 3.5, 1.25), "neck"),
        C((-2.25, -2.25, -7.5), (4.5, 4.5, 1.75), "head"),
        *[C((x, y, -8), (1, 1, 0.5), "bezel") for x in (-2.25, 1.25) for y in (-2.25, 1.25)],
        C((-1.75, -1.75, -7.75), (3.5, 3.5, 0.25), "lens"),
        C((-0.5, 1.25, -4), (1, 0.5, 1.5), "switch"),
        *[C((x, 1.25, -4.25), (0.25, 0.75, 2), "guard") for x in (-0.875, 0.625)],
        C((-0.25, 1.25, -1.75), (0.5, 0.25, 0.5), "led"),
        C((1.5, -0.5, -1.5), (0.25, 1, 4), "clip"),
    ])


def battery():
    return item_model("battery", [
        C((-1, -2, -1), (2, 4, 2), "cell"),
        C((-0.5, 2, -0.5), (1, 0.5, 1), "nub"),
    ])


def journal():
    return item_model("journal", [
        C((-3, -0.75, -4), (6, 1.5, 8), "cover"),
        C((-2.5, -0.5, -3.75), (5.75, 1, 7.5), "leaves"),
        C((2.75, -1, -0.5), (0.5, 2, 1), "strap"),
        C((0.5, -0.5, 4), (0.5, 0.5, 2.5), "ribbon"),
    ])


def page():
    return item_model("page", [C((-2.5, -3.5, -0.25), (5, 7, 0.5), "sheet")])


def hollow_pickaxe():
    pivot = (0, 7, 0)
    return item_model("hollow_pickaxe", [
        C((-0.5, -6, -0.5), (1, 14, 1), "handle"),
        C((-0.75, -6.5, -0.75), (1.5, 1.5, 1.5), "pommel"),
        C((-1, 6, -1.5), (2, 2.5, 3), "head"),
        C((-0.75, 6.25, -6.5), (1.5, 2, 5), "blade", pivot=pivot, rotation=(14, 0, 0)),
        C((-0.5, 6.5, -8.5), (1, 1.5, 2), "tip", pivot=pivot, rotation=(26, 0, 0)),
        C((-0.75, 6.25, 1.5), (1.5, 2, 5), "blade", pivot=pivot, rotation=(-14, 0, 0)),
        C((-0.5, 6.5, 6.5), (1, 1.5, 2), "tip", pivot=pivot, rotation=(-26, 0, 0)),
    ])


# --------------------------------------------------------------------------- #
# Textures
# --------------------------------------------------------------------------- #

METAL = Ramp(colors=["#121216", "#1e1e24", "#2c2c34", "#3e3e48", "#565662", "#74747e"])
BRASS = Ramp(colors=["#4a3410", "#6e4e18", "#946c22", "#b88c30", "#d6aa46"])
COPPER = Ramp(colors=["#5a2a16", "#7e3e20", "#a2552c", "#c06e3c", "#d88c58"])
LEATHER = Ramp(colors=["#1a100a", "#2a1a10", "#3a2618", "#4c3220", "#5e402a"])
PAPER = Ramp(colors=["#8a7a5a", "#a89674", "#c4b28e", "#d8c8a4", "#e8dcbc"])
ROCK = Ramp(colors=["#0e0e12", "#1a1a20", "#26262e", "#34343c", "#44444c"])
SKIN = (0x9A, 0x86, 0x74)
LAVA = Ramp(colors=["#6a1404", "#b43008", "#e8640c", "#ffa028", "#ffd872"])


def paint_flashlight(seed, m):
    hot = glow("#fffbe8", 50)
    warm = glow("#ffe7a0", 60)
    rubber = Ramp(colors=["#060606", "#0e0e10", "#18181c", "#24242a"])
    tape = Ramp(colors=["#4e4e52", "#6c6c70", "#8a8a8e", "#a6a6a8"])
    blood = Ramp(colors=["#2a0404", "#4a0808", "#6a1010"])

    def scuffed(t, base):
        # Worn metal: paint scraped back to bright steel along the edges.
        if t.ridge(2.5, 0.05) > 0.6 or (t.fy == 0 and t.grain() > 0.6):
            return t.paint(IRON, 0.8, 0.1)
        return t.paint(METAL, base, 0.3)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "lens":
            if f != "front":
                return CLEAR
            # A bright core, a reflector of rings, a dark rim.
            cx, cy = (t.ffw - 1) / 2, (t.ffh - 1) / 2
            r = math.hypot(t.fx - cx, t.fy - cy) / max(1.0, cx)
            if r < 0.3:
                return hot
            if r < 0.85:
                return warm if int(r * 10) % 2 == 0 else glow("#d8b060", 80)
            return t.paint(METAL, 0.3, 0.1)
        if tag in ("head", "neck"):
            if tag == "head" and f == "front":
                return t.paint(METAL, 0.75, 0.1)
            return scuffed(t, 0.55 + 0.1 * (f == "top"))
        if tag == "bezel":
            return t.paint(IRON, 0.6 + 0.2 * (f == "front"), 0.2)
        if tag == "body":
            return scuffed(t, 0.5 + 0.1 * (f == "top"))
        if tag == "grip":
            if f in ("front", "back"):
                return CLEAR
            return t.paint(rubber, 0.5 + 0.3 * (t.fx % 2 == 0), 0.2)  # ribbed rubber
        if tag == "tape":
            if f in ("front", "back"):
                return CLEAR
            if t.n2(1.2) > 0.62:
                return t.paint(blood, 0.55, 0.3)  # a bloody thumbprint
            return t.paint(tape, 0.55 + 0.15 * (t.fx % 3 == 0), 0.2)
        if tag == "tailcap":
            if f == "back":
                return t.paint(METAL, 0.35, 0.2)
            return t.paint(METAL, 0.3 + 0.3 * ((t.fx + t.fy) % 2), 0.1)  # knurled
        if tag == "button":
            return t.paint(rubber, 0.4, 0.2)
        if tag in ("ring", "clip", "guard"):
            return t.paint(IRON, 0.65, 0.2)
        if tag == "switch":
            return t.paint(rubber, 0.7, 0.2) if f == "top" else t.paint(rubber, 0.4, 0.1)
        if tag == "led":
            return glow("#40ff60", 40) if f == "top" else (0x10, 0x30, 0x14)
        if tag == "strap":
            return t.paint(LEATHER, 0.45, 0.3)
        return SKIN  # (only the preview's stand-in player)

    return paint_model(m, seed, fn)


def paint_battery(seed, m):
    def fn(t):
        tag, f = t.tag, t.face
        if tag not in ("cell", "nub"):
            return SKIN
        if tag == "nub":
            return t.paint(IRON, 0.75, 0.1)
        if f == "top":
            return t.paint(COPPER, 0.6, 0.1)
        if f == "bottom":
            return t.paint(IRON, 0.5, 0.1)
        if t.y < 2:
            return t.paint(COPPER, 0.6, 0.2)  # copper top
        if t.y in (4, 5) and 1 <= t.x <= 2:
            return (0xF0, 0xD0, 0x20)  # the lightning mark
        return t.paint(Ramp(colors=["#2a0a0a", "#3e1010", "#561818", "#702424"]), 0.5, 0.2)

    return paint_model(m, seed, fn)


def paint_journal(seed, m):
    blood = (0x7A, 0x0C, 0x0C)

    def fn(t):
        tag, f = t.tag, t.face
        if tag == "cover":
            if f == "top":
                # A bloody handprint on the front cover.
                cx, cy = t.fw / 2, t.fh / 2
                dx, dy = t.x - cx, t.y - cy
                if abs(dx) <= 2 and -1 <= dy <= 3:
                    return blood
                if dy < -1 and dy >= -6 and abs(dx) <= 3 and (t.x % 2 == 0):
                    return blood  # fingers
                if t.x in (1, t.fw - 2) or t.y in (1, t.fh - 2):
                    return t.paint(LEATHER, 0.75, 0.1)  # stitching
            if f in ("front", "back", "left") and t.fh <= 3:
                return t.paint(LEATHER, 0.35, 0.1)
            return t.paint(LEATHER, 0.5, 0.3, 2.0)
        if tag == "leaves":
            if f in ("top", "bottom"):
                return CLEAR
            return t.paint(PAPER, 0.75 - 0.25 * (t.x % 2), 0.1)
        if tag == "strap":
            return t.paint(BRASS, 0.5, 0.2) if f == "right" and t.y == 1 else t.paint(LEATHER, 0.3, 0.2)
        if tag == "ribbon":
            return (0xA8, 0x14, 0x14)
        return SKIN  # (only the preview's stand-in player)

    return paint_model(m, seed, fn)


def paint_page(seed, m, icon):
    """The sheet: the page's own drawing (its 16x16 icon) on the front."""
    def fn(t):
        tag, f = t.tag, t.face
        if tag != "sheet":
            return SKIN
        if f in ("front", "back"):
            sx = 3 + int(t.x * 10 / t.fw)
            sy = 1 + int(t.y * 14 / t.fh)
            c = icon.get(sx if f == "front" else 15 - sx, sy)
            if c[3] == 0:
                return t.paint(PAPER, 0.7, 0.15)
            return c[:3] if f == "front" else t.paint(PAPER, 0.55, 0.15)
        return t.paint(PAPER, 0.5, 0.1) if tag == "sheet" else SKIN

    return paint_model(m, seed, fn)


def paint_hollow_pickaxe(seed, m):
    def fn(t):
        tag, f = t.tag, t.face
        if tag in ("handle", "pommel"):
            if t.ridge(2.0, 0.08) > 0.5:
                return glow(t.paint(LAVA, 0.5, 0.2), 70)
            return t.paint(WOOD, 0.3, 0.3) if tag == "handle" else t.paint(ROCK, 0.5, 0.2)
        if tag in ("head", "blade", "tip"):
            if t.ridge(3.0, 0.08) > 0.45:
                return glow(t.paint(LAVA, 0.6, 0.3), 70)
            return t.paint(ROCK, 0.5 + 0.15 * (f == "top"), 0.3)
        return SKIN  # (only the preview's stand-in player)

    return paint_model(m, seed, fn)


# --------------------------------------------------------------------------- #
# How each one sits in the hand
# --------------------------------------------------------------------------- #

# (first person, third person): rotation and position of the item bone, about
# the grip. Worked out against the player's arm (in first person the game
# swings the right arm to (95, -45, 115)) so the flashlight points where you
# look, the book lies open-side up, the page faces you and the pickaxe is
# held up and forward.
HOLD = {
    "flashlight": (([-90, -120, -50], [0, 0, 0]), ([20, 0, 0], [0, 0, 0])),
    "battery": (([-80, -110, -60], [0, 0, 0]), ([20, 0, 0], [0, 0, 0])),
    "journal": (([50, -60, 130], [0, 0, 0]), ([70, 0, -180], [0, 0, 0])),
    "page": (([70, 60, -50], [0, 0, 0]), ([20, 0, 0], [0, 0, 0])),
    "hollow_pickaxe": (([30, 60, -70], [0, 0, 0]), ([-60, -180, 0], [0, 0, 0])),
}


def animations():
    out = {}
    for name, ((r1, p1), (r3, p3)) in HOLD.items():
        out[f"animation.hh.item.{name}.first"] = {"loop": True, "bones": {name: {"rotation": r1, "position": p1}}}
        out[f"animation.hh.item.{name}.third"] = {"loop": True, "bones": {name: {"rotation": r3, "position": p3}}}
    return out


# The items and which model each uses.
ITEMS = {
    "hh:flashlight": "flashlight",
    "hh:battery": "battery",
    "hh:journal": "journal",
}
MODELS = {"flashlight": flashlight, "battery": battery, "journal": journal}


def attachable(identifier, model_name, texture):
    return {"format_version": "1.10.0", "minecraft:attachable": {"description": {
        "identifier": identifier,
        "materials": {"default": "entity_emissive_alpha", "enchanted": "entity_alphatest_glint"},
        "textures": {"default": f"textures/models/hh/{texture}", "enchanted": "textures/misc/enchanted_item_glint"},
        "geometry": {"default": f"geometry.hh.item.{model_name}"},
        "animations": {"first": f"animation.hh.item.{model_name}.first",
                       "third": f"animation.hh.item.{model_name}.third"},
        "scripts": {"animate": [{"first": "c.is_first_person"}, {"third": "!c.is_first_person"}]},
        "render_controllers": ["controller.render.item_default"],
    }}}
