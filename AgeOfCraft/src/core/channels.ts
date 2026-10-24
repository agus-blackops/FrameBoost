/**
 * Hands out move-order channels.
 *
 * Entity filters cannot compare one entity's tag with another's, so the
 * spearman JSON lists a fixed set of pairs: "I have aoc_ch_N and the target
 * has aoc_ch_N". A move order borrows one N for its lifetime.
 */
export class ChannelAllocator {
  private readonly used: boolean[];

  constructor(readonly count: number) {
    if (!Number.isInteger(count) || count <= 0) throw new Error(`channel count must be a positive integer, got ${count}`);
    this.used = new Array<boolean>(count).fill(false);
  }

  acquire(): number | undefined {
    const free = this.used.indexOf(false);
    if (free < 0) return undefined;
    this.used[free] = true;
    return free;
  }

  release(channel: number): void {
    if (channel >= 0 && channel < this.count) this.used[channel] = false;
  }

  isUsed(channel: number): boolean {
    return this.used[channel] === true;
  }

  get freeCount(): number {
    return this.used.filter((u) => !u).length;
  }
}
