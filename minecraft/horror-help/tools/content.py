"""Everything in the packs that is data rather than art: entities (server and
client), items, blocks, recipes, fogs, render controllers, texture atlases,
entity sounds and the language files. build.py calls write_all().
"""

import json
from pathlib import Path

from lang import LANG
from models import alternate as alternate_model
from models import strobe as strobe_model

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "behavior_pack"
RP = ROOT / "resource_pack"

VERSION = [1, 0, 0]
UUID = {
    "bp": "5b0e6a39-8d1f-4c27-9f53-2e7a6c41d8b0",
    "bp_data": "c3a91f74-0b56-4e8d-a2f1-7d94e3b60c25",
    "bp_script": "e87d2c10-5f3b-4a69-b14e-9c0a7f2d6e83",
    "rp": "1d64b8e2-a7c9-43f0-8e15-4b2f9d0c7a61",
    "rp_res": "9f2c5a07-3e81-4bd6-a940-6c1e8d7f2b54",
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# =========================================================================== #
# Manifests
# =========================================================================== #

def manifests():
    header = lambda uuid: {"name": "pack.name", "description": "pack.description", "uuid": uuid,
                           "version": VERSION, "min_engine_version": [1, 21, 0]}
    write(BP / "manifest.json", {
        "format_version": 2,
        "header": header(UUID["bp"]),
        "modules": [
            {"type": "data", "uuid": UUID["bp_data"], "version": VERSION},
            {"type": "script", "language": "javascript", "uuid": UUID["bp_script"], "version": VERSION,
             "entry": "scripts/main.js"},
        ],
        "dependencies": [
            {"uuid": UUID["rp"], "version": VERSION},
            {"module_name": "@minecraft/server", "version": "1.11.0"},
            {"module_name": "@minecraft/server-ui", "version": "1.1.0"},
        ],
    })
    write(RP / "manifest.json", {
        "format_version": 2,
        "header": header(UUID["rp"]),
        "modules": [{"type": "resources", "uuid": UUID["rp_res"], "version": VERSION}],
        "dependencies": [{"uuid": UUID["bp"], "version": VERSION}],
    })


# =========================================================================== #
# Entities (behaviour)
# =========================================================================== #
#
# The scripts drive every creature: they choose where it appears and switch
# it between modes. A mode is an entity property the client animates on
# (hh:mode) plus, where the mode changes what the mob does, an event of the
# same name that swaps its component groups (hh:<mode>).

PLAYER = {"test": "is_family", "subject": "other", "value": "player"}
PLAYER_TARGET = [{"filters": PLAYER, "max_dist": 128}]
BREAKABLE = ["minecraft:glass", "minecraft:glass_pane"] + [f"minecraft:{w}_leaves" for w in (
    "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "azalea")] + [
    "minecraft:leaves", "minecraft:leaves2", "minecraft:wooden_door"] + [f"minecraft:{w}_door" for w in (
        "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped")]
LEAVES = [b for b in BREAKABLE if "leaves" in b]


def base_components(families, width, height, health, extra=None):
    c = {
        "minecraft:is_hidden_when_invisible": {},
        "minecraft:physics": {},
        "minecraft:breathable": {"total_supply": 15, "suffocate_time": 0, "breathes_water": True},
        "minecraft:fire_immune": {},
        "minecraft:despawn": {"despawn_from_distance": {"min_distance": 128, "max_distance": 160}},
        "minecraft:conditional_bandwidth_optimization": {},
        "minecraft:type_family": {"family": families},
        "minecraft:collision_box": {"width": width, "height": height},
        "minecraft:health": {"value": health, "max": health},
        "minecraft:movement.basic": {},
        "minecraft:jump.static": {},
        "minecraft:loot": {"table": "loot_tables/empty.json"},
    }
    c.update(extra or {})
    return c


INVULNERABLE = {"minecraft:damage_sensor": {"triggers": [{"cause": "all", "deals_damage": False}]}}
NO_FALL = {"minecraft:damage_sensor": {"triggers": [
    {"cause": "fall", "deals_damage": False}, {"cause": "suffocation", "deals_damage": False},
    {"cause": "drowning", "deals_damage": False}, {"cause": "lightning", "deals_damage": False}]}}
HEAVY = {"minecraft:knockback_resistance": {"value": 1.0},
         "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False}}
VANISH_GROUP = {"hh:vanish": {"minecraft:instant_despawn": {}}}
VANISH_EVENT = {"hh:vanish": {"add": {"component_groups": ["hh:vanish"]}}}


def look_at_player(priority=0, dist=96):
    return {"minecraft:behavior.look_at_player": {"priority": priority, "look_distance": dist, "probability": 1.0,
                                                  "angle_of_view_horizontal": 360}}


STILL = {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}, **HEAVY, **look_at_player()}


def walk(**kw):
    return {"minecraft:navigation.walk": {"can_path_over_water": True, "avoid_water": True,
                                          "avoid_damage_blocks": True, **kw}}


def hunt(radius, damage, speed=1.0):
    return {
        "minecraft:attack": {"damage": damage},
        "minecraft:follow_range": {"value": radius, "max": radius},
        "minecraft:behavior.nearest_attackable_target": {"priority": 1, "must_see": False, "reselect_targets": True,
                                                         "within_radius": float(radius), "entity_types": PLAYER_TARGET},
        "minecraft:behavior.melee_attack": {"priority": 3, "speed_multiplier": speed, "track_target": True},
    }


def avoid(dist=48):
    return {"minecraft:behavior.avoid_mob_type": {"priority": 1, "entity_types": [
        {"filters": PLAYER, "max_dist": dist, "walk_speed_multiplier": 1.2, "sprint_speed_multiplier": 1.5}]}}


def entity(identifier, components, groups=None, events=None, properties=None, start=None):
    desc = {"identifier": identifier, "is_spawnable": True, "is_summonable": True, "is_experimental": False}
    if properties:
        desc["properties"] = properties
    events = {**VANISH_EVENT, **(events or {})}
    if start:
        events["minecraft:entity_spawned"] = {"add": {"component_groups": [start]}}
    return {"format_version": "1.20.50", "minecraft:entity": {
        "description": desc,
        "component_groups": {**VANISH_GROUP, **(groups or {})},
        "components": components,
        "events": events,
    }}


def modes(groups):
    """One event per mode group, each removing the others."""
    names = list(groups)
    return {f"hh:{n}": {"remove": {"component_groups": [f"hh:{o}" for o in names if o != n]},
                        "add": {"component_groups": [f"hh:{n}"]}} for n in names}, \
        {f"hh:{n}": g for n, g in groups.items()}


def mode_property(*values):
    return {"hh:mode": {"type": "enum", "values": list(values), "default": values[0], "client_sync": True}}


def flag(name):
    return {name: {"type": "bool", "default": False, "client_sync": True}}


def moded(identifier, families, size, health, components, groups, extra_props=None):
    events, gs = modes(groups)
    fam = lambda: {"minecraft:type_family": {"family": families}}
    return entity(identifier, base_components(families, size[0], size[1], health, components),
                  groups={n: {**g, **fam()} for n, g in gs.items()}, events=events,
                  properties={**mode_property(*groups), **(extra_props or {})}, start=f"hh:{next(iter(groups))}")


def entities():
    out = {}
    still = lambda ident, w, h, extra=None, props=None: entity(ident, base_components(
        [ident.replace(":", "_"), "hh_haunt"], w, h, 100, {**INVULNERABLE, **STILL, **(extra or {})}), properties=props)
    floating = {"minecraft:physics": {"has_gravity": False, "has_collision": False}}
    # --- the ones the scripts move ---------------------------------------- #
    # The Mandela Catalogue: the Intruder, and Gabriel.
    out["intruder"] = still("hh:intruder", 0.7, 2.8, props=flag("hh:stare"))
    out["gabriel"] = still("hh:gabriel", 1.0, 4.0, floating, props=flag("hh:open"))
    # Local 58: the thing that isn't the moon.
    out["false_moon"] = still("hh:false_moon", 0.5, 0.5, floating, props=flag("hh:face"))
    # The Whistle Occurrence: Flowers.
    out["flowers"] = still("hh:flowers", 0.7, 4.0, props=flag("hh:stretch"))
    # The Sun Vanished: the Strobes.
    out["strobe"] = still("hh:strobe", 1.2, 1.2, floating, props={
        "hh:light": {"type": "int", "range": [0, 2], "default": 0, "client_sync": True}})

    # --- the ones that hunt --------------------------------------------- #
    body = {"minecraft:knockback_resistance": {"value": 0.6}, "minecraft:behavior.float": {"priority": 0},
            "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": True}}
    out["alternate"] = moded("hh:alternate", ["hh_alternate", "monster", "mob"], (0.6, 1.9), 40, {
        **body, **look_at_player(6, 64)}, {
        "disguise": {**walk(can_open_doors=True), "minecraft:movement": {"value": 0.2}, **INVULNERABLE,
                     "minecraft:behavior.move_towards_target": {"priority": 3, "speed_multiplier": 0.8,
                                                                "within_radius": 48.0},
                     "minecraft:behavior.nearest_attackable_target": {
                         "priority": 2, "must_see": False, "within_radius": 48.0, "entity_types": PLAYER_TARGET}},
        "hunt": {**walk(can_open_doors=True), "minecraft:movement": {"value": 0.42}, **hunt(64, 7, 1.25)}},
        extra_props={**flag("hh:revealed"),
                     "hh:variant": {"type": "int", "range": [0, 2], "default": 0, "client_sync": True}})
    out["siren_head"] = moded("hh:siren_head", ["hh_siren_head", "monster", "mob"], (1.0, 5.5), 300, {
        **INVULNERABLE, **HEAVY, **look_at_player(6, 128)}, {
        "idle": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}},
        "hunt": {**walk(), "minecraft:movement": {"value": 0.42}, **hunt(128, 14, 1.2),
                 "minecraft:break_blocks": {"breakable_blocks": LEAVES}}})
    out["cartoon_cat"] = moded("hh:cartoon_cat", ["hh_cartoon_cat", "monster", "mob"], (0.8, 3.6), 200, {
        **body, **INVULNERABLE, **look_at_player(6, 96)}, {
        "stalk": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}},
        "chase": {"minecraft:movement": {"value": 0.52}, **hunt(96, 9, 1.3), "minecraft:can_climb": {},
                  "minecraft:navigation.climb": {"can_path_over_water": True, "avoid_water": True,
                                                 "can_pass_doors": True, "can_break_doors": True},
                  "minecraft:break_blocks": {"breakable_blocks": BREAKABLE},
                  "minecraft:behavior.leap_at_target": {"priority": 2, "yd": 0.5, "must_be_on_ground": True}}})

    for name, data in out.items():
        write(BP / "entities" / f"{name}.json", data)


