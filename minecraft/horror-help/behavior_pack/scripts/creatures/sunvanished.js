// Act III, the end: The Sun Vanished. "Help." It is nine in the morning
// and the sun hasn't come up. It isn't an eclipse, there are no clouds; it
// just isn't there. The night goes on and on (the daylight cycle stops),
// and out in the dark there are lights.
//
// The Strobes: machines hanging in the air above the trees, ticking,
// strobing white while they search. If one finds you it turns red. Don't
// look into the red light: it gets into your head, you start saying things
// you didn't say, and then it turns blue, and blue hurts. Get under a roof
// or out of its sight and it loses you.
//
// Live until the sun comes back (four minutes) and there are a few days of
// peace.

import { system, world } from "@minecraft/server";
import {
  OVERWORLD, actionBar, distance, faceTowards, groundSpot, inSight, isIndoors, later, looksAt, overworldPlayers, rand,
  sound, soundAt, soundBehind, tr, tryRun,
} from "../core/util.js";
import { allFor, clearKind, define, spawn, vanish } from "../core/creatures.js";
import { addFear, bump, discover, flags, setWorldProp, worldProp } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { broadcast } from "../core/broadcast.js";
import { nearWard } from "../items/ward.js";

export const LASTS = 20 * 240;
const STROBES = 3;
const FOUND_AFTER = 20; // ticks in plain sight before a Strobe turns red
const LOSES_AFTER = 100; // ticks out of its sight before it lets you go
const WASHED = 40; // ticks of looking into the red light
const BLUE_FOR = 100;
const LINES = ["hh.tsv.1", "hh.tsv.2", "hh.tsv.3", "hh.tsv.4", "hh.tsv.5"];

// One for the whole world.
export const eclipse = { on: false, until: 0 };

function dim() {
  return world.getDimension(OVERWORLD);
}

export function sunVanishes(player, anywhere = false) {
  if (eclipse.on) return false;
  eclipse.on = true;
  eclipse.until = system.currentTick + LASTS;
  flags.sunGone = true;
  setWorldProp("hh:sun_gone", true);
  tryRun(() => dim().runCommand("gamerule dodaylightcycle false"));
  tryRun(() => dim().runCommand("time set midnight"));
  for (const p of overworldPlayers()) {
    tryRun(() => p.runCommand("fog @s push hh:dark hh_dark"));
    broadcast(p, "hh.tsv.title", LINES, { hijack: 4 });
    addFear(p, 30);
  }
  discover("sunvanished");
  later(120, () => spawnStrobe(player));
  return true;
}

// The sun is back.
export function sunReturns(quiet = false) {
  const was = eclipse.on || worldProp("hh:sun_gone", false);
  eclipse.on = false;
  flags.sunGone = false;
  setWorldProp("hh:sun_gone", false);
  tryRun(() => dim().runCommand("gamerule dodaylightcycle true"));
  if (!was) return;
  tryRun(() => dim().runCommand("time set sunrise"));
  clearKind("sunvanished");
  for (const p of overworldPlayers()) {
    tryRun(() => p.runCommand("fog @s remove hh_dark"));
    if (quiet) continue;
    tryRun(() => p.onScreenDisplay.setTitle(tr("hh.tsv.end"), { subtitle: tr("hh.tsv.end_sub"), fadeInDuration: 20, stayDuration: 80, fadeOutDuration: 30 }));
    sound(p, "hh.dawn", 1, 1);
  }
  if (!quiet) {
    bump("wins");
    setWorldProp("hh:peace_until", world.getDay() + 3);
  }
}

function strobesOf(player) {
  return allFor(player).filter((s) => s.kind === "sunvanished");
}

export function spawnStrobe(player) {
  if (!eclipse.on || strobesOf(player).length >= STROBES || nearWard(player.dimension, player.location)) return false;
  const g = groundSpot(player, 18, 28, rand(0, 360), 180);
  if (!g) return false;
  const e = spawn("sunvanished", { x: g.x, y: g.y + rand(4, 7), z: g.z }, player, undefined, {
    light: 0, found: 0, lost: 0, wash: 0, blue: 0, clock: 0,
  });
  if (e) soundAt(player, "hh.strobe", e.location, 1, 1);
  return !!e;
}

