// Horror Legends: a horror director that slowly turns an ordinary world
// against the player, in the spirit of the classic Java horror mods and
// creepypastas, told in chapters:
//
//   Prologue   sounds that aren't there
//   1          HerobrineGamer788, a player who joins your world
//   2          the Cave Dweller, below
//   3          Herobrine, white eyes at the edge of sight
//   4          the Presence, something in your house
//   5          the Man From The Fog
//   6          null, something broken in the code
//   7          the Red Night
//   The end    five torn pages, a ritual, and Herobrine's true form
//
// lib/        helpers, saved state, tracked creatures, jumpscares
// director.js fear and pacing: when things happen
// threats/    what each of them does
// items/      flashlight, Ward Lantern, pages
// ui/         the Survivor's Journal

import { system, world } from "@minecraft/server";
import { onFirstJoin } from "./ui/welcome.js";
import { later, onCooldown, overworldPlayers, tryRun, valid } from "./lib/util.js";
import { hauntDay } from "./lib/state.js";
import { stateOf, tickActors, tracked, vanish } from "./lib/actors.js";
import { tickHeart } from "./lib/fx.js";
import { announceChapters, onPlayerDied as directorDied, tickDirector } from "./director.js";
import { onBreakBlock, onHit as gamerHit, onPlaceBlock, onPlayerDied as gamerDied, tickGamer } from "./threats/gamer.js";
import { onHit as dwellerHit, onHurt as dwellerHurt } from "./threats/dweller.js";
import { strikesBack } from "./threats/herobrine.js";
import { tickPresence } from "./threats/presence.js";
import { fogManChase, onPlayerSpawn as fogSpawn, tickFog } from "./threats/fog.js";
import { jumpscare, restoreCorruption } from "./threats/null.js";
import { onPlayerSpawn as bloodSpawn, tickBlood } from "./threats/bloodnight.js";
import { onDeath as bossDeath, onHurt as bossHurt, onPlayerDied as bossDied } from "./threats/boss.js";
import { restoreBlocks } from "./lib/saved.js";
import { isLightsGone } from "./threats/presence.js";
import { FLASHLIGHT, cleanupLights, onPlayerLeave, tickBeams, tickFlashlights, toggleFlashlight } from "./items/flashlight.js";
import { onBreak as wardBreak, onPlace as wardPlace } from "./items/ward.js";
import { PAGE_OF, readPage } from "./items/pages.js";
import { openJournal } from "./ui/journal.js";

let started = false;

function worldTick() {
  if (!started) {
    started = true;
    cleanupLights(); // light blocks left by a flashlight when the game closed
  }
  hauntDay();
  tryRun(tickFog);
  tryRun(tickBlood);
  tryRun(() => restoreCorruption());
  tryRun(() => restoreBlocks("hl:lights", isLightsGone));
  tryRun(announceChapters);
}

const heart = () => tryRun(tickHeart);
const lights = () => tryRun(tickFlashlights);
const actors = () => tryRun(tickActors);
const presence = () => tryRun(() => tickPresence(overworldPlayers()));
const gamer = () => tryRun(tickGamer);
const director = () => tryRun(tickDirector);
const beams = () => tryRun(tickBeams);

system.runInterval(heart, 2);
system.runInterval(lights, 3);
system.runInterval(actors, 4);
system.runInterval(presence, 6);
system.runInterval(gamer, 10);
system.runInterval(director, 20);
system.runInterval(beams, 20);
system.runInterval(worldTick, 100);

// ------------------------------------------------------------------------- //
// Items
// ------------------------------------------------------------------------- //

world.afterEvents.itemUse.subscribe(({ source, itemStack }) => {
  if (source?.typeId !== "minecraft:player" || !itemStack) return;
  const id = itemStack.typeId;
  if (id === FLASHLIGHT) {
    if (!onCooldown(`flash:${source.id}`, 5)) toggleFlashlight(source);
  } else if (id === "hl:journal") {
    if (!onCooldown(`journal:${source.id}`, 10)) openJournal(source);
  } else if (PAGE_OF[id] && !onCooldown(`page:${source.id}`, 10)) {
    readPage(source, PAGE_OF[id]);
  }
});

world.afterEvents.playerPlaceBlock.subscribe(({ player, block }) => {
  tryRun(() => wardPlace(block));
  tryRun(() => onPlaceBlock(player, block));
});

world.afterEvents.playerBreakBlock.subscribe(({ player, block, brokenBlockPermutation }) => {
  tryRun(() => wardBreak(block));
  tryRun(() => onBreakBlock(player, brokenBlockPermutation));
});

// ------------------------------------------------------------------------- //
// Fighting back
// ------------------------------------------------------------------------- //

world.afterEvents.entityHitEntity.subscribe(({ damagingEntity: attacker, hitEntity: target }) => {
  if (attacker?.typeId !== "minecraft:player") return;
  if (tryRun(() => gamerHit(attacker, target))) return;
  const s = stateOf(target);
  if (!s) return;
  tryRun(() => {
    if (s.kind === "herobrine") strikesBack(target, attacker);
    else if (s.kind === "null") jumpscare(target, attacker);
    else if (s.kind === "fogMan" && s.mode === "watching") fogManChase(target, attacker);
    else if (s.kind === "dweller") dwellerHit(target, attacker);
  });
});

world.afterEvents.entityHurt.subscribe(({ hurtEntity }) => {
  const s = stateOf(hurtEntity);
  if (!s) return;
  const player = world.getAllPlayers().find((p) => p.id === s.playerId);
  if (!player) return;
  if (s.kind === "dweller") tryRun(() => dwellerHurt(hurtEntity, player));
  else if (s.kind === "boss") tryRun(() => bossHurt(hurtEntity, player));
});

world.afterEvents.entityDie.subscribe(({ deadEntity }) => {
  if (tryRun(() => bossDeath(deadEntity))) return;
  if (deadEntity.typeId !== "minecraft:player") return;
  tryRun(() => directorDied(deadEntity));
  tryRun(() => gamerDied(deadEntity));
  tryRun(() => bossDied(deadEntity));
  // Whatever was after you lets go (the Man From The Fog keeps watching).
  for (const s of [...tracked.values()]) {
    if (s.playerId === deadEntity.id && valid(s.entity) && s.mode !== "watching") vanish(s.entity, false);
  }
});

// ------------------------------------------------------------------------- //
// Joining
// ------------------------------------------------------------------------- //

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (!initialSpawn) return;
  // Fog pushed in an earlier session would otherwise linger.
  tryRun(() => fogSpawn(player));
  tryRun(() => bloodSpawn(player));
  tryRun(() => player.runCommand("fog @s remove hl_boss"));
  later(100, () => tryRun(() => onFirstJoin(player)));
});

world.afterEvents.playerLeave.subscribe(({ playerId }) => tryRun(() => onPlayerLeave(playerId)));
