package dev.stonespawn.plugin.gui;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import dev.stonespawn.plugin.manager.SpawnManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnGuiTest extends PluginTestBase {

    private Location hub;

    @BeforeEach
    void spawns() {
        spawnAt(SpawnManager.MAIN_SPAWN, 100.5, 70, 100.5);
        hub = spawnAt("hub", 200.5, 70, 200.5);
        spawnAt("arena", 300.5, 70, 300.5);
        configure(Map.of("teleport.delay-seconds", 0));
    }

    private InventoryClickEvent click(TestPlayer player, int rawSlot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void spawnsArePlacedInConfiguredSlotsWithFiller() {
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");
        Inventory gui = player.getOpenInventory().getTopInventory();

        assertInstanceOf(SpawnGui.Holder.class, gui.getHolder());
        assertEquals(27, gui.getSize());
        assertEquals(Material.ENDER_PEARL, gui.getItem(10).getType());
        assertEquals(Material.ENDER_PEARL, gui.getItem(12).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, gui.getItem(0).getType());
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, gui.getItem(13).getType());
    }

    @Test
    void clickingASpawnTeleportsOnceAndCancelsTheClick() {
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");

        // Slot order: spawn (10), arena (11), hub (12)
        InventoryClickEvent first = click(player, 12);
        InventoryClickEvent second = click(player, 12);
        assertTrue(first.isCancelled());
        assertTrue(second.isCancelled());
        server.getScheduler().performTicks(2);

        assertTrue(sameBlock(player.getLocation(), hub));
        assertFalse(messages(player).stream().anyMatch(m -> m.contains("Please wait")),
                "a double click must not trigger a second teleport request");
    }

    @Test
    void itemsCannotBeTakenOut() {
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");
        assertTrue(click(player, 0).isCancelled(), "filler");
        assertTrue(click(player, 40).isCancelled(), "own inventory while the GUI is open (shift-click/collect)");
    }

    @Test
    void guiIsClosedWhenPluginIsDisabled() {
        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");
        server.getPluginManager().disablePlugin(plugin);
        Inventory top = player.getOpenInventory().getTopInventory();
        assertFalse(top != null && top.getHolder() instanceof SpawnGui.Holder,
                "an open GUI without its click listener would let players take the items");
    }

    @Test
    void hiddenSpawnAndFixedSlotFromGuiConfig() throws Exception {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "gui.yml");
        org.bukkit.configuration.file.YamlConfiguration gui = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        gui.set("spawns.arena.hidden", true);
        gui.set("spawns.hub.slot", 4);
        gui.set("spawns.hub.material", "GRASS_BLOCK");
        gui.set("spawns.hub.name", "&aHub <bold>{spawn}");
        gui.set("spawn-item.material", "NOT_A_MATERIAL");
        gui.save(file);
        plugin.reload();

        TestPlayer player = addPlayer("Steve");
        player.performCommand("spawn");
        Inventory top = player.getOpenInventory().getTopInventory();
        assertEquals(Material.GRASS_BLOCK, top.getItem(4).getType());
        assertEquals(Material.STONE, top.getItem(10).getType(), "invalid material falls back to STONE");
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, top.getItem(11).getType(), "hidden spawn leaves the slot to the filler");
    }
}
