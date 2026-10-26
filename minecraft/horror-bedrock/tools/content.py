"""Everything in the packs that is data rather than art: entities (server and
client), items, blocks, recipes, fogs, render controllers, texture atlases,
entity sounds and the language files. build.py calls write_all().
"""

import json
from pathlib import Path

from lang import LANG
from animations import flow

ROOT = Path(__file__).resolve().parent.parent
BP = ROOT / "behavior_pack"
RP = ROOT / "resource_pack"

VERSION = [1, 0, 0]
NOTES = 8  # lore notes (hb:note_1 ..)
UUID = {
    "bp": "b27932ab-b542-4297-a747-f89c7282eb18",
    "bp_data": "d7687c13-9d77-4896-a3c5-b1ae95ddd276",
    "bp_script": "3f290c7b-d0c1-4b5d-83ba-f6fe6c66ba56",
    "rp": "2251b098-5670-4edd-8902-f039504f486b",
    "rp_res": "3f9d2747-1b3a-4fa6-924a-277df463cbcb",
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
# (hb:mode) plus, where the mode changes what the mob does, an event of the
# same name that swaps its component groups (hb:<mode>).

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
VANISH_GROUP = {"hb:vanish": {"minecraft:instant_despawn": {}}}
VANISH_EVENT = {"hb:vanish": {"add": {"component_groups": ["hb:vanish"]}}}


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
    return {f"hb:{n}": {"remove": {"component_groups": [f"hb:{o}" for o in names if o != n]},
                        "add": {"component_groups": [f"hb:{n}"]}} for n in names}, \
        {f"hb:{n}": g for n, g in groups.items()}


def mode_property(*values):
    return {"hb:mode": {"type": "enum", "values": list(values), "default": values[0], "client_sync": True}}


def flag(name):
    return {name: {"type": "bool", "default": False, "client_sync": True}}


def moded(identifier, families, size, health, components, groups, extra_props=None):
    events, gs = modes(groups)
    fam = lambda: {"minecraft:type_family": {"family": families}}
    return entity(identifier, base_components(families, size[0], size[1], health, components),
                  groups={n: {**g, **fam()} for n, g in gs.items()}, events=events,
                  properties={**mode_property(*groups), **(extra_props or {})}, start=f"hb:{next(iter(groups))}")


def entities():
    out = {}
    still = lambda ident, w, h, props=None: entity(ident, base_components(
        [ident.replace(":", "_"), "hb_haunt"], w, h, 100, {**INVULNERABLE, **STILL}), properties=props)
    # --- the ones the scripts move ---------------------------------------- #
    out["boiled_one"] = still("hb:boiled_one", 0.8, 4.4, flag("hb:stare"))
    out["slenderman"] = still("hb:slenderman", 0.8, 3.9, flag("hb:tendrils"))

    # --- the ones that hunt --------------------------------------------- #
    body = {"minecraft:knockback_resistance": {"value": 0.6}, "minecraft:behavior.float": {"priority": 0},
            "minecraft:pushable": {"is_pushable": False, "is_pushable_by_piston": True}}
    doors = walk(can_open_doors=True, can_pass_doors=True, can_break_doors=True)
    smash = {"minecraft:break_blocks": {"breakable_blocks": BREAKABLE}}
    leap = {"minecraft:behavior.leap_at_target": {"priority": 2, "yd": 0.4, "must_be_on_ground": True}}
    out["green_steve"] = moded("hb:green_steve", ["hb_green_steve", "monster", "mob"], (0.6, 1.9), 40, {
        **body, **look_at_player(6, 64), "minecraft:nameable": {"always_show": True, "allow_name_tag_renaming": False}}, {
        "watch": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}, **INVULNERABLE},
        "hunt": {**doors, "minecraft:movement": {"value": 0.32}, **hunt(64, 6, 1.2)}})
    out["mimicer"] = moded("hb:mimicer", ["hb_mimicer", "monster", "mob"], (0.8, 3.0), 150, {
        **body, **look_at_player(6, 96)}, {
        "watch": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}, **INVULNERABLE},
        "chase": {**walk(), "minecraft:movement": {"value": 0.5}, **hunt(96, 9, 1.3), **smash}},
        extra_props=flag("hb:talk"))
    out["knocker"] = moded("hb:knocker", ["hb_knocker", "monster", "mob"], (0.7, 2.6), 60, {
        **body, **look_at_player(6, 48)}, {
        "lurk": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}, **INVULNERABLE},
        "hunt": {**doors, "minecraft:movement": {"value": 0.38}, **hunt(48, 7, 1.2), **smash, **leap}},
        extra_props=flag("hb:knock"))
    out["lick"] = moded("hb:lick", ["hb_lick", "monster", "mob"], (0.6, 2.2), 80, {
        **body, **INVULNERABLE, **look_at_player(6, 64)}, {
        "still": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}},
        "chase": {**walk(), "minecraft:movement": {"value": 0.46}, **hunt(64, 8, 1.25), "minecraft:can_climb": {}}})
    out["smile_dog"] = moded("hb:smile_dog", ["hb_smile_dog", "monster", "mob"], (0.7, 1.0), 30, {
        **body, **look_at_player(6, 48)}, {
        "sit": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}, **INVULNERABLE},
        "hunt": {**walk(), "minecraft:movement": {"value": 0.45}, **hunt(48, 6, 1.4), **leap}})
    out["jeff"] = moded("hb:jeff", ["hb_jeff", "monster", "mob"], (0.6, 1.9), 60, {
        **body, **look_at_player(6, 48)}, {
        "lurk": {"minecraft:movement": {"value": 0.0}, "minecraft:navigation.walk": {}, **INVULNERABLE},
        "hunt": {**doors, "minecraft:movement": {"value": 0.4}, **hunt(48, 8, 1.3)}})

    for name, data in out.items():
        write(BP / "entities" / f"{name}.json", data)


