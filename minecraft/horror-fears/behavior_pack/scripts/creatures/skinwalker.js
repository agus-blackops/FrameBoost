// Act II: the Skinwalker. A cow in the field that is always facing you.
// Its eyes are a person's. It moos too low. You hear someone calling your
// name from the grass, a player getting hurt where nobody is. Get close, or
// hit it, or stare at it too long, and it stands up: a starved thing with
// the cow's skull over its face and the hide on its back, and it runs at
// you on all fours. Hurt it enough and it flees.

import {
  actionBar, canSee, distance, groundSpot, later, rand, soundAt, soundBehind, tr, tryRun, visibleSpot,
} from "../core/util.js";
import { define, setMode, spawn, stateOf, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards } from "../core/hunt.js";

export function skinwalkerMinor(player) {
  const event = Math.random();
  if (event < 0.35) soundBehind(player, "mob.cow.say", rand(10, 18), 0.45, 1);
  else if (event < 0.7) {
    // Your own name, in a voice that is almost right.
    soundBehind(player, "hl.whisper", rand(8, 14), 1.1, 0.9);
    player.sendMessage(tr("hl.sw.voice", player.name));
  } else soundBehind(player, "game.player.hurt", rand(10, 16), 0.9, 1);
  addFear(player, 8);
  return true;
}

export function spawnSkinwalker(player) {
  if (trackedFor(player, "skinwalker")) return false;
  const at = visibleSpot(player, 12, 20, rand(-50, 50), 5) ?? groundSpot(player, 12, 20, 0, 90);
  if (!at || !outsideWards(player.dimension, at)) return false;
  return !!spawn("skinwalker", at, player, "cow");
}

export function reveal(e, player) {
  const s = stateOf(e);
  if (!s || s.mode !== "cow") return;
  tryRun(() => e.setProperty("hl:revealed", true));
  setMode(s, "hunt");
  s.hunting = true;
  s.major = true;
  soundAt(player, "mob.cow.hurt", e.location, 0.3, 1);
  later(10, () => soundAt(player, "hl.scream", e.location, 0.7, 1));
  scare(player, " ", "§4§l. . .");
  actionBar(player, tr("hl.sw.reveal"));
  discover("skinwalker");
  addFear(player, 35);
}

function flee(e, player) {
  const s = stateOf(e);
  if (!s || s.mode === "flee") return;
  setMode(s, "flee");
  s.hunting = false;
  soundAt(player, "hl.scream", e.location, 1.2, 0.7);
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  if (s.mode === "flee") {
    if ((s.timer += 4) > 120 || d > 48) vanish(e, false);
    return;
  }
  if (s.mode === "hunt") {
    s.timer += 4;
    if (s.timer % 80 === 0) soundAt(player, Math.random() < 0.5 ? "mob.cow.say" : "hl.whisper", e.location, 0.4, 1);
    if (s.timer > 20 * 25 || nearWard(player.dimension, player.location, 4)) flee(e, player);
    return;
  }
  if (s.adopted) return;
  // The cow. It watches you; it comes closer while you aren't looking.
  const seen = canSee(player, e, 48, 0.9);
  if (seen) {
    s.seen++;
    if (s.seen % 25 === 0) soundAt(player, "mob.cow.say", e.location, 0.45, 1);
  } else s.unseen++;
  if (d < 5 || s.seen > 75) {
    reveal(e, player);
    return;
  }
  if (s.age > 20 * 120 || d > 64 || nearWard(e.dimension, e.location)) vanish(e, false);
}

define({
  kind: "skinwalker",
  type: "hl:skinwalker",
  modes: ["cow", "hunt", "flee"],
  weights: (ctx) => (ctx.outside && !ctx.forest ? [2, ctx.night ? 3 : 2] : [0, 0]),
  minor: skinwalkerMinor,
  major: spawnSkinwalker,
  tick,
  adopt: () => "cow",
  onHit: (e, s, player) => s.mode === "cow" && reveal(e, player),
  onHurt: (e, s, player) => {
    const h = tryRun(() => e.getComponent("minecraft:health"));
    if (h && h.currentValue < h.effectiveMax * 0.35) flee(e, player);
  },
  shine: (e, s) => s.mode === "hunt" && tryRun(() => e.addEffect("slowness", 40, { amplifier: 1, showParticles: false })),
  test: (player) => spawnSkinwalker(player) || skinwalkerMinor(player),
});
