// Footsteps. For a while, someone walks behind you, step for step: when
// you walk, they walk; when you stop, they stop. Except that every time you
// stop, they take one more step, a little closer. Turn round and there is
// nobody. Then, right at the end, breathing on your neck.

import { system, world } from "@minecraft/server";
import { actionBar, later, soundBehind, tr, tryRun } from "../core/util.js";
import { define } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";

const LASTS = 20 * 25;
const STEP_EVERY = 6; // ticks between steps while you walk

export const walkers = new Map(); // playerId -> { start, last, moving, dist, next }

export function startSteps(player) {
  if (walkers.has(player.id)) return false;
  const now = system.currentTick;
  walkers.set(player.id, { start: now, last: { ...player.location }, moving: false, dist: 4.5, next: now });
  discover("steps");
  return true;
}

// Their own footsteps (heavy ones, placed behind you in 3D).
function stepSound() {
  return "hl.step";
}

function tickOne(player, w, now) {
  const moved = Math.hypot(player.location.x - w.last.x, player.location.z - w.last.z);
  w.last = { ...player.location };
  if (moved > 0.12) {
    w.moving = true;
    if (now >= w.next) {
      w.next = now + STEP_EVERY;
      later(3, () => soundBehind(player, stepSound(), w.dist, 0.95, 0.8));
    }
  } else if (w.moving) {
    // You stopped. They take one more.
    w.moving = false;
    w.dist = Math.max(1.2, w.dist - 0.8);
    later(10, () => soundBehind(player, stepSound(), w.dist, 0.85, 1));
    addFear(player, 4);
  }
  if (now - w.start >= LASTS) {
    walkers.delete(player.id);
    soundBehind(player, "hl.breath", 0.8, 0.8, 1);
    actionBar(player, tr("hl.steps.end"));
    addFear(player, 12);
  }
}

// Every 5 ticks.
export function tickSteps() {
  const now = system.currentTick;
  for (const [id, w] of [...walkers.entries()]) {
    const player = world.getAllPlayers().find((p) => p.id === id);
    if (!player) walkers.delete(id);
    else tryRun(() => tickOne(player, w, now));
  }
}

define({
  kind: "steps",
  weights: (ctx) => [ctx.swimming ? 0 : ctx.night || ctx.under ? 3 : 1, 0],
  minor: startSteps,
  off: () => walkers.clear(),
  test: startSteps,
});
