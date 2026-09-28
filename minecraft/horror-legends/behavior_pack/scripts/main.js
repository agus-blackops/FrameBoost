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
//   The Presence (day 3+) something in your house: the lights go out one by
//                         one, footsteps follow you and stop when you stop, a
//                         face at the window, someone by your bed when you
//                         wake, a music box, your own death in the chat
//   The Red Night (day 7+) every few days a night of red fog when everything
//                         comes twice as often
//
// Everything is scored with the add-on's own sounds (resource_pack/sounds/hl):
// a heartbeat that quickens when something is near or after you, breathing,
// whispers, static, and a sting with ringing ears for the jumpscares, which
// can be toned down in the journal.
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

const THREATS = ["ambience", "fakeplayer", "caves", "herobrine", "fog", "null", "presence", "bloodnight"];
const START_DAY = { ambience: 0, fakeplayer: 1, caves: 1, herobrine: 2, fog: 4, null: 6, presence: 3, bloodnight: 7 };
const INTENSITY = [
  { key: "low", days: 2, chance: 0.5 },
  { key: "normal", days: 1, chance: 1 },
  { key: "high", days: 0.5, chance: 1.6 },
  { key: "nightmare", days: 0, chance: 2.5 },
];

const PASSABLE = /air|leaves|grass|fern|flower|vine|snow_layer|bush|sapling|mushroom|dandelion|poppy|tulip|orchid|allium|bluet|daisy|cornflower|lily|berry|moss_carpet|pink_petals|torch/;
const NATURAL_STONE = /^minecraft:(stone|deepslate|granite|diorite|andesite|tuff|dirt|gravel|calcite|smooth_basalt)$/;
const LIGHTS = /^minecraft:(soul_)?(torch|lantern)$/;
const PROTECTED = /chest|barrel|shulker|furnace|smoker|sign|bed|door|hopper|dispenser|dropper|portal|bedrock|spawner|lectern|anvil|beacon|command|structure|jigsaw|water|lava|torch|lantern|campfire|brewing|enchanting|ender|frame|pot|banner|head|skull|crafter|bell|rail|redstone|lever|button|pressure|piston|observer|comparator|repeater|sculk|vault|trial/;

// id -> { entity, kind, playerId, mode, seen, unseen, age, timer }
const tracked = new Map();
const tension = new Map();
const fogged = new Set();
const cooldowns = new Map();
const fog = { active: false, until: 0, respawn: new Map() };
const blood = { active: false, until: 0, fogged: new Set() };

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

// Inside the player's view cone, walls or not: for things seen through glass.
function looksAt(player, target, range, cone) {
  const eye = player.getHeadLocation();
  const d = sub({ x: target.location.x, y: target.location.y + 1.6, z: target.location.z }, eye);
  const dist = length(d);
  if (dist > range || dist < 0.01) return false;
  const view = player.getViewDirection();
  return (d.x * view.x + d.y * view.y + d.z * view.z) / dist >= cone;
}

function jumpscaresOn() {
  return worldProp("hl:on_jumpscares", true);
}

// A proper jumpscare: a sting, a red flash, the screen shaking, the world
// going dark for a moment and your ears ringing after. With jumpscares off in
// the journal only a quieter sting is left.
function scare(player, title, subtitle = " ") {
  const strong = jumpscaresOn();
  tryRun(() => player.playSound("hl.stinger", { volume: strong ? 1 : 0.4 }));
  if (title) {
    tryRun(() => player.onScreenDisplay.setTitle(title, { subtitle, fadeInDuration: 0, stayDuration: 16, fadeOutDuration: 12 }));
  }
  if (!strong) return;
  tryRun(() =>
    player.camera.fade({
      fadeColor: { red: 0.45, green: 0, blue: 0 },
      fadeTime: { fadeInTime: 0.05, holdTime: 0.2, fadeOutTime: 0.8 },
    })
  );
  tryRun(() => player.runCommand("camerashake add @s 1.2 0.6 rotational"));
  tryRun(() => player.addEffect("darkness", 80, { showParticles: false }));
  later(35, () => player.playSound("hl.ringing", { volume: 0.7 }));
}

