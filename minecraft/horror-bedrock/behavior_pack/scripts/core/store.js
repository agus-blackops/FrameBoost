// Everything the add-on remembers about a world: settings, how many days the
// haunting has been going, which act and which creatures are loose, what you
// have met, how many times you have found all eight pages, and how afraid
// you are.

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
  const i = worldProp("hb:intensity", 1);
  return INTENSITY[i] ? i : 1;
}

export function intensity() {
  return INTENSITY[intensityIndex()];
}

export function cycleIntensity() {
  setWorldProp("hb:intensity", (intensityIndex() + 1) % INTENSITY.length);
}

export function jumpscaresOn() {
  return worldProp("hb:on_jumpscares", true);
}

// ------------------------------------------------------------------------- //
// The story: three acts, one new horror at a time
// ------------------------------------------------------------------------- //

// Everything that can be switched off, in the order they arrive.
export const THREATS = [
  "ambience", "scares", "ruins", "greensteve", "knocker", "smiledog",
  "lick", "mimicer", "boiled", "jeff",
  "slender",
];

// The day (at normal intensity) each one arrives.
export const ARRIVES = {
  ambience: 0, scares: 0, ruins: 0, greensteve: 1, knocker: 1, smiledog: 2,
  lick: 3, mimicer: 4, boiled: 4, jeff: 5,
  slender: 6,
};

export const ACTS = [
  { n: 1, day: 0, threats: ["ambience", "scares", "ruins", "greensteve", "knocker", "smiledog"] },
  { n: 2, day: 3, threats: ["lick", "mimicer", "boiled", "jeff"] },
  { n: 3, day: 6, threats: ["slender"] },
];

export function hauntDay() {
  let start = worldProp("hb:start_day", undefined);
  if (start === undefined) {
    start = world.getDay();
    setWorldProp("hb:start_day", start);
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
  return worldProp(`hb:on_${threat}`, true);
}

export function toggle(threat) {
  setWorldProp(`hb:on_${threat}`, !enabled(threat));
}

export function unlocked(threat) {
  return threat in ARRIVES && hauntDay() >= arrivalDay(threat);
}

export function daysUntil(threat) {
  return Math.max(0, arrivalDay(threat) - hauntDay());
}

// After the last page, there is peace for a few days.
export function cursed() {
  return world.getDay() >= worldProp("hb:peace_until", -1);
}

export function active(threat) {
  return enabled(threat) && unlocked(threat) && cursed();
}

// While Slenderman's pages are out, everything is twice as likely.
export const flags = { slender: false };

export function roll(p) {
  return Math.random() < p * intensity().chance * (flags.slender ? 2 : 1);
}

// ------------------------------------------------------------------------- //
// Discoveries and counters
// ------------------------------------------------------------------------- //

export function discovered(kind) {
  return worldProp(`hb:seen_${kind}`, false);
}

export function discover(kind) {
  if (discovered(kind)) return false;
  setWorldProp(`hb:seen_${kind}`, true);
  return true;
}

export function counter(key) {
  return worldProp(`hb:count_${key}`, 0);
}

export function bump(key, by = 1) {
  const v = counter(key) + by;
  setWorldProp(`hb:count_${key}`, v);
  return v;
}

export function resetCounter(key) {
  setWorldProp(`hb:count_${key}`, 0);
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
