// Act II: the Faceless Enderman. Taller than the ones in the game, with no
// face at all, only a hairline of violet where the eyes should be. It
// doesn't walk: it blinks. You hear it, the warble of it vanishing at one
// place and the warble of it arriving at another, in 3D, so you know which
// way it went and which way to turn. Every blink it is a little nearer.
// Look straight at it and the seam opens, the arms rise, and it comes for
// you. A light sends it away.

import {
  actionBar, canSee, caveSpot, distance, faceTowards, groundAt, later, offsetFromView, rand, soundAt, tr, tryRun,
  visibleSpot,
} from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";

const STARE_BEFORE = 36; // ticks of being looked at before it comes
const BLINK_EVERY = [44, 80];

export function faceless(player, underground = false) {
  if (trackedFor(player, "faceless") || nearWard(player.dimension, player.location)) return false;
  const at = underground ? caveSpot(player, 14, 22, rand(-170, 170), 20) : visibleSpot(player, 16, 24, rand(-70, 70), 10);
  if (!at) return false;
  const e = spawn("faceless", at, player, undefined, { major: true, look: 0, blinkIn: 40, hunting: false, blinks: 0 });
  if (e) soundAt(player, "hl.warp", e.location, 1, 1);
  return !!e;
}

// A blink: gone here, there, a little nearer.
function blink(e, s, player, nearer = 4) {
  const from = { ...e.location };
  const d = distance(player.location, from);
  const r = Math.max(2.5, d - nearer);
  for (let attempt = 0; attempt < 6; attempt++) {
    const p = offsetFromView(player, r, rand(-110, 110) + (attempt > 3 ? 180 : 0));
    const y = groundAt(player.dimension, p.x, p.z, player.location.y);
    if (y === undefined) continue;
    soundAt(player, "hl.warp", from, 1.2, 1); // leaving
    const to = { x: p.x, y, z: p.z };
    if (!tryRun(() => (e.teleport(to), true))) continue;
    later(2, () => soundAt(player, "hl.warp", to, 0.8, 1)); // arriving
    faceTowards(e, player.location);
    s.blinks++;
    return true;
  }
  return false;
}

function attack(e, s, player) {
  s.hunting = true;
  // Right in front of you, seam wide open.
  const v = player.getViewDirection();
  const to = { x: player.location.x + v.x * 1.5, y: player.location.y, z: player.location.z + v.z * 1.5 };
  tryRun(() => e.teleport(to));
  faceTowards(e, player.location);
  soundAt(player, "hl.warp", to, 1.4, 1);
  scare(player, " ", tr("hl.faceless.sub"));
  soundAt(player, "hl.shriek", to, 0.7, 1);
  tryRun(() => player.addEffect("darkness", 100, { showParticles: false }));
  tryRun(() => player.addEffect("slowness", 60, { amplifier: 1, showParticles: false }));
  discover("faceless");
  addFear(player, 35);
  later(14, () => vanish(e, false));
}

function tick(e, s, player) {
  if (s.hunting) return;
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || s.age > 20 * 90 || d > 60) {
    vanish(e, false);
    return;
  }
  faceTowards(e, player.location);
  const seen = canSee(player, e, 48, 0.92);
  if (seen) {
    s.look += 4;
    if (s.look === 4) {
      tryRun(() => e.setProperty("hl:stare", true));
      discover("faceless");
      actionBar(player, tr("hl.faceless.seen"));
      addFear(player, 8);
    }
    if (s.look >= STARE_BEFORE) attack(e, s, player);
    return;
  }
  if (s.look > 0) {
    s.look = 0;
    tryRun(() => e.setProperty("hl:stare", false));
  }
  // Out of sight: blink, nearer each time. Close enough, behind you: it's over.
  if ((s.blinkIn -= 4) <= 0) {
    s.blinkIn = rand(BLINK_EVERY[0], BLINK_EVERY[1]);
    if (d <= 5 && !seen) attack(e, s, player);
    else blink(e, s, player, d > 14 ? 6 : 3);
  }
}

define({
  kind: "faceless",
  type: "hl:faceless_enderman",
  weights: (ctx) => (ctx.night || ctx.under ? [0, ctx.outside ? 3 : 2] : [0, 0]),
  major: (player, ctx) => faceless(player, !!ctx?.under),
  tick,
  adopt: () => undefined,
  onHit: (e, s, player) => {
    soundAt(player, "hl.warp", e.location, 1.3, 1);
    vanish(e, false);
  },
  shine: (e, s, player) => {
    soundAt(player, "hl.warp", e.location, 1.5, 1);
    vanish(e, false);
  },
  test: (player) => faceless(player, false) || faceless(player, true),
});
