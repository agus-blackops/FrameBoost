// There Are Monsters — gameplay scripts.
//
//  * Camcorder: filming a disguised Imposter exposes its real face.
//  * VHS tapes: play back a recovered fragment of footage.
//  * Replacement: whoever an Imposter kills is replaced. Villagers come back
//    as new disguised Imposters; players leave behind a Doppelganger that
//    wears their name.
//  * The Doppelganger only moves while nobody is looking at it.

import { system, world } from "@minecraft/server";

const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];

const CAMCORDER = "tam:camcorder";
const TAPE = "tam:vhs_tape";
const IMPOSTER = "tam:imposter";
const DOPPELGANGER = "tam:doppelganger";

const CAMCORDER_COOLDOWN = 60; // ticks
const CAMCORDER_RANGE = 24;
const WATCH_RANGE = 48;
const WATCH_CONE = 0.8; // cos of the half-angle a player "sees"
const TAPE_COUNT = 8;

const VILLAGERS = new Set(["minecraft:villager_v2", "minecraft:villager", "minecraft:wandering_trader"]);

const lastFilmed = new Map();
const recordingSince = new Map();
const wasRevealed = new Map();

// --------------------------------------------------------------------------- //
// Helpers
// --------------------------------------------------------------------------- //

function valid(entity) {
  if (!entity) return false;
  return typeof entity.isValid === "function" ? entity.isValid() : entity.isValid;
}

function sub(a, b) {
  return { x: a.x - b.x, y: a.y - b.y, z: a.z - b.z };
}

function length(v) {
  return Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
}

function dot(a, b) {
  return a.x * b.x + a.y * b.y + a.z * b.z;
}

function chest(entity) {
  return { x: entity.location.x, y: entity.location.y + 1.4, z: entity.location.z };
}

function tr(key, ...args) {
  return { rawtext: [{ translate: key, with: args.map(String) }] };
}

function tryRun(fn) {
  try {
    return fn();
  } catch {
    return undefined;
  }
}

// True when `target` sits inside the player's view cone with nothing solid
// in between.
function canSee(player, target, range, cone) {
  const eye = player.getHeadLocation();
  const toTarget = sub(chest(target), eye);
  const dist = length(toTarget);
  if (dist > range || dist < 0.01) return false;

  const dir = { x: toTarget.x / dist, y: toTarget.y / dist, z: toTarget.z / dist };
  if (dot(dir, player.getViewDirection()) < cone) return false;

  const hit = tryRun(() =>
    player.dimension.getBlockFromRay(eye, dir, {
      maxDistance: dist,
      includeLiquidBlocks: false,
      includePassableBlocks: false,
    })
  );
  return !hit;
}

function playersIn(dimension) {
  return world.getAllPlayers().filter((p) => p.dimension.id === dimension.id);
}

function heldItem(player) {
  return tryRun(() => {
    const inventory = player.getComponent("minecraft:inventory");
    return inventory.container.getItem(player.selectedSlotIndex);
  });
}

function timecode(ticks) {
  const total = Math.floor(ticks / 20);
  const pad = (n) => String(n).padStart(2, "0");
  return `${pad(Math.floor(total / 3600))}:${pad(Math.floor(total / 60) % 60)}:${pad(total % 60)}`;
}

// --------------------------------------------------------------------------- //
// Camcorder
// --------------------------------------------------------------------------- //

function film(player) {
  const now = system.currentTick;
  if (now - (lastFilmed.get(player.id) ?? -Infinity) < CAMCORDER_COOLDOWN) return;
  lastFilmed.set(player.id, now);

  player.playSound("random.click", { pitch: 0.6 });
  tryRun(() => player.addEffect("night_vision", 20 * 12, { showParticles: false }));

  const nearby = player.dimension.getEntities({
    location: player.location,
    maxDistance: CAMCORDER_RANGE,
  });

  let exposed = 0;
  let danger = false;
  for (const entity of nearby) {
    if (entity.typeId !== IMPOSTER && entity.typeId !== DOPPELGANGER) continue;
    if (!canSee(player, entity, CAMCORDER_RANGE, 0.7)) continue;

    if (entity.matches({ families: ["tam_disguised"] })) {
      entity.triggerEvent("tam:reveal");
      tryRun(() => entity.dimension.spawnParticle("minecraft:villager_angry", chest(entity)));
      exposed++;
    } else {
      danger = true;
    }
  }

  if (exposed > 0) {
    player.onScreenDisplay.setActionBar(tr("tam.camcorder.found", exposed));
    player.playSound("mob.endermen.stare", { pitch: 0.7, volume: 0.8 });
  } else if (danger) {
    player.onScreenDisplay.setActionBar(tr("tam.camcorder.danger"));
  } else {
    player.onScreenDisplay.setActionBar(tr("tam.camcorder.clear"));
  }
}

function playTape(player) {
  const now = system.currentTick;
  if (now - (lastFilmed.get(`tape:${player.id}`) ?? -Infinity) < 40) return;
  lastFilmed.set(`tape:${player.id}`, now);

  const n = 1 + Math.floor(Math.random() * TAPE_COUNT);
  player.playSound("random.click", { pitch: 0.4 });
  player.sendMessage(tr("tam.tape.play"));
  player.sendMessage(tr(`tam.tape.${n}`));
}

