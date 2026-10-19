// Act II: the Stalker (The Parallel, Twixxel's Stalker). Four blocks of
// pitch-black bones, arms to the ground, and a pale mask of a face with
// hollow eyes and a smile that never changes. It can't be hurt, and it NEVER
// moves while you are looking at it. Only when you turn away.
//
// It comes in three ways:
//   far      standing at the edge of the world, watching. Look away and it
//            is closer. And closer. And then it is right behind you.
//   behind   right behind you. Turn round, and it's gone. Don't, and it
//            touches you.
//   chase    down on all fours, smashing through doors and windows, fast,
//            until you look at it, when it freezes mid-step. Look away...

import {
  actionBar, canSee, distance, faceTowards, later, looksAt, pick, rand, soundAt, soundBehind, title, tr, tryRun,
  visibleSpot,
} from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover, getFear } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";

const SEEN_TO_HOP = 20; // it has to be seen for a second before it can move again
const UNSEEN_TO_HOP = 16;
const BEHIND_FOR = 20 * 6;
const CHASE_FOR = 20 * 25;

function behindAt(player, dist) {
  const v = player.getViewDirection();
  const len = Math.hypot(v.x, v.z) || 1;
  return { x: player.location.x - (v.x / len) * dist, y: player.location.y, z: player.location.z - (v.z / len) * dist };
}

export function stalk(player, how, anywhere = false) {
  if (trackedFor(player, "stalker") || nearWard(player.dimension, player.location)) return false;
  how ??= pick([["far", 4], ["behind", 2], ["chase", getFear(player) >= 40 || anywhere ? 2 : 0]]);
  let at;
  if (how === "far") at = visibleSpot(player, 36, 56, rand(-50, 50), 20) ?? (anywhere ? visibleSpot(player, 16, 30, 0, 90) : undefined);
  else if (how === "behind") at = behindAt(player, 2.5);
  else at = visibleSpot(player, 20, 28, rand(-40, 40), 30) ?? visibleSpot(player, 12, 20, 180, 90);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("stalker", at, player, "watch", { major: true, how, look: 0, unseen: 0, clock: 0, hops: 0 });
  if (!e) return false;
  faceTowards(e, player.location);
  if (how === "behind") soundBehind(player, "hl.breath", 1.5, 0.6, 1);
  if (how === "chase") {
    title(player, " ", tr("hl.stalker.chase"), 30);
    soundAt(player, "hl.static", e.location, 0.6, 1);
    later(10, () => soundAt(player, "hl.screech", e.location, 0.55, 1));
  }
  addFear(player, how === "far" ? 10 : 20);
  return true;
}

// Nothing to see. A burst of static, something big stepping in the dark.
export function stalkerMinor(player) {
  if (Math.random() < 0.5) soundBehind(player, "hl.static", rand(10, 20), rand(0.5, 0.7), 0.6);
  else for (let i = 0; i < 3; i++) later(i * 14, () => soundBehind(player, "hl.step", rand(14, 20), 0.55, 1));
  addFear(player, 6);
  return true;
}

function gone(e, player, loud = true) {
  if (loud) soundAt(player, "hl.static", e.location, 0.8, 0.8);
  vanish(e, false);
}

function seenNow(player, e, range = 96) {
  return canSee(player, e, range, 0.8) || looksAt(player, e, 12, 0.85);
}

function far(e, s, player, d) {
  if (seenNow(player, e)) {
    s.look += 4;
    s.unseen = 0;
    if (s.look >= SEEN_TO_HOP) {
      discover("stalker");
      if (s.look === SEEN_TO_HOP) actionBar(player, tr("hl.stalker.seen"));
    }
    return;
  }
  s.unseen += 4;
  if (s.look < SEEN_TO_HOP || s.unseen < UNSEEN_TO_HOP) return;
  // You looked away. It is closer.
  s.look = 0;
  s.unseen = 0;
  s.hops++;
  if (d < 10 || s.hops > 4) {
    s.how = "behind";
    s.clock = 0;
    tryRun(() => e.teleport(behindAt(player, 2.5)));
    faceTowards(e, player.location);
    soundBehind(player, "hl.breath", 1.5, 0.6, 1);
    return;
  }
  if (stepCloser(e, player, 0.45, 6)) {
    soundAt(player, "hl.static", e.location, rand(0.5, 0.7), 0.5);
    addFear(player, 8);
  }
}

function behind(e, s, player) {
  if (seenNow(player, e, 8)) {
    // You turned round.
    soundAt(player, "hl.screech", e.location, 0.7, 1);
    tryRun(() => player.runCommand("camerashake add @s 0.8 0.4 rotational"));
    scare(player, " ", tr("hl.stalker.sub"));
    discover("stalker");
    addFear(player, 30);
    s.done = true;
    later(6, () => gone(e, player, false));
    return;
  }
  // It stays behind you, wherever you turn.
  tryRun(() => e.teleport(behindAt(player, 2.5)));
  faceTowards(e, player.location);
  if (s.clock >= BEHIND_FOR) {
    // You never turned round. It touches you.
    blackout(player, 0.5);
    tryRun(() => player.applyDamage(6));
    tryRun(() => player.addEffect("darkness", 100, { showParticles: false }));
    soundBehind(player, "hl.whisper", 1, 0.5, 1);
    actionBar(player, tr("hl.stalker.touch"));
    discover("stalker");
    addFear(player, 30);
    s.done = true;
    gone(e, player, false);
  }
}

function chase(e, s, player) {
  // It never moves while you are looking at it.
  const seen = seenNow(player, e);
  if (seen && s.mode !== "watch") {
    setMode(s, "watch");
    discover("stalker");
  } else if (!seen && s.mode !== "chase") {
    setMode(s, "chase");
    soundAt(player, "hl.static", e.location, rand(0.5, 0.7), 0.7);
  }
  if (s.clock >= CHASE_FOR) gone(e, player);
}

function tick(e, s, player) {
  if (s.done) return;
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || d > 110 || (!s.adopted && s.age > 20 * 150)) {
    gone(e, player, false);
    return;
  }
  s.clock = (s.clock ?? 0) + 4;
  s.look ??= 0;
  s.unseen ??= 0;
  s.hops ??= 0;
  if (s.how === "behind") behind(e, s, player);
  else if (s.how === "chase") chase(e, s, player);
  else {
    if (s.mode !== "watch") setMode(s, "watch");
    faceTowards(e, player.location);
    far(e, s, player, d);
  }
}

define({
  kind: "stalker",
  type: "hl:stalker",
  modes: ["watch", "chase"],
  stride: { id: "hl.step", every: 1.6, volume: 0.9, pitch: [0.5, 0.6] },
  weights: (ctx) => (ctx.under ? [0, 0] : ctx.night ? [2, ctx.outside ? 3 : 2] : [1, 1]),
  minor: stalkerMinor,
  major: (player) => stalk(player),
  tick,
  adopt: () => "watch",
  onHit: (e, s, player) => {
    // It can't be hurt. It only looks at you.
    soundAt(player, "hl.static", e.location, 0.5, 1);
    tryRun(() => player.addEffect("darkness", 60, { showParticles: false }));
  },
  onStrike: (e, s, player) => tryRun(() => player.addEffect("darkness", 80, { showParticles: false })),
  test: (player) => stalk(player, "far", true) || stalk(player, "behind", true) || stalkerMinor(player),
});
