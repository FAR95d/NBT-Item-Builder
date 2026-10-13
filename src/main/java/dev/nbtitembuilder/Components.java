package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Option lists and text helpers shared by the builder screens. No Minecraft
 * classes are used, so this can be tested on its own.
 *
 * The component ids match the 1.21 Java Edition data component format
 * (minecraft.wiki/w/Data_component_format).
 */
public final class Components {
    private Components() {}

    public static final String[] RARITY_KEYS = {"", "common", "uncommon", "rare", "epic"};
    public static final String[] RARITY_LABELS = {"Default", "Common", "Uncommon", "Rare", "Epic"};

    public static final String[] GLINT_KEYS = {"", "true", "false"};
    public static final String[] GLINT_LABELS = {"Default", "Always glint", "Never glint"};

    public static final String[] EAT_ANIM_KEYS = {"", "eat", "drink"};
    public static final String[] EAT_ANIM_LABELS = {"Default", "Eat", "Drink"};

    public static final String[] EQUIP_KEYS = {"", "head", "chest", "legs", "feet", "body", "mainhand", "offhand", "saddle"};
    public static final String[] EQUIP_LABELS = {"Not equipable", "Head", "Chest", "Legs", "Feet", "Body", "Main hand", "Off hand", "Saddle"};

    /** Component ids the builder has structured fields for (kept out of suggestions for the raw box). */
    public static final String[] STRUCTURED = {
        "minecraft:custom_name", "minecraft:lore", "minecraft:enchantments",
        "minecraft:stored_enchantments",
        "minecraft:attribute_modifiers", "minecraft:unbreakable", "minecraft:container",
        "minecraft:rarity", "minecraft:max_stack_size", "minecraft:damage",
        "minecraft:max_damage", "minecraft:repair_cost", "minecraft:item_model", "minecraft:custom_model_data",
        "minecraft:custom_data", "minecraft:enchantment_glint_override",
        "minecraft:food", "minecraft:consumable", "minecraft:equippable",
        "minecraft:use_cooldown", "minecraft:use_remainder", "minecraft:block_entity_data",
        "minecraft:potion_contents", "minecraft:banner_patterns", "minecraft:base_color",
        "minecraft:trim", "minecraft:dyed_color", "minecraft:bees", "minecraft:fireworks",
        "minecraft:container_loot", "minecraft:pot_decorations", "minecraft:profile",
        "minecraft:instrument", "minecraft:jukebox_playable"
    };

    /** Component ids offered for the "Custom components" box and NBT autocomplete. */
    public static final String[][] COMPONENT_OPTIONS = {
        {"minecraft:custom_data", "Custom data"},
        {"minecraft:custom_model_data", "Custom model data"},
        {"minecraft:item_model", "Item model"},
        {"minecraft:tooltip_style", "Tooltip style"},
        {"minecraft:rarity", "Rarity"},
        {"minecraft:max_stack_size", "Max stack size"},
        {"minecraft:damage", "Damage"},
        {"minecraft:max_damage", "Max damage"},
        {"minecraft:repair_cost", "Repair cost"},
        {"minecraft:repairable", "Repairable"},
        {"minecraft:enchantable", "Enchantable"},
        {"minecraft:enchantment_glint_override", "Glint override"},
        {"minecraft:food", "Food"},
        {"minecraft:consumable", "Consumable"},
        {"minecraft:use_remainder", "Use remainder"},
        {"minecraft:use_cooldown", "Use cooldown"},
        {"minecraft:equippable", "Equippable"},
        {"minecraft:glider", "Glider"},
        {"minecraft:tool", "Tool"},
        {"minecraft:weapon", "Weapon"},
        {"minecraft:blocks_attacks", "Blocks attacks"},
        {"minecraft:damage_resistant", "Damage resistant"},
        {"minecraft:break_sound", "Break sound"},
        {"minecraft:can_break", "Can break"},
        {"minecraft:can_place_on", "Can place on"},
        {"minecraft:block_entity_data", "Block entity data"},
        {"minecraft:block_state", "Block state"},
        {"minecraft:entity_data", "Entity data"},
        {"minecraft:bucket_entity_data", "Bucket entity data"},
        {"minecraft:container", "Container"},
        {"minecraft:container_loot", "Container loot"},
        {"minecraft:bundle_contents", "Bundle contents"},
        {"minecraft:charged_projectiles", "Charged projectiles"},
        {"minecraft:fireworks", "Fireworks"},
        {"minecraft:firework_explosion", "Firework explosion"},
        {"minecraft:instrument", "Instrument"},
        {"minecraft:jukebox_playable", "Jukebox song"},
        {"minecraft:trim", "Trim"},
        {"minecraft:dyed_color", "Dyed color"},
        {"minecraft:banner_patterns", "Banner patterns"},
        {"minecraft:base_color", "Base color"},
        {"minecraft:pot_decorations", "Pot decorations"},
        {"minecraft:bees", "Bees"},
        {"minecraft:lock", "Lock"},
        {"minecraft:note_block_sound", "Note block sound"},
        {"minecraft:profile", "Player head"},
        {"minecraft:map_id", "Map id"},
        {"minecraft:potion_contents", "Potion contents"},
        {"minecraft:suspicious_stew_effects", "Stew effects"},
        {"minecraft:stored_enchantments", "Stored enchantments (book)"},
        {"minecraft:writable_book_content", "Book content"},
        {"minecraft:ominous_bottle_amplifier", "Ominous bottle"},
        {"minecraft:tooltip_display", "Tooltip display"},
        {"minecraft:death_protection", "Death protection (totem)"},
        {"minecraft:villager_food", "Villager food"},
    };

