# Horror Legends: Fears — Add-On de terror para Minecraft Bedrock

> *Este mundo no está tan vacío como parece.*

**Fears** es el remake más duro de **Horror Legends**: una historia de terror
en **tres actos** que, poco a poco, pone tu mundo en tu contra. Empieza en
dificultad **Alta**, con **sonidos mucho más fuertes** y un sistema de
**miedos**. Tiene un final.

**Desde la 1.1 solo quedan las leyendas y creepypastas de Minecraft**: ni
folclore ni monstruos de internet de fuera del juego.

- **Herobrine** y su forma verdadera, **el Minero Hueco** (el jefe)
- **HerobrineGamer788**, el jugador que entra a tu partida
- **Entity 303**
- **null** (The Broken Script)
- **The Man From The Fog**
- **El Morador de las Cuevas** (Cave Dweller / From The Caves)
- **El Aldeano Sin Nombre** y **el Endermán Sin Rostro** (nuevos en la 1.2,
  inspirados en las creepypastas de aldeanos y Endermen)

Es un proyecto de fans: **no contiene código, arte ni sonido de esos mods**.
Los modelos, las texturas y los sonidos se generan por código en `tools/`, y
todo el comportamiento está escrito desde cero para Bedrock.

> ⚠️ **Aviso: suena MUY fuerte.** Los sustos usan sonidos masterizados a
> volumen máximo y reproducidos por duplicado. Baja el volumen antes de jugar
> con auriculares. Se puede bajar en el Diario → Ajustes → **Sonidos fuertes**
> (y **Sustos fuertes** para quitar la pantalla roja y el temblor).
>
> Fears usa los mismos identificadores que Horror Legends: **no actives los
> dos a la vez en el mismo mundo**. Elige uno.

## Novedades de la 1.2: sonido 3D y modelos nuevos

**Sonido 3D.** Minecraft coloca en el espacio todo sonido que tiene una
posición: izquierda, derecha, detrás, cerca, lejos. En la 1.2 los sonidos de
Fears **salen de donde están las cosas** y algunos **se mueven**. Con
auriculares se nota mucho:

- **Voces desde la criatura.** Herobrine se ríe desde donde está, el Morador
  gruñe, el Hombre de la Niebla respira, null y Entity 303 sueltan chasquidos
  digitales, el Aldeano canturrea y el Minero Hueco ruge. Oyes de qué lado
  vienen y a qué distancia.
- **Pasos reales.** El Morador, el Hombre de la Niebla y el jefe hacen un
  sonido de pisada **cada vez que dan un paso**, desde sus pies. Y en «Pasos»,
  quien te sigue suena justo detrás de ti.
- **Sonidos que se mueven** (nuevos sustos pequeños del director):
  - *pasos que dan vueltas a tu alrededor*, cada vez más cerca;
  - *pasos que vienen hacia ti* desde lejos, en línea recta;
  - *algo rápido que te pasa de lado* (más agudo al acercarse, más grave al
    alejarse);
  - *un aullido muy lejano* y *un crujido de madera justo a tu espalda*.
- **Eco en cuevas y túneles**: los sonidos grandes y los pasos **rebotan en las
  paredes** y vuelven dos veces, más flojos.
- **9 sonidos nuevos**: risa, gruñido, quejido, pisada, crujido, fallo digital,
  rugido, aullido y salto de Enderman. Todos en mono para que el juego los
  coloque en 3D.
- Se apaga en Diario → Ajustes → **Sonido 3D**.

**Modelos nuevos.** Dos criaturas más de creepypastas de Minecraft, con modelo,
textura y animaciones propios:

- **El Aldeano Sin Nombre** (desde el día 2): un aldeano gris ceniza con los
  brazos hasta las rodillas, la cabeza ladeada y **dos agujeros blancos por
  ojos**. Si no lo miras, se acerca; si lo miras, se queda quieto y **te
  señala**. Si llega a tu espalda, **te pone una mano en el hombro**. Si te
  acercas o lo miras mucho rato, desaparece.
- **El Endermán Sin Rostro** (desde el día 4, de noche o bajo tierra): casi 3,5
  bloques, **sin cara, solo una línea violeta** donde irían los ojos. No camina:
  **parpadea**, y oyes en 3D dónde desaparece y dónde aparece, cada vez más
  cerca. Si lo miras de frente, la línea se abre y se te echa encima. Una luz lo
  manda lejos.

