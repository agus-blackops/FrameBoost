// Act II: the Goatman. At night in the woods: a goat screaming where there
// are no goats, something running on two legs between the trees. He stays
// at the edge of your light. Every time you look away he is closer; when
// you catch him he throws his head back and screams, and then either bolts
// into the trees or comes for you. Light and a beating send him running.

import {
  actionBar, canSee, distance, later, rand, soundAt, soundBehind, tr, tryRun, visibleSpot,
} from "../core/util.js";
import { define, setMode, spawn, stateOf, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";

const SCREAM = "mob.goat.ambient.screamer";

export function goatmanMinor(player) {
  soundBehind(player, SCREAM, rand(18, 30), rand(0.55, 0.7), 1);
  if (Math.random() < 0.5) later(30, () => soundBehind(player, "mob.goat.ambient", rand(10, 16), 0.5, 1));
  else for (let i = 0; i < 5; i++) later(i * 5, () => soundBehind(player, "step.grass", 14 - i * 2, 1.3, 0.8));
  addFear(player, 8);
  return true;
}

export function spawnGoatman(player) {
  if (trackedFor(player, "goatman")) return false;
  const at = visibleSpot(player, 20, 28, rand(-50, 50), 5);
  if (!at || !outsideWards(player.dimension, at)) return false;
  return !!spawn("goatman", at, player, "stalk", { major: true });
}

function scream(e, player) {
  tryRun(() => e.setProperty("hl:scream", true));
  later(40, () => tryRun(() => e.setProperty("hl:scream", false)));
  soundAt(player, SCREAM, e.location, 0.5, 1);
  later(8, () => soundAt(player, SCREAM, e.location, 0.45, 1));
}

export function charge(e, player) {
  const s = stateOf(e);
  if (!s || s.mode === "charge") return;
  setMode(s, "charge");
  s.hunting = true;
  scream(e, player);
  scare(player, " ", "§4§l!");
  actionBar(player, tr("hl.goat.charge"));
  discover("goatman");
  addFear(player, 30);
}

export function bolt(e, player) {
  const s = stateOf(e);
  if (!s || s.mode === "flee") return;
  setMode(s, "flee");
  s.hunting = false;
  soundAt(player, SCREAM, e.location, 0.7, 0.8);
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  if (s.mode === "flee") {
    if ((s.timer += 4) > 120 || d > 48) vanish(e, false);
    return;
  }
  if (s.mode === "charge") {
    s.timer += 4;
    if (s.timer % 60 === 0) soundAt(player, "mob.goat.ambient", e.location, 0.5, 1);
    if (s.timer > 20 * 20 || nearWard(player.dimension, player.location, 4)) bolt(e, player);
    return;
  }
  if (s.adopted) return;
  if (nearWard(e.dimension, e.location) || s.age > 20 * 90) {
    vanish(e);
    return;
  }
  const seen = canSee(player, e, 64, 0.92);
  if (seen) {
    discover("goatman");
    if (++s.seen >= 6) {
      // Caught: the scream, and then he decides.
      scream(e, player);
      addFear(player, 15);
      if (Math.random() < 0.5 || d > 18) later(30, () => bolt(e, player));
      else later(30, () => charge(e, player));
      s.seen = -100; // only once
    }
    return;
  }
  if (++s.unseen >= 25) {
    s.unseen = 0;
    if (d < 11) charge(e, player);
    else if (stepCloser(e, player, 0.65, 9)) soundAt(player, "step.grass", e.location, 1.3, 0.8);
  }
}

define({
  kind: "goatman",
  type: "hl:goatman",
  modes: ["stalk", "charge", "flee"],
  weights: (ctx) => (ctx.outside && ctx.night ? [ctx.forest ? 4 : 1, ctx.forest ? 4 : 1] : [0, 0]),
  minor: goatmanMinor,
  major: spawnGoatman,
  tick,
  adopt: () => "stalk",
  shine: (e, s, player) => bolt(e, player),
  onHit: (e, s, player) => s.mode === "stalk" && charge(e, player),
  onHurt: (e, s, player) => {
    const h = tryRun(() => e.getComponent("minecraft:health"));
    if (h && h.currentValue < h.effectiveMax * 0.4) bolt(e, player);
  },
  test: (player) => spawnGoatman(player) || goatmanMinor(player),
});
