import { system, type Player } from "@minecraft/server";
import { ActionFormData, FormCancelationReason, type ActionFormResponse } from "@minecraft/server-ui";
import type { GameConfig } from "../config/gameConfig";
import { RESOURCES } from "../data/resources";
import { UNITS } from "../data/units";
import { formatCost } from "../core/treasury";
import type { Logger } from "../core/log";
import type { CivilizationManager } from "./CivilizationManager";
import type { CommandResult, CommandSystem } from "./CommandSystem";
import type { EconomyManager } from "./EconomyManager";
import type { SaveManager } from "./SaveManager";
import type { SquadManager } from "./SquadManager";
import type { UnitManager } from "./UnitManager";

interface MenuAction {
  label: string;
  run: () => void | Promise<void>;
}

/**
 * The Commander Map interface, built only from @minecraft/server-ui forms.
 *
 * Forms are large, list-style menus that work with a controller (D-pad + A),
 * touch and mouse alike. True RTS drag-selection of units in the world is not
 * possible with the supported API, so the squad is addressed through menus
 * and the player's aim.
 */
export class UIManager {
  private readonly open = new Set<string>();

  constructor(
    private readonly config: GameConfig,
    private readonly log: Logger,
    private readonly save: SaveManager,
    private readonly civ: CivilizationManager,
    private readonly economy: EconomyManager,
    private readonly units: UnitManager,
    private readonly squads: SquadManager,
    private readonly commands: CommandSystem,
  ) {}

  async openCommanderMap(player: Player): Promise<void> {
    if (this.open.has(player.id)) return;
    this.open.add(player.id);
    try {
      if (!this.civ.factionOf(player)) {
        const joined = await this.factionPicker(player);
        if (!joined) return;
      }
      await this.mainMenu(player);
    } catch (e) {
      this.log.error("commander map failed", e);
      player.sendMessage("§cThe Commander Map hit an error; see the content log.");
    } finally {
      this.open.delete(player.id);
    }
  }

  private async mainMenu(player: Player): Promise<void> {
    const faction = this.civ.factionOf(player);
    if (!faction) return;
    const treasury = this.economy.treasury(faction.key);
    const squad = this.squads.summary(player);
    const orders = this.commands.ordersOf(player.id);

    const stock = RESOURCES.filter((r) => r.enabled)
      .map((r) => `${r.name}: §e${treasury?.get(r.key) ?? 0}§r`)
      .join("   ");
    const orderLine =
      orders.length > 0
        ? `Order: marching (${orders.reduce((n, o) => n + o.arrivedCount, 0)}/${orders.reduce((n, o) => n + o.initialCount, 0)} arrived)`
        : "Order: holding position";
    const body = [
      `${faction.chatColor}${faction.name}§r`,
      stock,
      `Units: ${squad.total}/${this.config.unitCapPerPlayer}  (nearby ${squad.nearby}, moving ${squad.moving})`,
      orderLine,
      "",
      "§7Tip: aim at a spot before opening the map, then pick \"Move to target\".",
    ].join("\n");

    const tick = () => system.currentTick;
    const actions: MenuAction[] = [
      { label: "Move to target\n§8where you are aiming", run: () => this.report(player, this.commands.moveToLookTarget(player, tick())) },
      { label: "Rally to me\n§8gather at your position", run: () => this.report(player, this.commands.moveToPlayer(player, tick())) },
      { label: "Halt\n§8cancel current orders", run: () => this.report(player, this.commands.halt(player)) },
      ...UNITS.map(
        (def): MenuAction => ({
          label: `Recruit ${def.name}\n§8${formatCost(def.cost)}`,
          run: () => this.report(player, this.units.recruit(player, def, tick())),
        }),
      ),
      { label: "Faction", run: () => this.factionInfo(player) },
    ];

    const form = new ActionFormData().title("Commander Map").body(body);
    for (const a of actions) form.button(a.label);
    const res = await this.show(player, form);
    if (!res || res.canceled || res.selection === undefined) return;
    await actions[res.selection]?.run();
  }

  private async factionPicker(player: Player): Promise<boolean> {
    const factions = this.civ.factions;
    const form = new ActionFormData()
      .title("Choose your faction")
      .body("Allies share one treasury. Units of other factions are hostile.\n\nYou can only switch later while you command no units.");
    for (const f of factions) {
      const online = this.civ.onlineMembers(f.key).length;
      form.button(`${f.chatColor}${f.name}§r\n§8${online} player${online === 1 ? "" : "s"} online`);
    }
    const res = await this.show(player, form);
    if (!res || res.canceled || res.selection === undefined) return false;
    const chosen = factions[res.selection];
    if (!chosen) return false;
    this.civ.join(player, chosen.key);
    this.save.player(player.id, player.name);
    player.sendMessage(`You joined ${chosen.chatColor}${chosen.name}§r.`);
    return true;
  }

  private async factionInfo(player: Player): Promise<void> {
    const faction = this.civ.factionOf(player);
    if (!faction) return;
    const fs = this.save.data.factions[faction.key];
    const members = this.civ.onlineMembers(faction.key).map((p) => p.name);
    const others = this.civ.factions
      .filter((f) => f.key !== faction.key)
      .map((f) => `${f.chatColor}${f.name}§r: ${this.civ.relation(faction, f)}`);
    const body = [
      `${faction.chatColor}${faction.name}§r`,
      `Online: ${members.join(", ") || "none"}`,
      `Faction units: ${fs?.unitsAlive ?? 0}/${this.config.unitCapPerFaction}`,
      "",
      ...others,
    ].join("\n");
    const ownsUnits = (this.save.data.players[player.id]?.unitsAlive ?? 0) > 0;
    const form = new ActionFormData().title("Faction").body(body).button("Back");
    if (!ownsUnits) form.button("Switch faction");
    const res = await this.show(player, form);
    if (!res || res.canceled) return;
    if (res.selection === 0) return this.mainMenu(player);
    if (res.selection === 1 && !ownsUnits) {
      if (await this.factionPicker(player)) return this.mainMenu(player);
    }
  }

  private report(player: Player, result: CommandResult): void {
    player.sendMessage(result.ok ? `§a${result.message}` : `§c${result.message}`);
  }

  /** Shows a form, retrying while the player is busy (for example, chat is open). */
  private async show(player: Player, form: ActionFormData): Promise<ActionFormResponse | undefined> {
    for (let attempt = 0; attempt < 10; attempt++) {
      if (!player.isValid) return undefined;
      const res = await form.show(player);
      if (!(res.canceled && res.cancelationReason === FormCancelationReason.UserBusy)) return res;
      await system.waitTicks(5);
    }
    return undefined;
  }
}
