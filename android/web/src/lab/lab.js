// The free half of the app: the simulator, driven by hand, plus a tuner and a
// plain-language reading of each run. No API key, no network.

import { SCENARIOS, KNOBS, ENV, rungLabel } from "./catalog.js";
import { diagnose } from "./diagnose.js";
import { tune } from "./tuner.js";
import { timelineChart } from "./chart.js";

const el = (tag, cls, text) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
};

const fmt = {
  fps: (v) => `${v.toFixed(1)}`,
  x: (v) => `${v.toFixed(2)}×`,
  pct: (v) => `${v.toFixed(1)}%`,
};

function knobText(quality) {
  const parts = Object.entries(quality).map(([k, v]) => `${KNOBS[k]?.label ?? k}: ${v}`);
  return parts.length ? parts.join(" · ") : "valores por defecto";
}

function tiles(r, base) {
  const row = el("div", "tiles");
  const items = [
    ["fps entregados", fmt.fps(r.delivered_fps), base && fmt.fps(base.delivered_fps)],
    ["boost", fmt.x(r.boost_ratio), base && fmt.x(base.boost_ratio)],
    ["coste vs nativo", fmt.x(r.relative_cost), base && fmt.x(base.relative_cost)],
    ["rechazados", fmt.pct(r.rejected_pct), base && fmt.pct(base.rejected_pct)],
  ];
  for (const [label, value, before] of items) {
    const t = el("div", "tile");
    t.append(el("strong", null, value), el("span", null, label));
    if (before && before !== value) t.append(el("small", null, `antes ${before}`));
    row.append(t);
  }
  return row;
}

function table(headers, rows) {
  const wrap = el("div", "table-wrap");
  const t = el("table");
  const tr = el("tr");
  for (const h of headers) tr.append(el("th", null, h));
  const head = el("thead");
  head.append(tr);
  t.append(head);
  const body = el("tbody");
  for (const r of rows) {
    const row = el("tr");
    for (const c of r) row.append(el("td", null, c));
    body.append(row);
  }
  t.append(body);
  wrap.append(t);
  return wrap;
}

function rungTable(run) {
  const rows = Object.entries(run.result.time_on_rungs_pct).map(([k, v]) => [rungLabel(k), fmt.pct(v)]);
  const d = el("details", "data");
  d.append(el("summary", null, "Ver datos"), table(["rung", "tiempo"], rows));
  return d;
}

