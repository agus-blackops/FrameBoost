#!/usr/bin/env python3
"""Build the "There Are Monsters" add-on.

    python3 tools/build.py            # paint textures, validate JSON, pack .mcaddon
    python3 tools/build.py --textures # only repaint the PNGs

Every texture is painted procedurally here, so the add-on ships no third-party
art. Only the standard library is used: a minimal PNG encoder is included.
"""

import json
import random
import struct
import sys
import zlib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "behavior_pack"
RP = ROOT / "resource_pack"
DIST = ROOT / "dist" / "ThereAreMonsters.mcaddon"


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


# --------------------------------------------------------------------------- #
# Humanoid skin (standard 64x64 box-UV layout)
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


SKIN = (0xC9, 0x9A, 0x7A)
SKIN_PALE = (0xB8, 0xB0, 0xA2)
SKIN_DOUBLE = (0xDA, 0xD6, 0xCE)
BLACK = (0x0B, 0x08, 0x08)
SMEAR = (0x3A, 0x22, 0x24)

FACE_HUMAN = [
    "HHHHHHHH",
    "HHHHHHHH",
    "HSSSSSSH",
    "SBBSSBBS",
    "SWPSSPWS",
    "SSSnnSSS",
    "SSMMMMSS",
    "SSSSSSSS",
]

# Eyes run down into the cheeks and the mouth hangs open: the head bone is
# also stretched vertically by the animation, which drags these streaks out.
FACE_REVEALED = [
    "HHHHHHHH",
    "HHHHHHHH",
    "HKKSSKKH",
    "SKKSSKKS",
    "SKKSSKKS",
    "SxKSSKxS",
    "SxSKKSxS",
    "SSSKKSSS",
]

FACE_DOUBLE = [
    "HHHHHHHH",
    "HSSSSSSH",
    "SKKSSKKS",
    "SKKSSKKS",
    "SSSSSSSS",
    "KKKKKKKK",
    "SKKKKKKS",
    "SSSKKSSS",
]


def plaid(x, y):
    a, b = x % 4 == 1, y % 4 == 1
    if a and b:
        return (0x1A, 0x0A, 0x0A)
    if a or b:
        return (0x45, 0x12, 0x12)
    return (0xA8, 0x22, 0x22)


def hoodie(x, y, face):
    base = (0x3F, 0x6B, 0x3A)
    if face == "front" and 7 <= y <= 9 and 1 <= x <= 6:
        return shade(base, 0.8)
    if face == "front" and y <= 3 and x in (3, 4):
        return (0xE0, 0xE0, 0xD8)
    return base


def raincoat(x, y, face):
    base = (0xD8, 0xB0, 0x2A)
    if face == "front" and x in (3, 4):
        return shade(base, 0.7) if y % 3 else (0x2A, 0x24, 0x1A)
    return base


OUTFITS = [
    # Plaid flannel, jeans, brown hair.
    dict(hair=(0x4A, 0x31, 0x20), shirt=lambda f, x, y: plaid(x, y),
         pants=(0x2F, 0x46, 0x7A), shoes=(0x3B, 0x2A, 0x1E)),
    # Green hoodie, dark trousers, fair hair.
    dict(hair=(0xB8, 0x94, 0x52), shirt=lambda f, x, y: hoodie(x, y, f),
         pants=(0x2A, 0x2A, 0x30), shoes=(0x22, 0x22, 0x22)),
    # Yellow fisherman's slicker, dark jeans, black hair.
    dict(hair=(0x1C, 0x18, 0x16), shirt=lambda f, x, y: raincoat(x, y, f),
         pants=(0x24, 0x33, 0x55), shoes=(0x2E, 0x22, 0x18)),
]


