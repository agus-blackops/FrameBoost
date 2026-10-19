"""Modelos de las criaturas. Cada función devuelve (huesos, alto, ancho, visual):
alto y ancho son la caja de colisión en bloques; visual dice qué animaciones
extra y partículas usa la criatura."""
import math

from arte import BRILLO, Mat, borde, cubo, dibujo, espejo, franja, hueso, varias

ORO = Mat((236, 188, 48), "metal")
HUESO = Mat((232, 222, 198), "hueso")
BLANCO = (250, 250, 250)


def gema(color, brilla=True):
    return Mat(color, "gema", brilla=brilla, contorno=1.0)


def todas(p):
    """Mismo dibujo en las cuatro caras laterales."""
    return {"front": p, "back": p, "left": p, "right": p}


def bipedo(pierna, cuerpo, cabeza, brazo, mats, cara=None, pecho=None, espalda=None,
           mano=None, bota=None, cabeza_z=0.0, cabeza_y=0.0):
    """Esqueleto humanoide. pierna=(alto, ancho); cuerpo/cabeza/brazo=(w, h, d).
    mano/bota=(alto, Mat) parten el brazo y la pierna en dos piezas."""
    ph, pw = pierna
    bw, bh, bd = cuerpo
    hw, hh, hd = cabeza
    aw, ah, ad = brazo
    cima = ph + bh
    h = {
        "body": hueso([0, cima, 0], [cubo([-bw / 2, ph, -bd / 2], cuerpo, mats["cuerpo"],
                                          {k: v for k, v in (("front", pecho), ("back", espalda)) if v})]),
        "head": hueso([0, cima, cabeza_z], [cubo([-hw / 2, cima + cabeza_y, -hd / 2 + cabeza_z], cabeza,
                                                 mats["cabeza"], {"front": cara} if cara else {})]),
    }
    for nombre, s in (("rightLeg", -1), ("leftLeg", 1)):
        cx = s * bw / 4
        cs = []
        if bota:
            bh_, bm = bota
            cs.append(cubo([cx - pw / 2, 0, -pw / 2], [pw, bh_, pw], bm, inflar=0.25))
            cs.append(cubo([cx - pw / 2, bh_, -pw / 2], [pw, ph - bh_, pw], mats["piernas"]))
        else:
            cs.append(cubo([cx - pw / 2, 0, -pw / 2], [pw, ph, pw], mats["piernas"]))
        h[nombre] = hueso([cx, ph, 0], cs)
    for nombre, s in (("rightArm", -1), ("leftArm", 1)):
        cx = s * (bw / 2 + aw / 2)
        cs = []
        if mano:
            mh, mm = mano
            cs.append(cubo([cx - aw / 2, cima - ah, -ad / 2], [aw, mh, ad], mm))
            cs.append(cubo([cx - aw / 2, cima - ah + mh, -ad / 2], [aw, ah - mh, ad], mats["brazos"]))
        else:
            cs.append(cubo([cx - aw / 2, cima - ah, -ad / 2], brazo, mats["brazos"]))
        h[nombre] = hueso([cx, cima - 2, 0], cs)
    return h, cima


def par(h, nombre, c):
    """Añade un cubo al hueso y su reflejo al hueso simétrico (o al mismo)."""
    otro = {"rightArm": "leftArm", "rightLeg": "leftLeg"}.get(nombre, nombre)
    h[nombre]["cubos"].append(c)
    h[otro]["cubos"].append(espejo(c))


def cuerno(h, base, alto, mat, curva=1):
    """Cuerno curvo de tres piezas en el lado derecho (-X) y su reflejo."""
    x, y, z = base
    piezas = [
        cubo([x - 3, y, z - 1], [3, 2, 2], mat),
        cubo([x - 4, y + 1, z - 1], [2, alto, 2], mat, rot=[0, 0, 15 * curva], piv=[x - 3, y + 1, z]),
        cubo([x - 4.5, y + alto, z - 0.5], [1, alto - 1, 1], mat, rot=[0, 0, -10 * curva], piv=[x - 4, y + alto, z]),
    ]
    for c in piezas:
        par(h, "head", c)


