# Ragdoll Playground (Android)

Sandbox de física con ragdolls inspirado en *People Playground*: coloca personas y objetos, agárralos,
córtalos, quémalos, vuélalos o dispárales. Todo el juego (física, gráficos y sonido) es código propio
en Java, sin motores ni assets externos; el APK pesa unos 80 KB.

APK listo para instalar: [`dist/ragdoll-playground.apk`](dist/ragdoll-playground.apk)
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
