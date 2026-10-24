# Building and installing Age Of Craft

## Requirements

- Minecraft Bedrock Edition **1.26.50 or newer** on every device in the session.
  Cross-platform play needs everyone on the same game version anyway.
- No experimental toggles. The add-on uses only stable Script API modules:
  `@minecraft/server` 2.10.0 and `@minecraft/server-ui` 2.2.0.
- Cheats are **not** required to play. `/aoc:debug` needs operator permission.
- To build from source: Node.js 22.2 or newer.

## Build

```sh
cd AgeOfCraft
npm install
npm run check
```

`npm run check` runs, in order:

| Step | Command | What it does |
|---|---|---|
| typecheck | `npm run typecheck` | TypeScript strict mode against the exact `@minecraft/*` typings declared in the manifest |
| test | `npm test` | core logic tests and pack-consistency tests (`tests/`) |
| build | `npm run build` | bundles `src/` into `behavior_pack/scripts/main.js` |
| package | `npm run package` | writes `dist/AgeOfCraft-<version>.mcaddon` and one `.mcpack` per pack |

Optional: `npm run mct:validate` runs Mojang's
[Minecraft Creator Tools](https://www.npmjs.com/package/@minecraft/creator-tools)
validator on the `.mcaddon`. It downloads the tool on first use.

`npm run textures` regenerates every PNG from `tools/generate-textures.mjs`. The
generated PNGs are committed, so you only need it after changing the generator.

## Import on Windows, Android or iOS

1. Copy `dist/AgeOfCraft-<version>.mcaddon` to the device.
2. Open it:
   - **Windows:** double-click it.
   - **Android:** open it from a file manager and choose Minecraft.
   - **iOS:** open it from the Files app with Minecraft.

   Minecraft starts and reports two successful imports.
3. Create or edit a world, go to **Behavior Packs**, and activate
   **Age Of Craft (Behavior)**. Minecraft asks to add the matching resource pack
   as well; accept.
4. Play the world.

Xbox and PlayStation cannot import files. See [PLATFORMS.md](PLATFORMS.md) for how
console players join.

### Updating

Raise `version` in `package.json` and the `header.version` and dependency
versions in both manifests (the tests check that the manifests agree), then rebuild and
re-import. Minecraft ignores an import whose version is not higher than the one
it already has. A world keeps a copy of its packs, so for an existing world
you may also need to remove and re-add the pack in that world's settings.

## Development loop (Windows)

Copy or link the pack folders into the game's development folders. Packs there
are reloaded from disk instead of being cached:

- `behavior_pack` → `…/com.mojang/development_behavior_packs/AgeOfCraft_BP`
- `resource_pack` → `…/com.mojang/development_resource_packs/AgeOfCraft_RP`

The location of `com.mojang` depends on how Minecraft is installed. Recent
Windows releases keep it under `%APPDATA%\Minecraft Bedrock\Users\Shared\games\com.mojang`;
older Microsoft Store builds used
`%LOCALAPPDATA%\Packages\Microsoft.MinecraftUWP_8wekyb3d8bbwe\LocalState\games\com.mojang`.
Use whichever one exists on your PC.

After `npm run build`, run `/reload` in-game to reload scripts (operator only).
Turn on **Settings › Creator › Content Log GUI** to see script errors on screen.
Every message from the add-on starts with `[AgeOfCraft]`.
