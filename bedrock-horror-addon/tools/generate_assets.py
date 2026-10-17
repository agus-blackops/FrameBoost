#!/usr/bin/env python3
"""Generate the data-driven files of the addon from one creature table.

Models live in horror/models.py, textures in horror/textures.py and
animations in horror/animations.py; this file wires them into entity,
spawn-rule, loot, sound, fog, language and manifest files.
Run from anywhere: `python3 tools/generate_assets.py`.
"""
import json
import random
from pathlib import Path

from horror import animations, models
from horror.textures import SCALE, paint, write_png

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "BP"
RP = ROOT / "RP"

BP_UUID = "1198378e-61c2-42cf-9250-c911f122ba86"
BP_DATA_UUID = "e17c57c4-21e2-4e61-95b8-278899234605"
BP_SCRIPT_UUID = "4314d6ff-421e-4c6f-bc3d-be3ea6e888a0"
RP_UUID = "19f8b22e-a0a5-4125-bb6a-35d944dcb15f"
RP_DATA_UUID = "6a0f5c2e-3b1d-4e8a-9c47-2d5e8f1a7b30"
VERSION = [1, 2, 0]
MIN_ENGINE = [1, 21, 0]

EMISSIVE = "entity_emissive_alpha"  # texture pixels with alpha 0 glow
BONE = (200, 190, 170)

