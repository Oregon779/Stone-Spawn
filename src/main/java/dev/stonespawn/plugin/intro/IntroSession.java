package dev.stonespawn.plugin.intro;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.manager.MessageManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.WeatherType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * One player watching the intro, following the timeline of an {@link IntroPath}. Java players look through an
 * invisible camera entity that glides along the path; Bedrock players can't follow one, so the path cuts to every
 * point for them. Titles, chat lines, sounds, commands, weather and the chapter in the boss bar change whenever the
 * camera reaches a point; the time of day changes smoothly along the way.
 */
final class IntroSession {

    // The client smooths every camera move over this many ticks, so the flight doesn't stutter.
    private static final int CAMERA_SMOOTHING_TICKS = 2;
    // A new entity reaches the client with the next tracker update; a camera switch sent before that is ignored.
    private static final long ATTACH_DELAY_TICKS = 2L;
    private static final int SKIP_HINT_INTERVAL_TICKS = 40;
    // Titles of points flown through stay readable for at least this long.
    private static final int MIN_TITLE_TICKS = 60;

    private final StoneSpawn plugin;
    private final IntroManager manager;
    private final Player player;
    private final IntroPath path;
    private final ConfigurationSection settings;
    private final Location returnTo;
    private final boolean jumpOnly;
    private final boolean skippable;
    private final boolean firstJoin;
    private final Map<String, String> placeholders;

    private ItemDisplay camera;
    private BukkitTask task;
    private Location expectedTeleport;
    private boolean stopped;
    private int tick;
    private int pointIndex = -1;
    private Long shownTime;
    private BossBar bossBar;
    private String chapter = "";
    private boolean changedWeather;
    private Component typing;
    private int typed;
    private int typingLength;

