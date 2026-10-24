import {
  CommandPermissionLevel,
  CustomCommandParamType,
  CustomCommandStatus,
  ItemStack,
  Player,
  system,
  type CustomCommandOrigin,
  type CustomCommandRegistry,
  type CustomCommandResult,
} from "@minecraft/server";
import { COMMAND, ITEM } from "../data/ids";
import type { Logger } from "../core/log";
import type { CivilizationManager } from "./CivilizationManager";
import type { CommandSystem } from "./CommandSystem";
import type { EconomyManager } from "./EconomyManager";
import type { PerformanceMonitor } from "./PerformanceMonitor";
import type { SaveManager } from "./SaveManager";
import type { SquadManager } from "./SquadManager";
import type { UnitManager } from "./UnitManager";

export const DEBUG_ACTIONS = ["on", "off", "perf", "recount", "cleanup", "givemap", "wood", "reset"] as const;
type DebugAction = (typeof DEBUG_ACTIONS)[number];

export interface DebugDeps {
  log: Logger;
  save: SaveManager;
  civ: CivilizationManager;
  economy: EconomyManager;
  units: UnitManager;
  squads: SquadManager;
  commands: CommandSystem;
  perf: PerformanceMonitor;
}

/**
 * Slash commands for testing:
 *   /aoc:status          anyone: your faction, resources and squad
 *   /aoc:debug <action>  operators: logging, perf report, repairs, test resources
 *
 * Command callbacks run in a read-only context, so all work is deferred to
 * the next tick with system.run.
 */
export function registerDebugCommands(registry: CustomCommandRegistry, deps: DebugDeps): void {
  registry.registerCommand(
    {
      name: COMMAND.status,
      description: "Show your Age Of Craft faction, resources and squad",
      permissionLevel: CommandPermissionLevel.Any,
      cheatsRequired: false,
    },
    (origin) => {
      const player = playerOf(origin);
      if (!player) return fail("Run this as a player.");
      system.run(() => status(player, deps));
      return ok();
    },
  );

  registry.registerEnum(COMMAND.debugActionEnum, [...DEBUG_ACTIONS]);
  registry.registerCommand(
    {
      name: COMMAND.debug,
      description: "Age Of Craft debugging tools",
      permissionLevel: CommandPermissionLevel.Admin,
      cheatsRequired: false,
      mandatoryParameters: [{ type: CustomCommandParamType.Enum, name: COMMAND.debugActionEnum }],
    },
    (origin, action: DebugAction) => {
      const player = playerOf(origin);
      system.run(() => runDebug(action, player, deps));
      return ok();
    },
  );
}

function status(player: Player, d: DebugDeps): void {
  const faction = d.civ.factionOf(player);
  if (!faction) {
    player.sendMessage("You have not joined a faction. Use the Commander Map.");
    return;
  }
  const t = d.economy.treasury(faction.key);
  const s = d.squads.summary(player);
  player.sendMessage(
    `${faction.chatColor}${faction.name}§r | Wood ${t?.get("wood") ?? 0} | units ${s.total} (loaded ${s.loaded}, nearby ${s.nearby}, moving ${s.moving})`,
  );
}

function runDebug(action: DebugAction, player: Player | undefined, d: DebugDeps): void {
  const say = (msg: string) => (player ? player.sendMessage(`§7[aoc] ${msg}`) : d.log.info(msg));
  switch (action) {
    case "on":
    case "off":
      d.log.debugEnabled = action === "on";
      d.perf.reset();
      say(`debug logging ${action}`);
      return;
    case "perf":
      for (const line of d.perf.report()) say(line);
      d.perf.logReport();
      return;
    case "recount":
      d.units.recount();
      say(`unit counts rebuilt from ${d.units.loadedCount} loaded units`);
      return;
    case "cleanup":
      say(`removed ${d.commands.removeOrphanMarkers()} orphaned rally markers`);
      return;
    case "givemap":
      if (!player) return say("run as a player");
      player.getComponent("minecraft:inventory")?.container.addItem(new ItemStack(ITEM.commanderMap, 1));
      say("Commander Map given");
      return;
    case "wood": {
      const faction = player ? d.civ.factionOf(player) : undefined;
      if (!faction) return say("join a faction first");
      say(`${faction.name} wood: ${d.economy.treasury(faction.key)?.add("wood", 100)}`);
      return;
    }
    case "reset":
      d.save.reset();
      say("strategic save data reset (units in the world are unchanged; run recount)");
      return;
  }
}

function playerOf(origin: CustomCommandOrigin): Player | undefined {
  const e = origin.initiator ?? origin.sourceEntity;
  return e instanceof Player ? e : undefined;
}

function ok(): CustomCommandResult {
  return { status: CustomCommandStatus.Success };
}

function fail(message: string): CustomCommandResult {
  return { status: CustomCommandStatus.Failure, message };
}
