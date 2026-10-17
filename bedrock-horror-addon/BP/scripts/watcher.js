import { world } from "@minecraft/server";
import { TYPES, distance, inOverworld, isNight, isWatching, nearby, spotAround, trySpawn } from "./util.js";

const RANGE = 96;
const VANISH_DIST = 14; // walk toward it and it is gone
const STARE_TICKS = 40; // hold its gaze for 2 s and it is gone
const LIFETIME = 1200; // ticks
const SPAWN_CHANCE = 0.25;

const born = new Map(); // watcher id -> tick first seen
const stare = new Map(); // watcher id -> ticks watched in a row

/** A silhouette that stands far away and stares; it never attacks. */
export function updateWatchers(tick) {
    const seen = new Map(); // id -> { entity, nearest, watched, players }
    for (const player of world.getAllPlayers()) {
        for (const w of nearby(player, TYPES.watcher, RANGE)) {
            const e = seen.get(w.id) ?? { entity: w, nearest: Infinity, watched: false, players: [] };
            e.nearest = Math.min(e.nearest, distance(player.location, w.location));
            e.watched = e.watched || isWatching(player, w, 0.97);
            e.players.push(player);
            seen.set(w.id, e);
        }
    }
    for (const [id, e] of seen) {
        if (!born.has(id)) born.set(id, tick);
        const staredFor = e.watched ? (stare.get(id) ?? 0) + 5 : 0;
        stare.set(id, staredFor);
        if (e.nearest < VANISH_DIST || staredFor >= STARE_TICKS || tick - born.get(id) > LIFETIME) {
            vanish(id, e);
        }
    }
    for (const id of born.keys()) {
        if (!seen.has(id)) {
            born.delete(id);
            stare.delete(id);
        }
    }
}

function vanish(id, e) {
    for (const player of e.players) {
        try {
            player.playSound("mob.enderman.portal", { volume: 0.5, pitch: 0.5 });
        } catch {
            // player left
        }
    }
    try {
        e.entity.triggerEvent("horror:vanish");
    } catch {
        // already gone
    }
    born.delete(id);
    stare.delete(id);
}

/** Place a Watcher 30-40 blocks from the player if none is already around. */
export function spawnWatcherNear(player) {
    if (!inOverworld(player) || nearby(player, TYPES.watcher, RANGE).length > 0) return;
    const spot = spotAround(player, 30, 40);
    if (spot) trySpawn(player, TYPES.watcher, spot);
}

/** At night, on the surface, now and then someone is watching. */
export function maybeSpawnWatchers() {
    if (!isNight()) return;
    for (const player of world.getAllPlayers()) {
        if (player.location.y > 55 && Math.random() < SPAWN_CHANCE) spawnWatcherNear(player);
    }
}
