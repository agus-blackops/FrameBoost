// Remastered: the scares in between. Not a creature: the moments when
// something is too close. A face right in front of yours for the length of
// a blink. Breath on the back of your neck and something brushing past. A
// door slamming in an empty house. The screen tearing into static. A blow
// that hurts and leaves no mark. Your own name, written across your eyes.

import { BlockPermutation } from "@minecraft/server";
import {
  actionBar, block, facing, groundSpot, later, offsetFromView, pick, rand, sound, soundAt, soundBehind, tr, tryRun,
} from "../core/util.js";
import { define, spawn, trackedFor, vanish } from "../core/creatures.js";
import { addFear, discover, jumpscaresOn, unlocked } from "../core/store.js";
import { blackout } from "../core/fx.js";
import { circleSteps, creakNear, farOff, passBy, stepsTowards } from "../core/audio3d.js";
import { nearWard } from "../items/ward.js";

// Whose face it is depends on who has arrived.
const FACES = [["intruder", 4], ["alternate", 3], ["cartoon", 3], ["siren", 1]];

function fade(player, rgb, inT, hold, out) {
  tryRun(() =>
    player.camera.fade({ fadeColor: { red: rgb[0], green: rgb[1], blue: rgb[2] }, fadeTime: { fadeInTime: inT, holdTime: hold, fadeOutTime: out } })
  );
}

// A face, right in front of yours, for the length of a blink.
export function faceFlash(player) {
  if (nearWard(player.dimension, player.location)) return false;
  const kind = pick(FACES.map(([k, w]) => [k, unlocked(k) && !trackedFor(player, k) ? w : 0]));
  if (!kind) return false;
  const at = groundSpot(player, 1.3, 1.7, 0, 8);
  if (!at) return false;
  // Darkness first, the face in it, then the light again and it's gone.
  fade(player, [0, 0, 0], 0.05, 0.15, 0.05);
  const e = spawn(kind, at, player, "flash", { frozen: true, close: true });
  if (!e) return false;
  later(5, () => {
    sound(player, "hh.stinger", jumpscaresOn() ? 1 : 0.5, 1.1);
    if (jumpscaresOn()) sound(player, "hh.screech", 1, 1);
    if (jumpscaresOn()) {
      tryRun(() => player.runCommand("camerashake add @s 0.8 0.4 rotational"));
      later(8, () => fade(player, [0.5, 0, 0], 0.0, 0.1, 0.6));
    }
  });
  later(12, () => vanish(e, false));
  later(40, () => sound(player, "hh.ringing", 0.6));
  discover("scares");
  addFear(player, 30);
  return true;
}

// Breath on your neck, and something brushing past.
export function breathOnNeck(player) {
  const f = facing(player);
  soundBehind(player, "hh.breath", 0.6, 0.85, 1);
  later(14, () => {
    tryRun(() => player.applyKnockback(f.x, f.z, 0.35, 0.05));
    soundBehind(player, "step.cloth", 0.8, 0.7, 1);
    actionBar(player, tr("hh.scare.neck"));
  });
  addFear(player, 12);
  return true;
}

// A door slams in the house.
export function doorSlam(player) {
  const { x, y, z } = player.location;
  for (let dx = -8; dx <= 8; dx++)
    for (let dz = -8; dz <= 8; dz++)
      for (let dy = -2; dy <= 2; dy++) {
        const b = block(player.dimension, x + dx, y + dy, z + dz);
        if (!b || !/door/.test(b.typeId) || /trapdoor/.test(b.typeId)) continue;
        const states = tryRun(() => b.permutation.getAllStates()) ?? {};
        if (states.upper_block_bit) continue;
        const open = !!states.open_bit;
        const perm = tryRun(() => BlockPermutation.resolve(b.typeId, { ...states, open_bit: !open }));
        const at = { x: b.x + 0.5, y: b.y + 1, z: b.z + 0.5 };
        if (perm) tryRun(() => b.setPermutation(perm));
        soundAt(player, open ? "random.door_close" : "random.door_open", at, 0.7, 1);
        later(10, () => soundAt(player, "hh.knock", at, 0.6, 0.8));
        addFear(player, 12);
        return true;
      }
  // No door: one bangs somewhere in the dark anyway.
  const p = offsetFromView(player, rand(6, 10), rand(100, 260));
  soundAt(player, "random.door_close", { x: p.x, y: player.location.y + 1, z: p.z }, 0.6, 1);
  addFear(player, 8);
  return true;
}

// The picture tears into static.
export function staticBurst(player) {
  sound(player, "hh.static", 1, rand(0.8, 1.1));
  later(10, () => sound(player, "hh.shatter", 0.8, rand(0.9, 1.1)));
  for (let k = 0; k < 3; k++) {
    later(k * 5, () => fade(player, k % 2 ? [0.9, 0.9, 0.9] : [0, 0, 0], 0.0, 0.08, 0.05));
  }
  tryRun(() => player.onScreenDisplay.setTitle("§k||||||||||||||||", { fadeInDuration: 0, stayDuration: 12, fadeOutDuration: 2 }));
  addFear(player, 10);
  return true;
}

// It hurts, and there's nothing there.
export function phantomBlow(player) {
  fade(player, [0.6, 0, 0], 0.0, 0.1, 0.4);
  sound(player, "game.player.hurt", 1, 0.9);
  tryRun(() => player.runCommand("camerashake add @s 0.5 0.3 positional"));
  soundBehind(player, "hh.whisper", 1.2, 0.7, 0.8);
  addFear(player, 12);
  return true;
}

// Your name, across your eyes.
export function nameCall(player) {
  blackout(player, 0.4);
  tryRun(() =>
    player.onScreenDisplay.setTitle({ rawtext: [{ text: `§4${player.name}` }] }, { subtitle: tr("hh.scare.name_sub"), fadeInDuration: 0, stayDuration: 30, fadeOutDuration: 10 })
  );
  sound(player, "hh.whisper", 1, 0.6);
  addFear(player, 14);
  return true;
}

// 3D: something circles you in the dark, footsteps come up out of nowhere,
// a howl a long way off, a board creaking behind you, something fast going by.
export function circling(player) {
  addFear(player, 10);
  return circleSteps(player);
}

export function approaching(player) {
  addFear(player, 12);
  return stepsTowards(player, { from: 18, to: 2.5, count: 11, gap: 8 });
}

export function howling(player) {
  addFear(player, 8);
  return farOff(player, "hh.howl", { volume: 1 });
}

export function creaking(player) {
  addFear(player, 9);
  return creakNear(player);
}

export function passing(player) {
  addFear(player, 9);
  return passBy(player, { id: "hh.warp", count: 7, gap: 3, side: 4, length: 20 });
}

export function scaresMinor(player, ctx = {}) {
  const event = pick([
    ["circle", 2],
    ["approach", 2],
    ["howl", 2],
    ["creak", ctx.indoors ? 3 : 1],
    ["pass", 1],
    ["neck", 3],
    ["door", ctx.indoors ? 4 : 1],
    ["static", 2],
    ["blow", 2],
    ["name", 1],
  ]);
  const done = {
    neck: breathOnNeck, door: doorSlam, static: staticBurst, blow: phantomBlow, name: nameCall,
    circle: circling, approach: approaching, howl: howling, creak: creaking, pass: passing,
  }[event]?.(player);
  if (done) discover("scares");
  return !!done;
}

define({
  kind: "scares",
  weights: (ctx) => [3, ctx.night || ctx.under || ctx.indoors ? 2 : 1],
  minor: scaresMinor,
  major: faceFlash,
  test: (player) => faceFlash(player) || scaresMinor(player),
});