# --------------------------------------------------------------------------

def modelo_superbius():
    piel = Mat((238, 210, 198), "piel")
    ropa = Mat((105, 40, 160), "tela")
    ropa_osc = Mat((62, 22, 100), "tela")
    pelo = Mat((214, 214, 232), "pelo")
    capa = Mat((165, 22, 48), "tela")
    o, r = (240, 196, 50), (225, 30, 60, 1)
    cara = dibujo([
        "........",
        "........",
        ".bbb.bb.",
        ".we..ew.",
        "........",
        "...nn...",
        "....mmm.",
        "........",
    ], {"b": (95, 70, 115), "w": BLANCO, "e": (240, 190, 40, 1), "n": (214, 182, 170), "m": (160, 80, 90)})
    pecho = dibujo([
        "oo......oo",
        ".oo....oo.",
        "..oooooo..",
        "....oo....",
        "...oRRo...",
        "...oRRo...",
        "....oo....",
        "....oo....",
        "....oo....",
        "....oo....",
    ], {"o": o, "R": r})
    h, cima = bipedo((14, 4), (10, 14, 6), (8, 8, 8), (4, 14, 4),
                     {"cuerpo": ropa, "cabeza": piel, "piernas": ropa_osc, "brazos": ropa},
                     cara=cara, pecho=pecho, mano=(3, piel), bota=(3, Mat((40, 25, 50), "armadura", acento=o)))
    T = cima + 8
    h["head"]["cubos"] += [
        cubo([-4, T - 2, -4], [8, 2, 8], pelo, inflar=0.3),
        cubo([-4, T - 7, 3.5], [8, 6, 1], pelo, inflar=0.3),
        cubo([-4.5, T - 0.5, -4.5], [9, 2, 9], ORO, {"front": dibujo(["....R...."], {"R": r}, dy=0)}),
        cubo([-0.5, T + 1.5, -4.5], [1, 3, 1], gema((225, 30, 60))),
    ]
    for x in (-4.5, 3.5):
        for z in (-4.5, 3.5):
            h["head"]["cubos"].append(cubo([x, T + 1.5, z], [1, 2, 1], ORO))
    h["body"]["cubos"].append(cubo([-5, 15, -3], [10, 2, 6], ORO, {"front": dibujo(["....RR...."], {"R": r})},
                                   inflar=0.3))
    par(h, "rightArm", cubo([-10, cima - 2, -3], [6, 3, 6], ORO, inflar=0.1))
    # cetro en la mano derecha
    h["rightArm"]["cubos"] += [
        cubo([-7.5, 9, -3], [1, 18, 1], ORO),
        cubo([-9, 27, -4], [3, 3, 3], gema((150, 60, 220))),
    ]
    h["capa"] = hueso([0, cima, 3], [cubo([-6, 6, 3], [12, 22, 1], capa,
                                          {"back": varias(borde(o), dibujo(["..o..", ".ooo.", "ooooo"], {"o": o}, dy=3))},
                                          inflar=0.1)], padre="body")
    return h, 2.6, 0.8, {"capa": True}


