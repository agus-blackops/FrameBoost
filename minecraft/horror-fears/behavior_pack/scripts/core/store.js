// Everything the add-on remembers about a world: settings, how many days the
// haunting has been going, which act and which creatures are loose, what you
// have met, which pages you have read, and how afraid you are.

import { world } from "@minecraft/server";
import { tryRun } from "./util.js";

export function worldProp(key, fallback) {
  const value = tryRun(() => world.getDynamicProperty(key));
  return value === undefined ? fallback : value;
}

export function setWorldProp(key, value) {
  tryRun(() => world.setDynamicProperty(key, value));
}

export function playerProp(player, key, fallback) {
  const value = tryRun(() => player.getDynamicProperty(key));
  return value === undefined ? fallback : value;
}

export function setPlayerProp(player, key, value) {
  tryRun(() => player.setDynamicProperty(key, value));
}

// ------------------------------------------------------------------------- //
// Settings
// ------------------------------------------------------------------------- //

// `days` scales when things arrive, `chance` how often they happen, `pace`
// how long the director waits between them.
export const INTENSITY = [
  { key: "low", days: 2, chance: 0.5, pace: 1.6 },
  { key: "normal", days: 1, chance: 1, pace: 1 },
  { key: "high", days: 0.5, chance: 1.6, pace: 0.7 },
  { key: "nightmare", days: 0, chance: 2.5, pace: 0.45 },
];

export function intensityIndex() {
  const i = worldProp("hl:intensity", 2); // Fears starts on High
  return INTENSITY[i] ? i : 1;
}

export function intensity() {
  return INTENSITY[intensityIndex()];
}

export function cycleIntensity() {
  setWorldProp("hl:intensity", (intensityIndex() + 1) % INTENSITY.length);
}

export function jumpscaresOn() {
  return worldProp("hl:on_jumpscares", true);
}

// ------------------------------------------------------------------------- //
// The story: three acts, one new horror at a time
// ------------------------------------------------------------------------- //

// Everything that can be switched off, in the order they arrive.
export const THREATS = [
  "ambience", "scares", "phobias", "gamer", "steps", "dweller", "nameless", "ancient",
  "herobrine", "visit", "countdown", "faceless", "fog", "grave", "hour",
  "null", "e303", "redNight",
];

// The day (at normal intensity) each one arrives.
export const ARRIVES = {
  ambience: 0, scares: 0, phobias: 1, gamer: 1, steps: 1, dweller: 1, nameless: 2, ancient: 1,
  herobrine: 3, visit: 3, countdown: 3, faceless: 4, fog: 4, grave: 4, hour: 5,
  null: 6, e303: 6, redNight: 8,
};

export const ACTS = [
  { n: 1, day: 0, threats: ["ambience", "scares", "phobias", "gamer", "steps", "dweller", "nameless", "ancient"] },
  { n: 2, day: 3, threats: ["herobrine", "visit", "countdown", "faceless", "fog", "grave", "hour"] },
  { n: 3, day: 6, threats: ["null", "e303", "redNight"] },
];

export function hauntDay() {
  let start = worldProp("hl:start_day", undefined);
  if (start === undefined) {
    start = world.getDay();
    setWorldProp("hl:start_day", start);
  }
  return world.getDay() - start;
}

export function arrivalDay(threat) {
  return Math.ceil((ARRIVES[threat] ?? 0) * intensity().days);
}

export function actDay(act) {
  return Math.ceil(act.day * intensity().days);
}

export function actIndex() {
  let n = 1;
  for (const act of ACTS) if (hauntDay() >= actDay(act)) n = act.n;
  return n;
}

export function enabled(threat) {
  return worldProp(`hl:on_${threat}`, true);
}

export function toggle(threat) {
  setWorldProp(`hl:on_${threat}`, !enabled(threat));
}

export function unlocked(threat) {
  return threat in ARRIVES && hauntDay() >= arrivalDay(threat);
}

export function daysUntil(threat) {
  return Math.max(0, arrivalDay(threat) - hauntDay());
}

// After you break the curse it sleeps for a few days.
export function cursed() {
  return world.getDay() >= worldProp("hl:peace_until", -1);
}

export function active(threat) {
  return enabled(threat) && unlocked(threat) && cursed();
}

// The Red Night doubles everything; nothing interrupts the final fight.
export const flags = { redNight: false, boss: false };

export function roll(p) {
  return Math.random() < p * intensity().chance * (flags.redNight ? 2 : 1);
}

// ------------------------------------------------------------------------- //
// Discoveries and pages
// ------------------------------------------------------------------------- //

export function discovered(kind) {
  return worldProp(`hl:seen_${kind}`, false);
}

export function discover(kind) {
  if (discovered(kind)) return false;
  setWorldProp(`hl:seen_${kind}`, true);
  return true;
}

// Seven torn pages, each kept by one of them.
export const PAGES = [1, 2, 3, 4, 5, 6, 7];
export const PAGE_KEEPER = { 1: "gamer", 2: "dweller", 3: "herobrine", 4: "fog", 5: "null", 6: "grave", 7: "e303" };

export function pageRead(n) {
  return worldProp(`hl:page_${n}`, false);
}

export function setPageRead(n, value = true) {
  setWorldProp(`hl:page_${n}`, value);
}

export function pagesRead() {
  return PAGES.filter(pageRead).length;
}

export function counter(key) {
  return worldProp(`hl:count_${key}`, 0);
}

export function bump(key, by = 1) {
  const v = counter(key) + by;
  setWorldProp(`hl:count_${key}`, v);
  return v;
}

export function resetCounter(key) {
  setWorldProp(`hl:count_${key}`, 0);
}

// ------------------------------------------------------------------------- //
// Fear
// ------------------------------------------------------------------------- //

const fear = new Map();

export function getFear(player) {
  return fear.get(player.id) ?? 0;
}

export function setFear(player, value) {
  fear.set(player.id, Math.max(0, Math.min(100, value)));
}

export function addFear(player, amount) {
  setFear(player, getFear(player) + amount);
}
