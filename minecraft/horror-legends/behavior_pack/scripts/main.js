// Horror Legends — gameplay scripts.
//
// A horror director that slowly turns an ordinary world against the player,
// in the spirit of the classic Java horror mods and creepypastas:
//
//   Sounds (day 0+)       footsteps behind you, doors, chests, a creeper hiss
//                         that isn't there
//   Cave Dweller (day 1+) tension builds the longer you stay underground;
//                         it stalks, flees from light, and eventually hunts
//   Herobrine (day 2+)    white eyes at the edge of the render distance, sand
//                         pyramids, leafless trees, 2x2 tunnels, red torches,
//                         signs, "Herobrine joined the game"
//   The Man From The Fog  a thick fog rolls in; a tall pale figure watches,
//   (day 4+)              creeps closer whenever you look away, knocks on
//                         doors, and sometimes runs at you
//   Null (day 6+)         something broken in the code: it hides in the corner
//                         of your eye, writes in chat, corrupts blocks into the
//                         missing texture, and fakes the game freezing
//
// Start days scale with the intensity setting. Every threat can be switched
// off, and summoned for testing, from the Survivor's Journal.

import { BlockPermutation, ItemStack, system, world } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

const OVERWORLD = "minecraft:overworld";

const MOB = {
  herobrine: "hl:herobrine",
  null: "hl:null",
  fogMan: "hl:fog_man",
  dweller: "hl:cave_dweller",
};
const KIND_OF = {
  [MOB.herobrine]: "herobrine",
  [MOB.null]: "null",
  [MOB.fogMan]: "fogMan",
  [MOB.dweller]: "dweller",
};
const ITEM = { journal: "hl:journal", flashlight: "hl:flashlight" };
const CORRUPTED = "hl:corrupted_block";

const THREATS = ["ambience", "caves", "herobrine", "fog", "null"];
const START_DAY = { ambience: 0, caves: 1, herobrine: 2, fog: 4, null: 6 };
const INTENSITY = [
  { key: "low", days: 2, chance: 0.5 },
  { key: "normal", days: 1, chance: 1 },
  { key: "high", days: 0.5, chance: 1.6 },
  { key: "nightmare", days: 0, chance: 2.5 },
];

const PASSABLE = /air|leaves|grass|fern|flower|vine|snow_layer|bush|sapling|mushroom|dandelion|poppy|tulip|orchid|allium|bluet|daisy|cornflower|lily|berry|moss_carpet|pink_petals|torch/;
const NATURAL_STONE = /^minecraft:(stone|deepslate|granite|diorite|andesite|tuff|dirt|gravel|calcite|smooth_basalt)$/;
const PROTECTED = /chest|barrel|shulker|furnace|smoker|sign|bed|door|hopper|dispenser|dropper|portal|bedrock|spawner|lectern|anvil|beacon|command|structure|jigsaw|water|lava|torch|lantern|campfire|brewing|enchanting|ender|frame|pot|banner|head|skull|crafter|bell|rail|redstone|lever|button|pressure|piston|observer|comparator|repeater|sculk|vault|trial/;

// id -> { entity, kind, playerId, mode, seen, unseen, age, timer }
const tracked = new Map();
const tension = new Map();
const fogged = new Set();
const cooldowns = new Map();
const fog = { active: false, until: 0, respawn: new Map() };

// --------------------------------------------------------------------------- //
// Helpers
// --------------------------------------------------------------------------- //

function valid(entity) {
  if (!entity) return false;
  return typeof entity.isValid === "function" ? entity.isValid() : entity.isValid;
}

function tryRun(fn) {
  try {
    return fn();
  } catch {
    return undefined;
  }
}

function later(ticks, fn) {
  system.runTimeout(() => tryRun(fn), ticks);
}

function tr(key, ...args) {
  return { rawtext: [{ translate: key, with: args.map(String) }] };
}

function rand(min, max) {
  return min + Math.random() * (max - min);
}

function randInt(min, max) {
  return Math.floor(rand(min, max + 1));
}

function pick(weights) {
  const total = weights.reduce((sum, [, w]) => sum + w, 0);
  let r = Math.random() * total;
  for (const [value, w] of weights) {
    r -= w;
    if (r <= 0) return value;
  }
  return weights[weights.length - 1][0];
}

function sub(a, b) {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}

function length(v) {
  return Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
}

function distance(a, b) {
  return length(sub(a, b));
}

function onCooldown(key, ticks) {
  const now = system.currentTick;
  if (now - (cooldowns.get(key) ?? -Infinity) < ticks) return true;
  cooldowns.set(key, now);
  return false;
}

function isNight() {
  const t = world.getTimeOfDay();
  return t >= 13000 && t < 23000;
}

function overworldPlayers() {
  return world.getAllPlayers().filter((p) => p.dimension.id === OVERWORLD);
}

// Horizontal unit vector the player is facing.
function facing(player) {
  const d = player.getViewDirection();
  const len = Math.hypot(d.x, d.z) || 1;
  return { x: d.x / len, z: d.z / len };
}

// A point `dist` blocks from the player, `deg` degrees off their line of
// sight (0 = straight ahead, 180 = directly behind).
function offsetFromView(player, dist, deg) {
  const f = facing(player);
  const a = (deg * Math.PI) / 180;
  return {
    x: player.location.x + (f.x * Math.cos(a) - f.z * Math.sin(a)) * dist,
    z: player.location.z + (f.x * Math.sin(a) + f.z * Math.cos(a)) * dist,
  };
}

function faceTowards(entity, target) {
  const dx = target.x - entity.location.x;
  const dz = target.z - entity.location.z;
  tryRun(() => entity.setRotation({ x: 0, y: (-Math.atan2(dx, dz) * 180) / Math.PI }));
}

