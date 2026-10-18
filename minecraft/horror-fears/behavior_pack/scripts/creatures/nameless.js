// Act I: the Nameless Villager. A villager with no job and no name, standing
// in the fields at the edge of what you can see, arms hanging to its knees,
// head tipped to one side, two blank white holes for eyes. It hums, low and
// slow, from where it stands (you can tell which way to look by the sound).
// While you aren't looking, it comes closer. When you look, it stands
// still, and after a moment it lifts one arm and points at you. Walk up to
// it and it is gone. Let it get close without looking and it puts a hand on
// your shoulder.

import {
  actionBar, canSee, distance, faceTowards, later, rand, soundAt, soundBehind, tr, tryRun, visibleSpot,
} from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";

const POINT_AFTER = 24; // ticks of being looked at before it points
const GONE_AFTER = 100; // ticks of being looked at before it leaves

export function nameless(player) {
  if (trackedFor(player, "nameless") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 22, 32, rand(-60, 60), 6);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("nameless", at, player, undefined, { major: true, look: 0, close: 0 });
  if (e) soundAt(player, "hl.moan", e.location, 0.9, 1);
  return !!e;
}

// Far off, something humming that isn't a villager.
export function villagerMinor(player) {
  soundBehind(player, "hl.moan", rand(24, 40), rand(0.8, 1.0), 1);
  addFear(player, 8);
  return true;
}

function point(e, s, player) {
  tryRun(() => e.setProperty("hl:point", true));
  s.pointing = true;
  soundAt(player, "hl.moan", e.location, 0.6, 1);
  actionBar(player, tr("hl.nameless.point"));
  addFear(player, 12);
  discover("nameless");
}

function touch(e, player) {
  // It was right behind you the whole time.
  scare(player, " ", tr("hl.nameless.touch"));
  tryRun(() => player.addEffect("blindness", 50, { showParticles: false }));
  soundAt(player, "hl.moan", e.location, 0.5, 1);
  addFear(player, 25);
  discover("nameless");
  vanish(e, false);
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || s.age > 20 * 100 || d > 70) {
    vanish(e, false);
    return;
  }
  const seen = canSee(player, e, 64, 0.9);
  if (seen) {
    s.look += 4;
    s.close = 0;
    if (!s.pointing && s.look >= POINT_AFTER) point(e, s, player);
    // Walk up to it and it is gone; stare long enough and it is gone too.
    if (d < 9 || s.look >= GONE_AFTER) {
      soundAt(player, "hl.moan", e.location, 0.5, 0.8);
      vanish(e);
    }
    return;
  }
  // Unseen: no longer pointing, and one step closer, now and then.
  if (s.pointing) {
    s.pointing = false;
    tryRun(() => e.setProperty("hl:point", false));
  }
  s.look = Math.max(0, s.look - 4);
  faceTowards(e, player.location);
  if (d < 3) {
    touch(e, player);
    return;
  }
  if (++s.close >= 12) {
    s.close = 0;
    if (stepCloser(e, player, 0.7, 2.5)) soundAt(player, "hl.step", e.location, rand(0.8, 0.9), 0.8);
  }
}

define({
  kind: "nameless",
  type: "hl:nameless_villager",
  voice: { id: "hl.moan", every: [8, 14], volume: 0.9, pitch: [0.8, 1.0] },
  weights: (ctx) => (ctx.outside ? [2, 3] : [0, 0]),
  minor: villagerMinor,
  major: nameless,
  tick,
  adopt: () => undefined,
  onHit: (e) => vanish(e),
  shine: (e, s, player) => s.look >= POINT_AFTER && vanish(e),
  test: (player) => nameless(player) || villagerMinor(player),
});
