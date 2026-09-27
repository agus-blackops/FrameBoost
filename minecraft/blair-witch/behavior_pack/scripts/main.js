// The Blair Witch — gameplay scripts.
//
// Every night a player spends in a forest deepens the curse:
//
//   night 1+  twigs snap behind you; stick figures hang in the trees
//   night 2+  at dawn, three piles of stones stand around you
//   night 3+  children's voices in the dark; the Witch starts to hunt you
//   night 4+  a black fog closes in; your map disappears
//   night 5+  a bundle of twigs at dawn; a derelict house among the trees,
//             and in its basement a man standing in the corner
//
// Nights spent outside the forest let the curse fade, one night at a time.
// The Witch is never seen: look straight at her and she is gone.

import { ItemStack, system, world } from "@minecraft/server";

const OVERWORLD = "minecraft:overworld";

const ITEM = {
  camcorder: "bw:camcorder",
  map: "bw:map",
  bundle: "bw:twig_bundle",
  effigy: "bw:twig_effigy",
};
const MOB = {
  witch: "bw:witch",
  figure: "bw:stick_figure",
  cairn: "bw:rock_cairn",
  corner: "bw:corner_man",
};
const PROP = {
  nights: "bw:nights",
  lastNight: "bw:last_night",
  lastDawn: "bw:last_dawn",
  house: "bw:house",
  intro: "bw:intro",
};

const MAX_FIGURES = 10;
const WITCH_WATCH_CHECKS = 2; // consecutive 4-tick checks before she vanishes
const TAPE_NOTES = 6;

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

function onCooldown(key, ticks) {
  const now = system.currentTick;
  if (now - (cooldowns.get(key) ?? -Infinity) < ticks) return true;
  cooldowns.set(key, now);
  return false;
}

function heldItem(player) {
  return tryRun(() => {
    const inventory = player.getComponent("minecraft:inventory");
    return inventory.container.getItem(player.selectedSlotIndex);
  });
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

function snapTwig(player) {
  const f = facing(player);
  const d = rand(4, 9);
  const location = {
    x: player.location.x - f.x * d + rand(-2, 2),
    y: player.location.y + 1,
    z: player.location.z - f.z * d + rand(-2, 2),
  };
  player.playSound("dig.wood", { location, pitch: rand(0.6, 0.8), volume: 1 });
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
    system.runTimeout(() => {
      if (valid(player)) player.playSound("mob.vex.ambient", { location, pitch: rand(1.0, 1.3), volume: 0.9 });
    }, i * 12);
  }
  if (Math.random() < 0.4) player.onScreenDisplay.setActionBar(tr("bw.msg.children"));
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
  if (lost) {
    player.sendMessage(tr("bw.msg.map_lost"));
    player.playSound("random.splash", { pitch: 0.8, volume: 0.6 });
  }
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
  if (placed) player.sendMessage(tr("bw.msg.cairns"));
}

function leaveBundle(player) {
  const f = facing(player);
  const location = { x: player.location.x + f.x * 1.5, y: player.location.y + 0.5, z: player.location.z + f.z * 1.5 };
  tryRun(() => player.dimension.spawnItem(new ItemStack(ITEM.bundle, 1), location));
  player.sendMessage(tr("bw.msg.bundle"));
}

// --------------------------------------------------------------------------- //
// The house
// --------------------------------------------------------------------------- //

function place(dimension, x, y, z, type) {
  tryRun(() => dimension.getBlock({ x, y, z })?.setType(type));
}

function wallBlock() {
  return Math.random() < 0.3 ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone";
}

