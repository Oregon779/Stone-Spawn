package dev.stonespawn.plugin.manager;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/** Recognizes Bedrock players (Geyser/Floodgate) without a hard dependency on either plugin. */
public class BedrockDetector {

    private final Api floodgate = new Api("org.geysermc.floodgate.api.FloodgateApi", "getInstance", "isFloodgatePlayer");
    private final Api geyser = new Api("org.geysermc.geyser.api.GeyserApi", "api", "connectionByUuid");

    public boolean isBedrock(Player player) {
        UUID id = player.getUniqueId();
        // Floodgate gives unlinked Bedrock players UUIDs starting with 64 zero bits; Java account UUIDs never do.
        if (id.getMostSignificantBits() == 0L) {
            return true;
        }
        return Boolean.TRUE.equals(floodgate.call(id)) || geyser.call(id) != null;
    }

    private static final class Api {

        private final String className;
        private final String instanceMethod;
        private final String lookupMethod;
        private boolean missing;
        private Object instance;
        private Method lookup;

        Api(String className, String instanceMethod, String lookupMethod) {
            this.className = className;
            this.instanceMethod = instanceMethod;
            this.lookupMethod = lookupMethod;
        }

        Object call(UUID id) {
            if (!resolve()) {
                return null;
            }
            try {
                return lookup.invoke(instance, id);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return null;
            }
        }

        // The plugin may not be installed (remembered) or may not have finished starting yet (retried later).
        private boolean resolve() {
            if (instance != null) {
                return true;
            }
            if (missing) {
                return false;
            }
            try {
                Class<?> api = Class.forName(className);
                Method lookupMethodHandle = api.getMethod(lookupMethod, UUID.class);
                instance = api.getMethod(instanceMethod).invoke(null);
                lookup = lookupMethodHandle;
                return instance != null;
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ex) {
                missing = true;
                return false;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return false;
            }
        }
    }
}
