// Act III: Gabriel (The Mandela Catalogue). In the middle of the night, a
// choir, from above. Something comes down out of the sky in front of you:
// five blocks of it, robed, four wings of eyes, a golden ring turning over
// its head, seven eyes on its face and a mouth from the top of it to the
// bottom. "Do not be afraid." It drifts closer. Do not look upon it: turn
// your back and wait until it has passed over. Look at it for too long and
// it opens its wings to you, and you have seen what it is.

import { actionBar, distance, faceTowards, later, looksAt, rand, sound, soundAt, tr, tryRun, visibleSpot } from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";

const LOOK_LIMIT = 60; // ticks of looking upon it
const PASSES = 20 * 12; // ticks with your back turned until it passes over
const SAYS = 3;

function say(player, n) {
  tryRun(() => player.sendMessage({ rawtext: [{ translate: "hh.gab.name" }, { text: " " }, { translate: `hh.gab.say.${n}` }] }));
}

function white(player) {
  tryRun(() => player.camera.fade({ fadeColor: { red: 1, green: 1, blue: 0.9 }, fadeTime: { fadeInTime: 0.1, holdTime: 0.3, fadeOutTime: 1.5 } }));
}

export function descend(player, anywhere = false) {
  if (trackedFor(player, "gabriel") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 12, 18, rand(-20, 20), 10) ?? (anywhere ? visibleSpot(player, 6, 10, 0, 90) : undefined);
  if (!at) return false;
  const e = spawn("gabriel", at, player, undefined, { major: true, look: 0, back: 0, said: 0, clock: 0 });
  if (!e) return false;
  faceTowards(e, player.location);
  white(player);
  sound(player, "hh.choir", 1, 1);
  later(30, () => say(player, 0));
  discover("gabriel");
  addFear(player, 25);
  return true;
}

// A choir, very far above you, singing one chord that is nearly right.
export function choirMinor(player) {
  soundAt(player, "hh.choir", { x: player.location.x + rand(-20, 20), y: player.location.y + 40, z: player.location.z + rand(-20, 20) }, rand(0.8, 0.95), 1);
  later(60, () => actionBar(player, tr("hh.gab.minor")));
  addFear(player, 8);
  return true;
}

function beheld(e, s, player) {
  s.done = true;
  tryRun(() => e.setProperty("hh:open", true));
  white(player);
  sound(player, "hh.choir", 1, 0.7);
  soundAt(player, "hh.screech", e.location, 0.6, 1);
  blackout(player, 0.6);
  tryRun(() => player.addEffect("blindness", 200, { showParticles: false }));
  tryRun(() => player.addEffect("slowness", 160, { amplifier: 2, showParticles: false }));
  tryRun(() => player.applyDamage(10));
  scare(player, " ", tr("hh.gab.sub"));
  addFear(player, 40);
  later(30, () => vanish(e, false));
}

function tick(e, s, player) {
  if (s.done) return;
  s.clock += 4;
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || d > 80) {
    vanish(e, false);
    return;
  }
  faceTowards(e, player.location);
  if (s.clock % 140 === 0 && s.said < SAYS - 1) say(player, ++s.said);
  // It drifts towards you, always.
  if (d > 4) {
    const k = 0.2 / d;
    tryRun(() => e.teleport({
      x: e.location.x + (player.location.x - e.location.x) * k, y: e.location.y, z: e.location.z + (player.location.z - e.location.z) * k,
    }));
  }
  tryRun(() => e.setProperty("hh:open", d < 7));
  if (looksAt(player, e, 64, 0.85)) {
    s.look += 4;
    s.back = 0;
    if (s.look >= LOOK_LIMIT) beheld(e, s, player);
    else if (s.look === 24) actionBar(player, tr("hh.gab.warn"));
    return;
  }
  // Your back is turned. Wait, and it passes over you.
  s.back += 4;
  if (s.back >= PASSES) {
    white(player);
    actionBar(player, tr("hh.gab.passed"));
    vanish(e, false);
  }
}

define({
  kind: "gabriel",
  type: "hh:gabriel",
  weights: (ctx) => (ctx.night && !ctx.under ? [1, 3] : [1, 0]),
  minor: choirMinor,
  major: (player) => descend(player),
  tick,
  adopt: () => undefined,
  onHit: (e, s, player) => !s.done && beheld(e, s, player),
  test: (player) => descend(player, true) || choirMinor(player),
});
