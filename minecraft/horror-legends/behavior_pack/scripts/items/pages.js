// The five torn pages of a survivor's diary. Each creature leaves one behind
// the first time you live through it; reading all five teaches you the
// ritual that calls Herobrine out to fight.

import { ItemStack } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";
import { world } from "@minecraft/server";
import { actionBar, countItem, later, sound, soundAt, takeItem, tr, tryRun } from "../lib/util.js";
import { PAGES, pageRead, pagesRead, setPageRead, setWorldProp, worldProp } from "../lib/state.js";

export const pageItem = (n) => `hl:page_${n}`;
export const PAGE_OF = Object.fromEntries(PAGES.map((n) => [pageItem(n), n]));

// Leave page `n` where something just happened, unless it has been read or
// is already lying around somewhere (at most once a day).
export function dropPage(n, location, player) {
  if (pageRead(n) || countItem(player, pageItem(n)) > 0) return false;
  const day = world.getDay();
  if (worldProp(`hl:page_drop_${n}`, -99) === day) return false;
  const item = tryRun(() => player.dimension.spawnItem(new ItemStack(pageItem(n), 1), location));
  if (!item) return false;
  setWorldProp(`hl:page_drop_${n}`, day);
  soundAt(player, "hl.page", location, 1, 1);
  later(10, () => actionBar(player, tr("hl.page.dropped")));
  return true;
}

export function readPage(player, n) {
  const first = !pageRead(n);
  if (first) {
    takeItem(player, pageItem(n));
    setPageRead(n);
  }
  sound(player, "hl.page");
  new ActionFormData()
    .title(tr(`hl.page.${n}.title`))
    .body(tr(`hl.page.${n}.text`))
    .button(tr("hl.j.close"))
    .show(player)
    .then(() => {
      if (first && pagesRead() === PAGES.length) {
        player.sendMessage(tr("hl.page.all"));
        sound(player, "hl.drone", 0.8);
      } else if (first) {
        actionBar(player, tr("hl.page.count", pagesRead(), PAGES.length));
      }
    })
    .catch(() => {});
  return first;
}
