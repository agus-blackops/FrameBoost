// The flashlight: use it to switch it on and off. While it is on and in
// your hand it really lights the way: invisible light blocks follow your
// head and the spot you're pointing at. It runs on batteries (five minutes
// each); a spare battery in your inventory goes in by itself. Its beam
// drives off the Cave Dweller, makes Herobrine, null and a watching Man From
// The Fog vanish, and burns Herobrine's true form. A running Man From The
// Fog doesn't care.

import { BlockPermutation, system, world } from "@minecraft/server";
import {
  OVERWORLD, actionBar, block, canSee, heldItem, later, overworldPlayers, playerById, sound, takeItem, tr, tryRun, valid,
} from "../lib/util.js";
import { tracked, vanish } from "../lib/actors.js";
import { playerProp, setPlayerProp } from "../lib/state.js";
import { saveBlocks, savedBlocks } from "../lib/saved.js";
import { flee } from "../threats/dweller.js";
import { glitch } from "../threats/null.js";
import { burnWithLight } from "../threats/boss.js";

export const FLASHLIGHT = "hl:flashlight";
export const BATTERY = "hl:battery";
const DRAIN_TICKS = 60; // 1% of charge; 100% = 5 minutes
const on = new Set();
const lit = new Map(); // playerId -> ["x,y,z"]

let lightPerm;
function light() {
  if (lightPerm === undefined) {
    lightPerm =
      tryRun(() => BlockPermutation.resolve("minecraft:light_block_14")) ??
      tryRun(() => BlockPermutation.resolve("minecraft:light_block", { block_light_level: 14 })) ??
      null;
  }
  return lightPerm;
}

const isLight = (type) => typeof type === "string" && type.startsWith("minecraft:light_block");

export function charge(player) {
  return playerProp(player, "hl:battery", 100);
}

function setCharge(player, value) {
  setPlayerProp(player, "hl:battery", Math.max(0, Math.min(100, value)));
}

export function isOn(player) {
  return on.has(player.id);
}

// On, and in your hand.
export function shining(player) {
  return on.has(player.id) && heldItem(player)?.typeId === FLASHLIGHT;
}

export function toggleFlashlight(player) {
  if (on.has(player.id)) {
    on.delete(player.id);
    clearLights(player.id);
    sound(player, "hl.click", 1, 0.9);
    return false;
  }
  if (charge(player) <= 0 && !swapBattery(player)) {
    actionBar(player, tr("hl.flash.empty"));
    sound(player, "hl.click", 0.6, 0.6);
    return false;
  }
  on.add(player.id);
  sound(player, "hl.click", 1, 1.2);
  showCharge(player);
  return true;
}

function swapBattery(player) {
  if (!takeItem(player, BATTERY, 1)) return false;
  setCharge(player, 100);
  sound(player, "hl.click", 1, 1.4);
  actionBar(player, tr("hl.flash.swap"));
  return true;
}

function showCharge(player) {
  const c = charge(player);
  const bars = Math.ceil(c / 10);
  const color = c > 50 ? "§a" : c > 20 ? "§e" : "§c";
  actionBar(player, { rawtext: [{ translate: "hl.flash.bar" }, { text: ` ${color}${"|".repeat(bars)}§8${"|".repeat(10 - bars)} §7${c}%` }] });
}

// ------------------------------------------------------------------------- //
// The light itself
// ------------------------------------------------------------------------- //

function keyOf(x, y, z) {
  return `${Math.floor(x)},${Math.floor(y)},${Math.floor(z)}`;
}

// Where the light goes: at your head, halfway along the beam, and just in
// front of whatever the beam hits (up to 16 blocks).
function targets(player) {
  const eye = player.getHeadLocation();
  const dir = player.getViewDirection();
  const hit = tryRun(() =>
    player.dimension.getBlockFromRay(eye, dir, { maxDistance: 16, includeLiquidBlocks: false, includePassableBlocks: false })
  );
  const reach = hit ? Math.max(0, Math.hypot(hit.block.x + 0.5 - eye.x, hit.block.y + 0.5 - eye.y, hit.block.z + 0.5 - eye.z) - 1.2) : 16;
  const at = (t) => keyOf(eye.x + dir.x * t, eye.y + dir.y * t, eye.z + dir.z * t);
  return [...new Set([keyOf(eye.x, eye.y, eye.z), at(reach / 2), at(reach)])];
}

