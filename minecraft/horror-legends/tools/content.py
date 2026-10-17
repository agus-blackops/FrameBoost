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

VERSION = [7, 1, 0]
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
#
# The scripts drive every creature: they choose where it appears and switch
# it between modes. A mode is an entity property the client animates on
# (hl:mode) plus, where the mode changes what the mob does, an event of the
# same name that swaps its component groups (hl:<mode>).

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
VANISH_GROUP = {"hl:vanish": {"minecraft:instant_despawn": {}}}
VANISH_EVENT = {"hl:vanish": {"add": {"component_groups": ["hl:vanish"]}}}


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
    return {f"hl:{n}": {"remove": {"component_groups": [f"hl:{o}" for o in names if o != n]},
                        "add": {"component_groups": [f"hl:{n}"]}} for n in names}, \
        {f"hl:{n}": g for n, g in groups.items()}


def mode_property(*values):
    return {"hl:mode": {"type": "enum", "values": list(values), "default": values[0], "client_sync": True}}


def flag(name):
    return {name: {"type": "bool", "default": False, "client_sync": True}}


def moded(identifier, families, size, health, components, groups, extra_props=None):
    events, gs = modes(groups)
    fam = lambda: {"minecraft:type_family": {"family": families}}
    return entity(identifier, base_components(families, size[0], size[1], health, components),
                  groups={n: {**g, **fam()} for n, g in gs.items()}, events=events,
                  properties={**mode_property(*groups), **(extra_props or {})}, start=f"hl:{next(iter(groups))}")