// The screen goes black for a blink.
function blackout(player, seconds = 0.35) {
  tryRun(() =>
    player.camera.fade({
      fadeColor: { red: 0, green: 0, blue: 0 },
      fadeTime: { fadeInTime: 0.05, holdTime: seconds, fadeOutTime: 0.1 },
    })
  );
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
  return Math.random() < p * intensity().chance * (blood.active ? 2 : 1);
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
    ["whisper", 2],
    ["breath", 1],
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
      soundBehind(player, "hl.whisper", 2, rand(0.85, 1.05), 0.8);
      if (Math.random() < 0.5) player.sendMessage(tr("hl.ambience.whisper", player.name));
      break;
    case "breath":
      soundBehind(player, "hl.breath", 1.5, rand(0.85, 1.0), 0.9);
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
  if (s.mode === "reveal") {
    faceTowards(e, player.location);
    if (s.scare && canSee(player, e, 32, 0.8)) {
      s.scare = false;
      scare(player, "§4§lHEROBRINE");
    }
    if (s.age > 20 * 5 || distance(player.location, e.location) < 2) vanish(e);
    return;
  }
  if (s.mode === "window" || s.mode === "bedside") {
    tickWatcher(e, s, player);
    return;
  }
  const behind = s.mode === "behind";
  const seen = canSee(player, e, 96, behind ? 0.75 : 0.97);
  s.seen = seen ? s.seen + 1 : 0;
  const close = distance(player.location, e.location) < (behind ? 1.2 : 14);
  if (s.seen >= (behind ? 1 : 3) || close || s.age > 20 * (behind ? 12 : 25)) {
    if (behind && seen) {
      if (Math.random() < 0.5) scare(player, " ", "§f§l. .");
      else {
        blackout(player);
        player.playSound("hl.whisper", { pitch: 0.8 });
      }
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
  scare(player);
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
    p.playSound("hl.drone", { volume: 0.8 });
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
  soundAt(player, "hl.knock", at, rand(0.85, 1.0), 1);
  player.onScreenDisplay.setActionBar(tr("hl.fog.knock"));
  later(90, () => {
    if (!fog.active || Math.random() > 0.35 || trackedFor(player, "fogMan")?.mode === "chasing") return;
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
  soundAt(player, "hl.scream", e.location, rand(0.9, 1.1), 1);
  soundAt(player, "mob.warden.roar", e.location, 1.3, 0.6);
  tryRun(() => player.addEffect("darkness", 120, { showParticles: false }));
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
    if (s.timer % 100 === 0) soundAt(player, Math.random() < 0.5 ? "hl.scream" : "hl.chitter", e.location, rand(0.8, 1.1), 1);
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
    if (Math.random() < 0.5) soundAt(player, "hl.chitter", e.location, rand(0.8, 1.2), 0.9);
    else for (let i = 0; i < 4; i++) later(i * 3, () => soundAt(player, "step.stone", e.location, 1.6, 0.7));
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
  if (t >= 50 && roll(0.06)) soundBehind(player, "hl.whisper", rand(3, 6), rand(0.7, 0.9), 0.7);
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
  blackout(player);
  player.onScreenDisplay.setTitle("§k||||||||||||", { fadeInDuration: 0, stayDuration: 6, fadeOutDuration: 2 });
  player.playSound("hl.static", { pitch: rand(0.8, 1.1) });
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
  player.playSound("hl.static", { pitch: 0.7 });
  scare(player, "§4§knull", "§8null");
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

// Blocks the add-on changed for a while, with what they were and when they
// go back: missing-texture corruption, and lights that went out.
function savedBlocks(key) {
  const raw = worldProp(key, "");
  return (raw && tryRun(() => JSON.parse(raw))) || [];
}

function saveBlocks(key, list) {
  setWorldProp(key, JSON.stringify(list));
}

function corruptionList() {
  return savedBlocks("hl:corrupt");
}

function saveCorruption(list) {
  saveBlocks("hl:corrupt", list);
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
  restoreBlocks("hl:corrupt", CORRUPTED, force);
}

// Put saved blocks back once their time is up, if nothing else has taken
// their place since (`marker` is what the add-on left there).
function restoreBlocks(key, marker, force = false) {
  const list = savedBlocks(key);
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
    if (b.typeId !== marker) continue; // broken or built over since
    const perm = tryRun(() => BlockPermutation.resolve(entry.type, entry.st));
    if (!(perm && tryRun(() => (b.setPermutation(perm), true)))) tryRun(() => b.setType(entry.type));
  }
  if (keep.length !== list.length) saveBlocks(key, keep);
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
// HerobrineGamer788: a player who joins your world
// --------------------------------------------------------------------------- //
//
// He joins like anyone else and plays like anyone else: walks over, chats,
// mines, builds, puts down torches, jumps around, hands you food. Then he
// starts copying you: he mines what you mine, places what you place, sneaks
// when you sneak, jumps when you jump. Then something is wrong: he freezes
// when you look at him and creeps closer when you don't, his eyes flicker
// white, his name glitches, your torches turn red. And then he stops
// pretending.

const FAKE = "hl:fake_player";
const FAKE_NAME = "HerobrineGamer788";
const FAKE_STAGE_TICKS = [20 * 150, 20 * 120, 20 * 90];
const MINABLE = /(_log|leaves|^minecraft:(dirt|grass_block|stone|sand|gravel|deepslate|andesite|diorite|granite|tuff))$/;

let fake = null; // { entity, playerId, stage, t, cool, mode, state, name }

function fakeAlive() {
  return fake && valid(fake.entity);
}

function fakeChat(key, ...args) {
  if (!fakeAlive()) return;
  world.sendMessage({ rawtext: [{ text: `<${fake.name}> ` }, { translate: key, with: args.map(String) }] });
}

function fakeMode(mode) {
  if (fake.mode === mode) return;
  fake.mode = mode;
  tryRun(() => fake.entity.triggerEvent(`hl:${mode}`));
}

function fakeState(state) {
  if (fake.state === state) return;
  fake.state = state;
  tryRun(() => fake.entity.setProperty("hl:state", state));
}

function fakeSwing(ticks = 24) {
  fakeState("swing");
  later(ticks, () => fakeAlive() && fake.state === "swing" && fakeState("idle"));
}

function fakeJoinGame(player) {
  if (fakeAlive() || player.dimension.id !== OVERWORLD) return false;
  const at = groundSpot(player, 16, 24, 180, 50) ?? groundSpot(player, 10, 20, 0, 180);
  if (!at) return false;
  const e = tryRun(() => player.dimension.spawnEntity(FAKE, at));
  if (!e) return false;
  e.nameTag = FAKE_NAME;
  tryRun(() => e.setDynamicProperty("hl:director", true));
  fake = { entity: e, playerId: player.id, stage: 0, t: 0, cool: 80, mode: "roam", state: "idle", name: FAKE_NAME };
  world.sendMessage({ rawtext: [{ text: "§e" }, { translate: "multiplayer.player.joined", with: [FAKE_NAME] }] });
  later(60, () => fakeChat(`hl.fake.hello.${randInt(1, 4)}`, player.name));
  return true;
}

function fakeLeave(silent = false) {
  if (fakeAlive()) {
    vanish(fake.entity, !silent);
    world.sendMessage({ rawtext: [{ text: "§e" }, { translate: "multiplayer.player.left", with: [FAKE_NAME] }] });
  }
  fake = null;
}

// Mine a natural block within reach, dropping it like a player would.
function fakeMine(preferType) {
  const e = fake.entity;
  const { x, y, z } = e.location;
  let target;
  // Copying you, he'll dig into the ground too; on his own he mines what's in front of him.
  for (let dy = preferType ? -1 : 0; dy <= 2 && !target; dy++)
    for (let dx = -2; dx <= 2 && !target; dx++)
      for (let dz = -2; dz <= 2 && !target; dz++) {
        const b = block(e.dimension, x + dx, y + dy, z + dz);
        if (b && (preferType ? b.typeId === preferType : MINABLE.test(b.typeId))) target = b;
      }
  if (!target) return false;
  faceTowards(e, { x: target.x + 0.5, z: target.z + 0.5 });
  fakeSwing(30);
  later(24, () => {
    if (!fakeAlive() || target.typeId === "minecraft:air") return;
    const type = target.typeId;
    const drop = { "minecraft:stone": "minecraft:cobblestone", "minecraft:grass_block": "minecraft:dirt", "minecraft:deepslate": "minecraft:cobbled_deepslate" }[type] ?? type;
    tryRun(() => target.setType("minecraft:air"));
    const sound = /log/.test(type) ? "dig.wood" : /leaves|grass|dirt/.test(type) ? "dig.grass" : /sand|gravel/.test(type) ? "dig.sand" : "dig.stone";
    for (const p of overworldPlayers()) soundAt(p, sound, target.location, 1, 0.9);
    if (!/leaves/.test(type)) tryRun(() => e.dimension.spawnItem(new ItemStack(drop, 1), { x: target.x + 0.5, y: target.y + 0.3, z: target.z + 0.5 }));
  });
  return true;
}

// Put a block down next to himself: a little pillar, or whatever you placed.
function fakePlace(type) {
  const e = fake.entity;
  const dirs = [[1, 0], [-1, 0], [0, 1], [0, -1]].sort(() => Math.random() - 0.5);
  for (const [dx, dz] of dirs) {
    const bx = Math.floor(e.location.x) + dx;
    const bz = Math.floor(e.location.z) + dz;
    // The first air cell with something solid under it, from a step down to a stack up.
    for (let by = Math.floor(e.location.y) - 1; by <= Math.floor(e.location.y) + 2; by++) {
      const here = block(e.dimension, bx, by, bz);
      const below = block(e.dimension, bx, by - 1, bz);
      if (!here?.isAir || !below || below.isAir) continue;
      faceTowards(e, { x: bx + 0.5, z: bz + 0.5 });
      fakeSwing(16);
      later(8, () => {
        if (!here.isAir) return;
        const ok = tryRun(() => (here.setType(type), true)) || tryRun(() => (here.setType("minecraft:planks"), true));
        if (ok) for (const p of overworldPlayers()) soundAt(p, "dig.stone", { x: bx + 0.5, y: by, z: bz + 0.5 }, 1.2, 0.8);
      });
      return true;
    }
  }
  return false;
}

function fakeGift(player, items) {
  const [type, amount] = items[randInt(0, items.length - 1)];
  const f = facing(player);
  const at = { x: player.location.x + f.x, y: player.location.y + 0.5, z: player.location.z + f.z };
  fakeSwing(12);
  tryRun(() => player.dimension.spawnItem(new ItemStack(type, amount), at));
}

function fakeAction(player, d) {
  const stage = fake.stage;
  if (stage === 0) {
    const act = pick([["chat", 30], ["mine", 25], ["build", 15], ["torch", 8], ["jump", 12], ["gift", d < 6 ? 10 : 0]]);
    if (act === "chat") fakeChat(`hl.fake.chat.${randInt(1, 8)}`, player.name);
    else if (act === "mine") fakeMine() || fakeChat(`hl.fake.chat.${randInt(1, 8)}`, player.name);
    else if (act === "build") {
      const type = ["minecraft:dirt", "minecraft:cobblestone", "minecraft:oak_planks"][randInt(0, 2)];
      fakePlace(type);
      later(20, () => fakeAlive() && fakePlace(type));
    } else if (act === "torch") fakePlace("minecraft:torch");
    else if (act === "jump") tryRun(() => fake.entity.applyImpulse({ x: 0, y: 0.42, z: 0 }));
    else if (act === "gift") {
      fakeGift(player, [["minecraft:bread", 3], ["minecraft:apple", 2], ["minecraft:torch", 8], ["minecraft:cookie", 4]]);
      fakeChat("hl.fake.gift");
    }
    fake.cool = randInt(80, 180);
  } else if (stage === 1) {
    const act = pick([["chat", 45], ["jump", 15], ["mine", 15], ["stare", 25]]);
    if (act === "chat") fakeChat(`hl.fake.mimic.${randInt(1, 6)}`, player.name);
    else if (act === "jump") tryRun(() => fake.entity.applyImpulse({ x: 0, y: 0.42, z: 0 }));
    else if (act === "mine") fakeMine();
    else {
      fakeMode("freeze");
      fakeState("stare");
      later(60, () => fakeAlive() && fake.stage === 1 && (fakeState("idle"), fakeMode("roam")));
    }
    fake.cool = randInt(80, 160);
  } else if (stage === 2) {
    const act = pick([["chat", 40], ["eyes", 25], ["torches", 15], ["name", 20]]);
    if (act === "chat") fakeChat(`hl.fake.creepy.${randInt(1, 6)}`, player.name);
    else if (act === "eyes") {
      tryRun(() => fake.entity.setProperty("hl:eyes", true));
      later(randInt(10, 30), () => fakeAlive() && fake.stage < 3 && tryRun(() => fake.entity.setProperty("hl:eyes", false)));
    } else if (act === "torches") redTorches(player);
    else {
      const e = fake.entity;
      e.nameTag = Math.random() < 0.5 ? `§k${FAKE_NAME}` : "Herobrine";
      fake.name = e.nameTag;
      later(randInt(20, 40), () => {
        if (fakeAlive() && fake.stage < 3) {
          e.nameTag = FAKE_NAME;
          fake.name = FAKE_NAME;
        }
      });
    }
    fake.cool = randInt(60, 120);
  }
}

// He stops pretending.
function fakeReveal(player) {
  if (!fakeAlive() || fake.stage === 3) return;
  fake.stage = 3;
  const e = fake.entity;
  e.nameTag = "Herobrine";
  fake.name = "Herobrine";
  fakeMode("freeze");
  fakeState("stare");
  tryRun(() => e.setProperty("hl:eyes", true));
  faceTowards(e, player.location);
  fakeChat("hl.fake.reveal");
  later(50, () => {
    if (!fakeAlive()) return;
    const at = { ...e.location };
    vanish(e);
    fake = null;
    glitch(player);
    const hb = spawn(MOB.herobrine, at, player, "herobrine", "reveal");
    if (hb) faceTowards(hb, player.location);
    scare(player);
    player.onScreenDisplay.setTitle("§4§lHEROBRINE", { subtitle: "§8" + FAKE_NAME, fadeInDuration: 0, stayDuration: 30, fadeOutDuration: 20 });
    player.playSound("mob.endermen.scream", { pitch: 0.4 });
    soundAt(player, "ambient.weather.thunder", at, 0.6, 1);
    tryRun(() => player.addEffect("blindness", 30, { showParticles: false }));
    later(90, () => world.sendMessage({ rawtext: [{ text: "§e" }, { translate: "multiplayer.player.left", with: [FAKE_NAME] }] }));
    setWorldProp("hl:fp_day", world.getDay());
  });
}

function tickFake() {
  if (!fakeAlive()) {
    fake = null;
    // Spawn eggs: adopt. Leftovers from a closed session: leave.
    for (const e of world.getDimension(OVERWORLD).getEntities({ type: FAKE })) {
      if (tryRun(() => e.getDynamicProperty("hl:director"))) {
        fake = { entity: e };
        fakeLeave(true);
        continue;
      }
      const p = overworldPlayers()[0];
      if (!p) return;
      e.nameTag = FAKE_NAME;
      tryRun(() => e.setDynamicProperty("hl:director", true));
      fake = { entity: e, playerId: p.id, stage: 0, t: 0, cool: 40, mode: "roam", state: "idle", name: FAKE_NAME };
      world.sendMessage({ rawtext: [{ text: "§e" }, { translate: "multiplayer.player.joined", with: [FAKE_NAME] }] });
      break;
    }
    return;
  }
  const player = overworldPlayers().find((p) => p.id === fake.playerId);
  if (!player) {
    fakeLeave();
    return;
  }
  const e = fake.entity;
  fake.t += 10;
  const d = distance(player.location, e.location);

  // Players who fall too far behind just... catch up.
  if (d > 48) {
    const at = groundSpot(player, 14, 20, 180, 40);
    if (at) tryRun(() => e.teleport(at));
  }

  if (fake.stage < 2) {
    fakeMode(d > 7 ? "follow" : "roam");
    if (fake.stage === 1) {
      // Copying you.
      if (fake.state !== "swing" && fake.state !== "stare") fakeState(player.isSneaking ? "sneak" : "idle");
      if (player.isJumping && !onCooldown(`fakejump:${e.id}`, 10)) tryRun(() => e.applyImpulse({ x: 0, y: 0.42, z: 0 }));
    }
  } else if (fake.stage === 2) {
    // Still when you look. Closer when you don't.
    if (canSee(player, e, 64, 0.88)) {
      fakeMode("freeze");
      fakeState("stare");
    } else {
      fakeMode(d > 4 ? "follow" : "freeze");
      if (fake.state === "stare") fakeState("idle");
    }
  }

  if (fake.stage < 3 && (fake.cool -= 10) <= 0) tryRun(() => fakeAction(player, d));

  const limit = fake.stage < 3 ? FAKE_STAGE_TICKS[fake.stage] / intensity().chance : Infinity;
  if (fake.stage < 2 && fake.t >= limit) {
    fake.stage++;
    fake.t = 0;
    if (fake.stage === 1) fakeChat("hl.fake.mimic.1", player.name);
  } else if (fake.stage === 2 && fake.t >= limit && (d < 12 || fake.t >= limit + 20 * 30)) {
    fakeReveal(player);
  }
}

system.runInterval(() => tryRun(tickFake), 10);

// Copy what the player does, while he is in his copying phase.
function mimicking(player) {
  return fakeAlive() && fake.stage === 1 && fake.playerId === player.id;
}

world.afterEvents.playerBreakBlock.subscribe(({ player, brokenBlockPermutation }) => {
  if (!mimicking(player)) return;
  // BlockPermutation.type isn't in this API version; the item form carries the id.
  const type = tryRun(() => brokenBlockPermutation.type?.id) ?? tryRun(() => brokenBlockPermutation.getItemStack(1)?.typeId);
  later(randInt(15, 35), () => {
    if (!fakeAlive()) return;
    if (!(type && fakeMine(type))) {
      fakeSwing(20);
      if (Math.random() < 0.6) fakeChat("hl.fake.copy");
    }
  });
});

world.afterEvents.playerPlaceBlock.subscribe(({ player, block: placed }) => {
  if (!mimicking(player)) return;
  const type = placed?.typeId;
  if (type && !PROTECTED.test(type)) later(randInt(15, 30), () => fakeAlive() && fakePlace(type));
});

// --------------------------------------------------------------------------- //
// The Presence: something in your house
// --------------------------------------------------------------------------- //

const stalkers = new Map(); // playerId -> { until, last, look, quiet }
const bedNights = new Map(); // playerId -> the day it last came to your bed

// The lights go out one by one, furthest first. You hear breathing in the
// dark. When they come back, sometimes someone is standing in front of you.
function lightsOut(player) {
  if (onCooldown(`lights:${player.id}`, 20 * 60)) return false;
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
      const type = here.typeId;
      const st = tryRun(() => here.permutation.getAllStates()) ?? {};
      if (!tryRun(() => (here.setType("minecraft:air"), true))) return;
      const list = savedBlocks("hl:lights");
      list.push({ x: b.x, y: b.y, z: b.z, type, st, at: back });
      saveBlocks("hl:lights", list);
      soundAt(player, "random.fizz", { x: b.x + 0.5, y: b.y + 0.5, z: b.z + 0.5 }, 0.6, 0.4);
    })
  );
  const out = lights.length * 6;
  later(out + 30, () => soundBehind(player, "hl.breath", 1.3, 0.9, 1));
  later(out + dark, () => {
    restoreBlocks("hl:lights", "minecraft:air", true);
    if (Math.random() < 0.45 && !trackedFor(player, "herobrine")) {
      const at = groundSpot(player, 2.5, 3.5, 0, 25);
      const e = at && spawn(MOB.herobrine, at, player, "herobrine", "reveal");
      const s = e && tracked.get(e.id);
      if (s) s.scare = true;
    }
  });
  return true;
}

// Footsteps that follow you, stop a step after you stop, and are gone the
// moment you turn round.
function stalk(player) {
  if (stalkers.has(player.id)) return false;
  stalkers.set(player.id, { until: system.currentTick + 20 * 25, last: { ...player.location }, look: facing(player), quiet: 0 });
  return true;
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
  } else if (++s.quiet === 1) {
    later(9, () => soundBehind(player, step, 2.2, 0.85, 0.9)); // one more step, after you stopped
  }
}

// A face at the window, tapping on the glass.
function windowWatcher(player) {
  if (trackedFor(player, "herobrine")) return false;
  const { x, y, z } = player.location;
  const panes = [];
  for (let dx = -8; dx <= 8; dx++)
    for (let dz = -8; dz <= 8; dz++)
      for (let dy = -1; dy <= 2; dy++) {
        const b = block(player.dimension, x + dx, y + dy, z + dz);
        if (b && /glass/.test(b.typeId) && distance(b, player.location) >= 3) panes.push(b);
      }
  for (let i = panes.length - 1; i > 0; i--) {
    const j = randInt(0, i);
    [panes[i], panes[j]] = [panes[j], panes[i]];
  }
  for (const g of panes.slice(0, 16)) {
    const dx = g.x + 0.5 - x;
    const dz = g.z + 0.5 - z;
    const out = Math.abs(dx) > Math.abs(dz) ? { x: g.x + Math.sign(dx), z: g.z } : { x: g.x, z: g.z + Math.sign(dz) };
    const gy = groundAt(player.dimension, out.x + 0.5, out.z + 0.5, g.y + 1);
    if (gy === undefined || Math.abs(gy - g.y) > 2) continue;
    const feet = block(player.dimension, out.x, gy, out.z);
    const head = block(player.dimension, out.x, gy + 1, out.z);
    if (!feet || !head || !(feet.isAir || PASSABLE.test(feet.typeId)) || !(head.isAir || PASSABLE.test(head.typeId))) continue;
    const e = spawn(MOB.herobrine, { x: out.x + 0.5, y: gy, z: out.z + 0.5 }, player, "herobrine", "window");
    if (!e) continue;
    const pane = { x: g.x + 0.5, y: g.y + 0.5, z: g.z + 0.5 };
    for (let k = 0; k < 3; k++) later(k * 9, () => soundAt(player, "hl.knock", pane, 1.8, 0.25));
    return true;
  }
  return false;
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
    if (d < 2.2 || Math.random() < (s.mode === "bedside" ? 0.7 : 0.45)) scare(player, " ", "§4§l. . .");
    else blackout(player);
    vanish(e);
    return;
  }
  if (s.age > 20 * 30) vanish(e, false);
}

