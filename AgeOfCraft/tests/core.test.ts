import { describe, expect, it } from "vitest";
import { ChannelAllocator } from "../src/core/channels";
import { BoundedSet, Cooldowns, checkUnitCaps } from "../src/core/limits";
import { OrderBook, arrivalRadius, validateOrderTarget } from "../src/core/orderBook";
import { PerfStats, TpsEstimator } from "../src/core/perfStats";
import { CHUNK_SIZE, newSave, parseSave, serializeSave, splitChunks } from "../src/core/saveSchema";
import { Scheduler } from "../src/core/scheduler";
import { MAX_RESOURCE_AMOUNT, Treasury, emptyAmounts, formatCost } from "../src/core/treasury";
import { GATHER_RULES } from "../src/data/resources";

describe("Treasury", () => {
  it("spends all-or-nothing and reports shortfall", () => {
    let changes = 0;
    const t = new Treasury(emptyAmounts(), () => changes++);
    t.add("wood", 10);
    expect(t.spend({ wood: 15 })).toBe(false);
    expect(t.get("wood")).toBe(10);
    expect(t.shortfall({ wood: 15, gold: 2 })).toEqual({ wood: 5, gold: 2 });
    expect(t.spend({ wood: 10 })).toBe(true);
    expect(t.get("wood")).toBe(0);
    expect(changes).toBe(2);
  });

  it("ignores non-positive or non-finite additions and clamps the total", () => {
    const t = new Treasury(emptyAmounts());
    t.add("wood", -5);
    t.add("wood", Number.NaN);
    expect(t.get("wood")).toBe(0);
    t.add("wood", MAX_RESOURCE_AMOUNT * 2);
    expect(t.get("wood")).toBe(MAX_RESOURCE_AMOUNT);
  });

  it("refund restores a spend", () => {
    const t = new Treasury(emptyAmounts());
    t.add("wood", 20);
    t.spend({ wood: 15 });
    t.refund({ wood: 15 });
    expect(t.get("wood")).toBe(20);
  });

  it("formats costs", () => {
    expect(formatCost({ wood: 15 })).toBe("15 Wood");
    expect(formatCost({})).toBe("free");
  });
});

describe("ChannelAllocator", () => {
  it("hands out each channel once until released", () => {
    const c = new ChannelAllocator(2);
    expect(c.acquire()).toBe(0);
    expect(c.acquire()).toBe(1);
    expect(c.acquire()).toBeUndefined();
    c.release(0);
    expect(c.freeCount).toBe(1);
    expect(c.acquire()).toBe(0);
  });

  it("rejects invalid counts", () => {
    expect(() => new ChannelAllocator(0)).toThrow();
  });
});

