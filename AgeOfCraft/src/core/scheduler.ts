export interface ScheduledTask {
  readonly name: string;
  readonly intervalTicks: number;
  readonly phase: number;
  readonly run: (tick: number) => void;
}

export interface SchedulerHooks {
  /** Wall-clock milliseconds; injected so tests can control time. */
  now: () => number;
  onTaskTimed?: (name: string, elapsedMs: number) => void;
  onTaskError?: (name: string, error: unknown) => void;
}

/**
 * Runs periodic work from a single per-tick callback.
 *
 * Tasks with the same interval are given different phases so that, for
 * example, the order update and the save flush do not land on the same tick.
 * One task throwing does not stop the others.
 */
export class Scheduler {
  private readonly tasks: ScheduledTask[] = [];

  constructor(private readonly hooks: SchedulerHooks) {}

  add(name: string, intervalTicks: number, run: (tick: number) => void, phase?: number): void {
    if (!Number.isInteger(intervalTicks) || intervalTicks <= 0) {
      throw new Error(`task ${name}: interval must be a positive integer, got ${intervalTicks}`);
    }
    const autoPhase = (this.tasks.length * 3) % intervalTicks;
    this.tasks.push({ name, intervalTicks, phase: (phase ?? autoPhase) % intervalTicks, run });
  }

  tick(currentTick: number): void {
    for (const task of this.tasks) {
      if ((currentTick + task.phase) % task.intervalTicks !== 0) continue;
      const start = this.hooks.now();
      try {
        task.run(currentTick);
      } catch (e) {
        this.hooks.onTaskError?.(task.name, e);
      }
      this.hooks.onTaskTimed?.(task.name, this.hooks.now() - start);
    }
  }

  list(): readonly ScheduledTask[] {
    return this.tasks;
  }
}
