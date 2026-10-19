#!/usr/bin/env python3
"""Renderiza todas las criaturas (de frente y de espaldas) en una sola imagen.

    python3 tools/vista_previa.py [salida.png]
"""
import json
import sys
from pathlib import Path

from PIL import Image

import arte

RAIZ = Path(__file__).resolve().parent.parent
RP = RAIZ / "resource_pack"
ORDEN = ["superbius", "avaricity", "lujurius", "iratus", "glotonix", "envidium", "perezoth", "rey_pecados"]


def main():
    salida = Path(sys.argv[1]) if len(sys.argv) > 1 else RAIZ / "vista_previa.png"
    escala, alto_fila = 6, 400
    filas = []
    for yaw in (-30, 150):
        piezas = []
        for clave in ORDEN:
            geo = json.loads((RP / "models" / "entity" / f"{clave}.geo.json").read_text())
            tex = Image.open(RP / "textures" / "entity" / "pecados" / f"{clave}.png").convert("RGBA")
            piezas.append(arte.renderizar(geo, tex, yaw=yaw, pitch=10, escala=escala))
        ancho = sum(p.width for p in piezas) + 10 * len(piezas)
        fila = Image.new("RGBA", (ancho, alto_fila), (196, 214, 232, 255))
        x = 5
        for p in piezas:
            fila.alpha_composite(p, (x, alto_fila - p.height - 5))
            x += p.width + 10
        filas.append(fila)
    img = Image.new("RGBA", (max(f.width for f in filas), alto_fila * len(filas)), (196, 214, 232, 255))
    for i, f in enumerate(filas):
        img.alpha_composite(f, (0, i * alto_fila))
    img.save(salida)
    print(f"Vista previa: {salida}")


if __name__ == "__main__":
    main()
