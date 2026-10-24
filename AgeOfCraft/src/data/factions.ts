/**
 * Faction definitions. Milestone 1 ships two; adding a third means adding an
 * entry here, raising the aoc:faction property range and adding a tunic
 * texture in the resource pack, and extending the hostility filters in the
 * spearman's entity JSON. tests/packs.test.ts checks all of these.
 */
export interface FactionDef {
  /** Stable key, used in saves, tags and dynamic properties. Never rename. */
  readonly key: string;
  readonly name: string;
  /** Minecraft formatting code used in chat and name tags. */
  readonly chatColor: string;
  /** Value written to the aoc:faction entity property; selects the tunic texture. 0 is reserved for unaligned. */
  readonly propertyIndex: number;
}

export const FACTIONS: readonly FactionDef[] = [
  { key: "aster", name: "Aster Kingdom", chatColor: "§9", propertyIndex: 1 },
  { key: "crimson", name: "Crimson Dominion", chatColor: "§c", propertyIndex: 2 },
];

export type Relation = "ally" | "enemy";

/**
 * Relations between distinct factions. Unlisted pairs are enemies. Members of
 * the same faction are always allies. The entity filters currently encode
 * "every other faction is hostile"; diplomacy arrives with Milestone 3.
 */
export const DEFAULT_RELATION: Relation = "enemy";

export function findFaction(key: string | undefined): FactionDef | undefined {
  if (key === undefined) return undefined;
  return FACTIONS.find((f) => f.key === key);
}
