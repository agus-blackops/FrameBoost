#!/usr/bin/env python3
"""Genera el addon "Los 7 Pecados Capitales" para Minecraft Bedrock.

Produce el Behavior Pack, el Resource Pack (modelos, texturas, animaciones,
idiomas) y el archivo .mcaddon listo para importar.

    python3 tools/generar.py
"""
import json
import random
import shutil
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

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
VERSION = [1, 0, 0]
MOTOR_MIN = [1, 21, 40]


# --------------------------------------------------------------------------
# Modelos (en píxeles: 16 = 1 bloque). El frente del mob mira a -Z (norte).
# --------------------------------------------------------------------------

def cubo(origen, tam, color, deco=None):
    return {"origin": list(origen), "size": list(tam), "color": color, "deco": deco}


def bipedo(pierna_h, pierna_w, cuerpo, cabeza, brazo, col):
    """Esqueleto humanoide base. Devuelve {hueso: (pivote, [cubos])}."""
    bw, bh, bd = cuerpo
    hw, hh, hd = cabeza
    aw, ah, ad = brazo
    cima = pierna_h + bh
    huesos = {
        "body": ([0, cima, 0], [cubo([-bw / 2, pierna_h, -bd / 2], cuerpo, col["cuerpo"], col.get("deco_cuerpo"))]),
        "head": ([0, cima, 0], [cubo([-hw / 2, cima, -hd / 2], cabeza, col["piel"], col.get("cara"))]),
    }
    for nombre, signo in (("rightLeg", -1), ("leftLeg", 1)):
        cx = signo * bw / 4
        huesos[nombre] = ([cx, pierna_h, 0],
                          [cubo([cx - pierna_w / 2, 0, -pierna_w / 2], [pierna_w, pierna_h, pierna_w], col["piernas"])])
    for nombre, signo in (("rightArm", -1), ("leftArm", 1)):
        cx = signo * (bw / 2 + aw / 2)
        huesos[nombre] = ([cx, cima - 2, 0],
                          [cubo([cx - aw / 2, cima - ah, -ad / 2], brazo, col.get("brazos", col["piel"]))])
    return huesos


# ---- decoraciones de la cara frontal (x, y, w, h = rectángulo de la cara) ----

