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
