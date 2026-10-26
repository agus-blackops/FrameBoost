// The lights go out one by one, furthest first. You hear breathing in the
// dark. When they come back, sometimes someone is standing in front of you.
// (Jeff does this to you when he comes into the house.)

import { world } from "@minecraft/server";
import { block, distance, later, onCooldown, randInt, soundAt, soundBehind, tryRun } from "../core/util.js";
import { addFear } from "../core/store.js";
import { restoreBlocks, swapBlock } from "../core/blocks.js";
import { nearWard } from "../items/ward.js";
import { faceFlash } from "../creatures/scares.js";

const LIGHTS = /^minecraft:(soul_)?(torch|lantern)$/;
export const isLightsGone = (type) => type === "minecraft:air";

export function lightsOut(player) {
  if (nearWard(player.dimension, player.location) || onCooldown(`lights:${player.id}`, 20 * 60)) return false;
  const { x, y, z } = player.location;
  const found = [];
  for (let dx = -10; dx <= 10; dx++)
    for (let dz = -10; dz <= 10; dz++)
      for (let dy = -3; dy <= 4; dy++) {
        const b = block(player.dimension, x + dx, y + dy, z + dz);
        if (b && LIGHTS.test(b.typeId)) found.push(b);
      }
  if (!found.length) return false;
  found.sort((a, b) => distance(b, player.location) - distance(a, player.location));
  const lights = found.slice(-12);
  const dark = randInt(100, 160);
  const back = world.getAbsoluteTime() + lights.length * 6 + dark;
  lights.forEach((b, i) =>
    later(i * 6, () => {
      const here = block(player.dimension, b.x, b.y, b.z);
      if (!here || !LIGHTS.test(here.typeId)) return;
      if (swapBlock("hb:lights", here, "minecraft:air", back)) {
        soundAt(player, "random.fizz", { x: b.x + 0.5, y: b.y + 0.5, z: b.z + 0.5 }, 0.6, 0.4);
      }
    })
  );
  const out = lights.length * 6;
  later(out + 30, () => soundBehind(player, "hb.breath", 1.3, 0.9, 1));
  later(out + dark, () => {
    restoreBlocks("hb:lights", isLightsGone, true);
    if (Math.random() < 0.5) tryRun(() => faceFlash(player));
  });
  addFear(player, 25);
  return true;
}
