// Act I: Green Steve. "Green Steve joined the game." Nobody else is on
// this world. Then, in the chat: "I am coming." He looks exactly like
// Steve, except that he is green all over and his eyes are blank. He stands
// in your doorway, behind you, or far off at the edge of the trees, and
// every time you look away he is closer. When he reaches you, he swings
// once, and the hissing starts all round you. "Hello, brother."

import { actionBar, behindSpot, canSee, distance, faceTowards, isIndoors, later, rand, sound, soundAt, soundBehind, tr, tryRun, visibleSpot } from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";

const SEEN_FOR = 40; // ticks of looking at him before he comes
const HUNT_FOR = 20 * 25;

function chat(player, key) {
  tryRun(() => player.sendMessage({ rawtext: [{ text: "§a<Green Steve>§r " }, { translate: key }] }));
}

function system(player, key) {
  tryRun(() => player.sendMessage({ rawtext: [{ text: "§e" }, { translate: key }] }));
}

export function greenSteve(player, anywhere = false) {
  if (trackedFor(player, "greensteve") || nearWard(player.dimension, player.location)) return false;
  // At home he is in the doorway behind you; outside, at the edge of sight.
  const at = isIndoors(player) ? behindSpot(player, 4) : visibleSpot(player, 22, 32, rand(-50, 50), 15);
  const spot = at ?? (anywhere ? visibleSpot(player, 8, 14, 0, 120) : undefined);
  if (!spot || !outsideWards(player.dimension, spot)) return false;
  const e = spawn("greensteve", spot, player, "watch", { major: true, look: 0, unseen: 0, clock: 0 });
  if (!e) return false;
  tryRun(() => (e.nameTag = "Green Steve"));
  faceTowards(e, player.location);
  system(player, "hb.gs.joined");
  later(60, () => chat(player, "hb.gs.coming"));
  addFear(player, 15);
  return true;
}

// Somebody joins and leaves your world. There is nobody else here.
export function joinLeave(player) {
  system(player, "hb.gs.joined");
  later(rand(60, 140), () => system(player, "hb.gs.left"));
  if (Math.random() < 0.5) later(30, () => soundBehind(player, "random.fuse", rand(6, 10), 1, 0.6));
  addFear(player, 6);
  return true;
}

function comes(e, s, player) {
  if (s.mode === "hunt") return;
  setMode(s, "hunt");
  s.hunting = true;
  s.clock = 0;
  chat(player, "hb.gs.brother");
  sound(player, "hb.stinger", 1, 0.9);
  discover("greensteve");
  addFear(player, 25);
}

// He reached you. The hissing, all round, and then...
function hello(e, s, player) {
  s.done = true;
  for (let i = 0; i < 4; i++) {
    const a = (i / 4) * Math.PI * 2;
    later(i * 3, () => soundAt(player, "random.fuse", { x: player.location.x + Math.cos(a) * 2, y: player.location.y, z: player.location.z + Math.sin(a) * 2 }, 1, 1));
  }
  later(30, () => {
    // An explosion that hurts but leaves the world as it was.
    tryRun(() => player.dimension.createExplosion(player.location, 2.5, { breaksBlocks: false, causesFire: false }));
    blackout(player, 0.6);
    scare(player, " ", tr("hb.gs.sub"));
    addFear(player, 35);
    vanish(e, false);
  });
}

function tick(e, s, player) {
  if (s.done) return;
  const d = distance(player.location, e.location);
  s.clock += 4;
  if (nearWard(player.dimension, player.location) || d > 80) {
    vanish(e, false);
    return;
  }
  if (s.mode === "hunt") {
    if (s.clock >= HUNT_FOR && !s.adopted) {
      system(player, "hb.gs.left");
      vanish(e, false);
    }
    return;
  }
  faceTowards(e, player.location);
  if (canSee(player, e, 64, 0.9)) {
    s.unseen = 0;
    if ((s.look += 4) >= SEEN_FOR) comes(e, s, player);
    return;
  }
  // Every time you look away, he is closer.
  if ((s.unseen += 4) >= 60) {
    s.unseen = 0;
    if (stepCloser(e, player, 0.55, 3)) soundAt(player, "hb.step", e.location, 1, 0.7);
  }
  if (d <= 3.5) comes(e, s, player);
}

define({
  kind: "greensteve",
  type: "hb:green_steve",
  modes: ["watch", "hunt"],
  stride: { id: "hb.step", every: 1.3, volume: 0.7, pitch: [0.95, 1.05] },
  weights: (ctx) => (ctx.under ? [1, 1] : [2, ctx.indoors ? 3 : 2]),
  minor: joinLeave,
  major: (player) => greenSteve(player),
  tick,
  adopt: () => "watch",
  onHit: (e, s, player) => comes(e, s, player),
  onStrike: (e, s, player) => !s.done && hello(e, s, player),
  onHurt: (e, s, player) => actionBar(player, tr("hb.gs.hurt")),
  test: (player) => greenSteve(player, true) || joinLeave(player),
});
