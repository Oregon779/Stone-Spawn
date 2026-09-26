package dev.stonespawn.plugin.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class SpawnGuiListener implements Listener {

    private final SpawnGui gui;

    public SpawnGuiListener(SpawnGui gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof SpawnGui.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (event.getWhoClicked() instanceof Player player && slot >= 0 && slot < event.getView().getTopInventory().getSize()) {
            gui.handleClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof SpawnGui.Holder) {
            event.setCancelled(true);
        }
    }
}