describe("OrderBook", () => {
  const target = { x: 10, y: 64, z: 10 };

  it("creates orders and tracks arrival", () => {
    const book = new OrderBook(new ChannelAllocator(4));
    const res = book.create({ ownerId: "p1", dimensionId: "minecraft:overworld", target, unitIds: ["a", "b", "a"], tick: 0 });
    expect(res?.order.initialCount).toBe(2);
    expect(book.orderOf("a")?.id).toBe(res?.order.id);
    const order = book.markArrived("a")!;
    expect(order.arrivedCount).toBe(1);
    expect(book.orderOf("a")).toBeUndefined();
    expect(book.isDone(order)).toBe(false);
    book.markArrived("b");
    expect(book.isDone(order)).toBe(true);
  });

  it("re-ordering a unit moves it out of its old order and reports emptied orders", () => {
    const book = new OrderBook(new ChannelAllocator(4));
    const first = book.create({ ownerId: "p1", dimensionId: "o", target, unitIds: ["a"], tick: 0 })!;
    const second = book.create({ ownerId: "p1", dimensionId: "o", target, unitIds: ["a"], tick: 5 })!;
    expect(second.emptied.map((o) => o.id)).toEqual([first.order.id]);
    expect(book.orderOf("a")?.id).toBe(second.order.id);
    expect(first.order.lostCount).toBe(1);
  });

  it("finish frees the channel and returns stranded units", () => {
    const channels = new ChannelAllocator(1);
    const book = new OrderBook(channels);
    const { order } = book.create({ ownerId: "p", dimensionId: "o", target, unitIds: ["a", "b"], tick: 0 })!;
    expect(book.create({ ownerId: "p", dimensionId: "o", target, unitIds: ["c"], tick: 0 })).toBeUndefined();
    book.markArrived("a");
    expect(book.finish(order.id)).toEqual(["b"]);
    expect(book.orderOf("b")).toBeUndefined();
    expect(channels.freeCount).toBe(1);
  });

  it("removeUnit counts losses and ignores unknown units", () => {
    const book = new OrderBook(new ChannelAllocator(1));
    const { order } = book.create({ ownerId: "p", dimensionId: "o", target, unitIds: ["a"], tick: 0 })!;
    expect(book.removeUnit("zzz")).toBeUndefined();
    book.removeUnit("a");
    expect(order.lostCount).toBe(1);
    expect(book.isDone(order)).toBe(true);
  });

  it("expires orders after the timeout", () => {
    const book = new OrderBook(new ChannelAllocator(1));
    const { order } = book.create({ ownerId: "p", dimensionId: "o", target, unitIds: ["a"], tick: 100 })!;
    expect(book.isExpired(order, 199, 100)).toBe(false);
    expect(book.isExpired(order, 200, 100)).toBe(true);
  });

  it("nextBatch respects the budget and eventually covers every unit", () => {
    const book = new OrderBook(new ChannelAllocator(2));
    const ids = Array.from({ length: 10 }, (_, i) => `u${i}`);
    book.create({ ownerId: "p", dimensionId: "o", target, unitIds: ids.slice(0, 6), tick: 0 });
    book.create({ ownerId: "q", dimensionId: "o", target, unitIds: ids.slice(6), tick: 0 });
    const seen = new Set<string>();
    for (let i = 0; i < 4; i++) {
      const batch = book.nextBatch(3);
      expect(batch.length).toBe(3);
      for (const c of batch) seen.add(c.unitId);
    }
    expect(seen.size).toBe(10);
    expect(book.nextBatch(50).length).toBe(10);
  });

  it("arrival radius grows with squad size", () => {
    expect(arrivalRadius(1, 2.5)).toBe(2.5);
    expect(arrivalRadius(10, 2.5)).toBeGreaterThan(arrivalRadius(2, 2.5));
  });

  it("validates order distance", () => {
    const me = { x: 0, y: 64, z: 0 };
    expect(validateOrderTarget(me, { x: 30, y: 64, z: 0 }, 48)).toBeUndefined();
    expect(validateOrderTarget(me, { x: 60, y: 64, z: 0 }, 48)).toMatch(/Too far/);
    expect(validateOrderTarget(me, { x: Number.NaN, y: 0, z: 0 }, 48)).toMatch(/not valid/);
  });
});

describe("Scheduler", () => {
  it("runs tasks on their interval with staggered phases and isolates errors", () => {
    const runs: string[] = [];
    const errors: string[] = [];
    const s = new Scheduler({ now: () => 0, onTaskError: (n) => errors.push(n) });
    s.add("a", 10, () => runs.push("a"));
    s.add("b", 10, () => runs.push("b"));
    s.add("boom", 5, () => {
      throw new Error("x");
    });
    for (let t = 0; t < 20; t++) s.tick(t);
    expect(runs.filter((r) => r === "a").length).toBe(2);
    expect(runs.filter((r) => r === "b").length).toBe(2);
    expect(errors.length).toBe(4);
    const [a, b] = s.list();
    expect(a?.phase).not.toBe(b?.phase);
  });

  it("rejects bad intervals", () => {
    const s = new Scheduler({ now: () => 0 });
    expect(() => s.add("x", 0, () => {})).toThrow();
  });
});