def modelo_iratus():
    piel = Mat((150, 26, 16), "grietas", acento=(255, 140, 20))
    negro = Mat((38, 18, 18), "armadura", acento=(120, 30, 20))
    cuerno_m = Mat((60, 40, 35), "hueso")
    cara = dibujo([
        "........",
        "BB....BB",
        ".BB..BB.",
        ".YYBBYY.",
        "..k..k..",
        ".mmmmmm.",
        ".tmtmtm.",
        "..mmmm..",
    ], {"B": (40, 5, 5), "Y": (255, 210, 40, 1), "k": (70, 5, 5), "m": (25, 0, 0), "t": (240, 232, 205)})
    pecho = dibujo([
        "............",
        "..dd....dd..",
        "...dd..dd...",
        "............",
        ".....LL.....",
        "....LLLL....",
        ".....LL.....",
        "..dddddddd..",
        "....d..d....",
        "..dddddddd..",
        "....d..d....",
    ], {"d": (90, 12, 8), "L": (255, 170, 30, 1)})
    h, cima = bipedo((12, 5), (12, 12, 7), (8, 8, 8), (6, 14, 6),
                     {"cuerpo": piel, "cabeza": piel, "piernas": piel, "brazos": piel},
                     cara=cara, pecho=pecho, mano=(4, Mat((110, 18, 12), "grietas", acento=(255, 140, 20))),
                     bota=(2, negro))
    T = cima + 8
    cuerno(h, [-4, T - 4, 0], 4, cuerno_m)
    par(h, "rightArm", cubo([-12.5, cima - 2, -3.5], [7, 4, 7], negro, inflar=0.1))
    par(h, "rightArm", cubo([-9.5, cima + 2, -0.5], [1, 3, 1], HUESO))
    par(h, "rightArm", cubo([-11.5, cima + 1.5, -0.5], [1, 2, 1], HUESO))
    h["body"]["cubos"].append(cubo([-6, 12, -3.5], [12, 2, 7], negro, inflar=0.3))
    h["cola"] = hueso([0, 15, 3.5], [
        cubo([-1, 14, 3.5], [2, 2, 8], piel),
        cubo([-1.5, 13.5, 11.5], [3, 3, 2], negro),
    ], padre="body", rot=[-20, 0, 0])
    h["head"]["loc"] = {"fx": [0, T + 1, 0]}
    return h, 2.4, 0.9, {"cola": True, "particulas": ("minecraft:basic_flame_particle", 1.0)}


def modelo_avaricity():
    piel = Mat((112, 168, 52), "piel")
    chaleco = Mat((112, 68, 34), "tela")
    pantalon = Mat((70, 52, 42), "tela")
    zapato = Mat((50, 32, 22), "liso")
    saco = Mat((160, 120, 70), "tela")
    sombrero = Mat((30, 28, 32), "liso")
    o = (245, 205, 45)
    cara = dibujo([
        "........",
        ".BB..BB.",
        ".Yp..pY.",
        "........",
        "m......m",
        ".mmGmmm.",
        "........",
    ], {"B": (50, 80, 20), "Y": (250, 215, 40, 1), "p": (30, 30, 0), "m": (40, 55, 10), "G": o})
    pecho = dibujo([
        "o....o",
        ".o..o.",
        "..oo..",
        "..$$..",
        "..$$..",
    ], {"o": o, "$": (255, 225, 90)})
    h, cima = bipedo((5, 3), (6, 6, 4), (8, 7, 7), (2, 6, 2),
                     {"cuerpo": chaleco, "cabeza": piel, "piernas": pantalon, "brazos": piel},
                     cara=cara, pecho=pecho)
    T = cima + 7
    par(h, "rightLeg", cubo([-3, 0, -3.5], [3, 1, 5], zapato))
    par(h, "head", cubo([-8, cima + 3, 0], [4, 2, 1], piel, rot=[0, 0, -25], piv=[-4, cima + 4, 0.5]))
    h["head"]["cubos"] += [
        cubo([-1, cima + 2, -5.5], [2, 2, 2], Mat((96, 146, 44), "piel")),
        cubo([-4, T, -4], [8, 1, 8], sombrero, inflar=0.2),
        cubo([-3, T + 1, -3], [6, 4, 6], sombrero, todas(franja(o, 3))),
    ]
    h["rightArm"]["cubos"].append(cubo([-5.5, 3.5, -2.5], [3, 3, 1], ORO, {"front": dibujo([".$.", "$.$", ".$."], {"$": (255, 235, 120)})}))
    h["saco"] = hueso([0, 11, 2], [
        cubo([-3, 4, 2], [6, 7, 5], saco, {"back": dibujo(["......", "..oo..", ".o..o.", "..oo..", ".o..o.", "..oo.."], {"o": o})}),
        cubo([-1, 11, 3.5], [2, 1, 2], Mat((110, 80, 40), "liso")),
        cubo([-2, 11, 5], [4, 1, 2], ORO),
    ], padre="body")
    h["head"]["loc"] = {"fx": [0, T + 4, 0]}
    return h, 1.4, 0.6, {"particulas": ("minecraft:villager_happy", 2.0)}


