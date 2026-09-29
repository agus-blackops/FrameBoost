// Prologue: not everything you hear is there.

import { isUnderground, later, offsetFromView, pick, rand, randInt, soundAt, soundBehind, tr } from "../lib/util.js";
import { addFear, discover } from "../lib/state.js";

export function ambience(player) {
  const under = isUnderground(player);
  const event = pick([
    ["footsteps", 5],
    ["door", 3],
    ["chest", 2],
    ["cave", 3],
    ["hiss", 1],
    ["mining", under ? 3 : 0],
    ["whisper", 2],
    ["breath", 1],
    ["scratch", 2],
  ]);
  const near = (min, max) => {
    const p = offsetFromView(player, rand(min, max), rand(90, 270));
    return { x: p.x, y: player.location.y + 1, z: p.z };
  };
  switch (event) {
    case "footsteps": {
      const step = under ? "step.stone" : "step.grass";
      for (let i = 0; i < 5; i++) later(i * 7, () => soundBehind(player, step, 4 - i * 0.5, 0.9, 0.8));
      break;
    }
    case "door": {
      const at = near(6, 10);
      soundAt(player, "random.door_open", at);
      later(randInt(20, 50), () => soundAt(player, "random.door_close", at));
      break;
    }
    case "chest": {
      const at = near(5, 9);
      soundAt(player, "random.chestopen", at, 1, 0.7);
      later(randInt(30, 60), () => soundAt(player, "random.chestclosed", at, 1, 0.7));
      break;
    }
    case "cave":
      soundBehind(player, "ambient.cave", rand(6, 14), rand(0.8, 1.0), 1);
      break;
    case "hiss":
      soundBehind(player, "random.fuse", 1.5, 0.5, 1);
      break;
    case "mining":
      for (let i = 0; i < 4; i++) later(i * 12, () => soundBehind(player, "dig.stone", 9, 0.9, 0.8));
      break;
    case "whisper":
      soundBehind(player, "hl.whisper", 2, rand(0.85, 1.05), 0.8);
      if (Math.random() < 0.5) player.sendMessage(tr("hl.ambience.whisper", player.name));
      break;
    case "breath":
      soundBehind(player, "hl.breath", 1.5, rand(0.85, 1.0), 0.9);
      break;
    case "scratch": {
      // Something dragging its nails along the other side of the wall.
      const at = near(2, 4);
      for (let i = 0; i < 6; i++) later(i * 4, () => soundAt(player, "dig.gravel", at, 0.5 + i * 0.05, 0.4));
      break;
    }
  }
  discover("ambience");
  addFear(player, 6);
  return true;
}
