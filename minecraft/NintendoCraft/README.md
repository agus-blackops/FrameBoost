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
| **Comunicado oficial** | La primera vez que entras al mundo sale el anuncio y te dan un kit: Catálogo de la Tienda, 10 monedas, 2 Bloques ? y 2 tuberías. |
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
| **Tienda Oficial** | Usa el Catálogo para comprar poderes, Bloques ? y tuberías con monedas. |
| **Modo Familiar** | Los creepers explotan pero ya no rompen bloques. La TNT sigue funcionando. |
| **Demandas** | El departamento legal de Nintendo te demanda: cada 8 minutos puede inventar un motivo, y te demanda en el acto si le pones un nombre "registrado" a una mascota o a un objeto (Mario, Pikachu, Zelda...). Lo que tenga ese nombre queda confiscado y renombrado. |
| **Abogado de Nintendo** | Aparece cerca de ti y te sigue. Es invencible, y si le pegas te sube la multa. Si no llega en 20 segundos, te manda la demanda "por correo". |
| **Juicio** | Cuando llega el abogado eliges: pagar la multa, defenderte solo (20% de ganar; si pierdes pagas el doble), contratar a tu abogado (10 monedas, 60% de ganar) o firmar un acuerdo y pagar la mitad. Si cierras el juicio cuenta como huir: vuelve en un minuto con un 50% más de multa. |
| **Embargo** | Si no te alcanza para pagar, te quitan todas las monedas y pasas 30 segundos de "arresto domiciliario" (lentitud y fatiga de minería). |
| **Nether de pago** | El Nether ahora es DLC: sin comprar "Expansión: El Nether" (30 monedas en la Tienda), el portal te devuelve. En creativo no aplica. El End es "gratis por tiempo limitado". |
| **Nintendo Online** | Cada 12 minutos llega un "aviso oficial". A veces te cobra 5 monedas de suscripción. |

## Recetas (mesa de crafteo)

| Objeto | Receta |
|---|---|
| 2 × Bloque ? | 8 tablones de madera alrededor de 1 lingote de oro |
| 4 × Tubería | 8 lingotes de hierro alrededor de 1 tinte verde |
| 4 × Monedas | 1 lingote de oro |
| Catálogo de la Tienda | 1 libro + 1 pepita de oro |

Los poderes no se craftean: salen de los Bloques ?, de la Tienda o de los Champi Gruñón.

## Estructura

```
NintendoCraft_BP/     Paquete de comportamiento
  blocks/             Bloque ?, Bloque Usado, Tubería
  items/              Monedas, poderes, catálogo
  entities/           Champi Gruñón, Abogado de Nintendo y bola de fuego
  scripts/            Lógica (API @minecraft/server 2.0.0)
    main.js           Punto de entrada
    bloques.js        Bloques ?, cabezazos, bloques en el cielo y tuberías
    poderes.js        Champiñones, flor de fuego, estrella, aplastar
    vidas.js          Vidas, marcador, GAME OVER, Modo Familiar y avisos
    tienda.js         Tienda Oficial y Nether de pago
    demandas.js       Demandas, abogado, juicio y embargos
NintendoCraft_RP/     Paquete de recursos (texturas, modelo, textos en español)
herramientas/         Generador de texturas
construir.sh          Crea dist/NintendoCraft.mcaddon
```
