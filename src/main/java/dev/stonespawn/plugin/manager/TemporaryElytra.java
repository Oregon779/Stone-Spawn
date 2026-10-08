package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.ListIterator;

/**
 * Bedrock clients only glide while actually wearing an elytra, so Bedrock players get a temporary one.
 * The chestplate it replaces is stored inside the elytra itself, so it comes back even after a crash.
 */
public class TemporaryElytra {

    private final NamespacedKey markerKey;
    private final NamespacedKey originalKey;

    public TemporaryElytra(StoneSpawn plugin) {
        this.markerKey = new NamespacedKey(plugin, "temporary_elytra");
        this.originalKey = new NamespacedKey(plugin, "original_chestplate");
    }

    public void equip(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getChestplate();
        if (current != null && current.getType() == Material.ELYTRA) {
            return;
        }
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        elytra.editMeta(meta -> {
            // Unbreakable also keeps Geyser's "next damage would break it" check from refusing the glide.
            meta.setUnbreakable(true);
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(markerKey, PersistentDataType.BOOLEAN, true);
            if (current != null && !current.isEmpty()) {
                data.set(originalKey, PersistentDataType.BYTE_ARRAY, current.serializeAsBytes());
            }
        });
        inventory.setChestplate(elytra);
    }

    /** Takes every temporary elytra away from the player and gives the stored chestplate back. */
    public void restore(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack chest = inventory.getChestplate();
        if (isTemporary(chest)) {
            inventory.setChestplate(originalOf(chest));
        }

        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isTemporary(contents[slot])) {
                inventory.setItem(slot, null);
                giveBack(player, originalOf(contents[slot]));
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (isTemporary(cursor)) {
            player.setItemOnCursor(null);
            giveBack(player, originalOf(cursor));
        }
    }

    /** Swaps temporary elytras in death drops for the chestplates they replaced. */
    public void replaceInDrops(List<ItemStack> drops) {
        ListIterator<ItemStack> iterator = drops.listIterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (!isTemporary(drop)) {
                continue;
            }
            ItemStack original = originalOf(drop);
            if (original == null || original.containsEnchantment(Enchantment.VANISHING_CURSE)) {
                iterator.remove();
            } else {
                iterator.set(original);
            }
        }
    }

    public boolean isWorn(Player player) {
        return isTemporary(player.getInventory().getChestplate());
    }

    public boolean isTemporary(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BOOLEAN);
    }

    public static boolean isChestEquippable(ItemStack item) {
        Equippable equippable = item.getData(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private ItemStack originalOf(ItemStack elytra) {
        byte[] bytes = elytra.getItemMeta().getPersistentDataContainer().get(originalKey, PersistentDataType.BYTE_ARRAY);
        return bytes == null ? null : ItemStack.deserializeBytes(bytes);
    }

    private static void giveBack(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
