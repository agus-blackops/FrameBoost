// Act I: the Intruder Alert (The Mandela Catalogue). You are at home. The
// emergency alert comes on: there is someone in your house who does not
// live there. Do not look at it. Do not speak to it. Leave the house, now.
//
// It is already inside, behind you, stooped in a corner: too tall, a dark
// suit, a long neck, a face that is all eyes and teeth. Look at it and it
// straightens up and its head tips all the way over; keep looking and it
// comes for you. Don't look, and it creeps closer every time you aren't
// looking. Get out of the house and it lets you go.

import {
  actionBar, canSee, caveSpot, distance, faceTowards, isIndoors, later, rand, soundAt, soundBehind, tr, tryRun,
} from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { broadcast } from "../core/broadcast.js";
import { nearWard } from "../items/ward.js";
import { lightsOut } from "../events/lights.js";

const STARE = 8; // ticks of being seen before it straightens up
const TAKES = 32; // ticks of being seen before it comes for you
const OUTSIDE_TO_ESCAPE = 60;
const LASTS = 20 * 70;
const LINES = ["hh.int.1", "hh.int.2", "hh.int.3", "hh.int.4"];

export function intruderAlert(player, anywhere = false) {
  if (trackedFor(player, "intruder") || nearWard(player.dimension, player.location)) return false;
  if (!anywhere && !isIndoors(player)) return false;
  // Somewhere behind you, inside, in a corner.
  const at = caveSpot(player, 3, 7, 180, 80) ?? (anywhere ? caveSpot(player, 2, 6, 0, 180) : undefined);
  if (!at) return false;
  const e = spawn("intruder", at, player, undefined, { major: true, look: 0, out: 0, unseen: 0, clock: 0 });
  if (!e) return false;
  faceTowards(e, player.location);
  broadcast(player, "hh.int.title", LINES);
  if (Math.random() < 0.5) later(90, () => tryRun(() => lightsOut(player)));
  discover("intruder");
  addFear(player, 25);
  return true;
}

// A floorboard upstairs, a door you didn't open.
export function intruderMinor(player) {
  if (!isIndoors(player)) return false;
  soundBehind(player, "hh.creak", rand(4, 8), rand(0.7, 0.9), 0.9);
  later(40, () => soundBehind(player, "hh.step", rand(3, 6), 0.8, 0.6));
  addFear(player, 8);
  return true;
}

function takes(e, s, player) {
  // It was right there, and you looked.
  s.done = true;
  const v = player.getViewDirection();
  tryRun(() => e.teleport({ x: player.location.x + v.x * 1.2, y: player.location.y, z: player.location.z + v.z * 1.2 }));
  faceTowards(e, player.location);
  soundAt(player, "hh.screech", e.location, 0.8, 1);
  blackout(player, 0.4);
  tryRun(() => player.applyDamage(8));
  tryRun(() => player.addEffect("darkness", 140, { showParticles: false }));
  scare(player, " ", tr("hh.int.sub"));
  addFear(player, 35);
  later(14, () => vanish(e, false));
}

function tick(e, s, player) {
  if (s.done) return;
  s.clock += 4;
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || (!s.adopted && s.clock > LASTS)) {
    vanish(e, false);
    return;
  }
  // Out of the house: it lets you go.
  s.out = !isIndoors(player) || d > 16 ? s.out + 4 : 0;
  if (s.out >= OUTSIDE_TO_ESCAPE && !s.adopted) {
    actionBar(player, tr("hh.int.safe"));
    vanish(e, false);
    return;
  }
  const seen = canSee(player, e, 20, 0.85);
  if (seen) {
    s.look += 4;
    s.unseen = 0;
    if (s.look >= STARE && !s.staring) {
      s.staring = true;
      tryRun(() => e.setProperty("hh:stare", true));
      soundAt(player, "hh.heartbeat", player.location, 1.2, 1);
      actionBar(player, tr("hh.int.seen"));
    }
    if (s.look >= TAKES) takes(e, s, player);
    return;
  }
  if (s.staring) {
    s.staring = false;
    tryRun(() => e.setProperty("hh:stare", false));
  }
  s.look = Math.max(0, s.look - 2);
  faceTowards(e, player.location);
  // Every few seconds you aren't looking, it is closer.
  if ((s.unseen += 4) >= 60 && d > 2.5) {
    s.unseen = 0;
    const k = Math.max(2, d * 0.55) / d;
    const to = { x: player.location.x + (e.location.x - player.location.x) * k, y: e.location.y, z: player.location.z + (e.location.z - player.location.z) * k };
    if (tryRun(() => (e.teleport(to), true))) soundAt(player, "hh.creak", to, rand(0.8, 1.0), 0.7);
  }
  if (d <= 2.5 && s.unseen >= 40) takes(e, s, player);
}

define({
  kind: "intruder",
  type: "hh:intruder",
  voice: { id: "hh.breath", every: [6, 10], volume: 0.7, pitch: [0.6, 0.75] },
  weights: (ctx) => (ctx.indoors ? [2, ctx.night ? 4 : 2] : [0, 0]),
  minor: intruderMinor,
  major: (player) => intruderAlert(player),
  tick,
  adopt: () => undefined,
  onHit: (e, s, player) => !s.done && takes(e, s, player),
  shine: (e, s, player) => {
    // A torch on it: it straightens up, it has been seen.
    s.look = Math.max(s.look, STARE);
  },
  test: (player) => intruderAlert(player, true) || intruderMinor(player),
});

