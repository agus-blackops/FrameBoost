// Act II: the False Tree. In the jungle and in the taiga, one of the trunks
// is not a tree. It stands among the others, thin and dark, and you can walk
// past it all day. Look at it properly, or get too close, and it straightens
// up: nearly four blocks of long black limbs with twigs growing out of them,
// and a flat white face like a child's drawing with a crown of points. It
// raises one hand and waves. Look away and it is a trunk again, somewhere
// nearer. Let it get close and it won't wave any more.

import {
  actionBar, canSee, distance, faceTowards, later, rand, soundAt, soundBehind, tr, tryRun,
} from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards } from "../core/hunt.js";
import { trunkSpot } from "../core/places.js";

const TRUNKS = /(jungle|spruce)_log$/;
const LOOK_TO_REVEAL = 40; // 2 s of a proper look
const CLOSE_TO_REVEAL = 7;
const STARE_LIMIT = 100; // 5 s of looking at the revealed thing before it hides
const LOSE_SIGHT = 16; // ticks without being looked at before it moves
const STRIKE_RANGE = 4.5;

export function plantFalseTree(player, anywhere = false) {
  if (trackedFor(player, "falsetree") || nearWard(player.dimension, player.location)) return false;
  const at = trunkSpot(player, TRUNKS, 14, 28) ?? (anywhere ? trunkSpot(player, /_log$/, 10, 28) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("falsetree", at, player, undefined, { major: true, look: 0, shown: 0, stare: 0, away: 0, hops: 0, revealed: false });
  if (e) faceTowards(e, player.location);
  return !!e;
}

// Branches in the wind, and one of them is not.
export function treeMinor(player) {
  const v = tryRun(() => player.getViewDirection()) ?? { x: 0, z: 1 };
  const a = Math.atan2(v.z, v.x) + rand(-2.5, 2.5);
  const r = rand(8, 16);
  soundAt(player, "hl.creak", { x: player.location.x + Math.cos(a) * r, y: player.location.y + rand(3, 7), z: player.location.z + Math.sin(a) * r }, rand(0.6, 0.8), 0.9);
  later(18, () => soundBehind(player, "step.grass", rand(5, 9), 0.9, 0.7));
  addFear(player, 7);
  return true;
}

function setRevealed(e, s, on) {
  s.revealed = on;
  tryRun(() => e.setProperty("hl:revealed", on));
  tryRun(() => e.setProperty("hl:wave", on));
}

function reveal(e, s, player) {
  setRevealed(e, s, true);
  s.shown = 0;
  s.stare = 0;
  faceTowards(e, player.location);
  soundAt(player, "hl.creak", e.location, 0.55, 1);
  later(10, () => soundAt(player, "hl.moan", e.location, 0.5, 0.8));
  actionBar(player, tr("hl.falsetree.seen"));
  discover("falsetree");
  addFear(player, 20);
}

// Back to being a trunk, somewhere nearer (or, with none to be found, just
// standing where it is: a trunk again).
function hide(e, s, player, nearer = 0.65) {
  const d = distance(player.location, e.location);
  const want = Math.max(6, d * nearer);
  const at = trunkSpot(player, TRUNKS, Math.max(5, want - 3), want + 3, 200)
    ?? trunkSpot(player, TRUNKS, 5, Math.max(6, d - 1), 200)
    ?? trunkSpot(player, /_log$/, 5, Math.max(6, d - 1), 200);
  setRevealed(e, s, false);
  s.look = 0;
  s.away = 0;
  s.hops++;
  if (at && tryRun(() => (e.teleport(at), true))) {
    faceTowards(e, player.location);
    soundAt(player, "hl.creak", at, rand(0.8, 1.0), 0.6);
    return true;
  }
  return false;
}

function strike(e, s, player) {
  // Right behind you, hand down at last.
  const v = player.getViewDirection();
  const to = { x: player.location.x - v.x * 1.4, y: player.location.y, z: player.location.z - v.z * 1.4 };
  tryRun(() => e.teleport(to));
  setRevealed(e, s, true);
  tryRun(() => e.setProperty("hl:wave", false));
  faceTowards(e, player.location);
  soundAt(player, "hl.creak", to, 0.5, 1);
  soundAt(player, "hl.screech", to, 0.8, 1);
  scare(player, " ", tr("hl.falsetree.sub"));
  tryRun(() => player.addEffect("darkness", 100, { showParticles: false }));
  tryRun(() => player.addEffect("slowness", 60, { amplifier: 1, showParticles: false }));
  discover("falsetree");
  addFear(player, 35);
  s.struck = true;
  later(16, () => vanish(e, false));
}

function tick(e, s, player) {
  if (s.struck) return;
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || s.age > 20 * 120 || d > 60) {
    vanish(e, false);
    return;
  }
  const seen = canSee(player, e, 40, 0.93);
  if (!s.revealed) {
    s.look = seen ? s.look + 4 : Math.max(0, s.look - 2);
    if ((s.look >= LOOK_TO_REVEAL && d <= 28) || d <= CLOSE_TO_REVEAL) reveal(e, s, player);
    return;
  }
  // Revealed.
  s.shown += 4;
  faceTowards(e, player.location);
  if (d <= STRIKE_RANGE) {
    strike(e, s, player);
    return;
  }
  if (seen) {
    s.away = 0;
    s.stare += 4;
    if (s.stare >= STARE_LIMIT) hide(e, s, player, 0.85); // it loses the staring contest, and steps away
  } else if ((s.away += 4) >= LOSE_SIGHT) {
    // You looked away: it is a trunk again, and it is closer.
    if (s.hops >= 3 && d < 12) strike(e, s, player);
    else hide(e, s, player, 0.6);
  }
}

define({
  kind: "falsetree",
  type: "hl:false_tree",
  voice: { id: "hl.creak", every: [7, 11], volume: 0.8, pitch: [0.5, 0.7] },
  weights: (ctx) => {
    const woods = ctx.outside && (ctx.jungle || ctx.taiga);
    return [woods ? 2 : 0, woods ? 3 : 0];
  },
  minor: treeMinor,
  major: (player) => plantFalseTree(player),
  tick,
  adopt: () => undefined,
  onHit: (e, s, player) => {
    soundAt(player, "hl.creak", e.location, 0.4, 1);
    vanish(e, false);
  },
  shine: (e, s, player) => {
    if (!s.revealed) return;
    soundAt(player, "hl.creak", e.location, 0.4, 1);
    vanish(e, false);
  },
  test: (player) => plantFalseTree(player, true) || treeMinor(player),
});
