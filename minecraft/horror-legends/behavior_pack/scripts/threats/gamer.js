// Chapter 1: HerobrineGamer788, a player who joins your world.
//
// He joins like anyone else and plays like anyone else: walks over, chats,
// mines, builds, puts down torches, jumps around, hands you food. Then he
// starts copying you: he mines what you mine, places what you place, sneaks
// when you sneak, jumps when you jump. Then something is wrong: he freezes
// when you look at him and creeps closer when you don't, his eyes flicker
// white, his name glitches, your torches turn red. And then he stops
// pretending, and leaves the first page behind.

import { ItemStack, world } from "@minecraft/server";
import {
  OVERWORLD, PROTECTED, block, canSee, distance, faceTowards, facing, groundSpot, later, onCooldown, overworldPlayers,
  pick, randInt, sound, soundAt, tryRun, valid,
} from "../lib/util.js";
import { MOB, spawn, vanish } from "../lib/actors.js";
import { addFear, discover, intensity, setWorldProp, worldProp } from "../lib/state.js";
import { blackout, scare } from "../lib/fx.js";
import { redTorches } from "./herobrine.js";
import { dropPage } from "../items/pages.js";

export const FAKE = "hl:fake_player";
export const FAKE_NAME = "HerobrineGamer788";
const STAGE_TICKS = [20 * 150, 20 * 120, 20 * 90];
const MINABLE = /(_log|leaves|^minecraft:(dirt|grass_block|stone|sand|gravel|deepslate|andesite|diorite|granite|tuff))$/;

let fake = null; // { entity, playerId, stage, t, cool, mode, state, name }

export function fakeAlive() {
  return !!fake && valid(fake.entity);
}

export function fakeInfo() {
  return fake;
}

function chat(key, ...args) {
  if (!fakeAlive()) return;
  world.sendMessage({ rawtext: [{ text: `<${fake.name}> ` }, { translate: key, with: args.map(String) }] });
}

function mode(m) {
  if (fake.mode === m) return;
  fake.mode = m;
  tryRun(() => fake.entity.triggerEvent(`hl:${m}`));
}

function state(st) {
  if (fake.state === st) return;
  fake.state = st;
  tryRun(() => fake.entity.setProperty("hl:state", st));
}

function swing(ticks = 24) {
  state("swing");
  later(ticks, () => fakeAlive() && fake.state === "swing" && state("idle"));
}

export function gamerJoin(player) {
  if (fakeAlive() || player.dimension.id !== OVERWORLD) return false;
  if (worldProp("hl:fp_day", -1) === world.getDay()) return false;
  const at = groundSpot(player, 16, 24, 180, 50) ?? groundSpot(player, 10, 20, 0, 180);
  if (!at) return false;
  const e = tryRun(() => player.dimension.spawnEntity(FAKE, at));
  if (!e) return false;
  e.nameTag = FAKE_NAME;
  tryRun(() => e.setDynamicProperty("hl:director", true));
  fake = { entity: e, playerId: player.id, stage: 0, t: 0, cool: 80, mode: "roam", state: "idle", name: FAKE_NAME };
  world.sendMessage({ rawtext: [{ text: "§e" }, { translate: "multiplayer.player.joined", with: [FAKE_NAME] }] });
  later(60, () => chat(`hl.fake.hello.${randInt(1, 4)}`, player.name));
  discover("gamer");
  return true;
}

export function gamerLeave(silent = false) {
  if (fakeAlive()) {
    vanish(fake.entity, !silent);
    world.sendMessage({ rawtext: [{ text: "§e" }, { translate: "multiplayer.player.left", with: [FAKE_NAME] }] });
  }
  fake = null;
}

// Mine a natural block within reach, dropping it like a player would.
function mine(preferType) {
  const e = fake.entity;
  const { x, y, z } = e.location;
  let target;
  // Copying you, he'll dig into the ground too; on his own he mines what's in front of him.
  for (let dy = preferType ? -1 : 0; dy <= 2 && !target; dy++)
    for (let dx = -2; dx <= 2 && !target; dx++)
      for (let dz = -2; dz <= 2 && !target; dz++) {
        const b = block(e.dimension, x + dx, y + dy, z + dz);
        if (b && (preferType ? b.typeId === preferType : MINABLE.test(b.typeId))) target = b;
      }
  if (!target) return false;
  faceTowards(e, { x: target.x + 0.5, z: target.z + 0.5 });
  swing(30);
  later(24, () => {
    if (!fakeAlive() || target.typeId === "minecraft:air") return;
    const type = target.typeId;
    const drop = { "minecraft:stone": "minecraft:cobblestone", "minecraft:grass_block": "minecraft:dirt", "minecraft:deepslate": "minecraft:cobbled_deepslate" }[type] ?? type;
    tryRun(() => target.setType("minecraft:air"));
    const dig = /log/.test(type) ? "dig.wood" : /leaves|grass|dirt/.test(type) ? "dig.grass" : /sand|gravel/.test(type) ? "dig.sand" : "dig.stone";
    for (const p of overworldPlayers()) soundAt(p, dig, target.location, 1, 0.9);
    if (!/leaves/.test(type)) tryRun(() => e.dimension.spawnItem(new ItemStack(drop, 1), { x: target.x + 0.5, y: target.y + 0.3, z: target.z + 0.5 }));
  });
  return true;
}

