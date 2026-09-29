// Everything the add-on remembers about a world: settings, how many days the
// haunting has been going, which chapters have opened, which creatures you
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

// `days` scales when chapters open, `chance` how often things happen, `pace`
// how long the director waits between them.
export const INTENSITY = [
  { key: "low", days: 2, chance: 0.5, pace: 1.6 },
  { key: "normal", days: 1, chance: 1, pace: 1 },
  { key: "high", days: 0.5, chance: 1.6, pace: 0.7 },
  { key: "nightmare", days: 0, chance: 2.5, pace: 0.45 },
];

export function intensityIndex() {
  const i = worldProp("hl:intensity", 1);
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
// Chapters: the story opens one threat at a time
// ------------------------------------------------------------------------- //

export const THREATS = ["ambience", "gamer", "dweller", "herobrine", "presence", "fog", "null", "bloodnight"];

export const CHAPTERS = [
  { n: 0, day: 0, threat: "ambience" },
  { n: 1, day: 1, threat: "gamer" },
  { n: 2, day: 2, threat: "dweller" },
  { n: 3, day: 3, threat: "herobrine" },
  { n: 4, day: 4, threat: "presence" },
  { n: 5, day: 5, threat: "fog" },
  { n: 6, day: 6, threat: "null" },
  { n: 7, day: 7, threat: "bloodnight" },
];

export function hauntDay() {
  let start = worldProp("hl:start_day", undefined);
  if (start === undefined) {
    start = world.getDay();
    setWorldProp("hl:start_day", start);
  }
  return world.getDay() - start;
}

export function chapterDay(chapter) {
  return Math.ceil(chapter.day * intensity().days);
}

export function chapterIndex() {
  let n = 0;
  for (const ch of CHAPTERS) if (hauntDay() >= chapterDay(ch)) n = ch.n;
  return n;
}

export function enabled(threat) {
  return worldProp(`hl:on_${threat}`, true);
}

export function toggle(threat) {
  setWorldProp(`hl:on_${threat}`, !enabled(threat));
}

export function unlocked(threat) {
  const ch = CHAPTERS.find((c) => c.threat === threat);
  return !!ch && hauntDay() >= chapterDay(ch);
}

export function daysUntil(threat) {
  const ch = CHAPTERS.find((c) => c.threat === threat);
  return Math.max(0, chapterDay(ch) - hauntDay());
}

// After you break the curse it sleeps for a few days.
export function cursed() {
  return world.getDay() >= worldProp("hl:peace_until", -1);
}

export function active(threat) {
  return enabled(threat) && unlocked(threat) && cursed();
}

// The Red Night doubles everything.
export const flags = { bloodNight: false, boss: false };

export function roll(p) {
  return Math.random() < p * intensity().chance * (flags.bloodNight ? 2 : 1);
}

// ------------------------------------------------------------------------- //
// Discoveries and pages
// ------------------------------------------------------------------------- //

export function discovered(threat) {
  return worldProp(`hl:seen_${threat}`, false);
}

export function discover(threat) {
  if (discovered(threat)) return false;
  setWorldProp(`hl:seen_${threat}`, true);
  return true;
}

export const PAGES = [1, 2, 3, 4, 5];

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
