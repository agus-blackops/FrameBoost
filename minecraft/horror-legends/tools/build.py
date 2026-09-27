#!/usr/bin/env python3
"""Build the "Horror Legends" add-on.

    python3 tools/build.py            # paint textures, validate JSON, pack
    python3 tools/build.py --textures # only repaint the PNGs

Every texture is painted procedurally here, so the add-on ships no third-party
art. Only the standard library is used: a minimal PNG encoder is included.
Produces dist/HorrorLegends.mcaddon and dist/HorrorLegends_WhatsApp.zip (the
same .mcaddon wrapped in a .zip, which messaging apps accept as an attachment).
"""

import json
import math
import random
import struct
import sys
import zlib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "behavior_pack"
RP = ROOT / "resource_pack"
DIST = ROOT / "dist" / "HorrorLegends.mcaddon"
DIST_ZIP = ROOT / "dist" / "HorrorLegends_WhatsApp.zip"


# --------------------------------------------------------------------------- #
# PNG canvas
# --------------------------------------------------------------------------- #

class Canvas:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.px = [fill] * (w * h)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y * self.w + x] = c if len(c) == 4 else (*c, 255)

    def get(self, x, y):
        return self.px[y * self.w + x]

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, c)

    def save(self, path):
        raw = bytearray()
        for y in range(self.h):
            raw.append(0)
            for x in range(self.w):
                raw.extend(self.px[y * self.w + x])

        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(png)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def jitter(rng, c, amount):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, v + d)) for v in c[:3])


def line(cv, x0, y0, x1, y1, c):
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        cv.set(round(x0 + (x1 - x0) * i / steps), round(y0 + (y1 - y0) * i / steps), c)


# --------------------------------------------------------------------------- #
# Humanoid skins (standard 64x64 box-UV layout)
# --------------------------------------------------------------------------- #

def box_faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def paint_box(cv, u, v, w, h, d, fn):
    for face, (x0, y0, fw, fh) in box_faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                cv.set(x0 + x, y0 + y, fn(face, x, y))


