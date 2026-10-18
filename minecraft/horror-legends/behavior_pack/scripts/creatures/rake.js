// Act II: the Rake. In open fields at night, something pale crouches at the
// edge of what you can see, knees up by its ears, watching you with big
// black eyes. While you look at it, it doesn't move; look too long and it
// shrieks and runs off on all fours, faster than anything should. Look
// away and it is closer. If it gets near enough without being seen, it
// rakes you with its claws. And some nights, when you wake up, it is
// crouched at the foot of your bed.

import { world } from "@minecraft/server";
import {
  actionBar, canSee, distance, faceTowards, groundSpot, later, overworldPlayers, rand, soundAt, soundBehind, tr,
  tryRun, visibleSpot,
} from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { active, addFear, discover, roll, setWorldProp, worldProp } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";

const STARE_LIMIT = 50; // ticks of being watched before it bolts
const STRIKE_RANGE = 3;

export function rakeMinor(player) {
  const r = Math.random();
  if (r < 0.35) {
    // A shriek, far off, too high for any animal.
    soundBehind(player, "hl.shriek", rand(28, 40), rand(1.5, 1.7), 0.8);
  } else if (r < 0.7) {
    // Something running on all fours, past you, in the dark.
    const side = Math.random() < 0.5 ? -1 : 1;
    for (let i = 0; i < 8; i++) {
      later(i * 3, () => {
        const v = player.getViewDirection();
        const at = { x: player.location.x - v.z * side * 9 + v.x * (i - 4) * 2, y: player.location.y, z: player.location.z + v.x * side * 9 + v.z * (i - 4) * 2 };
        soundAt(player, "step.grass", at, 1.4, 0.9);
      });
    }
  } else {
    soundBehind(player, "hl.whisper", rand(6, 9), 1.6, 0.6);
  }
  addFear(player, 9);
  return true;
}

export function spawnRake(player) {
  if (trackedFor(player, "rake")) return false;
  const at = visibleSpot(player, 20, 30, rand(-55, 55), 6);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("rake", at, player, "crouch", { major: true, stare: 0 });
  if (e) soundAt(player, "hl.breath", e.location, 1.5, 0.6);
  return !!e;
}

function bolt(e, s, player) {
  if (s.mode === "flee") return;
  setMode(s, "flee");
  s.hunting = false;
  soundAt(player, "hl.shriek", e.location, rand(1.4, 1.6), 1);
  addFear(player, 15);
}

function pounce(e, s, player) {
  if (s.mode === "hunt") return;
  setMode(s, "hunt");
  s.hunting = true;
  s.timer = 0;
  soundAt(player, "hl.shriek", e.location, 1.7, 1);
  scare(player, " ", "§f§l! ! !");
  discover("rake");
  addFear(player, 30);
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  s.timer += 4;
  if (s.mode === "flee") {
    if (s.timer > 60 || d > 40) vanish(e, false);
    return;
  }
  if (s.mode === "hunt") {
    // One rush; whether it lands or not, it is gone again.
    if (s.timer > 20 * 10 || s.struck) bolt(e, s, player);
    return;
  }
  if (s.adopted) return;
  if ((!s.bed && nearWard(player.dimension, player.location)) || s.age > 20 * 120 || d > 64) {
    vanish(e);
    return;
  }
  const seen = canSee(player, e, 64, 0.9);
  if (s.bed) {
    // At the foot of the bed: there when you open your eyes, then gone.
    if (tryRun(() => player.isSleeping)) return;
    if (!s.woke) {
      s.woke = true;
      s.timer = 0;
      faceTowards(e, player.location);
      soundAt(player, "hl.breath", e.location, 1.4, 1);
      actionBar(player, tr("hl.rake.bed"));
      discover("rake");
      addFear(player, 40);
    }
    if (s.timer >= 30 || (seen && s.timer >= 12)) bolt(e, s, player);
    return;
  }
  if (seen) {
    discover("rake");
    s.unseen = 0;
    s.stare += 4;
    if (d < 6) pounce(e, s, player); // you walked right up to it
    else if (s.stare >= STARE_LIMIT) bolt(e, s, player);
    return;
  }
  s.stare = Math.max(0, s.stare - 2);
  if (d <= STRIKE_RANGE) {
    pounce(e, s, player);
    return;
  }
  if (++s.unseen >= 6) {
    s.unseen = 0;
    if (stepCloser(e, player, 0.55, 2.5)) soundAt(player, "step.grass", e.location, 1.3, 0.7);
  }
}

// Every second: a sleeping player, a night it hasn't come yet.
export function tickRakeBed() {
  if (!active("rake")) return;
  const day = world.getDay();
  for (const player of overworldPlayers()) {
    if (!tryRun(() => player.isSleeping) || trackedFor(player, "rake")) continue;
    if (worldProp("hl:rake_bed_day", -1) === day || !roll(0.08)) continue;
    const at = groundSpot(player, 1.6, 2.4, 0, 70);
    if (!at) continue;
    setWorldProp("hl:rake_bed_day", day);
    spawn("rake", at, player, "crouch", { bed: true, stare: 0, major: true });
  }
}

define({
  kind: "rake",
  type: "hl:rake",
  modes: ["crouch", "hunt", "flee"],
  weights: (ctx) => {
    if (!ctx.outside || !ctx.night) return [0, 0];
    return [ctx.forest ? 1 : 2, ctx.forest ? 2 : 3];
  },
  minor: rakeMinor,
  major: spawnRake,
  tick,
  adopt: () => "crouch",
  onHit: (e, s, player) => bolt(e, s, player),
  onStrike: (e, s, player) => {
    s.struck = true;
    tryRun(() => player.addEffect("nausea", 120, { showParticles: false }));
    tryRun(() => player.addEffect("slowness", 40, { amplifier: 1, showParticles: false }));
  },
  shine: (e, s, player) => s.mode === "crouch" && !s.bed && bolt(e, s, player),
  test: (player) => spawnRake(player) || rakeMinor(player),
});
