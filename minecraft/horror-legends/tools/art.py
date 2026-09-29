"""Item icons, block textures and the pack icon, drawn as 16x16 pixel
patterns (one character per pixel, each character a colour)."""

import random

from paint import Ramp
from pixels import Canvas, jitter

CLEAR = (0, 0, 0, 0)


def draw(pattern, palette, cv=None, ox=0, oy=0):
    cv = cv or Canvas(len(pattern[0]), len(pattern))
    for y, row in enumerate(pattern):
        for x, ch in enumerate(row):
            if ch != "." and ch in palette:
                cv.set(ox + x, oy + y, palette[ch])
    return cv


def journal():
    return draw([
        "................",
        "..kkkkkkkkkkkk..",
        "..kLLLLLLLLLLpk.",
        "..kLsssssssslpk.",
        "..kLsLLLLLLslpk.",
        "..kLsLhLhLLslpk.",
        "..kLsLhhhhLslpk.",
        "..kLsLhhhhLslpk.",
        "..kLsLLhhLLslpk.",
        "..kLsLLLLLLslpk.",
        "..kLsssssssslpk.",
        "..kLLLLLLLLLLpk.",
        "..kLLLLLLLrLLpk.",
        "..kkkkkkkkrkkkk.",
        "..........r.....",
        "..........R.....",
    ], {"k": (0x10, 0x0c, 0x0a), "L": (0x3a, 0x26, 0x1c), "l": (0x2c, 0x1c, 0x14), "s": (0x6a, 0x4e, 0x36),
        "p": (0xe0, 0xd4, 0xb8), "h": (0x8a, 0x10, 0x12), "r": (0xb4, 0x18, 0x18), "R": (0x7a, 0x0c, 0x0c)})


def flashlight():
    return draw([
        "............yyy.",
        "...........yYYYy",
        "..........yYWWYy",
        ".........oyYWWYy",
        "........ooOyYYy.",
        ".......ooOOOyy..",
        "......ggOOOo....",
        ".....gGgOOo.....",
        "....gGgGgo......",
        "...gGgGgg.......",
        "..gGgGgg........",
        ".gGgGgg.........",
        ".ggGgg..........",
        ".gggg...........",
        "..gg............",
        "................",
    ], {"g": (0x22, 0x22, 0x28), "G": (0x3e, 0x3e, 0x48), "o": (0x2e, 0x2e, 0x34), "O": (0x5a, 0x5a, 0x66),
        "y": (0xd8, 0xa8, 0x20), "Y": (0xff, 0xec, 0x9c), "W": (0xff, 0xff, 0xf0)})


def battery():
    return draw([
        "................",
        "......cc........",
        ".....cCCc.......",
        ".....kkkk.......",
        "....kKKKKk......",
        "....kKKyKk......",
        "....kKyyKk......",
        "....kyyyyk......",
        "....kKKyKk......",
        "....kKyKKk......",
        "....kKKKKk......",
        "....kGGGGk......",
        "....kGGGGk......",
        "....kkkkkk......",
        ".....ssss.......",
        "................",
    ], {"k": (0x0c, 0x0c, 0x10), "K": (0x26, 0x26, 0x2e), "y": (0xf0, 0xc8, 0x28), "c": (0x9a, 0x5a, 0x2a),
        "C": (0xd8, 0x8a, 0x48), "G": (0x3a, 0x7a, 0x3a), "s": (0x8a, 0x8a, 0x92)})


PAPER = {"p": (0xd8, 0xca, 0xa4), "P": (0xe8, 0xdc, 0xbc), "e": (0xa8, 0x94, 0x6c), "i": (0x2a, 0x22, 0x1c),
         "r": (0x8a, 0x10, 0x12), "w": (0xff, 0xff, 0xff), "g": (0x7a, 0x7a, 0x80), "y": (0xf0, 0xd8, 0x60),
         "m": (0xf8, 0x00, 0xf8), "k": (0x08, 0x08, 0x0a), "b": (0x5e, 0x4a, 0x30), "t": (0x9a, 0x8c, 0x74)}