function onUse(source, itemStack) {
  if (!itemStack || source?.typeId !== "minecraft:player") return;
  if (itemStack.typeId === CAMCORDER) film(source);
  else if (itemStack.typeId === TAPE) playTape(source);
}

world.afterEvents.itemUse.subscribe(({ source, itemStack }) => onUse(source, itemStack));
// itemUseOn covers clicks aimed at a block; not every API version exposes it.
world.afterEvents.itemUseOn?.subscribe(({ source, itemStack }) => onUse(source, itemStack));

// While a camcorder is held, show a found-footage REC counter.
system.runInterval(() => {
  for (const player of world.getAllPlayers()) {
    const item = heldItem(player);
    if (item?.typeId !== CAMCORDER) {
      recordingSince.delete(player.id);
      continue;
    }
    if (!recordingSince.has(player.id)) recordingSince.set(player.id, system.currentTick);
    // Don't overwrite a fresh result from film().
    if (system.currentTick - (lastFilmed.get(player.id) ?? -Infinity) < 40) continue;
    const blink = Math.floor(system.currentTick / 20) % 2 === 0 ? "§c●" : "§4●";
    const elapsed = system.currentTick - recordingSince.get(player.id);
    player.onScreenDisplay.setActionBar(`${blink} REC§r  ${timecode(elapsed)}`);
  }
}, 10);

// --------------------------------------------------------------------------- //
// Replacement: those the Imposters kill do not stay dead
// --------------------------------------------------------------------------- //

world.afterEvents.entityDie.subscribe(({ deadEntity, damageSource }) => {
  const killer = damageSource?.damagingEntity;
  if (!killer || killer.typeId !== IMPOSTER) return;

  const dimension = deadEntity.dimension;
  const location = { ...deadEntity.location };

  if (VILLAGERS.has(deadEntity.typeId)) {
    system.runTimeout(() => {
      tryRun(() => dimension.spawnEntity(IMPOSTER, location));
    }, 60);
    return;
  }

  if (deadEntity.typeId === "minecraft:player") {
    const name = deadEntity.name;
    world.sendMessage(tr("tam.msg.replaced", name));
    system.runTimeout(() => {
      const copy = tryRun(() => dimension.spawnEntity(DOPPELGANGER, location));
      if (copy) copy.nameTag = name;
    }, 80);
  }
});

// --------------------------------------------------------------------------- //
// The Doppelganger freezes while watched
// --------------------------------------------------------------------------- //

system.runInterval(() => {
  for (const id of DIMENSIONS) {
    const dimension = world.getDimension(id);
    const doubles = dimension.getEntities({ type: DOPPELGANGER });
    if (doubles.length === 0) continue;

    const players = playersIn(dimension);
    for (const double of doubles) {
      if (!valid(double)) continue;
      const watched = players.some((p) => canSee(p, double, WATCH_RANGE, WATCH_CONE));
      const frozen = double.matches({ families: ["tam_frozen"] });
      if (watched && !frozen) double.triggerEvent("tam:freeze");
      else if (!watched && frozen) double.triggerEvent("tam:unfreeze");
    }
  }
}, 4);

// --------------------------------------------------------------------------- //
// Imposters: reveal sting and ambient dread
// --------------------------------------------------------------------------- //

system.runInterval(() => {
  const seen = new Set();
  for (const id of DIMENSIONS) {
    const dimension = world.getDimension(id);
    const imposters = dimension.getEntities({ type: IMPOSTER });
    if (imposters.length === 0) continue;

    const players = playersIn(dimension);
    for (const imposter of imposters) {
      if (!valid(imposter)) continue;
      seen.add(imposter.id);
      const revealed = imposter.matches({ families: ["tam_revealed"] });
      const before = wasRevealed.get(imposter.id);
      wasRevealed.set(imposter.id, revealed);
      if (!revealed || before !== false) continue;

      // Just dropped the disguise.
      for (const player of players) {
        const d = length(sub(player.location, imposter.location));
        if (d > 24) continue;
        player.playSound("mob.endermen.scream", { pitch: 0.5, volume: Math.max(0.3, 1 - d / 24) });
        if (d < 12) player.onScreenDisplay.setActionBar(tr("tam.msg.reveal"));
      }
    }
  }
  for (const key of wasRevealed.keys()) {
    if (!seen.has(key)) wasRevealed.delete(key);
  }
}, 10);

system.runInterval(() => {
  for (const player of world.getAllPlayers()) {
    const near = player.dimension.getEntities({
      location: player.location,
      maxDistance: 20,
      families: ["tam_revealed"],
    });
    if (near.length === 0) continue;
    player.playSound("ambient.cave", { pitch: 0.6, volume: 0.6 });
  }
}, 200);

// --------------------------------------------------------------------------- //
// First join
// --------------------------------------------------------------------------- //

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (!initialSpawn) return;
  const seenIntro = tryRun(() => player.getDynamicProperty("tam:intro"));
  if (seenIntro) return;
  tryRun(() => player.setDynamicProperty("tam:intro", true));
  system.runTimeout(() => {
    if (!valid(player)) return;
    player.sendMessage(tr("tam.msg.intro_1"));
    player.sendMessage(tr("tam.msg.intro_2"));
  }, 100);
});
