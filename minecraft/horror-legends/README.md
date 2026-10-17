# Horror Legends — Add-On de terror para Minecraft Bedrock

> *Este mundo no está tan vacío como parece.*

Una historia de terror en **tres actos** que, poco a poco, pone tu mundo en tu
contra, con **15 leyendas** del terror de Minecraft y de internet. Tiene un
final. Está inspirada en los mods de terror más conocidos de Java y en las
leyendas de la comunidad:

- **The Man From The Fog**, **Cave Dweller / From The Caves**, **The Broken
  Script** (null), **Herobrine** y **Entity 303**
- **Siren Head**, el **Skinwalker**, el **Wendigo**, el **Hombre Cabra**, el
  **Hombre del Sombrero**, los **Ángeles Llorosos / SCP-173** y el **Mimic**

Es un proyecto de fans: **no contiene código, arte ni sonido de esos mods**.
Los modelos, las texturas y los sonidos se generan por código en `tools/`, y
todo el comportamiento está escrito desde cero para Bedrock.

## Instalación

- **Directo:** abre [`dist/HorrorLegends.mcaddon`](dist/HorrorLegends.mcaddon) con Minecraft.
- **Por WhatsApp:** envía [`dist/HorrorLegends_WhatsApp.zip`](dist/HorrorLegends_WhatsApp.zip).
  Quien lo reciba lo descomprime y abre el `.mcaddon` de dentro con Minecraft.

Después, al crear o editar un mundo, activa **Horror Legends** en *Packs de
comportamiento* (el de recursos se añade solo). Requiere **Minecraft Bedrock
1.21.0 o superior**. No necesita experimentos.

**Actualizar:** abre el `.mcaddon` nuevo (versión **7.0.0**). Si en los ajustes
del mundo sigue apareciendo una versión antigua, quítala y activa la 7.0.0. Los
días que llevas en el mundo se conservan; las páginas que ya habías leído
siguen leídas (ahora hacen falta siete).

## La historia

Cada acto se anuncia con un título al caer la noche, y cada criatura nueva
llega con su propio aviso en el chat la noche en que aparece.

| Día | | Qué pasa |
|---|---|---|
| 0 | **Acto I: Visitantes** | Sonidos que no están: pasos, puertas, cofres, uñas en la pared, golpes, tu nombre. |
| 1 | HerobrineGamer788 | Un jugador con cascos de luz roja entra a tu partida. Juega, te regala cosas, **te copia**, se queda quieto cuando lo miras... y deja de fingir. |
| 1 | El Morador de las Cuevas | Bajo tierra: sin ojos, a cuatro patas, con un **hocico de cuatro labios que se abre como una flor** sobre anillos de dientes. Huye si lo pillas mirando. Odia la luz. |
| 2 | El Mimic | Un **cofre que no estaba**. Ábrelo, pégale o acércate y la tapa es una boca con dientes y le salen seis patas. Se le puede matar y suelta hierro, oro, esmeraldas o un diamante. |
| 2 | La Estatua que Llora | Una estatua con la cara entre las manos. **No se mueve si la miras**; si apartas la vista, o **parpadeas** (la pantalla se va a negro un instante), está más cerca, con las manos más bajas. Si te alcanza sin que la mires, te toca. |
| 3 | **Acto II: El bosque** · Herobrine | Con la **columna fuera del cuerpo arqueada sobre la cabeza como la cola de un escorpión**. Te observa y desaparece si lo miras; pirámides de arena, árboles sin hojas, túneles, carteles, antorchas rojas. Si le pegas, la cola te alcanza. |
| 3 | El Hombre Cabra | En el bosque de noche: **gritos de cabra** entre los árboles. Se acerca cuando no miras; cuando lo pillas, grita y huye... o carga. La luz lo espanta. |
| 4 | El Hombre de la Niebla | Una niebla espesa y, dentro, un hombre en **chubasquero con capucha** y una sonrisa de oreja a oreja. Se acerca cuando apartas la vista; de noche llama a la puerta. |
| 4 | El Skinwalker | **Una vaca que siempre te mira**, con ojos y dientes de persona. Oyes tu nombre en el campo. Si te acercas, se levanta: cráneo de vaca por cara y su piel a la espalda. |
| 5 | La Sombra | **El Hombre del Sombrero** entra en tu casa: apaga las luces una a una, te sigue, mira por la ventana, está a los pies de tu cama al despertar. Música de cajita, tu propia muerte en el chat. |
| 5 | El Ahogado | Cerca del agua, gorgoteos. De noche te mira desde la orilla. Si nadas, **te agarra desde abajo y tira de ti**. Pégale para que te suelte. |
| 6 | **Acto III: El error** · null | Roto en franjas negras desalineadas, con la caja de F3+B. Corrompe bloques, escribe en el chat, finge que el juego se cuelga, acaba **detrás de ti**. |
| 6 | Entity 303 | Entra a tu partida, te cambia los objetos de mano, te "expulsa". En persona flota con la capucha blanca y **juega con tu mundo**: te hace volar, te teletransporta, caen rayos... y te **banea**. |
| 7 | Siren Head | Una **sirena** muy lejos. Luego, entre los árboles y más alto que ellos, algo inmóvil con dos altavoces por cabeza. **No lo mires**: si lo miras demasiado, viene. **Escóndete** (bajo techo, en una cueva) hasta que te pierda. |
| 7 | El Wendigo | En la nieve y el bosque: un silbido, ramas que crujen. **Caza por el oído**: si corres, te oye y carga; **agáchate** y quédate quieto. Sus garras te dejan muerto de hambre. |
| 8 | La Noche Roja | Cada cuatro días, una noche de niebla roja: todo viene el doble de veces y tus faroles pueden apagarse. |
| — | **El final** | Junta las **siete páginas arrancadas**, haz **el ritual** y enfréntate a **Herobrine en su forma verdadera**. |

