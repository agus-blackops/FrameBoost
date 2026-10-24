// Bundles src/main.ts into behavior_pack/scripts/main.js. The @minecraft/*
// modules are provided by the game at runtime, so they stay external.
import { build } from "esbuild";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

await build({
  entryPoints: [join(ROOT, "src/main.ts")],
  outfile: join(ROOT, "behavior_pack/scripts/main.js"),
  bundle: true,
  format: "esm",
  platform: "neutral",
  target: "es2022",
  external: ["@minecraft/server", "@minecraft/server-ui"],
  sourcemap: false,
  legalComments: "none",
  logLevel: "info",
});
