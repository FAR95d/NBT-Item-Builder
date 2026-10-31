package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * Fills every option list in {@link ItemSpec} and {@link Components} from the client's
 * live registries, so pickers, strips and autocomplete show everything the game or its
 * mods define instead of a frozen vanilla snapshot. Runs when the builder screen opens
 * (it only opens in a world, once the datapack registries have synced).
 *
 * Deliberately avoids the registry-id class (renamed ResourceLocation to Identifier in
 * 1.21.11) by going through the per-version {@link Ids} helper, so this file compiles
 * identically in every supported version from 1.21.10 through 26.x.
 */
final class GameLists {
    private GameLists() {}

    /** Vanilla trim material to the item shown as its strip icon (the registry no longer
     * records ingredients; unknown materials just get no icon). */
    private static final Map<String, String> INGREDIENTS = Map.ofEntries(
        Map.entry("quartz", "quartz"), Map.entry("iron", "iron_ingot"),
        Map.entry("netherite", "netherite_ingot"), Map.entry("redstone", "redstone"),
        Map.entry("copper", "copper_ingot"), Map.entry("gold", "gold_ingot"),
        Map.entry("emerald", "emerald"), Map.entry("diamond", "diamond"),
        Map.entry("lapis", "lapis_lazuli"), Map.entry("amethyst", "amethyst_shard"),
        Map.entry("resin", "resin_brick"));

    /** {material id, ingredient item id or null} for the wear-tab icon strip. */
    private static List<String[]> trimMats = List.of();

    static List<String[]> trimMaterials() {
        return trimMats;
    }

    /** Pushes live registry data into the shared lists. Never throws; each
     * section fails on its own so one bad registry cannot blank the rest. */
    static void refresh() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            RegistryAccess access = mc.level.registryAccess();

            // Enchantments: id, localized name, vanilla max level.
            tryC(() -> {
                List<String[]> ench = new ArrayList<>();
                for (var e : access.lookupOrThrow(Registries.ENCHANTMENT).entrySet()) {
                    ench.add(new String[] {Ids.of(e.getKey()), e.getValue().description().getString(),
                        Integer.toString(e.getValue().getMaxLevel())});
                }
                ItemSpec.setEnchants(ench);
            });

            // Attributes live in a static registry (modded ones register there too).
            tryC(() -> {
                List<String[]> attrs = new ArrayList<>();
                for (var e : BuiltInRegistries.ATTRIBUTE.entrySet()) {
                    attrs.add(new String[] {Ids.of(e.getKey()),
                        Component.translatable(e.getValue().getDescriptionId()).getString()});
                }
                ItemSpec.setAttributes(attrs);
            });

            tryC(() -> Components.bannerIds = ids(access.lookupOrThrow(Registries.BANNER_PATTERN)));
            tryC(() -> Components.trimPatternIds = ids(access.lookupOrThrow(Registries.TRIM_PATTERN)));
            tryC(() -> Components.songIds = ids(access.lookupOrThrow(Registries.JUKEBOX_SONG)));
            tryC(() -> Components.setInstruments(ids(access.lookupOrThrow(Registries.INSTRUMENT))));

            // Trim materials keep their ingredient table only for the strip icons.
            tryC(() -> {
                List<String[]> mats = new ArrayList<>();
                for (var e : access.lookupOrThrow(Registries.TRIM_MATERIAL).entrySet()) {
                    String id = Ids.of(e.getKey());
                    mats.add(new String[] {id, INGREDIENTS.get(pathOf(id))});
                }
                trimMats = List.copyOf(mats);
            });
        } catch (RuntimeException e) {
            // Main menu or a very early join: keep the last set.
        }
    }

    private interface Section {
        void run();
    }

    private static void tryC(Section r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            // A modded registry with a bad entry: keep whatever we had.
        }
    }

    /** Registry id paths, in registry order. */
    private static <T> List<String> ids(Registry<T> reg) {
        List<String> out = new ArrayList<>();
        for (Object id : reg.keySet()) out.add(Ids.of(id));
        return out;
    }

    /** "minecraft:x" to "x", for tables keyed on bare vanilla paths. */
    private static String pathOf(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }
}
