package dev.nbtitembuilder;

/**
 * Checks the per-era {@link Ids} helper against the real runtime classes, via
 * reflection so this file compiles unchanged on every supported Minecraft version
 * (the id class was renamed ResourceLocation to Identifier in 1.21.11).
 */
public final class IdsSmoke {
    private IdsSmoke() {}

    /** @return true when it all checks out, false (with a message) when not. */
    static boolean run(StringBuilder out) {
        try {
            Class<?> idClass = null;
            String idName = null;
            for (String n : new String[] {"net.minecraft.resources.Identifier",
                    "net.minecraft.resources.ResourceLocation"}) {
                try {
                    idClass = Class.forName(n);
                    idName = n;
                    break;
                } catch (ClassNotFoundException e) {
                    // try the other name
                }
            }
            if (idClass == null) {
                out.append("IdsSmoke: no id class found (neither Identifier nor ResourceLocation)");
                return false;
            }
            Object id = idClass.getMethod("parse", String.class).invoke(null, "minecraft:quartz");
            Class<?> keyClass = Class.forName("net.minecraft.resources.ResourceKey");
            Object key = keyClass.getMethod("createRegistryKey", id.getClass()).invoke(null, id);

            // Both should read the full id the key was built from.
            if (!"minecraft:quartz".equals(Ids.of(key))) {
                out.append("IdsSmoke FAIL " + idName + ": resource key reads as '"
                    + Ids.of(key) + "', wanted 'minecraft:quartz'");
                return false;
            }
            if (!"minecraft:quartz".equals(Ids.of(id))) {
                out.append("IdsSmoke FAIL " + idName + ": registry id reads as '"
                    + Ids.of(id) + "', wanted 'minecraft:quartz'");
                return false;
            }
            if (!"plain".equals(Ids.of("plain"))) {
                out.append("IdsSmoke FAIL: plain string round-trip broken");
                return false;
            }
            out.append("IdsSmoke OK (id class " + idName.substring(idName.lastIndexOf('.') + 1) + ")");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            out.append("IdsSmoke could not load the id classes: " + e);
            return false;
        }
    }
}