## Novedades de la 1.1

- **Eliminadas** las criaturas que no son de Minecraft: Siren Head, el
  Skinwalker, el Wendigo, el Hombre Cabra, la Sombra (Hombre del Sombrero), la
  Estatua que Llora, el Mimic, el Ahogado, The Rake, el Eco y los Ojos.
- La **Ciudad Antigua** sigue (velas que se apagan, chilladores, pasos que
  vuelven, portal que zumba, susurros) y ahora, si te quedas un minuto,
  **Herobrine te observa desde las ruinas**.
- La **Página VI** ya no es de Siren Head: está **en tu tumba** (mira «Tu
  Tumba»).
- Los sustos de cara usan solo a Herobrine, null, el Hombre de la Niebla y el
  Morador. El apagón de luces del jefe sigue (ahora sin la Sombra).

## Qué hay de especial en Fears

- **Sonidos fuertes.** Todos los sustos, gritos y chillidos se han vuelto a
  sintetizar saturados y normalizados a máximo volumen, y suenan **dos veces a
  la vez**. Hay **4 sonidos nuevos**: explosión grave, chillido metálico,
  grito largo y cristales rotos. Cada susto lleva una explosión grave y un
  grito encima del golpe de siempre.
- **Los Miedos** (desde el día 1): una barra por miedo que se llena mientras
  estás expuesto. Al llenarse, pasa algo hecho a medida:
  - **La oscuridad** (de noche o bajo tierra, sin linterna ni antorchas a
    menos de 3 bloques): pantalla en negro, explosión, chillido a tu espalda.
  - **Las alturas** (14 o más bloques de caída): temblor, mareo y un lamento.
  - **El agua profunda** (8 o más bloques debajo): algo enorme pasa por
    debajo.
  - **Los espacios cerrados**: las paredes golpean desde el otro lado y se
    cierran.
  El Diario tiene la página **Tus miedos** con las barras.
- **Empieza en dificultad Alta**: todo llega el doble de rápido.

## Instalación

- **Directo:** abre [`dist/HorrorFears.mcaddon`](dist/HorrorFears.mcaddon) con Minecraft.
- **Por WhatsApp:** envía [`dist/HorrorFears_WhatsApp.zip`](dist/HorrorFears_WhatsApp.zip).
  Quien lo reciba lo descomprime y abre el `.mcaddon` de dentro con Minecraft.

Después, al crear o editar un mundo, activa **Horror Legends: Fears** en *Packs
de comportamiento* (el de recursos se añade solo). Requiere **Minecraft
Bedrock 1.21.0 o superior**. No necesita experimentos.

**Actualizar:** abre el `.mcaddon` nuevo (versión **1.2.0**). Si en los ajustes
del mundo sigue apareciendo una versión antigua, quítala y activa la 1.2.0. Los
días que llevas se conservan; las páginas leídas siguen leídas.

## La historia

Cada acto se anuncia con un título al caer la noche, y cada novedad llega con
su propio aviso en el chat la noche en que aparece.

