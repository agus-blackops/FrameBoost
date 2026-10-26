// The diary of whoever was here before you, a page at a time, found in the
// chests of the structures (creatures/ruins.js). Use one to read it; the
// journal keeps the ones you have read.

import { ActionFormData } from "@minecraft/server-ui";
import { sound, tr, tryRun } from "../core/util.js";
import { playerProp, setPlayerProp } from "../core/store.js";

export const NOTES = 8;
const NOTE = /^hb:note_(\d)$/;

export function noteNumber(id) {
  const m = NOTE.exec(id ?? "");
  return m ? Number(m[1]) : 0;
}

export function notesRead(player) {
  return playerProp(player, "hb:notes", 0);
}

export function hasRead(player, n) {
  return (notesRead(player) & (1 << n)) !== 0;
}

export function readNote(player, n, back) {
  setPlayerProp(player, "hb:notes", notesRead(player) | (1 << n));
  sound(player, "hb.page", 0.6, 1);
  new ActionFormData()
    .title(tr(`hb.note.${n}.title`))
    .body(tr(`hb.note.${n}.text`))
    .button(tr(back ? "hb.j.back" : "hb.j.close"))
    .show(player)
    .then(() => back && tryRun(back))
    .catch(() => {});
}
