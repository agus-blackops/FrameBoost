// The end: the ritual and Herobrine's true form.
//
// With all five pages read, the journal teaches you the ritual: at night,
// under the open sky, you call him by his name. Lightning answers, and the
// Hollow Miner comes: four blocks of burning bone with a pickaxe for a hand.
// He teleports behind you, calls down lightning, summons things from the
// caves and puts out the light. At half health he flies into a rage. Light
// burns him: a flashlight in his face slows him. Kill him and the curse
// sleeps for three days; you keep his pickaxe. Die, or run, or let the sun
// come up, and he goes back into the dark until you call him again.

import { system, world } from "@minecraft/server";
import {
  actionBar, distance, facing, give, groundAt, groundSpot, isIndoors, isNight, isUnderground, later, offsetFromView,
  onCooldown, pick, rand, randInt, sound, soundAt, tr, tryRun, valid,
} from "../lib/util.js";
import { MOB, clearKind, registerKind, smoke, spawn, tracked, vanish } from "../lib/actors.js";
import {
  PAGES, addFear, bump, discover, flags, pagesRead, setPageRead, setWorldProp,
} from "../lib/state.js";
import { blackout, scare } from "../lib/fx.js";
import { spawnDweller } from "./dweller.js";
import { lightsOut } from "./presence.js";

export const REWARD = "hl:hollow_pickaxe";
let fight = null; // { playerId, entity, phase, next, music, starting }

export function fighting() {
  return !!fight;
}

// Why the ritual can't happen right now (a lang key), or "" if it can.
export function ritualBlocker(player) {
  if (pagesRead() < PAGES.length) return "hl.ritual.need_pages";
  if (fight) return "hl.ritual.busy";
  if (!isNight()) return "hl.ritual.need_night";
  if (isUnderground(player) || isIndoors(player)) return "hl.ritual.need_sky";
  return "";
}

function strike(player, x, z) {
  const y = groundAt(player.dimension, x, z, player.location.y) ?? player.location.y;
  tryRun(() => player.dimension.spawnEntity("minecraft:lightning_bolt", { x, y, z }));
}

export function startRitual(player, force = false) {
  const why = force ? "" : ritualBlocker(player);
  if (why) {
    player.sendMessage(tr(why));
    return false;
  }
  fight = { playerId: player.id, entity: undefined, phase: 1, next: 0, music: 0, starting: true };
  flags.boss = true;
  tryRun(() => player.dimension.runCommand("weather thunder 1200"));
  tryRun(() => player.runCommand("fog @s push hl:boss hl_boss"));
  tryRun(() =>
    player.onScreenDisplay.setTitle(tr("hl.ritual.title"), { subtitle: tr("hl.ritual.sub"), fadeInDuration: 10, stayDuration: 50, fadeOutDuration: 20 })
  );
  sound(player, "hl.drone", 1);
  sound(player, "hl.whisper", 1, 0.7);
  for (let k = 0; k < 4; k++) {
    later(20 + k * 12, () => {
      const p = offsetFromView(player, 10, k * 90 + 45);
      strike(player, p.x, p.z);
    });
  }
  later(90, () => {
    if (!fight || !valid(player)) return;
    const at = groundSpot(player, 10, 14, 0, 30) ?? groundSpot(player, 8, 14, 180, 180) ?? { ...player.location };
    const e = spawn(MOB.boss, at, player, "boss", "fighting", { major: true });
    if (!e) {
      fight = null;
      flags.boss = false;
      return;
    }
    strike(player, at.x, at.z);
    fight.entity = e;
    fight.starting = false;
    fight.next = system.currentTick + 100;
    tryRun(() =>
      player.onScreenDisplay.setTitle("§4§lHEROBRINE", { subtitle: tr("hl.boss.sub"), fadeInDuration: 0, stayDuration: 50, fadeOutDuration: 20 })
    );
    scare(player);
    discover("boss");
  });
  return true;
}

function setCast(e, on) {
  tryRun(() => e.setProperty("hl:cast", on));
}

function minions() {
  return [...tracked.values()].filter((s) => s.minion && valid(s.entity));
}

function ability(e, player) {
  const phase = fight.phase;
  // Never the same trick twice in a row.
  const a = pick([
    ["teleport", 3],
    ["bolts", 3],
    ["summon", minions().length < 2 ? 2 : 0],
    ["dark", 2],
    ["roar", phase === 2 ? 3 : 0],
  ].filter(([k]) => k !== fight.last));
  fight.last = a;
  setCast(e, true);
  later(24, () => valid(e) && setCast(e, false));
  if (a === "teleport") {
    // Behind you.
    const f = facing(player);
    const at = { x: player.location.x - f.x * 2, y: player.location.y, z: player.location.z - f.z * 2 };
    smoke(e);
    tryRun(() => e.teleport(at));
    blackout(player, 0.2);
    sound(player, "hl.stinger", 0.6, 1.2);
    later(8, () => valid(e) && distance(e.location, player.location) < 4 && tryRun(() => player.applyDamage(5)));
  } else if (a === "bolts") {
    soundAt(player, "ambient.weather.thunder", player.location, 1.2, 0.7);
    actionBar(player, tr("hl.boss.bolts"));
    for (let k = 0; k < 3; k++) {
      const p = offsetFromView(player, rand(2, 5), rand(0, 360));
      later(20 + k * 6, () => strike(player, p.x, p.z));
    }
  } else if (a === "summon") {
    actionBar(player, tr("hl.boss.summon"));
    sound(player, "hl.scream", 1, 0.6);
    for (let k = 0; k < 2; k++) {
      const at = groundSpot(player, 6, 10, k * 180 + 90, 40);
      if (at) spawnDweller(player, true, { minion: true, at });
    }
  } else if (a === "dark") {
    tryRun(() => player.addEffect("darkness", 100, { showParticles: false }));
    tryRun(() => player.addEffect("blindness", 30, { showParticles: false }));
    tryRun(() => e.addEffect("invisibility", 60, { showParticles: false }));
    sound(player, "hl.whisper", 1, 0.7);
  } else if (a === "roar") {
    sound(player, "hl.scream", 1, 0.5);
    const d = { x: player.location.x - e.location.x, z: player.location.z - e.location.z };
    const n = Math.hypot(d.x, d.z) || 1;
    tryRun(() => player.applyKnockback(d.x / n, d.z / n, 2.4, 0.5));
    tryRun(() => player.addEffect("slowness", 60, { amplifier: 1, showParticles: false }));
  }
}

