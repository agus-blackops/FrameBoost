// Act I: Smile Dog (smile.jpg). A husky sitting in the dark of a room, its
// muzzle pulled back into a wide human smile. Look at it too long and it is
// in your head: the picture, the flash, and the words. SPREAD THE WORD. Then
// it gets up.
//
//   sit      somewhere in the dark ahead of you. Look away and it follows, a
//            little closer each time, still sitting, still smiling
//   hunt     after you have looked at it for long enough: fast, and it can
//            be killed
//   minor    panting in the dark behind you, a whine far off, or the words in
//            the chat under your own name

import { actionBar, canSee, distance, faceTowards, later, rand, sound, soundAt, soundBehind, tr, tryRun, visibleSpot } from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards, stepCloser } from "../core/hunt.js";

const STARE = 30; // a second and a half of looking at it
const HUNT_FOR = 20 * 25;

export function sitting(player, anywhere = false) {
  if (trackedFor(player, "smiledog") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 9, 16, rand(-40, 40), 15) ?? (anywhere ? visibleSpot(player, 5, 10, 0, 120) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("smiledog", at, player, "sit", { major: true, look: 0, unseen: 0, clock: 0 });
  if (!e) return false;
  faceTowards(e, player.location);
  soundAt(player, "mob.wolf.panting", at, 0.6, 0.7);
  addFear(player, 12);
  return true;
}

// The words, under your own name, as if you had said them.
function spread(player) {
  tryRun(() => player.sendMessage({ rawtext: [{ text: `<${player.name}> ` }, { translate: "hb.sd.word" }] }));
}

export function smileMinor(player) {
  const r = Math.random();
  if (r < 0.4) soundBehind(player, "mob.wolf.panting", 3, 0.6, 0.8);
  else if (r < 0.7) soundBehind(player, "mob.wolf.whine", 24, 0.5, 1);
  else spread(player);
  addFear(player, 6);
  return true;
}

// The picture, all at once.
function flash(e, s, player) {
  s.done = true;
  s.flashed = true;
  for (let k = 0; k < 4; k++) later(k * 4, () => blackout(player, 0.08));
  tryRun(() =>
    player.onScreenDisplay.setTitle({ rawtext: [{ text: "§f§l:)" }] }, {
      subtitle: tr("hb.sd.word_big"), fadeInDuration: 0, stayDuration: 30, fadeOutDuration: 6,
    })
  );
  sound(player, "hb.static", 1, 0.8);
  later(8, () => sound(player, "hb.laugh", 0.8, 0.6));
  tryRun(() => player.addEffect("nausea", 160, { showParticles: false }));
  later(30, () => {
    spread(player);
    s.done = false;
    s.hunting = true;
    s.clock = 0;
    setMode(s, "hunt");
    soundAt(player, "hb.snarl", e.location, 1.1, 1);
    scare(player, " ", tr("hb.sd.sub"));
  });
  discover("smiledog");
  addFear(player, 30);
}

function tick(e, s, player) {
  if (s.done) return;
  const d = distance(player.location, e.location);
  s.clock += 4;
  if (nearWard(player.dimension, player.location) || d > 64) {
    vanish(e, false);
    return;
  }
  if (s.mode === "hunt") {
    if (s.clock >= HUNT_FOR && !s.adopted) vanish(e, true);
    return;
  }
  faceTowards(e, player.location);
  if (canSee(player, e, 48, 0.94)) {
    s.unseen = 0;
    if ((s.look += 4) >= STARE) flash(e, s, player);
    else if (s.look === 12) actionBar(player, tr("hb.sd.look"));
    return;
  }
  s.look = Math.max(0, s.look - 2);
  // It follows you, still sitting, a little closer.
  if ((s.unseen += 4) >= 80) {
    s.unseen = 0;
    if (stepCloser(e, player, 0.6, 4)) soundAt(player, "mob.wolf.panting", e.location, 0.6, 0.6);
  }
  if (s.clock > 20 * 120 && !s.adopted) vanish(e, false);
}

define({
  kind: "smiledog",
  type: "hb:smile_dog",
  modes: ["sit", "hunt"],
  stride: { id: "hb.step", every: 1.1, volume: 0.5, pitch: [1.2, 1.4] },
  weights: (ctx) => (ctx.under ? [1, 1] : ctx.indoors ? [2, ctx.night ? 3 : 1] : [2, ctx.night ? 2 : 1]),
  minor: smileMinor,
  major: (player) => sitting(player),
  tick,
  adopt: () => "sit",
  onHit: (e, s, player) => {
    if (s.mode === "sit" && !s.flashed) flash(e, s, player);
  },
  onStrike: (e, s, player) => tryRun(() => player.addEffect("nausea", 100, { showParticles: false })),
  onHurt: (e, s, player) => soundAt(player, "hb.laugh", e.location, 1.3, 0.5),
  test: (player) => sitting(player, true) || smileMinor(player),
});
