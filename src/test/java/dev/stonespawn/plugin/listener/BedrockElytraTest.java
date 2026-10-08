package dev.stonespawn.plugin.listener;

import dev.stonespawn.plugin.PluginTestBase;
import dev.stonespawn.plugin.TestPlayer;
import dev.stonespawn.plugin.manager.SpawnManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockElytraTest extends PluginTestBase {

    private ItemStack chestplate;

    @BeforeEach
    void island() {
        spawnAt(SpawnManager.MAIN_SPAWN, 0.5, 100, 0.5);
        configure(Map.of("elytra.enabled", true));
        chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
        chestplate.addUnsafeEnchantment(Enchantment.PROTECTION, 4);
    }

    private void jumpOff(TestPlayer player) {
        player.teleport(new Location(world, 10.5, 100, 0.5));
        player.setGrounded(true);
        player.simulatePlayerMove(new Location(world, 11.5, 100, 0.5));
        player.setGrounded(false);
        player.setFallDistance(5f);
        player.simulatePlayerMove(new Location(world, 12.5, 95, 0.5));
    }

    private void land(TestPlayer player) {
        player.setGrounded(true);
        server.getPluginManager().callEvent(new EntityToggleGlideEvent(player, false));
    }

    private static long elytrasIn(TestPlayer player) {
        return Arrays.stream(player.getInventory().getContents())
                .filter(Objects::nonNull).filter(i -> i.getType() == Material.ELYTRA).count();
    }

    @Test
    void bedrockPlayerGetsTemporaryElytraAndChestplateBackOnLanding() {
        TestPlayer player = addBedrockPlayer("Steve");
        player.getInventory().setChestplate(chestplate.clone());

        jumpOff(player);
        ItemStack worn = player.getInventory().getChestplate();
        assertEquals(Material.ELYTRA, worn.getType(), "Bedrock clients only glide with a real elytra");
        assertTrue(worn.getItemMeta().isUnbreakable(), "Geyser refuses gliding with an elytra about to break");
        assertTrue(player.isGliding());

        land(player);
        assertEquals(chestplate, player.getInventory().getChestplate(), "the exact chestplate (incl. enchantments) comes back");
        assertEquals(0, elytrasIn(player));
    }

    @Test
    void emptyChestSlotStaysEmpty() {
        TestPlayer player = addBedrockPlayer("Steve");
        jumpOff(player);
        assertEquals(Material.ELYTRA, player.getInventory().getChestplate().getType());
        land(player);
        assertNull(player.getInventory().getChestplate());
    }

    @Test
    void javaPlayersKeepTheirChestplate() {
        TestPlayer player = addPlayer("Alex");
        player.getInventory().setChestplate(chestplate.clone());
        jumpOff(player);
        assertTrue(player.isGliding());
        assertEquals(chestplate, player.getInventory().getChestplate());
    }

    @Test
    void ownRealElytraIsNotReplaced() {
        TestPlayer player = addBedrockPlayer("Steve");
        ItemStack ownElytra = new ItemStack(Material.ELYTRA);
        player.getInventory().setChestplate(ownElytra.clone());
        jumpOff(player);
        land(player);
        assertEquals(ownElytra, player.getInventory().getChestplate());
    }

    @Test
    void temporaryElytraCannotBeMovedDroppedOrSwappedAway() {
        TestPlayer player = addBedrockPlayer("Steve");
        jumpOff(player);
        ItemStack temporary = player.getInventory().getChestplate().clone();

        player.openInventory(server.createInventory(null, 9, Component.text("chest")));
        player.getInventory().setItem(0, temporary.clone());
        InventoryClickEvent click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.QUICKBAR,
                9 + 27, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        click.setCurrentItem(temporary.clone());
        server.getPluginManager().callEvent(click);
        assertTrue(click.isCancelled(), "clicking the temporary elytra");
        player.getInventory().setItem(0, null);
        player.closeInventory();

        PlayerDropItemEvent drop = new PlayerDropItemEvent(player, world.dropItem(player.getLocation(), temporary.clone()));
        server.getPluginManager().callEvent(drop);
        assertTrue(drop.isCancelled(), "dropping the temporary elytra");

        ItemStack otherChestplate = new ItemStack(Material.IRON_CHESTPLATE);
        otherChestplate.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.CHEST).build());
        PlayerInteractEvent use = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, otherChestplate, null, BlockFace.SELF);
        server.getPluginManager().callEvent(use);
        assertEquals(Event.Result.DENY, use.useItemInHand(), "right-click equipping would swap the elytra into the hand");
    }

    @Test
    void deathDropsTheChestplateInsteadOfTheElytra() {
        TestPlayer player = addBedrockPlayer("Steve");
        player.getInventory().setChestplate(chestplate.clone());
        jumpOff(player);

        List<ItemStack> drops = new ArrayList<>(List.of(player.getInventory().getChestplate().clone(), new ItemStack(Material.DIRT)));
        server.getPluginManager().callEvent(new PlayerDeathEvent(player, DamageSource.builder(DamageType.FALL).build(),
                drops, 0, Component.empty(), false));

        assertTrue(drops.contains(chestplate));
        assertTrue(drops.stream().noneMatch(i -> i.getType() == Material.ELYTRA));
    }

    @Test
    void chestplateWithCurseOfVanishingVanishesOnDeath() {
        TestPlayer player = addBedrockPlayer("Steve");
        ItemStack cursed = new ItemStack(Material.IRON_CHESTPLATE);
        cursed.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
        player.getInventory().setChestplate(cursed);
        jumpOff(player);

        List<ItemStack> drops = new ArrayList<>(List.of(player.getInventory().getChestplate().clone()));
        server.getPluginManager().callEvent(new PlayerDeathEvent(player, DamageSource.builder(DamageType.FALL).build(),
                drops, 0, Component.empty(), false));
        assertTrue(drops.isEmpty());
    }

    @Test
    void quitMidGlideGivesChestplateBack() {
        TestPlayer player = addBedrockPlayer("Steve");
        player.getInventory().setChestplate(chestplate.clone());
        jumpOff(player);
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(chestplate, player.getInventory().getChestplate());
    }

    @Test
    void chestplateComesBackOnJoinAfterACrash() {
        TestPlayer glider = addBedrockPlayer("Steve");
        glider.getInventory().setChestplate(chestplate.clone());
        jumpOff(glider);
        ItemStack savedWithCrash = glider.getInventory().getChestplate().clone();

        TestPlayer rejoined = addBedrockPlayer("Steve2");
        rejoined.getInventory().setChestplate(savedWithCrash);
        server.getPluginManager().callEvent(new PlayerJoinEvent(rejoined, Component.empty()));
        assertEquals(chestplate, rejoined.getInventory().getChestplate());
    }

    @Test
    void disablingThePluginGivesChestplatesBack() {
        TestPlayer player = addBedrockPlayer("Steve");
        player.getInventory().setChestplate(chestplate.clone());
        jumpOff(player);
        server.getPluginManager().disablePlugin(plugin);
        assertEquals(chestplate, player.getInventory().getChestplate());
    }

    @Test
    void bedrockPlayersBoostWithSneakBecauseTheyHaveNoSwapKey() {
        TestPlayer bedrock = addBedrockPlayer("Steve");
        jumpOff(bedrock);
        bedrock.setVelocity(new Vector());

        PlayerSwapHandItemsEvent swap = new PlayerSwapHandItemsEvent(bedrock, new ItemStack(Material.AIR), new ItemStack(Material.AIR));
        server.getPluginManager().callEvent(swap);
        assertFalse(swap.isCancelled());

        server.getPluginManager().callEvent(new PlayerToggleSneakEvent(bedrock, true));
        assertTrue(bedrock.getVelocity().length() > 1.0);

        TestPlayer java = addPlayer("Alex");
        jumpOff(java);
        java.setVelocity(new Vector());
        server.getPluginManager().callEvent(new PlayerToggleSneakEvent(java, true));
        assertEquals(0.0, java.getVelocity().length(), "Java players keep the configured SWAP_HAND trigger");
    }
}