def modelo_lujurius():
    piel = Mat((250, 210, 226), "piel")
    vestido = Mat((188, 30, 122), "tela")
    pelo = Mat((245, 108, 178), "pelo")
    medias = Mat((112, 14, 76), "tela")
    ala = Mat((112, 12, 78), "tela")
    rosa = (255, 70, 140)
    cara = dibujo([
        "........",
        "........",
        ".ll..ll.",
        ".EW..WE.",
        ".EE..EE.",
        "h......h",
        "...rr...",
        "........",
    ], {"l": (60, 10, 40), "E": (200, 20, 140, 1), "W": BLANCO, "h": (245, 160, 190), "r": (220, 30, 90)})
    pecho = dibujo([
        "......",
        ".x..x.",
        "..xx..",
        ".hhhh.",
        "hhhhhh",
        "hhhhhh",
        ".hhhh.",
        "..hh..",
    ], {"x": (250, 200, 230), "h": rosa})
    pulsera = franja((240, 196, 50), 7)
    h, cima = bipedo((13, 3), (6, 12, 3), (8, 8, 8), (2, 12, 2),
                     {"cuerpo": vestido, "cabeza": piel, "piernas": medias, "brazos": piel},
                     cara=cara, pecho=pecho, bota=(2, Mat((70, 8, 50), "liso")))
    for b in ("rightArm", "leftArm"):
        h[b]["cubos"][0]["caras"] = todas(pulsera)
    T = cima + 8
    h["body"]["cubos"].append(cubo([-4, 12, -2.5], [8, 5, 5], vestido, todas(franja((240, 196, 50), 4))))
    h["head"]["cubos"] += [
        cubo([-4, T - 2, -4], [8, 2, 8], pelo, inflar=0.3),
        cubo([-4, T - 13, 3.5], [8, 13, 2], pelo, inflar=0.2),
    ]
    par(h, "head", cubo([-5, T - 9, -3], [1, 8, 4], pelo))
    par(h, "head", cubo([-3, T + 0.5, -2], [1, 3, 1], Mat((95, 10, 62), "hueso"), rot=[0, 0, 20], piv=[-2.5, T, -1.5]))
    h["alaD"] = hueso([-1.5, 22, 1.5], [
        cubo([-11.5, 14, 1.5], [10, 9, 1], ala, {"back": dibujo(["mmmmmmmmmm", ".m..m..m.."], {"m": (60, 5, 40)})}),
        cubo([-11.5, 23, 1.5], [10, 1, 1], Mat((60, 5, 40), "hueso")),
    ], padre="body")
    h["alaI"] = hueso([1.5, 22, 1.5], [espejo(c) for c in h["alaD"]["cubos"]], padre="body")
    h["cola"] = hueso([0, 14, 1.5], [
        cubo([-0.5, 13.5, 1.5], [1, 1, 8], Mat((95, 10, 62), "liso")),
        cubo([-1.5, 12.5, 9.5], [3, 3, 1], gema(rosa), {"back": dibujo(["h.h", "hhh", ".h."], {"h": (255, 30, 110, 1)})}),
    ], padre="body", rot=[-15, 0, 0])
    h["head"]["loc"] = {"fx": [0, T + 2, 0]}
    return h, 2.2, 0.6, {"alas": True, "cola": True, "particulas": ("minecraft:heart_particle", 2.5)}


