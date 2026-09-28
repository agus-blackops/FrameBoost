#!/usr/bin/env python3
"""Build the "Horror Legends" add-on.

    python3 tools/build.py                  # models, textures, validate, pack
    python3 tools/build.py --textures       # only regenerate models and textures
    python3 tools/build.py --preview DIR    # also render model previews into DIR

Models are defined in Python (creatures.py, on top of geometry.py) and written to
resource_pack/models/entity/hl.geo.json; every texture is painted
procedurally against the same cube list, so the add-on ships no third-party
art. Only the standard library is used.

Produces dist/HorrorLegends.mcaddon and dist/HorrorLegends_WhatsApp.zip (the
same .mcaddon wrapped in a .zip, which messaging apps accept as an attachment).
"""

import json
import random
import sys
import zipfile
from pathlib import Path

import creatures
from animations import ANIMATIONS
from creatures import grad
from geometry import render
from pixels import Canvas, jitter, shade

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "behavior_pack"
RP = ROOT / "resource_pack"
DIST = ROOT / "dist" / "HorrorLegends.mcaddon"
DIST_ZIP = ROOT / "dist" / "HorrorLegends_WhatsApp.zip"

MODELS = {
    "herobrine": creatures.herobrine_model(),
    "null": creatures.null_model(),
    "fog_man": creatures.fog_man_model(),
    "cave_dweller": creatures.cave_dweller_model(),
    "fake_player": creatures.fake_player_model(),
}
SEEDS = {"herobrine": 10, "null": 20, "fog_man": 30, "cave_dweller": 40, "fake_player": 80}
SCALE = 1.0 / creatures.K  # models are built at double size (see creatures.py)


# =========================================================================== #
# Blocks, items, pack icon
# =========================================================================== #

def paint_corrupted(seed):
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


def paint_journal(seed):
    """A scuffed black notebook with a pale handprint on the cover and a red
    string bookmark."""
    rng = random.Random(seed)
    cv = Canvas(16, 16)
    cover, edge = (0x26, 0x24, 0x28), (0x12, 0x11, 0x14)
    for y in range(1, 15):
        for x in range(2, 14):
            c = jitter(rng, grad(cover, y - 1, 14, 1.2, 0.85), 5)
            if x in (2, 13) or y in (1, 14):
                c = edge
            elif rng.random() < 0.05:
                c = shade(c, 1.5)  # scuffs
            cv.set(x, y, c)
    for y in range(2, 14):
        cv.set(12, y, (0xE4, 0xDA, 0xC2) if y % 2 else (0xB8, 0xAE, 0x96))  # page edges
        cv.set(3, y, (0x3A, 0x38, 0x3E))  # spine
    hand = [
        ".#.#.#",
        ".#.#.#",
        "######",
        "#####.",
        ".####.",
        "..##..",
    ]
    for y, row in enumerate(hand):
        for x, ch in enumerate(row):
            if ch == "#":
                cv.set(5 + x, 4 + y, jitter(rng, (0xD8, 0xD2, 0xC8), 10))
    for y in range(9, 16):
        cv.set(10, y, (0xB0, 0x18, 0x18))  # bookmark
    return cv


def paint_flashlight():
    """A black rubber torch, lit, seen side-on."""
    cv = Canvas(16, 16)
    body, hi, lo = (0x2A, 0x2A, 0x30), (0x4E, 0x4E, 0x58), (0x16, 0x16, 0x1A)
    for x in range(1, 10):
        cv.set(x, 7, hi)
        cv.set(x, 8, body)
        cv.set(x, 9, lo)
    for x in (3, 5):
        cv.set(x, 8, lo)  # grip ribs
    cv.set(6, 6, (0xD0, 0x22, 0x22))  # switch
    cv.rect(10, 5, 3, 6, body)  # head
    cv.set(10, 5, hi)
    cv.rect(11, 6, 1, 4, (0xE0, 0xB0, 0x20))  # yellow ring
    cv.rect(12, 6, 1, 4, (0xFF, 0xF4, 0xB0))  # lens
    for x in range(13, 16):
        spread = x - 12
        for y in range(8 - spread - 1, 8 + spread + 1):
            cv.set(x, y, (0xFF, 0xF4, 0xB8, max(40, 150 - spread * 35)))  # beam
    return cv


def render_view(name, textures, size, yaw, pitch, pose=None, ppu=None):
    return render(MODELS[name], textures[name], size=size, yaw=yaw, pitch=pitch, pose=pose, ppu=ppu,
                  model_scale=SCALE)


FOG_WATCH_POSE = {"body": (4, 0, 0), "jaw": (4, 0, 0)}


def paint_pack_icon(textures, seed):
    rng = random.Random(seed)
    cv = Canvas(128, 128)
    for y in range(128):
        for x in range(128):
            fog = int(14 + 70 * (y / 127) ** 1.4 + rng.randint(-4, 4))
            cv.set(x, y, (fog, fog + 2, fog + 5))
    # Dead trees in the background.
    for tx, w in ((6, 5), (30, 4), (96, 6), (118, 4)):
        for y in range(0, 110):
            for x in range(tx, tx + w):
                cv.blend(x, y, (0x0C, 0x0D, 0x10), 0.8)
    # Herobrine, far off between the trees; null glitching at the edge.
    cv.paste(render_view("herobrine", textures, (20, 30), 15, -4), 12, 52)
    cv.paste(render_view("null", textures, (28, 44), -30, -4), 98, 44)
    # The Man From The Fog, close.
    cv.paste(render_view("fog_man", textures, (64, 116), 22, -6, FOG_WATCH_POSE), 34, 6)
    # Fog rolling over everything below the waist.
    for y in range(62, 128):
        t = min(1.0, (y - 62) / 60) * 0.7
        for x in range(128):
            cv.blend(x, y, (0x5A, 0x5E, 0x64), t)
    # The Cave Dweller crawling out of the dark in the corner.
    for y in range(92, 128):
        for x in range(0, 60):
            if (x / 60) ** 2 + ((128 - y) / 36) ** 2 < 1.0:
                cv.blend(x, y, (0x06, 0x06, 0x08), 0.85)
    cv.paste(render_view("cave_dweller", textures, (52, 34), 35, -10), 2, 92)
    for y in range(128):
        for x in range(128):
            if rng.random() < 0.05:
                cv.set(x, y, jitter(rng, cv.get(x, y), 20))
    return cv