function checkSleep(player) {
  if (!active("presence") || !tryRun(() => player.isSleeping)) return;
  if (bedNights.get(player.id) === world.getDay()) return;
  bedNights.set(player.id, world.getDay());
  if (!roll(0.35) || trackedFor(player, "herobrine")) return;
  const at = groundSpot(player, 1.5, 2.5, rand(0, 360), 180);
  if (!at) return;
  spawn(MOB.herobrine, at, player, "herobrine", "bedside");
  later(20, () => soundAt(player, "hl.breath", at, 0.85, 1));
}

function musicBox(player) {
  const p = offsetFromView(player, rand(5, 9), rand(100, 260));
  const at = { x: p.x, y: player.location.y + 1, z: p.z };
  soundAt(player, "hl.musicbox", at, rand(0.92, 1.0), 1);
  later(240, () => soundAt(player, "hl.whisper", at, 0.8, 0.8));
  return true;
}

// Your own death in the chat, and then a correction.
function fakeDeath(player) {
  if (onCooldown(`death:${player.id}`, 20 * 600)) return false;
  player.sendMessage(tr("hl.presence.death", player.name));
  later(80, () => player.sendMessage(tr("hl.presence.death2")));
  return true;
}

function presenceEvent(player) {
  const night = isNight();
  const under = isUnderground(player);
  const event = pick([
    ["lights", night || under ? 22 : 0],
    ["stalk", 20],
    ["window", night ? 18 : 6],
    ["breath", 12],
    ["musicbox", night ? 10 : 4],
    ["voice", 10],
    ["death", 4],
  ]);
  switch (event) {
    case "lights":
      return lightsOut(player) || stalk(player);
    case "stalk":
      return stalk(player);
    case "window":
      return windowWatcher(player) || stalk(player);
    case "breath":
      soundBehind(player, "hl.breath", 1.2, rand(0.85, 1.0), 1);
      return true;
    case "musicbox":
      return musicBox(player);
    case "voice":
      soundBehind(player, "hl.whisper", 1.5, rand(0.8, 1.0), 1);
      player.sendMessage(tr("hl.ambience.whisper", player.name));
      return true;
    case "death":
      return fakeDeath(player);
  }
  return false;
}

