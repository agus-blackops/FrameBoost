// The Ancient City, reworked. Walk into one and the game tells you where you
// are. While you are inside, the city is awake: candles go out one by one
// around you, shriekers scream with nothing near them, your own footsteps
// come back to you a moment late, the dark pulses, the portal frame hums,
// and you hear the city's dead whisper. And Herobrine stands in the ruins:
// stay long enough and you see him, director or not.

import { BlockPermutation, system } from "@minecraft/server";
import {
  actionBar, later, overworldPlayers, pick, rand, randInt, sound, soundAt, soundBehind, tr, tryRun,
} from "../core/util.js";
import { active, addFear, bump, setWorldProp, worldProp } from "../core/store.js";
import { findBlocks, inAncientCity } from "../core/places.js";
import { define } from "../core/creatures.js";
import { sighting } from "../creatures/herobrine.js";

const AMBIENCE_EVERY = [300, 600]; // ticks between the city's moments
const WATCHED_AFTER = 20 * 60; // a minute inside and Herobrine stands in the ruins, watching
const WHISPERS = 6;

export const visits = new Map(); // playerId -> { since, next, seen }

function title(player) {
  tryRun(() =>
    player.onScreenDisplay.setTitle(tr("hl.ancient.title"), {
      subtitle: tr("hl.ancient.sub"), fadeInDuration: 20, stayDuration: 60, fadeOutDuration: 30,
    })
  );
  sound(player, "hl.drone", 0.9, 0.6);
  later(40, () => sound(player, "hl.heartbeat", 0.6, 0.5));
}

// Candles around you go out, one after another.
export function snuffCandles(player) {
  const lit = findBlocks(player, /candle/, 10, 12).filter((b) => tryRun(() => b.permutation.getAllStates())?.lit);
  if (!lit.length) return false;
  lit.forEach((b, i) =>
    later(i * 8, () => {
      const states = tryRun(() => b.permutation.getAllStates()) ?? {};
      const perm = tryRun(() => BlockPermutation.resolve(b.typeId, { ...states, lit: false }));
      if (perm) tryRun(() => b.setPermutation(perm));
      soundAt(player, "random.fizz", { x: b.x + 0.5, y: b.y + 0.5, z: b.z + 0.5 }, 1.2, 0.5);
    })
  );
  later(lit.length * 8 + 10, () => actionBar(player, tr("hl.ancient.candles")));
  addFear(player, 10);
  return true;
}

// A shrieker screams with nothing near it.
export function shriek(player) {
  const [b] = findBlocks(player, /sculk_shrieker/, 16, 1);
  const at = b ? { x: b.x + 0.5, y: b.y + 1, z: b.z + 0.5 } : null;
  if (at) soundAt(player, "hl.shriek", at, 0.7, 1);
  else soundBehind(player, "hl.shriek", rand(10, 16), 0.7, 0.8);
  tryRun(() => player.addEffect("darkness", 100, { showParticles: false }));
  addFear(player, 12);
  return true;
}

// Your own footsteps, a moment late.
export function echoSteps(player) {
  for (let i = 0; i < 5; i++) later(30 + i * 7, () => soundBehind(player, "step.stone", 4 + i * 0.3, 0.9, 0.7));
  later(70, () => actionBar(player, tr("hl.ancient.steps")));
  addFear(player, 8);
  return true;
}

// The dark pulses like a heart.
export function pulse(player) {
  for (let i = 0; i < 3; i++) later(i * 24, () => sound(player, "hl.heartbeat", 1, 0.45));
  tryRun(() => player.addEffect("darkness", 80, { showParticles: false }));
  addFear(player, 6);
  return true;
}

// The frame in the middle of the city hums, as if something were trying the door.
export function portal(player) {
  const [b] = findBlocks(player, /reinforced_deepslate/, 14, 1);
  if (!b) return false;
  const at = { x: b.x + 0.5, y: b.y + 1, z: b.z + 0.5 };
  soundAt(player, "hl.drone", at, 0.4, 1);
  for (let i = 0; i < 6; i++) {
    later(i * 5, () => tryRun(() => player.dimension.spawnParticle("minecraft:sculk_soul_particle", {
      x: at.x + rand(-2, 2), y: at.y + rand(0, 2), z: at.z + rand(-2, 2) })));
  }
  later(30, () => actionBar(player, tr("hl.ancient.portal")));
  addFear(player, 10);
  return true;
}

export function whisper(player) {
  soundBehind(player, "hl.whisper", rand(3, 6), 0.55, 0.9);
  actionBar(player, tr(`hl.ancient.whisper.${randInt(1, WHISPERS)}`));
  addFear(player, 6);
  return true;
}

export function cityMoment(player) {
  const moment = pick([["candles", 3], ["shriek", 2], ["steps", 3], ["pulse", 2], ["portal", 2], ["whisper", 3]]);
  const fn = { candles: snuffCandles, shriek, steps: echoSteps, pulse, portal, whisper }[moment];
  return !!(fn(player) || whisper(player));
}

// Every second.
export function tickAncient() {
  if (!active("ancient")) return;
  const now = system.currentTick;
  for (const player of overworldPlayers()) {
    const inside = inAncientCity(player);
    let v = visits.get(player.id);
    if (!inside) {
      if (v && now - v.last > 20 * 30) visits.delete(player.id);
      continue;
    }
    if (!v) {
      v = { since: now, next: now + 200, last: now, seen: false };
      visits.set(player.id, v);
      title(player);
      if (!worldProp("hl:ancient_found", false)) setWorldProp("hl:ancient_found", true);
      bump("ancient");
      addFear(player, 15);
    }
    v.last = now;
    if (now >= v.next) {
      v.next = now + randInt(...AMBIENCE_EVERY);
      tryRun(() => cityMoment(player));
    }
    if (!v.seen && now - v.since >= WATCHED_AFTER) {
      // Someone is standing in the ruins, watching, at the end of the street.
      v.seen = !!tryRun(() => active("herobrine") && sighting(player));
      if (!v.seen) v.since = now - WATCHED_AFTER + 200; // try again in a bit
    }
  }
}


define({
  kind: "ancient",
  weights: () => [0, 0],
  // Test: whatever the city does, wherever you are.
  test: (player) => cityMoment(player),
  off: () => visits.clear(),
});
