// Moves that many creatures share: creeping closer while you aren't looking,
// keeping out of a Ward Lantern's light, and where the player is (the
// context the director hands every creature).

import { system } from "@minecraft/server";
import {
  faceTowards, groundAt, isIndoors, isNight, isUnderground, rand, surroundings, tryRun, inWater,
} from "./util.js";
import { WARD_RADIUS, nearWard } from "../items/ward.js";
import { flags } from "./store.js";
import { inAncientCity } from "./places.js";

export function outsideWards(dimension, at, margin = 2) {
  return !nearWard(dimension, at, WARD_RADIUS + margin);
}

// Somewhere on the ground a fraction `k` of the way from the player to the
// creature (never nearer than `min`), not in a tree or a ward's light.
// Returns true if it moved.
export function stepCloser(e, player, k = 0.6, min = 8) {
  const d = Math.hypot(e.location.x - player.location.x, e.location.z - player.location.z) || 1;
  const f = Math.max(min, d * k) / d;
  for (let attempt = 0; attempt < 5; attempt++) {
    const x = player.location.x + (e.location.x - player.location.x) * f + (attempt ? rand(-3, 3) : 0);
    const z = player.location.z + (e.location.z - player.location.z) * f + (attempt ? rand(-3, 3) : 0);
    const y = groundAt(player.dimension, x, z, player.location.y);
    if (y === undefined || !outsideWards(e.dimension, { x, y, z })) continue;
    if (!tryRun(() => (e.teleport({ x, y, z }), true))) continue;
    faceTowards(e, player.location);
    return true;
  }
  return false;
}

// Where the player is, worked out once a second for the director.
const cache = new Map();

export function context(player) {
  const now = system.currentTick;
  const hit = cache.get(player.id);
  if (hit && now - hit.at < 20) return hit.ctx;
  const under = isUnderground(player);
  const land = under ? { water: 0, trees: 0, snow: 0, samples: 0, jungle: 0, taiga: 0 } : surroundings(player);
  const ctx = {
    under,
    indoors: !under && isIndoors(player),
    night: isNight() || flags.redNight,
    water: land.water,
    swimming: inWater(player),
    trees: land.trees,
    snow: land.snow,
    jungle: land.jungle >= 2,
    taiga: land.taiga >= 2,
  };
  ctx.outside = !ctx.under && !ctx.indoors;
  ctx.forest = ctx.trees >= 3;
  ctx.ancient = under && inAncientCity(player);
  cache.set(player.id, { at: now, ctx });
  return ctx;
}
