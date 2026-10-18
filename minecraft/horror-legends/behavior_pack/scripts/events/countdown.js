// The Countdown. A message from nobody: "When it reaches zero, turn
// around." Then the numbers, one a second, in the chat, with a tick like a
// clock. At zero the screen goes black, and when it comes back there is a
// face in front of yours. Or, sometimes, nothing at all, and a minute later
// a scream.

import { world } from "@minecraft/server";
import { later, sound, soundBehind, tryRun } from "../core/util.js";
import { define } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout } from "../core/fx.js";
import { faceFlash, staticBurst } from "../creatures/scares.js";

const FROM = 10;
export const counting = new Set();

function here(player) {
  return world.getAllPlayers().some((p) => p.id === player.id);
}

function say(player, text) {
  tryRun(() => player.sendMessage({ rawtext: [{ text: "§8[§k??§r§8]§r " }, ...text] }));
}

export function startCountdown(player) {
  if (counting.has(player.id)) return false;
  counting.add(player.id);
  say(player, [{ translate: "hl.countdown.start" }]);
  for (let n = FROM; n >= 1; n--) {
    later((FROM - n + 1) * 20, () => {
      if (!here(player)) return;
      say(player, [{ text: `§${n <= 3 ? "4" : "7"}${n}` }]);
      sound(player, "random.click", 0.6 + (FROM - n) * 0.05, 0.6 + (FROM - n) * 0.06);
      addFear(player, 2);
    });
  }
  later((FROM + 1) * 20, () => {
    counting.delete(player.id);
    if (!here(player)) return;
    blackout(player, 0.6);
    if (Math.random() < 0.75) {
      later(8, () => tryRun(() => faceFlash(player)) || staticBurst(player));
    } else {
      // Nothing. Then, a minute later...
      say(player, [{ translate: "hl.countdown.nothing" }]);
      later(20 * 60, () => here(player) && soundBehind(player, "hl.scream", 6, 0.9, 1));
    }
    discover("countdown");
    addFear(player, 15);
  });
  return true;
}

define({
  kind: "countdown",
  weights: (ctx) => [0, ctx.night || ctx.under ? 2 : 1],
  major: startCountdown,
  test: startCountdown,
});
