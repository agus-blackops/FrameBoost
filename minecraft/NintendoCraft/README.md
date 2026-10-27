# NintendoCraft: ¿qué pasaría si Nintendo comprara Minecraft?

Add-on para **Minecraft Bedrock** (versión 1.21.90 o más nueva) que imagina el
día en que Nintendo compra Minecraft. Tu mundo recibe Bloques ?, tuberías,
monedas, vidas, poderes, una tienda oficial con precios "sin descuento" y
comunicados oficiales en broma.

> Es una parodia hecha por fans y no es oficial. No está afiliada con Nintendo ni con
> Mojang/Microsoft, ni tiene su respaldo. Todas las texturas son pixel art original.

## Instalación

1. Descarga `dist/NintendoCraft.mcaddon`.
2. Ábrelo: Minecraft importa los dos packs solo. En móvil, ábrelo con Minecraft
   desde el administrador de archivos.
3. Crea un mundo nuevo (o edita uno) y activa **NintendoCraft (Comportamiento)**
   en *Paquetes de comportamiento*. El de recursos se activa a la vez.

Si cambias algo y quieres volver a empaquetarlo, ejecuta `./construir.sh`.
Las texturas se generan con `python3 herramientas/generar_texturas.py`
(necesitas Pillow).

## Qué cambia cuando Nintendo compra Minecraft

| Novedad | Cómo funciona |
|---|---|
| **Comunicado oficial** | La primera vez que entras al mundo sale el anuncio y te dan un kit: Tarjeta de la Nintendo Shop, 10 monedas, 2 Bloques ?, 2 tuberías y un Formulario de Demanda. |
| **Monedas y vidas** | Abajo de la pantalla ves tus monedas y tus vidas. Cada **100 monedas** se convierten en **1 vida**. |
| **3 vidas y GAME OVER** | Cada muerte te quita una vida. Si pierdes la última sale **GAME OVER** y Nintendo te regala 3 vidas nuevas. |
| **Bloque ?** | Golpéalo **saltando desde abajo** (o tócalo con clic derecho, o rómpelo). Suelta monedas o un poder y se vuelve un Bloque Usado. Cada 45 segundos pueden aparecer Bloques ? nuevos en el cielo cerca de ti. |
| **Tuberías** | Coloca dos o más. **Agáchate encima** de una y viajarás a la siguiente, aunque esté en otra dimensión. |
| **Aplastar enemigos** | Cae encima de un monstruo para dañarlo y rebotar. El Champi Gruñón muere de un solo pisotón. |
| **Champi Gruñón** | Un champiñón malhumorado que aparece sobre el pasto y te persigue. Suelta monedas. |
| **Súper Champiñón** | Al comerlo ganas +4 corazones durante 2 minutos y fuerza. |
| **Champiñón 1-UP** | Al comerlo ganas una vida. |
| **Flor de Fuego** | Con clic derecho lanzas bolas de fuego. Aguanta 64 disparos. |
| **Estrella** | Te hace invencible y más rápido durante 12 s, y derrota a los monstruos que tocas. |
| **Nintendo Shop** | Abre la tienda con la Tarjeta de la Nintendo Shop. Todo es carísimo y además cobra un 10% de "comisión por usar la tienda". Antes de cada compra sale una confirmación ("no reembolsable"). Tiene secciones de juegos de Nintendo, juegos basura, poderes y bloques, expansiones (DLC) y tu biblioteca. |
| **Juegos de Nintendo** | Cuestan entre 350 y 450 monedas, siempre con "-0%" de descuento. Cada uno es un cartucho que se juega con clic derecho. *Mareo Kart 9 Deluxe* te da un turbo, aunque a veces te pega el caparazón azul. *La Leyenda de Zelga* te da una paravela. *Pokemán Escarlato y Púrpuro* atrapa un "Pokemán" salvaje, pero a veces se cierra con un error. *Súper Smosh Hermanos* manda a volar a los bichos cercanos. *Animales Cruzados* planta flores y Tom Nuk te presta 10 monedas (le debes 98.000). *Kirbi* inhala los objetos tirados cerca. A veces piden una "actualización obligatoria" antes de jugar. |
| **Juegos basura** | Juegos "destacados" de 35 a 60 monedas, cada uno peor que el anterior. *Simulador de Mirar una Pared VR*. *Pato Clicker*, que da 1 moneda el 2% de las veces. *Cubo Rojo: El Juego*, que pone un cubo rojo y ya ganaste. *Carreras de Caracoles 3D Extremo*, que te deja lentísimo. *Simulador de Descargas*, que se queda en 99% y da error. *Fútbol de Granja 2009 HD*, donde el balón es un cerdo. |
| **Mi biblioteca** | Si pierdes un cartucho, lo descargas otra vez gratis. Es lo único gratis de la tienda. |
| **Modo Familiar** | Los creepers explotan pero ya no rompen bloques. La TNT sigue funcionando. |
| **Demandas** | El departamento legal de Nintendo te demanda: cada 8 minutos puede inventar un motivo, y te demanda en el acto si le pones un nombre "registrado" a una mascota o a un objeto (Mario, Pikachu, Zelda...). Lo que tenga ese nombre queda confiscado y renombrado. |
| **Abogado de Nintendo** | Aparece cerca de ti y te sigue. Es invencible, y si le pegas te sube la multa. Si no llega en 20 segundos, te manda la demanda "por correo". |
| **Juicio** | Cuando llega el abogado eliges: pagar la multa, defenderte solo (20% de ganar; si pierdes pagas el doble), contratar a tu abogado (10 monedas, 60% de ganar) o firmar un acuerdo y pagar la mitad. Si cierras el juicio cuenta como huir: vuelve en un minuto con un 50% más de multa. |
| **Demandar a Nintendo** | Usa el **Formulario de Demanda contra Nintendo** (los jugadores nuevos reciben uno; también se craftea). Eliges un motivo, como la comisión de la tienda, el Nether de pago, el Pato Clicker que no da monedas, tu Pokemán que se cerró, el caparazón azul o que te demandaron. Después eliges abogado: tú mismo (gratis), barato (10 monedas, +10%) o carísimo (60 monedas, +30%). Presentar la demanda cuesta 15 monedas. Si eso **te pasó de verdad** en el juego, tienes una *prueba* y +25% de probabilidad. El juicio se ve en pantalla. Si ganas, Nintendo te paga, aunque a veces apela y te paga la mitad; si ganas por el Nether, te lo devuelven gratis. Si pierdes, Nintendo te contrademanda. Solo puedes demandar cada 5 minutos. |
| **Embargo** | Si no te alcanza para pagar, te quitan todas las monedas y pasas 30 segundos de "arresto domiciliario" (lentitud y fatiga de minería). |
| **Nether de pago** | El Nether ahora es DLC: sin comprar "Expansión: El Nether" (99 monedas en la Nintendo Shop), el portal te devuelve. En creativo no aplica. El End es "gratis por tiempo limitado". |
| **Nintendo Online** | Cada 12 minutos llega un "aviso oficial". A veces te cobra 5 monedas de suscripción. |

