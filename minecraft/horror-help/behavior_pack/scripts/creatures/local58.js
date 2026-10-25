// Act I: Local 58, "Weather Service". Late at night the emergency alert
// comes on: a meteorological event is in progress, it will last until
// sunrise, stay inside, do not look outside. Then the message is taken
// over by someone else: DO NOT LOOK AT THE MOON. Face away from windows.
// Avoid mirrors. Do not look up.
//
// Up in the sky there is a moon. It isn't the moon. Look at it and the face
// comes up through the craters; keep looking and it has seen you.

import { actionBar, distance, faceTowards, isNight, later, looksAt, rand, sound, soundAt, tr, tryRun } from "../core/util.js";
import { define, spawn, stateOf, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { broadcast } from "../core/broadcast.js";
import { nearWard } from "../items/ward.js";

const NOTICE = 20; // ticks of looking before the face comes
const SEEN = 60; // ticks of looking before it has seen you
const LINES = ["hh.l58.1", "hh.l58.2", "hh.l58.3", "hh.l58.4", "hh.l58.5", "hh.l58.6"];

function skyPoint(player) {
  // Low over the horizon, far away, the way the moon hangs.
  const a = rand(0, Math.PI * 2);
  return { x: player.location.x + Math.cos(a) * 48, y: player.location.y + 34, z: player.location.z + Math.sin(a) * 48 };
}

export function weatherService(player, anywhere = false) {
  if (trackedFor(player, "local58") || nearWard(player.dimension, player.location)) return false;
  if (!anywhere && !isNight()) return false;
  const e = spawn("local58", skyPoint(player), player, undefined, { major: true, look: 0, clock: 0 });
  if (!e) return false;
  faceTowards(e, player.location);
  const s = stateOf(e);
  if (s) s.length = broadcast(player, "hh.l58.title", LINES, { hijack: 3 });
  discover("local58");
  addFear(player, 20);
  return true;
}

// A television left on somewhere: the station ID, and the tone.
export function stationId(player) {
  soundAt(player, "hh.static", { x: player.location.x + rand(-6, 6), y: player.location.y + 1, z: player.location.z + rand(-6, 6) }, 0.8, 0.8);
  later(30, () => actionBar(player, tr("hh.l58.minor")));
  addFear(player, 6);
  return true;
}

function seenYou(e, s, player) {
  s.done = true;
  soundAt(player, "hh.wail", e.location, 0.6, 1);
  blackout(player, 0.8);
  tryRun(() => player.addEffect("darkness", 200, { showParticles: false }));
  tryRun(() => player.addEffect("nausea", 200, { showParticles: false }));
  tryRun(() => player.applyDamage(4));
  scare(player, " ", tr("hh.l58.sub"));
  addFear(player, 35);
  later(40, () => vanish(e, false));
}

function tick(e, s, player) {
  if (s.done) return;
  s.clock += 4;
  faceTowards(e, player.location);
  if (nearWard(player.dimension, player.location) || distance(player.location, e.location) > 160) {
    vanish(e, false);
    return;
  }
  // Looking at it counts through a window too.
  const looking = looksAt(player, e, 200, 0.97);
  s.look = looking ? s.look + 4 : Math.max(0, s.look - 1);
  if (s.look >= NOTICE && !s.face) {
    s.face = true;
    tryRun(() => e.setProperty("hh:face", true));
    sound(player, "hh.stinger", 0.8, 0.8);
    actionBar(player, tr("hh.l58.face"));
  }
  if (s.look >= SEEN) {
    seenYou(e, s, player);
    return;
  }
  // You didn't look. The broadcast ends, and so does the night's event.
  if (s.clock >= (s.length ?? 400) + 20 * 40) {
    actionBar(player, tr("hh.l58.safe"));
    vanish(e, false);
  }
}

define({
  kind: "local58",
  type: "hh:false_moon",
  weights: (ctx) => (ctx.night && !ctx.under ? [2, 3] : [1, 0]),
  minor: stationId,
  major: (player) => weatherService(player),
  tick,
  adopt: () => undefined,
  test: (player) => weatherService(player, true),
});
