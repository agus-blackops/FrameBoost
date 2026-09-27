// The Blair Witch — gameplay scripts.
//
// Using the Hi8 camera for the first time starts the documentary: Josh and
// Mike join you. From then on, every night spent in a forest deepens the
// curse, and the crew lives through the film with you:
//
//   night 1+  twigs snap behind you; stick figures hang in the trees
//   night 2+  three piles of stones at dawn; something shakes the tent
//   night 3+  children's voices; the Witch hunts you; by day you walk in
//             circles back to the same fallen log
//   night 4+  black fog; Mike kicks the map into the creek; at dawn, Josh
//             is gone, and at night you hear him screaming in the dark
//   night 5+  a bundle of twigs wrapped in Josh's shirt; a derelict house
//             among the trees; Mike runs inside after Josh's voice... and
//             in the basement, someone stands facing the corner
//
// Nights spent away from the trees let the curse fade, one at a time. The
// Witch is never seen: look straight at her and she is gone.

import { BlockPermutation, ItemStack, system, world } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

const OVERWORLD = "minecraft:overworld";
const NIGHT_START = 12500;
const NIGHT_END = 23000;

const ITEM = {
  camcorder: "bw:camcorder",
  map: "bw:map",
  bundle: "bw:twig_bundle",
  effigy: "bw:twig_effigy",
  dossier: "bw:dossier",
};
const MOB = {
  witch: "bw:witch",
  figure: "bw:stick_figure",
  cairn: "bw:rock_cairn",
  corner: "bw:corner_man",
  josh: "bw:josh",
  mike: "bw:mike",
};
const CREW_NAMES = { [MOB.josh]: "Josh", [MOB.mike]: "Mike" };
const PROP = {
  nights: "bw:nights",
  lastNight: "bw:last_night",
  lastDawn: "bw:last_dawn",
  house: "bw:house",
  housePos: "bw:house_pos",
  camp: "bw:camp",
  crew: "bw:crew",
  joshGone: "bw:josh_gone",
  tentDay: "bw:tent_day",
  apologyDay: "bw:apology_day",
  intro: "bw:intro",
  owner: "bw:owner",
};

const MAX_FIGURES = 10;
const WITCH_WATCH_CHECKS = 2; // consecutive 4-tick checks before she vanishes
const NOTES = 6;
const LORE_PAGES = 8;
const APOLOGY_LINES = 4;
const HANDPRINT = "bw:handprint_wall";

const cooldowns = new Map();
const recordingSince = new Map();
const fogged = new Set();
const witchWatched = new Map();

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

function tr(key, ...args) {
  return { rawtext: [{ translate: key, with: args.map(String) }] };
}

function rand(min, max) {
  return min + Math.random() * (max - min);
}

function sub(a, b) {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}

function length(v) {
  return Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
}

function flatDistance(a, b) {
  return Math.hypot(a.x - b.x, a.z - b.z);
}

function getProp(player, key, fallback) {
  const value = tryRun(() => player.getDynamicProperty(key));
  return value === undefined ? fallback : value;
}

function setProp(player, key, value) {
  tryRun(() => player.setDynamicProperty(key, value));
}

function getJson(player, key) {
  const raw = getProp(player, key, "");
  return raw ? tryRun(() => JSON.parse(raw)) : undefined;
}

function setJson(player, key, value) {
  setProp(player, key, value === undefined ? "" : JSON.stringify(value));
}

function onCooldown(key, ticks) {
  const now = system.currentTick;
  if (now - (cooldowns.get(key) ?? -Infinity) < ticks) return true;
  cooldowns.set(key, now);
  return false;
}

function isNightTime(time) {
  return time >= NIGHT_START && time < NIGHT_END;
}

function heldItem(player) {
  return tryRun(() => {
    const inventory = player.getComponent("minecraft:inventory");
    return inventory.container.getItem(player.selectedSlotIndex);
  });
}

function later(ticks, fn) {
  system.runTimeout(() => tryRun(fn), ticks);
}

// Horizontal unit vector the player is facing.
function facing(player) {
  const d = player.getViewDirection();
  const len = Math.hypot(d.x, d.z) || 1;
  return { x: d.x / len, z: d.z / len };
}

