// Act III: the Red Night. Every four days a whole night turns red: a fog of
// blood, a heartbeat that won't stop, and everything that hunts you comes
// twice as often. It may snuff out your Ward Lanterns. It ends at dawn.

import { system, world } from "@minecraft/server";
import { actionBar, isNight, later, overworldPlayers, sound, tr, tryRun } from "../core/util.js";
import { active, addFear, arrivalDay, discover, enabled, flags, hauntDay, setWorldProp, worldProp } from "../core/store.js";
import { define } from "../core/creatures.js";
import { snuffWards } from "../items/ward.js";

const blood = { until: 0, fogged: new Set() };

function bloodDue() {
  if (!active("redNight")) return false;
  const since = hauntDay() - arrivalDay("redNight");
  return since >= 0 && since % 4 === 0;
}

function bloodOn(player) {
  if (blood.fogged.has(player.id)) return;
  blood.fogged.add(player.id);
  tryRun(() => player.runCommand("fog @s push hl:blood hl_blood"));
}

// Summoned from the journal it also works by day, for a couple of minutes.
export function startBlood(summoned = false) {
  flags.redNight = true;
  blood.until = summoned ? system.currentTick + 20 * 120 : 0;
  setWorldProp("hl:blood_day", world.getDay());
  discover("redNight");
  for (const p of overworldPlayers()) {
    bloodOn(p);
    tryRun(() =>
      p.onScreenDisplay.setTitle(tr("hl.blood.title"), { subtitle: tr("hl.blood.sub"), fadeInDuration: 20, stayDuration: 70, fadeOutDuration: 30 })
    );
    sound(p, "hl.drone", 1);
    later(30, () => sound(p, "hl.stinger", 0.5, 0.7));
    later(200, () => snuffWards(p));
    addFear(p, 30);
  }
}

export function endBlood() {
  flags.redNight = false;
  for (const p of world.getAllPlayers()) {
    if (blood.fogged.has(p.id)) actionBar(p, tr("hl.blood.end"));
    tryRun(() => p.runCommand("fog @s remove hl_blood"));
  }
  blood.fogged.clear();
}

export function bloodActive() {
  return flags.redNight;
}

// Every 100 ticks.
export function tickBlood() {
  if (flags.redNight) {
    if ((!isNight() && system.currentTick > blood.until) || !enabled("redNight")) {
      endBlood();
      return;
    }
    for (const p of overworldPlayers()) {
      bloodOn(p);
      if (Math.random() < 0.3) sound(p, "hl.drone", 0.7);
    }
    return;
  }
  if (isNight() && bloodDue() && worldProp("hl:blood_day", -1) !== world.getDay()) startBlood();
}

export function onPlayerSpawn(player) {
  tryRun(() => player.runCommand("fog @s remove hl_blood"));
}

define({
  kind: "redNight",
  weights: () => [0, 0],
  off: () => bloodActive() && endBlood(),
  test: () => (bloodActive() ? endBlood() : startBlood(true)),
});
