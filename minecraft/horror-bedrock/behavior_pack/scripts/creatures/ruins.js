// Structures: someone was in this world before you. As the days go by you
// come across what they left, built a long way off where you weren't
// looking, each with a chest and, in it, a page of their diary (the notes,
// items/notes.js). In the order they come:
//
//   cabin    an abandoned spruce cabin             notes 1 and 2
//   kennel   a fenced pen and a dog house, bones   note 3
//   crosses  a field of cobblestone crosses        note 4
//   temple   a small blue shrine of lapis          note 5
//   tower    a radio mast on a stone base          note 6
//   camp     a cold campfire and a torn tent       note 7
//
// (Note 8 is what you get for the eighth page.) Each is built once, when its
// day has come, 40 to 60 blocks away, and the chat says roughly where.

import { BlockPermutation, ItemStack } from "@minecraft/server";
import {
  PROTECTED, block, distance, groundAt, inSight, isOpen, offsetFromView, rand, sound, tryRun,
} from "../core/util.js";
import { define } from "../core/creatures.js";
import { addFear, discover, hauntDay, intensity, setWorldProp, worldProp } from "../core/store.js";

// ------------------------------------------------------------------------- //
// The plans: [dx, dy, dz, block] from the corner, dy 0 being the floor
// ------------------------------------------------------------------------- //

function box(out, x0, x1, y0, y1, z0, z1, type, hollow = false) {
  for (let x = x0; x <= x1; x++)
    for (let y = y0; y <= y1; y++)
      for (let z = z0; z <= z1; z++) {
        if (hollow && x > x0 && x < x1 && z > z0 && z < z1) continue;
        out.push([x, y, z, type]);
      }
  return out;
}

function cabin() {
  const p = [];
  box(p, 0, 6, 0, 0, 0, 5, "minecraft:spruce_planks");
  box(p, 0, 6, 1, 3, 0, 5, "minecraft:spruce_planks", true);
  for (const [x, z] of [[0, 0], [6, 0], [0, 5], [6, 5]]) box(p, x, x, 1, 3, z, z, "minecraft:stripped_spruce_log");
  box(p, 0, 6, 4, 4, 0, 5, "minecraft:spruce_slab");
  box(p, 3, 3, 1, 2, 0, 0, "minecraft:air"); // the doorway
  p.push([0, 2, 2, "minecraft:glass_pane"], [6, 2, 3, "minecraft:glass_pane"], [2, 2, 5, "minecraft:glass_pane"]);
  p.push([2, 4, 2, "minecraft:air"], [4, 4, 3, "minecraft:air"]); // holes in the roof
  p.push([1, 3, 1, "minecraft:web"], [5, 3, 4, "minecraft:web"], [5, 1, 1, "minecraft:crafting_table"]);
  p.push([1, 1, 4, "minecraft:chest"], [4, 1, 4, "minecraft:red_carpet"], [3, 1, 3, "minecraft:red_carpet"]);
  return { plan: p, w: 7, d: 6, h: 4, chest: [1, 1, 4], notes: [1, 2], loot: [["minecraft:bread", 3], ["minecraft:torch", 6]] };
}

function kennel() {
  const p = [];
  box(p, 0, 6, 0, 0, 0, 6, "minecraft:coarse_dirt");
  box(p, 0, 6, 1, 1, 0, 6, "minecraft:oak_fence", true);
  p.push([3, 1, 0, "minecraft:air"]);
  box(p, 3, 5, 1, 2, 3, 5, "minecraft:oak_planks", true);
  box(p, 3, 5, 3, 3, 3, 5, "minecraft:dark_oak_slab");
  p.push([4, 1, 3, "minecraft:air"], [4, 2, 3, "minecraft:air"], [4, 1, 4, "minecraft:chest"]);
  p.push([1, 1, 2, "minecraft:bone_block"], [2, 1, 5, "minecraft:bone_block"], [1, 1, 4, "minecraft:red_carpet"]);
  return { plan: p, w: 7, d: 7, h: 3, chest: [4, 1, 4], notes: [3], loot: [["minecraft:bone", 5], ["minecraft:lead", 1]] };
}

function crossAt(p, x, z) {
  box(p, x, x, 1, 3, z, z, "minecraft:cobblestone");
  p.push([x - 1, 2, z, "minecraft:cobblestone"], [x + 1, 2, z, "minecraft:cobblestone"]);
}

