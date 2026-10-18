// Act I: what lives below. The longer you stay underground, the more you
// hear it: clicking, fast steps, whispers. Then it peeks round the corner.
// Catch it looking and it scurries off; wait too long and its snout peels
// open and it comes for you, screaming. Light drives it away. Live through
// it, or drive it off with your flashlight, and it leaves a page behind.

import {
  actionBar, canSee, caveSpot, faceTowards, isUnderground, later, pick, rand, soundAt, soundBehind, tr, tryRun,
} from "../core/util.js";
import { define, setMode, spawn, stateOf, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover, getFear } from "../core/store.js";
import { nearWard } from "../items/ward.js";
import { dropPage } from "../items/pages.js";
import { shining } from "../items/flashlight.js";

export function spawnDweller(player, hunting = false, extra = {}) {
  if (trackedFor(player, "dweller") && !extra.minion) return undefined;
  const at = extra.at ?? caveSpot(player, 14, 24, 180, 70) ?? caveSpot(player, 10, 20, 0, 180);
  if (!at || (!extra.minion && nearWard(player.dimension, at, 18))) return undefined;
  const e = spawn("dweller", at, player, "stalk", { major: true, ...extra });
  if (e && hunting) chase(e, player);
  return e;
}

export function chase(e, player) {
  const s = stateOf(e);
  if (!s) return;
  setMode(s, "chase");
  s.hunting = true;
  soundAt(player, "hl.scream", e.location, rand(0.9, 1.1), 1);
  soundAt(player, "mob.warden.roar", e.location, 1.3, 0.6);
  tryRun(() => player.addEffect("darkness", 120, { showParticles: false }));
  actionBar(player, tr("hl.cave.chase"));
  discover("dweller");
  addFear(player, 30);
}

// Driven off: by light, by a beating, or by the surface.
export function flee(e, player, byLight = false) {
  const s = stateOf(e);
  if (!s || s.mode === "flee") return;
  const was = s.mode;
  setMode(s, "flee");
  s.hunting = false;
  soundAt(player, "hl.chitter", e.location, 1.6, 1);
  discover("dweller");
  if ((byLight || was === "chase") && !s.minion) {
    dropPage(2, { x: e.location.x, y: e.location.y + 0.3, z: e.location.z }, player);
  }
  if (byLight) actionBar(player, tr("hl.flash.dweller"));
}

// Noises in the dark while the fear builds.
export function dwellerMinor(player) {
  if (!isUnderground(player)) return false;
  const f = getFear(player);
  const event = pick([
    ["cave", 4],
    ["steps", f >= 30 ? 4 : 0],
    ["chitter", f >= 40 ? 3 : 0],
    ["whisper", f >= 50 ? 2 : 0],
  ]);
  if (event === "cave") soundBehind(player, "ambient.cave", rand(8, 16), rand(0.7, 1.0), 1);
  else if (event === "steps") for (let i = 0; i < 6; i++) later(i * 3, () => soundBehind(player, "step.stone", 10 - i, 1.5, 0.8));
  else if (event === "chitter") soundBehind(player, "hl.chitter", rand(6, 12), rand(0.8, 1.1), 0.9);
  else if (event === "whisper") soundBehind(player, "hl.whisper", rand(3, 6), rand(0.7, 0.9), 0.7);
  addFear(player, 7);
  return true;
}

export function dwellerMajor(player) {
  if (!isUnderground(player)) return false;
  return !!spawnDweller(player, getFear(player) >= 90);
}

function tick(e, s, player) {
  if (s.minion) {
    // Called up by the Hollow Miner: it hunts until he falls.
    if (s.mode !== "chase") chase(e, player);
    return;
  }
  const under = isUnderground(player);
  s.surface = under ? 0 : (s.surface ?? 0) + 4;
  if (s.surface > (s.mode === "chase" ? 200 : 100)) {
    vanish(e, false);
    return;
  }
  if (s.mode === "flee") {
    s.timer += 4;
    if (s.timer > 60) vanish(e, false);
    return;
  }
  if (s.mode === "chase") {
    s.timer += 4;
    if (s.timer % 100 === 0) soundAt(player, Math.random() < 0.5 ? "hl.scream" : "hl.chitter", e.location, rand(0.8, 1.1), 1);
    if (s.timer > 20 * 30) {
      // You lived through it.
      dropPage(2, { x: player.location.x, y: player.location.y + 0.5, z: player.location.z }, player);
      vanish(e, false);
    }
    return;
  }
  if (nearWard(e.dimension, e.location, 10)) {
    flee(e, player);
    return;
  }
  // Stalking: it peeks at you. Catch it looking and it scurries away.
  const seen = canSee(player, e, 40, 0.9);
  if (seen) {
    s.seen++;
    s.unseen = 0;
    discover("dweller");
  } else {
    s.unseen++;
  }
  if (s.seen >= 3) {
    // Caught looking. In the beam of a flashlight, that counts as driving it off.
    flee(e, player, shining(player));
    addFear(player, 15);
    return;
  }
  if (!seen && s.unseen % 15 === 0 && Math.random() < 0.3) {
    if (Math.random() < 0.5) soundAt(player, "hl.chitter", e.location, rand(0.8, 1.2), 0.9);
    else for (let i = 0; i < 4; i++) later(i * 3, () => soundAt(player, "step.stone", e.location, 1.6, 0.7));
  }
  if (s.unseen >= 150) {
    s.unseen = 0;
    if (getFear(player) >= 80 || s.age > 20 * 60) {
      chase(e, player);
      return;
    }
    const at = caveSpot(player, 8, 12, 180, 60);
    if (at) {
      tryRun(() => e.teleport(at));
      faceTowards(e, player.location);
    }
  }
}

define({
  kind: "dweller",
  type: "hl:cave_dweller",
  voice: { id: "hl.growl", every: [7, 13], volume: 0.8, pitch: [0.9, 1.1] },
  stride: { id: "hl.step", every: 1.1, volume: 0.7, pitch: [1.2, 1.5] },
  modes: ["stalk", "chase", "flee"],
  weights: (ctx) => (ctx.under ? [5, 6] : [0, 0]),
  minor: dwellerMinor,
  major: dwellerMajor,
  tick,
  adopt: () => "stalk",
  shine: (e, s, player) => s.mode !== "flee" && !s.minion && flee(e, player, true),
  onHit: (e, s, player) => s.mode === "stalk" && chase(e, player),
  onHurt: (e, s, player) => {
    const health = tryRun(() => e.getComponent("minecraft:health"));
    if (!health || s.mode === "flee" || s.minion) return;
    if (health.currentValue < health.effectiveMax * 0.3) flee(e, player);
  },
  test: (player) => spawnDweller(player, false) ?? dwellerMinor(player),
});
