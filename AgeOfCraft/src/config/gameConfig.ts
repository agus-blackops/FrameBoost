/**
 * Tunables for the strategy layer. Gameplay data (units, factions, resources)
 * lives in src/data; this file holds limits and scheduling that the world
 * owner may want to adjust for their hardware.
 *
 * Values that must agree with entity JSON (follow range, channel count) are
 * checked by tests/packs.test.ts.
 */
export interface GameConfig {
  /** Verbose logging and periodic performance reports in the content log. */
  readonly debug: boolean;

  /** Maximum living units one player may own. */
  readonly unitCapPerPlayer: number;
  /** Maximum living units across a whole faction. */
  readonly unitCapPerFaction: number;

  /** Furthest a move order may target, measured from the commanding player. */
  readonly maxOrderDistance: number;
  /**
   * Simultaneous move orders, world-wide. Each order needs one channel tag
   * pair in the spearman's entity filters, so this must match the JSON.
   */
  readonly moveChannelCount: number;
  /** An order is abandoned after this many ticks, arrived or not. */
  readonly orderTimeoutTicks: number;
  /** How often active orders are checked for arrival. */
  readonly orderUpdateIntervalTicks: number;
  /** Arrival radius for a single unit; grows with squad size. */
  readonly arrivalRadiusBase: number;
  /** Upper bound on unit lookups per order update, to cap per-tick cost. */
  readonly maxUnitChecksPerUpdate: number;

  /** Ticks between save flushes (only written when something changed). */
  readonly saveIntervalTicks: number;
  /** Minimum ticks between two recruitments by the same player. */
  readonly recruitCooldownTicks: number;
  /** Minimum ticks between two orders by the same player. */
  readonly orderCooldownTicks: number;

  /** Give each player a Commander Map the first time they join. */
  readonly giveCommanderMapOnFirstJoin: boolean;
  /** Breaking blocks in creative mode still yields resources (useful for testing). */
  readonly gatherInCreative: boolean;
  /** How many player-placed resource blocks are remembered to stop place/break farming. */
  readonly placedBlockMemory: number;
  /** A spawn-egg unit is given to the nearest player within this radius. */
  readonly eggOwnerSearchRadius: number;

  /** Ticks between periodic performance reports while debug is on. */
  readonly perfReportIntervalTicks: number;
}

export const DEFAULT_CONFIG: GameConfig = {
  debug: false,

  unitCapPerPlayer: 10,
  unitCapPerFaction: 20,

  maxOrderDistance: 48,
  moveChannelCount: 8,
  orderTimeoutTicks: 60 * 20,
  orderUpdateIntervalTicks: 10,
  arrivalRadiusBase: 2.5,
  maxUnitChecksPerUpdate: 32,

  saveIntervalTicks: 100,
  recruitCooldownTicks: 10,
  orderCooldownTicks: 10,

  giveCommanderMapOnFirstJoin: true,
  gatherInCreative: true,
  placedBlockMemory: 1024,
  eggOwnerSearchRadius: 6,

  perfReportIntervalTicks: 200,
};
