# Platforms and distribution

Target group: three players, on **Xbox Series S** (owner), **PlayStation** (Samir)
and **Android** (Gustavo).

## How Bedrock add-ons reach players

- Add-ons are applied to a **world**. The behavior pack and its scripts run on
  whatever **hosts** the world: a player's device, a Realm, or a server.
  Joining players download the world's resource pack automatically and need
  nothing installed.
- **Xbox and PlayStation cannot import `.mcaddon` / `.mcpack` files**, because
  they have no user file system access. Windows, Android and iOS can.
- This project does not use, and will not document, unsupported methods such
  as console file injection, Xbox Developer Mode on retail consoles, or
  modified clients.

So at least one device that can import (Gustavo's Android phone, a Windows PC,
or an iPhone/iPad) has to prepare the world.

## Routes, in order of preference

### 1. Realm (recommended for the three-player group)

1. On Android or Windows, import the `.mcaddon` and create a world with both
   packs active ([INSTALL.md](INSTALL.md)).
2. Upload that world to a Realm: **Realm settings › Replace world**. This needs
   a Realms subscription.
3. Everyone joins the Realm from Xbox, PlayStation or Android. The scripts run
   on the Realm, so no phone or console has to host.

Evidence and open questions:

- Microsoft's creator documentation describes publishing worlds to Realms
  "so you and others can play or test across platforms"
  ([Publish to Realms](https://learn.microsoft.com/en-us/minecraft/creator/documents/bedrockeditor/editorrealms)).
- The [1.21.20 changelog](https://www.minecraft.net/en-us/article/minecraft-1-21-20-bedrock-changelog)
  fixed an issue where sideloaded `.mcpack`s failed to apply when uploaded to Realms.
- Community reports conflict on whether non-Marketplace add-ons keep working on
  Realms. **Not verified for this add-on yet**; this is Milestone 6 work. Check:
  the packs survive the upload, scripts run (the content log shows
  `[AgeOfCraft] ready`), and the console players see the textures.

### 2. Android or PC host, consoles join

Gustavo (or a Windows PC) hosts the world locally; the Xbox and PlayStation
players join through the friends list. This works without a Realm, but:

- the host must stay in the game for the others to play;
- a phone host runs the whole simulation (every unit's AI plus the scripts),
  so expect a lower practical army size than a Realm. Measure it with
  `/aoc:debug perf` ([TESTING.md](TESTING.md)).

### 3. Xbox as host, via a Realm download (to investigate)

A Realm world can be downloaded to a console, which would let the Xbox Series S
host a world that was prepared on another device. Whether the add-on packs come
along in that download is **unverified**. It will be tested in Milestone 6
before it is recommended.

### Not recommended: Bedrock Dedicated Server

A dedicated server runs add-ons well, but Xbox and PlayStation do not offer an
"add server" option for arbitrary addresses in their standard interface. Working
around that is outside what this project supports.

## Controls by platform

The Commander Map is opened with the item **use** action:

| Platform | Use action |
|---|---|
| Xbox | LT |
| PlayStation | L2 |
| Android / iOS (touch) | tap and hold, or the dedicated use button in the joystick/D-pad layouts |
| Windows | right-click |

Every menu is a `@minecraft/server-ui` form: a vertical list of large buttons,
navigated with the D-pad or stick plus A (Xbox) or Cross (PlayStation), or by
tapping. No menu asks the player to type coordinates. Destinations come from
where the player is aiming, or from the player's own position.

## Platform-specific limitations (current)

- **Touch selection in the world is not possible.** The supported API has no
  click-to-select or drag-box selection of entities. The menu and aiming are
  the supported substitute.
- **Simulation distance.** Units only act in loaded, ticking chunks around
  players. Consoles commonly run with a 4-chunk simulation distance (about 64
  blocks), so orders are limited to 48 blocks from the commander by default.
- **Performance on phones and the Series S** is unmeasured. Unit caps default
  to 10 per player and 20 per faction (`src/config/gameConfig.ts`) and will be
  raised only after measurements.
