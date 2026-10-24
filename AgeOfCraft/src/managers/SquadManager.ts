import type { Player } from "@minecraft/server";
import { ENTITY_PROPERTY, ORDER_STATE } from "../data/ids";
import { distance } from "../core/vec";
import type { SaveManager } from "./SaveManager";
import type { UnitManager } from "./UnitManager";

export interface SquadSummary {
  /** Living units per the save, including those in unloaded chunks. */
  total: number;
  /** Units currently loaded in the world. */
  loaded: number;
  /** Loaded units within NEARBY_RADIUS of the commander. */
  nearby: number;
  /** Loaded units currently executing a move order. */
  moving: number;
}

const NEARBY_RADIUS = 32;

/**
 * Milestone 1 has exactly one squad per player: every unit they own. Orders
 * address squads through this class so that named, multiple squads
 * (Milestone 3) only change this module.
 */
export class SquadManager {
  constructor(
    private readonly save: SaveManager,
    private readonly units: UnitManager,
  ) {}

  summary(player: Player): SquadSummary {
    const units = this.units.ownedUnits(player.id);
    let nearby = 0;
    let moving = 0;
    for (const u of units) {
      if (u.dimension.id === player.dimension.id && distance(u.location, player.location) <= NEARBY_RADIUS) nearby++;
      if (u.getProperty(ENTITY_PROPERTY.order) === ORDER_STATE.move) moving++;
    }
    return {
      total: this.save.data.players[player.id]?.unitsAlive ?? 0,
      loaded: units.length,
      nearby,
      moving,
    };
  }
}