// True when `target` is inside the player's view cone with nothing solid in
// between.
function canSee(player, target, range, cone) {
  const eye = player.getHeadLocation();
  const aim = { x: target.location.x, y: target.location.y + 1.2, z: target.location.z };
  const toTarget = sub(aim, eye);
  const dist = length(toTarget);
  if (dist > range || dist < 0.01) return false;

  const dir = { x: toTarget.x / dist, y: toTarget.y / dist, z: toTarget.z / dist };
  const view = player.getViewDirection();
  if (dir.x * view.x + dir.y * view.y + dir.z * view.z < cone) return false;

  const hit = tryRun(() =>
    player.dimension.getBlockFromRay(eye, dir, {
      maxDistance: dist,
      includeLiquidBlocks: false,
      includePassableBlocks: false,
    })
  );
  return !hit;
}

function block(dimension, x, y, z) {
  return tryRun(() => dimension.getBlock({ x: Math.floor(x), y: Math.floor(y), z: Math.floor(z) }));
}

function place(dimension, x, y, z, type) {
  tryRun(() => block(dimension, x, y, z)?.setType(type));
}

// Standing height on the ground at (x, z), searching down from `fromY`.
// Undefined when the column is a tree trunk, water, or not loaded.
function groundAt(dimension, x, z, fromY) {
  for (let y = Math.floor(fromY) + 12; y > Math.floor(fromY) - 24; y--) {
    const b = block(dimension, x, y, z);
    if (!b) return undefined;
    if (b.isAir || PASSABLE.test(b.typeId)) continue;
    if (/log|water|lava/.test(b.typeId)) return undefined;
    return y + 1;
  }
  return undefined;
}

// Solid rock overhead: a cave, a mine, a basement.
function isUnderground(player) {
  if (player.dimension.id !== OVERWORLD || player.location.y > 62) return false;
  const hit = tryRun(() =>
    player.dimension.getBlockFromRay(player.getHeadLocation(), { x: 0, y: 1, z: 0 }, {
      maxDistance: 64,
      includeLiquidBlocks: false,
      includePassableBlocks: false,
    })
  );
  return !!hit;
}

// An air pocket with a floor near the player's height: somewhere in the cave
// a crawling thing could be.
function caveSpot(player, minDist, maxDist, deg, spread) {
  for (let attempt = 0; attempt < 16; attempt++) {
    const p = offsetFromView(player, rand(minDist, maxDist), deg + rand(-spread, spread));
    for (const dy of [0, -1, 1, -2, 2, -3, 3, -4, 4]) {
      const y = Math.floor(player.location.y) + dy;
      const here = block(player.dimension, p.x, y, p.z);
      const below = block(player.dimension, p.x, y - 1, p.z);
      if (here?.isAir && below && !below.isAir && !/water|lava/.test(below.typeId)) {
        return { x: Math.floor(p.x) + 0.5, y, z: Math.floor(p.z) + 0.5 };
      }
    }
  }
  return undefined;
}

// Somewhere on the ground at the given distance and angle from the player's view.
function groundSpot(player, minDist, maxDist, deg, spread) {
  for (let attempt = 0; attempt < 8; attempt++) {
    const p = offsetFromView(player, rand(minDist, maxDist), deg + rand(-spread, spread));
    const y = groundAt(player.dimension, p.x, p.z, player.location.y);
    if (y !== undefined) return { x: Math.floor(p.x) + 0.5, y, z: Math.floor(p.z) + 0.5 };
  }
  return undefined;
}

// A clear line from the player's eyes to someone standing at `at`.
function inSight(player, at) {
  const eye = player.getHeadLocation();
  const d = { x: at.x - eye.x, y: at.y + 1.4 - eye.y, z: at.z - eye.z };
  const dist = length(d);
  if (dist < 0.01) return true;
  const hit = tryRun(() =>
    player.dimension.getBlockFromRay(eye, { x: d.x / dist, y: d.y / dist, z: d.z / dist }, {
      maxDistance: dist,
      includeLiquidBlocks: false,
      includePassableBlocks: false,
    })
  );
  return !hit;
}

// Like groundSpot, but somewhere the player could actually see.
function visibleSpot(player, minDist, maxDist, deg, spread) {
  let fallback;
  for (let attempt = 0; attempt < 6; attempt++) {
    const at = groundSpot(player, minDist, maxDist, deg, spread);
    if (!at) continue;
    if (inSight(player, at)) return at;
    fallback ??= at;
  }
  return fallback;
}

function soundAt(player, sound, location, pitch = 1, volume = 1) {
  tryRun(() => player.playSound(sound, { location, pitch, volume }));
}

function soundBehind(player, sound, dist, pitch = 1, volume = 1) {
  const p = offsetFromView(player, dist, 180 + rand(-30, 30));
  soundAt(player, sound, { x: p.x, y: player.location.y + 1, z: p.z }, pitch, volume);
}

// --------------------------------------------------------------------------- //
// Settings and progression (world dynamic properties)
// --------------------------------------------------------------------------- //

function worldProp(key, fallback) {
  const value = tryRun(() => world.getDynamicProperty(key));
  return value === undefined ? fallback : value;
}

function setWorldProp(key, value) {
  tryRun(() => world.setDynamicProperty(key, value));
}

function intensityIndex() {
  const i = worldProp("hl:intensity", 1);
  return INTENSITY[i] ? i : 1;
}

function intensity() {
  return INTENSITY[intensityIndex()];
}

function enabled(threat) {
  return worldProp(`hl:on_${threat}`, true);
}

function hauntDay() {
  let start = worldProp("hl:start_day", undefined);
  if (start === undefined) {
    start = world.getDay();
    setWorldProp("hl:start_day", start);
  }
  return world.getDay() - start;
}

