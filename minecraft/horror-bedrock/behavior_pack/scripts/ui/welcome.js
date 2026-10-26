// The introduction, the first time you are in the world: a few camera shots
// around you while the story is told in titles, from high above, from far
// off across the ground, from over your shoulder, and from right in front of
// your face. Sneak to skip it. At the end, the journal.

import { system } from "@minecraft/server";
import { give, later, offsetFromView, sound, tr, tryRun, valid } from "../core/util.js";
import { playerProp, setPlayerProp } from "../core/store.js";

const SHOT = 90; // ticks each shot lasts
const playing = new Map(); // playerId -> the interval watching for a sneak

function fade(player, inT, hold, out) {
  tryRun(() =>
    player.camera.fade({ fadeColor: { red: 0, green: 0, blue: 0 }, fadeTime: { fadeInTime: inT, holdTime: hold, fadeOutTime: out } })
  );
}

function shot(player, from, to, look) {
  tryRun(() => player.camera.setCamera("minecraft:free", { location: from, facingLocation: look }));
  later(1, () =>
    tryRun(() =>
      player.camera.setCamera("minecraft:free", {
        location: to, facingLocation: look, easeOptions: { easeTime: SHOT / 20, easeType: "InOutSine" },
      })
    )
  );
}

function caption(player, main, sub) {
  tryRun(() => player.onScreenDisplay.setTitle(main, { subtitle: sub, fadeInDuration: 15, stayDuration: SHOT - 35, fadeOutDuration: 20 }));
}

function finish(player, skipped) {
  const id = playing.get(player.id);
  if (id === undefined) return;
  system.clearRun(id);
  playing.delete(player.id);
  if (!valid(player)) return;
  fade(player, 0, 0.3, 1);
  later(5, () => tryRun(() => player.camera.clear()));
  tryRun(() => player.removeEffect("slowness"));
  if (skipped) tryRun(() => player.onScreenDisplay.setTitle(" ", { fadeInDuration: 0, stayDuration: 1, fadeOutDuration: 0 }));
  if (!playerProp(player, "hb:journal_given", false)) {
    setPlayerProp(player, "hb:journal_given", true);
    give(player, "hb:journal");
    give(player, "hb:flashlight");
    give(player, "hb:battery", 2);
  }
  later(20, () => {
    player.sendMessage(tr("hb.intro.msg.1"));
    player.sendMessage(tr("hb.intro.msg.2"));
  });
}

export function playIntro(player) {
  if (playing.has(player.id) || player.dimension.id !== "minecraft:overworld") return false;
  const p = { ...player.location };
  const head = { x: p.x, y: p.y + 1.6, z: p.z };
  const ahead = (d, deg, dy) => {
    const o = offsetFromView(player, d, deg);
    return { x: o.x, y: p.y + dy, z: o.z };
  };
  const shots = [
    // High above, looking down on you, coming lower.
    () => {
      shot(player, { x: p.x, y: p.y + 34, z: p.z + 2 }, { x: p.x, y: p.y + 18, z: p.z + 6 }, head);
      caption(player, { rawtext: [{ text: "§l§fHORROR LEGENDS" }] }, { rawtext: [{ text: "§4§lBEDROCK" }] });
      sound(player, "hb.drone", 0.9, 0.6);
    },
    // From far off across the ground, closing in.
    () => {
      shot(player, ahead(28, 20, 3), ahead(14, 10, 2), head);
      caption(player, " ", tr("hb.intro.1"));
      sound(player, "hb.musicbox", 0.6, 0.8);
    },
    // Over your shoulder, at whatever is out there.
    () => {
      const back = ahead(2.5, 180, 2.2);
      shot(player, back, ahead(1.5, 160, 2), ahead(30, 0, 1));
      caption(player, " ", tr("hb.intro.2"));
      later(40, () => sound(player, "hb.whisper", 0.7, 0.8));
    },
    // Your face. Something behind you.
    () => {
      shot(player, ahead(5, 0, 1.8), ahead(2.2, 0, 1.7), head);
      caption(player, " ", tr("hb.intro.3"));
      later(60, () => sound(player, "hb.stinger", 0.7, 0.8));
    },
  ];
  tryRun(() => player.addEffect("slowness", SHOT * shots.length + 40, { amplifier: 255, showParticles: false }));
  fade(player, 0.3, 0.5, 1);
  shots.forEach((run, i) =>
    later(10 + i * SHOT, () => {
      if (!playing.has(player.id)) return;
      if (i) fade(player, 0.3, 0.2, 0.5);
      later(i ? 8 : 0, () => playing.has(player.id) && run());
    })
  );
  later(10 + shots.length * SHOT, () => finish(player, false));
  // Sneak to skip.
  playing.set(
    player.id,
    system.runInterval(() => {
      if (!valid(player)) return finish(player, true);
      if (tryRun(() => player.isSneaking)) finish(player, true);
    }, 5)
  );
  return true;
}

export function onFirstJoin(player) {
  if (!valid(player) || playerProp(player, "hb:intro", false)) return;
  setPlayerProp(player, "hb:intro", true);
  tryRun(() => player.sendMessage(tr("hb.intro.skip")));
  playIntro(player);
}
