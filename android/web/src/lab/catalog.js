// Spanish wording for what the simulator reports. Names and numbers come from
// the Rust side; only the prose lives here.

export const SCENARIOS = {
  "calm-pan": {
    title: "Paneo tranquilo",
    about: "Un paneo lento sobre terreno abierto. No pasa nada malo: el controlador debería subir hasta el rung que cumple el presupuesto y quedarse ahí, sin titubear.",
  },
  "whip-turn": {
    title: "Giro brusco",
    about: "Un giro rápido de cámara entre tramos tranquilos. El paralaje abre grandes desoclusiones: durante el giro los frames generados se descartan y la escalera vuelve a subir despacio.",
  },
  "scene-cut": {
    title: "Cortes de escena",
    about: "Dos cortes. El primero cambia la exposición y toma el camino duro (colapso a espacial y cooldown largo); el segundo lo detecta la desoclusión. Defensa en profundidad.",
  },
  "particle-burst": {
    title: "Ráfaga de partículas",
    about: "Efectos que el G-buffer no menciona: profundidad y vectores de movimiento dicen que todo va bien, y solo la consistencia fotométrica lo nota. Es el fallo que hace ver rota la generación de frames.",
  },
  fence: {
    title: "Valla",
    about: "Un paneo sobre geometría fina, barras de un par de píxeles que desaparecen a media resolución. Es un problema espacial: la escalera debería quedarse baja y tantear el siguiente rung de vez en cuando.",
  },
  "load-swing": {
    title: "Carga variable",
    about: "El coste de render sube y baja mientras la reconstrucción es fácil. Solo el governor de rendimiento debería reaccionar; si el techo de calidad se mueve aquí, los dos governors están acoplados.",
  },
};

export const RUNG_SHORT = ["nativo", "ultra cal.", "calidad", "equilibr.", "rendim.", "rend.+gen", "ultra+gen"];
export const RUNG_NAMES = ["native", "ultra_quality", "quality", "balanced", "performance", "performance_gen", "ultra_performance_gen"];
export const rungLabel = (name) => RUNG_SHORT[RUNG_NAMES.indexOf(name)] ?? name;

export const SIGNALS = {
  disocclusion: "desoclusión: píxeles que el frame anterior no veía y no se pueden reproyectar",
  motion_residual: "residuo de movimiento: cambios que los vectores de movimiento no explican (partículas, efectos)",
  luma_shift: "cambio de luminancia: destellos o cambios de exposición",
  camera_motion: "movimiento de cámara: produce shimmer en el upscaler espacial",
  depth_complexity: "complejidad de profundidad: geometría fina que el upscaler espacial no puede resolver",
  pacing_instability: "irregularidad en el ritmo de frames",
};

export const KNOBS = {
  reject_below: { label: "Umbral de rechazo", min: 0.3, max: 0.8, step: 0.01, def: 0.55, help: "Confianza mínima para aceptar la reconstrucción. Más bajo = más boost, pero deja pasar frames que pueden verse mal." },
  growth_interval: { label: "Frames limpios para subir", min: 10, max: 600, step: 10, def: 120, help: "Cuántos frames sin rechazo hacen falta para subir un rung." },
  cooldown_frames: { label: "Cooldown tras rechazo", min: 0, max: 600, step: 10, def: 60, help: "Espera tras un rechazo antes de empezar a contar frames limpios." },
  cut_cooldown_frames: { label: "Cooldown tras corte", min: 0, max: 1200, step: 30, def: 300, help: "Espera tras un corte de escena." },
  backoff_rungs: { label: "Rungs que baja al rechazar", min: 1, max: 3, step: 1, def: 1, help: "Cuánto baja el techo con cada rechazo." },
  max_level: { label: "Rung máximo permitido", min: 0, max: 6, step: 1, def: 6, help: "Tope absoluto de la escalera (0 = nativo, 6 = ultra+gen)." },
};

export const ENV = {
  native_ms: { label: "Coste de un frame nativo (ms)", min: 5, max: 200, step: 1, def: 70 },
  target_fps: { label: "FPS objetivo", min: 30, max: 240, step: 1, def: 60 },
};
