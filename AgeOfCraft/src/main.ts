import { ItemStack, system, world, type Player } from "@minecraft/server";
import { DEFAULT_CONFIG } from "./config/gameConfig";
import { DP, ITEM, ITEM_COMPONENT } from "./data/ids";
import { Logger } from "./core/log";
import { Scheduler } from "./core/scheduler";
import { CivilizationManager } from "./managers/CivilizationManager";
import { CommandSystem } from "./managers/CommandSystem";
import { registerDebugCommands } from "./managers/DebugCommands";
import { EconomyManager } from "./managers/EconomyManager";
import { PerformanceMonitor } from "./managers/PerformanceMonitor";
import { SaveManager } from "./managers/SaveManager";
import { SquadManager } from "./managers/SquadManager";
import { UIManager } from "./managers/UIManager";
import { UnitManager } from "./managers/UnitManager";

// Construction touches no world state: with Script API v2 the script runs
// before the world is ready. Everything that reads the world starts in boot().
const config = DEFAULT_CONFIG;
const log = new Logger(console, config.debug);
const save = new SaveManager(log);
const civ = new CivilizationManager(log);
const economy = new EconomyManager(config, log, save, civ);
const units = new UnitManager(config, log, save, civ, economy);
const squads = new SquadManager(save, units);
const commands = new CommandSystem(config, log, units);
const ui = new UIManager(config, log, save, civ, economy, units, squads, commands);
const perf = new PerformanceMonitor(log, { loadedUnits: () => units.loadedCount, activeOrders: () => commands.activeOrders });
const scheduler = new Scheduler({
  now: () => Date.now(),
  onTaskTimed: (name, ms) => perf.stats.record(name, ms),
  onTaskError: (name, e) => log.error(`task ${name} failed`, e),
});

system.beforeEvents.startup.subscribe((ev) => {
  ev.itemComponentRegistry.registerCustomComponent(ITEM_COMPONENT.commanderMap, {
    onUse: (use) => {
      const player = use.source;
      system.run(() => void ui.openCommanderMap(player));
    },
  });
  registerDebugCommands(ev.customCommandRegistry, { log, save, civ, economy, units, squads, commands, perf });
});

world.afterEvents.worldLoad.subscribe(() => {
  try {
    boot();
  } catch (e) {
    log.error("startup failed; Age Of Craft is inactive in this session", e);
  }
});

function boot(): void {
  save.load();
  economy.subscribe();
  units.subscribe();
  commands.subscribe();

  world.afterEvents.playerSpawn.subscribe((ev) => {
    civ.syncTags(ev.player);
    save.player(ev.player.id, ev.player.name);
    if (ev.initialSpawn) giveStarterMap(ev.player);
  });
  world.beforeEvents.playerLeave.subscribe(() => {
    system.run(() => save.flushIfDirty());
  });

  for (const p of world.getAllPlayers()) civ.syncTags(p);
  const scanned = units.scanLoaded();
  const orphans = commands.removeOrphanMarkers();

  scheduler.add("orders", config.orderUpdateIntervalTicks, (tick) => commands.update(tick));
  scheduler.add("save", config.saveIntervalTicks, () => save.flushIfDirty());
  scheduler.add("tps", 20, () => perf.sampleTps());
  scheduler.add("perf-report", config.perfReportIntervalTicks, () => {
    if (log.debugEnabled) perf.logReport();
  });
  system.runInterval(() => scheduler.tick(system.currentTick), 1);

  log.info(`ready: ${scanned} loaded units registered, ${orphans} stale rally markers removed`);
}

function giveStarterMap(player: Player): void {
  if (!config.giveCommanderMapOnFirstJoin || player.getDynamicProperty(DP.playerGotMap) === true) return;
  const container = player.getComponent("minecraft:inventory")?.container;
  if (!container || container.emptySlotsCount === 0) return;
  container.addItem(new ItemStack(ITEM.commanderMap, 1));
  player.setDynamicProperty(DP.playerGotMap, true);
  player.sendMessage("§6Age Of Craft:§r use the §eCommander Map§r to choose a faction and command your troops.");
}