// Put a block down next to himself: a little pillar, or whatever you placed.
function build(type) {
  const e = fake.entity;
  const dirs = [[1, 0], [-1, 0], [0, 1], [0, -1]].sort(() => Math.random() - 0.5);
  for (const [dx, dz] of dirs) {
    const bx = Math.floor(e.location.x) + dx;
    const bz = Math.floor(e.location.z) + dz;
    for (let by = Math.floor(e.location.y) - 1; by <= Math.floor(e.location.y) + 2; by++) {
      const here = block(e.dimension, bx, by, bz);
      const below = block(e.dimension, bx, by - 1, bz);
      if (!here?.isAir || !below || below.isAir) continue;
      faceTowards(e, { x: bx + 0.5, z: bz + 0.5 });
      swing(16);
      later(8, () => {
        if (!here.isAir) return;
        const ok = tryRun(() => (here.setType(type), true)) || tryRun(() => (here.setType("minecraft:planks"), true));
        if (ok) for (const p of overworldPlayers()) soundAt(p, "dig.stone", { x: bx + 0.5, y: by, z: bz + 0.5 }, 1.2, 0.8);
      });
      return true;
    }
  }
  return false;
}

function gift(player, items) {
  const [type, amount] = items[randInt(0, items.length - 1)];
  const f = facing(player);
  const at = { x: player.location.x + f.x, y: player.location.y + 0.5, z: player.location.z + f.z };
  swing(12);
  tryRun(() => player.dimension.spawnItem(new ItemStack(type, amount), at));
}

function act(player, d) {
  const stage = fake.stage;
  if (stage === 0) {
    const a = pick([["chat", 30], ["mine", 25], ["build", 15], ["torch", 8], ["jump", 12], ["gift", d < 6 ? 10 : 0]]);
    if (a === "chat") chat(`hl.fake.chat.${randInt(1, 8)}`, player.name);
    else if (a === "mine") mine() || chat(`hl.fake.chat.${randInt(1, 8)}`, player.name);
    else if (a === "build") {
      const type = ["minecraft:dirt", "minecraft:cobblestone", "minecraft:oak_planks"][randInt(0, 2)];
      build(type);
      later(20, () => fakeAlive() && build(type));
    } else if (a === "torch") build("minecraft:torch");
    else if (a === "jump") tryRun(() => fake.entity.applyImpulse({ x: 0, y: 0.42, z: 0 }));
    else if (a === "gift") {
      gift(player, [["minecraft:bread", 3], ["minecraft:apple", 2], ["minecraft:torch", 8], ["minecraft:cookie", 4]]);
      chat("hl.fake.gift");
    }
    fake.cool = randInt(80, 180);
  } else if (stage === 1) {
    const a = pick([["chat", 45], ["jump", 15], ["mine", 15], ["stare", 25]]);
    if (a === "chat") chat(`hl.fake.mimic.${randInt(1, 6)}`, player.name);
    else if (a === "jump") tryRun(() => fake.entity.applyImpulse({ x: 0, y: 0.42, z: 0 }));
    else if (a === "mine") mine();
    else {
      mode("freeze");
      state("stare");
      later(60, () => fakeAlive() && fake.stage === 1 && (state("idle"), mode("roam")));
    }
    fake.cool = randInt(80, 160);
  } else if (stage === 2) {
    const a = pick([["chat", 40], ["eyes", 25], ["torches", 15], ["name", 20]]);
    if (a === "chat") chat(`hl.fake.creepy.${randInt(1, 6)}`, player.name);
    else if (a === "eyes") {
      tryRun(() => fake.entity.setProperty("hl:eyes", true));
      later(randInt(10, 30), () => fakeAlive() && fake.stage < 3 && tryRun(() => fake.entity.setProperty("hl:eyes", false)));
    } else if (a === "torches") redTorches(player);
    else {
      const e = fake.entity;
      e.nameTag = Math.random() < 0.5 ? `§k${FAKE_NAME}` : "Herobrine";
      fake.name = e.nameTag;
      later(randInt(20, 40), () => {
        if (fakeAlive() && fake.stage < 3) {
          e.nameTag = FAKE_NAME;
          fake.name = FAKE_NAME;
        }
      });
    }
    addFear(player, 5);
    fake.cool = randInt(60, 120);
  }
}

