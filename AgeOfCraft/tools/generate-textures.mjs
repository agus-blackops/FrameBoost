// Generates every texture in the packs from code, so all art is original and
// reproducible. Run with `npm run textures`; output is committed.
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { Canvas, grain, hex, rng, shade } from "./png.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

function save(relPath, canvas) {
  const out = join(ROOT, relPath);
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, canvas.toPng());
  console.log(`wrote ${relPath} (${canvas.width}x${canvas.height})`);
}

const SKIN = hex("#c99a6e");
const HAIR = hex("#4a3121");
const STEEL = hex("#8d939b");
const LEATHER = hex("#6b4a2b");
const TROUSERS = hex("#4b3d2e");
const BOOTS = hex("#2c221a");
const WOOD = hex("#7a5a32");
const GOLD = hex("#d9a634");

const TUNICS = {
  unaligned: hex("#8c8672"),
  aster: hex("#2f5fae"),
  crimson: hex("#a32b2b"),
};

// UV layout matches resource_pack/models/entity/spearman.geo.json.
function spearman(tunic, seed) {
  const r = rng(seed);
  const c = new Canvas(64, 64);

  // Head (8x8x8 at 0,0)
  c.box(0, 0, 8, 8, 8, (face, x, y) => {
    if (face === "top" || face === "back") return grain(r, HAIR, 0.1);
    if (face === "bottom") return grain(r, SKIN, 0.04);
    if (y <= 2) return grain(r, HAIR, 0.1);
    if (face === "front") {
      if (y === 3 && (x === 1 || x === 2 || x === 5 || x === 6)) return shade(HAIR, -0.2);
      if (y === 4 && (x === 1 || x === 6)) return hex("#f2efe8");
      if (y === 4 && (x === 2 || x === 5)) return hex("#2b3a55");
      if (y === 6 && (x === 3 || x === 4)) return shade(SKIN, -0.28);
      if (y === 7) return grain(r, shade(HAIR, 0.1), 0.1); // short beard
    }
    if ((face === "right" || face === "left") && y === 3) return grain(r, HAIR, 0.1);
    return grain(r, SKIN, 0.04);
  });

  // Kettle helmet (8x4x8 at 32,0)
  c.box(32, 0, 8, 4, 8, (face, x, y, w, h) => {
    if (face === "top") return x === 3 || x === 4 ? shade(STEEL, 0.25) : grain(r, STEEL, 0.06);
    if (face === "bottom") return null;
    if (y === h - 1) return shade(STEEL, -0.3); // rim
    return grain(r, shade(STEEL, y === 0 ? 0.12 : 0), 0.06);
  });

  // Body (8x12x4 at 16,16)
  c.box(16, 16, 8, 12, 4, (face, x, y, w) => {
    if (face === "top") return grain(r, shade(tunic, -0.15), 0.05);
    if (face === "bottom") return grain(r, tunic, 0.05);
    if (y === 7) {
      if (face === "front" && (x === 3 || x === 4)) return GOLD;
      return grain(r, LEATHER, 0.06);
    }
    if (face === "front" || face === "back") {
      if (y === 0 && x >= 2 && x <= 5) return shade(tunic, -0.3); // collar
      const strapX = face === "front" ? Math.floor(y * 0.9) : w - 1 - Math.floor(y * 0.9);
      if (y < 7 && x === strapX) return grain(r, LEATHER, 0.06);
      if (y >= 8 && x === 3) return shade(tunic, -0.18); // tunic split
    }
    return grain(r, tunic, 0.06);
  });

  // Arms (4x12x4 at 40,16 and 32,48)
  const arm = (face, x, y) => {
    if (face === "top") return grain(r, tunic, 0.05);
    if (face === "bottom") return grain(r, SKIN, 0.04);
    if (y <= 3) return grain(r, y === 3 ? shade(tunic, -0.2) : tunic, 0.06);
    if (y >= 7 && y <= 9) return grain(r, LEATHER, 0.06);
    return grain(r, SKIN, 0.04);
  };
  c.box(40, 16, 4, 12, 4, arm);
  c.box(32, 48, 4, 12, 4, arm);

  // Legs (4x12x4 at 0,16 and 16,48)
  const leg = (face, x, y) => {
    if (face === "top") return grain(r, TROUSERS, 0.05);
    if (face === "bottom") return shade(BOOTS, -0.2);
    if (y >= 9) return grain(r, y === 9 ? shade(BOOTS, 0.15) : BOOTS, 0.05);
    return grain(r, TROUSERS, 0.07);
  };
  c.box(0, 16, 4, 12, 4, leg);
  c.box(16, 48, 4, 12, 4, leg);

  // Spear shaft (1x28x1 at 56,32) and head (1x3x2 at 48,32)
  c.box(56, 32, 1, 28, 1, (face, x, y) => grain(r, y % 9 === 4 ? shade(WOOD, -0.2) : WOOD, 0.08));
  c.box(48, 32, 1, 3, 2, (face, x, y) => (y === 0 ? hex("#e1e6ea") : grain(r, hex("#c0c6cc"), 0.05)));

  return c;
}

