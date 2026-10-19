// Act II: Soup.AVI. A tape nobody should have. In your house, or out in the
// night, there is a man sitting on a white stool eating soup: his face is a
// block of censored squares, his shoulders shake, he is crying. Behind him
// stands someone in a huge foam mascot head, rubbing his back, round and
// round. The tape plays (you can see the counter). Watch for too long, or
// walk up to them, and they stop, and both heads turn to look at you. Then
// the lights go, and there is another one. Behind you. Turn round and look at
// it. If you don't, it starts rubbing your back.

import {
  actionBar, canSee, distance, faceTowards, later, looksAt, rand, soundAt, soundBehind, title, tr, tryRun, valid,
  visibleSpot,
} from "../core/util.js";
import { define, spawn, stateOf, tracked, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards } from "../core/hunt.js";

const WATCH_TO_TURN = 120; // 6 s of watching the tape
const CLOSE_TO_TURN = 4.5;
const TURNED_FOR = 40; // the heads hold you for 2 s before the lights go
const BEHIND_FOR = 100; // 5 s to turn round
const LASTS = 20 * 75;

export function playSoup(player, anywhere = false) {
  if (trackedFor(player, "soup") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 7, 12, rand(-30, 30), 12) ?? (anywhere ? visibleSpot(player, 4, 8, 0, 60) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("soup", at, player, undefined, { major: true, look: 0, phase: "tape", clock: 0, slurpIn: 30, sobIn: 70 });
  if (!e) return false;
  // The one with the big head, just behind him, rubbing his back.
  const dx = at.x - player.location.x;
  const dz = at.z - player.location.z;
  const len = Math.hypot(dx, dz) || 1;
  const back = { x: at.x + (dx / len) * 1.1, y: at.y, z: at.z + (dz / len) * 1.1 };
  const g = spawn("soupGuest", back, player, undefined, { major: true, owner: e.id });
  if (!g) {
    vanish(e, false);
    return false;
  }
  stateOf(e).guest = g;
  faceTowards(g, player.location);
  title(player, "§7SOUP.AVI", "§8▶ PLAY  00:00:00", 30);
  soundAt(player, "hl.static", e.location, 0.8, 0.6);
  later(20, () => valid(e) && soundAt(player, "hl.sob", e.location, rand(0.9, 1.0), 1));
  discover("soup");
  addFear(player, 15);
  return true;
}

// Somewhere in the house, someone is eating soup and crying.
export function soupMinor(player) {
  soundBehind(player, "hl.slurp", rand(6, 10), rand(0.9, 1.05), 1);
  later(40, () => soundBehind(player, "hl.sob", rand(7, 11), rand(0.85, 1.0), 0.9));
  actionBar(player, tr("hl.soup.minor"));
  addFear(player, 6);
  return true;
}

function stamp(seconds) {
  const s = Math.floor(seconds);
  return `00:${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}

// The tape is over: everyone goes.
function end(s, withSmoke = true) {
  for (const x of [s.behind, s.guest, s.entity]) if (x && valid(x)) vanish(x, withSmoke);
}

function setStare(x, on) {
  if (x && valid(x)) tryRun(() => x.setProperty("hl:stare", on));
}

function turn(e, s, player) {
  // They stop. Both heads come round to you.
  s.phase = "turned";
  s.clock = 0;
  setStare(e, true);
  setStare(s.guest, true);
  soundAt(player, "hl.static", e.location, 0.6, 1);
  actionBar(player, tr("hl.soup.seen"));
  addFear(player, 15);
}

function another(e, s, player) {
  // The lights go, and there is another one. Behind you.
  s.phase = "behind";
  s.clock = 0;
  blackout(player, 0.6);
  const v = player.getViewDirection();
  const at = { x: player.location.x - v.x * 1.4, y: player.location.y, z: player.location.z - v.z * 1.4 };
  const b = spawn("soupGuest", at, player, undefined, { major: true, owner: e.id });
  if (!b) {
    end(s);
    return;
  }
  s.behind = b;
  faceTowards(b, player.location);
  soundAt(player, "hl.breath", at, 0.7, 1);
  later(4, () => scare(player, " ", tr("hl.soup.another")));
  addFear(player, 15);
}

function looked(e, s, player) {
  // You turned round, and it was right there.
  const b = s.behind;
  setStare(b, true);
  soundAt(player, "hl.screech", b.location, 0.85, 1);
  soundAt(player, "hl.boom", b.location, 0.8, 1);
  tryRun(() => player.runCommand("camerashake add @s 0.9 0.6 rotational"));
  tryRun(() => player.addEffect("darkness", 80, { showParticles: false }));
  scare(player, " ", tr("hl.soup.sub"));
  addFear(player, 35);
  s.phase = "over";
  later(10, () => end(s, false));
}

function rubbed(e, s, player) {
  // You didn't turn round. It is rubbing your back. There, there.
  soundBehind(player, "hl.sob", 1.2, 0.8, 1);
  tryRun(() => player.addEffect("nausea", 160, { showParticles: false }));
  tryRun(() => player.addEffect("slowness", 100, { amplifier: 1, showParticles: false }));
  actionBar(player, tr("hl.soup.rub"));
  addFear(player, 25);
  s.phase = "over";
  later(60, () => end(s));
}

function tick(e, s, player) {
  if (s.phase === "over") return;
  if (s.adopted && !s.phase) Object.assign(s, { phase: "tape", look: 0, clock: 0, slurpIn: 30, sobIn: 70 });
  const d = distance(player.location, e.location);
  if (nearWard(player.dimension, player.location) || (!s.adopted && (s.age > LASTS || d > 48 || !valid(s.guest)))) {
    end(s);
    return;
  }
  s.clock += 4;
  if (s.phase === "tape") {
    // The counter runs, he eats, he cries.
    if (s.clock % 20 === 0) actionBar(player, `§8▶ PLAY  ${stamp(s.age / 20)}`);
    if ((s.slurpIn -= 4) <= 0) {
      s.slurpIn = rand(50, 70);
      soundAt(player, "hl.slurp", e.location, rand(0.9, 1.05), 1);
    }
    if ((s.sobIn -= 4) <= 0) {
      s.sobIn = rand(90, 130);
      soundAt(player, "hl.sob", e.location, rand(0.85, 1.0), 1);
    }
    s.look = canSee(player, e, 32, 0.9) ? s.look + 4 : s.look;
    if (s.look >= WATCH_TO_TURN || d <= CLOSE_TO_TURN) turn(e, s, player);
    return;
  }
  if (s.phase === "turned") {
    faceTowards(e, player.location);
    if (valid(s.guest)) faceTowards(s.guest, player.location);
    if (s.clock >= TURNED_FOR) another(e, s, player);
    return;
  }
  // Behind you: it stays behind you, wherever you go, until you look.
  const b = s.behind;
  if (!valid(b)) {
    end(s);
    return;
  }
  if (canSee(player, b, 6, 0.6) || looksAt(player, b, 4, 0.7)) {
    looked(e, s, player);
    return;
  }
  const v = player.getViewDirection();
  tryRun(() => b.teleport({ x: player.location.x - v.x * 1.4, y: player.location.y, z: player.location.z - v.z * 1.4 }));
  faceTowards(b, player.location);
  if (s.clock >= BEHIND_FOR) rubbed(e, s, player);
}

// The ones with the big heads belong to the man with the soup: without him,
// they go. (One from a spawn egg belongs to nobody, and stays.)
function owner(s) {
  const o = tracked.get(s.owner);
  return o && valid(o.entity) ? o : undefined;
}

function guestTick(g, s) {
  if (!s.adopted && !owner(s)) vanish(g, false);
}

define({
  kind: "soup",
  type: "hl:soup_man",
  weights: (ctx) => (ctx.indoors && !ctx.under ? [2, 3] : ctx.night && ctx.outside ? [1, 1] : [0, 0]),
  minor: soupMinor,
  major: (player) => playSoup(player),
  tick,
  adopt: () => undefined,
  onHit: (e, s, player) => {
    soundAt(player, "hl.glitch", e.location, 0.8, 1);
    end(s);
  },
  // Light on the tape: it stops.
  shine: (e, s, player) => {
    if (s.phase !== "tape") return;
    soundAt(player, "hl.static", e.location, 1.2, 0.6);
    end(s);
  },
  test: (player) => playSoup(player, true) || soupMinor(player),
});

define({
  kind: "soupGuest",
  type: "hl:soup_mascot",
  tick: guestTick,
  adopt: () => undefined,
  onHit: (g, s, player) => {
    const o = owner(s);
    soundAt(player, "hl.glitch", g.location, 0.7, 1);
    if (o) end(o);
    else vanish(g);
  },
});
