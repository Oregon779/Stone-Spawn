# StoneSpawn Changelog — 1.6.2 → 1.16.1

## [1.16.1]
### Changed
- Death and void teleportation are now **off by default** on new installs (`spawn.death.enabled` / `spawn.void.enabled`). Join-based and manual `/spawn` teleportation are unaffected.

## [1.16.0]
### Changed
- Reworked the arrival effect: particles now start scattered around the player on a sphere and gather inward as they land ("converge"), instead of orbiting.

## [1.15.1]
### Changed
- Softer, less saturated arrival effect color, shorter duration, quieter sound.
- New countdown sound: a clear bell tone instead of the portal ambience.

## [1.15.0]
### Changed
- Simplified the countdown and arrival particle effects to a single clean colored spiral each, replacing the combined multi-layer effects. Color is fully customizable via RGB (DUST particles).

## [1.14.0]
### Changed
- Countdown/arrival ring effects now wrap around the player's whole body (a vertical halo passing over the head and under the feet) instead of sitting flat on the ground.
- The ambient spawn marker is purple-only again (fire beacon layer removed).

## [1.13.0]
### Changed
- Removed the END_ROD particle from every effect. Countdown effects reworked again (`helix`/`ring`/`pulse`), arrival effects reworked again (`burst`/`shockwave`/`spiral`), using only PORTAL, WITCH, FLAME and FIREWORK.

## [1.12.0]
### Added
- `/setspawn` now centers the spawn point on the middle of the block you're standing on (matches vanilla's world-spawn convention).
- Spawn marker gained a second layer: a slowly rising beacon column alongside the ring.

## [1.11.0]
### Changed
- Countdown effects reworked to react to time remaining: a shrinking-radius "vortex," an accelerating "orbit," and a once-per-second "pulse" ring synced to the countdown.
- Arrival effects reworked: "shockwave" ring, upward "geyser," and falling "rain" of particles.
- `teleport.blindness-during-delay` now defaults to `false`.

## [1.10.0]
### Fixed
- Rapid movement (e.g. repeated void falls) could trigger a teleport more than once concurrently, duplicating effects/messages.
- A boss bar shown during the countdown could be hidden early by a stale scheduled task from an earlier update.
- Changing worlds could, in rare setups (spawn in a third world), re-trigger the world-change teleport a second time.
- The `/spawn` cooldown map grew forever with no cleanup on player quit.
### Changed
- Performance pass: cached the resolved spawn location, world whitelist/blacklist, and parsed chat messages; moved `data.yml` writes off the main thread; reordered the most frequently firing event listener checks to reject cheaply before touching config or world lookups; reused a single `HttpClient` for the update checker instead of creating a new one per check.

## [1.9.0]
### Added
- Commands you don't have permission for no longer appear in tab-completion at all (except `/stonespawn help`, which still lists only what you're allowed to use).

## [1.8.1]
### Changed
- No arrival message/effects on a player's very first ever join — a "welcome back" message didn't make sense for someone who'd never been there before. Later joins are unaffected.

## [1.8.0]
### Added
- An ambient particle ring now marks the spawn location itself, visible to everyone nearby, independent of anyone teleporting.

## [1.7.0]
### Changed
- Countdown and arrival effects reworked into combined multi-particle layers instead of a single spiral: `spiral`/`ring`/`beam` for the countdown, `burst`/`ring`/`spiral` for arrival.
- Reworked default messages, sounds, and volumes to match.

## [1.6.3]
### Added
- `WIKI.md` with a full overview: installation, commands, permissions, configuration, update checker, FAQ.
### Fixed
- A stale config.yml comment still referencing a permission node removed in 1.6.0.
