package dev.nbtitembuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

/** Reads the item in the player's main hand as SNBT, and decodes SNBT back for previews. */
final class Held {
    private Held() {}

    /** Returns the held item as SNBT, or throws IllegalArgumentException with a message to show. */
    static String snbt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            throw new IllegalArgumentException("Join a world first.");
        }
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Your main hand is empty. Hold the item first.");
        }
        Tag tag = ItemStack.CODEC
                .encodeStart(mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack)
                .result().orElse(null);
        if (tag == null) {
            throw new IllegalArgumentException("Could not read the held item.");
        }
        return tag.toString();
    }

    /** One contents-box entry for the held item, e.g. {@code diamond x3}. */
    static String handEntry() {
        String snbt = snbt();
        Snbt.Node root;
        try {
            root = Snbt.parse(snbt);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not read the held item.");
        }
        if (!(root instanceof Snbt.Obj o)) throw new IllegalArgumentException("Could not read the held item.");
        String id = o.m.get("id") instanceof Snbt.Str s ? s.v : "";
        if (id.startsWith("minecraft:")) id = id.substring(10);
        if (id.isEmpty()) throw new IllegalArgumentException("Could not read the held item.");
        int count = 1;
        if (o.m.get("count") instanceof Snbt.Num n) count = Math.max(1, (int) Math.round(n.asDouble()));
        return count > 1 ? id + " x" + count : id;
    }

    /**
     * Turns item SNBT (as built by {@link ItemSpec}) back into an ItemStack for
     * the preview icon. Returns null when it cannot be decoded (bad id or data).
     */
    static ItemStack preview(String snbt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || snbt == null || snbt.isBlank()) return null;
        try {
            Tag tag = TagParser.create(NbtOps.INSTANCE).parseFully(snbt);
            return ItemStack.CODEC
                    .parse(mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag)
                    .result().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Durability of the plain item id, without any components. Returns -1 when
     * it cannot be read (no world, empty or unknown id). Used to check rules
     * the game enforces, like damageable items never stacking.
     */
    static int baseMaxDamage(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || id == null || id.isBlank()) return -1;
        try {
            String nid = id.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
            if (!nid.contains(":")) nid = "minecraft:" + nid;
            Tag tag = TagParser.create(NbtOps.INSTANCE).parseFully("{id:\"" + nid + "\",count:1}");
            ItemStack stack = ItemStack.CODEC
                    .parse(mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag)
                    .result().orElse(null);
            return stack == null ? -1 : stack.getMaxDamage();
        } catch (Exception e) {
            return -1;
        }
    }
}
