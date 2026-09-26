package dev.stonespawn.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class PluginTestBase {

    protected ServerMock server;
    protected StoneSpawn plugin;
    protected WorldMock world;

    @BeforeEach
    void setUpServer() {
        server = MockBukkit.mock(new TestServer());
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.load(StoneSpawn.class);
        plugin.getUpdateChecker().stop();
    }

    @AfterEach
    void tearDownServer() {
        MockBukkit.unmock();
    }

    protected TestPlayer addPlayer(String name) {
        TestPlayer player = new TestPlayer(server, name);
        server.addPlayer(player);
        // Let the delayed first-join teleport finish before the test moves the player.
        server.getScheduler().performTicks(2);
        player.teleport(new Location(world, 0.5, 64, 0.5));
        messages(player);
        return player;
    }

    protected TestPlayer addAdmin(String name) {
        TestPlayer player = addPlayer(name);
        player.setOp(true);
        return player;
    }

    /** Overrides config.yml values and runs the plugin's own reload, like an admin editing the file. */
    protected void configure(Map<String, Object> values) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        values.forEach(config::set);
        try {
            config.save(file);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        plugin.reload();
    }

    protected Location spawnAt(String name, double x, double y, double z) {
        Location location = new Location(world, x, y, z);
        plugin.getSpawnManager().setSpawn(name, location);
        return location;
    }

    protected static List<String> messages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return messages;
    }

    protected static boolean sameBlock(Location a, Location b) {
        return a.getWorld() == b.getWorld() && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}
