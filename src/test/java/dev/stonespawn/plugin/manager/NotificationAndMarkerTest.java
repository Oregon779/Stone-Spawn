package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationAndMarkerTest extends PluginTestBase {

    private static List<BossBar> bars(TestPlayer player) {
        List<BossBar> bars = new ArrayList<>();
        player.activeBossBars().forEach(bars::add);
        return bars;
    }

    @Test
    void countdownBossBarIsUpdatedInPlaceAndHiddenAfterwards() {
        spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        configure(Map.of("teleport.countdown.notification", "BOSSBAR", "teleport.bossbar.duration-seconds", 2));
        TestPlayer player = addPlayer("Steve");

        player.performCommand("spawn");
        List<BossBar> first = bars(player);
        assertEquals(1, first.size());
        server.getScheduler().performTicks(21);
        List<BossBar> second = bars(player);
        assertEquals(1, second.size());
        assertSame(first.get(0), second.get(0), "the same bar is updated instead of replaced");
        assertTrue(PlainTextComponentSerializer.plainText().serialize(second.get(0).name()).contains("2"));

        server.getScheduler().performTicks(20 * 5);
        assertTrue(bars(player).isEmpty());
    }

    @Test
    void quitClearsBossBarState() {
        spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        configure(Map.of("teleport.countdown.notification", "BOSSBAR"));
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");

        server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertTrue(bars(player).isEmpty());
    }

    @Test
    void markerRunsWithAndWithoutNearbyPlayers() {
        spawnAt(SpawnManager.MAIN_SPAWN, 0.5, 64, 0.5);
        spawnAt("far", 5000.5, 64, 5000.5);
        plugin.reload();
        TestPlayer near = addPlayer("Near");
        TestPlayer away = addPlayer("Away");
        away.teleport(new Location(world, 2000, 64, 2000));

        server.getScheduler().performTicks(60);
        assertTrue(near.isOnline() && away.isOnline(), "marker task must not throw");
    }
}
