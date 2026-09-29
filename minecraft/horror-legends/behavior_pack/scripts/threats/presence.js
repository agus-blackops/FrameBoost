// Chapter 4: the Presence, something in your house. The lights go out one by
// one; footsteps follow you and stop a step after you stop; something taps
// on the window glass and looks in; a music box plays in another room; your
// own death turns up in the chat; and sometimes, when you wake, someone was
// standing by your bed. A Ward Lantern keeps it out.

import { system, world } from "@minecraft/server";
import {
  block, distance, facing, groundAt, groundSpot, isIndoors, isNight, isOpen, isUnderground, later,
  offsetFromView, onCooldown, pick, rand, randInt, shuffle, soundAt, soundBehind, tr, tryRun,
} from "../lib/util.js";
import { MOB, spawn, trackedFor } from "../lib/actors.js";
import { active, addFear, discover, roll } from "../lib/state.js";
import { restoreBlocks, swapBlock } from "../lib/saved.js";
import { nearWard } from "../items/ward.js";
import { reveal } from "./herobrine.js";

const LIGHTS = /^minecraft:(soul_)?(torch|lantern)$/;
export const isLightsGone = (type) => type === "minecraft:air";

const stalkers = new Map(); // playerId -> { until, last, look, quiet }
const bedNights = new Map(); // playerId -> the day it last came to your bed

// The lights go out one by one, furthest first. You hear breathing in the
// dark. When they come back, sometimes someone is standing in front of you.
export function lightsOut(player) {
  if (nearWard(player.dimension, player.location) || onCooldown(`lights:${player.id}`, 20 * 60)) return false;
  const { x, y, z } = player.location;
  const found = [];
  for (let dx = -10; dx <= 10; dx++)
    for (let dz = -10; dz <= 10; dz++)
      for (let dy = -3; dy <= 4; dy++) {
        const b = block(player.dimension, x + dx, y + dy, z + dz);
        if (b && LIGHTS.test(b.typeId)) found.push(b);
      }
  if (!found.length) return false;
  found.sort((a, b) => distance(b, player.location) - distance(a, player.location));
  const lights = found.slice(-12);
  const dark = randInt(100, 160);
  const back = world.getAbsoluteTime() + lights.length * 6 + dark;
  lights.forEach((b, i) =>
    later(i * 6, () => {
      const here = block(player.dimension, b.x, b.y, b.z);
      if (!here || !LIGHTS.test(here.typeId)) return;
      if (swapBlock("hl:lights", here, "minecraft:air", back)) {
        soundAt(player, "random.fizz", { x: b.x + 0.5, y: b.y + 0.5, z: b.z + 0.5 }, 0.6, 0.4);
      }
    })
  );
  const out = lights.length * 6;
  later(out + 30, () => soundBehind(player, "hl.breath", 1.3, 0.9, 1));
  later(out + dark, () => {
    restoreBlocks("hl:lights", isLightsGone, true);
    if (Math.random() < 0.45) reveal(player);
  });
  discover("presence");
  addFear(player, 25);
  return true;
}

// Footsteps that follow you, stop a step after you stop, and are gone the
// moment you turn round.
export function stalk(player) {
  if (stalkers.has(player.id)) return false;
  stalkers.set(player.id, { until: system.currentTick + 20 * 25, last: { ...player.location }, look: facing(player), quiet: 0 });
  discover("presence");
  return true;
}

export function stalking(player) {
  return stalkers.has(player.id);
}

export function clearPresence() {
  stalkers.clear();
  restoreBlocks("hl:lights", isLightsGone, true);
}

function tickStalker(player) {
  const s = stalkers.get(player.id);
  if (!s) return;
  const f = facing(player);
  const turned = f.x * s.look.x + f.z * s.look.z < -0.2;
  s.look = f;
  if (turned) {
    stalkers.delete(player.id);
    soundBehind(player, "hl.whisper", 5, 0.8, 0.5);
    return;
  }
  if (system.currentTick > s.until) {
    stalkers.delete(player.id);
    soundBehind(player, "hl.breath", 1.2, 0.9, 1);
    return;
  }
  const moved = Math.hypot(player.location.x - s.last.x, player.location.z - s.last.z);
  s.last = { ...player.location };
  const step = isUnderground(player) ? "step.stone" : "step.grass";
  if (moved > 0.35) {
    s.quiet = 0;
    soundBehind(player, step, 2.5, 0.85, 0.9);
    addFear(player, 1);
  } else if (++s.quiet === 1) {
    later(9, () => soundBehind(player, step, 2.2, 0.85, 0.9)); // one more step, after you stopped
  }
}

