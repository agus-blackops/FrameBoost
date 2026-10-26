// Act II: the Lick. Deep in a cave, at the very back, a figure in black
// standing perfectly still and facing you, a white mask with a long red
// tongue hanging out of the smile. Around where it waits, the floor is marked
// with crosses of cobblestone. It does nothing. Until you come too close, or
// put a light on it.
//
//   still    at the back of the cave, among its crosses
//   chase    fast, and it climbs. It cannot be hurt. Get out of the cave
//   minor    you find crosses on the floor of the cave behind you
// The crosses go back to stone after a while (core/blocks.js).

import { world } from "@minecraft/server";
import {
  NATURAL_STONE, actionBar, block, canSee, caveSpot, distance, faceTowards, isUnderground, later, rand, randInt,
  soundAt, soundBehind, tr, tryRun,
} from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { restoreBlocks, swapBlock } from "../core/blocks.js";

export const CROSSES = "hb:crosses";
export const isCross = (type) => type === "minecraft:cobblestone";
const CHASE_FOR = 20 * 20;
const NEAR = 10;

// A cross of cobblestone laid into the cave floor at (x, z), five blocks.
export function cross(dimension, x, y, z) {
  const back = world.getAbsoluteTime() + 24000; // for a day
  let n = 0;
  for (const [dx, dz] of [[0, 0], [1, 0], [-1, 0], [0, 1], [0, -1], [0, -2]]) {
    const floor = block(dimension, x + dx, y - 1, z + dz);
    const above = block(dimension, x + dx, y, z + dz);
    if (!floor || !above?.isAir || !NATURAL_STONE.test(floor.typeId)) continue;
    if (swapBlock(CROSSES, floor, "minecraft:cobblestone", back)) n++;
  }
  return n >= 3;
}

function crossesAround(player, at, count) {
  let made = 0;
  for (let i = 0; i < count * 3 && made < count; i++) {
    const a = rand(0, Math.PI * 2);
    const r = rand(2.5, 5);
    const x = Math.floor(at.x + Math.cos(a) * r);
    const z = Math.floor(at.z + Math.sin(a) * r);
    for (const dy of [0, -1, 1]) {
      if (cross(player.dimension, x, Math.floor(at.y) + dy, z)) {
        made++;
        break;
      }
    }
  }
  return made;
}

export function waiting(player, anywhere = false) {
  if (trackedFor(player, "lick") || nearWard(player.dimension, player.location)) return false;
  if (!anywhere && !isUnderground(player)) return false;
  const at = caveSpot(player, 16, 26, rand(-30, 30), 20) ?? (anywhere ? caveSpot(player, 8, 14, 0, 90) : undefined);
  if (!at) return false;
  const e = spawn("lick", at, player, "still", { major: true, clock: 0, seen: false });
  if (!e) return false;
  faceTowards(e, player.location);
  crossesAround(player, at, randInt(3, 5));
  soundAt(player, "hb.breath", at, 0.5, 0.4);
  addFear(player, 12);
  return true;
}

export function crossesMinor(player) {
  const p = player.location;
  const v = player.getViewDirection();
  const x = Math.floor(p.x - v.x * 4);
  const z = Math.floor(p.z - v.z * 4);
  for (const dy of [0, -1, 1]) {
    if (cross(player.dimension, x, Math.floor(p.y) + dy, z)) {
      soundBehind(player, "hb.creak", 3, 0.6, 0.6);
      later(20, () => actionBar(player, tr("hb.lk.crosses")));
      addFear(player, 8);
      return true;
    }
  }
  return false;
}

function chase(e, s, player) {
  if (s.mode === "chase") return;
  s.hunting = true;
  s.clock = 0;
  setMode(s, "chase");
  soundAt(player, "hb.screech", e.location, 0.6, 1);
  scare(player, " ", tr("hb.lk.sub"));
  discover("lick");
  addFear(player, 30);
}

function tick(e, s, player) {
  s.clock += 4;
  const d = distance(player.location, e.location);
  // Out of the cave, or far enough away, and it's gone.
  if (nearWard(player.dimension, player.location) || d > 48 || (!s.adopted && !isUnderground(player) && s.clock > 40)) {
    vanish(e, false);
    later(200, () => restoreBlocks(CROSSES, isCross, true));
    return;
  }
  if (s.mode === "chase") {
    if (s.clock >= CHASE_FOR && !s.adopted) vanish(e, true);
    return;
  }
  faceTowards(e, player.location);
  if (!s.seen && canSee(player, e, 40, 0.9)) {
    s.seen = true;
    actionBar(player, tr("hb.lk.seen"));
    discover("lick");
  }
  if (d < NEAR) chase(e, s, player);
  else if (s.clock > 20 * 150 && !s.adopted) vanish(e, false);
}

define({
  kind: "lick",
  type: "hb:lick",
  modes: ["still", "chase"],
  stride: { id: "hb.step", every: 1.2, volume: 0.8, pitch: [0.8, 0.9] },
  weights: (ctx) => (ctx.under ? [3, 4] : [0, 0]),
  minor: crossesMinor,
  major: (player) => waiting(player),
  tick,
  adopt: () => "still",
  // A light on it, or a blow, and it comes.
  shine: (e, s, player) => s.mode === "still" && distance(player.location, e.location) < 24 && chase(e, s, player),
  onHit: (e, s, player) => chase(e, s, player),
  onStrike: (e, s, player) => tryRun(() => player.addEffect("blindness", 60, { showParticles: false })),
  off: () => restoreBlocks(CROSSES, isCross, true),
  test: (player) => waiting(player, true) || crossesMinor(player),
});
