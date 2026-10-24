export interface TaskStat {
  readonly name: string;
  readonly calls: number;
  readonly totalMs: number;
  readonly maxMs: number;
  readonly avgMs: number;
}

/**
 * Per-task timing. Script time is measured with Date.now(), which has 1 ms
 * resolution in Bedrock, so short tasks mostly read 0: treat these figures as
 * "did anything get expensive", not as a profiler.
 */
export class PerfStats {
  private readonly stats = new Map<string, { calls: number; totalMs: number; maxMs: number }>();

  record(name: string, elapsedMs: number): void {
    const s = this.stats.get(name) ?? { calls: 0, totalMs: 0, maxMs: 0 };
    s.calls++;
    s.totalMs += elapsedMs;
    if (elapsedMs > s.maxMs) s.maxMs = elapsedMs;
    this.stats.set(name, s);
  }

  snapshot(): TaskStat[] {
    return [...this.stats.entries()]
      .map(([name, s]) => ({ name, calls: s.calls, totalMs: s.totalMs, maxMs: s.maxMs, avgMs: s.calls ? s.totalMs / s.calls : 0 }))
      .sort((a, b) => b.totalMs - a.totalMs);
  }

  reset(): void {
    this.stats.clear();
  }
}

/**
 * Estimates server ticks per second from (tick, wall-clock) samples. Bedrock
 * targets 20 TPS; sustained lower values mean the simulation is overloaded.
 */
export class TpsEstimator {
  private lastTick: number | undefined;
  private lastMs: number | undefined;
  private current: number | undefined;

  sample(tick: number, nowMs: number): number | undefined {
    if (this.lastTick !== undefined && this.lastMs !== undefined && nowMs > this.lastMs) {
      this.current = ((tick - this.lastTick) * 1000) / (nowMs - this.lastMs);
    }
    this.lastTick = tick;
    this.lastMs = nowMs;
    return this.current;
  }

  get tps(): number | undefined {
    return this.current;
  }
}
