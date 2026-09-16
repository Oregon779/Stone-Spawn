# Changelog

All notable changes to StoneSpawn are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.16.1]

### Changed
- Death and void teleportation are now **off by default** on new installs. Manual `/spawn` and join-based teleportation still work as before — enable `spawn.death.enabled` / `spawn.void.enabled` in `config.yml` if you want automatic teleportation on death or when falling into the void.

## [1.16.0]

### Changed
- Reworked the arrival effect: instead of particles orbiting the player, they now start scattered around the player on a sphere and gather inward as they land — a "settling" motion rather than a spin.

## [1.15.1]

### Changed
- Softer, less saturated arrival effect color and a shorter duration.
- New countdown sound (a clear bell tone) instead of the previous portal ambience.

## [1.15.0]

### Changed
- Simplified the countdown and arrival particle effects down to a single, clean colored spiral each, instead of several combined layers. Color is fully customizable via RGB.

## [1.14.0]

### Changed
- Countdown and arrival ring effects now wrap around the player's whole body (a vertical halo passing over the head and under the feet) instead of sitting flat on the ground.
- The ambient particle marker at the spawn point is purple-only again.

## [1.12.0]

### Added
- `/setspawn` now centers the spawn point on the middle of the block you're standing on, matching vanilla's world-spawn convention.
- The spawn marker gained a second layer: a slowly rising beacon column alongside the ring.

## [1.10.0]

### Fixed
- Rapid movement (e.g. falling repeatedly through the void) could occasionally trigger a teleport more than once at the same time, causing duplicated effects/messages.
- A boss bar shown during the countdown could disappear early if the countdown updated again before the previous boss bar had faded out.
- Changing worlds could, in rare setups, re-trigger the world-change teleport a second time.

### Changed
- General performance pass: cached frequently-read configuration values, moved data file writes off the main thread, and reduced redundant lookups in the most frequently firing event listeners.

## [1.9.0]

### Added
- Commands you don't have permission for no longer appear in tab-completion at all (except `/stonespawn help`, which still lists only what you're allowed to use).

## [1.8.0]

### Added
- An ambient particle ring now marks the spawn location itself, visible to everyone nearby — independent of anyone teleporting.

### Changed
- No arrival message/effects are shown on a player's very first ever join (a "welcome back" message didn't make sense for someone who's never been there before).

## [1.6.0]

### Changed
- Simplified the permission structure from eight separate nodes down to five: `stonespawn.use`, `stonespawn.admin`, `stonespawn.bypass`, `stonespawn.bypass.cooldown`, `stonespawn.bypass.delay`.

### Fixed
- The bundled German translation is now always extracted on first startup, regardless of which language is active — previously it only appeared once German was already selected.

## [1.4.0]

### Added
- Countdown and arrival messages can now be shown on chat, action bar, boss bar, or title/subtitle — configurable independently for each, with text for every channel kept ready at once so switching between them doesn't require retyping anything.
- Built-in update checker: checks Modrinth on startup and hourly, notifies admins in-game and in the console.

## [1.0.0]

### Added
- Initial release: set and teleport to a server spawn point, with automatic teleportation on join, death, falling into the void, or changing worlds.
- Teleport countdown with cancel-on-move, particle and sound effects, and a configurable delay.
- Command cooldowns, fall damage protection, and per-world whitelist/blacklist.
- Full color support (legacy codes, hex, and MiniMessage) usable at the same time in any message.
- English and German translations included, with automatic config/message migration on updates that never touches your existing settings.
