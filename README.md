# StoneSpawn

A configurable server spawn point plugin for Paper 1.21+, by **Stone Plugins**.

Set a spawn point, teleport to it with `/spawn`, and let StoneSpawn handle
join/death/void/world-change teleports automatically - complete with a
countdown, a spiral particle effect, sound, and fully configurable
chat/actionbar/bossbar/title messages.

## Features

- `/setspawn` - set the server spawn point to your current location
- `/spawn [player]` - teleport to spawn, or teleport another player there
- Automatic teleport to spawn:
  - on join (always, only on first join, or never)
  - on death (bed and respawn anchor spawn points can be respected or ignored)
  - when falling into the void (configurable void height)
  - on world change
- Ambient particle ring sitting permanently at the spawn location itself, visible to everyone nearby
- Teleport countdown with a spiral particle effect and rising-pitch sound
  - cancels if the player moves
  - optional blindness effect during the countdown
  - can be bypassed per-permission
- Command cooldown, configurable, bypassable per-permission
- Fall damage can be disabled right after a spawn teleport
- Countdown and arrival messages: pick where each is shown (chat, action
  bar, boss bar, or title/subtitle) and fully customize the text for every
  channel, all in `config.yml`
- Whitelist/blacklist worlds to restrict where the plugin is active
- Commands you don't have permission for don't even show up in tab-completion (except `/stonespawn help`, which lists only what you can actually use)
- Colors: legacy `&` codes, hex (`&#RRGGBB`) and MiniMessage tags
  (`<gradient>`, ...) all work at the same time, no switch needed
- General command feedback is translated per-language (`en`/`de` included);
  new installs default to English
- Built-in update checker for [Modrinth](https://modrinth.com/project/stone-spawn),
  checks on startup and hourly (configurable); notifies OPs and permission
  holders in-game

## Installation

1. Drop `StoneSpawn-<version>.jar` into your server's `plugins/` folder.
2. Start (or restart) the server. StoneSpawn will generate its default files
   under `plugins/StoneSpawn/`.
3. Stand where you want spawn to be and run `/setspawn`.
4. Adjust `plugins/StoneSpawn/config.yml` to taste, then `/stonespawn reload`.

Requires Paper (or a Paper fork) 1.21+ and Java 21. Not supported on Folia.

## Configuration

- `plugins/StoneSpawn/config.yml` - all settings, plus the text and effects
  for the teleport countdown and arrival message (`teleport.countdown.*` /
  `teleport.arrival.*`). Each has one `notification` field (`CHAT`,
  `ACTIONBAR`, `BOSSBAR` or `TITLE`) picking where it's shown; text for
  every channel stays filled in under `messages`, so you can switch which
  one is active without retyping anything.
- `plugins/StoneSpawn/languages/<lang>/messages.yml` - general command
  feedback (help, errors, cooldown messages, ...), translated per language.
  Add a new language by creating `plugins/StoneSpawn/languages/<code>/messages.yml`
  and setting `language: <code>` in `config.yml`.
- `plugins/StoneSpawn/data.yml` - stores the spawn location and which
  players have joined before. Not meant to be edited by hand.

Missing options are added automatically when the plugin updates; your
existing settings are never overwritten, even in nested sections.

## Permissions

| Permission                  | Default | Description                                                            |
|------------------------------|---------|--------------------------------------------------------------------------|
| `stonespawn.use`              | true    | Teleport yourself to spawn with `/spawn`                                |
| `stonespawn.admin`            | op      | Set spawn, teleport others, reload, checkupdate, update notifications   |
| `stonespawn.bypass`           | op      | Bypass both the cooldown and the teleport delay (parent of the two below) |
| `stonespawn.bypass.cooldown`  | op      | Bypass the `/spawn` command cooldown                                    |
| `stonespawn.bypass.delay`     | op      | Bypass the teleport delay and countdown                                 |

## Commands

| Command                          | Description                                             |
|-----------------------------------|-----------------------------------------------------------|
| `/setspawn`                       | Set the spawn point to your current location             |
| `/spawn [player]`                 | Teleport to spawn, or teleport another player there       |
| `/stonespawn reload`               | Reload the configuration and messages                     |
| `/stonespawn checkupdate`          | Check Modrinth for a new version right now                |
| `/stonespawn help`                 | Show the help menu (also `/sp`, `/ss`)                    |

## Support

Found a bug or have a suggestion? Let us know via the plugin's Modrinth page.
