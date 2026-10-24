/**
 * Identifiers shared between the TypeScript layer and the pack JSON.
 *
 * Every string here also appears in behavior_pack/ or resource_pack/ JSON.
 * tests/packs.test.ts checks that the two sides agree, so a rename that only
 * touches one side fails the test suite instead of failing silently in-game.
 */

export const NAMESPACE = "aoc";

export const ENTITY = {
  spearman: "aoc:spearman",
  rallyMarker: "aoc:rally_marker",
} as const;

export const ITEM = {
  commanderMap: "aoc:commander_map",
} as const;

/** Custom item component registered from script (Custom Components V2). */
export const ITEM_COMPONENT = {
  commanderMap: "aoc:commander_map",
} as const;

/** Data-driven entity events declared in the unit/marker entity files. */
export const ENTITY_EVENT = {
  orderMove: "aoc:order_move",
  orderIdle: "aoc:order_idle",
  markerExpire: "aoc:expire",
} as const;

/** Entity properties (declared under description.properties). */
export const ENTITY_PROPERTY = {
  /** int: 0 = unaligned, otherwise FactionDef.propertyIndex. Client-synced for tunic colour. */
  faction: "aoc:faction",
  /** enum: "idle" | "move". Set by the order events. */
  order: "aoc:order",
} as const;

export const ORDER_STATE = {
  idle: "idle",
  move: "move",
} as const;

/** Families used by entity filters and script queries. */
export const FAMILY = {
  unit: "aoc_unit",
  rallyMarker: "aoc_rally_marker",
} as const;

/** Tags. Faction and channel tags are what the entity filters test against. */
export const TAG = {
  unit: "aoc_unit",
  factionPrefix: "aoc_f_",
  channelPrefix: "aoc_ch_",
} as const;

/** Dynamic property keys. */
export const DP = {
  /** world: chunk count of the serialized SaveData; chunks live under `aoc:save#N` (see SaveManager). */
  worldSave: "aoc:save",
  /** player: faction key. */
  playerFaction: "aoc:faction",
  /** player: true once the starter Commander Map was handed out. */
  playerGotMap: "aoc:got_map",
  /** unit: owning player id. */
  unitOwner: "aoc:owner",
  /** unit: owning player display name, for UI only. */
  unitOwnerName: "aoc:owner_name",
  /** unit: faction key. */
  unitFaction: "aoc:faction",
  /** marker: id of the order that spawned it. */
  markerOrder: "aoc:order_id",
} as const;

export const COMMAND = {
  status: "aoc:status",
  debug: "aoc:debug",
  debugActionEnum: "aoc:debug_action",
} as const;

export function factionTag(factionKey: string): string {
  return `${TAG.factionPrefix}${factionKey}`;
}

export function channelTag(channel: number): string {
  return `${TAG.channelPrefix}${channel}`;
}
