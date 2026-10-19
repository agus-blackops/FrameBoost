#!/usr/bin/env python3
"""Genera el addon "Los 7 Pecados Capitales" para Minecraft Bedrock.

Produce el Behavior Pack, el Resource Pack (modelos, texturas, animaciones,
idiomas) y el archivo .mcaddon listo para importar.

    python3 tools/generar.py
"""
import json
import math
import random
import shutil
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

import arte
from modelos import (modelo_avaricity, modelo_envidium, modelo_glotonix, modelo_iratus,
                     modelo_lujurius, modelo_perezoth, modelo_rey, modelo_superbius)

RAIZ = Path(__file__).resolve().parent.parent
BP = RAIZ / "behavior_pack"
RP = RAIZ / "resource_pack"
DIST = RAIZ / "dist"
NS = "pecados"

UUID_BP_HEADER = "3d1ccddd-c3ab-4976-85a1-ed330acb9bea"
UUID_BP_DATA = "f091af7d-9ae2-478d-9466-49f3c6a6041d"
UUID_BP_SCRIPT = "0effd3e2-bbbc-44ad-9357-721ae2a8460b"
UUID_RP_HEADER = "15c8a921-72a5-4114-a37e-96a0aac93924"
UUID_RP_RES = "8a3f5c27-1d4e-4b6a-9f0c-2e7d6b1a9c44"
VERSION = [1, 1, 0]
MOTOR_MIN = [1, 21, 40]


PECADOS = {
    "superbius": dict(
        pecado="soberbia", nombre="Superbius", modelo=modelo_superbius, escala=1.0,
        vida=60, dano=8, velocidad=0.25, efecto=None, peso=3, xp=25,
        egg=("#6E28A0", "#EBC32D"), extra={"minecraft:knockback_resistance": {"value": 0.9}},
        botin=[("minecraft:gold_ingot", 1, 3, 1.0), ("minecraft:diamond", 1, 1, 0.15)],
        esencia_color=((150, 60, 210), (240, 200, 60)),
        desc="Pecado de la Soberbia. Un rey corrupto con corona de oro que lanza a sus víctimas por los aires "
             "y devuelve parte del daño que recibe."),
    "avaricity": dict(
        pecado="avaricia", nombre="Avaricity", modelo=modelo_avaricity, escala=1.0,
        vida=18, dano=2, velocidad=0.38, efecto=None, peso=8, xp=12,
        egg=("#78AF3C", "#F5C828"),
        extra={"minecraft:inventory": {"container_type": "inventory", "inventory_size": 9}},
        botin=[("minecraft:gold_nugget", 2, 8, 1.0), ("minecraft:emerald", 1, 2, 0.4)],
        esencia_color=((240, 200, 40), (120, 180, 60)),
        desc="Pecado de la Avaricia. Un duende veloz que roba el objeto que llevas en la mano. "
             "¡Mátalo para recuperar tu botín!"),
    "lujurius": dict(
        pecado="lujuria", nombre="Lujurius", modelo=modelo_lujurius, escala=1.0,
        vida=26, dano=3, velocidad=0.3, efecto=("nausea", 6), peso=6, xp=12,
        egg=("#F064AA", "#B41E78"), extra={},
        botin=[("minecraft:pink_dye", 1, 3, 1.0), ("minecraft:rose_bush", 1, 1, 0.3)],
        esencia_color=((250, 90, 160), (255, 200, 220)),
        desc="Pecado de la Lujuria. Hechiza a los jugadores cercanos y los atrae hacia sí; su golpe "
             "provoca náuseas."),
    "iratus": dict(
        pecado="ira", nombre="Iratus", modelo=modelo_iratus, escala=1.0,
        vida=40, dano=7, velocidad=0.3, efecto=None, peso=5, xp=20,
        egg=("#AA1E14", "#2D1414"),
        extra={"minecraft:fire_immune": True, "minecraft:knockback_resistance": {"value": 0.5}},
        botin=[("minecraft:blaze_powder", 1, 3, 1.0), ("minecraft:magma_cream", 1, 1, 0.3)],
        esencia_color=((220, 40, 20), (255, 160, 40)),
        desc="Pecado de la Ira. Un demonio con cuernos que prende fuego a quien golpea y entra en "
             "furia cuando está herido. Inmune al fuego; aparece también en el Nether."),
    "glotonix": dict(
        pecado="gula", nombre="Glotonix", modelo=modelo_glotonix, escala=1.0,
        vida=50, dano=4, velocidad=0.18, efecto=("hunger", 15), peso=6, xp=15,
        egg=("#DC8232", "#825028"), extra={"minecraft:knockback_resistance": {"value": 0.7}},
        botin=[("minecraft:cooked_beef", 2, 4, 1.0), ("minecraft:cake", 1, 1, 0.1)],
        esencia_color=((230, 140, 50), (130, 80, 40)),
        desc="Pecado de la Gula. Un glotón enorme que te provoca hambre, se cura robándote la comida "
             "y devora los objetos que haya en el suelo."),
    "envidium": dict(
        pecado="envidia", nombre="Envidium", modelo=modelo_envidium, escala=1.0,
        vida=30, dano=4, velocidad=0.32, efecto=("weakness", 8), peso=6, xp=15,
        egg=("#1E5A28", "#A0FF50"), extra={},
        botin=[("minecraft:ender_pearl", 1, 1, 0.5), ("minecraft:slime_ball", 1, 3, 1.0)],
        esencia_color=((40, 160, 60), (170, 255, 90)),
        desc="Pecado de la Envidia. Una sombra de muchos ojos que te debilita, roba tu fuerza para "
             "sí misma, se teletransporta a tu espalda y se vuelve invisible."),
    "perezoth": dict(
        pecado="pereza", nombre="Perezoth", modelo=modelo_perezoth, escala=1.0,
        vida=60, dano=5, velocidad=0.12, efecto=("slowness", 6), peso=6, xp=15,
        egg=("#6E788C", "#AAB4BE"), extra={"minecraft:knockback_resistance": {"value": 0.6}},
        botin=[("minecraft:white_wool", 1, 3, 1.0), ("minecraft:string", 1, 4, 1.0)],
        esencia_color=((120, 130, 160), (200, 210, 230)),
        desc="Pecado de la Pereza. Lento pero resistente; su aura de sopor ralentiza y cansa a los "
             "jugadores cercanos."),
}

