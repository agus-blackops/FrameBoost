import { world } from "@minecraft/server";
import { HOSTILE_TYPES, TYPES, inOverworld, isNight, nearby, spotAround, trySpawn } from "./util.js";

const EVERY_N_NIGHTS = 7;
const MAX_NEARBY_HOSTILES = 5;
const SPAWN_CHANCE = 0.4;
const FOG_ID = "horror_blood";
const SPAWN_POOL = [TYPES.stalker, TYPES.woodbeast, TYPES.woodbeast];

let active = false;
let forced = false;
const fogged = new Set(); // player ids with the red fog pushed

export function isBloodNight() {
    return active;
}

function dayNumber() {
    return Math.floor(world.getAbsoluteTime() / 24000);
}

/** Every 7th night the sky bleeds: red fog and many more hunters. */
export function updateBloodNight() {
    const night = isNight();
    if (!night) forced = false;
    const shouldBeActive = night && (forced || dayNumber() % EVERY_N_NIGHTS === EVERY_N_NIGHTS - 1);
    if (shouldBeActive && !active) start();
    else if (!shouldBeActive && active) end();
    if (!active) return;
    for (const player of world.getAllPlayers()) {
        if (!fogged.has(player.id) && inOverworld(player)) {
            player.runCommandAsync(`fog @s push horror:blood_fog ${FOG_ID}`).catch(() => {});
            fogged.add(player.id);
        }
    }
}

/** Start a Blood Night right now (jumps the clock to dusk). */
export function forceBloodNight() {
    forced = true;
    world.setTimeOfDay(13000);
    updateBloodNight();
}

function start() {
    active = true;
    for (const player of world.getAllPlayers()) {
        try {
            player.onScreenDisplay.setTitle({ rawtext: [{ translate: "horror.bloodnight.title" }] }, {
                fadeInDuration: 20,
                stayDuration: 80,
                fadeOutDuration: 40,
                subtitle: { rawtext: [{ translate: "horror.bloodnight.subtitle" }] },
            });
            player.playSound("mob.wither.spawn", { volume: 0.7, pitch: 0.5 });
        } catch {
            // player left
        }
    }
}

function end() {
    active = false;
    for (const player of world.getAllPlayers()) {
        player.runCommandAsync(`fog @s remove ${FOG_ID}`).catch(() => {});
        try {
            player.onScreenDisplay.setActionBar({ rawtext: [{ translate: "horror.bloodnight.end" }] });
        } catch {
            // player left
        }
    }
    fogged.clear();
}

/** During a Blood Night, keep a few hunters closing in on every player. */
export function bloodNightSpawns() {
    if (!active) return;
    for (const player of world.getAllPlayers()) {
        if (!inOverworld(player) || Math.random() > SPAWN_CHANCE) continue;
        const count = HOSTILE_TYPES.reduce((n, type) => n + nearby(player, type, 64).length, 0);
        if (count >= MAX_NEARBY_HOSTILES) continue;
        const spot = spotAround(player, 24, 36);
        if (spot) trySpawn(player, SPAWN_POOL[Math.floor(Math.random() * SPAWN_POOL.length)], spot);
    }
}

/** Re-apply the fog to players who join or respawn during a Blood Night. */
export function registerBloodNightEvents() {
    world.afterEvents.playerSpawn.subscribe((event) => fogged.delete(event.player.id));
}