def modelo_glotonix():
    piel = Mat((222, 138, 68), "piel")
    barriga = Mat((244, 182, 118), "piel")
    pantalon = Mat((118, 72, 38), "tela")
    babero = Mat((246, 244, 236), "tela")
    boca = {"t": (250, 248, 225), "M": (95, 10, 22), "B": (110, 50, 20), "w": BLANCO, "k": (30, 15, 5),
            "n": (190, 100, 50)}
    h, cima = bipedo((6, 5), (16, 14, 12), (10, 6, 9), (4, 10, 4),
                     {"cuerpo": piel, "cabeza": piel, "piernas": pantalon, "brazos": piel},
                     cabeza_y=2, mano=(3, Mat((205, 120, 55), "piel")))
    h["head"]["cubos"][0]["caras"] = {"front": dibujo([
        "..........",
        ".BB....BB.",
        ".wk....kw.",
        "....nn....",
        ".tttttttt.",
        ".MMMMMMMM.",
    ], boca)}
    h["mandibula"] = hueso([0, cima + 2, 4.5], [cubo([-5, cima, -4.5], [10, 2, 9], piel, {"front": dibujo([
        ".MtMtMtMM.",
        "..........",
    ], boca)})], padre="head")
    h["body"]["cubos"] += [
        cubo([-6, 7, -8], [12, 9, 2], barriga, {"front": dibujo([
            "............",
            "...hhh......",
            "..hh........",
            "............",
            ".....nn.....",
            "......n.....",
        ], {"h": (255, 222, 175), "n": (180, 105, 60)})}),
        cubo([-4, 16, -7], [8, 4, 1], babero, {"front": dibujo([
            "r.r.r.r.",
            ".r.r.r.r",
            "r.r.r.r.",
            ".r.ssr.r",
        ], {"r": (215, 40, 40), "s": (140, 90, 40)})}),
        cubo([-8, 6, -6], [16, 2, 12], pantalon, {"front": dibujo([".......oo......."], {"o": (210, 180, 60)})},
             inflar=0.3),
        cubo([2, 17, -5.5], [1, 3, 1], gema((150, 210, 255), brilla=False)),
    ]
    h["rightArm"]["cubos"] += [
        cubo([-10.5, 8, -3], [1, 3, 1], HUESO),
        cubo([-11.5, 4, -4], [3, 4, 3], Mat((160, 82, 32), "piel")),
    ]
    return h, 1.8, 1.2, {"mandibula": True}


def modelo_envidium():
    sombra = Mat((24, 50, 36), "sombra", acento=(170, 255, 90))
    oscuro = Mat((12, 24, 18), "sombra", acento=(170, 255, 90))
    garra = Mat((70, 110, 60), "hueso")
    G = (170, 255, 90, 1)
    cara = dibujo([
        "..G....",
        ".....G.",
        "GG.....",
        "Gp..GG.",
        "....pG.",
        ".......",
        "TkTkTkT",
        ".TkTkT.",
        ".......",
    ], {"G": G, "p": (10, 40, 10), "k": (2, 8, 4), "T": (200, 255, 150, 1)})
    pecho = dibujo([
        "......",
        "......",
        "..G...",
        "..p...",
        "......",
        "....G.",
        "....p.",
    ], {"G": G, "p": (10, 40, 10)})
    h, cima = bipedo((14, 2), (6, 14, 3), (7, 9, 7), (2, 16, 2),
                     {"cuerpo": sombra, "cabeza": oscuro, "piernas": oscuro, "brazos": sombra},
                     cara=cara, pecho=pecho)
    T = cima + 9
    h["body"]["cubos"].append(cubo([-3.5, 7, -2], [7, 8, 4], sombra, todas(franja((10, 30, 12), 7))))
    capucha = Mat((36, 84, 46), "tela")
    h["head"]["cubos"] += [
        cubo([-4.5, T, -4.5], [9, 1, 9], capucha),
        cubo([-4.5, cima, 3.5], [9, 10, 1], capucha),
        cubo([-1.5, T + 1, -0.5], [3, 2, 3], capucha, rot=[25, 0, 0], piv=[0, T + 1, 1]),
    ]
    par(h, "head", cubo([-4.5, cima, -4.5], [1, 10, 8], capucha))
    for dx in (0, 1):
        par(h, "rightArm", cubo([-5 + dx, cima - 19, -1], [1, 3, 1], garra, rot=[-20, 0, 0], piv=[-4.5 + dx, cima - 16, -0.5]))
    h["head"]["loc"] = {"fx": [0, T - 3, -3]}
    return h, 2.4, 0.6, {"flotar": True, "particulas": ("minecraft:basic_smoke_particle", 0.8)}


