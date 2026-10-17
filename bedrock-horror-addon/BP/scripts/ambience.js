import { system, world } from "@minecraft/server";
import { HOSTILE_TYPES, distance, inOverworld, isNight, nearby } from "./util.js";

const HEARTBEAT_RANGE = 20;
const AMBIENT_SOUNDS = [
    "ambient.cave",
    "mob.ghast.moan",
    "mob.enderman.stare",
    "mob.wither.ambient",
    "mob.warden.nearby_close",
];

/** Heartbeat that gets louder and higher the closer a hunter is. */
export function playHeartbeats() {
    for (const player of world.getAllPlayers()) {
        let nearest = Infinity;
        for (const type of HOSTILE_TYPES) {
            for (const e of nearby(player, type, HEARTBEAT_RANGE)) {
                nearest = Math.min(nearest, distance(player.location, e.location));
            }
        }
        if (nearest > HEARTBEAT_RANGE) continue;
        const closeness = 1 - nearest / HEARTBEAT_RANGE;
        try {
            player.playSound("mob.warden.heartbeat", { volume: 0.4 + closeness * 0.8, pitch: 0.9 + closeness * 0.5 });
        } catch {
            // player left
        }
    }
}

/** Three footsteps right behind the player; nothing is there when they turn. */
function footstepsBehind(player) {
    for (let i = 0; i < 3; i++) {
        system.runTimeout(() => {
            try {
                const view = player.getViewDirection();
                const len = Math.hypot(view.x, view.z) || 1;
                const location = {
                    x: player.location.x - (view.x / len) * 3,
                    y: player.location.y,
                    z: player.location.z - (view.z / len) * 3,
                };
                player.playSound("step.gravel", { location, volume: 0.9, pitch: 0.8 });
            } catch {
                // player left
            }
        }, i * 9);
    }
}

/** Unsettling sounds at night and in caves; far more often during a Blood Night. */
export function playAmbience(bloodNight) {
    const chance = bloodNight ? 0.7 : 0.35;
    for (const player of world.getAllPlayers()) {
        if (!inOverworld(player)) continue;
        const inCave = player.location.y < 50;
        if (!(isNight() || inCave) || Math.random() > chance) continue;
        try {
            if (Math.random() < 0.3) {
                footstepsBehind(player);
            } else {
                const sound = AMBIENT_SOUNDS[Math.floor(Math.random() * AMBIENT_SOUNDS.length)];
                player.playSound(sound, { volume: 0.6, pitch: 0.6 + Math.random() * 0.4 });
            }
        } catch {
            // player left
        }
    }
}
