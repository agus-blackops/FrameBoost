import { ChannelAllocator } from "./channels";
import { distance, type Vec3 } from "./vec";

export interface MoveOrder {
  readonly id: number;
  readonly ownerId: string;
  readonly dimensionId: string;
  readonly channel: number;
  readonly target: Vec3;
  readonly createdTick: number;
  /** Units ordered at creation time, for progress reporting. */
  readonly initialCount: number;
  /** Units still walking. */
  readonly pending: Set<string>;
  arrivedCount: number;
  /** Units that died, unloaded or were re-ordered before arriving. */
  lostCount: number;
  markerId: string | undefined;
}

export interface CreateOrderParams {
  ownerId: string;
  dimensionId: string;
  target: Vec3;
  unitIds: readonly string[];
  tick: number;
}

export interface CreateOrderResult {
  order: MoveOrder;
  /** Older orders that lost every pending unit to this one; the caller should finish them. */
  emptied: MoveOrder[];
}

export interface PendingCheck {
  orderId: number;
  unitId: string;
}

/**
 * Bookkeeping for move orders, independent of the game API so it can be unit
 * tested. A unit belongs to at most one order; issuing a new order to a unit
 * silently takes it out of the old one.
 */
export class OrderBook {
  private readonly orders = new Map<number, MoveOrder>();
  private readonly unitToOrder = new Map<string, number>();
  private nextId = 1;
  private cursor = 0;

  constructor(private readonly channels: ChannelAllocator) {}

  /** Returns undefined when every channel is in use. */
  create(params: CreateOrderParams): CreateOrderResult | undefined {
    const unique = [...new Set(params.unitIds)];
    if (unique.length === 0) return undefined;
    const channel = this.channels.acquire();
    if (channel === undefined) return undefined;

    const touched = new Set<MoveOrder>();
    for (const unitId of unique) {
      const prev = this.orderOf(unitId);
      if (prev) {
        prev.pending.delete(unitId);
        prev.lostCount++;
        touched.add(prev);
      }
    }

    const order: MoveOrder = {
      id: this.nextId++,
      ownerId: params.ownerId,
      dimensionId: params.dimensionId,
      channel,
      target: params.target,
      createdTick: params.tick,
      initialCount: unique.length,
      pending: new Set(unique),
      arrivedCount: 0,
      lostCount: 0,
      markerId: undefined,
    };
    this.orders.set(order.id, order);
    for (const unitId of unique) this.unitToOrder.set(unitId, order.id);

    const emptied = [...touched].filter((o) => o.pending.size === 0);
    return { order, emptied };
  }

  get(id: number): MoveOrder | undefined {
    return this.orders.get(id);
  }

  orderOf(unitId: string): MoveOrder | undefined {
    const id = this.unitToOrder.get(unitId);
    return id === undefined ? undefined : this.orders.get(id);
  }

  all(): MoveOrder[] {
    return [...this.orders.values()];
  }

  ofOwner(ownerId: string): MoveOrder[] {
    return this.all().filter((o) => o.ownerId === ownerId);
  }

  get size(): number {
    return this.orders.size;
  }

  markArrived(unitId: string): MoveOrder | undefined {
    const order = this.orderOf(unitId);
    if (!order || !order.pending.delete(unitId)) return undefined;
    order.arrivedCount++;
    this.unitToOrder.delete(unitId);
    return order;
  }

  /** The unit died, unloaded, or was halted. */
  removeUnit(unitId: string): MoveOrder | undefined {
    const order = this.orderOf(unitId);
    if (!order || !order.pending.delete(unitId)) return undefined;
    order.lostCount++;
    this.unitToOrder.delete(unitId);
    return order;
  }

  /** Removes the order and frees its channel. Returns the units that had not arrived. */
  finish(id: number): string[] {
    const order = this.orders.get(id);
    if (!order) return [];
    const stranded = [...order.pending];
    for (const unitId of stranded) {
      if (this.unitToOrder.get(unitId) === id) this.unitToOrder.delete(unitId);
    }
    order.pending.clear();
    this.orders.delete(id);
    this.channels.release(order.channel);
    return stranded;
  }

  isDone(order: MoveOrder): boolean {
    return order.pending.size === 0;
  }

  isExpired(order: MoveOrder, tick: number, timeoutTicks: number): boolean {
    return tick - order.createdTick >= timeoutTicks;
  }

  /**
   * Up to `budget` pending units across all orders, continuing where the
   * previous call stopped so large armies are checked fairly over several
   * updates instead of all at once.
   */
  nextBatch(budget: number): PendingCheck[] {
    const flat: PendingCheck[] = [];
    for (const order of this.orders.values()) {
      for (const unitId of order.pending) flat.push({ orderId: order.id, unitId });
    }
    if (flat.length === 0 || budget <= 0) return [];
    if (flat.length <= budget) {
      this.cursor = 0;
      return flat;
    }
    const start = this.cursor % flat.length;
    const out: PendingCheck[] = [];
    for (let i = 0; i < budget; i++) {
      const item = flat[(start + i) % flat.length];
      if (item) out.push(item);
    }
    this.cursor = (start + budget) % flat.length;
    return out;
  }
}

/** Units crowd around a single point, so a bigger squad needs a wider arrival circle. */
export function arrivalRadius(unitCount: number, base: number): number {
  return base + 0.75 * Math.sqrt(Math.max(0, unitCount - 1));
}

export function validateOrderTarget(commander: Vec3, target: Vec3, maxDistance: number): string | undefined {
  if (![target.x, target.y, target.z].every(Number.isFinite)) return "That location is not valid.";
  const d = distance(commander, target);
  if (d > maxDistance) return `Too far away (${Math.round(d)} blocks, limit ${maxDistance}).`;
  return undefined;
}
