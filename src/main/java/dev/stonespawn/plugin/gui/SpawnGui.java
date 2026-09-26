package dev.stonespawn.plugin.gui;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.config.ConfigUpdater;
import dev.stonespawn.plugin.manager.SpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SpawnGui {

    private static final String RESOURCE_PATH = "gui.yml";

    private final StoneSpawn plugin;
    private YamlConfiguration gui;

    public SpawnGui(StoneSpawn plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!file.exists()) {
            plugin.saveResource(RESOURCE_PATH, false);
        }
        try {
            ConfigUpdater.UpdateResult result = ConfigUpdater.update(plugin, RESOURCE_PATH, file);
            if (result.addedKeys() > 0) {
                plugin.getLogger().info("Added " + result.addedKeys() + " new option(s) to gui.yml");
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to update gui.yml: " + ex.getMessage());
        }
        gui = YamlConfiguration.loadConfiguration(file);
    }

    public void open(Player player) {
        int size = Math.max(1, Math.min(6, gui.getInt("rows", 3))) * 9;
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, size, text(gui.getString("title", "Spawns"), null));
        holder.inventory = inventory;

        placeExtraItems(inventory, holder);
        placeSpawnItems(inventory, holder);

        ConfigurationSection filler = gui.getConfigurationSection("filler");
        if (filler != null && filler.getBoolean("enabled", true)) {
            ItemStack fillerItem = buildItem(filler, null, null);
            for (int slot = 0; slot < size; slot++) {
                if (inventory.getItem(slot) == null) {
                    inventory.setItem(slot, fillerItem);
                }
            }
        }

        player.openInventory(inventory);
        playSound(player, "sounds.open");
    }

    /** Closes every open spawn GUI, so no items can be taken out once the click listener is gone. */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder(false) instanceof Holder) {
                player.closeInventory();
            }
        }
    }

    void handleClick(Player player, Holder holder, int slot) {
        if (holder.used) {
            return;
        }
        if (holder.closeSlots.contains(slot)) {
            holder.used = true;
            playSound(player, "sounds.click");
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }

        String spawnName = holder.spawnSlots.get(slot);
        if (spawnName == null) {
            return;
        }
        holder.used = true;
        playSound(player, "sounds.click");
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            if (!player.hasPermission("stonespawn.use")) {
                plugin.getMessageManager().sendChat(player, "general.no-permission", null);
                return;
            }
            Location spawn = plugin.getSpawnManager().getSpawn(spawnName);
            if (spawn == null) {
                plugin.getMessageManager().sendChat(player, "spawn.not-found", Map.of("spawn", spawnName));
                return;
            }
            plugin.getTeleportManager().requestCommandTeleport(player, spawn);
        });
    }

    private void placeExtraItems(Inventory inventory, Holder holder) {
        ConfigurationSection items = gui.getConfigurationSection("items");
        if (items == null) {
            return;
        }
        for (String key : items.getKeys(false)) {
            ConfigurationSection spec = items.getConfigurationSection(key);
            if (spec == null) {
                continue;
            }
            int slot = spec.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, buildItem(spec, null, null));
            if ("CLOSE".equalsIgnoreCase(spec.getString("action", "NONE"))) {
                holder.closeSlots.add(slot);
            }
        }
    }

    private void placeSpawnItems(Inventory inventory, Holder holder) {
        SpawnManager spawnManager = plugin.getSpawnManager();
        ConfigurationSection defaults = gui.getConfigurationSection("spawn-item");
        ConfigurationSection overrides = gui.getConfigurationSection("spawns");
        Map<String, ItemStack> unplaced = new LinkedHashMap<>();

        for (String name : spawnManager.getSpawnNames()) {
            Location location = spawnManager.getSpawn(name);
            if (location == null) {
                continue;
            }
            ConfigurationSection override = overrides == null ? null : overrides.getConfigurationSection(name);
            if (override != null && override.getBoolean("hidden", false)) {
                continue;
            }

            ItemStack item = buildItem(override, defaults, placeholders(name, location));
            int slot = override == null ? -1 : override.getInt("slot", -1);
            if (slot >= 0 && slot < inventory.getSize() && inventory.getItem(slot) == null) {
                inventory.setItem(slot, item);
                holder.spawnSlots.put(slot, name);
            } else {
                unplaced.put(name, item);
            }
        }

        List<Integer> freeSlots = new ArrayList<>();
        for (int slot : gui.getIntegerList("spawn-slots")) {
            if (slot >= 0 && slot < inventory.getSize() && !freeSlots.contains(slot)) {
                freeSlots.add(slot);
            }
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!freeSlots.contains(slot)) {
                freeSlots.add(slot);
            }
        }

        int index = 0;
        for (Map.Entry<String, ItemStack> entry : unplaced.entrySet()) {
            while (index < freeSlots.size() && inventory.getItem(freeSlots.get(index)) != null) {
                index++;
            }
            if (index >= freeSlots.size()) {
                break;
            }
            int slot = freeSlots.get(index);
            inventory.setItem(slot, entry.getValue());
            holder.spawnSlots.put(slot, entry.getKey());
        }
    }

    private Map<String, String> placeholders(String name, Location location) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("spawn", name);
        placeholders.put("world", location.getWorld().getName());
        placeholders.put("x", String.valueOf(location.getBlockX()));
        placeholders.put("y", String.valueOf(location.getBlockY()));
        placeholders.put("z", String.valueOf(location.getBlockZ()));
        return placeholders;
    }

    private ItemStack buildItem(ConfigurationSection spec, ConfigurationSection fallback, Map<String, String> placeholders) {
        String materialName = value(spec, fallback, "material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir() || !material.isItem()) {
            plugin.getLogger().warning("Invalid material '" + materialName + "' in gui.yml, using STONE instead.");
            material = Material.STONE;
        }

        int amount = Math.max(1, Math.min(64, intValue(spec, fallback, "amount", 1)));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        String name = value(spec, fallback, "name", null);
        if (name != null) {
            meta.displayName(text(name, placeholders));
        }
        List<String> lore = listValue(spec, fallback, "lore");
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(line -> text(line, placeholders)).toList());
        }
        if (Boolean.parseBoolean(value(spec, fallback, "glow", "false"))) {
            meta.setEnchantmentGlintOverride(true);
        }
        String itemModel = value(spec, fallback, "item-model", null);
        if (itemModel != null && !itemModel.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(itemModel.trim().toLowerCase(Locale.ROOT));
            if (key != null) {
                meta.setItemModel(key);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    private Component text(String raw, Map<String, String> placeholders) {
        return plugin.getMessageManager().format(raw, placeholders)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private void playSound(Player player, String path) {
        String soundName = gui.getString(path, "");
        if (soundName == null || soundName.isBlank()) {
            return;
        }
        Sound sound = plugin.getEffectManager().parseSound(soundName, Sound.UI_BUTTON_CLICK);
        player.playSound(player.getLocation(), sound,
                (float) gui.getDouble("sounds.volume", 0.6), (float) gui.getDouble("sounds.pitch", 1.0));
    }

    private static String value(ConfigurationSection spec, ConfigurationSection fallback, String key, String def) {
        if (spec != null && spec.contains(key)) {
            return spec.getString(key, def);
        }
        if (fallback != null && fallback.contains(key)) {
            return fallback.getString(key, def);
        }
        return def;
    }

    private static int intValue(ConfigurationSection spec, ConfigurationSection fallback, String key, int def) {
        if (spec != null && spec.contains(key)) {
            return spec.getInt(key, def);
        }
        if (fallback != null && fallback.contains(key)) {
            return fallback.getInt(key, def);
        }
        return def;
    }

    private static List<String> listValue(ConfigurationSection spec, ConfigurationSection fallback, String key) {
        if (spec != null && spec.contains(key)) {
            return spec.getStringList(key);
        }
        if (fallback != null && fallback.contains(key)) {
            return fallback.getStringList(key);
        }
        return List.of();
    }

    public static final class Holder implements InventoryHolder {

        private final Map<Integer, String> spawnSlots = new HashMap<>();
        private final Set<Integer> closeSlots = new HashSet<>();
        private Inventory inventory;
        private boolean used;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