CREATURES = {
    "stalker": {
        "names": {"en_US": "The Stalker", "es_ES": "El Acechador"},
        "palette": {"skin": (52, 44, 54), "bone": BONE, "vein": (110, 12, 18),
                    "void": (8, 6, 10), "eye": (255, 36, 18)},
        "material": EMISSIVE, "egg": ("#0b0b0d", "#b01010"),
        "health": 60, "speed": 0.26, "damage": 8, "follow": 64, "kb": 0.6,
        "width": 0.7, "height": 2.9, "xp": 15, "frozen_property": True,
        "sounds": {"pitch": [0.45, 0.6], "ambient": "mob.enderman.idle",
                   "hurt": "mob.enderman.hit", "death": "mob.enderman.death"},
        "loot": [
            {"rolls": 1, "entries": [{"type": "item", "name": "minecraft:ender_pearl", "weight": 1,
                                      "functions": [{"function": "set_count", "count": {"min": 0, "max": 2}}]}]},
            {"rolls": 1, "conditions": [{"condition": "killed_by_player"},
                                        {"condition": "random_chance", "chance": 0.15}],
             "entries": [{"type": "item", "name": "minecraft:echo_shard", "weight": 1}]},
        ],
        "spawn": {
            "minecraft:spawns_on_surface": {},
            "minecraft:brightness_filter": {"min": 0, "max": 7, "adjust_for_weather": False},
            "minecraft:weight": {"default": 3},
            "minecraft:herd": {"min_size": 1, "max_size": 1},
            "minecraft:density_limit": {"surface": 1},
            "minecraft:distance_filter": {"min": 24, "max": 64},
            "minecraft:difficulty_filter": {"min": "easy", "max": "hard"},
            "minecraft:biome_filter": {"test": "has_biome_tag", "operator": "==", "value": "overworld"},
        },
    },
    "woodbeast": {
        "names": {"en_US": "The Woodbeast", "es_ES": "La Bestia del Bosque"},
        "palette": {"fur": (58, 42, 30), "skin": (74, 46, 40), "bone": BONE, "antler": (96, 78, 60),
                    "vein": (96, 14, 12), "eye": (255, 176, 34)},
        "material": EMISSIVE, "egg": ("#2a1c12", "#ffbe1e"),
        "health": 40, "speed": 0.42, "damage": 6, "follow": 48, "kb": 0.3,
        "width": 1.0, "height": 1.9, "xp": 10,
        "sounds": {"pitch": [0.7, 0.9], "ambient": "mob.ravager.ambient",
                   "hurt": "mob.ravager.hurt", "death": "mob.ravager.death"},
        "extra_components": {
            # Hates fire: burns at dawn and backs off from players holding light.
            "minecraft:burns_in_daylight": {},
            "minecraft:behavior.avoid_mob_type": {
                "priority": 1,
                "entity_types": [{
                    "filters": {"all_of": [
                        {"test": "is_family", "subject": "other", "value": "player"},
                        {"any_of": [{"test": "has_equipment", "subject": "other", "domain": "hand", "value": item}
                                    for item in ("minecraft:torch", "minecraft:soul_torch",
                                                 "minecraft:lantern", "minecraft:soul_lantern")]},
                    ]},
                    "max_dist": 8, "walk_speed_multiplier": 1.0, "sprint_speed_multiplier": 1.4,
                }],
            },
        },
        "loot": [
            {"rolls": 1, "entries": [{"type": "item", "name": "minecraft:leather", "weight": 1,
                                      "functions": [{"function": "set_count", "count": {"min": 1, "max": 3}}]}]},
            {"rolls": 1, "entries": [{"type": "item", "name": "minecraft:bone", "weight": 1,
                                      "functions": [{"function": "set_count", "count": {"min": 0, "max": 2}}]}]},
        ],
        "spawn": {
            "minecraft:spawns_on_surface": {},
            "minecraft:brightness_filter": {"min": 0, "max": 7, "adjust_for_weather": False},
            "minecraft:weight": {"default": 6},
            "minecraft:herd": {"min_size": 1, "max_size": 1},
            "minecraft:density_limit": {"surface": 2},
            "minecraft:distance_filter": {"min": 24, "max": 64},
            "minecraft:difficulty_filter": {"min": "easy", "max": "hard"},
            "minecraft:biome_filter": {"test": "has_biome_tag", "operator": "==", "value": "forest"},
        },
    },
    "whisperer": {
        "names": {"en_US": "The Whisperer", "es_ES": "El Susurrante"},
        "palette": {"cloth": (58, 60, 76), "skin": (150, 148, 160), "bone": BONE, "vein": (70, 60, 90),
                    "void": (5, 5, 9), "eye": (225, 240, 255), "alpha": 165},
        "material": "entity_alphablend", "egg": ("#3c3e4a", "#e6f0ff"),
        "health": 30, "speed": 0.2, "damage": 4, "follow": 32, "kb": 0.0,
        "width": 0.7, "height": 2.6, "xp": 10,
        "effect": ("blindness", 6),
        "sounds": {"pitch": [0.4, 0.55], "ambient": "mob.vex.ambient",
                   "hurt": "mob.vex.hurt", "death": "mob.vex.death"},
        "extra_components": {
            "minecraft:damage_sensor": {"triggers": [{"cause": "fall", "deals_damage": False}]},
        },
        "loot": [
            {"rolls": 1, "entries": [{"type": "item", "name": "minecraft:phantom_membrane", "weight": 1,
                                      "functions": [{"function": "set_count", "count": {"min": 0, "max": 1}}]}]},
            {"rolls": 1, "entries": [{"type": "item", "name": "minecraft:glow_ink_sac", "weight": 1,
                                      "functions": [{"function": "set_count", "count": {"min": 0, "max": 2}}]}]},
        ],
        "spawn": {
            "minecraft:spawns_underground": {},
            "minecraft:brightness_filter": {"min": 0, "max": 4, "adjust_for_weather": False},
            "minecraft:weight": {"default": 8},
            "minecraft:herd": {"min_size": 1, "max_size": 1},
            "minecraft:density_limit": {"underground": 2},
            "minecraft:difficulty_filter": {"min": "easy", "max": "hard"},
            "minecraft:biome_filter": {"test": "has_biome_tag", "operator": "==", "value": "overworld"},
        },
    },
    # Never attacks: the script places it far away at night, it only stares,
    # and it vanishes when you approach or hold its gaze.
    "watcher": {
        "names": {"en_US": "The Watcher", "es_ES": "El Observador"},
        "palette": {"void": (9, 8, 12), "eye": (248, 248, 248)},
        "material": EMISSIVE, "egg": ("#050506", "#fafafa"),
        "width": 0.7, "height": 3.4,
        "passive": True,
    },
}

