// Jumpscares, blackouts and the heartbeat.

import { system } from "@minecraft/server";
import { isUnderground, later, overworldPlayers, sound, tryRun, distance } from "./util.js";
import { allFor } from "./actors.js";
import { flags, getFear, jumpscaresOn } from "./state.js";

// A proper jumpscare: a sting, a red flash, the screen shaking, the world
// going dark for a moment and your ears ringing after. With jumpscares off in
// the journal only a quieter sting is left.
export function scare(player, main, subtitle = " ") {
  const strong = jumpscaresOn();
  sound(player, "hl.stinger", strong ? 1 : 0.4);
  if (main) {
    tryRun(() =>
      player.onScreenDisplay.setTitle(main, { subtitle, fadeInDuration: 0, stayDuration: 16, fadeOutDuration: 12 })
    );
  }
  if (!strong) return;
  tryRun(() =>
    player.camera.fade({
      fadeColor: { red: 0.45, green: 0, blue: 0 },
      fadeTime: { fadeInTime: 0.05, holdTime: 0.2, fadeOutTime: 0.8 },
    })
  );
  tryRun(() => player.runCommand("camerashake add @s 1.2 0.6 rotational"));
  tryRun(() => player.addEffect("darkness", 80, { showParticles: false }));
  later(35, () => sound(player, "hl.ringing", 0.7));
}

// The screen goes black for a blink.
export function blackout(player, seconds = 0.35) {
  tryRun(() =>
    player.camera.fade({
      fadeColor: { red: 0, green: 0, blue: 0 },
      fadeTime: { fadeInTime: 0.05, holdTime: seconds, fadeOutTime: 0.1 },
    })
  );
}

// How loud the heartbeat is: 2 when something is right on you or after you,
// 1 when something is near or you are very afraid.
export function danger(player) {
  let level = 0;
  for (const s of allFor(player)) {
    const d = distance(player.location, s.entity.location);
    if (s.mode === "chasing" && d < 32) return 2;
    if (s.kind === "boss" && d < 40) return 2;
    if ((s.mode === "behind" || s.mode === "window" || s.mode === "bedside") && d < 12) return 2;
    if (d < 40) level = 1;
  }
  if (flags.bloodNight || getFear(player) >= 70) return Math.max(level, 1);
  if (!level && getFear(player) >= 55 && isUnderground(player)) level = 1;
  return level;
}

const heartNext = new Map();

// Every 2 ticks.
export function tickHeart() {
  const now = system.currentTick;
  for (const player of overworldPlayers()) {
    tryRun(() => {
      if (now < (heartNext.get(player.id) ?? 0)) return;
      const level = danger(player);
      if (!level) {
        heartNext.set(player.id, now + 20);
        return;
      }
      heartNext.set(player.id, now + (level === 2 ? 14 : 26));
      sound(player, "hl.heartbeat", level === 2 ? 1 : 0.55);
    });
  }
}
