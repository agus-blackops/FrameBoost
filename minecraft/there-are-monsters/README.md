# There Are Monsters — Add-On para Minecraft Bedrock

> *Se parecen a nosotros. No son nosotros.*

Add-on de terror inspirado en la película canadiense de *found footage*
**There Are Monsters** (2013, dir. Jay Dahl): personas corrientes que, sin que
nadie lo note, han sido **reemplazadas** por algo que imita su aspecto... hasta
que su cara deja de estar bien.

Es un proyecto de fans, sin relación con los autores de la película. Todo el
arte (texturas, iconos) se genera por código en `tools/build.py`, y los textos
de las cintas son originales.

## Instalación

1. Descarga [`dist/ThereAreMonsters.mcaddon`](dist/ThereAreMonsters.mcaddon).
2. Ábrelo con Minecraft (doble clic en PC, "Abrir con" en móvil). Se importan
   el pack de comportamiento y el de recursos.
3. Al crear o editar un mundo, activa **There Are Monsters** en *Packs de
   comportamiento* (el de recursos se añade solo).

Requiere **Minecraft Bedrock 1.21.0 o superior**. No necesita experimentos:
los scripts usan la API estable `@minecraft/server` 1.11.0.

## Criaturas

### Impostor (`tam:imposter`)
Aparece de noche en la superficie del Overworld con aspecto de persona normal
(tres atuendos: camisa de franela, sudadera verde, impermeable amarillo de
pescador). Pasea, abre puertas y **se te queda mirando**.

Se quita el disfraz cuando:
- te acercas a menos de ~2,5 bloques,
- recibe daño,
- lo grabas con la Videocámara,
- o, simplemente, cuando le apetece (azar, solo de noche).

Revelado, su cara se **estira y vibra**, la piel se vuelve grisácea, y ataca a
jugadores, aldeanos y comerciantes (5 de daño, 30 de vida). Los gólems de
hierro lo reconocen como monstruo. De día, si no hay nadie cerca, vuelve a
ponerse la máscara.

**Reemplazo:** si un Impostor mata a un aldeano, al poco aparece un Impostor
disfrazado en su lugar. El pueblo sigue pareciendo normal.

### Doble (`tam:doppelganger`)
Si un Impostor te mata, en el sitio donde caíste aparece **tu Doble**, con tu
nombre encima. Una copia pálida, vestida de negro, con una sonrisa demasiado
ancha.

**Solo se mueve cuando nadie lo mira.** Mientras lo tengas en pantalla (y sin
bloques en medio) se queda quieto, con la cabeza ladeada. En cuanto apartas la
vista, corre hacia ti (7 de daño, 40 de vida). Se quema con la luz del sol.

## Objetos

| Objeto | Cómo conseguirlo | Uso |
|---|---|---|
| **Videocámara** (`tam:camcorder`) | Mesa de crafteo: `III / IGR / III` (I = lingote de hierro, G = panel de cristal, R = redstone) | Úsala (clic derecho / mantener pulsado) para grabar: revela a los Impostores disfrazados que tengas **delante y a la vista** en 24 bloques, y te da visión nocturna 12 s. Mientras la sostienes aparece el contador `● REC 00:00:00`. |
| **Cinta VHS** (`tam:vhs_tape`) | La sueltan los Impostores (12 %, más con Botín) y siempre los Dobles al matarlos un jugador | Úsala para reproducir un fragmento de metraje recuperado (8 distintos). |

Los dos mobs tienen huevo de generación en el inventario creativo.

## Idiomas

Español (España y México) e inglés.

## Estructura

```
behavior_pack/     entidades, objetos, spawn, botín, receta, scripts/main.js
resource_pack/     modelo, animaciones, render controllers, texturas, sonidos, textos
tools/build.py     pinta las texturas, valida los JSON y empaqueta el .mcaddon
dist/              ThereAreMonsters.mcaddon listo para instalar
```

Para regenerar el paquete tras cambiar algo:

```sh
python3 tools/build.py
```

(Solo usa la biblioteca estándar de Python 3.)
