import { GameMode, world, type Player } from "@minecraft/server";
import type { GameConfig } from "../config/gameConfig";
import { GATHER_RULES, RESOURCES, resourceName, type GatherRule } from "../data/resources";
import { BoundedSet } from "../core/limits";
import { Treasury } from "../core/treasury";
import type { Logger } from "../core/log";
import type { CivilizationManager } from "./CivilizationManager";
import type { SaveManager } from "./SaveManager";

/**
 * Faction stockpiles and the Milestone 1 gathering mechanic: a player who
 * breaks a log adds wood to their faction's treasury.
 */
export class EconomyManager {
  private readonly ruleByBlock = new Map<string, GatherRule>();
  /** Positions of resource blocks placed by players, so place-and-break does not farm resources. */
  private readonly placed: BoundedSet<string>;
  private readonly warnedNoFaction = new Set<string>();

  constructor(
    private readonly config: GameConfig,
    private readonly log: Logger,
    private readonly save: SaveManager,
    private readonly civ: CivilizationManager,
  ) {
    for (const rule of GATHER_RULES) {
      if (!RESOURCES.find((r) => r.key === rule.resource)?.enabled) continue;
      for (const id of rule.blockTypes) this.ruleByBlock.set(id, rule);
    }
    this.placed = new BoundedSet(config.placedBlockMemory);
  }

  treasury(factionKey: string): Treasury | undefined {
    const fs = this.save.data.factions[factionKey];
    return fs ? new Treasury(fs.treasury, () => this.save.markDirty()) : undefined;
  }

  subscribe(): void {
    world.afterEvents.playerPlaceBlock.subscribe((ev) => {
      if (this.ruleByBlock.has(ev.block.typeId)) this.placed.add(posKey(ev.block.dimension.id, ev.block.location));
    });

    world.afterEvents.playerBreakBlock.subscribe((ev) => {
      const rule = this.ruleByBlock.get(ev.brokenBlockPermutation.type.id);
      if (!rule) return;
      const key = posKey(ev.dimension.id, ev.block.location);
      if (this.placed.delete(key)) return;
      this.gather(ev.player, rule);
    });
  }

  private gather(player: Player, rule: GatherRule): void {
    if (!this.config.gatherInCreative && player.getGameMode() === GameMode.Creative) return;
    const faction = this.civ.factionOf(player);
    if (!faction) {
      if (!this.warnedNoFaction.has(player.id)) {
        this.warnedNoFaction.add(player.id);
        player.sendMessage("§7Join a faction with the Commander Map to start collecting resources.");
      }
      return;
    }
    const treasury = this.treasury(faction.key);
    if (!treasury) return;
    const total = treasury.add(rule.resource, rule.amount);
    player.onScreenDisplay.setActionBar(`§a+${rule.amount} ${resourceName(rule.resource)}§r  (${faction.chatColor}${faction.name}§r: ${total})`);
    this.log.debug(`${player.name} gathered ${rule.amount} ${rule.resource} for ${faction.key} (now ${total})`);
  }
}

function posKey(dimensionId: string, loc: { x: number; y: number; z: number }): string {
  return `${dimensionId}|${loc.x}|${loc.y}|${loc.z}`;
}
