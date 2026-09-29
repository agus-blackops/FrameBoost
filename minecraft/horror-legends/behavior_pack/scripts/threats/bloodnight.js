// Chapter 7: the Red Night. Every five days a whole night turns red: a fog of
// blood, a heartbeat that won't stop, and everything that hunts you comes
// twice as often. It may snuff out your Ward Lanterns. It ends at dawn.

import { system, world } from "@minecraft/server";
import { actionBar, isNight, later, overworldPlayers, sound, tr, tryRun } from "../lib/util.js";
import { active, addFear, chapterDay, CHAPTERS, discover, enabled, flags, hauntDay, setWorldProp, worldProp } from "../lib/state.js";
import { snuffWards } from "../items/ward.js";

const blood = { until: 0, fogged: new Set() };

function bloodDue() {
  if (!active("bloodnight")) return false;
  const since = hauntDay() - chapterDay(CHAPTERS.find((c) => c.threat === "bloodnight"));
  return since >= 0 && since % 5 === 0;
}

function bloodOn(player) {
  if (blood.fogged.has(player.id)) return;
  blood.fogged.add(player.id);
  tryRun(() => player.runCommand("fog @s push hl:blood hl_blood"));
}

// Summoned from the journal it also works by day, for a couple of minutes.
export function startBlood(summoned = false) {
  flags.bloodNight = true;
  blood.until = summoned ? system.currentTick + 20 * 120 : 0;
  setWorldProp("hl:blood_day", world.getDay());
  discover("bloodnight");
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
  flags.bloodNight = false;
  for (const p of world.getAllPlayers()) {
    if (blood.fogged.has(p.id)) actionBar(p, tr("hl.blood.end"));
    tryRun(() => p.runCommand("fog @s remove hl_blood"));
  }
  blood.fogged.clear();
}

export function bloodActive() {
  return flags.bloodNight;
}

// Every 100 ticks.
export function tickBlood() {
  if (flags.bloodNight) {
    if ((!isNight() && system.currentTick > blood.until) || !enabled("bloodnight")) {
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
