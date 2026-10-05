// Searches the quality governor's knobs for a scenario, using nothing but the
// simulator. The objective is frames delivered to the display, under one hard
// constraint: no more rejected frames than the default configuration.
//
// The rejection threshold is only ever raised. Lowering it always looks better
// in these numbers, and what it costs — ghosting on frames that are now let
// through — does not show up in any of them.

/** Allowed slack on the rejection rate, in percentage points. */
export const REJECT_SLACK = 0.1;
/** A change must beat the baseline by this factor to count. */
const MIN_GAIN = 1.005;

export function variations(baseline) {
  const v = [
    ...[60, 90, 180, 240].map((n) => ({ growth_interval: n })),
    ...[30, 90, 120].map((n) => ({ cooldown_frames: n })),
    ...[0.6, 0.65].map((x) => ({ reject_below: x })),
    { backoff_rungs: 2 },
  ];
  // Cut cooldown only matters where there are cuts.
  if (baseline.result.scene_cuts > 0) v.push({ cut_cooldown_frames: 150 }, { cut_cooldown_frames: 450 });
  return v;
}

const fps = (run) => run.result.delivered_fps;
const feasible = (run, baseline) => run.result.rejected_pct <= baseline.result.rejected_pct + REJECT_SLACK;

/**
 * @param {(input: object) => Promise<object>} runScenario resolves to a run_scenario result
 * @param {{scenario: string, native_ms?: number, target_fps?: number}} env
 * @param {(done: number, total: number) => void} [progress]
 */
export async function tune(runScenario, env, progress = () => {}) {
  const baseline = await runScenario({ ...env });
  const vs = variations(baseline);
  const total = vs.length + 2;
  let done = 1;
  progress(done, total);

  const tried = await Promise.all(
    vs.map(async (quality) => {
      const run = await runScenario({ ...env, quality });
      progress(++done, total);
      return { quality, run, ok: feasible(run, baseline) };
    }),
  );

  // Best feasible winner per knob.
  const winners = {};
  for (const t of tried) {
    const [knob] = Object.keys(t.quality);
    if (!t.ok || fps(t.run) < fps(baseline) * MIN_GAIN) continue;
    if (!winners[knob] || fps(t.run) > fps(winners[knob].run)) winners[knob] = t;
  }

  let best = null;
  for (const t of Object.values(winners)) if (!best || fps(t.run) > fps(best.run)) best = t;

  // The winners together, if there is more than one: knobs interact.
  if (Object.keys(winners).length > 1) {
    const quality = Object.assign({}, ...Object.values(winners).map((w) => w.quality));
    const run = await runScenario({ ...env, quality });
    const combined = { quality, run, ok: feasible(run, baseline) };
    tried.push(combined);
    if (combined.ok && fps(run) > fps(best.run)) best = combined;
  }
  progress(total, total);

  return { baseline, best, tried };
}
