// Small helpers shared by every module: maths, timing, where the player is
// looking, finding ground, caves and water, what the land around is like,
// sounds and the inventory.

import { ItemStack, system, world } from "@minecraft/server";

export const OVERWORLD = "minecraft:overworld";

export const PASSABLE =
  /air|leaves|grass|fern|flower|vine|snow_layer|bush|sapling|mushroom|dandelion|poppy|tulip|orchid|allium|bluet|daisy|cornflower|lily|berry|moss_carpet|pink_petals|torch|light_block/;
export const NATURAL_STONE = /^minecraft:(stone|deepslate|granite|diorite|andesite|tuff|dirt|gravel|calcite|smooth_basalt)$/;
export const PROTECTED =
  /chest|barrel|shulker|furnace|smoker|sign|bed|door|hopper|dispenser|dropper|portal|bedrock|spawner|lectern|anvil|beacon|command|structure|jigsaw|water|lava|torch|lantern|campfire|brewing|enchanting|ender|frame|pot|banner|head|skull|crafter|bell|rail|redstone|lever|button|pressure|piston|observer|comparator|repeater|sculk|vault|trial|light_block|hl:/;

export function valid(entity) {
  if (!entity) return false;
  return typeof entity.isValid === "function" ? entity.isValid() : entity.isValid;
}

export function tryRun(fn) {
  try {
    return fn();
  } catch {
    return undefined;
  }
}

export function later(ticks, fn) {
  system.runTimeout(() => tryRun(fn), ticks);
}

export function tr(key, ...args) {
  return { rawtext: [{ translate: key, with: args.map(String) }] };
}

export function rand(min, max) {
  return min + Math.random() * (max - min);
}

export function randInt(min, max) {
  return Math.floor(rand(min, max + 1));
}

export function pick(weights) {
  const options = weights.filter(([, w]) => w > 0);
  if (!options.length) return undefined;
  const total = options.reduce((sum, [, w]) => sum + w, 0);
  let r = Math.random() * total;
  for (const [value, w] of options) {
    r -= w;
    if (r <= 0) return value;
  }
  return options[options.length - 1][0];
}

export function shuffle(list) {
  for (let i = list.length - 1; i > 0; i--) {
    const j = randInt(0, i);
    [list[i], list[j]] = [list[j], list[i]];
  }
  return list;
}

export function sub(a, b) {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}

export function length(v) {
  return Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
}

export function distance(a, b) {
  return length(sub(a, b));
}

const cooldowns = new Map();

// True if `key` fired less than `ticks` ago; otherwise starts the cooldown.
export function onCooldown(key, ticks) {
  const now = system.currentTick;
  if (now - (cooldowns.get(key) ?? -Infinity) < ticks) return true;
  cooldowns.set(key, now);
  return false;
}

export function isNight() {
  const t = world.getTimeOfDay();
  return t >= 13000 && t < 23000;
}

export function overworldPlayers() {
  return world.getAllPlayers().filter((p) => p.dimension.id === OVERWORLD);
}

export function playerById(id) {
  return world.getAllPlayers().find((p) => p.id === id);
}

// Horizontal unit vector the player is facing.
export function facing(player) {
  const d = player.getViewDirection();
  const len = Math.hypot(d.x, d.z) || 1;
  return { x: d.x / len, z: d.z / len };
}

// A point `dist` blocks from the player, `deg` degrees off their line of
// sight (0 = straight ahead, 180 = directly behind).
export function offsetFromView(player, dist, deg) {
  const f = facing(player);
  const a = (deg * Math.PI) / 180;
  return {
    x: player.location.x + (f.x * Math.cos(a) - f.z * Math.sin(a)) * dist,
    z: player.location.z + (f.x * Math.sin(a) + f.z * Math.cos(a)) * dist,
  };
}

export function faceTowards(entity, target) {
  const dx = target.x - entity.location.x;
  const dz = target.z - entity.location.z;
  tryRun(() => entity.setRotation({ x: 0, y: (-Math.atan2(dx, dz) * 180) / Math.PI }));
}