def modelo_perezoth():
    pelaje = Mat((122, 110, 92), "pelo")
    pelaje_osc = Mat((92, 82, 68), "pelo")
    mascara = (214, 200, 172)
    garra = Mat((58, 52, 46), "hueso")
    gorro = Mat((70, 92, 172), "tela")
    blanco = Mat((240, 240, 245), "pelo")
    cara = dibujo([
        ".mmmmmm.",
        "mDDmmDDm",
        "DccmmccD",
        "mDmmmmDm",
        "mmmNNmmm",
        "mmsmmsmm",
        ".mmssmm.",
    ], {"m": mascara, "D": (82, 62, 46), "c": (35, 26, 20), "N": (40, 30, 24), "s": (70, 45, 40)})
    pecho = dibujo([
        "..........",
        "...pppp...",
        "..pppppp..",
        "..pppppp..",
        "..pppppp..",
        "...pppp...",
    ], {"p": (168, 152, 126)}, dy=2)
    h, cima = bipedo((6, 4), (10, 10, 8), (8, 7, 7), (3, 13, 3),
                     {"cuerpo": pelaje, "cabeza": pelaje, "piernas": pelaje_osc, "brazos": pelaje},
                     cara=cara, pecho=pecho, cabeza_z=-2, cabeza_y=-2)
    T = cima - 2 + 7
    h["body"]["cubos"].append(cubo([-4, cima - 4, 1], [8, 5, 4], pelaje))
    h["head"]["cubos"] += [
        cubo([-4, T - 1, -5.5], [8, 1, 7], blanco, inflar=0.35),
        cubo([-3.5, T, -5], [7, 3, 6], gorro),
        cubo([-2, T + 3, -4.5], [5, 2, 5], gorro),
        cubo([-0.5, T + 5, -4], [4, 2, 4], gorro),
        cubo([2.5, T + 5.5, -3.5], [3, 2, 3], gorro),
        cubo([5, T + 3.5, -3.5], [3, 3, 3], blanco),
    ]
    for dx in (-1, 0, 1):
        par(h, "rightArm", cubo([-7 + dx, 0, -1.5], [1, 4, 1], garra, rot=[-25, 0, 0], piv=[-6.5 + dx, 4, -1]))
    return h, 1.6, 0.9, {"cabecear": True}


