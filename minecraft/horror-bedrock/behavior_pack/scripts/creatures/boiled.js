// Act II: the Boiled One (Doctor Nowhere). First, the broadcast: a numbers
// station on a frequency nobody uses, a flat voice reading digits, and
// between them, something else. Then, one evening, it is standing far out in
// the open: a crimson, withered figure five blocks tall with a misshapen
// head and two red points of light for eyes. Don't look at it. Those who
// look too long suffer the Phenomenon: the world goes red, you can't stand
// straight, and it hurts.
//
//   still    far off in the open; closer, rarely, when you look away
//   sleep    it watches you sleep, from the foot of the bed
//   minor    the numbers station

import {
  actionBar, canSee, distance, faceTowards, later, rand, randInt, sound, soundAt, tr, tryRun, visibleSpot,
} from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";
import { bedside } from "../events/sleep.js";

const STARE = 60; // three seconds and the Phenomenon starts
const PHENOMENON = 140; // seven and it has you

function red(player, hold = 0.4, strength = 0.5) {
  tryRun(() =>
    player.camera.fade({
      fadeColor: { red: strength, green: 0, blue: 0 },
      fadeTime: { fadeInTime: 0.2, holdTime: hold, fadeOutTime: 0.6 },
    })
  );
}

// A flat voice reading digits on a dead frequency.
export function numbers(player) {
  tryRun(() =>
    player.onScreenDisplay.setTitle(" ", { subtitle: tr("hb.bo.signal"), fadeInDuration: 10, stayDuration: 50, fadeOutDuration: 10 })
  );
  sound(player, "hb.static", 0.4, 0.7);
  later(20, () => sound(player, "hb.numbers", 0.9, 1));
  for (let i = 0; i < 5; i++) {
    later(30 + i * 30, () => {
      const digits = Array.from({ length: 5 }, () => randInt(0, 9)).join(" ");
      actionBar(player, { rawtext: [{ text: `§4${digits}` }] });
    });
  }
  later(190, () => actionBar(player, tr(`hb.bo.line.${randInt(1, 4)}`)));
  addFear(player, 8);
  return true;
}

export function standing(player, anywhere = false) {
  if (trackedFor(player, "boiled") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 40, 60, rand(-45, 45), 20) ?? (anywhere ? visibleSpot(player, 18, 30, 0, 120) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("boiled", at, player, "still", { major: true, look: 0, unseen: 0, clock: 0, hops: 0 });
  if (!e) return false;
  faceTowards(e, player.location);
  later(10, () => sound(player, "hb.drone", 0.6, 0.6));
  addFear(player, 15);
  return true;
}

// The Phenomenon.
function phenomenon(e, s, player) {
  if (s.look === STARE) {
    tryRun(() => e.setProperty("hb:stare", true));
    actionBar(player, tr("hb.bo.stare"));
    sound(player, "hb.numbers", 0.7, 0.6);
    discover("boiled");
  }
  if (s.look % 20 === 0) {
    red(player, 0.3, 0.25 + 0.3 * Math.min(1, (s.look - STARE) / (PHENOMENON - STARE)));
    tryRun(() => player.addEffect("nausea", 200, { showParticles: false }));
    tryRun(() => player.addEffect("slowness", 60, { amplifier: 1, showParticles: false }));
    tryRun(() => player.applyDamage(1));
    sound(player, "hb.drone", 0.8, 0.5);
  }
  if (s.look >= PHENOMENON) {
    s.done = true;
    red(player, 1.2, 0.7);
    scare(player, " ", tr("hb.bo.sub"));
    tryRun(() => player.applyDamage(6));
    tryRun(() => player.addEffect("blindness", 60, { showParticles: false }));
    addFear(player, 35);
    later(10, () => vanish(e, false));
  }
}

function tick(e, s, player) {
  if (s.done) return;
  s.clock += 4;
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || d > 110) {
    vanish(e, false);
    return;
  }
  faceTowards(e, player.location);
  if (canSee(player, e, 110, 0.95)) {
    s.unseen = 0;
    s.look += 4;
    if (s.look >= STARE) phenomenon(e, s, player);
    return;
  }
  if (s.look >= STARE) tryRun(() => e.setProperty("hb:stare", false));
  s.look = Math.max(0, s.look - 8);
  // It is closer, a little, and not often.
  if ((s.unseen += 4) >= 200 && s.hops < 3) {
    s.unseen = 0;
    s.hops++;
    if (stepCloser(e, player, 0.7, 20)) soundAt(player, "hb.drone", e.location, 0.5, 0.6);
  }
  if (s.clock > 20 * 120 && !s.adopted) vanish(e, false);
}

define({
  kind: "boiled",
  type: "hb:boiled_one",
  weights: (ctx) => (ctx.under ? [1, 0] : ctx.outside ? [2, ctx.night ? 3 : 1] : [2, 0]),
  minor: numbers,
  major: (player) => standing(player),
  sleep: (player) => bedside(player, "boiled", "still", {
    sub: tr("hb.bo.bed"),
    after: (e) => {
      tryRun(() => e.setProperty("hb:stare", true));
      red(player, 0.8, 0.5);
      later(50, () => vanish(e, false));
    },
  }),
  tick,
  onHit: (e, s, player) => {
    // It isn't really there.
    red(player, 0.3, 0.4);
    tryRun(() => player.addEffect("nausea", 100, { showParticles: false }));
  },
  test: (player) => standing(player, true) || numbers(player),
});
