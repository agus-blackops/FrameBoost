// First time in the world: the journal, and a warning.

import { give, tr, valid } from "../lib/util.js";
import { playerProp, setPlayerProp } from "../lib/state.js";

export function onFirstJoin(player) {
  if (!valid(player) || playerProp(player, "hl:intro", false)) return;
  setPlayerProp(player, "hl:intro", true);
  give(player, "hl:journal");
  player.sendMessage(tr("hl.intro.1"));
  player.sendMessage(tr("hl.intro.2"));
}
