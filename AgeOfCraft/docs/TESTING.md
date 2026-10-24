# Testing

## Automated (run on every change)

```sh
npm run check          # typecheck + vitest + build + package
npm run mct:validate   # Mojang Creator Tools validator on the .mcaddon
```

- `tests/core.test.ts` covers the pure logic: treasury, order book and channel
  allocation, scheduler, save parsing and recovery, cooldowns and caps.
- `tests/packs.test.ts` checks the packs and their agreement with the scripts:
  strict JSON; manifest UUIDs and links; script module versions equal to the
  installed typings; every entity has a client entity, geometry, textures,
  animations, render controller and name; UV boxes fit the textures; events and
  component groups exist; faction count, channel count, stats and timers agree
  with `src/`.

These tests cannot prove in-game behaviour. Only the checklist below can.

## Manual in-game checklist, Milestone 1

Setup: a new **Creative** world (for quick logs and spawn eggs) with both packs
active and no experiments. Turn on **Settings › Creator › Content Log GUI**.
Record the platform and game version for each run.

| # | Step | Expected |
|---|---|---|
| 1 | Load the world | Content log shows `[AgeOfCraft] save loaded…` and `[AgeOfCraft] ready: …`, with no red script errors |
| 2 | Join for the first time | A **Commander Map** appears in the inventory, with a chat hint |
| 3 | Check the map item | Icon is a parchment map; name is "Commander Map"; it can be crafted from 2 paper + 1 stick |
| 4 | Use the map (LT / L2 / hold / right-click) | Faction picker with two large buttons |
| 5 | Pick **Aster Kingdom** | Chat: "You joined Aster Kingdom". Main menu opens with Wood: 0, Units: 0/10 |
| 6 | Break an oak log | Action bar: `+1 Wood (Aster Kingdom: 1)` |
| 7 | Place a log and break it again | No wood gained (anti-farming) |
| 8 | Break planks or dirt | Nothing |
| 9 | `/aoc:debug wood` (operator) | +100 wood |
| 10 | Map › **Recruit Spearman** | Chat: "Spearman recruited for 15 Wood." A blue-tunic spearman with a spear appears about 2 blocks ahead. Its name shows owner and faction |
| 11 | Recruit until the cap | The 11th attempt fails: "You command the maximum of 10 units." |
| 12 | Press recruit twice quickly | Only one unit is spawned and charged |
| 13 | Aim at the ground 20 blocks away, Map › **Move to target** | Chat: "N units moving to x, y, z". A gold rally flag appears and the units walk to it, around obstacles |
| 14 | Wait | Action bar: "Squad arrived (N/N)". The flag disappears and the units stand guard |
| 15 | Move to target, then **Halt** at once | Flag removed, units stop |
| 16 | **Rally to me** | Units walk to you |
| 17 | Aim more than 48 blocks away | "Too far away (…)", and no flag |
| 18 | Night: let a zombie approach | Idle spearmen attack it |
| 19 | Hit your own spearman | No damage; it does not retaliate |
| 20 | Second player joins **Crimson Dominion** and recruits | Red tunics. The two factions' units fight each other and attack enemy players |
| 21 | Kill a unit, then reopen the map | The unit count went down by 1 |
| 22 | Save and quit during a move, reload | `ready: … stale rally markers removed`. Units are idle, wood and unit counts are kept |
| 23 | Spawn egg (creative) next to you | "Spawned unit assigned to you", tunic in your faction colour |
| 24 | `/aoc:status`, `/aoc:debug perf` | Summary lines, and TPS around 20 |

Platform passes (Milestone 6 sign-off, but record anything you notice earlier):

- **Xbox Series S, controller:** steps 4, 5, 10, 13–16. Every button can be
  reached with the D-pad, the text is readable on a TV, and B (or the close button) closes menus.
- **Android, touch:** the same steps. Buttons are easy to hit on a phone
  screen, and the use gesture opens the map reliably.
- **PlayStation:** the same steps after joining a Realm or a hosted world.

## Performance test procedure

Repeat on each host type: Realm, Xbox host, Android host.

1. Flat world, simulation distance 4, `/aoc:debug on`.
2. Recruit N units per faction for N = 10, 20, 30, raising the caps in
   `gameConfig.ts` for the larger runs.
3. Issue a move order with both factions meeting in the middle.
4. After 60 s, run `/aoc:debug perf` and record: TPS, the `orders` task's
   total and max ms, the device, and any visible stutter.
5. Record the results in `docs/milestones/` for that milestone.

Until this has been done, no particular frame rate or army size is claimed.
