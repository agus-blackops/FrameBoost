#!/usr/bin/env python3
"""Generate the data-driven files of the addon from one creature table.

Writes manifests, entity behaviour/client files, spawn rules, loot tables,
geometry, textures, animations, sounds, fog, render controller, language
files and pack icons. Run from anywhere: `python3 tools/generate_assets.py`.
"""
import copy
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
VERSION = [1, 1, 0]
MIN_ENGINE = [1, 21, 0]

ATLAS = 128  # every creature texture is ATLAS x ATLAS
BONE_WHITE = (205, 198, 180)
# Pixels with alpha 0 glow under entity_emissive_alpha.
EMISSIVE = "entity_emissive_alpha"


def cube(origin, size, **paint):
    """A model cube. `paint` keys: eyes, teeth, ribs, color, fade (bottom alpha)."""
    return {"origin": origin, "size": size, **paint}


STALKER_BONES = [
    {"name": "root", "pivot": [0, 0, 0], "cubes": []},
    {"name": "leg_r", "parent": "root", "pivot": [-2, 20, 0], "cubes": [cube([-3, 0, -1], [2, 20, 2])]},
    {"name": "leg_l", "parent": "root", "pivot": [2, 20, 0], "cubes": [cube([1, 0, -1], [2, 20, 2])]},
    {"name": "body", "parent": "root", "pivot": [0, 20, 0],
     "cubes": [cube([-3, 20, -1.5], [6, 16, 3], ribs=True),
               cube([-1, 24, 1.5], [2, 10, 1])]},  # spine ridge
    {"name": "arm_r", "parent": "root", "pivot": [-4.5, 36, 0],
     "cubes": [cube([-5.5, 8, -1], [2, 28, 2]),
               cube([-5.5, 2, -1], [1, 6, 1], color=BONE_WHITE),   # claw fingers
               cube([-4.5, 3, 0], [1, 5, 1], color=BONE_WHITE)]},
    {"name": "arm_l", "parent": "root", "pivot": [4.5, 36, 0],
     "cubes": [cube([3.5, 8, -1], [2, 28, 2]),
               cube([4.5, 2, -1], [1, 6, 1], color=BONE_WHITE),
               cube([3.5, 3, 0], [1, 5, 1], color=BONE_WHITE)]},
    {"name": "head", "parent": "root", "pivot": [0, 36, 0], "rotation": [12, 0, 0],
     "cubes": [cube([-1, 36, -1], [2, 3, 2]),                       # neck
               cube([-2.5, 39, -3.5], [5, 8, 5], eyes=True, teeth=True)]},
]

