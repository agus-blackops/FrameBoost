import { FACTIONS } from "../data/factions";
import { emptyAmounts, sanitizeAmounts, type ResourceAmounts } from "./treasury";

export const SAVE_VERSION = 1;

export interface FactionSave {
  treasury: ResourceAmounts;
  /** Living units across the faction, for the faction cap. */
  unitsAlive: number;
}

export interface PlayerSave {
  /** Last known name, for UI and logs only; identity is the map key. */
  name: string;
  /** Living units owned. Kept here because units in unloaded chunks cannot be counted. */
  unitsAlive: number;
}

export interface SaveData {
  version: number;
  factions: Record<string, FactionSave>;
  players: Record<string, PlayerSave>;
}

export function newSave(): SaveData {
  const factions: Record<string, FactionSave> = {};
  for (const f of FACTIONS) factions[f.key] = { treasury: emptyAmounts(), unitsAlive: 0 };
  return { version: SAVE_VERSION, factions, players: {} };
}

function nonNegativeInt(n: unknown): number {
  return typeof n === "number" && Number.isFinite(n) && n > 0 ? Math.floor(n) : 0;
}

export interface ParseResult {
  data: SaveData;
  /** Set when the stored value was present but unusable and defaults were substituted. */
  warning?: string;
}

/**
 * Parses whatever is stored, never throws. Unknown factions are dropped,
 * missing ones are created, and numbers are clamped, so a hand-edited or
 * truncated save degrades to defaults instead of breaking the add-on.
 */
export function parseSave(raw: string | undefined): ParseResult {
  if (raw === undefined || raw === "") return { data: newSave() };
  let obj: unknown;
  try {
    obj = JSON.parse(raw);
  } catch {
    return { data: newSave(), warning: "save data was not valid JSON; starting fresh" };
  }
  if (!obj || typeof obj !== "object") return { data: newSave(), warning: "save data had the wrong shape; starting fresh" };
  const rec = obj as Record<string, unknown>;
  const version = typeof rec.version === "number" ? rec.version : 0;
  if (version > SAVE_VERSION) {
    return { data: newSave(), warning: `save version ${version} is newer than this add-on (${SAVE_VERSION}); starting fresh` };
  }

  const data = newSave();
  const factions = rec.factions && typeof rec.factions === "object" ? (rec.factions as Record<string, unknown>) : {};
  for (const f of FACTIONS) {
    const fs = factions[f.key];
    if (fs && typeof fs === "object") {
      const fr = fs as Record<string, unknown>;
      data.factions[f.key] = { treasury: sanitizeAmounts(fr.treasury), unitsAlive: nonNegativeInt(fr.unitsAlive) };
    }
  }
  const players = rec.players && typeof rec.players === "object" ? (rec.players as Record<string, unknown>) : {};
  for (const [id, ps] of Object.entries(players)) {
    if (!ps || typeof ps !== "object") continue;
    const p = ps as Record<string, unknown>;
    data.players[id] = {
      name: typeof p.name === "string" ? p.name.slice(0, 64) : "",
      unitsAlive: nonNegativeInt(p.unitsAlive),
    };
  }
  return { data };
}

export function serializeSave(data: SaveData): string {
  return JSON.stringify(data);
}

/**
 * Bedrock limits a single string dynamic property (32767 characters at the
 * time of writing), so large saves are split across numbered keys.
 */
export const CHUNK_SIZE = 30_000;

export function splitChunks(s: string, size = CHUNK_SIZE): string[] {
  if (s.length === 0) return [""];
  const out: string[] = [];
  for (let i = 0; i < s.length; i += size) out.push(s.slice(i, i + size));
  return out;
}