    /** Mobs commonly used in spawners, for the picker and autocomplete. */
    public static final String[][] ENTITY_OPTIONS = {
        {"zombie", "Zombie"}, {"skeleton", "Skeleton"}, {"spider", "Spider"},
        {"cave_spider", "Cave spider"}, {"creeper", "Creeper"}, {"enderman", "Enderman"},
        {"slime", "Slime"}, {"blaze", "Blaze"}, {"ghast", "Ghast"},
        {"magma_cube", "Magma cube"}, {"wither_skeleton", "Wither skeleton"},
        {"piglin", "Piglin"}, {"piglin_brute", "Piglin brute"}, {"hoglin", "Hoglin"},
        {"zoglin", "Zoglin"}, {"ghastling", "Ghastling"},
        {"drowned", "Drowned"}, {"husk", "Husk"}, {"stray", "Stray"},
        {"phantom", "Phantom"}, {"witch", "Witch"}, {"evoker", "Evoker"},
        {"vindicator", "Vindicator"}, {"pillager", "Pillager"}, {"ravager", "Ravager"},
        {"warden", "Warden"}, {"endermite", "Endermite"}, {"silverfish", "Silverfish"},
        {"guardian", "Guardian"}, {"elder_guardian", "Elder guardian"},
        {"shulker", "Shulker"}, {"cow", "Cow"}, {"pig", "Pig"},
        {"sheep", "Sheep"}, {"chicken", "Chicken"}, {"horse", "Horse"},
        {"wolf", "Wolf"}, {"cat", "Cat"}, {"villager", "Villager"},
        {"iron_golem", "Iron golem"}, {"snow_golem", "Snow golem"}, {"bee", "Bee"},
        {"axolotl", "Axolotl"}, {"allay", "Allay"}, {"sniffer", "Sniffer"},
        {"camel", "Camel"}, {"frog", "Frog"}, {"tadpole", "Tadpole"},
        {"armadillo", "Armadillo"}, {"bogged", "Bogged"}, {"breeze", "Breeze"},
        {"creaking", "Creaking"},
    };

    /** {id, label} pairs for the component picker. */
    public static List<String[]> componentOptions() {
        List<String[]> out = new ArrayList<>();
        for (String[] c : COMPONENT_OPTIONS) out.add(new String[] {c[0], c[1]});
        return out;
    }

    /** {id, label} pairs for the spawner entity picker. */
    public static List<String[]> entityOptions() {
        List<String[]> out = new ArrayList<>();
        for (String[] c : ENTITY_OPTIONS) out.add(new String[] {c[0], c[1]});
        return out;
    }

