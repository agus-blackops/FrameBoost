// Runs BP/scripts against a fake @minecraft/server to catch runtime errors.
// Usage: node tools/smoke_test.mjs
import { cpSync, mkdtempSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const work = mkdtempSync(join(tmpdir(), "horror-smoke-"));
cpSync(join(root, "BP", "scripts"), work, { recursive: true });
for (const f of readdirSync(work)) {
    const p = join(work, f);
    writeFileSync(p, readFileSync(p, "utf8").replaceAll('"@minecraft/server"', '"./mock_server.js"'));
}

writeFileSync(join(work, "mock_server.js"), `
export const log = [];
let intervals = [], timeouts = [], time = 13500;
const subs = {};
const sub = (name) => ({ subscribe(fn) { subs[name] = fn; } });
export const handlers = subs;

class Entity {
  constructor(typeId, location, dimension) {
    this.typeId = typeId; this.location = location; this.dimension = dimension;
    this.id = String(Math.random()); this.effects = new Set();
  }
  getHeadLocation() { return { ...this.location, y: this.location.y + 1.6 }; }
  addEffect(id) { this.effects.add(id); log.push("effect:" + id); }
  removeEffect(id) { this.effects.delete(id); }
  teleport(loc) { this.location = loc; log.push("teleport:" + this.typeId); }
  triggerEvent(e) { log.push("event:" + e); this.dimension.entities = this.dimension.entities.filter((x) => x !== this); }
}
class Player extends Entity {
  constructor(dim, view) {
    super("minecraft:player", { x: 0, y: 70, z: 0 }, dim);
    this.view = view;
    this.onScreenDisplay = { setTitle: (t) => log.push("title"), setActionBar: () => log.push("actionbar") };
  }
  getViewDirection() { return this.view; }
  playSound(s) { log.push("sound:" + s); }
  runCommandAsync(c) { log.push("cmd:" + c); return Promise.resolve(); }
}
class Dimension {
  constructor() { this.id = "minecraft:overworld"; this.entities = []; }
  getBlock({ y }) { const solid = y < 64; return { isAir: !solid, isLiquid: false }; }
  getBlockFromRay() { return undefined; }
  getEntities({ type, location, maxDistance }) {
    return this.entities.filter((e) => e.typeId === type &&
      Math.hypot(e.location.x - location.x, e.location.y - location.y, e.location.z - location.z) <= maxDistance);
  }
  spawnEntity(type, loc) { const e = new Entity(type, loc, this); this.entities.push(e); log.push("spawn:" + type); return e; }
}

export const dimension = new Dimension();
export const players = [new Player(dimension, { x: 0, y: 0, z: 1 }), new Player(dimension, { x: 1, y: 0, z: 0 })];
export const world = {
  getAllPlayers: () => players,
  getTimeOfDay: () => time,
  setTimeOfDay: (t) => { time = t; },
  getAbsoluteTime: () => 6 * 24000 + time,
  afterEvents: { entityHurt: sub("entityHurt"), playerSpawn: sub("playerSpawn") },
};
export const system = {
  currentTick: 0,
  runInterval(fn, every) { intervals.push({ fn, every }); },
  runTimeout(fn, delay) { timeouts.push({ fn, at: this.currentTick + delay }); },
  afterEvents: { scriptEventReceive: sub("scriptEventReceive") },
  advance(ticks) {
    for (let i = 0; i < ticks; i++) {
      this.currentTick++;
      for (const t of intervals) if (this.currentTick % t.every === 0) t.fn();
      for (const t of timeouts.filter((t) => t.at <= this.currentTick)) t.fn();
      timeouts = timeouts.filter((t) => t.at > this.currentTick);
    }
  },
  setDay(day) { time = day ? 1000 : 13500; },
};
`);

const mock = await import(pathToFileURL(join(work, "mock_server.js")));
await import(pathToFileURL(join(work, "main.js")));
const { system, dimension, players, handlers, log } = mock;

// One of each creature: a stalker in front of player 0 (watched), one far behind, etc.
dimension.spawnEntity("horror:stalker", { x: 0, y: 64, z: 10 });
dimension.spawnEntity("horror:stalker", { x: 0, y: 64, z: -40 });
dimension.spawnEntity("horror:whisperer", { x: 5, y: 64, z: 0 });
dimension.spawnEntity("horror:whisperer", { x: 20, y: 64, z: 0 });
dimension.spawnEntity("horror:woodbeast", { x: -8, y: 64, z: 0 });
dimension.spawnEntity("horror:watcher", { x: 0, y: 64, z: 60 });

system.advance(2400); // two minutes of night, the 7th night => Blood Night
handlers.entityHurt({ hurtEntity: players[0], damageSource: { damagingEntity: { typeId: "horror:stalker" } } });
handlers.scriptEventReceive({ id: "horror:watcher", sourceEntity: players[1] });
handlers.scriptEventReceive({ id: "horror:bloodnight" });
system.setDay(true);
system.advance(200); // dawn ends the Blood Night

const has = (prefix) => log.some((l) => l.startsWith(prefix));
const expectations = {
  "stalker frozen when watched": has("effect:slowness"),
  "stalker sprints when unseen": has("effect:speed"),
  "heartbeat plays": has("sound:mob.warden.heartbeat"),
  "whisperer darkness": has("effect:darkness"),
  "whisperer hides when far": has("effect:invisibility"),
  "watcher vanishes": has("event:horror:vanish"),
  "jumpscare title": has("title"),
  "blood night fog pushed": has("cmd:fog @s push horror:blood_fog"),
  "blood night spawns": log.filter((l) => l.startsWith("spawn:")).length > 5,
  "blood night ends": has("cmd:fog @s remove"),
  "ambience plays": log.some((l) => l.startsWith("sound:") && !l.includes("heartbeat")),
};
let failed = 0;
for (const [name, ok] of Object.entries(expectations)) {
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}`);
  if (!ok) failed++;
}
process.exit(failed ? 1 : 0);
