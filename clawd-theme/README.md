# Tema Clawd para Samsung Galaxy A51

Paquete de personalización (One UI) con Clawd, la mascota de Claude Code. Medidas para el A51: **1080 × 2400**.

> No es una ROM. No reemplaza Android ni One UI, no necesita root ni desbloquear el bootloader
> (salvo la animación de arranque, que es opcional y se explica abajo).

## Qué incluye

| Carpeta | Contenido |
| --- | --- |
| `wallpapers/` | 6 fondos: Clásico, Naranja, Crema, Noche, Minimal (deja espacio al reloj) y Terminal |
| `icons/` | 12 iconos de 512 px en dos estilos (`naranja` y `oscuro`) más una hoja resumen de cada uno |
| `palette/` | Colores de Clawd en `paleta.png` y `palette.json` |
| `bootanimation/` | `bootanimation.zip` (720 × 1600, 30 fps) y una vista previa en GIF |
| `preview.html` | Maqueta del A51 para ver cómo queda (ábrela en el navegador) |

## Cómo aplicarlo

### 1. Fondo de pantalla
1. Copia las imágenes al teléfono y ábrelas en **Galería**.
2. Menú (⋮) → **Establecer como fondo de pantalla** → elige bloqueo, inicio o ambos.
3. Para el reloj de la pantalla de bloqueo, `05-minimal` es el que mejor se lee.

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
Necesita Python 3 y Pillow. Todo se dibuja desde código, así que puedes cambiar colores o textos en `tools/build.py`.