PAGE = [
    "................",
    "..epPpPpPpePp...",
    ".epPPPPPPPPPPe..",
    ".ePPPPPPPPPPPPe.",
    "..PPPPPPPPPPPPe.",
    ".ePPPPPPPPPPPe..",
    ".ePPPPPPPPPPPPe.",
    "..PPPPPPPPPPPPe.",
    ".ePPPPPPPPPPPPe.",
    ".ePPPPPPPPPPPe..",
    "..PPPPPPPPPPPPe.",
    ".ePPPPPPPPPPPPe.",
    ".ePPPPPPPPPPPe..",
    "..ePpPePpPpePe..",
    "................",
    "................",
]

DRAWINGS = {
    1: [  # a player's head with white eyes
        "....iiiiii....",
        "....ibbbbi....",
        "....iwbbwi....",
        "....ibbbbi....",
        "....iibbii....",
        "....iiiiii....",
        "..............",
        "..tt.tt.tt.tt.",
    ],
    2: [  # four eyes in the dark
        "...kkkkkkkk...",
        "..kkykkkkykk..",
        "..kkkkkkkkkk..",
        "..kkkykkykkk..",
        "...kkkkkkkk...",
        "....kwkwkw....",
        "..............",
        "..tt.tt.tt....",
    ],
    3: [  # his pickaxe
        "..gggggggg....",
        ".g......b.g...",
        "........b.....",
        ".......b......",
        "......b.......",
        ".....b....rr..",
        "....b......r..",
        "..tt.tt.tt....",
    ],
    4: [  # a tall figure in the fog
        "gg.....k....gg",
        ".g....kwk...g.",
        "gg.....k....gg",
        "......kkk.....",
        ".g...k.k.k..g.",
        "......k.k.....",
        "gg...k...k..gg",
        "..tt.tt.tt....",
    ],
    5: [  # the missing texture
        "...mmkkmmkk...",
        "...mmkkmmkk...",
        "...kkmmkkmm...",
        "...kkmmkkmm...",
        "..............",
        "...i.ii.i.i...",
        "...ii.i.i.i...",
        "..tt.tt.tt....",
    ],
}


def page(n):
    cv = draw(PAGE, PAPER)
    draw(DRAWINGS[n], PAPER, cv, 1, 3)
    # A smear of old blood in a corner.
    rng = random.Random(n)
    for _ in range(4):
        x, y = rng.randint(9, 12), rng.randint(9, 12)
        if cv.get(x, y)[3]:
            cv.set(x, y, (0x7a, 0x14, 0x14))
    return cv


def hollow_pickaxe():
    return draw([
        "................",
        "...kkkkkk.......",
        "..kCCoCCCkk.....",
        ".kCOkkkkCoCk....",
        ".kCk....kkCOk...",
        ".kk......bkCk...",
        "........b.kOk...",
        ".......b...kCk..",
        "......b.....kk..",
        ".....b..........",
        "....B...........",
        "...b............",
        "..B.............",
        ".b..............",
        "b...............",
        "................",
    ], {"k": (0x0a, 0x08, 0x08), "C": (0x2e, 0x26, 0x24), "o": (0xff, 0x6a, 0x20), "O": (0xff, 0xb0, 0x40),
        "b": (0xd2, 0xc4, 0xa2), "B": (0x8a, 0x7c, 0x64)})


def ward_lantern():
    """The block texture, laid out for the model in content.py:
    (0,0) 8x8 iron top/bottom, (8,0) 8x1 iron sides, (8,2) 4x4 knob top,
    (8,6) 4x2 knob sides, (12,2) 4x1 handle, (0,8) 6x7 glass sides,
    (6,8) 6x6 glass top, (12,8) 2x5 crystal sides, (14,8) 2x2 crystal top."""
    iron = Ramp(colors=["#1a1a1e", "#2c2c32", "#44444c", "#5e5e68", "#7a7a86"])
    amethyst = Ramp(colors=["#3a1e5c", "#5e3490", "#8a56c6", "#b48cff", "#dcc8ff"])
    cv = Canvas(16, 16)
    for y in range(8):
        for x in range(8):
            edge = x in (0, 7) or y in (0, 7)
            cv.set(x, y, iron(0.25 if edge else 0.55 + 0.1 * ((x + y) % 2), x, y))
    for x in range(8, 16):
        cv.set(x, 0, iron(0.5, x, 0))
    for y in range(2, 6):
        for x in range(8, 12):
            cv.set(x, y, iron(0.65, x, y))
    for y in range(6, 8):
        for x in range(8, 12):
            cv.set(x, y, iron(0.4, x, y))
    for x in range(12, 16):
        cv.set(x, 2, iron(0.35, x, 2))
    # Glass: an iron frame round clear panes with a scratch or two.
    for y in range(8, 15):
        for x in range(0, 6):
            frame = x in (0, 5) or y in (8, 14)
            cv.set(x, y, iron(0.3, x, y) if frame else ((0xe0, 0xe8, 0xff, 255) if (x + y) % 5 == 0 and y < 11 else CLEAR))
    for y in range(8, 14):
        for x in range(6, 12):
            frame = x in (6, 11) or y in (8, 13)
            cv.set(x, y, iron(0.3, x, y) if frame else CLEAR)
    for y in range(8, 13):
        for x in range(12, 14):
            cv.set(x, y, amethyst(0.9 - (y - 8) * 0.12 + (0.1 if x == 12 else 0), x, y))
    for y in range(8, 10):
        for x in range(14, 16):
            cv.set(x, y, amethyst(0.95, x, y))
    return cv


