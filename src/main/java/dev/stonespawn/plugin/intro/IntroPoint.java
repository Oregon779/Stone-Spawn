package dev.stonespawn.plugin.intro;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One camera point of the intro, as set up in intro.yml.
 *
 * @param lookAt       block the camera keeps looking at, or null to keep the saved view
 * @param orbitDegrees how far the camera circles around its look-at target while it stays here; 0 = stands still
 * @param chapter      name shown in the boss bar from this point on
 * @param time         time of day the viewer sees from here on, or null to keep the previous one
 * @param weather      weather the viewer sees from here on, or null to keep the previous one
 * @param stayTicks    0 = the camera flies through without stopping
 */
record IntroPoint(String name, Location location, Vector lookAt, double orbitDegrees, String chapter, String title,
                  String subtitle, List<String> chat, String sound, float soundVolume, float soundPitch,
                  List<String> commands, Long time, WeatherType weather, int flyTicks, int stayTicks) {

    private static final Map<String, Long> TIME_NAMES = Map.of(
            "sunrise", 23000L, "day", 1000L, "morning", 1000L, "noon", 6000L,
            "sunset", 12000L, "night", 13000L, "midnight", 18000L);

    /** @return the point, or null if its world isn't loaded */
    static IntroPoint read(String name, ConfigurationSection section, double defaultFlySeconds, double defaultStaySeconds) {
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        Location location = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
        ConfigurationSection target = section.getConfigurationSection("look-at");
        Vector lookAt = target == null ? null : new Vector(target.getDouble("x"), target.getDouble("y"), target.getDouble("z"));
        String title = section.getString("title", "");
        return new IntroPoint(name, location, lookAt, section.getDouble("orbit-degrees", 0),
                section.getString("chapter", title), title, section.getString("subtitle", ""),
                section.getStringList("chat"), section.getString("sound", ""),
                (float) section.getDouble("sound-volume", 1.0), (float) section.getDouble("sound-pitch", 1.0),
                section.getStringList("commands"), parseTime(section.getString("time", "")),
                parseWeather(section.getString("weather", "")),
                Math.max(1, ticks(section.getDouble("fly-seconds", defaultFlySeconds))),
                Math.max(0, ticks(section.getDouble("stay-seconds", defaultStaySeconds))));
    }

    boolean orbits() {
        return orbitDegrees != 0 && stayTicks > 0;
    }

    /** Ticks (0 to 23999), a name like "sunset", or null for "" and anything unknown. */
    static Long parseTime(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }
        if (TIME_NAMES.containsKey(text)) {
            return TIME_NAMES.get(text);
        }
        try {
            return Math.floorMod(Long.parseLong(text), 24000L);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** "clear" or "rain" (a player can only be shown these two), or null. */
    static WeatherType parseWeather(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "clear", "sun", "sunny" -> WeatherType.CLEAR;
            case "rain", "downfall", "storm", "thunder" -> WeatherType.DOWNFALL;
            default -> null;
        };
    }

    private static int ticks(double seconds) {
        return (int) Math.round(seconds * 20);
    }
}
