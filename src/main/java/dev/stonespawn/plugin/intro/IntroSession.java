package dev.stonespawn.plugin.intro;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.manager.MessageManager;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * One player watching the intro. Java players look through an invisible camera entity that glides from point to
 * point; Bedrock players can't follow one, so they - like any move into another world - jump to the next point.
 */
final class IntroSession {

    // The client smooths every camera move over this many ticks, so the flight doesn't stutter.
    private static final int CAMERA_SMOOTHING_TICKS = 2;
    // A new entity reaches the client with the next tracker update; a camera switch sent before that is ignored.
    private static final long ATTACH_DELAY_TICKS = 2L;
    private static final int SKIP_HINT_INTERVAL_TICKS = 40;

    private final StoneSpawn plugin;
    private final IntroManager manager;
    private final Player player;
    private final List<IntroPoint> points;
    private final Location returnTo;
    private final boolean jumpOnly;
    private final boolean skippable;

    private ItemDisplay camera;
    private BukkitTask task;
    private Location expectedTeleport;
    private boolean stopped;
    private int index;
    private boolean flying;
    private int elapsed;
    private int ticksRunning;

    IntroSession(StoneSpawn plugin, IntroManager manager, Player player, List<IntroPoint> points, boolean jumpOnly,
                 boolean skippable) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.points = points;
        this.returnTo = player.getLocation();
        this.jumpOnly = jumpOnly;
        this.skippable = skippable;
    }

    void start() {
        player.setGameMode(GameMode.SPECTATOR);
        jumpTo(points.getFirst());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    void stop() {
        stopped = true;
        if (task != null) {
            task.cancel();
        }
        removeCamera();
        player.clearTitle();
    }

    Location returnTo() {
        return returnTo.clone();
    }

    boolean skippable() {
        return skippable;
    }

    boolean isCamera(Entity entity) {
        return camera != null && entity == camera;
    }

    /** True once for the teleport this session started itself; anything else moving the player interrupts it. */
    boolean isOwnTeleport(Location to) {
        if (expectedTeleport == null || to == null || to.getWorld() != expectedTeleport.getWorld()
                || to.distanceSquared(expectedTeleport) > 0.01) {
            return false;
        }
        expectedTeleport = null;
        return true;
    }

    private void tick() {
        if (skippable && ticksRunning++ % SKIP_HINT_INTERVAL_TICKS == 0) {
            player.sendActionBar(plugin.getMessageManager().format(plugin.getMessageManager().getRaw("intro.skip-hint"), null));
        }
        IntroPoint current = points.get(index);
        if (!flying) {
            if (elapsed++ == 0) {
                show(current);
            }
            if (elapsed < current.stayTicks()) {
                return;
            }
            if (index + 1 >= points.size()) {
                manager.finish(player, IntroManager.End.COMPLETED);
                return;
            }
            IntroPoint next = points.get(index + 1);
            elapsed = 0;
            if (jumpOnly || camera == null || next.location().getWorld() != current.location().getWorld()) {
                index++;
                jumpTo(next);
            } else {
                flying = true;
            }
            return;
        }

        IntroPoint next = points.get(index + 1);
        elapsed++;
        if (!camera.isValid()) {
            // Removed by something else (e.g. a kill command): carry on with a new one.
            camera = spawnCamera(camera.getLocation());
            attach(camera);
        }
        camera.teleport(between(current.location(), next.location(), ease(Math.min(1.0, (double) elapsed / next.flyTicks()))));
        if (elapsed >= next.flyTicks()) {
            index++;
            flying = false;
            elapsed = 0;
        }
    }

    private void jumpTo(IntroPoint point) {
        removeCamera();
        expectedTeleport = point.location();
        player.teleportAsync(point.location()).thenRun(() -> {
            if (!stopped && !jumpOnly) {
                camera = spawnCamera(point.location());
                attach(camera);
            }
        });
    }

    private void attach(ItemDisplay newCamera) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!stopped && camera == newCamera && newCamera.isValid()) {
                player.setSpectatorTarget(newCamera);
            }
        }, ATTACH_DELAY_TICKS);
    }

    private ItemDisplay spawnCamera(Location location) {
        // An item display without an item is invisible; it is never saved with the world.
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setTeleportDuration(CAMERA_SMOOTHING_TICKS);
        });
    }

    private void removeCamera() {
        ItemDisplay old = camera;
        camera = null;
        if (old != null) {
            if (player.getSpectatorTarget() == old) {
                player.setSpectatorTarget(null);
            }
            old.remove();
        }
    }

    private void show(IntroPoint point) {
        MessageManager mm = plugin.getMessageManager();
        Map<String, String> placeholders = Map.of("player", player.getName());
        if (!point.title().isEmpty() || !point.subtitle().isEmpty()) {
            Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(point.stayTicks() * 50L),
                    Duration.ofMillis(500));
            player.showTitle(Title.title(mm.format(point.title(), placeholders), mm.format(point.subtitle(), placeholders), times));
        }
        for (String line : point.chat()) {
            player.sendMessage(mm.format(line, placeholders));
        }
        if (!point.sound().isBlank()) {
            Sound sound = plugin.getEffectManager().parseSound(point.sound(), null);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, point.soundVolume(), point.soundPitch());
            }
        }
    }

    private static Location between(Location from, Location to, double progress) {
        double yawDelta = ((to.getYaw() - from.getYaw()) % 360 + 540) % 360 - 180;
        return new Location(from.getWorld(),
                from.getX() + (to.getX() - from.getX()) * progress,
                from.getY() + (to.getY() - from.getY()) * progress,
                from.getZ() + (to.getZ() - from.getZ()) * progress,
                (float) (from.getYaw() + yawDelta * progress),
                (float) (from.getPitch() + (to.getPitch() - from.getPitch()) * progress));
    }

    // Starts and stops gently at every point instead of moving at constant speed.
    private static double ease(double progress) {
        return progress * progress * (3 - 2 * progress);
    }
}
