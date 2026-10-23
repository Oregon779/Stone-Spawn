package dev.stonespawn.plugin.intro;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.config.ConfigSchema;
import dev.stonespawn.plugin.config.ConfigUpdater;
import dev.stonespawn.plugin.manager.MessageManager;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The intro for first-time players: starts and ends {@link IntroSession}s and manages the camera points in intro.yml.
 * While a player watches it, the game mode and position to return to are stored on the player, so a server crash
 * mid-intro can't leave them stuck in spectator mode.
 */
public class IntroManager {

    private static final String RESOURCE_PATH = "intro.yml";
    private static final int JOIN_TELEPORT_RETRIES = 20;
    private static final long JOIN_TELEPORT_RETRY_TICKS = 10L;
    private static final int PREVIEW_SECONDS = 60;
    private static final long PREVIEW_REFRESH_TICKS = 10L;
    // Enough dots for a smooth line without flooding the viewer with particles on long intros.
    private static final int PREVIEW_MAX_DOTS = 400;
    private static final Particle.DustOptions PATH_DUST = new Particle.DustOptions(Color.fromRGB(85, 198, 255), 0.8f);
    private static final Particle.DustOptions POINT_DUST = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 2.0f);
    private static final Particle.DustOptions TARGET_DUST = new Particle.DustOptions(Color.fromRGB(90, 255, 120), 2.0f);

    private final StoneSpawn plugin;
    private final NamespacedKey gameModeKey;
    private final NamespacedKey returnKey;
    private final Map<UUID, IntroSession> sessions = new HashMap<>();
    private final Set<UUID> returning = new HashSet<>();
    private final Map<UUID, BukkitTask> previews = new HashMap<>();
    private final Set<String> reportedPoints = new HashSet<>();
    private YamlConfiguration intro;

    public IntroManager(StoneSpawn plugin) {
        this.plugin = plugin;
        this.gameModeKey = new NamespacedKey(plugin, "intro_game_mode");
        this.returnKey = new NamespacedKey(plugin, "intro_return");
    }

    public void load() {
        intro = ConfigUpdater.load(plugin, RESOURCE_PATH, file(), ConfigSchema.INTRO);
        reportedPoints.clear();
    }

    private File file() {
        return new File(plugin.getDataFolder(), RESOURCE_PATH);
    }

    /** True for players new to the server while the intro is switched on. */
    public boolean playsOnFirstJoin(Player player) {
        return intro.getBoolean("enabled", false) && !player.hasPlayedBefore();
    }

    /** Starts the intro once the player's world has had time to load. Without camera points only the book opens. */
    public void startAfterJoin(Player player) {
        startAfterJoin(player, Math.max(1, intro.getInt("start-delay-ticks", 40)), JOIN_TELEPORT_RETRIES);
    }

    // The intro returns players to where it started, so it waits until the first-join teleport has arrived.
    private void startAfterJoin(Player player, long delay, int retries) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (retries > 0 && plugin.getTeleportManager().isTeleporting(player.getUniqueId())) {
                startAfterJoin(player, JOIN_TELEPORT_RETRY_TICKS, retries - 1);
            } else if (start(player, true) == StartResult.NO_POINTS) {
                openBook(player);
                runCommands(intro.getStringList("end-commands"), player);
            }
        }, delay);
    }

    /** Plays the intro for a player, e.g. to test it; rewards in end-commands are only given on the first join. */
    public StartResult start(Player player) {
        return start(player, false);
    }

    private StartResult start(Player player, boolean firstJoin) {
        if (sessions.containsKey(player.getUniqueId())) {
            return StartResult.ALREADY_RUNNING;
        }
        List<IntroPoint> points = points();
        if (points.isEmpty()) {
            return StartResult.NO_POINTS;
        }
        boolean jumpOnly = plugin.getBedrockDetector().isBedrock(player);
        IntroPath path = new IntroPath(points, curved(), jumpOnly, IntroPoint.parseTime(intro.getString("time", "")));
        IntroSession session = new IntroSession(plugin, this, player, path, intro, jumpOnly, firstJoin);
        sessions.put(player.getUniqueId(), session);
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.set(gameModeKey, PersistentDataType.STRING, player.getGameMode().name());
        data.set(returnKey, PersistentDataType.STRING, encode(session.returnTo()));
        session.start();
        return StartResult.STARTED;
    }

    public void finish(Player player, End end) {
        IntroSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.stop();
        UUID id = player.getUniqueId();
        switch (end) {
            case COMPLETED, SKIPPED -> {
                returning.add(id);
                player.teleportAsync(returnLocation(player, session)).whenComplete((success, error) -> {
                    if (!plugin.isEnabled()) {
                        returning.remove(id);
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!Boolean.TRUE.equals(success) && player.isOnline()) {
                            player.teleport(fallbackLocation(player));
                        }
                        returning.remove(id);
                        restoreGameMode(player);
                        openBook(player);
                        if (session.firstJoin()) {
                            runCommands(intro.getStringList("end-commands"), player);
                        }
                    });
                });
            }
            case QUIT, SHUTDOWN -> {
                returning.add(id);
                if (!player.teleport(returnLocation(player, session))) {
                    player.teleport(fallbackLocation(player));
                }
                returning.remove(id);
                restoreGameMode(player);
            }
            case INTERRUPTED -> restoreGameMode(player);
            case GAME_MODE_CHANGED -> forget(player);
        }
    }

    /** Puts a player back who was watching the intro when the server stopped without a clean shutdown. */
    public void recover(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (!data.has(gameModeKey, PersistentDataType.STRING)) {
            return;
        }
        Location back = decode(data.get(returnKey, PersistentDataType.STRING));
        player.teleport(back != null ? back : fallbackLocation(player));
        restoreGameMode(player);
        plugin.getLogger().info("Brought " + player.getName() + " back from an intro that was cut off by a server stop.");
    }

    /** True while the player watches the intro or is on the way back, so other automatic teleports stay out of it. */
    public boolean isBusy(Player player) {
        return sessions.containsKey(player.getUniqueId()) || returning.contains(player.getUniqueId());
    }

    private Location returnLocation(Player player, IntroSession session) {
        Location back = session.returnTo();
        return back.isWorldLoaded() ? back : fallbackLocation(player);
    }

    // Used when the way back is gone (e.g. its world was unloaded), so nobody is left in mid-air at the camera.
    private Location fallbackLocation(Player player) {
        Location spawn = plugin.getSpawnManager().getSpawn();
        return spawn != null ? spawn : player.getWorld().getSpawnLocation();
    }

    public void shutdown() {
        previews.values().forEach(BukkitTask::cancel);
        previews.clear();
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                finish(player, End.SHUTDOWN);
            }
        }
    }

    IntroSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    private void restoreGameMode(Player player) {
        String stored = player.getPersistentDataContainer().get(gameModeKey, PersistentDataType.STRING);
        forget(player);
        GameMode mode = Bukkit.getDefaultGameMode();
        try {
            if (stored != null) {
                mode = GameMode.valueOf(stored);
            }
        } catch (IllegalArgumentException ignored) {
            // Unknown value: the server's default game mode is the safe choice.
        }
        player.setGameMode(mode);
    }

    private void forget(Player player) {
        player.getPersistentDataContainer().remove(gameModeKey);
        player.getPersistentDataContainer().remove(returnKey);
    }

    private void openBook(Player player) {
        ConfigurationSection book = intro.getConfigurationSection("book");
        if (book == null || !book.getBoolean("enabled", true) || book.getStringList("pages").isEmpty()) {
            return;
        }
        MessageManager mm = plugin.getMessageManager();
        Map<String, String> placeholders = Map.of("player", player.getName());
        List<Component> pages = book.getStringList("pages").stream().map(page -> mm.format(page, placeholders)).toList();
        player.openBook(Book.book(mm.format(book.getString("title", ""), placeholders),
                mm.format(book.getString("author", ""), placeholders), pages));
    }

    // ---------------------------------------------------------------- camera points

    private List<IntroPoint> points() {
        ConfigurationSection section = intro.getConfigurationSection("points");
        if (section == null) {
            return List.of();
        }
        double fly = intro.getDouble("fly-seconds", 4.0);
        double stay = intro.getDouble("stay-seconds", 3.0);
        List<IntroPoint> points = new ArrayList<>();
        for (String name : section.getKeys(false)) {
            ConfigurationSection spec = section.getConfigurationSection(name);
            IntroPoint point = spec == null ? null : IntroPoint.read(name, spec, fly, stay);
            if (point == null) {
                if (reportedPoints.add(name)) {
                    plugin.getLogger().warning("Intro point '" + name + "' is skipped: its world isn't loaded.");
                }
                continue;
            }
            points.add(point);
        }
        return points;
    }

    public List<String> pointNames() {
        ConfigurationSection section = intro.getConfigurationSection("points");
        return section == null ? List.of() : List.copyOf(section.getKeys(false));
    }

    /** Placeholders for listing every point in order: number, point, world, x, y, z. */
    public List<Map<String, String>> describePoints() {
        List<Map<String, String>> result = new ArrayList<>();
        int number = 1;
        for (String name : pointNames()) {
            ConfigurationSection spec = intro.getConfigurationSection("points." + name);
            Map<String, String> info = new HashMap<>();
            info.put("number", String.valueOf(number++));
            info.put("point", name);
            info.put("world", spec == null ? "?" : spec.getString("world", "?"));
            for (String axis : List.of("x", "y", "z")) {
                info.put(axis, spec == null ? "?" : String.format(Locale.ROOT, "%.1f", spec.getDouble(axis)));
            }
            result.add(info);
        }
        return result;
    }

    /** Saves a camera point at the given eye position and view (moving it if it exists; its texts stay). */
    public PointResult setPoint(String name, Location eye) {
        boolean added = !pointNames().contains(name);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("world", eye.getWorld().getName());
        values.put("x", round(eye.getX(), 100));
        values.put("y", round(eye.getY(), 100));
        values.put("z", round(eye.getZ(), 100));
        values.put("yaw", round(Location.normalizeYaw(eye.getYaw()), 10));
        values.put("pitch", round(eye.getPitch(), 10));
        if (!ConfigUpdater.setValues(plugin, file(), "points." + name, values)) {
            return PointResult.SAVE_FAILED;
        }
        load();
        return added ? PointResult.ADDED : PointResult.MOVED;
    }

    /** The world a point was saved in, or null if there is no such point. */
    public String pointWorld(String name) {
        return pointNames().contains(name) ? intro.getString("points." + name + ".world", "") : null;
    }

    /** Makes the camera keep looking at {@code target} at this point (null: back to the saved view). */
    public PointResult setLookAt(String name, Vector target) {
        if (!pointNames().contains(name)) {
            return PointResult.NOT_FOUND;
        }
        String path = "points." + name + ".look-at";
        boolean saved;
        if (target == null) {
            saved = !intro.contains(path) || ConfigUpdater.removeKey(plugin, file(), path);
        } else {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("x", round(target.getX(), 100));
            values.put("y", round(target.getY(), 100));
            values.put("z", round(target.getZ(), 100));
            saved = ConfigUpdater.setValues(plugin, file(), path, values);
        }
        if (!saved) {
            return PointResult.SAVE_FAILED;
        }
        load();
        return PointResult.MOVED;
    }

    public boolean removePoint(String name) {
        boolean removed = ConfigUpdater.removeKey(plugin, file(), "points." + name);
        if (removed) {
            load();
        }
        return removed;
    }

    private boolean curved() {
        return !"STRAIGHT".equalsIgnoreCase(intro.getString("path", "CURVE").trim());
    }

    /** Runs console commands for a player of the intro; {player} is their name. A failing command doesn't stop the intro. */
    void runCommands(List<String> commands, Player player) {
        for (String command : commands) {
            String line = command.trim().replace("{player}", player.getName());
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            if (line.isEmpty()) {
                continue;
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Intro command '" + line + "' failed: " + ex.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- path preview for admins

    /** Shows (or hides again) the camera's path as particles, only to {@code viewer}. */
    public PreviewResult togglePreview(Player viewer) {
        BukkitTask running = previews.remove(viewer.getUniqueId());
        if (running != null) {
            running.cancel();
            return PreviewResult.HIDDEN;
        }
        List<IntroPoint> points = points();
        if (points.isEmpty()) {
            return PreviewResult.NO_POINTS;
        }
        IntroPath path = new IntroPath(points, curved(), false, null);
        List<Location> trail = new ArrayList<>();
        int step = Math.max(2, path.totalTicks() / PREVIEW_MAX_DOTS);
        for (int tick = 0; tick < path.totalTicks(); tick += step) {
            trail.add(path.frame(tick).camera());
        }
        UUID id = viewer.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int runs;

            @Override
            public void run() {
                if (!viewer.isOnline() || runs++ >= PREVIEW_SECONDS * 20 / PREVIEW_REFRESH_TICKS) {
                    BukkitTask self = previews.remove(id);
                    if (self != null) {
                        self.cancel();
                    }
                    return;
                }
                World world = viewer.getWorld();
                for (Location spot : trail) {
                    if (spot.getWorld() == world) {
                        viewer.spawnParticle(Particle.DUST, spot, 1, 0, 0, 0, 0, PATH_DUST, true);
                    }
                }
                for (IntroPoint point : points) {
                    if (point.location().getWorld() != world) {
                        continue;
                    }
                    viewer.spawnParticle(Particle.DUST, point.location(), 4, 0.15, 0.15, 0.15, 0, POINT_DUST, true);
                    if (point.lookAt() != null) {
                        viewer.spawnParticle(Particle.DUST, point.lookAt().toLocation(world), 4, 0.15, 0.15, 0.15, 0,
                                TARGET_DUST, true);
                    }
                }
            }
        }, 0L, PREVIEW_REFRESH_TICKS);
        previews.put(id, task);
        return PreviewResult.SHOWN;
    }

    private static double round(double value, int factor) {
        return Math.round(value * factor) / (double) factor;
    }

    private static String encode(Location location) {
        return location.getWorld().getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ()
                + ";" + location.getYaw() + ";" + location.getPitch();
    }

    private static Location decode(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(";");
        World world = parts.length == 6 ? Bukkit.getWorld(parts[0]) : null;
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public enum StartResult {
        STARTED, ALREADY_RUNNING, NO_POINTS
    }

    public enum PointResult {
        ADDED, MOVED, NOT_FOUND, SAVE_FAILED
    }

    public enum PreviewResult {
        SHOWN, HIDDEN, NO_POINTS
    }

    public enum End {
        /** All points shown: back to the start, then the book. */
        COMPLETED,
        /** Ended with Shift: same as completed. */
        SKIPPED,
        /** Teleported away by something else: only the game mode is restored. */
        INTERRUPTED,
        /** Game mode changed by something else: that game mode is kept. */
        GAME_MODE_CHANGED,
        QUIT,
        SHUTDOWN
    }
}
