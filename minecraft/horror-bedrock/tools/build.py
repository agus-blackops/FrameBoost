#!/usr/bin/env python3
"""Build the "Horror Legends" add-on.

    python3 tools/build.py                  # everything, checked and packed
    python3 tools/build.py --textures       # only regenerate content, models and textures
    python3 tools/build.py --preview DIR    # also render model previews into DIR

content.py writes the data (entities, items, blocks, recipes, text),
models/ has one module per creature (its model and the painter for its
texture), animations.py the animations, art.py the icons, and sounds.py (run
separately, it needs numpy) makes the sounds. The add-on ships no
third-party art. Only the standard library is used here.

Produces dist/HorrorFears.mcaddon and dist/HorrorFears_WhatsApp.zip (the
same .mcaddon wrapped in a .zip, which messaging apps accept as an attachment).
"""

import json
import re
import sys
import zipfile
from pathlib import Path

import importlib

import art
import content
import items3d
from animations import ANIMATIONS
from geometry import render
from lang import LANG
from models.kit import K
from molang import pose as anim_pose
from pixels import Canvas

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "behavior_pack"
RP = ROOT / "resource_pack"
DIST = ROOT / "dist" / "HorrorBedrock.mcaddon"
DIST_ZIP = ROOT / "dist" / "HorrorBedrock_WhatsApp.zip"
# One .mcpack per pack: the most reliable thing to import on phones (open
# both, behaviour first).
DIST_PACKS = {"HorrorBedrock_BP": ROOT / "dist" / "HorrorBedrock_BP.mcpack",
              "HorrorBedrock_RP": ROOT / "dist" / "HorrorBedrock_RP.mcpack"}

CREATURES = ["green_steve", "mimicer", "knocker", "lick", "boiled_one", "slenderman", "smile_dog", "jeff"]
MODULES = {name: importlib.import_module(f"models.{name}") for name in CREATURES}
MODELS = {name: mod.model() for name, mod in MODULES.items()}
SEEDS = {name: 10 * (i + 1) for i, name in enumerate(CREATURES)}
# Extra textures: (file, creature, painter options).
VARIANTS = []
SCALE = 1.0 / K  # models are built at double size (see models/kit.py)


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
    path = RP / "models" / "entity" / "hb.geo.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text + "\n", encoding="utf-8")
    anims = {"format_version": "1.8.0", "animations": ANIMATIONS}
    (RP / "animations").mkdir(parents=True, exist_ok=True)
    (RP / "animations" / "hb.animation.json").write_text(json.dumps(anims, indent=2) + "\n", encoding="utf-8")


def write_items():
    """The items' 3D models in your hand (see items3d.py)."""
    models = {name: make() for name, make in items3d.MODELS.items()}
    errors = [e for m in models.values() for e in m.check_uv()]
    if errors:
        print("\n".join(errors))
        sys.exit(1)
    geo = {"format_version": "1.16.0", "minecraft:geometry": [m.to_json() for m in models.values()]}
    (RP / "models" / "entity" / "hb_items.geo.json").write_text(json.dumps(geo) + "\n", encoding="utf-8")
    anims = {"format_version": "1.8.0", "animations": items3d.animations()}
    (RP / "animations" / "hb_items.animation.json").write_text(json.dumps(anims, indent=2) + "\n", encoding="utf-8")
    tex = RP / "textures" / "models" / "hb"
    painters = {"flashlight": items3d.paint_flashlight, "battery": items3d.paint_battery,
                "journal": items3d.paint_journal}
    tex.mkdir(parents=True, exist_ok=True)
    for name, paint in painters.items():
        paint(7, models[name]).save(tex / f"{name}.png")
    folder = RP / "attachables"
    folder.mkdir(parents=True, exist_ok=True)
    for identifier, model in items3d.ITEMS.items():
        short = identifier.split(":")[1]
        content.write(folder / f"{short}.json", items3d.attachable(identifier, model, model))


def paint_textures():
    for name, model in MODELS.items():
        TEXTURES[name] = MODULES[name].paint(SEEDS[name], model)
    ent = RP / "textures" / "entity" / "hb"
    for name, tex in TEXTURES.items():
        tex.save(ent / f"{name}.png")
    for file, name, options in VARIANTS:
        MODULES[name].paint(SEEDS[name], MODELS[name], **options).save(ent / f"{file}.png")

    art.ward_lantern().save(RP / "textures" / "blocks" / "hb" / "ward_lantern.png")
    items = RP / "textures" / "items" / "hb"
    art.journal().save(items / "journal.png")
    art.flashlight().save(items / "flashlight.png")
    art.battery().save(items / "battery.png")
    art.note().save(items / "note.png")
    art.slender_page_icon().save(items / "page.png")
    art.slender_page_block().save(RP / "textures" / "blocks" / "hb" / "slender_page.png")
    icon = art.pack_icon(render_view, 70)
    icon.save(BP / "pack_icon.png")
    icon.save(RP / "pack_icon.png")


def animated(names, **kw):
    """A preview pose taken from the creature's own animations."""
    return anim_pose(ANIMATIONS, [f"animation.hb.{n}" for n in names], **kw)


