#!/usr/bin/env python3
"""Genera las texturas pixel art del add-on NintendoCraft.

Cada textura se dibuja como una cuadrícula de caracteres; cada carácter es un
color de PALETA. Ejecuta este script después de editar un dibujo:

    python3 herramientas/generar_texturas.py
"""
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
RP = RAIZ / "NintendoCraft_RP"
BP = RAIZ / "NintendoCraft_BP"

PALETA = {
    ".": (0, 0, 0, 0),
    "K": (26, 20, 16, 255),     # contorno
    "W": (255, 250, 235, 255),  # blanco
    "w": (214, 206, 190, 255),  # blanco sombra
    "Y": (252, 216, 48, 255),   # amarillo
    "y": (255, 240, 150, 255),  # amarillo brillo
    "O": (240, 160, 32, 255),   # naranja dorado
    "D": (160, 82, 0, 255),     # naranja oscuro
    "R": (220, 40, 16, 255),    # rojo
    "r": (140, 20, 8, 255),     # rojo oscuro
    "G": (56, 184, 56, 255),    # verde
    "g": (16, 112, 24, 255),    # verde oscuro
    "L": (140, 232, 120, 255),  # verde claro
    "B": (150, 82, 34, 255),    # marrón
    "b": (90, 46, 14, 255),     # marrón oscuro
    "T": (246, 220, 170, 255),  # piel / tallo
    "t": (208, 172, 120, 255),  # tallo sombra
    "P": (110, 60, 160, 255),   # morado (libro)
    "p": (70, 30, 110, 255),    # morado oscuro
}

MONEDA = [
    "................",
    "......KKKK......",
    ".....KyYYYK.....",
    "....KyYYYYOK....",
    "....KyYKKYOK....",
    "....KYYKOYOK....",
    "....KYYKOYOK....",
    "....KYYKOYOK....",
    "....KYYKOYOK....",
    "....KYYKOYOK....",
    "....KYYKOYOK....",
    "....KYYYOYOK....",
    ".....KYYYOK.....",
    "......KKKK......",
    "................",
    "................",
]


def champinon(sombrero, sombra):
    return [
        "................",
        ".....KKKKKK.....",
        "...KK" + sombrero * 2 + "WW" + sombrero * 2 + "KK...",
        "..K" + sombrero * 3 + "WWWW" + sombrero * 3 + "K..",
        "..K" + sombrero + "WW" + sombrero * 4 + "WW" + sombrero + "K..",
        ".K" + sombrero + "WWW" + sombrero * 4 + "WWW" + sombrero + "K.",
        ".K" + sombrero + "WW" + sombrero * 6 + "WW" + sombrero + "K.",
        ".K" + sombrero * 5 + "WW" + sombrero * 5 + "K.",
        ".K" + sombra * 4 + "WWWW" + sombra * 4 + "K.",
        "..KKKKKKKKKKKK..",
        "...KTTTTTTTTK...",
        "...KTTKTTKTTK...",
        "...KTTKTTKTTK...",
        "...KtTTTTTTtK...",
        "....KttttttK....",
        ".....KKKKKK.....",
    ]


FLOR_FUEGO = [
    "................",
    ".....KKKKKK.....",
    "...KKRRRRRRKK...",
    "..KRROOOOOORRK..",
    "..KROOYYYYOORK..",
    "..KROYKWWKYORK..",
    "..KROYKWWKYORK..",
    "..KROOYYYYOORK..",
    "..KRROOOOOORRK..",
    "...KKRRRRRRKK...",
    ".....KKGGKK.....",
    ".KKK...KGK..KKK.",
    ".KLGKK.KGK.KGLK.",
    "..KLGGKKGKKGGK..",
    "...KKGGGGGGKK...",
    ".....KKKKKK.....",
]

ESTRELLA = [
    ".......KK.......",
    "......KyYK......",
    "......KYYK......",
    ".....KYYYYK.....",
    "KKKKKKYYYYKKKKKK",
    "KyYYYYYYYYYYYYOK",
    ".KYYYKYYYYKYYOK.",
    "..KYYKYYYYKYOK..",
    "...KYYYYYYYYK...",
    "...KYYYYYYYOK...",
    "..KYYYYOOYYYOK..",
    "..KYYYOKKOYYOK..",
    ".KYYYOK..KOYYOK.",
    ".KYYOK....KOYOK.",
    "KYOKK......KKOK.",
    "KKK..........KKK",
]