| Día | | Qué pasa |
|---|---|---|
| 0 | **Acto I: Visitantes** | Sonidos que no están: pasos, puertas, uñas en la pared, golpes, tu nombre. Sustos: una cara delante de la tuya en un parpadeo, un aliento en la nuca, puertas que se cierran solas, estática. |
| 1 | Los Miedos | La oscuridad, las alturas, el agua profunda y los espacios cerrados (ver arriba). |
| 1 | HerobrineGamer788 | Un jugador con cascos de luz roja entra a tu partida. Juega, te regala cosas, **te copia**, se queda quieto cuando lo miras... y deja de fingir. |
| 1 | Pasos | Alguien camina detrás de ti, paso a paso. Cuando te paras, **da un paso más**. |
| 1 | El Morador de las Cuevas | Bajo tierra: sin ojos, a cuatro patas, con un **hocico de cuatro labios que se abre como una flor** sobre anillos de dientes. Huye si lo pillas mirando. Odia la luz. |
| 2 | El Aldeano Sin Nombre | Un aldeano gris con ojos blancos, en el campo. Se acerca si no lo miras; si lo miras, te señala. Su canturreo viene de donde está. |
| 1 | La Ciudad Antigua | Muy abajo, bajo la pizarra: las velas se apagan solas, los chilladores gritan, tus pasos vuelven tarde, el marco del portal zumba, susurros... y **Herobrine te mira desde las ruinas**. |
| 3 | **Acto II: El bosque** · Herobrine | Con la **columna fuera del cuerpo arqueada sobre la cabeza como la cola de un escorpión**. Te observa y desaparece si lo miras; pirámides de arena, árboles sin hojas, túneles, carteles, antorchas rojas. Si le pegas, la cola te alcanza. |
| 3 | La Visita | De noche, en casa, **llaman a la puerta** con tu voz. Si abres no hay nadie... y luego **estás detrás de ti**. Si no abres, deja un cartel. |
| 3 | La Cuenta Atrás | *«Cuando llegue a cero, date la vuelta»*. 10, 9, 8... y **una cara delante de la tuya**. |
| 4 | El Endermán Sin Rostro | Sin cara, solo una línea violeta. **Parpadea** hasta ti, cada vez más cerca, y lo oyes en 3D. Si lo miras, se echa encima. |
| 4 | El Hombre de la Niebla | Una niebla espesa y, dentro, un hombre en **chubasquero con capucha** y una sonrisa de oreja a oreja. Se acerca cuando apartas la vista; de noche llama a la puerta. |
| 4 | Tu Tumba | Una campana a tu espalda. Una tumba con **tu nombre y la fecha de mañana**. Si te acercas, algo golpea **desde abajo** (y ahí está la Página VI). |
| 5 | La Hora Muerta | Las **3:00**: animales y aldeanos **se paran y se giran a mirarte** mientras suena una campana tres veces. |
| 6 | **Acto III: El error** · null | Roto en franjas negras desalineadas, con la caja de F3+B. Corrompe bloques, escribe en el chat, finge que el juego se cuelga, acaba **detrás de ti**. |
| 6 | Entity 303 | Entra a tu partida, te cambia los objetos de mano, te "expulsa". En persona flota con la capucha blanca y **juega con tu mundo**: te hace volar, te teletransporta, caen rayos... y te **banea**. |
| 8 | La Noche Roja | Cada cuatro días, una noche de niebla roja: todo viene el doble de veces y tus faroles pueden apagarse. |
| — | **El final** | Junta las **siete páginas arrancadas**, haz **el ritual** y enfréntate a **Herobrine en su forma verdadera**. |

### Las siete páginas

| Página | Cómo se consigue |
|---|---|
| I. El jugador | Cuando HerobrineGamer788 se revela, queda en el suelo donde estaba. |
| II. Lo que vive abajo | Espanta al Morador con la linterna, o sobrevive a una persecución. |
| III. Ojos blancos | Mira fijamente a Herobrine hasta que desaparezca, tres veces. |
| IV. La niebla | Sobrevive a una niebla en la que lo hayas visto: al disiparse, cae a tus pies. |
| V. El error | Mira a null a los ojos dos veces, o sobrevive a su susto. |
| VI. La tumba | Acércate a **tu tumba** de noche y aguanta los golpes de debajo. |
| VII. El nombre | Aguanta el "espectáculo" de Entity 303 hasta que te banee. |

**Usa** una página para leerla: se guarda en el Diario.

### El final: el ritual

Con las siete páginas leídas, el Diario muestra **El Ritual**. De noche y al
aire libre, dices su nombre: truena, cae una niebla oscura, los rayos rodean el
lugar y aparece **Herobrine, el Minero Hueco**, con barra de jefe:

- casi **5 bloques** de pizarra agrietada con **lava por dentro**, el **casco
  de minero** con la lámpara encendida, un **pico en lugar de mano**, un
  **puño de piedra** y la **columna arqueada sobre su cabeza** hasta un
  aguijón de obsidiana;
- **se teletransporta** detrás de ti, **llama a los rayos**, **invoca**
  Moradores de las cuevas, **apaga la luz**, **te pica con la cola** (veneno y
  fuego) y, a mitad de vida, **enfurece**: las grietas arden más, corre más,
  te empuja con un rugido y apaga las luces;
- **la luz de la linterna le quema**: lo ralentiza y lo debilita.

