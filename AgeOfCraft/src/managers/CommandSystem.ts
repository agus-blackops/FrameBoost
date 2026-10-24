import { Direction, world, type Entity, type Player, type Vector3 } from "@minecraft/server";
import type { GameConfig } from "../config/gameConfig";
import { DP, ENTITY, ENTITY_EVENT, ENTITY_PROPERTY, ORDER_STATE, channelTag } from "../data/ids";
import { ChannelAllocator } from "../core/channels";
import { Cooldowns } from "../core/limits";
import { OrderBook, arrivalRadius, validateOrderTarget, type MoveOrder } from "../core/orderBook";
import { distance, formatVec } from "../core/vec";
import type { Logger } from "../core/log";
import type { UnitManager } from "./UnitManager";

export interface CommandResult {
  ok: boolean;
  message: string;
}

const FACE_OFFSET: Record<Direction, Vector3> = {
  [Direction.Up]: { x: 0, y: 1, z: 0 },
  [Direction.Down]: { x: 0, y: -1, z: 0 },
  [Direction.North]: { x: 0, y: 0, z: -1 },
  [Direction.South]: { x: 0, y: 0, z: 1 },
  [Direction.East]: { x: 1, y: 0, z: 0 },
  [Direction.West]: { x: -1, y: 0, z: 0 },
};

const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];

type FinishReason = "arrived" | "timeout" | "halted" | "marker_lost" | "units_lost" | "superseded";

/**
 * Turns commander orders into unit behaviour.
 *
 * The Script API cannot path-find for an entity, so a move order spawns a
 * rally marker at the destination and gives the marker and the ordered units
 * a shared channel tag. The unit's "aoc:moving" behaviour targets markers on
 * its own channel, which makes the game's own navigation walk it there. This
 * system watches for arrival and returns units to idle.
 */
export class CommandSystem {
  private readonly channels: ChannelAllocator;
  private readonly book: OrderBook;
  private readonly cooldowns = new Cooldowns();

  constructor(
    private readonly config: GameConfig,
    private readonly log: Logger,
    private readonly units: UnitManager,
  ) {
    this.channels = new ChannelAllocator(config.moveChannelCount);
    this.book = new OrderBook(this.channels);
  }

  subscribe(): void {
    // A unit that reloads mid-order belongs to an order that no longer exists
    // (orders are not persisted in Milestone 1): put it back to idle.
    this.units.onUnitAvailable((unit) => {
      if (!this.book.orderOf(unit.id)) this.resetUnit(unit);
    });
    this.units.onUnitLost((unitId) => {
      this.book.removeUnit(unitId);
    });
    world.afterEvents.entityLoad.subscribe((ev) => {
      if (ev.entity.typeId === ENTITY.rallyMarker && !this.isActiveMarker(ev.entity)) ev.entity.remove();
    });
  }

  get activeOrders(): number {
    return this.book.size;
  }

  ordersOf(playerId: string): MoveOrder[] {
    return this.book.ofOwner(playerId);
  }

  /** Move to the block the player is looking at. */
  moveToLookTarget(player: Player, tick: number): CommandResult {
    const hit = player.getBlockFromViewDirection({
      maxDistance: this.config.maxOrderDistance,
      includeLiquidBlocks: true,
      includePassableBlocks: false,
    });
    if (!hit) return { ok: false, message: `Look at the ground within ${this.config.maxOrderDistance} blocks, then choose this order.` };
    const off = FACE_OFFSET[hit.face];
    const target = hit.block.offset(off)?.bottomCenter();
    if (!target) return { ok: false, message: "That location is outside the world." };
    return this.issueMove(player, target, tick);
  }

  moveToPlayer(player: Player, tick: number): CommandResult {
    return this.issueMove(player, player.location, tick);
  }

  halt(player: Player): CommandResult {
    const orders = this.book.ofOwner(player.id);
    for (const o of orders) this.finish(o, "halted");
    return orders.length > 0 ? { ok: true, message: "Squad halted." } : { ok: false, message: "Your squad has no active orders." };
  }

  issueMove(player: Player, target: Vector3, tick: number): CommandResult {
    if (!this.cooldowns.tryUse(player.id, tick, this.config.orderCooldownTicks)) {
      return { ok: false, message: "Order already sent." };
    }
    const err = validateOrderTarget(player.location, target, this.config.maxOrderDistance);
    if (err) return { ok: false, message: err };

    const dimId = player.dimension.id;
    const units = this.units.ownedUnits(player.id).filter((u) => u.dimension.id === dimId);
    if (units.length === 0) {
      return { ok: false, message: "None of your units are nearby. Units only respond while their area is loaded." };
    }

    const params = { ownerId: player.id, dimensionId: dimId, target, unitIds: units.map((u) => u.id), tick };
    let created = this.book.create(params);
    if (!created) {
      // Out of channels: the player's oldest order makes way for the new one.
      const oldest = this.book.ofOwner(player.id).sort((a, b) => a.createdTick - b.createdTick)[0];
      if (oldest) {
        this.finish(oldest, "superseded");
        created = this.book.create(params);
      }
    }
    if (!created) return { ok: false, message: "Too many orders in progress. Try again shortly." };
    for (const o of created.emptied) this.finish(o, "superseded");

    const order = created.order;
    let marker: Entity;
    try {
      marker = player.dimension.spawnEntity(ENTITY.rallyMarker, target);
    } catch (e) {
      this.book.finish(order.id);
      this.log.warn(`rally marker spawn failed: ${String(e)}`);
      return { ok: false, message: "Cannot place a rally point there." };
    }
    marker.addTag(channelTag(order.channel));
    marker.setDynamicProperty(DP.markerOrder, order.id);
    order.markerId = marker.id;

    for (const unit of units) {
      this.stripChannelTags(unit);
      unit.addTag(channelTag(order.channel));
      // Re-entering the moving state restarts its safety timer.
      if (unit.getProperty(ENTITY_PROPERTY.order) === ORDER_STATE.move) unit.triggerEvent(ENTITY_EVENT.orderIdle);
      unit.triggerEvent(ENTITY_EVENT.orderMove);
    }
    this.log.debug(`order ${order.id}: ${units.length} units -> ${formatVec(target)} on channel ${order.channel}`);
    return { ok: true, message: `${units.length} unit${units.length === 1 ? "" : "s"} moving to ${formatVec(target)}.` };
  }

