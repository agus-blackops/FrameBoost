#!/usr/bin/env python3
"""Build "The Blair Witch" add-on.

    python3 tools/build.py            # paint textures, validate JSON, pack
    python3 tools/build.py --textures # only repaint the PNGs

Every texture is painted procedurally here, so the add-on ships no third-party
art. Only the standard library is used: a minimal PNG encoder is included.
Produces dist/BlairWitch.mcaddon and dist/BlairWitch_WhatsApp.zip (the same
.mcaddon wrapped in a .zip, which messaging apps accept as an attachment).
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
DIST = ROOT / "dist" / "BlairWitch.mcaddon"
DIST_ZIP = ROOT / "dist" / "BlairWitch_WhatsApp.zip"


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


def paint_witch(seed):
    """Never seen clearly: a tall, hooded smear of darkness, half transparent,
    ragged at the hems."""
    rng = random.Random(seed)
    cv = Canvas(64, 64)

    def cloth(face, x, y, h=12):
        # Hems fray away into nothing.
        if face not in ("top",) and y >= h - 3 and rng.random() < (y - (h - 4)) * 0.22:
            return (0, 0, 0, 0)
        g = rng.randint(14, 30)
        return (g, g - 2, g + 2, rng.randint(120, 185))

    def head(face, x, y):
        if face == "front" and 2 <= y <= 6 and 1 <= x <= 6:
            return (0, 0, 0, 235)  # the hood's opening: no face at all
        return cloth(face, x, y, h=99)

    paint_box(cv, *HEAD, head)
    paint_box(cv, *BODY, lambda f, x, y: cloth(f, x, y))
    for box in ARMS + LEGS:
        paint_box(cv, *box, lambda f, x, y: cloth(f, x, y))
    return cv


def paint_crew(seed, cap, hair, top, pants, boots=(0x2A, 0x20, 0x18)):
    """One of the film crew: knit cap, field clothes. `top(face, x, y)` paints
    the shirt or jacket."""
    rng = random.Random(seed)
    cv = Canvas(64, 64)
    skin = (0xC4, 0x96, 0x78)

    def head(face, x, y):
        if face == "top" or y < 3:
            c = cap if y != 2 or face == "top" else shade(cap, 0.75)  # folded brim
        elif face == "back":
            c = hair if y < 6 else skin
        elif face == "front":
            c = {4: (0x25, 0x20, 0x1C) if x in (2, 5) else skin,
                 3: hair if x in (0, 7) else skin,
                 6: shade(skin, 0.8) if 3 <= x <= 4 else skin}.get(y, skin)
        else:
            c = hair if y < 4 else skin
        return jitter(rng, c, 5)

    paint_box(cv, *HEAD, head)
    paint_box(cv, *BODY, lambda f, x, y: jitter(rng, top(f, x, y), 6))
    for box in ARMS:
        paint_box(cv, *box, lambda f, x, y: jitter(rng, skin if (f == "bottom" or (f != "top" and y >= 10)) else top(f, x, y), 6))
    for box in LEGS:
        paint_box(cv, *box, lambda f, x, y: jitter(rng, boots if (f == "bottom" or (f != "top" and y >= 9)) else pants, 6))
    return cv


def paint_mike(seed):
    """Green knit cap, canvas work jacket. He ends up in the corner."""
    jacket = (0x5A, 0x4A, 0x36)
    return paint_crew(seed, cap=(0x2E, 0x3A, 0x2C), hair=(0x3A, 0x2A, 0x1E),
                      top=lambda f, x, y: shade(jacket, 0.85) if f == "front" and x in (3, 4) else jacket,
                      pants=(0x33, 0x40, 0x5E))


def paint_josh(seed):
    """Red flannel shirt: the same cloth that turns up in the bundle of twigs."""
    def flannel(f, x, y):
        a, b = x % 4 == 1, y % 4 == 1
        if f == "front" and x in (3, 4):
            return (0x2A, 0x2A, 0x2E)  # dark tee under the open shirt
        if a and b:
            return (0x1A, 0x0A, 0x0A)
        return (0x4A, 0x14, 0x14) if a or b else (0x9C, 0x22, 0x22)
    return paint_crew(seed, cap=(0x5A, 0x24, 0x1C), hair=(0x2A, 0x1E, 0x16),
                      top=flannel, pants=(0x8A, 0x7A, 0x5A))


def paint_handprint_wall(seed):
    """Old plaster, grimy, covered in small handprints."""
    rng = random.Random(seed)
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c = jitter(rng, (0xA8, 0xA0, 0x8C), 10)
            if rng.random() < 0.06:
                c = shade(c, 0.75)
            cv.set(x, y, c)
    line(cv, 11, 0, 13, 5, (0x6A, 0x64, 0x56))      # crack
    line(cv, 13, 5, 12, 9, (0x6A, 0x64, 0x56))
    hand = [
        ".#.#.#",
        ".#.#.#",
        ".#####",
        "######",
        ".#####",
        "..###.",
    ]
    for (hx, hy) in [(1, 1), (9, 7), (2, 10)]:
        ink = jitter(rng, (0x3A, 0x30, 0x28), 8)
        for dy, row in enumerate(hand):
            for dx, ch in enumerate(row):
                if ch == "#":
                    cv.set(hx + dx, hy + dy, ink)
    return cv


def paint_wood(seed):
    """Stick figure texture: bark on rows 0-19, twine on rows 20-31."""
    rng = random.Random(seed)
    cv = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            if y < 20:
                base = (0x6B, 0x4E, 0x33) if (x + y // 3) % 5 else (0x4A, 0x35, 0x22)
                cv.set(x, y, jitter(rng, base, 10))
            else:
                base = (0xC2, 0xAE, 0x82) if (x + y) % 3 else (0x9C, 0x88, 0x5E)
                cv.set(x, y, jitter(rng, base, 8))
    return cv


def paint_stone(seed):
    rng = random.Random(seed)
    cv = Canvas(32, 32)
    for y in range(32):
        for x in range(32):
            c = jitter(rng, (0x80, 0x7E, 0x78), 14)
            r = rng.random()
            if r < 0.07:
                c = (0x4E, 0x4C, 0x48)
            elif r < 0.11:
                c = jitter(rng, (0x4E, 0x62, 0x34), 8)  # moss
            cv.set(x, y, c)
    return cv


# --------------------------------------------------------------------------- #
# Item icons and pack icon
# --------------------------------------------------------------------------- #

STICK = (0x6B, 0x4E, 0x33)
TWINE = (0xC2, 0xAE, 0x82)


def draw_effigy(cv, cx, top, s, c=STICK, tw=TWINE):
    """A stick figure: head loop, spine, cross arms, splayed legs."""
    t = max(1, s // 2)
    for i in range(t):
        line(cv, cx - 2 * s + i, top, cx - 2 * s + i, top + 3 * s, c)            # head left
        line(cv, cx + 2 * s - 1 - i, top, cx + 2 * s - 1 - i, top + 3 * s, c)    # head right
        line(cv, cx - 2 * s, top + i, cx + 2 * s - 1, top + i, c)                # head top
        line(cv, cx - t // 2 + i, top + 3 * s, cx - t // 2 + i, top + 8 * s, c)  # spine
        line(cv, cx - 5 * s, top + 4 * s + i, cx + 5 * s, top + 4 * s + i, c)    # arms
        line(cv, cx + i, top + 8 * s, cx - 3 * s + i, top + 12 * s, c)           # legs
        line(cv, cx + i, top + 8 * s, cx + 3 * s + i, top + 12 * s, c)
    cv.rect(cx - t, top + 4 * s - t // 2, 2 * t, 2 * t, tw)                      # binding


def paint_camcorder():
    cv = Canvas(16, 16)
    body, dark, lens = (0x8C, 0x8E, 0x94), (0x2A, 0x2A, 0x2E), (0x6A, 0x8C, 0xB0)
    cv.rect(2, 3, 5, 2, dark)             # viewfinder
    cv.rect(1, 5, 11, 7, body)
    cv.rect(1, 5, 11, 1, (0xB4, 0xB6, 0xBC))
    cv.rect(1, 11, 11, 1, (0x5A, 0x5C, 0x62))
    cv.rect(12, 6, 3, 5, dark)
    cv.rect(14, 7, 1, 3, lens)
    cv.rect(2, 6, 2, 2, (0xE0, 0x20, 0x20))
    cv.rect(5, 7, 5, 1, dark)             # "Hi8" badge
    cv.rect(5, 9, 6, 2, (0x70, 0x72, 0x78))
    cv.rect(2, 12, 8, 1, (0x3A, 0x2A, 0x20))  # strap
    return cv


def paint_map(seed):
    rng = random.Random(seed)
    cv = Canvas(16, 16)
    for y in range(2, 14):
        for x in range(1, 15):
            cv.set(x, y, jitter(rng, (0xD8, 0xC8, 0x9C), 10))
    for x in range(1, 15):           # fold creases
        cv.set(x, 7, (0xB0, 0xA0, 0x78))
    for y in range(2, 14):
        cv.set(5, y, (0xB0, 0xA0, 0x78))
        cv.set(10, y, (0xB0, 0xA0, 0x78))
    for (x, y) in [(2, 12), (3, 11), (4, 11), (5, 10), (6, 9), (7, 9), (8, 8), (9, 6), (10, 5)]:
        cv.set(x, y, (0x3C, 0x6A, 0xA8))   # the creek
    for (x, y) in [(3, 5), (5, 5), (7, 6), (9, 8), (11, 9)]:
        cv.set(x, y, (0xB0, 0x20, 0x20))   # dotted trail
    line(cv, 11, 3, 13, 5, (0x20, 0x18, 0x14))  # X
    line(cv, 13, 3, 11, 5, (0x20, 0x18, 0x14))
    return cv


def paint_bundle():
    cv = Canvas(16, 16)
    for i, (x0, y0, x1, y1) in enumerate([(2, 13, 12, 2), (4, 14, 13, 4), (3, 3, 13, 13), (1, 9, 14, 7)]):
        line(cv, x0, y0, x1, y1, shade(STICK, 1.0 - 0.08 * i))
    cv.rect(6, 6, 4, 4, (0x7A, 0x22, 0x20))    # a scrap of flannel
    cv.set(7, 7, (0x3A, 0x10, 0x10))
    cv.set(8, 8, (0x3A, 0x10, 0x10))
    line(cv, 5, 7, 10, 9, TWINE)
    line(cv, 5, 9, 10, 6, TWINE)
    return cv


def paint_dossier(seed):
    rng = random.Random(seed)
    cv = Canvas(16, 16)
    cv.rect(1, 3, 14, 11, (0x8A, 0x6A, 0x3A))           # folder
    cv.rect(1, 2, 6, 1, (0x8A, 0x6A, 0x3A))             # tab
    cv.rect(3, 4, 11, 9, (0xE4, 0xDC, 0xC4))            # papers
    for y in (6, 8, 10):
        for x in range(4, 13):
            if rng.random() < 0.8:
                cv.set(x, y, (0x5A, 0x56, 0x50))
    cv.rect(10, 4, 3, 3, (0x30, 0x2C, 0x28))            # photo
    draw_stamp = (0xB0, 0x20, 0x20)
    line(cv, 4, 12, 8, 12, draw_stamp)
    cv.rect(1, 13, 14, 1, (0x6A, 0x50, 0x2A))
    return cv


def paint_effigy_icon():
    cv = Canvas(16, 16)
    draw_effigy(cv, 8, 1, 1)
    cv.set(8, 0, TWINE)
    return cv


def paint_pack_icon(seed):
    rng = random.Random(seed)
    cv = Canvas(128, 128, (0x0B, 0x0D, 0x0A, 255))
    # Bare trunks receding into the dark.
    for _ in range(18):
        x, w = rng.randint(0, 124), rng.randint(3, 9)
        g = rng.randint(22, 44)
        for y in range(128):
            for xx in range(x, x + w):
                cv.set(xx, y, jitter(rng, (g, g + 3, g - 2), 4))
    # Vignette.
    for y in range(128):
        for x in range(128):
            d = math.hypot(x - 64, y - 70) / 90
            cv.set(x, y, shade(cv.get(x, y), max(0.25, 1.1 - d)))
    line(cv, 64, 0, 64, 18, TWINE)
    draw_effigy(cv, 64, 18, 8, c=(0xB8, 0x9A, 0x6E), tw=(0xE8, 0xD8, 0xB0))
    # Hi8 grain and scanlines.
    for y in range(128):
        for x in range(128):
            c = shade(cv.get(x, y), 0.8 if y % 2 else 1.0)
            if rng.random() < 0.06:
                c = jitter(rng, c, 30)
            cv.set(x, y, c)
    for y in range(-5, 6):
        for x in range(-5, 6):
            if x * x + y * y <= 20:
                cv.set(12 + x, 12 + y, (0xE0, 0x18, 0x18))
    return cv


def paint_textures():
    ent = RP / "textures" / "entity" / "bw"
    paint_witch(10).save(ent / "witch.png")
    paint_mike(20).save(ent / "mike.png")
    paint_josh(25).save(ent / "josh.png")
    paint_handprint_wall(35).save(RP / "textures" / "blocks" / "bw" / "handprint_wall.png")
    paint_wood(30).save(ent / "stick_figure.png")
    paint_stone(40).save(ent / "rock_cairn.png")
    items = RP / "textures" / "items" / "bw"
    paint_camcorder().save(items / "camcorder.png")
    paint_map(50).save(items / "map.png")
    paint_bundle().save(items / "twig_bundle.png")
    paint_effigy_icon().save(items / "twig_effigy.png")
    paint_dossier(70).save(items / "dossier.png")
    icon = paint_pack_icon(60)
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
        for src, name in ((BP, "BlairWitch_BP"), (RP, "BlairWitch_RP")):
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