CATALOGO = [
    "................",
    "..KKKKKKKKKKKK..",
    "..KPPPPPPPPPPKK.",
    "..KPKKKKKKKKPKWK",
    "..KPKyYYYYOKPKWK",
    "..KPKYYKKYOKPKWK",
    "..KPKYYKOYOKPKWK",
    "..KPKYYKOYOKPKWK",
    "..KPKYYYYYOKPKWK",
    "..KPKKKKKKKKPKWK",
    "..KPPPPPPPPPPKWK",
    "..KPWWWWWWWWPKWK",
    "..KPPPPPPPPPPKWK",
    "..KppppppppppKK.",
    "..KKKKKKKKKKKK..",
    "................",
]

BOLA_FUEGO = [
    "................",
    "................",
    "......KKKK......",
    "....KKRRRRKK....",
    "...KRROOOORRK...",
    "...KROOYYOORK...",
    "..KROOYWWYOORK..",
    "..KROYWWWWYORK..",
    "..KROYWWWWYORK..",
    "..KROOYWWYOORK..",
    "...KROOYYOORK...",
    "...KRROOOORRK...",
    "....KKRRRRKK....",
    "......KKKK......",
    "................",
    "................",
]


def bloque_pregunta(fondo, brillo, signo):
    s, f, h = signo, fondo, brillo
    return [
        h * 15 + "D",
        h + f * 14 + "D",
        h + f + "K" + f * 10 + "K" + f + "D",
        h + f * 4 + s * 5 + f * 5 + "D",
        h + f * 3 + s * 2 + "DDD" + s * 2 + f * 4 + "D",
        h + f * 3 + s * 2 + "D" + f * 2 + s * 2 + "D" + f * 3 + "D",
        h + f * 4 + "DD" + f * 2 + s * 2 + "D" + f * 3 + "D",
        h + f * 7 + s * 2 + "DD" + f * 3 + "D",
        h + f * 6 + s * 2 + "DD" + f * 4 + "D",
        h + f * 6 + s * 2 + "D" + f * 5 + "D",
        h + f * 7 + "DD" + f * 5 + "D",
        h + f * 6 + s * 2 + f * 6 + "D",
        h + f * 6 + s * 2 + "D" + f * 5 + "D",
        h + f + "K" + f * 5 + "DD" + f * 3 + "K" + f + "D",
        h + f * 14 + "D",
        "D" * 16,
    ]


BLOQUE_USADO = [
    "tttttttttttttttb",
    "tBBBBBBBBBBBBBBb",
    "tBKBBBBBBBBBBKBb",
    "tBBBBBBBBBBBBBBb",
    "tBBBBBBBBBBBBBBb",
    "tBBBBBBBBBBBBBBb",
    "tBBBBBBBBBBBBBBb",
    "tBBBBBBBBBBBBBBb",
    "tBBBBBBBBBBBBBBb",
    "tBBBBBBBBBBBBBBb",
    "tBBBBBBBBBBBBBBb",
    "tBBBBBBBBBBBBBBb",
    "tBBBBBBBBBBBBBBb",
    "tBKBBBBBBBBBBKBb",
    "tBBBBBBBBBBBBBBb",
    "bbbbbbbbbbbbbbbb",
]

TUBERIA_ARRIBA = [
    "KKKKKKKKKKKKKKKK",
    "KLLLLLLLLLLLLLGK",
    "KLGGGGGGGGGGGGgK",
    "KLGKKKKKKKKKKGgK",
    "KLGKKKKKKKKKKGgK",
    "KLGKKKKKKKKKKGgK",
    "KLGKKKKKKKKKKGgK",
    "KLGKKKKKKKKKKGgK",
    "KLGKKKKKKKKKKGgK",
    "KLGKKKKKKKKKKGgK",
    "KLGKKKKKKKKKKGgK",
    "KLGKKKKKKKKKKGgK",
    "KLGKKKKKKKKKKGgK",
    "KLGGGGGGGGGGGGgK",
    "KGgggggggggggggK",
    "KKKKKKKKKKKKKKKK",
]

TUBERIA_LADO = ["KLLWGGGGGGGGGggK"] * 16


