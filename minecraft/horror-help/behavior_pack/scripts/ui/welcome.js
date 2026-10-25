// First time in the world: the journal, and a warning.

import { give, tr, valid } from "../core/util.js";
import { playerProp, setPlayerProp } from "../core/store.js";

export function onFirstJoin(player) {
  if (!valid(player) || playerProp(player, "hh:intro", false)) return;
  setPlayerProp(player, "hh:intro", true);
  give(player, "hh:journal");
  give(player, "hh:radio");
  player.sendMessage(tr("hh.intro.1"));
  player.sendMessage(tr("hh.intro.2"));
}