def paint_person(outfit, face_rows, skin, seed, grime=0.0):
    rng = random.Random(seed)
    cv = Canvas(64, 64)
    hair = outfit["hair"]

    def dirty(c):
        if grime and rng.random() < grime:
            return shade(c, rng.uniform(0.55, 0.8))
        return c

    def face_px(ch):
        return {
            "H": hair, "S": skin, "B": shade(hair, 0.8), "W": (0xEE, 0xEE, 0xE6),
            "P": (0x30, 0x4A, 0x6A), "n": shade(skin, 0.87), "M": (0x8A, 0x4A, 0x40),
            "K": BLACK, "x": SMEAR,
        }[ch]

    def head(face, x, y):
        if face == "front":
            c = face_px(face_rows[y][x])
        elif face == "top":
            c = hair
        elif face == "bottom":
            c = shade(skin, 0.85)
        elif face == "back":
            c = hair if y < 6 else skin
        else:
            c = hair if y < 3 or (face == "right" and x < 5 and y < 4) or (face == "left" and x > 2 and y < 4) else skin
        return jitter(rng, c, 6)

    paint_box(cv, 0, 0, 8, 8, 8, head)

    def body(face, x, y):
        if face == "bottom":
            return outfit["pants"]
        return dirty(jitter(rng, outfit["shirt"](face, x, y), 5))

    paint_box(cv, 16, 16, 8, 12, 4, body)

    def arm(face, x, y):
        if face == "bottom" or (face not in ("top",) and y >= 10):
            return jitter(rng, skin, 5)
        return dirty(jitter(rng, outfit["shirt"](face, x, y), 5))

    paint_box(cv, 40, 16, 4, 12, 4, arm)
    paint_box(cv, 32, 48, 4, 12, 4, arm)

    def leg(face, x, y):
        if face == "bottom" or (face != "top" and y >= 10):
            return jitter(rng, outfit["shoes"], 4)
        return dirty(jitter(rng, outfit["pants"], 6))

    paint_box(cv, 0, 16, 4, 12, 4, leg)
    paint_box(cv, 16, 48, 4, 12, 4, leg)
    return cv