system.runInterval(() => {
  for (const player of overworldPlayers()) {
    tryRun(() => tickStalker(player));
    tryRun(() => checkSleep(player));
  }
}, 6);

// --------------------------------------------------------------------------- //
// The Red Night
// --------------------------------------------------------------------------- //

function bloodDue() {
  if (!active("bloodnight")) return false;
  const since = hauntDay() - unlockDay("bloodnight");
  return since >= 0 && since % 5 === 0;
}

function bloodOn(player) {
  if (blood.fogged.has(player.id)) return;
  blood.fogged.add(player.id);
  tryRun(() => player.runCommand("fog @s push hl:blood hl_blood"));
}

// Summoned from the journal it also works by day, for a couple of minutes.
function startBlood(summoned = false) {
  blood.active = true;
  blood.until = summoned ? system.currentTick + 20 * 120 : 0;
  setWorldProp("hl:blood_day", world.getDay());
  for (const p of overworldPlayers()) {
    bloodOn(p);
    tryRun(() => p.onScreenDisplay.setTitle(tr("hl.blood.title"), { subtitle: tr("hl.blood.sub"), fadeInDuration: 20, stayDuration: 70, fadeOutDuration: 30 }));
    p.playSound("hl.drone", { volume: 1 });
    later(30, () => p.playSound("hl.stinger", { volume: 0.5, pitch: 0.7 }));
  }
}

