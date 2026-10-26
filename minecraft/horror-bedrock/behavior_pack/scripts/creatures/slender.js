// Act III: Slenderman, and the eight pages. One night the fog comes in and
// there are pages pinned to the trees around you, scrawled drawings and
// words: ALWAYS WATCHES. NO EYES. LEAVE ME ALONE. Find all eight. With
// every page the drums get louder, the fog thicker, and he is closer: very
// tall, very thin, a black suit and no face, standing among the trees. Don't
// look at him. Looking brings the static, and the static hurts.
//
//   pages    the game: eight pages within forty blocks, eight minutes. Find
//            them all and he lets you go, with a few nights of peace.
//            If he reaches you, or time runs out, the pages blow away
//   glimpse  (minor) far off between the trees for a moment
//
// The pages are blocks (hb:slender_page) on the north side of trunks; where
// there aren't enough trees, a bare post is put up for one. Both are cleared
// when the game ends, and also on the next start if the game was closed.

import { world } from "@minecraft/server";
import {
  actionBar, block, canSee, distance, faceTowards, give, groundAt, groundSpot, later, looksAt, rand, sound,
  soundAt, takeItem, title, tr, tryRun, visibleSpot,
} from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, bump, discover, flags, setWorldProp, worldProp } from "../core/store.js";
import { blackout, scare } from "../core/fx.js";
import { swapBlock, restoreBlocks } from "../core/blocks.js";
import { nearWard } from "../items/ward.js";

export const PAGE = "hb:slender_page";
const PAGES = 8;
const TIME = 20 * 60 * 8; // ticks to find them all
const LOG = /_log$|^minecraft:log2?$/;
const POSTS = "hb:posts";
const isPost = (type) => type === "minecraft:oak_log";

// The game in progress: { playerId, found, pages: [{x, y, z}], clock, drumIn }
let game;

function savePages(list) {
  setWorldProp("hb:pages", JSON.stringify(list));
}

// Take the pages off the trees and the posts down.
export function clearPages() {
  const list = tryRun(() => JSON.parse(worldProp("hb:pages", "[]"))) ?? [];
  const dimension = world.getDimension("minecraft:overworld");
  for (const p of list) {
    const b = block(dimension, p.x, p.y, p.z);
    if (b?.typeId === PAGE) tryRun(() => b.setType("minecraft:air"));
  }
  savePages([]);
  restoreBlocks(POSTS, isPost, true);
}

function far(a, list, min) {
  return list.every((p) => distance(a, p) >= min);
}

// A page on the north side of a trunk, at eye height.
function treePage(player, list) {
  const { x, y, z } = player.location;
  for (let i = 0; i < 400; i++) {
    const a = rand(0, Math.PI * 2);
    const r = rand(12, 40);
    const cx = Math.floor(x + Math.cos(a) * r);
    const cz = Math.floor(z + Math.sin(a) * r);
    for (const dy of [1, 3, -1, 5, -3]) {
      const b = block(player.dimension, cx, Math.floor(y) + dy, cz);
      if (!b || !LOG.test(b.typeId)) continue;
      let base = b;
      for (let k = 1; k <= 12; k++) {
        const below = block(player.dimension, cx, b.y - k, cz);
        if (below && LOG.test(below.typeId)) base = below;
        else break;
      }
      const at = { x: cx, y: base.y + 1, z: cz - 1 };
      const trunk = block(player.dimension, cx, at.y, cz);
      const spot = block(player.dimension, at.x, at.y, at.z);
      const floor = block(player.dimension, at.x, base.y - 1, at.z);
      if (!trunk || !LOG.test(trunk.typeId) || !spot?.isAir || !floor || floor.isAir) break;
      if (!far(at, list, 8)) break;
      return at;
    }
  }
  return undefined;
}

