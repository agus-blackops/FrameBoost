// Bundles the app into a directory of static files:
//   node build.mjs <out-dir> <frameboost.wasm>

import { build } from "esbuild";
import { copyFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const [out = "dist", wasm = "../../target/wasm32-unknown-unknown/release/frameboost_wasm.wasm"] = process.argv.slice(2);
mkdirSync(out, { recursive: true });

const common = { bundle: true, minify: true, format: "iife", target: ["chrome100"], legalComments: "none" };
await build({ ...common, entryPoints: ["src/main.js"], outfile: join(out, "app.js") });
await build({ ...common, entryPoints: ["src/worker.js"], outfile: join(out, "worker.js") });

for (const f of ["index.html", "styles.css"]) copyFileSync(join("src", f), join(out, f));
copyFileSync(wasm, join(out, "frameboost.wasm"));
console.log(`web app → ${out}`);
