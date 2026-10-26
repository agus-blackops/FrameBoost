// The journal: where the story is, what you have met, the notes you have
// found, the settings, and a test menu to see anything right away.

import { ActionFormData } from "@minecraft/server-ui";
import { world } from "@minecraft/server";
import { audio3dOn, give, loudOn, sound, tr, tryRun } from "../core/util.js";
import {
  ACTS, THREATS, actDay, actIndex, counter, cursed, cycleIntensity, daysUntil, discovered, enabled, getFear,
  hauntDay, intensity, jumpscaresOn, setWorldProp, toggle, worldProp,
} from "../core/store.js";
import { scare } from "../core/fx.js";
import { DEFS, clearKind } from "../core/creatures.js";
import { BATTERY, FLASHLIGHT } from "../items/flashlight.js";
import { WARD } from "../items/ward.js";
import { NOTES, hasRead, readNote } from "../items/notes.js";
import { playIntro } from "./welcome.js";

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
  const n = actIndex();
  const raw = [
    { translate: "hb.j.day", with: [String(hauntDay())] },
    { text: "\n" },
    { translate: "hb.act.n", with: [String(n)] },
    { text: ": " },
    { translate: `hb.act.${n}.name` },
    { text: "\n\n" },
    { translate: "hb.j.fear" },
    { text: ` ${fearBar(player)}` },
  ];
  if (!cursed()) raw.push({ text: "\n\n" }, { translate: "hb.j.peace" });
  if (counter("wins")) raw.push({ text: "\n" }, { translate: "hb.j.wins", with: [String(counter("wins"))] });
  return { rawtext: raw };
}

export function openJournal(player) {
  sound(player, "hb.page", 0.5, 1.2);
  const buttons = [
    ["hb.j.acts", () => openActs(player)],
    ["hb.j.bestiary", () => openBestiary(player)],
    ["hb.j.notes", () => openNotes(player)],
    ["hb.j.settings", () => openSettings(player)],
    ["hb.j.tests", () => openTests(player)],
    ["hb.j.close", () => {}],
  ];
  const form = new ActionFormData().title(tr("hb.j.title")).body(mainBody(player));
  for (const [key] of buttons) form.button(tr(key));
  show(form, player, (i) => buttons[i]?.[1]());
}

function openActs(player) {
  const form = new ActionFormData().title(tr("hb.j.acts")).body(tr("hb.j.acts_body"));
  const open = ACTS.map((act) => hauntDay() >= actDay(act));
  ACTS.forEach((act, i) => {
    if (open[i]) form.button({ rawtext: [{ translate: "hb.act.n", with: [String(act.n)] }, { text: "\n" }, { translate: `hb.act.${act.n}.name` }] });
    else form.button({ rawtext: [{ text: "§8???\n" }, { translate: "hb.j.in_days", with: [String(actDay(act) - hauntDay())] }] });
  });
  form.button(tr("hb.j.back"));
  show(form, player, (i) => {
    if (i >= ACTS.length) return openJournal(player);
    if (!open[i]) return openActs(player);
    const n = ACTS[i].n;
    const page = new ActionFormData()
      .title({ rawtext: [{ translate: "hb.act.n", with: [String(n)] }, { text: ": " }, { translate: `hb.act.${n}.name` }] })
      .body(tr(`hb.act.${n}.text`))
      .button(tr("hb.j.back"));
    show(page, player, () => openActs(player));
  });
}

const BEASTS = THREATS;

function openBestiary(player) {
  const form = new ActionFormData().title(tr("hb.j.bestiary")).body(tr("hb.j.bestiary_body"));
  for (const t of BEASTS) form.button(discovered(t) ? tr(`hb.threat.${t}`) : { rawtext: [{ text: "§8???" }] });
  form.button(tr("hb.j.back"));
  show(form, player, (i) => {
    if (i >= BEASTS.length) return openJournal(player);
    const t = BEASTS[i];
    const page = new ActionFormData()
      .title(discovered(t) ? tr(`hb.threat.${t}`) : { rawtext: [{ text: "???" }] })
      .body(discovered(t) ? tr(`hb.lore.${t}`) : tr("hb.j.unknown"))
      .button(tr("hb.j.back"));
    show(page, player, () => openBestiary(player));
  });
}

