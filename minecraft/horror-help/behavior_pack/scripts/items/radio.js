// The emergency radio. Turn it on and you get static, and then, if anyone
// is still broadcasting, a warning about whatever is closest to you right
// now. Most nights there is only the station ID and the hum.

import { actionBar, distance, later, sound, tr } from "../core/util.js";
import { allFor } from "../core/creatures.js";
import { eclipse } from "../creatures/sunvanished.js";

export const RADIO = "hh:radio";
const QUIET = 3; // hh.radio.quiet.0 .. 2

export function tuneIn(player) {
  sound(player, "hh.static", 0.6, 1.2);
  const near = allFor(player)
    .map((s) => [s, distance(player.location, s.entity.location)])
    .sort((a, b) => a[1] - b[1])[0]?.[0];
  const key = eclipse.on
    ? "hh.radio.sunvanished"
    : near
      ? `hh.radio.${near.kind}`
      : `hh.radio.quiet.${Math.floor(Math.random() * QUIET)}`;
  later(20, () => actionBar(player, tr(key)));
  return key;
}