LANGS = {"en_US": "en_US", "es_ES": "es_ES", "es_MX": "es_ES"}  # lang file -> names key
LANG_EXTRA = {
    "horror.bloodnight.title": {"en_US": "§4§lBlood Night", "es_ES": "§4§lNoche de Sangre"},
    "horror.bloodnight.subtitle": {"en_US": "§7They are coming for you...", "es_ES": "§7Vienen por ti..."},
    "horror.bloodnight.end": {"en_US": "§7Dawn saves you... for now", "es_ES": "§7El amanecer te salva... por ahora"},
    "horror.jumpscare.title": {"en_US": "§4§lFOUND YOU", "es_ES": "§4§lTE ENCONTRÉ"},
    "horror.jumpscare.subtitle": {"en_US": "§8don't look away", "es_ES": "§8no apartes la vista"},
}


# ---------------------------------------------------------------- helpers
def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")


def all_cubes(bones):
    return [c for b in bones for c in b["cubes"]]


def pack_uvs(cubes):
    """Shelf-pack box-UV regions (2d+2w wide, d+h tall); returns the smallest square atlas that fits."""
    for atlas in (64, 128, 256):
        x = y = row = 0
        fits = True
        for c in sorted(cubes, key=lambda c: (-(c["size"][1] + c["size"][2]), -c["size"][0])):
            w, h, d = c["size"]
            bw, bh = 2 * (w + d), h + d
            if x + bw > atlas:
                x, y, row = 0, y + row, 0
            if y + bh > atlas or bw > atlas:
                fits = False
                break
            c["uv"] = [x, y]
            x += bw
            row = max(row, bh)
        if fits:
            return atlas
    raise ValueError("model does not fit a 256 atlas")


# --------------------------------------------------------------- geometry
def build_geometry(key, bones, atlas):
    out = []
    for b in bones:
        bone = {k: b[k] for k in ("name", "parent", "pivot", "rotation") if k in b}
        if b["cubes"]:
            bone["cubes"] = [{"origin": c["origin"], "size": c["size"], "uv": c["uv"]} for c in b["cubes"]]
        out.append(bone)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.horror_{key}",
                "texture_width": atlas, "texture_height": atlas,
                "visible_bounds_width": 4, "visible_bounds_height": 4.5,
                "visible_bounds_offset": [0, 1.75, 0],
            },
            "bones": out,
        }],
    }


def build_icon(path, bg, fg, seed):
    rng = random.Random(seed)
    size = 128
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            fog = rng.randint(-8, 8) + int(14 * (y / size))
            row.append(tuple(max(0, min(255, bg[i] + fog)) for i in range(3)) + (255,))
        rows.append(row)
    for y in range(36, 128):  # tall thin silhouette
        half = 10 if y < 64 else 7
        for x in range(64 - half, 64 + half):
            rows[y][x] = (4, 4, 5, 255)
    for ex in (56, 68):  # two glaring eyes
        for y in range(46, 52):
            for x in range(ex, ex + 4):
                rows[y][x] = (*fg, 255)
    write_png(path, rows)


# --------------------------------------------------------------- entities
def base_components(spec):
    return {
        "minecraft:collision_box": {"width": spec["width"], "height": spec["height"]},
        "minecraft:physics": {},
        "minecraft:nameable": {},
        "minecraft:jump.static": {},
        "minecraft:movement.basic": {},
    }


def hostile_entity(key, spec):
    follow = spec["follow"]
    target_family = {"test": "is_family", "subject": "other", "value": "player"}
    attack = {"damage": spec["damage"]}
    if "effect" in spec:
        attack["effect_name"], attack["effect_duration"] = spec["effect"]
    components = {
        **base_components(spec),
        "minecraft:type_family": {"family": ["horror", "monster", key]},
        "minecraft:health": {"value": spec["health"], "max": spec["health"]},
        "minecraft:movement": {"value": spec["speed"]},
        "minecraft:attack": attack,
        "minecraft:follow_range": {"value": follow, "max": follow},
        "minecraft:knockback_resistance": {"value": spec["kb"]},
        "minecraft:navigation.walk": {"can_path_over_water": False, "avoid_water": True,
                                      "can_pass_doors": True},
        "minecraft:pushable": {"is_pushable": True, "is_pushable_by_piston": True},
        "minecraft:despawn": {"despawn_from_distance": {"min_distance": 96, "max_distance": 128}},
        "minecraft:loot": {"table": f"loot_tables/entities/horror_{key}.json"},
        "minecraft:experience_reward": {"on_death": f"query.last_hit_by_player ? {spec['xp']} : 0"},
        "minecraft:ambient_sound_interval": {"value": 8, "range": 12, "event_name": "ambient"},
        "minecraft:behavior.float": {"priority": 0},
        "minecraft:behavior.hurt_by_target": {"priority": 1},
        "minecraft:behavior.nearest_attackable_target": {
            "priority": 2, "must_see": False, "reselect_targets": True,
            "within_radius": follow,
            "entity_types": [{"filters": target_family, "max_dist": follow}],
        },
        "minecraft:behavior.melee_attack": {"priority": 3, "speed_multiplier": 1.2, "track_target": True},
        "minecraft:behavior.random_stroll": {"priority": 6, "speed_multiplier": 0.6},
        "minecraft:behavior.look_at_player": {"priority": 7, "look_distance": 16},
        "minecraft:behavior.random_look_around": {"priority": 8},
        **spec.get("extra_components", {}),
    }
    properties = None
    if spec.get("frozen_property"):
        # Set by the script while a player's gaze freezes it; the client stops idle motion.
        properties = {"horror:frozen": {"type": "bool", "default": False, "client_sync": True}}
    return entity_file(key, components, properties=properties)


