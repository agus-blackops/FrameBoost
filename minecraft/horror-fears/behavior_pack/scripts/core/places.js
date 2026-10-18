// Recognising places the world built itself. An Ancient City is deep under
// the ground (below y 0), and around you there is sculk and the stone the
// city was built from: deepslate tiles and bricks, reinforced deepslate,
// candles, soul lanterns.

import { system } from "@minecraft/server";
import { block, tryRun } from "./util.js";

export const CITY_STONE = /deepslate_(tiles|bricks)|reinforced_deepslate|polished_deepslate|chiseled_deepslate|soul_lantern|candle|sculk_(shrieker|sensor|catalyst)|gray_wool|dark_oak_fence|soul_fire|skull/;
const SCULK = /sculk/;

const cache = new Map(); // playerId -> { at, yes }

// Sampled on a coarse grid around the player; worked out at most once a second.
export function inAncientCity(player) {
  const now = system.currentTick;
  const hit = cache.get(player.id);
  if (hit && now - hit.at < 20) return hit.yes;
  let yes = false;
  const { x, y, z } = player.location;
  if (y < 0 && tryRun(() => player.dimension.id) === "minecraft:overworld") {
    let stone = 0;
    let sculk = 0;
    for (let dx = -8; dx <= 8 && !yes; dx += 4)
      for (let dz = -8; dz <= 8 && !yes; dz += 4)
        for (let dy = -2; dy <= 3; dy += 1) {
          const t = block(player.dimension, x + dx, y + dy, z + dz)?.typeId ?? "";
          if (SCULK.test(t)) sculk++;
          if (CITY_STONE.test(t)) stone++;
          if (sculk >= 2 && stone >= 4) {
            yes = true;
            break;
          }
        }
  }
  cache.set(player.id, { at: now, yes });
  return yes;
}

// The nearest blocks of a kind (a regex on the type id) within `r`.
export function findBlocks(player, re, r = 10, max = 8) {
  const out = [];
  const { x, y, z } = player.location;
  for (let dx = -r; dx <= r && out.length < max; dx++)
    for (let dz = -r; dz <= r && out.length < max; dz++)
      for (let dy = -3; dy <= 4; dy++) {
        const b = block(player.dimension, x + dx, y + dy, z + dz);
        if (b && re.test(b.typeId)) {
          out.push(b);
          if (out.length >= max) break;
        }
      }
  return out;
}

// A trunk of a given kind (a regex on the block id), `minD` to `maxD` blocks
// from the player, and the spot of open ground right beside its base. Trees
// are thin, so this looks at a few hundred columns of the ring and, in each,
// a few heights around the player's own.
export function trunkSpot(player, re, minD, maxD, tries = 500) {
  const { x, y, z } = player.location;
  const fy = Math.floor(y);
  for (let i = 0; i < tries; i++) {
    const a = Math.random() * Math.PI * 2;
    const r = minD + Math.random() * (maxD - minD);
    const cx = Math.floor(x + Math.cos(a) * r);
    const cz = Math.floor(z + Math.sin(a) * r);
    let hit;
    for (const dy of [1, 3, -1, 5]) {
      const b = block(player.dimension, cx, fy + dy, cz);
      if (b && re.test(b.typeId)) {
        hit = b;
        break;
      }
    }
    if (!hit) continue;
    // Down to the lowest log of this trunk.
    let base = hit;
    for (let k = 1; k <= 12; k++) {
      const b = block(player.dimension, cx, hit.y - k, cz);
      if (b && re.test(b.typeId)) base = b;
      else break;
    }
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const at = block(player.dimension, base.x + dx, base.y, base.z + dz);
      const head = block(player.dimension, base.x + dx, base.y + 1, base.z + dz);
      const floor = block(player.dimension, base.x + dx, base.y - 1, base.z + dz);
      if (at?.isAir && head?.isAir && floor && !floor.isAir && !/water|lava|leaves/.test(floor.typeId)) {
        return { x: base.x + dx + 0.5, y: base.y, z: base.z + dz + 0.5 };
      }
    }
  }
  return undefined;
}
