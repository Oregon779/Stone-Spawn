package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EffectManager {

    private final StoneSpawn plugin;
    private final Map<UUID, BukkitTask> countdownTasks = new HashMap<>();
    private BukkitTask spawnMarkerTask;

    public EffectManager(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    public void startCountdownEffects(Player player, int totalSeconds) {
        stopCountdownEffects(player.getUniqueId());

        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.getBoolean("teleport.countdown.spiral.enabled", true)) {
            return;
        }

        double radius = cfg.getDouble("teleport.countdown.spiral.radius", 0.6);
        double height = cfg.getDouble("teleport.countdown.spiral.height", 1.8);
        double speed = cfg.getDouble("teleport.countdown.spiral.rotation-speed-degrees", 20.0);
        int count = Math.max(1, cfg.getInt("teleport.countdown.spiral.particle-count-per-point", 2));
        Particle.DustOptions dust = readDustOptions(cfg, "teleport.countdown.spiral.color", "teleport.countdown.spiral.size",
                120, 190, 255, 1.2f);

        BukkitTask task = new BukkitRunnable() {
            double angle = 0.0;
            double y = 0.0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopCountdownEffects(player.getUniqueId());
                    return;
                }

                Location base = player.getLocation();
                double angleRad = Math.toRadians(angle);
                spawnDust(player, base, angleRad, radius, y, dust, count);
                spawnDust(player, base, angleRad + Math.PI, radius, y, dust, count);

                angle = (angle + speed) % 360.0;
                y += height / 20.0;
                if (y > height) {
                    y = 0.0;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        countdownTasks.put(player.getUniqueId(), task);
    }

    public void stopCountdownEffects(UUID playerId) {
        BukkitTask task = countdownTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public void playCountdownTick(Player player, int secondsRemaining, int totalSeconds) {
        ConfigManager cfg = plugin.getConfigManager();
        Sound sound = parseSound(cfg.getString("teleport.countdown.sound", "BLOCK_NOTE_BLOCK_BELL"), Sound.BLOCK_NOTE_BLOCK_BELL);
        float volume = (float) cfg.getDouble("teleport.countdown.sound-volume", 0.5);
        float pitchStart = (float) cfg.getDouble("teleport.countdown.sound-pitch-start", 0.7);
        float pitchEnd = (float) cfg.getDouble("teleport.countdown.sound-pitch-end", 1.6);

        float progress = totalSeconds <= 0 ? 1f : 1f - ((float) secondsRemaining / totalSeconds);
        float pitch = pitchStart + (pitchEnd - pitchStart) * Math.max(0f, Math.min(1f, progress));

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public void playArrivalEffect(Player player) {
        ConfigManager cfg = plugin.getConfigManager();

        Sound sound = parseSound(cfg.getString("teleport.arrival.sound", "ENTITY_ENDERMAN_TELEPORT"), Sound.ENTITY_ENDERMAN_TELEPORT);
        float volume = (float) cfg.getDouble("teleport.arrival.sound-volume", 0.5);
        float pitch = (float) cfg.getDouble("teleport.arrival.sound-pitch", 1.1);
        player.playSound(player.getLocation(), sound, volume, pitch);

        if (!cfg.getBoolean("teleport.arrival.converge.enabled", true)) {
            return;
        }

        Location base = player.getLocation();
        double startRadius = cfg.getDouble("teleport.arrival.converge.radius", 1.8);
        double heightCenter = cfg.getDouble("teleport.arrival.converge.height-center", 1.0);
        int particleTotal = Math.max(1, cfg.getInt("teleport.arrival.converge.particles", 36));
        int durationTicks = Math.max(1, cfg.getInt("teleport.arrival.converge.duration-ticks", 20));
        Particle.DustOptions dust = readDustOptions(cfg, "teleport.arrival.converge.color", "teleport.arrival.converge.size",
                255, 240, 215, 0.9f);

        double[][] directions = new double[particleTotal][3];
        for (int i = 0; i < particleTotal; i++) {
            double theta = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * ThreadLocalRandom.current().nextDouble() - 1);
            directions[i][0] = Math.sin(phi) * Math.cos(theta);
            directions[i][1] = Math.cos(phi);
            directions[i][2] = Math.sin(phi) * Math.sin(theta);
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= durationTicks || !player.isOnline()) {
                    cancel();
                    return;
                }

                double currentRadius = startRadius * (1.0 - ((double) ticks / durationTicks));
                for (double[] dir : directions) {
                    double x = dir[0] * currentRadius;
                    double y = heightCenter + dir[1] * currentRadius;
                    double z = dir[2] * currentRadius;
                    Location point = base.clone().add(x, y, z);
                    player.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Particle.DustOptions readDustOptions(ConfigManager cfg, String colorPath, String sizePath,
                                                  int defaultRed, int defaultGreen, int defaultBlue, float defaultSize) {
        int red = clampColor(cfg.getInt(colorPath + ".red", defaultRed));
        int green = clampColor(cfg.getInt(colorPath + ".green", defaultGreen));
        int blue = clampColor(cfg.getInt(colorPath + ".blue", defaultBlue));
        float size = (float) cfg.getDouble(sizePath, defaultSize);
        return new Particle.DustOptions(Color.fromRGB(red, green, blue), Math.max(0.1f, size));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void spawnDust(Player player, Location base, double angleRad, double radius, double yOffset,
                            Particle.DustOptions dust, int count) {
        double x = Math.cos(angleRad) * radius;
        double z = Math.sin(angleRad) * radius;
        Location point = base.clone().add(x, yOffset, z);
        player.spawnParticle(Particle.DUST, point, count, 0, 0, 0, 0, dust);
    }

    public void startSpawnMarker() {
        stopSpawnMarker();

        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.getBoolean("spawn.marker.enabled", true)) {
            return;
        }
        if (!plugin.getSpawnManager().hasSpawn()) {
            return;
        }

        Particle particle = parseParticle(cfg.getString("spawn.marker.particle", "PORTAL"), Particle.PORTAL);
        double radius = cfg.getDouble("spawn.marker.radius", 1.5);
        int points = Math.max(4, cfg.getInt("spawn.marker.points", 24));
        double rotationSpeed = cfg.getDouble("spawn.marker.rotation-speed-degrees", 6.0);
        double heightOffset = cfg.getDouble("spawn.marker.height-offset", 0.1);
        int intervalTicks = Math.max(1, cfg.getInt("spawn.marker.interval-ticks", 4));
        int particleCount = Math.max(1, cfg.getInt("spawn.marker.particle-count-per-point", 1));

        spawnMarkerTask = new BukkitRunnable() {
            double angle = 0.0;

            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().isEmpty()) {
                    return;
                }
                Location spawn = plugin.getSpawnManager().getSpawn();
                if (spawn == null || spawn.getWorld() == null) {
                    return;
                }

                double angleRad = Math.toRadians(angle);
                for (int i = 0; i < points; i++) {
                    double a = angleRad + (2 * Math.PI * i / points);
                    double x = Math.cos(a) * radius;
                    double z = Math.sin(a) * radius;
                    Location point = spawn.clone().add(x, heightOffset, z);
                    spawn.getWorld().spawnParticle(particle, point, particleCount, 0, 0, 0, 0);
                }

                angle = (angle + rotationSpeed) % 360.0;
            }
        }.runTaskTimer(plugin, 20L, intervalTicks);
    }

    public void stopSpawnMarker() {
        if (spawnMarkerTask != null) {
            spawnMarkerTask.cancel();
            spawnMarkerTask = null;
        }
    }

    private Particle parseParticle(String name, Particle fallback) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid particle '" + name + "' in config.yml, using " + fallback + " instead.");
            return fallback;
        }
    }

    private Sound parseSound(String name, Sound fallback) {
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid sound '" + name + "' in config.yml, using " + fallback + " instead.");
            return fallback;
        }
    }
}
