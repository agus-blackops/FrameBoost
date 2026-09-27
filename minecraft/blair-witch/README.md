# The Blair Witch — Add-On para Minecraft Bedrock

> *En octubre de 1994, tres estudiantes de cine desaparecieron en el bosque
> cerca de Burkittsville, Maryland, mientras rodaban un documental.
> Un año después se encontró su metraje.*

Add-on de terror inspirado en **The Blair Witch Project** (1999). No hay un
monstruo al que pegar: hay un bosque que, noche tras noche, se va cerrando a tu
alrededor. Josh y Mike vienen contigo. La Bruja nunca se deja ver.

Es un proyecto de fans, sin relación con los autores de la película. Todo el
arte (texturas, iconos) se genera por código en `tools/build.py`. Los diálogos,
las notas y el expediente están escritos para el add-on, siguiendo la película
y su leyenda.

## Instalación

- **Directo:** abre [`dist/BlairWitch.mcaddon`](dist/BlairWitch.mcaddon) con Minecraft.
- **Por WhatsApp:** envía [`dist/BlairWitch_WhatsApp.zip`](dist/BlairWitch_WhatsApp.zip).
  Quien lo reciba lo descomprime y abre el `.mcaddon` de dentro con Minecraft.

Después, al crear o editar un mundo, activa **The Blair Witch** en *Packs de
comportamiento* (el de recursos se añade solo).

Requiere **Minecraft Bedrock 1.21.0 o superior**. No necesita experimentos.

**Actualizar desde la versión 1.0.0:** abre el `.mcaddon` nuevo (versión
1.1.0). Si en los ajustes de tu mundo sigue apareciendo la 1.0.0, quítala y
activa la 1.1.0.

## Cómo se juega

1. Fabrica la **Cámara Hi8** y úsala por primera vez: empieza el rodaje.
   Aparecen **Josh** y **Mike**, que te siguen a todas partes, y recibes el
   **Expediente de la Bruja de Blair** con la leyenda.
2. Entra en un bosque y pasa allí la noche. Cada noche que pases **entre los
   árboles** cuenta (también si duermes): en pantalla aparece `NOCHE 1`,
   `NOCHE 2`...
3. La historia de la película va pasando noche a noche:

| Noche | Qué pasa |
|---|---|
| 1 | Ramas que crujen a tu espalda. **Figuras de palos** colgando de los árboles. Josh: *"¿Habéis oído eso?"* |
| 2 | Al amanecer, **tres montones de piedras** alrededor. Si duermes, **algo sacude la tienda**. |
| 3 | **Voces de niños** en la oscuridad. **La Bruja** empieza a cazarte. De día, **caminas en círculo**: vuelves al mismo **tronco caído**. |
| 4 | **Niebla negra.** Mike confiesa: **tiró el mapa al arroyo.** Al amanecer, **Josh ha desaparecido**. Desde entonces, de noche **se le oye gritar**. |
| 5 | Al amanecer, un **atado de ramas envuelto en la camisa de franela de Josh**. De noche aparece **la casa**. Los gritos de Josh salen de dentro. **Mike corre hacia la casa**... |

4. La casa en ruinas tiene dos pisos y un sótano, y las paredes están
   **llenas de huellas de manos de niños**. En la esquina del sótano hay alguien
   de pie, **mirando a la pared**. Es Mike. Si te acercas... la cámara cae al
   suelo, y un año después se encuentra tu metraje.

Cada noche que pases **fuera** del bosque, la maldición baja un nivel. Tras el
final, vuelve a usar la cámara para rodar un documental nuevo.

**La confesión:** desde la noche 4, de noche, **agáchate y usa la cámara**
para grabarte a ti mismo y pedir perdón, como Heather.

## Criaturas

- **Josh y Mike** — tu equipo de rodaje. Te siguen (si te alejas, te
  alcanzan), se acercan cuando llevas la cámara en la mano y comentan lo que
  pasa. No se les puede hacer daño.
- **La Bruja de Blair** — una silueta alta, oscura y medio transparente, sin
  cara. Siempre llega **por la espalda**. **Si la miras directamente,
  desaparece.** Si te toca, te deja en la oscuridad. No se le puede hacer daño.
  Se va con la luz del día.
- **El de la Esquina** — en el sótano, de pie, mirando a la pared. No se
  mueve. No se gira.
- **Figura de Palos** y **Montón de Piedras** — las señales de la Bruja. Se
  pueden romper.

## Objetos y bloques

| Objeto | Cómo conseguirlo | Uso |
|---|---|---|
| **Cámara Hi8** | `I R I / I G I / I L I` (I = lingote de hierro, R = redstone, G = panel de cristal, L = cuero) | La primera vez empieza el rodaje. En la mano muestra `● REC 00:00:00` y la noche. Úsala para encender su luz (visión nocturna 30 s); si la Bruja está cerca, sale estática. Agachado, de noche, desde la noche 4: la confesión. |
| **Expediente de la Bruja de Blair** | Al empezar el rodaje, o libro + palo | Abre un libro de 8 páginas con la leyenda: Elly Kedward (1785), el pueblo de Blair abandonado, Burkittsville, la niña del arroyo, Coffin Rock, Rustin Parr y los estudiantes de 1994. |
| **Mapa de Black Hills** | 2 papeles + 1 bolsa de tinta | Dice a cuántos bloques y en qué dirección está el pueblo (el punto de aparición del mundo). Mike lo acabará tirando al arroyo. |
| **Figura de Palos** | `_ W _ / S S S / S _ S` (W = cuerda, S = palo) → 2 | Colócala en un bloque; bajo un bloque (techo, hojas) queda colgando. |
| **Atado de Ramas** | Aparece al amanecer desde la noche 5 | Ábrelo para leer una nota encontrada (6 distintas). |
| **Pared con Huellas** | En la casa (se puede picar) | Bloque de yeso viejo con huellas de manos de niños. |

## Qué viene de la película y qué es inventado

**De la película y su leyenda:** Heather, Josh y Mike; el documental; el bosque
de Black Hills y Burkittsville; las figuras de palos; los montones de piedras;
la tienda sacudida de noche; las voces de niños; caminar en círculo hasta el
mismo tronco; Mike tirando el mapa al arroyo; la desaparición de Josh y sus
gritos en la oscuridad; el atado de ramas con la tela de su camisa; la
confesión a cámara; la casa en ruinas con huellas de manos de niños; Mike
corriendo hacia la casa; el sótano y el que mira a la esquina; la cámara que
cae al suelo; el metraje encontrado; la leyenda de Elly Kedward, Coffin Rock y
Rustin Parr.

**Inventado para el juego:** el sistema de noches, que la Bruja desaparezca si
la miras (en la película nunca se la ve), la niebla, el mapa que señala el
pueblo y las notas del atado de ramas.

## Estructura

```
behavior_pack/     entidades, bloque, objetos, recetas, botín, scripts/main.js
resource_pack/     modelos, animaciones, texturas, niebla, sonidos, textos
tools/build.py     pinta las texturas, valida los JSON y empaqueta
dist/              BlairWitch.mcaddon y BlairWitch_WhatsApp.zip
```

Para regenerar los paquetes: `python3 tools/build.py` (solo Python 3 estándar).