def paint_double(seed):
    """The Doppelganger: a washed-out copy, black clothes, a grin too wide."""
    rng = random.Random(seed)
    dark = (0x1E, 0x1D, 0x22)
    outfit = dict(hair=(0x14, 0x13, 0x16), shirt=lambda f, x, y: dark,
                  pants=(0x16, 0x15, 0x19), shoes=(0x0C, 0x0C, 0x0E))
    cv = paint_person(outfit, FACE_DOUBLE, SKIN_DOUBLE, seed)
    # VHS static across the clothes.
    for (u, v, w, h, d) in [(16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (32, 48, 4, 12, 4),
                            (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)]:
        for (x0, y0, fw, fh) in box_faces(u, v, w, h, d).values():
            for y in range(fh):
                for x in range(fw):
                    if rng.random() < 0.08:
                        g = rng.randint(90, 170)
                        cv.set(x0 + x, y0 + y, (g, g, g))
    return cv


# --------------------------------------------------------------------------- #
# Item icons and pack icon
# --------------------------------------------------------------------------- #

def paint_camcorder():
    cv = Canvas(16, 16)
    body, dark, lens = (0x3A, 0x3A, 0x40), (0x1E, 0x1E, 0x22), (0x6A, 0x8C, 0xB0)
    cv.rect(4, 3, 4, 2, dark)            # viewfinder
    cv.rect(1, 5, 11, 7, body)           # body
    cv.rect(1, 5, 11, 1, (0x55, 0x55, 0x5C))
    cv.rect(1, 11, 11, 1, dark)
    cv.rect(12, 6, 3, 5, dark)           # lens barrel
    cv.rect(14, 7, 1, 3, lens)
    cv.set(15, 8, shade(lens, 1.25))
    cv.rect(2, 6, 2, 2, (0xE0, 0x20, 0x20))  # REC light
    cv.set(2, 6, (0xFF, 0x70, 0x70))
    cv.rect(5, 8, 5, 2, (0x28, 0x28, 0x2E))  # side panel
    cv.rect(3, 12, 6, 1, (0x55, 0x3A, 0x26))  # strap
    return cv


def paint_tape():
    cv = Canvas(16, 16)
    shell, label = (0x16, 0x16, 0x18), (0xE8, 0xE2, 0xD0)
    cv.rect(1, 4, 14, 9, shell)
    cv.rect(1, 4, 14, 1, (0x30, 0x30, 0x34))
    cv.rect(3, 5, 10, 3, label)
    cv.rect(4, 6, 6, 1, (0xB0, 0x20, 0x20))   # scrawled title
    cv.rect(4, 9, 2, 2, (0x50, 0x50, 0x56))   # reels
    cv.rect(10, 9, 2, 2, (0x50, 0x50, 0x56))
    cv.rect(6, 11, 4, 1, (0x3A, 0x2A, 0x20))  # exposed tape
    return cv


ICON_FACE = [
    "................",
    "....HHHHHHHH....",
    "...HHHHHHHHHH...",
    "...HSSSSSSSSH...",
    "...SKKSSSSKKS...",
    "...SKKSSSSKKS...",
    "...SKKSSSSKKS...",
    "...SxKSSSSKxS...",
    "...SSxSSSSxSS...",
    "...SSSSKKSSSS...",
    "...SSSKKKKSSS...",
    "...SSSKKKKSSS...",
    "...SSSKKKKSSS...",
    "...SSSSKKSSSS...",
    "....SSSKKSSS....",
    ".....SSSSSS.....",
]


def paint_pack_icon(seed):
    rng = random.Random(seed)
    scale = 8
    cv = Canvas(128, 128, (0x0C, 0x0C, 0x0E, 255))
    colors = {"H": (0x22, 0x1C, 0x1A), "S": SKIN_PALE, "K": BLACK, "x": SMEAR}
    for gy, row in enumerate(ICON_FACE):
        assert len(row) == 16, row
        for gx, ch in enumerate(row):
            if ch == ".":
                continue
            for y in range(scale):
                for x in range(scale):
                    cv.set(gx * scale + x, gy * scale + y, jitter(rng, colors[ch], 8))
    # Tracking error: shear a few bands sideways like a worn tape.
    for band_y in rng.sample(range(8, 120), 5):
        shift, height = rng.randint(3, 9), rng.randint(2, 5)
        for y in range(band_y, min(128, band_y + height)):
            row = [cv.get(x, y) for x in range(128)]
            for x in range(128):
                cv.set(x, y, row[(x - shift) % 128])
    # Scanlines and grain.
    for y in range(128):
        for x in range(128):
            c = cv.get(x, y)
            f = 0.72 if y % 2 else 1.0
            c = shade(c, f)
            if rng.random() < 0.05:
                c = jitter(rng, c, 40)
            cv.set(x, y, c)
    # The red REC dot.
    for y in range(-5, 6):
        for x in range(-5, 6):
            if x * x + y * y <= 20:
                cv.set(12 + x, 12 + y, (0xE0, 0x18, 0x18))
    return cv


def paint_textures():
    ent = RP / "textures" / "entity" / "tam"
    for i, outfit in enumerate(OUTFITS):
        paint_person(outfit, FACE_HUMAN, SKIN, seed=100 + i).save(ent / f"imposter_disguised_{i}.png")
        paint_person(outfit, FACE_REVEALED, SKIN_PALE, seed=200 + i, grime=0.18).save(ent / f"imposter_revealed_{i}.png")
    paint_double(300).save(ent / "doppelganger.png")
    items = RP / "textures" / "items" / "tam"
    paint_camcorder().save(items / "camcorder.png")
    paint_tape().save(items / "vhs_tape.png")
    icon = paint_pack_icon(400)
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
        for n, line in enumerate(lang.read_text(encoding="utf-8").splitlines(), 1):
            if line.strip() and not line.startswith("##") and "=" not in line:
                print(f"bad lang line: {lang.relative_to(ROOT)}:{n}")
                errors += 1
    if errors:
        sys.exit(1)


def package():
    DIST.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(DIST, "w", zipfile.ZIP_DEFLATED) as z:
        for src, name in ((BP, "ThereAreMonsters_BP"), (RP, "ThereAreMonsters_RP")):
            for path in sorted(src.rglob("*")):
                if path.is_file():
                    z.write(path, f"{name}/{path.relative_to(src).as_posix()}")
    print(f"wrote {DIST.relative_to(ROOT)} ({DIST.stat().st_size} bytes)")


if __name__ == "__main__":
    paint_textures()
    if "--textures" not in sys.argv:
        validate()
        package()
