"""Everything in the packs that is data rather than art: entities (server and
client), items, blocks, recipes, fogs, render controllers, texture atlases,
entity sounds and the language files. build.py calls write_all().
"""

import json
from pathlib import Path

from lang import LANG

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "behavior_pack"
RP = ROOT / "resource_pack"

VERSION = [6, 0, 0]
UUID = {
    "bp": "95cc20cd-1272-4a55-8a97-f2b1dfd18e3f",
    "bp_data": "71d0f013-b17b-4635-843d-5f11ba161bba",
    "bp_script": "f5f688e4-5e67-4aa8-b2b1-6f1b1cbdb1e0",
    "rp": "d3e97fe4-9ba2-4c1e-87f5-b8766ad714c2",
    "rp_res": "44ab4806-2cd9-4435-8d36-f6fcad967d06",
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

PLAYER_TARGET = [{"filters": {"test": "is_family", "subject": "other", "value": "player"}, "max_dist": 96}]
BREAKABLE = ["minecraft:glass", "minecraft:glass_pane"] + [f"minecraft:{w}_leaves" for w in (
    "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "azalea")] + [
    "minecraft:leaves", "minecraft:leaves2", "minecraft:wooden_door"] + [f"minecraft:{w}_door" for w in (
        "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped")]


def base_components(families, width, height, health, extra=None):
    c = {
        "minecraft:is_hidden_when_invisible": {},
        "minecraft:physics": {},
        "minecraft:breathable": {"total_supply": 15, "suffocate_time": 0, "breathes_water": True},
        "minecraft:fire_immune": {},
        "minecraft:despawn": {"despawn_from_distance": {"min_distance": 96, "max_distance": 128}},
        "minecraft:conditional_bandwidth_optimization": {},
        "minecraft:type_family": {"family": families},
        "minecraft:collision_box": {"width": width, "height": height},
        "minecraft:health": {"value": health, "max": health},
        "minecraft:movement.basic": {},
        "minecraft:loot": {"table": "loot_tables/empty.json"},
    }
    c.update(extra or {})
    return c


INVULNERABLE = {"minecraft:damage_sensor": {"triggers": [{"cause": "all", "deals_damage": False}]}}
VANISH_GROUP = {"hl:vanish": {"minecraft:instant_despawn": {}}}
VANISH_EVENT = {"hl:vanish": {"add": {"component_groups": ["hl:vanish"]}}}


def entity(identifier, components, groups=None, events=None, properties=None):
    desc = {"identifier": identifier, "is_spawnable": True, "is_summonable": True, "is_experimental": False}
    if properties:
        desc["properties"] = properties
    return {"format_version": "1.20.50", "minecraft:entity": {
        "description": desc,
        "component_groups": {**VANISH_GROUP, **(groups or {})},
        "components": components,
        "events": {**VANISH_EVENT, **(events or {})},
    }}


def swap(add, remove):
    return {"remove": {"component_groups": remove}, "add": {"component_groups": [add]}}


def entities():
    still = {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {},
             "minecraft:knockback_resistance": {"value": 1.0},
             "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
             "minecraft:behavior.look_at_player": {"priority": 0, "look_distance": 96, "probability": 1.0,
                                                   "angle_of_view_horizontal": 360}}
    write(BP / "entities" / "herobrine.json", entity(
        "hl:herobrine", base_components(["hl_herobrine", "hl_haunt"], 0.6, 2.7, 100, {**INVULNERABLE, **still})))
    write(BP / "entities" / "null.json", entity(
        "hl:null", base_components(["hl_null", "hl_haunt"], 0.6, 1.8, 100, {**INVULNERABLE, **still})))

    walk = lambda **kw: {"minecraft:navigation.walk": {"can_path_over_water": True, "avoid_water": True,
                                                       "avoid_damage_blocks": True, **kw}}
    hunt = lambda radius, damage: {
        "minecraft:attack": {"damage": damage},
        "minecraft:behavior.nearest_attackable_target": {"priority": 1, "must_see": False, "reselect_targets": True,
                                                         "within_radius": float(radius), "entity_types": PLAYER_TARGET},
        "minecraft:behavior.melee_attack": {"priority": 3, "speed_multiplier": 1.0, "track_target": True},
    }
    fam = lambda *f: {"minecraft:type_family": {"family": list(f) + ["monster", "mob"]}}

    write(BP / "entities" / "cave_dweller.json", entity(
        "hl:cave_dweller",
        base_components(["hl_cave_dweller", "monster", "mob"], 0.8, 0.9, 70, {
            "minecraft:knockback_resistance": {"value": 0.5},
            "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": True},
            "minecraft:follow_range": {"value": 64, "max": 64},
            "minecraft:jump.static": {},
            "minecraft:can_climb": {},
            "minecraft:ambient_sound_interval": {"value": 5.0, "range": 8.0, "event_name": "ambient"},
            "minecraft:behavior.float": {"priority": 0},
        }),
        groups={
            "hl:stalking": {**walk(), **fam("hl_cave_dweller", "hl_stalking"), "minecraft:movement": {"value": 0.18},
                            "minecraft:behavior.look_at_player": {"priority": 1, "look_distance": 64, "probability": 1.0,
                                                                  "angle_of_view_horizontal": 360},
                            "minecraft:mark_variant": {"value": 0}},
            "hl:chasing": {**walk(), **fam("hl_cave_dweller", "hl_chasing"), "minecraft:movement": {"value": 0.44},
                           **hunt(64, 7),
                           "minecraft:behavior.leap_at_target": {"priority": 2, "yd": 0.4, "must_be_on_ground": True},
                           "minecraft:mark_variant": {"value": 1}},
            "hl:fleeing": {**walk(), **fam("hl_cave_dweller", "hl_fleeing"), "minecraft:movement": {"value": 0.5},
                           "minecraft:behavior.avoid_mob_type": {"priority": 1, "entity_types": [
                               {"filters": {"test": "is_family", "subject": "other", "value": "player"}, "max_dist": 48,
                                "walk_speed_multiplier": 1.2, "sprint_speed_multiplier": 1.4}]},
                           "minecraft:mark_variant": {"value": 2}},
        },
        events={
            "minecraft:entity_spawned": {"add": {"component_groups": ["hl:stalking"]}},
            "hl:chase": swap("hl:chasing", ["hl:stalking", "hl:fleeing"]),
            "hl:flee": swap("hl:fleeing", ["hl:stalking", "hl:chasing"]),
        }))

    write(BP / "entities" / "fog_man.json", entity(
        "hl:fog_man",
        base_components(["hl_fog_man", "monster", "mob"], 0.7, 1.95, 120, {
            "minecraft:knockback_resistance": {"value": 0.8},
            "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": True},
            "minecraft:follow_range": {"value": 96, "max": 96},
            "minecraft:jump.static": {},
            "minecraft:behavior.float": {"priority": 0},
            "minecraft:behavior.look_at_player": {"priority": 6, "look_distance": 96, "probability": 1.0,
                                                  "angle_of_view_horizontal": 360},
        }),
        groups={
            "hl:watching": {"minecraft:mark_variant": {"value": 0}, **fam("hl_fog_man", "hl_watching"),
                            "minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}},
            "hl:chasing": {"minecraft:mark_variant": {"value": 1}, **fam("hl_fog_man", "hl_chasing"),
                           "minecraft:movement": {"value": 0.46}, **hunt(96, 8),
                           "minecraft:navigation.climb": {"can_path_over_water": True, "avoid_water": True},
                           "minecraft:can_climb": {},
                           "minecraft:break_blocks": {"breakable_blocks": BREAKABLE}},
        },
        events={
            "minecraft:entity_spawned": {"add": {"component_groups": ["hl:watching"]}},
            "hl:chase": swap("hl:chasing", ["hl:watching"]),
            "hl:watch": swap("hl:watching", ["hl:chasing"]),
        }))

    fake_walk = walk(can_open_doors=True, can_pass_doors=True)
    write(BP / "entities" / "fake_player.json", entity(
        "hl:fake_player",
        {
            "minecraft:type_family": {"family": ["hl_fake_player", "hl_haunt"]},
            "minecraft:collision_box": {"width": 0.6, "height": 1.8},
            "minecraft:health": {"value": 20, "max": 20},
            **INVULNERABLE,
            "minecraft:nameable": {"always_show": True, "allow_name_tag_renaming": False},
            "minecraft:persistent": {},
            "minecraft:breathable": {"total_supply": 15, "suffocate_time": 0, "breathes_water": True},
            "minecraft:fire_immune": {},
            "minecraft:movement.basic": {},
            "minecraft:jump.static": {},
            "minecraft:can_climb": {},
            "minecraft:physics": {},
            "minecraft:pushable": {"is_pushable": True, "is_pushable_by_piston": True},
            "minecraft:annotation.open_door": {},
            "minecraft:behavior.open_door": {"priority": 5, "close_door_after": True},
            "minecraft:behavior.float": {"priority": 0},
            "minecraft:behavior.look_at_player": {"priority": 4, "look_distance": 16, "probability": 0.7},
            "minecraft:behavior.random_look_around": {"priority": 7},
            "minecraft:conditional_bandwidth_optimization": {},
        },
        groups={
            "hl:roam": {**fake_walk, "minecraft:movement": {"value": 0.24},
                        "minecraft:behavior.random_stroll": {"priority": 6, "speed_multiplier": 0.9, "xz_dist": 6}},
            "hl:follow": {**fake_walk, "minecraft:movement": {"value": 0.3},
                          "minecraft:behavior.nearest_attackable_target": {
                              "priority": 2, "must_see": False, "reselect_targets": True, "within_radius": 64.0,
                              "entity_types": PLAYER_TARGET},
                          "minecraft:behavior.move_towards_target": {"priority": 3, "speed_multiplier": 1.0,
                                                                     "within_radius": 64.0}},
            "hl:freeze": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}},
        },
        events={
            "minecraft:entity_spawned": {"add": {"component_groups": ["hl:roam"]}},
            "hl:roam": swap("hl:roam", ["hl:follow", "hl:freeze"]),
            "hl:follow": swap("hl:follow", ["hl:roam", "hl:freeze"]),
            "hl:freeze": swap("hl:freeze", ["hl:roam", "hl:follow"]),
        },
        properties={
            "hl:state": {"type": "enum", "values": ["idle", "sneak", "swing", "stare"], "default": "idle",
                         "client_sync": True},
            "hl:eyes": {"type": "bool", "default": False, "client_sync": True},
        }))

    # The boss: can be hurt, fights back, has a boss bar.
    boss = entity(
        "hl:herobrine_boss",
        {
            "minecraft:type_family": {"family": ["hl_boss", "hl_herobrine", "monster", "mob"]},
            "minecraft:collision_box": {"width": 1.0, "height": 3.8},
            "minecraft:health": {"value": 260, "max": 260},
            "minecraft:boss": {"should_darken_sky": True, "hud_range": 64},
            "minecraft:attack": {"damage": 8},
            "minecraft:movement": {"value": 0.3},
            "minecraft:movement.basic": {},
            "minecraft:navigation.walk": {"can_path_over_water": True, "avoid_water": True, "can_open_doors": True},
            "minecraft:jump.static": {},
            "minecraft:can_climb": {},
            "minecraft:physics": {},
            "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": False},
            "minecraft:knockback_resistance": {"value": 0.9},
            "minecraft:follow_range": {"value": 64, "max": 64},
            "minecraft:fire_immune": {},
            "minecraft:persistent": {},
            "minecraft:breathable": {"total_supply": 15, "suffocate_time": 0, "breathes_water": True},
            "minecraft:loot": {"table": "loot_tables/empty.json"},
            "minecraft:damage_sensor": {"triggers": [
                {"cause": "fall", "deals_damage": False},
                {"cause": "lightning", "deals_damage": False},
                {"cause": "suffocation", "deals_damage": False},
                {"cause": "drowning", "deals_damage": False}]},
            "minecraft:behavior.float": {"priority": 0},
            "minecraft:behavior.nearest_attackable_target": {
                "priority": 1, "must_see": False, "reselect_targets": True, "within_radius": 64.0,
                "entity_types": PLAYER_TARGET},
            "minecraft:behavior.melee_attack": {"priority": 2, "speed_multiplier": 1.1, "track_target": True},
            "minecraft:behavior.look_at_player": {"priority": 7, "look_distance": 64, "probability": 1.0},
            "minecraft:conditional_bandwidth_optimization": {},
        },
        properties={
            "hl:phase": {"type": "int", "range": [1, 2], "default": 1, "client_sync": True},
            "hl:cast": {"type": "bool", "default": False, "client_sync": True},
        })
    write(BP / "entities" / "herobrine_boss.json", boss)