export function mountLab(root, sim) {
  const state = {
    scenario: "whip-turn",
    quality: {},
    env: {},
  };

  const runScenario = async (input) => {
    const out = await sim.execute("run_scenario", input);
    if (!out.result) throw new Error(out.tool_error);
    return out.result;
  };

  // ---- controls ----
  const controls = el("section", "lab-controls");
  const picker = el("div", "scenario-picker");
  const about = el("p", "about");
  for (const [name, s] of Object.entries(SCENARIOS)) {
    const b = el("button", "pick", s.title);
    b.type = "button";
    b.dataset.name = name;
    b.addEventListener("click", () => select(name));
    picker.append(b);
  }
  function select(name) {
    state.scenario = name;
    for (const b of picker.children) b.classList.toggle("on", b.dataset.name === name);
    about.textContent = SCENARIOS[name].about;
  }

  const knobs = el("details", "knobs");
  const changed = el("span", "changed");
  const sum = el("summary", null, "Ajustes del governor ");
  sum.append(changed);
  knobs.append(sum);
  const sliders = {};
  function slider(key, spec, target) {
    const row = el("label", "knob");
    const head = el("div", "knob-head");
    const value = el("output");
    head.append(el("span", null, spec.label), value);
    const range = el("input");
    Object.assign(range, { type: "range", min: spec.min, max: spec.max, step: spec.step, value: spec.def });
    const sync = () => {
      const v = Number(range.value);
      value.textContent = String(v);
      if (v === spec.def) delete target[key];
      else target[key] = v;
      const n = Object.keys(state.quality).length + Object.keys(state.env).length;
      changed.textContent = n ? `(${n} cambiado${n === 1 ? "" : "s"})` : "";
    };
    range.addEventListener("input", sync);
    sync();
    row.append(head, range);
    if (spec.help) row.append(el("small", null, spec.help));
    sliders[key] = { range, sync, spec };
    return row;
  }
  knobs.append(el("h3", null, "Calidad"));
  for (const [k, spec] of Object.entries(KNOBS)) knobs.append(slider(k, spec, state.quality));
  knobs.append(el("h3", null, "Escena"));
  for (const [k, spec] of Object.entries(ENV)) knobs.append(slider(k, spec, state.env));
  const reset = el("button", "ghost small", "Restablecer");
  reset.type = "button";
  reset.addEventListener("click", () => setKnobs({}, true));
  knobs.append(reset);

  function setKnobs(quality, resetEnv) {
    for (const [k, spec] of Object.entries(KNOBS)) {
      sliders[k].range.value = quality[k] ?? spec.def;
      sliders[k].sync();
    }
    if (resetEnv) {
      for (const [k, spec] of Object.entries(ENV)) {
        sliders[k].range.value = spec.def;
        sliders[k].sync();
      }
    }
  }

  const actions = el("div", "lab-actions");
  const bRun = el("button", "primary", "Simular");
  const bTune = el("button", "primary alt", "Auto-ajustar");
  const bAll = el("button", "ghost", "Comparar los 6 escenarios");
  for (const b of [bRun, bTune, bAll]) b.type = "button";
  actions.append(bRun, bTune, bAll);

  controls.append(picker, about, knobs, actions);
  const results = el("section", "lab-results");
  root.append(controls, results);
  select(state.scenario);

  // ---- running ----
  let running = false;
  function lock(on) {
    running = on;
    for (const b of [bRun, bTune, bAll]) b.disabled = on;
  }

  function card(title, subtitle) {
    const c = el("article", "card");
    c.append(el("h3", null, title));
    if (subtitle) c.append(el("p", "sub", subtitle));
    results.prepend(c);
    c.scrollIntoView({ behavior: "smooth", block: "start" });
    return c;
  }

  function progress(c, text) {
    const p = el("div", "progress ind");
    const bar = el("div", "bar");
    const fill = el("div", "fill");
    bar.append(fill);
    const label = el("span", null, text);
    p.append(bar, label);
    c.append(p);
    return {
      set(done, total) {
        p.classList.remove("ind");
        fill.style.width = `${(done / total) * 100}%`;
        label.textContent = `${text} ${done}/${total}`;
      },
      done() {
        p.remove();
      },
    };
  }

  function input() {
    return { scenario: state.scenario, ...state.env, quality: { ...state.quality } };
  }

  bRun.addEventListener("click", async () => {
    if (running) return;
    lock(true);
    const args = input();
    const c = card(SCENARIOS[args.scenario].title, knobText(args.quality));
    const p = progress(c, "Simulando…");
    try {
      const run = await runScenario(args);
      p.done();
      c.append(tiles(run.result), timelineChart(run.timeline, run.settings.frames));
      const ul = el("ul", "diag");
      for (const line of diagnose(run)) ul.append(el("li", null, line));
      c.append(ul, rungTable(run));
    } catch (e) {
      p.done();
      c.append(el("p", "error", `Error: ${e.message}`));
    } finally {
      lock(false);
    }
  });

  bTune.addEventListener("click", async () => {
    if (running) return;
    lock(true);
    const env = { scenario: state.scenario, ...state.env };
    const c = card(`Auto-ajuste: ${SCENARIOS[env.scenario].title}`, "Busca más fps sin aumentar los rechazos y sin bajar el umbral de rechazo.");
    const p = progress(c, "Probando configuraciones");
    try {
      const r = await tune(runScenario, env, (d, t) => p.set(d, t));
      p.done();
      const base = r.baseline.result;
      if (r.best) {
        const gain = (r.best.run.result.delivered_fps / base.delivered_fps - 1) * 100;
        c.append(el("p", "verdict good", `✓ +${gain.toFixed(1)}% de fps entregados`));
        const changes = el("ul", "changes");
        for (const [k, v] of Object.entries(r.best.quality)) {
          changes.append(el("li", null, `${KNOBS[k].label}: ${KNOBS[k].def} → ${v}`));
        }
        c.append(changes);
        c.append(tiles(r.best.run.result, base), timelineChart(r.best.run.timeline, r.best.run.settings.frames));
        const apply = el("button", "primary small", "Aplicar estos ajustes");
        apply.type = "button";
        apply.addEventListener("click", () => {
          setKnobs(r.best.quality, false);
          knobs.open = true;
          knobs.scrollIntoView({ behavior: "smooth" });
        });
        c.append(apply);
      } else {
        c.append(el("p", "verdict", "Los valores por defecto ya son los mejores que encontré para esta escena: ningún cambio daba más fps sin rechazar más frames."));
        c.append(tiles(base), timelineChart(r.baseline.timeline, r.baseline.settings.frames));
      }
      c.append(el("p", "note", "El umbral de rechazo solo se sube, nunca se baja: bajarlo siempre mejora estas cifras, pero deja pasar frames con ghosting que el simulador no puede ver."));
      const rows = [["por defecto", fmt.fps(base.delivered_fps), fmt.pct(base.rejected_pct), "—"]];
      for (const t of r.tried) {
        rows.push([knobText(t.quality), fmt.fps(t.run.result.delivered_fps), fmt.pct(t.run.result.rejected_pct), t.ok ? "✓ válida" : "✗ más rechazos"]);
      }
      const d = el("details", "data");
      d.append(el("summary", null, `Ver las ${rows.length} configuraciones probadas`), table(["configuración", "fps", "rechazo", ""], rows));
      c.append(d);
    } catch (e) {
      p.done();
      c.append(el("p", "error", `Error: ${e.message}`));
    } finally {
      lock(false);
    }
  });

  bAll.addEventListener("click", async () => {
    if (running) return;
    lock(true);
    const quality = { ...state.quality };
    const c = card("Los 6 escenarios", knobText(quality));
    const p = progress(c, "Simulando");
    const names = Object.keys(SCENARIOS);
    let done = 0;
    p.set(0, names.length);
    try {
      const runs = await Promise.all(
        names.map(async (scenario) => {
          const run = await runScenario({ scenario, ...state.env, quality });
          p.set(++done, names.length);
          return run;
        }),
      );
      p.done();
      c.append(table(
        ["escenario", "fps", "boost", "coste", "rechazo"],
        runs.map((run) => [SCENARIOS[run.scenario].title, fmt.fps(run.result.delivered_fps), fmt.x(run.result.boost_ratio), fmt.x(run.result.relative_cost), fmt.pct(run.result.rejected_pct)]),
      ));
      const worst = runs.reduce((a, b) => (b.result.delivered_fps < a.result.delivered_fps ? b : a));
      c.append(el("p", "note", `La más difícil es ${SCENARIOS[worst.scenario].title.toLowerCase()} (${fmt.fps(worst.result.delivered_fps)} fps). Selecciónala arriba y pulsa Simular para ver por qué.`));
    } catch (e) {
      p.done();
      c.append(el("p", "error", `Error: ${e.message}`));
    } finally {
      lock(false);
    }
  });
}
