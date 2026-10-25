// Act II: Cartoon Cat (Trevor Henderson). Somewhere in the woods, an old
// cartoon is playing: a slide whistle, a honk, a laugh, hyuk-hyuk. Then,
// between the trees, very tall and very black, a cartoon cat with white
// gloves and a grin of human teeth, bouncing on the spot. It doesn't walk
// towards you. It stretches: every time you look away it is further across
// the gap, its rubber limbs pulling it along. Look at it too long, or let it
// get close, and it runs at you, fast, laughing, through doors and fences.
// A flashlight in its eyes stops it for a moment; nothing else does.

import { actionBar, canSee, distance, later, rand, soundAt, soundBehind, tr, visibleSpot } from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";

const WATCHED = 100; // ticks of being watched before it runs
const RUNS_AT = 12; // blocks
const STRETCH_EVERY = 40; // ticks unseen between stretches
const CHASE_FOR = 20 * 30;

export function cartoonCat(player, anywhere = false) {
  if (trackedFor(player, "cartoon") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 30, 42, rand(-50, 50), 15) ?? (anywhere ? visibleSpot(player, 16, 24, 0, 90) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("cartoon", at, player, "stalk", { major: true, look: 0, unseen: 0, clock: 0 });
  if (!e) return false;
  soundAt(player, "hh.toon", e.location, 1.0, 1);
  later(30, () => actionBar(player, tr("hh.cat.seen")));
  return true;
}

// An old cartoon playing somewhere in the trees.
export function toonMinor(player) {
  soundBehind(player, "hh.toon", rand(24, 40), rand(0.9, 1.1), 1);
  later(50, () => actionBar(player, tr("hh.cat.minor")));
  addFear(player, 8);
  return true;
}

export function run(e, s, player) {
  if (s.mode === "chase") return;
  setMode(s, "chase");
  s.hunting = true;
  s.clock = 0;
  soundAt(player, "hh.toon", e.location, 0.75, 1);
  soundAt(player, "hh.screech", e.location, 1.2, 0.8);
  scare(player, " ", tr("hh.cat.sub"));
  discover("cartoon");
  addFear(player, 35);
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  s.clock += 4;
  if (nearWard(player.dimension, player.location) || d > 100) {
    vanish(e, false);
    return;
  }
  if (s.mode === "chase") {
    if (s.frozen > 0 && (s.frozen -= 4) <= 0) setMode(s, "chase");
    if (s.clock % 80 === 0) soundAt(player, "hh.toon", e.location, rand(0.7, 0.9), 1);
    if (!s.adopted && s.clock >= CHASE_FOR) {
      soundAt(player, "hh.toon", e.location, 1.2, 1);
      actionBar(player, tr("hh.cat.gone"));
      vanish(e);
    }
    return;
  }
  if (s.adopted) return;
  if (canSee(player, e, 96, 0.9)) {
    s.look += 4;
    s.unseen = 0;
    discover("cartoon");
    if (s.look % 80 === 0) soundAt(player, "hh.toon", e.location, rand(1.0, 1.2), 0.7);
  } else if ((s.unseen += 4) >= STRETCH_EVERY) {
    // It stretches across the gap while you aren't looking.
    s.unseen = 0;
    if (stepCloser(e, player, 0.6, 6)) soundAt(player, "hh.warp", e.location, rand(1.4, 1.7), 0.6);
  }
  if (s.look >= WATCHED || d <= RUNS_AT) run(e, s, player);
  else if (s.clock > 20 * 120) vanish(e, false);
}

define({
  kind: "cartoon",
  type: "hh:cartoon_cat",
  modes: ["stalk", "chase"],
  stride: { id: "hh.step", every: 1.4, volume: 0.8, pitch: [1.3, 1.6] },
  weights: (ctx) => (ctx.outside ? [2, ctx.night ? 3 : 1] : [0, 0]),
  minor: toonMinor,
  major: (player) => cartoonCat(player),
  tick,
  adopt: () => "stalk",
  onHit: (e, s, player) => run(e, s, player),
  // A light in its eyes: it stops dead for two seconds, grinning.
  shine: (e, s, player) => {
    if (s.mode !== "chase" || s.frozen > 0) return;
    setMode(s, "stalk"); // stops dead...
    s.mode = "chase"; // ...but it is still the chase
    s.frozen = 40;
    soundAt(player, "hh.toon", e.location, 1.5, 0.6);
  },
  test: (player) => cartoonCat(player, true) || toonMinor(player),
});

