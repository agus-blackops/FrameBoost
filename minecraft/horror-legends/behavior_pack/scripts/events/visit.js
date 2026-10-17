// The Visit. At night, at home, three knocks on the door. Then a voice in
// the chat, with your name: "Open up. It's me. It's cold out here."
//
// Open the door and there is nobody, only the wind. A moment later someone
// thanks you for letting them in, right behind you, wearing your name. Turn
// around and it is there; don't, and it is gone.
//
// Don't open it, and it keeps knocking, louder, saying it knows you're in
// there. It tries the handle. Then silence, and in the morning there is a
// sign outside your door.

import { BlockPermutation, world } from "@minecraft/server";
import {
  actionBar, block, canSee, distance, faceTowards, groundSpot, later, sound, soundAt, soundBehind, tr, tryRun, valid,
} from "../core/util.js";
import { define } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { FAKE } from "../creatures/gamer.js";
import { nearWard } from "../items/ward.js";

const KNOCK_EVERY = 160; // 8 s between rounds of knocking
const RATTLE_AT = 900; // 45 s: the handle
const GIVE_UP_AT = 1200; // 60 s: silence, and the sign
const BEHIND_FOR = 120; // 6 s for you to turn around

// playerId -> { door, outside, phase, t, round, visitor }
export const visits = new Map();

function isDoor(b) {
  return !!b && /door/.test(b.typeId) && !/trapdoor/.test(b.typeId);
}

function states(b) {
  return tryRun(() => b.permutation.getAllStates()) ?? {};
}

// The nearest door (its lower half) within a few blocks.
export function findDoor(player, range = 7) {
  const { x, y, z } = player.location;
  let best;
  let bestD = Infinity;
  for (let dx = -range; dx <= range; dx++)
    for (let dz = -range; dz <= range; dz++)
      for (let dy = -2; dy <= 2; dy++) {
        const b = block(player.dimension, x + dx, y + dy, z + dz);
        if (!isDoor(b) || states(b).upper_block_bit) continue;
        const d = distance({ x: b.x + 0.5, y: b.y, z: b.z + 0.5 }, player.location);
        if (d < bestD) {
          bestD = d;
          best = b;
        }
      }
  return best;
}

// The side of the door away from you.
function outsideOf(player, door) {
  let best;
  let bestD = -1;
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    const b = block(player.dimension, door.x + dx, door.y, door.z + dz);
    if (!b?.isAir) continue;
    const at = { x: door.x + dx + 0.5, y: door.y, z: door.z + dz + 0.5 };
    const d = distance(at, player.location);
    if (d > bestD) {
      bestD = d;
      best = at;
    }
  }
  return best;
}

function doorCentre(v) {
  return { x: v.door.x + 0.5, y: v.door.y + 1, z: v.door.z + 0.5 };
}

function doorOpen(player, v) {
  const b = block(player.dimension, v.door.x, v.door.y, v.door.z);
  if (!isDoor(b)) return true; // broken down from the inside counts
  return !!states(b).open_bit;
}

// A line in the chat, from you.
function say(player, key) {
  tryRun(() => world.sendMessage({ rawtext: [{ text: `<${player.name}> ` }, { translate: key }] }));
}

function knock(player, v, volume) {
  const at = doorCentre(v);
  for (let k = 0; k < 3; k++) later(k * 7, () => soundAt(player, "hl.knock", at, 0.95 - k * 0.05, volume));
}

export function startVisit(player) {
  if (visits.has(player.id) || nearWard(player.dimension, player.location)) return false;
  const door = findDoor(player);
  const outside = door && outsideOf(player, door);
  if (!outside) return false;
  if (states(door).open_bit) return false; // it only knocks on a shut door
  const v = { door: { x: door.x, y: door.y, z: door.z }, outside, phase: "knock", t: 0, round: 0, visitor: undefined };
  visits.set(player.id, v);
  knock(player, v, 0.8);
  later(40, () => visits.get(player.id) === v && v.phase === "knock" && say(player, "hl.visit.1"));
  discover("visit");
  addFear(player, 15);
  return true;
}

function end(player, v) {
  if (valid(v.visitor)) tryRun(() => v.visitor.triggerEvent("hl:vanish"));
  v.phase = "done";
  visits.delete(player.id);
}

// Nobody there. And then somebody behind you.
function opened(player, v) {
  v.phase = "opened";
  v.t = 0;
  soundAt(player, "ambient.weather.rain", v.outside, 0.6, 0.6);
  soundAt(player, "hl.whisper", v.outside, 0.5, 0.4);
  actionBar(player, tr("hl.visit.nobody"));
  addFear(player, 10);
}

