// Chapter 5: the fog. It rolls in thick, and a tall pale figure watches from
// inside it. Every time you look away he is closer; stare too long and he
// leaves, or runs. At night he knocks, and sometimes he doesn't wait to be
// let in. Live through a fog in which you saw him and a page falls at your
// feet when it lifts. He will not step into a Ward Lantern's light.

import { system, world } from "@minecraft/server";
import {
  actionBar, block, canSee, distance, faceTowards, groundAt, isNight, isUnderground, later, overworldPlayers, rand,
  randInt, sound, soundAt, tr, tryRun, visibleSpot,
} from "../lib/util.js";
import { MOB, clearKind, registerKind, spawn, tracked, trackedFor, vanish } from "../lib/actors.js";
import { active, addFear, discover, roll } from "../lib/state.js";
import { scare } from "../lib/fx.js";
import { WARD_RADIUS, nearWard } from "../items/ward.js";
import { dropPage } from "../items/pages.js";

export const fog = { active: false, until: 0, respawn: new Map(), fogged: new Set(), witnessed: new Set() };

function fogOn(player) {
  if (fog.fogged.has(player.id)) return;
  fog.fogged.add(player.id);
  tryRun(() => player.runCommand("fog @s push hl:fog hl_fog"));
}

function fogOff(player) {
  if (!fog.fogged.has(player.id)) return;
  fog.fogged.delete(player.id);
  tryRun(() => player.runCommand("fog @s remove hl_fog"));
}

export function startFog(duration = randInt(3600, 6000)) {
  if (fog.active) return false;
  fog.active = true;
  fog.until = system.currentTick + duration;
  fog.witnessed.clear();
  for (const p of overworldPlayers()) {
    fogOn(p);
    actionBar(p, tr("hl.fog.start"));
    sound(p, "hl.drone", 0.8);
    fog.respawn.set(p.id, system.currentTick + randInt(200, 500));
    addFear(p, 15);
  }
  return true;
}

export function endFog() {
  fog.active = false;
  clearKind("fogMan");
  for (const p of world.getAllPlayers()) {
    if (fog.fogged.has(p.id)) {
      actionBar(p, tr("hl.fog.end"));
      // He was here. Something is left where you stand.
      if (fog.witnessed.has(p.id)) later(40, () => dropPage(4, { x: p.location.x, y: p.location.y + 0.5, z: p.location.z }, p));
    }
    fogOff(p);
  }
  fog.witnessed.clear();
}

// Where he may stand: out of every ward's light.
function outsideWards(dimension, at) {
  return !nearWard(dimension, at, WARD_RADIUS + 2);
}

export function spawnFogMan(player, near = false) {
  const at = near ? visibleSpot(player, 16, 22, rand(-40, 40), 5) : visibleSpot(player, 34, 44, rand(-50, 50), 5);
  if (!at || !outsideWards(player.dimension, at)) return undefined;
  return spawn(MOB.fogMan, at, player, "fogMan", "watching", { major: near });
}

export function fogManChase(e, player) {
  const s = tracked.get(e.id);
  tryRun(() => e.triggerEvent("hl:chase"));
  if (s) {
    s.mode = "chasing";
    s.timer = 0;
    s.major = true;
  }
  soundAt(player, "mob.ravager.roar", e.location, 0.6, 1);
  soundAt(player, "hl.scream", e.location, 0.6, 0.8);
  actionBar(player, tr("hl.fog.chase"));
  discover("fog");
  addFear(player, 30);
}

function tick(e, s, player) {
  if (!fog.active && !s.adopted) {
    vanish(e);
    return;
  }
  const d = distance(player.location, e.location);

  if (s.mode === "chasing") {
    s.timer += 4;
    const lost = d > 48 && !canSee(player, e, 128, 0.5);
    if (s.timer > 20 * 25 || lost || nearWard(player.dimension, player.location, 6)) {
      vanish(e);
      fog.respawn.set(player.id, system.currentTick + randInt(600, 1200));
      if (lost) actionBar(player, tr("hl.fog.lost"));
    }
    return;
  }

  if (!outsideWards(e.dimension, e.location)) {
    vanish(e);
    return;
  }

  // Watching: stares from the fog. Look too long and he leaves, or comes.
  const seen = canSee(player, e, 128, 0.94);
  if (seen) {
    s.seen++;
    s.unseen = 0;
    discover("fog");
    fog.witnessed.add(player.id);
  } else {
    s.unseen++;
  }
  if (s.seen >= 8) {
    s.seen = 0;
    if (!s.adopted && (Math.random() < 0.55 || nearWard(player.dimension, player.location))) {
      vanish(e);
      fog.respawn.set(player.id, system.currentTick + randInt(300, 700));
    } else {
      fogManChase(e, player);
    }
    return;
  }
  // Every time you look away for a while, he is closer.
  if (s.unseen >= 75) {
    s.unseen = 0;
    if (d <= 12 && !nearWard(player.dimension, player.location)) {
      fogManChase(e, player);
      return;
    }
    // Somewhere on the ground about 40% nearer (not inside a tree, not in a ward's light).
    const k = Math.max(10, d * 0.6) / d;
    for (let attempt = 0; attempt < 5; attempt++) {
      const x = player.location.x + (e.location.x - player.location.x) * k + (attempt ? rand(-3, 3) : 0);
      const z = player.location.z + (e.location.z - player.location.z) * k + (attempt ? rand(-3, 3) : 0);
      const y = groundAt(player.dimension, x, z, player.location.y);
      if (y === undefined || !outsideWards(e.dimension, { x, y, z })) continue;
      tryRun(() => e.teleport({ x, y, z }));
      faceTowards(e, player.location);
      addFear(player, 6);
      break;
    }
  }
}

