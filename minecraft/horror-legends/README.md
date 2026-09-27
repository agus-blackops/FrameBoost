# Horror Legends — Add-On de terror para Minecraft Bedrock

> *Este mundo no está tan vacío como parece.*

Un add-on de terror que, poco a poco, pone tu mundo en tu contra. Está
inspirado en los mods de terror más conocidos de Minecraft Java y en las
leyendas de la comunidad:

- **The Man From The Fog** → *El Hombre de la Niebla*
- **Cave Dweller / From The Caves** → *El Morador de las Cuevas*
- **Herobrine** (la leyenda de siempre, y los mods tipo *From The Fog*)
- **The Broken Script** y la creepypasta de **null** → *null*
- y los "sustos de sonido" de muchos otros mods de terror

Es un proyecto de fans: **no contiene código ni arte de esos mods**. Los
modelos y las texturas se generan por código en `tools/`, y todo el
comportamiento está escrito desde cero para Bedrock.

## Instalación

- **Directo:** abre [`dist/HorrorLegends.mcaddon`](dist/HorrorLegends.mcaddon) con Minecraft.
- **Por WhatsApp:** envía [`dist/HorrorLegends_WhatsApp.zip`](dist/HorrorLegends_WhatsApp.zip).
  Quien lo reciba lo descomprime y abre el `.mcaddon` de dentro con Minecraft.

Después, al crear o editar un mundo, activa **Horror Legends** en *Packs de
comportamiento* (el de recursos se añade solo).

Requiere **Minecraft Bedrock 1.21.0 o superior**. No necesita experimentos.

**Actualizar desde la 1.0.0:** abre el `.mcaddon` nuevo (versión 1.1.0). Si en
los ajustes de tu mundo sigue apareciendo la 1.0.0, quítala y activa la 1.1.0.

## Cómo funciona

Al entrar recibes el **Diario del Superviviente**. El terror va llegando por
días (contados desde que instalas el add-on en el mundo):

| Día | Amenaza | Qué hace |
|---|---|---|
| 0 | **Sonidos** | Pasos detrás de ti, puertas y cofres que se abren, minería lejana, el siseo de un creeper que no está, tu nombre susurrado en el chat. |
| 1 | **El Morador de las Cuevas** | Cuanto más tiempo pasas **bajo tierra**, más sube la tensión: ruidos de cueva, pasitos rápidos en la oscuridad... hasta que aparece. Te acecha, se asoma y huye si lo ves. Si esperas demasiado, **te persigue**. Se arrastra por huecos de 1 bloque. Subir a la superficie lo calma. |
| 2 | **Herobrine** | Lo ves a lo lejos, quieto, con los ojos blancos brillando. Si lo miras fijamente o te acercas, desaparece. A veces está **justo detrás de ti**. Deja **pirámides de arena**, **árboles sin hojas**, **túneles de 2x2** en las cuevas, **carteles** ("DETRÁS DE TI") y cambia tus antorchas por **antorchas de redstone**. A veces "entra" al chat: *Herobrine se ha unido a la partida*. Si le pegas, te devuelve el golpe. |
| 4 | **El Hombre de la Niebla** | Llega una **niebla espesa**. Una figura alta y pálida te observa desde lejos... y **cada vez que apartas la vista está más cerca**. Si lo miras demasiado, se va o viene corriendo. De noche **llama a tu puerta** y a veces **la rompe**. Cuando te persigue, trepa paredes y rompe cristales, puertas y hojas. |
| 6 | **null** | Aparece por el **rabillo del ojo** y desaparece al mirarlo. Si no lo miras, acaba **detrás de ti**, y al darte la vuelta... Escribe en el chat, convierte bloques en la **textura que falta** (magenta y negro) y a veces finge que **el juego se ha colgado**. Los bloques corruptos vuelven a la normalidad solos. |

## Objetos

| Objeto | Receta | Uso |
|---|---|---|
| **Diario del Superviviente** | Libro + carbón vegetal (también te lo dan al entrar) | Muestra el día y qué amenazas están activas. Tiene **Bestiario**, **Ajustes** e **Invocar (pruebas)**. |
| **Linterna** | `I G I / _ R _ / _ I _` (I = lingote de hierro, G = polvo de piedra luminosa, R = redstone) | Visión nocturna 20 s. Si iluminas al **Morador**, huye. Herobrine y null desaparecen. Al Hombre de la Niebla, cuando corre, **no le detiene**. |
| **Bloque Corrupto** | — | La textura que falta. Lo crea null; vuelve solo a ser el bloque original. |

Las cuatro criaturas tienen **huevo de generación** en el modo creativo. Las
que sacas con un huevo no desaparecen solas.

## Ajustes (en el Diario)

- **Intensidad**: *Baja* (el doble de días, la mitad de sustos), *Normal*,
  *Alta* o *Pesadilla* (todo desde el primer día y muy seguido).
- **Activar o desactivar** cada amenaza por separado.
- **Invocar (pruebas)**: provoca al momento cualquier evento, para verlo sin
  esperar días.

## Modelos

- **Herobrine** y **null**: forma de jugador con la **capa exterior** (pelo,
  cuello y puños de la ropa). Los ojos brillan en la oscuridad. null suelta
  fragmentos "rotos" de magenta y cian, y a su alrededor flotan cubos con la
  textura que falta, que parpadean.
- **El Hombre de la Niebla**: modelo propio de casi 3 bloques de alto. Tiene
  piernas con rodilla y pies descalzos, cintura estrecha, pecho encorvado con
  costillas, cuello, y un cráneo alargado cuya **mandíbula se abre** cuando
  corre. Los brazos llegan por debajo de las rodillas y tienen codo, mano y
  dedos largos. Al observar ladea la cabeza y mueve los dedos. Al correr va
  doblado, con los brazos hacia atrás y la boca abierta.
- **El Morador de las Cuevas**: modelo propio sobre **cuatro patas
  articuladas** con los codos por encima del lomo, espolones y garras. Tiene
  la columna con púas, cuello largo y una cabeza con **varios ojos** y una
  mandíbula que **chasquea** cuando te persigue. Camina en diagonal, como una
  araña, y respira.

## Estructura

```
behavior_pack/       entidades, bloque corrupto, objetos, recetas, scripts/main.js
resource_pack/       modelos, animaciones, texturas, niebla, sonidos, textos
tools/build.py       define los modelos, pinta las texturas, valida y empaqueta
tools/geometry.py    modelos en Python, comprobación de UV y renderizador 3D
tools/pixels.py      lienzo PNG y utilidades de pixel art
dist/                HorrorLegends.mcaddon y HorrorLegends_WhatsApp.zip
```

Para regenerar los paquetes: `python3 tools/build.py` (solo Python 3 estándar).
Con `python3 tools/build.py --preview carpeta` además dibuja cada modelo
desde tres ángulos, para ver los cambios sin abrir el juego.
