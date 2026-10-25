// Act I: the Alternates (The Mandela Catalogue). A neighbour comes walking
// up to you: shirt, jeans, a face you almost know. They talk to you in the
// chat, friendly, a little wrong. "Can I come in?" Don't look at their face
// up close for too long: the eyes are uneven, the pupils are pin points,
// the smile is too wide, and once you have noticed, it stops pretending. The
// head comes up out of the body on a long neck, the jaw drops to its chest,
// the fingers grow long, and it hunts you. Keep your distance and it gives
// up and walks away.

import { actionBar, canSee, distance, faceTowards, later, rand, randInt, soundAt, soundBehind, tr, tryRun, visibleSpot } from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards } from "../core/hunt.js";

const NOTICE = 40; // ticks of looking at its face up close
const CLOSE = 12;
const SAYS = 4; // hh.alt.say.0 .. 3
const AVOIDED = 20 * 20; // keep away this long and it gives up
const HUNT_FOR = 20 * 35;

function say(player, n) {
  tryRun(() => player.sendMessage({ rawtext: [{ translate: "hh.alt.name" }, { text: " " }, { translate: `hh.alt.say.${n}` }] }));
}

export function neighbour(player, anywhere = false) {
  if (trackedFor(player, "alternate") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 16, 26, rand(-60, 60), 20) ?? (anywhere ? visibleSpot(player, 8, 14, 0, 120) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("alternate", at, player, "disguise", { major: true, look: 0, away: 0, said: 0, clock: 0 });
  if (!e) return false;
  tryRun(() => e.setProperty("hh:variant", randInt(0, 2)));
  faceTowards(e, player.location);
  later(20, () => say(player, 0));
  return true;
}

// Someone knocks. "Hello? Are you home? It's me."
export function knockKnock(player) {
  for (let i = 0; i < 3; i++) later(i * 8, () => soundBehind(player, "hh.knock", rand(5, 8), 0.9, 1));
  later(50, () => say(player, 1));
  addFear(player, 8);
  return true;
}

export function reveal(e, s, player) {
  if (s.mode === "hunt") return;
  setMode(s, "hunt");
  s.hunting = true;
  s.clock = 0;
  tryRun(() => e.setProperty("hh:revealed", true));
  soundAt(player, "hh.screech", e.location, 0.9, 1);
  soundAt(player, "hh.static", e.location, 0.6, 1);
  scare(player, " ", tr("hh.alt.sub"));
  actionBar(player, tr("hh.alt.reveal"));
  discover("alternate");
  addFear(player, 35);
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  s.clock += 4;
  if (nearWard(player.dimension, player.location) || d > 90) {
    vanish(e, false);
    return;
  }
  if (s.mode === "hunt") {
    if (s.clock % 100 === 0) soundAt(player, "hh.static", e.location, rand(0.5, 0.7), 0.7);
    if (!s.adopted && s.clock >= HUNT_FOR) {
      actionBar(player, tr("hh.alt.gone"));
      vanish(e);
    }
    return;
  }
  // Pretending. It talks to you as it comes.
  faceTowards(e, player.location);
  if (s.clock % 120 === 0 && s.said < SAYS - 1 && d < 30) say(player, ++s.said);
  // Look at its face up close for too long and you'll see.
  s.look = d <= CLOSE && canSee(player, e, CLOSE, 0.92) ? s.look + 4 : Math.max(0, s.look - 2);
  if (s.look >= NOTICE || d <= 2.5) {
    reveal(e, s, player);
    return;
  }
  // Keep away from it and it gives up.
  s.away = d > 24 ? s.away + 4 : 0;
  if (!s.adopted && (s.away >= AVOIDED || s.clock > 20 * 120)) {
    actionBar(player, tr("hh.alt.left"));
    vanish(e, false);
  }
}

define({
  kind: "alternate",
  type: "hh:alternate",
  modes: ["disguise", "hunt"],
  stride: { id: "hh.step", every: 1.2, volume: 0.6, pitch: [0.95, 1.05] },
  weights: (ctx) => (ctx.under ? [0, 0] : [2, ctx.night ? 3 : 2]),
  minor: knockKnock,
  major: (player) => neighbour(player),
  tick,
  adopt: () => "disguise",
  onHit: (e, s, player) => reveal(e, s, player),
  test: (player) => neighbour(player, true) || knockKnock(player),
});
