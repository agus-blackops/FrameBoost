// Act II: the Evil Hunter. Something answered a ritual nobody remembers
// doing. First the signs: wood burning where there is no fire, a laugh far
// off, the smell of smoke in the dark. Then the Hunt: a corrupted man,
// burned from the chest up and shadow from the waist down, red eyes, stands
// at the edge of the night and looks at you. Then it runs. It smashes
// through doors and windows, it can't be hurt, and its claws set you
// alight. Survive until it burns out, or hold up a Silver Cross when it is
// close: the Hunt ends at once.

import {
  actionBar, distance, faceTowards, groundSpot, later, onCooldown, rand, soundAt, soundBehind, title, tr, tryRun, valid,
} from "../core/util.js";
import { allFor, define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards } from "../core/hunt.js";

export const CROSS = "hl:silver_cross";
const STARE_FOR = 20 * 3; // it looks at you before it runs
const HUNT_FOR = 20 * 45;
const CROSS_RANGE = 10;
const CROSS_REST = 20 * 30;

export function startHunt(player, anywhere = false) {
  if (trackedFor(player, "hunter") || nearWard(player.dimension, player.location)) return false;
  const at = groundSpot(player, 22, 30, 180, 70) ?? (anywhere ? groundSpot(player, 10, 16, 0, 180) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("hunter", at, player, "stalk", { major: true, hunting: true, phase: "stare" });
  if (!e) return false;
  title(player, tr("hl.hunter.title"), tr("hl.hunter.sub"), 50);
  soundAt(player, "hl.burn", e.location, 0.8, 1);
  later(10, () => soundAt(player, "hl.laugh", at, 0.6, 1));
  discover("hunter");
  addFear(player, 25);
  return true;
}

// The signs: something burning that isn't there, a laugh, far off.
export function hunterSigns(player) {
  soundBehind(player, "hl.burn", rand(10, 18), rand(0.7, 0.9), 0.9);
  later(50, () => soundBehind(player, "hl.laugh", rand(30, 45), rand(0.5, 0.65), 1));
  tryRun(() => {
    const v = player.getViewDirection();
    for (let i = 0; i < 6; i++) {
      player.dimension.spawnParticle("minecraft:basic_flame_particle", {
        x: player.location.x - v.x * 3 + rand(-1, 1), y: player.location.y + rand(0.2, 1.6), z: player.location.z - v.z * 3 + rand(-1, 1),
      });
    }
  });
  actionBar(player, tr("hl.hunter.signs"));
  addFear(player, 8);
  return true;
}

function run(e, s, player) {
  s.phase = "hunt";
  s.clock = 0;
  setMode(s, "hunt");
  soundAt(player, "hl.roar", e.location, 0.8, 1);
  soundAt(player, "hl.burn", e.location, 1.0, 1);
  tryRun(() => player.runCommand("camerashake add @s 0.5 0.6 rotational"));
  scare(player, " ", tr("hl.hunter.run"));
  addFear(player, 25);
}

function burnOut(e, s, player) {
  // You lived through the Hunt. It burns down to nothing.
  soundAt(player, "hl.burn", e.location, 0.6, 1);
  actionBar(player, tr("hl.hunter.over"));
  vanish(e);
}

// The Silver Cross: hold it up while it is close and it is gone.
export function raiseCross(player) {
  const s = allFor(player).find((x) => x.kind === "hunter" && valid(x.entity));
  if (!s || distance(player.location, s.entity.location) > CROSS_RANGE) {
    actionBar(player, tr("hl.cross.nothing"));
    return false;
  }
  if (onCooldown(`cross:${player.id}`, CROSS_REST)) {
    actionBar(player, tr("hl.cross.rest"));
    return false;
  }
  blackout(player, 0.3);
  soundAt(player, "hl.shriek", s.entity.location, 0.7, 1);
  soundAt(player, "hl.burn", s.entity.location, 1.4, 1);
  tryRun(() => player.dimension.spawnParticle("minecraft:totem_particle", s.entity.location));
  title(player, " ", tr("hl.cross.banished"), 40);
  addFear(player, -20);
  vanish(s.entity);
  return true;
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  if (d > 96 || (!s.adopted && s.age > HUNT_FOR + STARE_FOR + 200)) {
    vanish(e, false);
    return;
  }
  s.clock = (s.clock ?? 0) + 4;
  if (nearWard(player.dimension, player.location, 6)) {
    // It won't come into the light of a Ward Lantern: it burns out at the edge.
    if (s.phase === "hunt") burnOut(e, s, player);
    return;
  }
  if (s.phase === "stare" || !s.phase) {
    faceTowards(e, player.location);
    if (s.clock >= STARE_FOR || d < 8) run(e, s, player);
    return;
  }
  if (s.clock % 100 === 0) soundAt(player, Math.random() < 0.6 ? "hl.burn" : "hl.laugh", e.location, rand(0.7, 0.9), 1);
  if (!s.adopted && s.clock >= HUNT_FOR) burnOut(e, s, player);
}

define({
  kind: "hunter",
  type: "hl:evil_hunter",
  modes: ["stalk", "hunt"],
  voice: { id: "hl.burn", every: [6, 10], volume: 0.8, pitch: [0.8, 1.0] },
  stride: { id: "hl.step", every: 1.4, volume: 1, pitch: [0.6, 0.75] },
  weights: (ctx) => (ctx.night && !ctx.under ? [2, 2] : [0, 0]),
  minor: hunterSigns,
  major: (player) => startHunt(player),
  tick,
  adopt: () => "stalk",
  // A flashlight in its eyes: it staggers, for a moment.
  shine: (e, s, player) => {
    if (s.phase !== "hunt" || (s.dazed ?? 0) > s.age) return;
    s.dazed = s.age + 100;
    tryRun(() => e.addEffect("slowness", 40, { amplifier: 3, showParticles: false }));
    soundAt(player, "hl.burn", e.location, 1.3, 0.8);
  },
  onHit: (e, s, player) => s.phase === "stare" && run(e, s, player),
  // Its claws set you on fire.
  onStrike: (e, s, player) => {
    tryRun(() => player.setOnFire(3, true));
    soundAt(player, "hl.burn", player.location, 1.2, 0.8);
  },
  test: (player) => startHunt(player, true) || hunterSigns(player),
});