// Lights flickering far off over the trees.
export function strobeMinor(player) {
  soundBehind(player, "hh.strobe", rand(30, 45), rand(0.8, 1.0), 1);
  later(30, () => actionBar(player, tr("hh.tsv.minor")));
  addFear(player, 8);
  return true;
}

function setLight(e, s, n) {
  s.light = n;
  tryRun(() => e.setProperty("hh:light", n));
}

// You looked into the red light.
function brainwash(e, s, player) {
  s.wash = 0;
  setLight(e, s, 2);
  s.blue = 0;
  blackout(player, 0.3);
  tryRun(() => player.addEffect("nausea", 240, { showParticles: false }));
  tryRun(() => player.applyDamage(4));
  soundAt(player, "hh.strobe", e.location, 0.6, 1);
  scare(player, " ", tr("hh.tsv.sub"));
  // You hear yourself say things you never said.
  for (let i = 0; i < 3; i++) {
    later(30 + i * 40, () =>
      tryRun(() => player.sendMessage({ rawtext: [{ text: `<${player.name}> ` }, { translate: `hh.tsv.wash.${i}` }] }))
    );
  }
  addFear(player, 30);
}

function tick(e, s, player) {
  if (!eclipse.on && !s.adopted) {
    vanish(e, false);
    return;
  }
  s.clock += 4;
  const d = distance(player.location, e.location);
  if (d > 70 || nearWard(player.dimension, player.location, 6)) {
    vanish(e, false);
    return;
  }
  faceTowards(e, player.location);
  if (s.clock % 40 === 0) soundAt(player, "hh.strobe", e.location, s.light === 1 ? 0.7 : 1, 1);
  const visible = !isIndoors(player) && d < 22 && inSight(player, { x: e.location.x, y: e.location.y - 1.4, z: e.location.z });
  if (s.light === 0) {
    // Searching: drifting closer over the treetops.
    if (d > 8) {
      const k = 0.5 / d;
      tryRun(() => e.teleport({ x: e.location.x + (player.location.x - e.location.x) * k, y: e.location.y, z: e.location.z + (player.location.z - e.location.z) * k }));
    }
    s.found = visible ? s.found + 4 : 0;
    if (s.found >= FOUND_AFTER) {
      setLight(e, s, 1);
      s.lost = 0;
      soundAt(player, "hh.screech", e.location, 0.5, 0.8);
      actionBar(player, tr("hh.tsv.found"));
    }
    return;
  }
  if (s.light === 1) {
    s.lost = visible ? 0 : s.lost + 4;
    if (s.lost >= LOSES_AFTER) {
      setLight(e, s, 0);
      s.found = 0;
      actionBar(player, tr("hh.tsv.lost"));
      return;
    }
    s.wash = visible && looksAt(player, e, 32, 0.93) ? s.wash + 4 : Math.max(0, s.wash - 2);
    if (s.wash >= WASHED) brainwash(e, s, player);
    return;
  }
  // Blue: it hurts, for as long as it can see you.
  s.blue += 4;
  if (visible && d < 16 && s.blue % 20 === 0) tryRun(() => player.applyDamage(2));
  if (s.blue >= BLUE_FOR) setLight(e, s, 0);
}

// Every second, from main.js.
export function tickEclipse() {
  if (!eclipse.on) {
    // The world was closed while the sun was gone: give it back.
    if (worldProp("hh:sun_gone", false)) sunReturns(true);
    return;
  }
  if (system.currentTick >= eclipse.until) {
    sunReturns();
    return;
  }
  for (const p of overworldPlayers()) {
    if (!isIndoors(p) && Math.random() < 0.15) tryRun(() => spawnStrobe(p));
  }
}

export function onPlayerSpawn(player) {
  if (eclipse.on) tryRun(() => player.runCommand("fog @s push hh:dark hh_dark"));
  else tryRun(() => player.runCommand("fog @s remove hh_dark"));
}

define({
  kind: "sunvanished",
  type: "hh:strobe",
  weights: (ctx) => (ctx.under ? [0, 0] : [1, 2]),
  minor: strobeMinor,
  major: (player) => sunVanishes(player),
  tick,
  adopt: () => undefined,
  onHit: (e, s, player) => {
    // It doesn't feel it. It knows where you are now.
    if (s.light === 0) setLight(e, s, 1);
  },
  off: () => sunReturns(true),
  test: (player) => sunVanishes(player, true) || spawnStrobe(player),
});
