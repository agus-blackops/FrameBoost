// Packs behavior_pack/ and resource_pack/ into importable archives:
//   dist/AgeOfCraft-<version>.mcaddon  (both packs; open it to import both)
//   dist/AgeOfCraft_BP-<version>.mcpack, dist/AgeOfCraft_RP-<version>.mcpack
// Uses a small built-in ZIP writer so no external zip tool is needed.
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { crc32, deflateRawSync } from "node:zlib";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const version = JSON.parse(readFileSync(join(ROOT, "package.json"), "utf8")).version;

if (!existsSync(join(ROOT, "behavior_pack/scripts/main.js"))) {
  console.error("behavior_pack/scripts/main.js is missing: run `npm run build` first.");
  process.exit(1);
}

function walk(dir) {
  return readdirSync(dir)
    .sort()
    .flatMap((name) => {
      const p = join(dir, name);
      return statSync(p).isDirectory() ? walk(p) : [p];
    });
}

/** [archive path, file path] pairs for a pack, placed under `prefix/`. */
function packEntries(packDir, prefix) {
  return walk(packDir).map((f) => [`${prefix}${relative(packDir, f).split(sep).join("/")}`, f]);
}

function zip(entries) {
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const [name, file] of entries) {
    const data = readFileSync(file);
    const packed = deflateRawSync(data, { level: 9 });
    const nameBuf = Buffer.from(name, "utf8");
    const crc = crc32(data) >>> 0;
    // DOS date 1980-01-01 00:00 keeps archives reproducible.
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6); // UTF-8 names
    local.writeUInt16LE(8, 8); // deflate
    local.writeUInt16LE(0, 10);
    local.writeUInt16LE(0x21, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(packed.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    local.writeUInt16LE(0, 28);
    locals.push(local, nameBuf, packed);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(8, 10);
    central.writeUInt16LE(0, 12);
    central.writeUInt16LE(0x21, 14);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(packed.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(nameBuf.length, 28);
    central.writeUInt32LE(offset, 42);
    centrals.push(central, nameBuf);
    offset += local.length + nameBuf.length + packed.length;
  }
  const centralBuf = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralBuf.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, centralBuf, end]);
}

const dist = join(ROOT, "dist");
mkdirSync(dist, { recursive: true });
const bp = join(ROOT, "behavior_pack");
const rp = join(ROOT, "resource_pack");
const outputs = [
  [`AgeOfCraft-${version}.mcaddon`, [...packEntries(bp, "AgeOfCraft_BP/"), ...packEntries(rp, "AgeOfCraft_RP/")]],
  [`AgeOfCraft_BP-${version}.mcpack`, packEntries(bp, "")],
  [`AgeOfCraft_RP-${version}.mcpack`, packEntries(rp, "")],
];
for (const [name, entries] of outputs) {
  const buf = zip(entries);
  writeFileSync(join(dist, name), buf);
  console.log(`dist/${name}: ${entries.length} files, ${(buf.length / 1024).toFixed(1)} KiB`);
}
