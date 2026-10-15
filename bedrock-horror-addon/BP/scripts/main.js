import { world, system } from "@minecraft/server";

const STALKER = "horror:stalker";
const HORROR_TYPES = ["horror:stalker", "horror:woodbeast", "horror:whisperer"];

const STALK_RANGE = 64; // blocks: how far a Stalker reacts to being watched
const WATCH_COS = 0.8; // ~37 degree half-cone counts as "looking at it"
const HEARTBEAT_RANGE = 20;
const AMBIENT_SOUNDS = [
    "ambient.cave",
    "mob.ghast.moan",
    "mob.enderman.stare",
    "mob.wither.ambient",
    "mob.warden.nearby_close",
];

let tickCount = 0;

/** True if `player` has a clear line of sight to `entity` inside their view cone. */
function isWatching(player, entity) {
    const eye = player.getHeadLocation();
    const target = entity.getHeadLocation();
    const dx = target.x - eye.x;
    const dy = target.y - eye.y;
    const dz = target.z - eye.z;
    const dist = Math.hypot(dx, dy, dz);
    if (dist < 0.001) return true;

    const view = player.getViewDirection();
    if ((view.x * dx + view.y * dy + view.z * dz) / dist < WATCH_COS) return false;

    try {
        const dir = { x: dx / dist, y: dy / dist, z: dz / dist };
        const hit = player.dimension.getBlockFromRay(eye, dir, {
            maxDistance: dist,
            includeLiquidBlocks: false,
            includePassableBlocks: false,
        });
        if (hit) return false; // a block is in the way
    } catch {
        // unloaded chunk or API hiccup: treat as visible
    }
    return true;
}

/** Weeping-Angel rule: a Stalker freezes while watched and sprints when it is not. */
function updateStalkers() {
    const watched = new Map(); // entity id -> { entity, seen }
    for (const player of world.getAllPlayers()) {
        let stalkers;
        try {
            stalkers = player.dimension.getEntities({
                type: STALKER,
                location: player.location,
                maxDistance: STALK_RANGE,
            });
        } catch {
            continue;
        }
        for (const stalker of stalkers) {
            const entry = watched.get(stalker.id) ?? { entity: stalker, seen: false };
            entry.seen = entry.seen || isWatching(player, stalker);
            watched.set(stalker.id, entry);
        }
    }
    for (const { entity, seen } of watched.values()) {
        try {
            if (seen) {
                entity.addEffect("slowness", 10, { amplifier: 255, showParticles: false });
            } else {
                entity.removeEffect("slowness");
                entity.addEffect("speed", 20, { amplifier: 2, showParticles: false });
            }
        } catch {
            // entity unloaded between query and update
        }
    }
}

/** Heartbeat that gets louder and faster the closer a creature is. */
function playHeartbeats() {
    for (const player of world.getAllPlayers()) {
        let nearest = Infinity;
        try {
            for (const type of HORROR_TYPES) {
                for (const e of player.dimension.getEntities({
                    type,
                    location: player.location,
                    maxDistance: HEARTBEAT_RANGE,
                })) {
                    nearest = Math.min(nearest, Math.hypot(
                        e.location.x - player.location.x,
                        e.location.y - player.location.y,
                        e.location.z - player.location.z,
                    ));
                }
            }
        } catch {
            continue;
        }
        if (nearest > HEARTBEAT_RANGE) continue;
        const closeness = 1 - nearest / HEARTBEAT_RANGE;
        player.playSound("mob.warden.heartbeat", {
            volume: 0.4 + closeness * 0.8,
            pitch: 0.9 + closeness * 0.5,
        });
    }
}

/** Unsettling distant sounds at night and in caves. */
function playAmbience() {
    const time = world.getTimeOfDay();
    const night = time >= 13000 && time <= 23000;
    for (const player of world.getAllPlayers()) {
        if (player.dimension.id !== "minecraft:overworld") continue;
        const inCave = player.location.y < 50;
        if (!(night || inCave) || Math.random() > 0.35) continue;
        const sound = AMBIENT_SOUNDS[Math.floor(Math.random() * AMBIENT_SOUNDS.length)];
        player.playSound(sound, { volume: 0.6, pitch: 0.6 + Math.random() * 0.4 });
    }
}

system.runInterval(() => {
    tickCount += 5;
    updateStalkers();
    if (tickCount % 30 === 0) playHeartbeats();
    if (tickCount % 400 === 0) playAmbience();
}, 5);
