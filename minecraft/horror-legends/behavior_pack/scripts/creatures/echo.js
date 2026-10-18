// Act I, in the deep: the Echo, the thing that lives in the Ancient City.
// It is blind. It stands among the ruins with its head cocked, listening,
// its heart glowing through its open chest, and it hears everything: your
// footsteps, the blocks you break, the door you open. Make too much noise
// and it comes for you, and it hits harder than anything else down there.
// Crouch, keep still, and it loses you. It copies the sounds it has heard
// (your steps, your door, your own voice when you were hurt) to bring you
// closer. It never leaves the city.

import {
  actionBar, caveSpot, distance, later, pick, rand, soundAt, tr, tryRun,
} from "../core/util.js";
import { define, setMode, spawn, stateOf, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { inAncientCity } from "../core/places.js";
import { nearWard } from "../items/ward.js";

const HEARD = 40; // noise it takes to find you
const QUIET_TO_LOSE = 60; // ticks of silence until it loses you
const HUNT_FOR = 20 * 30;
const MIMIC = [["step.stone", 3], ["random.door_open", 2], ["game.player.hurt", 2], ["random.eat", 1], ["dig.stone", 2]];

const extra = new Map(); // playerId -> noise made by breaking or placing blocks

// How loud you are right now.
export function noise(player) {
  if (tryRun(() => player.isSneaking)) return -6;
  if (tryRun(() => player.isSprinting)) return 10;
  const v = tryRun(() => player.getVelocity()) ?? { x: 0, z: 0 };
  return Math.hypot(v.x, v.z) > 0.08 ? 3 : -3;
}

// Breaking, placing, opening: it hears that too.
export function makeNoise(player, amount) {
  extra.set(player.id, (extra.get(player.id) ?? 0) + amount);
}

export function spawnEcho(player, anywhere = false) {
  if (trackedFor(player, "echo") || (!anywhere && !inAncientCity(player))) return false;
  // Down a street ahead of you; in a tight corner of the city, nearer.
  const at = caveSpot(player, 14, 22, rand(-70, 70), 10) ?? caveSpot(player, 8, 14, rand(-90, 90), 30);
  if (!at) return false;
  const e = spawn("echo", at, player, "listen", { major: true, noise: 0, quiet: 0, away: 0, anywhere });
  if (e) {
    soundAt(player, "hl.heartbeat", e.location, 0.5, 1);
    later(30, () => actionBar(player, tr("hl.echo.near")));
  }
  return !!e;
}

export function huntEcho(e, player) {
  const s = stateOf(e);
  if (!s || s.mode === "hunt") return;
  setMode(s, "hunt");
  s.hunting = true;
  s.timer = 0;
  s.quiet = 0;
  soundAt(player, "hl.shriek", e.location, 0.55, 1);
  scare(player, " ", "§3§l)))  (((");
  actionBar(player, tr("hl.echo.heard"));
  discover("echo");
  addFear(player, 35);
}

function lose(e, s, player) {
  setMode(s, "listen");
  s.hunting = false;
  s.noise = 0;
  s.timer = 0;
  soundAt(player, "hl.heartbeat", e.location, 0.4, 0.6);
  actionBar(player, tr("hl.echo.lost"));
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  s.timer += 4;
  // It never leaves the city: if you do, it lets you go.
  s.away = s.anywhere || inAncientCity(player) ? 0 : (s.away ?? 0) + 4;
  if (s.away > 20 * 20 || d > 64 || (!s.adopted && s.age > 20 * 300 && s.mode !== "hunt")) {
    vanish(e, false);
    return;
  }
  const loud = noise(player) + (extra.get(player.id) ?? 0);
  extra.delete(player.id);
  if (s.mode === "hunt") {
    s.quiet = loud <= 0 ? s.quiet + 4 : 0;
    if (s.quiet >= QUIET_TO_LOSE || s.timer > HUNT_FOR || d > 40 || nearWard(player.dimension, player.location, 4)) {
      lose(e, s, player);
    }
    return;
  }
  // Listening, or wandering the streets.
  s.noise = Math.max(0, s.noise + loud * (d < 12 ? 1.5 : d < 24 ? 1 : 0.4));
  if (s.noise >= HEARD && !nearWard(player.dimension, player.location)) {
    huntEcho(e, player);
    return;
  }
  if (s.timer >= 20 * rand(6, 10)) {
    s.timer = 0;
    setMode(s, s.mode === "listen" ? "wander" : "listen");
    // It plays back something it heard.
    if (Math.random() < 0.6) soundAt(player, pick(MIMIC), e.location, rand(0.8, 1.0), 0.9);
    else soundAt(player, "hl.whisper", e.location, 0.5, 0.8);
  }
}

define({
  kind: "echo",
  type: "hl:echo",
  modes: ["listen", "wander", "hunt"],
  weights: (ctx) => (ctx.ancient ? [0, 6] : [0, 0]),
  major: (player) => spawnEcho(player),
  tick,
  adopt: () => "listen",
  onHit: (e, s, player) => huntEcho(e, player),
  onStrike: (e, s, player) => tryRun(() => player.addEffect("darkness", 120, { showParticles: false })),
  test: (player) => spawnEcho(player, true),
});