MOVING = "query.modified_move_speed > 0.05"
ATTACK = "variable.attack_time > 0.0"
HURT = "query.hurt_time > 0"


def mode_is(m):
    return f"query.property('hh:mode') == '{m}'"


# name: (textures, animations {short: full}, animate list, controller, egg colours)
CLIENTS = {
    # name: (textures, animations, animate, render controller, egg colours)
    "intruder": ({"default": "intruder"},
                 {"look": "intruder.look", "idle": "intruder.idle", "stare": "intruder.stare"},
                 ["look", {"idle": "!query.property('hh:stare')"}, {"stare": "query.property('hh:stare')"}],
                 "default", ("#c4c2c0", "#060608")),
    "alternate": ({"default": "alternate", "v1": "alternate_1", "v2": "alternate_2"},
                  {"look": "alternate.look", "idle": "alternate.idle", "walk": "alternate.walk",
                   "reveal": "alternate.reveal", "attack": "alternate.attack"},
                  ["look", "idle", {"walk": MOVING}, {"reveal": "query.property('hh:revealed')"}, {"attack": ATTACK}],
                  "alternate", ("#8e3a34", "#2a3a56")),
    "gabriel": ({"default": "gabriel"},
                {"float": "gabriel.float", "look": "gabriel.look", "open": "gabriel.open"},
                ["float", "look", {"open": "query.property('hh:open')"}],
                "default", ("#e0dbca", "#ecc232")),
    "false_moon": ({"default": "false_moon"}, {"hang": "moon.hang"}, ["hang"],
                   "false_moon", ("#b4b4b4", "#000000")),
    "flowers": ({"default": "flowers"},
                {"look": "flowers.look", "idle": "flowers.idle", "stretch": "flowers.stretch"},
                ["look", "idle", {"stretch": "query.property('hh:stretch')"}],
                "default", ("#08080b", "#f800f8")),
    "strobe": ({"default": "strobe"}, {"hover": "strobe.hover", "look": "strobe.look"}, ["hover", "look"],
               "strobe", ("#141418", "#ff1010")),
    "siren_head": ({"default": "siren_head"},
                   {"look": "siren.look", "idle": "siren.idle", "walk": "siren.walk", "hunt": "siren.hunt",
                    "attack": "siren.attack"},
                   ["look", "idle", {"walk": MOVING}, {"hunt": mode_is("hunt")}, {"attack": ATTACK}],
                   "default", ("#54402e", "#a05c28")),
    "cartoon_cat": ({"default": "cartoon_cat"},
                    {"look": "cat.look", "idle": "cat.idle", "run": "cat.run", "attack": "cat.attack"},
                    ["look", "idle", {"run": MOVING}, {"attack": ATTACK}],
                    "default", ("#08080a", "#f2f0e8")),
}


