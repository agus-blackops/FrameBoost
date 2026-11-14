# Roadmap de Sonora

Estado: **v1.9** · Android 8.0+ · Java + Media3 (compilado desde su código) · ~2,3 MB

Este documento separa lo que **está hecho y probado**, lo que **está hecho pero sin verificar en un teléfono real**, y lo que **falta**. Los tamaños son de esfuerzo relativo (S = días, M = 1–2 semanas, L = más), no fechas: no hay equipo ni calendario detrás.

---

## 1. Dónde estamos

| Versión | Qué trajo |
|---|---|
| 1.0 | Reproductor de música local, notificación, likes, playlists, buscar |
| 1.1 | Música en línea (Deezer, vistas previas de 30 s) y radios en vivo |
| 1.5 | Letras sincronizadas, ecualizador, temporizador, velocidad, cola, retomar sesión, historial |
| 1.6 | Podcasts (episodios completos), álbumes/artistas/playlists/géneros, más radios y estantes |
| 1.6.1 / 1.6.2 | Fandoms y gaming (The Living Tombstone y más); versión para PC |
| 1.7 | Icono nuevo (adaptable y temático) generado en Rust |
| 1.9 | Música completa gratis (Audius, Internet Archive), modo "Solo canciones completas", "Escuchar completa", Mezcla de descubrimiento, motor Media3 (sin pausas entre canciones, radios HLS, "ahora suena") |

**Probado** (59 pruebas automáticas en un simulador, con respuestas de ejemplo; se repiten con `./test/run-tests.sh`): flujos de pantallas, reproducción, cola, sesión, historial, letras, podcasts, persistencia, casos sin conexión, fuentes completas, mezcla, y el **reproductor Media3 real** con archivos WAV.

**Sin verificar en un dispositivo real** (el entorno donde se construyó no tiene emulador ni acceso a las APIs):
- Que las APIs reales (Deezer, Apple Podcasts, radio-browser, LRCLIB) respondan como las respuestas de ejemplo.
- El sonido real del ecualizador, refuerzo de graves y sonido envolvente.
- Cambio de velocidad, audio en segundo plano y botones de auriculares/Bluetooth en distintas marcas.
- Episodios con enlaces que redirigen o servidores lentos.
- Las APIs reales de **Audius** y del **Internet Archive** (1.9) y el streaming real con Media3.

---

## 2. Límites conocidos (la deuda honesta)

1. **Deezer solo da 30 segundos por canción.** Desde la 1.9 hay música completa gratis (Audius, Internet Archive) y el modo "Solo canciones completas", pero los éxitos comerciales siguen siendo vistas previas: no hay forma legal de tenerlos completos sin suscripción.
2. **Nunca se corrió en un teléfono real.** Todo lo anterior puede esconder fallos de compatibilidad.
3. **La clave de firma está en el repositorio con contraseña conocida.** Sirve para instalar el APK, no para publicar.
4. ~~Las pruebas no se pueden correr desde el repositorio.~~ Resuelto en la 1.9: `./test/run-tests.sh`. Falta conectarlo a una integración continua.
5. **La interfaz está armada en código** (`MainActivity` ~1.850 líneas, `Library` ~1.200): funciona, pero es difícil de mantener.
6. **Datos en SharedPreferences como JSON.** Aguanta cientos de elementos, no miles.
7. **Textos en español escritos en el código**, sin `strings.xml` ni otros idiomas.
8. **Sin descripciones de accesibilidad** (TalkBack) en los iconos.
9. **No hay APK de 64 bits explícito ni AAB**, necesarios para Google Play.
10. **Desde la 1.9 pide Android 8.0** (antes 5.0): Media3 usa funciones de Java 8 que, sin D8 (el compilador de Google, que el entorno de construcción no permite), solo funcionan desde Android 8. Compilar con D8 devolvería Android 5–7.
11. **Media3 se compila desde su código** en el primer `build.sh` (necesita GitHub y Maven Central), porque sus paquetes solo están en el repositorio de Google.

---

## 3. Ahora — v1.8 "Cimientos" (prioridad máxima)

Objetivo: que lo que ya existe sea confiable. Sin funciones nuevas grandes.

- **Probar en teléfonos reales** (al menos Android 8, 11 y 14) y arreglar lo que salga. *(S–M)*
- **Integración continua**: un script `test.sh` + GitHub Actions que compile y corra las 39 pruebas en cada cambio. *(S)*
- **Informe de errores**: capturar cierres inesperados y guardarlos en un archivo que el usuario pueda compartir (sin enviar nada solo). *(S)*
- **Accesibilidad**: descripciones de contenido en todos los iconos, tamaños de toque ≥ 48 dp, contraste. *(S)*
- **Textos a `strings.xml`** y versión en inglés y portugués. *(M)*
- **Clave de firma fuera del repositorio** y documentar cómo firmar una versión de lanzamiento. *(S)*
- Arreglar lo que descubra el punto 1 en servicios en segundo plano (Android 14 exige permisos de servicio en primer plano más estrictos).

