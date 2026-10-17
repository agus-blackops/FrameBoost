// First time in the world: the journal, and a warning.

import { give, tr, valid } from "../core/util.js";
import { playerProp, setPlayerProp } from "../core/store.js";

export function onFirstJoin(player) {
  if (!valid(player) || playerProp(player, "hl:intro", false)) return;
  setPlayerProp(player, "hl:intro", true);
  give(player, "hl:journal");
  player.sendMessage(tr("hl.intro.1"));
  player.sendMessage(tr("hl.intro.2"));
}
