// The free lab: the tuner and the diagnosis, against the real wasm simulator.

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import { instantiate } from "../src/wasm.js";
import { tune, REJECT_SLACK } from "../src/lab/tuner.js";
import { diagnose, segments } from "../src/lab/diagnose.js";

const WASM = new URL("../../../target/wasm32-unknown-unknown/release/frameboost_wasm.wasm", import.meta.url);
const call = await instantiate(await WebAssembly.compile(readFileSync(WASM)));
const run = async (input) => {
  const out = call({ op: "execute", name: "run_scenario", input });
  if (!out.result) throw new Error(out.tool_error);
  return out.result;
};

test("the tuner never trades rejections or the threshold for speed", async () => {
  let last = 0;
  const r = await tune(run, { scenario: "whip-turn", frames: 600 }, (d, t) => {
    assert.ok(d >= last && d <= t);
    last = d;
  });
  assert.ok(r.tried.length >= 10);
  for (const t of r.tried) {
    if (t.quality.reject_below != null) assert.ok(t.quality.reject_below >= 0.55);
  }
  if (r.best) {
    assert.ok(r.best.ok);
    assert.ok(r.best.run.result.rejected_pct <= r.baseline.result.rejected_pct + REJECT_SLACK);
    assert.ok(r.best.run.result.delivered_fps > r.baseline.result.delivered_fps);
  }
});

test("cut cooldown is only explored where there are cuts", async () => {
  const r = await tune(run, { scenario: "calm-pan", frames: 300 });
  assert.ok(r.tried.every((t) => t.quality.cut_cooldown_frames == null));
});

test("diagnosis names the cause, the budget and the rung in Spanish", async () => {
  const fence = diagnose(await run({ scenario: "fence" }));
  assert.match(fence.join(" "), /complejidad de profundidad/);
  assert.match(fence[0], /No llega al presupuesto/);

  const calm = diagnose(await run({ scenario: "calm-pan" }));
  assert.match(calm[0], /Cumple el presupuesto/);
  assert.match(calm.join(" "), /rend\.\+gen/);

  const risky = diagnose(await run({ scenario: "calm-pan", frames: 200, quality: { reject_below: 0.4 } }));
  assert.match(risky.at(-1), /ghosting/);
});

test("segments collapse a timeline into runs", () => {
  assert.deepEqual(segments("0011155"), [{ rung: 0, cols: 2 }, { rung: 1, cols: 3 }, { rung: 5, cols: 2 }]);
});