function endBlood() {
  blood.active = false;
  for (const p of world.getAllPlayers()) {
    if (blood.fogged.has(p.id)) p.onScreenDisplay.setActionBar(tr("hl.blood.end"));
    tryRun(() => p.runCommand("fog @s remove hl_blood"));
  }
  blood.fogged.clear();
}

function bloodTick() {
  if (blood.active) {
    if ((!isNight() && system.currentTick > blood.until) || !enabled("bloodnight")) {
      endBlood();
      return;
    }
    for (const p of overworldPlayers()) {
      bloodOn(p);
      if (Math.random() < 0.3) p.playSound("hl.drone", { volume: 0.7 });
    }
    return;
  }
  if (isNight() && bloodDue() && worldProp("hl:blood_day", -1) !== world.getDay()) startBlood();
}

// --------------------------------------------------------------------------- //
// The heartbeat: faster the closer it is
// --------------------------------------------------------------------------- //

const heartNext = new Map();

function danger(player) {
  let level = 0;
  for (const s of tracked.values()) {
    if (s.playerId !== player.id || !valid(s.entity)) continue;
    const d = distance(player.location, s.entity.location);
    if (s.mode === "chasing" && d < 32) return 2;
    if ((s.mode === "behind" || s.mode === "window" || s.mode === "bedside") && d < 12) return 2;
    if (d < 40) level = 1;
  }
  if (blood.active || stalkers.has(player.id)) return Math.max(level, 1);
  if (!level && (tension.get(player.id) ?? 0) >= 60 && isUnderground(player)) level = 1;
  return level;
}

