# Horror Legends — Add-On de terror para Minecraft Bedrock

> *Este mundo no está tan vacío como parece.*

Una historia de terror por capítulos que, poco a poco, pone tu mundo en tu
contra. Tiene un final. Está inspirada en los mods de terror más conocidos de
Minecraft Java y en las leyendas de la comunidad:

- **The Man From The Fog** → *El Hombre de la Niebla*
- **Cave Dweller / From The Caves** → *El Morador de las Cuevas*
- **Herobrine**, la leyenda de siempre, y *HerobrineGamer788*, un jugador que
  no debería estar en tu mundo
- **The Broken Script** y la creepypasta de **null** → *null*

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

**Actualizar:** abre el `.mcaddon` nuevo (versión **6.0.0**). Si en los ajustes
del mundo sigue apareciendo una versión antigua, quítala y activa la 6.0.0. Los
días que llevas en el mundo se conservan; los ajustes vuelven a los de fábrica.

## La historia

Cada capítulo abre una amenaza nueva. Se anuncia con un título al caer la
noche.

| Día | Capítulo | Qué pasa |
|---|---|---|
| 0 | **Prólogo: Solo** | Pasos detrás de ti, puertas que se abren, cofres que se cierran, uñas arañando la pared, respiración, tu nombre susurrado. |
| 1 | **1. El jugador** | **HerobrineGamer788** entra a tu partida. Juega, te regala comida, construye... luego **te copia**, luego se queda quieto cuando lo miras y se acerca cuando no, y al final **deja de fingir**. |
| 2 | **2. Lo que vive abajo** | Bajo tierra oyes chasquidos, pasitos, susurros. El **Morador de las Cuevas** te acecha, huye si lo pillas mirando y, si esperas demasiado, **te persigue chillando**. Odia la luz. |
| 3 | **3. Ojos blancos** | **Herobrine** te observa a lo lejos y desaparece si lo miras. A veces está **justo detrás de ti**. Pirámides de arena, árboles sin hojas, túneles, carteles, antorchas rojas. Si le pegas, te devuelve el golpe. |
| 4 | **4. La Presencia** | Algo en tu casa: **las luces se apagan una a una**, unos **pasos te siguen** y se paran cuando te paras, una **cara en la ventana**, una **cajita de música**, alguien **junto a tu cama** al despertar, tu **propia muerte** en el chat. |
| 5 | **5. La niebla** | Llega una niebla espesa. El **Hombre de la Niebla** se acerca cada vez que apartas la vista. De noche **llama a tu puerta**... y a veces la rompe. |
| 6 | **6. El error** | **null** aparece por el rabillo del ojo, corrompe bloques en la textura que falta, escribe en el chat, finge que el juego se cuelga y acaba **detrás de ti**. |
| 7 | **7. La Noche Roja** | Cada 5 días, una noche de **niebla roja**: todo viene el doble de veces y tus faroles pueden apagarse. |
| — | **El final** | Junta las **cinco páginas arrancadas**, haz **el ritual** y enfréntate a **Herobrine en su forma verdadera**. |

### El director del miedo

Ya no pasan cosas al azar. Tienes un **nivel de miedo** (lo ves en el Diario)
que sube de noche, bajo tierra, con niebla, con poca vida, en la Noche Roja y
cuando algo está cerca; un Farol Protector lo baja. El director reparte el
terror como en una película:

1. **Calma:** algún ruido suelto.
2. **Tensión:** sustos pequeños cada poco (sonidos, carteles, siluetas a lo lejos).
3. **Clímax:** un encuentro de verdad, elegido según dónde estás (el Morador
   en las cuevas, el Hombre de la Niebla en la niebla, la Presencia en casa,
   Herobrine o null en cualquier parte).
4. **Respiro:** unos minutos para recuperarte... y vuelta a empezar.

De noche todo va más rápido. Tu **corazón late** cuando algo está cerca, y más
deprisa cuando te persigue.

### Las cinco páginas

Un superviviente anterior dejó un diario. Cada criatura guarda una de sus
páginas y la suelta cuando **sobrevives** a ella:

| Página | Cómo se consigue |
|---|---|
| I. El jugador | Cuando HerobrineGamer788 se revela, queda en el suelo donde estaba. |
| II. Lo que vive abajo | Espanta al Morador con la linterna, o sobrevive a una persecución. |
| III. Ojos blancos | Mira fijamente a Herobrine hasta que desaparezca, tres veces. |
| IV. La niebla | Sobrevive a una niebla en la que lo hayas visto: al disiparse, cae a tus pies. |
| V. El error | Mira a null a los ojos dos veces, o sobrevive a su susto. |

**Usa** una página para leerla: se guarda en el Diario.

### El final: el ritual

Con las cinco páginas leídas, el Diario muestra **El Ritual**. De noche y al
aire libre, dices su nombre: truena, cae una niebla oscura, los rayos rodean el
lugar y aparece **Herobrine, el Minero Hueco**, con barra de jefe:

- casi **4 bloques**, quemado por dentro con la luz saliendo por las grietas,
  el **casco de minero** todavía puesto, un **pico fundido en el brazo**, las
  costillas abiertas alrededor de un **corazón ardiendo** y una **corona de
  vértebras**;
- **se teletransporta** detrás de ti, **llama a los rayos**, **invoca**
  Moradores de las cuevas, **apaga la luz** y, a mitad de vida, **enfurece**:
  arde más, corre más, te empuja con un rugido y apaga las luces;
- **la luz de la linterna le quema**: lo ralentiza y lo debilita.