function openNotes(player) {
  const form = new ActionFormData().title(tr("hb.j.notes")).body(tr("hb.j.notes_body"));
  for (let n = 1; n <= NOTES; n++) {
    form.button(hasRead(player, n) ? tr(`hb.note.${n}.title`) : { rawtext: [{ text: `§8${n}. ???` }] });
  }
  form.button(tr("hb.j.back"));
  show(form, player, (i) => {
    if (i >= NOTES) return openJournal(player);
    if (!hasRead(player, i + 1)) return openNotes(player);
    readNote(player, i + 1, () => openNotes(player));
  });
}

function openSettings(player) {
  const form = new ActionFormData()
    .title(tr("hb.j.settings"))
    .body(tr("hb.j.settings_body"))
    .button({ rawtext: [{ translate: "hb.j.intensity" }, { text: ": " }, { translate: `hb.int.${intensity().key}` }] });
  for (const t of THREATS) {
    const status = enabled(t)
      ? daysUntil(t) > 0 ? { translate: "hb.j.in_days", with: [String(daysUntil(t))] } : { translate: "hb.j.on" }
      : { translate: "hb.j.off" };
    form.button({ rawtext: [{ translate: `hb.threat.${t}` }, { text: ": " }, status] });
  }
  form.button({ rawtext: [{ translate: "hb.j.jumpscares" }, { text: ": " }, { translate: jumpscaresOn() ? "hb.j.on" : "hb.j.off" }] });
  form.button({ rawtext: [{ translate: "hb.j.loud" }, { text: ": " }, { translate: loudOn() ? "hb.j.on" : "hb.j.off" }] });
  form.button({ rawtext: [{ translate: "hb.j.audio3d" }, { text: ": " }, { translate: audio3dOn() ? "hb.j.on" : "hb.j.off" }] });
  form.button(tr("hb.j.back"));
  show(form, player, (i) => {
    if (i === 0) cycleIntensity();
    else if (i <= THREATS.length) {
      const t = THREATS[i - 1];
      toggle(t);
      if (!enabled(t)) {
        clearKind(t);
        tryRun(() => DEFS[t]?.off?.());
      }
    } else if (i === THREATS.length + 1) setWorldProp("hb:on_jumpscares", !jumpscaresOn());
    else if (i === THREATS.length + 2) setWorldProp("hb:on_loud", !loudOn());
    else if (i === THREATS.length + 3) setWorldProp("hb:on_3d", !audio3dOn());
    else return openJournal(player);
    openSettings(player);
  });
}

function openTests(player) {
  const tests = [
    ...THREATS.map((t) => [`hb.threat.${t}`, () => DEFS[t]?.test?.(player)]),
    ["hb.t.scare", () => scare(player, "§f§l:)")],
    ["hb.t.items", () => {
      give(player, FLASHLIGHT);
      give(player, BATTERY, 4);
      give(player, WARD);
      for (let n = 1; n <= NOTES; n++) give(player, `hb:note_${n}`);
    }],
    ["hb.t.intro", () => playIntro(player)],
    ["hb.t.day", () => {
      setWorldProp("hb:start_day", worldProp("hb:start_day", world.getDay()) - 1);
      player.sendMessage(tr("hb.j.day", String(hauntDay())));
    }],
  ];
  const form = new ActionFormData().title(tr("hb.j.tests")).body(tr("hb.j.tests_body"));
  for (const [key] of tests) form.button(tr(key));
  form.button(tr("hb.j.back"));
  show(form, player, (i) => (i < tests.length ? tests[i][1]() : openJournal(player)));
}