def ojos(color, fila=3, tam=1, sep=None, pupila=None):
    def pintar(d, x, y, w, h):
        s = sep if sep is not None else max(1, w // 4)
        cx = x + w // 2
        for ox in (cx - s - tam, cx + s):
            d.rectangle([ox, y + fila, ox + tam - 1, y + fila + tam - 1], fill=color)
            if pupila:
                d.point((ox + tam // 2, y + fila + tam // 2), fill=pupila)
    return pintar


def boca(color, fila, ancho, alto=1, dientes=None):
    def pintar(d, x, y, w, h):
        x0 = x + (w - ancho) // 2
        d.rectangle([x0, y + fila, x0 + ancho - 1, y + fila + alto - 1], fill=color)
        if dientes:
            for i in range(0, ancho, 2):
                d.point((x0 + i, y + fila), fill=dientes)
    return pintar


def corazon(color, fila=2):
    patron = ["0110110", "1111111", "1111111", "0111110", "0011100", "0001000"]

    def pintar(d, x, y, w, h):
        x0 = x + (w - 7) // 2
        for j, linea in enumerate(patron):
            for i, c in enumerate(linea):
                if c == "1" and x0 + i < x + w:
                    d.point((x0 + i, y + fila + j), fill=color)
    return pintar


def franja(color, fila, grosor=1):
    def pintar(d, x, y, w, h):
        d.rectangle([x, y + fila, x + w - 1, y + fila + grosor - 1], fill=color)
    return pintar


def muchos_ojos(color, pupila):
    def pintar(d, x, y, w, h):
        rng = random.Random(7)
        for _ in range(6):
            ox, oy = x + rng.randrange(0, w - 1), y + rng.randrange(1, h - 2)
            d.rectangle([ox, oy, ox + 1, oy], fill=color)
            d.point((ox + 1, oy), fill=pupila)
    return pintar


def ojos_dormidos(color, fila):
    def pintar(d, x, y, w, h):
        cx = x + w // 2
        d.line([cx - 3, y + fila, cx - 1, y + fila], fill=color)
        d.line([cx + 1, y + fila, cx + 3, y + fila], fill=color)
    return pintar


def varias(*fns):
    def pintar(d, x, y, w, h):
        for f in fns:
            f(d, x, y, w, h)
    return pintar


# ---- los siete pecados ----

def modelo_superbius():
    c = {"cuerpo": (110, 40, 160), "piel": (225, 205, 235), "piernas": (70, 25, 110), "brazos": (110, 40, 160),
         "cara": varias(ojos((250, 210, 40), fila=3, tam=1, sep=1), boca((120, 60, 120), 6, 3)),
         "deco_cuerpo": varias(franja((230, 190, 40), 0, 2), franja((230, 190, 40), 9))}
    h = bipedo(14, 4, (10, 14, 6), (8, 8, 8), (4, 14, 4), c)
    oro = (235, 195, 45)
    cima = 14 + 14 + 8
    h["head"][1].extend([
        cubo([-5, cima - 1, -5], [10, 2, 10], oro),
        cubo([-1, cima + 1, -5], [2, 3, 1], oro, franja((200, 30, 60), 0)),
        cubo([-5, cima + 1, -5], [1, 2, 1], oro), cubo([4, cima + 1, -5], [1, 2, 1], oro),
        cubo([-5, cima + 1, 4], [1, 2, 1], oro), cubo([4, cima + 1, 4], [1, 2, 1], oro),
    ])
    h["body"][1].append(cubo([-6, 9, 3], [12, 19, 1], (150, 20, 40), franja(oro, 0)))
    return h, 2.5, 0.8


def modelo_iratus():
    c = {"cuerpo": (170, 30, 20), "piel": (150, 25, 15), "piernas": (45, 20, 20), "brazos": (170, 30, 20),
         "cara": varias(ojos((255, 220, 40), fila=3, tam=2, sep=1),
                        franja((60, 0, 0), 2),
                        boca((40, 0, 0), 5, 6, 2, dientes=(240, 240, 220))),
         "deco_cuerpo": varias(franja((255, 120, 20), 4), franja((255, 120, 20), 8))}
    h = bipedo(12, 5, (12, 12, 7), (8, 8, 8), (6, 14, 6), c)
    hueso = (235, 225, 200)
    cima = 12 + 12 + 8
    h["head"][1].extend([
        cubo([-6, cima - 3, -1], [2, 2, 2], hueso), cubo([-7, cima - 2, -1], [2, 5, 2], hueso),
        cubo([4, cima - 3, -1], [2, 2, 2], hueso), cubo([5, cima - 2, -1], [2, 5, 2], hueso),
    ])
    return h, 2.3, 0.9


def modelo_avaricity():
    oro = (245, 200, 40)
    c = {"cuerpo": (90, 60, 30), "piel": (120, 175, 60), "piernas": (70, 45, 25), "brazos": (120, 175, 60),
         "cara": varias(ojos(oro, fila=2, tam=2, sep=1, pupila=(30, 30, 0)), boca((40, 60, 10), 5, 4, 1, dientes=oro)),
         "deco_cuerpo": varias(franja(oro, 1), franja(oro, 3))}
    h = bipedo(5, 3, (6, 6, 4), (8, 7, 7), (2, 6, 2), c)
    cima = 5 + 6
    h["head"][1].extend([
        cubo([-7, cima + 3, 0], [3, 2, 1], (120, 175, 60)),
        cubo([4, cima + 3, 0], [3, 2, 1], (120, 175, 60)),
        cubo([-1, cima + 2, -5.5], [2, 2, 2], (100, 150, 50)),
    ])
    h["leftArm"][1].append(cubo([2.5, 0, -2], [4, 4, 4], (130, 90, 40), varias(franja(oro, 0), ojos(oro, 2, 1, 0))))
    return h, 1.2, 0.6


def modelo_lujurius():
    rosa = (240, 100, 170)
    c = {"cuerpo": (180, 30, 120), "piel": (250, 205, 225), "piernas": (150, 20, 100), "brazos": (250, 205, 225),
         "cara": varias(ojos((190, 20, 130), fila=3, tam=1, sep=1), boca((220, 40, 90), 6, 2)),
         "deco_cuerpo": corazon((255, 60, 120), 2)}
    h = bipedo(13, 3, (6, 12, 3), (8, 8, 8), (2, 12, 2), c)
    h["head"][1].append(cubo([-4, 22, 4], [8, 11, 2], rosa))
    h["head"][1].append(cubo([-4.5, 33, -4.5], [9, 1, 9], rosa))
    h["body"][1].extend([
        cubo([-10, 15, 2], [7, 9, 1], (120, 10, 80), franja(rosa, 0)),
        cubo([3, 15, 2], [7, 9, 1], (120, 10, 80), franja(rosa, 0)),
    ])
    return h, 2.1, 0.6


def modelo_glotonix():
    c = {"cuerpo": (220, 130, 50), "piel": (230, 150, 70), "piernas": (130, 80, 40), "brazos": (230, 150, 70),
         "cara": varias(ojos((40, 20, 10), fila=2, tam=1, sep=2),
                        boca((110, 10, 20), 4, 8, 3, dientes=(250, 250, 230)))}
    h = bipedo(6, 5, (16, 14, 12), (10, 8, 9), (4, 10, 4), c)
    h["body"][1].append(cubo([-6, 8, -8], [12, 10, 2], (240, 170, 100), franja((200, 110, 40), 5)))
    h["head"][1].append(cubo([-1, 21, -5.5], [2, 1, 1], (150, 200, 255)))
    return h, 1.8, 1.2


def modelo_envidium():
    verde = (30, 90, 40)
    c = {"cuerpo": verde, "piel": (20, 60, 30), "piernas": (15, 45, 20), "brazos": verde,
         "cara": muchos_ojos((160, 255, 80), (0, 30, 0)),
         "deco_cuerpo": muchos_ojos((160, 255, 80), (0, 30, 0))}
    h = bipedo(14, 2, (6, 14, 3), (7, 9, 7), (2, 16, 2), c)
    h["head"][1].append(cubo([-1, 37, -1], [2, 2, 2], (100, 200, 60)))
    return h, 2.4, 0.6


def modelo_perezoth():
    c = {"cuerpo": (110, 120, 140), "piel": (170, 180, 190), "piernas": (90, 95, 110), "brazos": (110, 120, 140),
         "cara": varias(franja((90, 95, 110), 0, 2), ojos_dormidos((30, 30, 40), 3), boca((60, 50, 60), 5, 2)),
         "deco_cuerpo": franja((140, 150, 165), 6, 3)}
    h = bipedo(6, 4, (10, 10, 8), (8, 7, 7), (3, 15, 3), c)
    h["head"][1].append(cubo([-5, 20, -1], [10, 4, 6], (90, 95, 110)))
    return h, 1.5, 0.9


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
# Texturas con UV de caja
# --------------------------------------------------------------------------

def caras_uv(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def matiz(color, f):
    return tuple(max(0, min(255, int(c * f))) for c in color)


def generar_modelo(clave, huesos, rng):
    ancho_tex = 128
    cubos = [c for _, (_, cs) in huesos.items() for c in cs]
    # empaquetado en estanterías, los más altos primero
    orden = sorted(cubos, key=lambda c: -(c["size"][2] + c["size"][1]))
    x = y = fila_h = 0
    for c in orden:
        w, h, d = (int(round(s)) for s in c["size"])
        rw, rh = 2 * (d + w), d + h
        if x + rw > ancho_tex:
            x, y, fila_h = 0, y + fila_h, 0
        c["uv"] = [x, y]
        x += rw
        fila_h = max(fila_h, rh)
    alto_tex = 32
    while alto_tex < y + fila_h:
        alto_tex *= 2

    img = Image.new("RGBA", (ancho_tex, alto_tex), (0, 0, 0, 0))
    dib = ImageDraw.Draw(img)
    for c in cubos:
        w, h, d = (int(round(s)) for s in c["size"])
        for nombre, (fx, fy, fw, fh) in caras_uv(*c["uv"], w, h, d).items():
            f = {"top": 1.15, "bottom": 0.7, "front": 1.0, "back": 0.85}.get(nombre, 0.92)
            for px in range(fx, fx + fw):
                for py in range(fy, fy + fh):
                    img.putpixel((px, py), matiz(c["color"], f * rng.uniform(0.9, 1.08)) + (255,))
            if nombre == "front" and c["deco"]:
                c["deco"](dib, fx, fy, fw, fh)

    alto_px = max(c["origin"][1] + c["size"][1] for c in cubos)
    bones = [{"name": "root", "pivot": [0, 0, 0]}]
    for nombre, (pivote, cs) in huesos.items():
        bones.append({
            "name": nombre, "parent": "root", "pivot": pivote,
            "cubes": [{"origin": c["origin"], "size": c["size"], "uv": c["uv"]} for c in cs],
        })
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{NS}.{clave}",
                "texture_width": ancho_tex, "texture_height": alto_tex,
                "visible_bounds_width": 3, "visible_bounds_height": alto_px / 16 + 1,
                "visible_bounds_offset": [0, alto_px / 32, 0],
            },
            "bones": bones,
        }],
    }
    return geo, img