function rayBlocked(player, from, dir, dist) {
  return !!tryRun(() =>
    player.dimension.getBlockFromRay(from, dir, {
      maxDistance: dist,
      includeLiquidBlocks: false,
      includePassableBlocks: false,
    })
  );
}

// `target` is inside the player's view cone with nothing solid in between.
export function canSee(player, target, range, cone) {
  const eye = player.getHeadLocation();
  const aim = { x: target.location.x, y: target.location.y + 1.2, z: target.location.z };
  const to = sub(aim, eye);
  const dist = length(to);
  if (dist > range || dist < 0.01) return false;
  const dir = { x: to.x / dist, y: to.y / dist, z: to.z / dist };
  const view = player.getViewDirection();
  if (dir.x * view.x + dir.y * view.y + dir.z * view.z < cone) return false;
  return !rayBlocked(player, eye, dir, dist);
}

// Inside the player's view cone, walls or not: for things seen through glass.
export function looksAt(player, target, range, cone) {
  const eye = player.getHeadLocation();
  const d = sub({ x: target.location.x, y: target.location.y + 1.6, z: target.location.z }, eye);
  const dist = length(d);
  if (dist > range || dist < 0.01) return false;
  const view = player.getViewDirection();
  return (d.x * view.x + d.y * view.y + d.z * view.z) / dist >= cone;
}

export function block(dimension, x, y, z) {
  return tryRun(() => dimension.getBlock({ x: Math.floor(x), y: Math.floor(y), z: Math.floor(z) }));
}

export function place(dimension, x, y, z, type) {
  return !!tryRun(() => (block(dimension, x, y, z).setType(type), true));
}

export function isOpen(b) {
  return !!b && (b.isAir || PASSABLE.test(b.typeId));
}

// Standing height on the ground at (x, z), searching down from `fromY`.
// Undefined when the column is a tree trunk, water, or not loaded.
export function groundAt(dimension, x, z, fromY) {
  for (let y = Math.floor(fromY) + 12; y > Math.floor(fromY) - 24; y--) {
    const b = block(dimension, x, y, z);
    if (!b) return undefined;
    if (isOpen(b)) continue;
    if (/log|water|lava/.test(b.typeId)) return undefined;
    return y + 1;
  }
  return undefined;
}

// Solid rock overhead: a cave, a mine, a basement.
export function isUnderground(player) {
  if (player.dimension.id !== OVERWORLD || player.location.y > 62) return false;
  return rayBlocked(player, player.getHeadLocation(), { x: 0, y: 1, z: 0 }, 64);
}

// A roof over your head: indoors, whether above or below ground.
export function isIndoors(player) {
  return rayBlocked(player, player.getHeadLocation(), { x: 0, y: 1, z: 0 }, 12);
}