function findDoor(player) {
  const { x, y, z } = player.location;
  for (let dx = -5; dx <= 5; dx++)
    for (let dz = -5; dz <= 5; dz++)
      for (let dy = -2; dy <= 2; dy++) {
        const b = block(player.dimension, x + dx, y + dy, z + dz);
        if (b && b.typeId.includes("door") && !b.typeId.includes("trapdoor")) return b;
      }
  return undefined;
}

// Knocking at night. Sometimes he doesn't wait to be let in.
export function knock(player) {
  const door = findDoor(player);
  if (!door) return false;
  const at = { x: door.x + 0.5, y: door.y + 1, z: door.z + 0.5 };
  soundAt(player, "hl.knock", at, rand(0.85, 1.0), 1);
  actionBar(player, tr("hl.fog.knock"));
  addFear(player, 15);
  later(90, () => {
    if (!fog.active || Math.random() > 0.35 || trackedFor(player, "fogMan")?.mode === "chasing") return;
    if (nearWard(player.dimension, at, 12)) return; // the ward holds the door
    for (const dy of [-1, 0, 1]) {
      const b = block(player.dimension, door.x, door.y + dy, door.z);
      if (b?.typeId.includes("door") && !b.typeId.includes("trapdoor")) tryRun(() => b.setType("minecraft:air"));
    }
    soundAt(player, "mob.zombie.woodbreak", at, 0.8, 1);
    scare(player);
    // He comes in from the side of the door away from you.
    const ox = Math.sign(door.x + 0.5 - player.location.x) || 1;
    const oz = Math.sign(door.z + 0.5 - player.location.z) || 1;
    const out = Math.abs(door.x + 0.5 - player.location.x) > Math.abs(door.z + 0.5 - player.location.z)
      ? { x: door.x + 0.5 + ox * 1.5, y: door.y, z: door.z + 0.5 }
      : { x: door.x + 0.5, y: door.y, z: door.z + 0.5 + oz * 1.5 };
    const old = trackedFor(player, "fogMan");
    if (old) vanish(old.entity, false);
    const e = spawn(MOB.fogMan, out, player, "fogMan", "watching", { major: true });
    if (e) fogManChase(e, player);
  });
  return true;
}

// The fog as a whole: starts as a minor event, ends on its own. Every 100 ticks.
export function tickFog() {
  const now = system.currentTick;
  if (!fog.active) return;
  if (now >= fog.until || !active("fog")) {
    endFog();
    return;
  }
  for (const p of overworldPlayers()) {
    fogOn(p);
    const man = trackedFor(p, "fogMan");
    if (!man && now >= (fog.respawn.get(p.id) ?? 0) && !isUnderground(p)) spawnFogMan(p);
    if (isNight() && roll(0.08) && man?.mode !== "chasing") knock(p);
  }
}

// A peak: he is suddenly much closer, or at your door.
export function fogMajor(player) {
  if (!fog.active || isUnderground(player)) return false;
  if (isNight() && Math.random() < 0.4 && knock(player)) return true;
  const man = trackedFor(player, "fogMan");
  if (man) {
    if (man.mode === "chasing") return false;
    vanish(man.entity, false);
  }
  return !!spawnFogMan(player, true);
}

export function fogMinor(player) {
  if (fog.active || isUnderground(player)) return false;
  return startFog();
}

export function onPlayerSpawn(player) {
  tryRun(() => player.runCommand("fog @s remove hl_fog"));
}

registerKind("fogMan", {
  tick,
  adopt: (e) => (tryRun(() => e.matches({ families: ["hl_chasing"] })) ? "chasing" : "watching"),
});