def textura_esencia(c1, c2):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (13, 7), (8, 15), (3, 7)], fill=c1 + (255,), outline=matiz(c1, 0.55) + (255,))
    d.polygon([(8, 3), (11, 7), (8, 12), (5, 7)], fill=c2 + (255,))
    d.point((7, 5), fill=(255, 255, 255, 255))
    d.point((6, 6), fill=(255, 255, 255, 255))
    return img


def icono_pack():
    img = Image.new("RGBA", (128, 128), (20, 10, 25, 255))
    d = ImageDraw.Draw(img)
    import math
    for i, p in enumerate(PECADOS.values()):
        ang = -math.pi / 2 + i * 2 * math.pi / 7
        cx, cy = 64 + 40 * math.cos(ang), 64 + 40 * math.sin(ang)
        c1, c2 = p["esencia_color"]
        d.ellipse([cx - 13, cy - 13, cx + 13, cy + 13], fill=c1, outline=c2, width=3)
    d.regular_polygon((64, 64, 20), 7, fill=(140, 0, 30), outline=(240, 200, 60))
    return img


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


def entidad_rp(clave, p):
    return {
        "format_version": "1.10.0",
        "minecraft:client_entity": {
            "description": {
                "identifier": f"{NS}:{clave}",
                "materials": {"default": "entity_alphatest"},
                "textures": {"default": f"textures/entity/{NS}/{clave}"},
                "geometry": {"default": f"geometry.{NS}.{clave}"},
                "render_controllers": ["controller.render.default"],
                "animations": {
                    "caminar": f"animation.{NS}.caminar",
                    "mirar": f"animation.{NS}.mirar",
                    "respirar": f"animation.{NS}.respirar",
                },
                "scripts": {"scale": str(p["escala"]), "animate": ["caminar", "mirar", "respirar"]},
                "spawn_egg": {"base_color": p["egg"][0], "overlay_color": p["egg"][1]},
            },
        },
    }


