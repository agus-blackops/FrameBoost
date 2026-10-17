import { world } from "@minecraft/server";

export const TYPES = {
    stalker: "horror:stalker",
    woodbeast: "horror:woodbeast",
    whisperer: "horror:whisperer",
    watcher: "horror:watcher",
};
export const HOSTILE_TYPES = [TYPES.stalker, TYPES.woodbeast, TYPES.whisperer];

export function distance(a, b) {
    return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
}

export function isNight() {
    const time = world.getTimeOfDay();
    return time >= 13000 && time <= 23000;
}

export function inOverworld(player) {
    return player.dimension.id === "minecraft:overworld";
}

/** Entities of `type` within `radius` of `player`; [] if the area is not loaded. */
export function nearby(player, type, radius) {
    try {
        return player.dimension.getEntities({ type, location: player.location, maxDistance: radius });
    } catch {
        return [];
    }
}

/** True if `player` has a clear line of sight to `entity` inside a view cone (cosine of half-angle). */
export function isWatching(player, entity, cone = 0.8) {
    const eye = player.getHeadLocation();
    const target = entity.getHeadLocation();
    const dx = target.x - eye.x;
    const dy = target.y - eye.y;
    const dz = target.z - eye.z;
    const dist = Math.hypot(dx, dy, dz);
    if (dist < 0.001) return true;

    const view = player.getViewDirection();
    if ((view.x * dx + view.y * dy + view.z * dz) / dist < cone) return false;

    try {
        const hit = player.dimension.getBlockFromRay(eye, { x: dx / dist, y: dy / dist, z: dz / dist }, {
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

/** Nearest standable spot (solid floor, 3 air blocks) around (x, y, z), scanning vertically. */
export function findGround(dimension, x, y, z) {
    const bx = Math.floor(x);
    const bz = Math.floor(z);
    const top = Math.floor(y) + 8;
    try {
        for (let by = top; by >= top - 20; by--) {
            const floor = dimension.getBlock({ x: bx, y: by - 1, z: bz });
            if (!floor || floor.isAir || floor.isLiquid) continue;
            let clear = true;
            for (let h = 0; h < 3 && clear; h++) {
                clear = dimension.getBlock({ x: bx, y: by + h, z: bz })?.isAir ?? false;
            }
            if (clear) return { x: bx + 0.5, y: by, z: bz + 0.5 };
        }
    } catch {
        // chunk not loaded
    }
    return undefined;
}

/** A random standable spot on a ring `minDist`..`maxDist` blocks around `player`. */
export function spotAround(player, minDist, maxDist) {
    for (let attempt = 0; attempt < 6; attempt++) {
        const angle = Math.random() * Math.PI * 2;
        const d = minDist + Math.random() * (maxDist - minDist);
        const spot = findGround(
            player.dimension,
            player.location.x + Math.cos(angle) * d,
            player.location.y,
            player.location.z + Math.sin(angle) * d,
        );
        if (spot) return spot;
    }
    return undefined;
}

export function trySpawn(player, type, spot) {
    try {
        return player.dimension.spawnEntity(type, spot);
    } catch {
        return undefined;
    }
}
