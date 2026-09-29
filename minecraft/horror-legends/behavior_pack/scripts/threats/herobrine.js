// Chapter 3: white eyes. Herobrine at the edge of sight, gone when you stare;
// right behind you when you don't turn round. Sand pyramids, leafless trees,
// 2x2 tunnels, red torches, signs. Hit him and he is behind you.
// Stare him down three times and he leaves a page behind.

import { BlockPermutation, world } from "@minecraft/server";
import {
  NATURAL_STONE, block, canSee, distance, faceTowards, facing, groundAt, groundSpot, isUnderground, later, looksAt,
  offsetFromView, pick, place, rand, randInt, sound, soundAt, tr, tryRun, visibleSpot, actionBar,
} from "../lib/util.js";
import { MOB, registerKind, spawn, trackedFor, vanish } from "../lib/actors.js";
import { addFear, bump, discover } from "../lib/state.js";
import { blackout, scare } from "../lib/fx.js";
import { nearWard } from "../items/ward.js";
import { dropPage } from "../items/pages.js";

// Far off between the trees, or (sometimes) right behind you.
export function sighting(player, behind = Math.random() < 0.15) {
  if (trackedFor(player, "herobrine")) return false;
  if (behind) {
    const at = groundSpot(player, 2.5, 3.5, 180, 20);
    if (at) return !!spawn(MOB.herobrine, at, player, "herobrine", "behind", { major: true });
  }
  const side = Math.random() < 0.5 ? -1 : 1;
  const at = visibleSpot(player, 28, 44, side * rand(15, 35), 5);
  if (!at) return false;
  const e = spawn(MOB.herobrine, at, player, "herobrine", "sighting");
  if (e && Math.random() < 0.3) soundAt(player, "ambient.weather.thunder", at, 0.8, 0.6);
  return !!e;
}

// Standing in front of you when the lights come back on.
export function reveal(player, at) {
  if (trackedFor(player, "herobrine")) return undefined;
  const spot = at ?? groundSpot(player, 2.5, 3.5, 0, 25);
  return spot && spawn(MOB.herobrine, spot, player, "herobrine", "reveal", { scare: true, major: true });
}

function pyramid(player) {
  const dimension = player.dimension;
  for (let attempt = 0; attempt < 6; attempt++) {
    const p = offsetFromView(player, rand(20, 32), rand(0, 360));
    const gy = groundAt(dimension, p.x, p.z, player.location.y);
    if (gy === undefined || nearWard(dimension, { x: p.x, y: gy, z: p.z })) continue;
    const x = Math.floor(p.x);
    const z = Math.floor(p.z);
    for (let layer = 0; layer < 3; layer++) {
      const half = 2 - layer;
      for (let dx = -half; dx <= half; dx++)
        for (let dz = -half; dz <= half; dz++) place(dimension, x + dx, gy + layer, z + dz, "minecraft:sand");
    }
    place(dimension, x, gy + 3, z, "minecraft:redstone_torch");
    return true;
  }
  return false;
}

function stripLeaves(player) {
  const dimension = player.dimension;
  let origin;
  for (let i = 0; i < 50 && !origin; i++) {
    const p = offsetFromView(player, rand(8, 22), rand(0, 360));
    const b = block(dimension, p.x, player.location.y + rand(2, 14), p.z);
    if (b?.typeId.includes("leaves") && !nearWard(dimension, b)) origin = { x: b.x, y: b.y, z: b.z };
  }
  if (!origin) return false;
  const seen = new Set();
  const queue = [origin];
  let removed = 0;
  while (queue.length && removed < 90) {
    const c = queue.shift();
    const key = `${c.x},${c.y},${c.z}`;
    if (seen.has(key)) continue;
    seen.add(key);
    if (Math.abs(c.x - origin.x) > 5 || Math.abs(c.y - origin.y) > 5 || Math.abs(c.z - origin.z) > 5) continue;
    const b = block(dimension, c.x, c.y, c.z);
    if (!b?.typeId.includes("leaves")) continue;
    tryRun(() => b.setType("minecraft:air"));
    removed++;
    for (const [dx, dy, dz] of [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]]) {
      queue.push({ x: c.x + dx, y: c.y + dy, z: c.z + dz });
    }
  }
  return removed > 0;
}

function tunnel(player) {
  const dimension = player.dimension;
  const [dx, dz] = [[1, 0], [-1, 0], [0, 1], [0, -1]][randInt(0, 3)];
  const px = -dz;
  const pz = dx;
  const side = Math.random() < 0.5 ? -1 : 1;
  const sx = Math.floor(player.location.x) + dx * randInt(3, 6) + px * side * randInt(3, 5);
  const sz = Math.floor(player.location.z) + dz * randInt(3, 6) + pz * side * randInt(3, 5);
  const sy = Math.floor(player.location.y);
  if (nearWard(dimension, { x: sx, y: sy, z: sz })) return false;
  let dug = 0;
  for (let i = 0; i < 16; i++)
    for (let w = 0; w < 2; w++)
      for (let h = 0; h < 2; h++) {
        const b = block(dimension, sx + dx * i + px * w, sy + h, sz + dz * i + pz * w);
        if (b && NATURAL_STONE.test(b.typeId)) {
          tryRun(() => b.setType("minecraft:air"));
          dug++;
        }
      }
  return dug > 8;
}