def clients():
    for name, (textures, anims, animate, controller, egg) in CLIENTS.items():
        write(RP / "entity" / f"{name}.entity.json", {"format_version": "1.10.0", "minecraft:client_entity": {
            "description": {
                "identifier": f"hh:{name}",
                "materials": {"default": "entity_emissive_alpha"},
                "textures": {k: f"textures/entity/hh/{v}" for k, v in textures.items()},
                "geometry": {"default": f"geometry.hh.{name}"},
                "animations": {k: f"animation.hh.{v}" for k, v in anims.items()},
                "scripts": {"animate": animate},
                "render_controllers": [f"controller.render.hh.{controller}"],
                "spawn_egg": {"base_color": egg[0], "overlay_color": egg[1]},
            }}})

    material = [{"*": "Material.default"}]
    rc = lambda **kw: {"geometry": "Geometry.default", "materials": material, "textures": ["Texture.default"], **kw}
    write(RP / "render_controllers" / "hh.render_controllers.json", {"format_version": "1.8.0", "render_controllers": {
        "controller.render.hh.default": rc(),
        # The neighbour, or what is wearing its face.
        "controller.render.hh.alternate": {
            "geometry": "Geometry.default", "materials": material,
            "arrays": {"textures": {"Array.skins": ["Texture.default", "Texture.v1", "Texture.v2"]}},
            "textures": ["Array.skins[query.property('hh:variant')]"],
            "part_visibility": [
                {"*": True},
                *[{b: "!query.property('hh:revealed')"} for b in alternate_model.DISGUISE],
                *[{b: "query.property('hh:revealed')"} for b in alternate_model.TRUE]]},
        # The moon's face only comes when it has seen you.
        "controller.render.hh.false_moon": rc(part_visibility=[{"*": True}, {"face": "query.property('hh:face')"}]),
        # One lamp colour at a time, strobing: white fast, red throbbing, blue steady.
        "controller.render.hh.strobe": rc(part_visibility=[
            {"*": True},
            {"white": "query.property('hh:light') == 0 && math.mod(math.floor(query.life_time * 12.0), 2) == 0"},
            {"red": "query.property('hh:light') == 1 && math.mod(math.floor(query.life_time * 4.0), 3) != 0"},
            {"blue": "query.property('hh:light') == 2"}]),
    }})

    write(RP / "sounds.json", {"entity_sounds": {"entities": {
        "hh:alternate": {"volume": 1.0, "pitch": [0.6, 0.7], "events": {"hurt": "mob.zombie.hurt", "death": "mob.zombie.death"}},
    }}})


