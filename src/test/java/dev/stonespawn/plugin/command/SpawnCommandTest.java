package dev.stonespawn.plugin.command;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import dev.stonespawn.plugin.gui.SpawnGui;
import dev.stonespawn.plugin.manager.SpawnManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnCommandTest extends PluginTestBase {

    private static final int COUNTDOWN_TICKS = 3 * 20 + 5;

    private Location mainSpawn() {
        return spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
    }

    @Test
    void withoutSpawnPlayerIsTold() {
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");
        assertEquals(List.of("[StoneSpawn] ✖ No spawn point has been set yet."), messages(player));
    }

    @Test
    void singleSpawnTeleportsAfterCountdown() {
        Location spawn = mainSpawn();
        TestPlayer player = addPlayer("Steve");

        player.performCommand("spawn");
        server.getScheduler().performTicks(40);
        assertFalse(sameBlock(player.getLocation(), spawn), "must still be counting down");

        server.getScheduler().performTicks(COUNTDOWN_TICKS - 40);
        assertTrue(sameBlock(player.getLocation(), spawn));
    }

    @Test
    void movingCancelsCountdown() {
        Location spawn = mainSpawn();
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");
        messages(player);

        player.simulatePlayerMove(player.getLocation().add(2, 0, 0));
        server.getScheduler().performTicks(COUNTDOWN_TICKS);

        assertFalse(sameBlock(player.getLocation(), spawn));
        assertTrue(messages(player).contains("[StoneSpawn] ✖ Teleport cancelled because you moved."));
    }

    @Test
    void cooldownBlocksSecondUseAndSurvivesRelog() {
        mainSpawn();
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");
        server.getScheduler().performTicks(COUNTDOWN_TICKS);
        messages(player);

        server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        player.performCommand("spawn");
        assertTrue(messages(player).stream().anyMatch(m -> m.contains("Please wait")),
                "reconnecting must not reset the cooldown");
    }

    @Test
    void cooldownIsNotUsedUpWhileCountdownIsAlreadyRunning() {
        mainSpawn();
        TestPlayer admin = addAdmin("Admin");
        TestPlayer player = addPlayer("Steve");
        admin.performCommand("spawn Steve");
        assertTrue(plugin.getTeleportManager().hasPending(player.getUniqueId()));

        player.performCommand("spawn");
        assertEquals(0, plugin.getCooldownManager().getRemaining(player.getUniqueId(), 10));
    }

    @Test
    void foreignBlindnessIsKeptAfterSpawnTeleport() {
        mainSpawn();
        TestPlayer player = addPlayer("Steve");
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 60, 0));

        player.performCommand("spawn");
        server.getScheduler().performTicks(COUNTDOWN_TICKS);

        assertTrue(player.hasPotionEffect(PotionEffectType.BLINDNESS),
                "blindness from other plugins (e.g. login plugins) must not be removed");
    }

    @Test
    void countdownBlindnessIsRemovedOnArrival() {
        Location spawn = mainSpawn();
        configure(Map.of("teleport.blindness-during-delay", true));
        TestPlayer player = addPlayer("Steve");

        player.performCommand("spawn");
        server.getScheduler().performTicks(10);
        assertTrue(player.hasPotionEffect(PotionEffectType.BLINDNESS));

        server.getScheduler().performTicks(COUNTDOWN_TICKS);
        assertTrue(sameBlock(player.getLocation(), spawn));
        assertFalse(player.hasPotionEffect(PotionEffectType.BLINDNESS));
    }

    @Test
    void multipleSpawnsOpenGuiUnlessDisabled() {
        Location spawn = mainSpawn();
        spawnAt("hub", 200, 70, 200);
        TestPlayer player = addPlayer("Steve");

        player.performCommand("spawn");
        assertInstanceOf(SpawnGui.Holder.class, player.getOpenInventory().getTopInventory().getHolder());
        player.closeInventory();

        configure(Map.of("command.spawn-gui", false, "teleport.delay-seconds", 0));
        player.performCommand("spawn");
        server.getScheduler().performTicks(2);
        assertTrue(sameBlock(player.getLocation(), spawn));
    }

    @Test
    void namedSpawnIsReachableByName() {
        mainSpawn();
        Location hub = spawnAt("hub", 200.5, 70, 200.5);
        configure(Map.of("teleport.delay-seconds", 0));
        TestPlayer player = addPlayer("Steve");

        player.performCommand("spawn HUB");
        server.getScheduler().performTicks(2);
        assertTrue(sameBlock(player.getLocation(), hub));
    }

    @Test
    void adminCanSendOtherPlayersToMainOrNamedSpawn() {
        Location spawn = mainSpawn();
        Location hub = spawnAt("hub", 200.5, 70, 200.5);
        configure(Map.of("teleport.delay-seconds", 0));
        TestPlayer admin = addAdmin("Admin");
        TestPlayer target = addPlayer("Steve");

        admin.performCommand("spawn Steve");
        server.getScheduler().performTicks(2);
        assertTrue(sameBlock(target.getLocation(), spawn));

        admin.performCommand("spawn hub Steve");
        server.getScheduler().performTicks(2);
        assertTrue(sameBlock(target.getLocation(), hub));
    }

    @Test
    void normalPlayerCannotMoveOthers() {
        mainSpawn();
        spawnAt("hub", 200.5, 70, 200.5);
        TestPlayer player = addPlayer("Steve");
        TestPlayer other = addPlayer("Alex");
        Location before = other.getLocation();

        player.performCommand("spawn Alex");
        player.performCommand("spawn hub Alex");
        server.getScheduler().performTicks(COUNTDOWN_TICKS);

        assertTrue(sameBlock(other.getLocation(), before));
        List<String> messages = messages(player);
        assertTrue(messages.contains("[StoneSpawn] ✖ There is no spawn called Alex."));
        assertTrue(messages.contains("[StoneSpawn] ✖ You don't have permission to do this."));
    }

    @Test
    void unknownNameWithMarkupIsEchoedSafely() {
        mainSpawn();
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn <click:run_command:'/op_Steve'>&4pwn" + "x".repeat(300));

        String message = messages(player).get(0);
        assertFalse(message.contains("<"), message);
        assertFalse(message.contains("&"), message);
        assertTrue(message.length() < 120, "echo must be length-limited: " + message.length());
    }

    @Test
    void withoutUsePermissionSpawnIsDenied() {
        mainSpawn();
        TestPlayer player = addPlayer("Steve");
        player.addAttachment(plugin, "stonespawn.use", false);

        player.performCommand("spawn");
        assertEquals(List.of("[StoneSpawn] ✖ You don't have permission to do this."), messages(player));
    }

    @Test
    void consoleNeedsATargetPlayer() {
        Location spawn = mainSpawn();
        configure(Map.of("teleport.delay-seconds", 0));
        TestPlayer target = addPlayer("Steve");
        ConsoleCommandSenderMock console = server.getConsoleSender();

        server.dispatchCommand(console, "spawn");
        assertTrue(console.nextMessage().contains("Only players"));

        server.dispatchCommand(console, "spawn Steve");
        server.getScheduler().performTicks(2);
        assertTrue(sameBlock(target.getLocation(), spawn));
    }

    @Test
    void tooManyArgumentsAreIgnored() {
        Location spawn = mainSpawn();
        configure(Map.of("teleport.delay-seconds", 0));
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn spawn extra args here");
        assertTrue(messages(player).contains("[StoneSpawn] ✖ You don't have permission to do this."));
        assertFalse(sameBlock(player.getLocation(), spawn));
    }

    @Test
    void manyPlayersCanTeleportAtOnce() {
        Location spawn = mainSpawn();
        List<TestPlayer> players = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            TestPlayer player = addPlayer("P" + i);
            players.add(player);
            player.performCommand("spawn");
        }
        server.getScheduler().performTicks(COUNTDOWN_TICKS);
        players.forEach(p -> assertTrue(sameBlock(p.getLocation(), spawn), p.getName()));
    }
}