def entities():
    out = {}
    # --- the ones that only watch: the scripts move them ------------------ #
    out["herobrine"] = entity("hl:herobrine", base_components(
        ["hl_herobrine", "hl_haunt"], 0.6, 3.0, 100, {**INVULNERABLE, **STILL}), properties=flag("hl:strike"))
    out["null"] = entity("hl:null", base_components(["hl_null", "hl_haunt"], 0.6, 2.5, 100, {**INVULNERABLE, **STILL}))
    out["shadow"] = entity("hl:shadow", base_components(
        ["hl_shadow", "hl_haunt"], 0.6, 2.6, 100, {**INVULNERABLE, **STILL}))
    out["entity_303"] = entity("hl:entity_303", base_components(
        ["hl_entity_303", "hl_haunt"], 0.6, 2.2, 100, {**INVULNERABLE, **STILL, "minecraft:physics": {"has_gravity": False}}),
        properties=flag("hl:cast"))
    # The statue must not even turn its head while you watch.
    out["weeping_statue"] = entity("hl:weeping_statue", base_components(
        ["hl_weeping_statue", "hl_haunt"], 0.8, 2.4, 100,
        {**INVULNERABLE, **HEAVY, "minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}}),
        properties={"hl:pose": {"type": "int", "range": [0, 2], "default": 0, "client_sync": True}})

    # --- the ones that hunt --------------------------------------------- #
    body = {"minecraft:knockback_resistance": {"value": 0.6}, "minecraft:behavior.float": {"priority": 0},
            "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": True}}
    out["cave_dweller"] = moded("hl:cave_dweller", ["hl_cave_dweller", "monster", "mob"], (0.8, 0.9), 70, {
        **body, "minecraft:can_climb": {},
        "minecraft:ambient_sound_interval": {"value": 5.0, "range": 8.0, "event_name": "ambient"}}, {
        "stalk": {**walk(), "minecraft:movement": {"value": 0.18}, **look_at_player(1, 64)},
        "chase": {**walk(), "minecraft:movement": {"value": 0.44}, **hunt(64, 7),
                  "minecraft:behavior.leap_at_target": {"priority": 2, "yd": 0.4, "must_be_on_ground": True}},
        "flee": {**walk(), "minecraft:movement": {"value": 0.5}, **avoid()}})
    out["fog_man"] = moded("hl:fog_man", ["hl_fog_man", "monster", "mob"], (0.7, 1.95), 120, {
        **body, **look_at_player(6)}, {
        "watch": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}},
        "chase": {"minecraft:movement": {"value": 0.46}, **hunt(96, 8), "minecraft:can_climb": {},
                  "minecraft:navigation.climb": {"can_path_over_water": True, "avoid_water": True},
                  "minecraft:break_blocks": {"breakable_blocks": BREAKABLE}}})
    out["siren_head"] = moded("hl:siren_head", ["hl_siren_head", "monster", "mob"], (1.0, 5.5), 300, {
        **INVULNERABLE, **HEAVY, **look_at_player(6, 128)}, {
        "idle": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}},
        "hunt": {**walk(), "minecraft:movement": {"value": 0.42}, **hunt(128, 14, 1.2),
                 "minecraft:break_blocks": {"breakable_blocks": LEAVES}}})
    out["goatman"] = moded("hl:goatman", ["hl_goatman", "monster", "mob"], (0.7, 2.6), 60, {
        **body, **look_at_player(6, 64)}, {
        "stalk": {**walk(), "minecraft:movement": {"value": 0.0}},
        "charge": {**walk(), "minecraft:movement": {"value": 0.48}, **hunt(64, 6, 1.3),
                   "minecraft:behavior.leap_at_target": {"priority": 2, "yd": 0.5, "must_be_on_ground": True}},
        "flee": {**walk(), "minecraft:movement": {"value": 0.5}, **avoid(64)}},
        flag("hl:scream"))
    out["wendigo"] = moded("hl:wendigo", ["hl_wendigo", "monster", "mob"], (0.8, 3.2), 90, {
        **body, **look_at_player(6, 64)}, {
        "stalk": {**walk(), "minecraft:movement": {"value": 0.0}},
        "charge": {**walk(), "minecraft:movement": {"value": 0.5}, **hunt(64, 9, 1.3),
                   "minecraft:break_blocks": {"breakable_blocks": LEAVES}},
        "flee": {**walk(), "minecraft:movement": {"value": 0.55}, **avoid(64)}})
    out["skinwalker"] = moded("hl:skinwalker", ["hl_skinwalker", "monster", "mob"], (0.9, 1.4), 80, {
        **body, "minecraft:ambient_sound_interval": {"value": 8.0, "range": 12.0, "event_name": "ambient"}}, {
        "cow": {**walk(), "minecraft:movement": {"value": 0.14}, **look_at_player(1, 48),
                "minecraft:behavior.random_stroll": {"priority": 6, "speed_multiplier": 0.8, "xz_dist": 5}},
        "hunt": {**walk(), "minecraft:movement": {"value": 0.46}, **hunt(64, 8, 1.3),
                 "minecraft:collision_box": {"width": 0.7, "height": 2.6},
                 "minecraft:behavior.leap_at_target": {"priority": 2, "yd": 0.5, "must_be_on_ground": True}},
        "flee": {**walk(), "minecraft:movement": {"value": 0.55}, **avoid(64),
                 "minecraft:collision_box": {"width": 0.7, "height": 2.6}}},
        flag("hl:revealed"))
    amphibious = {"minecraft:navigation.generic": {"is_amphibious": True, "can_path_over_water": False,
                                                   "can_swim": True, "can_walk": True, "can_break_doors": True},
                  "minecraft:underwater_movement": {"value": 0.12}}
    out["drowned_one"] = moded("hl:drowned_one", ["hl_drowned_one", "monster", "mob"], (0.6, 2.0), 50, {
        **body, **look_at_player(6, 48)}, {
        "lurk": {**amphibious, "minecraft:movement": {"value": 0.0}},
        "grab": {**amphibious, "minecraft:movement": {"value": 0.25}, **hunt(48, 5, 1.2)}})

    # The Mimic: a chest until you open it (or hit it, or come too close).
    out["mimic"] = moded("hl:mimic", ["hl_mimic", "monster", "mob"], (0.9, 0.9), 40, {
        "minecraft:loot": {"table": "loot_tables/hl/mimic.json"},
        "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": True}}, {
        "asleep": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {},
                   "minecraft:knockback_resistance": {"value": 1.0},
                   "minecraft:interact": {"interactions": [{"on_interact": {
                       "filters": PLAYER, "event": "hl:awake", "target": "self"}}]}},
        "awake": {**walk(), "minecraft:movement": {"value": 0.34}, **hunt(32, 7, 1.3),
                  "minecraft:collision_box": {"width": 0.9, "height": 1.5}}},
        flag("hl:awake"))

    # HerobrineGamer788: a "player".
    fake_walk = walk(can_open_doors=True, can_pass_doors=True)
    fake_events, fake_groups = modes({
        "roam": {**fake_walk, "minecraft:movement": {"value": 0.24},
                 "minecraft:behavior.random_stroll": {"priority": 6, "speed_multiplier": 0.9, "xz_dist": 6}},
        "follow": {**fake_walk, "minecraft:movement": {"value": 0.3},
                   "minecraft:behavior.nearest_attackable_target": {
                       "priority": 2, "must_see": False, "reselect_targets": True, "within_radius": 64.0,
                       "entity_types": PLAYER_TARGET},
                   "minecraft:behavior.move_towards_target": {"priority": 3, "speed_multiplier": 1.0,
                                                              "within_radius": 64.0}},
        "freeze": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}}})
    out["fake_player"] = entity("hl:fake_player", {
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
    }, groups=fake_groups, events=fake_events, start="hl:roam", properties={
        "hl:state": {"type": "enum", "values": ["idle", "sneak", "swing", "stare"], "default": "idle",
                     "client_sync": True},
        **flag("hl:eyes")})

    # The boss: can be hurt, fights back, has a boss bar.
    out["herobrine_boss"] = entity("hl:herobrine_boss", {
        "minecraft:type_family": {"family": ["hl_boss", "hl_herobrine", "monster", "mob"]},
        "minecraft:collision_box": {"width": 1.2, "height": 4.4},
        "minecraft:health": {"value": 300, "max": 300},
        "minecraft:boss": {"should_darken_sky": True, "hud_range": 64},
        "minecraft:attack": {"damage": 9},
        "minecraft:movement": {"value": 0.3},
        "minecraft:movement.basic": {},
        **walk(can_open_doors=True),
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
        **NO_FALL,
        "minecraft:behavior.float": {"priority": 0},
        "minecraft:behavior.nearest_attackable_target": {
            "priority": 1, "must_see": False, "reselect_targets": True, "within_radius": 64.0,
            "entity_types": PLAYER_TARGET},
        "minecraft:behavior.melee_attack": {"priority": 2, "speed_multiplier": 1.1, "track_target": True},
        **look_at_player(7, 64),
        "minecraft:conditional_bandwidth_optimization": {},
    }, properties={
        "hl:phase": {"type": "int", "range": [1, 2], "default": 1, "client_sync": True},
        **flag("hl:cast")})

    for name, data in out.items():
        write(BP / "entities" / f"{name}.json", data)
    write(BP / "loot_tables" / "hl" / "mimic.json", {"pools": [
        {"rolls": {"min": 2, "max": 4}, "entries": [
            {"type": "item", "name": "minecraft:iron_ingot", "weight": 30, "functions": [
                {"function": "set_count", "count": {"min": 1, "max": 4}}]},
            {"type": "item", "name": "minecraft:gold_ingot", "weight": 20, "functions": [
                {"function": "set_count", "count": {"min": 1, "max": 3}}]},
            {"type": "item", "name": "minecraft:emerald", "weight": 15, "functions": [
                {"function": "set_count", "count": {"min": 1, "max": 3}}]},
            {"type": "item", "name": "minecraft:diamond", "weight": 5},
            {"type": "item", "name": "minecraft:bone", "weight": 20, "functions": [
                {"function": "set_count", "count": {"min": 1, "max": 5}}]}]}]})


