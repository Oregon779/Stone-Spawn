package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import dev.stonespawn.plugin.manager.SpawnManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraTest extends PluginTestBase {

    @BeforeEach
    void island() {
        spawnAt(SpawnManager.MAIN_SPAWN, 0.5, 100, 0.5);
    }

    private void enable() {
        configure(Map.of("elytra.enabled", true));
    }

    /** Walks on the island, then steps off the edge and falls far enough. */
    private TestPlayer jumpOffIsland(String name) {
        TestPlayer player = addPlayer(name);
        player.teleport(new Location(world, 10.5, 100, 0.5));
        player.setGrounded(true);
        player.simulatePlayerMove(new Location(world, 11.5, 100, 0.5));

        player.setGrounded(false);
        player.setFallDistance(5f);
        player.simulatePlayerMove(new Location(world, 12.5, 95, 0.5));
        return player;
    }

    private boolean toggleOffIsCancelled(TestPlayer player) {
        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, false);
        server.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    private boolean fallDamageIsCancelled(TestPlayer player) {
        EntityDamageEvent event = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL,
                DamageSource.builder(DamageType.FALL).build(), 10);
        server.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    @Test
    void offByDefault() {
        TestPlayer player = jumpOffIsland("Steve");
        assertFalse(player.isGliding());
    }

    @Test
    void jumpingOffStartsGlideUntilLanding() {
        enable();
        TestPlayer player = jumpOffIsland("Steve");
        assertTrue(player.isGliding());
        assertTrue(toggleOffIsCancelled(player), "the server's per-tick 'no elytra' switch-off must be blocked mid-air");
        assertTrue(fallDamageIsCancelled(player));

        player.setGrounded(true);
        assertFalse(toggleOffIsCancelled(player), "landing ends the glide");
        assertFalse(plugin.getElytraManager().isGliding(player.getUniqueId()));
        assertTrue(fallDamageIsCancelled(player), "landing damage is covered by the grace period");
    }

    @Test
    void boostWorksOncePerGlideAndBlocksItemSwap() {
        enable();
        TestPlayer player = jumpOffIsland("Steve");
        player.setVelocity(new Vector());

        PlayerSwapHandItemsEvent first = new PlayerSwapHandItemsEvent(player, new ItemStack(org.bukkit.Material.AIR),
                new ItemStack(org.bukkit.Material.AIR));
        server.getPluginManager().callEvent(first);
        assertTrue(first.isCancelled());
        assertTrue(player.getVelocity().length() > 1.0, "boost pushes the player forward");

        player.setVelocity(new Vector());
        PlayerSwapHandItemsEvent second = new PlayerSwapHandItemsEvent(player, new ItemStack(org.bukkit.Material.AIR),
                new ItemStack(org.bukkit.Material.AIR));
        server.getPluginManager().callEvent(second);
        assertEquals(0.0, player.getVelocity().length(), "only one boost per glide by default");
    }

    @Test
    void swapHandIsUntouchedWhenNotGliding() {
        enable();
        TestPlayer player = addPlayer("Steve");
        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(player, new ItemStack(org.bukkit.Material.AIR),
                new ItemStack(org.bukkit.Material.AIR));
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void normalJumpOnIslandDoesNotGlide() {
        enable();
        TestPlayer player = addPlayer("Steve");
        player.teleport(new Location(world, 10.5, 100, 0.5));
        player.simulatePlayerMove(new Location(world, 11.5, 100, 0.5));
        player.setGrounded(false);
        player.setFallDistance(1.25f);
        player.simulatePlayerMove(new Location(world, 12.5, 99, 0.5));
        assertFalse(player.isGliding());
    }

    @Test
    void fallingElsewhereDoesNotGlide() {
        enable();
        TestPlayer player = addPlayer("Steve");
        player.teleport(new Location(world, 500.5, 100, 500.5));
        player.simulatePlayerMove(new Location(world, 501.5, 100, 500.5));
        player.setGrounded(false);
        player.setFallDistance(10f);
        player.simulatePlayerMove(new Location(world, 502.5, 90, 500.5));
        assertFalse(player.isGliding());
    }

    @Test
    void permissionAndGameModeAreRespected() {
        enable();
        TestPlayer noPermission = addPlayer("NoPerm");
        noPermission.addAttachment(plugin, "stonespawn.elytra", false);
        noPermission.teleport(new Location(world, 10.5, 100, 0.5));
        noPermission.simulatePlayerMove(new Location(world, 11.5, 100, 0.5));
        noPermission.setGrounded(false);
        noPermission.setFallDistance(5f);
        noPermission.simulatePlayerMove(new Location(world, 12.5, 95, 0.5));
        assertFalse(noPermission.isGliding());

        TestPlayer creative = addPlayer("Creative");
        creative.setGameMode(GameMode.CREATIVE);
        creative.teleport(new Location(world, 10.5, 100, 0.5));
        creative.simulatePlayerMove(new Location(world, 11.5, 100, 0.5));
        creative.setGrounded(false);
        creative.setFallDistance(5f);
        creative.simulatePlayerMove(new Location(world, 12.5, 95, 0.5));
        assertFalse(creative.isGliding());
    }

    @Test
    void teleportEndsGlide() {
        enable();
        TestPlayer player = jumpOffIsland("Steve");
        player.teleport(new Location(world, 50, 64, 50));
        assertFalse(player.isGliding());
        assertFalse(plugin.getElytraManager().isGliding(player.getUniqueId()));
    }
}
