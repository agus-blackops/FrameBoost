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

**Actualizar desde una versión anterior:** abre el `.mcaddon` nuevo (versión
2.2.0). Si en los ajustes de tu mundo sigue apareciendo una versión antigua,
quítala y activa la 2.2.0.

## Cómo funciona

Al entrar recibes el **Diario del Superviviente**. El terror va llegando por
días (contados desde que instalas el add-on en el mundo):

| Día | Amenaza | Qué hace |
|---|---|---|
| 0 | **Sonidos** | Pasos detrás de ti, puertas y cofres que se abren, minería lejana, el siseo de un creeper que no está, tu nombre susurrado en el chat. |
| 1 | **HerobrineGamer788** | Un "jugador" entra a tu mundo (*HerobrineGamer788 se ha unido a la partida*). Parece un Steve normal: te saluda, te sigue, pica bloques, construye, pone antorchas, salta, chatea y te regala comida. Luego **te copia**: pica lo que picas, pone lo que pones, se agacha y salta contigo. Después se vuelve raro: **se congela cuando lo miras y se acerca cuando no**, se le ponen los ojos blancos, su nombre se rompe y tus antorchas se vuelven rojas. Al final **deja de fingir** y aparece Herobrine. Si le pegas cuando ya está raro, no espera más. Como mucho viene una vez al día. |
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

## Modelos (versión 2.0.0, rehechos desde cero)

- **Herobrine** (2.2.0): casi **3 bloques** de lo que la mina dejó de un
  minero, encorvado y con espasmos:
  - el **estómago abierto** y mal cosido, con los intestinos fuera y uno
    colgando;
  - la mitad izquierda de la cara quemada hasta el **cráneo**, con una
    **cuenca vacía** donde algo aún brilla;
  - **colmillos** sobre una **mandíbula dislocada** que cuelga de lado y
    **gotea sangre**;
  - **costillas rotas** saliendo del pecho y la **columna vertebral fuera del
    cuerpo**;
  - un **pico clavado en la espalda**, **clavos** en la cabeza, el antebrazo
    derecho **despellejado** y el brazo izquierdo quemado, lleno de
    **ampollas** y acabado en **garras**.

  Sigue arrastrando su propio pico, manchado de sangre.
- **HerobrineGamer788**: un jugador normal con capa exterior. Tiene una
  segunda textura con los ojos blancos y brillantes para cuando algo va mal,
  y un pico en la mano que solo se ve mientras pica o construye.
- **null**: un error de renderizado con forma de jugador. El torso está
  **partido en tres rodajas que no encajan** y se desplazan solas, la cabeza
  está torcida y un brazo es **más largo que el otro**. Tiene los ojos
  brillantes y un **halo de píxeles sueltos** y textura que falta.
- **El Hombre de la Niebla**: 3 bloques de alto, torcido, con un hombro más alto
  que el otro y la cabeza ladeada. Lleva un **abrigo largo y roído** con mangas
  deshilachadas y agujeros, cuyos faldones se mueven al andar y **vuelan al
  correr**. Las **vértebras atraviesan el abrigo** por la espalda. Tiene
  mandíbula articulada y manos de **cuatro dedos** que le llegan por debajo de
  las rodillas.
- **El Morador de las Cuevas**: algo que fue una persona, ahora **a cuatro
  patas**. Tiene brazos largos plantados delante, **rodillas al revés**, pies
  largos, costillas marcadas y espinas en el lomo. El cuello es estirado, los
  **ojos hundidos brillan** y la **mandíbula se abre enorme** cuando ataca.
- **Bloque corrupto animado**: la textura que falta ahora parpadea y se rasga.
- Diario, linterna e icono del pack nuevos. El icono muestra a las cuatro
  criaturas.
- **Pulido (2.2.0)**: todas las texturas llevan sombreado en los bordes de
  cada cara. El Hombre de la Niebla tiene mechones largos de pelo y flecos en
  el abrigo, el Morador tiene cola y ojos extra, y null una capa de
  interferencias en la cabeza.
- **Animaciones (2.2.0)**:
  - todas las criaturas respiran;
  - el Hombre de la Niebla y el Morador reaccionan al recibir daño y atacan
    con impulso;
  - se mueven el pelo, los faldones, la cola, los intestinos y las gotas de
    sangre;
  - Herobrine tiene espasmos;
  - HerobrineGamer788 anda, se balancea, se agacha, pica, salta y se queda
    mirando como un jugador.

## Estructura

```
behavior_pack/       entidades, bloque corrupto, objetos, recetas, scripts/main.js
resource_pack/       modelos, animaciones, texturas, niebla, sonidos, textos
tools/build.py       define los modelos, pinta las texturas, valida y empaqueta
tools/animations.py  todas las animaciones
tools/geometry.py    modelos en Python, comprobación de UV y renderizador 3D
tools/pixels.py      lienzo PNG y utilidades de pixel art
dist/                HorrorLegends.mcaddon y HorrorLegends_WhatsApp.zip
```

Para regenerar los paquetes: `python3 tools/build.py` (solo Python 3 estándar).
Con `python3 tools/build.py --preview carpeta` además dibuja cada modelo
desde tres ángulos, para ver los cambios sin abrir el juego.