describe("save schema", () => {
  it("round-trips", () => {
    const d = newSave();
    d.factions.aster!.treasury.wood = 42;
    d.factions.aster!.unitsAlive = 3;
    d.players["-123"] = { name: "Gustavo", unitsAlive: 3 };
    expect(parseSave(serializeSave(d)).data).toEqual(d);
  });

  it("falls back to defaults on garbage without throwing", () => {
    expect(parseSave("{not json").warning).toBeDefined();
    expect(parseSave("42").data).toEqual(newSave());
    expect(parseSave(undefined).data).toEqual(newSave());
  });

  it("sanitizes values and drops unknown factions", () => {
    const raw = JSON.stringify({
      version: 1,
      factions: { aster: { treasury: { wood: -5, gold: 1.9, bogus: 9 }, unitsAlive: "7" }, ghosts: { treasury: { wood: 1 } } },
      players: { p: { name: 5, unitsAlive: 2.7 } },
    });
    const { data } = parseSave(raw);
    expect(data.factions.aster).toEqual({ treasury: { wood: 0, food: 0, stone: 0, gold: 1 }, unitsAlive: 0 });
    expect(data.factions.ghosts).toBeUndefined();
    expect(data.factions.crimson).toBeDefined();
    expect(data.players.p).toEqual({ name: "", unitsAlive: 2 });
  });

  it("refuses saves from a newer version", () => {
    expect(parseSave(JSON.stringify({ version: 999 })).warning).toMatch(/newer/);
  });

  it("splits into chunks that rejoin exactly", () => {
    const s = "x".repeat(CHUNK_SIZE * 2 + 5);
    const parts = splitChunks(s);
    expect(parts.length).toBe(3);
    expect(parts.join("")).toBe(s);
    expect(splitChunks("")).toEqual([""]);
  });
});

describe("limits", () => {
  it("cooldowns drop repeats within the window", () => {
    const c = new Cooldowns();
    expect(c.tryUse("p", 100, 10)).toBe(true);
    expect(c.tryUse("p", 105, 10)).toBe(false);
    expect(c.tryUse("p", 110, 10)).toBe(true);
  });

  it("bounded set forgets the oldest entry", () => {
    const s = new BoundedSet<number>(2);
    s.add(1);
    s.add(2);
    s.add(3);
    expect(s.has(1)).toBe(false);
    expect(s.has(3)).toBe(true);
    expect(s.size).toBe(2);
  });

  it("unit caps", () => {
    const caps = { perPlayer: 10, perFaction: 20 };
    expect(checkUnitCaps(9, 9, 1, caps).ok).toBe(true);
    expect(checkUnitCaps(10, 10, 1, caps).ok).toBe(false);
    expect(checkUnitCaps(1, 20, 1, caps).reason).toMatch(/faction/);
  });
});

describe("perf stats", () => {
  it("aggregates timings", () => {
    const p = new PerfStats();
    p.record("a", 2);
    p.record("a", 4);
    const [a] = p.snapshot();
    expect(a).toMatchObject({ name: "a", calls: 2, totalMs: 6, maxMs: 4, avgMs: 3 });
  });

  it("estimates TPS", () => {
    const t = new TpsEstimator();
    expect(t.sample(0, 0)).toBeUndefined();
    expect(t.sample(20, 1000)).toBe(20);
    expect(t.sample(30, 2000)).toBe(10);
  });
});

describe("gather rules", () => {
  it("cover logs and stems but not planks or crop stems", () => {
    const blocks = new Set(GATHER_RULES.flatMap((r) => r.blockTypes));
    for (const id of ["minecraft:oak_log", "minecraft:stripped_pale_oak_wood", "minecraft:crimson_stem", "minecraft:bamboo_block"]) {
      expect(blocks.has(id)).toBe(true);
    }
    for (const id of ["minecraft:oak_planks", "minecraft:pumpkin_stem", "minecraft:mushroom_stem"]) {
      expect(blocks.has(id)).toBe(false);
    }
  });
});
