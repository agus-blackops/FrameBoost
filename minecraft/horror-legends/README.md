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
4.0.0). Si en los ajustes de tu mundo sigue apareciendo una versión antigua,
quítala y activa la 4.0.0.

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

## Modelos (versión 4.0.0, rediseñados)

Todas las criaturas tienen **forma nueva** y **texturas nuevas**, pintadas
con el doble de detalle (cada píxel del modelo lleva 2x2 píxeles de textura) y
con manchas, piel y tela de aspecto orgánico en lugar de puntitos sueltos.

- **Herobrine, el minero hueco**: casi 3 bloques, doblado sobre una
  **joroba enorme**. La **columna vertebral está fuera**, arqueada desde la
  cintura por encima de los hombros, con púas. La **cabeza cuelga por
  delante** de un cuello estirado. El **pecho reventado** abre las
  costillas como una segunda boca alrededor del corazón, y debajo el
  **estómago abierto** y cosido deja salir los intestinos. La **boca está
  rajada hasta las orejas**, la mandíbula cuelga suelta y gotea sangre, tiene
  **colmillos**, la mitad izquierda de la cara quemada hasta el **cráneo** y
  **ampollas** por todo el lado quemado. Los brazos le llegan a las rodillas:
  el derecho **despellejado** con un hueso saliendo de la muñeca y
  arrastrando el pico, y el izquierdo quemado y acabado en **garras de
  hueso**. Cojea al andar.
- **El Hombre de la Niebla**: una figura que confundes con un árbol muerto
  en la niebla. **Piernas como zancos** con rodillas nudosas, una **cintura
  finísima**, costillas marcadas, un hombro huesudo más alto que el otro y un
  chal de trapos podridos. Un **cuello largo** sostiene una cabeza pequeña y
  alargada, **muy ladeada**, con dos **ojos negros enormes** y una
  **mandíbula que cuelga abierta**. Los brazos le llegan a las espinillas y
  acaban en **cuatro dedos largos y negros**. A la espalda lleva un
  **sudario hecho jirones**. Piel gris y húmeda con venas azules.
- **El Morador de las Cuevas**: ahora se mueve **como una araña**. El cuerpo
  va colgado bajo, entre cuatro extremidades larguísimas con **codos y
  rodillas por encima del lomo**, y avanza correteando. El cráneo es plano y
  largo, con una **corona de púas**, **cuatro ojos hundidos que brillan** y
  una mandíbula llena de **dientes de aguja** que se abre demasiado. Tiene
  garras en manos y pies, omóplatos marcados, espinas en el lomo y piel
  pálida y babosa.
- **null**: un jugador que el juego no consiguió montar. **Cada parte flota
  separada** de la siguiente: piernas, tres rodajas de torso que no encajan,
  brazos rotos en trozos y la cabeza girada al revés sobre el cuello. Un
  brazo tiene tres trozos y arrastra tres dedos por el suelo. Alrededor lleva
  la **caja de colisión de F3+B**: el marco blanco, la **línea roja** a la
  altura de los ojos y la **línea azul** de la mirada, que le salen de los
  ojos. Sigue teniendo el halo de píxeles sueltos y textura que falta, y
  restos de una skin normal en la oscuridad.
- **HerobrineGamer788**: sigue siendo **un Steve normal**, con textura
  repintada en el estilo nuevo, capa exterior y el pico que solo se ve cuando
  pica o construye. La textura de "algo va mal" le deja los ojos blancos.
- **Bloque corrupto animado**: la textura que falta parpadea y se rasga.
- **Animaciones**: todas las criaturas respiran. El Morador **corretea como
  una araña**. Herobrine cojea y tiene espasmos. El Hombre de la Niebla y el
  Morador reaccionan al daño y atacan con impulso. Se mueven el pelo, los
  jirones, los intestinos y las gotas de sangre. HerobrineGamer788 anda, se
  agacha, pica, salta y se queda mirando como un jugador.

## Estructura

```
behavior_pack/       entidades, bloque corrupto, objetos, recetas, scripts/main.js
resource_pack/       modelos, animaciones, texturas, niebla, sonidos, textos
tools/creatures.py   los modelos de las criaturas y sus texturas
tools/build.py       objetos, icono, validación y empaquetado
tools/animations.py  todas las animaciones
tools/geometry.py    modelos en Python, comprobación de UV y renderizador 3D
tools/pixels.py      lienzo PNG y utilidades de pixel art
dist/                HorrorLegends.mcaddon y HorrorLegends_WhatsApp.zip
```

Para regenerar los paquetes: `python3 tools/build.py` (solo Python 3 estándar).
Con `python3 tools/build.py --preview carpeta` además dibuja cada modelo
desde tres ángulos, para ver los cambios sin abrir el juego.
