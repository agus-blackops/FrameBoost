# Trastazo 3.8 — máquinas, pinchos y nitrógeno líquido

**Laboratorio de muñecos y caos** para Android. La 3.3 es un juego nuevo: el motor de física, los personajes,
la IA y el mundo se han escrito otra vez desde una hoja en blanco. Sigue siendo código propio en Java, sin
motores ni assets externos (el APK pesa ~200 KB).

**PC (Windows, Linux, macOS):** [`dist/trastazo-pc.zip`](dist/trastazo-pc.zip) — descomprimir y abrir `Trastazo.bat` (o `java -jar Trastazo.jar`). Necesita Java 8 o superior ([adoptium.net](https://adoptium.net)).

APK listo para instalar: [`dist/trastazo.apk`](dist/trastazo.apk) (Android 5.0+, pantalla horizontal; se instala
encima de las versiones anteriores).

## Novedades de la 3.8

Más cosas al estilo People Playground:

* **Categoría Máquinas** (fijas en su sitio; *Activar* las enciende y apaga):
  * **Sierra circular:** el disco gira y corta lo que toca: heridas, sangre, chispas y amputaciones; también
    destroza cajas y tablas.
  * **Pinchos:** quien cae encima con fuerza **se queda clavado** (sangra, forcejea). Un tirón fuerte con la
    mano lo suelta.
  * **Ventilador:** sopla hacia donde mira; las pelotas salen volando y a la gente la empuja y tumba.
  * **Prensa hidráulica:** la placa baja de golpe y sube despacio; lo que queda debajo contra el suelo se
    **aplasta** (personas, animales, sandías, cajas...). El acero y lo muy pesado la frenan.
  * **Bobina Tesla:** lanza rayos a lo que se acerca: **electrocuta** (sacudidas, quemaduras, KO), detona
    explosivos y avería máquinas.
* **Nitrógeno líquido** (jeringa): la persona se **congela**, queda rígida y azulada; se descongela sola (o con
  fuego). Un golpe fuerte, una caída o un balazo la **hace añicos** en trozos de hielo.
* **Bate y martillo** (Armas) para golpear agarrándolos con la mano.
* Logros nuevos *Brocheta* y *Cubito de hielo*.

## Novedades de la 3.7: versión PC

* **El mismo juego en una ventana de escritorio**: misma física, IA, animales, construcciones, desafíos, logros,
  mods y música. El código del juego es el mismo que en Android; solo cambian el dibujo (Java2D), el sonido
  (Java Sound, con mezclador propio para los efectos) y la entrada (ratón y teclado).
* **Controles:** clic izquierdo = dedo (tocar y arrastrar; mantener = menú del objeto), clic derecho o
  central = mover la cámara, rueda = zoom (sobre el catálogo, desplazar), 1-9/0 = categorías, Espacio/P =
  pausa, S = cámara lenta, G = gravedad, Ctrl+Z = deshacer, H = soltar la herramienta, flechas/A/D/W y +/- =
  cámara, Esc = volver/menú, F11 o Alt+Enter = pantalla completa (también en Ajustes).
* La interfaz se escala con la ventana y la **resolución de dibujo se ajusta sola** si el PC va justo, para
  mantener la fluidez.
* Partidas, ajustes y mods en `%APPDATA%\Trastazo` (Windows) o `~/.trastazo` (Linux/macOS).
* Se compila con `pc/build.sh` (solo necesita un JDK); `pc/test/PcSmoke.java` prueba la ventana real con
  ratón y teclado simulados.

## Novedades de la 3.6

* **La mano ya no es una herramienta:** como en el original, siempre está activa. Elijas lo que elijas en el
  catálogo, arrastrar algo con el dedo lo agarra; tocar sin arrastrar usa la herramienta elegida (colocar,
  disparar, inyectar...); mantener pulsado abre el menú del objeto. Tocar otra vez la carta elegida la
  deselecciona. Borrar, Cortar, Fuego y Rifle siguen usando el arrastre para sí mismos.
* **Animales (nueva categoría):** perro, cerdo y rata. Pasean, se caen y se levantan; el perro ladra a zombis
  y matones, el cerdo y la rata huyen. **Se comen los cadáveres pieza a pieza** (con mordiscos, sangre y algún
  hueso que dejan tirado) y unos segundos después **hacen caca**, que huele (humo verde) y se aplasta si la
  pisas o le disparas. Se les puede herir, matar o reventar. También hay *Caca* colocable.
* **Remodelado de armas y objetos:** pistola, rifle con mira y culata de madera, ametralladora de tres
  cañones, cuchillo y espada con hoja afilada, cajas con tablas y refuerzo, barriles con aros, TNT en
  cartuchos con temporizador, granada, C4, ruedas, propulsor, viga de acero y plataforma con franjas. Los
  soldados llevan el mismo rifle nuevo.
* Sonidos nuevos (ladrido/gruñido/chillido, mordisco, pedo) y logro *Ciclo de la vida*.

## Novedades de la 3.5

* **Destrucción estilo sandbox:**
  * **Heridas visibles** en cada parte del cuerpo: agujeros de bala, cortes de armas afiladas, quemaduras de
    explosión y moratones de golpes, pegados a la pieza y girando con ella. Las partes dañadas se enrojecen
    (o se ennegrecen en robots). La jeringa de curación las cierra.
  * **Gibs:** una explosión a quemarropa, una bala pesada en la cabeza o un golpe brutal revientan la parte en
    trozos físicos (carne, metal o hueso según el personaje) que rebotan por la sala. Con la sangre
    desactivada los trozos salen del color de la ropa/piel. Sin cabeza, torso o pelvis no hay resurrección.
  * **Objetos que se rompen:** cajas, tablas y bloques de construcción acumulan daño (balas, golpes, explosiones),
    se agrietan y se parten en fragmentos más pequeños, que a su vez pueden romperse hasta quedar en astillas.
    Los ladrillos aguantan más que la madera y el acero mucho más. Hay un límite de 90 trozos en la sala.
* **Remodelado:** cuerpos con contorno oscuro, relleno plano, borde iluminado y sombra; cabezas cuadradas con
  pelo en bloque; bloques con textura de ladrillo, madera o acero; nuevo fondo **Sandbox** (sala oscura con
  cuadrícula), que es el fondo por defecto.
* **Interfaz como un sandbox de PC:** barra de categorías a la izquierda y catálogo de objetos en cuadrícula
  de dos columnas al lado, con desplazamiento vertical. Al elegir algo el catálogo se cierra solo. En los
  desafíos se mantiene la barra de herramientas abajo.
* Logros nuevos *Carnicero* y *Escombros*; corregido el orden de los logros (en la 3.4 reanimar mostraba
  *Demoledor*).

## Novedades de la 3.4

* **Construir (pestaña primera):** casa, puente, torre de ladrillos, muro y coche ya montados. Un toque
  coloca la construcción entera. El coche arranca solo.
* **Destrucción de verdad:** cada construcción son bloques unidos por soldaduras con fuerza de rotura. Un
  golpe fuerte o una explosión las rompe, y las piezas caen y se separan. Una persona que cae sobre una casa
  no la derriba; una caja pesada o una explosión sí. Logro *Demoledor* (10 uniones rotas).
* **Interfaz:** la pestaña de construcciones es la primera y el juego arranca con ella a mano.

## Novedades de la 3.3

## Qué hay de nuevo respecto a la 3.2

* **Motor de física de cuerpos rígidos propio** (`Sim`, `RB`, `Shape`, `Joint`): círculos, cápsulas y cajas,
  contactos persistentes con *warm starting*, articulaciones con límites, **músculos PD**, motores de velocidad,
  uniones soldadas y rotas por fuerza, pasos fijos de 120 Hz. Las pilas de cajas se sostienen, las ruedas ruedan
  de verdad y los coches se mueven con motores reales.
* **Ragdolls activos de 13 piezas y 12 articulaciones** (`Person`): se mantienen en pie con un titiritero
  suave, andan y corren con una marcha cinemática (IK de dos eslabones), se giran, caen, quedan KO y **se
  levantan solos**. Cada pieza tiene su propia vida: fracturas, hemorragias, amputaciones por fuerza o por corte.
* **IA programada** (`Mind`): civiles que huyen de zombis, matones, explosivos encendidos y fuego; zombis que
  cazan y contagian; **soldados** que apuntan con el brazo de verdad y solo disparan cuando el arma apunta al
  objetivo y no hay un civil en la línea; **médicos** que curan y hacen RCP; **bomberos** que apagan con manguera;
  **matones** que buscan pelea; **torretas** con servo y mira láser; **drones** con control de vuelo.
* **Mundo** (`World`): daño por velocidad de impacto, sangre, fuego que se propaga, explosiones con onda
  de choque, balas con perforación, cohetes, rayos, jeringas (curar, adrenalina, resucitar, virus, ácido, sedante).
* **Cuerdas** como cadenas de cápsulas, **soldar**, **eje** (con ruedas motor), **cortar**, **clavar**, C4 remoto,
  armas como objeto (se sueldan a un coche o a una mesa), menú contextual con pulsación larga, inspector.
* Guardado de escena completo (poses, estado, uniones), Taller de mods, 7 desafíos, 15 logros, 6 fondos.

## Cómo se juega

| Gesto | Efecto |
|---|---|
| Barra inferior | Categorías: Herramientas, Gente, Objetos, Explosivos, Armas, Efectos, Jeringas, Taller, Estilo |
| Tocar una tarjeta | Elige el objeto/herramienta |
| Mano + arrastrar algo | Agarra y lanza personas y objetos |
| Mano + mantener pulsado | Menú: Activar, Inspeccionar, Duplicar, Clavar, Borrar |
| Dos dedos / arrastrar el fondo | Zoom y cámara |
| Objeto elegido + tocar | Lo coloca |
| Cortar / Borrar / Fuego | Desliza sobre las cosas |
| Cuerda / Soldar / Eje | Toca dos piezas (o el aire) |

## Estructura

```
src/com/frameboost/ragdoll/
  Sim, RB, Shape, Joint      física de cuerpos rígidos
  Person, Mind               ragdoll activo + IA
  World, Prop, Ent           mundo, objetos, daño, fuego, explosiones
  Game, Ui, Screens          entrada, herramientas, menús
  Renderer, Fx, Draw2D …     dibujo (motor GPU OpenGL ES 2.0 o Canvas), partículas
  Music, MusicPlayer, Sfx    banda sonora y efectos generados por código
```

## Compilar y probar

```
./build.sh                     # APK sin SDK: aapt + javac + dx + zipalign + apksigner
test/fetch-lwjgl.sh            # (una vez) bibliotecas para las pruebas de GPU
MAIN=PhysTest   test/run.sh    # motor de física
MAIN=InputTest  test/run.sh /tmp/shots
MAIN=AiTest     test/run.sh /tmp/shots   # comportamiento de la IA
MAIN=MonkeyTest test/run.sh 6            # juego aleatorio
MAIN=GLStress   test/run.sh /tmp/shots   # motor GPU con Mesa llvmpipe
```