// No tree: a bare post, two logs, and the page on that.
function postPage(player, list) {
  for (let i = 0; i < 12; i++) {
    const g = groundSpot(player, 12, 36, rand(0, 360), 0);
    if (!g) continue;
    const x = Math.floor(g.x);
    const z = Math.floor(g.z);
    const y = groundAt(player.dimension, x + 0.5, z + 0.5, g.y);
    if (y === undefined) continue;
    const at = { x, y: y + 1, z: z - 1 };
    if (!far(at, list, 8)) continue;
    const lo = block(player.dimension, x, y, z);
    const hi = block(player.dimension, x, y + 1, z);
    const front = block(player.dimension, at.x, at.y, at.z);
    if (!lo?.isAir || !hi?.isAir || !front?.isAir) continue;
    const back = world.getAbsoluteTime() + 24000 * 30;
    if (!swapBlock(POSTS, lo, "minecraft:oak_log", back) || !swapBlock(POSTS, hi, "minecraft:oak_log", back)) continue;
    return at;
  }
  return undefined;
}

export function startPages(player, anywhere = false) {
  if (game || trackedFor(player, "slender") || nearWard(player.dimension, player.location)) return false;
  const list = [];
  for (let i = 0; i < PAGES; i++) {
    const at = treePage(player, list) ?? postPage(player, list);
    if (!at) break;
    list.push(at);
  }
  if (list.length < (anywhere ? 1 : PAGES)) {
    restoreBlocks(POSTS, isPost, true);
    return false;
  }
  for (const p of list) tryRun(() => block(player.dimension, p.x, p.y, p.z).setType(PAGE));
  savePages(list);
  game = { playerId: player.id, found: 0, total: list.length, pages: list, clock: 0, drumIn: 40 };
  flags.slender = true;
  tryRun(() => player.runCommand("fog @s push hb:slender hb_slender"));
  title(player, tr("hb.sl.title"), tr("hb.sl.goal", String(list.length)), 80);
  sound(player, "hb.drone", 1, 0.6);
  later(100, () => actionBar(player, tr("hb.sl.count", "0", String(list.length))));
  addFear(player, 20);
  return true;
}

function him(player) {
  return trackedFor(player, "slender");
}

// Somewhere `dist` blocks away, off to the side or behind: where you'll see
// him when you turn.
function moveHim(e, player, dist) {
  const at = groundSpot(player, dist, dist + 4, rand(0, 1) < 0.5 ? rand(60, 180) : rand(-180, -60), 15);
  if (!at) return false;
  tryRun(() => e.teleport(at));
  faceTowards(e, player.location);
  return true;
}

function appear(player) {
  if (him(player)) return;
  const at = groundSpot(player, 30, 36, 180, 40);
  if (!at) return;
  spawn("slender", at, player, "game", { major: true, how: "game", look: 0, unseen: 0, clock: 0 });
}

// You tore a page off a tree.
export function onPage(player, b) {
  if (!game || game.playerId !== player.id) return;
  const i = game.pages.findIndex((p) => p.x === b.x && p.y === b.y && p.z === b.z);
  if (i < 0) return;
  game.pages.splice(i, 1);
  savePages(game.pages);
  game.found++;
  give(player, "hb:page");
  sound(player, "hb.page", 1, 1);
  actionBar(player, tr("hb.sl.count", String(game.found), String(game.total)));
  tryRun(() => player.sendMessage(tr(`hb.sl.page.${Math.min(game.found, PAGES)}`)));
  discover("slender");
  addFear(player, 10);
  if (game.found === 1) appear(player);
  if (game.found >= game.total) win(player);
}

function end(player) {
  const s = player && him(player);
  if (s) vanish(s.entity, false);
  if (player) tryRun(() => player.runCommand("fog @s remove hb_slender"));
  game = undefined;
  flags.slender = false;
  clearPages();
}

function win(player) {
  const n = bump("wins");
  setWorldProp("hb:peace_until", world.getDay() + 3);
  takeItem(player, "hb:page", PAGES);
  end(player);
  blackout(player, 1.5);
  later(30, () => {
    title(player, tr("hb.sl.win"), tr("hb.sl.win_sub"), 100);
    sound(player, "hb.dawn", 1, 1);
    give(player, "hb:note_8");
    player.sendMessage(tr("hb.j.wins", String(n)));
  });
}

function lose(player, how) {
  end(player);
  if (!player || how === "silent") return;
  scare(player, " ", tr(how === "time" ? "hb.sl.time" : "hb.sl.caught"));
  tryRun(() => player.addEffect("darkness", 200, { showParticles: false }));
  tryRun(() => player.applyDamage(12));
  addFear(player, 40);
}

export function onPlayerDied(player) {
  if (game?.playerId === player.id) lose(player, "silent");
}

