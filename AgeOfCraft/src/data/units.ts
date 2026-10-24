import { ENTITY } from "./ids";
import type { Cost } from "./resources";

export type Era = "dark" | "feudal" | "castle" | "imperial" | "modern";
export type UnitRole = "worker" | "infantry" | "ranged" | "cavalry" | "siege" | "vehicle";

/**
 * Script-side view of a unit type. Combat stats are authored in the entity
 * JSON (that is what the game simulates); they are repeated here for UI and
 * future AI evaluation, and tests/packs.test.ts fails if the two drift apart.
 */
export interface UnitDef {
  readonly entityId: string;
  readonly name: string;
  readonly era: Era;
  readonly role: UnitRole;
  readonly cost: Cost;
  /** Population used against unit caps. */
  readonly supply: number;
  readonly maxHealth: number;
  readonly meleeDamage: number;
  readonly movementSpeed: number;
}

export const UNITS: readonly UnitDef[] = [
  {
    entityId: ENTITY.spearman,
    name: "Spearman",
    era: "dark",
    role: "infantry",
    cost: { wood: 15 },
    supply: 1,
    maxHealth: 24,
    meleeDamage: 4,
    movementSpeed: 0.3,
  },
];

export const UNIT_TYPE_IDS: readonly string[] = UNITS.map((u) => u.entityId);

export function findUnit(entityId: string): UnitDef | undefined {
  return UNITS.find((u) => u.entityId === entityId);
}
