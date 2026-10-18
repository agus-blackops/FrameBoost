// Act III: Siren Head. First only a siren, very far away, winding up and
// down in the dark. Then a voice through a loudspeaker, words that are
// almost words. Then, among the trees and taller than them, something
// standing perfectly still with two speakers where its head should be.
// Don't watch it. If you watch it too long the sirens turn to you and it
// comes, in strides of five blocks. Get out of its sight (behind a hill,
// under a roof, into a ward's light) and stay out of it, and it loses you;
// whoever lived through that wrote the sixth page.

import {
  actionBar, canSee, distance, inSight, isIndoors, isUnderground, later, offsetFromView, rand, soundAt, tr, visibleSpot,
} from "../core/util.js";
import { define, setMode, spawn, stateOf, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover } from "../core/store.js";
import { scare } from "../core/fx.js";
import { nearWard } from "../items/ward.js";
import { dropPage } from "../items/pages.js";
import { outsideWards } from "../core/hunt.js";

function far(player, dist) {
  const p = offsetFromView(player, dist, rand(0, 360));
  return { x: p.x, y: player.location.y + 10, z: p.z };
}

export function sirenMinor(player) {
  if (Math.random() < 0.6) soundAt(player, "hl.siren", far(player, rand(50, 90)), rand(0.85, 1.0), 1);
  else soundAt(player, "hl.voice", far(player, rand(30, 50)), rand(0.8, 1.0), 1);
  addFear(player, 10);
  return true;
}

export function spawnSiren(player) {
  if (trackedFor(player, "siren")) return false;
  const at = visibleSpot(player, 38, 54, rand(-45, 45), 5);
  if (!at || !outsideWards(player.dimension, at)) return false;
  const e = spawn("siren", at, player, "idle", { major: true });
  if (e) later(40, () => soundAt(player, "hl.voice", e.location, 0.9, 1));
  return !!e;
}

export function hunt(e, player) {
  const s = stateOf(e);
  if (!s || s.mode === "hunt") return;
  setMode(s, "hunt");
  s.hunting = true;
  s.hidden = 0;
  soundAt(player, "hl.siren", e.location, 1.1, 1);
  scare(player, " ", "§4§l! ! !");
  actionBar(player, tr("hl.siren.hunt"));
  discover("siren");
  addFear(player, 35);
}

// You got away: it stands still, and then it is gone. The page is at your feet.
function lost(e, player) {
  soundAt(player, "hl.voice", e.location, 0.7, 1);
  vanish(e, false);
  actionBar(player, tr("hl.siren.lost"));
  later(40, () => dropPage(6, { x: player.location.x, y: player.location.y + 0.5, z: player.location.z }, player));
}

function hidden(player, e) {
  if (isIndoors(player) || isUnderground(player) || nearWard(player.dimension, player.location, 8)) return true;
  return !inSight(player, e.location) && distance(player.location, e.location) > 10;
}

function tick(e, s, player) {
  const d = distance(player.location, e.location);
  s.timer += 4;
  if (s.mode === "hunt") {
    if (s.timer % 60 === 0) soundAt(player, Math.random() < 0.6 ? "hl.siren" : "hl.voice", e.location, rand(0.9, 1.1), 1);
    s.hidden = hidden(player, e) ? s.hidden + 4 : 0;
    if (s.hidden >= 20 * 10) lost(e, player);
    else if (s.timer > 20 * 60) vanish(e, false);
    return;
  }
  if (s.adopted) return;
  // Standing among the trees. Don't watch it.
  if (canSee(player, e, 96, 0.95)) {
    discover("siren");
    if (++s.seen >= 12) hunt(e, player);
  } else if (s.seen > 0) s.seen--;
  if (s.timer % 120 === 0 && Math.random() < 0.5) soundAt(player, "hl.voice", e.location, rand(0.8, 1.0), 1);
  if (s.timer > 20 * 60 || d > 110) vanish(e, false);
}

define({
  kind: "siren",
  type: "hl:siren_head",
  modes: ["idle", "hunt"],
  weights: (ctx) => (ctx.outside && ctx.night ? [3, 3] : ctx.outside ? [1, 0] : [0, 0]),
  minor: sirenMinor,
  major: spawnSiren,
  tick,
  adopt: () => "idle",
  onHit: (e, s, player) => hunt(e, player),
  shine: () => {},
  test: (player) => spawnSiren(player) || sirenMinor(player),
});