function appear(player, v) {
  const at = groundSpot(player, 1.6, 2.4, 180, 30);
  const e = at && tryRun(() => player.dimension.spawnEntity(FAKE, at));
  if (!e) {
    end(player, v);
    return;
  }
  tryRun(() => e.setDynamicProperty("hl:director", true));
  tryRun(() => e.setDynamicProperty("hl:visitor", true));
  tryRun(() => (e.nameTag = player.name));
  tryRun(() => e.triggerEvent("hl:freeze"));
  tryRun(() => e.setProperty("hl:state", "stare"));
  tryRun(() => e.setProperty("hl:eyes", true));
  faceTowards(e, player.location);
  v.visitor = e;
  v.phase = "behind";
  v.t = 0;
  soundBehind(player, "hl.breath", 1.2, 0.8, 1);
  say(player, "hl.visit.thanks");
  addFear(player, 15);
}

// It wasn't let in, so it leaves a message.
function leaveSign(player, v) {
  const { x, y, z } = v.outside;
  const b = block(player.dimension, x, y, z);
  if (!b?.isAir) return;
  const dx = v.door.x + 0.5 - x;
  const dz = v.door.z + 0.5 - z;
  const dir = ((Math.round((Math.atan2(-dx, dz) * 180) / Math.PI / 22.5) % 16) + 16) % 16;
  const perm = tryRun(() => BlockPermutation.resolve("minecraft:standing_sign", { ground_sign_direction: dir }));
  if (perm) tryRun(() => b.setPermutation(perm));
  else tryRun(() => b.setType("minecraft:standing_sign"));
  const placed = block(player.dimension, x, y, z);
  tryRun(() => placed.getComponent("minecraft:sign")?.setText({ rawtext: [{ translate: "hl.visit.sign" }] }));
}

function tickOne(player, v) {
  v.t += 10;
  const near = distance(doorCentre(v), player.location);
  if (v.phase === "knock") {
    if (doorOpen(player, v) || near < 1.6) return opened(player, v);
    if (near > 32) return end(player, v); // you left; it doesn't follow
    if (v.t >= GIVE_UP_AT) {
      soundAt(player, "hl.knock", doorCentre(v), 0.6, 0.3);
      leaveSign(player, v);
      actionBar(player, tr("hl.visit.silence"));
      addFear(player, 15);
      return end(player, v);
    }
    if (v.t >= RATTLE_AT && !v.rattled) {
      v.rattled = true;
      const at = doorCentre(v);
      for (let k = 0; k < 4; k++) later(k * 5, () => soundAt(player, k % 2 ? "random.door_close" : "random.door_open", at, 1.2, 0.9));
      later(30, () => say(player, "hl.visit.4"));
      addFear(player, 10);
    } else if (v.t % KNOCK_EVERY === 0 && v.round < 3) {
      v.round++;
      knock(player, v, 0.8 + v.round * 0.4);
      later(30, () => v.phase === "knock" && say(player, `hl.visit.${v.round + 1}`));
      addFear(player, 6);
    }
  } else if (v.phase === "opened") {
    if (v.t >= 40) appear(player, v);
  } else if (v.phase === "behind") {
    const e = v.visitor;
    if (!valid(e)) return end(player, v);
    if (canSee(player, e, 16, 0.8)) {
      scare(player, { rawtext: [{ text: `§4${player.name}` }] }, tr("hl.visit.scare_sub"));
      tryRun(() => player.runCommand("camerashake add @s 0.9 0.5 rotational"));
      addFear(player, 30);
      later(6, () => end(player, v));
      v.phase = "gone";
    } else if (v.t >= BEHIND_FOR) {
      sound(player, "hl.whisper", 0.8, 0.6);
      end(player, v);
    }
  }
}

// Every 10 ticks.
export function tickVisits() {
  for (const [id, v] of [...visits.entries()]) {
    const player = world.getAllPlayers().find((p) => p.id === id);
    if (!player) {
      if (valid(v.visitor)) tryRun(() => v.visitor.triggerEvent("hl:vanish"));
      visits.delete(id);
      continue;
    }
    tryRun(() => tickOne(player, v));
  }
}

export function onPlayerDied(player) {
  const v = visits.get(player.id);
  if (v) end(player, v);
}

export function isVisitor(entity) {
  return !!tryRun(() => entity.getDynamicProperty("hl:visitor"));
}

define({
  kind: "visit",
  weights: (ctx) => [0, ctx.indoors && ctx.night ? 5 : 0],
  major: startVisit,
  off: () => {
    for (const [id, v] of [...visits.entries()]) {
      if (valid(v.visitor)) tryRun(() => v.visitor.triggerEvent("hl:vanish"));
      visits.delete(id);
    }
  },
  test: startVisit,
});
