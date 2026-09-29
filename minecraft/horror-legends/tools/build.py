#!/usr/bin/env python3
"""Build the "Horror Legends" add-on.

    python3 tools/build.py                  # everything, checked and packed
    python3 tools/build.py --textures       # only regenerate content, models and textures
    python3 tools/build.py --preview DIR    # also render model previews into DIR

content.py writes the data (entities, items, blocks, recipes, text),
creatures.py defines the models and paints their textures, animations.py
has the animations, art.py draws the icons, and sounds.py (run separately,
it needs numpy) makes the sounds. The add-on ships no third-party art. Only
the standard library is used here.

Produces dist/HorrorLegends.mcaddon and dist/HorrorLegends_WhatsApp.zip (the
same .mcaddon wrapped in a .zip, which messaging apps accept as an attachment).
"""

import json
import re
import sys
import zipfile
from pathlib import Path

import art
import content
import creatures
from animations import ANIMATIONS
from geometry import render
from lang import LANG
from pixels import Canvas

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "behavior_pack"
RP = ROOT / "resource_pack"
DIST = ROOT / "dist" / "HorrorLegends.mcaddon"
DIST_ZIP = ROOT / "dist" / "HorrorLegends_WhatsApp.zip"

MODELS = {
    "herobrine": creatures.herobrine_model(),
    "herobrine_boss": creatures.herobrine_boss_model(),
    "null": creatures.null_model(),
    "fog_man": creatures.fog_man_model(),
    "cave_dweller": creatures.cave_dweller_model(),
    "fake_player": creatures.fake_player_model(),
}
SEEDS = {"herobrine": 10, "herobrine_boss": 90, "null": 20, "fog_man": 30, "cave_dweller": 40, "fake_player": 80}
SCALE = 1.0 / creatures.K  # models are built at double size (see creatures.py)
FOG_WATCH_POSE = {"body": (4, 0, 0), "jaw": (4, 0, 0)}


def render_view(name, size, yaw, pitch, pose=None, textures=None):
    tex = (textures or TEXTURES)[name]
    return render(MODELS[name], tex, size=size, yaw=yaw, pitch=pitch, pose=pose, model_scale=SCALE)


TEXTURES = {}


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
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text + "\n", encoding="utf-8")
    anims = {"format_version": "1.8.0", "animations": ANIMATIONS}
    (RP / "animations" / "hl.animation.json").write_text(json.dumps(anims, indent=2) + "\n", encoding="utf-8")


def paint_textures():
    for name, model in MODELS.items():
        TEXTURES[name] = getattr(creatures, f"paint_{name}")(SEEDS[name], model)
    ent = RP / "textures" / "entity" / "hl"
    for name, tex in TEXTURES.items():
        tex.save(ent / f"{name}.png")
    creatures.paint_fake_player(SEEDS["fake_player"], MODELS["fake_player"], white_eyes=True).save(ent / "fake_player_eyes.png")
    creatures.paint_herobrine_boss(SEEDS["herobrine_boss"], MODELS["herobrine_boss"], phase=2).save(ent / "herobrine_boss_2.png")

    art.corrupted(50).save(RP / "textures" / "blocks" / "hl" / "corrupted_block.png")
    art.ward_lantern().save(RP / "textures" / "blocks" / "hl" / "ward_lantern.png")
    items = RP / "textures" / "items" / "hl"
    art.journal().save(items / "journal.png")
    art.flashlight().save(items / "flashlight.png")
    art.battery().save(items / "battery.png")
    art.hollow_pickaxe().save(items / "hollow_pickaxe.png")
    for n in range(1, 6):
        art.page(n).save(items / f"page_{n}.png")
    icon = art.pack_icon(render_view, 70)
    icon.save(BP / "pack_icon.png")
    icon.save(RP / "pack_icon.png")