# =========================================================================== #
# Items, blocks, recipes
# =========================================================================== #

def item(identifier, icon, category="items", **components):
    c = {"minecraft:icon": {"texture": icon}, "minecraft:display_name": {"value": f"item.{identifier}.name"}}
    c.update(components)
    return {"format_version": "1.20.80", "minecraft:item": {
        "description": {"identifier": identifier, "menu_category": {"category": category}}, "components": c}}




def items():
    stack1 = {"minecraft:max_stack_size": 1}
    write(BP / "items" / "journal.json", item("hh:journal", "hh_journal", **stack1))
    write(BP / "items" / "flashlight.json", item("hh:flashlight", "hh_flashlight", "equipment", **stack1))
    write(BP / "items" / "battery.json", item("hh:battery", "hh_battery", **{"minecraft:max_stack_size": 16}))
    # The emergency radio: what is out there tonight, if anyone is still broadcasting.
    write(BP / "items" / "radio.json", item("hh:radio", "hh_radio", "equipment", **stack1))
    box = {"origin": [-4, 0, -4], "size": [8, 11, 8]}
    write(BP / "blocks" / "ward_lantern.json", {"format_version": "1.20.80", "minecraft:block": {
        "description": {"identifier": "hh:ward_lantern", "menu_category": {"category": "items"}},
        "components": {
            "minecraft:geometry": "geometry.hh.ward_lantern",
            "minecraft:material_instances": {"*": {"texture": "hh_ward_lantern", "render_method": "alpha_test"}},
            "minecraft:light_emission": 15,
            "minecraft:light_dampening": 0,
            "minecraft:collision_box": box,
            "minecraft:selection_box": box,
            "minecraft:destructible_by_mining": {"seconds_to_destroy": 0.6},
            "minecraft:destructible_by_explosion": {"explosion_resistance": 3},
            "minecraft:map_color": "#b48cff"}}})
    write(BP / "loot_tables" / "empty.json", {"pools": []})

    shaped = lambda ident, pattern, key, result, count=1: {"format_version": "1.20.10", "minecraft:recipe_shaped": {
        "description": {"identifier": ident}, "tags": ["crafting_table"], "pattern": pattern,
        "key": {k: {"item": v} for k, v in key.items()}, "result": {"item": result, "count": count}}}
    shapeless = lambda ident, ingredients, result, count=1: {"format_version": "1.20.10", "minecraft:recipe_shapeless": {
        "description": {"identifier": ident}, "tags": ["crafting_table"],
        "ingredients": [{"item": i} for i in ingredients], "result": {"item": result, "count": count}}}
    write(BP / "recipes" / "journal.json", shapeless("hh:journal", ["minecraft:book", "minecraft:charcoal"], "hh:journal"))
    write(BP / "recipes" / "flashlight.json", shaped(
        "hh:flashlight", ["IGI", " R ", " I "],
        {"I": "minecraft:iron_ingot", "G": "minecraft:glowstone_dust", "R": "minecraft:redstone"}, "hh:flashlight"))
    write(BP / "recipes" / "battery.json", shapeless(
        "hh:battery", ["minecraft:redstone", "minecraft:iron_nugget", "minecraft:copper_ingot"], "hh:battery", 2))
    write(BP / "recipes" / "radio.json", shaped(
        "hh:radio", ["  I", "PRP", "PCP"],
        {"I": "minecraft:iron_ingot", "P": "minecraft:iron_nugget", "R": "minecraft:redstone",
         "C": "minecraft:copper_ingot"}, "hh:radio"))
    write(BP / "recipes" / "ward_lantern.json", shaped(
        "hh:ward_lantern", [" A ", "ALA", " A "], {"A": "minecraft:amethyst_shard", "L": "minecraft:lantern"},
        "hh:ward_lantern"))

    write(RP / "blocks.json", {"format_version": [1, 1, 0],
                               "hh:ward_lantern": {"sound": "lantern"}})
    write(RP / "textures" / "terrain_texture.json", {
        "resource_pack_name": "horror_help", "texture_name": "atlas.terrain", "padding": 8, "num_mip_levels": 4,
        "texture_data": {"hh_ward_lantern": {"textures": "textures/blocks/hh/ward_lantern"}}})
    icons = ["journal", "flashlight", "battery", "radio"]
    write(RP / "textures" / "item_texture.json", {
        "resource_pack_name": "horror_help", "texture_name": "atlas.items",
        "texture_data": {f"hh_{i}": {"textures": f"textures/items/hh/{i}"} for i in icons}})


