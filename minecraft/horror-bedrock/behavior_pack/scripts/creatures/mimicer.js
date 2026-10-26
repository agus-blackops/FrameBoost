// Act II: the Mimicer. Something shaped like Steve, but wrong: too tall, its
// arms and legs too long, the eyes just empty holes. It talks. In the chat,
// under YOUR name, it says things you never said; from the trees, it makes
// the sounds of villagers, of someone calling. Look at it for too long, or
// get too close, and it runs at you on those long legs. It can't climb:
// build up three blocks and it stands at the bottom, looking up, and goes.
//
//   watch    far off, talking, closer every time you look away
//   chase    fast, smashing through what's in the way; can be hurt
//   minor    your own name in the chat saying something you didn't, or a
//            villager's voice where there are no villagers

import {
  actionBar, canSee, distance, faceTowards, later, pick, rand, randInt, soundAt, soundBehind, tr, tryRun,
  visibleSpot,
} from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";

const LINES = 8; // hb.mi.say.1 .. 8
const STARE = 50;
const CHASE_FOR = 20 * 30;
const VOICES = ["mob.villager.idle", "mob.villager.haggle", "mob.villager.no", "mob.villager.yes"];

// Something you never said, under your own name.
function says(player, n = randInt(1, LINES)) {
  tryRun(() => player.sendMessage({ rawtext: [{ text: `<${player.name}> ` }, { translate: `hb.mi.say.${n}` }] }));
}

function talk(e, player) {
  tryRun(() => e.setProperty("hb:talk", true));
  soundAt(player, pick(VOICES.map((v) => [v, 1])), e.location, rand(0.5, 0.65), 1);
  later(randInt(10, 30), () => soundAt(player, pick(VOICES.map((v) => [v, 1])), e.location, rand(0.45, 0.6), 1));
  later(40, () => tryRun(() => e.setProperty("hb:talk", false)));
}

export function mimic(player, anywhere = false) {
  if (trackedFor(player, "mimicer") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 26, 40, rand(-60, 60), 20) ?? (anywhere ? visibleSpot(player, 12, 20, 0, 120) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("mimicer", at, player, "watch", { major: true, look: 0, unseen: 0, clock: 0, talkIn: 40, said: 0 });
  if (!e) return false;
  faceTowards(e, player.location);
  says(player, 1);
  addFear(player, 15);
  return true;
}

export function mimicMinor(player) {
  if (Math.random() < 0.6) says(player);
  else soundBehind(player, pick(VOICES.map((v) => [v, 1])), rand(14, 22), rand(0.5, 0.6), 1);
  addFear(player, 6);
  return true;
}

function chase(e, s, player) {
  if (s.mode === "chase") return;
  s.hunting = true;
  s.clock = 0;
  setMode(s, "chase");
  tryRun(() => e.setProperty("hb:talk", true));
  soundAt(player, "hb.screech", e.location, 0.5, 1);
  later(20, () => tryRun(() => e.setProperty("hb:talk", false)));
  scare(player, " ", tr("hb.mi.sub"));
  discover("mimicer");
  addFear(player, 30);
}

// You built up out of its reach. It stands at the bottom and looks up.
function givesUp(e, s, player) {
  s.done = true;
  setMode(s, "watch");
  faceTowards(e, player.location);
  actionBar(player, tr("hb.mi.up"));
  says(player, LINES);
  later(60, () => vanish(e, true));
}

function tick(e, s, player) {
  if (s.done) return;
  s.clock += 4;
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || d > 96) {
    vanish(e, false);
    return;
  }
  if (s.mode === "chase") {
    const flat = Math.hypot(player.location.x - e.location.x, player.location.z - e.location.z);
    if (player.location.y - e.location.y >= 3 && flat < 4) givesUp(e, s, player);
    else if (s.clock >= CHASE_FOR && !s.adopted) vanish(e, true);
    return;
  }
  faceTowards(e, player.location);
  // It talks.
  if ((s.talkIn -= 4) <= 0) {
    s.talkIn = randInt(80, 160);
    talk(e, player);
    if (s.said++ < 3 && Math.random() < 0.6) later(20, () => says(player));
  }
  if (canSee(player, e, 96, 0.92)) {
    s.unseen = 0;
    s.look += 4;
    if (s.look === 20) actionBar(player, tr("hb.mi.seen"));
    if (s.look >= STARE) chase(e, s, player);
    return;
  }
  if ((s.unseen += 4) >= 60) {
    s.unseen = 0;
    stepCloser(e, player, 0.6, 10);
  }
  if (d < 12) chase(e, s, player);
  if (s.clock > 20 * 150 && !s.adopted) vanish(e, false);
}

define({
  kind: "mimicer",
  type: "hb:mimicer",
  modes: ["watch", "chase"],
  stride: { id: "hb.step", every: 2.2, volume: 1, pitch: [0.55, 0.65] },
  weights: (ctx) => (ctx.under ? [1, 1] : ctx.outside ? [2, ctx.forest || ctx.night ? 4 : 2] : [1, 1]),
  minor: mimicMinor,
  major: (player) => mimic(player),
  tick,
  adopt: () => "watch",
  onHit: (e, s, player) => chase(e, s, player),
  onStrike: (e, s, player) => says(player, randInt(1, LINES)),
  test: (player) => mimic(player, true) || mimicMinor(player),
});