// He stops pretending: white eyes, "I don't have to pretend any more",
// Herobrine where he stood, and the first page on the ground.
export function gamerReveal(player) {
  if (!fakeAlive() || fake.stage === 3) return;
  fake.stage = 3;
  const e = fake.entity;
  e.nameTag = "Herobrine";
  fake.name = "Herobrine";
  mode("freeze");
  state("stare");
  tryRun(() => e.setProperty("hl:eyes", true));
  faceTowards(e, player.location);
  chat("hl.fake.reveal");
  later(50, () => {
    if (!fakeAlive()) return;
    const at = { ...e.location };
    vanish(e);
    fake = null;
    blackout(player);
    const hb = spawn(MOB.herobrine, at, player, "herobrine", "reveal", { major: true });
    if (hb) faceTowards(hb, player.location);
    tryRun(() =>
      player.onScreenDisplay.setTitle("§4§lHEROBRINE", { subtitle: "§8" + FAKE_NAME, fadeInDuration: 0, stayDuration: 30, fadeOutDuration: 20 })
    );
    scare(player);
    sound(player, "mob.endermen.scream", 1, 0.4);
    soundAt(player, "ambient.weather.thunder", at, 0.6, 1);
    addFear(player, 40);
    later(90, () => world.sendMessage({ rawtext: [{ text: "§e" }, { translate: "multiplayer.player.left", with: [FAKE_NAME] }] }));
    later(60, () => dropPage(1, { x: at.x, y: at.y + 0.5, z: at.z }, player));
    setWorldProp("hl:fp_day", world.getDay());
  });
}

// Every 10 ticks.
export function tickGamer() {
  if (!fakeAlive()) {
    fake = null;
    // Spawn eggs: adopt. Leftovers from a closed session: leave.
    for (const e of world.getDimension(OVERWORLD).getEntities({ type: FAKE })) {
      if (tryRun(() => e.getDynamicProperty("hl:director"))) {
        fake = { entity: e };
        gamerLeave(true);
        continue;
      }
      const p = overworldPlayers()[0];
      if (!p) return;
      e.nameTag = FAKE_NAME;
      tryRun(() => e.setDynamicProperty("hl:director", true));
      fake = { entity: e, playerId: p.id, stage: 0, t: 0, cool: 40, mode: "roam", state: "idle", name: FAKE_NAME };
      world.sendMessage({ rawtext: [{ text: "§e" }, { translate: "multiplayer.player.joined", with: [FAKE_NAME] }] });
      break;
    }
    return;
  }
  const player = overworldPlayers().find((p) => p.id === fake.playerId);
  if (!player) {
    gamerLeave();
    return;
  }
  const e = fake.entity;
  fake.t += 10;
  const d = distance(player.location, e.location);

  // Players who fall too far behind just... catch up.
  if (d > 48) {
    const at = groundSpot(player, 14, 20, 180, 40);
    if (at) tryRun(() => e.teleport(at));
  }

  if (fake.stage < 2) {
    mode(d > 7 ? "follow" : "roam");
    if (fake.stage === 1) {
      // Copying you.
      if (fake.state !== "swing" && fake.state !== "stare") state(player.isSneaking ? "sneak" : "idle");
      if (player.isJumping && !onCooldown(`fakejump:${e.id}`, 10)) tryRun(() => e.applyImpulse({ x: 0, y: 0.42, z: 0 }));
    }
  } else if (fake.stage === 2) {
    // Still when you look. Closer when you don't.
    if (canSee(player, e, 64, 0.88)) {
      mode("freeze");
      state("stare");
    } else {
      mode(d > 4 ? "follow" : "freeze");
      if (fake.state === "stare") state("idle");
    }
  }

  if (fake.stage < 3 && (fake.cool -= 10) <= 0) tryRun(() => act(player, d));

  const limit = fake.stage < 3 ? STAGE_TICKS[fake.stage] / intensity().chance : Infinity;
  if (fake.stage < 2 && fake.t >= limit) {
    fake.stage++;
    fake.t = 0;
    if (fake.stage === 1) chat("hl.fake.mimic.1", player.name);
  } else if (fake.stage === 2 && fake.t >= limit && (d < 12 || fake.t >= limit + 20 * 30)) {
    gamerReveal(player);
  }
}

function mimicking(player) {
  return fakeAlive() && fake.stage === 1 && fake.playerId === player.id;
}

export function onBreakBlock(player, brokenBlockPermutation) {
  if (!mimicking(player)) return;
  // BlockPermutation.type isn't in this API version; the item form carries the id.
  const type = tryRun(() => brokenBlockPermutation.type?.id) ?? tryRun(() => brokenBlockPermutation.getItemStack(1)?.typeId);
  later(randInt(15, 35), () => {
    if (!fakeAlive()) return;
    if (!(type && mine(type))) {
      swing(20);
      if (Math.random() < 0.6) chat("hl.fake.copy");
    }
  });
}

export function onPlaceBlock(player, placed) {
  if (!mimicking(player)) return;
  const type = placed?.typeId;
  if (type && !PROTECTED.test(type)) later(randInt(15, 30), () => fakeAlive() && build(type));
}

// Hit him while he is still pretending and he laughs it off; once he is
// strange, he stops pretending.
export function onHit(player, target) {
  if (!fakeAlive() || target.id !== fake.entity.id) return false;
  if (fake.stage >= 2) gamerReveal(player);
  else {
    chat("hl.fake.hit");
    const f = facing(player);
    tryRun(() => target.applyImpulse({ x: f.x * 0.5, y: 0.3, z: f.z * 0.5 }));
  }
  return true;
}

export function onPlayerDied(player) {
  if (fakeAlive() && fake.playerId === player.id) later(40, () => gamerLeave());
}