# =========================================================================== #
# Output
# =========================================================================== #

def write_models():
    errors = [e for m in MODELS.values() for e in m.check_uv()]
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    geo = {"format_version": "1.16.0", "minecraft:geometry": [m.to_json() for m in MODELS.values()]}
    # One line per cube keeps the file readable with six faces of UVs each.
    cubes = {}
    for g in geo["minecraft:geometry"]:
        for b in g["bones"]:
            for i, c in enumerate(b.get("cubes", [])):
                key = f"@cube{len(cubes)}@"
                cubes[key] = json.dumps(c)
                b["cubes"][i] = key
    text = json.dumps(geo, indent=2)
    for key, c in cubes.items():
        text = text.replace(f'"{key}"', c)
    path = RP / "models" / "entity" / "hl.geo.json"
    path.write_text(text + "\n", encoding="utf-8")
    anims = {"format_version": "1.8.0", "animations": ANIMATIONS}
    path = RP / "animations" / "hl.animation.json"
    path.write_text(json.dumps(anims, indent=2) + "\n", encoding="utf-8")


def paint_textures():
    textures = {name: getattr(creatures, f"paint_{name}")(SEEDS[name], model) for name, model in MODELS.items()}
    creatures.paint_fake_player(SEEDS["fake_player"], MODELS["fake_player"], white_eyes=True).save(
        RP / "textures" / "entity" / "hl" / "fake_player_eyes.png")
    ent = RP / "textures" / "entity" / "hl"
    for name, tex in textures.items():
        tex.save(ent / f"{name}.png")
    paint_corrupted(50).save(RP / "textures" / "blocks" / "hl" / "corrupted_block.png")
    flipbook = [{"flipbook_texture": "textures/blocks/hl/corrupted_block", "atlas_tile": "hl_corrupted",
                 "ticks_per_frame": 3, "blend_frames": False}]
    (RP / "textures" / "flipbook_textures.json").write_text(json.dumps(flipbook, indent=2) + "\n", encoding="utf-8")
    items = RP / "textures" / "items" / "hl"
    paint_journal(60).save(items / "journal.png")
    paint_flashlight().save(items / "flashlight.png")
    icon = paint_pack_icon(textures, 70)
    icon.save(BP / "pack_icon.png")
    icon.save(RP / "pack_icon.png")
    return textures


PREVIEW_POSES = {
    "herobrine": [("", {})],
    "null": [("", {})],
    "fog_man": [("watch", FOG_WATCH_POSE),
                ("sprint", {"body": (30, 0, 0), "chest": (10, 0, 0), "neck": (-20, 0, 0), "head": (-25, 0, 0),
                            "jaw": (30, 0, 0), "rightArm": (70, 0, 0), "leftArm": (20, 0, 0),
                            "coatBack": (40, 0, 0), "coatRight": (30, 0, 0), "coatLeft": (35, 0, 0),
                            "rightLeg": (-35, 0, 0), "rightShin": (30, 0, 0), "leftLeg": (25, 0, 0), "leftShin": (50, 0, 0)})],
    "cave_dweller": [("stalk", {}), ("chase", {"jaw": (38, 0, 0), "head": (-14, 0, 0), "neck": (-6, 0, 0)})],
    "fake_player": [("", {}), ("mining", {"rightArm": (-75, 0, 0), "head": (15, 0, 0)}),
                    ("sneaking", {"body": (28, 0, 0), "rightLeg": (0, 0, 0), "head": (-10, 0, 0),
                                  "rightArm": (20, 0, 0), "leftArm": (20, 0, 0)})],
}


def write_previews(textures, directory):
    """One sheet per model: front three-quarter, side and back views."""
    directory.mkdir(parents=True, exist_ok=True)
    for name, model in MODELS.items():
        views = []
        for label, pose in PREVIEW_POSES[name]:
            for yaw, pitch in ((25, -12), (90, -8), (200, -12)):
                views.append(render(model, textures[name], size=(200, 240), yaw=yaw, pitch=pitch, pose=pose,
                                    background=(0x3A, 0x3C, 0x44, 255)))
        sheet = Canvas(200 * 3, 240 * (len(views) // 3), (0x20, 0x20, 0x24, 255))
        for k, view in enumerate(views):
            sheet.paste(view, (k % 3) * 200, (k // 3) * 240)
        big = Canvas(64 * 4, 64 * 4, (0x50, 0x50, 0x58, 255))
        tex = textures[name]
        for y in range(256):
            for x in range(256):
                c = tex.get(x * tex.w // 256, y * tex.h // 256)
                if c[3]:
                    big.set(x, y, c[:3])
        sheet.save(directory / f"{name}.png")
        big.save(directory / f"{name}_texture.png")
    print(f"previews in {directory}")


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
    write_models()
    textures = paint_textures()
    if "--preview" in sys.argv:
        write_previews(textures, Path(sys.argv[sys.argv.index("--preview") + 1]))
    if "--textures" not in sys.argv and "--preview" not in sys.argv:
        validate()
        package()
