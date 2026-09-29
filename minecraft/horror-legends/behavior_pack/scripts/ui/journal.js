// The Survivor's Journal: where the story is, what you have met, the pages
// you have found, the ritual, the settings, and a test menu to see anything
// right away.

import { ActionFormData } from "@minecraft/server-ui";
import { world } from "@minecraft/server";
import { give, sound, tr, tryRun } from "../lib/util.js";
import {
  CHAPTERS, PAGES, THREATS, chapterDay, chapterIndex, counter, cursed, cycleIntensity, daysUntil,
  discovered, enabled, getFear, hauntDay, intensity, jumpscaresOn, pageRead, pagesRead, setWorldProp, toggle, worldProp,
} from "../lib/state.js";
import { scare } from "../lib/fx.js";
import { clearKind } from "../lib/actors.js";
import { ambience } from "../threats/ambience.js";
import { gamerJoin, gamerLeave, gamerReveal, fakeAlive } from "../threats/gamer.js";
import { spawnDweller } from "../threats/dweller.js";
import { herobrineMinor, sighting } from "../threats/herobrine.js";
import { clearPresence, lightsOut, presenceMinor, stalk, windowWatcher } from "../threats/presence.js";
import { endFog, fog, spawnFogMan, startFog } from "../threats/fog.js";
import { corrupt, nullAppears, restoreCorruption } from "../threats/null.js";
import { endBlood, startBlood, bloodActive } from "../threats/bloodnight.js";
import { fighting, ritualBlocker, startRitual } from "../threats/boss.js";
import { pageItem, readPage } from "../items/pages.js";
import { BATTERY, FLASHLIGHT } from "../items/flashlight.js";
import { WARD } from "../items/ward.js";

function show(form, player, onSelect) {
  form
    .show(player)
    .then((r) => {
      if (!r.canceled && r.selection !== undefined) tryRun(() => onSelect(r.selection));
    })
    .catch(() => {});
}

function fearBar(player) {
  const f = Math.round(getFear(player) / 10);
  const color = f >= 7 ? "§4" : f >= 4 ? "§c" : "§7";
  return `${color}${"|".repeat(f)}§8${"|".repeat(10 - f)}`;
}

function mainBody(player) {
  const ch = chapterIndex();
  const raw = [
    { translate: "hl.j.day", with: [String(hauntDay())] },
    { text: "\n" },
    { translate: `hl.ch.${ch}.name` },
    { text: "\n\n" },
    { translate: "hl.j.fear" },
    { text: ` ${fearBar(player)}\n` },
    { translate: "hl.j.pages_count", with: [String(pagesRead()), String(PAGES.length)] },
  ];
  if (!cursed()) raw.push({ text: "\n\n" }, { translate: "hl.j.peace" });
  if (counter("wins")) raw.push({ text: "\n" }, { translate: "hl.j.wins", with: [String(counter("wins"))] });
  return { rawtext: raw };
}

export function openJournal(player) {
  sound(player, "hl.page", 0.5, 1.2);
  const ritual = pagesRead() === PAGES.length;
  const buttons = [
    ["hl.j.chapters", () => openChapters(player)],
    ["hl.j.bestiary", () => openBestiary(player)],
    ["hl.j.pages", () => openPages(player)],
    ...(ritual ? [["hl.j.ritual", () => openRitual(player)]] : []),
    ["hl.j.settings", () => openSettings(player)],
    ["hl.j.tests", () => openTests(player)],
    ["hl.j.close", () => {}],
  ];
  const form = new ActionFormData().title(tr("hl.j.title")).body(mainBody(player));
  for (const [key] of buttons) form.button(tr(key));
  show(form, player, (i) => buttons[i]?.[1]());
}

function chapterLabel(n) {
  return n === 0 ? { translate: "hl.j.prologue" } : { translate: "hl.j.chapter_n", with: [String(n)] };
}

function openChapters(player) {
  const form = new ActionFormData().title(tr("hl.j.chapters")).body(tr("hl.j.chapters_body"));
  const open = CHAPTERS.map((ch) => hauntDay() >= chapterDay(ch));
  CHAPTERS.forEach((ch, i) => {
    if (open[i]) form.button({ rawtext: [chapterLabel(ch.n), { text: "\n" }, { translate: `hl.ch.${ch.n}.name` }] });
    else form.button({ rawtext: [{ text: "§8???\n" }, { translate: "hl.j.in_days", with: [String(chapterDay(ch) - hauntDay())] }] });
  });
  form.button(tr("hl.j.back"));
  show(form, player, (i) => {
    if (i >= CHAPTERS.length) return openJournal(player);
    if (!open[i]) return openChapters(player);
    const n = CHAPTERS[i].n;
    const page = new ActionFormData()
      .title({ rawtext: [chapterLabel(n), { text: ": " }, { translate: `hl.ch.${n}.name` }] })
      .body(tr(`hl.ch.${n}.text`))
      .button(tr("hl.j.back"));
    show(page, player, () => openChapters(player));
  });
}

const BEASTS = [...THREATS, "boss"];

