// Act II: Jeff the Killer. White hoodie, black hair, a chalk-white face that
// never moves: eyes that can't close and a smile cut into the cheeks. He
// comes at night, into houses. The lights go out one by one. Somebody
// whispers, very close: "Go to sleep." And if you do, you wake up and he is
// standing over the bed.
//
//   lurk     in the dark of your house, just out of sight; closer each time
//            you look away. See him and he laughs, and runs at you
//   hunt     through doors, with the knife. He can be killed
//   sleep    over the bed when you wake
//   minor    "Go to sleep." in the chat, laughter in another room

import {
  actionBar, behindSpot, canSee, distance, faceTowards, isIndoors, later, rand, soundAt, soundBehind, tr,
  tryRun, visibleSpot,
} from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";
import { lightsOut } from "../events/lights.js";
import { bedside } from "../events/sleep.js";

const HUNT_FOR = 20 * 30;

function whisper(player) {
  tryRun(() => player.sendMessage({ rawtext: [{ text: "§7§o" }, { translate: "hb.jf.sleep" }] }));
  soundBehind(player, "hb.whisper", 1.2, 0.8, 1);
}

export function visit(player, anywhere = false) {
  if (trackedFor(player, "jeff") || nearWard(player.dimension, player.location)) return false;
  const inside = isIndoors(player);
  if (!inside && !anywhere) return false;
  const at = (inside ? behindSpot(player, 5) : visibleSpot(player, 20, 30, 180, 60)) ?? (anywhere ? behindSpot(player, 6) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  if (inside) lightsOut(player);
  const e = spawn("jeff", at, player, "lurk", { major: true, unseen: 0, clock: 0 });
  if (!e) return false;
  faceTowards(e, player.location);
  later(60, () => whisper(player));
  addFear(player, 15);
  return true;
}

export function jeffMinor(player) {
  if (Math.random() < 0.5) whisper(player);
  else soundBehind(player, "hb.laugh", rand(8, 14), 1.1, 0.5);
  addFear(player, 7);
  return true;
}

function hunt(e, s, player) {
  if (s.mode === "hunt") return;
  s.hunting = true;
  s.clock = 0;
  setMode(s, "hunt");
  soundAt(player, "hb.laugh", e.location, 1, 1);
  scare(player, " ", tr("hb.jf.sub"));
  discover("jeff");
  addFear(player, 30);
}

function tick(e, s, player) {
  s.clock += 4;
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || d > 64) {
    vanish(e, false);
    return;
  }
  if (s.mode === "hunt") {
    if (s.clock >= HUNT_FOR && !s.adopted) {
      soundAt(player, "hb.laugh", e.location, 0.9, 0.6);
      vanish(e, true);
    }
    return;
  }
  faceTowards(e, player.location);
  if (canSee(player, e, 32, 0.88)) return hunt(e, s, player);
  if ((s.unseen += 4) >= 70) {
    s.unseen = 0;
    if (stepCloser(e, player, 0.55, 3)) soundAt(player, "hb.step", e.location, 1.1, 0.6);
  }
  if (d < 2.5) hunt(e, s, player);
  else if (s.clock > 20 * 120 && !s.adopted) vanish(e, false);
}

define({
  kind: "jeff",
  type: "hb:jeff",
  modes: ["lurk", "hunt"],
  stride: { id: "hb.step", every: 1.3, volume: 0.7, pitch: [1.05, 1.15] },
  weights: (ctx) => (ctx.under ? [0, 0] : ctx.indoors ? [3, ctx.night ? 4 : 1] : [1, ctx.night ? 1 : 0]),
  minor: jeffMinor,
  major: (player) => visit(player),
  sleep: (player) => bedside(player, "jeff", "lurk", {
    sub: tr("hb.jf.bed"),
    lines: { rawtext: [{ text: "§7§o" }, { translate: "hb.jf.sleep" }] },
    after: (e, s) => s && later(20, () => hunt(e, s, player)),
  }),
  tick,
  adopt: () => "lurk",
  onHit: (e, s, player) => hunt(e, s, player),
  onStrike: (e, s, player) => actionBar(player, tr("hb.jf.cut")),
  onHurt: (e, s, player) => soundAt(player, "hb.laugh", e.location, 1.2, 0.6),
  test: (player) => visit(player, true) || jeffMinor(player),
});