system.runInterval(() => {
  const now = system.currentTick;
  for (const player of overworldPlayers()) {
    tryRun(() => {
      if (now < (heartNext.get(player.id) ?? 0)) return;
      const level = danger(player);
      if (!level) {
        heartNext.set(player.id, now + 20);
        return;
      }
      heartNext.set(player.id, now + (level === 2 ? 14 : 26));
      player.playSound("hl.heartbeat", { volume: level === 2 ? 1 : 0.55 });
    });
  }
}, 2);

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
  tryRun(bloodTick);
  tryRun(() => restoreCorruption());
  tryRun(() => restoreBlocks("hl:lights", "minecraft:air"));
  const night = isNight();
  for (const player of overworldPlayers()) {
    tryRun(() => {
      if (active("ambience") && roll(night ? 0.1 : 0.05)) ambience(player);
      caveTick(player);
      if (active("herobrine") && roll(night ? 0.045 : 0.03)) herobrineEvent(player);
      if (active("fakeplayer") && !fakeAlive() && worldProp("hl:fp_day", -1) !== world.getDay() && roll(0.015)) {
        fakeJoinGame(player);
      }
      if (active("null") && roll(0.03)) nullEvent(player);
      if (active("presence") && roll(night ? 0.05 : 0.02)) presenceEvent(player);
    });
  }
}, 100);

