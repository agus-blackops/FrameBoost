// The registry. Every horror in the add-on describes itself here once (what
// entity it is, where it likes to happen, what it does when the director
// calls on it, how it reacts to light, to being hit, to being switched off),
// and every creature put in the world is tracked with the player it haunts
// and what it is doing. Creatures from spawn eggs are adopted by the nearest
// player; ones left over from a closed session simply vanish.

import { world } from "@minecraft/server";
import { OVERWORLD, distance, faceTowards, rand, tryRun, valid } from "./util.js";

// kind -> {
//   kind, type?          the entity, if it has one
//   modes?               values of its hl:mode property
//   weights(ctx)         -> [minor, major] how much it wants to happen here
//   minor(player, ctx)   a small scare; true if it happened
//   major(player, ctx)   an encounter; true if it happened
//   tick(e, s, player)   every 4 ticks for each of its entities
//   adopt(e)             the mode a spawn-egg creature starts in
//   onHit / onHurt / onDie / shine (the flashlight) / off (switched off) / test (journal)
// }
export const DEFS = {};
const TYPE_KIND = {};

export function define(def) {
  DEFS[def.kind] = def;
  if (def.type) TYPE_KIND[def.type] = def.kind;
  return def;
}

export function kindOf(entity) {
  return entity && TYPE_KIND[entity.typeId];
}

// id -> { entity, kind, playerId, mode, seen, unseen, age, timer, major, ... }
export const tracked = new Map();

export function setMode(s, mode) {
  if (!s) return;
  s.mode = mode;
  s.timer = 0;
  const def = DEFS[s.kind];
  if (def?.modes?.includes(mode)) {
    tryRun(() => s.entity.triggerEvent(`hl:${mode}`));
    tryRun(() => s.entity.setProperty("hl:mode", mode));
  }
}

export function spawn(kind, location, player, mode, extra = {}) {
  const def = DEFS[kind];
  const entity = def?.type && tryRun(() => player.dimension.spawnEntity(def.type, location));
  if (!entity) return undefined;
  faceTowards(entity, player.location);
  tryRun(() => entity.setDynamicProperty("hl:director", true));
  const s = { entity, kind, playerId: player.id, mode, seen: 0, unseen: 0, age: 0, timer: 0, ...extra };
  tracked.set(entity.id, s);
  if (def.modes?.includes(mode)) setMode(s, mode);
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
  if (!entity) return;
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
  return allFor(player).some((s) => s.major || s.hunting);
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
  const kind = kindOf(entity);
  const s = { entity, kind, playerId: nearest.id, mode: "idle", seen: 0, unseen: 0, age: 0, timer: 0, adopted: true };
  tracked.set(entity.id, s);
  tryRun(() => entity.setDynamicProperty("hl:director", true));
  const mode = DEFS[kind]?.adopt?.(entity, s, nearest);
  if (mode) setMode(s, mode);
  return s;
}

// Every 4 ticks.
export function tickCreatures() {
  const dimension = world.getDimension(OVERWORLD);
  const players = world.getAllPlayers().filter((p) => p.dimension.id === OVERWORLD);
  const alive = new Set();
  for (const [type, kind] of Object.entries(TYPE_KIND)) {
    const def = DEFS[kind];
    if (def.ownTick) continue; // manages its own entity
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
      if (s.frozen) continue; // a face held still for a jumpscare
      tryRun(() => def.tick?.(e, s, player));
    }
  }
  for (const [id, s] of [...tracked.entries()]) if (!alive.has(id) && !DEFS[s.kind]?.ownTick) tracked.delete(id);
}