// Every 20 ticks: the drums, the clock.
export function tickPages() {
  if (!game) return;
  const player = world.getAllPlayers().find((p) => p.id === game.playerId);
  if (!player || player.dimension.id !== "minecraft:overworld") return lose(player, "silent");
  game.clock += 20;
  if (game.clock >= TIME) return lose(player, "time");
  if (game.clock === 20 * 30) appear(player);
  if ((game.drumIn -= 20) <= 0) {
    game.drumIn = Math.max(40, 160 - game.found * 15);
    sound(player, "hb.drum", 0.4 + game.found * 0.08, 1);
    if (game.found >= 3) later(10, () => sound(player, "hb.static", 0.12 + game.found * 0.03, 0.6));
    if (game.found >= 6) later(20, () => sound(player, "hb.drone", 0.5, 0.5));
  }
  if (game.clock % (20 * 60) === 0) {
    const left = Math.round((TIME - game.clock) / (20 * 60));
    actionBar(player, tr("hb.sl.left", String(left)));
  }
}

// Looking at him: the static, louder, and then it hurts.
function stared(e, s, player, d) {
  s.look += 4;
  s.unseen = 0;
  tryRun(() => e.setProperty("hb:tendrils", d < 14));
  if (s.look % 12 === 0) sound(player, "hb.static", Math.min(1, 0.2 + s.look / 80), rand(0.7, 0.9));
  if (s.look >= 60 && s.look % 20 === 0) {
    tryRun(() => player.applyDamage(2));
    tryRun(() => player.addEffect("nausea", 120, { showParticles: false }));
    blackout(player, 0.1);
  }
  if (s.look >= 120) caught(e, s, player);
}

function caught(e, s, player) {
  s.look = 0;
  blackout(player, 0.6);
  scare(player, " ", tr("hb.sl.sub"));
  tryRun(() => player.applyDamage(game ? 8 : 4));
  addFear(player, 30);
  if (!game) return vanish(e, false);
  moveHim(e, player, 30);
}

function tick(e, s, player) {
  s.clock += 4;
  const d = distance(player.location, e.location);
  if (s.how === "glimpse") {
    faceTowards(e, player.location);
    if (canSee(player, e, 80, 0.93)) s.look += 4;
    if (s.look >= 20 || s.clock > 200 || nearWard(player.dimension, player.location)) {
      if (s.look) soundAt(player, "hb.static", e.location, 0.6, 0.7);
      vanish(e, false);
    }
    return;
  }
  if (!game && !s.adopted) return vanish(e, false);
  faceTowards(e, player.location);
  if (d < 2.5) return caught(e, s, player);
  if (canSee(player, e, 72, 0.9) || looksAt(player, e, 10, 0.8)) return stared(e, s, player, d);
  s.look = Math.max(0, s.look - 6);
  tryRun(() => e.setProperty("hb:tendrils", false));
  // Every time you look away, he is closer: how close depends on the pages.
  const found = game?.found ?? 4;
  if ((s.unseen += 4) >= Math.max(40, 120 - found * 10)) {
    s.unseen = 0;
    moveHim(e, player, Math.max(5, 34 - found * 4));
  }
}

export function glimpse(player) {
  if (trackedFor(player, "slender") || nearWard(player.dimension, player.location)) return false;
  const at = visibleSpot(player, 30, 45, rand(-60, 60), 20);
  if (!at) return false;
  const e = spawn("slender", at, player, "glimpse", { how: "glimpse", look: 0, clock: 0 });
  if (!e) return false;
  addFear(player, 8);
  return true;
}

define({
  kind: "slender",
  type: "hb:slenderman",
  weights: (ctx) => (ctx.under ? [0, 0] : [ctx.forest ? 3 : 1, ctx.night ? (ctx.forest ? 6 : 3) : 1]),
  minor: glimpse,
  major: (player) => startPages(player),
  tick,
  onHit: (e, s, player) => {
    sound(player, "hb.static", 1, 0.6);
    tryRun(() => player.addEffect("darkness", 80, { showParticles: false }));
  },
  off: () => {
    const player = game && world.getAllPlayers().find((p) => p.id === game.playerId);
    end(player);
  },
  test: (player) => startPages(player, true) || glimpse(player),
});

export const pagesActive = () => !!game;
