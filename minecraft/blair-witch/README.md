# The Blair Witch — Add-On para Minecraft Bedrock

> *En octubre de 1994, tres estudiantes de cine desaparecieron en el bosque...*

Add-on de terror inspirado en **The Blair Witch Project** (1999). No hay un
monstruo al que pegar: hay un bosque que, noche tras noche, se va cerrando a tu
alrededor. La Bruja nunca se deja ver.

Es un proyecto de fans, sin relación con los autores de la película. Todo el
arte (texturas, iconos) se genera por código en `tools/build.py`, y las notas
del juego son textos originales.

## Instalación

- **Directo:** abre [`dist/BlairWitch.mcaddon`](dist/BlairWitch.mcaddon) con Minecraft.
- **Por WhatsApp:** envía [`dist/BlairWitch_WhatsApp.zip`](dist/BlairWitch_WhatsApp.zip).
  Quien lo reciba lo descomprime y abre el `.mcaddon` de dentro con Minecraft.

Después, al crear o editar un mundo, activa **The Blair Witch** en *Packs de
comportamiento* (el de recursos se añade solo).

Requiere **Minecraft Bedrock 1.21.0 o superior**. No necesita experimentos.

## La maldición, noche a noche

Cada noche que pases **dentro de un bosque** (con árboles alrededor) cuenta. Al
caer la noche aparece en pantalla `NOCHE 1`, `NOCHE 2`...

| Noche | Qué pasa |
|---|---|
| 1+ | Ramas que crujen a tu espalda. **Figuras de palos** aparecen colgando de los árboles. |
| 2+ | Al amanecer, **tres montones de piedras** a tu alrededor. |
| 3+ | **Voces de niños** en la oscuridad. **La Bruja** empieza a cazarte. |
| 4+ | Una **niebla negra** se cierra sobre ti. Tu **mapa desaparece** ("alguien lo tiró al arroyo"). |
| 5+ | Al amanecer, un **atado de ramas** junto a ti. De noche, una **casa en ruinas** aparece entre los árboles. |

Cada noche que pases **fuera** del bosque, la maldición baja un nivel.

## Criaturas

### La Bruja de Blair (`bw:witch`)
Una silueta alta, oscura y medio transparente, sin cara. Siempre aparece **a tu
espalda**. **Si la miras directamente, desaparece** entre humo. Si no la miras,
se acerca. Si te toca, te deja en la oscuridad (efecto Oscuridad) y se esfuma.
No se le puede hacer daño. Se va con la luz del día.

### El de la Esquina (`bw:corner_man`)
En el sótano de la casa hay un hombre **de pie, mirando a la esquina**. No se
mueve. No se gira. Si te acercas a él... la cámara cae al suelo. Te deja ciego y
mareado, te quita 6 corazones, y si llevabas la Cámara Hi8 en la mano, se te
cae. La maldición vuelve a cero.

### Figura de Palos y Montón de Piedras
Señales de la Bruja. Se pueden romper (sueltan palos, cuerda y piedra).

## Objetos

| Objeto | Cómo conseguirlo | Uso |
|---|---|---|
| **Cámara Hi8** | `I R I / I G I / I L I` (I = lingote de hierro, R = redstone, G = panel de cristal, L = cuero) | En la mano muestra `● REC 00:00:00` y la noche actual. Úsala para encender su luz (visión nocturna 30 s). Si la Bruja está cerca, la imagen se llena de estática. |
| **Mapa de Black Hills** | 2 papeles + 1 bolsa de tinta (sin forma) | Te dice a cuántos bloques está el pueblo (el punto de aparición del mundo) y en qué dirección, y cuántas noches llevas en el bosque. |
| **Figura de Palos** | `_ W _ / S S S / S _ S` (W = cuerda, S = palo) → 2 | Colócala en un bloque. Si la pones debajo de un bloque (en el techo o bajo unas hojas), queda colgando. |
| **Atado de Ramas** | Aparece al amanecer desde la noche 5 | Ábrelo para leer una nota encontrada (6 distintas). |

## Qué viene de la película y qué es inventado

**De la película:** el bosque de Black Hills, las figuras de palos colgadas, los
montones de piedras que aparecen de noche, las voces de niños, el mapa tirado
al arroyo, perderse caminando en círculos (en las notas), el atado de ramas con
un trozo de franela, la casa en ruinas con el sótano, el hombre mirando a la
esquina, la Bruja que nunca se ve, la cámara Hi8 y el estilo de *metraje
encontrado*.

**Inventado para el juego:** el sistema de noches, que la Bruja desaparezca si
la miras, la niebla, el mapa que señala el pueblo, y los textos de las notas.

## Estructura

```
behavior_pack/     entidades, objetos, recetas, botín, scripts/main.js
resource_pack/     modelos, animaciones, texturas, niebla, sonidos, textos
tools/build.py     pinta las texturas, valida los JSON y empaqueta
dist/              BlairWitch.mcaddon y BlairWitch_WhatsApp.zip
```

Para regenerar los paquetes: `python3 tools/build.py` (solo Python 3 estándar).
