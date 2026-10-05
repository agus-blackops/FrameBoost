// A plain-language reading of one simulator run. No model involved: every
// sentence is a rule over the measured numbers.

import { RUNG_SHORT, SIGNALS, rungLabel } from "./catalog.js";

const pct = (x) => `${x.toFixed(1)}%`;

/** Segments of constant rung from the timeline, e.g. [{rung: 3, cols: 12}]. */
export function segments(rungString) {
  const out = [];
  for (const ch of rungString) {
    const r = Number(ch);
    if (out.length && out.at(-1).rung === r) out.at(-1).cols++;
    else out.push({ rung: r, cols: 1 });
  }
  return out;
}

/** Bullet points, most important first. */
export function diagnose(run) {
  const r = run.result;
  const s = run.settings;
  const lines = [];
  const budgetMs = 1000 / s.target_fps;

  // Did it meet the budget?
  if (r.mean_frame_ms <= budgetMs * 1.05) {
    lines.push(`Cumple el presupuesto: ${r.mean_frame_ms.toFixed(1)} ms de media frente a ${budgetMs.toFixed(1)} ms, y entrega ${r.delivered_fps.toFixed(1)} fps a la pantalla.`);
  } else {
    lines.push(`No llega al presupuesto: ${r.mean_frame_ms.toFixed(1)} ms de media frente a ${budgetMs.toFixed(1)} ms (${r.delivered_fps.toFixed(1)} fps entregados). La calidad no le deja subir lo suficiente, o el frame nativo es demasiado caro.`);
  }

  // How much boost, and where it spent its time.
  const top = Object.entries(r.time_on_rungs_pct).sort((a, b) => b[1] - a[1])[0];
  if (r.boost_ratio > 1.01) {
    lines.push(`Muestra ${r.boost_ratio.toFixed(2)} frames por cada frame renderizado; pasa la mayor parte del tiempo en ${rungLabel(top[0])} (${pct(top[1])}).`);
  } else {
    lines.push(`Sin generación de frames: cada frame mostrado se renderizó. Pasa la mayor parte del tiempo en ${rungLabel(top[0])} (${pct(top[1])}).`);
  }

  // Why it was held back.
  const causes = Object.entries(r.rejections_by_signal).sort((a, b) => b[1] - a[1]);
  if (causes.length) {
    const [sig, n] = causes[0];
    lines.push(`Se rechazó el ${pct(r.rejected_pct)} de los frames. La causa principal (${n} ${n === 1 ? "vez" : "veces"}) fue la ${SIGNALS[sig] ?? sig}.`);
  } else {
    lines.push("Ningún frame rechazado: la reconstrucción fue de fiar todo el tiempo.");
  }
  if (r.scene_cuts) lines.push(`Detectó ${r.scene_cuts} corte${r.scene_cuts === 1 ? "" : "s"} de escena y aplicó el cooldown de corte.`);

  // Stability.
  const seg = segments(run.timeline.rung);
  const changes = seg.length - 1;
  const settled = seg.at(-1).cols >= Math.max(5, seg.length > 1 ? 0.3 * run.timeline.rung.length : 0);
  if (changes <= 6 && settled) {
    lines.push(`Estable: ${changes} cambio${changes === 1 ? "" : "s"} de rung y termina asentado en ${RUNG_SHORT[seg.at(-1).rung]}.`);
  } else if (changes > 12) {
    lines.push(`Inestable: ${changes} cambios de rung en la línea de tiempo. Un growth interval o un cooldown más largos lo calmarían.`);
  } else {
    lines.push(`${changes} cambios de rung: sube, tropieza y vuelve a intentarlo, que es lo esperado cuando el contenido cambia.`);
  }

  if (s.quality.reject_below < 0.55) {
    lines.push("Atención: el umbral de rechazo está por debajo del valor por defecto. Las cifras mejoran, pero los frames que ahora se aceptan pueden tener ghosting o smear, y eso no aparece en ninguna métrica del simulador.");
  }
  return lines;
}
