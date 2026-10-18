// The Dead Hour. Three o'clock in the morning. Every animal and villager
// around you stops where it is and turns to look at you, all of them at
// once, and doesn't move, while a bell tolls three times somewhere nobody
// has a bell. Then they go back to what they were doing, as if nothing had
// happened, and far away, someone screams.

import { system, world } from "@minecraft/server";
import { faceTowards, later, sound, soundBehind, tr, tryRun, valid } from "../core/util.js";
import { define } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";

const WATCHERS = /^minecraft:(cow|pig|sheep|chicken|villager|villager_v2|horse|donkey|mule|cat|wolf|rabbit|goat|llama|fox|mooshroom|wandering_trader|camel|sniffer|armadillo)$/;
const LASTS = 200;

export const hours = new Map(); // playerId -> { start, watchers }

export function deadHour(player) {
  if (hours.has(player.id)) return false;
  const watchers = (tryRun(() => player.dimension.getEntities({ location: player.location, maxDistance: 24 })) ?? [])
    .filter((e) => WATCHERS.test(e.typeId) && valid(e));
  if (watchers.length < 2) return false;
  for (const e of watchers) tryRun(() => e.addEffect("slowness", LASTS + 10, { amplifier: 255, showParticles: false }));
  hours.set(player.id, { start: system.currentTick, watchers });
  tryRun(() =>
    player.onScreenDisplay.setTitle("§83:00", { subtitle: tr("hl.hour.sub"), fadeInDuration: 10, stayDuration: 60, fadeOutDuration: 20 })
  );
  for (let i = 0; i < 3; i++) later(20 + i * 50, () => sound(player, "block.bell.hit", 0.9, 0.45));
  discover("hour");
  addFear(player, 25);
  return true;
}

function tickOne(player, h, now) {
  if (now - h.start >= LASTS) {
    hours.delete(player.id);
    later(60, () => soundBehind(player, "hl.scream", 30, 0.8, 0.6));
    return;
  }
  // Every one of them, looking at you.
  for (const e of h.watchers) if (valid(e)) faceTowards(e, player.location);
}

// Every 5 ticks.
export function tickHours() {
  const now = system.currentTick;
  for (const [id, h] of [...hours.entries()]) {
    const player = world.getAllPlayers().find((p) => p.id === id);
    if (!player) hours.delete(id);
    else tryRun(() => tickOne(player, h, now));
  }
}

define({
  kind: "hour",
  weights: (ctx) => [0, ctx.night && !ctx.under ? 2 : 0],
  major: deadHour,
  off: () => hours.clear(),
  test: deadHour,
});
