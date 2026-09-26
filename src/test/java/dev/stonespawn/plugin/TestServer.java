package dev.stonespawn.plugin;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.InventoryHolder;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.inventory.ChestInventoryMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;

/** ServerMock whose custom inventories support Paper's getHolder(boolean), which MockBukkit leaves unimplemented. */
public class TestServer extends ServerMock {

    @Override
    public InventoryMock createInventory(InventoryHolder owner, int size, Component title) {
        return new ChestInventoryMock(owner, size) {
            @Override
            public InventoryHolder getHolder(boolean useSnapshot) {
                return getHolder();
            }
        };
    }
}