## Recetas (mesa de crafteo)

| Objeto | Receta |
|---|---|
| 2 × Bloque ? | 8 tablones de madera alrededor de 1 lingote de oro |
| 4 × Tubería | 8 lingotes de hierro alrededor de 1 tinte verde |
| 4 × Monedas | 1 lingote de oro |
| Tarjeta de la Nintendo Shop | 1 libro + 1 pepita de oro |
| Formulario de Demanda contra Nintendo | 1 papel + 1 saco de tinta + 1 pluma |

Los poderes no se craftean: salen de los Bloques ?, de la Nintendo Shop o de los Champi Gruñón.

## Estructura

```
NintendoCraft_BP/     Paquete de comportamiento
  blocks/             Bloque ?, Bloque Usado, Tubería
  items/              Monedas, poderes, tarjeta de la tienda, cartuchos, formulario de demanda
  entities/           Champi Gruñón, Abogado de Nintendo y bola de fuego
  scripts/            Lógica (API @minecraft/server 2.0.0)
    main.js           Punto de entrada
    bloques.js        Bloques ?, cabezazos, bloques en el cielo y tuberías
    poderes.js        Champiñones, flor de fuego, estrella, aplastar
    vidas.js          Vidas, marcador, GAME OVER, Modo Familiar y avisos
    tienda.js         Nintendo Shop y Nether de pago
    juegos.js         Juegos de Nintendo y juegos basura (cartuchos)
    demandas.js       Demandas, abogado, juicio y embargos
    demandar_a_nintendo.js  Tú demandas a Nintendo (motivos, pruebas, juicio)
NintendoCraft_RP/     Paquete de recursos (texturas, modelo, textos en español)
herramientas/         Generador de texturas
construir.sh          Crea dist/NintendoCraft.mcaddon
```