Si ganas: **amanece**, te quedas su **Pico del Minero Hueco**, la maldición
**duerme tres días** y luego vuelve a empezar (las páginas se dispersan otra
vez). Si mueres, huyes o sale el sol, se retira: podrás llamarlo otra noche.

## Objetos

| Objeto | Receta | Uso |
|---|---|---|
| **Diario del Superviviente** | Libro + carbón vegetal (también te lo dan al entrar) | Día, capítulo, tu **miedo** y tus páginas. Capítulos, **Bestiario** (lo que no has visto sale como ???), Páginas, **El Ritual**, Ajustes y **Pruebas**. |
| **Linterna** | `I G I / _ R _ / _ I _` (I = hierro, G = polvo de piedra luminosa, R = redstone) | Úsala para encenderla o apagarla. **Ilumina de verdad** lo que tienes delante mientras la llevas en la mano. Espanta al Morador, hace desaparecer a Herobrine, a null y al Hombre de la Niebla cuando solo observa, y quema al jefe. |
| **Pila** | Redstone + pepita de hierro + lingote de cobre (da 2) | Una pila dura 5 minutos. Si llevas una de repuesto, se cambia sola. |
| **Farol Protector** | Farol rodeado de 4 fragmentos de amatista (`_ A _ / A F A / _ A _`) | Nada te acecha en su luz: la Presencia no puede entrar, el Morador y null no se acercan, el Hombre de la Niebla no pisa su círculo ni rompe esa puerta y Herobrine no construye nada dentro. Tu miedo baja. **Pero en la Noche Roja algo puede apagarlo.** |
| **Páginas arrancadas I–V** | Las dejan las criaturas | Úsalas para leerlas. |
| **Pico del Minero Hueco** | Recompensa del final | Pico muy rápido que hace mucho daño y no se rompe. |
| **Bloque Corrupto** | — | La textura que falta. Lo crea null y vuelve solo a ser el bloque original. |

Todas las criaturas tienen **huevo de generación** en creativo (el jefe
también: con huevo pelea incluso de día).

## Ajustes (en el Diario)

- **Intensidad**: *Baja* (el doble de días, menos sustos), *Normal*, *Alta* o
  *Pesadilla* (todo desde el día 0 y muy seguido).
- **Activar o desactivar** cada amenaza. Cada una dice en cuántos días llega.
- **Sustos fuertes**: pantalla roja, temblor, oscuridad y pitido en los oídos.
  Si los quitas, queda un susto más suave.
- **Pruebas**: provoca al momento cualquier criatura o evento, te da los
  objetos (linterna, pilas, farol y páginas), empieza el ritual o avanza un
  día.

## Modelos, texturas y sonidos (6.0.0)

- **Texturas nuevas** para todo, pintadas con **rampas de color por
  material** (sombras frías, luces cálidas) y tramado, como el pixel art hecho
  a mano. Tienen el doble de detalle que un modelo normal (una cara es de
  16x16).
- **Herobrine**: joroba enorme con la columna fuera, arqueada hasta el
  cráneo; cabeza colgando delante; pecho reventado; estómago abierto; boca
  rajada hasta las orejas; mitad de la cara quemada hasta el hueso; ampollas;
  brazo derecho despellejado con un hueso saliendo de la muñeca y los dedos
  agarrando el pico; brazo izquierdo quemado con garras de hueso.
- **El Minero Hueco** (nuevo): el jefe, con dos texturas (en la fase 2 las
  grietas arden más).
- **El Hombre de la Niebla**: cabeza más grande y legible, frente, pómulos,
  cuencas que lloran negro, jirones en los brazos.
- **El Morador**: como una araña, con mandíbulas en las esquinas de la boca y
  espolones en los codos.
- **null**: desmontado, con la caja de F3+B, restos de una skin normal.
- **HerobrineGamer788**: sigue siendo Steve, repintado.
- **Iconos nuevos** para el diario, la linterna, la pila, las cinco páginas
  (cada una con su dibujo), el pico y el Farol Protector (modelo 3D propio).
- **Sonidos propios**, creados por código: latidos, respiración, susurros,
  estática, golpe de susto, pitido, dron, cajita de música, chillido y
  chasquidos del Morador, golpes en la puerta, **tema del jefe**, **páginas**,
  **clic de la linterna** y **amanecer**.

## Estructura

```
behavior_pack/scripts/
  main.js            engancha todo a Minecraft
  director.js        miedo y ritmo: cuándo pasa cada cosa
  lib/               utilidades, estado guardado, criaturas, sustos y latidos
  threats/           cada amenaza y el jefe
  items/             linterna, Farol Protector, páginas
  ui/                el Diario
tools/
  build.py           lo genera y comprueba todo y empaqueta
  content.py         entidades, objetos, bloques, recetas, nieblas, textos
  lang.py            todos los textos en español e inglés
  creatures.py       modelos y texturas de las criaturas
  paint.py           rampas de color y tramado
  art.py             iconos, bloques e icono del pack
  animations.py      todas las animaciones
  sounds.py          crea los sonidos (necesita numpy y soundfile; ya van generados)
  geometry.py        modelos en Python, comprobación de UV y renderizador 3D
  pixels.py          lienzo PNG
dist/                HorrorLegends.mcaddon y HorrorLegends_WhatsApp.zip
```

Para regenerar los paquetes: `python3 tools/build.py` (solo Python 3 estándar).
Con `python3 tools/build.py --preview carpeta` además dibuja cada modelo desde
tres ángulos.