    /**
     * True when the query finds the entry: plain substring on the id or name,
     * or the same with underscores removed, so "comm_" still finds
     * "command_block" and "oak log" finds "oak_log".
     */
    public static boolean matches(String id, String name, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (q.isEmpty()) return true;
        String lid = id == null ? "" : id.toLowerCase(Locale.ROOT);
        String lname = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lid.contains(q) || lname.contains(q)) return true;
        String sq = q.replace("_", "");
        if (sq.isEmpty()) return true;
        return lid.replace("_", "").contains(sq) || lname.replace(" ", "").replace("_", "").contains(sq);
    }

    /** Parses a decimal number, or returns the fallback when the box is empty or bad. */
    public static double parseDoubleOr(String s, double fallback) {
        try {
            return Double.parseDouble(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Rounds v to the step grid: whole numbers when step is 1 or more, decimals otherwise. */
    public static String fmtStep(double v, double step) {
        double r = step <= 0 ? v : Math.round(v / step) * step;
        if (r == -0d) r = 0d;
        if (step >= 1) return String.valueOf((int) Math.round(r));
        return new java.math.BigDecimal(Double.toString(r)).stripTrailingZeros().toPlainString();
    }

    /** Turns an id like "diamond_sword" into "Diamond sword" for picker lists. */
    public static String humanize(String id) {
        String b = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String[] parts = b.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    /** {id, label} pairs with auto-made labels, for long id pickers. */
    public static List<String[]> options(String[] ids) {
        List<String[]> out = new ArrayList<>();
        for (String id : ids) out.add(new String[] {id, humanize(id)});
        return out;
    }

    /** Banner pattern ids from the vanilla files, for the banner picker. */
    public static final String[] BANNER_IDS = {
        "base", "square_bottom_left", "square_bottom_right", "square_top_left", "square_top_right",
        "stripe_bottom", "stripe_top", "stripe_left", "stripe_right", "stripe_center", "stripe_middle",
        "stripe_downright", "stripe_downleft", "small_stripes", "cross", "straight_cross",
        "diagonal_left", "diagonal_right", "diagonal_up_left", "diagonal_up_right",
        "triangle_bottom", "triangle_top", "triangles_bottom", "triangles_top",
        "circle", "rhombus", "half_vertical", "half_horizontal", "half_vertical_right", "half_horizontal_bottom",
        "border", "curly_border", "gradient", "gradient_up", "bricks", "field_masoned", "bordure_indented",
        "creeper", "skull", "flower", "mojang", "globe", "piglin", "flow", "guster",
    };

    /** Dye colors, for banner colors and the base color button. */
    public static final String[] DYE_KEYS = {"", "white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    public static final String[] DYE_LABELS = {"Default", "White", "Orange", "Magenta", "Light blue", "Yellow",
        "Lime", "Pink", "Gray", "Light gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"};

    /** Armor trim materials from the vanilla files. */
    public static final String[] TRIM_MATS = {
        "quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond", "lapis", "amethyst",
        "resin",
    };

    /** Armor trim patterns from the vanilla files. */
    public static final String[] TRIM_PATS = {
        "bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib", "sentry", "shaper", "silence", "snout",
        "spire", "tide", "vex", "ward", "wayfinder", "wild",
    };

    /** Goat horn instruments from the vanilla files, for the cycle button. */
    public static final String[] INSTRUMENT_KEYS = {"", "ponder_goat_horn", "sing_goat_horn", "seek_goat_horn",
        "feel_goat_horn", "admire_goat_horn", "call_goat_horn", "yearn_goat_horn", "dream_goat_horn"};
    public static final String[] INSTRUMENT_LABELS = {"Default", "Ponder", "Sing", "Seek", "Feel", "Admire", "Call",
        "Yearn", "Dream"};

    /** Jukebox song ids from the vanilla files, for the song picker. */
    public static final String[] SONG_IDS = {
        "11", "13", "5", "blocks", "cat", "chirp", "creator", "creator_music_box", "far", "lava_chicken", "mall",
        "mellohi", "otherside", "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward",
    };

    /** True when the id is one the builder writes from its own fields. */
    public static boolean isStructured(String fullId) {
        String id = fullId.contains(":") ? fullId : "minecraft:" + fullId;
        for (String s : STRUCTURED) {
            if (s.equals(id)) return true;
        }
        return false;
    }

    /**
     * Suggests up to {@code max} component ids starting with (or containing)
     * the given fragment, for autocomplete. Fragment may or may not carry the
     * {@code minecraft:} prefix.
     */
    public static List<String> suggestComponents(String fragment, int max) {
        String q = fragment == null ? "" : fragment.trim().toLowerCase(Locale.ROOT);
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (String[] c : COMPONENT_OPTIONS) {
            String id = c[0];
            String base = id.startsWith("minecraft:") ? id.substring(10) : id;
            if (q.isEmpty() || id.toLowerCase(Locale.ROOT).startsWith(q) || base.startsWith(q)) {
                starts.add(id);
            } else if (id.toLowerCase(Locale.ROOT).contains(q) || c[1].toLowerCase(Locale.ROOT).contains(q)) {
                contains.add(id);
            }
        }
        List<String> out = new ArrayList<>(starts);
        out.addAll(contains);
        return out.size() > max ? out.subList(0, max) : out;
    }

    /** Plain SNBT keys that show up inside components (spawner, food, item root...). */
    public static final String[] NBT_KEYS = {
        "id", "count", "components", "slot", "item",
        "SpawnDelay", "MinSpawnDelay", "MaxSpawnDelay", "SpawnCount", "SpawnRange",
        "RequiredPlayerRange", "MaxNearbyEntities", "SpawnData", "SpawnPotentials", "entity",
        "nutrition", "saturation", "can_always_eat",
        "consume_seconds", "animation", "sound", "slot", "equip_sound", "seconds",
        "levels", "modifiers", "type", "amount", "operation",
    };

    /**
     * Suggests full key spellings for the raw NBT editor: component ids first,
     * then plain keys. Accepts fragments with or without quotes/namespace.
     */
    public static List<String> suggestNbtKey(String fragment, int max) {
        String q = fragment == null ? "" : fragment.trim();
        if (q.startsWith("\"")) q = q.substring(1);
        if (q.startsWith("minecraft:")) q = q.substring(10);
        q = q.toLowerCase(Locale.ROOT);
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (String[] c : COMPONENT_OPTIONS) {
            String base = c[0].substring(10);
            if (q.isEmpty() || base.startsWith(q) || c[0].toLowerCase(Locale.ROOT).startsWith(q)) {
                starts.add(c[0]);
            } else if (base.contains(q) || c[1].toLowerCase(Locale.ROOT).contains(q)) {
                contains.add(c[0]);
            }
        }
        for (String k : NBT_KEYS) {
            String low = k.toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && low.startsWith(q) && !starts.contains(k)) starts.add(k);
        }
        List<String> out = new ArrayList<>(starts);
        out.addAll(contains);
        return out.size() > max ? out.subList(0, max) : out;
    }

    /** Suggests entity ids for the spawner field. */
    public static List<String> suggestEntities(String fragment, int max) {
        String q = fragment == null ? "" : fragment.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (String[] c : ENTITY_OPTIONS) {
            if (q.isEmpty() || c[0].startsWith(q)) starts.add(c[0]);
            else if (c[0].contains(q) || c[1].toLowerCase(Locale.ROOT).contains(q)) contains.add(c[0]);
        }
        List<String> out = new ArrayList<>(starts);
        out.addAll(contains);
        return out.size() > max ? out.subList(0, max) : out;
    }

    /**
     * "Did you mean ...?" correction for a mistyped id, comparing against the
     * component list, entities and a few core ids. Returns null when nothing
     * is close enough.
     */
    public static String correct(String raw) {
        if (raw == null) return null;
        String q = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (q.isEmpty()) return null;
        if (q.startsWith("minecraft:")) q = q.substring(10);
        List<String> pool = new ArrayList<>();
        for (String[] c : COMPONENT_OPTIONS) pool.add(c[0].substring(10));
        for (String[] c : ENTITY_OPTIONS) pool.add(c[0]);
        for (String s : new String[] {"spawner", "chest", "diamond_sword", "common", "uncommon", "rare", "epic"}) {
            pool.add(s);
        }
        String best = null;
        int bestD = Integer.MAX_VALUE;
        for (String cand : pool) {
            int d = distance(q, cand);
            if (d < bestD) {
                bestD = d;
                best = cand;
            }
        }
        if (best == null) return null;
        int allowed = Math.max(1, Math.min(3, best.length() / 3));
        return bestD <= allowed ? best : null;
    }

    /** Classic edit distance, capped to keep long strings cheap. */
    static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (Math.abs(n - m) > 4) return Integer.MAX_VALUE / 2;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[m];
    }
}
