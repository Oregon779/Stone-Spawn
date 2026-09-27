# StoneSpawn Wiki

A configurable spawn plugin for Paper servers, by **Stone Plugins**.

> **Requirements:** Paper (or a Paper fork) **26.2 or newer** and **Java 25**. Spigot and Folia are not supported.

---

## Contents

1. [Overview](#overview)
2. [Installation](#installation)
3. [Updating](#updating)
4. [Commands](#commands)
5. [Permissions](#permissions)
6. [How Teleporting Works](#how-teleporting-works)
7. [Automatic Teleports](#automatic-teleports)
8. [Multiple Spawns](#multiple-spawns)
9. [Spawn Selection GUI](#spawn-selection-gui)
10. [Elytra Glide](#elytra-glide)
11. [Effects and Notifications](#effects-and-notifications)
12. [Configuration Reference](#configuration-reference)
13. [Messages and Languages](#messages-and-languages)
14. [Data Storage and Safety](#data-storage-and-safety)
15. [Update Checker](#update-checker)
16. [Performance Tips for Large Servers](#performance-tips-for-large-servers)
17. [FAQ and Troubleshooting](#faq-and-troubleshooting)
18. [Building from Source](#building-from-source)

---

## Overview

StoneSpawn lets you:

- set the spawn point with one command (centered on the block you're standing on)
- create **several named spawns** and let players pick one with `/spawn <name>` or a **fully customizable selection GUI**
- teleport yourself, or another player, to a spawn with `/spawn`
- run a **teleport countdown** with a colored particle spiral and a sound that rises in pitch, cancelled when the player moves
- automatically teleport players to spawn **on join** (always, first join only, or never), **on death**, **when falling into the void** or **on world change**
- show an **arrival effect** (particles gathering around the player) and an arrival message
- mark every spawn with a permanent, rotating **particle ring**
- let players **glide down like with an elytra** after jumping off a spawn island, with an optional **rocket boost**
- show every countdown/arrival text in the **chat, action bar, boss bar or as a title**, all fully editable
- hide commands from tab-completion for players who can't use them
- get notified in-game and in the console when a **new plugin version** is available

General command feedback ships in **English and German**, and colors work with `&` codes, hex colors and MiniMessage at the same time.

---

## Installation

1. Download the latest `StoneSpawn-X.X.X.jar`.
2. Put it into your server's `plugins/` folder.
3. Start (or restart) the server.
4. On the first start, these files are created in `plugins/StoneSpawn/`:
   - `config.yml` - all settings
   - `gui.yml` - look and layout of the spawn selection GUI
   - `languages/en/messages.yml` and `languages/de/messages.yml` - command feedback
   - `data.yml` is created as soon as there is something to store (the first `/setspawn` or the first player joining).
5. Stand where the spawn should be and run `/setspawn`.

The console prints one line on startup, for example:

```
Config loaded (en), 0 spawn point(s), main spawn not set - use /setspawn - commands, listeners and update checker ready.
```

**Quick start**

```
/setspawn                  set the main spawn
/setspawn nether_hub       add a second, named spawn (optional)
/spawn                     test it - with two or more spawns this opens the selection GUI
/stonespawn reload         after editing config.yml, gui.yml or messages.yml
```

New installations use English for command feedback. Switch to German with `language: de` in `config.yml`.

---

## Updating

Updating is a file swap:

1. Stop the server.
2. Delete the old jar from the `plugins/` folder.
3. Put the new jar in.
4. Start the server.

Your settings are kept. On startup, StoneSpawn checks `config.yml`, `gui.yml` and every `messages.yml` for options that were added in the new version and adds only those, **including their explanation comments**. Existing values are never overwritten or deleted.

Things worth knowing when coming from older versions:

- **Multiple spawns:** a spawn set with an older version is converted automatically on the first start and becomes the **main spawn**. Nothing to do.
- **Permissions (1.6.0):** `stonespawn.admin.setspawn`, `stonespawn.others`, `stonespawn.admin.reload` and `stonespawn.notify.update` were merged into `stonespawn.admin`.
- **Notification channels (1.4.0):** countdown/arrival texts use one `notification` field per section, with text for every channel kept under `messages`.
- The particle effects were restructured several times. When jumping from a much older version, look over `teleport.countdown` and `teleport.arrival` once - old, unused fields are left in place instead of being deleted.

See `CHANGELOG.md` for the version history.

---

## Commands

Base command: `/stonespawn` (aliases: `/sp`, `/ss`)

**Spawns**

| Command | Description | Permission |
|---|---|---|
| `/setspawn` | Set the **main spawn** to your current location | `stonespawn.admin` |
| `/setspawn <name>` | Set an additional, **named spawn** (or move it, if it already exists) | `stonespawn.admin` |
| `/delspawn <name>` | Delete a spawn | `stonespawn.admin` |
| `/spawn` | Teleport to the main spawn - or open the **selection GUI** if more than one spawn exists | `stonespawn.use` |
| `/spawn <spawn>` | Teleport to a named spawn | `stonespawn.use` |
| `/spawn <player>` | Teleport another player to the main spawn | `stonespawn.admin` |
| `/spawn <spawn> <player>` | Teleport another player to a named spawn | `stonespawn.admin` |

**Admin**

| Command | Description | Permission |
|---|---|---|
| `/stonespawn reload` | Reloads `config.yml`, `gui.yml`, all `messages.yml` files and the update checker settings, and restarts the spawn markers | `stonespawn.admin` |
| `/stonespawn checkupdate` | Checks Modrinth for a new version right now | `stonespawn.admin` |
| `/stonespawn help` | Shows the commands you are allowed to use | everyone |

**Good to know**

- `/setspawn` centers the spawn on the middle of the block and keeps the direction you are looking in.
- Spawn names may contain `a-z`, `0-9`, `_` and `-`, up to 32 characters. Names are not case-sensitive and are stored in lowercase.
- The main spawn is simply the spawn called `spawn`, so `/setspawn` and `/setspawn spawn` do the same.
- If a spawn and an online player have the same name, `/spawn <name>` means the spawn. Use `/spawn <spawn> <player>` to be explicit.
- The console can use `/spawn <player>`, `/spawn <spawn> <player>`, `/delspawn` and `/stonespawn`, but not `/setspawn` (it needs a position).
- Commands a player can't use are hidden from tab-completion. `/stonespawn` stays visible, because `help` only lists what you may use.

**Examples**

```
/setspawn
/setspawn nether_hub
/spawn
/spawn nether_hub
/spawn Notch
/spawn nether_hub Notch
/delspawn nether_hub
/stonespawn reload
```

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `stonespawn.use` | Teleport yourself with `/spawn`, `/spawn <name>` or the GUI | everyone |
| `stonespawn.admin` | All admin actions: set/delete spawns, teleport other players, reload, checkupdate, update notifications | OP |
| `stonespawn.bypass` | Skips both the cooldown and the countdown (grants the two permissions below) | OP |
| `stonespawn.bypass.cooldown` | Skips only the `/spawn` cooldown | OP |
| `stonespawn.bypass.delay` | Skips only the countdown - the teleport happens immediately | OP |
| `stonespawn.elytra` | Glide and boost after jumping off a spawn island (only if `elytra.enabled` is on) | everyone |

Examples with LuckPerms:

```
/lp group vip permission set stonespawn.bypass.delay true      VIPs teleport without countdown
/lp group default permission set stonespawn.elytra false        nobody glides except groups you allow
/lp group default permission set stonespawn.use false           disable /spawn for normal players
```

---

## How Teleporting Works

This applies to `/spawn`, `/spawn <name>` and clicks in the selection GUI.

1. **World check** - the world the player is currently in must be allowed (see `worlds` in the [Configuration Reference](#configuration-reference)).
2. **Cooldown** - by default 10 seconds between uses (`command.cooldown-seconds`). It starts when a teleport begins, keeps running if the player logs out and back in, and is not used up if a countdown is already running. `stonespawn.bypass.cooldown` skips it.
3. **Countdown** - by default 3 seconds (`teleport.delay-seconds`, `0` = instant). During the countdown:
   - a particle spiral turns around the player and a sound plays every second with rising pitch,
   - the countdown text is shown every second (chat, action bar, boss bar or title),
   - optionally the player is blinded (`teleport.blindness-during-delay`),
   - **moving to another block cancels the teleport** (`teleport.cancel-on-move`). Looking around does not.

   `stonespawn.bypass.delay` skips the countdown.
4. **Teleport** - loads the destination chunk in the background, so it doesn't lag the server.
5. **Arrival** - arrival sound, particle effect and message (each can be switched off), and **5 seconds without fall damage** (`teleport.disable-fall-damage`). Blindness from the countdown is removed; blindness from other plugins (e.g. login plugins) is left alone.

**Teleporting other players** (`/spawn <player>`, `stonespawn.admin`): the target goes through the countdown (unless they have `stonespawn.bypass.delay`) and can cancel it by moving. There is no cooldown for this. The world the target is in must be allowed. The target is told who teleported them.

---

## Automatic Teleports

All automatic teleports go to the **main spawn** and happen instantly (no countdown, no cooldown). They are skipped if no main spawn is set or its world isn't loaded.

| Trigger | Setting | Default | Details |
|---|---|---|---|
| Joining the server | `spawn.join.mode` | `FIRST_JOIN` | `ALWAYS` = every join, `FIRST_JOIN` = only a player's very first join, `NEVER` = off. On the very first join no arrival effect/message is shown, on later joins it is. Not affected by the `worlds` restriction. |
| Respawning after death | `spawn.death.enabled` | `false` | Replaces the respawn point. A bed (`ignore-bed-spawn: true`) or respawn anchor (`ignore-respawn-anchor: true`) takes priority by default. The main spawn's world must be allowed. Arrival effect/message follow the `teleport.arrival` switches. |
| Falling into the void | `spawn.void.enabled` | `false` | Triggers below `spawn.void.height` (default `-64`). The player's world must be allowed. Not while a countdown is running. |
| Changing worlds | `spawn.world-change.enabled` | `false` | Every world change sends the player to the main spawn - including nether and end portals. The new world must be allowed. Teleports made by StoneSpawn itself don't trigger it. |

---

## Multiple Spawns

- `/setspawn` sets the **main spawn**. It is used by `/spawn` (with one spawn or with the GUI switched off) and by all [automatic teleports](#automatic-teleports).
- `/setspawn <name>` adds more spawns. Setting an existing name moves that spawn.
- Players reach named spawns with `/spawn <name>` or the [selection GUI](#spawn-selection-gui).
- `/delspawn <name>` deletes a spawn. The main spawn can be deleted as well - automatic teleports then stop until a new main spawn is set.
- Every spawn gets its own particle ring (the spawn marker, see [Effects and Notifications](#effects-and-notifications)).
- Spawns in worlds that are loaded later (for example by Multiverse) become available automatically as soon as the world loads.

---

## Spawn Selection GUI

As soon as **more than one spawn** exists, `/spawn` opens a selection menu instead of teleporting directly. Set `command.spawn-gui: false` in `config.yml` to turn it off - `/spawn` then always goes to the main spawn and other spawns are reached with `/spawn <name>`.

Clicking a spawn runs exactly the same checks as the command (world, cooldown, countdown). Items can't be taken out of the menu, and one opened menu only triggers one teleport.

Everything about the menu is set in `plugins/StoneSpawn/gui.yml`:

| Option | Default | Description |
|---|---|---|
| `title` | `&8Choose a spawn` | Menu title |
| `rows` | `3` | Menu size, 1-6 rows of 9 slots |
| `spawn-slots` | `[10, 11, 12, 13, 14, 15, 16]` | Slots used for spawns without a fixed slot, in this order. When they are full, the remaining free slots are used. |
| `spawn-item` | ender pearl | Default look of every spawn (see item options below) |
| `spawns.<name>` | none | Changes for one spawn: any item option, plus `slot: <number>` for a fixed slot and `hidden: true` to hide it from the menu |
| `filler` | gray glass pane | Fills all empty slots, `enabled: false` leaves them empty |
| `items.<key>` | none | Extra items (decoration, close button). `action: CLOSE` closes the menu on click. |
| `sounds.open` / `sounds.click` | ender chest open / button click | Empty (`""`) = no sound |
| `sounds.volume` / `sounds.pitch` | `0.6` / `1.0` | |

**Item options** (for `spawn-item`, `spawns.<name>`, `filler` and `items.<key>`): `material`, `amount`, `name`, `lore`, `glow` (`true`/`false`) and `item-model` (e.g. `myserver:spawn_icon`, for resource packs).

**Placeholders** in spawn item names and lore: `{spawn}`, `{world}`, `{x}`, `{y}`, `{z}`.

Slots count from `0` (top left) to `rows * 9 - 1` (bottom right). Spawns are sorted with the main spawn first, then alphabetically.

**Example**

```yaml
title: "&8» &bWhere do you want to go?"
rows: 3
spawn-slots: [11, 13, 15]

spawn-item:
  material: ENDER_PEARL
  name: "&b&l{spawn}"
  lore:
    - "&7World: &f{world}"
    - ""
    - "&eClick to teleport"

spawns:
  spawn:
    slot: 13
    material: GRASS_BLOCK
    name: "&a&lMain spawn"
    glow: true
  event_arena:
    hidden: true

items:
  close:
    slot: 26
    material: BARRIER
    name: "&cClose"
    action: CLOSE
```

**Notes**

- If there are more spawns than free slots, the remaining spawns are not shown - increase `rows`.
- An unknown material is replaced by `STONE` and reported in the console.
- Spawns in worlds that are not loaded are not shown.
- Open menus are closed when the plugin is disabled or the server reloads.

---

## Elytra Glide

With `elytra.enabled: true`, players who jump or fall off a **spawn island** start gliding like with an elytra - **no elytra item needed**. It is **off by default**.

How it works:

- The spawn island is everything within `elytra.radius` blocks (default 50, measured in 3D) of **any** spawn point.
- A player who last stood on the ground on a spawn island and then falls at least `elytra.min-fall-distance` blocks (default 4, minimum 1.5 so that normal jumps don't trigger it) starts gliding.
- The glide ends as soon as they **land**: on the ground, in water or lava, or on a ladder/vine. It also ends on teleport, death and game mode change.
- It starts again every time they jump off a spawn island.
- Only in survival and adventure mode, not while flying or riding, only in allowed worlds, and only with the `stonespawn.elytra` permission.
- With `elytra.disable-damage: true` (default), fall damage and "flew into a wall" damage are cancelled while gliding and for 2 seconds after landing.

**Rocket boost:** while gliding, players can boost themselves into the direction they are looking (`elytra.boost`, default once per glide). When the glide starts, the action bar tells them which key to press.

The boost key is configurable. A server only receives a few keys from the game, so freely chosen keys like `B` are **not possible**. Available triggers:

| `trigger` | Key |
|---|---|
| `SWAP_HAND` | `F` (swap item to off hand - the swap is blocked while gliding) - **default** |
| `SNEAK` | `Shift` |
| `LEFT_CLICK` | Left mouse button |

```yaml
elytra:
  enabled: true
  radius: 50
  min-fall-distance: 4.0
  disable-damage: true
  boost:
    enabled: true
    trigger: SWAP_HAND
    uses-per-glide: 1        # 0 = unlimited
    strength: 1.5            # ~1.5 feels like a firework rocket
    sound: ENTITY_FIREWORK_ROCKET_LAUNCH
    sound-volume: 1.0
    sound-pitch: 1.0
    particle: FIREWORK
    particle-count: 20       # 0 = no particles
```

The action bar text is `elytra.boost-hint` in `messages.yml` (empty = no hint); the key names shown in it are under `elytra.keys`.

> **Anti-cheat plugins:** gliding without an elytra item can look like a fly hack to anti-cheat plugins. If players get flagged or kicked while gliding, add an exemption for gliding in your anti-cheat.

---

## Effects and Notifications

**Where texts are shown**

`teleport.countdown` and `teleport.arrival` each have one `notification` field: `CHAT`, `ACTIONBAR`, `BOSSBAR` or `TITLE`. The text for all four channels stays filled in under `messages`, so switching is a one-word change. `TITLE` uses `title` and `subtitle` together.

```yaml
teleport:
  arrival:
    notification: TITLE
    messages:
      chat: "&a✔ &7You have arrived at &b&lspawn&7!"
      actionbar: "&a✔ &7You have arrived at &b&lspawn&7!"
      bossbar: "&a✔ You have arrived at spawn!"
      title: "&a&l✔ SPAWN"
      subtitle: "&7Welcome back!"
```

- **Boss bar** look (`teleport.bossbar`): `color` = `PINK`, `BLUE`, `RED`, `GREEN`, `YELLOW`, `PURPLE`, `WHITE`; `style` = `PROGRESS`, `NOTCHED_6`, `NOTCHED_10`, `NOTCHED_12`, `NOTCHED_20`; `duration-seconds` until it disappears. Common alternatives like `GOLD` or `SOLID` are recognized automatically.
- **Title** timing (`teleport.title`): `fade-in-ticks`, `stay-ticks`, `fade-out-ticks` (20 ticks = 1 second).
- Switch the arrival message or the arrival effects off completely with `teleport.arrival.message-enabled` / `effects-enabled`. This applies to every teleport, including automatic ones.

**Particle effects**

| Effect | Where | Visible to | Settings |
|---|---|---|---|
| Countdown spiral | around the player during the countdown | only that player | `teleport.countdown.spiral` - RGB color, size, radius, height, speed |
| Arrival effect | particles gathering around the player on arrival | only that player | `teleport.arrival.converge` - RGB color, size, radius, particle count, duration |
| Spawn marker | a rotating ring at every spawn, all the time | players within about 32 blocks | `spawn.marker` - particle type, radius, points, speed, height, interval |
| Boost | at the player when boosting | nearby players | `elytra.boost.particle` / `particle-count` |

The countdown and arrival effects use colored dust particles with any RGB color. The marker and the boost take a particle name (e.g. `PORTAL`, `END_ROD`, `FIREWORK`). Particles that need extra data, such as `DUST`, can't be used there - the default is used instead and a warning is shown once.

**Sounds**

Sound names use the Paper sound names (e.g. `BLOCK_NOTE_BLOCK_BELL`) or Minecraft keys (e.g. `minecraft:entity.firework_rocket.launch`). The countdown sound rises from `sound-pitch-start` to `sound-pitch-end` as the countdown reaches zero. An unknown sound name falls back to the default and is reported once in the console (again after each reload).

---

## Configuration Reference

All files are in `plugins/StoneSpawn/`. After changes, run `/stonespawn reload`. Invalid values fall back to their defaults.

**`config.yml`**

| Setting | Default | Description |
|---|---|---|
| `language` | `en` | Language folder for command feedback (`en`, `de` or your own) |
| `spawn.join.mode` | `FIRST_JOIN` | `ALWAYS`, `FIRST_JOIN` or `NEVER` |
| `spawn.death.enabled` | `false` | Teleport to spawn on respawn |
| `spawn.death.ignore-bed-spawn` | `true` | A bed spawn wins over the spawn teleport |
| `spawn.death.ignore-respawn-anchor` | `true` | A respawn anchor wins over the spawn teleport |
| `spawn.void.enabled` | `false` | Teleport to spawn when falling into the void |
| `spawn.void.height` | `-64` | Y level below which a player counts as "in the void" |
| `spawn.world-change.enabled` | `false` | Teleport to spawn on every world change |
| `spawn.marker.enabled` | `true` | Particle ring at every spawn |
| `spawn.marker.particle` | `PORTAL` | Particle type of the ring |
| `spawn.marker.radius` / `points` | `1.5` / `24` | Ring size and number of particles in the ring |
| `spawn.marker.rotation-speed-degrees` | `6.0` | Rotation per update |
| `spawn.marker.height-offset` | `0.1` | Height above the spawn point |
| `spawn.marker.interval-ticks` | `4` | How often the ring is drawn (lower = smoother, more particles) |
| `spawn.marker.particle-count-per-point` | `1` | Particles per ring point |
| `teleport.delay-seconds` | `3` | Countdown length, `0` = instant |
| `teleport.cancel-on-move` | `true` | Moving to another block cancels the countdown |
| `teleport.blindness-during-delay` | `false` | Blind the player during the countdown |
| `teleport.disable-fall-damage` | `true` | 5 seconds without fall damage after every spawn teleport |
| `teleport.countdown.*` | | Countdown channel, texts (`{seconds}`), spiral and sound - see [Effects and Notifications](#effects-and-notifications) |
| `teleport.arrival.*` | | Arrival switches, channel, texts, effect and sound |
| `teleport.bossbar.*` / `teleport.title.*` | | Shared boss bar look and title timing |
| `command.cooldown-seconds` | `10` | Cooldown between `/spawn` uses (also for the GUI) |
| `command.spawn-gui` | `true` | Open the selection GUI when more than one spawn exists |
| `elytra.*` | off | See [Elytra Glide](#elytra-glide) |
| `worlds.mode` | `NONE` | `NONE` = everywhere, `WHITELIST` = only in `worlds.list`, `BLACKLIST` = everywhere except `worlds.list` |
| `worlds.list` | `[]` | World names (exact spelling) |
| `update-checker.enabled` | `true` | Check Modrinth for new versions |
| `update-checker.check-interval-minutes` | `60` | Time between checks, minimum 5 |

**Other files**

- `gui.yml` - see [Spawn Selection GUI](#spawn-selection-gui).
- `languages/<language>/messages.yml` - see [Messages and Languages](#messages-and-languages).
- `data.yml` - see [Data Storage and Safety](#data-storage-and-safety). Not meant to be edited.

---

## Messages and Languages

- `config.yml` contains the **countdown and arrival texts** (together with their effects). They are the same for every language.
- `languages/<language>/messages.yml` contains all **command feedback** (help, errors, cooldown, confirmations). English (`en`) and German (`de`) are included; `language` in `config.yml` selects the one that is used.
- A text that is missing in the selected language falls back to the English one.
- **New language:** copy `languages/en/messages.yml` to e.g. `languages/fr/messages.yml`, translate it, set `language: fr` and run `/stonespawn reload`.

**Colors** - all of these work in every text, even mixed:

| Format | Example |
|---|---|
| Legacy codes | `&a`, `&l`, `&r` |
| Hex colors | `&#FF00AA` |
| MiniMessage | `<red>`, `<bold>`, `<gradient:#ff0000:#0000ff>text</gradient>` |

**Placeholders**

| Placeholder | Used in |
|---|---|
| `{seconds}` | countdown texts in `config.yml`, `spawn.cooldown` |
| `{player}` | `general.player-not-found`, `spawn.teleported-other`, `spawn.teleported-by` |
| `{spawn}` | `setspawn.success-named`, `setspawn.invalid-name`, `delspawn.success`, `spawn.not-found`; GUI items |
| `{world}` `{x}` `{y}` `{z}` | GUI spawn items |
| `{key}` | `elytra.boost-hint` |
| `{version}` `{current}` `{behind}` | `update.available` |
| `{count}` | `update.versions-behind` |

`prefix` is put in front of the chat messages from `messages.yml`, except the help lines and the update notice (which has its own prefix in the text). Countdown and arrival texts from `config.yml` are shown without it. When a message repeats something a player typed (e.g. an unknown spawn name), color codes and tags in it are neutralized and it is shortened to 32 characters.

---

## Data Storage and Safety

`data.yml` stores all spawn points and the list of players who have joined before (for `FIRST_JOIN`). Don't edit it while the server is running.

- Spawn changes are saved immediately in the background. First joins are collected and saved together a few seconds later, so a rush of new players doesn't cause lag.
- Everything that is still pending is saved when the server stops.
- The file is written to a temporary file first and then swapped in, so a crash can't leave a half-written `data.yml` behind.
- If `data.yml` can't be read (for example after editing it by hand), StoneSpawn **keeps a copy** as `data.yml.broken-<timestamp>`, logs an error and starts without spawn points instead of overwriting your data. To recover: stop the server, fix the file (or rename the backup back to `data.yml`) and start again.
- Spawns set with versions before multiple spawns existed are converted automatically.

---

## Update Checker

StoneSpawn checks [Modrinth](https://modrinth.com/project/stone-spawn) for newer versions: 5 seconds after startup and then every `update-checker.check-interval-minutes` (default 60). Everyone who is OP **or** has `stonespawn.admin` is notified:

- **in chat** when a newer version is found and every time they join, as long as the plugin isn't updated
- **in the console** after every check

Console messages:

- Update found: `A new version of StoneSpawn is available: 1.1.0 (you're on 1.0.0, 1 version(s) behind). Get it at https://modrinth.com/project/stone-spawn`
- No update: `No new version available (running 1.0.0).`
- Modrinth not reachable: `Update checker: Modrinth responded with status 404 for project 'stone-spawn'.` or `Update checker: check failed (...)`

`/stonespawn checkupdate` checks immediately. Turn the checker off with `update-checker.enabled: false`; the change applies after `/stonespawn reload`.

---

## Performance Tips for Large Servers

StoneSpawn is built for busy servers: teleports load chunks in the background, files are written off the main thread, and the spawn marker only sends particles to players near a spawn. The settings with the most effect when many players stand at spawn:

- **Spawn marker:** with the defaults (24 points every 4 ticks) every player near a spawn receives 6 particle packets per tick. On a crowded spawn, raise `spawn.marker.interval-ticks` (e.g. to `8`) or lower `spawn.marker.points` (e.g. to `16`), or switch it off with `spawn.marker.enabled: false`.
- **Arrival effect:** `teleport.arrival.converge.particles` (default 36 per tick for 20 ticks) is sent to the arriving player only. Lower it if many players join at once with `spawn.join.mode: ALWAYS`.
- **Countdown spiral:** `teleport.countdown.spiral.particle-count-per-point` is sent every tick to each player in a countdown.
- The elytra feature costs nothing while `elytra.enabled` is `false`.

To measure the real impact on your server, use the [spark](https://spark.lucko.me/) profiler.

---

## FAQ and Troubleshooting

**Will I lose my settings when updating?**
No. New versions only add missing options (with their comments); existing values are never changed. See [Updating](#updating).

**Why does `/spawn` open a menu instead of teleporting?**
There is more than one spawn. Set `command.spawn-gui: false` to always go to the main spawn, or delete spawns you don't need with `/delspawn`.

**How do I rename a spawn?**
Stand at the spawn, set it with the new name (`/setspawn newname`) and delete the old one (`/delspawn oldname`).

**Why doesn't dying or falling into the void teleport me to spawn?**
Both are off by default. Set `spawn.death.enabled: true` and/or `spawn.void.enabled: true`.

**How do I teleport players only on their very first join?**
`spawn.join.mode: FIRST_JOIN` - this is the default.

**How do I stop death from sending players to spawn when they have a bed?**
`spawn.death.ignore-bed-spawn: true` (default) lets the bed win. Set it to `false` if the spawn should always win.

**Why do my players get teleported back to spawn when using a nether portal?**
`spawn.world-change.enabled` is on. It sends players to spawn on every world change - turn it off, or restrict it with `worlds`.

**How do I choose where the countdown or arrival message shows up?**
Set `teleport.countdown.notification` or `teleport.arrival.notification` to `CHAT`, `ACTIONBAR`, `BOSSBAR` or `TITLE`.

**How do I change the particle colors?**
Set `red`/`green`/`blue` (0-255) under `teleport.countdown.spiral.color` and `teleport.arrival.converge.color`.

**Can certain players skip the countdown or the cooldown?**
Yes: `stonespawn.bypass.delay`, `stonespawn.bypass.cooldown`, or `stonespawn.bypass` for both.

**Does logging out reset the `/spawn` cooldown?**
No, a running cooldown continues after reconnecting.

**How do I restrict StoneSpawn to certain worlds?**
Set `worlds.mode` to `WHITELIST` or `BLACKLIST` and list the world names under `worlds.list`. Note that the join teleport is not affected by this.

**Why can't I use the `B` key for the elytra boost?**
Minecraft doesn't send free keys to the server. Pick `SWAP_HAND` (F), `SNEAK` (Shift) or `LEFT_CLICK` - see [Elytra Glide](#elytra-glide).

**Players get kicked while gliding off the spawn island.**
Most likely your anti-cheat flags gliding without an elytra item. Add an exemption for gliding there.

**Why don't I see `/setspawn` or `/delspawn` in tab-completion?**
You need `stonespawn.admin`. Commands you can't use are hidden, not just blocked.

**The console says "Invalid sound", "Invalid particle" or "Invalid material".**
A name in `config.yml` or `gui.yml` is misspelled or not allowed there. The default is used until you fix it.

**The console says "data.yml could not be read".**
See [Data Storage and Safety](#data-storage-and-safety) - your data was kept as `data.yml.broken-<timestamp>`.

**How do I translate the plugin?**
See [Messages and Languages](#messages-and-languages). The countdown/arrival texts in `config.yml` are shared by all languages.

**Does this work on Spigot?**
No. StoneSpawn uses Paper features (titles, boss bars, MiniMessage text, background teleports).

---

## Building from Source

Requirements: **Java 25** and **Maven**.

```
mvn package
```

The plugin jar is created as `target/StoneSpawn-<version>.jar`. The build also runs the automated tests (JUnit and MockBukkit); `mvn package -DskipTests` skips them. The Paper API is downloaded from `repo.papermc.io`, the test libraries from Maven Central.
