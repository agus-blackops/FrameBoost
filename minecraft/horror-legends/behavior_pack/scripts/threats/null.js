// Chapter 6: null, something broken in the code. It hides in the corner of
// your eye and is gone when you look; left alone it ends up right behind
// you. It writes in the chat, turns blocks into the missing texture, and
// fakes the game freezing. Catch it in the eye twice, or survive it, and it
// drops a page.

import { world } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";
import {
  PROTECTED, block, canSee, distance, faceTowards, facing, groundSpot, later, offsetFromView, onCooldown, pick, rand,
  randInt, sound, tr, tryRun,
} from "../lib/util.js";
import { MOB, registerKind, spawn, trackedFor, vanish } from "../lib/actors.js";
import { addFear, bump, discover } from "../lib/state.js";
import { restoreBlocks, savedBlocks, swapBlock } from "../lib/saved.js";
import { blackout, scare } from "../lib/fx.js";
import { nearWard } from "../items/ward.js";
import { dropPage } from "../items/pages.js";
import { fakeJoin } from "./herobrine.js";

export const CORRUPTED = "hl:corrupted_block";

export function glitch(player) {
  blackout(player);
  tryRun(() => player.onScreenDisplay.setTitle("§k||||||||||||", { fadeInDuration: 0, stayDuration: 6, fadeOutDuration: 2 }));
  sound(player, "hl.static", 1, rand(0.8, 1.1));
}

export function nullAppears(player, mode = "peripheral") {
  if (trackedFor(player, "null") || nearWard(player.dimension, player.location, 16)) return false;
  const at =
    mode === "behind"
      ? groundSpot(player, 2, 3, 180, 15)
      : groundSpot(player, 10, 18, (Math.random() < 0.5 ? -1 : 1) * rand(55, 80), 5);
  return !!(at && spawn(MOB.null, at, player, "null", mode, { major: mode === "behind" }));
}

function caught(e, player) {
  discover("null");
  if (bump("null_seen") >= 2) dropPage(5, { x: e.location.x, y: e.location.y + 0.5, z: e.location.z }, player);
}

export function jumpscare(e, player) {
  const at = { ...e.location };
  vanish(e, false);
  sound(player, "hl.static", 1, 0.7);
  scare(player, "§4§knull", "§8null");
  sound(player, "mob.endermen.scream", 1, 0.5);
  tryRun(() => player.addEffect("nausea", 100, { showParticles: false }));
  tryRun(() => player.applyDamage(4));
  addFear(player, 35);
  discover("null");
  later(60, () => dropPage(5, { x: at.x, y: at.y + 0.5, z: at.z }, player));
}

function tick(e, s, player) {
  if (s.adopted) return;
  const d = distance(player.location, e.location);
  if (s.mode === "peripheral") {
    if (canSee(player, e, 64, 0.9)) {
      glitch(player);
      caught(e, player);
      addFear(player, 12);
      vanish(e, false);
      return;
    }
    if (++s.unseen >= 40) {
      // It gave up waiting to be noticed.
      s.mode = "behind";
      s.major = true;
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
    jumpscare(e, player);
    return;
  }
  if (++s.unseen >= 75) vanish(e, false);
}

// Blocks in front of the player turn into the missing texture for a while.
export function corrupt(player) {
  if (savedBlocks("hl:corrupt").length > 60 || nearWard(player.dimension, player.location)) return false;
  const now = world.getAbsoluteTime();
  const target = randInt(4, 9);
  let done = 0;
  for (let attempt = 0; attempt < 50 && done < target; attempt++) {
    const p = offsetFromView(player, rand(3, 10), rand(-50, 50));
    const b = block(player.dimension, p.x, player.location.y + randInt(-2, 2), p.z);
    if (!b || b.isAir || b.typeId === CORRUPTED || PROTECTED.test(b.typeId)) continue;
    if (!block(player.dimension, b.x, b.y + 1, b.z)?.isAir) continue;
    if (swapBlock("hl:corrupt", b, CORRUPTED, now + randInt(1200, 2400))) done++;
  }
  if (done) sound(player, "hl.static", 0.6, 0.6);
  return done > 0;
}

export function restoreCorruption(force = false) {
  restoreBlocks("hl:corrupt", (type) => type === CORRUPTED, force);
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

export function nullMinor(player) {
  const event = pick([
    ["appear", 30],
    ["corrupt", 25],
    ["chat", 20],
    ["glitch", 12],
    ["crash", 5],
    ["join", 8],
  ]);
  const done = {
    appear: () => nullAppears(player),
    corrupt: () => corrupt(player),
    chat: () => (player.sendMessage({ rawtext: [{ text: "§f<null>§r " }, { translate: `hl.null.chat.${randInt(1, 6)}`, with: [player.name] }] }), true),
    glitch: () => (glitch(player), true),
    crash: () => fakeCrash(player),
    join: () => (fakeJoin("null", player), true),
  }[event]?.();
  if (done) addFear(player, 8);
  return !!done;
}

export function nullMajor(player) {
  return nullAppears(player, "behind");
}

registerKind("null", { tick, adopt: () => "peripheral" });