def passive_entity(key, spec):
    components = {
        **base_components(spec),
        "minecraft:type_family": {"family": ["horror", key]},
        "minecraft:health": {"value": 1000, "max": 1000},
        "minecraft:movement": {"value": 0.0},
        "minecraft:damage_sensor": {"triggers": [{"cause": "all", "deals_damage": False}]},
        "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
        "minecraft:knockback_resistance": {"value": 1.0},
        "minecraft:behavior.look_at_player": {"priority": 0, "look_distance": 96,
                                              "probability": 1.0, "angle_of_view_horizontal": 360},
    }
    return entity_file(key, components, vanish=True)


def entity_file(key, components, vanish=False, properties=None):
    description = {
        "identifier": f"horror:{key}",
        "is_spawnable": True,
        "is_summonable": True,
        "is_experimental": False,
    }
    if properties:
        description["properties"] = properties
    entity = {"description": description, "components": components}
    if vanish:
        entity["component_groups"] = {"horror:despawn": {"minecraft:instant_despawn": {}}}
        entity["events"] = {"horror:vanish": {"add": {"component_groups": ["horror:despawn"]}}}
    return {"format_version": "1.21.0", "minecraft:entity": entity}


def client_entity(key, spec, anim_names, animate):
    return {
        "format_version": "1.10.0",
        "minecraft:client_entity": {
            "description": {
                "identifier": f"horror:{key}",
                "materials": {"default": spec["material"]},
                "textures": {"default": f"textures/entity/{key}"},
                "geometry": {"default": f"geometry.horror_{key}"},
                "animations": {name: f"animation.horror.{key}.{name}" for name in anim_names},
                "scripts": {
                    "pre_animation": animations.pre_animation(spec.get("frozen_property", False)),
                    "animate": animate,
                },
                "render_controllers": ["controller.render.horror_default"],
                "spawn_egg": {"base_color": spec["egg"][0], "overlay_color": spec["egg"][1]},
            }
        },
    }


def spawn_rules(key, spec):
    return {
        "format_version": "1.8.0",
        "minecraft:spawn_rules": {
            "description": {"identifier": f"horror:{key}", "population_control": "monster"},
            "conditions": [spec["spawn"]],
        },
    }


# ---------------------------------------------------------------- shared
def render_controller():
    return {
        "format_version": "1.8.0",
        "render_controllers": {
            "controller.render.horror_default": {
                "geometry": "Geometry.default",
                "materials": [{"*": "Material.default"}],
                "textures": ["Texture.default"],
            }
        },
    }


def sounds():
    entities = {}
    for key, spec in CREATURES.items():
        if "sounds" not in spec:
            continue
        s = spec["sounds"]
        entities[f"horror:{key}"] = {
            "volume": 1.0, "pitch": s["pitch"],
            "events": {"ambient": s["ambient"], "hurt": s["hurt"], "death": s["death"]},
        }
    return {"entity_sounds": {"entities": entities}}


