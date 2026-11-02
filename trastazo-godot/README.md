# Trastazo Playground para Godot — 1.0

El mismo juego (versión 5.3: gente, trampas, edificios, destrucción tipo The Finals, Levolution, sangre y gore V6...)
como proyecto de **Godot 4.3 (.NET / C#)** para Android, Windows, Linux y macOS.

El juego entero —física, IA, mundo, interfaz y dibujo— está en `Core/`: es C# traducido del Java original (el mismo núcleo que usa
la versión de Unity), sin ninguna dependencia de motor. Lo único propio de Godot está en `Godot/` (unas 600 líneas):

| Archivo | Qué hace |
|---|---|
| `Godot/TrastazoApp.cs` | Nodo raíz: arranca el juego, lo actualiza cada frame y lo dibuja; guardado, pausa, pantalla completa, vibración |
| `Godot/GodotCanvasRenderer.cs` | Dibuja el fotograma del juego (triángulos de color y texto) con `RenderingServer.CanvasItemAddTriangleArray` y `DrawString` |
| `Godot/GodotInput.cs` | Táctil, ratón (botón izquierdo = tocar, derecho/central = mover, rueda = zoom) y teclado (Espacio, Z, S, G, H, 0-9, flechas, F11, Esc...) |
| `Godot/GodotAudio.cs` | Efectos sintetizados (`AudioStreamWav`) y banda sonora (`AudioStreamGenerator`) |
| `Godot/ModEditor.cs` | Editor de mods del Taller (ventana con cuadro de texto) |

## Cómo abrirlo

1. Instala **Godot 4.3 .NET** (la versión «.NET», no la estándar) y el SDK de .NET 8.
2. Abre `project.godot` desde el gestor de proyectos.
3. Pulsa **Construir** (martillo, arriba a la derecha) y luego **Ejecutar** (F5). La escena principal es `scenes/Main.tscn`.

Para exportar: *Proyecto → Exportar…* (hay ajustes preparados para Windows, Linux y Android). Android necesita las plantillas de
exportación de .NET, el SDK de Android y una keystore, como cualquier proyecto de Godot.

## Mantenerlo al día

El Java sigue siendo la fuente. Para llevar un cambio a Godot:

```sh
sh ../trastazo-unity/tools/test_all.sh   # traduce el Java a C# y compila los tests
sh tools/sync_core.sh                    # copia el núcleo traducido a Godot/Core
sh tools/typecheck.sh                    # compila contra la API real de GodotSharp
```

Los tests del núcleo (los mismos de Unity, `trastazo-unity/tools/run_tests.sh`) corren sin motor.

## Estado

* Compila sin errores contra GodotSharp 4.3 (`tools/typecheck.sh`), y el núcleo pasa los mismos tests que en Unity.
* **No se ha abierto en el editor de Godot ni probado en un móvil**: no había Godot instalado donde se escribió. Si algo falla al
  arrancar, `trastazo-informe.txt` (en la carpeta de datos del usuario) recoge el error.
* El recorte de texto en listas con desplazamiento es aproximado (se omite el texto fuera de la lista, no se corta a medias).
