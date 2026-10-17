// The director: decides what happens to each player, and when.
//
// Every player has a fear level (0-100). It drifts towards what their
// surroundings deserve (night, caves, fog, a Red Night, low health,
// something nearby; a Ward Lantern calms it) and every scare pushes it up.
// The director paces the horror like a film:
//
//   calm   rare small things; when fear reaches 30 (or after a few quiet
//          minutes) the tension builds
//   build  small scares every half minute or so
//   peak   when fear reaches 65 (or the build-up has gone on long enough),
//          one real encounter
//   relax  a few minutes to breathe, then it starts again
//
// What happens is chosen by where you are: every creature in the registry
// says how much it wants to happen here (in a cave, in the woods, in the
// snow, by the water, at home, in the open) and only those whose act has
// begun are in the pool.

import { system } from "@minecraft/server";
import { overworldPlayers, pick, randInt, sound, tr, tryRun } from "./core/util.js";
import {
  THREATS, actIndex, active, addFear, arrivalDay, flags, getFear, intensity, setFear, setWorldProp, unlocked, worldProp,
} from "./core/store.js";
import { DEFS, allFor, encounterActive } from "./core/creatures.js";
import { context } from "./core/hunt.js";
import { nearWard } from "./items/ward.js";
import { fog } from "./creatures/fog.js";

const SECOND = 20;
export const pace = new Map(); // playerId -> { phase, since, next }

export function baseline(player, ctx = context(player)) {
  let b = 5;
  if (ctx.night) b += 15;
  if (ctx.under) b += 20;
  if (fog.active && !ctx.under) b += 12;
  if (ctx.swimming) b += 8;
  if (flags.redNight) b += 25;
  const health = tryRun(() => player.getComponent("minecraft:health"));
  if (health && health.currentValue < health.effectiveMax * 0.4) b += 10;
  if (allFor(player).length) b += 20;
  if (nearWard(player.dimension, player.location)) b -= 30;
  return Math.max(0, Math.min(95, b));
}

function pool(player, which) {
  const ctx = context(player);
  const options = [];
  for (const [kind, def] of Object.entries(DEFS)) {
    if (!def[which] || !THREATS.includes(kind) || !active(kind)) continue;
    const w = tryRun(() => def.weights?.(ctx))?.[which === "minor" ? 0 : 1] ?? 0;
    if (w > 0) options.push([kind, w]);
  }
  return { ctx, options };
}

// The favourite first; if it can't happen here, any of the others.
function happen(player, which) {
  const { ctx, options } = pool(player, which);
  while (options.length) {
    const kind = pick(options);
    if (tryRun(() => DEFS[kind][which](player, ctx))) return kind;
    options.splice(options.findIndex(([k]) => k === kind), 1);
  }
  return undefined;
}

export const minorEvent = (player) => happen(player, "minor");
export const majorEvent = (player) => happen(player, "major");

function phase(s, name) {
  s.phase = name;
  s.since = system.currentTick;
}

function direct(player) {
  const now = system.currentTick;
  const ctx = context(player);
  const P = intensity().pace * (ctx.night ? 0.7 : 1);
  const s = pace.get(player.id) ?? { phase: "calm", since: now, next: now + 60 * SECOND * P };
  pace.set(player.id, s);

  // Fear drifts towards what the surroundings deserve.
  const f = getFear(player);
  const base = baseline(player, ctx);
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

// Each act is announced at nightfall (the first one right away): a title,
// a low drone, a line in the chat. And each new horror, the night it
// arrives, gets a line of its own.
export function announce() {
  const players = overworldPlayers();
  if (!players.length) return;
  const night = !!tryRun(() => context(players[0]).night);
  const n = actIndex();
  if (n > worldProp("hl:act_shown", 0) && (n === 1 || night)) {
    setWorldProp("hl:act_shown", n);
    for (const p of players) {
      tryRun(() =>
        p.onScreenDisplay.setTitle(tr("hl.act.n", String(n)), { subtitle: tr(`hl.act.${n}.name`), fadeInDuration: 20, stayDuration: 60, fadeOutDuration: 30 })
      );
      sound(p, "hl.drone", 0.8);
      p.sendMessage(tr(`hl.act.${n}.intro`));
    }
  }
  for (const kind of THREATS) {
    if (!unlocked(kind) || worldProp(`hl:arrived_${kind}`, false)) continue;
    if (arrivalDay(kind) === 0) {
      setWorldProp(`hl:arrived_${kind}`, true); // the prologue needs no herald
      continue;
    }
    if (!night) continue;
    setWorldProp(`hl:arrived_${kind}`, true);
    for (const p of players) p.sendMessage(tr(`hl.arrive.${kind}`));
  }
}

export function onPlayerDied(player) {
  setFear(player, 0);
  pace.delete(player.id);
}