def blood_fog():
    layer = {"fog_start": 4, "fog_end": 40, "fog_color": "#4a0000", "render_distance_type": "fixed"}
    return {
        "format_version": "1.16.100",
        "minecraft:fog_settings": {
            "description": {"identifier": "horror:blood_fog"},
            "distance": {"air": layer, "weather": layer},
        },
    }


def manifests():
    bp = {
        "format_version": 2,
        "header": {
            "name": "Horror Stalkers [BP]",
            "description": "Stalking horror creatures, the Watcher and the Blood Night.",
            "uuid": BP_UUID, "version": VERSION, "min_engine_version": MIN_ENGINE,
        },
        "modules": [
            {"type": "data", "uuid": BP_DATA_UUID, "version": VERSION},
            {"type": "script", "language": "javascript", "uuid": BP_SCRIPT_UUID,
             "version": VERSION, "entry": "scripts/main.js"},
        ],
        "dependencies": [
            {"uuid": RP_UUID, "version": VERSION},
            {"module_name": "@minecraft/server", "version": "1.11.0"},
        ],
    }
    rp = {
        "format_version": 2,
        "header": {
            "name": "Horror Stalkers [RP]",
            "description": "Models, textures, animations, sounds and fog for Horror Stalkers.",
            "uuid": RP_UUID, "version": VERSION, "min_engine_version": MIN_ENGINE,
        },
        "modules": [{"type": "resources", "uuid": RP_DATA_UUID, "version": VERSION}],
        "dependencies": [{"uuid": BP_UUID, "version": VERSION}],
    }
    write_json(BP / "manifest.json", bp)
    write_json(RP / "manifest.json", rp)


def lang_files():
    (RP / "texts").mkdir(parents=True, exist_ok=True)
    for lang, source in LANGS.items():
        lines = []
        for key, spec in CREATURES.items():
            name = spec["names"][source]
            lines.append(f"entity.horror:{key}.name={name}")
            lines.append(f"item.spawn_egg.entity.horror:{key}.name={name}")
        lines += [f"{k}={v[source]}" for k, v in LANG_EXTRA.items()]
        (RP / "texts" / f"{lang}.lang").write_text("\n".join(lines) + "\n", encoding="utf-8")
    write_json(RP / "texts" / "languages.json", list(LANGS))


def clean_generated():
    """Remove previously generated files so renamed outputs never linger."""
    for folder in (RP / "animations", RP / "models" / "entity", RP / "textures" / "entity"):
        if folder.is_dir():
            for f in folder.iterdir():
                f.unlink()


def main():
    clean_generated()
    manifests()
    all_animations = {}
    for key, spec in CREATURES.items():
        if spec.get("passive"):
            write_json(BP / "entities" / f"{key}.json", passive_entity(key, spec))
        else:
            write_json(BP / "entities" / f"{key}.json", hostile_entity(key, spec))
            write_json(BP / "spawn_rules" / f"{key}.json", spawn_rules(key, spec))
            write_json(BP / "loot_tables" / "entities" / f"horror_{key}.json", {"pools": spec["loot"]})

        bones = getattr(models, key)()
        atlas = pack_uvs(all_cubes(bones))
        anims, animate = animations.ALL[key]()
        all_animations.update({f"animation.horror.{key}.{name}": a for name, a in anims.items()})

        write_json(RP / "entity" / f"{key}.entity.json", client_entity(key, spec, anims, animate))
        write_json(RP / "models" / "entity" / f"{key}.geo.json", build_geometry(key, bones, atlas))
        write_png(RP / "textures" / "entity" / f"{key}.png",
                  paint(atlas, all_cubes(bones), spec["palette"], spec["material"], key))
    write_json(RP / "animations" / "horror.animation.json",
               {"format_version": "1.8.0", "animations": all_animations})
    write_json(RP / "render_controllers" / "horror.render_controllers.json", render_controller())
    write_json(RP / "sounds.json", sounds())
    write_json(RP / "fogs" / "blood_fog.json", blood_fog())
    lang_files()
    build_icon(BP / "pack_icon.png", (20, 8, 8), (255, 40, 20), "bp")
    build_icon(RP / "pack_icon.png", (8, 10, 20), (230, 240, 255), "rp")
    print(f"assets generated in {ROOT} (textures at {SCALE}x)")


if __name__ == "__main__":
    main()
