# StoneSpawn 1.0.0 - First release

A configurable spawn plugin: set one or more spawn points, teleport to them with a countdown and effects, and let players glide down from the spawn island.

> **Requirements:** Paper 26.2 or newer, Java 25. Spigot and Folia are not supported.

---

## Spawn points

- `/setspawn` sets the main spawn - centered on the block you're standing on, keeping the direction you're looking in.
- **Multiple spawns:** `/setspawn <name>` adds named spawns, `/delspawn <name>` removes them.
- `/spawn` teleports to the main spawn, `/spawn <name>` to a named one.
- Admins can send other players: `/spawn <player>` or `/spawn <spawn> <player>`.
- A rotating **particle ring** marks every spawn point permanently.

## Spawn selection GUI

- With more than one spawn, `/spawn` opens a selection menu (can be turned off).
- Fully customizable in `gui.yml`: title, size, slot layout, item material, name, lore and glow, custom item models for resource packs, per-spawn items with fixed slots or hidden spawns, filler items, extra items like a close button, and sounds.

## Teleporting

- **Countdown** (default 3 seconds) with a colored particle spiral around the player and a bell sound that rises in pitch. Moving cancels it.
- **Cooldown** between uses (default 10 seconds) - reconnecting doesn't reset it.
- **Arrival effect**: particles gather around the player, plus a sound and an arrival message.
- 5 seconds of **fall damage protection** after every spawn teleport.
- Optional blindness during the countdown.
- Teleports load the destination in the background, so they don't lag the server.
- Permissions to skip the countdown and/or the cooldown.

## Automatic teleports

- **On join:** every join, only on a player's very first join, or never.
- **On death:** respawn at spawn - a bed or respawn anchor can take priority.
- **When falling into the void**, below a configurable height.
- **On world change.**
- Restrict the plugin to certain worlds with a whitelist or blacklist.

## Elytra glide

- Players who jump off the area around a spawn **glide down like with an elytra - no item needed**. Landing ends the glide; it works again every time they jump off.
- Optional **rocket boost** while gliding (default: once per glide) on `F`, `Shift` or left click.
- No fall or "flew into a wall" damage while gliding and right after landing.
- **Bedrock players (Geyser/Floodgate)** are supported: they get a temporary elytra while gliding, their own chestplate is given back on landing, and they boost with sneak.
- Off by default - turn it on with `elytra.enabled: true`.

## Messages and customization

- Show the countdown and arrival texts in the **chat, action bar, boss bar or as a title** - chosen separately for each.
- Colors with `&` codes, hex colors (`&#FF00AA`) and MiniMessage, all usable at the same time.
- **English and German** included; add your own language by copying a language file.
- Every effect is configurable: particle colors (RGB), sizes, sounds and timings.
- When the plugin is updated, new options are added to your config files automatically - with their explanations - without touching your existing settings.

## For admins

- `/stonespawn reload`, `/stonespawn checkupdate` and `/stonespawn help` (aliases `/sp`, `/ss`).
- Commands a player can't use are hidden from tab-completion.
- **Update checker:** notifies admins in-game and in the console when a new version is on Modrinth.
- Built for busy servers: data is saved in the background and crash-safe, particles are only sent to players nearby, and a damaged data file is kept as a backup instead of being overwritten.

## Permissions

| Permission | Default | Allows |
|---|---|---|
| `stonespawn.use` | everyone | `/spawn`, `/spawn <name>` and the selection GUI |
| `stonespawn.admin` | OP | Set/delete spawns, teleport other players, reload, update notifications |
| `stonespawn.bypass` | OP | Skip cooldown and countdown |
| `stonespawn.bypass.cooldown` | OP | Skip only the cooldown |
| `stonespawn.bypass.delay` | OP | Skip only the countdown |
| `stonespawn.elytra` | everyone | Glide and boost from the spawn island |

## Getting started

1. Put the jar into your `plugins/` folder and restart the server.
2. Stand where the spawn should be and run `/setspawn`.
3. Adjust `plugins/StoneSpawn/config.yml` if you like and run `/stonespawn reload`.
