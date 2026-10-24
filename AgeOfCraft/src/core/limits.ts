/** Per-key cooldowns measured in game ticks. Used to drop duplicate menu presses. */
export class Cooldowns {
  private readonly lastUse = new Map<string, number>();

  /** True (and records the use) if `key` has not been used within `cooldownTicks`. */
  tryUse(key: string, tick: number, cooldownTicks: number): boolean {
    const last = this.lastUse.get(key);
    if (last !== undefined && tick - last < cooldownTicks) return false;
    this.lastUse.set(key, tick);
    return true;
  }

  clear(key: string): void {
    this.lastUse.delete(key);
  }
}

/** A set that forgets its oldest entries past `capacity`. */
export class BoundedSet<T> {
  private readonly items = new Set<T>();

  constructor(readonly capacity: number) {}

  add(item: T): void {
    this.items.delete(item);
    this.items.add(item);
    while (this.items.size > this.capacity) {
      const oldest = this.items.values().next();
      if (oldest.done) break;
      this.items.delete(oldest.value);
    }
  }

  has(item: T): boolean {
    return this.items.has(item);
  }

  delete(item: T): boolean {
    return this.items.delete(item);
  }

  get size(): number {
    return this.items.size;
  }
}

export interface CapCheck {
  ok: boolean;
  reason?: string;
}

/** Recruitment limit check, kept pure so the rules are testable. */
export function checkUnitCaps(
  playerUnits: number,
  factionUnits: number,
  supply: number,
  caps: { perPlayer: number; perFaction: number },
): CapCheck {
  if (playerUnits + supply > caps.perPlayer) return { ok: false, reason: `You command the maximum of ${caps.perPlayer} units.` };
  if (factionUnits + supply > caps.perFaction) return { ok: false, reason: `Your faction has reached its limit of ${caps.perFaction} units.` };
  return { ok: true };
}