function openBestiary(player) {
  const form = new ActionFormData().title(tr("hl.j.bestiary")).body(tr("hl.j.bestiary_body"));
  for (const t of BEASTS) form.button(discovered(t) ? tr(`hl.threat.${t}`) : { rawtext: [{ text: "§8???" }] });
  form.button(tr("hl.j.back"));
  show(form, player, (i) => {
    if (i >= BEASTS.length) return openJournal(player);
    const t = BEASTS[i];
    const page = new ActionFormData()
      .title(discovered(t) ? tr(`hl.threat.${t}`) : { rawtext: [{ text: "???" }] })
      .body(discovered(t) ? tr(`hl.lore.${t}`) : tr("hl.j.unknown"))
      .button(tr("hl.j.back"));
    show(page, player, () => openBestiary(player));
  });
}

function openPages(player) {
  const form = new ActionFormData()
    .title(tr("hl.j.pages"))
    .body(tr("hl.j.pages_body", String(pagesRead()), String(PAGES.length)));
  for (const n of PAGES) form.button(pageRead(n) ? tr(`hl.page.${n}.title`) : tr("hl.j.page_missing", String(n)));
  form.button(tr("hl.j.back"));
  show(form, player, (i) => {
    if (i >= PAGES.length) return openJournal(player);
    if (pageRead(PAGES[i])) readPage(player, PAGES[i]);
    else openPages(player);
  });
}

function openRitual(player) {
  const why = ritualBlocker(player);
  const form = new ActionFormData()
    .title(tr("hl.j.ritual"))
    .body({ rawtext: [{ translate: "hl.ritual.body" }, { text: "\n\n" }, why ? { translate: why } : { translate: "hl.ritual.ready" }] })
    .button(tr(why ? "hl.j.back" : "hl.ritual.begin"))
    .button(tr("hl.j.close"));
  show(form, player, (i) => {
    if (i === 0 && !why) startRitual(player);
    else if (i === 0) openJournal(player);
  });
}

function openSettings(player) {
  const form = new ActionFormData()
    .title(tr("hl.j.settings"))
    .body(tr("hl.j.settings_body"))
    .button({ rawtext: [{ translate: "hl.j.intensity" }, { text: ": " }, { translate: `hl.int.${intensity().key}` }] });
  for (const t of THREATS) {
    const status = enabled(t) ? (daysUntil(t) > 0 ? { translate: "hl.j.in_days", with: [String(daysUntil(t))] } : { translate: "hl.j.on" }) : { translate: "hl.j.off" };
    form.button({ rawtext: [{ translate: `hl.threat.${t}` }, { text: ": " }, status] });
  }
  form.button({ rawtext: [{ translate: "hl.j.jumpscares" }, { text: ": " }, { translate: jumpscaresOn() ? "hl.j.on" : "hl.j.off" }] });
  form.button(tr("hl.j.back"));
  show(form, player, (i) => {
    if (i === 0) cycleIntensity();
    else if (i <= THREATS.length) {
      const t = THREATS[i - 1];
      toggle(t);
      if (!enabled(t)) switchedOff(t);
    } else if (i === THREATS.length + 1) setWorldProp("hl:on_jumpscares", !jumpscaresOn());
    else return openJournal(player);
    openSettings(player);
  });
}

function switchedOff(t) {
  if (t === "herobrine") clearKind("herobrine");
  if (t === "null") {
    clearKind("null");
    restoreCorruption(true);
  }
  if (t === "dweller") clearKind("dweller");
  if (t === "fog" && fog.active) endFog();
  if (t === "gamer") gamerLeave();
  if (t === "presence") clearPresence();
  if (t === "bloodnight" && bloodActive()) endBlood();
}

function openTests(player) {
  const tests = [
    ["hl.threat.gamer", () => (fakeAlive() ? gamerReveal(player) : (setWorldProp("hl:fp_day", -1), gamerJoin(player)))],
    ["hl.threat.dweller", () => spawnDweller(player, false)],
    ["hl.threat.herobrine", () => sighting(player) || herobrineMinor(player)],
    ["hl.t.lights", () => lightsOut(player) || stalk(player)],
    ["hl.t.window", () => windowWatcher(player) || presenceMinor(player)],
    ["hl.threat.fog", () => {
      if (!fog.active) startFog();
      else spawnFogMan(player, true);
    }],
    ["hl.threat.null", () => nullAppears(player) || corrupt(player)],
    ["hl.threat.bloodnight", () => (bloodActive() ? endBlood() : startBlood(true))],
    ["hl.threat.ambience", () => ambience(player)],
    ["hl.t.scare", () => scare(player, "§4§lHEROBRINE")],
    ["hl.t.items", () => {
      give(player, FLASHLIGHT);
      give(player, BATTERY, 4);
      give(player, WARD);
      for (const n of PAGES) if (!pageRead(n)) give(player, pageItem(n));
    }],
    ["hl.t.ritual", () => fighting() || startRitual(player, true)],
    ["hl.t.day", () => {
      setWorldProp("hl:start_day", worldProp("hl:start_day", world.getDay()) - 1);
      player.sendMessage(tr("hl.j.day", String(hauntDay())));
    }],
  ];
  const form = new ActionFormData().title(tr("hl.j.tests")).body(tr("hl.j.tests_body"));
  for (const [key] of tests) form.button(tr(key));
  form.button(tr("hl.j.back"));
  show(form, player, (i) => (i < tests.length ? tests[i][1]() : openJournal(player)));
}

