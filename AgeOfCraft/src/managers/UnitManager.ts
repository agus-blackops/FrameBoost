import {
  EntityInitializationCause,
  system,
  world,
  type Entity,
  type Player,
  type Vector3,
} from "@minecraft/server";
import type { GameConfig } from "../config/gameConfig";
import { findFaction, type FactionDef } from "../data/factions";
import { DP, ENTITY_PROPERTY, FAMILY, TAG, factionTag } from "../data/ids";
import { UNIT_TYPE_IDS, findUnit, type UnitDef } from "../data/units";
import { Cooldowns, checkUnitCaps } from "../core/limits";
import { formatCost } from "../core/treasury";
import type { Logger } from "../core/log";
import type { CivilizationManager } from "./CivilizationManager";
import type { EconomyManager } from "./EconomyManager";
import type { SaveManager } from "./SaveManager";

export interface UnitRecord {
  readonly id: string;
  readonly typeId: string;
  ownerId: string | undefined;
  ownerName: string | undefined;
  factionKey: string | undefined;
}

export interface RecruitResult {
  ok: boolean;
  message: string;
}

const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];

/**
 * Tracks every loaded unit and owns recruitment.
 *
 * The registry only contains units in loaded chunks; Bedrock does not expose
 * unloaded entities. Unit counts for caps are therefore kept in the save and
 * adjusted on recruitment and death.
 */
export class UnitManager {
  private readonly registry = new Map<string, UnitRecord>();
  private readonly cooldowns = new Cooldowns();
  private readonly unitTypes = new Set(UNIT_TYPE_IDS);
  private readonly availableListeners: ((unit: Entity, record: UnitRecord) => void)[] = [];
  private readonly lostListeners: ((unitId: string) => void)[] = [];

  constructor(
    private readonly config: GameConfig,
    private readonly log: Logger,
    private readonly save: SaveManager,
    private readonly civ: CivilizationManager,
    private readonly economy: EconomyManager,
  ) {}

  /** Called whenever a unit enters the registry (spawn, chunk load, startup scan). */
  onUnitAvailable(listener: (unit: Entity, record: UnitRecord) => void): void {
    this.availableListeners.push(listener);
  }

  /** Called when a unit dies or unloads. */
  onUnitLost(listener: (unitId: string) => void): void {
    this.lostListeners.push(listener);
  }

  get loadedCount(): number {
    return this.registry.size;
  }

  record(id: string): UnitRecord | undefined {
    return this.registry.get(id);
  }

  subscribe(): void {
    const entityTypes = [...this.unitTypes];

    world.afterEvents.entitySpawn.subscribe((ev) => {
      if (!this.unitTypes.has(ev.entity.typeId)) return;
      const unit = ev.entity;
      if (ev.cause === EntityInitializationCause.Spawned && unit.getDynamicProperty(DP.unitOwner) === undefined) {
        // Spawn egg or /summon: hand the unit to whoever is standing next to it.
        system.run(() => this.adoptStray(unit));
        return;
      }
      this.register(unit);
    });

    world.afterEvents.entityLoad.subscribe((ev) => {
      if (this.unitTypes.has(ev.entity.typeId)) this.register(ev.entity);
    });

    world.afterEvents.entityDie.subscribe(
      (ev) => {
        const rec = this.registry.get(ev.deadEntity.id);
        this.registry.delete(ev.deadEntity.id);
        if (rec) this.countDeath(rec);
        for (const l of this.lostListeners) l(ev.deadEntity.id);
      },
      { entityTypes },
    );

    world.afterEvents.entityRemove.subscribe(
      (ev) => {
        // Fires for unloads too, so it must not touch the living-unit counts.
        if (this.registry.delete(ev.removedEntityId)) for (const l of this.lostListeners) l(ev.removedEntityId);
      },
      { entityTypes },
    );

    // Friendly fire: cancel damage between members of the same faction.
    world.beforeEvents.entityHurt.subscribe(
      (ev) => {
        const attacker = ev.damageSource.damagingEntity;
        if (!attacker) return;
        const victim = this.civ.factionOfEntity(ev.hurtEntity);
        if (victim && attacker.hasTag(factionTag(victim.key))) ev.cancel = true;
      },
      { entityFilter: { families: [FAMILY.unit] } },
    );
  }

  /** Startup pass: registers units that loaded before the event subscriptions existed. */
  scanLoaded(): number {
    let n = 0;
    for (const dimId of DIMENSIONS) {
      try {
        for (const type of this.unitTypes) {
          for (const e of world.getDimension(dimId).getEntities({ type })) {
            this.register(e);
            n++;
          }
        }
      } catch (e) {
        this.log.warn(`unit scan of ${dimId} failed: ${String(e)}`);
      }
    }
    return n;
  }

  /** Loaded, valid units owned by a player. Bounded by the registry size. */
  ownedUnits(playerId: string): Entity[] {
    const out: Entity[] = [];
    for (const rec of this.registry.values()) {
      if (rec.ownerId !== playerId) continue;
      const e = world.getEntity(rec.id);
      if (e?.isValid) out.push(e);
    }
    return out;
  }

