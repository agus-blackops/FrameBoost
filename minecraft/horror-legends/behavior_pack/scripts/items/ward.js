// The Ward Lantern: a lantern with an amethyst heart. Nothing haunts you
// within its light: the Presence can't reach the lights round it, the Cave
// Dweller and null won't come near it, the Man From The Fog stays out of its
// circle and Herobrine builds nothing inside it. But on a Red Night,
// something may snuff it out.

import { BlockPermutation } from "@minecraft/server";
import { OVERWORLD, actionBar, block, distance, soundAt, tr, tryRun } from "../lib/util.js";
import { setWorldProp, worldProp } from "../lib/state.js";

export const WARD = "hl:ward_lantern";
export const WARD_RADIUS = 14;

function list() {
  const raw = worldProp("hl:wards", "");
  return (raw && tryRun(() => JSON.parse(raw))) || [];
}

function save(wards) {
  setWorldProp("hl:wards", JSON.stringify(wards));
}

export function wards() {
  return list();
}

export function onPlace(b) {
  if (b?.typeId !== WARD) return;
  const wards = list().filter((w) => !(w.x === b.x && w.y === b.y && w.z === b.z));
  wards.push({ x: b.x, y: b.y, z: b.z });
  save(wards);
}

export function onBreak(b) {
  if (!b) return;
  const wards = list();
  const keep = wards.filter((w) => !(w.x === b.x && w.y === b.y && w.z === b.z));
  if (keep.length !== wards.length) save(keep);
}

// Is `location` inside the light of a ward? Wards that are gone (broken by
// something other than a player, blown up) are forgotten as they are found.
export function nearWard(dimension, location, radius = WARD_RADIUS) {
  if (dimension?.id !== OVERWORLD) return false;
  const wards = list();
  let stale = false;
  let hit = false;
  for (const w of wards) {
    if (distance({ x: w.x + 0.5, y: w.y + 0.5, z: w.z + 0.5 }, location) > radius) continue;
    const b = block(dimension, w.x, w.y, w.z);
    if (!b) {
      hit = true; // not loaded: trust it
      continue;
    }
    if (b.typeId === WARD) hit = true;
    else {
      w.gone = true;
      stale = true;
    }
  }
  if (stale) save(wards.filter((w) => !w.gone));
  return hit;
}

// The Red Night: each ward near the player may be snuffed out, its amethyst
// cracked, leaving an ordinary lantern.
export function snuffWards(player, chance = 0.25) {
  let snuffed = 0;
  for (const w of list()) {
    if (distance({ x: w.x, y: w.y, z: w.z }, player.location) > 48 || Math.random() >= chance) continue;
    const b = block(player.dimension, w.x, w.y, w.z);
    if (b?.typeId !== WARD) continue;
    const lantern = tryRun(() => BlockPermutation.resolve("minecraft:lantern"));
    if (!tryRun(() => (lantern ? b.setPermutation(lantern) : b.setType("minecraft:lantern"), true))) continue;
    onBreak(b);
    soundAt(player, "random.glass", { x: w.x + 0.5, y: w.y + 0.5, z: w.z + 0.5 }, 0.7, 1);
    snuffed++;
  }
  if (snuffed) actionBar(player, tr("hl.ward.snuffed"));
  return snuffed;
}
