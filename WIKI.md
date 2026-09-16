# StoneSpawn Wiki

A plugin by Stone Plugins.

---

## Overview

StoneSpawn lets you:

- set the server spawn point to your current location with one command (centered on the block you're standing on)
- teleport yourself (or another player) to spawn with `/spawn`
- automatically teleport players to spawn on join (always, first join only, or never), on death, when falling into the void, or on world change - death and void teleportation are off by default, join/world-change are configurable
- run a teleport countdown with a colored double-helix particle spiral around the player and a sound that rises in pitch as it counts down
- watch particles gather inward around the player the moment they arrive at spawn
- mark the spawn location itself with a permanent ambient particle ring, visible to everyone nearby
- keep commands out of tab-completion entirely for players who don't have permission to use them
- get notified in-game (and in the console) when a new plugin version is available

Every countdown/arrival message (Chat, ActionBar, BossBar, Title) is fully configurable in `config.yml`, and general command feedback is available in both English and German out of the box.

---

## Installation

**Requirements:** Paper server 1.21 or newer, Java 21

1. Download the latest `StoneSpawn-X.X.X.jar`
2. Copy it into your server's plugins folder
3. Restart the server
4. On first start, these are created automatically:
   - `plugins/StoneSpawn/config.yml`
   - `plugins/StoneSpawn/languages/en/messages.yml`
   - `plugins/StoneSpawn/languages/de/messages.yml`
   - `plugins/StoneSpawn/data.yml`
5. Stand where you want spawn to be and run `/setspawn`

New installations default to English for command feedback. Switch to German (or any language you add yourself) via `config.yml → language`.

The console prints exactly one line on startup, e.g.:

```
Config loaded (en), spawn point not set - use /setspawn - commands, listeners and update checker ready.
```

---

## Updating

Updating is always just a file swap:

1. Stop the server
2. Delete the old jar file in the plugins folder
3. Drop in the new jar file
4. Start the server

Your settings are preserved. On startup, the plugin automatically checks whether your `config.yml` and `messages.yml` are missing any new options introduced by the update, and appends only the missing lines to the relevant section. Existing values are never overwritten or deleted. The `plugins/StoneSpawn` folder is otherwise left untouched during an update.

The countdown/arrival particle setup has been restructured a few times as the effects were refined - if you're jumping from a version more than a couple of releases old, it's worth reviewing `teleport.countdown` and `teleport.arrival` once after updating, since old fields are left in place but unused rather than deleted. The two structural changes worth knowing about specifically:

- **Permissions (1.6.0):** the old `stonespawn.admin.setspawn`, `stonespawn.others`, `stonespawn.admin.reload` and `stonespawn.notify.update` permissions were merged into a single `stonespawn.admin` node.
- **Notification channels (1.4.0):** countdown/arrival text moved from separate always-on channel toggles into a single `notification: "..."` field per section, with text for every channel kept in `messages`.

See `CHANGELOG.md` for the full version-by-version history.

The plugin also checks Modrinth periodically for newer releases on its own — see [Update Checker](#update-checker) below.

---

## Commands

Base command: `/stonespawn` (aliases: `/sp`, `/ss`)

**Spawn**

| Command | Description | Permission |
|---|---|---|
| `/setspawn` | Set the spawn point to your current location (centered on the block) | `stonespawn.admin` |
| `/spawn` | Teleport yourself to spawn | `stonespawn.use` |
| `/spawn <player>` | Teleport another player to spawn | `stonespawn.admin` |

**Admin**

| Command | Description | Permission |
|---|---|---|
| `/stonespawn reload` | Reloads `config.yml` and `messages.yml` | `stonespawn.admin` |
| `/stonespawn checkupdate` | Checks Modrinth for a new version right now instead of waiting for the next scheduled check | `stonespawn.admin` |
| `/stonespawn help` | Shows all commands with descriptions (admin-only lines are hidden if you don't have `stonespawn.admin`) | anyone |

Commands you don't have permission for don't appear in tab-completion at all - `/stonespawn` itself is always visible since `help` filters its own contents per permission anyway.

**Examples**

```
/setspawn
/spawn
/spawn Notch
/stonespawn reload
/stonespawn checkupdate
```

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `stonespawn.use` | Teleport yourself to spawn with `/spawn` | all players |
| `stonespawn.admin` | Access to all admin actions: setspawn, teleporting others, reload, checkupdate, update notifications | `op` |
| `stonespawn.bypass` | Bypasses both the `/spawn` cooldown and the teleport delay | `op` |
| `stonespawn.bypass.cooldown` | Bypasses only the `/spawn` command cooldown | `op` |
| `stonespawn.bypass.delay` | Bypasses only the teleport delay/countdown | `op` |

`stonespawn.bypass` automatically grants both bypass permissions below it as well. For more targeted access — for example, skipping the cooldown but still going through the countdown — grant just that one permission.

---

## Configuration

Config files live in `plugins/StoneSpawn/`:

- **`config.yml`** — settings *and* the countdown/arrival notification text, always in English, not affected by the language setting
- **`languages/en/messages.yml`** and **`languages/de/messages.yml`** — general command feedback (help, errors, cooldown messages), fully editable per language
- **`data.yml`** — stores the spawn location and which players have joined before; not meant to be edited by hand

After making changes, run `/stonespawn reload`.

Colors work everywhere at the same time, no toggle needed: legacy codes like `&a` or `&#ff00ff`, and MiniMessage tags like `<red>` or `<gradient:#ff0000:#0000ff>`.

The server console is always in English regardless of the configured language, doesn't use color codes, and stays deliberately short — one line on startup, nothing else unless something actually needs attention.

**Sections in `config.yml`:**

| Section | Purpose |
|---|---|
| `language` | Which language file to use for general command feedback |
| `spawn.join` | Teleport-on-join mode: `ALWAYS`, `FIRST_JOIN`, or `NEVER` |
| `spawn.death` | Teleport-on-death (off by default), and whether bed/respawn anchor spawns override it |
| `spawn.void` | Teleport-on-void-fall (off by default), and the void height |
| `spawn.world-change` | Teleport whenever a player changes worlds |
| `spawn.marker` | The ambient particle ring permanently marking the spawn location |
| `teleport.delay-seconds` / `cancel-on-move` / `blindness-during-delay` / `disable-fall-damage` | Shared behavior of the manual `/spawn` teleport |
| `teleport.countdown` | Notification channel, all channel text, the countdown particle spiral, sound |
| `teleport.arrival` | Notification channel, all channel text, the arrival particle effect, sound |
| `teleport.bossbar` / `teleport.title` | Shared BossBar look and Title timing, used by both countdown and arrival |
| `command.cooldown-seconds` | Cooldown for the manual `/spawn` command |
| `worlds` | Whitelist/blacklist to restrict where the plugin is active |
| `update-checker` | On/off and check interval (the Modrinth project itself is fixed in the plugin, not configurable) |

### Notification channels

Both `teleport.countdown` and `teleport.arrival` have **one** `notification` field instead of separate toggles — pick exactly one channel:

```yaml
teleport:
  arrival:
    notification: TITLE   # CHAT, ACTIONBAR, BOSSBAR or TITLE
    messages:
      chat: "&a✔ &7You have arrived at &b&lspawn&7!"
      actionbar: "&a✔ &7You have arrived at &b&lspawn&7!"
      bossbar: "&a✔ You have arrived at spawn!"
      title: "&a&l✔ SPAWN"
      subtitle: "&7Welcome back!"
```

All channel texts stay filled in at the same time, so switching where something is shown is a one-word change — no need to re-write the text. TITLE uses `title` + `subtitle` together; the other three channels use their own single line.

### Particle effects

The countdown shows a continuously-running colored double-helix spiral around the player; arrival shows particles starting scattered on a sphere around the player and gathering inward as they land. Both use the DUST particle with a fully custom RGB color instead of a fixed particle type:

```yaml
teleport:
  countdown:
    spiral:
      enabled: true
      color: { red: 120, green: 190, blue: 255 }
      size: 1.2
      radius: 0.6
      height: 1.8
      rotation-speed-degrees: 20.0
      particle-count-per-point: 2
    sound: BLOCK_NOTE_BLOCK_BELL
    sound-volume: 0.5
    sound-pitch-start: 0.7
    sound-pitch-end: 1.6

  arrival:
    converge:
      enabled: true
      color: { red: 255, green: 240, blue: 215 }
      size: 0.9
      radius: 1.8
      height-center: 1.0
      particles: 36
      duration-ticks: 20
    sound: ENTITY_ENDERMAN_TELEPORT
    sound-volume: 0.5
    sound-pitch: 1.1
```

Countdown sound pitch rises from `sound-pitch-start` to `sound-pitch-end` as the countdown reaches zero, so it builds toward the teleport instead of repeating the same beep.

The permanent marker at the spawn location itself works the same way, but uses a fixed `PORTAL` particle and broadcasts to everyone nearby (not just the teleporting player):

```yaml
spawn:
  marker:
    enabled: true
    particle: PORTAL
    radius: 1.5
    points: 24
    rotation-speed-degrees: 6.0
    height-offset: 0.1
    interval-ticks: 4
    particle-count-per-point: 1
```

**Common placeholders in texts:** `seconds` (countdown only), `player` (in general/spawn messages.yml messages), `version`, `current`, `behind` (update messages)

**Valid BossBar colors:** `PINK`, `BLUE`, `RED`, `GREEN`, `YELLOW`, `PURPLE`, `WHITE`
**Valid BossBar styles:** `PROGRESS`, `NOTCHED_6`, `NOTCHED_10`, `NOTCHED_12`, `NOTCHED_20`

Common alternatives like `GOLD` or `SOLID` are automatically recognized and corrected.

---

## Update Checker

StoneSpawn checks [Modrinth](https://modrinth.com/project/stone-spawn) periodically for newer releases. Anyone who is OP **or** has `stonespawn.admin` gets notified:

- **In chat**, every time they join, as long as a newer version is known
- **In the console**, once per check, plain English text, always after the server has fully started (first check runs 5 seconds after startup)

Console messages are kept minimal:

- Update found: `A new version of StoneSpawn is available: 1.1.0 (you're on 1.0.0, 1 version(s) behind). Get it at https://modrinth.com/project/stone-spawn`
- No update: `No new version available (running 1.0.0).`
- Problem reaching Modrinth: `Update checker: Modrinth responded with status 404 for project 'stone-spawn'.`

Use `/stonespawn checkupdate` to trigger a check immediately instead of waiting for the next scheduled one.

---

## FAQ

**Will I lose my settings when updating?**
No, new versions only add missing new options, existing values are left untouched. See **Updating** above for the couple of structural changes worth knowing about.

**Why doesn't dying or falling into the void teleport me to spawn?**
Both are off by default. Set `spawn.death.enabled: true` and/or `spawn.void.enabled: true` in `config.yml` to turn them on.

**How do I let a spawn happen only on a player's very first join, not every time?**
Set `spawn.join.mode: FIRST_JOIN` in `config.yml` (this is the default).

**How do I stop death from sending players back to spawn if they have a bed?**
`spawn.death.ignore-bed-spawn: true` (default) makes a bed spawn point take priority over teleporting to spawn. Set it to `false` if you want spawn to always win.

**How do I choose where the countdown or arrival message shows up?**
Set `teleport.countdown.notification` or `teleport.arrival.notification` in `config.yml` to `CHAT`, `ACTIONBAR`, `BOSSBAR`, or `TITLE`. All the text for every channel stays configured regardless of which one you pick.

**How do I change the color of the particle effects?**
Set the RGB values under `teleport.countdown.spiral.color` and `teleport.arrival.converge.color` in `config.yml` - any combination of `red`/`green`/`blue` from 0-255.

**Can I let certain players skip the countdown entirely?**
Yes, grant `stonespawn.bypass.delay`. Combine with `stonespawn.bypass.cooldown`, or just grant `stonespawn.bypass` for both at once.

**How do I restrict StoneSpawn to only work in certain worlds?**
Set `worlds.mode` to `WHITELIST` or `BLACKLIST` and list the world names under `worlds.list`.

**Why don't I see `/setspawn` when I press tab?**
You need `stonespawn.admin` - commands you don't have permission for are hidden from tab-completion entirely, not just blocked.

**How do I translate the plugin?**
General command feedback: copy `languages/en/messages.yml` to a new folder (e.g. `languages/fr/messages.yml`), translate it, and set `language: "fr"` in `config.yml`. The countdown/arrival text in `config.yml` itself is English-only and shared across all languages.

**Does this work with Spigot instead of Paper?**
No, it uses Paper-specific APIs for Title, BossBar, Adventure/MiniMessage text, and async teleportation.