def ward_lantern_geometry():
    """The Ward Lantern as a block model on a 16x16 texture: an iron base and
    cap, glass sides with an amethyst crystal glowing inside, a handle."""
    def cube(origin, size, faces):
        uv = {}
        for face, (u, v, w, h) in faces.items():
            uv[face] = {"uv": [u, v], "uv_size": [w, h]}
        return {"origin": origin, "size": size, "uv": uv}

    iron_top = (0, 0, 8, 8)
    iron_side = (8, 0, 8, 1)
    glass = (0, 8, 6, 7)
    glass_top = (6, 8, 6, 6)
    crystal = (12, 8, 2, 5)
    crystal_top = (14, 8, 2, 2)
    knob_top = (8, 2, 4, 4)
    knob_side = (8, 6, 4, 2)
    handle = (12, 2, 4, 1)
    sides = lambda f: {k: f for k in ("north", "south", "east", "west")}
    bones = [{"name": "lantern", "pivot": [0, 0, 0], "cubes": [
        cube([-4, 0, -4], [8, 1, 8], {**sides(iron_side), "up": iron_top, "down": iron_top}),
        cube([-3, 1, -3], [6, 7, 6], {**sides(glass), "up": glass_top, "down": glass_top}),
        cube([-1, 2, -1], [2, 5, 2], {**sides(crystal), "up": crystal_top, "down": crystal_top}),
        cube([-4, 8, -4], [8, 1, 8], {**sides(iron_side), "up": iron_top, "down": iron_top}),
        cube([-2, 9, -2], [4, 2, 4], {**sides(knob_side), "up": knob_top, "down": knob_top}),
        cube([-2, 11, -0.5], [4, 1, 1], {**sides(handle), "up": handle, "down": handle}),
    ]}]
    write(RP / "models" / "blocks" / "ward_lantern.geo.json", {
        "format_version": "1.16.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.hh.ward_lantern", "texture_width": 16, "texture_height": 16,
                            "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
            "bones": bones}]})