function unlockDay(threat) {
  return Math.ceil(START_DAY[threat] * intensity().days);
}

function active(threat) {
  return enabled(threat) && hauntDay() >= unlockDay(threat);
}

function roll(p) {
  return Math.random() < p * intensity().chance;
}

// --------------------------------------------------------------------------- //
// Tracked apparitions
// --------------------------------------------------------------------------- //

function spawn(type, location, player, kind, mode) {
  const entity = tryRun(() => player.dimension.spawnEntity(type, location));
  if (!entity) return undefined;
  faceTowards(entity, player.location);
  tryRun(() => entity.setDynamicProperty("hl:director", true));
  tracked.set(entity.id, { entity, kind, playerId: player.id, mode, seen: 0, unseen: 0, age: 0, timer: 0 });
  return entity;
}

function vanish(entity, smoke = true) {
  if (smoke) {
    const at = entity.location;
    for (let i = 0; i < 6; i++) {
      tryRun(() =>
        entity.dimension.spawnParticle("minecraft:basic_smoke_particle", {
          x: at.x + rand(-0.4, 0.4),
          y: at.y + rand(0.2, 1.8),
          z: at.z + rand(-0.4, 0.4),
        })
      );
    }
  }
  tracked.delete(entity.id);
  tryRun(() => entity.triggerEvent("hl:vanish"));
}

function trackedFor(player, kind) {
  for (const state of tracked.values()) {
    if (state.kind === kind && state.playerId === player.id && valid(state.entity)) return state;
  }
  return undefined;
}

// Entities from spawn eggs are handed to the nearest player. Apparitions left
// over from an earlier session are not: they vanish.
function adopt(entity, players) {
  if (tryRun(() => entity.getDynamicProperty("hl:director"))) return undefined;
  let nearest;
  let best = 128;
  for (const p of players) {
    const d = distance(p.location, entity.location);
    if (d < best) {
      best = d;
      nearest = p;
    }
  }
  if (!nearest) return undefined;
  const kind = KIND_OF[entity.typeId];
  const mode = {
    herobrine: "sighting",
    null: "peripheral",
    fogMan: entity.matches({ families: ["hl_chasing"] }) ? "chasing" : "watching",
    dweller: "stalking",
  }[kind];
  const state = { entity, kind, playerId: nearest.id, mode, seen: 0, unseen: 0, age: 0, timer: 0, adopted: true };
  tracked.set(entity.id, state);
  return state;
}

function clearKind(kind) {
  for (const state of [...tracked.values()]) {
    if (state.kind === kind && valid(state.entity)) vanish(state.entity, false);
  }
}

// --------------------------------------------------------------------------- //
// Sounds: not everything you hear is there
// --------------------------------------------------------------------------- //

function ambience(player) {
  const under = isUnderground(player);
  const event = pick([
    ["footsteps", 5],
    ["door", 3],
    ["chest", 2],
    ["cave", 3],
    ["hiss", 1],
    ["mining", under ? 3 : 0],
    ["whisper", 1],
  ]);
  switch (event) {
    case "footsteps": {
      const sound = under ? "step.stone" : "step.grass";
      for (let i = 0; i < 5; i++) later(i * 7, () => soundBehind(player, sound, 4 - i * 0.5, 0.9, 0.8));
      break;
    }
    case "door": {
      const p = offsetFromView(player, rand(6, 10), rand(90, 270));
      const at = { x: p.x, y: player.location.y + 1, z: p.z };
      soundAt(player, "random.door_open", at);
      later(randInt(20, 50), () => soundAt(player, "random.door_close", at));
      break;
    }
    case "chest": {
      const p = offsetFromView(player, rand(5, 9), rand(90, 270));
      const at = { x: p.x, y: player.location.y + 1, z: p.z };
      soundAt(player, "random.chestopen", at, 1, 0.7);
      later(randInt(30, 60), () => soundAt(player, "random.chestclosed", at, 1, 0.7));
      break;
    }
    case "cave":
      soundBehind(player, "ambient.cave", rand(6, 14), rand(0.8, 1.0), 1);
      break;
    case "hiss":
      soundBehind(player, "random.fuse", 1.5, 0.5, 1);
      break;
    case "mining":
      for (let i = 0; i < 4; i++) later(i * 12, () => soundBehind(player, "dig.stone", 9, 0.9, 0.8));
      break;
    case "whisper":
      player.sendMessage(tr("hl.ambience.whisper", player.name));
      break;
  }
}

// --------------------------------------------------------------------------- //
// Herobrine
// --------------------------------------------------------------------------- //

function herobrineSighting(player) {
  if (trackedFor(player, "herobrine")) return false;
  if (Math.random() < 0.15) {
    // Right behind you, for as long as you don't turn around.
    const at = groundSpot(player, 2.5, 3.5, 180, 20);
    if (at) return !!spawn(MOB.herobrine, at, player, "herobrine", "behind");
  }
  const side = Math.random() < 0.5 ? -1 : 1;
  const at = visibleSpot(player, 28, 44, side * rand(15, 35), 5);
  if (!at) return false;
  const e = spawn(MOB.herobrine, at, player, "herobrine", "sighting");
  if (e && Math.random() < 0.3) soundAt(player, "ambient.weather.thunder", at, 0.8, 0.6);
  return !!e;
}

