package dev.stonespawn.plugin.intro;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/** One camera point of the intro, as set up in intro.yml. */
record IntroPoint(String name, Location location, String title, String subtitle, List<String> chat, String sound,
                  float soundVolume, float soundPitch, int flyTicks, int stayTicks) {

    /** @return the point, or null if its world isn't loaded */
    static IntroPoint read(String name, ConfigurationSection section, double defaultFlySeconds, double defaultStaySeconds) {
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        Location location = new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
        return new IntroPoint(name, location,
                section.getString("title", ""), section.getString("subtitle", ""), section.getStringList("chat"),
                section.getString("sound", ""),
                (float) section.getDouble("sound-volume", 1.0), (float) section.getDouble("sound-pitch", 1.0),
                ticks(section.getDouble("fly-seconds", defaultFlySeconds)),
                ticks(section.getDouble("stay-seconds", defaultStaySeconds)));
    }

    private static int ticks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20));
    }
}
