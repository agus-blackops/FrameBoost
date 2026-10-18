// Act III: Entity 303, the banned player who never left. He joins the
// game, says things in the chat, swaps the item in your hand, kicks you
// (or pretends to). When he comes in person he floats in a white hood with
// red eyes, his hands raised, and your world is his to play with: you are
// lifted into the air, thrown somewhere else, lightning falls around you.
// Then a screen says you have been banned, and he is gone, and where he
// floated there is a page.

import { ActionFormData } from "@minecraft/server-ui";
import {
  distance, faceTowards, groundSpot, groundAt, later, offsetFromView, onCooldown, pick, rand, randInt,
  sound, soundAt, swapHotbar, tr, tryRun, valid, visibleSpot,
} from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { dropPage } from "../items/pages.js";

export const NAME = "Entity303";

function chat(player, key, ...args) {
  player.sendMessage({ rawtext: [{ text: `<${NAME}> ` }, { translate: key, with: args.map(String) }] });
}

function joined(player, left = false) {
  player.sendMessage({ rawtext: [{ text: "§e" }, { translate: left ? "multiplayer.player.left" : "multiplayer.player.joined", with: [NAME] }] });
}

function strikeNear(player) {
  const p = offsetFromView(player, rand(5, 9), rand(0, 360));
  const y = groundAt(player.dimension, p.x, p.z, player.location.y) ?? player.location.y;
  tryRun(() => player.dimension.spawnEntity("minecraft:lightning_bolt", { x: p.x, y, z: p.z }));
}

// A fake kick: the screen goes black, a disconnection message, and you are
// still here.
function fakeKick(player) {
  blackout(player, 1.2);
  tryRun(() =>
    player.onScreenDisplay.setTitle(tr("hl.e303.kick"), { subtitle: tr("hl.e303.kick_sub"), fadeInDuration: 0, stayDuration: 40, fadeOutDuration: 10 })
  );
  sound(player, "hl.hack", 1, 0.8);
}

export function e303Minor(player) {
  const event = pick([
    ["join", 3],
    ["chat", 4],
    ["swap", 3],
    ["kick", onCooldown(`kick:${player.id}`, 20 * 300) ? 0 : 1],
    ["hack", 2],
  ]);
  if (event === "join") {
    joined(player);
    later(randInt(40, 80), () => chat(player, `hl.e303.chat.${randInt(1, 6)}`, player.name));
    later(randInt(140, 260), () => joined(player, true));
  } else if (event === "chat") chat(player, `hl.e303.chat.${randInt(1, 6)}`, player.name);
  else if (event === "swap") {
    if (!swapHotbar(player)) return false;
    sound(player, "hl.hack", 0.6, 1.3);
  } else if (event === "kick") fakeKick(player);
  else sound(player, "hl.hack", 0.8, rand(0.9, 1.1));
  discover("e303");
  addFear(player, 8);
  return true;
}

// He comes in person, and the world is his for a few seconds.
export function e303Major(player) {
  if (trackedFor(player, "e303") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 7, 11, rand(-25, 25), 5);
  if (!at) return false;
  const e = spawn("e303", at, player, "hack", { major: true, close: true, step: 0 });
  if (!e) return false;
  joined(player);
  chat(player, "hl.e303.come", player.name);
  discover("e303");
  addFear(player, 20);
  return true;
}

function cast(e, on) {
  tryRun(() => e.setProperty("hl:cast", on));
}

// The ban screen, and the page.
function ban(e, s, player) {
  const at = { ...e.location };
  vanish(e);
  joined(player, true);
  sound(player, "hl.hack", 1, 0.6);
  new ActionFormData()
    .title(tr("hl.e303.ban.title"))
    .body(tr("hl.e303.ban.body", player.name))
    .button(tr("hl.e303.ban.ok"))
    .show(player)
    .then(() => dropPage(7, { x: at.x, y: at.y + 0.5, z: at.z }, player))
    .catch(() => dropPage(7, { x: at.x, y: at.y + 0.5, z: at.z }, player));
}

function tick(e, s, player) {
  faceTowards(e, player.location);
  if (s.adopted) return;
  if (distance(player.location, e.location) > 40 && s.step === 0) {
    vanish(e, false);
    return;
  }
  if ((s.timer += 4) < 40) return;
  s.timer = 0;
  // One trick every two seconds, in this order.
  const step = s.step++;
  cast(e, true);
  later(20, () => valid(e) && cast(e, false));
  sound(player, "hl.hack", 0.8, rand(0.8, 1.2));
  if (step === 0) {
    tryRun(() => player.addEffect("levitation", 40, { amplifier: 1, showParticles: false }));
    chat(player, "hl.e303.fly");
  } else if (step === 1) {
    swapHotbar(player);
    for (let k = 0; k < 3; k++) later(k * 8, () => strikeNear(player));
  } else if (step === 2) {
    const to = groundSpot(player, 10, 16, rand(0, 360), 180);
    if (to) {
      blackout(player, 0.3);
      tryRun(() => player.teleport(to));
    }
    chat(player, "hl.e303.where");
  } else if (step === 3) {
    scare(player, "§c§lEntity303", "§8...");
    addFear(player, 25);
  } else {
    ban(e, s, player);
  }
}

define({
  kind: "e303",
  type: "hl:entity_303",
  voice: { id: "hl.glitch", every: [8, 14], volume: 0.8, pitch: [0.5, 0.7] },
  weights: (ctx) => [2, ctx.under ? 0 : 2],
  minor: e303Minor,
  major: e303Major,
  tick,
  adopt: () => "hack",
  onHit: (e, s, player) => {
    // You can't hit an admin.
    tryRun(() => player.addEffect("levitation", 30, { amplifier: 2, showParticles: false }));
    chat(player, "hl.e303.hit");
    if (s.adopted) vanish(e);
  },
  shine: () => {},
  test: (player) => e303Major(player) || e303Minor(player),
});
