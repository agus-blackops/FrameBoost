# Architecture

## Layers

```
data/      what the game is      factions, units, resources, identifiers
config/    how it is tuned       caps, distances, update intervals
core/      pure rules            no @minecraft imports; unit-tested in Node
managers/  the game boundary     reads and writes the world through the Script API
main.ts    wiring                builds the managers, subscribes events, starts the scheduler
```

Gameplay data is kept apart from engine logic. Adding a unit or faction is
mostly data work, and `tests/packs.test.ts` points out every JSON file that
must change with it.

## Modules

| Module | Milestone 1 responsibility |
|---|---|
| `SaveManager` | Loads and saves `SaveData` (treasuries, unit counts) in world dynamic properties. Chunked, versioned, and recovers from corrupt data. Writes only when dirty, every 100 ticks and when a player leaves |
| `CivilizationManager` | Faction membership (player dynamic property mirrored to a tag) and relations |
| `EconomyManager` | Faction treasuries. Wood from player-broken logs, with place/break farming blocked |
| `UnitManager` | Registry of loaded units; recruitment with cost, caps and cooldown; death accounting; spawn-egg adoption; friendly-fire cancellation |
| `SquadManager` | One squad per player (all their units). Summary for the UI |
| `CommandSystem` | Move, rally and halt orders, arrival tracking, rally-marker lifecycle |
| `UIManager` | The Commander Map menus (`@minecraft/server-ui` forms) |
| `PerformanceMonitor` | Per-task timings and a TPS estimate |
| `DebugCommands` | `/aoc:status`, `/aoc:debug …` |

Not yet created, because nothing would use them: `AgeManager` (Milestone 5),
`WarDirector` (Milestone 4) and `BuildingManager` (Milestone 2). They will slot
in beside the existing managers and be wired in `main.ts`.

## How a move order works

The stable Script API cannot tell a mob to walk to a point. There is no
`moveTo`, and the navigation components only expose settings. Teleporting
units would skip pathfinding and collision. Instead, the order reuses the
game's own targeting and pathfinding:

1. `CommandSystem` takes a free **channel** N (0–7) and spawns an invulnerable
   `aoc:rally_marker` (a gold flag) at the destination, tagged `aoc_ch_N`.
2. Each ordered unit gets the tag `aoc_ch_N` and the event `aoc:order_move`.
   That swaps the `aoc:idle` component group for `aoc:moving`.
3. In `aoc:moving`, the unit's only target selector accepts rally markers for
   which *self has `aoc_ch_N` and other has `aoc_ch_N`*, for some N. Entity
   filters cannot compare two entities' tags directly, so the JSON lists one
   pair per channel. The unit's `melee_attack` goal then pathfinds to the flag.
4. Every 10 ticks, `CommandSystem` checks a bounded batch of marching units.
   Units inside the arrival radius (which grows with squad size) get
   `aoc:order_idle` and lose the tag. When none are left, or after 60 s, the
   marker is removed and the channel is freed.

Safety nets that do not depend on scripts:

- The moving group has a 90 s `minecraft:timer` back to idle.
- Markers despawn themselves after 120 s.
- On load, markers no active order owns are removed, and units left in the
  moving state are reset.

Eight channels limit the world to eight simultaneous move orders. When a
player runs out, their oldest order is replaced. The number is
`moveChannelCount` plus the filter list in `spearman.json`. The tests fail if
the two disagree.

## Performance design

- **One scheduler tick, staggered tasks.** A single `runInterval` drives every
  periodic job, with phases offset so jobs do not share a tick. Nothing runs
  per unit per tick.
- **Bounded work.** The arrival check looks at no more than
  `maxUnitChecksPerUpdate` units per update, round-robin. World scans run only
  at startup and on demand, and are filtered by entity type.
- **Engine-side AI.** Targeting, pathfinding and combat are native entity
  behaviours. Scripts only switch states.
- **Event filters.** Death, removal and hurt subscriptions are filtered by
  entity type or family, so the engine does not call scripts for unrelated mobs.
- `minecraft:conditional_bandwidth_optimization` on units and markers.

## Multiplayer and authority

All scripts run on the host (the world owner's device, a Realm or a server).
Clients only send form responses and item use, which the host validates
before acting: faction membership, caps, costs, cooldowns, order distance,
ownership of each ordered unit, and same-dimension checks. Unit ownership is
stored on the unit, and player faction on the player, both as dynamic
properties. That keeps ownership consistent across reloads and devices. Player
identity is `Player.id`.

## Persistence

| Key | Scope | Content |
|---|---|---|
| `aoc:save`, `aoc:save#N` | world | chunk count, then JSON chunks of `SaveData` (`src/core/saveSchema.ts`) |
| `aoc:faction`, `aoc:got_map` | player | faction key; starter map handed out |
| `aoc:owner`, `aoc:owner_name`, `aoc:faction` | unit | ownership |
| `aoc:order_id` | rally marker | owning order (session-scoped) |

Move orders are deliberately not persisted. After a reload, units are idle.

## Extending

- **New unit:** behavior entity JSON, client entity, geometry, texture and lang
  line; a `UnitDef` in `src/data/units.ts`; its id in `ENTITY` if scripts refer
  to it. If it should take orders, copy the `aoc:idle` / `aoc:moving` groups and
  events. The recruit menu builds itself from `UNITS`.
- **New faction:** a `FactionDef`; raise the `aoc:faction` property range; add
  a tunic texture and a render-controller array entry; extend the hostility and
  friendly-fire filters. The tests list anything missed.
- **New resource:** set `enabled: true` in `src/data/resources.ts` and add a
  `GatherRule`. The save schema already stores all four resources.
