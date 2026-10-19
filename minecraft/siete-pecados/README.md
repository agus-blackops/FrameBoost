# Los 7 Pecados Capitales — Addon para Minecraft Bedrock

Siete criaturas hostiles nuevas, una por cada pecado capital. Cada una tiene su modelo, su textura,
sus poderes y su propio botín, incluida una **Esencia** que puedes beber para conseguir su poder.

## Instalación

1. Descarga `dist/LosSietePecados.mcaddon`.
2. Ábrelo en el dispositivo donde tengas Minecraft Bedrock (Windows, Android, iOS…). Minecraft
   importa solos los dos packs.
3. En los ajustes del mundo, activa el **Behavior Pack** «Los 7 Pecados Capitales (BP)». El
   Resource Pack se activa con él.
4. Necesitas Minecraft Bedrock **1.21.40 o superior**.

Para probarlos enseguida: `/summon pecados:envidium`, o usa los huevos de generación del inventario
creativo (Generar Envidium, Generar Lujurius…).

## Las criaturas

| Pecado | Criatura | Vida | Poderes |
|---|---|---|---|
| Soberbia | **Superbius** | 60 | Rey con corona de oro. Te lanza por los aires al golpearte, devuelve parte del daño que le haces y se regenera con poca vida. Casi inmune al empuje. |
| Avaricia | **Avaricity** | 18 | Duende muy rápido. Puede **robarte el objeto que tienes en la mano**; al matarlo suelta todo lo que robó. Da oro y esmeraldas. |
| Lujuria | **Lujurius** | 26 | Te atrae hacia ella con su hechizo; su golpe da náuseas y lentitud. |
| Ira | **Iratus** | 40 | Demonio con cuernos, inmune al fuego. Te prende fuego y entra en **furia** (fuerza y velocidad) por debajo de la mitad de vida. Aparece también en el Nether. |
| Gula | **Glotonix** | 50 | Gigante glotón: te da hambre, se cura al golpearte y **se come los objetos del suelo**. |
| Envidia | **Envidium** | 30 | Sombra de muchos ojos. Te debilita, roba tu fuerza, se **teletransporta a tu espalda** y se vuelve invisible. |
| Pereza | **Perezoth** | 60 | Lentísimo pero duro. Su aura de sopor te da lentitud y fatiga minera si te acercas. |

Aparecen de noche o en zonas oscuras, como cualquier monstruo. Superbius es el más raro.

## Esencias

Cada pecado puede soltar su Esencia (más probable con Botín). Bébela para obtener su poder:

| Esencia | Efecto |
|---|---|
| Soberbia | Resistencia II y Absorción II (30 s) |
| Avaricia | Suerte y Prisa minera II (60 s) |
| Lujuria | Regeneración II (15 s) |
| Ira | Fuerza II y Resistencia al fuego (30 s) |
| Gula | Saturación y Salud extra II (60 s) |
| Envidia | Invisibilidad y Visión nocturna (60 s) |
| Pereza | Caída lenta y Salto II (60 s) |

## Estructura

```
behavior_pack/   entidades, apariciones, botín, objetos y scripts/main.js (poderes)
resource_pack/   modelos, texturas, animaciones e idiomas (es_ES, es_MX, en_US)
dist/            el .mcaddon listo para instalar
tools/generar.py genera los modelos, las texturas y todos los JSON
```

Para cambiar algo (vida, daño, colores, modelos…), edita `tools/generar.py` y ejecuta
`python3 tools/generar.py` (requiere Pillow). Los poderes están en `behavior_pack/scripts/main.js`.