# =========================================================================== #
# Entities (client)
# =========================================================================== #

MOVING = "query.modified_move_speed > 0.05"
ATTACK = "variable.attack_time > 0.0"
HURT = "query.hurt_time > 0"


def mode_is(m):
    return f"query.property('hl:mode') == '{m}'"


# name: (textures, animations {short: full}, animate list, controller, egg colours)
CLIENTS = {
    "herobrine": ({"default": "herobrine"},
                  {"look": "herobrine.look", "idle": "herobrine.idle", "tail": "herobrine.tail",
                   "twitch": "herobrine.twitch", "walk": "herobrine.walk", "strike": "herobrine.strike"},
                  ["look", "idle", "tail", "twitch", {"walk": MOVING}, {"strike": "query.property('hl:strike')"}],
                  "default", ("#1e8486", "#a62a22")),
    "fake_player": ({"default": "fake_player", "eyes": "fake_player_eyes"},
                    {"look": "player.look", "walk": "player.walk", "idle": "player.idle", "sneak": "player.sneak",
                     "swing": "player.swing", "stare": "player.stare", "air": "player.air"},
                    [{"look": "query.property('hl:state') != 'stare'"}, "walk", "idle",
                     {"sneak": "query.property('hl:state') == 'sneak'"},
                     {"swing": "query.property('hl:state') == 'swing'"},
                     {"stare": "query.property('hl:state') == 'stare'"}, {"air": "!query.is_on_ground"}],
                    "fake_player", ("#1ca2a6", "#3e389c")),
    "fog_man": ({"default": "fog_man"},
                {"look": "fog_man.look", "walk": "fog_man.walk", "watch": "fog_man.watch",
                 "chase": "fog_man.chase", "attack": "fog_man.attack", "hurt": "fog_man.hurt"},
                ["look", "walk", {"watch": mode_is("watch")}, {"chase": mode_is("chase")},
                 {"attack": ATTACK}, {"hurt": HURT}],
                "default", ("#b8b8b2", "#161b17")),
    "cave_dweller": ({"default": "cave_dweller"},
                     {"walk": "dweller.walk", "idle": "dweller.idle", "look": "dweller.look",
                      "twitch": "dweller.twitch", "lips_idle": "dweller.lips_idle", "lips_open": "dweller.lips_open",
                      "attack": "dweller.attack", "hurt": "dweller.hurt"},
                     ["walk", "idle", "look", "twitch", {"lips_idle": f"!({mode_is('chase')})"},
                      {"lips_open": mode_is("chase")}, {"attack": ATTACK}, {"hurt": HURT}],
                     "default", ("#d6d6ce", "#48121c")),
    "null": ({"default": "null"}, {"look": "null.look", "glitch": "null.glitch"}, ["look", "glitch"],
             "null", ("#0a0a0c", "#f800f8")),
    "herobrine_boss": ({"default": "herobrine_boss", "phase2": "herobrine_boss_2"},
                       {"look": "boss.look", "idle": "boss.idle", "walk": "boss.walk", "attack": "boss.attack",
                        "cast": "boss.cast", "rage": "boss.rage"},
                       ["look", "idle", {"walk": MOVING}, {"attack": ATTACK}, {"cast": "query.property('hl:cast')"},
                        {"rage": "query.property('hl:phase') == 2"}],
                       "boss", ("#1a1a20", "#e8640c")),
    "siren_head": ({"default": "siren_head"},
                   {"look": "siren.look", "idle": "siren.idle", "walk": "siren.walk", "hunt": "siren.hunt",
                    "attack": "siren.attack"},
                   ["look", "idle", {"walk": MOVING}, {"hunt": mode_is("hunt")}, {"attack": ATTACK}],
                   "default", ("#54402e", "#a05c28")),
    "goatman": ({"default": "goatman"},
                {"look": "goatman.look", "idle": "goatman.idle", "walk": "goatman.walk", "run": "goatman.run",
                 "scream": "goatman.scream", "attack": "goatman.attack"},
                ["look", "idle", {"walk": MOVING}, {"run": mode_is("charge")},
                 {"scream": "query.property('hl:scream')"}, {"attack": ATTACK}],
                "default", ("#1f1812", "#e8c020")),
    "weeping_statue": ({"default": "weeping_statue"},
                       {"pose1": "statue.pose1", "pose2": "statue.pose2"},
                       [{"pose1": "query.property('hl:pose') == 1"}, {"pose2": "query.property('hl:pose') == 2"}],
                       "default", ("#928d84", "#3c5222")),
    "mimic": ({"default": "mimic"},
              {"awake": "mimic.awake", "walk": "mimic.walk", "attack": "mimic.attack", "asleep": "mimic.asleep"},
              [{"asleep": "!query.property('hl:awake')"}, {"awake": "query.property('hl:awake')"},
               {"walk": MOVING}, {"attack": ATTACK}],
              "mimic", ("#8a5e2c", "#a83e34")),
    "shadow": ({"default": "shadow"}, {"look": "shadow.look", "idle": "shadow.idle", "walk": "shadow.walk"},
               ["look", "idle", {"walk": MOVING}], "default", ("#030304", "#b01010")),
    "skinwalker": ({"default": "skinwalker"},
                   {"cow_walk": "skinwalker.cow_walk", "cow_stare": "skinwalker.cow_stare",
                    "idle": "skinwalker.idle", "walk": "skinwalker.walk", "run": "skinwalker.run",
                    "look": "skinwalker.look", "attack": "skinwalker.attack"},
                   [{"cow_walk": "!query.property('hl:revealed')"}, {"cow_stare": "!query.property('hl:revealed')"},
                    {"idle": "query.property('hl:revealed')"}, {"walk": "query.property('hl:revealed')"},
                    {"look": "query.property('hl:revealed')"}, {"run": mode_is("hunt")}, {"attack": ATTACK}],
                   "skinwalker", ("#402818", "#c4c0b6")),
    "wendigo": ({"default": "wendigo"},
                {"look": "wendigo.look", "idle": "wendigo.idle", "walk": "wendigo.walk", "charge": "wendigo.charge",
                 "attack": "wendigo.attack"},
                ["look", "idle", {"walk": MOVING}, {"charge": mode_is("charge")}, {"attack": ATTACK}],
                "default", ("#b6bec4", "#2a2018")),
    "entity_303": ({"default": "entity_303"}, {"look": "e303.look", "float": "e303.float", "cast": "e303.cast"},
                   ["look", "float", {"cast": "query.property('hl:cast')"}], "default", ("#dcdad4", "#ff1818")),
    "drowned_one": ({"default": "drowned_one"},
                    {"idle": "drowned.idle", "walk": "drowned.walk", "swim": "drowned.swim",
                     "attack": "drowned.attack"},
                    ["idle", {"walk": f"{MOVING} && !query.is_in_water"}, {"swim": "query.is_in_water"},
                     {"attack": ATTACK}],
                    "default", ("#5e7278", "#0b0e12")),
}