function crosses() {
  const p = [];
  box(p, 0, 8, 0, 0, 0, 8, "minecraft:gravel");
  for (const [x, z] of [[1, 1], [7, 1], [1, 7], [7, 7], [4, 1], [4, 7]]) crossAt(p, x, z);
  p.push([4, 0, 4, "minecraft:mossy_cobblestone"], [4, 1, 4, "minecraft:chest"]);
  return { plan: p, w: 9, d: 9, h: 3, chest: [4, 1, 4], notes: [4], loot: [["minecraft:candle", 4]] };
}

function temple() {
  const p = [];
  box(p, 0, 6, 0, 0, 0, 6, "minecraft:lapis_block");
  box(p, 1, 5, 0, 0, 1, 5, "minecraft:blue_concrete");
  for (const [x, z] of [[0, 0], [6, 0], [0, 6], [6, 6]]) {
    box(p, x, x, 1, 3, z, z, "minecraft:stone_bricks");
    p.push([x, 4, z, "minecraft:lapis_block"]);
  }
  box(p, 0, 6, 5, 5, 0, 6, "minecraft:blue_stained_glass", true);
  p.push([3, 1, 3, "minecraft:lapis_block"], [3, 2, 3, "minecraft:chest"], [3, 1, 1, "minecraft:soul_lantern"]);
  return { plan: p, w: 7, d: 7, h: 5, chest: [3, 2, 3], notes: [5], loot: [["minecraft:lapis_lazuli", 6]] };
}

function tower() {
  const p = [];
  box(p, 0, 4, 0, 0, 0, 4, "minecraft:stone_bricks");
  box(p, 1, 3, 1, 1, 1, 3, "minecraft:cracked_stone_bricks");
  box(p, 2, 2, 2, 13, 2, 2, "minecraft:iron_bars");
  p.push([2, 14, 2, "minecraft:lightning_rod"], [1, 2, 2, "minecraft:iron_bars"], [3, 2, 2, "minecraft:iron_bars"]);
  p.push([0, 1, 2, "minecraft:chest"], [4, 1, 2, "minecraft:lever"]);
  return { plan: p, w: 5, d: 5, h: 14, chest: [0, 1, 2], notes: [6], loot: [["minecraft:redstone", 8], ["minecraft:copper_ingot", 2]] };
}

function camp() {
  const p = [];
  box(p, 0, 6, 0, 0, 0, 6, "minecraft:coarse_dirt");
  p.push([3, 1, 3, "campfire"]);
  // A-frame tent of wool, torn.
  box(p, 0, 0, 1, 1, 0, 2, "minecraft:white_wool");
  box(p, 2, 2, 1, 1, 0, 2, "minecraft:white_wool");
  box(p, 1, 1, 2, 2, 0, 2, "minecraft:white_wool");
  p.push([2, 1, 1, "minecraft:air"], [1, 1, 1, "minecraft:chest"]);
  p.push([5, 1, 5, "minecraft:red_carpet"], [4, 1, 5, "minecraft:red_carpet"], [5, 1, 1, "minecraft:oak_log"]);
  return { plan: p, w: 7, d: 7, h: 3, chest: [1, 1, 1], notes: [7], loot: [["minecraft:cooked_porkchop", 2], ["minecraft:paper", 3]] };
}

export const STRUCTURES = [
  { name: "cabin", day: 0, make: cabin },
  { name: "kennel", day: 2, make: kennel },
  { name: "crosses", day: 3, make: crosses },
  { name: "temple", day: 4, make: temple },
  { name: "tower", day: 4, make: tower },
  { name: "camp", day: 5, make: camp },
];

// ------------------------------------------------------------------------- //
// Building
// ------------------------------------------------------------------------- //

export function built() {
  return tryRun(() => JSON.parse(worldProp("hb:ruins", "[]"))) ?? [];
}

// Flat enough, nothing of yours in the way.
function fits(dimension, o, s) {
  for (let x = -1; x <= s.w; x++)
    for (let z = -1; z <= s.d; z++) {
      const g = groundAt(dimension, o.x + x + 0.5, o.z + z + 0.5, o.y + 1);
      if (g === undefined || Math.abs(g - 1 - o.y) > 1) return false;
      const surface = block(dimension, o.x + x, g - 1, o.z + z);
      if (!surface || PROTECTED.test(surface.typeId) || /leaves|log|water/.test(surface.typeId)) return false;
      for (let y = 1; y <= s.h + 1; y++) {
        const b = block(dimension, o.x + x, o.y + y, o.z + z);
        if (!b || (!isOpen(b) && y > 1) || PROTECTED.test(b.typeId)) return false;
      }
    }
  return true;
}

