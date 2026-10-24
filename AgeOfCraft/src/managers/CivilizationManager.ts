import { world, type Entity, type Player } from "@minecraft/server";
import { DEFAULT_RELATION, FACTIONS, findFaction, type FactionDef, type Relation } from "../data/factions";
import { DP, factionTag } from "../data/ids";
import type { Logger } from "../core/log";

/**
 * Faction membership. A player's faction lives in a player dynamic property
 * (persisted with the player in this world) and is mirrored to a tag, because
 * entity JSON filters can test tags but not dynamic properties.
 */
export class CivilizationManager {
  constructor(private readonly log: Logger) {}

  get factions(): readonly FactionDef[] {
    return FACTIONS;
  }

  factionOf(player: Player): FactionDef | undefined {
    const key = player.getDynamicProperty(DP.playerFaction);
    return typeof key === "string" ? findFaction(key) : undefined;
  }

  /** Faction of any entity carrying a faction tag (players and units). */
  factionOfEntity(entity: Entity): FactionDef | undefined {
    return FACTIONS.find((f) => entity.hasTag(factionTag(f.key)));
  }

  join(player: Player, key: string): FactionDef | undefined {
    const faction = findFaction(key);
    if (!faction) return undefined;
    player.setDynamicProperty(DP.playerFaction, faction.key);
    this.syncTags(player);
    this.log.info(`${player.name} joined ${faction.name}`);
    return faction;
  }

  /** Re-applies the faction tag from the dynamic property, e.g. after /tag edits or on join. */
  syncTags(player: Player): void {
    const faction = this.factionOf(player);
    for (const f of FACTIONS) {
      const tag = factionTag(f.key);
      if (f === faction) {
        if (!player.hasTag(tag)) player.addTag(tag);
      } else if (player.hasTag(tag)) {
        player.removeTag(tag);
      }
    }
  }

  relation(a: FactionDef, b: FactionDef): Relation {
    return a.key === b.key ? "ally" : DEFAULT_RELATION;
  }

  onlineMembers(key: string): Player[] {
    return world.getAllPlayers().filter((p) => this.factionOf(p)?.key === key);
  }
}
