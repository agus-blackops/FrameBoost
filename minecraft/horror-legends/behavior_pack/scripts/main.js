// Horror Legends: Remastered. A horror director that slowly turns an ordinary world
// against the player, in the spirit of the classic Java horror mods,
// creepypastas and internet legends, told in three acts:
//
//   Act I    Visitors     sounds that aren't there, HerobrineGamer788, the
//                         Cave Dweller, the Mimic, the Weeping Statue
//   Act II   The woods    Herobrine, the Goatman, the Man From The Fog, the
//                         Skinwalker, the Shadow, the Drowned One
//   Act III  The error    null, Entity 303, Siren Head, the Wendigo, the
//                         Red Night
//   The end              seven torn pages, a ritual, and Herobrine's true form
//
// core/       helpers, saved state, the creature registry, jumpscares
// director.js fear and pacing: when things happen, and what, and where
// creatures/  one module per horror (each registers itself)
// events/     the Red Night
// items/      flashlight, Ward Lantern, pages
// ui/         the Survivor's Journal

import { system, world } from "@minecraft/server";
import { later, onCooldown, overworldPlayers, tryRun, valid } from "./core/util.js";
import { hauntDay } from "./core/store.js";
import { DEFS, stateOf, tickCreatures, tracked, vanish } from "./core/creatures.js";
import { tickHeart } from "./core/fx.js";
import { restoreBlocks } from "./core/blocks.js";
import { announce, onPlayerDied as directorDied, tickDirector } from "./director.js";
import "./creatures/ambience.js";
import "./creatures/scares.js";
import { onBreakBlock, onHit as gamerHit, onPlaceBlock, onPlayerDied as gamerDied, tickGamer } from "./creatures/gamer.js";
import "./creatures/dweller.js";
import "./creatures/mimic.js";
import "./creatures/statue.js";
import "./creatures/herobrine.js";
import "./creatures/goatman.js";
import { onPlayerSpawn as fogSpawn, tickFog } from "./creatures/fog.js";
import "./creatures/skinwalker.js";
import { isLightsGone, tickPresence } from "./creatures/shadow.js";
import "./creatures/drowned.js";
import { restoreCorruption } from "./creatures/null.js";
import "./creatures/e303.js";
import "./creatures/siren.js";
import "./creatures/wendigo.js";
import { onDeath as bossDeath, onPlayerDied as bossDied } from "./creatures/boss.js";
import { onPlayerSpawn as redSpawn, tickBlood } from "./events/rednight.js";
import { FLASHLIGHT, cleanupLights, onPlayerLeave, tickBeams, tickFlashlights, toggleFlashlight } from "./items/flashlight.js";
import { onBreak as wardBreak, onPlace as wardPlace } from "./items/ward.js";
import { PAGE_OF, readPage } from "./items/pages.js";
import { openJournal } from "./ui/journal.js";
import { onFirstJoin } from "./ui/welcome.js";

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
  tryRun(announce);
}

const heart = () => tryRun(tickHeart);
const lights = () => tryRun(tickFlashlights);
const creatures = () => tryRun(tickCreatures);
const presence = () => tryRun(() => tickPresence(overworldPlayers()));
const gamer = () => tryRun(tickGamer);
const director = () => tryRun(tickDirector);
const beams = () => tryRun(tickBeams);

system.runInterval(heart, 2);
system.runInterval(lights, 3);
system.runInterval(creatures, 4);
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
// Fighting back, and being hit
// ------------------------------------------------------------------------- //

function playerOf(s) {
  return world.getAllPlayers().find((p) => p.id === s.playerId);
}

world.afterEvents.entityHitEntity.subscribe(({ damagingEntity: attacker, hitEntity: target }) => {
  if (attacker?.typeId === "minecraft:player") {
    if (tryRun(() => gamerHit(attacker, target))) return;
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

// The Mimic's lid, opened by a curious hand.
world.afterEvents.dataDrivenEntityTrigger.subscribe(({ entity, eventId }) => {
  const s = stateOf(entity);
  const player = s && playerOf(s);
  if (player) tryRun(() => DEFS[s.kind]?.onTrigger?.(entity, s, player, eventId));
});

world.afterEvents.entityDie.subscribe(({ deadEntity }) => {
  if (tryRun(() => bossDeath(deadEntity))) return;
  if (deadEntity.typeId !== "minecraft:player") {
    tracked.delete(deadEntity.id);
    return;
  }
  tryRun(() => directorDied(deadEntity));
  tryRun(() => gamerDied(deadEntity));
  tryRun(() => bossDied(deadEntity));
  // Whatever was after you lets go (the Man From The Fog keeps watching).
  for (const s of [...tracked.values()]) {
    if (s.playerId === deadEntity.id && valid(s.entity) && s.mode !== "watch") vanish(s.entity, false);
  }
});

// ------------------------------------------------------------------------- //
// Joining
// ------------------------------------------------------------------------- //

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (!initialSpawn) return;
  // Fog pushed in an earlier session would otherwise linger.
  tryRun(() => fogSpawn(player));
  tryRun(() => redSpawn(player));
  tryRun(() => player.runCommand("fog @s remove hl_boss"));
  later(100, () => tryRun(() => onFirstJoin(player)));
});

world.afterEvents.playerLeave.subscribe(({ playerId }) => tryRun(() => onPlayerLeave(playerId)));
