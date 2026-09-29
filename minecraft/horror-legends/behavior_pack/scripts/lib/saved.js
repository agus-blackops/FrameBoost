// Blocks the add-on changes for a while, remembered in the world so they
// come back even if the game is closed in between: missing-texture
// corruption, lights that went out, the flashlight's invisible light.

import { BlockPermutation, world } from "@minecraft/server";
import { OVERWORLD, block, tryRun } from "./util.js";
import { setWorldProp, worldProp } from "./state.js";

export function savedBlocks(key) {
  const raw = worldProp(key, "");
  return (raw && tryRun(() => JSON.parse(raw))) || [];
}

export function saveBlocks(key, list) {
  setWorldProp(key, JSON.stringify(list));
}

// Change a block and remember what it was, to be put back at absolute time
// `at` by restoreBlocks. Returns false if nothing changed.
export function swapBlock(key, b, to, at) {
  const type = b.typeId;
  const st = tryRun(() => b.permutation.getAllStates()) ?? {};
  const ok = typeof to === "string" ? tryRun(() => (b.setType(to), true)) : tryRun(() => (b.setPermutation(to), true));
  if (!ok) return false;
  const list = savedBlocks(key);
  list.push({ x: b.x, y: b.y, z: b.z, type, st, at });
  saveBlocks(key, list);
  return true;
}

// Put saved blocks back once their time is up, if nothing else has taken
// their place since (`marker` tests what the add-on left there).
export function restoreBlocks(key, marker, force = false) {
  const list = savedBlocks(key);
  if (!list.length) return;
  const now = world.getAbsoluteTime();
  const dimension = world.getDimension(OVERWORLD);
  const keep = [];
  for (const entry of list) {
    if (!force && entry.at > now) {
      keep.push(entry);
      continue;
    }
    const b = block(dimension, entry.x, entry.y, entry.z);
    if (!b) {
      keep.push(entry); // not loaded: try again later
      continue;
    }
    if (!marker(b.typeId)) continue; // broken or built over since
    const perm = tryRun(() => BlockPermutation.resolve(entry.type, entry.st));
    if (!(perm && tryRun(() => (b.setPermutation(perm), true)))) tryRun(() => b.setType(entry.type));
  }
  if (keep.length !== list.length) saveBlocks(key, keep);
}
