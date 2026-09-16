package dev.stonespawn.plugin.manager;

import dev.stonespawn.plugin.StoneSpawn;
import dev.stonespawn.plugin.model.WorldMode;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class WorldRestrictionManager {

    private final StoneSpawn plugin;
    private Set<String> cachedWorlds = Collections.emptySet();
    private WorldMode cachedMode = WorldMode.NONE;

    public WorldRestrictionManager(StoneSpawn plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        cachedMode = plugin.getConfigManager().getWorldMode();
        cachedWorlds = new HashSet<>(plugin.getConfigManager().getStringList("worlds.list"));
    }

    public boolean isAllowed(World world) {
        return switch (cachedMode) {
            case WHITELIST -> cachedWorlds.contains(world.getName());
            case BLACKLIST -> !cachedWorlds.contains(world.getName());
            case NONE -> true;
        };
    }
}
