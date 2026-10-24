/**
 * Resource types and gathering rules.
 *
 * All four Age-style resources exist in the save format from the start so the
 * schema does not change later, but only those with `enabled: true` are shown
 * or gatherable. Milestone 1 enables wood only.
 */
export type ResourceKey = "wood" | "food" | "stone" | "gold";

export interface ResourceDef {
  readonly key: ResourceKey;
  readonly name: string;
  readonly enabled: boolean;
}

export const RESOURCES: readonly ResourceDef[] = [
  { key: "wood", name: "Wood", enabled: true },
  { key: "food", name: "Food", enabled: false },
  { key: "stone", name: "Stone", enabled: false },
  { key: "gold", name: "Gold", enabled: false },
];

export const RESOURCE_KEYS: readonly ResourceKey[] = RESOURCES.map((r) => r.key);

export type Cost = Partial<Record<ResourceKey, number>>;

export interface GatherRule {
  readonly resource: ResourceKey;
  readonly amount: number;
  /** Exact block type ids. A Set lookup per break event, no pattern matching. */
  readonly blockTypes: readonly string[];
}

const WOOD_SPECIES = [
  "oak",
  "spruce",
  "birch",
  "jungle",
  "acacia",
  "dark_oak",
  "mangrove",
  "cherry",
  "pale_oak",
] as const;
const NETHER_STEMS = ["crimson", "warped"] as const;

function woodBlocks(): string[] {
  const ids: string[] = [];
  for (const s of WOOD_SPECIES) {
    for (const form of ["log", "wood"]) {
      ids.push(`minecraft:${s}_${form}`, `minecraft:stripped_${s}_${form}`);
    }
  }
  for (const s of NETHER_STEMS) {
    for (const form of ["stem", "hyphae"]) {
      ids.push(`minecraft:${s}_${form}`, `minecraft:stripped_${s}_${form}`);
    }
  }
  ids.push("minecraft:bamboo_block", "minecraft:stripped_bamboo_block");
  return ids;
}

export const GATHER_RULES: readonly GatherRule[] = [{ resource: "wood", amount: 1, blockTypes: woodBlocks() }];

export function resourceName(key: ResourceKey): string {
  return RESOURCES.find((r) => r.key === key)?.name ?? key;
}
