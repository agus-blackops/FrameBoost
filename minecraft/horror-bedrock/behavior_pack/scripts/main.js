// Horror Legends: Bedrock. The legends of Minecraft itself, and the oldest
// creepypastas, come to your world. Three acts:
//
//   Act I    Joined the Game   Green Steve, the Knocker, Smile Dog
//   Act II   The Others        the Lick, the Mimicer, the Boiled One, Jeff
//   Act III  The Pages         Slenderman, and the eight pages
//
// Someone was here before you: what they built is out there (ruins.js), and
// in each place, a page of their diary (items/notes.js).
//
// core/       helpers, saved state, the creature registry, jumpscares
// director.js fear and pacing: when things happen, and what, and where
// creatures/  one module per horror (each registers itself), and the ruins
// events/     the lights going out, the night you go to bed
// items/      flashlight, Ward Lantern, the notes
// ui/         the journal, the introduction

import { world, system } from "@minecraft/server";
import { later, onCooldown, tryRun, valid } from "./core/util.js";
import { hauntDay } from "./core/store.js";
import { DEFS, stateOf, tickCreatures, tracked, vanish } from "./core/creatures.js";
import { tickHeart } from "./core/fx.js";
import { restoreBlocks } from "./core/blocks.js";
import { announce, onPlayerDied as directorDied, tickDirector } from "./director.js";
import "./creatures/ambience.js";
import "./creatures/scares.js";
import "./creatures/ruins.js";
import "./creatures/greensteve.js";
import "./creatures/knocker.js";
import "./creatures/smiledog.js";
import { CROSSES, isCross } from "./creatures/lick.js";
import "./creatures/mimicer.js";
import "./creatures/boiled.js";
import "./creatures/jeff.js";
import { PAGE, clearPages, onPage, onPlayerDied as slenderDied, tickPages } from "./creatures/slender.js";
import { isLightsGone } from "./events/lights.js";
import { tickSleep } from "./events/sleep.js";
import { FLASHLIGHT, cleanupLights, onPlayerLeave, tickBeams, tickFlashlights, toggleFlashlight } from "./items/flashlight.js";
import { onBreak as wardBreak, onPlace as wardPlace } from "./items/ward.js";
import { noteNumber, readNote } from "./items/notes.js";
import { openJournal } from "./ui/journal.js";
import { onFirstJoin } from "./ui/welcome.js";

let started = false;

function worldTick() {
  if (!started) {
    started = true;
    cleanupLights(); // light blocks left by a flashlight when the game closed
    clearPages(); // pages left on the trees when the game closed
  }
  hauntDay();
  tryRun(() => restoreBlocks("hb:lights", isLightsGone));
  tryRun(() => restoreBlocks(CROSSES, isCross));
  tryRun(announce);
}

system.runInterval(() => tryRun(tickHeart), 2);
system.runInterval(() => tryRun(tickFlashlights), 3);
system.runInterval(() => tryRun(tickCreatures), 4);
system.runInterval(() => tryRun(tickDirector), 20);
system.runInterval(() => tryRun(tickBeams), 20);
system.runInterval(() => tryRun(tickSleep), 20);
system.runInterval(() => tryRun(tickPages), 20);
system.runInterval(worldTick, 100);

// ------------------------------------------------------------------------- //
// Items and blocks
// ------------------------------------------------------------------------- //

world.afterEvents.itemUse.subscribe(({ source, itemStack }) => {
  if (source?.typeId !== "minecraft:player" || !itemStack) return;
  const id = itemStack.typeId;
  if (id === FLASHLIGHT) {
    if (!onCooldown(`flash:${source.id}`, 5)) toggleFlashlight(source);
  } else if (id === "hb:journal") {
    if (!onCooldown(`journal:${source.id}`, 10)) openJournal(source);
  } else if (noteNumber(id)) {
    if (!onCooldown(`note:${source.id}`, 10)) readNote(source, noteNumber(id));
  }
});

world.afterEvents.playerPlaceBlock.subscribe(({ block }) => tryRun(() => wardPlace(block)));
world.afterEvents.playerBreakBlock.subscribe(({ player, block, brokenBlockPermutation }) => {
  tryRun(() => wardBreak(block));
  if (brokenBlockPermutation?.type?.id === PAGE) tryRun(() => onPage(player, block));
});

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
  tryRun(() => slenderDied(deadEntity));
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
  later(100, () => tryRun(() => onFirstJoin(player)));
});

world.afterEvents.playerLeave.subscribe(({ playerId }) => tryRun(() => onPlayerLeave(playerId)));