// A two-level ruin: a gutted timber house over a stone basement, reached by a
// run of steps along the west wall. Returns the house origin or undefined.
function buildHouse(player) {
  const dimension = player.dimension;
  for (let attempt = 0; attempt < 8; attempt++) {
    const angle = rand(0, Math.PI * 2);
    const d = rand(26, 34);
    const cx = player.location.x + Math.cos(angle) * d;
    const cz = player.location.z + Math.sin(angle) * d;
    const gy = groundAt(dimension, cx, cz, player.location.y);
    if (gy === undefined) continue;

    // Reject slopes: the four corners must be close to the centre height.
    const corners = [[-4, -4], [4, -4], [-4, 4], [4, 4]].map(([ox, oz]) => groundAt(dimension, cx + ox, cz + oz, gy));
    if (corners.some((y) => y === undefined || Math.abs(y - gy) > 3)) continue;

    const ox = Math.floor(cx) - 4;
    const oz = Math.floor(cz) - 4;
    const floor = gy - 1;

    // Clear the plot (and any trees) above ground.
    for (let x = -1; x <= 9; x++)
      for (let z = -1; z <= 9; z++)
        for (let y = floor + 1; y <= floor + 10; y++) place(dimension, ox + x, y, oz + z, "minecraft:air");

    // Basement: stone shell, hollow inside.
    for (let x = 0; x <= 8; x++)
      for (let z = 0; z <= 8; z++)
        for (let y = floor - 5; y < floor; y++) {
          const edge = x === 0 || x === 8 || z === 0 || z === 8 || y === floor - 5;
          place(dimension, ox + x, y, oz + z, edge ? wallBlock() : "minecraft:air");
        }

    // Ground floor and walls, with gaps where the boards have rotted.
    for (let x = 0; x <= 8; x++)
      for (let z = 0; z <= 8; z++) {
        place(dimension, ox + x, floor, oz + z, "minecraft:dark_oak_planks");
        const edgeX = x === 0 || x === 8;
        const edgeZ = z === 0 || z === 8;
        if (!edgeX && !edgeZ) continue;
        for (let y = floor + 1; y <= floor + 4; y++) {
          const type = edgeX && edgeZ ? "minecraft:stripped_dark_oak_log" : Math.random() < 0.14 ? "minecraft:air" : "minecraft:dark_oak_planks";
          place(dimension, ox + x, y, oz + z, type);
        }
      }

    // Roof, partly fallen in.
    for (let x = 0; x <= 8; x++)
      for (let z = 0; z <= 8; z++)
        if (Math.random() > 0.22) place(dimension, ox + x, floor + 5, oz + z, "minecraft:dark_oak_planks");

    // Doorway (south) and windows (east, west).
    for (const y of [floor + 1, floor + 2]) place(dimension, ox + 4, y, oz + 8, "minecraft:air");
    place(dimension, ox, floor + 2, oz + 4, "minecraft:air");
    place(dimension, ox + 8, floor + 2, oz + 4, "minecraft:air");

    // Steps down to the basement along the west wall.
    for (let i = 0; i <= 4; i++) {
      const z = oz + 2 + i;
      const stepTop = floor - 1 - i;
      for (let y = floor - 5; y <= stepTop; y++) place(dimension, ox + 1, y, z, "minecraft:cobblestone");
      for (let y = stepTop + 1; y <= floor; y++) place(dimension, ox + 1, y, z, "minecraft:air");
    }

    // Cobwebs in the corners.
    for (const [x, y, z] of [[1, floor - 1, 1], [7, floor - 1, 1], [7, floor - 1, 7], [3, floor - 1, 7], [1, floor + 4, 7], [7, floor + 4, 1]])
      place(dimension, ox + x, y, oz + z, "minecraft:web");

    // Face to the wall, in the far corner of the basement.
    const man = tryRun(() => dimension.spawnEntity(MOB.corner, { x: ox + 7.5, y: floor - 4, z: oz + 7.5 }));
    tryRun(() => man?.setRotation({ x: 0, y: -45 }));

    return { x: ox + 4, z: oz + 4 };
  }
  return undefined;
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
  const held = heldItem(player);
  if (held?.typeId === ITEM.camcorder) {
    tryRun(() => player.getComponent("minecraft:inventory").container.setItem(player.selectedSlotIndex, undefined));
    tryRun(() => player.dimension.spawnItem(new ItemStack(ITEM.camcorder, 1), player.location));
  }
  tryRun(() => player.applyDamage(12));

  const name = player.name;
  system.runTimeout(() => world.sendMessage(tr("bw.msg.footage", name)), 120);

  setProp(player, PROP.nights, 0);
  setProp(player, PROP.house, false);
  fogOff(player);
}

// --------------------------------------------------------------------------- //
// The curse, night by night
// --------------------------------------------------------------------------- //

function nightTick(player, day) {
  const forest = inForest(player);
  let nights = getProp(player, PROP.nights, 0);

  if (forest && getProp(player, PROP.lastNight, -1) !== day) {
    nights += 1;
    setProp(player, PROP.nights, nights);
    setProp(player, PROP.lastNight, day);
    player.onScreenDisplay.setTitle(tr("bw.title.night", nights), {
      subtitle: tr("bw.title.night_sub"),
      fadeInDuration: 20,
      stayDuration: 60,
      fadeOutDuration: 40,
    });
  }

  if (!forest || getProp(player, PROP.lastNight, -1) !== day) {
    fogOff(player);
    return;
  }

  if (Math.random() < 0.35) snapTwig(player);
  if (Math.random() < 0.3) hangStickFigure(player);

  if (nights >= 3) {
    if (Math.random() < 0.2) childrenVoices(player);
    if (Math.random() < 0.3) summonWitch(player);
  }

  if (nights >= 4) {
    fogOn(player);
    if (Math.random() < 0.15) loseMap(player);
  }

  if (nights >= 5 && !getProp(player, PROP.house, false) && Math.random() < 0.25) {
    const house = buildHouse(player);
    if (house) {
      setProp(player, PROP.house, true);
      const dx = house.x - player.location.x;
      const dz = house.z - player.location.z;
      player.sendMessage({ rawtext: [{ translate: "bw.msg.house", with: { rawtext: [{ translate: compass(dx, dz) }] } }] });
      player.playSound("mob.vex.ambient", { pitch: 0.9, volume: 0.7 });
    }
  }
}

function dawnTick(player, day) {
  fogOff(player);
  if (getProp(player, PROP.lastDawn, -1) === day) return;
  setProp(player, PROP.lastDawn, day);

  const nights = getProp(player, PROP.nights, 0);
  const lastNight = getProp(player, PROP.lastNight, -99);

  if (lastNight === day - 1) {
    // Woke up in the woods.
    if (nights >= 2) leaveCairns(player);
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
    if (time >= 13000 && time < 23000) tryRun(() => nightTick(player, day));
    else if (time < 13000) tryRun(() => dawnTick(player, day));
    else fogOff(player);
  }
}, 100);

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
// The man in the corner
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

function useCamcorder(player) {
  if (onCooldown(`cam:${player.id}`, 40)) return;
  player.playSound("random.click", { pitch: 0.6 });
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
  player.sendMessage(tr(`bw.note.${1 + Math.floor(Math.random() * TAPE_NOTES)}`));
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
  system.runTimeout(() => {
    if (!valid(player)) return;
    player.sendMessage(tr("bw.msg.intro_1"));
    player.sendMessage(tr("bw.msg.intro_2"));
  }, 100);
});
