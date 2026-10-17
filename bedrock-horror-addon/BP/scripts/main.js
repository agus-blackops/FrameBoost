import { system, world } from "@minecraft/server";
import { playAmbience, playHeartbeats } from "./ambience.js";
import {
    bloodNightSpawns,
    forceBloodNight,
    isBloodNight,
    registerBloodNightEvents,
    updateBloodNight,
} from "./bloodnight.js";
import { registerStalkerJumpscare, updateStalkers } from "./stalker.js";
import { maybeSpawnWatchers, spawnWatcherNear, updateWatchers } from "./watcher.js";
import { updateWhisperers } from "./whisperer.js";

const STEP = 5; // ticks between updates
let tick = 0;

registerStalkerJumpscare();
registerBloodNightEvents();

system.runInterval(() => {
    tick += STEP;
    updateStalkers();
    updateWhisperers(tick);
    updateWatchers(tick);
    if (tick % 30 === 0) playHeartbeats();
    if (tick % 100 === 0) updateBloodNight();
    if (tick % 200 === 0) bloodNightSpawns();
    if (tick % 400 === 0) playAmbience(isBloodNight());
    if (tick % 600 === 0) maybeSpawnWatchers();
}, STEP);

// Test commands: /scriptevent horror:bloodnight   /scriptevent horror:watcher
system.afterEvents.scriptEventReceive.subscribe((event) => {
    if (event.id === "horror:bloodnight") {
        forceBloodNight();
    } else if (event.id === "horror:watcher") {
        const source = event.sourceEntity;
        const players = source?.typeId === "minecraft:player" ? [source] : world.getAllPlayers();
        for (const player of players) spawnWatcherNear(player);
    }
});