world.afterEvents.entityHitEntity.subscribe(({ damagingEntity: attacker, hitEntity: target }) => {
  if (attacker?.typeId !== "minecraft:player") return;
  if (target.typeId === FAKE && fakeAlive() && target.id === fake.entity.id) {
    if (fake.stage >= 2) fakeReveal(attacker);
    else {
      fakeChat("hl.fake.hit");
      const f = facing(attacker);
      tryRun(() => target.applyImpulse({ x: f.x * 0.5, y: 0.3, z: f.z * 0.5 }));
    }
    return;
  }
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
  if (fakeAlive() && fake.playerId === deadEntity.id) later(40, () => fakeLeave());
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
  form.button({ rawtext: [{ translate: "hl.j.jumpscares" }, { text: ": " }, { translate: jumpscaresOn() ? "hl.j.on" : "hl.j.off" }] });
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
        if (t === "fakeplayer") fakeLeave();
        if (t === "presence") {
          stalkers.clear();
          restoreBlocks("hl:lights", "minecraft:air", true);
        }
        if (t === "bloodnight" && blood.active) endBlood();
      }
    } else if (i === THREATS.length + 1) {
      setWorldProp("hl:on_jumpscares", !jumpscaresOn());
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
    ["hl.threat.fakeplayer", () => fakeAlive() ? fakeReveal(player) : fakeJoinGame(player)],
    ["hl.threat.presence", () => presenceEvent(player)],
    ["hl.threat.bloodnight", () => (blood.active ? endBlood() : startBlood(true))],
    ["hl.j.summon_scare", () => scare(player, "§4§lHEROBRINE")],
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
  tryRun(() => player.runCommand("fog @s remove hl_blood"));
  if (tryRun(() => player.getDynamicProperty("hl:intro"))) return;
  tryRun(() => player.setDynamicProperty("hl:intro", true));
  later(100, () => {
    if (!valid(player)) return;
    giveJournal(player);
    player.sendMessage(tr("hl.intro.1"));
    player.sendMessage(tr("hl.intro.2"));
  });
});
