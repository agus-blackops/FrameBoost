// The apparitions: every creature the add-on puts in the world is tracked
// here with the player it is haunting and what it is doing, and ticked by
// its own module. Creatures from spawn eggs are adopted by the nearest
// player; ones left over from a closed session simply vanish.

import { world } from "@minecraft/server";
import { OVERWORLD, distance, faceTowards, rand, tryRun, valid } from "./util.js";

export const MOB = {
  herobrine: "hl:herobrine",
  null: "hl:null",
  fogMan: "hl:fog_man",
  dweller: "hl:cave_dweller",
  boss: "hl:herobrine_boss",
};
export const KIND_OF = Object.fromEntries(Object.entries(MOB).map(([k, v]) => [v, k]));

// id -> { entity, kind, playerId, mode, seen, unseen, age, timer, major, ... }
export const tracked = new Map();
const kinds = {};

// tick(entity, state, player); adopt(entity) -> the mode a spawn-egg creature starts in.
export function registerKind(kind, handlers) {
  kinds[kind] = handlers;
}

export function spawn(type, location, player, kind, mode, extra = {}) {
  const entity = tryRun(() => player.dimension.spawnEntity(type, location));
  if (!entity) return undefined;
  faceTowards(entity, player.location);
  tryRun(() => entity.setDynamicProperty("hl:director", true));
  tracked.set(entity.id, { entity, kind, playerId: player.id, mode, seen: 0, unseen: 0, age: 0, timer: 0, ...extra });
  return entity;
}

export function smoke(entity) {
  const at = entity.location;
  for (let i = 0; i < 6; i++) {
    tryRun(() =>
      entity.dimension.spawnParticle("minecraft:basic_smoke_particle", {
        x: at.x + rand(-0.4, 0.4),
        y: at.y + rand(0.2, 1.8),
        z: at.z + rand(-0.4, 0.4),
      })
    );
  }
}

export function vanish(entity, withSmoke = true) {
  if (withSmoke) tryRun(() => smoke(entity));
  tracked.delete(entity.id);
  tryRun(() => entity.triggerEvent("hl:vanish"));
}

export function stateOf(entity) {
  return entity && tracked.get(entity.id);
}

export function trackedFor(player, kind) {
  for (const s of tracked.values()) {
    if (s.kind === kind && s.playerId === player.id && valid(s.entity)) return s;
  }
  return undefined;
}

export function allFor(player) {
  return [...tracked.values()].filter((s) => s.playerId === player.id && valid(s.entity));
}

export function clearKind(kind) {
  for (const s of [...tracked.values()]) if (s.kind === kind && valid(s.entity)) vanish(s.entity, false);
}

// Something big is happening to this player right now.
export function encounterActive(player) {
  return allFor(player).some((s) => s.major || s.mode === "chasing");
}

function adopt(entity, players) {
  if (tryRun(() => entity.getDynamicProperty("hl:director"))) return undefined;
  let nearest;
  let best = 128;
  for (const p of players) {
    const d = distance(p.location, entity.location);
    if (d < best) {
      best = d;
      nearest = p;
    }
  }
  if (!nearest) return undefined;
  const kind = KIND_OF[entity.typeId];
  const mode = kinds[kind]?.adopt?.(entity) ?? "idle";
  const s = { entity, kind, playerId: nearest.id, mode, seen: 0, unseen: 0, age: 0, timer: 0, adopted: true };
  tracked.set(entity.id, s);
  return s;
}

// Every 4 ticks.
export function tickActors() {
  const dimension = world.getDimension(OVERWORLD);
  const players = world.getAllPlayers().filter((p) => p.dimension.id === OVERWORLD);
  const alive = new Set();
  for (const type of Object.keys(KIND_OF)) {
    for (const e of dimension.getEntities({ type })) {
      if (!valid(e)) continue;
      alive.add(e.id);
      const s = tracked.get(e.id) ?? adopt(e, players);
      if (!s) {
        vanish(e, false);
        continue;
      }
      s.entity = e;
      const player = players.find((p) => p.id === s.playerId);
      if (!player) {
        vanish(e, false);
        continue;
      }
      s.age += 4;
      tryRun(() => kinds[s.kind]?.tick(e, s, player));
    }
  }
  for (const id of [...tracked.keys()]) if (!alive.has(id)) tracked.delete(id);
}
