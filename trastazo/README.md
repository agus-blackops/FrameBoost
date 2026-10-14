# Trastazo 3.4 — construcciones y destrucción

**Laboratorio de muñecos y caos** para Android. La 3.3 es un juego nuevo: el motor de física, los personajes,
la IA y el mundo se han escrito otra vez desde una hoja en blanco. Sigue siendo código propio en Java, sin
motores ni assets externos (el APK pesa ~200 KB).

APK listo para instalar: [`dist/trastazo.apk`](dist/trastazo.apk) (Android 5.0+, pantalla horizontal; se instala
encima de las versiones anteriores).

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
