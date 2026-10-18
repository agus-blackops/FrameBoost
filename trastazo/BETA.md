# Trastazo 4.0 beta — guía para probadores

Gracias por probarlo. Este juego **nunca se ha probado en un móvil ni en un PC reales** (solo en simulación),
así que lo más valioso que puedes contar es qué pasa de verdad en tu dispositivo.

## Qué mirar (5-10 minutos)

1. **Arranque:** ¿abre sin cerrarse? ¿se oye la música y se ve bien la pantalla de inicio?
2. **Rendimiento:** en Ajustes activa *Mostrar FPS*. Coloca 20-30 objetos y personas: ¿cuántos FPS marca?
   (En móviles flojos prueba *Calidad: Ahorro* y *Motor: Canvas*.)
3. **Controles:** arrastrar personas con el dedo/ratón, zoom con dos dedos (o la rueda), abrir categorías en la
   barra izquierda. ¿Algún botón queda tapado o es difícil de tocar?
4. **Sonido:** ¿suenan explosiones, disparos, mordiscos, ladridos? ¿algo suena mal, muy alto o se corta?
5. **Cosas nuevas de las últimas versiones:** máquinas (sierra, pinchos, ventilador, prensa, Tesla), animales
   que comen cadáveres, refrigerador con persona congelada, nitrógeno líquido, construcciones.
6. **Guardado:** sal del juego y vuelve a entrar con *Continuar*: ¿está todo como lo dejaste?
7. **Desafíos:** ¿alguno es imposible o demasiado fácil?

## Si algo falla

* El juego guarda un **informe de errores** en un archivo de texto (sin enviar nada por internet):
  * **Android:** carpeta de datos de la app, archivo `trastazo-informe.txt`
    (con un explorador de archivos con acceso a `Android/data` o por `adb`).
  * **PC:** `%APPDATA%\Trastazo\trastazo-informe.txt` (Windows) o `~/.trastazo/trastazo-informe.txt`.
* Si el juego se cerró solo, al abrirlo otra vez la pantalla de inicio lo avisa con el aviso naranja.
* Cuéntanos: modelo de dispositivo, qué estabas haciendo y, si puedes, el archivo del informe.

## Qué sabemos que no está probado

* Todo el **sonido** (se genera por código; nadie lo ha oído todavía).
* **Móviles de gama baja** y pantallas muy anchas o muy cuadradas.
* La **versión de PC en Windows** (probada solo en una ventana de Linux simulada).
* El **equilibrio** de los desafíos y de la fuerza de los golpes.