MOVING = "query.modified_move_speed > 0.05"
ATTACK = "variable.attack_time > 0.0"
HURT = "query.hurt_time > 0"


def mode_is(m):
    return f"query.property('hb:mode') == '{m}'"


# name: (textures, animations {short: full}, animate list, controller, egg colours)
# Each creature: (textures, animations, animate, render controller, egg
# colours, flow). `flow` is (idle, walk, run, fast): the controller that
# blends idle and movement smoothly; its animations must be in the dict.
CLIENTS = {
    "green_steve": ({"default": "green_steve"},
                    {"look": "steve.look", "idle": "steve.idle", "walk": "steve.walk", "attack": "steve.attack"},
                    ["look", {"attack": ATTACK}], "default", ("#2a602e", "#145042"),
                    ("idle", "walk", None, None)),
    "mimicer": ({"default": "mimicer"},
                {"look": "mimicer.look", "idle": "mimicer.idle", "walk": "mimicer.walk", "run": "mimicer.run",
                 "talk": "mimicer.talk", "attack": "mimicer.attack"},
                ["look", {"talk": "query.property('hb:talk')"}, {"attack": ATTACK}], "default", ("#9a8678", "#010102"),
                ("idle", "walk", "run", mode_is("chase"))),
    "knocker": ({"default": "knocker"},
                {"look": "knocker.look", "idle": "knocker.idle", "walk": "knocker.walk", "run": "knocker.run",
                 "knock": "knocker.knock", "attack": "knocker.attack"},
                ["look", {"knock": "query.property('hb:knock')"}, {"attack": ATTACK}], "default", ("#aaa8a2", "#5c1618"),
                ("idle", "walk", "run", mode_is("hunt"))),
    "lick": ({"default": "lick"},
             {"look": "lick.look", "idle": "lick.idle", "run": "lick.run", "attack": "lick.attack"},
             ["look", {"attack": ATTACK}], "default", ("#08080a", "#f2eee6"),
             ("idle", "run", None, None)),
    "boiled_one": ({"default": "boiled_one"}, {"idle": "boiled.idle", "stare": "boiled.stare"},
                   ["idle", {"stare": "query.property('hb:stare')"}], "default", ("#8c1c18", "#ff1408"), None),
    "slenderman": ({"default": "slenderman"},
                   {"look": "slender.look", "idle": "slender.idle", "tendrils": "slender.tendrils", "fold": "slender.fold"},
                   ["look", "idle", {"tendrils": "query.property('hb:tendrils')"},
                    {"fold": "!query.property('hb:tendrils')"}], "default", ("#0b0b0e", "#f8f8f4"), None),
    "smile_dog": ({"default": "smile_dog"},
                  {"look": "dog.look", "sit": "dog.sit", "run": "dog.run", "attack": "dog.attack"},
                  ["look", {"attack": ATTACK}], "default", ("#54565c", "#e2e2dc"),
                  ("sit", "run", None, None)),
    "jeff": ({"default": "jeff"},
             {"look": "jeff.look", "idle": "jeff.idle", "walk": "jeff.walk", "run": "jeff.run", "attack": "jeff.attack"},
             ["look", {"attack": ATTACK}], "default", ("#dcdcd4", "#6e0a10"),
             ("idle", "walk", "run", mode_is("hunt"))),
}