CREATURES = {
    "stalker": {
        "names": {"en_US": "The Stalker", "es_ES": "El Acechador"},
        "base": (24, 22, 26), "accent": (70, 8, 8), "eye": (255, 40, 20), "alpha": 255,
        "material": EMISSIVE, "egg": ("#0b0b0d", "#b01010"),
        "health": 60, "speed": 0.26, "damage": 8, "follow": 64, "kb": 0.6,
        "width": 0.7, "height": 2.9, "xp": 15,
        "animate": ["walk", "idle", "look_at", "twitch"],
        "sounds": {"pitch": [0.45, 0.6], "ambient": "mob.enderman.idle",
                   "hurt": "mob.enderman.hit", "death": "mob.enderman.death"},
        "loot": [
            {"rolls": 1, "entries": [{"type": "item", "name": "minecraft:ender_pearl", "weight": 1,
                                      "functions": [{"function": "set_count", "count": {"min": 0, "max": 2}}]}]},
            {"rolls": 1, "conditions": [{"condition": "killed_by_player"},
                                        {"condition": "random_chance", "chance": 0.15}],
             "entries": [{"type": "item", "name": "minecraft:echo_shard", "weight": 1}]},
        ],
        "bones": STALKER_BONES,
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
        "material": EMISSIVE, "egg": ("#2a1c12", "#ffbe1e"),
        "health": 40, "speed": 0.42, "damage": 6, "follow": 48, "kb": 0.3,
        "width": 1.0, "height": 1.9, "xp": 10,
        "animate": ["walk", "look_at", "tail"],
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
        "bones": [
            {"name": "root", "pivot": [0, 0, 0], "cubes": []},
            {"name": "body", "parent": "root", "pivot": [0, 12, 0],
             "cubes": [cube([-4, 12, -7], [8, 8, 14], ribs=True),
                       cube([-1, 20, -6], [2, 2, 3], color=BONE_WHITE),   # spine spikes
                       cube([-1, 20, -1], [2, 3, 3], color=BONE_WHITE),
                       cube([-1, 20, 4], [2, 2, 3], color=BONE_WHITE)]},
            {"name": "head", "parent": "root", "pivot": [0, 17, -7],
             "cubes": [cube([-3, 15, -13], [6, 6, 6], eyes=True, color=BONE_WHITE),  # skull
                       cube([-2, 14, -16], [4, 3, 3], teeth=True, color=BONE_WHITE),  # snout
                       cube([-4, 21, -11], [1, 7, 1], color=(90, 78, 66)),            # antlers
                       cube([-6, 25, -11], [2, 1, 1], color=(90, 78, 66)),
                       cube([-6, 26, -11], [1, 3, 1], color=(90, 78, 66)),
                       cube([3, 21, -11], [1, 7, 1], color=(90, 78, 66)),
                       cube([4, 25, -11], [2, 1, 1], color=(90, 78, 66)),
                       cube([5, 26, -11], [1, 3, 1], color=(90, 78, 66))]},
            {"name": "tail", "parent": "body", "pivot": [0, 18, 7],
             "cubes": [cube([-1, 16, 7], [2, 2, 10])]},
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
        "width": 0.7, "height": 2.6, "xp": 10,
        "effect": ("blindness", 6),
        "animate": ["walk", "idle", "look_at", "float", "twitch"],
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
        "bones": [
            {"name": "root", "pivot": [0, 0, 0], "cubes": []},
            {"name": "body", "parent": "root", "pivot": [0, 8, 0],
             "cubes": [cube([-4, 10, -2], [8, 22, 4]),
                       cube([-5, 2, -3], [10, 8, 6], fade=70),          # wide robe hem
                       cube([-4, 0, -3], [1, 2, 1], fade=30),           # tattered strips
                       cube([-1, 0, -3], [1, 2, 1], fade=30),
                       cube([2, 0, -3], [1, 2, 1], fade=30)]},
            {"name": "arm_r", "parent": "root", "pivot": [-5, 30, 0],
             "cubes": [cube([-7, 6, -1], [2, 24, 2]),
                       cube([-7, 1, -1], [1, 5, 1], color=BONE_WHITE),
                       cube([-6, 2, 0], [1, 4, 1], color=BONE_WHITE)]},
            {"name": "arm_l", "parent": "root", "pivot": [5, 30, 0],
             "cubes": [cube([5, 6, -1], [2, 24, 2]),
                       cube([6, 1, -1], [1, 5, 1], color=BONE_WHITE),
                       cube([5, 2, 0], [1, 4, 1], color=BONE_WHITE)]},
            {"name": "head", "parent": "root", "pivot": [0, 32, 0],
             "cubes": [cube([-3, 32, -3], [6, 6, 6], eyes=True, color=(16, 16, 22)),  # void face
                       cube([-4, 31, -2], [8, 9, 6]),                                 # hood
                       cube([-4, 38, -4], [8, 3, 2])]},                               # hood brim
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
        "base": (6, 6, 8), "accent": (2, 2, 3), "eye": (250, 250, 250), "alpha": 255,
        "material": EMISSIVE, "egg": ("#050506", "#fafafa"),
        "width": 0.7, "height": 2.9,
        "animate": ["look_at", "twitch"],
        "passive": True,
        "bones": copy.deepcopy(STALKER_BONES),
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


def clamp(v):
    return max(0, min(255, int(v)))


def all_cubes(spec):
    return [c for b in spec["bones"] for c in b["cubes"]]


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
    pack_uvs(all_cubes(spec))
    bones = []
    for b in spec["bones"]:
        bone = {k: b[k] for k in ("name", "parent", "pivot", "rotation") if k in b}
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
def build_texture(key, spec):
    rng = random.Random(key)
    accent, alpha = spec["accent"], spec["alpha"]
    emissive_eyes = spec["material"] == EMISSIVE
    px = [[(0, 0, 0, 0)] * ATLAS for _ in range(ATLAS)]
    for c in all_cubes(spec):
        w, h, d = (int(v) for v in c["size"])
        u, v = c["uv"]
        base = c.get("color", spec["base"])
        streaks = [rng.randint(-14, 14) for _ in range(2 * (w + d))]
        for yy in range(v, v + h + d):
            # Optional alpha fade toward the bottom of the side faces (ragged hems).
            a = alpha
            if "fade" in c and yy >= v + d and h > 1:
                t = (yy - v - d) / (h - 1)
                a = int(alpha + (c["fade"] - alpha) * t)
            for xx in range(u, u + 2 * (w + d)):
                n = rng.randint(-10, 10) + streaks[xx - u]
                col = tuple(clamp(base[i] + n) for i in range(3))
                if rng.random() < 0.07:
                    col = tuple(clamp(accent[i] + rng.randint(-6, 6)) for i in range(3))
                px[yy][xx] = (*col, a)
        fx, fy = u + d, v + d  # front (-Z) face origin
        if c.get("ribs"):
            for ry in range(fy + 2, fy + h - 1, 3):
                for rx in range(fx + 1, fx + w - 1):
                    px[ry][rx] = (*(clamp(ch * 0.4) for ch in base), alpha)
        if c.get("teeth"):
            ty = fy + h - 2
            for tx in range(fx + 1, fx + w - 1):
                tooth = (tx - fx) % 2 == 1
                px[ty][tx] = (*(BONE_WHITE if tooth else (10, 4, 4)), 255)
        if c.get("eyes"):
            ey = fy + max(1, h // 3)
            eye_alpha = 0 if emissive_eyes else 255
            tall = h >= 8
            for ex in (fx + 1, fx + w - 2):
                px[ey][ex] = (*spec["eye"], eye_alpha)
                if tall:
                    px[ey + 1][ex] = (*spec["eye"], eye_alpha)
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
    for y in range(36, 128):  # tall thin silhouette
        half = 10 if y < 64 else 7
        for x in range(64 - half, 64 + half):
            rows[y][x] = (4, 4, 5, 255)
    for ex in (56, 68):  # two glaring eyes
        for y in range(46, 52):
            for x in range(ex, ex + 4):
                rows[y][x] = (*fg, 255)
    write_png(path, size, size, rows)


# --------------------------------------------------------------- entities
def base_components(key, spec):
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
        **base_components(key, spec),
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
    return entity_file(key, components)


def passive_entity(key, spec):
    components = {
        **base_components(key, spec),
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


def entity_file(key, components, vanish=False):
    entity = {
        "description": {
            "identifier": f"horror:{key}",
            "is_spawnable": True,
            "is_summonable": True,
            "is_experimental": False,
        },
        "components": components,
    }
    if vanish:
        entity["component_groups"] = {"horror:despawn": {"minecraft:instant_despawn": {}}}
        entity["events"] = {"horror:vanish": {"add": {"component_groups": ["horror:despawn"]}}}
    return {"format_version": "1.21.0", "minecraft:entity": entity}


def client_entity(key, spec):
    return {
        "format_version": "1.10.0",
        "minecraft:client_entity": {
            "description": {
                "identifier": f"horror:{key}",
                "materials": {"default": spec["material"]},
                "textures": {"default": f"textures/entity/{key}"},
                "geometry": {"default": f"geometry.horror_{key}"},
                "animations": {name: f"animation.horror.{name}" for name in spec["animate"]},
                "scripts": {"animate": spec["animate"]},
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
def animations():
    swing = "math.cos(query.modified_distance_moved * 38.17) * 40.0 * query.modified_move_speed"
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
                },
            },
            "animation.horror.look_at": {
                "loop": True,
                "bones": {"head": {"rotation": ["query.target_x_rotation", "query.target_y_rotation", 0]}},
            },
            # Slow, uneasy sway of the long arms.
            "animation.horror.idle": {
                "loop": True,
                "bones": {
                    "arm_r": {"rotation": ["math.sin(query.life_time * 60) * 3", 0,
                                           "math.sin(query.life_time * 45) * 2 + 3"]},
                    "arm_l": {"rotation": ["-math.sin(query.life_time * 60) * 3", 0,
                                           "-math.sin(query.life_time * 45) * 2 - 3"]},
                },
            },
            # A violent head jerk for a quarter second every five seconds.
            "animation.horror.twitch": {
                "loop": True,
                "bones": {"head": {"rotation": [
                    0, 0, "math.mod(query.life_time, 5) < 0.25 ? math.sin(query.life_time * 3000) * 18 : 0"]}},
            },
            "animation.horror.float": {
                "loop": True,
                "bones": {"root": {"position": [0, "math.sin(query.life_time * 90) * 1.5 + 1.5", 0]}},
            },
            "animation.horror.tail": {
                "loop": True,
                "bones": {"tail": {"rotation": ["math.sin(query.life_time * 120) * 8",
                                                "math.sin(query.life_time * 80) * 20", 0]}},
            },
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


def main():
    manifests()
    for key, spec in CREATURES.items():
        if spec.get("passive"):
            write_json(BP / "entities" / f"{key}.json", passive_entity(key, spec))
        else:
            write_json(BP / "entities" / f"{key}.json", hostile_entity(key, spec))
            write_json(BP / "spawn_rules" / f"{key}.json", spawn_rules(key, spec))
            write_json(BP / "loot_tables" / "entities" / f"horror_{key}.json", {"pools": spec["loot"]})
        write_json(RP / "entity" / f"{key}.entity.json", client_entity(key, spec))
        write_json(RP / "models" / "entity" / f"{key}.geo.json", build_geometry(key, spec))
        write_png(RP / "textures" / "entity" / f"{key}.png", ATLAS, ATLAS, build_texture(key, spec))
    write_json(RP / "animations" / "horror.animation.json", animations())
    write_json(RP / "render_controllers" / "horror.render_controllers.json", render_controller())
    write_json(RP / "sounds.json", sounds())
    write_json(RP / "fogs" / "blood_fog.json", blood_fog())
    lang_files()
    build_icon(BP / "pack_icon.png", (20, 8, 8), (255, 40, 20), "bp")
    build_icon(RP / "pack_icon.png", (8, 10, 20), (230, 240, 255), "rp")
    print("assets generated in", ROOT)


if __name__ == "__main__":
    main()
