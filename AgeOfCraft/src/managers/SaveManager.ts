import { world } from "@minecraft/server";
import { DP } from "../data/ids";
import { newSave, parseSave, serializeSave, splitChunks, type PlayerSave, type SaveData } from "../core/saveSchema";
import type { Logger } from "../core/log";

/**
 * Persists strategic state in world dynamic properties, the supported
 * per-world storage for Script API add-ons. State is written on a timer
 * when dirty rather than on every change.
 *
 * Layout: `aoc:save` holds the chunk count as a number; `aoc:save#N` holds
 * the N-th piece of the JSON string.
 */
export class SaveManager {
  private state: SaveData = newSave();
  private dirty = false;
  private loaded = false;

  constructor(private readonly log: Logger) {}

  get data(): SaveData {
    return this.state;
  }

  load(): void {
    let raw: string | undefined;
    try {
      const count = world.getDynamicProperty(DP.worldSave);
      if (typeof count === "number" && count > 0) {
        const parts: string[] = [];
        for (let i = 0; i < count; i++) {
          const part = world.getDynamicProperty(`${DP.worldSave}#${i}`);
          if (typeof part !== "string") throw new Error(`missing save chunk ${i} of ${count}`);
          parts.push(part);
        }
        raw = parts.join("");
      }
    } catch (e) {
      this.log.error("could not read save data; starting fresh", e);
    }
    const result = parseSave(raw);
    if (result.warning) this.log.warn(result.warning);
    this.state = result.data;
    this.loaded = true;
    this.log.info(`save loaded (${Object.keys(this.state.players).length} players known)`);
  }

  markDirty(): void {
    this.dirty = true;
  }

  flushIfDirty(): void {
    if (this.dirty) this.flush();
  }

  flush(): void {
    if (!this.loaded) return;
    try {
      const chunks = splitChunks(serializeSave(this.state));
      const previous = world.getDynamicProperty(DP.worldSave);
      chunks.forEach((c, i) => world.setDynamicProperty(`${DP.worldSave}#${i}`, c));
      // Remove chunks left over from a longer previous save.
      if (typeof previous === "number") {
        for (let i = chunks.length; i < previous; i++) world.setDynamicProperty(`${DP.worldSave}#${i}`, undefined);
      }
      world.setDynamicProperty(DP.worldSave, chunks.length);
      this.dirty = false;
    } catch (e) {
      this.log.error("save failed; will retry", e);
    }
  }

  player(id: string, name: string): PlayerSave {
    let p = this.state.players[id];
    if (!p) {
      p = { name, unitsAlive: 0 };
      this.state.players[id] = p;
      this.markDirty();
    } else if (name && p.name !== name) {
      p.name = name;
      this.markDirty();
    }
    return p;
  }

  /** Clears all strategic state (debug command). Units already in the world are untouched. */
  reset(): void {
    this.state = newSave();
    this.markDirty();
    this.flush();
  }
}