PODERES_ESENCIA = {
    "soberbia": "Resistencia II y Absorción",
    "avaricia": "Suerte y Prisa minera II",
    "lujuria": "Regeneración II",
    "ira": "Fuerza II y Resistencia al fuego",
    "gula": "Saturación y Salud extra",
    "envidia": "Invisibilidad y Visión nocturna",
    "pereza": "Caída lenta y Salto II",
}


# --------------------------------------------------------------------------
# Texturas de objetos e icono del pack
# --------------------------------------------------------------------------

def textura_esencia(c1, c2):
    """Frasco con el líquido del pecado y un remolino de su color secundario."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    borde_v, vidrio = (52, 40, 66), (205, 222, 238)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 10.0
            d = math.hypot(dx, dy)
            if d > 5.3:
                continue
            if d > 4.4:
                col = borde_v
            elif y < 7:
                col = vidrio
            else:
                col = arte.mezclar(arte.mezclar(c1, (255, 255, 255), 0.25), arte.escalar(c1, 0.55), (dy + 3) / 7)
                if (int(round(dx * 1.2 - dy)) % 4) == 0:
                    col = arte.mezclar(col, c2, 0.75)
            img.putpixel((x, y), col + (255,))
    for x in range(6, 10):
        for y in range(3, 6):
            img.putpixel((x, y), (borde_v if x in (6, 9) else vidrio) + (255,))
        img.putpixel((x, 1), (170, 120, 70, 255))
        img.putpixel((x, 2), (130, 85, 45, 255))
    for x, y in ((5, 8), (5, 9), (6, 8), (4, 10)):
        img.putpixel((x, y), (255, 255, 255, 255))
    return img


def textura_corona():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    claro, oro, oscuro, sombra = (255, 240, 150), (240, 196, 50), (180, 128, 28), (110, 72, 14)
    joyas = [p["esencia_color"][0] for p in PECADOS.values()]
    for xc, alto in ((2, 6), (5, 4), (8, 2), (11, 4), (14, 6)):
        for y in range(alto, 9):
            for x in ((xc,) if y < 7 else (xc - 1, xc, xc + 1)):
                if 1 <= x <= 14:
                    img.putpixel((x, y), (claro if x < xc else oro) + (255,))
        img.putpixel((xc, alto - 1), joyas[(xc - 2) // 3 + 1] + (255,))
    for x in range(1, 15):
        for y, c in ((9, claro), (10, oro), (11, oro), (12, oscuro), (13, sombra)):
            img.putpixel((x, y), c + (255,))
    for i, c in enumerate(joyas):
        x = 2 + 2 * i if i < 6 else 13
        img.putpixel((x, 10), arte.mezclar(c, (255, 255, 255), 0.4) + (255,))
        img.putpixel((x, 11), c + (255,))
    return img


def textura_espada():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    colores = [p["esencia_color"][0] for p in PECADOS.values()]
    for t in range(10):
        x, y = 5 + t, 10 - t
        c = colores[min(6, t * 7 // 10)]
        img.putpixel((x, y), c + (255,))
        img.putpixel((x + 1, y), arte.mezclar(c, (255, 255, 255), 0.45) + (255,))
        img.putpixel((x, y - 1), arte.mezclar(c, (255, 255, 255), 0.45) + (255,))
        img.putpixel((x - 1, y), (30, 12, 40, 255))
        img.putpixel((x, y + 1), (30, 12, 40, 255))
    img.putpixel((15, 0), (250, 250, 255, 255))
    for x, y in ((2, 9), (3, 10), (5, 12), (6, 13)):
        img.putpixel((x, y), (240, 196, 50, 255))
    for x, y in ((1, 9), (6, 14), (2, 8), (7, 13)):
        img.putpixel((x, y), (160, 110, 20, 255))
    img.putpixel((4, 11), (230, 30, 60, 255))
    for x, y in ((3, 12), (2, 13)):
        img.putpixel((x, y), (70, 30, 90, 255))
    img.putpixel((1, 14), (170, 80, 230, 255))
    img.putpixel((0, 15), (90, 40, 120, 255))
    return img


def icono_pack(geo_rey, tex_rey):
    """El Rey de los Pecados sobre un fondo con las siete gemas."""
    n = 256
    img = Image.new("RGBA", (n, n))
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - n / 2, y - n / 2) / (n / 1.4)
            img.putpixel((x, y), arte.mezclar((90, 20, 70), (12, 4, 18), d) + (255,))
    dib = ImageDraw.Draw(img)
    for i, p in enumerate(PECADOS.values()):
        a = -math.pi / 2 + i * 2 * math.pi / 7
        cx, cy = n / 2 + 108 * math.cos(a), n / 2 + 108 * math.sin(a)
        c1, c2 = p["esencia_color"]
        dib.ellipse([cx - 13, cy - 13, cx + 13, cy + 13], fill=c1, outline=c2, width=3)
    return arte.renderizar(geo_rey, tex_rey, yaw=-25, pitch=8, escala=3.4, lienzo=img)


# --------------------------------------------------------------------------
# Archivos JSON
# --------------------------------------------------------------------------

def escribir(ruta, datos):
    ruta.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(datos, (dict, list)):
        ruta.write_text(json.dumps(datos, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    else:
        ruta.write_text(datos, encoding="utf-8")


def entidad_bp(clave, p, alto, ancho):
    ataque = {"damage": p["dano"]}
    if p["efecto"]:
        ataque["effect_name"], ataque["effect_duration"] = p["efecto"]
    comps = {
        "minecraft:type_family": {"family": ["pecado", p["pecado"], "monster", "mob"]},
        "minecraft:health": {"value": p["vida"], "max": p["vida"]},
        "minecraft:attack": ataque,
        "minecraft:collision_box": {"width": ancho, "height": alto},
        "minecraft:movement": {"value": p["velocidad"]},
        "minecraft:movement.basic": {},
        "minecraft:navigation.walk": {"can_path_over_water": True, "avoid_damage_blocks": True, "can_open_doors": False},
        "minecraft:jump.static": {},
        "minecraft:can_climb": {},
        "minecraft:physics": {},
        "minecraft:pushable": {"is_pushable": True, "is_pushable_by_piston": True},
        "minecraft:nameable": {},
        "minecraft:breathable": {"total_supply": 15, "suffocate_time": 0},
        "minecraft:despawn": {"despawn_from_distance": {}},
        "minecraft:loot": {"table": f"loot_tables/entities/{clave}.json"},
        "minecraft:experience_reward": {"on_death": f"query.last_hit_by_player ? {p['xp']} : 0"},
        "minecraft:follow_range": {"value": 32, "max": 32},
        "minecraft:behavior.float": {"priority": 0},
        "minecraft:behavior.hurt_by_target": {"priority": 1},
        "minecraft:behavior.nearest_attackable_target": {
            "priority": 2, "must_see": True, "reselect_targets": True,
            "entity_types": [{"filters": {"any_of": [
                {"test": "is_family", "subject": "other", "value": "player"},
                {"test": "is_family", "subject": "other", "value": "irongolem"},
                {"test": "is_family", "subject": "other", "value": "villager"},
            ]}, "max_dist": 24}],
        },
        "minecraft:behavior.melee_attack": {"priority": 3, "speed_multiplier": 1.25, "track_target": True},
        "minecraft:behavior.random_stroll": {"priority": 6, "speed_multiplier": 1.0},
        "minecraft:behavior.look_at_player": {"priority": 7, "look_distance": 8},
        "minecraft:behavior.random_look_around": {"priority": 8},
    }
    comps.update(p["extra"])
    return {
        "format_version": "1.20.80",
        "minecraft:entity": {
            "description": {"identifier": f"{NS}:{clave}", "is_spawnable": True, "is_summonable": True,
                            "is_experimental": False},
            "components": comps,
        },
    }


def entidad_rp(clave, p, visual):
    anims = {k: f"animation.{NS}.{k}" for k in ("caminar", "mirar", "respirar", "atacar")}
    animar = ["caminar", "mirar", "respirar", {"atacar": "variable.attack_time > 0"}]
    for extra in ("capa", "alas", "cola", "flotar", "cabecear", "mandibula", "orbes"):
        if visual.get(extra):
            anims[extra] = f"animation.{NS}.{extra}"
            animar.append(extra)
    efectos = {}
    for clave_fx, loc in (("particulas", "fx"), ("particulas2", "fx2")):
        if visual.get(clave_fx):
            anims[clave_fx] = f"animation.{NS}.{clave}.{clave_fx}"
            animar.append(clave_fx)
            efectos[loc] = visual[clave_fx][0]
    desc = {
        "identifier": f"{NS}:{clave}",
        "materials": {"default": "entity_emissive_alpha"},
        "textures": {"default": f"textures/entity/{NS}/{clave}"},
        "geometry": {"default": f"geometry.{NS}.{clave}"},
        "render_controllers": ["controller.render.default"],
        "animations": anims,
        "scripts": {"scale": str(p["escala"]), "animate": animar},
        "spawn_egg": {"base_color": p["egg"][0], "overlay_color": p["egg"][1]},
    }
    if efectos:
        desc["particle_effects"] = efectos
    return {"format_version": "1.10.0", "minecraft:client_entity": {"description": desc}}


def animaciones_particulas(clave, visual):
    out = {}
    for clave_fx, loc in (("particulas", "fx"), ("particulas2", "fx2")):
        if visual.get(clave_fx):
            out[f"animation.{NS}.{clave}.{clave_fx}"] = {
                "loop": True, "animation_length": visual[clave_fx][1],
                "particle_effects": {"0.0": {"effect": loc, "locator": loc}},
            }
    return out


PASO = "query.modified_distance_moved * 38.17"
ANIMACIONES = {
    f"animation.{NS}.caminar": {"loop": True, "bones": {
        "rightLeg": {"rotation": [f"math.cos({PASO}) * 40.0 * query.modified_move_speed", 0, 0]},
        "leftLeg": {"rotation": [f"math.cos({PASO} + 180) * 40.0 * query.modified_move_speed", 0, 0]},
        "rightArm": {"rotation": [f"math.cos({PASO} + 180) * 35.0 * query.modified_move_speed", 0, 0]},
        "leftArm": {"rotation": [f"math.cos({PASO}) * 35.0 * query.modified_move_speed", 0, 0]},
        "body": {"rotation": [0, f"math.cos({PASO}) * 4.0 * query.modified_move_speed", 0]},
    }},
    f"animation.{NS}.mirar": {"loop": True, "bones": {
        "head": {"rotation": ["query.target_x_rotation", "query.target_y_rotation", 0]}}},
    f"animation.{NS}.respirar": {"loop": True, "bones": {
        "rightArm": {"rotation": [0, 0, "math.sin(query.life_time * 90) * 3 + 3"]},
        "leftArm": {"rotation": [0, 0, "-math.sin(query.life_time * 90) * 3 - 3"]},
        "head": {"position": [0, "math.sin(query.life_time * 90) * 0.2", 0]},
    }},
    f"animation.{NS}.atacar": {"loop": True, "bones": {
        "rightArm": {"rotation": ["-math.sin(variable.attack_time * 180) * 80", 0, 0]},
        "leftArm": {"rotation": ["-math.sin(variable.attack_time * 180) * 40", 0, 0]},
        "body": {"rotation": [0, "math.sin(variable.attack_time * 180) * 12", 0]},
    }},
    f"animation.{NS}.capa": {"loop": True, "bones": {
        "capa": {"rotation": ["6 + math.clamp(query.modified_move_speed * 50, 0, 35) + math.sin(query.life_time * 90) * 2", 0, 0]}}},
    f"animation.{NS}.alas": {"loop": True, "bones": {
        "alaD": {"rotation": [0, "-30 - math.sin(query.life_time * 240) * 18", 0]},
        "alaI": {"rotation": [0, "30 + math.sin(query.life_time * 240) * 18", 0]},
    }},
    f"animation.{NS}.cola": {"loop": True, "bones": {
        "cola": {"rotation": ["math.sin(query.life_time * 100) * 6", "math.sin(query.life_time * 140) * 22", 0]}}},
    f"animation.{NS}.flotar": {"loop": True, "bones": {
        "root": {"position": [0, "2 + math.sin(query.life_time * 120)", 0]},
        "rightLeg": {"rotation": ["12 + math.sin(query.life_time * 120) * 5", 0, 0]},
        "leftLeg": {"rotation": ["12 + math.sin(query.life_time * 120 + 60) * 5", 0, 0]},
    }},
    f"animation.{NS}.cabecear": {"loop": True, "bones": {
        "head": {"rotation": ["12 + math.sin(query.life_time * 45) * 7", 0, "math.sin(query.life_time * 22) * 4"]}}},
    f"animation.{NS}.mandibula": {"loop": True, "bones": {
        "mandibula": {"rotation": ["math.max(0, math.sin(query.life_time * 360)) * 18", 0, 0]}}},
    f"animation.{NS}.orbes": {"loop": True, "bones": {
        "orbes": {"rotation": [0, "query.life_time * 50", 0],
                  "position": [0, "math.sin(query.life_time * 90) * 1.5", 0]}}},
}


def reglas_aparicion(clave, p):
    cond = {
        "minecraft:spawns_on_surface": {},
        "minecraft:spawns_underground": {},
        "minecraft:brightness_filter": {"min": 0, "max": 7, "adjust_for_weather": True},
        "minecraft:difficulty_filter": {"min": "easy", "max": "hard"},
        "minecraft:weight": {"default": p["peso"]},
        "minecraft:herd": {"min_size": 1, "max_size": 1},
        "minecraft:biome_filter": {"test": "has_biome_tag", "value": "monster"},
    }
    conds = [cond]
    if clave == "iratus":
        conds.append({
            "minecraft:spawns_on_surface": {},
            "minecraft:spawns_underground": {},
            "minecraft:difficulty_filter": {"min": "easy", "max": "hard"},
            "minecraft:weight": {"default": 20},
            "minecraft:herd": {"min_size": 1, "max_size": 2},
            "minecraft:biome_filter": {"test": "has_biome_tag", "value": "nether"},
        })
    return {"format_version": "1.8.0", "minecraft:spawn_rules": {
        "description": {"identifier": f"{NS}:{clave}", "population_control": "monster"}, "conditions": conds}}


def botin(p):
    pools = [{
        "rolls": 1,
        "conditions": [{"condition": "killed_by_player"},
                       {"condition": "random_chance_with_looting", "chance": 0.6, "looting_multiplier": 0.1}],
        "entries": [{"type": "item", "name": f"{NS}:esencia_{p['pecado']}", "weight": 1}],
    }]
    for item, mn, mx, prob in p["botin"]:
        pool = {"rolls": 1, "entries": [{
            "type": "item", "name": item, "weight": 1,
            "functions": [{"function": "set_count", "count": {"min": mn, "max": mx}},
                          {"function": "looting_enchant", "count": {"min": 0, "max": 1}}],
        }]}
        if prob < 1:
            pool["conditions"] = [{"condition": "random_chance_with_looting", "chance": prob, "looting_multiplier": 0.05}]
        pools.append(pool)
    return {"pools": pools}


def item_esencia(pecado):
    ident = f"{NS}:esencia_{pecado}"
    return {
        "format_version": "1.21.40",
        "minecraft:item": {
            "description": {"identifier": ident, "menu_category": {"category": "items"}},
            "components": {
                "minecraft:icon": {"textures": {"default": f"{NS}_esencia_{pecado}"}},
                "minecraft:display_name": {"value": f"item.{ident}.name"},
                "minecraft:max_stack_size": 16,
                "minecraft:glint": True,
                "minecraft:use_animation": "drink",
                "minecraft:use_modifiers": {"use_duration": 1.6, "movement_modifier": 0.35},
                "minecraft:food": {"nutrition": 1, "saturation_modifier": 0.1, "can_always_eat": True},
            },
        },
    }


# --------------------------------------------------------------------------
# El jefe: Rey de los Pecados
# --------------------------------------------------------------------------

REY = "rey_pecados"


REY_DATOS = dict(
    pecado="rey", nombre="Rey de los Pecados", escala=1.0, vida=400, dano=10, velocidad=0.27,
    efecto=("wither", 4), xp=500, egg=("#230F2D", "#F0C32D"),
    extra={
        "minecraft:fire_immune": True,
        "minecraft:knockback_resistance": {"value": 1.0},
        "minecraft:boss": {"hud_range": 64, "name": "Rey de los Pecados", "should_darken_sky": True},
        "minecraft:inventory": {"container_type": "inventory", "inventory_size": 27},
        "minecraft:follow_range": {"value": 48, "max": 48},
        "minecraft:persistent": {},
    },
)


def botin_rey():
    fijo = [(f"{NS}:espada_pecados", 1, 1), ("minecraft:nether_star", 1, 1), ("minecraft:diamond", 3, 6),
            ("minecraft:enchanted_golden_apple", 1, 1), ("minecraft:totem_of_undying", 1, 1)]
    fijo += [(f"{NS}:esencia_{p['pecado']}", 1, 2) for p in PECADOS.values()]
    return {"pools": [{"rolls": 1, "entries": [{
        "type": "item", "name": item, "weight": 1,
        "functions": [{"function": "set_count", "count": {"min": mn, "max": mx}}]}]} for item, mn, mx in fijo]}


def item_simple(ident, extra):
    comps = {
        "minecraft:icon": {"textures": {"default": ident.replace(":", "_")}},
        "minecraft:display_name": {"value": f"item.{ident}.name"},
        "minecraft:glint": True,
    }
    comps.update(extra)
    return {"format_version": "1.21.40", "minecraft:item": {
        "description": {"identifier": ident, "menu_category": {"category": "equipment"}}, "components": comps}}


def receta_corona():
    letras = {"S": "soberbia", "A": "avaricia", "L": "lujuria", "I": "ira",
              "U": "gula", "E": "envidia", "P": "pereza"}
    clave = {k: {"item": f"{NS}:esencia_{v}"} for k, v in letras.items()}
    clave["G"] = {"item": "minecraft:gold_block"}
    clave["D"] = {"item": "minecraft:diamond"}
    return {"format_version": "1.20.10", "minecraft:recipe_shaped": {
        "description": {"identifier": f"{NS}:corona_pecados"},
        "tags": ["crafting_table"],
        "pattern": ["SAL", "IGU", "EDP"],
        "key": clave,
        "result": {"item": f"{NS}:corona_pecados", "count": 1},
    }}


def idioma(lengua):
    es = lengua.startswith("es")
    lineas = [f"## Los 7 Pecados Capitales ({lengua})"]
    pecado_txt = {
        "soberbia": "Soberbia" if es else "Pride", "avaricia": "Avaricia" if es else "Greed",
        "lujuria": "Lujuria" if es else "Lust", "ira": "Ira" if es else "Wrath",
        "gula": "Gula" if es else "Gluttony", "envidia": "Envidia" if es else "Envy",
        "pereza": "Pereza" if es else "Sloth",
    }
    for clave, p in PECADOS.items():
        lineas.append(f"entity.{NS}:{clave}.name={p['nombre']}")
        lineas.append(f"item.spawn_egg.entity.{NS}:{clave}.name={'Generar' if es else 'Spawn'} {p['nombre']}")
    for pecado, txt in pecado_txt.items():
        lineas.append(f"item.{NS}:esencia_{pecado}.name={'Esencia de' if es else 'Essence of'} {txt}")
    rey = "Rey de los Pecados" if es else "King of Sins"
    lineas += [f"entity.{NS}:{REY}.name={rey}",
               f"item.spawn_egg.entity.{NS}:{REY}.name={'Generar' if es else 'Spawn'} {rey}",
               f"item.{NS}:corona_pecados.name=" + ("Corona de los Pecados" if es else "Crown of Sins"),
               f"item.{NS}:espada_pecados.name=" + ("Espada de los Siete Pecados" if es else "Sword of the Seven Sins")]
    lineas += ["pack.name=Los 7 Pecados Capitales",
               "pack.description=" + ("7 criaturas, una por cada pecado capital." if es
                                       else "7 creatures, one for each deadly sin.")]
    return "\n".join(lineas) + "\n"


def manifiestos():
    bp = {
        "format_version": 2,
        "header": {"name": "Los 7 Pecados Capitales (BP)", "description": "pack.description",
                   "uuid": UUID_BP_HEADER, "version": VERSION, "min_engine_version": MOTOR_MIN},
        "modules": [
            {"type": "data", "uuid": UUID_BP_DATA, "version": VERSION},
            {"type": "script", "language": "javascript", "uuid": UUID_BP_SCRIPT, "version": VERSION,
             "entry": "scripts/main.js"},
        ],
        "dependencies": [
            {"uuid": UUID_RP_HEADER, "version": VERSION},
            {"module_name": "@minecraft/server", "version": "1.11.0"},
        ],
    }
    rp = {
        "format_version": 2,
        "header": {"name": "Los 7 Pecados Capitales (RP)", "description": "pack.description",
                   "uuid": UUID_RP_HEADER, "version": VERSION, "min_engine_version": MOTOR_MIN},
        "modules": [{"type": "resources", "uuid": UUID_RP_RES, "version": VERSION}],
        "dependencies": [{"uuid": UUID_BP_HEADER, "version": VERSION}],
    }
    return bp, rp


def main():
    for d in (BP, RP, DIST):
        if d.exists():
            # el script lo mantenemos a mano; solo regeneramos lo demás
            for hijo in d.iterdir():
                if not (d == BP and hijo.name == "scripts"):
                    shutil.rmtree(hijo) if hijo.is_dir() else hijo.unlink()
    rng = random.Random(1337)

    texturas_items = {}
    animaciones = dict(ANIMACIONES)
    for clave, p in PECADOS.items():
        huesos, alto, ancho, visual = p["modelo"]()
        geo, img = arte.generar_modelo(clave, huesos, NS, rng)
        escribir(RP / "models" / "entity" / f"{clave}.geo.json", geo)
        (RP / "textures" / "entity" / NS).mkdir(parents=True, exist_ok=True)
        img.save(RP / "textures" / "entity" / NS / f"{clave}.png")
        escribir(RP / "entity" / f"{clave}.entity.json", entidad_rp(clave, p, visual))
        animaciones.update(animaciones_particulas(clave, visual))

        escribir(BP / "entities" / f"{clave}.json", entidad_bp(clave, p, alto, ancho))
        escribir(BP / "spawn_rules" / f"{clave}.json", reglas_aparicion(clave, p))
        escribir(BP / "loot_tables" / "entities" / f"{clave}.json", botin(p))

        pecado = p["pecado"]
        escribir(BP / "items" / f"esencia_{pecado}.json", item_esencia(pecado))
        (RP / "textures" / "items").mkdir(parents=True, exist_ok=True)
        textura_esencia(*p["esencia_color"]).save(RP / "textures" / "items" / f"esencia_{pecado}.png")
        texturas_items[f"{NS}_esencia_{pecado}"] = {"textures": f"textures/items/esencia_{pecado}"}

    huesos, alto, ancho, visual = modelo_rey(list(PECADOS.values()))
    geo_rey, tex_rey = arte.generar_modelo(REY, huesos, NS, rng)
    escribir(RP / "models" / "entity" / f"{REY}.geo.json", geo_rey)
    tex_rey.save(RP / "textures" / "entity" / NS / f"{REY}.png")
    escribir(RP / "entity" / f"{REY}.entity.json", entidad_rp(REY, REY_DATOS, visual))
    animaciones.update(animaciones_particulas(REY, visual))
    jefe = entidad_bp(REY, REY_DATOS, alto, ancho)
    del jefe["minecraft:entity"]["components"]["minecraft:despawn"]
    escribir(BP / "entities" / f"{REY}.json", jefe)
    escribir(BP / "loot_tables" / "entities" / f"{REY}.json", botin_rey())
    escribir(BP / "recipes" / "corona_pecados.json", receta_corona())
    escribir(BP / "items" / "corona_pecados.json", item_simple(f"{NS}:corona_pecados", {
        "minecraft:max_stack_size": 1,
    }))
    escribir(BP / "items" / "espada_pecados.json", item_simple(f"{NS}:espada_pecados", {
        "minecraft:max_stack_size": 1,
        "minecraft:hand_equipped": True,
        "minecraft:damage": 11,
        "minecraft:enchantable": {"slot": "sword", "value": 22},
        "minecraft:can_destroy_in_creative": False,
    }))
    for nombre, tex in (("corona_pecados", textura_corona()), ("espada_pecados", textura_espada())):
        tex.save(RP / "textures" / "items" / f"{nombre}.png")
        texturas_items[f"{NS}_{nombre}"] = {"textures": f"textures/items/{nombre}"}

    escribir(RP / "textures" / "item_texture.json",
             {"resource_pack_name": NS, "texture_name": "atlas.items", "texture_data": texturas_items})
    escribir(RP / "animations" / f"{NS}.animation.json", {"format_version": "1.8.0", "animations": animaciones})

    icono = icono_pack(geo_rey, tex_rey)
    lenguas = ["es_ES", "es_MX", "en_US"]
    for pack in (BP, RP):
        escribir(pack / "texts" / "languages.json", lenguas)
        for l in lenguas:
            escribir(pack / "texts" / f"{l}.lang", idioma(l))
        icono.save(pack / "pack_icon.png")

    bp, rp = manifiestos()
    escribir(BP / "manifest.json", bp)
    escribir(RP / "manifest.json", rp)

    DIST.mkdir(exist_ok=True)
    salida = DIST / "LosSietePecados.mcaddon"
    with zipfile.ZipFile(salida, "w", zipfile.ZIP_DEFLATED) as z:
        for pack, nombre in ((BP, "SietePecados_BP"), (RP, "SietePecados_RP")):
            for f in sorted(pack.rglob("*")):
                if f.is_file():
                    z.write(f, f"{nombre}/{f.relative_to(pack)}")
    print(f"Listo: {salida.relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