    IntroSession(StoneSpawn plugin, IntroManager manager, Player player, IntroPath path, ConfigurationSection settings,
                 boolean jumpOnly, boolean firstJoin) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.path = path;
        this.settings = settings;
        this.returnTo = player.getLocation();
        this.jumpOnly = jumpOnly;
        this.skippable = settings.getBoolean("skippable", true);
        this.firstJoin = firstJoin;
        this.placeholders = Map.of("player", player.getName());
    }

    void start() {
        player.setGameMode(GameMode.SPECTATOR);
        WeatherType weather = IntroPoint.parseWeather(settings.getString("weather", ""));
        if (weather != null) {
            player.setPlayerWeather(weather);
            changedWeather = true;
        }
        startMusic();
        if (settings.getBoolean("bossbar.enabled", true)) {
            bossBar = BossBar.bossBar(Component.empty(), 0f, color(), overlay());
            player.showBossBar(bossBar);
        }
        tick();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    void stop() {
        stopped = true;
        if (task != null) {
            task.cancel();
        }
        removeCamera();
        player.clearTitle();
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
        stopMusic();
        // Only what the intro changed, so a personal time set by another plugin stays.
        if (shownTime != null) {
            player.resetPlayerTime();
        }
        if (changedWeather) {
            player.resetPlayerWeather();
        }
    }

    Location returnTo() {
        return returnTo.clone();
    }

    boolean skippable() {
        return skippable;
    }

    boolean firstJoin() {
        return firstJoin;
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
        if (stopped) {
            return;
        }
        if (tick >= path.totalTicks()) {
            manager.finish(player, IntroManager.End.COMPLETED);
            return;
        }
        IntroPath.Frame frame = path.frame(tick);
        if (frame.cut()) {
            cutTo(frame.camera());
        } else if (camera != null) {
            if (!camera.isValid()) {
                // Removed by something else (e.g. a kill command): carry on with a new one.
                camera = spawnCamera(camera.getLocation());
                attach(camera);
            }
            camera.teleport(frame.camera());
        }
        if (frame.pointIndex() != pointIndex) {
            pointIndex = frame.pointIndex();
            arrive(path.points().get(pointIndex));
            if (stopped) {
                // A command of the point ended the intro, e.g. by teleporting the player.
                return;
            }
        }
        if (frame.time() != null && !frame.time().equals(shownTime)) {
            shownTime = frame.time();
            player.setPlayerTime(shownTime, false);
        }
        typeNextLetter();
        updateBossBar();
        if (skippable && tick % SKIP_HINT_INTERVAL_TICKS == 0) {
            player.sendActionBar(mm().format(mm().getRaw("intro.skip-hint"), null));
        }
        tick++;
    }

    // ---------------------------------------------------------------- camera

    private void cutTo(Location location) {
        removeCamera();
        expectedTeleport = location;
        player.teleportAsync(location).thenRun(() -> {
            if (!stopped && !jumpOnly) {
                camera = spawnCamera(location);
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

    // ---------------------------------------------------------------- what the viewer sees and hears at a point

    private void arrive(IntroPoint point) {
        MessageManager mm = mm();
        if (!point.chapter().isEmpty()) {
            chapter = point.chapter();
        }
        if (!point.title().isEmpty() || !point.subtitle().isEmpty()) {
            showTitle(point);
        }
        for (String line : point.chat()) {
            player.sendMessage(mm.format(line, placeholders));
        }
        if (!point.sound().isBlank()) {
            Sound sound = plugin.getEffectManager().parseSound(point.sound(), null);
            if (sound != null) {
                player.playSound(player, sound, SoundCategory.MASTER, point.soundVolume(), point.soundPitch());
            }
        }
        if (point.weather() != null) {
            player.setPlayerWeather(point.weather());
            changedWeather = true;
        }
        manager.runCommands(point.commands(), player);
    }

    private void showTitle(IntroPoint point) {
        Component title = mm().format(point.title(), placeholders);
        Component subtitle = mm().format(point.subtitle(), placeholders);
        Title.Times times = Title.Times.times(Duration.ofMillis(500),
                Duration.ofMillis(Math.max(point.stayTicks(), MIN_TITLE_TICKS) * 50L), Duration.ofMillis(500));
        boolean animate = settings.getBoolean("typewriter.enabled", true) && Typewriter.length(subtitle) > 0;
        player.showTitle(Title.title(title, animate ? Component.empty() : subtitle, times));
        typing = animate ? subtitle : null;
        typed = 0;
        typingLength = Typewriter.length(subtitle);
    }

    private void typeNextLetter() {
        if (typing == null || tick % Math.max(1, settings.getInt("typewriter.ticks-per-letter", 1)) != 0) {
            return;
        }
        typed++;
        player.sendTitlePart(TitlePart.SUBTITLE, Typewriter.cut(typing, typed));
        String plain = PlainTextComponentSerializer.plainText().serialize(typing);
        String soundName = settings.getString("typewriter.sound", "");
        if (!Character.isWhitespace(plain.charAt(typed - 1)) && soundName != null && !soundName.isBlank()) {
            Sound sound = plugin.getEffectManager().parseSound(soundName, null);
            if (sound != null) {
                player.playSound(player, sound, SoundCategory.MASTER,
                        (float) settings.getDouble("typewriter.sound-volume", 0.3),
                        (float) settings.getDouble("typewriter.sound-pitch", 1.8));
            }
        }
        if (typed >= typingLength) {
            typing = null;
        }
    }

    private void updateBossBar() {
        if (bossBar == null) {
            return;
        }
        float progress = Math.min(1f, (float) (tick + 1) / path.totalTicks());
        String percent = String.valueOf(Math.round(progress * 100));
        String text = settings.getString("bossbar.text", "{chapter}").replace("{chapter}", chapter);
        Component name = mm().format(text, Map.of("percent", percent, "player", player.getName()));
        bossBar.progress(progress);
        if (!name.equals(bossBar.name())) {
            bossBar.name(name);
        }
    }

    private BossBar.Color color() {
        try {
            return BossBar.Color.valueOf(settings.getString("bossbar.color", "WHITE").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BossBar.Color.WHITE;
        }
    }

    private BossBar.Overlay overlay() {
        try {
            return BossBar.Overlay.valueOf(settings.getString("bossbar.style", "PROGRESS").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    // ---------------------------------------------------------------- music

    private void startMusic() {
        String name = settings.getString("music.sound", "");
        if (name == null || name.isBlank()) {
            return;
        }
        // The game's own background music would play on top of it.
        player.stopSound(SoundCategory.MUSIC);
        float volume = (float) settings.getDouble("music.volume", 1.0);
        float pitch = (float) settings.getDouble("music.pitch", 1.0);
        Sound sound = plugin.getEffectManager().parseSound(name, null);
        if (sound != null) {
            player.playSound(player, sound, SoundCategory.MUSIC, volume, pitch);
        } else if (name.contains(":")) {
            // A sound from a resource pack, unknown to the server.
            player.playSound(player, name.trim(), SoundCategory.MUSIC, volume, pitch);
        }
    }

    private void stopMusic() {
        String name = settings.getString("music.sound", "");
        if (name == null || name.isBlank()) {
            return;
        }
        Sound sound = plugin.getEffectManager().parseSound(name, null);
        if (sound != null) {
            player.stopSound(sound, SoundCategory.MUSIC);
        } else if (name.contains(":")) {
            player.stopSound(name.trim(), SoundCategory.MUSIC);
        }
    }

    private MessageManager mm() {
        return plugin.getMessageManager();
    }
}
