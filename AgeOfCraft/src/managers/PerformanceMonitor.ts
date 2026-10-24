import { system } from "@minecraft/server";
import { PerfStats, TpsEstimator } from "../core/perfStats";
import type { Logger } from "../core/log";

export interface PerfCounters {
  loadedUnits: () => number;
  activeOrders: () => number;
}

/**
 * Collects per-task script timings and an estimated server TPS. Numbers are
 * coarse (1 ms clock); they exist so performance tests on Xbox and Android
 * can be repeated and compared, not to promise a frame rate.
 */
export class PerformanceMonitor {
  readonly stats = new PerfStats();
  private readonly tps = new TpsEstimator();
  private sinceMs = Date.now();

  constructor(
    private readonly log: Logger,
    private readonly counters: PerfCounters,
  ) {}

  sampleTps(): void {
    this.tps.sample(system.currentTick, Date.now());
  }

  report(): string[] {
    const secs = Math.max(1, Math.round((Date.now() - this.sinceMs) / 1000));
    const tps = this.tps.tps;
    const lines = [
      `TPS ~${tps === undefined ? "?" : tps.toFixed(1)} | units loaded ${this.counters.loadedUnits()} | orders ${this.counters.activeOrders()} | window ${secs}s`,
    ];
    for (const s of this.stats.snapshot()) {
      lines.push(`  ${s.name}: ${s.calls} runs, total ${s.totalMs} ms, max ${s.maxMs} ms`);
    }
    return lines;
  }

  logReport(): void {
    for (const line of this.report()) this.log.info(line);
  }

  reset(): void {
    this.stats.reset();
    this.sinceMs = Date.now();
  }
}
