// Act II: Jenny Dweller. A friendly girl in a red hoodie, out on her own,
// who is SO happy to see you. She waves. She talks to you. She gives you a
// flower. She wants you to come with her, just a bit further, she has
// something to show you, and every time you get close she giggles and runs
// on ahead. When you finally catch up with her she stops pretending: the
// face splits down the middle into two jaws, two long grey arms with claws
// unfold out of the hoodie, and she hunts you. She hits hard enough to kill.
// Ignore her and she won't like it: she comes to find you.

import { ItemStack } from "@minecraft/server";
import {
  actionBar, caveSpot, distance, faceTowards, groundAt, isUnderground, later, rand, soundAt, soundBehind, title, tr,
  tryRun, visibleSpot,
} from "../core/util.js";
import { define, setMode, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { outsideWards } from "../core/hunt.js";

const CATCH_UP = 5; // how close you get before she runs on
const HOPS = 3; // how many times she runs on before she stops pretending
const IGNORED_FOR = 20 * 30;
const HUNT_FOR = 20 * 40;

function say(player, n) {
  tryRun(() => player.sendMessage({ rawtext: [{ text: "§c<Jenny>§r " }, { translate: `hl.jenny.say.${n}` }] }));
}

function hearts(e) {
  for (let i = 0; i < 3; i++) {
    tryRun(() => e.dimension.spawnParticle("minecraft:heart_particle", {
      x: e.location.x + rand(-0.4, 0.4), y: e.location.y + 2.1 + rand(0, 0.4), z: e.location.z + rand(-0.4, 0.4),
    }));
  }
}

export function meetJenny(player, anywhere = false) {
  if (trackedFor(player, "jenny") || nearWard(player.dimension, player.location)) return false;
  // Like the Dweller she has become, she also waits down in the caves.
  const cave = isUnderground(player);
  const at = cave
    ? caveSpot(player, 10, 16, rand(-40, 40), 25) ?? (anywhere ? caveSpot(player, 6, 12, 0, 120) : undefined)
    : visibleSpot(player, 14, 20, rand(-40, 40), 15) ?? (anywhere ? visibleSpot(player, 8, 14, 0, 90) : undefined);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("jenny", at, player, "lure", { major: true, phase: "lure", hops: 0, ignored: 0, clock: 0, cave });
  if (!e) return false;
  tryRun(() => e.setProperty("hl:wave", true));
  soundAt(player, "hl.giggle", e.location, 1.0, 1);
  later(10, () => say(player, 1));
  hearts(e);
  addFear(player, 4);
  return true;
}

// Somewhere behind you, a girl giggles. "Hello? Do you want to play?"
export function jennyMinor(player) {
  soundBehind(player, "hl.giggle", rand(8, 14), rand(0.95, 1.05), 1);
  later(30, () => say(player, 0));
  addFear(player, 6);
  return true;
}

// She runs on ahead, further from home (or deeper into the cave): "this way!"
function ahead(e, s, player) {
  if (s.cave) return caveSpot(player, 10, 16, rand(-30, 30), 20);
  const a = Math.atan2(e.location.z - player.location.z, e.location.x - player.location.x) + rand(-0.5, 0.5);
  const r = rand(10, 14);
  const x = e.location.x + Math.cos(a) * r;
  const z = e.location.z + Math.sin(a) * r;
  const y = groundAt(player.dimension, x, z, e.location.y);
  return y === undefined ? undefined : { x: Math.floor(x) + 0.5, y, z: Math.floor(z) + 0.5 };
}

function runAhead(e, s, player) {
  s.hops++;
  // A present for you, left where she stood.
  if (s.hops === 2) tryRun(() => player.dimension.spawnItem(new ItemStack("minecraft:poppy", 1), e.location));
  for (let attempt = 0; attempt < 6; attempt++) {
    const at = ahead(e, s, player);
    if (!at || !outsideWards(player.dimension, at)) continue;
    if (!tryRun(() => (e.teleport(at), true))) continue;
    faceTowards(e, player.location);
    tryRun(() => e.setProperty("hl:wave", s.hops === 1));
    soundAt(player, "hl.giggle", e.location, rand(1.0, 1.1), 1);
    say(player, 1 + s.hops);
    hearts(e);
    return true;
  }
  return false;
}

export function reveal(e, s, player, behind = false) {
  if (s.phase === "hunt") return;
  s.phase = "hunt";
  s.clock = 0;
  s.hunting = true;
  if (!behind) say(player, 5);
  else {
    const v = player.getViewDirection();
    tryRun(() => e.teleport({ x: player.location.x - v.x * 2, y: player.location.y, z: player.location.z - v.z * 2 }));
  }
  tryRun(() => e.setProperty("hl:wave", false));
  tryRun(() => e.setProperty("hl:revealed", true));
  setMode(s, "hunt");
  faceTowards(e, player.location);
  blackout(player, 0.35);
  soundAt(player, "hl.screech", e.location, 0.75, 1);
  later(4, () => soundAt(player, "hl.giggle", e.location, 0.55, 1));
  tryRun(() => player.addEffect("darkness", 100, { showParticles: false }));
  title(player, "§4JENNY", tr("hl.jenny.sub"), 30);
  discover("jenny");
  addFear(player, 35);
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  if (d > 80 || (!s.adopted && s.age > 20 * 150)) {
    vanish(e, false);
    return;
  }
  s.clock = (s.clock ?? 0) + 4;
  if (s.phase === "hunt") {
    if (nearWard(player.dimension, player.location, 6) || (!s.adopted && s.clock >= HUNT_FOR)) {
      // She goes back to pretending, somewhere else.
      soundAt(player, "hl.giggle", e.location, 0.7, 1);
      actionBar(player, tr("hl.jenny.gone"));
      vanish(e);
      return;
    }
    if (s.clock % 100 === 0) soundAt(player, "hl.giggle", e.location, rand(0.5, 0.65), 1);
    return;
  }
  // Pretending.
  s.phase ??= "lure";
  s.hops ??= 0;
  s.ignored ??= 0;
  faceTowards(e, player.location);
  if (s.clock % 40 === 0) hearts(e);
  if (nearWard(player.dimension, player.location)) return;
  if (d <= CATCH_UP) {
    s.ignored = 0;
    if (s.hops >= HOPS || !runAhead(e, s, player)) reveal(e, s, player);
    return;
  }
  // Not coming? She doesn't like that.
  s.ignored += d > 8 ? 4 : 0;
  if (s.ignored === 20 * 15) say(player, 6);
  if (s.ignored >= IGNORED_FOR) {
    say(player, 7);
    reveal(e, s, player, true);
  }
}

define({
  kind: "jenny",
  type: "hl:jenny_dweller",
  modes: ["lure", "hunt"],
  stride: { id: "hl.step", every: 1.2, volume: 0.7, pitch: [1.1, 1.3] },
  weights: (ctx) => (ctx.under ? [1, 2] : ctx.outside ? [2, 2] : [1, 0]),
  minor: jennyMinor,
  major: (player) => meetJenny(player),
  tick,
  adopt: () => "lure",
  // Hit her while she is pretending and she stops pretending.
  onHit: (e, s, player) => s.phase !== "hunt" && reveal(e, s, player),
  shine: (e, s, player) => {
    if (s.phase !== "hunt" || (s.dazed ?? 0) > s.age) return;
    s.dazed = s.age + 100;
    tryRun(() => e.addEffect("slowness", 40, { amplifier: 2, showParticles: false }));
    soundAt(player, "hl.shriek", e.location, 1.3, 0.7);
  },
  // She holds on.
  onStrike: (e, s, player) => {
    tryRun(() => player.addEffect("slowness", 40, { amplifier: 2, showParticles: false }));
    soundAt(player, "hl.giggle", e.location, 0.6, 1);
  },
  test: (player) => meetJenny(player, true) || jennyMinor(player),
});