Si ganas: **amanece**, te quedas su **Pico del Minero Hueco**, la maldición
**duerme tres días** y luego vuelve a empezar (las páginas se dispersan otra
vez). Si mueres, huyes o sale el sol, se retira: podrás llamarlo otra noche.

## Objetos

| Objeto | Receta | Uso |
|---|---|---|
| **Diario del Superviviente** | Libro + carbón vegetal (también te lo dan al entrar) | Día, acto, tu **miedo** y tus páginas. Actos, **Bestiario** de 19 entradas (lo que no has visto sale como ???), Páginas, **El Ritual**, Ajustes y **Pruebas**. |
| **Linterna** | `I G I / _ R _ / _ I _` (I = hierro, G = polvo de piedra luminosa, R = redstone) | Úsala para encenderla o apagarla. **Ilumina de verdad** lo que tienes delante mientras la llevas en la mano. Espanta al Morador, hace desaparecer a Herobrine, null y al Hombre de la Niebla cuando solo observa, y quema al jefe. A Entity 303 le da igual. |
| **Pila** | Redstone + pepita de hierro + lingote de cobre (da 2) | Una pila dura 5 minutos. Si llevas una de repuesto, se cambia sola. |
| **Farol Protector** | Farol rodeado de 4 fragmentos de amatista (`_ A _ / A F A / _ A _`) | Nada te acecha en su luz: el Morador y null se apartan, el Hombre de la Niebla no pisa su círculo ni rompe esa puerta, Herobrine no construye nada dentro, los miedos no se llenan y tu miedo baja. **Pero en la Noche Roja algo puede apagarlo.** |
| **Páginas arrancadas I–VII** | Las dejan las criaturas | Úsalas para leerlas. |
| **Pico del Minero Hueco** | Recompensa del final | Pico muy rápido que hace mucho daño y no se rompe. |
| **Bloque Corrupto** | — | La textura que falta. Lo crea null y vuelve solo a ser el bloque original. |

Las 9 criaturas tienen **huevo de generación** en creativo (el jefe
también: con huevo pelea incluso de día).

## Ajustes (en el Diario)

- **Intensidad**: *Baja*, *Normal*, *Alta* (por defecto en Fears) o *Pesadilla*.
- **Activar o desactivar** cada amenaza. Cada una dice en cuántos días llega.
- **Sustos fuertes**: pantalla roja, temblor, oscuridad y pitido en los oídos.
- **Sonidos fuertes**: los sonidos grandes suenan por duplicado.
- **Sonido 3D**: voces y pasos desde las criaturas, sonidos que se mueven y eco en cuevas.
- **Pruebas**: provoca al momento cualquiera de las 18 amenazas, te da los objetos (linterna, pilas, farol y páginas), empieza el ritual o avanza un día.

## Estructura

```
behavior_pack/scripts/
  main.js            engancha todo a Minecraft
  director.js        miedo, ritmo y hábitats: cuándo pasa algo, qué y dónde
  core/              utilidades, estado guardado, registro de criaturas, sustos,
                     reconocer la Ciudad Antigua, audio3d (sonidos en 3D)
  creatures/         un archivo por criatura (cada una se registra sola)
  events/            la Noche Roja, la Ciudad Antigua, la Visita, Pasos,
                     La Cuenta Atrás, Tu Tumba, La Hora Muerta, Los Miedos
  items/             linterna, Farol Protector, páginas
  ui/                el Diario
tools/
  build.py           lo genera y comprueba todo y empaqueta
  models/            un archivo por criatura: su modelo y su textura
  content.py         entidades, objetos, bloques, recetas, nieblas
  lang.py            todos los textos en español e inglés
  animations.py      todas las animaciones
  molang.py          evalúa las animaciones para las vistas previas
  paint.py           rampas de color y tramado
  art.py             iconos, bloques e icono del pack
  items3d.py         los objetos en 3D en la mano
  sounds.py          crea los sonidos (necesita numpy y soundfile; ya van generados)
  geometry.py        modelos en Python, comprobación de UV y renderizador 3D
  pixels.py          lienzo PNG
dist/                HorrorFears.mcaddon y HorrorFears_WhatsApp.zip
```

Para regenerar los paquetes: `python3 tools/build.py` (solo Python 3 estándar).
Con `python3 tools/build.py --preview carpeta` además dibuja cada modelo desde
tres ángulos.