export function redTorches(player) {
  if (nearWard(player.dimension, player.location)) return false;
  const dimension = player.dimension;
  const { x, y, z } = player.location;
  let changed = 0;
  for (let dx = -8; dx <= 8 && changed < 6; dx++)
    for (let dz = -8; dz <= 8 && changed < 6; dz++)
      for (let dy = -3; dy <= 4 && changed < 6; dy++) {
        const b = block(dimension, x + dx, y + dy, z + dz);
        if (b?.typeId !== "minecraft:torch") continue;
        const states = tryRun(() => b.permutation.getAllStates()) ?? {};
        const perm = tryRun(() => BlockPermutation.resolve("minecraft:redstone_torch", states));
        if (perm && tryRun(() => (b.setPermutation(perm), true))) changed++;
      }
  if (changed) {
    sound(player, "random.fizz", 1, 0.6);
    actionBar(player, tr("hl.hb.torches"));
  }
  return changed > 0;
}

function sign(player) {
  const at = groundSpot(player, 5, 9, 0, 35);
  if (!at || nearWard(player.dimension, at)) return false;
  const dx = player.location.x - at.x;
  const dz = player.location.z - at.z;
  const direction = ((Math.round((-Math.atan2(dx, dz) * 180) / Math.PI / 22.5) % 16) + 16) % 16;
  const perm = tryRun(() => BlockPermutation.resolve("minecraft:standing_sign", { ground_sign_direction: direction }));
  const b = block(player.dimension, at.x, at.y, at.z);
  if (!b || !perm || !tryRun(() => (b.setPermutation(perm), true))) return false;
  later(1, () => b.getComponent("minecraft:sign")?.setText({ translate: `hl.sign.${randInt(1, 6)}` }));
  return true;
}

export function fakeJoin(name, recipient) {
  const send = (key) => {
    const msg = { rawtext: [{ text: "§e" }, { translate: key, with: [name] }] };
    if (recipient) recipient.sendMessage(msg);
    else world.sendMessage(msg);
  };
  send("multiplayer.player.joined");
  later(randInt(60, 180), () => send("multiplayer.player.left"));
}

// Something he leaves behind. Returns true if anything happened.
export function herobrineMinor(player) {
  const under = isUnderground(player);
  const event = pick([
    ["sighting", under ? 0 : 40],
    ["pyramid", under ? 0 : 12],
    ["leaves", under ? 0 : 12],
    ["tunnel", under ? 40 : 0],
    ["torches", 10],
    ["sign", under ? 0 : 8],
    ["join", 5],
  ]);
  const done = {
    sighting: () => sighting(player, false),
    pyramid: () => pyramid(player),
    leaves: () => stripLeaves(player),
    tunnel: () => tunnel(player),
    torches: () => redTorches(player),
    sign: () => sign(player),
    join: () => (fakeJoin("Herobrine"), true),
  }[event]?.();
  if (done) addFear(player, 8);
  return !!done;
}

export function herobrineMajor(player) {
  return sighting(player, true) || sighting(player, false);
}

// Standing outside the window, or by your bed when you wake: it waits to be
// seen, and then it is gone.
function tickWatcher(e, s, player) {
  faceTowards(e, player.location);
  const d = distance(player.location, e.location);
  if (s.mode === "bedside" && tryRun(() => player.isSleeping)) {
    if (s.age % 80 === 0) soundAt(player, "hl.breath", e.location, 0.8, 0.8);
    if (s.age > 20 * 60) vanish(e, false);
    return;
  }
  const seen = looksAt(player, e, 32, 0.9);
  s.seen = seen ? s.seen + 1 : 0;
  if (s.seen >= 4 || d < 2.2) {
    discover("presence");
    if (d < 2.2 || Math.random() < (s.mode === "bedside" ? 0.7 : 0.45)) scare(player, " ", "§4§l. . .");
    else blackout(player);
    addFear(player, 25);
    vanish(e);
    return;
  }
  if (s.age > 20 * 30) vanish(e, false);
}

function tick(e, s, player) {
  if (s.adopted) return;
  if (s.mode === "window" || s.mode === "bedside") return tickWatcher(e, s, player);
  if (s.mode === "reveal") {
    faceTowards(e, player.location);
    if (s.scare && canSee(player, e, 32, 0.8)) {
      s.scare = false;
      discover("herobrine");
      scare(player, "§4§lHEROBRINE");
      addFear(player, 35);
    }
    if (s.age > 20 * 5 || distance(player.location, e.location) < 2) vanish(e);
    return;
  }
  const behind = s.mode === "behind";
  const seen = canSee(player, e, 96, behind ? 0.75 : 0.97);
  s.seen = seen ? s.seen + 1 : 0;
  const close = distance(player.location, e.location) < (behind ? 1.2 : 14);
  if (s.seen >= (behind ? 1 : 3) || close || s.age > 20 * (behind ? 12 : 25)) {
    if (seen) {
      discover("herobrine");
      addFear(player, behind ? 30 : 15);
      if (behind) {
        if (Math.random() < 0.5) scare(player, " ", "§f§l. .");
        else {
          blackout(player);
          sound(player, "hl.whisper", 1, 0.8);
        }
      } else if (bump("stares") >= 3) {
        // Stared down three times: something falls where he stood.
        dropPage(3, { x: e.location.x, y: e.location.y + 0.5, z: e.location.z }, player);
      }
    }
    vanish(e);
  }
}

// Hit Herobrine and he is behind you.
export function strikesBack(e, player) {
  const f = facing(player);
  tryRun(() => e.teleport({ x: player.location.x - f.x * 1.2, y: player.location.y, z: player.location.z - f.z * 1.2 }));
  faceTowards(e, player.location);
  tryRun(() => player.addEffect("blindness", 50, { showParticles: false }));
  sound(player, "mob.endermen.stare", 1, 0.5);
  scare(player);
  tryRun(() => player.applyDamage(6));
  later(25, () => vanish(e));
}

registerKind("herobrine", { tick, adopt: () => "sighting" });
