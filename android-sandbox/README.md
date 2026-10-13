# Trastazo (Android)

**Trastazo — laboratorio de muñecos y caos.** Un sandbox de física con muñecos *activos*: la gente se
mantiene en pie, se tambalea, queda KO y se vuelve a levantar; los zombis persiguen y contagian; los humanos
huyen. Además de jugar libre, hay un modo **Desafíos** con objetivos y estrellas, un **Taller** de mods y una
banda sonora original compuesta y sintetizada por el propio juego. Todo el juego (física, gráficos y sonido) es código propio
en Java, sin motores ni assets externos; el APK pesa unos 80 KB.

APK listo para instalar: [`dist/trastazo.apk`](dist/trastazo.apk)
(Android 5.0+, pantalla horizontal; hay que permitir "instalar apps de orígenes desconocidos").

## Cómo se juega

| Gesto | Efecto |
|---|---|
| Barra inferior | Categorías: Herramientas, Gente, Objetos, Explosivos, Armas, Efectos |
| Tocar una tarjeta | Elige el objeto/herramienta (la paleta se desliza con el dedo) |
| Mano + arrastrar algo | Agarra y lanza personas y objetos (tirando fuerte se arrancan miembros) |
| Mano + arrastrar el fondo | Mueve la cámara |
| Dos dedos | Zoom y movimiento de cámara |
| Objeto elegido + tocar | Lo coloca donde sueltas el dedo |
| Cortar / Borrar / Fuego | Desliza sobre las cosas |
| Clavar | Fija una pieza en el aire (otra vez para soltarla) |
| Armas | Toca para disparar al punto; el rifle dispara en ráfaga si mantienes |
| Botones superiores | Pausa, cámara lenta, gravedad (normal/luna/cero), deshacer, limpiar, sonido |

Contenido: humano, gigante, niño y multitud; caja, caja grande, tabla, barril, pelota, globo, bloque de
acero, plataforma fija; granada, dinamita, TNT, barril explosivo, detonar y bomba nuclear; pistola, rifle,
escopeta, lanzacohetes, cuchillo y espada; fuego, rayo y onda de choque.

## Novedades de la v3.1: estilo People Playground

* **Daño por parte del cuerpo:** cada extremidad acumula daño y puede fracturarse (queda floja); con una
  pierna rota no se puede estar de pie. La cabeza tiene su propio daño.
* **Inspeccionar:** panel con el estado (consciente, inconsciente, muerto, infectado, ardiendo...), la sangre
  y cada parte (sana, herida, fractura, amputada, sangrando) + el cuerpo coloreado según el daño.
* **Jeringas:** Curación, Adrenalina, Resurrección, Virus zombi, Ácido (disuelve hasta el esqueleto) y Sedante.
* **Armas como objeto:** pistola, rifle y ametralladora que se colocan, se sueldan (a una mesa, a un coche, a
  la mano de alguien) y disparan con Activar, con retroceso. La ametralladora alterna fuego continuo.
* **C4:** se pega a lo primero que toca; Activar cualquier carga detona todas (detonador remoto).
* **Menú contextual:** mantén pulsado un objeto con la Mano → Activar, Inspeccionar, Duplicar, Clavar, Borrar.

## Novedades de la v3.0: motor gráfico propio (GPU)

* **Motor OpenGL ES 2.0 (`GLEngine`):** cada forma es un quad cuyo *fragment shader* evalúa un campo de
  distancias con signo (cápsula, aro, caja redondeada, elipse, sector, arco): antialiasing exacto a cualquier
  zoom y todo el mundo en un puñado de *draw calls*.
* **Iluminación dinámica:** fuego, explosiones, disparos, cohetes, propulsores, mechas, rayos, la luna y el
  suelo neón son fuentes de luz; cada tema tiene su luz ambiente (la Noche es oscura de verdad).
* **Post-proceso:** bloom (desenfoque gaussiano separable a media resolución), refracción de las ondas
  expansivas, viñeta, gradación de color y destellos.
* **Arquitectura:** `Draw2D` es una API de dibujo independiente del motor; el mismo `Renderer` dibuja en la
  GPU o en Canvas. En modo GPU la simulación y el render corren en el hilo de OpenGL y la interfaz en una capa
  Canvas encima. Si el móvil no puede con el motor GPU, el juego cambia solo al motor Canvas
  (Ajustes → Motor: GPU / Canvas).
* **Probado de verdad:** `test/GLTest.java` y `test/GLStress.java` ejecutan el motor real sin pantalla con
  Mesa (llvmpipe) mediante un shim de `android.opengl.GLES20` sobre LWJGL (`test/fetch-lwjgl.sh`).

## Novedades de la v2.2

* **Construcción:** Cuerda (entre dos objetos o colgando de un gancho en el aire), Soldar (unión rígida),
  Eje (bisagra: el centro de una rueda queda fijo y gira libre), Girar (rota cualquier objeto) y Activar
  (motores, propulsores, explosivos a distancia... o hacer saltar a una persona). Cortar también corta
  cuerdas y soldaduras. Todo se guarda con la escena.
* **Vehículos:** Rueda motor (arranca al montarla en un eje; Activar cambia adelante / atrás / apagado)
  y Propulsor (levanta incluso a una persona). Tabla + 2 ruedas motor + 2 ejes = un coche.
