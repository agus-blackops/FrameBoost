import { world } from "@minecraft/server";
import { TYPES, distance, nearby } from "./util.js";

const RANGE = 32;
const REVEAL_DIST = 10; // invisible until this close
const DARKNESS_DIST = 8;
const WHISPER_DIST = 12;

/** Unseen until close; smothers nearby players in darkness and whispers. */
export function updateWhisperers(tick) {
    const nearest = new Map(); // whisperer id -> { entity, d }
    for (const player of world.getAllPlayers()) {
        let closest = Infinity;
        for (const w of nearby(player, TYPES.whisperer, RANGE)) {
            const d = distance(player.location, w.location);
            closest = Math.min(closest, d);
            const entry = nearest.get(w.id);
            if (!entry || d < entry.d) nearest.set(w.id, { entity: w, d });
        }
        try {
            if (closest < DARKNESS_DIST && tick % 40 === 0) {
                player.addEffect("darkness", 60, { showParticles: false });
            }
            if (closest < WHISPER_DIST && tick % 80 === 0) {
                player.playSound("mob.vex.ambient", { volume: 0.8, pitch: 0.4 + Math.random() * 0.2 });
            }
        } catch {
            // player left
        }
    }
    for (const { entity, d } of nearest.values()) {
        try {
            if (d > REVEAL_DIST) entity.addEffect("invisibility", 15, { showParticles: false });
            else entity.removeEffect("invisibility");
        } catch {
            // entity unloaded
        }
    }
}
