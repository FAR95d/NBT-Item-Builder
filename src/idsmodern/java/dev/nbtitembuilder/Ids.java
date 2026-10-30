package dev.nbtitembuilder;

/**
 * The one name Minecraft changed between 1.21.10 and newer builds: the id class
 * (ResourceLocation became Identifier) and the getter on a registry key. Each legacy
 * minor version has its own build of this tiny helper; everything else is shared.
 */
final class Ids {
    private Ids() {}

    /** "minecraft:x" for a registry key or a raw registry id. */
    static String of(Object keyOrId) {
        if (keyOrId instanceof net.minecraft.resources.Identifier k) return k.getPath();
        return String.valueOf(keyOrId);
    }
}