* **Cámara que sigue:** doble toque con la Mano sobre algo para seguirlo; doble toque en el fondo para soltar.
* **12 logros** con aviso al desbloquearlos y pantalla de colección en el menú principal.
* **Desafío 7 "Ingeniería":** lleva la caja hasta la bandera construyendo un vehículo.
* Corregido: tocar el interior de una rueda o pelota no la seleccionaba.

## Novedades de la v2.0

* **Muñecos activos:** equilibrio con "músculos" (control PD sobre la velocidad, con pies que agarran el
  suelo), KO al recibir golpes fuertes y animación de levantarse, caminar y girarse. Los zombis persiguen y
  muerden; un mordido que muere se levanta como zombi; los humanos huyen agitando los brazos.
* **Desafíos (6):** Derribo, Plaga zombi, Frutería, Reacción en cadena, Puntería y Supervivencia, con
  herramientas y munición limitadas y de 1 a 3 estrellas.
* **Música original (3 temas):** "Trastazo" (menú, La menor, 100 BPM), "Laboratorio" (sandbox, re dórico,
  108 BPM, con una capa de batería y melodía que sube con la acción) y "Desafío" (Mi menor, 132 BPM). Las
  melodías están escritas nota a nota en `Music.java` y se sintetizan al arrancar (osciladores PolyBLEP,
  envolventes, filtros, eco y reverb); el APK no lleva ningún archivo de audio.
* **Móvil:** menú principal con escena en vivo, pausa (botón atrás incluido), ajustes guardados (volumen de
  música y efectos, vibración, sangre sí/no, calidad, FPS), vibración háptica, autoguardado al salir y
  "Continuar", tutorial la primera vez, cámara lenta automática en las grandes explosiones.

## Modelos, texturas y Taller

* **Modelos nuevos:** Robot (sin sangre, suelta chispas y aceite), Zombi (sangre verde), Esqueleto,
  Rueda de camión, Bolas de bolos y Sandía (revienta al golpear).
* **Texturas / Estilo:** 6 fondos (Habitación, Laboratorio, Noche, Ladrillos, Neón, Desierto) y un
  interruptor de texturas: vetas de madera, remaches de acero, rayas y bolsillos en las camisetas,
  ladrillos, estrellas, baldosas y tablones de suelo.
* **Taller (mods):** 17 mods incluidos (Lluvia de Cajas, Cabezones, Luna Loca, Ventilador Gigante,
  Apocalipsis Zombi, Bombardeo, Tormenta Eléctrica, Invasión Robot...). Tócalos para activarlos;
  se pueden combinar. **"+ Crear mod"** abre un editor de texto para escribir los tuyos; mantén pulsado
  un mod propio para editarlo o borrarlo. Se guardan en la carpeta privada de la app (`files/mods/*.rpmod`).

Ejemplo de mod propio:

```
name Mi Mod
desc Zombis con gravedad lunar
gravity 0.3
wind 200
once spawn zombie 4
every 3 spawn crate
every 5 explode 250
```

Órdenes: `gravity`, `wind`, `bounce`, `headsize`, `humanscale`, `timescale`, `theme 0..5`,
`once spawn <objeto> [n]`, `every <seg> spawn <objeto> [floor]`, `every <seg> explode|lightning|ignite|shock`.
Objetos: human giant kid robot zombie skeleton crate bigcrate plank barrel ball balloon steel wheel
bowling melon grenade dynamite tnt xbarrel knife sword platform.

## Cómo está hecho

* `World` – física Verlet por posiciones (partículas + varillas), colisiones partícula/partícula y
  partícula/varilla con broadphase en rejilla, rozamiento, límites articulares de los ragdolls
  (como restricciones de distancia, que conservan el momento), desmembramiento, sangrado, fuego que se
  propaga, explosiones en cadena, disparos por trazado de rayos y cohetes.
* `Spawner` – construye personas y objetos. `Renderer`/`Fx` – dibujo y efectos. `Ui` – interfaz táctil.
* `Game` – gestos, herramientas y cámara. `GameView` – un `View` con aceleración por hardware.
* `Sfx` – sonidos sintetizados en tiempo de ejecución (sin archivos de audio).

## Compilar

No hace falta el SDK de Android completo. `build.sh` usa paquetes de Ubuntu
(`aapt zipalign apksigner dalvik-exchange libandroid-23-java`) y un JDK:

```
sudo apt-get install aapt zipalign apksigner dalvik-exchange libandroid-23-java
./build.sh        # genera build/ragdoll-playground.apk (firmado v1+v2+v3)
```

El APK se firma con `tools/debug.keystore` (clave de depuración pública, contraseña `android`):
sirve para instalar y actualizar, no para publicar en Google Play.

## Pruebas en escritorio

`test/run.sh` compila la lógica del juego contra un shim de `android.graphics` sobre Java2D y ejecuta
escenarios reales (caída de un humano, torres de cajas, explosión, disparo, estrés, gestos táctiles
simulados) generando capturas PNG:

```
test/run.sh all /tmp/shots
MAIN=InputTest test/run.sh /tmp/shots
```