function resolve(type) {
  if (type === "campfire") return tryRun(() => BlockPermutation.resolve("minecraft:campfire", { extinguished: true }));
  return tryRun(() => BlockPermutation.resolve(type));
}

function build(dimension, o, s) {
  // Clear the space, and a foundation where the ground dips.
  for (let x = 0; x < s.w; x++)
    for (let z = 0; z < s.d; z++) {
      for (let y = 1; y <= s.h + 1; y++) tryRun(() => block(dimension, o.x + x, o.y + y, o.z + z).setType("minecraft:air"));
      for (let y = o.y - 1; y > o.y - 4; y--) {
        const b = block(dimension, o.x + x, y, o.z + z);
        if (b && isOpen(b)) tryRun(() => b.setType("minecraft:cobblestone"));
        else break;
      }
    }
  for (const [x, y, z, type] of s.plan) {
    const perm = resolve(type);
    const b = block(dimension, o.x + x, o.y + y, o.z + z);
    if (b && perm) tryRun(() => b.setPermutation(perm));
  }
  const [cx, cy, cz] = s.chest;
  const chest = block(dimension, o.x + cx, o.y + cy, o.z + cz);
  const inv = tryRun(() => chest.getComponent("minecraft:inventory").container);
  if (inv) {
    for (const n of s.notes) tryRun(() => inv.addItem(new ItemStack(`hb:note_${n}`, 1)));
    for (const [id, amount] of s.loot) tryRun(() => inv.addItem(new ItemStack(id, amount)));
  }
}

const DIRS = ["s", "sw", "w", "nw", "n", "ne", "e", "se"];

// Which way it is from you: north is -z.
function direction(from, to) {
  const a = Math.atan2(-(to.x - from.x), to.z - from.z); // 0 = south, turning through west
  const i = Math.round((a / (Math.PI * 2)) * 8);
  return DIRS[((i % 8) + 8) % 8];
}

function site(player, s, near) {
  let fallback;
  for (let i = 0; i < 12; i++) {
    const p = near ? offsetFromView(player, rand(14, 22), rand(-30, 30)) : offsetFromView(player, rand(40, 60), rand(0, 360));
    const g = groundAt(player.dimension, p.x, p.z, player.location.y);
    if (g === undefined) continue;
    const o = { x: Math.floor(p.x), y: g - 1, z: Math.floor(p.z) };
    if (!fits(player.dimension, o, s)) continue;
    // Somewhere you weren't looking.
    if (near || !inSight(player, { x: o.x + s.w / 2, y: o.y + 1, z: o.z + s.d / 2 })) return o;
    fallback ??= o;
  }
  return fallback;
}

export function buildNext(player, near = false) {
  const done = built();
  const names = new Set(done.map((r) => r.name));
  const day = hauntDay();
  let next = STRUCTURES.find((st) => !names.has(st.name) && day >= Math.ceil(st.day * intensity().days));
  if (!next && near) next = STRUCTURES[done.length % STRUCTURES.length];
  if (!next) return false;
  const s = next.make();
  const o = site(player, s, near) ?? (near ? site(player, s, false) : undefined);
  if (!o) return false;
  build(player.dimension, o, s);
  const center = { x: o.x + s.w / 2, y: o.y, z: o.z + s.d / 2 };
  done.push({ name: next.name, x: o.x, y: o.y, z: o.z });
  setWorldProp("hb:ruins", JSON.stringify(done));
  const dist = String(Math.round(distance(player.location, center) / 5) * 5);
  tryRun(() =>
    player.sendMessage({ rawtext: [
      { text: "§7" }, { translate: `hb.ruin.${next.name}` }, { text: " " },
      { translate: "hb.ruin.where", with: { rawtext: [{ text: dist }, { translate: `hb.dir.${direction(player.location, center)}` }] } },
    ] })
  );
  sound(player, "hb.creak", 0.6, 0.6);
  discover("ruins");
  addFear(player, 4);
  return true;
}

define({
  kind: "ruins",
  weights: (ctx) => [ctx.outside ? 3 : 0, 0],
  minor: (player) => buildNext(player),
  test: (player) => buildNext(player, true),
});