def clients():
    controllers = {}
    for name, (textures, anims, animate, controller, egg, flows) in CLIENTS.items():
        anims = {k: f"animation.hb.{v}" for k, v in anims.items()}
        animate = list(animate)
        if flows:
            idle, walk_, run, fast = flows
            cid, data = flow(name, idle, walk_, run, fast)
            controllers[cid] = data
            anims["flow"] = cid
            animate.insert(0, "flow")
        write(RP / "entity" / f"{name}.entity.json", {"format_version": "1.10.0", "minecraft:client_entity": {
            "description": {
                "identifier": f"hb:{name}",
                "materials": {"default": "entity_emissive_alpha"},
                "textures": {k: f"textures/entity/hb/{v}" for k, v in textures.items()},
                "geometry": {"default": f"geometry.hb.{name}"},
                "animations": anims,
                "scripts": {"animate": animate},
                "render_controllers": [f"controller.render.hb.{controller}"],
                "spawn_egg": {"base_color": egg[0], "overlay_color": egg[1]},
            }}})
    write(RP / "animation_controllers" / "hb.animation_controllers.json",
          {"format_version": "1.10.0", "animation_controllers": controllers})

    material = [{"*": "Material.default"}]
    rc = lambda **kw: {"geometry": "Geometry.default", "materials": material, "textures": ["Texture.default"], **kw}
    write(RP / "render_controllers" / "hb.render_controllers.json", {"format_version": "1.8.0", "render_controllers": {
        "controller.render.hb.default": rc(),
    }})

    write(RP / "sounds.json", {"entity_sounds": {"entities": {
        "hb:green_steve": {"volume": 1.0, "pitch": [0.9, 1.0], "events": {"hurt": "game.player.hurt", "death": "game.player.die"}},
        "hb:smile_dog": {"volume": 1.0, "pitch": [0.6, 0.7], "events": {"hurt": "mob.wolf.hurt", "death": "mob.wolf.death"}},
        "hb:jeff": {"volume": 1.0, "pitch": [0.8, 0.9], "events": {"hurt": "game.player.hurt", "death": "game.player.die"}},
        "hb:knocker": {"volume": 1.0, "pitch": [0.5, 0.6], "events": {"hurt": "mob.zombie.hurt", "death": "mob.zombie.death"}},
        "hb:mimicer": {"volume": 1.0, "pitch": [0.5, 0.6], "events": {"hurt": "game.player.hurt", "death": "mob.zombie.death"}},
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
    write(BP / "items" / "journal.json", item("hb:journal", "hb_journal", **stack1))
    write(BP / "items" / "flashlight.json", item("hb:flashlight", "hb_flashlight", "equipment", **stack1))
    write(BP / "items" / "battery.json", item("hb:battery", "hb_battery", **{"minecraft:max_stack_size": 16}))
    # Lore: notes left in the structures by whoever was here before you.
    for n in range(1, NOTES + 1):
        write(BP / "items" / f"note_{n}.json", item(f"hb:note_{n}", "hb_note", **stack1))
    # Slenderman's pages: you get one by tearing it off a tree.
    write(BP / "items" / "page.json", item("hb:page", "hb_page", **{"minecraft:max_stack_size": 8, "minecraft:glint": True}))
    write(BP / "blocks" / "slender_page.json", {"format_version": "1.20.80", "minecraft:block": {
        # Always placed by the script on the north side of a trunk, so the
        # sheet sits on the block's south edge, against the bark.
        "description": {"identifier": "hb:slender_page", "menu_category": {"category": "none"}},
        "components": {
            "minecraft:geometry": "geometry.hb.slender_page",
            "minecraft:material_instances": {"*": {"texture": "hb_slender_page", "render_method": "alpha_test"}},
            "minecraft:collision_box": False,
            "minecraft:selection_box": {"origin": [-5, 2, 6], "size": [10, 12, 2]},
            "minecraft:destructible_by_mining": {"seconds_to_destroy": 0.1},
            "minecraft:light_dampening": 0,
            "minecraft:loot": "loot_tables/empty.json",
            "minecraft:map_color": "#f0f0e8"}}})
    box = {"origin": [-4, 0, -4], "size": [8, 11, 8]}
    write(BP / "blocks" / "ward_lantern.json", {"format_version": "1.20.80", "minecraft:block": {
        "description": {"identifier": "hb:ward_lantern", "menu_category": {"category": "items"}},
        "components": {
            "minecraft:geometry": "geometry.hb.ward_lantern",
            "minecraft:material_instances": {"*": {"texture": "hb_ward_lantern", "render_method": "alpha_test"}},
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
    write(BP / "recipes" / "journal.json", shapeless("hb:journal", ["minecraft:book", "minecraft:charcoal"], "hb:journal"))
    write(BP / "recipes" / "flashlight.json", shaped(
        "hb:flashlight", ["IGI", " R ", " I "],
        {"I": "minecraft:iron_ingot", "G": "minecraft:glowstone_dust", "R": "minecraft:redstone"}, "hb:flashlight"))
    write(BP / "recipes" / "battery.json", shapeless(
        "hb:battery", ["minecraft:redstone", "minecraft:iron_nugget", "minecraft:copper_ingot"], "hb:battery", 2))
    write(BP / "recipes" / "ward_lantern.json", shaped(
        "hb:ward_lantern", [" A ", "ALA", " A "], {"A": "minecraft:amethyst_shard", "L": "minecraft:lantern"},
        "hb:ward_lantern"))

    write(RP / "blocks.json", {"format_version": [1, 1, 0],
                               "hb:ward_lantern": {"sound": "lantern"},
                               "hb:slender_page": {"sound": "wood"}})
    write(RP / "textures" / "terrain_texture.json", {
        "resource_pack_name": "horror_bedrock", "texture_name": "atlas.terrain", "padding": 8, "num_mip_levels": 4,
        "texture_data": {"hb_ward_lantern": {"textures": "textures/blocks/hb/ward_lantern"},
                         "hb_slender_page": {"textures": "textures/blocks/hb/slender_page"}}})
    icons = ["journal", "flashlight", "battery", "note", "page"]
    write(RP / "textures" / "item_texture.json", {
        "resource_pack_name": "horror_bedrock", "texture_name": "atlas.items",
        "texture_data": {f"hb_{i}": {"textures": f"textures/items/hb/{i}"} for i in icons}})


def page_geometry():
    """A sheet of paper nailed to the side of a tree trunk."""
    write(RP / "models" / "blocks" / "slender_page.geo.json", {
        "format_version": "1.16.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.hb.slender_page", "texture_width": 16, "texture_height": 16},
            "bones": [{"name": "page", "pivot": [0, 0, 0], "cubes": [
                {"origin": [-5, 2, 7.5], "size": [10, 12, 0.5], "uv": {
                    "north": {"uv": [3, 2], "uv_size": [10, 12]}, "south": {"uv": [3, 2], "uv_size": [10, 12]},
                    "east": {"uv": [0, 0], "uv_size": [1, 12]}, "west": {"uv": [0, 0], "uv_size": [1, 12]},
                    "up": {"uv": [0, 0], "uv_size": [10, 1]}, "down": {"uv": [0, 0], "uv_size": [10, 1]}}}]}]}]})


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
            "description": {"identifier": "geometry.hb.ward_lantern", "texture_width": 16, "texture_height": 16,
                            "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
            "bones": bones}]})


def fogs():
    def fog(identifier, start, end, color):
        return {"format_version": "1.16.100", "minecraft:fog_settings": {
            "description": {"identifier": identifier},
            "distance": {"air": {"fog_start": start, "fog_end": end, "fog_color": color,
                                 "render_distance_type": "fixed"}}}}
    # Slenderman's woods: the fog comes in as you find the pages.
    write(RP / "fogs" / "hb_slender.json", fog("hb:slender", 2, 28, "#0a0a0c"))


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
    page_geometry()
    fogs()
    langs()