PREVIEW_POSES = {
    "herobrine": [("", {})],
    "herobrine_boss": [("", {}), ("attack", {"rightArm": (-140, 0, 10), "chest": (10, -15, 0), "jaw": (20, 0, 0)}),
                       ("cast", {"rightArm": (-150, 0, 20), "leftArm": (-150, 0, -20), "head": (-25, 0, 0), "jaw": (35, 0, 0)})],
    "null": [("", {})],
    "fog_man": [("watch", FOG_WATCH_POSE),
                ("sprint", {"body": (28, 0, 0), "chest": (8, 0, 0), "neck": (-18, 0, 0), "head": (-22, 0, -12),
                            "jaw": (26, 0, 0), "rightArm": (80, 0, 0), "leftArm": (10, 0, 0),
                            "rightForearm": (-25, 0, 0), "leftForearm": (-25, 0, 0),
                            "coatBack": (40, 0, 0), "coatRight": (32, 0, 0), "coatLeft": (34, 0, 0),
                            "rightLeg": (-35, 0, 0), "rightShin": (30, 0, 0), "leftLeg": (25, 0, 0), "leftShin": (50, 0, 0)})],
    "cave_dweller": [("stalk", {}), ("attack", {"armRight": (-50, 0, 0), "armLeft": (-30, 0, 0), "jaw": (34, 0, 0), "neck": (14, 0, 0)})],
    "fake_player": [("", {}), ("mining", {"rightArm": (-75, 0, 0), "head": (15, 0, 0)})],
}


def write_previews(directory):
    """One sheet per model: front three-quarter, side and back views."""
    directory.mkdir(parents=True, exist_ok=True)
    for name, model in MODELS.items():
        views = []
        for label, pose in PREVIEW_POSES[name]:
            for yaw, pitch in ((25, -12), (90, -8), (200, -12)):
                views.append(render(model, TEXTURES[name], size=(200, 240), yaw=yaw, pitch=pitch, pose=pose,
                                    background=(0x3A, 0x3C, 0x44, 255), model_scale=SCALE))
        sheet = Canvas(200 * 3, 240 * (len(views) // 3), (0x20, 0x20, 0x24, 255))
        for k, view in enumerate(views):
            sheet.paste(view, (k % 3) * 200, (k // 3) * 240)
        sheet.save(directory / f"{name}.png")
    print(f"previews in {directory}")


# =========================================================================== #
# Checks
# =========================================================================== #

def validate():
    errors = []
    for path in sorted(list(BP.rglob("*.json")) + list(RP.rglob("*.json"))):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            errors.append(f"invalid JSON: {path.relative_to(ROOT)}: {e}")
    for lang in sorted(list(BP.rglob("*.lang")) + list(RP.rglob("*.lang"))):
        for n, text in enumerate(lang.read_text(encoding="utf-8").splitlines(), 1):
            if text.strip() and not text.startswith("##") and "=" not in text:
                errors.append(f"bad lang line: {lang.relative_to(ROOT)}:{n}")

    # Every bone an entity animates exists in its model.
    bones = {m.identifier: {b.name for b in m.bones} for m in MODELS.values()}
    for path in (RP / "entity").glob("*.json"):
        desc = json.loads(path.read_text())["minecraft:client_entity"]["description"]
        names = bones[desc["geometry"]["default"]]
        for short, anim in desc["animations"].items():
            missing = set(ANIMATIONS[anim]["bones"]) - names
            if missing:
                errors.append(f"{path.name}: {anim} animates missing bones {sorted(missing)}")
        for tex in desc["textures"].values():
            if not (RP / f"{tex}.png").exists():
                errors.append(f"{path.name}: missing texture {tex}")

    # Every text and sound the scripts use exists.
    sounds = json.loads((RP / "sounds" / "sound_definitions.json").read_text())["sound_definitions"]
    scripts = "\n".join(p.read_text(encoding="utf-8") for p in (BP / "scripts").rglob("*.js"))
    for key in sorted(set(re.findall(r'"(hl\.[a-z0-9_.]+)"', scripts))):
        if key not in LANG and key not in sounds:
            errors.append(f"scripts use unknown text or sound: {key}")
    for pattern in re.findall(r"`(hl\.[a-z0-9_.]*)\$\{", scripts):
        if not any(k.startswith(pattern) for k in LANG):
            errors.append(f"scripts use unknown text family: {pattern}*")
    for s in sounds.values():
        for f in s["sounds"]:
            if not (RP / f"{f['name']}.ogg").exists():
                errors.append(f"missing sound file {f['name']}.ogg")
    if errors:
        print("\n".join(errors))
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
    content.write_all()
    write_models()
    paint_textures()
    if "--preview" in sys.argv:
        write_previews(Path(sys.argv[sys.argv.index("--preview") + 1]))
    validate()
    if "--textures" not in sys.argv and "--preview" not in sys.argv:
        package()