function enrage(e, player) {
  fight.phase = 2;
  tryRun(() => e.setProperty("hl:phase", 2));
  tryRun(() => e.addEffect("speed", 20 * 600, { amplifier: 1, showParticles: false }));
  tryRun(() =>
    player.onScreenDisplay.setTitle(" ", { subtitle: tr("hl.boss.rage"), fadeInDuration: 0, stayDuration: 40, fadeOutDuration: 20 })
  );
  scare(player);
  sound(player, "hl.scream", 1, 0.45);
  lightsOut(player);
  strike(player, e.location.x, e.location.z);
}

// He goes back into the dark: `reason` picks the message.
export function retreat(reason) {
  if (!fight) return;
  const player = world.getAllPlayers().find((p) => p.id === fight.playerId);
  const e = fight.entity;
  if (valid(e)) {
    tryRun(() => smoke(e));
    vanish(e, false);
  }
  for (const s of minions()) vanish(s.entity);
  fight = null;
  flags.boss = false;
  tryRun(() => world.getDimension("minecraft:overworld").runCommand("weather clear"));
  if (player) {
    tryRun(() => player.runCommand("fog @s remove hl_boss"));
    player.sendMessage(tr(`hl.boss.retreat.${reason}`));
    sound(player, "hl.whisper", 1, 0.6);
  }
}

// He falls. The curse sleeps.
export function victory(player) {
  for (const s of minions()) vanish(s.entity);
  fight = null;
  flags.boss = false;
  tryRun(() => world.getDimension("minecraft:overworld").runCommand("weather clear"));
  for (const kind of ["herobrine", "null", "fogMan", "dweller"]) clearKind(kind);
  if (!player) return;
  tryRun(() => player.runCommand("fog @s remove hl_boss"));
  tryRun(() =>
    player.onScreenDisplay.setTitle(tr("hl.win.title"), { subtitle: tr("hl.win.sub"), fadeInDuration: 20, stayDuration: 100, fadeOutDuration: 40 })
  );
  sound(player, "hl.dawn", 1);
  give(player, REWARD, 1);
  const wins = bump("wins");
  setWorldProp("hl:peace_until", world.getDay() + 3);
  // The next cycle starts over: the pages scatter again.
  for (const n of PAGES) {
    setPageRead(n, false);
    setWorldProp(`hl:page_drop_${n}`, -99);
  }
  setWorldProp("hl:count_stares", 0);
  setWorldProp("hl:count_null_seen", 0);
  later(60, () => player.sendMessage(tr("hl.win.epilogue.1")));
  later(160, () => player.sendMessage(tr("hl.win.epilogue.2")));
  later(260, () => player.sendMessage(tr("hl.win.epilogue.3", String(wins))));
}

function tick(e, s, player) {
  const now = system.currentTick;
  if (!fight || fight.entity?.id !== e.id) {
    // From a spawn egg: the fight starts right here.
    fight = { playerId: player.id, entity: e, phase: 1, next: now + 100, music: 0, starting: false, egg: true };
    flags.boss = true;
  }
  const d = distance(player.location, e.location);
  if (d > 80) return retreat("far");
  if (!isNight() && !fight.egg) return retreat("dawn");
  if (now >= fight.music) {
    sound(player, "hl.boss", 0.9);
    fight.music = now + 240;
  }
  addFear(player, 2);
  const health = tryRun(() => e.getComponent("minecraft:health"));
  if (fight.phase === 1 && health && health.currentValue <= health.effectiveMax * 0.5) enrage(e, player);
  if (now >= fight.next) {
    tryRun(() => ability(e, player));
    fight.next = now + (fight.phase === 1 ? randInt(120, 180) : randInt(70, 110));
  }
}

// In phase 2 he sometimes steps out of the way of your blows.
export function onHurt(e, player) {
  if (!fight || fight.phase !== 2 || Math.random() > 0.15) return;
  const at = groundSpot(player, 5, 8, rand(0, 360), 180);
  if (at) {
    smoke(e);
    tryRun(() => e.teleport(at));
  }
}

// Light burns him.
export function burnWithLight(e, player) {
  if (onCooldown(`burn:${e.id}`, 200)) return;
  tryRun(() => e.addEffect("slowness", 60, { amplifier: 2, showParticles: false }));
  tryRun(() => e.addEffect("weakness", 60, { amplifier: 0, showParticles: false }));
  actionBar(player, tr("hl.flash.boss"));
  soundAt(player, "hl.scream", e.location, 0.7, 0.8);
}

export function onDeath(entity) {
  if (entity.typeId !== MOB.boss) return false;
  const player = fight && world.getAllPlayers().find((p) => p.id === fight.playerId);
  tracked.delete(entity.id);
  victory(player);
  return true;
}

export function onPlayerDied(player) {
  if (fight && fight.playerId === player.id) retreat("died");
}

registerKind("boss", { tick, adopt: () => "fighting" });
