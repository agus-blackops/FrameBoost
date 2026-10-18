// Act I: the Mimic. A chest that wasn't there before: in a cave, at the
// edge of your camp, in the corner of a room. Open it, hit it or walk up to
// it and the lid is a jaw full of teeth, six legs unfold and it comes for
// you. It can be killed, and what it swallowed falls out (iron, gold,
// emeralds, now and then a diamond). Left alone too long, it wanders off.

import {
  actionBar, caveSpot, distance, groundSpot, later, rand, sound, soundAt, tr, tryRun,
} from "../core/util.js";
import { define, setMode, spawn, stateOf, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";

export function placeMimic(player, ctx = {}) {
  if (trackedFor(player, "mimic")) return false;
  const at = ctx.under
    ? caveSpot(player, 8, 16, rand(-60, 60), 30)
    : groundSpot(player, 7, 14, (Math.random() < 0.5 ? -1 : 1) * rand(25, 60), 10);
  if (!at || nearWard(player.dimension, at)) return false;
  const e = spawn("mimic", at, player, "asleep");
  if (e) tryRun(() => e.setRotation({ x: 0, y: Math.round(rand(0, 4)) * 90 })); // like a placed chest
  return !!e;
}

export function wake(e, player) {
  const s = stateOf(e);
  if (!s || s.mode === "awake") return;
  setMode(s, "awake");
  tryRun(() => e.setProperty("hl:awake", true));
  s.hunting = true;
  s.major = true;
  soundAt(player, "hl.chomp", e.location, 1, 1);
  later(6, () => soundAt(player, "hl.chomp", e.location, 0.8, 1));
  if (distance(player.location, e.location) < 3) tryRun(() => player.applyDamage(4));
  scare(player, " ", "§6§l!");
  actionBar(player, tr("hl.mimic.wake"));
  discover("mimic");
  addFear(player, 25);
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  if (s.mode !== "awake") {
    // Asleep: a chest. It wakes if you get too close.
    if (d < 2.4) wake(e, player);
    else if (s.age > 20 * 300 || d > 80) vanish(e, false);
    else if (s.age % 200 === 0 && d < 16 && Math.random() < 0.3) soundAt(player, "random.chestclosed", e.location, 0.6, 0.4);
    return;
  }
  s.timer += 4;
  if (s.timer % 40 === 0 && d < 10) soundAt(player, "hl.chomp", e.location, rand(0.9, 1.2), 0.8);
  // It gives up after a while and burrows away.
  if (s.timer > 20 * 30 || nearWard(e.dimension, e.location, 8)) {
    soundAt(player, "dig.gravel", e.location, 0.6, 1);
    vanish(e);
  }
}

define({
  kind: "mimic",
  type: "hl:mimic",
  modes: ["asleep", "awake"],
  weights: (ctx) => [ctx.under || ctx.indoors ? 2 : 1, ctx.under ? 1 : 0],
  minor: placeMimic,
  major: placeMimic,
  tick,
  adopt: () => "asleep",
  onHit: (e, s, player) => wake(e, player),
  onTrigger: (e, s, player, id) => id === "hl:awake" && wake(e, player),
  shine: () => {},
  test: (player) => placeMimic(player, {}) || (sound(player, "random.chestopen", 1, 0.5), true),
});