### El director del miedo

Ya no pasan cosas al azar. Tienes un **nivel de miedo** (lo ves en el Diario)
que sube de noche, bajo tierra, con niebla, con poca vida, en la Noche Roja y
cuando algo está cerca; un Farol Protector lo baja. El director reparte el
terror como en una película:

1. **Calma:** algún ruido suelto.
2. **Tensión:** sustos pequeños cada poco (sonidos, carteles, siluetas a lo lejos).
3. **Clímax:** un encuentro de verdad, **elegido según dónde estás**: en las
   cuevas (el Morador, el Mimic), en el bosque de noche (el Hombre Cabra, el
   Wendigo, Siren Head), en la nieve (el Wendigo), junto al agua (el Ahogado),
   en el campo (el Skinwalker), en casa (la Sombra, el Mimic) o en cualquier
   parte (Herobrine, la Estatua, null, Entity 303).
4. **Respiro:** unos minutos para recuperarte... y vuelta a empezar.

De noche todo va más rápido. Tu **corazón late** cuando algo está cerca, y más
deprisa cuando te persigue.

### Las siete páginas

Un superviviente anterior dejó un diario. Siete criaturas guardan una de sus
páginas y la sueltan cuando **sobrevives** a ellas:

| Página | Cómo se consigue |
|---|---|
| I. El jugador | Cuando HerobrineGamer788 se revela, queda en el suelo donde estaba. |
| II. Lo que vive abajo | Espanta al Morador con la linterna, o sobrevive a una persecución. |
| III. Ojos blancos | Mira fijamente a Herobrine hasta que desaparezca, tres veces. |
| IV. La niebla | Sobrevive a una niebla en la que lo hayas visto: al disiparse, cae a tus pies. |
| V. El error | Mira a null a los ojos dos veces, o sobrevive a su susto. |
| VI. La sirena | Escóndete de Siren Head hasta que te pierda. |
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
| **Diario del Superviviente** | Libro + carbón vegetal (también te lo dan al entrar) | Día, acto, tu **miedo** y tus páginas. Actos, **Bestiario** de 17 entradas (lo que no has visto sale como ???), Páginas, **El Ritual**, Ajustes y **Pruebas**. |
| **Linterna** | `I G I / _ R _ / _ I _` (I = hierro, G = polvo de piedra luminosa, R = redstone) | Úsala para encenderla o apagarla. **Ilumina de verdad** lo que tienes delante mientras la llevas en la mano. Espanta al Morador y al Hombre Cabra, hace desaparecer a Herobrine, null, la Sombra y al Hombre de la Niebla cuando solo observa, frena al Skinwalker y al Wendigo, y quema al jefe. A la Estatua, a Siren Head y a Entity 303 les da igual. |
| **Pila** | Redstone + pepita de hierro + lingote de cobre (da 2) | Una pila dura 5 minutos. Si llevas una de repuesto, se cambia sola. |
| **Farol Protector** | Farol rodeado de 4 fragmentos de amatista (`_ A _ / A F A / _ A _`) | Nada te acecha en su luz: la Sombra no puede entrar, la Estatua no se acerca, el Morador, null, el Hombre Cabra y el Wendigo se apartan, el Hombre de la Niebla no pisa su círculo ni rompe esa puerta, Siren Head te pierde y Herobrine no construye nada dentro. Tu miedo baja. **Pero en la Noche Roja algo puede apagarlo.** |
| **Páginas arrancadas I–VII** | Las dejan las criaturas | Úsalas para leerlas. |
| **Pico del Minero Hueco** | Recompensa del final | Pico muy rápido que hace mucho daño y no se rompe. |
| **Bloque Corrupto** | — | La textura que falta. Lo crea null y vuelve solo a ser el bloque original. |

