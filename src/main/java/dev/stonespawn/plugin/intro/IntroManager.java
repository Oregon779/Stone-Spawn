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
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final StoneSpawn plugin;
    private final NamespacedKey gameModeKey;
    private final NamespacedKey returnKey;
    private final Map<UUID, IntroSession> sessions = new HashMap<>();
    private YamlConfiguration intro;

    public IntroManager(StoneSpawn plugin) {
        this.plugin = plugin;
        this.gameModeKey = new NamespacedKey(plugin, "intro_game_mode");
        this.returnKey = new NamespacedKey(plugin, "intro_return");
    }

    public void load() {
        intro = ConfigUpdater.load(plugin, RESOURCE_PATH, file(), ConfigSchema.INTRO);
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
            } else if (start(player) == StartResult.NO_POINTS) {
                openBook(player);
            }
        }, delay);
    }

    public StartResult start(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            return StartResult.ALREADY_RUNNING;
        }
        List<IntroPoint> points = points();
        if (points.isEmpty()) {
            return StartResult.NO_POINTS;
        }
        IntroSession session = new IntroSession(plugin, this, player, points,
                plugin.getBedrockDetector().isBedrock(player), intro.getBoolean("skippable", true));
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
        switch (end) {
            case COMPLETED, SKIPPED -> player.teleportAsync(session.returnTo()).whenComplete((success, error) -> {
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        restoreGameMode(player);
                        openBook(player);
                    });
                }
            });
            case QUIT, SHUTDOWN -> {
                player.teleport(session.returnTo());
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
        if (back != null) {
            player.teleport(back);
        }
        restoreGameMode(player);
        plugin.getLogger().info("Brought " + player.getName() + " back from an intro that was cut off by a server stop.");
    }

    public void shutdown() {
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
                plugin.getLogger().warning("Intro point '" + name + "' is skipped: its world isn't loaded.");
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

    /** Saves a camera point at the given eye position and view; returns false if it only moved an existing point. */
    public boolean setPoint(String name, Location eye) {
        boolean added = !pointNames().contains(name);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("world", eye.getWorld().getName());
        values.put("x", round(eye.getX(), 100));
        values.put("y", round(eye.getY(), 100));
        values.put("z", round(eye.getZ(), 100));
        values.put("yaw", round(Location.normalizeYaw(eye.getYaw()), 10));
        values.put("pitch", round(eye.getPitch(), 10));
        ConfigUpdater.setValues(plugin, file(), "points." + name, values);
        load();
        return added;
    }

    public boolean removePoint(String name) {
        boolean removed = ConfigUpdater.removeKey(plugin, file(), "points." + name);
        if (removed) {
            load();
        }
        return removed;
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
