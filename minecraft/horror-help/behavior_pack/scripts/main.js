// Horror Legends: Help. Analog horror comes to your world: emergency
// broadcasts, things that are not your neighbours, things in the woods, and
// in the end the morning when the sun doesn't come up. Three acts:
//
//   Act I    The Signal   Local 58's Weather Service, the Mandela Catalogue's
//                         Intruder Alert and its Alternates
//   Act II   The Woods    Siren Head, Cartoon Cat, the Whistle Occurrence
//   Act III  Help.        Gabriel, and The Sun Vanished
//
// core/       helpers, saved state, the creature registry, jumpscares, the
//             emergency broadcasts
// director.js fear and pacing: when things happen, and what, and where
// creatures/  one module per horror (each registers itself)
// events/     the lights going out
// items/      flashlight, Ward Lantern, emergency radio
// ui/         the journal

import { system, world } from "@minecraft/server";
import { later, onCooldown, tryRun, valid } from "./core/util.js";
import { hauntDay } from "./core/store.js";
import { DEFS, stateOf, tickCreatures, tracked, vanish } from "./core/creatures.js";
import { tickHeart } from "./core/fx.js";
import { restoreBlocks } from "./core/blocks.js";
import { announce, onPlayerDied as directorDied, tickDirector } from "./director.js";
import "./creatures/ambience.js";
import "./creatures/scares.js";
import "./creatures/local58.js";
import "./creatures/intruder.js";
import "./creatures/alternate.js";
import "./creatures/siren.js";
import "./creatures/cartoon.js";
import "./creatures/whistle.js";
import "./creatures/gabriel.js";
import { onPlayerSpawn as sunSpawn, tickEclipse } from "./creatures/sunvanished.js";
import { isLightsGone } from "./events/lights.js";
import { FLASHLIGHT, cleanupLights, onPlayerLeave, tickBeams, tickFlashlights, toggleFlashlight } from "./items/flashlight.js";
import { onBreak as wardBreak, onPlace as wardPlace } from "./items/ward.js";
import { RADIO, tuneIn } from "./items/radio.js";
import { openJournal } from "./ui/journal.js";
import { onFirstJoin } from "./ui/welcome.js";

let started = false;

function worldTick() {
  if (!started) {
    started = true;
    cleanupLights(); // light blocks left by a flashlight when the game closed
  }
  hauntDay();
  tryRun(() => restoreBlocks("hh:lights", isLightsGone));
  tryRun(announce);
}

system.runInterval(() => tryRun(tickHeart), 2);
system.runInterval(() => tryRun(tickFlashlights), 3);
system.runInterval(() => tryRun(tickCreatures), 4);
system.runInterval(() => tryRun(tickDirector), 20);
system.runInterval(() => tryRun(tickBeams), 20);
system.runInterval(() => tryRun(tickEclipse), 20);
system.runInterval(worldTick, 100);

// ------------------------------------------------------------------------- //
// Items
// ------------------------------------------------------------------------- //

world.afterEvents.itemUse.subscribe(({ source, itemStack }) => {
  if (source?.typeId !== "minecraft:player" || !itemStack) return;
  const id = itemStack.typeId;
  if (id === FLASHLIGHT) {
    if (!onCooldown(`flash:${source.id}`, 5)) toggleFlashlight(source);
  } else if (id === RADIO) {
    if (!onCooldown(`radio:${source.id}`, 40)) tuneIn(source);
  } else if (id === "hh:journal") {
    if (!onCooldown(`journal:${source.id}`, 10)) openJournal(source);
  }
});

world.afterEvents.playerPlaceBlock.subscribe(({ block }) => tryRun(() => wardPlace(block)));
world.afterEvents.playerBreakBlock.subscribe(({ block }) => tryRun(() => wardBreak(block)));

// ------------------------------------------------------------------------- //
// Fighting back, and being hit
// ------------------------------------------------------------------------- //

function playerOf(s) {
  return world.getAllPlayers().find((p) => p.id === s.playerId);
}

world.afterEvents.entityHitEntity.subscribe(({ damagingEntity: attacker, hitEntity: target }) => {
  if (attacker?.typeId === "minecraft:player") {
    const s = stateOf(target);
    if (s) tryRun(() => DEFS[s.kind]?.onHit?.(target, s, attacker));
    return;
  }
  // Something of ours landed a blow.
  const s = stateOf(attacker);
  if (s && target?.typeId === "minecraft:player") tryRun(() => DEFS[s.kind]?.onStrike?.(attacker, s, target));
});

world.afterEvents.entityHurt.subscribe(({ hurtEntity }) => {
  const s = stateOf(hurtEntity);
  const player = s && playerOf(s);
  if (player) tryRun(() => DEFS[s.kind]?.onHurt?.(hurtEntity, s, player));
});

world.afterEvents.entityDie.subscribe(({ deadEntity }) => {
  if (deadEntity.typeId !== "minecraft:player") {
    tracked.delete(deadEntity.id);
    return;
  }
  tryRun(() => directorDied(deadEntity));
  // Whatever was after you lets go.
  for (const s of [...tracked.values()]) {
    if (s.playerId === deadEntity.id && valid(s.entity)) vanish(s.entity, false);
  }
});

// ------------------------------------------------------------------------- //
// Joining
// ------------------------------------------------------------------------- //

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (!initialSpawn) return;
  // The dark of a vanished sun, pushed in an earlier session.
  tryRun(() => sunSpawn(player));
  later(100, () => tryRun(() => onFirstJoin(player)));
});

world.afterEvents.playerLeave.subscribe(({ playerId }) => tryRun(() => onPlayerLeave(playerId)));
