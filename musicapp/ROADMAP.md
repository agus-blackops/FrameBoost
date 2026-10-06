# Roadmap de Sonora

Estado: **v1.6** · Android 5.0+ · Java sin dependencias · ~115 KB

Este documento separa lo que **está hecho y probado**, lo que **está hecho pero sin verificar en un teléfono real**, y lo que **falta**. Los tamaños son de esfuerzo relativo (S = días, M = 1–2 semanas, L = más), no fechas: no hay equipo ni calendario detrás.

---

## 1. Dónde estamos

| Versión | Qué trajo |
|---|---|
| 1.0 | Reproductor de música local, notificación, likes, playlists, buscar |
| 1.1 | Música en línea (Deezer, vistas previas de 30 s) y radios en vivo |
| 1.5 | Letras sincronizadas, ecualizador, temporizador, velocidad, cola, retomar sesión, historial |
| 1.6 | Podcasts (episodios completos), álbumes/artistas/playlists/géneros, más radios y estantes |

**Probado** (39 pruebas automáticas en un simulador, con respuestas de ejemplo): flujos de pantallas, reproducción, cola, sesión, historial, letras, podcasts, persistencia y casos sin conexión.

**Sin verificar en un dispositivo real** (el entorno donde se construyó no tiene emulador ni acceso a las APIs):
- Que las APIs reales (Deezer, Apple Podcasts, radio-browser, LRCLIB) respondan como las respuestas de ejemplo.
- El sonido real del ecualizador, refuerzo de graves y sonido envolvente.
- Cambio de velocidad, audio en segundo plano y botones de auriculares/Bluetooth en distintas marcas.
- Episodios con enlaces que redirigen o servidores lentos.

---

## 2. Límites conocidos (la deuda honesta)

1. **Deezer solo da 30 segundos por canción.** Es el mayor límite del producto: para "música real" completa solo sirven tu música local y las radios.
2. **Nunca se corrió en un teléfono real.** Todo lo anterior puede esconder fallos de compatibilidad.
3. **La clave de firma está en el repositorio con contraseña conocida.** Sirve para instalar el APK, no para publicar.
4. **Las pruebas no se pueden correr desde el repositorio.** Viven en `test/`, pero el entorno que las ejecuta (Robolectric + librerías) solo existía en la sesión de trabajo. Hoy no hay integración continua.
5. **La interfaz está armada en código** (`MainActivity` ~1.850 líneas, `Library` ~1.200): funciona, pero es difícil de mantener.
6. **Datos en SharedPreferences como JSON.** Aguanta cientos de elementos, no miles.
7. **Textos en español escritos en el código**, sin `strings.xml` ni otros idiomas.
8. **Sin descripciones de accesibilidad** (TalkBack) en los iconos.
9. **No hay APK de 64 bits explícito ni AAB**, necesarios para Google Play.

---

## 3. Ahora — v1.7 "Cimientos" (prioridad máxima)

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

## 4. Siguiente — v1.8 "Música completa"

Objetivo: resolver el límite de 30 segundos con fuentes **legales y gratuitas**.

- **Jamendo** (música con licencia Creative Commons, canciones completas, API gratuita con clave propia). Estantes, búsqueda y páginas de artista/álbum como las de Deezer. *(M)*
- **Internet Archive / Free Music Archive** como fuentes extra de música libre. *(M)*
- **Descargas sin conexión**, empezando por **episodios de podcast** (lo más simple y sin problemas de licencias) y música libre. Gestor de descargas con espacio usado y borrado. *(M–L)*
- Marcar claramente en cada canción de qué fuente viene y si es completa o vista previa. *(S)*

Decisión pendiente (ver sección 7): ¿Jamendo y Archive bastan, o se quiere integrar un servicio con cuenta (p. ej. Spotify con su SDK, que exige Premium)?

---

## 5. Después — v2.0 "Pulido y plataforma"

**Reproducción**
- Migrar de `MediaPlayer` a **Media3/ExoPlayer**: mejor streaming, caché, reproducción sin pausas entre canciones (gapless), fundido cruzado (crossfade), radios HLS. *(L)*
- Normalización de volumen y "saltar silencios" en podcasts. *(M)*

**Podcasts**
- Avisos de episodios nuevos, marcar como escuchado, cola de episodios, capítulos. *(M)*
- Importar/exportar suscripciones (OPML). *(S)*

**Descubrimiento**
- "Mezclas" generadas a partir de tu historial y tus "me gusta". *(M)*
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
3. **¿Idiomas?** Hoy solo español; ¿añadimos inglés y portugués en 1.7?
4. **¿Prioridad: estabilidad (1.7) antes de funciones nuevas?** Mi recomendación es sí: nunca corrió en un teléfono real.
5. **¿Nombre y marca definitivos?** "Sonora" es provisional; conviene revisar que no choque con marcas existentes antes de publicar.

---

## 8. Orden recomendado

1. **1.7 Cimientos** → confiabilidad y pruebas reales.
2. **1.8 Música completa** → fuentes legales completas + descargas de podcasts.
3. **2.0** en el orden que dicte el uso real: primero Media3 (gapless/crossfade), luego descubrimiento, luego plataformas (Auto/widget).

*Última revisión: v1.6.*