function placeLight(dimension, key) {
  const [x, y, z] = key.split(",").map(Number);
  const b = block(dimension, x, y, z);
  const perm = light();
  if (!b || !perm) return false;
  if (isLight(b.typeId)) return true;
  if (!b.isAir) return false;
  return !!tryRun(() => (b.setPermutation(perm), true));
}

function removeLight(dimension, key) {
  const [x, y, z] = key.split(",").map(Number);
  const b = block(dimension, x, y, z);
  if (b && isLight(b.typeId)) tryRun(() => b.setType("minecraft:air"));
}

function clearLights(playerId) {
  const dimension = world.getDimension(OVERWORLD);
  for (const key of lit.get(playerId) ?? []) removeLight(dimension, key);
  lit.delete(playerId);
  remember();
}

// The world remembers every light block we placed, so none are left behind
// if the game closes with a flashlight on.
function remember() {
  const all = [...lit.values()].flat().map((k) => k.split(",").map(Number));
  saveBlocks("hl:flashlight", all.map(([x, y, z]) => ({ x, y, z })));
}

export function cleanupLights() {
  const dimension = world.getDimension(OVERWORLD);
  for (const { x, y, z } of savedBlocks("hl:flashlight")) removeLight(dimension, keyOf(x, y, z));
  saveBlocks("hl:flashlight", []);
}

// Every 3 ticks.
export function tickFlashlights() {
  for (const id of [...on]) {
    const player = playerById(id);
    if (!player || !valid(player)) {
      on.delete(id);
      clearLights(id);
      continue;
    }
    if (heldItem(player)?.typeId !== FLASHLIGHT || player.dimension.id !== OVERWORLD) {
      if (lit.has(id)) clearLights(id);
      continue;
    }
    const want = targets(player);
    const old = lit.get(id) ?? [];
    const placed = want.filter((k) => placeLight(player.dimension, k));
    for (const k of old) if (!placed.includes(k)) removeLight(player.dimension, k);
    const changed = placed.length !== old.length || placed.some((k, i) => k !== old[i]);
    lit.set(id, placed);
    if (changed) remember();
  }
}

// Every 20 ticks: drain the battery, show the charge, and shine on things.
export function tickBeams() {
  const now = system.currentTick;
  for (const player of overworldPlayers()) {
    if (!on.has(player.id) || heldItem(player)?.typeId !== FLASHLIGHT) continue;
    if (now % DRAIN_TICKS < 20) {
      setCharge(player, charge(player) - 1);
      if (charge(player) <= 0 && !swapBattery(player)) {
        // It flickers, and dies.
        clearLights(player.id);
        on.delete(player.id);
        actionBar(player, tr("hl.flash.dead"));
        sound(player, "hl.click", 1, 0.5);
        later(10, () => sound(player, "hl.whisper", 0.6, 0.9));
        continue;
      }
    }
    showCharge(player);
    shine(player);
  }
}

function shine(player) {
  for (const s of [...tracked.values()]) {
    const e = s.entity;
    if (s.playerId !== player.id || !valid(e) || !canSee(player, e, 24, 0.93)) continue;
    if (s.kind === "dweller" && s.mode !== "fleeing" && !s.minion) flee(e, player, true);
    else if (s.kind === "herobrine" && s.mode !== "reveal") vanish(e);
    else if (s.kind === "null") {
      glitch(player);
      vanish(e, false);
    } else if (s.kind === "fogMan") {
      if (s.mode === "chasing") actionBar(player, tr("hl.flash.fog"));
      else vanish(e);
    } else if (s.kind === "boss") burnWithLight(e, player);
  }
}

export function onPlayerLeave(id) {
  on.delete(id);
  clearLights(id);
}
