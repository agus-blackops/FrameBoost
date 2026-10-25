// The Emergency Alert System. Local 58 and The Mandela Catalogue both talk
// to you through it: the screen goes to the red bar, the two-tone attention
// signal blares, and then the message, one line at a time. Sometimes the
// message is not from who it says it is from.

import { actionBar, later, sound, tr, tryRun } from "./util.js";

export const LINE = 50; // ticks each line stays up

// title: lang key for the subtitle under ⚠ EMERGENCY ALERT ⚠
// lines: lang keys, shown one after another on the action bar
// Returns how many ticks the whole broadcast lasts.
export function broadcast(player, title, lines, { tone = true, hijack = 0 } = {}) {
  tryRun(() =>
    player.onScreenDisplay.setTitle(tr("hh.eas.title"), {
      subtitle: tr(title), fadeInDuration: 0, stayDuration: 70, fadeOutDuration: 10,
    })
  );
  if (tone) sound(player, "hh.eas", 1, 1);
  lines.forEach((key, i) =>
    later(70 + i * LINE, () => {
      // From line `hijack` on, someone else is speaking.
      if (hijack && i >= hijack) sound(player, "hh.static", 0.5, 0.7);
      actionBar(player, tr(key));
    })
  );
  return 70 + lines.length * LINE;
}
