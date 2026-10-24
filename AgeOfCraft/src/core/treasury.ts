import { RESOURCE_KEYS, resourceName, type Cost, type ResourceKey } from "../data/resources";

/** Hard ceiling so a corrupted or exploited save cannot grow without bound. */
export const MAX_RESOURCE_AMOUNT = 1_000_000;

export type ResourceAmounts = Record<ResourceKey, number>;

export function emptyAmounts(): ResourceAmounts {
  const out = {} as ResourceAmounts;
  for (const k of RESOURCE_KEYS) out[k] = 0;
  return out;
}

function clampAmount(n: unknown): number {
  if (typeof n !== "number" || !Number.isFinite(n)) return 0;
  return Math.max(0, Math.min(MAX_RESOURCE_AMOUNT, Math.floor(n)));
}

/** Copies only known resource keys, coercing junk to 0. Used when loading saves. */
export function sanitizeAmounts(raw: unknown): ResourceAmounts {
  const out = emptyAmounts();
  if (raw && typeof raw === "object") {
    const rec = raw as Record<string, unknown>;
    for (const k of RESOURCE_KEYS) out[k] = clampAmount(rec[k]);
  }
  return out;
}

function costEntries(cost: Cost): [ResourceKey, number][] {
  const out: [ResourceKey, number][] = [];
  for (const k of RESOURCE_KEYS) {
    const v = cost[k];
    if (v !== undefined && v > 0) out.push([k, v]);
  }
  return out;
}

/**
 * A faction's stockpile. Mutations go through this class so that spending is
 * all-or-nothing and amounts stay integral and bounded.
 */
export class Treasury {
  constructor(
    private readonly amounts: ResourceAmounts,
    private readonly onChange: () => void = () => {},
  ) {}

  get(key: ResourceKey): number {
    return this.amounts[key];
  }

  /** Adds a positive integer amount; returns the new total. */
  add(key: ResourceKey, amount: number): number {
    if (!Number.isFinite(amount) || amount <= 0) return this.amounts[key];
    this.amounts[key] = clampAmount(this.amounts[key] + Math.floor(amount));
    this.onChange();
    return this.amounts[key];
  }

  canAfford(cost: Cost): boolean {
    return costEntries(cost).every(([k, v]) => this.amounts[k] >= v);
  }

  /** Deducts the whole cost, or nothing. */
  spend(cost: Cost): boolean {
    if (!this.canAfford(cost)) return false;
    for (const [k, v] of costEntries(cost)) this.amounts[k] -= v;
    this.onChange();
    return true;
  }

  refund(cost: Cost): void {
    for (const [k, v] of costEntries(cost)) this.amounts[k] = clampAmount(this.amounts[k] + v);
    this.onChange();
  }

  /** Resources still needed to afford `cost`, empty if affordable. */
  shortfall(cost: Cost): Cost {
    const out: Cost = {};
    for (const [k, v] of costEntries(cost)) {
      if (this.amounts[k] < v) out[k] = v - this.amounts[k];
    }
    return out;
  }
}

export function formatCost(cost: Cost): string {
  const parts = costEntries(cost).map(([k, v]) => `${v} ${resourceName(k)}`);
  return parts.length > 0 ? parts.join(", ") : "free";
}
