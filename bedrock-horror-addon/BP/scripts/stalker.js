import { system, world } from "@minecraft/server";
import { TYPES, distance, findGround, isWatching, nearby } from "./util.js";

const RANGE = 64;
const TELEPORT_MIN_DIST = 28; // only relocates when it has fallen far behind
const TELEPORT_CHANCE = 0.02; // per update (every 5 ticks) => roughly every 12 s
const JUMPSCARE_COOLDOWN = 200; // ticks

const lastScare = new Map(); // player id -> tick

/** Weeping-Angel rule: freezes while watched, sprints and blinks behind you when not. */
export function updateStalkers() {
    const states = new Map(); // stalker id -> { entity, seen, nearest, player }
    for (const player of world.getAllPlayers()) {
        for (const stalker of nearby(player, TYPES.stalker, RANGE)) {
            const st = states.get(stalker.id) ?? { entity: stalker, seen: false, nearest: Infinity, player };
            st.seen = st.seen || isWatching(player, stalker);
            const d = distance(player.location, stalker.location);
            if (d < st.nearest) {
                st.nearest = d;
                st.player = player;
            }
            states.set(stalker.id, st);
        }
    }
    for (const st of states.values()) {
        try {
            setFrozen(st.entity, st.seen);
            if (st.seen) {
                st.entity.addEffect("slowness", 10, { amplifier: 255, showParticles: false });
                continue;
            }
            st.entity.removeEffect("slowness");
            st.entity.addEffect("speed", 20, { amplifier: 2, showParticles: false });
            if (st.nearest > TELEPORT_MIN_DIST && Math.random() < TELEPORT_CHANCE) {
                teleportBehind(st.entity, st.player);
            }
        } catch {
            // entity unloaded between query and update
        }
    }
}

/** Mirrors the freeze into an entity property so the client animation turns to stone. */
function setFrozen(stalker, frozen) {
    try {
        if (stalker.getProperty("horror:frozen") !== frozen) stalker.setProperty("horror:frozen", frozen);
    } catch {
        // property API unavailable: the freeze still works, only the pose keeps moving
    }
}

function teleportBehind(stalker, player) {
    const view = player.getViewDirection();
    const len = Math.hypot(view.x, view.z) || 1;
    const d = 14 + Math.random() * 6;
    const spot = findGround(
        player.dimension,
        player.location.x - (view.x / len) * d,
        player.location.y,
        player.location.z - (view.z / len) * d,
    );
    if (!spot) return;
    stalker.teleport(spot, { dimension: player.dimension, facingLocation: player.location });
}

/** A hit from the Stalker flashes a scream, darkness and nausea. */
export function registerStalkerJumpscare() {
    world.afterEvents.entityHurt.subscribe(
        (event) => {
            const player = event.hurtEntity;
            if (event.damageSource.damagingEntity?.typeId !== TYPES.stalker) return;
            const now = system.currentTick;
            if (now - (lastScare.get(player.id) ?? -Infinity) < JUMPSCARE_COOLDOWN) return;
            lastScare.set(player.id, now);
            try {
                player.onScreenDisplay.setTitle({ rawtext: [{ translate: "horror.jumpscare.title" }] }, {
                    fadeInDuration: 0,
                    stayDuration: 15,
                    fadeOutDuration: 10,
                    subtitle: { rawtext: [{ translate: "horror.jumpscare.subtitle" }] },
                });
                player.playSound("mob.enderman.scream", { volume: 1, pitch: 0.5 });
                player.addEffect("darkness", 80, { showParticles: false });
                player.addEffect("nausea", 100, { showParticles: false });
            } catch {
                // player left mid-event
            }
        },
        { entityTypes: ["minecraft:player"] },
    );
}
