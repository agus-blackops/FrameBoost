// The journal: where the story is, what you have met, how many times you
// have seen the sun come back, the settings, and a test menu to see
// anything right away.

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
import { RADIO } from "../items/radio.js";

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
    { translate: "hh.j.day", with: [String(hauntDay())] },
    { text: "\n" },
    { translate: "hh.act.n", with: [String(n)] },
    { text: ": " },
    { translate: `hh.act.${n}.name` },
    { text: "\n\n" },
    { translate: "hh.j.fear" },
    { text: ` ${fearBar(player)}` },
  ];
  if (!cursed()) raw.push({ text: "\n\n" }, { translate: "hh.j.peace" });
  if (counter("wins")) raw.push({ text: "\n" }, { translate: "hh.j.wins", with: [String(counter("wins"))] });
  return { rawtext: raw };
}

export function openJournal(player) {
  sound(player, "hh.page", 0.5, 1.2);
  const buttons = [
    ["hh.j.acts", () => openActs(player)],
    ["hh.j.bestiary", () => openBestiary(player)],
    ["hh.j.settings", () => openSettings(player)],
    ["hh.j.tests", () => openTests(player)],
    ["hh.j.close", () => {}],
  ];
  const form = new ActionFormData().title(tr("hh.j.title")).body(mainBody(player));
  for (const [key] of buttons) form.button(tr(key));
  show(form, player, (i) => buttons[i]?.[1]());
}

function openActs(player) {
  const form = new ActionFormData().title(tr("hh.j.acts")).body(tr("hh.j.acts_body"));
  const open = ACTS.map((act) => hauntDay() >= actDay(act));
  ACTS.forEach((act, i) => {
    if (open[i]) form.button({ rawtext: [{ translate: "hh.act.n", with: [String(act.n)] }, { text: "\n" }, { translate: `hh.act.${act.n}.name` }] });
    else form.button({ rawtext: [{ text: "§8???\n" }, { translate: "hh.j.in_days", with: [String(actDay(act) - hauntDay())] }] });
  });
  form.button(tr("hh.j.back"));
  show(form, player, (i) => {
    if (i >= ACTS.length) return openJournal(player);
    if (!open[i]) return openActs(player);
    const n = ACTS[i].n;
    const page = new ActionFormData()
      .title({ rawtext: [{ translate: "hh.act.n", with: [String(n)] }, { text: ": " }, { translate: `hh.act.${n}.name` }] })
      .body(tr(`hh.act.${n}.text`))
      .button(tr("hh.j.back"));
    show(page, player, () => openActs(player));
  });
}

const BEASTS = THREATS;

function openBestiary(player) {
  const form = new ActionFormData().title(tr("hh.j.bestiary")).body(tr("hh.j.bestiary_body"));
  for (const t of BEASTS) form.button(discovered(t) ? tr(`hh.threat.${t}`) : { rawtext: [{ text: "§8???" }] });
  form.button(tr("hh.j.back"));
  show(form, player, (i) => {
    if (i >= BEASTS.length) return openJournal(player);
    const t = BEASTS[i];
    const page = new ActionFormData()
      .title(discovered(t) ? tr(`hh.threat.${t}`) : { rawtext: [{ text: "???" }] })
      .body(discovered(t) ? tr(`hh.lore.${t}`) : tr("hh.j.unknown"))
      .button(tr("hh.j.back"));
    show(page, player, () => openBestiary(player));
  });
}

function openSettings(player) {
  const form = new ActionFormData()
    .title(tr("hh.j.settings"))
    .body(tr("hh.j.settings_body"))
    .button({ rawtext: [{ translate: "hh.j.intensity" }, { text: ": " }, { translate: `hh.int.${intensity().key}` }] });
  for (const t of THREATS) {
    const status = enabled(t)
      ? daysUntil(t) > 0 ? { translate: "hh.j.in_days", with: [String(daysUntil(t))] } : { translate: "hh.j.on" }
      : { translate: "hh.j.off" };
    form.button({ rawtext: [{ translate: `hh.threat.${t}` }, { text: ": " }, status] });
  }
  form.button({ rawtext: [{ translate: "hh.j.jumpscares" }, { text: ": " }, { translate: jumpscaresOn() ? "hh.j.on" : "hh.j.off" }] });
  form.button({ rawtext: [{ translate: "hh.j.loud" }, { text: ": " }, { translate: loudOn() ? "hh.j.on" : "hh.j.off" }] });
  form.button({ rawtext: [{ translate: "hh.j.audio3d" }, { text: ": " }, { translate: audio3dOn() ? "hh.j.on" : "hh.j.off" }] });
  form.button(tr("hh.j.back"));
  show(form, player, (i) => {
    if (i === 0) cycleIntensity();
    else if (i <= THREATS.length) {
      const t = THREATS[i - 1];
      toggle(t);
      if (!enabled(t)) {
        clearKind(t);
        tryRun(() => DEFS[t]?.off?.());
      }
    } else if (i === THREATS.length + 1) setWorldProp("hh:on_jumpscares", !jumpscaresOn());
    else if (i === THREATS.length + 2) setWorldProp("hh:on_loud", !loudOn());
    else if (i === THREATS.length + 3) setWorldProp("hh:on_3d", !audio3dOn());
    else return openJournal(player);
    openSettings(player);
  });
}

function openTests(player) {
  const tests = [
    ...THREATS.map((t) => [`hh.threat.${t}`, () => DEFS[t]?.test?.(player)]),
    ["hh.t.scare", () => scare(player, "§f§lHELP.")],
    ["hh.t.items", () => {
      give(player, FLASHLIGHT);
      give(player, BATTERY, 4);
      give(player, WARD);
      give(player, RADIO);
    }],
    ["hh.t.day", () => {
      setWorldProp("hh:start_day", worldProp("hh:start_day", world.getDay()) - 1);
      player.sendMessage(tr("hh.j.day", String(hauntDay())));
    }],
  ];
  const form = new ActionFormData().title(tr("hh.j.tests")).body(tr("hh.j.tests_body"));
  for (const [key] of tests) form.button(tr(key));
  form.button(tr("hh.j.back"));
  show(form, player, (i) => (i < tests.length ? tests[i][1]() : openJournal(player)));
}
