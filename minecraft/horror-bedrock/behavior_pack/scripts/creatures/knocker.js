// Act I: the Knocker. A pale dead thing, too tall and too thin for a zombie,
// with long white fingers and a mouth that hangs open. It learns where you
// live. At night, it knocks: three slow knocks on your door, and then again.
// It never comes in on its own. It waits for you to open the door.
//
//   knock    standing outside the nearest door, knocking. Open it, and it
//            comes in. Don't, and after a while the knocking stops, and in
//            the silence something taps on a window
//   bedside  it learned where you sleep: when you wake, it is beside the bed
//   minor    knocks on a wall, a door, a window, somewhere in the house

import {
  actionBar, block, canSee, distance, faceTowards, later, rand, randInt, soundAt, soundBehind, tr, tryRun,
} from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { findBlocks } from "../core/places.js";
import { nearWard } from "../items/ward.js";
import { bedside } from "../events/sleep.js";

const DOOR = /^minecraft:(\w+_)?door$/;
const GLASS = /glass/;
const KNOCK_FOR = 20 * 40; // how long it waits at the door
const HUNT_FOR = 20 * 30;

function isDoor(b) {
  return !!b && DOOR.test(b.typeId) && !/trapdoor/.test(b.typeId);
}

function doorOpen(b) {
  return !!tryRun(() => b.permutation.getState("open_bit"));
}

// Three slow knocks from a place.
function knocks(player, at, n = 3, pitch = 1) {
  for (let i = 0; i < n; i++) later(i * 9, () => soundAt(player, "hb.knock", at, pitch * rand(0.95, 1.05), 1));
}

// The two sides of a door: open floor on each, and which is further from you.
function outsideOf(player, door) {
  let best;
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    const at = block(player.dimension, door.x + dx, door.y, door.z + dz);
    const head = block(player.dimension, door.x + dx, door.y + 1, door.z + dz);
    const floor = block(player.dimension, door.x + dx, door.y - 1, door.z + dz);
    if (!at?.isAir || !head?.isAir || !floor || floor.isAir) continue;
    const spot = { x: door.x + dx + 0.5, y: door.y, z: door.z + dz + 0.5 };
    const d = distance(spot, player.location);
    if (!best || d > best.d) best = { spot, d };
  }
  return best?.spot;
}

export function knock(player, anywhere = false) {
  if (trackedFor(player, "knocker") || nearWard(player.dimension, player.location)) return false;
  const doors = findBlocks(player, DOOR, 12, 16).filter(
    (b) => isDoor(b) && !tryRun(() => b.permutation.getState("upper_block_bit")) && !doorOpen(b)
  );
  doors.sort((a, b) => distance(a, player.location) - distance(b, player.location));
  for (const door of doors) {
    if (distance(door, player.location) < 3) continue; // not with you standing in the doorway
    const at = outsideOf(player, door);
    if (!at) continue;
    const e = spawn("knocker", at, player, "lurk", {
      major: true, how: "door", door: { x: door.x, y: door.y, z: door.z }, clock: 0, next: 20,
    });
    if (!e) return false;
    faceTowards(e, { x: door.x + 0.5, y: door.y, z: door.z + 0.5 });
    actionBar(player, tr("hb.kn.door"));
    addFear(player, 15);
    return true;
  }
  return anywhere ? knockMinor(player) : false;
}

// Somewhere in the house: a wall, a door, a window.
export function knockMinor(player) {
  const glass = findBlocks(player, GLASS, 8, 4);
  if (glass.length && Math.random() < 0.5) {
    const g = glass[randInt(0, glass.length - 1)];
    const at = { x: g.x + 0.5, y: g.y + 0.5, z: g.z + 0.5 };
    for (let i = 0; i < 4; i++) later(i * 5, () => soundAt(player, "hb.knock", at, 1.8, 0.5));
    actionBar(player, tr("hb.kn.window"));
  } else {
    const p = player.location;
    const a = rand(0, Math.PI * 2);
    knocks(player, { x: p.x + Math.cos(a) * 5, y: p.y + 1, z: p.z + Math.sin(a) * 5 }, 3, 0.9);
  }
  addFear(player, 8);
  return true;
}

function setKnocking(e, on) {
  tryRun(() => e.setProperty("hb:knock", on));
}

function comeIn(e, s, player) {
  setKnocking(e, false);
  s.how = "hunt";
  s.hunting = true;
  s.clock = 0;
  setMode(s, "hunt");
  soundAt(player, "hb.snarl", e.location, 0.7, 1);
  scare(player, " ", tr("hb.kn.sub"));
  discover("knocker");
  addFear(player, 30);
}

function atDoor(e, s, player) {
  const door = block(player.dimension, s.door.x, s.door.y, s.door.z);
  if (!isDoor(door) || doorOpen(door)) {
    // You opened it. Or it isn't there any more.
    comeIn(e, s, player);
    return;
  }
  faceTowards(e, { x: s.door.x + 0.5, y: s.door.y, z: s.door.z + 0.5 });
  if (s.clock >= s.next) {
    s.next = s.clock + randInt(70, 120);
    setKnocking(e, true);
    knocks(player, { x: s.door.x + 0.5, y: s.door.y + 1, z: s.door.z + 0.5 });
    later(30, () => setKnocking(e, false));
    if (Math.random() < 0.3) later(40, () => soundAt(player, "hb.whisper", e.location, 0.6, 0.6));
  }
  if (s.clock >= KNOCK_FOR) {
    // It stopped. And then, in the silence, a window.
    s.done = true;
    vanish(e, false);
    later(randInt(60, 120), () => knockMinor(player));
  }
}

function tick(e, s, player) {
  if (s.done) return;
  s.clock = (s.clock ?? 0) + 4;
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || d > 64) {
    vanish(e, false);
    return;
  }
  if (s.how === "door") return atDoor(e, s, player);
  if (s.mode === "lurk") {
    // One from a spawn egg, or after the bedside: it watches, and comes.
    faceTowards(e, player.location);
    if (d < 6 || canSee(player, e, 24, 0.9)) comeIn(e, s, player);
    return;
  }
  if (s.clock >= HUNT_FOR && !s.adopted) {
    soundAt(player, "hb.knock", e.location, 0.6, 1);
    vanish(e, true);
  }
}

define({
  kind: "knocker",
  type: "hb:knocker",
  modes: ["lurk", "hunt"],
  stride: { id: "hb.step", every: 1.5, volume: 0.8, pitch: [0.7, 0.8] },
  voice: { id: "hb.breath", every: [6, 12], volume: 0.6, pitch: [0.6, 0.7] },
  weights: (ctx) => (ctx.under ? [0, 0] : ctx.indoors ? [3, ctx.night ? 4 : 1] : [1, 0]),
  minor: (player, ctx) => (ctx?.indoors ? knockMinor(player) : (soundBehind(player, "hb.knock", 12, 0.8, 0.6), true)),
  major: (player) => knock(player),
  sleep: (player) => bedside(player, "knocker", "lurk", {
    sub: tr("hb.kn.bed"),
    after: (e, s) => s && comeIn(e, s, player),
  }),
  tick,
  adopt: () => "lurk",
  onHit: (e, s, player) => s.mode === "lurk" && s.how !== "door" && comeIn(e, s, player),
  onStrike: (e, s, player) => tryRun(() => player.addEffect("weakness", 100, { showParticles: false })),
  test: (player) => knock(player, true),
});