HEAD = (0, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
ARMS = [(40, 16, 4, 12, 4), (32, 48, 4, 12, 4)]
LEGS = [(0, 16, 4, 12, 4), (16, 48, 4, 12, 4)]


def paint_face(cv, rng, rows, palette, u=8, v=8):
    """Paint an 8x8 face (the head's front) from character rows."""
    for y, row in enumerate(rows):
        assert len(row) == 8, row
        for x, ch in enumerate(row):
            c = palette[ch]
            if len(c) == 4:            # alpha < 255 glows under entity_emissive_alpha
                cv.set(u + x, v + y, c)
            else:
                cv.set(u + x, v + y, jitter(rng, c, 4))


def paint_humanoid(seed, head, body, arm, leg):
    """Each callback is fn(rng, face, x, y) -> colour for that box."""
    rng = random.Random(seed)
    cv = Canvas(64, 64)
    paint_box(cv, *HEAD, lambda f, x, y: head(rng, f, x, y))
    paint_box(cv, *BODY, lambda f, x, y: body(rng, f, x, y))
    for box in ARMS:
        paint_box(cv, *box, lambda f, x, y: arm(rng, f, x, y))
    for box in LEGS:
        paint_box(cv, *box, lambda f, x, y: leg(rng, f, x, y))
    return cv, rng


# --------------------------------------------------------------------------- #
# Herobrine: an ordinary miner, except for the eyes
# --------------------------------------------------------------------------- #

GLOW_WHITE = (0xFF, 0xFF, 0xFF, 40)


def paint_herobrine(seed):
    skin, hair = (0xB8, 0x8A, 0x6E), (0x2E, 0x20, 0x12)
    shirt, pants, shoes = (0x00, 0xA0, 0xA4), (0x40, 0x38, 0x9E), (0x5E, 0x5E, 0x62)

    def head(rng, f, x, y):
        if f == "top" or (f != "bottom" and y < 2):
            return jitter(rng, hair, 5)
        if f in ("left", "right") and y < 3:
            return jitter(rng, hair, 5)
        if f == "back":
            return jitter(rng, hair if y < 7 else skin, 5)
        return jitter(rng, skin, 5)

    def body(rng, f, x, y):
        return jitter(rng, shirt if y < 11 else pants, 5)

    def arm(rng, f, x, y):
        return jitter(rng, shirt if f == "top" or y < 4 else skin, 5)

    def leg(rng, f, x, y):
        return jitter(rng, shoes if f == "bottom" or y >= 10 else pants, 5)

    cv, rng = paint_humanoid(seed, head, body, arm, leg)
    paint_face(cv, rng, [
        "HHHHHHHH",
        "HHHHHHHH",
        "HSSSSSSH",
        "SSSSSSSS",
        "SWWSSWWS",
        "SSSnnSSS",
        "SSmmmmSS",
        "SSmSSmSS",
    ], {"H": hair, "S": skin, "W": GLOW_WHITE, "n": shade(skin, 0.85), "m": (0x6A, 0x44, 0x30)})
    return cv


# --------------------------------------------------------------------------- #
# Null: a hole in the world shaped like a player
# --------------------------------------------------------------------------- #

def paint_null(seed):
    def void(rng, f, x, y):
        r = rng.random()
        if r < 0.025:
            return (0xFF, 0x00, 0xFF)
        if r < 0.04:
            return (0x00, 0xE0, 0xE0)
        g = rng.randint(6, 22)
        return (g, g, g + 2)

    cv, rng = paint_humanoid(seed, void, void, void, void)
    k = (0x05, 0x05, 0x06)
    paint_face(cv, rng, [
        "KKKKKKKK",
        "KKKKKKKK",
        "KKKKKKKK",
        "KKKKKKKK",
        "KWKKKKWK",
        "KKKKKKKK",
        "KKKKKKKK",
        "KKKKKKKK",
    ], {"K": k, "W": GLOW_WHITE})
    return cv


# --------------------------------------------------------------------------- #
# The Man From The Fog: tall, pale, grinning
# --------------------------------------------------------------------------- #

def paint_fog_man(seed):
    skin, dark = (0xC4, 0xC2, 0xBA), (0x2E, 0x2E, 0x32)

    def pale(rng, c):
        c = jitter(rng, c, 6)
        return shade(c, 0.82) if rng.random() < 0.08 else c

    def head(rng, f, x, y):
        return pale(rng, skin)

    def body(rng, f, x, y):
        if y >= 10:
            return jitter(rng, dark, 5)
        if f == "front" and y in (3, 5, 7) and x not in (3, 4):
            return shade(skin, 0.78)            # ribs
        return pale(rng, skin)

    def arm(rng, f, x, y):
        if f == "bottom" or y >= 11:
            return jitter(rng, shade(skin, 0.7), 4)  # grey fingertips
        return pale(rng, skin)

    def leg(rng, f, x, y):
        if f == "bottom" or y >= 10:
            return pale(rng, skin)              # bare feet
        if y == 9 and rng.random() < 0.5:
            return pale(rng, skin)              # ragged hem
        return jitter(rng, dark, 5)

    cv, rng = paint_humanoid(seed, head, body, arm, leg)
    paint_face(cv, rng, [
        "SSSSSSSS",
        "SSSSSSSS",
        "SKKSSKKS",
        "SKKSSKKS",
        "SsKSSKsS",
        "SSSSSSSS",
        "KKKKKKKK",
        "SKWKKWKS",
    ], {"S": skin, "s": shade(skin, 0.8), "K": (0x08, 0x08, 0x0A), "W": (0xD8, 0xD4, 0xC4)})
    return cv


# --------------------------------------------------------------------------- #
# The Cave Dweller: custom crawling model (see hl.geo.json)
# --------------------------------------------------------------------------- #

def paint_cave_dweller(seed):
    rng = random.Random(seed)
    cv = Canvas(64, 64)
    skin = (0xC6, 0xC2, 0xB8)

    def mottled(c):
        c = jitter(rng, c, 8)
        r = rng.random()
        if r < 0.10:
            return shade(c, 0.7)
        if r < 0.13:
            return (0x5A, 0x54, 0x4C)
        return c

    # Head: 7x7x7 at uv (0, 0).
    paint_box(cv, 0, 0, 7, 7, 7, lambda f, x, y: mottled(skin))
    face = [
        ".......",
        ".KK.KK.",
        ".KK.KK.",
        "..s.s..",
        ".......",
        "KWKWKWK",
        ".KKKKK.",
    ]
    for y, row in enumerate(face):
        for x, ch in enumerate(row):
            if ch == "K":
                cv.set(7 + x, 7 + y, (0x06, 0x06, 0x08))
            elif ch == "W":
                cv.set(7 + x, 7 + y, (0xE0, 0xDC, 0xCC))
            elif ch == "s":
                cv.set(7 + x, 7 + y, shade(skin, 0.6))

    # Body: 6x4x14 at uv (0, 20). Spine ridge on top, ribs along the sides.
    def body(f, x, y):
        if f == "top" and x in (2, 3):
            return jitter(rng, (0x7A, 0x74, 0x6A), 6)
        if f in ("left", "right") and x % 3 == 0 and y < 3:
            return shade(skin, 0.72)
        return mottled(skin)

    paint_box(cv, 0, 20, 6, 4, 14, body)

    # Limbs: 2x11x2 at uv (48, 0). Dark claws at the ends.
    paint_box(cv, 48, 0, 2, 11, 2, lambda f, x, y: (0x2A, 0x26, 0x22) if (f == "bottom" or y >= 9) else mottled(skin))
    return cv


# --------------------------------------------------------------------------- #
# Blocks, items, pack icon
# --------------------------------------------------------------------------- #

def paint_corrupted(seed):
    """The missing-texture checkerboard, with scanline tears."""
    rng = random.Random(seed)
    cv = Canvas(16, 16)
    for y in range(16):
        shift = rng.choice([0, 0, 0, 0, 2, 3, -2]) if y % 4 == 1 else 0
        for x in range(16):
            sx = (x + shift) % 16
            magenta = ((sx // 8) + (y // 8)) % 2 == 0
            c = (0xF8, 0x00, 0xF8) if magenta else (0x00, 0x00, 0x00)
            if rng.random() < 0.03:
                c = (0x00, 0xE0, 0xE0)
            cv.set(x, y, c)
    return cv


def paint_journal(seed):
    rng = random.Random(seed)
    cv = Canvas(16, 16)
    cover = (0x4A, 0x2E, 0x1C)
    for y in range(1, 15):
        for x in range(2, 14):
            cv.set(x, y, jitter(rng, cover, 6))
    cv.rect(2, 1, 2, 14, (0x2E, 0x1C, 0x10))      # spine
    cv.rect(13, 2, 1, 12, (0xE0, 0xD8, 0xC0))     # page edges
    eye = [
        "..####..",
        ".#....#.",
        "#..##..#",
        ".#....#.",
        "..####..",
    ]
    for y, row in enumerate(eye):
        for x, ch in enumerate(row):
            if ch == "#":
                cv.set(5 + x, 5 + y, (0xD8, 0xD0, 0xB8))
    cv.set(8, 7, (0xC0, 0x10, 0x10))
    cv.set(9, 7, (0xC0, 0x10, 0x10))
    return cv


def paint_flashlight():
    cv = Canvas(16, 16)
    body, dark = (0x7A, 0x7C, 0x84), (0x3A, 0x3C, 0x42)
    for i in range(8):                              # diagonal barrel
        cv.rect(2 + i, 12 - i, 2, 2, body)
        cv.set(2 + i, 13 - i, dark)
    cv.rect(9, 3, 4, 4, dark)                       # head
    cv.rect(10, 4, 2, 2, (0xFF, 0xE8, 0x70))        # lens
    for (x, y) in [(13, 2), (14, 1), (12, 1), (14, 3)]:
        cv.set(x, y, (0xFF, 0xF4, 0xB0))            # beam
    cv.set(5, 10, (0xC0, 0x20, 0x20))               # switch
    return cv


def paint_pack_icon(seed):
    rng = random.Random(seed)
    cv = Canvas(128, 128)
    for y in range(128):
        for x in range(128):
            fog = int(18 + 70 * (y / 127) ** 1.6 + rng.randint(-5, 5))
            cv.set(x, y, (fog, fog + 2, fog + 4))
    # The tall pale figure in the fog.
    fig = (0xB4, 0xB2, 0xAA)
    cv.rect(58, 34, 12, 12, fig)                    # head
    cv.rect(60, 38, 3, 3, (0x06, 0x06, 0x08))
    cv.rect(65, 38, 3, 3, (0x06, 0x06, 0x08))
    cv.rect(60, 43, 8, 1, (0x06, 0x06, 0x08))
    cv.rect(57, 46, 14, 30, fig)                    # torso
    cv.rect(52, 46, 4, 40, fig)                     # long arms
    cv.rect(72, 46, 4, 40, fig)
    cv.rect(58, 76, 5, 36, (0x2E, 0x2E, 0x32))      # legs
    cv.rect(65, 76, 5, 36, (0x2E, 0x2E, 0x32))
    # Fog over the lower half of the figure.
    for y in range(70, 128):
        for x in range(128):
            c = cv.get(x, y)
            t = min(1.0, (y - 70) / 50)
            f = (0x5A, 0x5E, 0x62)
            cv.set(x, y, tuple(int(c[i] * (1 - t * 0.7) + f[i] * t * 0.7) for i in range(3)))
    # White eyes in the dark, off to the left.
    cv.rect(14, 40, 3, 2, (0xFF, 0xFF, 0xFF))
    cv.rect(21, 40, 3, 2, (0xFF, 0xFF, 0xFF))
    # A tear of missing texture in the corner.
    for y in range(0, 24):
        for x in range(100 + (y % 5), 128):
            magenta = ((x // 6) + (y // 6)) % 2 == 0
            cv.set(x, y, (0xF8, 0x00, 0xF8) if magenta else (0, 0, 0))
    # Grain.
    for y in range(128):
        for x in range(128):
            if rng.random() < 0.05:
                cv.set(x, y, jitter(rng, cv.get(x, y), 25))
    return cv


def paint_textures():
    ent = RP / "textures" / "entity" / "hl"
    paint_herobrine(10).save(ent / "herobrine.png")
    paint_null(20).save(ent / "null.png")
    paint_fog_man(30).save(ent / "fog_man.png")
    paint_cave_dweller(40).save(ent / "cave_dweller.png")
    paint_corrupted(50).save(RP / "textures" / "blocks" / "hl" / "corrupted_block.png")
    items = RP / "textures" / "items" / "hl"
    paint_journal(60).save(items / "journal.png")
    paint_flashlight().save(items / "flashlight.png")
    icon = paint_pack_icon(70)
    icon.save(BP / "pack_icon.png")
    icon.save(RP / "pack_icon.png")


# --------------------------------------------------------------------------- #
# Validation and packaging
# --------------------------------------------------------------------------- #

def validate():
    errors = 0
    for path in sorted(list(BP.rglob("*.json")) + list(RP.rglob("*.json"))):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            print(f"invalid JSON: {path.relative_to(ROOT)}: {e}")
            errors += 1
    for lang in sorted(list(BP.rglob("*.lang")) + list(RP.rglob("*.lang"))):
        for n, text in enumerate(lang.read_text(encoding="utf-8").splitlines(), 1):
            if text.strip() and not text.startswith("##") and "=" not in text:
                print(f"bad lang line: {lang.relative_to(ROOT)}:{n}")
                errors += 1
    if errors:
        sys.exit(1)


def package():
    DIST.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(DIST, "w", zipfile.ZIP_DEFLATED) as z:
        for src, name in ((BP, "HorrorLegends_BP"), (RP, "HorrorLegends_RP")):
            for path in sorted(src.rglob("*")):
                if path.is_file():
                    z.write(path, f"{name}/{path.relative_to(src).as_posix()}")
    with zipfile.ZipFile(DIST_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(DIST, DIST.name)
    for f in (DIST, DIST_ZIP):
        print(f"wrote {f.relative_to(ROOT)} ({f.stat().st_size} bytes)")


if __name__ == "__main__":
    paint_textures()
    if "--textures" not in sys.argv:
        validate()
        package()