ANIMACIONES = {
    "format_version": "1.8.0",
    "animations": {
        f"animation.{NS}.caminar": {
            "loop": True,
            "bones": {
                "rightLeg": {"rotation": ["math.cos(query.modified_distance_moved * 38.17) * 40.0 * query.modified_move_speed", 0, 0]},
                "leftLeg": {"rotation": ["math.cos(query.modified_distance_moved * 38.17 + 180) * 40.0 * query.modified_move_speed", 0, 0]},
                "rightArm": {"rotation": ["math.cos(query.modified_distance_moved * 38.17 + 180) * 35.0 * query.modified_move_speed", 0, 0]},
                "leftArm": {"rotation": ["math.cos(query.modified_distance_moved * 38.17) * 35.0 * query.modified_move_speed", 0, 0]},
            },
        },
        f"animation.{NS}.mirar": {
            "loop": True,
            "bones": {"head": {"rotation": ["query.target_x_rotation", "query.target_y_rotation", 0]}},
        },
        f"animation.{NS}.respirar": {
            "loop": True,
            "bones": {
                "rightArm": {"rotation": [0, 0, "math.sin(query.life_time * 90) * 3 + 3"]},
                "leftArm": {"rotation": [0, 0, "-math.sin(query.life_time * 90) * 3 - 3"]},
            },
        },
    },
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


def modelo_rey():
    oro = (240, 195, 45)
    c = {"cuerpo": (35, 15, 45), "piel": (60, 20, 50), "piernas": (25, 10, 30), "brazos": (35, 15, 45),
         "cara": varias(franja((20, 5, 20), 2, 2),
                        ojos((255, 30, 30), fila=3, tam=2, sep=1, pupila=(255, 230, 120)),
                        boca((10, 0, 10), 7, 6, 2, dientes=(240, 235, 210))),
         "deco_cuerpo": varias(*[franja(p["esencia_color"][0], 2 + 2 * i) for i, p in enumerate(PECADOS.values())])}
    h = bipedo(18, 6, (16, 20, 8), (10, 10, 10), (6, 20, 6), c)
    cima = 18 + 20 + 10
    hueso = (225, 215, 195)
    joyas = [p["esencia_color"][0] for p in PECADOS.values()]
    h["head"][1].extend([
        cubo([-6, cima - 1, -6], [12, 2, 12], oro),
        # siete puntas de la corona, cada una con la gema de su pecado
        *[cubo([-6 + round(i * 11 / 6), cima + 1, -6], [1, 3, 1], joyas[i])
          for i in range(7)],
        cubo([-6, cima + 1, 5], [12, 2, 1], oro),
        cubo([-8, cima - 4, -1], [2, 2, 2], hueso), cubo([-9, cima - 3, -1], [2, 6, 2], hueso),
        cubo([6, cima - 4, -1], [2, 2, 2], hueso), cubo([7, cima - 3, -1], [2, 6, 2], hueso),
    ])
    h["body"][1].extend([
        cubo([-9, 12, 4], [18, 26, 1], (120, 10, 30), franja(oro, 0, 2)),
        cubo([-11, 36, -5], [6, 3, 10], oro), cubo([5, 36, -5], [6, 3, 10], oro),
    ])
    h["rightArm"][1].append(cubo([-13, 0, -1], [2, 22, 2], (150, 60, 210), franja((255, 40, 40), 0, 3)))
    return h, 3.4, 1.3


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


def textura_corona():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    oro, sombra = (245, 200, 45, 255), (170, 120, 20, 255)
    d.rectangle([2, 9, 13, 13], fill=oro, outline=sombra)
    for i, x in enumerate((2, 4, 6, 8, 10, 12, 13)):
        d.line([x, 4 + (i % 2) * 2, x, 9], fill=oro)
    for i, p in enumerate(PECADOS.values()):
        img.putpixel((2 + i * 2 - (1 if i == 6 else 0), 11), p["esencia_color"][0] + (255,))
    return img


def textura_espada():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    colores = [p["esencia_color"][0] for p in PECADOS.values()]
    for i in range(10):
        x, y = 5 + i, 10 - i
        img.putpixel((x, y), colores[i % 7] + (255,))
        img.putpixel((x - 1, y), (40, 15, 50, 255))
        img.putpixel((x, y + 1), (90, 40, 110, 255))
    for x, y in ((2, 11), (3, 12), (4, 13), (5, 12), (6, 13), (3, 10)):
        img.putpixel((x, y), (240, 195, 45, 255))
    for x, y in ((1, 14), (2, 13)):
        img.putpixel((x, y), (90, 50, 20, 255))
    return img


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
    for clave, p in PECADOS.items():
        huesos, alto, ancho = p["modelo"]()
        geo, img = generar_modelo(clave, huesos, rng)
        escribir(RP / "models" / "entity" / f"{clave}.geo.json", geo)
        (RP / "textures" / "entity" / NS).mkdir(parents=True, exist_ok=True)
        img.save(RP / "textures" / "entity" / NS / f"{clave}.png")
        escribir(RP / "entity" / f"{clave}.entity.json", entidad_rp(clave, p))

        escribir(BP / "entities" / f"{clave}.json", entidad_bp(clave, p, alto, ancho))
        escribir(BP / "spawn_rules" / f"{clave}.json", reglas_aparicion(clave, p))
        escribir(BP / "loot_tables" / "entities" / f"{clave}.json", botin(p))

        pecado = p["pecado"]
        escribir(BP / "items" / f"esencia_{pecado}.json", item_esencia(pecado))
        (RP / "textures" / "items").mkdir(parents=True, exist_ok=True)
        textura_esencia(*p["esencia_color"]).save(RP / "textures" / "items" / f"esencia_{pecado}.png")
        texturas_items[f"{NS}_esencia_{pecado}"] = {"textures": f"textures/items/esencia_{pecado}"}

    huesos, alto, ancho = modelo_rey()
    geo, img = generar_modelo(REY, huesos, rng)
    escribir(RP / "models" / "entity" / f"{REY}.geo.json", geo)
    img.save(RP / "textures" / "entity" / NS / f"{REY}.png")
    escribir(RP / "entity" / f"{REY}.entity.json", entidad_rp(REY, REY_DATOS))
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
    escribir(RP / "animations" / f"{NS}.animation.json", ANIMACIONES)

    lenguas = ["es_ES", "es_MX", "en_US"]
    for pack in (BP, RP):
        escribir(pack / "texts" / "languages.json", lenguas)
        for l in lenguas:
            escribir(pack / "texts" / f"{l}.lang", idioma(l))
        icono_pack().save(pack / "pack_icon.png")

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