def clients():
    for name, (textures, anims, animate, controller, egg) in CLIENTS.items():
        write(RP / "entity" / f"{name}.entity.json", {"format_version": "1.10.0", "minecraft:client_entity": {
            "description": {
                "identifier": f"hl:{name}",
                "materials": {"default": "entity_emissive_alpha"},
                "textures": {k: f"textures/entity/hl/{v}" for k, v in textures.items()},
                "geometry": {"default": f"geometry.hl.{name}"},
                "animations": {k: f"animation.hl.{v}" for k, v in anims.items()},
                "scripts": {"animate": animate},
                "render_controllers": [f"controller.render.hl.{controller}"],
                "spawn_egg": {"base_color": egg[0], "overlay_color": egg[1]},
            }}})

    material = [{"*": "Material.default"}]
    rc = lambda **kw: {"geometry": "Geometry.default", "materials": material, "textures": ["Texture.default"], **kw}
    mimic_parts = ["tongue", "tongueTip"] + [f"leg{a}{b}{t}" for a in ("Front", "Mid", "Back")
                                              for b in ("Right", "Left") for t in ("", "Tip")]
    from models import skinwalker
    cow = [b for b in ("cowBody",) + skinwalker.COW]
    walker = [b for b in skinwalker.WALKER]
    write(RP / "render_controllers" / "hl.render_controllers.json", {"format_version": "1.8.0", "render_controllers": {
        "controller.render.hl.default": rc(),
        "controller.render.hl.null": rc(part_visibility=[
            {"*": "math.random(0.0, 1.0) > 0.04"}, {"halo": "math.mod(math.floor(query.life_time * 7.0), 3) != 0"},
            {"hitbox": "math.mod(math.floor(query.life_time * 2.0), 5) != 0"}]),
        "controller.render.hl.fake_player": rc(
            textures=["query.property('hl:eyes') ? Texture.eyes : Texture.default"],
            part_visibility=[{"*": True}, {"tool": "query.property('hl:state') == 'swing'"}]),
        "controller.render.hl.boss": rc(
            textures=["query.property('hl:phase') == 2 ? Texture.phase2 : Texture.default"]),
        "controller.render.hl.mimic": rc(part_visibility=[{"*": True}] + [
            {b: "query.property('hl:awake')"} for b in mimic_parts]),
        "controller.render.hl.skinwalker": rc(part_visibility=[{"*": True}] + [
            {b: "!query.property('hl:revealed')"} for b in dict.fromkeys(cow)] + [
            {b: "query.property('hl:revealed')"} for b in walker]),
    }})

    write(RP / "sounds.json", {"entity_sounds": {"entities": {
        "hl:cave_dweller": {"volume": 0.9, "pitch": [0.8, 1.1], "events": {
            "ambient": "hl.chitter", "hurt": "mob.spider.say", "death": "mob.spider.death"}},
        "hl:fog_man": {"volume": 1.0, "pitch": [0.5, 0.6], "events": {"hurt": "mob.zombie.hurt"}},
        "hl:herobrine_boss": {"volume": 1.0, "pitch": [0.5, 0.6], "events": {
            "hurt": "mob.zombie.hurt", "death": "mob.wither.death"}},
        "hl:goatman": {"volume": 1.0, "pitch": [0.5, 0.6], "events": {
            "hurt": "mob.goat.hurt", "death": "mob.goat.death"}},
        "hl:wendigo": {"volume": 1.0, "pitch": [0.4, 0.5], "events": {
            "hurt": "mob.skeleton.hurt", "death": "mob.skeleton.death"}},
        "hl:skinwalker": {"volume": 1.0, "pitch": [0.55, 0.6], "events": {
            "ambient": "mob.cow.say", "hurt": "mob.cow.hurt", "death": "mob.zombie.death"}},
        "hl:drowned_one": {"volume": 1.0, "pitch": [0.7, 0.8], "events": {
            "hurt": "mob.drowned.hurt", "death": "mob.drowned.death"}},
        "hl:mimic": {"volume": 1.0, "pitch": [0.8, 1.0], "events": {
            "hurt": "random.chestclosed", "death": "random.break"}},
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
    for n in range(1, 8):
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
    icons = ["journal", "flashlight", "battery", "hollow_pickaxe"] + [f"page_{n}" for n in range(1, 8)]
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