def fogs():
    def fog(identifier, start, end, color):
        return {"format_version": "1.16.100", "minecraft:fog_settings": {
            "description": {"identifier": identifier},
            "distance": {"air": {"fog_start": start, "fog_end": end, "fog_color": color,
                                 "render_distance_type": "fixed"}}}}
    # When the sun has vanished: a black night that swallows the distance.
    write(RP / "fogs" / "hh_dark.json", fog("hh:dark", 4, 36, "#020205"))


# =========================================================================== #
# Language
# =========================================================================== #

def lang_value(text):
    return text.replace("\n", "\\n")


def langs():
    bp_keys = ("pack.name", "pack.description")
    for code, i in (("es_ES", 0), ("es_MX", 0), ("en_US", 1), ("en_GB", 1)):
        lines = [f"{k}={lang_value(v[i])}" for k, v in LANG.items()]
        (RP / "texts" / f"{code}.lang").write_text("\n".join(lines) + "\n", encoding="utf-8")
        (BP / "texts" / f"{code}.lang").write_text(
            "\n".join(f"{k}={lang_value(LANG[k][i])}" for k in bp_keys) + "\n", encoding="utf-8")
    for pack in (RP, BP):
        write(pack / "texts" / "languages.json", ["en_US", "en_GB", "es_ES", "es_MX"])


def write_all():
    manifests()
    entities()
    clients()
    items()
    ward_lantern_geometry()
    fogs()
    langs()