  /** Periodic arrival check. Cost is bounded by maxUnitChecksPerUpdate. */
  update(tick: number): void {
    const markerPos = new Map<number, Vector3>();
    for (const order of this.book.all()) {
      if (this.book.isExpired(order, tick, this.config.orderTimeoutTicks)) {
        this.finish(order, "timeout");
        continue;
      }
      const marker = order.markerId ? world.getEntity(order.markerId) : undefined;
      if (!marker?.isValid) {
        this.finish(order, "marker_lost");
        continue;
      }
      markerPos.set(order.id, marker.location);
    }

    for (const check of this.book.nextBatch(this.config.maxUnitChecksPerUpdate)) {
      const order = this.book.get(check.orderId);
      const goal = markerPos.get(check.orderId);
      if (!order || !goal) continue;
      const unit = world.getEntity(check.unitId);
      if (!unit?.isValid) {
        this.book.removeUnit(check.unitId);
        continue;
      }
      if (unit.getProperty(ENTITY_PROPERTY.order) !== ORDER_STATE.move) {
        // The entity's own safety timer ended the move.
        this.book.removeUnit(check.unitId);
        this.stripChannelTags(unit);
        continue;
      }
      if (distance(unit.location, goal) <= arrivalRadius(order.initialCount, this.config.arrivalRadiusBase)) {
        this.book.markArrived(check.unitId);
        this.resetUnit(unit);
      }
    }

    for (const order of this.book.all()) {
      if (this.book.isDone(order)) this.finish(order, order.arrivedCount > 0 ? "arrived" : "units_lost");
    }
  }

  /** Removes rally markers that no active order owns (after a reload, or a crash). */
  removeOrphanMarkers(): number {
    let removed = 0;
    for (const dimId of DIMENSIONS) {
      try {
        for (const m of world.getDimension(dimId).getEntities({ type: ENTITY.rallyMarker })) {
          if (!this.isActiveMarker(m)) {
            m.remove();
            removed++;
          }
        }
      } catch (e) {
        this.log.warn(`marker cleanup in ${dimId} failed: ${String(e)}`);
      }
    }
    return removed;
  }

  private finish(order: MoveOrder, reason: FinishReason): void {
    const stranded = this.book.finish(order.id);
    for (const id of stranded) {
      const unit = world.getEntity(id);
      if (unit?.isValid) this.resetUnit(unit);
    }
    if (order.markerId) {
      const marker = world.getEntity(order.markerId);
      if (marker?.isValid) marker.remove();
    }
    this.log.debug(`order ${order.id} finished: ${reason} (${order.arrivedCount}/${order.initialCount} arrived)`);

    const owner = world.getAllPlayers().find((p) => p.id === order.ownerId);
    if (!owner) return;
    const progress = `${order.arrivedCount}/${order.initialCount}`;
    if (reason === "arrived") owner.onScreenDisplay.setActionBar(`§aSquad arrived (${progress}).`);
    else if (reason === "timeout") owner.onScreenDisplay.setActionBar(`§eMove order timed out: ${progress} arrived.`);
    else if (reason === "marker_lost") owner.onScreenDisplay.setActionBar("§eMove order ended: rally point unavailable.");
    else if (reason === "units_lost") owner.onScreenDisplay.setActionBar("§eMove order ended: no units left on the march.");
  }

  private resetUnit(unit: Entity): void {
    const hadChannel = this.stripChannelTags(unit);
    if (hadChannel || unit.getProperty(ENTITY_PROPERTY.order) === ORDER_STATE.move) unit.triggerEvent(ENTITY_EVENT.orderIdle);
  }

  private stripChannelTags(unit: Entity): boolean {
    let removed = false;
    for (let ch = 0; ch < this.channels.count; ch++) {
      if (unit.hasTag(channelTag(ch))) removed = unit.removeTag(channelTag(ch)) || removed;
    }
    return removed;
  }

  private isActiveMarker(marker: Entity): boolean {
    const id = marker.getDynamicProperty(DP.markerOrder);
    return typeof id === "number" && this.book.get(id)?.markerId === marker.id;
  }
}