// True when `target` is inside the player's view cone with nothing solid in
// between.
function canSee(player, target, range, cone) {
  const eye = player.getHeadLocation();
  const aim = { x: target.location.x, y: target.location.y + 1.4, z: target.location.z };
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

// Compass word (translation key) for a horizontal offset. North is -Z.
function compass(dx, dz) {
  const deg = (Math.atan2(dx, -dz) * 180) / Math.PI;
  const keys = ["n", "ne", "e", "se", "s", "sw", "w", "nw"];
  return `bw.dir.${keys[Math.round(((deg + 360) % 360) / 45) % 8]}`;
}

// --------------------------------------------------------------------------- //
// Terrain helpers
// --------------------------------------------------------------------------- //

const PASSABLE = /air|leaves|grass|fern|flower|vine|snow_layer|bush|sapling|mushroom|dandelion|poppy|tulip|orchid|allium|bluet|daisy|cornflower|lily|berry|moss_carpet|pink_petals/;

function isTree(block) {
  return /leaves|log/.test(block.typeId);
}

// Samples the air around the player for leaves and logs.
function inForest(player) {
  if (player.dimension.id !== OVERWORLD) return false;
  const { x, y, z } = player.location;
  let hits = 0;
  for (let i = 0; i < 40; i++) {
    const block = tryRun(() =>
      player.dimension.getBlock({
        x: Math.floor(x + rand(-10, 10)),
        y: Math.floor(y + rand(0, 10)),
        z: Math.floor(z + rand(-10, 10)),
      })
    );
    if (block && isTree(block) && ++hits >= 4) return true;
  }
  return false;
}

// Standing height on the ground at (x, z), searching down from `fromY`.
// Undefined when the column is a tree trunk, water, or not loaded.
function groundAt(dimension, x, z, fromY) {
  const bx = Math.floor(x);
  const bz = Math.floor(z);
  for (let y = Math.floor(fromY) + 12; y > Math.floor(fromY) - 24; y--) {
    const block = tryRun(() => dimension.getBlock({ x: bx, y, z: bz }));
    if (!block) return undefined;
    if (block.isAir || PASSABLE.test(block.typeId)) continue;
    if (/log|water|lava/.test(block.typeId)) return undefined;
    return y + 1;
  }
  return undefined;
}

function spawnOnGround(dimension, type, x, z, fromY) {
  const y = groundAt(dimension, x, z, fromY);
  if (y === undefined) return undefined;
  return tryRun(() => dimension.spawnEntity(type, { x: Math.floor(x) + 0.5, y, z: Math.floor(z) + 0.5 }));
}

function place(dimension, x, y, z, type) {
  tryRun(() => dimension.getBlock({ x, y, z })?.setType(type));
}

function fallenLog() {
  return (
    tryRun(() => BlockPermutation.resolve("minecraft:oak_log", { pillar_axis: "x" })) ??
    tryRun(() => BlockPermutation.resolve("minecraft:log", { old_log_type: "oak", pillar_axis: "x" }))
  );
}

// --------------------------------------------------------------------------- //
// The crew: Josh and Mike
// --------------------------------------------------------------------------- //

function crewMember(player, type) {
  return player.dimension
    .getEntities({ type, location: player.location, maxDistance: 64 })
    .find((e) => valid(e) && tryRun(() => e.getDynamicProperty(PROP.owner)) === player.id);
}

// A line of dialogue, if that member of the crew is still with you.
function say(player, type, key, delay = 0) {
  later(delay, () => {
    if (!valid(player) || !crewMember(player, type)) return;
    player.sendMessage({ rawtext: [{ text: `§e<${CREW_NAMES[type]}>§r ` }, { translate: key }] });
  });
}

function spawnCrewMember(player, type, side) {
  const f = facing(player);
  const x = player.location.x + -f.z * side * 1.5;
  const z = player.location.z + f.x * side * 1.5;
  const y = groundAt(player.dimension, x, z, player.location.y) ?? player.location.y;
  const member = tryRun(() => player.dimension.spawnEntity(type, { x, y, z }));
  if (!member) return;
  member.nameTag = CREW_NAMES[type];
  tryRun(() => member.setDynamicProperty(PROP.owner, player.id));
}

function startDocumentary(player) {
  setProp(player, PROP.crew, true);
  setProp(player, PROP.joshGone, false);
  spawnCrewMember(player, MOB.josh, 1);
  spawnCrewMember(player, MOB.mike, -1);
  player.sendMessage(tr("bw.msg.crew_start"));
  say(player, MOB.josh, "bw.crew.josh.start", 40);
  say(player, MOB.mike, "bw.crew.mike.start", 100);
  tryRun(() => player.dimension.spawnItem(new ItemStack(ITEM.dossier, 1), player.location));
}

function dismissCrew(player) {
  for (const type of [MOB.josh, MOB.mike]) {
    for (const e of player.dimension.getEntities({ type })) {
      if (tryRun(() => e.getDynamicProperty(PROP.owner)) === player.id) tryRun(() => e.triggerEvent("bw:vanish"));
    }
  }
  setProp(player, PROP.crew, false);
  setProp(player, PROP.joshGone, false);
}

// The crew keeps up with whoever is holding the camera.
system.runInterval(() => {
  const dimension = world.getDimension(OVERWORLD);
  const players = world.getAllPlayers();
  for (const type of [MOB.josh, MOB.mike]) {
    for (const member of dimension.getEntities({ type })) {
      const ownerId = tryRun(() => member.getDynamicProperty(PROP.owner));
      const owner = players.find((p) => p.id === ownerId);
      if (!owner || owner.dimension.id !== OVERWORLD) continue;
      if (flatDistance(owner.location, member.location) <= 14) continue;
      const f = facing(owner);
      const x = owner.location.x - f.x * 3 + rand(-1.5, 1.5);
      const z = owner.location.z - f.z * 3 + rand(-1.5, 1.5);
      const y = groundAt(dimension, x, z, owner.location.y) ?? owner.location.y;
      tryRun(() => member.teleport({ x, y, z }));
    }
  }
}, 20);

// --------------------------------------------------------------------------- //
// Signs of the Witch
// --------------------------------------------------------------------------- //

function hangStickFigure(player) {
  const dimension = player.dimension;
  const nearby = dimension.getEntities({ type: MOB.figure, location: player.location, maxDistance: 32 });
  if (nearby.length >= MAX_FIGURES) return;

  for (let attempt = 0; attempt < 10; attempt++) {
    const angle = rand(0, Math.PI * 2);
    const dist = rand(5, 14);
    const x = Math.floor(player.location.x + Math.cos(angle) * dist);
    const z = Math.floor(player.location.z + Math.sin(angle) * dist);
    const top = Math.floor(player.location.y) + 12;
    for (let y = top; y > top - 10; y--) {
      const block = tryRun(() => dimension.getBlock({ x, y, z }));
      if (!block || !block.typeId.includes("leaves")) continue;
      // Work down to the underside of the canopy.
      const below = tryRun(() => dimension.getBlock({ x, y: y - 1, z }));
      if (below?.typeId.includes("leaves")) continue;
      const below2 = tryRun(() => dimension.getBlock({ x, y: y - 2, z }));
      if (!below?.isAir || !below2?.isAir) break;
      const figure = tryRun(() => dimension.spawnEntity(MOB.figure, { x: x + 0.5, y: y - 1.75, z: z + 0.5 }));
      tryRun(() => figure?.setRotation({ x: 0, y: rand(-180, 180) }));
      return;
    }
  }
}

function soundBehind(player, sound, minDist, maxDist, pitch, volume) {
  const f = facing(player);
  const d = rand(minDist, maxDist);
  const location = {
    x: player.location.x - f.x * d + rand(-2, 2),
    y: player.location.y + 1,
    z: player.location.z - f.z * d + rand(-2, 2),
  };
  player.playSound(sound, { location, pitch, volume });
}

function childrenVoices(player) {
  const angle = rand(0, Math.PI * 2);
  const d = rand(10, 18);
  const location = {
    x: player.location.x + Math.cos(angle) * d,
    y: player.location.y + 1,
    z: player.location.z + Math.sin(angle) * d,
  };
  for (let i = 0; i < 3; i++) {
    later(i * 12, () => player.playSound("mob.vex.ambient", { location, pitch: rand(1.0, 1.3), volume: 0.9 }));
  }
  if (Math.random() < 0.4) player.onScreenDisplay.setActionBar(tr("bw.msg.children"));
}

// After Josh is gone, his voice calls from the dark, and once the house has
// appeared, from inside it.
function joshScreams(player) {
  const house = getJson(player, PROP.housePos);
  let location;
  if (house && flatDistance(player.location, house) < 96) {
    location = { x: house.x, y: house.y + 2, z: house.z };
  } else {
    const angle = rand(0, Math.PI * 2);
    location = {
      x: player.location.x + Math.cos(angle) * 20,
      y: player.location.y + 2,
      z: player.location.z + Math.sin(angle) * 20,
    };
  }
  player.playSound("mob.ghast.scream", { location, pitch: rand(1.1, 1.3), volume: 0.7 });
  const dx = location.x - player.location.x;
  const dz = location.z - player.location.z;
  player.sendMessage({ rawtext: [{ translate: "bw.msg.josh_scream", with: { rawtext: [{ translate: compass(dx, dz) }] } }] });
}

function summonWitch(player) {
  const dimension = player.dimension;
  const hunting = dimension.getEntities({ type: MOB.witch, location: player.location, maxDistance: 80 });
  if (hunting.length > 0) return;

  // Always from behind.
  const f = facing(player);
  for (let attempt = 0; attempt < 5; attempt++) {
    const d = rand(22, 30);
    const x = player.location.x - f.x * d + rand(-6, 6);
    const z = player.location.z - f.z * d + rand(-6, 6);
    if (spawnOnGround(dimension, MOB.witch, x, z, player.location.y)) return;
  }
}

function loseMap(player) {
  const container = tryRun(() => player.getComponent("minecraft:inventory").container);
  if (!container) return;
  let lost = false;
  for (let slot = 0; slot < container.size; slot++) {
    if (container.getItem(slot)?.typeId === ITEM.map) {
      container.setItem(slot, undefined);
      lost = true;
    }
  }
  if (!lost) return;
  player.playSound("random.splash", { pitch: 0.8, volume: 0.6 });
  if (crewMember(player, MOB.mike)) say(player, MOB.mike, "bw.crew.mike.map");
  else player.sendMessage(tr("bw.msg.map_lost"));
}

function fogOn(player) {
  if (fogged.has(player.id)) return;
  fogged.add(player.id);
  tryRun(() => player.runCommand("fog @s push bw:curse_fog bw_curse"));
}

function fogOff(player) {
  if (!fogged.has(player.id)) return;
  fogged.delete(player.id);
  tryRun(() => player.runCommand("fog @s remove bw_curse"));
}

function leaveCairns(player) {
  const base = rand(0, Math.PI * 2);
  let placed = 0;
  for (let i = 0; i < 3; i++) {
    const angle = base + (i * Math.PI * 2) / 3;
    const d = rand(2, 3.5);
    const x = player.location.x + Math.cos(angle) * d;
    const z = player.location.z + Math.sin(angle) * d;
    if (spawnOnGround(player.dimension, MOB.cairn, x, z, player.location.y)) placed++;
  }
  if (!placed) return;
  player.sendMessage(tr("bw.msg.cairns"));
  say(player, MOB.mike, "bw.crew.mike.cairns", 60);
}

function leaveBundle(player) {
  const f = facing(player);
  const location = { x: player.location.x + f.x * 1.5, y: player.location.y + 0.5, z: player.location.z + f.z * 1.5 };
  tryRun(() => player.dimension.spawnItem(new ItemStack(ITEM.bundle, 1), location));
  player.sendMessage(tr(getProp(player, PROP.joshGone, false) ? "bw.msg.bundle_josh" : "bw.msg.bundle"));
}

function joshVanishes(player) {
  const josh = crewMember(player, MOB.josh);
  if (!josh) return;
  tryRun(() => josh.triggerEvent("bw:vanish"));
  setProp(player, PROP.joshGone, true);
  player.sendMessage(tr("bw.msg.josh_gone"));
  say(player, MOB.mike, "bw.crew.mike.josh_gone", 80);
}

// Something outside the tent, while you try to sleep.
function shakeTent(player) {
  for (let i = 0; i < 4; i++) {
    later(i * 10, () => soundBehind(player, "mob.zombie.wood", 1, 2, rand(0.6, 0.8), 0.9));
  }
  later(50, () => childrenVoices(player));
  player.onScreenDisplay.setActionBar(tr("bw.msg.tent"));
  say(player, MOB.josh, "bw.crew.josh.tent", 30);
}

// --------------------------------------------------------------------------- //
// Walking in circles
// --------------------------------------------------------------------------- //

function makeCamp(player, day) {
  const camp = { x: Math.floor(player.location.x), y: Math.floor(player.location.y), z: Math.floor(player.location.z), day, loops: 0 };
  const log = fallenLog();
  if (log) {
    for (let dx = -1; dx <= 2; dx++) {
      const y = groundAt(player.dimension, camp.x + dx, camp.z + 2, camp.y);
      if (y !== undefined) tryRun(() => player.dimension.getBlock({ x: camp.x + dx, y, z: camp.z + 2 })?.setPermutation(log));
    }
  }
  setJson(player, PROP.camp, camp);
}

function walkInCircles(player, day) {
  const camp = getJson(player, PROP.camp);
  if (!camp || camp.day !== day || camp.loops >= 2) return;
  if (flatDistance(player.location, camp) < 60 || !inForest(player)) return;

  camp.loops += 1;
  setJson(player, PROP.camp, camp);
  tryRun(() => player.addEffect("blindness", 50, { showParticles: false }));
  later(25, () => {
    const y = groundAt(player.dimension, camp.x + 0.5, camp.z + 0.5, camp.y) ?? camp.y;
    player.teleport({ x: camp.x + 0.5, y, z: camp.z + 0.5 });
    player.sendMessage(tr("bw.msg.circles"));
    say(player, MOB.josh, "bw.crew.josh.circles", 40);
  });
}

// --------------------------------------------------------------------------- //
// The house
// --------------------------------------------------------------------------- //

function basementStone() {
  const r = Math.random();
  if (r < 0.35) return HANDPRINT;
  return r < 0.55 ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone";
}

function groundFloorWall() {
  const r = Math.random();
  if (r < 0.12) return "minecraft:air";
  return r < 0.3 ? HANDPRINT : "minecraft:dark_oak_planks";
}

// A gutted two-storey timber house over a stone basement. Children's
// handprints cover the walls. Steps lead down along the west wall and up
// along the east wall. Returns the house centre or undefined.
function buildHouse(player) {
  const dimension = player.dimension;
  const W = 10; // x extent (0..W)
  const D = 8; // z extent (0..D)
  for (let attempt = 0; attempt < 8; attempt++) {
    const angle = rand(0, Math.PI * 2);
    const d = rand(26, 34);
    const cx = player.location.x + Math.cos(angle) * d;
    const cz = player.location.z + Math.sin(angle) * d;
    const gy = groundAt(dimension, cx, cz, player.location.y);
    if (gy === undefined) continue;

    // Reject slopes: the four corners must be close to the centre height.
    const corners = [[-5, -4], [5, -4], [-5, 4], [5, 4]].map(([ox, oz]) => groundAt(dimension, cx + ox, cz + oz, gy));
    if (corners.some((y) => y === undefined || Math.abs(y - gy) > 3)) continue;

    const ox = Math.floor(cx) - W / 2;
    const oz = Math.floor(cz) - D / 2;
    const floor = gy - 1;
    const at = (x, y, z, type) => place(dimension, ox + x, y, oz + z, type);
    const isEdge = (x, z) => x === 0 || x === W || z === 0 || z === D;
    const isCorner = (x, z) => (x === 0 || x === W) && (z === 0 || z === D);

    // Clear the plot (and any trees) above ground.
    for (let x = -1; x <= W + 1; x++)
      for (let z = -1; z <= D + 1; z++)
        for (let y = floor + 1; y <= floor + 12; y++) at(x, y, z, "minecraft:air");

    for (let x = 0; x <= W; x++)
      for (let z = 0; z <= D; z++) {
        // Basement: stone shell, hollow inside.
        at(x, floor - 5, z, "minecraft:cobblestone");
        for (let y = floor - 4; y < floor; y++) at(x, y, z, isEdge(x, z) ? basementStone() : "minecraft:air");
        // Ground and upper floors.
        at(x, floor, z, "minecraft:dark_oak_planks");
        at(x, floor + 5, z, "minecraft:dark_oak_planks");
        // Roof, partly fallen in.
        if (Math.random() > 0.25) at(x, floor + 9, z, "minecraft:dark_oak_planks");
        if (!isEdge(x, z)) continue;
        for (let y = floor + 1; y <= floor + 4; y++)
          at(x, y, z, isCorner(x, z) ? "minecraft:stripped_dark_oak_log" : groundFloorWall());
        for (let y = floor + 6; y <= floor + 8; y++)
          at(x, y, z, isCorner(x, z) ? "minecraft:stripped_dark_oak_log" : Math.random() < 0.18 ? "minecraft:air" : "minecraft:dark_oak_planks");
      }

    // Doorway (south), windows.
    for (const y of [floor + 1, floor + 2]) at(W / 2, y, D, "minecraft:air");
    at(0, floor + 2, 4, "minecraft:air");
    at(W, floor + 2, 4, "minecraft:air");
    for (const [x, z] of [[0, 2], [0, 6], [W, 4], [W / 2, 0]]) at(x, floor + 7, z, "minecraft:air");

    // Steps down to the basement along the west wall.
    for (let i = 0; i <= 4; i++) {
      const z = 2 + i;
      const stepTop = floor - 1 - i;
      for (let y = floor - 5; y <= stepTop; y++) at(1, y, z, "minecraft:cobblestone");
      for (let y = stepTop + 1; y <= floor; y++) at(1, y, z, "minecraft:air");
    }

    // Steps up to the second floor along the east wall.
    for (let i = 0; i <= 4; i++) {
      const z = 2 + i;
      const stepTop = floor + 1 + i;
      for (let y = floor + 1; y <= stepTop; y++) at(W - 1, y, z, "minecraft:dark_oak_planks");
      for (let y = stepTop + 1; y <= floor + 5; y++) at(W - 1, y, z, "minecraft:air");
    }

    // Cobwebs in the corners.
    for (const [x, y, z] of [
      [1, floor - 1, 1], [W - 1, floor - 1, 1], [W - 1, floor - 1, D - 1], [4, floor - 1, D - 1],
      [1, floor + 4, D - 1], [W - 2, floor + 4, 1], [1, floor + 8, 1], [W - 1, floor + 8, D - 1],
    ]) at(x, y, z, "minecraft:web");

    // Face to the wall, in the far corner of the basement.
    const man = tryRun(() => dimension.spawnEntity(MOB.corner, { x: ox + W - 0.5, y: floor - 4, z: oz + D - 0.5 }));
    tryRun(() => man?.setRotation({ x: 0, y: -45 }));
    if (man && getProp(player, PROP.crew, false)) man.nameTag = "Mike";

    return { x: ox + W / 2, y: floor, z: oz + D / 2 };
  }
  return undefined;
}

function houseAppears(player) {
  const house = buildHouse(player);
  if (!house) return;
  setProp(player, PROP.house, true);
  setJson(player, PROP.housePos, house);
  const dx = house.x - player.location.x;
  const dz = house.z - player.location.z;
  player.sendMessage({ rawtext: [{ translate: "bw.msg.house", with: { rawtext: [{ translate: compass(dx, dz) }] } }] });
  later(60, () => joshScreams(player));

  // Mike hears Josh inside and runs for the house.
  const mike = crewMember(player, MOB.mike);
  if (mike) {
    say(player, MOB.mike, "bw.crew.mike.house", 100);
    later(140, () => {
      tryRun(() => mike.triggerEvent("bw:vanish"));
      player.sendMessage(tr("bw.msg.mike_runs"));
    });
  }
}

function theEnd(player, man) {
  tryRun(() => man.triggerEvent("bw:vanish"));
  tryRun(() => player.addEffect("blindness", 20 * 7, { showParticles: false }));
  tryRun(() => player.addEffect("nausea", 20 * 10, { showParticles: false }));
  tryRun(() => player.addEffect("slowness", 20 * 7, { amplifier: 2, showParticles: false }));
  player.playSound("mob.vex.charge", { pitch: 0.5, volume: 1 });
  player.onScreenDisplay.setTitle(tr("bw.title.end"), {
    subtitle: tr("bw.title.end_sub"),
    fadeInDuration: 0,
    stayDuration: 100,
    fadeOutDuration: 40,
  });

  // The camera falls to the floor, still recording.
  if (heldItem(player)?.typeId === ITEM.camcorder) {
    tryRun(() => player.getComponent("minecraft:inventory").container.setItem(player.selectedSlotIndex, undefined));
    tryRun(() => player.dimension.spawnItem(new ItemStack(ITEM.camcorder, 1), player.location));
  }
  tryRun(() => player.applyDamage(12));

  const name = player.name;
  later(120, () => world.sendMessage(tr("bw.msg.footage", name)));

  dismissCrew(player);
  setProp(player, PROP.nights, 0);
  setProp(player, PROP.house, false);
  setJson(player, PROP.housePos, undefined);
  setJson(player, PROP.camp, undefined);
  fogOff(player);
}

// --------------------------------------------------------------------------- //
// The curse, night by night
// --------------------------------------------------------------------------- //

// Counts tonight once, the first time the player is found among the trees.
// Returns true when tonight is a forest night for this player.
function registerNight(player, day, forest) {
  if (getProp(player, PROP.lastNight, -1) === day) return true;
  if (!forest) return false;

  const nights = getProp(player, PROP.nights, 0) + 1;
  setProp(player, PROP.nights, nights);
  setProp(player, PROP.lastNight, day);
  player.onScreenDisplay.setTitle(tr("bw.title.night", nights), {
    subtitle: tr("bw.title.night_sub"),
    fadeInDuration: 20,
    stayDuration: 60,
    fadeOutDuration: 40,
  });
  if (nights === 1) say(player, MOB.josh, "bw.crew.josh.n1", 100);
  else if (nights === 2) say(player, MOB.mike, "bw.crew.mike.n2", 100);
  else if (nights === 3) say(player, MOB.josh, "bw.crew.josh.n3", 100);
  else if (nights === 4) say(player, MOB.mike, "bw.crew.mike.n4", 100);
  else say(player, MOB.mike, "bw.crew.mike.n5", 100);
  return true;
}

function nightTick(player, day) {
  const forest = inForest(player);
  const tonight = registerNight(player, day, forest);
  if (!tonight || !forest) {
    fogOff(player);
    return;
  }
  const nights = getProp(player, PROP.nights, 0);

  if (Math.random() < 0.35) soundBehind(player, "dig.wood", 4, 9, rand(0.6, 0.8), 1);
  if (Math.random() < 0.3) hangStickFigure(player);

  if (nights >= 3) {
    if (Math.random() < 0.2) childrenVoices(player);
    if (Math.random() < 0.3) summonWitch(player);
  }

  if (nights >= 4) {
    fogOn(player);
    if (Math.random() < 0.15) loseMap(player);
  }

  if (getProp(player, PROP.joshGone, false) && Math.random() < 0.15) joshScreams(player);

  if (nights >= 5 && !getProp(player, PROP.house, false) && Math.random() < 0.25) houseAppears(player);
}

function dayTick(player, day) {
  fogOff(player);
  walkInCircles(player, day);

  if (getProp(player, PROP.lastDawn, -1) === day) return;
  setProp(player, PROP.lastDawn, day);

  const nights = getProp(player, PROP.nights, 0);
  const lastNight = getProp(player, PROP.lastNight, -99);

  if (lastNight === day - 1) {
    // Woke up in the woods.
    if (nights >= 2) leaveCairns(player);
    if (nights >= 3) makeCamp(player, day);
    if (nights >= 4 && !getProp(player, PROP.joshGone, false)) joshVanishes(player);
    if (nights >= 5) leaveBundle(player);
  } else if (lastNight < day - 1 && nights > 0) {
    // A night away from the trees: the curse loosens its grip.
    setProp(player, PROP.nights, nights - 1);
    if (nights - 1 === 0) player.sendMessage(tr("bw.msg.free"));
  }
}

// getDay() ticks over at time 0 (sunrise), so a night belongs to the day it
// started on, and the dawn after it is the first daylight of the next day.
system.runInterval(() => {
  const time = world.getTimeOfDay();
  const day = world.getDay();
  for (const player of world.getAllPlayers()) {
    if (player.dimension.id !== OVERWORLD) {
      fogOff(player);
      continue;
    }
    if (isNightTime(time)) tryRun(() => nightTick(player, day));
    else if (time < NIGHT_START) tryRun(() => dayTick(player, day));
    else fogOff(player);
  }
}, 100);

// Sleeping through the night doesn't get you out of it: the night still
// counts, and something comes to the tent.
system.runInterval(() => {
  const time = world.getTimeOfDay();
  if (!isNightTime(time)) return;
  const day = world.getDay();
  for (const player of world.getAllPlayers()) {
    if (!player.isSleeping || player.dimension.id !== OVERWORLD) continue;
    tryRun(() => {
      if (!registerNight(player, day, inForest(player))) return;
      if (getProp(player, PROP.nights, 0) < 2 || getProp(player, PROP.tentDay, -1) === day) return;
      setProp(player, PROP.tentDay, day);
      shakeTent(player);
    });
  }
}, 20);

// --------------------------------------------------------------------------- //
// The Witch: never seen, never fought
// --------------------------------------------------------------------------- //

function vanish(witch) {
  const at = { x: witch.location.x, y: witch.location.y + 1.2, z: witch.location.z };
  for (let i = 0; i < 6; i++) {
    tryRun(() =>
      witch.dimension.spawnParticle("minecraft:basic_smoke_particle", {
        x: at.x + rand(-0.4, 0.4),
        y: at.y + rand(-0.8, 0.8),
        z: at.z + rand(-0.4, 0.4),
      })
    );
  }
  witchWatched.delete(witch.id);
  tryRun(() => witch.triggerEvent("bw:vanish"));
}

system.runInterval(() => {
  const dimension = world.getDimension(OVERWORLD);
  const witches = dimension.getEntities({ type: MOB.witch });
  if (witches.length === 0) return;
  const players = world.getAllPlayers().filter((p) => p.dimension.id === OVERWORLD);

  for (const witch of witches) {
    if (!valid(witch)) continue;
    const seen = players.some((p) => canSee(p, witch, 40, 0.85));
    const count = seen ? (witchWatched.get(witch.id) ?? 0) + 1 : 0;
    witchWatched.set(witch.id, count);
    if (count >= WITCH_WATCH_CHECKS) {
      for (const p of players) {
        if (flatDistance(p.location, witch.location) < 40) p.playSound("mob.vex.death", { pitch: 0.5, volume: 0.6 });
      }
      vanish(witch);
    }
  }
}, 4);

world.afterEvents.entityHurt.subscribe(({ hurtEntity, damageSource }) => {
  const witch = damageSource?.damagingEntity;
  if (witch?.typeId !== MOB.witch || hurtEntity.typeId !== "minecraft:player") return;
  tryRun(() => hurtEntity.addEffect("darkness", 20 * 6, { showParticles: false }));
  tryRun(() => hurtEntity.addEffect("slowness", 20 * 3, { amplifier: 1, showParticles: false }));
  hurtEntity.onScreenDisplay.setActionBar(tr("bw.msg.touched"));
  vanish(witch);
});

// --------------------------------------------------------------------------- //
// The one in the corner
// --------------------------------------------------------------------------- //

system.runInterval(() => {
  const dimension = world.getDimension(OVERWORLD);
  for (const man of dimension.getEntities({ type: MOB.corner })) {
    if (!valid(man)) continue;
    const player = world
      .getAllPlayers()
      .find((p) => p.dimension.id === OVERWORLD && length(sub(p.location, man.location)) < 2.5);
    if (player) theEnd(player, man);
  }
}, 10);

// --------------------------------------------------------------------------- //
// Items
// --------------------------------------------------------------------------- //

// Night four onwards, crouch and film yourself: the confession to camera.
function apologize(player, day) {
  setProp(player, PROP.apologyDay, day);
  tryRun(() => player.addEffect("slowness", 20 * 16, { amplifier: 3, showParticles: false }));
  for (let i = 0; i < APOLOGY_LINES; i++) {
    later(i * 80, () =>
      player.onScreenDisplay.setTitle(" ", {
        subtitle: tr(`bw.apology.${i + 1}`),
        fadeInDuration: 10,
        stayDuration: 60,
        fadeOutDuration: 10,
      })
    );
  }
}

function useCamcorder(player) {
  if (onCooldown(`cam:${player.id}`, 40)) return;
  player.playSound("random.click", { pitch: 0.6 });

  if (!getProp(player, PROP.crew, false) && player.dimension.id === OVERWORLD) {
    startDocumentary(player);
    return;
  }

  const day = world.getDay();
  if (
    player.isSneaking &&
    isNightTime(world.getTimeOfDay()) &&
    getProp(player, PROP.nights, 0) >= 4 &&
    getProp(player, PROP.apologyDay, -1) !== day
  ) {
    apologize(player, day);
    return;
  }

  tryRun(() => player.addEffect("night_vision", 20 * 30, { showParticles: false }));
  const witchNear = player.dimension.getEntities({ type: MOB.witch, location: player.location, maxDistance: 32 }).length > 0;
  player.onScreenDisplay.setActionBar(tr(witchNear ? "bw.cam.static" : "bw.cam.light"));
}

function useMap(player) {
  if (onCooldown(`map:${player.id}`, 40)) return;
  const spawn = world.getDefaultSpawnLocation();
  const dx = spawn.x - player.location.x;
  const dz = spawn.z - player.location.z;
  const dist = Math.round(Math.hypot(dx, dz));
  player.playSound("item.book.page_turn", { pitch: 0.9 });
  player.sendMessage({
    rawtext: [
      { translate: "bw.map.read", with: { rawtext: [{ text: String(dist) }, { translate: compass(dx, dz) }] } },
    ],
  });
  const nights = getProp(player, PROP.nights, 0);
  if (nights > 0) player.sendMessage(tr("bw.map.nights", nights));
}

function useBundle(player) {
  if (onCooldown(`bundle:${player.id}`, 40)) return;
  player.playSound("dig.wood", { pitch: 0.5 });
  player.sendMessage(tr("bw.bundle.open"));
  player.sendMessage(tr(`bw.note.${1 + Math.floor(Math.random() * NOTES)}`));
}

function showDossier(player, page) {
  const form = new ActionFormData().title(tr("bw.dossier.title")).body(tr(`bw.lore.${page + 1}`));
  const hasNext = page < LORE_PAGES - 1;
  if (hasNext) form.button(tr("bw.dossier.next"));
  form.button(tr("bw.dossier.close"));
  form
    .show(player)
    .then((response) => {
      if (!response.canceled && hasNext && response.selection === 0) showDossier(player, page + 1);
    })
    .catch(() => {});
}

function useDossier(player) {
  if (onCooldown(`dossier:${player.id}`, 20)) return;
  player.playSound("item.book.page_turn", { pitch: 0.8 });
  showDossier(player, 0);
}

function placeEffigy(player, block, face, itemStack) {
  if (onCooldown(`effigy:${player.id}`, 10)) return;
  let location;
  if (face === "Down") {
    location = { x: block.x + 0.5, y: block.y - 1.75, z: block.z + 0.5 };
  } else {
    const offset = { Up: [0, 1, 0], North: [0, 0, -1], South: [0, 0, 1], East: [1, 0, 0], West: [-1, 0, 0] }[face];
    location = { x: block.x + offset[0] + 0.5, y: block.y + offset[1], z: block.z + offset[2] + 0.5 };
  }
  const figure = tryRun(() => player.dimension.spawnEntity(MOB.figure, location));
  if (!figure) return;
  tryRun(() => figure.triggerEvent("bw:persist"));
  tryRun(() => figure.setRotation({ x: 0, y: player.getRotation().y + 180 }));
  player.playSound("dig.wood", { pitch: 1.0 });

  if (tryRun(() => player.getGameMode()) === "creative") return;
  const container = tryRun(() => player.getComponent("minecraft:inventory").container);
  const slot = player.selectedSlotIndex;
  const current = container?.getItem(slot);
  if (current?.typeId !== itemStack.typeId) return;
  if (current.amount > 1) {
    current.amount -= 1;
    container.setItem(slot, current);
  } else {
    container.setItem(slot, undefined);
  }
}

function onUse(player, itemStack) {
  if (!itemStack || player?.typeId !== "minecraft:player") return;
  if (itemStack.typeId === ITEM.camcorder) useCamcorder(player);
  else if (itemStack.typeId === ITEM.map) useMap(player);
  else if (itemStack.typeId === ITEM.bundle) useBundle(player);
  else if (itemStack.typeId === ITEM.dossier) useDossier(player);
}

world.afterEvents.itemUse.subscribe(({ source, itemStack }) => onUse(source, itemStack));
world.afterEvents.itemUseOn.subscribe(({ source, itemStack, block, blockFace }) => {
  if (itemStack?.typeId === ITEM.effigy) placeEffigy(source, block, blockFace, itemStack);
  else onUse(source, itemStack);
});

// Found-footage HUD while the camera is in hand.
system.runInterval(() => {
  for (const player of world.getAllPlayers()) {
    if (heldItem(player)?.typeId !== ITEM.camcorder) {
      recordingSince.delete(player.id);
      continue;
    }
    if (!recordingSince.has(player.id)) recordingSince.set(player.id, system.currentTick);
    if (system.currentTick - (cooldowns.get(`cam:${player.id}`) ?? -Infinity) < 40) continue;

    const secs = Math.floor((system.currentTick - recordingSince.get(player.id)) / 20);
    const pad = (n) => String(n).padStart(2, "0");
    const blink = Math.floor(system.currentTick / 20) % 2 === 0 ? "§c●" : "§4●";
    const text = `${blink} REC§r  ${pad(Math.floor(secs / 3600))}:${pad(Math.floor(secs / 60) % 60)}:${pad(secs % 60)}`;
    const nights = getProp(player, PROP.nights, 0);
    const rawtext = [{ text }];
    if (nights > 0) rawtext.push({ text: "   " }, { translate: "bw.hud.night", with: [String(nights)] });
    player.onScreenDisplay.setActionBar({ rawtext });
  }
}, 10);

// --------------------------------------------------------------------------- //
// First join
// --------------------------------------------------------------------------- //

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (!initialSpawn) return;
  // Fog pushed in an earlier session would otherwise linger.
  tryRun(() => player.runCommand("fog @s remove bw_curse"));
  if (getProp(player, PROP.intro, false)) return;
  setProp(player, PROP.intro, true);
  later(100, () => {
    if (!valid(player)) return;
    player.sendMessage(tr("bw.msg.intro_1"));
    player.sendMessage(tr("bw.msg.intro_2"));
    player.sendMessage(tr("bw.msg.intro_3"));
  });
});
