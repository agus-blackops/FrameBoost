// The Eyes. Deep in a cave, in the dark between the rocks, pairs of eyes
// open all around you, low down, and blink, and watch. Look straight at a
// pair and it closes and is gone; shine a light on them and they all close
// at once. When the last pair has gone, one more opens, right behind you.

import { canSee, caveSpot, distance, faceTowards, later, looksAt, rand, randInt, soundAt, tryRun } from "../core/util.js";
import { allFor, define, spawn, vanish } from "../core/creatures.js";
import { addFear, discover, jumpscaresOn } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";

const LOOK_TO_CLOSE = 12; // ticks of being looked at
const LASTS = 20 * 40;

function pairs(player) {
  return allFor(player).filter((s) => s.kind === "eyes");
}

export function openEyes(player, least = 2) {
  if (pairs(player).length || nearWard(player.dimension, player.location)) return false;
  const n = randInt(4, 6);
  const made = [];
  for (let i = 0; i < n; i++) {
    const at = caveSpot(player, 6, 14, (360 / n) * i + rand(-20, 20), 15);
    if (!at) continue;
    const e = spawn("eyes", { x: at.x, y: at.y + rand(-0.4, 0.6), z: at.z }, player, undefined, { look: 0, major: true });
    if (e) made.push(e);
  }
  // A single pair isn't the Eyes; it needs a cave wide enough to surround you.
  if (made.length < least) {
    for (const e of made) vanish(e, false);
    return false;
  }
  soundAt(player, "hl.chitter", player.location, 0.5, 0.5);
  discover("eyes");
  addFear(player, 15);
  return true;
}

// The last pair, right behind you.
function behind(player) {
  const v = player.getViewDirection();
  const at = { x: player.location.x - v.x * 1.6, y: player.location.y + 0.4, z: player.location.z - v.z * 1.6 };
  return spawn("eyes", at, player, undefined, { look: 0, last: true, major: true });
}

function close(e, s, player) {
  soundAt(player, "hl.whisper", e.location, rand(1.3, 1.6), 0.4);
  vanish(e, false);
  addFear(player, 4);
  if (!s.last && pairs(player).length === 0) later(30, () => behind(player));
}

function tick(e, s, player) {
  faceTowards(e, player.location);
  if (distance(player.location, e.location) > 32 || s.age > LASTS) {
    vanish(e, false);
    return;
  }
  if (s.last) {
    // Turn round.
    if (canSee(player, e, 8, 0.8) || looksAt(player, e, 4, 0.75)) {
      scare(player, " ", "§e§l. .");
      tryRun(() => player.runCommand("camerashake add @s 0.7 0.4 rotational"));
      addFear(player, jumpscaresOn() ? 30 : 15);
      vanish(e, false);
    } else if (s.age > 20 * 8) {
      vanish(e, false);
    }
    return;
  }
  s.look = looksAt(player, e, 20, 0.985) ? s.look + 4 : 0;
  if (s.look >= LOOK_TO_CLOSE) close(e, s, player);
}

define({
  kind: "eyes",
  type: "hl:eyes",
  weights: (ctx) => (ctx.under ? [3, 2] : [0, 0]),
  minor: (player) => openEyes(player),
  major: (player) => openEyes(player),
  tick,
  adopt: () => undefined,
  // Light: every pair closes at once.
  shine: (e, s, player) => {
    for (const p of pairs(player)) if (!p.last) vanish(p.entity, false);
    soundAt(player, "hl.chitter", e.location, 1.4, 0.6);
  },
  test: (player) => openEyes(player) || openEyes(player) || openEyes(player, 1),
});