# =========================================================================== #
# Entities (client)
# =========================================================================== #

def client(identifier, geometry, textures, animations, animate, controller, egg, material="entity_emissive_alpha"):
    return {"format_version": "1.10.0", "minecraft:client_entity": {"description": {
        "identifier": identifier,
        "materials": {"default": material},
        "textures": {k: f"textures/entity/hl/{v}" for k, v in textures.items()},
        "geometry": {"default": geometry},
        "animations": animations,
        "scripts": {"animate": animate},
        "render_controllers": [controller],
        "spawn_egg": {"base_color": egg[0], "overlay_color": egg[1]},
    }}}


def clients():
    write(RP / "entity" / "herobrine.entity.json", client(
        "hl:herobrine", "geometry.hl.herobrine", {"default": "herobrine"},
        {"look": "animation.hl.look_at_target", "idle": "animation.hl.herobrine.idle",
         "twitch": "animation.hl.herobrine.twitch", "walk": "animation.hl.herobrine.walk"},
        ["look", "idle", "twitch", {"walk": "query.modified_move_speed > 0.05"}],
        "controller.render.hl.default", ("#1e8486", "#a62a22")))
    write(RP / "entity" / "null.entity.json", client(
        "hl:null", "geometry.hl.null", {"default": "null"},
        {"look": "animation.hl.look_at_target", "glitch": "animation.hl.null.glitch"},
        ["look", "glitch"], "controller.render.hl.null", ("#0a0a0c", "#f800f8")))
    write(RP / "entity" / "fog_man.entity.json", client(
        "hl:fog_man", "geometry.hl.fog_man", {"default": "fog_man"},
        {"walk": "animation.hl.fog_man.walk", "look": "animation.hl.look_at_target",
         "watch": "animation.hl.fog_man.watch", "sprint": "animation.hl.fog_man.sprint",
         "attack": "animation.hl.fog_man.attack", "hurt": "animation.hl.fog_man.hurt"},
        ["walk", "look", {"watch": "query.mark_variant == 0"}, {"sprint": "query.mark_variant == 1"},
         {"attack": "variable.attack_time > 0.0"}, {"hurt": "query.hurt_time > 0"}],
        "controller.render.hl.default", ("#c4c2ba", "#2e2e32"), material="entity_alphatest"))
    write(RP / "entity" / "cave_dweller.entity.json", client(
        "hl:cave_dweller", "geometry.hl.cave_dweller", {"default": "cave_dweller"},
        {"walk": "animation.hl.dweller.walk", "idle": "animation.hl.dweller.idle",
         "look": "animation.hl.dweller.look", "twitch": "animation.hl.dweller.twitch",
         "jaw_idle": "animation.hl.dweller.jaw_idle", "jaw_snap": "animation.hl.dweller.jaw_snap",
         "attack": "animation.hl.dweller.attack", "hurt": "animation.hl.dweller.hurt"},
        ["walk", "idle", "look", "twitch", {"jaw_idle": "query.mark_variant != 1"},
         {"jaw_snap": "query.mark_variant == 1"}, {"attack": "variable.attack_time > 0.0"},
         {"hurt": "query.hurt_time > 0"}],
        "controller.render.hl.default", ("#9e9a8e", "#06060a")))
    write(RP / "entity" / "fake_player.entity.json", client(
        "hl:fake_player", "geometry.hl.fake_player", {"default": "fake_player", "eyes": "fake_player_eyes"},
        {"walk": "animation.hl.player.walk", "idle": "animation.hl.player.idle",
         "look": "animation.hl.look_at_target", "sneak": "animation.hl.player.sneak",
         "swing": "animation.hl.player.swing", "stare": "animation.hl.player.stare", "air": "animation.hl.player.air"},
        ["walk", "look", {"idle": "query.property('hl:state') != 'stare'"},
         {"sneak": "query.property('hl:state') == 'sneak'"}, {"swing": "query.property('hl:state') == 'swing'"},
         {"stare": "query.property('hl:state') == 'stare'"}, {"air": "!query.is_on_ground"}],
        "controller.render.hl.fake_player", ("#1ca2a6", "#3e389c")))
    write(RP / "entity" / "herobrine_boss.entity.json", client(
        "hl:herobrine_boss", "geometry.hl.herobrine_boss", {"default": "herobrine_boss", "phase2": "herobrine_boss_2"},
        {"look": "animation.hl.look_at_target", "idle": "animation.hl.boss.idle", "walk": "animation.hl.boss.walk",
         "attack": "animation.hl.boss.attack", "cast": "animation.hl.boss.cast", "rage": "animation.hl.boss.rage"},
        ["look", "idle", {"walk": "query.modified_move_speed > 0.05"}, {"attack": "variable.attack_time > 0.0"},
         {"cast": "query.property('hl:cast')"}, {"rage": "query.property('hl:phase') == 2"}],
        "controller.render.hl.boss", ("#140e0c", "#ff5a1e")))

    material = [{"*": "Material.default"}]
    write(RP / "render_controllers" / "hl.render_controllers.json", {"format_version": "1.8.0", "render_controllers": {
        "controller.render.hl.default": {"geometry": "Geometry.default", "materials": material,
                                         "textures": ["Texture.default"]},
        "controller.render.hl.null": {"geometry": "Geometry.default", "materials": material,
                                      "textures": ["Texture.default"],
                                      "part_visibility": [{"*": "math.random(0.0, 1.0) > 0.04"},
                                                          {"halo": "math.mod(math.floor(query.life_time * 7.0), 3) != 0"}]},
        "controller.render.hl.fake_player": {"geometry": "Geometry.default", "materials": material,
                                             "textures": ["query.property('hl:eyes') ? Texture.eyes : Texture.default"],
                                             "part_visibility": [{"*": True},
                                                                 {"tool": "query.property('hl:state') == 'swing'"}]},
        "controller.render.hl.boss": {"geometry": "Geometry.default", "materials": material,
                                      "textures": ["query.property('hl:phase') == 2 ? Texture.phase2 : Texture.default"]},
    }})

    write(RP / "sounds.json", {"entity_sounds": {"entities": {
        "hl:cave_dweller": {"volume": 0.9, "pitch": [0.8, 1.1], "events": {
            "ambient": "hl.chitter", "hurt": "mob.spider.say", "death": "mob.spider.death"}},
        "hl:fog_man": {"volume": 1.0, "pitch": [0.5, 0.6], "events": {"hurt": "mob.zombie.hurt"}},
        "hl:herobrine_boss": {"volume": 1.0, "pitch": [0.5, 0.6], "events": {
            "hurt": "mob.zombie.hurt", "death": "mob.wither.death"}},
    }}})


