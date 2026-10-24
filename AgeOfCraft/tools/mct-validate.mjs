// Runs Mojang's Minecraft Creator Tools validator over the packaged add-on
// and prints a summary. Downloads the tool on first use via npx.
//   npm run build && npm run package && npm run mct:validate
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const MCT = "@minecraft/creator-tools@0.20.0";
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const version = JSON.parse(readFileSync(join(ROOT, "package.json"), "utf8")).version;
const addon = join(ROOT, "dist", `AgeOfCraft-${version}.mcaddon`);
const out = join(ROOT, "dist", "mct-report");

if (!existsSync(addon)) {
  console.error(`${addon} is missing: run \`npm run build && npm run package\` first.`);
  process.exit(1);
}

execFileSync("npx", ["--yes", MCT, "validate", "all", "--if", addon, "-o", out], { stdio: "inherit", cwd: ROOT });

const csvName = readdirSync(out).find((f) => f.endsWith(".csv") && !f.includes("sharing"));
if (!csvName) {
  console.error("validator produced no report");
  process.exit(1);
}

// Minimal CSV parsing: quoted fields, commas inside quotes.
function parseCsv(text) {
  const rows = [];
  for (const line of text.split(/\r?\n/).filter(Boolean)) {
    const cells = [];
    let cur = "";
    let quoted = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (quoted && ch === '"' && line[i + 1] === '"') {
        cur += '"';
        i++;
      } else if (ch === '"') quoted = !quoted;
      else if (ch === "," && !quoted) {
        cells.push(cur);
        cur = "";
      } else cur += ch;
    }
    cells.push(cur);
    rows.push(cells);
  }
  return rows;
}

const [header, ...rows] = parseCsv(readFileSync(join(out, csvName), "utf8"));
const col = (name) => header.indexOf(name);
const counts = {};
for (const r of rows) counts[r[col("Type")]] = (counts[r[col("Type")]] ?? 0) + 1;
console.log("\nSummary:", counts);
for (const r of rows) {
  const type = r[col("Type")];
  if (type === "Error" || type === "Warning" || type === "Recommendation") {
    console.log(`${type}: ${r[col("Message")]} | ${r[col("Path")]} | ${r[col("Data")]}`);
  }
}
console.log(`\nFull report: ${join(out, csvName.replace(".csv", ".report.html"))}`);
process.exit(counts.Error ? 1 : 0);
