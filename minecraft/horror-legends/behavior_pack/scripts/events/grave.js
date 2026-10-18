// Your Grave. A bell, somewhere behind you, at night. Go and look: a mound
// of fresh earth, a headstone, a black rose, and on the stone, your name and
// tomorrow's date. Come close and something knocks, from underneath.

import { BlockPermutation, system, world } from "@minecraft/server";
import { actionBar, block, distance, groundSpot, later, soundAt, tr, tryRun } from "../core/util.js";
import { define } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { nearWard } from "../items/ward.js";

const LASTS = 20 * 180;
export const graves = new Map(); // playerId -> { at, start, knocked }

const solid = (b) => !!b && !b.isAir && !/water|lava|leaves|log|snow_layer|tall|grass$|flower|sapling|bush/.test(b.typeId);

// The plot: two blocks of mound running away from you, the stone at the far end.
function plot(player) {
  for (let attempt = 0; attempt < 6; attempt++) {
    const at = groundSpot(player, 9, 14, 180, 50);
    if (!at) continue;
    const dx = at.x - player.location.x;
    const dz = at.z - player.location.z;
    const step = Math.abs(dx) > Math.abs(dz) ? { x: Math.sign(dx), z: 0 } : { x: 0, z: Math.sign(dz) };
    const cells = [0, 1, 2].map((k) => ({ x: Math.floor(at.x) + step.x * k, z: Math.floor(at.z) + step.z * k }));
    const y = at.y;
    const ok = cells.every((c) => block(player.dimension, c.x, y, c.z)?.isAir && solid(block(player.dimension, c.x, y - 1, c.z)))
      && block(player.dimension, cells[2].x, y + 1, cells[2].z)?.isAir;
    if (ok && !nearWard(player.dimension, at)) return { cells, y, step };
  }
  return undefined;
}

function set(dimension, x, y, z, type, states) {
  const b = block(dimension, x, y, z);
  if (!b) return undefined;
  const perm = states && tryRun(() => BlockPermutation.resolve(type, states));
  if (perm) tryRun(() => b.setPermutation(perm));
  else tryRun(() => b.setType(type));
  return block(dimension, x, y, z);
}

export function digGrave(player) {
  if (graves.has(player.id)) return false;
  const p = plot(player);
  if (!p) return false;
  const dim = player.dimension;
  const [a, b, c] = p.cells;
  set(dim, a.x, p.y - 1, a.z, "minecraft:coarse_dirt");
  set(dim, b.x, p.y - 1, b.z, "minecraft:coarse_dirt");
  set(dim, a.x, p.y, a.z, "minecraft:wither_rose");
  set(dim, c.x, p.y, c.z, "minecraft:mossy_cobblestone");
  // The sign on the stone faces the mound (and you, coming up to it).
  const dir = ((Math.round((Math.atan2(p.step.x, -p.step.z) * 180) / Math.PI / 22.5) % 16) + 16) % 16;
  const sign = set(dim, c.x, p.y + 1, c.z, "minecraft:standing_sign", { ground_sign_direction: dir });
  tryRun(() => sign.getComponent("minecraft:sign")?.setText({
    rawtext: [{ translate: "hl.grave.sign", with: [player.name, String(world.getDay() + 1)] }],
  }));
  const at = { x: b.x + 0.5, y: p.y, z: b.z + 0.5 };
  graves.set(player.id, { at, start: system.currentTick, knocked: false });
  later(10, () => soundAt(player, "block.bell.hit", at, 0.5, 1));
  later(50, () => soundAt(player, "block.bell.hit", at, 0.5, 0.8));
  addFear(player, 10);
  return true;
}

function tickOne(player, g, now) {
  if (now - g.start > LASTS) {
    graves.delete(player.id);
    return;
  }
  if (!g.knocked && distance(player.location, g.at) < 4) {
    g.knocked = true;
    const under = { x: g.at.x, y: g.at.y - 1, z: g.at.z };
    for (let i = 0; i < 3; i++) later(i * 9, () => soundAt(player, "hl.knock", under, 0.6, 1));
    later(40, () => soundAt(player, "hl.whisper", under, 0.6, 1));
    later(45, () => actionBar(player, tr("hl.grave.below")));
    discover("grave");
    addFear(player, 30);
    later(80, () => graves.delete(player.id));
  }
}

// Every 5 ticks.
export function tickGraves() {
  const now = system.currentTick;
  for (const [id, g] of [...graves.entries()]) {
    const player = world.getAllPlayers().find((p) => p.id === id);
    if (!player) graves.delete(id);
    else tryRun(() => tickOne(player, g, now));
  }
}

define({
  kind: "grave",
  weights: (ctx) => [0, ctx.outside && ctx.night ? 3 : 0],
  major: digGrave,
  off: () => graves.clear(),
  test: digGrave,
});
