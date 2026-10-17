// Act I: the Weeping Statue. A stone figure, its face in its hands. It
// never moves while anyone is looking at it. Look away and you hear stone
// grinding, and it is closer, and its hands are lower. And you have to
// blink: every few seconds the world goes dark for an instant, and in that
// instant it moves. If it reaches you while you aren't looking, it touches
// you. Keep it in sight until it gives up, or stand in a Ward Lantern's light.

import {
  canSee, distance, faceTowards, groundSpot, looksAt, rand, randInt, sound, soundAt, tryRun, visibleSpot,
} from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { stepCloser } from "../core/hunt.js";

export function placeStatue(player, near = false) {
  if (trackedFor(player, "statue") || nearWard(player.dimension, player.location)) return false;
  const at = near ? visibleSpot(player, 14, 20, rand(-40, 40), 5) : visibleSpot(player, 24, 34, rand(-50, 50), 5);
  return !!(at && spawn("statue", at, player, "stalk", { major: near, close: near, blink: 0 }));
}

function pose(e, s, d) {
  const p = d > 12 ? 0 : d > 5 ? 1 : 2;
  if (s.pose === p) return;
  s.pose = p;
  tryRun(() => e.setProperty("hl:pose", p));
}

// One step towards you, while nobody is looking.
function creep(e, s, player, k) {
  if (!stepCloser(e, player, k, 1.4)) return false;
  soundAt(player, "hl.stone", e.location, rand(0.8, 1.0), 0.9);
  pose(e, s, distance(player.location, e.location));
  return true;
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || s.age > 20 * 120 || d > 64) {
    vanish(e, false);
    return;
  }
  // Watched: stone. (Any direct look counts, even a glance.)
  const seen = canSee(player, e, 64, 0.82) || (d < 6 && looksAt(player, e, 8, 0.7));
  if (seen) {
    if (!s.seen) discover("statue");
    s.seen++;
    s.unseen = 0;
  } else {
    s.unseen++;
  }
  if (s.adopted) return;
  pose(e, s, d);

  // Close enough, and you weren't looking.
  if (!seen && d < 1.9) {
    tryRun(() => player.applyDamage(6));
    tryRun(() => player.addEffect("blindness", 60, { showParticles: false }));
    scare(player, " ", "§7§l. . .");
    addFear(player, 30);
    vanish(e, false);
    return;
  }
  // Unwatched for a moment: it moves.
  if (!seen && s.unseen >= 5) {
    s.unseen = 0;
    creep(e, s, player, d > 12 ? 0.65 : 0.5);
    faceTowards(e, player.location);
    return;
  }
  // You have to blink sometime.
  if (seen && d < 26 && (s.blink += 4) >= randInt(160, 240)) {
    s.blink = 0;
    blackout(player, 0.25);
    creep(e, s, player, 0.6);
    addFear(player, 6);
  }
}

export function statueMinor(player) {
  // A statue at the edge of sight that is gone the next time you look.
  return placeStatue(player, false);
}

define({
  kind: "statue",
  type: "hl:weeping_statue",
  weights: (ctx) => (ctx.under ? [1, 1] : [2, ctx.night ? 3 : 1]),
  minor: statueMinor,
  major: (player) => placeStatue(player, true),
  tick,
  adopt: () => "stalk",
  onHit: (e, s, player) => sound(player, "hl.stone", 1, 0.6),
  test: (player) => placeStatue(player, true) || (soundAt(player, "hl.stone", groundSpot(player, 4, 6, 180, 30) ?? player.location), true),
});
