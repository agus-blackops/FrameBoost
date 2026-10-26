// The night you go to bed. Some of them learn where you sleep (the Knocker,
// Jeff, the Boiled One): you lie down, the screen goes dark, and when you
// wake up something is standing beside the bed, looking down at you.
//
// A creature that does this has `sleep(player)` in the registry; this module
// picks one, at most once a night for each player.

import { world } from "@minecraft/server";
import { besideSpot, faceTowards, isSleeping, later, overworldPlayers, pick, sound, tryRun } from "../core/util.js";
import { DEFS, spawn, stateOf, vanish } from "../core/creatures.js";
import { THREATS, active, addFear, discover, roll } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";

const asleep = new Map(); // playerId -> seconds in bed
const visited = new Map(); // playerId -> the day of the last visit

// Every 20 ticks.
export function tickSleep() {
  const day = world.getDay();
  for (const player of overworldPlayers()) {
    if (!isSleeping(player)) {
      asleep.delete(player.id);
      continue;
    }
    const n = (asleep.get(player.id) ?? 0) + 1;
    asleep.set(player.id, n);
    if (n !== 2 || visited.get(player.id) === day) continue;
    if (nearWard(player.dimension, player.location) || !roll(0.45)) continue;
    const kind = pick(THREATS.filter((k) => DEFS[k]?.sleep && active(k)).map((k) => [k, 1]));
    if (kind && tryRun(() => DEFS[kind].sleep(player))) visited.set(player.id, day);
  }
}

// Standing beside the bed. In a moment you wake up and see it; `after(e, s)`
// decides what it does then (by default, it is gone a few seconds later).
/**
 * @param {import("@minecraft/server").Player} player
 * @param {string} kind
 * @param {string} mode
 * @param {{ sub?: any, after?: (e: any, s: any) => void, lines?: any }} [opts]
 */
export function bedside(player, kind, mode, { sub, after, lines } = {}) {
  const at = besideSpot(player, 2) ?? besideSpot(player, 1);
  if (!at) return false;
  const e = spawn(kind, at, player, mode, { major: true, frozen: true, close: true });
  if (!e) return false;
  faceTowards(e, player.location);
  sound(player, "hb.breath", 0.8, 0.8);
  later(30, () => {
    // You wake up.
    tryRun(() => player.teleport(player.location));
    scare(player, " ", sub);
    if (lines) tryRun(() => player.sendMessage(lines));
    discover(kind);
    addFear(player, 35);
    const s = stateOf(e);
    if (s) s.frozen = false;
    if (after) tryRun(() => after(e, s));
    else later(50, () => vanish(e, true));
  });
  return true;
}