// UV layout matches resource_pack/models/entity/rally_marker.geo.json.
function rallyMarker() {
  const r = rng(77);
  const c = new Canvas(32, 32);
  c.box(0, 0, 1, 24, 1, () => grain(r, WOOD, 0.1));
  c.box(0, 25, 4, 1, 4, () => grain(r, hex("#7b7b7b"), 0.1));
  c.box(4, 0, 10, 6, 1, (face, x, y, w, h) => {
    if (face !== "front" && face !== "back") return shade(GOLD, -0.25);
    // A simple tower emblem, mirrored on the back so it reads the same.
    const ex = face === "front" ? x : w - 1 - x;
    const inTower = ex >= 4 && ex <= 6 && y >= 1 && y <= 4;
    const crenel = y === 1 && ex === 5;
    if (inTower && !crenel) return hex("#f4efe1");
    if (y === 0 || y === h - 1) return shade(GOLD, -0.15);
    return grain(r, GOLD, 0.05);
  });
  return c;
}

function commanderMapIcon() {
  const r = rng(5);
  const c = new Canvas(16, 16);
  const paper = hex("#e7d7a6");
  const edge = hex("#8a6a3a");
  c.rect(1, 2, 14, 12, (x, y) => {
    if (x === 0 || y === 0 || x === 13 || y === 11) return edge;
    return grain(r, paper, 0.05);
  });
  // Forest patches, a river and a road.
  const forest = hex("#4f7a3a");
  for (const [x, y] of [[3, 4], [4, 4], [3, 5], [4, 5], [5, 5], [10, 9], [11, 9], [11, 10], [10, 10]]) c.set(x, y, grain(r, forest, 0.1));
  const water = hex("#3f6fb5");
  for (const [x, y] of [[7, 3], [7, 4], [8, 5], [8, 6], [7, 7], [7, 8], [8, 9], [8, 10], [8, 11], [8, 12]]) c.set(x, y, water);
  const road = hex("#a8875a");
  for (let x = 2; x <= 13; x++) if (x !== 7 && x !== 8) c.set(x, 8, road);
  // Command flag.
  c.set(11, 3, hex("#3b2a1a"));
  c.set(11, 4, hex("#3b2a1a"));
  c.set(11, 5, hex("#3b2a1a"));
  c.set(11, 6, hex("#3b2a1a"));
  c.set(12, 3, hex("#c0392b"));
  c.set(13, 3, hex("#c0392b"));
  c.set(12, 4, hex("#c0392b"));
  return c;
}

function packIcon() {
  const r = rng(9);
  const c = new Canvas(64, 64);
  c.rect(0, 0, 64, 64, (x, y) => grain(r, shade(hex("#1f3b4d"), -y / 160), 0.03));
  const stone = hex("#d9c38a");
  const tower = (x0, w, top) => c.rect(x0, top, w, 52 - top, (x, y) => (y < 3 && x % 3 === 1 ? null : grain(r, stone, 0.05)));
  tower(10, 12, 18);
  tower(42, 12, 18);
  tower(22, 20, 28);
  c.rect(29, 38, 6, 14, () => hex("#3b2a1a")); // gate
  c.rect(0, 52, 64, 12, () => grain(r, hex("#4f7a3a"), 0.08));
  // Banner on the keep.
  c.rect(32, 8, 1, 20, () => hex("#3b2a1a"));
  c.rect(33, 8, 9, 6, () => GOLD);
  return c;
}

for (const [key, tunic] of Object.entries(TUNICS)) {
  save(`resource_pack/textures/entity/aoc/spearman_${key}.png`, spearman(tunic, key.length * 97));
}
save("resource_pack/textures/entity/aoc/rally_marker.png", rallyMarker());
save("resource_pack/textures/items/aoc/commander_map.png", commanderMapIcon());
const icon = packIcon();
save("resource_pack/pack_icon.png", icon);
save("behavior_pack/pack_icon.png", icon);