# =========================================================================== #
# Items, blocks, recipes
# =========================================================================== #

def item(identifier, icon, category="items", **components):
    c = {"minecraft:icon": {"texture": icon}, "minecraft:display_name": {"value": f"item.{identifier}.name"}}
    c.update(components)
    return {"format_version": "1.20.80", "minecraft:item": {
        "description": {"identifier": identifier, "menu_category": {"category": category}}, "components": c}}


PICK_BLOCKS = "query.any_tag('stone', 'metal', 'rail', 'diamond_pick_diggable', 'iron_pick_diggable', 'stone_pick_diggable')"


def items():
    stack1 = {"minecraft:max_stack_size": 1}
    write(BP / "items" / "journal.json", item("hl:journal", "hl_journal", **stack1))
    write(BP / "items" / "flashlight.json", item("hl:flashlight", "hl_flashlight", "equipment", **stack1))
    write(BP / "items" / "battery.json", item("hl:battery", "hl_battery", **{"minecraft:max_stack_size": 16}))
    for n in range(1, 6):
        write(BP / "items" / f"page_{n}.json", item(f"hl:page_{n}", f"hl_page_{n}", **stack1,
                                                   **{"minecraft:glint": True}))
    write(BP / "items" / "hollow_pickaxe.json", item(
        "hl:hollow_pickaxe", "hl_hollow_pickaxe", "equipment", **stack1,
        **{"minecraft:hand_equipped": True,
           "minecraft:glint": True,
           "minecraft:damage": {"value": 9},
           "minecraft:enchantable": {"value": 15, "slot": "pickaxe"},
           "minecraft:tags": {"tags": ["minecraft:is_pickaxe", "minecraft:is_tool"]},
           "minecraft:digger": {"use_efficiency": True,
                                "destroy_speeds": [{"block": {"tags": PICK_BLOCKS}, "speed": 14}]}}))

    write(BP / "blocks" / "corrupted_block.json", {"format_version": "1.20.80", "minecraft:block": {
        "description": {"identifier": "hl:corrupted_block", "menu_category": {"category": "construction"}},
        "components": {
            "minecraft:material_instances": {"*": {"texture": "hl_corrupted", "render_method": "opaque"}},
            "minecraft:destructible_by_mining": {"seconds_to_destroy": 0.4},
            "minecraft:light_emission": 4,
            "minecraft:loot": "loot_tables/empty.json",
            "minecraft:map_color": "#f800f8"}}})
    box = {"origin": [-4, 0, -4], "size": [8, 11, 8]}
    write(BP / "blocks" / "ward_lantern.json", {"format_version": "1.20.80", "minecraft:block": {
        "description": {"identifier": "hl:ward_lantern", "menu_category": {"category": "items"}},
        "components": {
            "minecraft:geometry": "geometry.hl.ward_lantern",
            "minecraft:material_instances": {"*": {"texture": "hl_ward_lantern", "render_method": "alpha_test"}},
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
    write(BP / "recipes" / "journal.json", shapeless("hl:journal", ["minecraft:book", "minecraft:charcoal"], "hl:journal"))
    write(BP / "recipes" / "flashlight.json", shaped(
        "hl:flashlight", ["IGI", " R ", " I "],
        {"I": "minecraft:iron_ingot", "G": "minecraft:glowstone_dust", "R": "minecraft:redstone"}, "hl:flashlight"))
    write(BP / "recipes" / "battery.json", shapeless(
        "hl:battery", ["minecraft:redstone", "minecraft:iron_nugget", "minecraft:copper_ingot"], "hl:battery", 2))
    write(BP / "recipes" / "ward_lantern.json", shaped(
        "hl:ward_lantern", [" A ", "ALA", " A "], {"A": "minecraft:amethyst_shard", "L": "minecraft:lantern"},
        "hl:ward_lantern"))

    write(RP / "blocks.json", {"format_version": [1, 1, 0],
                               "hl:corrupted_block": {"sound": "stone"},
                               "hl:ward_lantern": {"sound": "lantern"}})
    write(RP / "textures" / "terrain_texture.json", {
        "resource_pack_name": "horror_legends", "texture_name": "atlas.terrain", "padding": 8, "num_mip_levels": 4,
        "texture_data": {"hl_corrupted": {"textures": "textures/blocks/hl/corrupted_block"},
                         "hl_ward_lantern": {"textures": "textures/blocks/hl/ward_lantern"}}})
    icons = ["journal", "flashlight", "battery", "hollow_pickaxe"] + [f"page_{n}" for n in range(1, 6)]
    write(RP / "textures" / "item_texture.json", {
        "resource_pack_name": "horror_legends", "texture_name": "atlas.items",
        "texture_data": {f"hl_{i}": {"textures": f"textures/items/hl/{i}"} for i in icons}})
    write(RP / "textures" / "flipbook_textures.json", [{
        "flipbook_texture": "textures/blocks/hl/corrupted_block", "atlas_tile": "hl_corrupted",
        "ticks_per_frame": 3, "blend_frames": False}])


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
            "description": {"identifier": "geometry.hl.ward_lantern", "texture_width": 16, "texture_height": 16,
                            "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
            "bones": bones}]})


def fogs():
    def fog(identifier, start, end, color):
        return {"format_version": "1.16.100", "minecraft:fog_settings": {
            "description": {"identifier": identifier},
            "distance": {"air": {"fog_start": start, "fog_end": end, "fog_color": color,
                                 "render_distance_type": "fixed"}}}}
    write(RP / "fogs" / "hl_fog.json", fog("hl:fog", 0, 24, "#8e9396"))
    write(RP / "fogs" / "hl_blood.json", fog("hl:blood", 4, 40, "#5a0808"))
    write(RP / "fogs" / "hl_boss.json", fog("hl:boss", 6, 48, "#1a0404"))


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
