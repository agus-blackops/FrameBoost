// The director: decides what happens to each player, and when.
//
// Every player has a fear level (0-100). It drifts towards what their
// surroundings deserve (night, caves, fog, a Red Night, low health,
// something nearby; a Ward Lantern calms it) and every scare pushes it up.
// The director paces the horror like a film:
//
//   calm   rare small things; when fear reaches 30 (or after a few quiet
//          minutes) the tension builds
//   build  small scares every half minute or so: sounds, signs, glimpses
//   peak   when fear reaches 65 (or the build-up has gone on long enough),
//          one big encounter chosen for where you are
//   relax  a few minutes to breathe, then it starts again
//
// Chapters decide which threats are in the pool.

import { system } from "@minecraft/server";
import { isIndoors, isNight, isUnderground, overworldPlayers, pick, randInt, sound, tr, tryRun } from "./lib/util.js";
import {
  active, addFear, chapterIndex, flags, getFear, intensity, setFear, setWorldProp, worldProp,
} from "./lib/state.js";
import { allFor, encounterActive } from "./lib/actors.js";
import { nearWard } from "./items/ward.js";
import { ambience } from "./threats/ambience.js";
import { fakeAlive, gamerJoin } from "./threats/gamer.js";
import { dwellerMajor, dwellerMinor } from "./threats/dweller.js";
import { herobrineMajor, herobrineMinor } from "./threats/herobrine.js";
import { presenceMajor, presenceMinor } from "./threats/presence.js";
import { fog, fogMajor, fogMinor } from "./threats/fog.js";
import { nullMajor, nullMinor } from "./threats/null.js";

const SECOND = 20;
export const pace = new Map(); // playerId -> { phase, since, next }

export function baseline(player) {
  let b = 5;
  const under = isUnderground(player);
  if (isNight()) b += 15;
  if (under) b += 20;
  if (fog.active && !under) b += 12;
  if (flags.bloodNight) b += 25;
  const health = tryRun(() => player.getComponent("minecraft:health"));
  if (health && health.currentValue < health.effectiveMax * 0.4) b += 10;
  if (allFor(player).length) b += 20;
  if (nearWard(player.dimension, player.location)) b -= 30;
  return Math.max(0, Math.min(95, b));
}

const MINOR = {
  ambience: ambience,
  herobrine: herobrineMinor,
  dweller: dwellerMinor,
  presence: presenceMinor,
  fog: fogMinor,
  null: nullMinor,
  gamer: gamerJoin,
};

const MAJOR = {
  dweller: dwellerMajor,
  fog: fogMajor,
  herobrine: herobrineMajor,
  presence: presenceMajor,
  null: nullMajor,
};

export function minorEvent(player) {
  const under = isUnderground(player);
  const night = isNight();
  const choice = pick([
    ["ambience", active("ambience") ? 4 : 0],
    ["herobrine", active("herobrine") ? 3 : 0],
    ["dweller", active("dweller") && under ? 5 : 0],
    ["presence", active("presence") && (night || isIndoors(player)) ? 3 : 0],
    ["fog", active("fog") && !under && !fog.active ? 1 : 0],
    ["null", active("null") ? 2 : 0],
    ["gamer", active("gamer") && !fakeAlive() ? 1 : 0],
  ]);
  return !!(choice && tryRun(() => MINOR[choice](player)));
}

export function majorEvent(player) {
  const under = isUnderground(player);
  const night = isNight();
  const options = [
    ["dweller", active("dweller") && under ? 6 : 0],
    ["fog", active("fog") && fog.active && !under ? 5 : 0],
    ["herobrine", active("herobrine") && !under ? 3 : 0],
    ["presence", active("presence") && (night || isIndoors(player)) ? 4 : 0],
    ["null", active("null") ? 2 : 0],
  ].filter(([, w]) => w > 0);
  // The favourite first; if it can't happen here (no lights to put out, no
  // cave to crawl out of), any of the others.
  while (options.length) {
    const choice = pick(options);
    if (tryRun(() => MAJOR[choice](player))) return true;
    options.splice(options.findIndex(([k]) => k === choice), 1);
  }
  return false;
}

function phase(s, name) {
  s.phase = name;
  s.since = system.currentTick;
}

function direct(player) {
  const now = system.currentTick;
  const P = intensity().pace * (isNight() || flags.bloodNight ? 0.7 : 1);
  const s = pace.get(player.id) ?? { phase: "calm", since: now, next: now + 60 * SECOND * P };
  pace.set(player.id, s);

  // Fear drifts towards what the surroundings deserve.
  const f = getFear(player);
  const base = baseline(player);
  setFear(player, f < base ? Math.min(base, f + 1.5) : Math.max(base, f - (s.phase === "relax" ? 1.5 : 0.35)));
  const fear = getFear(player);

  switch (s.phase) {
    case "calm":
      if (now >= s.next) {
        minorEvent(player);
        s.next = now + randInt(90, 180) * SECOND * P;
      }
      if (fear >= 30 || now - s.since > randInt(150, 240) * SECOND * P) {
        phase(s, "build");
        s.next = now + randInt(10, 25) * SECOND * P;
      }
      break;
    case "build":
      if (now >= s.next) {
        minorEvent(player);
        s.next = now + randInt(20, 45) * SECOND * P;
      }
      if (fear >= 65 || now - s.since > 240 * SECOND * P) {
        if (majorEvent(player)) {
          addFear(player, 10);
          phase(s, "peak");
        } else {
          s.since = now - 180 * SECOND * P; // try again in a minute
        }
      }
      break;
    case "peak":
      if ((!encounterActive(player) && now - s.since > 5 * SECOND) || now - s.since > 120 * SECOND) phase(s, "relax");
      break;
    case "relax":
      if (now - s.since > randInt(120, 240) * SECOND * P) {
        phase(s, "calm");
        s.next = now + randInt(40, 90) * SECOND * P;
      }
      break;
  }
}

// Every 20 ticks.
export function tickDirector() {
  if (flags.boss) return; // nothing interrupts the fight
  for (const player of overworldPlayers()) tryRun(() => direct(player));
}

// When a chapter opens it is announced at nightfall (the first two right
// away): a title, a low drone, and a line in the chat.
export function announceChapters() {
  const n = chapterIndex();
  const shown = worldProp("hl:chapter_shown", -1);
  if (n <= shown || (n >= 2 && !isNight())) return;
  setWorldProp("hl:chapter_shown", n);
  for (const p of overworldPlayers()) {
    tryRun(() =>
      p.onScreenDisplay.setTitle(n === 0 ? tr("hl.j.prologue") : tr("hl.j.chapter_n", String(n)), { subtitle: tr(`hl.ch.${n}.name`), fadeInDuration: 20, stayDuration: 60, fadeOutDuration: 30 })
    );
    sound(p, "hl.drone", 0.8);
    p.sendMessage(tr(`hl.ch.${n}.intro`));
  }
}

export function onPlayerDied(player) {
  setFear(player, 0);
  pace.delete(player.id);
}