def modelo_rey(pecados):
    armadura = Mat((48, 26, 64), "armadura", acento=(236, 188, 48))
    armadura_osc = Mat((28, 15, 38), "armadura", acento=(180, 140, 40))
    craneo = Mat((70, 24, 56), "grietas", acento=(255, 40, 70))
    capa = Mat((128, 10, 36), "tela")
    cuerno_m = Mat((40, 30, 36), "hueso")
    joyas = [p["esencia_color"][0] for p in pecados]
    o = (240, 196, 50)
    cara = dibujo([
        "..........",
        "..........",
        ".BBB..BBB.",
        ".BRB..BRB.",
        ".BBB..BBB.",
        "....BB....",
        "..........",
        ".tttttttt.",
        ".mmmmmmmm.",
        "..tttttt..",
    ], {"B": (12, 4, 12), "R": (255, 30, 40, 1), "t": (235, 228, 205), "m": (10, 0, 8)})
    gemas = {str(i): c + (1,) for i, c in enumerate(joyas)}
    gemas["o"] = o
    pecho = dibujo([
        "oooooooooooooooo",
        ".o............o.",
        "..o..........o..",
        "...0..1..2..3...",
        "................",
        ".....4..5..6....",
        "................",
        "......oooo......",
        ".......oo.......",
    ], gemas, dy=1)
    h, cima = bipedo((18, 6), (16, 20, 8), (10, 10, 10), (6, 16, 6),
                     {"cuerpo": armadura, "cabeza": craneo, "piernas": armadura, "brazos": armadura},
                     cara=cara, pecho=pecho, mano=(5, armadura_osc), bota=(4, armadura_osc))
    T = cima + 10
    par(h, "rightLeg", cubo([-7, 0, -5], [7, 3, 3], armadura_osc))
    h["body"]["cubos"] += [
        cubo([-8, 18, -4], [16, 3, 8], ORO, {"front": dibujo([".......RR......."], {"R": (255, 30, 40, 1)}, dy=1)},
             inflar=0.4),
        cubo([-4, 6, -5], [8, 13, 1], capa, {"front": varias(borde(o), dibujo(["o.o.o", "ooooo"], {"o": o}, dy=2))}),
    ]
    # corona con las siete gemas
    h["head"]["cubos"].append(cubo([-5.5, T - 1, -5.5], [11, 3, 11], ORO,
                                   {"front": dibujo(["0.1.2.3.4.5.6"], gemas, dy=1)}))
    for i, color in enumerate(joyas):
        a = i * 2 * math.pi / 7
        x, z = 5 * math.sin(a), -5 * math.cos(a)
        m = max(abs(x), abs(z)) / 5
        x, z = x / m, z / m
        h["head"]["cubos"] += [
            cubo([x - 0.5, T + 2, z - 0.5], [1, 3, 1], ORO),
            cubo([x - 0.5, T + 5, z - 0.5], [1, 1, 1], gema(color), inflar=0.25),
        ]
    cuerno(h, [-5, T - 5, 0], 6, cuerno_m)
    par(h, "rightArm", cubo([-15, cima - 3, -4], [8, 5, 8], armadura_osc, inflar=0.1))
    par(h, "rightArm", cubo([-12, cima + 2, -0.5], [1, 4, 1], ORO))
    par(h, "rightArm", cubo([-14.5, cima + 1, -0.5], [1, 3, 1], ORO))
    h["rightArm"]["cubos"] += [
        cubo([-12, 4, -5], [2, 34, 2], armadura_osc),
        cubo([-13, 38, -6], [4, 4, 4], gema((200, 30, 90))),
        cubo([-13.5, 37, -4.5], [1, 4, 1], ORO),
        cubo([-9.5, 37, -4.5], [1, 4, 1], ORO),
    ]
    h["rightArm"]["loc"] = {"fx2": [-11, 40, -4]}
    h["capa"] = hueso([0, cima, 4], [cubo([-9, 5, 4], [18, 33, 1], capa, {"back": varias(
        borde(o), dibujo(["0000000000000000", "1111111111111111", "2222222222222222", "3333333333333333",
                          "4444444444444444", "5555555555555555", "6666666666666666"],
                         {k: v[:3] for k, v in gemas.items()}, dy=25))}, inflar=0.1)], padre="body")
    orbes = []
    for i, color in enumerate(joyas):
        a = i * 2 * math.pi / 7
        orbes.append(cubo([16 * math.sin(a) - 1, 29, 16 * math.cos(a) - 1], [2, 2, 2], gema(color), inflar=0.2))
    h["orbes"] = hueso([0, 30, 0], orbes)
    h["head"]["loc"] = {"fx": [0, T + 6, 0]}
    return h, 3.6, 1.3, {"capa": True, "orbes": True,
                        "particulas": ("minecraft:soul_particle", 1.2),
                        "particulas2": ("minecraft:basic_flame_particle", 0.6)}
