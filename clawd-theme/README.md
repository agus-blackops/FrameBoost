# Tema Clawd para Samsung Galaxy A51

Paquete de personalización (One UI) con Clawd, la mascota de Claude Code. Medidas para el A51: **1080 × 2400**.

> No es una ROM. No reemplaza Android ni One UI, no necesita root ni desbloquear el bootloader
> (salvo la animación de arranque, que es opcional y se explica abajo).

## Qué incluye

| Carpeta | Contenido |
| --- | --- |
| `animated/` | Fondo animado: `clawd-neon-1080x2400.mp4` (10 s en bucle, sin sonido) y una versión GIF más liviana |
| `wallpapers/` | 9 fondos fijos: 3 a todo color (Atardecer, Aurora, Ultravioleta, sacados del animado) y 6 más sobrios (Clásico, Naranja, Crema, Noche, Minimal, Terminal) |
| `icons/` | 12 iconos de 512 px en dos estilos (`naranja` y `oscuro`) más una hoja resumen de cada uno |
| `palette/` | Colores de Clawd en `paleta.png` y `palette.json` |
| `bootanimation/` | `bootanimation.zip` (720 × 1600, 30 fps) y una vista previa en GIF |
| `preview.html` | Maqueta del A51 para ver cómo queda (ábrela en el navegador) |

## Cómo aplicarlo

### 1. Fondo de pantalla
1. Copia las imágenes al teléfono y ábrelas en **Galería**.
2. Menú (⋮) → **Establecer como fondo de pantalla** → elige bloqueo, inicio o ambos.
3. Para el reloj de la pantalla de bloqueo, `05-minimal` es el que mejor se lee.

### 1b. Fondo animado
1. Copia `clawd-neon-1080x2400.mp4` al teléfono.
2. **Ajustes → Fondo de pantalla y estilo → Cambiar fondos de pantalla → Galería** y elige el video.
3. Samsung lo reproduce en la **pantalla de bloqueo**; la pantalla de inicio muestra un cuadro fijo.
   Si tu versión de One UI no lo acepta, usa el GIF (`clawd-neon-360x800.gif`), de menor calidad.
4. El video dura 10 s y no tiene sonido. Gasta algo más de batería que un fondo fijo.
5. Para ver el cielo en otro color, usa los fondos fijos Atardecer, Aurora y Ultravioleta.

### 2. Colores del sistema
1. **Ajustes → Fondo de pantalla y estilo → Paleta de colores**.
2. Elige la opción naranja que genera One UI a partir del fondo.
3. Los valores exactos de Clawd están en `palette/` por si quieres usarlos en otras apps.

### 3. Iconos
One UI no permite cambiar los iconos de otras apps por sí sola. Lo más sencillo:
1. Instala un launcher que lo permita, por ejemplo **Nova Launcher**.
2. Mantén presionado un icono → **Editar** → toca el icono → **Galería** → elige el PNG de `icons/`.
3. Repite con cada app. Cada icono tiene su nombre en español (`telefono.png`, `camara.png`, …) y `clawd.png` sirve para cualquier app que quieras marcar.

### 4. Animación de arranque (opcional, avanzado)
`bootanimation.zip` usa el formato de Android estándar (AOSP). Sirve en ROMs personalizadas como LineageOS,
copiándolo a `/system/media/bootanimation.zip`. **El One UI original de Samsung no lo usa**: ahí el arranque es un
archivo `.qmg` propio de Samsung, así que este zip no tendría efecto.
Modificar el sistema anula la garantía y activa Knox, y un error puede dejar el teléfono sin arrancar.
Si quieres, solo mira `vista-previa.gif`.

## Regenerar el paquete
```
python3 clawd-theme/tools/build.py
```
Necesita Python 3 y Pillow. El fondo animado se genera con `clawd-theme/tools/animate.py` (necesita además numpy y ffmpeg). Todo se dibuja desde código, así que puedes cambiar colores o textos en `tools/build.py`.
