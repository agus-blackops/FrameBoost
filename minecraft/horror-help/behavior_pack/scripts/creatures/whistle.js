// Act II: the Whistle Occurrence (VibingLeaf). "Do you hear the whistle?"
// A little tune, whistled, from somewhere out there. Then you see it: a
// Roblox avatar, black, stretched to five blocks tall, with no face, that
// came out of a game that broke. Flowers.
//
// Its whistle does things to people. Every time you hear it, CROUCH (cover
// your ears) before it ends. If you don't, it gets into your head: the
// world swims, the screen flickers, it hurts. Three times and you black
// out, and when you open your eyes it is right in front of you, stretching.
// Cover your ears every time and it gives up, and stretches away.

import { actionBar, distance, faceTowards, later, rand, randInt, sound, soundAt, soundBehind, tr, tryRun, visibleSpot } from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards } from "../core/hunt.js";

const WHISTLE = 56; // ticks the whistle lasts: crouch before it ends
const WHISTLES = 4;
const TOO_MANY = 3;

export function whistleOccurrence(player, anywhere = false) {
  if (trackedFor(player, "whistle") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 20, 30, rand(-40, 40), 20) ?? (anywhere ? visibleSpot(player, 10, 18, 0, 90) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("whistle", at, player, undefined, {
    major: true, heard: 0, whistles: 0, next: 40, window: 0, covered: false, clock: 0,
  });
  if (!e) return false;
  faceTowards(e, player.location);
  later(10, () => actionBar(player, tr("hh.whi.hear")));
  addFear(player, 15);
  return true;
}

// A whistle, very far off. Someone calling a dog that isn't there.
export function whistleMinor(player) {
  soundBehind(player, "hh.flowers", rand(30, 50), rand(0.95, 1.05), 0.9);
  later(60, () => actionBar(player, tr("hh.whi.minor")));
  addFear(player, 8);
  return true;
}

function covering(player) {
  return !!tryRun(() => player.isSneaking);
}

// You didn't cover your ears.
function inYourHead(e, s, player) {
  s.heard++;
  discover("whistle");
  for (let i = 0; i < 4; i++) later(i * 7, () => blackout(player, 0.15));
  tryRun(() => player.addEffect("nausea", 200, { showParticles: false }));
  tryRun(() => player.applyDamage(3));
  sound(player, "hh.glitch", 1, rand(0.8, 1.0));
  actionBar(player, tr("hh.whi.hurt"));
  addFear(player, 20);
  if (s.heard >= TOO_MANY) later(30, () => blackOut(e, s, player));
}

// The third time, you lose consciousness. When you open your eyes...
function blackOut(e, s, player) {
  s.done = true;
  tryRun(() => player.addEffect("blindness", 70, { showParticles: false }));
  tryRun(() => player.addEffect("slowness", 70, { amplifier: 6, showParticles: false }));
  later(50, () => {
    const v = player.getViewDirection();
    tryRun(() => e.teleport({ x: player.location.x + v.x * 2.2, y: player.location.y, z: player.location.z + v.z * 2.2 }));
    faceTowards(e, player.location);
    tryRun(() => e.setProperty("hh:stretch", true));
    soundAt(player, "hh.screech", e.location, 0.7, 1);
    soundAt(player, "hh.flowers", e.location, 0.6, 1);
    tryRun(() => player.applyDamage(8));
    scare(player, " ", tr("hh.whi.sub"));
    addFear(player, 35);
  });
  later(90, () => vanish(e, false));
}

function tick(e, s, player) {
  if (s.done) return;
  s.clock += 4;
  faceTowards(e, player.location);
  if (nearWard(player.dimension, player.location) || distance(player.location, e.location) > 90) {
    vanish(e, false);
    return;
  }
  if (s.window > 0) {
    // The whistle is playing: crouch before it ends.
    if (covering(player)) s.covered = true;
    if ((s.window -= 4) <= 0 && !s.covered) inYourHead(e, s, player);
    else if (s.window <= 0) actionBar(player, tr("hh.whi.covered"));
    return;
  }
  if ((s.next -= 4) > 0) return;
  if (s.whistles >= WHISTLES) {
    // You never let it in. It stretches up and up, and is gone.
    s.done = true;
    tryRun(() => e.setProperty("hh:stretch", true));
    actionBar(player, tr("hh.whi.safe"));
    later(60, () => vanish(e, false));
    return;
  }
  s.whistles++;
  s.window = WHISTLE;
  s.covered = covering(player);
  s.next = randInt(5, 8) * 20;
  soundAt(player, "hh.flowers", e.location, rand(0.97, 1.03), 1);
  if (s.whistles === 1) later(4, () => actionBar(player, tr("hh.whi.cover")));
}

define({
  kind: "whistle",
  type: "hh:flowers",
  weights: (ctx) => (ctx.under ? [0, 0] : [2, ctx.night ? 3 : 1]),
  minor: whistleMinor,
  major: (player) => whistleOccurrence(player),
  tick,
  adopt: () => undefined,
  onHit: (e, s, player) => {
    sound(player, "hh.glitch", 1, 0.6);
    vanish(e, false);
  },
  test: (player) => whistleOccurrence(player, true) || whistleMinor(player),
});
