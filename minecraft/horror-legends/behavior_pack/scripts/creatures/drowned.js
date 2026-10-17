// Act II: the Drowned One. Near water you hear it first: something
// gurgling under the surface, wet footsteps on the bank. At night it stands
// in the shallows staring at you through its hair, and when you have looked
// at it long enough it walks out. In the water it doesn't wait: it takes
// hold of you from below and pulls you down. Hit it and it lets go; get out
// of the water and it sinks back.

import {
  actionBar, canSee, distance, inWater, later, rand, soundAt, soundBehind, tr, tryRun, waterSpot,
} from "../core/util.js";
import { define, setMode, spawn, stateOf, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";

export function drownedMinor(player) {
  const at = waterSpot(player, 4, 14);
  if (at) {
    soundAt(player, "hl.gurgle", at, rand(0.8, 1.0), 1);
    for (let i = 0; i < 6; i++) {
      tryRun(() => player.dimension.spawnParticle("minecraft:bubble_column_up_particle", { x: at.x, y: at.y + 0.6, z: at.z }));
    }
  } else soundBehind(player, "hl.gurgle", rand(4, 8), 0.8, 0.8);
  for (let i = 0; i < 4; i++) later(10 + i * 8, () => soundBehind(player, "step.mud", 5 - i, 0.8, 0.7));
  addFear(player, 7);
  return true;
}

// From below if you are swimming; in the shallows, watching, if not.
export function spawnDrowned(player) {
  if (trackedFor(player, "drowned") || nearWard(player.dimension, player.location)) return false;
  if (inWater(player)) {
    const at = { x: player.location.x + rand(-1.5, 1.5), y: player.location.y - 2, z: player.location.z + rand(-1.5, 1.5) };
    const e = spawn("drowned", at, player, "grab", { major: true, hunting: true, close: true });
    if (e) grab(e, player);
    return !!e;
  }
  const at = waterSpot(player, 10, 20);
  return !!(at && spawn("drowned", at, player, "lurk", { major: true }));
}

export function grab(e, player) {
  const s = stateOf(e);
  if (!s) return;
  if (s.mode !== "grab") setMode(s, "grab");
  s.hunting = true;
  s.holding = true;
  soundAt(player, "hl.gurgle", e.location, 0.7, 1);
  scare(player, " ", "§3§l. . .");
  actionBar(player, tr("hl.drowned.grab"));
  discover("drowned");
  addFear(player, 30);
}

function letGo(e, player) {
  const s = stateOf(e);
  if (!s || !s.holding) return;
  s.holding = false;
  soundAt(player, "hl.gurgle", e.location, 1.2, 0.8);
  later(40, () => vanish(e, false));
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  s.timer += 4;
  if (s.mode === "grab") {
    if (s.holding && inWater(player) && d < 5) {
      // Down.
      tryRun(() => player.applyKnockback(0, 0, 0, -0.35));
      tryRun(() => player.addEffect("slowness", 30, { amplifier: 2, showParticles: false }));
      if (s.timer % 40 === 0) soundAt(player, "hl.gurgle", e.location, rand(0.7, 0.9), 1);
    } else if (s.holding && !inWater(player)) letGo(e, player);
    if (s.timer > 20 * 20) letGo(e, player);
    return;
  }
  if (s.adopted) return;
  // Lurking in the shallows.
  if (canSee(player, e, 48, 0.92)) {
    discover("drowned");
    if (++s.seen >= 15) {
      setMode(s, "grab");
      s.hunting = true;
      soundAt(player, "hl.gurgle", e.location, 0.6, 1);
      addFear(player, 15);
    }
  }
  if (s.timer > 20 * 60 || d > 48) vanish(e, false);
}

define({
  kind: "drowned",
  type: "hl:drowned_one",
  modes: ["lurk", "grab"],
  weights: (ctx) => (ctx.swimming ? [3, 5] : ctx.water >= 2 ? [3, ctx.night ? 3 : 1] : [0, 0]),
  minor: drownedMinor,
  major: spawnDrowned,
  tick,
  adopt: () => "lurk",
  onHurt: (e, s, player) => letGo(e, player),
  shine: (e, s) => s.mode === "lurk" && vanish(e),
  test: (player) => spawnDrowned(player) || drownedMinor(player),
});