// An air pocket with a floor near the player's height: somewhere in the cave
// a crawling thing could be.
export function caveSpot(player, minDist, maxDist, deg, spread) {
  for (let attempt = 0; attempt < 24; attempt++) {
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
export function groundSpot(player, minDist, maxDist, deg, spread) {
  for (let attempt = 0; attempt < 8; attempt++) {
    const p = offsetFromView(player, rand(minDist, maxDist), deg + rand(-spread, spread));
    const y = groundAt(player.dimension, p.x, p.z, player.location.y);
    if (y !== undefined) return { x: Math.floor(p.x) + 0.5, y, z: Math.floor(p.z) + 0.5 };
  }
  return undefined;
}

// A clear line from the player's eyes to someone standing at `at`.
export function inSight(player, at) {
  const eye = player.getHeadLocation();
  const d = { x: at.x - eye.x, y: at.y + 1.4 - eye.y, z: at.z - eye.z };
  const dist = length(d);
  if (dist < 0.01) return true;
  return !rayBlocked(player, eye, { x: d.x / dist, y: d.y / dist, z: d.z / dist }, dist);
}

// Like groundSpot, but somewhere the player could actually see.
export function visibleSpot(player, minDist, maxDist, deg, spread) {
  let fallback;
  for (let attempt = 0; attempt < 6; attempt++) {
    const at = groundSpot(player, minDist, maxDist, deg, spread);
    if (!at) continue;
    if (inSight(player, at)) return at;
    fallback ??= at;
  }
  return fallback;
}

// Horror Legends: Fears is loud. The big sounds (stings, screams, shrieks,
// booms) are played twice at once, a hair apart in pitch, which is both
// louder and harsher than a single copy. The journal has a switch for it.
//
// And it is 3D: every sound can come from a place, and in caves and tunnels
// the loud ones and the footsteps come back off the walls a moment later.
const LOUD = /^hl\.(stinger|scream|shriek|boom|screech|wail|shatter|static|laugh|growl|roar|howl)$/;
const ECHOING = /^hl\.(stinger|scream|shriek|boom|screech|wail|laugh|growl|moan|roar|howl|step|knock|creak|warp)$/;

export function loudOn() {
  return tryRun(() => world.getDynamicProperty("hl:on_loud")) !== false;
}

// The 3D extras: echoes in caves, creature voices and footsteps, sounds that
// move around you.
export function audio3dOn() {
  return tryRun(() => world.getDynamicProperty("hl:on_3d")) !== false;
}

// Off the walls of a cave, twice, quieter each time.
function echoes(player, id, location, pitch, volume) {
  if (volume < 0.3 || !ECHOING.test(id) || !audio3dOn() || !isUnderground(player)) return;
  later(6, () => tryRun(() => player.playSound(id, { location, pitch: pitch * 0.97, volume: volume * 0.45 })));
  later(15, () => tryRun(() => player.playSound(id, { location, pitch: pitch * 0.93, volume: volume * 0.2 })));
}

export function soundAt(player, sound, location, pitch = 1, volume = 1) {
  tryRun(() => player.playSound(sound, { location, pitch, volume }));
  if (LOUD.test(sound) && volume >= 0.6 && loudOn()) {
    tryRun(() => player.playSound(sound, { location, pitch: pitch * 0.97, volume }));
  }
  echoes(player, sound, location, pitch, volume);
}

export function sound(player, id, volume = 1, pitch = 1) {
  tryRun(() => player.playSound(id, { volume, pitch }));
  if (LOUD.test(id) && volume >= 0.6 && loudOn()) tryRun(() => player.playSound(id, { volume, pitch: pitch * 1.03 }));
}

export function soundBehind(player, id, dist, pitch = 1, volume = 1) {
  const p = offsetFromView(player, dist, 180 + rand(-30, 30));
  soundAt(player, id, { x: p.x, y: player.location.y + 1, z: p.z }, pitch, volume);
}

export function actionBar(player, message) {
  tryRun(() => player.onScreenDisplay.setActionBar(message));
}

export function title(player, main, subtitle = " ", stay = 40) {
  tryRun(() =>
    player.onScreenDisplay.setTitle(main, { subtitle, fadeInDuration: 10, stayDuration: stay, fadeOutDuration: 20 })
  );
}

// ------------------------------------------------------------------------- //
// Inventory
// ------------------------------------------------------------------------- //

function container(player) {
  return tryRun(() => player.getComponent("minecraft:inventory").container);
}

export function heldItem(player) {
  const inv = container(player);
  return inv && tryRun(() => inv.getItem(player.selectedSlotIndex));
}

export function countItem(player, id) {
  const inv = container(player);
  if (!inv) return 0;
  let n = 0;
  for (let i = 0; i < inv.size; i++) {
    const item = tryRun(() => inv.getItem(i));
    if (item?.typeId === id) n += item.amount;
  }
  return n;
}

export function takeItem(player, id, amount = 1) {
  const inv = container(player);
  if (!inv) return false;
  for (let i = 0; i < inv.size && amount > 0; i++) {
    const item = tryRun(() => inv.getItem(i));
    if (item?.typeId !== id) continue;
    const used = Math.min(amount, item.amount);
    amount -= used;
    if (item.amount > used) {
      item.amount -= used;
      tryRun(() => inv.setItem(i, item));
    } else {
      tryRun(() => inv.setItem(i));
    }
  }
  return amount === 0;
}

export function give(player, id, amount = 1) {
  const inv = container(player);
  const left = tryRun(() => inv.addItem(new ItemStack(id, amount)));
  if (left || !inv) tryRun(() => player.dimension.spawnItem(left ?? new ItemStack(id, amount), player.location));
}

export function selectedSlot(player) {
  return tryRun(() => player.selectedSlotIndex) ?? 0;
}

// Swap the item in your hand with another hotbar slot (Entity 303's joke).
export function swapHotbar(player) {
  const inv = container(player);
  if (!inv) return false;
  const a = selectedSlot(player);
  const b = (a + randInt(1, 8)) % 9;
  const ia = tryRun(() => inv.getItem(a));
  const ib = tryRun(() => inv.getItem(b));
  if (!ia && !ib) return false;
  tryRun(() => inv.setItem(a, ib));
  tryRun(() => inv.setItem(b, ia));
  return true;
}

// ------------------------------------------------------------------------- //
// The land around the player
// ------------------------------------------------------------------------- //

export const WATER = /water/;
export const SNOW = /snow|ice|powder/;
export const TREE = /_log$|leaves|^minecraft:(log|log2)$/;

export function inWater(player) {
  if (tryRun(() => player.isInWater || player.isSwimming)) return true;
  const b = block(player.dimension, player.location.x, player.location.y + 0.4, player.location.z);
  return !!b && WATER.test(b.typeId);
}

// The first non-air block going down from above the player's head at (x, z).
export function topBlock(dimension, x, z, fromY) {
  for (let y = Math.floor(fromY) + 10; y > Math.floor(fromY) - 16; y--) {
    const b = block(dimension, x, y, z);
    if (!b) return undefined;
    if (!b.isAir && !/light_block/.test(b.typeId)) return b;
  }
  return undefined;
}

// What the land looks like around the player: water, trees, snow. Samples a
// ring of columns, so it costs a few dozen block reads.
export function surroundings(player) {
  const out = { water: 0, trees: 0, snow: 0, samples: 0 };
  const { x, y, z } = player.location;
  for (let i = 0; i < 10; i++) {
    const a = (i / 10) * Math.PI * 2;
    const r = i % 2 ? 6 : 12;
    const b = topBlock(player.dimension, x + Math.cos(a) * r, z + Math.sin(a) * r, y);
    if (!b) continue;
    out.samples++;
    if (WATER.test(b.typeId)) out.water++;
    else if (TREE.test(b.typeId)) out.trees++;
    else if (SNOW.test(b.typeId)) out.snow++;
  }
  const feet = block(player.dimension, x, y - 1, z);
  if (feet && SNOW.test(feet.typeId)) out.snow += 3;
  if (inWater(player)) out.water += 5;
  return out;
}

// A spot of water near the player (the surface block's position), for things
// that come out of it.
export function waterSpot(player, minDist, maxDist) {
  for (let attempt = 0; attempt < 16; attempt++) {
    const p = offsetFromView(player, rand(minDist, maxDist), rand(0, 360));
    const b = topBlock(player.dimension, p.x, p.z, player.location.y);
    if (b && WATER.test(b.typeId)) return { x: Math.floor(p.x) + 0.5, y: b.y, z: Math.floor(p.z) + 0.5 };
  }
  return undefined;
}

// Somewhere in plain view but behind the player's back, on the ground.
export function behindSpot(player, dist) {
  return groundSpot(player, dist, dist + 1, 180, 25);
}