def dibujar(filas, escala=1):
    alto, ancho = len(filas), len(filas[0])
    img = Image.new("RGBA", (ancho, alto))
    for y, fila in enumerate(filas):
        assert len(fila) == ancho, f"fila {y} mide {len(fila)}, se esperaban {ancho}"
        for x, c in enumerate(fila):
            img.putpixel((x, y), PALETA[c])
    if escala != 1:
        img = img.resize((ancho * escala, alto * escala), Image.NEAREST)
    return img


def guardar(img, ruta):
    ruta.parent.mkdir(parents=True, exist_ok=True)
    img.save(ruta)


def tira_animada(fotogramas):
    """Apila fotogramas 16x16 en vertical (formato de flipbook de Bedrock)."""
    tira = Image.new("RGBA", (16, 16 * len(fotogramas)))
    for i, f in enumerate(fotogramas):
        tira.paste(dibujar(f), (0, 16 * i))
    return tira


def rellenar(img, x, y, ancho, alto, color):
    for i in range(x, x + ancho):
        for j in range(y, y + alto):
            img.putpixel((i, j), PALETA[color])


def pegar(img, x, y, filas):
    for j, fila in enumerate(filas):
        for i, c in enumerate(fila):
            img.putpixel((x + i, y + j), PALETA[c])


def textura_champi_grunon():
    """Textura 64x32 con UV de caja para models/entity/nc_champi_grunon.geo.json."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # Sombrero: cubo 12x7x12 en uv [0, 0].
    rellenar(img, 12, 0, 12, 12, "B")   # arriba
    rellenar(img, 24, 0, 12, 12, "b")   # abajo
    rellenar(img, 0, 12, 48, 7, "B")    # laterales
    rellenar(img, 0, 18, 48, 1, "b")
    for x in range(13, 23, 3):          # manchas en la parte de arriba
        img.putpixel((x, 3 + x % 4), PALETA["t"])
    pegar(img, 12, 12, [                # cara (frente del sombrero)
        "BBBBBBBBBBBB",
        "BKKBBBBBBKKB",
        "BBKKBBBBKKBB",
        "BWKWBBBBWKWB",
        "BWKWBBBBWKWB",
        "BBBBBBBBBBBB",
        "bbbbbbbbbbbb",
    ])
    # Tallo: cubo 8x4x8 en uv [0, 19].
    rellenar(img, 8, 19, 8, 8, "T")
    rellenar(img, 16, 19, 8, 8, "t")
    rellenar(img, 0, 27, 32, 4, "T")
    rellenar(img, 0, 30, 32, 1, "t")
    pegar(img, 8, 27, ["TWTTTTWT", "TWTTTTWT"])  # colmillos
    # Pies: cubos 3x2x4 en uv [32, 19] y [32, 25].
    rellenar(img, 32, 19, 14, 6, "b")
    rellenar(img, 32, 25, 14, 6, "b")
    return img


def main():
    items = RP / "textures" / "items"
    guardar(dibujar(MONEDA), items / "nc_moneda.png")
    guardar(dibujar(champinon("R", "r")), items / "nc_super_champinon.png")
    guardar(dibujar(champinon("G", "g")), items / "nc_champinon_vida.png")
    guardar(dibujar(FLOR_FUEGO), items / "nc_flor_fuego.png")
    guardar(dibujar(ESTRELLA), items / "nc_estrella.png")
    guardar(dibujar(CATALOGO), items / "nc_catalogo_tienda.png")
    guardar(dibujar(BOLA_FUEGO), items / "nc_bola_fuego.png")

    bloques = RP / "textures" / "blocks"
    guardar(
        tira_animada([
            bloque_pregunta("O", "y", "W"),
            bloque_pregunta("Y", "y", "W"),
            bloque_pregunta("O", "y", "w"),
        ]),
        bloques / "nc_bloque_pregunta.png",
    )
    guardar(dibujar(BLOQUE_USADO), bloques / "nc_bloque_usado.png")
    guardar(dibujar(TUBERIA_ARRIBA), bloques / "nc_tuberia_arriba.png")
    guardar(dibujar(TUBERIA_LADO), bloques / "nc_tuberia_lado.png")

    guardar(textura_champi_grunon(), RP / "textures" / "entity" / "nc_champi_grunon.png")

    icono = dibujar(bloque_pregunta("O", "y", "W"), escala=8)
    guardar(icono, RP / "pack_icon.png")
    guardar(icono, BP / "pack_icon.png")


if __name__ == "__main__":
    main()
