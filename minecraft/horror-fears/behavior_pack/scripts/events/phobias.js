// Fears. Everyone is afraid of something, and the game knows what. While you
// stand in the dark with no light, on a ledge high above the ground, in deep
// water, or in a tunnel you barely fit in, a fear of that kind slowly fills.
// When it is full, it happens: something made for exactly that fear, loud.
// The journal shows how full each one is.

import {
  actionBar, block, isNight, inWater, isUnderground, later, overworldPlayers, rand, sound, soundAt, soundBehind, tr, tryRun,
} from "../core/util.js";
import { define } from "../core/creatures.js";
import { active, addFear, discover, getFear } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { isOn } from "../items/flashlight.js";
import { nearWard } from "../items/ward.js";

export const PHOBIAS = ["dark", "heights", "water", "closed"];
const FULL = 100;
const SECOND = 20;

// playerId -> { dark, heights, water, closed, cool }
export const levels = new Map();
const LIGHT = /torch|lantern|glowstone|lava|fire|candle|sea_lantern|shroomlight|froglight|lit_|campfire|end_rod|jack_o/;

function state(player) {
  let s = levels.get(player.id);
  if (!s) levels.set(player.id, (s = { dark: 0, heights: 0, water: 0, closed: 0, cool: 0 }));
  return s;
}

export function level(player, phobia) {
  return Math.round(state(player)[phobia] ?? 0);
}

// ------------------------------------------------------------------------- //
// What exposes you to what
// ------------------------------------------------------------------------- //

// Standing in the dark: underground or at night, no light of your own, and
// nothing burning within a few blocks.
export function inDark(player) {
  if (nearWard(player.dimension, player.location)) return false;
  if (!(isUnderground(player) || isNight())) return false;
  if (isOn(player)) return false;
  const { x, y, z } = player.location;
  for (let dx = -3; dx <= 3; dx++)
    for (let dz = -3; dz <= 3; dz++)
      for (let dy = -1; dy <= 3; dy++) {
        const b = block(player.dimension, x + dx, y + dy, z + dz);
        if (b && LIGHT.test(b.typeId)) return false;
      }
  return true;
}

// How far it is to the ground below, up to 40 blocks.
function dropAt(player, dx, dz) {
  const from = { x: player.location.x + dx, y: player.location.y, z: player.location.z + dz };
  const hit = tryRun(() =>
    player.dimension.getBlockFromRay(from, { x: 0, y: -1, z: 0 }, {
      maxDistance: 40, includeLiquidBlocks: true, includePassableBlocks: false,
    })
  );
  return hit ? from.y - (hit.block.y + 1) : 40;
}

// The drop at your feet, or at the edge of the ledge you are standing on.
export function dropBelow(player) {
  return Math.max(...[[0, 0], [4, 0], [-4, 0], [0, 4], [0, -4]].map(([dx, dz]) => dropAt(player, dx, dz)));
}

export function onHeight(player) {
  return !inWater(player) && dropBelow(player) >= 14;
}

// Deep water: swimming with a long way down.
export function inDeepWater(player) {
  if (!inWater(player)) return false;
  const hit = tryRun(() =>
    player.dimension.getBlockFromRay(player.location, { x: 0, y: -1, z: 0 }, {
      maxDistance: 30, includeLiquidBlocks: false, includePassableBlocks: true,
    })
  );
  return !hit || player.location.y - hit.block.y >= 8;
}

const solid = (b) => !!b && !b.isAir && !/water|lava|torch|grass$|flower|sapling|rail|button|sign|carpet|snow_layer/.test(b.typeId);

// A tunnel you barely fit in: a ceiling right above, walls close all round.
export function inTightSpace(player) {
  const { x, y, z } = player.location;
  const cx = Math.floor(x);
  const cy = Math.floor(y);
  const cz = Math.floor(z);
  if (!solid(block(player.dimension, cx, cy + 2, cz))) return false;
  let walls = 0;
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    if (solid(block(player.dimension, cx + dx, cy, cz + dz))) walls++;
    if (solid(block(player.dimension, cx + dx, cy + 1, cz + dz))) walls++;
  }
  return walls >= 5;
}

const EXPOSURE = {
  dark: [inDark, 100 / 25], // full after 25 s
  heights: [onHeight, 100 / 12],
  water: [inDeepWater, 100 / 14],
  closed: [inTightSpace, 100 / 20],
};

// ------------------------------------------------------------------------- //
// What happens when one is full
// ------------------------------------------------------------------------- //