// A face at the window, tapping on the glass.
export function windowWatcher(player) {
  if (trackedFor(player, "herobrine") || nearWard(player.dimension, player.location)) return false;
  const { x, y, z } = player.location;
  const panes = [];
  for (let dx = -8; dx <= 8; dx++)
    for (let dz = -8; dz <= 8; dz++)
      for (let dy = -1; dy <= 2; dy++) {
        const b = block(player.dimension, x + dx, y + dy, z + dz);
        if (b && /glass/.test(b.typeId) && distance(b, player.location) >= 3) panes.push(b);
      }
  for (const g of shuffle(panes).slice(0, 16)) {
    const dx = g.x + 0.5 - x;
    const dz = g.z + 0.5 - z;
    const out = Math.abs(dx) > Math.abs(dz) ? { x: g.x + Math.sign(dx), z: g.z } : { x: g.x, z: g.z + Math.sign(dz) };
    const gy = groundAt(player.dimension, out.x + 0.5, out.z + 0.5, g.y + 1);
    if (gy === undefined || Math.abs(gy - g.y) > 2) continue;
    if (!isOpen(block(player.dimension, out.x, gy, out.z)) || !isOpen(block(player.dimension, out.x, gy + 1, out.z))) continue;
    const e = spawn(MOB.herobrine, { x: out.x + 0.5, y: gy, z: out.z + 0.5 }, player, "herobrine", "window", { major: true });
    if (!e) continue;
    const pane = { x: g.x + 0.5, y: g.y + 0.5, z: g.z + 0.5 };
    for (let k = 0; k < 3; k++) later(k * 9, () => soundAt(player, "hl.knock", pane, 1.8, 0.25));
    discover("presence");
    return true;
  }
  return false;
}

// Every 6 ticks: if you are asleep, it may come and stand by the bed.
function checkSleep(player) {
  if (!active("presence") || !tryRun(() => player.isSleeping)) return;
  if (bedNights.get(player.id) === world.getDay()) return;
  bedNights.set(player.id, world.getDay());
  if (!roll(0.35) || trackedFor(player, "herobrine") || nearWard(player.dimension, player.location)) return;
  const at = groundSpot(player, 1.5, 2.5, rand(0, 360), 180);
  if (!at) return;
  spawn(MOB.herobrine, at, player, "herobrine", "bedside", { major: true });
  later(20, () => soundAt(player, "hl.breath", at, 0.85, 1));
}

export function musicBox(player) {
  const p = offsetFromView(player, rand(5, 9), rand(100, 260));
  const at = { x: p.x, y: player.location.y + 1, z: p.z };
  soundAt(player, "hl.musicbox", at, rand(0.92, 1.0), 1);
  later(240, () => soundAt(player, "hl.whisper", at, 0.8, 0.8));
  addFear(player, 10);
  return true;
}

// Your own death in the chat, and then a correction.
export function fakeDeath(player) {
  if (onCooldown(`death:${player.id}`, 20 * 600)) return false;
  player.sendMessage(tr("hl.presence.death", player.name));
  later(80, () => player.sendMessage(tr("hl.presence.death2")));
  addFear(player, 15);
  return true;
}

export function presenceMinor(player) {
  if (nearWard(player.dimension, player.location)) return false;
  const night = isNight();
  const event = pick([
    ["stalk", 20],
    ["breath", 14],
    ["musicbox", night ? 10 : 4],
    ["voice", 10],
    ["death", 3],
  ]);
  discover("presence");
  switch (event) {
    case "stalk":
      return stalk(player);
    case "breath":
      soundBehind(player, "hl.breath", 1.2, rand(0.85, 1.0), 1);
      addFear(player, 8);
      return true;
    case "musicbox":
      return musicBox(player);
    case "voice":
      soundBehind(player, "hl.whisper", 1.5, rand(0.8, 1.0), 1);
      player.sendMessage(tr("hl.ambience.whisper", player.name));
      addFear(player, 8);
      return true;
    case "death":
      return fakeDeath(player);
  }
  return false;
}

export function presenceMajor(player) {
  if (nearWard(player.dimension, player.location)) return false;
  const night = isNight();
  const indoors = isIndoors(player);
  const event = pick([
    ["lights", night || indoors ? 5 : 0],
    ["window", night ? 4 : 1],
  ]);
  if (event === "lights") return lightsOut(player) || windowWatcher(player);
  return windowWatcher(player) || lightsOut(player);
}

// Every 6 ticks.
export function tickPresence(players) {
  for (const player of players) {
    tryRun(() => tickStalker(player));
    tryRun(() => checkSleep(player));
  }
}