PREVIEW_POSES = {
    "green_steve": [("", {}), ("walk", animated(["steve.walk"], dist=0.6, speed=0.8))],
    "mimicer": [("", {}), ("run", animated(["mimicer.run"], dist=0.6, speed=0.9))],
    "knocker": [("", {}), ("knock", animated(["knocker.knock"]))],
    "lick": [("", {}), ("run", animated(["lick.run"], dist=0.6, speed=0.9))],
    "boiled_one": [("", {})],
    "slenderman": [("", animated(["slender.fold"])), ("tendrils", animated(["slender.tendrils"]))],
    "smile_dog": [("sit", animated(["dog.sit"])), ("run", animated(["dog.run"], dist=0.6, speed=0.9))],
    "jeff": [("", {}), ("run", animated(["jeff.run"], dist=0.6, speed=0.9))],
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
        controllers = json.loads((RP / "animation_controllers" / "hb.animation_controllers.json").read_text())
        for short, anim in desc["animations"].items():
            if anim.startswith("controller."):
                # Every animation its states play is one the entity has.
                states = controllers["animation_controllers"][anim]["states"].values()
                used = {next(iter(a)) if isinstance(a, dict) else a for st in states for a in st.get("animations", [])}
                if used - set(desc["animations"]):
                    errors.append(f"{path.name}: {anim} plays unknown {sorted(used - set(desc['animations']))}")
                continue
            missing = set(ANIMATIONS[anim]["bones"]) - names
            if missing:
                errors.append(f"{path.name}: {anim} animates missing bones {sorted(missing)}")
        for tex in desc["textures"].values():
            if not (RP / f"{tex}.png").exists():
                errors.append(f"{path.name}: missing texture {tex}")

    # Every text and sound the scripts use exists.
    sounds = json.loads((RP / "sounds" / "sound_definitions.json").read_text())["sound_definitions"]
    scripts = "\n".join(p.read_text(encoding="utf-8") for p in (BP / "scripts").rglob("*.js"))
    for key in sorted(set(re.findall(r'"(hb\.[a-z0-9_.]+)"', scripts))):
        if key not in LANG and key not in sounds:
            errors.append(f"scripts use unknown text or sound: {key}")
    for pattern in re.findall(r"`(hb\.[a-z0-9_.]*)\$\{", scripts):
        if not any(k.startswith(pattern) for k in LANG):
            errors.append(f"scripts use unknown text family: {pattern}*")
    # Every item model has its geometry, animations and texture.
    item_geo = {g["description"]["identifier"] for g in
                json.loads((RP / "models" / "entity" / "hb_items.geo.json").read_text())["minecraft:geometry"]}
    item_anims = json.loads((RP / "animations" / "hb_items.animation.json").read_text())["animations"]
    for path in (RP / "attachables").glob("*.json"):
        desc = json.loads(path.read_text())["minecraft:attachable"]["description"]
        if desc["geometry"]["default"] not in item_geo:
            errors.append(f"{path.name}: missing geometry")
        if any(a not in item_anims for a in desc["animations"].values()):
            errors.append(f"{path.name}: missing animation")
        if not (RP / f"{desc['textures']['default']}.png").exists():
            errors.append(f"{path.name}: missing texture")
    for s in sounds.values():
        for f in s["sounds"]:
            if not (RP / f"{f['name']}.ogg").exists():
                errors.append(f"missing sound file {f['name']}.ogg")
    if errors:
        print("\n".join(errors))
        sys.exit(1)


def package():
    DIST.parent.mkdir(parents=True, exist_ok=True)
    packs = ((BP, "HorrorBedrock_BP"), (RP, "HorrorBedrock_RP"))
    with zipfile.ZipFile(DIST, "w", zipfile.ZIP_DEFLATED) as z:
        for src, name in packs:
            for path in sorted(src.rglob("*")):
                if path.is_file():
                    z.write(path, f"{name}/{path.relative_to(src).as_posix()}")
    # An .mcpack has the pack's files at its root (manifest.json at the top).
    for src, name in packs:
        with zipfile.ZipFile(DIST_PACKS[name], "w", zipfile.ZIP_DEFLATED) as z:
            for path in sorted(src.rglob("*")):
                if path.is_file():
                    z.write(path, path.relative_to(src).as_posix())
    # WhatsApp won't send .mcaddon/.mcpack files, so they travel in a .zip:
    # extract it, then open the .mcaddon (or the two .mcpacks).
    with zipfile.ZipFile(DIST_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(DIST, DIST.name)
        for f in DIST_PACKS.values():
            z.write(f, f.name)
    for f in (DIST, *DIST_PACKS.values(), DIST_ZIP):
        print(f"wrote {f.relative_to(ROOT)} ({f.stat().st_size} bytes)")


if __name__ == "__main__":
    content.write_all()
    write_models()
    paint_textures()
    write_items()
    if "--preview" in sys.argv:
        write_previews(Path(sys.argv[sys.argv.index("--preview") + 1]))
    validate()
    if "--textures" not in sys.argv and "--preview" not in sys.argv:
        package()