Las 15 criaturas tienen **huevo de generación** en creativo (el jefe
también: con huevo pelea incluso de día).

## Ajustes (en el Diario)

- **Intensidad**: *Baja* (el doble de días, menos sustos), *Normal*, *Alta* o
  *Pesadilla* (todo desde el día 0 y muy seguido).
- **Activar o desactivar** cada amenaza. Cada una dice en cuántos días llega.
- **Sustos fuertes**: pantalla roja, temblor, oscuridad y pitido en los oídos.
  Si los quitas, queda un susto más suave.
- **Pruebas**: provoca al momento cualquiera de las 16 amenazas, te da los
  objetos (linterna, pilas, farol y páginas), empieza el ritual o avanza un
  día.

## Modelos, texturas y sonidos (7.0.0)

Los **15 modelos están hechos desde cero** en esta versión, con texturas
nuevas pintadas con rampas de color por material y tramado, al doble de
detalle que un modelo normal, y **animaciones propias** para cada uno (74 en
total: andar, acechar, cargar, gritar, abrir la boca, flotar, nadar...).

- **Herobrine**: tres bloques, la columna fuera como **cola de escorpión** con
  aguijón, estómago abierto y cosido, costillas abiertas, media cara quemada,
  mandíbula colgando, un pico clavado en el hombro, un brazo despellejado y
  otro carbonizado.
- **HerobrineGamer788**: Steve con **cascos gamer** y una luz roja; con los
  ojos blancos cuando se revela.
- **El Hombre de la Niebla**: chubasquero negro empapado, capucha, sonrisa de
  oreja a oreja, brazos hasta las rodillas.
- **El Morador**: sin ojos, a cuatro patas de araña, hocico de cuatro labios
  que se abre como una flor.
- **null**: negro, roto en franjas desalineadas con aberración roja y azul,
  antebrazo suelto con la textura que falta, caja de F3+B.
- **El Minero Hueco**: pizarra y lava, casco con lámpara, pico, puño de piedra
  y cola de vértebras; segunda textura para la fase de furia.
- **9 criaturas nuevas**: Siren Head, el Hombre Cabra, la Estatua que Llora
  (tres poses), el Mimic (cofre con patas), la Sombra (sombrero y humo), el
  Skinwalker (forma de vaca y forma verdadera), el Wendigo, Entity 303 y el
  Ahogado.
- **Sonidos nuevos** creados por código: sirena, voz por altavoz, piedra que
  se arrastra, gorgoteo, chillido, silbido, ruido de hackeo y mordisco.

## Estructura

```
behavior_pack/scripts/
  main.js            engancha todo a Minecraft
  director.js        miedo, ritmo y hábitats: cuándo pasa algo, qué y dónde
  core/              utilidades, estado guardado, registro de criaturas, sustos
  creatures/         un archivo por criatura (cada una se registra sola)
  events/            la Noche Roja
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
  sounds.py          crea los sonidos (necesita numpy y soundfile; ya van generados)
  geometry.py        modelos en Python, comprobación de UV y renderizador 3D
  pixels.py          lienzo PNG
dist/                HorrorLegends.mcaddon y HorrorLegends_WhatsApp.zip
```

Para regenerar los paquetes: `python3 tools/build.py` (solo Python 3 estándar).
Con `python3 tools/build.py --preview carpeta` además dibuja cada modelo desde
tres ángulos.
