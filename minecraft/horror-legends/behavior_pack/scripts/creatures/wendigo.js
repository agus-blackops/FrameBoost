// Act III: the Wendigo. In the snow and in the deep woods at night: a long
// whistle in the trees, branches snapping, clicking like teeth. It hunts by
// sound. Run, and it hears you: it comes, shrieking, faster than you, and
// its claws leave you starving. Crouch and keep still and it loses you; it
// stalks closer between the trees while you aren't looking, but it can't
// find what it can't hear. Fire and light make it flinch.

import {
  actionBar, canSee, distance, later, rand, soundAt, soundBehind, tr, tryRun, visibleSpot,
} from "../core/util.js";
import { define, setMode, spawn, stateOf, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";

export function wendigoMinor(player) {
  const r = Math.random();
  if (r < 0.4) soundBehind(player, "hl.whistle", rand(20, 34), rand(0.85, 1.05), 1);
  else if (r < 0.7) for (let i = 0; i < 3; i++) later(i * 14, () => soundBehind(player, "dig.wood", rand(12, 20), 0.6, 1));
  else soundBehind(player, "hl.whisper", rand(10, 16), 0.6, 0.8);
  addFear(player, 9);
  return true;
}

export function spawnWendigo(player) {
  if (trackedFor(player, "wendigo")) return false;
  const at = visibleSpot(player, 24, 34, rand(-50, 50), 5);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("wendigo", at, player, "stalk", { major: true, noise: 0 });
  if (e) soundAt(player, "hl.whistle", e.location, 0.9, 1);
  return !!e;
}

export function charge(e, player) {
  const s = stateOf(e);
  if (!s || s.mode === "charge") return;
  setMode(s, "charge");
  s.hunting = true;
  soundAt(player, "hl.shriek", e.location, rand(0.9, 1.05), 1);
  scare(player, " ", "§b§l! ! !");
  actionBar(player, tr("hl.wendigo.charge"));
  discover("wendigo");
  addFear(player, 35);
}

function backOff(e, player) {
  const s = stateOf(e);
  if (!s || s.mode === "flee") return;
  setMode(s, "flee");
  s.hunting = false;
  soundAt(player, "hl.shriek", e.location, 1.3, 0.7);
}

// How much noise you are making right now.
function noise(player) {
  if (tryRun(() => player.isSneaking)) return -6;
  if (tryRun(() => player.isSprinting)) return 8;
  const v = tryRun(() => player.getVelocity()) ?? { x: 0, z: 0 };
  return Math.hypot(v.x, v.z) > 0.08 ? 2 : -3;
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  s.timer += 4;
  if (s.mode === "flee") {
    if (s.timer > 100 || d > 48) vanish(e, false);
    return;
  }
  if (s.mode === "charge") {
    if (s.timer % 50 === 0) soundAt(player, "hl.shriek", e.location, rand(0.8, 1.1), 0.8);
    if (s.timer > 20 * 20 || nearWard(player.dimension, player.location, 4)) backOff(e, player);
    return;
  }
  if (s.adopted) return;
  if (nearWard(e.dimension, e.location) || s.age > 20 * 100 || d > 64) {
    vanish(e);
    return;
  }
  // It listens.
  s.noise = Math.max(0, (s.noise ?? 0) + noise(player) * (d < 20 ? 1.5 : 1));
  if (s.noise >= 60) {
    charge(e, player);
    return;
  }
  if (s.timer % 30 === 0 && Math.random() < 0.4) soundAt(player, "hl.chitter", e.location, 0.5, 0.6); // teeth
  const seen = canSee(player, e, 64, 0.92);
  if (seen) {
    discover("wendigo");
    s.unseen = 0;
  } else if (++s.unseen >= 30) {
    s.unseen = 0;
    if (stepCloser(e, player, 0.7, 10)) soundAt(player, "dig.wood", e.location, 0.6, 0.8);
  }
}

define({
  kind: "wendigo",
  type: "hl:wendigo",
  modes: ["stalk", "charge", "flee"],
  weights: (ctx) => {
    if (!ctx.outside) return [0, 0];
    const cold = ctx.snow >= 2;
    return [cold ? 4 : ctx.forest && ctx.night ? 2 : 0, cold ? 4 : ctx.forest && ctx.night ? 2 : 0];
  },
  minor: wendigoMinor,
  major: spawnWendigo,
  tick,
  adopt: () => "stalk",
  onHit: (e, s, player) => s.mode === "stalk" && charge(e, player),
  onHurt: (e, s, player) => {
    const h = tryRun(() => e.getComponent("minecraft:health"));
    if (h && h.currentValue < h.effectiveMax * 0.3) backOff(e, player);
  },
  // Its claws leave you starving.
  onStrike: (e, s, player) => tryRun(() => player.addEffect("hunger", 200, { amplifier: 2, showParticles: true })),
  shine: (e) => tryRun(() => e.addEffect("slowness", 40, { amplifier: 2, showParticles: false })),
  test: (player) => spawnWendigo(player) || wendigoMinor(player),
});