  recruit(player: Player, def: UnitDef, tick: number): RecruitResult {
    const faction = this.civ.factionOf(player);
    if (!faction) return { ok: false, message: "Join a faction first." };
    if (!this.cooldowns.tryUse(`recruit:${player.id}`, tick, this.config.recruitCooldownTicks)) {
      return { ok: false, message: "Recruitment already in progress." };
    }
    const ps = this.save.player(player.id, player.name);
    const fs = this.save.data.factions[faction.key];
    if (!fs) return { ok: false, message: "Faction data is missing." };
    const cap = checkUnitCaps(ps.unitsAlive, fs.unitsAlive, def.supply, {
      perPlayer: this.config.unitCapPerPlayer,
      perFaction: this.config.unitCapPerFaction,
    });
    if (!cap.ok) return { ok: false, message: cap.reason ?? "Unit cap reached." };

    const treasury = this.economy.treasury(faction.key);
    if (!treasury) return { ok: false, message: "Faction data is missing." };
    if (!treasury.spend(def.cost)) {
      return { ok: false, message: `Not enough resources: need ${formatCost(treasury.shortfall(def.cost))} more.` };
    }

    let unit: Entity;
    try {
      unit = player.dimension.spawnEntity(def.entityId, this.spawnPointNear(player), { initialPersistence: true });
    } catch (e) {
      treasury.refund(def.cost);
      this.log.warn(`spawn of ${def.entityId} failed: ${String(e)}`);
      return { ok: false, message: "No room to recruit here. Resources refunded." };
    }

    this.configure(unit, player, faction);
    ps.unitsAlive += def.supply;
    fs.unitsAlive += def.supply;
    this.save.markDirty();
    this.log.debug(`${player.name} recruited ${def.entityId} (${unit.id})`);
    return { ok: true, message: `${def.name} recruited for ${formatCost(def.cost)}.` };
  }

  /** Recomputes saved unit counts from the loaded registry (debug; ignores unloaded units). */
  recount(): void {
    for (const p of Object.values(this.save.data.players)) p.unitsAlive = 0;
    for (const f of Object.values(this.save.data.factions)) f.unitsAlive = 0;
    for (const rec of this.registry.values()) {
      const supply = findUnit(rec.typeId)?.supply ?? 1;
      if (rec.ownerId) this.save.player(rec.ownerId, rec.ownerName ?? "").unitsAlive += supply;
      const fs = rec.factionKey ? this.save.data.factions[rec.factionKey] : undefined;
      if (fs) fs.unitsAlive += supply;
    }
    this.save.markDirty();
  }

  private register(unit: Entity): void {
    if (!unit.isValid) return;
    const owner = unit.getDynamicProperty(DP.unitOwner);
    const ownerName = unit.getDynamicProperty(DP.unitOwnerName);
    const faction = unit.getDynamicProperty(DP.unitFaction);
    const rec: UnitRecord = {
      id: unit.id,
      typeId: unit.typeId,
      ownerId: typeof owner === "string" ? owner : undefined,
      ownerName: typeof ownerName === "string" ? ownerName : undefined,
      factionKey: typeof faction === "string" ? faction : undefined,
    };
    this.registry.set(unit.id, rec);
    for (const l of this.availableListeners) l(unit, rec);
  }

  private configure(unit: Entity, owner: Player, faction: FactionDef): void {
    const def = findUnit(unit.typeId);
    unit.setDynamicProperties({
      [DP.unitOwner]: owner.id,
      [DP.unitOwnerName]: owner.name,
      [DP.unitFaction]: faction.key,
    });
    unit.setProperty(ENTITY_PROPERTY.faction, faction.propertyIndex);
    unit.addTag(TAG.unit);
    unit.addTag(factionTag(faction.key));
    unit.nameTag = `${faction.chatColor}${def?.name ?? "Unit"}§r (${owner.name})`;
    this.register(unit);
  }

  private adoptStray(unit: Entity): void {
    if (!unit.isValid) return;
    const [player] = unit.dimension.getPlayers({
      location: unit.location,
      maxDistance: this.config.eggOwnerSearchRadius,
      closest: 1,
    });
    const faction = player ? this.civ.factionOf(player) : undefined;
    if (!player || !faction) {
      this.register(unit);
      return;
    }
    this.configure(unit, player, faction);
    const supply = findUnit(unit.typeId)?.supply ?? 1;
    this.save.player(player.id, player.name).unitsAlive += supply;
    const fs = this.save.data.factions[faction.key];
    if (fs) fs.unitsAlive += supply;
    this.save.markDirty();
    player.sendMessage(`§7Spawned unit assigned to you (${faction.chatColor}${faction.name}§7).`);
  }

  private countDeath(rec: UnitRecord): void {
    const supply = findUnit(rec.typeId)?.supply ?? 1;
    const ps = rec.ownerId ? this.save.data.players[rec.ownerId] : undefined;
    if (ps) ps.unitsAlive = Math.max(0, ps.unitsAlive - supply);
    const fs = rec.factionKey && findFaction(rec.factionKey) ? this.save.data.factions[rec.factionKey] : undefined;
    if (fs) fs.unitsAlive = Math.max(0, fs.unitsAlive - supply);
    this.save.markDirty();
  }

  /** Two blocks in front of the player if that space is open, otherwise at the player's feet. */
  private spawnPointNear(player: Player): Vector3 {
    const dir = player.getViewDirection();
    const len = Math.hypot(dir.x, dir.z) || 1;
    const p = player.location;
    const candidate = { x: p.x + (dir.x / len) * 2, y: p.y, z: p.z + (dir.z / len) * 2 };
    try {
      const feet = player.dimension.getBlock(candidate);
      const head = feet?.above();
      if (feet?.isAir && head?.isAir) return candidate;
    } catch {
      // Unloaded or out of bounds: fall through.
    }
    return p;
  }
}
