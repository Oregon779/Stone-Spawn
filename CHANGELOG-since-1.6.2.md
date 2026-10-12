# StoneSpawn - Changes since 1.6.2

> **Requirements changed:** this version needs **Paper 26.2 or newer** and **Java 25**. Older Minecraft versions, Spigot and Folia are not supported.

## Highlights

- **Multiple spawns** with a fully customizable **spawn selection GUI**
- **Elytra glide**: players glide down from the spawn island - no elytra needed - with an optional rocket boost, for Java **and Bedrock** players
- **Reworked teleport effects**: colored particle spiral, rising bell sound, and an arrival effect where particles gather around the player
- **Safer data storage** and much better **performance on big servers**

---

## New

### Multiple spawns
- `/setspawn <name>` creates additional named spawns, `/delspawn <name>` deletes them.
- `/spawn <name>` teleports to a named spawn, `/spawn <spawn> <player>` sends another player there.
- `/setspawn` without a name still sets the main spawn, which is used for all automatic teleports (join, death, void, world change).
- Every spawn gets its own particle ring.
- Your existing spawn is taken over automatically as the main spawn.

### Spawn selection GUI
- With more than one spawn, `/spawn` opens a selection menu (can be turned off with `command.spawn-gui: false`).
- Everything is configurable in the new `gui.yml`: title, size, slot layout, item material/name/lore/glow, custom item models for resource packs, per-spawn items with fixed slots or hidden spawns, filler items, extra items like a close button, and sounds.
- Clicking a spawn uses the same rules as the command (cooldown, countdown, world restrictions).

### Elytra glide from the spawn island
- Players who jump off the area around a spawn (`elytra.radius`) start gliding like with an elytra - **no item needed**. Landing ends the glide; it works again every time they jump off.
- Optional **rocket boost** while gliding (default: once per glide) on `F`, `Shift` or left click.
- Fall and "flew into a wall" damage are cancelled while gliding and right after landing.
- **Bedrock support (Geyser/Floodgate):** Bedrock players get a temporary, unbreakable elytra while gliding, because Bedrock clients can only glide with a real one. Their own chestplate is stored inside it and given back on landing - also after logging out, dying or a server crash. Bedrock players boost with sneak (`elytra.boost.bedrock-trigger`).
- **Off by default** - enable with `elytra.enabled: true`. New permission `stonespawn.elytra` (everyone by default).

### Other additions
- An ambient **particle ring** marks every spawn point permanently.
- `/setspawn` centers the spawn on the middle of the block you're standing on.
- Commands you can't use are hidden from tab-completion.

---

## Changed

- **Teleport effects reworked:** the countdown shows a colored double spiral around the player with a bell sound that rises in pitch; on arrival, particles gather inward around the player. Both colors are freely configurable (RGB).
- **New defaults on fresh installs:** death and void teleports are off, and blindness during the countdown is off. Existing configs keep their values.
- No arrival message or effect on a player's very first join.
- `/stonespawn reload` now also reloads `gui.yml` and the update checker settings.
- When updating, new config options are added **together with their explanation comments**.
- Death respawns now respect `teleport.arrival.message-enabled` and `effects-enabled`.

---

## Fixed

- **Spawn data could get lost or corrupted**: saves could overwrite each other, a crash while saving could leave a broken `data.yml`, an unreadable file was replaced with an empty one, and changes made shortly before a restart were not saved. All fixed - an unreadable file is now kept as a backup (`data.yml.broken-<time>`) instead.
- Spawn teleports removed **blindness from other plugins** (e.g. login plugins) even when the blindness option was off.
- The `/spawn` cooldown could be skipped by reconnecting.
- Using `DUST` as spawn marker particle flooded the console with errors.
- A misspelled sound name in the config spammed a warning every second per player.
- Two teleports in quick succession could end the fall damage protection too early.
- A teleport could trigger twice on fast movement (e.g. repeated void falls), duplicating effects and messages.
- A countdown boss bar could disappear too early, or stay on screen after a reload.
- Changing worlds could re-trigger the world-change teleport in some setups.
- Messages that repeat typed command input no longer interpret color codes or formatting from it.

---

## Performance

- **No more lag spikes when new players join.** Every first join used to rewrite the whole data file on the main thread - with many known players this took well over 100 ms. Saving now happens in the background and is batched.
- The spawn marker only sends particles to players near a spawn and skips spawns nobody is near.
- Spawn locations, config values, messages and sound/particle names are cached instead of being looked up again on every use.
- The countdown boss bar is updated instead of being replaced every second.

---

## Updating from 1.6.2

1. Make sure your server runs **Paper 26.2+ on Java 25**.
2. Replace the jar and restart.
3. Your settings, messages and spawn point are kept. New options are added to `config.yml`, `gui.yml` and the language files automatically.
4. The elytra glide stays off until you set `elytra.enabled: true`. The selection GUI only appears once you create a second spawn.