function dark(player) {
  // The dark has been looking at you the whole time.
  blackout(player, 1.1);
  sound(player, "hl.boom", 1, 0.8);
  soundBehind(player, "hl.breath", 1.4, 0.7, 1);
  later(12, () => {
    soundBehind(player, "hl.screech", 2.5, 0.9, 1);
    tryRun(() => player.runCommand("camerashake add @s 1 0.8 rotational"));
    scare(player, " ", tr("hl.fear.dark_sub"));
  });
  later(60, () => soundBehind(player, "hl.whisper", 1.2, 0.7, 1));
  tryRun(() => player.addEffect("darkness", 160, { showParticles: false }));
}

function heights(player) {
  // The ground is a very long way away, and it is calling you.
  tryRun(() => player.runCommand("camerashake add @s 0.9 4 rotational"));
  tryRun(() => player.addEffect("nausea", 160, { showParticles: false }));
  sound(player, "hl.heartbeat", 1, 1.4);
  later(10, () => sound(player, "hl.wail", 0.9, 0.7));
  later(30, () => soundBehind(player, "hl.whisper", 1, 0.6, 1));
  actionBar(player, tr("hl.fear.heights_bar"));
  later(50, () => scare(player, " ", tr("hl.fear.heights_sub")));
}

function water(player) {
  // Something enormous passes underneath you.
  sound(player, "hl.boom", 1, 0.55);
  soundAt(player, "hl.gurgle", { x: player.location.x, y: player.location.y - 5, z: player.location.z }, 0.6, 1);
  tryRun(() => player.runCommand("camerashake add @s 1 2.5 positional"));
  tryRun(() => player.addEffect("slowness", 100, { amplifier: 1, showParticles: false }));
  later(25, () => sound(player, "hl.screech", 1, 0.6));
  later(30, () => scare(player, " ", tr("hl.fear.water_sub")));
  actionBar(player, tr("hl.fear.water_bar"));
}

function closed(player) {
  // The walls knock, from the other side, from everywhere. Then they close in.
  const { x, y, z } = player.location;
  for (let k = 0; k < 8; k++) {
    later(k * 6, () => {
      const a = rand(0, Math.PI * 2);
      soundAt(player, "hl.knock", { x: x + Math.cos(a) * 2, y: y + 1, z: z + Math.sin(a) * 2 }, rand(0.7, 1), 1);
    });
  }
  later(50, () => {
    sound(player, "hl.boom", 1, 0.7);
    sound(player, "hl.shriek", 1, 0.8);
    blackout(player, 0.7);
    tryRun(() => player.runCommand("camerashake add @s 1 1.2 rotational"));
    tryRun(() => player.addEffect("darkness", 120, { showParticles: false }));
    scare(player, " ", tr("hl.fear.closed_sub"));
  });
  actionBar(player, tr("hl.fear.closed_bar"));
}

const SCARES = { dark, heights, water, closed };

export function triggerFear(player, phobia) {
  const s = state(player);
  s[phobia] = 40; // it ebbs, but never goes away
  s.cool = 20 * SECOND; // not two at once
  SCARES[phobia](player);
  discover("phobias");
  addFear(player, 35);
  return true;
}

// ------------------------------------------------------------------------- //
// Every second
// ------------------------------------------------------------------------- //

export function tickPhobias() {
  if (!active("phobias")) return;
  for (const player of overworldPlayers()) {
    const s = state(player);
    if (s.cool > 0) s.cool -= SECOND;
    // Already afraid? Everything fills faster.
    const scared = 1 + getFear(player) / 120;
    for (const p of PHOBIAS) {
      const [test, rate] = EXPOSURE[p];
      const exposed = tryRun(() => test(player));
      s[p] = exposed ? Math.min(FULL, s[p] + rate * scared) : Math.max(0, s[p] - 4);
    }
    if (s.cool > 0) continue;
    const full = PHOBIAS.filter((p) => s[p] >= FULL);
    if (full.length) tryRun(() => triggerFear(player, full[Math.floor(Math.random() * full.length)]));
  }
}

export function onPlayerDied(player) {
  levels.delete(player.id);
}

// The most-filled fear, for the journal.
export function worst(player) {
  const s = state(player);
  return PHOBIAS.reduce((a, b) => (s[b] > s[a] ? b : a));
}

define({
  kind: "phobias",
  weights: () => [0, 0],
  // Test: trigger the fullest fear (or the dark if all are empty).
  test: (player) => {
    const p = worst(player);
    return triggerFear(player, state(player)[p] > 0 ? p : "dark");
  },
  off: () => levels.clear(),
});
