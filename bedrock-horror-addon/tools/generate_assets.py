#!/usr/bin/env python3
"""Generate the data-driven files of the addon from one creature table.

Writes manifests, entity behaviour/client files, spawn rules, geometry,
textures, animation, render controller, language files and pack icons.
Run from anywhere: `python3 tools/generate_assets.py`.
"""
import json
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "BP"
RP = ROOT / "RP"

BP_UUID = "1198378e-61c2-42cf-9250-c911f122ba86"
BP_DATA_UUID = "e17c57c4-21e2-4e61-95b8-278899234605"
BP_SCRIPT_UUID = "4314d6ff-421e-4c6f-bc3d-be3ea6e888a0"
RP_UUID = "19f8b22e-a0a5-4125-bb6a-35d944dcb15f"
RP_DATA_UUID = "6a0f5c2e-3b1d-4e8a-9c47-2d5e8f1a7b30"
VERSION = [1, 0, 0]
MIN_ENGINE = [1, 21, 0]

ATLAS = 64  # every texture is 64x64


def cube(origin, size, **extra):
    return {"origin": origin, "size": size, **extra}


CREATURES = {
    "stalker": {
        "names": {"en_US": "The Stalker", "es_ES": "El Acechador"},
        "base": (24, 22, 26), "accent": (70, 8, 8), "eye": (255, 40, 20), "alpha": 255,
        "material": "entity_alphatest", "egg": ("#0b0b0d", "#b01010"),
        "health": 60, "speed": 0.26, "damage": 8, "follow": 64, "kb": 0.6,
        "width": 0.7, "height": 2.85,
        "bones": [
            {"name": "root", "pivot": [0, 0, 0], "cubes": []},
            {"name": "leg_r", "parent": "root", "pivot": [-2, 20, 0], "cubes": [cube([-3, 0, -1], [2, 20, 2])]},
            {"name": "leg_l", "parent": "root", "pivot": [2, 20, 0], "cubes": [cube([1, 0, -1], [2, 20, 2])]},
            {"name": "body", "parent": "root", "pivot": [0, 20, 0], "cubes": [cube([-3, 20, -1.5], [6, 16, 3])]},
            {"name": "arm_r", "parent": "root", "pivot": [-4.5, 36, 0], "cubes": [cube([-5.5, 8, -1], [2, 28, 2])]},
            {"name": "arm_l", "parent": "root", "pivot": [4.5, 36, 0], "cubes": [cube([3.5, 8, -1], [2, 28, 2])]},
            {"name": "head", "parent": "root", "pivot": [0, 36, 0],
             "cubes": [cube([-2.5, 36, -2.5], [5, 8, 5], eyes=True)]},
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
        "base": (38, 28, 20), "accent": (14, 10, 8), "eye": (255, 190, 30), "alpha": 255,
        "material": "entity_alphatest", "egg": ("#2a1c12", "#ffbe1e"),
        "health": 40, "speed": 0.42, "damage": 6, "follow": 48, "kb": 0.3,
        "width": 1.0, "height": 1.9,
        "bones": [
            {"name": "root", "pivot": [0, 0, 0], "cubes": []},
            {"name": "body", "parent": "root", "pivot": [0, 12, 0], "cubes": [cube([-4, 12, -7], [8, 8, 14])]},
            {"name": "head", "parent": "root", "pivot": [0, 18, -7],
             "cubes": [cube([-3, 17, -13], [6, 6, 6], eyes=True),
                       cube([-4, 23, -11], [1, 6, 1]),
                       cube([3, 23, -11], [1, 6, 1])]},
            {"name": "arm_r", "parent": "root", "pivot": [-2.5, 12, -5], "cubes": [cube([-4, 0, -6.5], [3, 12, 3])]},
            {"name": "arm_l", "parent": "root", "pivot": [2.5, 12, -5], "cubes": [cube([1, 0, -6.5], [3, 12, 3])]},
            {"name": "leg_r", "parent": "root", "pivot": [-2.5, 12, 5], "cubes": [cube([-4, 0, 3.5], [3, 12, 3])]},
            {"name": "leg_l", "parent": "root", "pivot": [2.5, 12, 5], "cubes": [cube([1, 0, 3.5], [3, 12, 3])]},
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
        "base": (60, 62, 74), "accent": (20, 20, 30), "eye": (230, 240, 255), "alpha": 150,
        "material": "entity_alphablend", "egg": ("#3c3e4a", "#e6f0ff"),
        "health": 30, "speed": 0.2, "damage": 4, "follow": 32, "kb": 0.0,
        "width": 0.7, "height": 2.4,
        "effect": ("blindness", 6),
        "bones": [
            {"name": "root", "pivot": [0, 0, 0], "cubes": []},
            {"name": "leg_r", "parent": "root", "pivot": [-2, 8, 0], "cubes": [cube([-3, 2, -1], [2, 6, 2])]},
            {"name": "leg_l", "parent": "root", "pivot": [2, 8, 0], "cubes": [cube([1, 2, -1], [2, 6, 2])]},
            {"name": "body", "parent": "root", "pivot": [0, 8, 0], "cubes": [cube([-4, 8, -2], [8, 24, 4])]},
            {"name": "arm_r", "parent": "root", "pivot": [-5, 30, 0], "cubes": [cube([-7, 6, -1], [2, 26, 2])]},
            {"name": "arm_l", "parent": "root", "pivot": [5, 30, 0], "cubes": [cube([5, 6, -1], [2, 26, 2])]},
            {"name": "head", "parent": "root", "pivot": [0, 32, 0],
             "cubes": [cube([-3, 32, -3], [6, 6, 6], eyes=True)]},
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
}


# ---------------------------------------------------------------- helpers
def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def write_png(path, width, height, rows):
    raw = b"".join(b"\x00" + bytes(ch for px in row for ch in px) for row in rows)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def pack_uvs(cubes):
    """Shelf-pack box-UV regions (2d+2w wide, d+h tall) into the atlas."""
    x = y = row = 0
    for c in sorted(cubes, key=lambda c: -(c["size"][1] + c["size"][2])):
        w, h, d = (int(v) for v in c["size"])
        bw, bh = 2 * (w + d), h + d
        if x + bw > ATLAS:
            x, y, row = 0, y + row, 0
        assert y + bh <= ATLAS and bw <= ATLAS, "texture atlas overflow"
        c["uv"] = [x, y]
        x += bw
        row = max(row, bh)


# --------------------------------------------------------------- geometry
def build_geometry(key, spec):
    all_cubes = [c for b in spec["bones"] for c in b["cubes"]]
    pack_uvs(all_cubes)
    bones = []
    for b in spec["bones"]:
        bone = {"name": b["name"], "pivot": b["pivot"]}
        if "parent" in b:
            bone["parent"] = b["parent"]
        if b["cubes"]:
            bone["cubes"] = [{"origin": c["origin"], "size": c["size"], "uv": c["uv"]} for c in b["cubes"]]
        bones.append(bone)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.horror_{key}",
                "texture_width": ATLAS, "texture_height": ATLAS,
                "visible_bounds_width": 4, "visible_bounds_height": 4,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": bones,
        }],
    }


# --------------------------------------------------------------- textures
def clamp(v):
    return max(0, min(255, int(v)))


def build_texture(key, spec, all_cubes):
    rng = random.Random(key)
    base, accent, alpha = spec["base"], spec["accent"], spec["alpha"]
    px = [[(0, 0, 0, 0)] * ATLAS for _ in range(ATLAS)]
    for c in all_cubes:
        w, h, d = (int(v) for v in c["size"])
        u, v = c["uv"]
        streaks = [rng.randint(-14, 14) for _ in range(2 * (w + d))]
        for yy in range(v, v + h + d):
            for xx in range(u, u + 2 * (w + d)):
                n = rng.randint(-10, 10) + streaks[xx - u]
                col = tuple(clamp(base[i] + n) for i in range(3))
                if rng.random() < 0.07:
                    col = tuple(clamp(accent[i] + rng.randint(-6, 6)) for i in range(3))
                px[yy][xx] = (*col, alpha)
        if c.get("eyes"):
            fx, fy = u + d, v + d  # front (-Z) face
            ey = fy + max(1, h // 3)
            for ex in (fx + 1, fx + w - 2):
                px[ey][ex] = (*spec["eye"], 255)
                if spec is CREATURES["stalker"]:  # tall hollow stare
                    px[ey + 1][ex] = (*spec["eye"], 255)
            if spec is CREATURES["stalker"]:  # thin mouth line
                for mx in range(fx + 1, fx + w - 1):
                    px[fy + h - 2][mx] = (*accent, 255)
    return px


def build_icon(path, bg, fg, seed):
    rng = random.Random(seed)
    size = 128
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            fog = rng.randint(-8, 8) + int(14 * (y / size))
            row.append(tuple(clamp(bg[i] + fog) for i in range(3)) + (255,))
        rows.append(row)
    for ex in (44, 76):  # two glaring eyes
        for y in range(52, 60):
            for x in range(ex, ex + 8):
                rows[y][x] = (*fg, 255)
    write_png(path, size, size, rows)


# --------------------------------------------------------------- entities
def behavior_entity(key, spec):
    follow = spec["follow"]
    target_family = {"test": "is_family", "subject": "other", "value": "player"}
    attack = {"damage": spec["damage"]}
    if "effect" in spec:
        attack["effect_name"], attack["effect_duration"] = spec["effect"]
    return {
        "format_version": "1.21.0",
        "minecraft:entity": {
            "description": {
                "identifier": f"horror:{key}",
                "is_spawnable": True,
                "is_summonable": True,
                "is_experimental": False,
            },
            "components": {
                "minecraft:type_family": {"family": ["horror", "monster", key]},
                "minecraft:collision_box": {"width": spec["width"], "height": spec["height"]},
                "minecraft:health": {"value": spec["health"], "max": spec["health"]},
                "minecraft:movement": {"value": spec["speed"]},
                "minecraft:attack": attack,
                "minecraft:follow_range": {"value": follow, "max": follow},
                "minecraft:knockback_resistance": {"value": spec["kb"]},
                "minecraft:navigation.walk": {"can_path_over_water": False, "avoid_water": True,
                                              "can_pass_doors": True},
                "minecraft:movement.basic": {},
                "minecraft:jump.static": {},
                "minecraft:physics": {},
                "minecraft:pushable": {"is_pushable": True, "is_pushable_by_piston": True},
                "minecraft:nameable": {},
                "minecraft:despawn": {"despawn_from_distance": {"min_distance": 96, "max_distance": 128}},
                "minecraft:behavior.float": {"priority": 0},
                "minecraft:behavior.hurt_by_target": {"priority": 1},
                "minecraft:behavior.nearest_attackable_target": {
                    "priority": 2, "must_see": False, "reselect_targets": True,
                    "within_radius": follow,
                    "entity_types": [{"filters": target_family, "max_dist": follow}],
                },
                "minecraft:behavior.melee_attack": {"priority": 3, "speed_multiplier": 1.2,
                                                    "track_target": True},
                "minecraft:behavior.random_stroll": {"priority": 6, "speed_multiplier": 0.6},
                "minecraft:behavior.look_at_player": {"priority": 7, "look_distance": 16},
                "minecraft:behavior.random_look_around": {"priority": 8},
            },
        },
    }


def client_entity(key, spec):
    return {
        "format_version": "1.10.0",
        "minecraft:client_entity": {
            "description": {
                "identifier": f"horror:{key}",
                "materials": {"default": spec["material"]},
                "textures": {"default": f"textures/entity/{key}"},
                "geometry": {"default": f"geometry.horror_{key}"},
                "animations": {"walk": "animation.horror.walk"},
                "scripts": {"animate": ["walk"]},
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
def walk_animation():
    swing = "Math.cos(query.modified_distance_moved * 38.17) * 40.0 * query.modified_move_speed"
    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.horror.walk": {
                "loop": True,
                "bones": {
                    "leg_r": {"rotation": [swing, 0, 0]},
                    "leg_l": {"rotation": [f"-({swing})", 0, 0]},
                    "arm_r": {"rotation": [f"-({swing})", 0, 0]},
                    "arm_l": {"rotation": [swing, 0, 0]},
                    "head": {"rotation": ["query.target_x_rotation", "query.target_y_rotation", 0]},
                },
            }
        },
    }


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


def manifests():
    bp = {
        "format_version": 2,
        "header": {
            "name": "Horror Stalkers [BP]",
            "description": "Terrifying stalking creatures: The Stalker, The Woodbeast and The Whisperer.",
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
            "description": "Models, textures and animations for the Horror Stalkers creatures.",
            "uuid": RP_UUID, "version": VERSION, "min_engine_version": MIN_ENGINE,
        },
        "modules": [{"type": "resources", "uuid": RP_DATA_UUID, "version": VERSION}],
        "dependencies": [{"uuid": BP_UUID, "version": VERSION}],
    }
    write_json(BP / "manifest.json", bp)
    write_json(RP / "manifest.json", rp)


def lang_files():
    languages = sorted({lang for s in CREATURES.values() for lang in s["names"]})
    for lang in languages:
        lines = []
        for key, spec in CREATURES.items():
            name = spec["names"][lang]
            lines.append(f"entity.horror:{key}.name={name}")
            lines.append(f"item.spawn_egg.entity.horror:{key}.name={name}")
        (RP / "texts").mkdir(parents=True, exist_ok=True)
        (RP / "texts" / f"{lang}.lang").write_text("\n".join(lines) + "\n")
    write_json(RP / "texts" / "languages.json", languages)


def main():
    manifests()
    for key, spec in CREATURES.items():
        write_json(BP / "entities" / f"{key}.json", behavior_entity(key, spec))
        write_json(BP / "spawn_rules" / f"{key}.json", spawn_rules(key, spec))
        write_json(RP / "entity" / f"{key}.entity.json", client_entity(key, spec))
        geo = build_geometry(key, spec)
        write_json(RP / "models" / "entity" / f"{key}.geo.json", geo)
        cubes = [c for b in spec["bones"] for c in b["cubes"]]
        write_png(RP / "textures" / "entity" / f"{key}.png", ATLAS, ATLAS,
                  build_texture(key, spec, cubes))
    write_json(RP / "animations" / "horror.animation.json", walk_animation())
    write_json(RP / "render_controllers" / "horror.render_controllers.json", render_controller())
    lang_files()
    build_icon(BP / "pack_icon.png", (20, 8, 8), (255, 40, 20), "bp")
    build_icon(RP / "pack_icon.png", (8, 10, 20), (230, 240, 255), "rp")
    print("assets generated in", ROOT)


if __name__ == "__main__":
    main()