function sandPyramid(player) {
  const dimension = player.dimension;
  for (let attempt = 0; attempt < 6; attempt++) {
    const p = offsetFromView(player, rand(20, 32), rand(0, 360));
    const gy = groundAt(dimension, p.x, p.z, player.location.y);
    if (gy === undefined) continue;
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
    if (b?.typeId.includes("leaves")) origin = { x: b.x, y: b.y, z: b.z };
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

function digTunnel(player) {
  const dimension = player.dimension;
  const [dx, dz] = [[1, 0], [-1, 0], [0, 1], [0, -1]][randInt(0, 3)];
  const px = -dz;
  const pz = dx;
  const side = Math.random() < 0.5 ? -1 : 1;
  const sx = Math.floor(player.location.x) + dx * randInt(3, 6) + px * side * randInt(3, 5);
  const sz = Math.floor(player.location.z) + dz * randInt(3, 6) + pz * side * randInt(3, 5);
  const sy = Math.floor(player.location.y);
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

function redTorches(player) {
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
    player.playSound("random.fizz", { pitch: 0.6 });
    player.onScreenDisplay.setActionBar(tr("hl.hb.torches"));
  }
  return changed > 0;
}

function leaveSign(player) {
  const at = groundSpot(player, 5, 9, 0, 35);
  if (!at) return false;
  const dx = player.location.x - at.x;
  const dz = player.location.z - at.z;
  const direction = ((Math.round((-Math.atan2(dx, dz) * 180) / Math.PI / 22.5) % 16) + 16) % 16;
  const perm = tryRun(() => BlockPermutation.resolve("minecraft:standing_sign", { ground_sign_direction: direction }));
  const b = block(player.dimension, at.x, at.y, at.z);
  if (!b || !perm || !tryRun(() => (b.setPermutation(perm), true))) return false;
  later(1, () => b.getComponent("minecraft:sign")?.setText({ translate: `hl.sign.${randInt(1, 6)}` }));
  return true;
}

function fakeJoin(name, recipient) {
  const send = (key) => {
    const msg = { rawtext: [{ text: "§e" }, { translate: key, with: [name] }] };
    if (recipient) recipient.sendMessage(msg);
    else world.sendMessage(msg);
  };
  send("multiplayer.player.joined");
  later(randInt(60, 180), () => send("multiplayer.player.left"));
}

function herobrineEvent(player) {
  const under = isUnderground(player);
  const event = pick([
    ["sighting", under ? 0 : 45],
    ["pyramid", under ? 0 : 12],
    ["leaves", under ? 0 : 12],
    ["tunnel", under ? 40 : 0],
    ["torches", 10],
    ["sign", under ? 0 : 8],
    ["join", 5],
  ]);
  switch (event) {
    case "sighting":
      herobrineSighting(player);
      break;
    case "pyramid":
      sandPyramid(player);
      break;
    case "leaves":
      stripLeaves(player);
      break;
    case "tunnel":
      digTunnel(player);
      break;
    case "torches":
      redTorches(player);
      break;
    case "sign":
      leaveSign(player);
      break;
    case "join":
      fakeJoin("Herobrine");
      break;
  }
}

function tickHerobrine(e, s, player) {
  if (s.adopted) return;
  const behind = s.mode === "behind";
  const seen = canSee(player, e, 96, behind ? 0.75 : 0.97);
  s.seen = seen ? s.seen + 1 : 0;
  const close = distance(player.location, e.location) < (behind ? 1.2 : 14);
  if (s.seen >= (behind ? 1 : 3) || close || s.age > 20 * (behind ? 12 : 25)) {
    if (behind && seen) {
      player.playSound("ambient.cave", { pitch: 0.7 });
      player.onScreenDisplay.setTitle(" ", { subtitle: "§f§l. .", fadeInDuration: 0, stayDuration: 10, fadeOutDuration: 10 });
    }
    vanish(e);
  }
}

// Hit Herobrine and he is behind you.
function herobrineStrikesBack(e, player) {
  const f = facing(player);
  const behind = { x: player.location.x - f.x * 1.2, y: player.location.y, z: player.location.z - f.z * 1.2 };
  tryRun(() => e.teleport(behind));
  faceTowards(e, player.location);
  tryRun(() => player.addEffect("blindness", 50, { showParticles: false }));
  player.playSound("mob.endermen.stare", { pitch: 0.5 });
  tryRun(() => player.applyDamage(6));
  later(25, () => valid(e) && vanish(e));
}

// --------------------------------------------------------------------------- //
// The Man From The Fog
// --------------------------------------------------------------------------- //

function fogOn(player) {
  if (fogged.has(player.id)) return;
  fogged.add(player.id);
  tryRun(() => player.runCommand("fog @s push hl:fog hl_fog"));
}

function fogOff(player) {
  if (!fogged.has(player.id)) return;
  fogged.delete(player.id);
  tryRun(() => player.runCommand("fog @s remove hl_fog"));
}

function startFog(duration = randInt(3600, 6000)) {
  fog.active = true;
  fog.until = system.currentTick + duration;
  for (const p of overworldPlayers()) {
    fogOn(p);
    p.onScreenDisplay.setActionBar(tr("hl.fog.start"));
    fog.respawn.set(p.id, system.currentTick + randInt(200, 500));
  }
}

function endFog() {
  fog.active = false;
  clearKind("fogMan");
  for (const p of world.getAllPlayers()) {
    if (fogged.has(p.id)) p.onScreenDisplay.setActionBar(tr("hl.fog.end"));
    fogOff(p);
  }
}

function spawnFogMan(player) {
  const at = visibleSpot(player, 34, 44, rand(-50, 50), 5);
  return at ? spawn(MOB.fogMan, at, player, "fogMan", "watching") : undefined;
}

function fogManChase(e, s, player) {
  tryRun(() => e.triggerEvent("hl:chase"));
  s.mode = "chasing";
  s.timer = 0;
  soundAt(player, "mob.ravager.roar", e.location, 0.6, 1);
  player.onScreenDisplay.setActionBar(tr("hl.fog.chase"));
}

function tickFogMan(e, s, player) {
  if (!fog.active && !s.adopted) {
    vanish(e);
    return;
  }
  const d = distance(player.location, e.location);

  if (s.mode === "chasing") {
    s.timer += 4;
    const lost = d > 48 && !canSee(player, e, 128, 0.5);
    if (s.timer > 20 * 25 || lost) {
      vanish(e);
      fog.respawn.set(player.id, system.currentTick + randInt(600, 1200));
      if (lost) player.onScreenDisplay.setActionBar(tr("hl.fog.lost"));
    }
    return;
  }

  // Watching: stares from the fog. Look too long and he leaves, or comes.
  const seen = canSee(player, e, 128, 0.94);
  if (seen) {
    s.seen++;
    s.unseen = 0;
  } else {
    s.unseen++;
  }
  if (s.seen >= 8) {
    s.seen = 0;
    if (!s.adopted && Math.random() < 0.55) {
      vanish(e);
      fog.respawn.set(player.id, system.currentTick + randInt(300, 700));
    } else {
      fogManChase(e, s, player);
    }
    return;
  }
  // Every time you look away for a while, he is closer.
  if (s.unseen >= 75) {
    s.unseen = 0;
    if (d <= 12) {
      fogManChase(e, s, player);
      return;
    }
    const k = Math.max(10, d * 0.6) / d;
    const x = player.location.x + (e.location.x - player.location.x) * k;
    const z = player.location.z + (e.location.z - player.location.z) * k;
    const y = groundAt(player.dimension, x, z, player.location.y);
    if (y !== undefined) {
      tryRun(() => e.teleport({ x, y, z }));
      faceTowards(e, player.location);
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
function knock(player) {
  const door = findDoor(player);
  if (!door) return false;
  const at = { x: door.x + 0.5, y: door.y + 1, z: door.z + 0.5 };
  for (let i = 0; i < 3; i++) later(i * 14, () => soundAt(player, "mob.zombie.wood", at, 1.2, 0.6));
  player.onScreenDisplay.setActionBar(tr("hl.fog.knock"));
  later(90, () => {
    if (!fog.active || Math.random() > 0.35 || trackedFor(player, "fogMan")?.mode === "chasing") return;
    for (const dy of [-1, 0, 1]) {
      const b = block(player.dimension, door.x, door.y + dy, door.z);
      if (b?.typeId.includes("door") && !b.typeId.includes("trapdoor")) tryRun(() => b.setType("minecraft:air"));
    }
    soundAt(player, "mob.zombie.woodbreak", at, 0.8, 1);
    // He comes in from the side of the door away from you.
    const ox = Math.sign(door.x + 0.5 - player.location.x) || 1;
    const oz = Math.sign(door.z + 0.5 - player.location.z) || 1;
    const out = Math.abs(door.x + 0.5 - player.location.x) > Math.abs(door.z + 0.5 - player.location.z)
      ? { x: door.x + 0.5 + ox * 1.5, y: door.y, z: door.z + 0.5 }
      : { x: door.x + 0.5, y: door.y, z: door.z + 0.5 + oz * 1.5 };
    const old = trackedFor(player, "fogMan");
    if (old) vanish(old.entity, false);
    const e = spawn(MOB.fogMan, out, player, "fogMan", "watching");
    const s = e && tracked.get(e.id);
    if (s) fogManChase(e, s, player);
  });
  return true;
}

function fogTick() {
  const now = system.currentTick;
  if (fog.active) {
    if (now >= fog.until) {
      endFog();
      return;
    }
    for (const p of overworldPlayers()) {
      fogOn(p);
      const man = trackedFor(p, "fogMan");
      if (!man && now >= (fog.respawn.get(p.id) ?? 0) && !isUnderground(p)) spawnFogMan(p);
      if (isNight() && roll(0.08) && man?.mode !== "chasing") knock(p);
    }
    return;
  }
  if (active("fog") && overworldPlayers().length && roll(isNight() ? 0.012 : 0.003)) startFog();
}

// --------------------------------------------------------------------------- //
// The Cave Dweller
// --------------------------------------------------------------------------- //

function spawnDweller(player, hunting) {
  if (trackedFor(player, "dweller")) return undefined;
  const at = caveSpot(player, 14, 24, 180, 70) ?? caveSpot(player, 10, 20, 0, 180);
  if (!at) return undefined;
  const e = spawn(MOB.dweller, at, player, "dweller", "stalking");
  if (e && hunting) dwellerChase(e, tracked.get(e.id), player);
  return e;
}

function dwellerChase(e, s, player) {
  tryRun(() => e.triggerEvent("hl:chase"));
  s.mode = "chasing";
  s.timer = 0;
  soundAt(player, "mob.warden.roar", e.location, 1.3, 1);
  player.onScreenDisplay.setActionBar(tr("hl.cave.chase"));
}

function dwellerFlee(e, s, player) {
  tryRun(() => e.triggerEvent("hl:flee"));
  s.mode = "fleeing";
  s.timer = 0;
  soundAt(player, "mob.spider.say", e.location, 1.6, 1);
  tension.set(player.id, Math.max(0, (tension.get(player.id) ?? 0) - 25));
}

function tickDweller(e, s, player) {
  const under = isUnderground(player);
  s.surface = under ? 0 : (s.surface ?? 0) + 4;
  if (s.surface > (s.mode === "chasing" ? 200 : 100)) {
    vanish(e, false);
    return;
  }

  if (s.mode === "fleeing") {
    s.timer += 4;
    if (s.timer > 60) vanish(e, false);
    return;
  }
  if (s.mode === "chasing") {
    s.timer += 4;
    if (s.timer % 100 === 0) soundAt(player, "mob.spider.say", e.location, 0.5, 1);
    if (s.timer > 20 * 30) {
      vanish(e, false);
      tension.set(player.id, 0);
    }
    return;
  }

  // Stalking: it peeks at you. Catch it looking and it scurries away.
  const seen = canSee(player, e, 40, 0.9);
  if (seen) {
    s.seen++;
    s.unseen = 0;
  } else {
    s.unseen++;
  }
  if (s.seen >= 3) {
    dwellerFlee(e, s, player);
    return;
  }
  if (!seen && s.unseen % 15 === 0 && Math.random() < 0.3) {
    for (let i = 0; i < 4; i++) later(i * 3, () => soundAt(player, "step.stone", e.location, 1.6, 0.7));
  }
  if (s.unseen >= 150) {
    s.unseen = 0;
    if ((tension.get(player.id) ?? 0) >= 80) {
      dwellerChase(e, s, player);
      return;
    }
    const at = caveSpot(player, 8, 12, 180, 60);
    if (at) {
      tryRun(() => e.teleport(at));
      faceTowards(e, player.location);
    }
  }
}

function caveTick(player) {
  if (!active("caves")) return;
  const under = isUnderground(player);
  let t = tension.get(player.id) ?? 0;
  t = under ? Math.min(100, t + 2 * intensity().chance) : Math.max(0, t - 6);
  tension.set(player.id, t);
  if (!under) return;

  if (t >= 20 && roll(0.12)) soundBehind(player, "ambient.cave", rand(8, 16), rand(0.7, 1.0), 1);
  if (t >= 40 && roll(0.12)) {
    // Something small and fast, running past in the dark.
    for (let i = 0; i < 6; i++) later(i * 3, () => soundBehind(player, "step.stone", 10 - i, 1.5, 0.8));
  }
  if (t >= 60 && roll(0.15)) spawnDweller(player, t >= 90);
}

// --------------------------------------------------------------------------- //
// Null
// --------------------------------------------------------------------------- //

function glitch(player) {
  tryRun(() => player.runCommand("camera @s fade time 0.05 0.35 0.1 color 0 0 0"));
  player.onScreenDisplay.setTitle("§k||||||||||||", { fadeInDuration: 0, stayDuration: 6, fadeOutDuration: 2 });
  player.playSound("mob.endermen.portal", { pitch: 0.3 });
}

function nullAppears(player, mode = "peripheral") {
  if (trackedFor(player, "null")) return false;
  const at =
    mode === "behind"
      ? groundSpot(player, 2, 3, 180, 15)
      : groundSpot(player, 10, 18, (Math.random() < 0.5 ? -1 : 1) * rand(55, 80), 5);
  return !!(at && spawn(MOB.null, at, player, "null", mode));
}

function nullJumpscare(e, player) {
  vanish(e, false);
  glitch(player);
  player.onScreenDisplay.setTitle("§4§knull", { subtitle: "§8null", fadeInDuration: 0, stayDuration: 20, fadeOutDuration: 10 });
  player.playSound("mob.endermen.scream", { pitch: 0.5 });
  tryRun(() => player.addEffect("nausea", 100, { showParticles: false }));
  tryRun(() => player.applyDamage(4));
}

function tickNull(e, s, player) {
  if (s.adopted) return;
  const d = distance(player.location, e.location);
  if (s.mode === "peripheral") {
    if (canSee(player, e, 64, 0.9)) {
      glitch(player);
      vanish(e, false);
      return;
    }
    if (++s.unseen >= 40) {
      // It gave up waiting to be noticed.
      s.mode = "behind";
      s.unseen = 0;
    }
    return;
  }

  // Behind you. Always behind you.
  if (d > 3.5) {
    const f = facing(player);
    tryRun(() => e.teleport({ x: player.location.x - f.x * 2, y: player.location.y, z: player.location.z - f.z * 2 }));
    faceTowards(e, player.location);
  }
  if (canSee(player, e, 8, 0.6)) {
    nullJumpscare(e, player);
    return;
  }
  if (++s.unseen >= 75) vanish(e, false);
}

function corruptionList() {
  const raw = worldProp("hl:corrupt", "");
  return (raw && tryRun(() => JSON.parse(raw))) || [];
}

function saveCorruption(list) {
  setWorldProp("hl:corrupt", JSON.stringify(list));
}

// Blocks in front of the player turn into the missing texture for a while.
function corrupt(player) {
  const list = corruptionList();
  if (list.length > 60) return false;
  const now = world.getAbsoluteTime();
  const target = randInt(4, 9);
  let done = 0;
  for (let attempt = 0; attempt < 50 && done < target; attempt++) {
    const p = offsetFromView(player, rand(3, 10), rand(-50, 50));
    const b = block(player.dimension, p.x, player.location.y + randInt(-2, 2), p.z);
    if (!b || b.isAir || b.typeId === CORRUPTED || PROTECTED.test(b.typeId)) continue;
    const above = block(player.dimension, b.x, b.y + 1, b.z);
    if (!above?.isAir) continue;
    const type = b.typeId;
    const states = tryRun(() => b.permutation.getAllStates()) ?? {};
    if (!tryRun(() => (b.setType(CORRUPTED), true))) continue;
    list.push({ x: b.x, y: b.y, z: b.z, type, st: states, at: now + randInt(1200, 2400) });
    done++;
  }
  if (done) saveCorruption(list);
  return done > 0;
}

function restoreCorruption(force = false) {
  const list = corruptionList();
  if (!list.length) return;
  const now = world.getAbsoluteTime();
  const dimension = world.getDimension(OVERWORLD);
  const keep = [];
  for (const entry of list) {
    if (!force && entry.at > now) {
      keep.push(entry);
      continue;
    }
    const b = block(dimension, entry.x, entry.y, entry.z);
    if (!b) {
      keep.push(entry); // not loaded: try again later
      continue;
    }
    if (b.typeId !== CORRUPTED) continue; // already broken
    const perm = tryRun(() => BlockPermutation.resolve(entry.type, entry.st));
    if (!(perm && tryRun(() => (b.setPermutation(perm), true)))) tryRun(() => b.setType(entry.type));
  }
  if (keep.length !== list.length) saveCorruption(keep);
}

function fakeCrash(player) {
  if (onCooldown(`crash:${player.id}`, 20 * 120)) return false;
  new ActionFormData()
    .title(tr("hl.null.crash.title"))
    .body(tr("hl.null.crash.body"))
    .button(tr("hl.null.crash.wait"))
    .button(tr("hl.null.crash.close"))
    .show(player)
    .then(() => {
      player.sendMessage({ rawtext: [{ text: "§f<null>§r " }, { translate: "hl.null.chat.1" }] });
      later(40, () => nullAppears(player, "behind"));
    })
    .catch(() => {});
  return true;
}

function nullEvent(player) {
  const event = pick([
    ["appear", 30],
    ["corrupt", 25],
    ["chat", 20],
    ["glitch", 12],
    ["crash", 5],
    ["join", 8],
  ]);
  switch (event) {
    case "appear":
      nullAppears(player);
      break;
    case "corrupt":
      if (corrupt(player)) player.playSound("mob.endermen.portal", { pitch: 0.4, volume: 0.6 });
      break;
    case "chat":
      player.sendMessage({ rawtext: [{ text: "§f<null>§r " }, { translate: `hl.null.chat.${randInt(1, 6)}`, with: [player.name] }] });
      break;
    case "glitch":
      glitch(player);
      break;
    case "crash":
      fakeCrash(player);
      break;
    case "join":
      fakeJoin("null", player);
      break;
  }
}

// --------------------------------------------------------------------------- //
// The director
// --------------------------------------------------------------------------- //

const TICKERS = { herobrine: tickHerobrine, null: tickNull, fogMan: tickFogMan, dweller: tickDweller };

system.runInterval(() => {
  const dimension = world.getDimension(OVERWORLD);
  const players = overworldPlayers();
  const alive = new Set();
  for (const type of Object.keys(KIND_OF)) {
    for (const e of dimension.getEntities({ type })) {
      if (!valid(e)) continue;
      alive.add(e.id);
      const s = tracked.get(e.id) ?? adopt(e, players);
      if (!s) {
        vanish(e, false);
        continue;
      }
      s.entity = e;
      const player = players.find((p) => p.id === s.playerId);
      if (!player) {
        vanish(e, false);
        continue;
      }
      s.age += 4;
      tryRun(() => TICKERS[s.kind](e, s, player));
    }
  }
  for (const id of [...tracked.keys()]) if (!alive.has(id)) tracked.delete(id);
}, 4);

system.runInterval(() => {
  hauntDay();
  tryRun(fogTick);
  tryRun(() => restoreCorruption());
  const night = isNight();
  for (const player of overworldPlayers()) {
    tryRun(() => {
      if (active("ambience") && roll(night ? 0.1 : 0.05)) ambience(player);
      caveTick(player);
      if (active("herobrine") && roll(night ? 0.045 : 0.03)) herobrineEvent(player);
      if (active("null") && roll(0.03)) nullEvent(player);
    });
  }
}, 100);

world.afterEvents.entityHitEntity.subscribe(({ damagingEntity: attacker, hitEntity: target }) => {
  if (attacker?.typeId !== "minecraft:player") return;
  const s = tracked.get(target.id);
  if (!s) return;
  tryRun(() => {
    if (s.kind === "herobrine") herobrineStrikesBack(target, attacker);
    else if (s.kind === "null") nullJumpscare(target, attacker);
    else if (s.kind === "fogMan" && s.mode === "watching") fogManChase(target, s, attacker);
    else if (s.kind === "dweller" && s.mode === "stalking") dwellerChase(target, s, attacker);
  });
});

// Badly hurt, the Cave Dweller retreats into the dark.
world.afterEvents.entityHurt.subscribe(({ hurtEntity }) => {
  if (hurtEntity.typeId !== MOB.dweller) return;
  const s = tracked.get(hurtEntity.id);
  const health = tryRun(() => hurtEntity.getComponent("minecraft:health"));
  if (!s || !health || s.mode === "fleeing") return;
  const player = world.getAllPlayers().find((p) => p.id === s.playerId);
  if (player && health.currentValue < health.effectiveMax * 0.3) dwellerFlee(hurtEntity, s, player);
});

world.afterEvents.entityDie.subscribe(({ deadEntity }) => {
  if (deadEntity.typeId !== "minecraft:player") return;
  tension.set(deadEntity.id, 0);
  for (const s of [...tracked.values()]) {
    if (s.playerId === deadEntity.id && valid(s.entity) && s.mode !== "watching") vanish(s.entity, false);
  }
});

// --------------------------------------------------------------------------- //
// Items
// --------------------------------------------------------------------------- //

function useFlashlight(player) {
  if (onCooldown(`flash:${player.id}`, 60)) return;
  player.playSound("random.click", { pitch: 1.4 });
  tryRun(() => player.addEffect("night_vision", 20 * 20, { showParticles: false }));
  for (const s of [...tracked.values()]) {
    const e = s.entity;
    if (!valid(e) || !canSee(player, e, 28, 0.85)) continue;
    if (s.kind === "dweller" && s.mode !== "fleeing") {
      dwellerFlee(e, s, player);
      player.onScreenDisplay.setActionBar(tr("hl.flash.dweller"));
    } else if (s.kind === "herobrine" || s.kind === "null") {
      vanish(e);
    } else if (s.kind === "fogMan") {
      if (s.mode === "chasing") player.onScreenDisplay.setActionBar(tr("hl.flash.fog"));
      else vanish(e);
    }
  }
}

function giveJournal(player) {
  const container = tryRun(() => player.getComponent("minecraft:inventory").container);
  const left = tryRun(() => container.addItem(new ItemStack(ITEM.journal, 1)));
  if (left) tryRun(() => player.dimension.spawnItem(left, player.location));
}

world.afterEvents.itemUse.subscribe(({ source, itemStack }) => {
  if (source?.typeId !== "minecraft:player" || !itemStack) return;
  if (itemStack.typeId === ITEM.flashlight) useFlashlight(source);
  else if (itemStack.typeId === ITEM.journal && !onCooldown(`journal:${source.id}`, 10)) openJournal(source);
});

// --------------------------------------------------------------------------- //
// The Survivor's Journal
// --------------------------------------------------------------------------- //

function show(form, player, onSelect) {
  form
    .show(player)
    .then((r) => {
      if (!r.canceled && r.selection !== undefined) tryRun(() => onSelect(r.selection));
    })
    .catch(() => {});
}

function statusBody() {
  const rawtext = [
    { translate: "hl.j.status", with: { rawtext: [{ text: String(hauntDay()) }, { translate: `hl.int.${intensity().key}` }] } },
  ];
  for (const t of THREATS) {
    rawtext.push({ text: "\n" }, { translate: `hl.threat.${t}` }, { text: ": " });
    if (!enabled(t)) rawtext.push({ translate: "hl.j.off" });
    else if (active(t)) rawtext.push({ translate: "hl.j.active" });
    else rawtext.push({ translate: "hl.j.in_days", with: [String(unlockDay(t) - hauntDay())] });
  }
  return { rawtext };
}

function openJournal(player) {
  const form = new ActionFormData()
    .title(tr("hl.j.title"))
    .body(statusBody())
    .button(tr("hl.j.bestiary"))
    .button(tr("hl.j.settings"))
    .button(tr("hl.j.summon"))
    .button(tr("hl.j.close"));
  show(form, player, (i) => [() => openBestiary(player), () => openSettings(player), () => openSummon(player)][i]?.());
}

function openBestiary(player) {
  const form = new ActionFormData().title(tr("hl.j.bestiary")).body(tr("hl.j.bestiary_body"));
  for (const t of THREATS) form.button(tr(`hl.threat.${t}`));
  form.button(tr("hl.j.back"));
  show(form, player, (i) => {
    if (i >= THREATS.length) return openJournal(player);
    const page = new ActionFormData().title(tr(`hl.threat.${THREATS[i]}`)).body(tr(`hl.lore.${THREATS[i]}`)).button(tr("hl.j.back"));
    show(page, player, () => openBestiary(player));
  });
}

function openSettings(player) {
  const form = new ActionFormData()
    .title(tr("hl.j.settings"))
    .body(tr("hl.j.settings_body"))
    .button({ rawtext: [{ translate: "hl.j.intensity" }, { text: ": " }, { translate: `hl.int.${intensity().key}` }] });
  for (const t of THREATS) {
    form.button({ rawtext: [{ translate: `hl.threat.${t}` }, { text: ": " }, { translate: enabled(t) ? "hl.j.on" : "hl.j.off" }] });
  }
  form.button(tr("hl.j.back"));
  show(form, player, (i) => {
    if (i === 0) {
      setWorldProp("hl:intensity", (intensityIndex() + 1) % INTENSITY.length);
    } else if (i <= THREATS.length) {
      const t = THREATS[i - 1];
      setWorldProp(`hl:on_${t}`, !enabled(t));
      if (!enabled(t)) {
        if (t === "herobrine") clearKind("herobrine");
        if (t === "null") {
          clearKind("null");
          restoreCorruption(true);
        }
        if (t === "caves") clearKind("dweller");
        if (t === "fog" && fog.active) endFog();
      }
    } else {
      return openJournal(player);
    }
    openSettings(player);
  });
}

function openSummon(player) {
  const events = [
    ["hl.threat.herobrine", () => {
      herobrineSighting(player);
      herobrineEvent(player);
    }],
    ["hl.threat.fog", () => {
      if (!fog.active) startFog();
      fog.respawn.set(player.id, system.currentTick);
    }],
    ["hl.threat.caves", () => spawnDweller(player, false)],
    ["hl.threat.null", () => nullAppears(player) || nullEvent(player)],
    ["hl.j.summon_corrupt", () => corrupt(player)],
    ["hl.threat.ambience", () => ambience(player)],
  ];
  const form = new ActionFormData().title(tr("hl.j.summon")).body(tr("hl.j.summon_body"));
  for (const [key] of events) form.button(tr(key));
  form.button(tr("hl.j.back"));
  show(form, player, (i) => (i < events.length ? events[i][1]() : openJournal(player)));
}

// --------------------------------------------------------------------------- //
// First join
// --------------------------------------------------------------------------- //

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (!initialSpawn) return;
  // Fog pushed in an earlier session would otherwise linger.
  tryRun(() => player.runCommand("fog @s remove hl_fog"));
  if (tryRun(() => player.getDynamicProperty("hl:intro"))) return;
  tryRun(() => player.setDynamicProperty("hl:intro", true));
  later(100, () => {
    if (!valid(player)) return;
    giveJournal(player);
    player.sendMessage(tr("hl.intro.1"));
    player.sendMessage(tr("hl.intro.2"));
  });
});