Criterio de salida: instalación limpia en 3 teléfonos distintos, 1 hora de uso sin cierres, CI en verde.

---

## 4. ✅ Hecho en la 1.9 — "Música completa" (y lo que quedó pendiente)

Objetivo: resolver el límite de 30 segundos con fuentes **legales y gratuitas**.

- ✅ **Audius** (en lugar de Jamendo: no necesita clave): tendencias, géneros y búsqueda, canciones completas.
- ✅ **Internet Archive**: álbumes libres de netlabels y conciertos en vivo.
- ✅ Cada canción dice si es **completa** y de qué fuente; modo **"Solo canciones completas"**; **"Escuchar completa"** sobre una vista previa cuando Audius tiene la misma canción.
- ✅ **Mezcla de descubrimiento** (adelantada de la 2.0) y **Media3** con paso sin pausas entre canciones y radios HLS (adelantado de la 2.0).
- Pendiente: **Jamendo** (necesita una clave propia de desarrollador). *(S)*
- Pendiente: **descargas sin conexión** (episodios de podcast y música libre), con gestor de espacio. *(M–L)*
- Pendiente: **fundido entre canciones** (crossfade), que Media3 no trae. *(M)*

La v1.8 "Cimientos" (sección 3) sigue pendiente: la 1.9 se adelantó a pedido. De ella ya está hecho que las pruebas se puedan repetir desde el repositorio.

---

## 5. Después — v2.0 "Pulido y plataforma"

**Reproducción**
- ✅ ~~Migrar a Media3/ExoPlayer~~ (1.9). Falta: caché de audio y fundido cruzado (crossfade). *(M)*
- Normalización de volumen y "saltar silencios" en podcasts. *(M)*

**Podcasts**
- Avisos de episodios nuevos, marcar como escuchado, cola de episodios, capítulos. *(M)*
- Importar/exportar suscripciones (OPML). *(S)*

**Descubrimiento**
- ✅ ~~Mezcla de descubrimiento~~ (1.9). Siguiente: más mezclas (por estado de ánimo o por género) y "radio" a partir de una canción. *(M)*
- Búsqueda unificada: canciones, álbumes, artistas, podcasts y radios en una sola pantalla. *(M)*

**Datos**
- Pasar a **base de datos (Room/SQLite)**; copia de seguridad y restauración de playlists (JSON/M3U). *(M)*
- Importar playlists desde archivos M3U. *(S)*

**Plataforma**
- Widget de pantalla de inicio, Android Auto, Chromecast, Wear OS. *(L, por partes)*
- Modo tableta y horizontal; tema claro y colores dinámicos del sistema. *(M)*
- Reconstruir la interfaz con **Jetpack Compose** o dividir `MainActivity` en pantallas independientes. *(L)*

**Social (opcional)**
- Compartir una canción o playlist con un enlace; registro de lo escuchado en Last.fm. *(M)*

---

## 6. Qué NO está en el plan (y por qué)

- **Streaming completo de catálogos comerciales** (Deezer, Spotify, Apple Music) sin suscripción: las licencias no lo permiten.
- **Descargar música de servicios comerciales** o extraer audio de plataformas de video: viola sus términos.
- **Cuentas propias y servidor**: añaden costos, privacidad y mantenimiento; hoy todo vive en el teléfono.
- **Anuncios o monetización**: sin definir, y cambiaría las prioridades.

---

## 7. Decisiones que necesito de ti

1. **¿Quieres publicar en Google Play o distribuir el APK a mano?** Publicar exige AAB, firma segura, política de privacidad y revisar licencias de cada fuente.
2. **¿Música libre (Jamendo/Archive) es suficiente para "música completa"**, o prefieres integrar un servicio con cuenta?
3. **¿Idiomas?** Hoy solo español; ¿añadimos inglés y portugués en 1.8?
4. **¿Prioridad: estabilidad (1.8) antes de funciones nuevas?** Mi recomendación es sí: nunca corrió en un teléfono real.
5. **¿Nombre y marca definitivos?** "Sonora" es provisional; conviene revisar que no choque con marcas existentes antes de publicar.

---

## 8. Orden recomendado

1. **1.8 Cimientos** → confiabilidad y pruebas reales.
2. **1.9 Música completa** → fuentes legales completas + descargas de podcasts.
3. **2.0** en el orden que dicte el uso real: primero Media3 (gapless/crossfade), luego descubrimiento, luego plataformas (Auto/widget).

*Última revisión: v1.9.*
