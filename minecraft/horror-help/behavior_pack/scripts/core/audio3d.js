// Sounds in three dimensions. The game places every sound that has a
// position: left, right, behind, above, near, far. This module is for the
// ones that MOVE: someone circling you in the dark, footsteps coming up a
// tunnel, a howl a long way off, a board creaking just behind you.

import { audio3dOn, later, rand, soundAt, tryRun } from "./util.js";

const TAU = Math.PI * 2;

function around(player, radius, angle, dy = 0) {
  return {
    x: player.location.x + Math.cos(angle) * radius,
    y: player.location.y + 1 + dy,
    z: player.location.z + Math.sin(angle) * radius,
  };
}

// Footsteps going round you, closing in: `count` steps, `radius` blocks away
// at first, a little nearer each time. They keep going whichever way you turn.
export function circleSteps(player, { count = 14, radius = 9, gap = 5, id = "hh.step", pitch = 1, volume = 0.9, closing = 0.35 } = {}) {
  if (!audio3dOn()) return false;
  const start = rand(0, TAU);
  const dir = Math.random() < 0.5 ? -1 : 1;
  for (let i = 0; i < count; i++) {
    later(i * gap, () => {
      const r = Math.max(2.5, radius - i * closing);
      soundAt(player, id, around(player, r, start + dir * (i / count) * TAU * 0.8), pitch * rand(0.92, 1.08), volume);
    });
  }
  return true;
}

// Footsteps straight towards you, from `from` blocks away to `to` blocks,
// from a direction (radians) you didn't pick.
export function stepsTowards(player, { from = 16, to = 2.5, count = 10, gap = 7, id = "hh.step", pitch = 1, volume = 1 } = {}) {
  if (!audio3dOn()) return false;
  const angle = rand(0, TAU);
  for (let i = 0; i < count; i++) {
    later(i * gap, () => {
      const r = from + (to - from) * (i / Math.max(1, count - 1));
      soundAt(player, id, around(player, r, angle), pitch * rand(0.95, 1.05), volume);
    });
  }
  return true;
}

// A sound that passes you from one side to the other: a few short copies on a
// line, pitched down once it has gone by (the way a moving thing sounds).
export function passBy(player, { id = "hh.warp", count = 6, gap = 4, side = 5, length = 18, volume = 1 } = {}) {
  if (!audio3dOn()) return false;
  const a = rand(0, TAU);
  const along = { x: Math.cos(a), z: Math.sin(a) };
  const across = { x: -along.z, z: along.x };
  for (let i = 0; i < count; i++) {
    later(i * gap, () => {
      const t = i / Math.max(1, count - 1) - 0.5; // -0.5 .. 0.5
      const at = {
        x: player.location.x + across.x * side + along.x * t * length,
        y: player.location.y + 1,
        z: player.location.z + across.z * side + along.z * t * length,
      };
      soundAt(player, id, at, t < 0 ? 1.1 : 0.9, volume);
    });
  }
  return true;
}

// Something calling from far away (the game carries a sound a long way).
export function farOff(player, id = "hh.howl", { min = 45, max = 70, volume = 1, pitch = 1 } = {}) {
  soundAt(player, id, around(player, rand(min, max), rand(0, TAU)), pitch, volume);
  return true;
}

// A board or a door complaining, close by, behind you.
export function creakNear(player, { volume = 0.9 } = {}) {
  const v = tryRun(() => player.getViewDirection()) ?? { x: 0, z: 1 };
  const back = Math.atan2(-v.z, -v.x) + rand(-0.8, 0.8);
  soundAt(player, "hh.creak", around(player, rand(2.5, 5), back), rand(0.85, 1.1), volume);
  return true;
}