def corrupted(seed):
    """The missing-texture checkerboard, four frames tall: the block's tears
    crawl and it flickers inverted (see flipbook_textures.json)."""
    rng = random.Random(seed)
    frames = 4
    cv = Canvas(16, 16 * frames)
    for f in range(frames):
        inverted = f == 2
        for y in range(16):
            shift = rng.choice([0, 0, 0, 2, 3, -2, -3]) if rng.random() < 0.3 else 0
            for x in range(16):
                sx = (x + shift) % 16
                magenta = ((sx // 8) + (y // 8)) % 2 == 0
                c = (0xF8, 0x00, 0xF8) if magenta else (0x00, 0x00, 0x00)
                if inverted:
                    c = (0x00, 0xE0, 0xE0) if magenta else (0x10, 0x00, 0x10)
                if rng.random() < 0.03:
                    c = (0xFF, 0xFF, 0xFF)
                cv.set(x, f * 16 + y, c)
    return cv


def pack_icon(render_view, seed):
    """The Red Night: a blood-red fog under a red moon, the Hollow Miner
    towering in the middle, the Man From The Fog and Herobrine among dead
    trees, the Cave Dweller crawling out of the dark, null glitching."""
    rng = random.Random(seed)
    cv = Canvas(128, 128)
    for y in range(128):
        for x in range(128):
            fog = 14 + 70 * (y / 127) ** 1.4 + rng.randint(-4, 4)
            cv.set(x, y, (int(fog * 1.1 + 6), int(fog * 0.35), int(fog * 0.35)))
    for y in range(2, 34):
        for x in range(80, 116):
            r = ((x - 98) ** 2 + (y - 16) ** 2) ** 0.5
            if r < 12:
                cv.set(x, y, jitter(rng, (0xC8, 0x22, 0x1C) if rng.random() > 0.15 else (0x9A, 0x14, 0x12), 6))
            elif r < 18:
                cv.blend(x, y, (0x8A, 0x10, 0x10), (18 - r) / 14)
    for tx, w in ((4, 4), (22, 3), (104, 5), (120, 3)):
        for y in range(0, 110):
            for x in range(tx, tx + w):
                cv.blend(x, y, (0x0C, 0x0A, 0x0A), 0.85)
    cv.paste(render_view("herobrine", (18, 28), 15, -4), 20, 60)
    cv.paste(render_view("null", (24, 38), -30, -4), 100, 52)
    cv.paste(render_view("fog_man", (30, 58), -20, -6, {"body": (4, 0, 0)}), 88, 36)
    cv.paste(render_view("herobrine_boss", (70, 116), 18, -8), 29, 4)
    for y in range(66, 128):
        t = min(1.0, (y - 66) / 60) * 0.65
        for x in range(128):
            cv.blend(x, y, (0x6A, 0x22, 0x22), t)
    for y in range(96, 128):
        for x in range(0, 64):
            if (x / 64) ** 2 + ((128 - y) / 32) ** 2 < 1.0:
                cv.blend(x, y, (0x06, 0x04, 0x04), 0.85)
    cv.paste(render_view("cave_dweller", (56, 30), 35, -12), 2, 96)
    for y in range(128):
        for x in range(128):
            if rng.random() < 0.04:
                cv.set(x, y, jitter(rng, cv.get(x, y), 16))
    return cv
