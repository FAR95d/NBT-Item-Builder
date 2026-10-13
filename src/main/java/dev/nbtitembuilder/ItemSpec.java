package dev.nbtitembuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an {@link ItemModel} into item NBT or a command that uses the Minecraft 1.21.5+
 * item component syntax. No Minecraft classes are used, so this can be tested on its own.
 */
public final class ItemSpec {
    private ItemSpec() {}

    // ---- option lists -------------------------------------------------------------------

    /** id, label, vanilla maximum level */
    private static final String[][] ENCH = {
        {"sharpness", "Sharpness", "5"}, {"smite", "Smite", "5"}, {"bane_of_arthropods", "Bane of Arthropods", "5"},
        {"knockback", "Knockback", "2"}, {"fire_aspect", "Fire Aspect", "2"}, {"looting", "Looting", "3"},
        {"sweeping_edge", "Sweeping Edge", "3"}, {"efficiency", "Efficiency", "5"}, {"silk_touch", "Silk Touch", "1"},
        {"unbreaking", "Unbreaking", "3"}, {"fortune", "Fortune", "3"}, {"mending", "Mending", "1"},
        {"power", "Power", "5"}, {"punch", "Punch", "2"}, {"flame", "Flame", "1"}, {"infinity", "Infinity", "1"},
        {"protection", "Protection", "4"}, {"fire_protection", "Fire Protection", "4"},
        {"blast_protection", "Blast Protection", "4"}, {"projectile_protection", "Projectile Protection", "4"},
        {"feather_falling", "Feather Falling", "4"}, {"respiration", "Respiration", "3"},
        {"aqua_affinity", "Aqua Affinity", "1"}, {"thorns", "Thorns", "3"}, {"depth_strider", "Depth Strider", "3"},
        {"frost_walker", "Frost Walker", "2"}, {"soul_speed", "Soul Speed", "3"}, {"swift_sneak", "Swift Sneak", "3"},
        {"luck_of_the_sea", "Luck of the Sea", "3"}, {"lure", "Lure", "3"}, {"loyalty", "Loyalty", "3"},
        {"impaling", "Impaling", "5"}, {"riptide", "Riptide", "3"}, {"channeling", "Channeling", "1"},
        {"multishot", "Multishot", "1"}, {"quick_charge", "Quick Charge", "3"}, {"piercing", "Piercing", "4"},
        {"curse_of_binding", "Curse of Binding", "1"}, {"curse_of_vanishing", "Curse of Vanishing", "1"},
        {"density", "Density", "5"}, {"breach", "Breach", "4"}, {"wind_burst", "Wind Burst", "3"}
    };

    private static final String[][] ATTR = {
        {"max_health", "Max health"}, {"attack_damage", "Attack damage"}, {"attack_speed", "Attack speed"},
        {"armor", "Armor"}, {"armor_toughness", "Armor toughness"}, {"knockback_resistance", "Knockback resistance"},
        {"movement_speed", "Movement speed"}, {"attack_knockback", "Attack knockback"}, {"luck", "Luck"},
        {"max_absorption", "Max absorption"}, {"flying_speed", "Flying speed"}, {"follow_range", "Follow range"},
        {"scale", "Scale"}, {"step_height", "Step height"}, {"gravity", "Gravity"}, {"jump_strength", "Jump strength"},
        {"safe_fall_distance", "Safe fall distance"}, {"fall_damage_multiplier", "Fall damage multiplier"},
        {"burning_time", "Burning time"}, {"explosion_knockback_resistance", "Explosion knockback resistance"},
        {"movement_efficiency", "Movement efficiency"}, {"oxygen_bonus", "Oxygen bonus"},
        {"water_movement_efficiency", "Water movement efficiency"},
        {"block_interaction_range", "Block interaction range"}, {"entity_interaction_range", "Entity interaction range"},
        {"block_break_speed", "Block break speed"}, {"mining_efficiency", "Mining efficiency"},
        {"sneaking_speed", "Sneaking speed"}, {"submerged_mining_speed", "Submerged mining speed"},
        {"sweeping_damage_ratio", "Sweeping damage ratio"}
    };

    public static final String[] OP_KEYS = {"add", "multiply_base", "multiply_total"};
    public static final String[] OP_LABELS = {"Add", "Mult. base", "Mult. total"};
    private static final String[] OP_COMPONENT = {"add_value", "add_multiplied_base", "add_multiplied_total"};
    public static final String[] SLOT_KEYS = {"any", "mainhand", "offhand", "head", "chest", "legs", "feet"};
    public static final String[] SLOT_LABELS = {"Any slot", "Main hand", "Off hand", "Head", "Chest", "Legs", "Feet"};

    private static final Map<String, Integer> ENCH_MAX = new HashMap<>();
    static {
        for (String[] e : ENCH) ENCH_MAX.put(e[0], Integer.parseInt(e[2]));
    }

    /** {id, label} pairs for the enchantment picker. */
    public static List<String[]> enchantOptions() {
        List<String[]> out = new ArrayList<>();
        for (String[] e : ENCH) out.add(new String[] {e[0], e[1]});
        return out;
    }

    /** {id, label} pairs for the attribute picker. */
    public static List<String[]> attributeOptions() {
        List<String[]> out = new ArrayList<>();
        for (String[] a : ATTR) out.add(new String[] {a[0], a[1]});
        return out;
    }

    public static int maxLevel(String enchantId) {
        return ENCH_MAX.getOrDefault(base(enchantId.trim().toLowerCase(Locale.ROOT)), 1);
    }

    // ---- output -------------------------------------------------------------------------

    /** The command without a leading slash, ready for sendCommand. */
    public static String giveCommand(ItemModel m, String target) {
        Built b = build(m);
        String t = target == null || target.isBlank() ? "@s" : target.trim();
        return "give " + t + " " + b.id + bracket(b.comps) + " " + b.count;
    }

    /** A command that swaps the item in the main hand for this one. */
    public static String replaceHeldCommand(ItemModel m) {
        Built b = build(m);
        return "item replace entity @s weapon.mainhand with " + b.id + bracket(b.comps) + " " + b.count;
    }

    /** The item as compact SNBT: {id:..., count:..., components:{...}}. */
    public static String itemNbt(ItemModel m) {
        Built b = build(m);
        StringBuilder sb = new StringBuilder("{id:").append(Snbt.quote(b.id)).append(",count:").append(b.count);
        if (!b.comps.isEmpty()) {
            sb.append(",components:{");
            for (int i = 0; i < b.comps.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(Snbt.quote(b.comps.get(i)[0])).append(':').append(b.comps.get(i)[1]);
            }
            sb.append('}');
        }
        return sb.append('}').toString();
    }

    public static String itemNbtPretty(ItemModel m) {
        return Snbt.pretty(Snbt.parse(itemNbt(m)));
    }

    private static String bracket(List<String[]> comps) {
        if (comps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < comps.size(); i++) {
            if (i > 0) sb.append(',');
            String k = comps.get(i)[0];
            sb.append(k.startsWith("minecraft:") ? k.substring(10) : k).append('=').append(comps.get(i)[1]);
        }
        return sb.append(']').toString();
    }

    // ---- building -----------------------------------------------------------------------

    private static final class Built {
        String id;
        int count;
        final List<String[]> comps = new ArrayList<>();
    }

    /** An item inside a container. */
    private static final class Child {
        String id;
        int count = 1;
        int slot = -1;
        String name = "";
        boolean unbreakable;
        final Map<String, Integer> enchants = new LinkedHashMap<>();
    }

    private static Built build(ItemModel m) {
        Built b = new Built();
        b.id = ns(cleanId(m.id, "Item id"));
        b.count = parseCount(m.count);

        String name = m.name == null ? "" : m.name.trim();
        if (!name.isEmpty()) {
            b.comps.add(new String[] {"minecraft:custom_name", styled(name, m.nameStyle)});
        }
        List<String> lore = parseLore(m.lore);
        List<String> lines = new ArrayList<>();
        for (String l : lore) lines.add(styled(l, m.loreStyle));
        for (int[] row : m.picture) lines.add(pictureRow(row, !m.loreStyle.italic));
        if (!lines.isEmpty()) {
            b.comps.add(new String[] {"minecraft:lore", "[" + String.join(",", lines) + "]"});
        }
        String ench = enchantments(m.enchants);
        if (ench != null) {
            // Books keep their enchantments in stored_enchantments.
            String key = b.id.equals("minecraft:enchanted_book")
                    ? "minecraft:stored_enchantments" : "minecraft:enchantments";
            b.comps.add(new String[] {key, ench});
        }
        String attrs = attributes(m.attrs);
        if (attrs != null) b.comps.add(new String[] {"minecraft:attribute_modifiers", attrs});
        if (m.unbreakable) b.comps.add(new String[] {"minecraft:unbreakable", "{}"});
        String rarity = rarity(m.rarity);
        if (rarity != null) b.comps.add(new String[] {"minecraft:rarity", rarity});
        String maxStack = intInRange(m.maxStack, "Max stack size", 1, 99);
        if (maxStack != null) {
            b.comps.add(new String[] {"minecraft:max_stack_size", maxStack});
            if (b.count > Integer.parseInt(maxStack)) {
                throw new IllegalArgumentException(
                        "Count " + b.count + " is more than the max stack size " + maxStack + ".");
            }
        }
        String damage = intAtLeast(m.damage, "Damage", 0);
        if (damage != null) b.comps.add(new String[] {"minecraft:damage", damage});
        String repair = intAtLeast(m.repairCost, "Repair cost", 0);
        if (repair != null) b.comps.add(new String[] {"minecraft:repair_cost", repair});
        String maxDmg = intAtLeast(m.maxDamage, "Max damage", 1);
        if (maxDmg != null) b.comps.add(new String[] {"minecraft:max_damage", maxDmg});
        if (m.itemModelId != null && !m.itemModelId.isBlank()) {
            b.comps.add(new String[] {"minecraft:item_model", Snbt.quote(ns(cleanId(m.itemModelId, "Item model")))});
        }
        String cmd = rawCompound(m.customModelData, "Custom model data");
        if (cmd != null) b.comps.add(new String[] {"minecraft:custom_model_data", cmd});
        String cdata = rawCompound(m.customData, "Custom data");
        if (cdata != null) b.comps.add(new String[] {"minecraft:custom_data", cdata});
        if (m.glint != null && !m.glint.isBlank()) {
            String gl = m.glint.trim().toLowerCase(Locale.ROOT);
            if (!gl.equals("true") && !gl.equals("false")) {
                throw new IllegalArgumentException("Glint must be Default, Always glint or Never glint.");
            }
            b.comps.add(new String[] {"minecraft:enchantment_glint_override", gl});
        }

        String food = food(m);
        if (food != null) b.comps.add(new String[] {"minecraft:food",
                merged(m.extras, "minecraft:food", food, "nutrition", "saturation", "can_always_eat")});
        String consumable = consumable(m);
        if (consumable != null) b.comps.add(new String[] {"minecraft:consumable",
                merged(m.extras, "minecraft:consumable", consumable,
                        "consume_seconds", "animation", "sound")});
        String equip = equippable(m);
        if (equip != null) b.comps.add(new String[] {"minecraft:equippable",
                merged(m.extras, "minecraft:equippable", equip, "slot", "equip_sound")});
        String cooldown = useCooldown(m.useCooldown);
        if (cooldown != null) b.comps.add(new String[] {"minecraft:use_cooldown",
                merged(m.extras, "minecraft:use_cooldown", cooldown, "seconds")});
        if (m.useRemainder != null && !m.useRemainder.isBlank()) {
            b.comps.add(new String[] {"minecraft:use_remainder",
                    merged(m.extras, "minecraft:use_remainder",
                            "{id:" + Snbt.quote(ns(cleanId(m.useRemainder, "Use remainder"))) + ",count:1}",
                            "id", "count")});
        }

        String blockEntity = blockEntityData(m);
        if (blockEntity != null) b.comps.add(new String[] {"minecraft:block_entity_data", blockEntity});

        String potion = potionComp(m);
        if (potion != null) b.comps.add(new String[] {"minecraft:potion_contents",
                merged(m.extras, "minecraft:potion_contents", potion, "potion", "custom_effects", "custom_color")});
        if (m.bannerBase != null && !m.bannerBase.isBlank()) {
            b.comps.add(new String[] {"minecraft:base_color", Snbt.quote(dyeColor(m.bannerBase, "Base color"))});
        }
        String banner = bannerPatterns(m.bannerPats);
        if (banner != null) b.comps.add(new String[] {"minecraft:banner_patterns", banner});
        String trim = trim(m);
        if (trim != null) b.comps.add(new String[] {"minecraft:trim",
                merged(m.extras, "minecraft:trim", trim, "material", "pattern")});
        String dye = dyedColor(m.dye);
        if (dye != null) b.comps.add(new String[] {"minecraft:dyed_color",
                merged(m.extras, "minecraft:dyed_color", dye, "rgb")});
        String bees = bees(m);
        if (bees != null) b.comps.add(new String[] {"minecraft:bees", bees});
        String flight = intInRange(m.flight, "Flight duration", 0, 127);
        if (flight != null) b.comps.add(new String[] {"minecraft:fireworks", "{flight_duration:" + flight + "}"});
        if (m.loot != null && !m.loot.isBlank()) {
            b.comps.add(new String[] {"minecraft:container_loot",
                    merged(m.extras, "minecraft:container_loot",
                            "{loot_table:" + Snbt.quote(ns(cleanId(m.loot, "Loot table"))) + "}", "loot_table")});
        }
        String sherds = sherds(m.sherds);
        if (sherds != null) b.comps.add(new String[] {"minecraft:pot_decorations", sherds});
        if (m.headName != null && !m.headName.isBlank()) {
            if (m.headName.trim().length() > 16) throw new IllegalArgumentException("Player names hold 16 letters at most.");
            b.comps.add(new String[] {"minecraft:profile", "{name:" + Snbt.quote(m.headName.trim()) + "}"});
        }
        if (m.instrument != null && !m.instrument.isBlank()) {
            b.comps.add(new String[] {"minecraft:instrument",
                    Snbt.quote(ns(cleanId(m.instrument, "Instrument")))});
        }
        if (m.song != null && !m.song.isBlank()) {
            // The song holder is written as a bare id, not {song:...}.
            b.comps.add(new String[] {"minecraft:jukebox_playable",
                    Snbt.quote(ns(cleanId(m.song, "Song")))});
        }

        if (m.rawContainer != null) {
            b.comps.add(new String[] {"minecraft:container", m.rawContainer});
        } else {
            List<Child> kids = parseContents(m.contents);
            if (!kids.isEmpty()) {
                List<String> slots = new ArrayList<>();
                for (Child k : kids) slots.add(childNbt(k));
                b.comps.add(new String[] {"minecraft:container", "[" + String.join(",", slots) + "]"});
            }
        }
        for (Map.Entry<String, String> e : parseCustomComps(m.customComps).entrySet()) {
            b.comps.add(new String[] {e.getKey(), e.getValue()});
        }
        for (Map.Entry<String, String> e : m.extras.entrySet()) {
            if (hasKey(b.comps, e.getKey())) continue;
            b.comps.add(new String[] {e.getKey(), e.getValue()});
        }
        return b;
    }

    private static boolean hasKey(List<String[]> comps, String key) {
        for (String[] c : comps) {
            if (c[0].equals(key)) return true;
        }
        return false;
    }

    /** Text with an optional solid color or a per-character gradient, plus decorations. */
    private static String styled(String s, ItemModel.ColorStyle st) {
        String flags = flags(st);
        StringBuilder b = new StringBuilder();
        if (st.mode == ItemModel.ColorStyle.GRADIENT) {
            int[] cps = s.codePoints().toArray();
            b.append("{text:\"\"").append(flags);
            b.append(",extra:[");
            for (int i = 0; i < cps.length; i++) {
                float t = cps.length == 1 ? 0f : i / (float) (cps.length - 1);
                if (i > 0) b.append(',');
                b.append("{text:").append(Snbt.quote(new String(Character.toChars(cps[i]))))
                        .append(",color:").append(Snbt.quote(Colors.hex(Colors.lerp(st.c1, st.c2, t)))).append('}');
            }
            return b.append("]}").toString();
        }
        b.append("{text:").append(Snbt.quote(s)).append(flags);
        if (st.mode == ItemModel.ColorStyle.SOLID) b.append(",color:").append(Snbt.quote(Colors.hex(st.c1)));
        return b.append('}').toString();
    }

    /** Decoration flags for a text object: bold, underline, strikethrough, non-italic. */
    private static String flags(ItemModel.ColorStyle st) {
        StringBuilder f = new StringBuilder();
        if (st.bold) f.append(",bold:1b");
        if (st.underline) f.append(",underlined:1b");
        if (st.strikethrough) f.append(",strikethrough:1b");
        if (!st.italic) f.append(",italic:0b");
        return f.toString();
    }

    /** One picture row: block characters, each with its own color. */
    static String pictureRow(int[] row, boolean noItalic) {
        StringBuilder b = new StringBuilder("{text:\"\"");
        if (noItalic) b.append(",italic:0b");
        b.append(",extra:[");
        for (int i = 0; i < row.length; i++) {
            if (i > 0) b.append(',');
            b.append("{text:\"█\",color:").append(Snbt.quote(Colors.hex(row[i]))).append('}');
        }
        return b.append("]}").toString();
    }

    private static String enchantments(List<ItemModel.Ench> list) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ItemModel.Ench e : list) {
            if (e.id == null || e.id.isBlank()) continue;
            String id = cleanId(e.id, "Enchantment");
            out.put(id, parseLevel(e.level, id));
        }
        return enchantMap(out);
    }

    private static String enchantMap(Map<String, Integer> map) {
        if (map.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            parts.add(Snbt.quote(ns(e.getKey())) + ":" + e.getValue());
        }
        return "{" + String.join(",", parts) + "}";
    }

    private static int parseLevel(String s, String id) {
        if (s == null || s.isBlank()) return maxLevel(id);
        try {
            return Math.max(1, Math.min(255, Integer.parseInt(s.trim())));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Level for " + base(id) + " must be a whole number.");
        }
    }

    private static String attributes(List<ItemModel.Attr> list) {
        List<String> mods = new ArrayList<>();
        for (ItemModel.Attr a : list) {
            if (a.id == null || a.id.isBlank()) continue;
            String name = a.id.trim().toLowerCase(Locale.ROOT);
            for (String prefix : new String[] {"minecraft:", "generic.", "player."}) {
                if (name.startsWith(prefix)) name = name.substring(prefix.length());
            }
            if (!ATTR_BASE.matcher(name).matches()) {
                throw new IllegalArgumentException("Attribute \"" + a.id.trim() + "\" is not a valid name.");
            }
            double amount;
            try {
                amount = Double.parseDouble(a.amount == null ? "" : a.amount.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Amount for " + name + " must be a number.");
            }
            int op = indexOf(OP_KEYS, a.op);
            int slot = indexOf(SLOT_KEYS, a.slot);
            if (op < 0 || slot < 0) throw new IllegalArgumentException("Pick an operation and slot for " + name + ".");
            mods.add("{type:" + Snbt.quote("minecraft:" + name)
                    + ",slot:" + Snbt.quote(SLOT_KEYS[slot])
                    + ",id:" + Snbt.quote("nbtitembuilder:" + name + "_" + (mods.size() + 1))
                    + ",amount:" + num(amount)
                    + ",operation:" + Snbt.quote(OP_COMPONENT[op]) + "}");
        }
        return mods.isEmpty() ? null : "[" + String.join(",", mods) + "]";
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return -1;
    }

    private static String childNbt(Child k) {
        StringBuilder s = new StringBuilder("{slot:").append(k.slot)
                .append(",item:{id:").append(Snbt.quote(ns(k.id))).append(",count:").append(k.count);
        List<String> comps = new ArrayList<>();
        if (!k.name.isEmpty()) {
            comps.add(Snbt.quote("minecraft:custom_name") + ":" + styled(k.name, new ItemModel.ColorStyle(0, 0)));
        }
        String ench = enchantMap(k.enchants);
        if (ench != null) comps.add(Snbt.quote("minecraft:enchantments") + ":" + ench);
        if (k.unbreakable) comps.add(Snbt.quote("minecraft:unbreakable") + ":{}");
        if (!comps.isEmpty()) s.append(",components:{").append(String.join(",", comps)).append('}');
        return s.append("}}").toString();
    }

    private static String num(double d) {
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    // ---- item properties ------------------------------------------------------------

    private static String rarity(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().toLowerCase(Locale.ROOT);
        for (String k : Components.RARITY_KEYS) {
            if (k.equals(v) && !k.isEmpty()) return Snbt.quote(v);
        }
        throw new IllegalArgumentException("Rarity must be Common, Uncommon, Rare or Epic.");
    }

    /** Empty gives null, otherwise a validated int string within range. */
    private static String intInRange(String s, String what, int min, int max) {
        if (s == null || s.isBlank()) return null;
        try {
            int v = Integer.parseInt(s.trim());
            if (v < min || v > max) throw new IllegalArgumentException(what + " must be " + min + " to " + max + ".");
            return String.valueOf(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a whole number.");
        }
    }

    private static String intAtLeast(String s, String what, int min) {
        if (s == null || s.isBlank()) return null;
        try {
            int v = Integer.parseInt(s.trim());
            if (v < min) throw new IllegalArgumentException(what + " must be " + min + " or more.");
            return String.valueOf(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a whole number.");
        }
    }

    private static String floatAtLeast(String s, String what, double min) {
        if (s == null || s.isBlank()) return null;
        try {
            double v = Double.parseDouble(s.trim());
            if (v < min) throw new IllegalArgumentException(what + " must be " + min + " or more.");
            return num(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a number.");
        }
    }

    /** Empty gives null, otherwise validated SNBT kept as the user wrote it. */
    private static String rawSnbt(String s, String what, boolean mustBeCompound) {
        if (s == null || s.isBlank()) return null;
        Snbt.Node n;
        try {
            n = Snbt.parse(s.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(what + " is not valid SNBT: " + e.getMessage());
        }
        if (mustBeCompound && !(n instanceof Snbt.Obj)) {
            throw new IllegalArgumentException(what + " must look like {key:value}.");
        }
        return Snbt.compact(n);
    }

    private static String rawCompound(String s, String what) {
        return rawSnbt(s, what, true);
    }

    /** Food plus the consumable entry that makes it edible in 1.21. Null when not food. */
    private static String food(ItemModel m) {
        if (m.nutrition == null || m.nutrition.isBlank()) {
            if (m.saturation != null && !m.saturation.isBlank()) {
                throw new IllegalArgumentException("Saturation needs Nutrition set first.");
            }
            if (m.alwaysEat) throw new IllegalArgumentException("Always edible needs Nutrition set first.");
            return null;
        }
        String nut = intAtLeast(m.nutrition, "Nutrition", 0);
        String sat = m.saturation == null || m.saturation.isBlank() ? "0" : floatAtLeast(m.saturation, "Saturation", 0);
        StringBuilder sb = new StringBuilder("{nutrition:").append(nut).append(",saturation:").append(sat);
        if (m.alwaysEat) sb.append(",can_always_eat:true");
        return sb.append('}').toString();
    }

    private static boolean isFood(ItemModel m) {
        return m.nutrition != null && !m.nutrition.isBlank();
    }

    private static String consumable(ItemModel m) {
        boolean want = isFood(m);
        boolean hasSecs = m.eatSeconds != null && !m.eatSeconds.isBlank();
        boolean hasAnim = m.eatAnimation != null && !m.eatAnimation.isBlank();
        boolean hasSound = m.eatSound != null && !m.eatSound.isBlank();
        if (!want) {
            if (hasSecs || hasAnim || hasSound) {
                throw new IllegalArgumentException("Eat time, animation and sound need Nutrition set first.");
            }
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (hasSecs) parts.add("consume_seconds:" + floatAtLeast(m.eatSeconds, "Eat time", 0.05));
        if (hasAnim) {
            String a = m.eatAnimation.trim().toLowerCase(Locale.ROOT);
            if (!a.equals("eat") && !a.equals("drink")) {
                throw new IllegalArgumentException("Eat animation must be Eat or Drink.");
            }
            parts.add("animation:" + Snbt.quote(a));
        }
        if (hasSound) parts.add("sound:" + soundValue(m.eatSound.trim(), "Eat sound"));
        if (parts.isEmpty()) return "{}";
        return "{" + String.join(",", parts) + "}";
    }

    private static String equippable(ItemModel m) {
        if (m.equipSlot == null || m.equipSlot.isBlank()) {
            if (m.equipSound != null && !m.equipSound.isBlank()) {
                throw new IllegalArgumentException("Equip sound needs an equip slot first.");
            }
            return null;
        }
        String slot = m.equipSlot.trim().toLowerCase(Locale.ROOT);
        boolean ok = false;
        for (String k : Components.EQUIP_KEYS) {
            if (k.equals(slot) && !k.isEmpty()) ok = true;
        }
        if (!ok) throw new IllegalArgumentException("Pick an equip slot, e.g. head or chest.");
        StringBuilder sb = new StringBuilder("{slot:").append(Snbt.quote(slot));
        if (m.equipSound != null && !m.equipSound.isBlank()) {
            sb.append(",equip_sound:").append(soundValue(m.equipSound.trim(), "Equip sound"));
        }
        return sb.append('}').toString();
    }

    private static String useCooldown(String s) {
        if (s == null || s.isBlank()) return null;
        return "{seconds:" + floatAtLeast(s, "Cooldown", 0) + "}";
    }

    /** A sound id, or a full sound definition in {…} as loaded from an item. */
    private static String soundValue(String s, String what) {
        if (s.startsWith("{")) return rawCompound(s, what);
        return Snbt.quote(s);
    }

    // ---- more tab -----------------------------------------------------------------------

    /** Empty gives null, otherwise a validated dye color id. */
    private static String dyeColor(String s, String what) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().toLowerCase(Locale.ROOT);
        for (String k : Components.DYE_KEYS) {
            if (k.equals(v) && !k.isEmpty()) return v;
        }
        throw new IllegalArgumentException(what + " must be a dye color.");
    }

    /** Potion plus custom effects. Null when neither is set. */
    private static String potionComp(ItemModel m) {
        boolean hasPotion = m.potion != null && !m.potion.isBlank();
        List<String> effs = new ArrayList<>();
        for (ItemModel.CustomEffect ce : m.effects) {
            if (ce.id == null || ce.id.isBlank()) continue;
            String id = cleanId(ce.id, "Effect");
            String amp = intInRange(ce.amp, "Amplifier", 0, 255);
            double durSec = ce.dur == null || ce.dur.isBlank() ? 30 : Components.parseDoubleOr(ce.dur, -1);
            if (durSec < 0) throw new IllegalArgumentException("Duration must be 0 seconds or more.");
            String dur = String.valueOf((int) Math.round(durSec * 20));
            effs.add("{id:" + Snbt.quote(ns(id)) + ",amplifier:" + (amp == null ? "0" : amp)
                    + ",duration:" + dur + "}");
        }
        if (!hasPotion && effs.isEmpty() && (m.potionColor == null || m.potionColor.isBlank())) return null;
        StringBuilder sb = new StringBuilder("{potion:");
        sb.append(Snbt.quote(hasPotion ? ns(cleanId(m.potion, "Potion")) : "minecraft:water"));
        if (!effs.isEmpty()) sb.append(",custom_effects:[").append(String.join(",", effs)).append(']');
        if (m.potionColor != null && !m.potionColor.isBlank()) {
            Integer rgb = Colors.parseHex(m.potionColor.trim());
            if (rgb == null) throw new IllegalArgumentException("Potion color must look like #ff0000.");
            sb.append(",custom_color:").append(rgb);
        }
        return sb.append('}').toString();
    }

    /** Banner layers. Null when none are set. */
    private static String bannerPatterns(List<ItemModel.BannerPat> list) {
        List<String> layers = new ArrayList<>();
        for (ItemModel.BannerPat p : list) {
            if (p.pattern == null || p.pattern.isBlank()) continue;
            String pattern = cleanId(p.pattern, "Banner pattern");
            String color = dyeColor(p.color, "Banner color");
            if (color == null) throw new IllegalArgumentException("Pick a color for the banner pattern.");
            layers.add("{pattern:" + Snbt.quote(ns(pattern)) + ",color:" + Snbt.quote(color) + "}");
        }
        return layers.isEmpty() ? null : "[" + String.join(",", layers) + "]";
    }

    /** Armor trim. Null when neither half is set. */
    private static String trim(ItemModel m) {
        boolean hasM = m.trimMaterial != null && !m.trimMaterial.isBlank();
        boolean hasP = m.trimPattern != null && !m.trimPattern.isBlank();
        if (!hasM && !hasP) return null;
        if (hasM != hasP) throw new IllegalArgumentException("Set both the trim material and the pattern.");
        return "{material:" + Snbt.quote(ns(cleanId(m.trimMaterial, "Trim material")))
                + ",pattern:" + Snbt.quote(ns(cleanId(m.trimPattern, "Trim pattern"))) + "}";
    }

    /** Leather dye from a hex color. Null when empty. */
    private static String dyedColor(String s) {
        if (s == null || s.isBlank()) return null;
        Integer rgb = Colors.parseHex(s.trim());
        if (rgb == null) throw new IllegalArgumentException("Dye color must look like #ff0000.");
        return "{rgb:" + rgb + "}";
    }

    /** Bees: a plain hive from the count slider, or raw data loaded from an item. */
    private static String bees(ItemModel m) {
        if (m.beeCount != null && !m.beeCount.isBlank()) {
            int n = Integer.parseInt(intInRange(m.beeCount, "Bee count", 0, 99));
            if (n == 0) return null;
            List<String> bees = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                bees.add("{entity_data:{id:\"minecraft:bee\"},ticks_in_hive:0,min_ticks_in_hive:600}");
            }
            return "[" + String.join(",", bees) + "]";
        }
        if (m.beesRaw != null && !m.beesRaw.isBlank()) {
            Snbt.Node v;
            try {
                v = Snbt.parse(m.beesRaw.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Bee data is not valid SNBT: " + e.getMessage());
            }
            if (!(v instanceof Snbt.Arr)) throw new IllegalArgumentException("Bee data must look like [{...}].");
            return Snbt.compact(v);
        }
        return null;
    }

    /** Four sherd ids for a decorated pot: back,left,right,front. Null when empty. */
    private static String sherds(String s) {
        if (s == null || s.isBlank()) return null;
        List<String> ids = new ArrayList<>();
        for (String part : s.split("[,;\\s]+")) {
            if (!part.isBlank()) ids.add(Snbt.quote(ns(cleanId(part, "Sherd"))));
        }
        if (ids.size() != 4) {
            throw new IllegalArgumentException("List 4 sherds for the pot: back,left,right,front.");
        }
        return "[" + String.join(",", ids) + "]";
    }

    /**
     * Puts unknown keys kept in extras back into a structured component, so a
     * loaded item round-trips even when the builder has no field for a key.
     */
    private static String merged(Map<String, String> extras, String key, String built, String... known) {
        String extra = extras.get(key);
        if (extra == null) return built;
        Snbt.Node n;
        try {
            n = Snbt.parse(extra);
        } catch (IllegalArgumentException e) {
            return built;
        }
        if (!(n instanceof Snbt.Obj o)) return built;
        Set<String> knownSet = new HashSet<>(List.of(known));
        List<String> add = new ArrayList<>();
        for (Map.Entry<String, Snbt.Node> e : o.m.entrySet()) {
            if (!knownSet.contains(e.getKey())) add.add(e.getKey() + ":" + Snbt.compact(e.getValue()));
        }
        if (add.isEmpty()) return built;
        if (built.equals("{}")) return "{" + String.join(",", add) + "}";
        return built.substring(0, built.length() - 1) + "," + String.join(",", add) + "}";
    }

    /**
     * Extra components typed as {@code id=SNBT} entries separated by ;.
     * Returns full id to compact SNBT, preserving order.
     */
    public static Map<String, String> parseCustomComps(String s) {
        Map<String, String> out = new LinkedHashMap<>();
        if (s == null || s.isBlank()) return out;
        for (String entry : splitTopLevel(s, ';')) {
            if (entry.isBlank()) continue;
            int eq = indexOfTopLevel(entry, '=');
            if (eq < 0) {
                String hint = Components.correct(entry);
                throw new IllegalArgumentException("Custom entry \"" + entry.trim()
                        + "\" needs an = sign, e.g. minecraft:foo={bar:1}."
                        + (hint == null ? "" : " Did you mean " + hint + "?"));
            }
            String id = cleanIdWithHint(entry.substring(0, eq), "Custom component");
            String full = ns(id);
            if (Components.isStructured(full)) {
                throw new IllegalArgumentException("Custom entry " + base(full)
                        + " already has its own tab or field. Set it there instead.");
            }
            String value = entry.substring(eq + 1).trim();
            if (value.isEmpty()) throw new IllegalArgumentException("Custom entry " + base(full) + " needs a value.");
            try {
                out.put(full, Snbt.compact(Snbt.parse(value)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Value for " + base(full) + " is not valid SNBT: " + e.getMessage());
            }
        }
        return out;
    }

    /**
     * Block entity data for the item: the raw box merged with the spawner
     * quick fields. Null when neither is set.
     */
    private static String blockEntityData(ItemModel m) {
        boolean spawner = (m.spawnerEntity != null && !m.spawnerEntity.isBlank())
                || (m.entityData != null && !m.entityData.isBlank())
                || (m.spawnDelay != null && !m.spawnDelay.isBlank())
                || (m.minDelay != null && !m.minDelay.isBlank())
                || (m.maxDelay != null && !m.maxDelay.isBlank())
                || (m.spawnCount != null && !m.spawnCount.isBlank())
                || (m.spawnRange != null && !m.spawnRange.isBlank())
                || (m.requiredPlayers != null && !m.requiredPlayers.isBlank())
                || (m.maxNearby != null && !m.maxNearby.isBlank());
        if ((m.blockEntityRaw == null || m.blockEntityRaw.isBlank()) && !spawner) return null;

        Map<String, Snbt.Node> keys = new LinkedHashMap<>();
        if (m.blockEntityRaw != null && !m.blockEntityRaw.isBlank()) {
            Snbt.Node n;
            try {
                n = Snbt.parse(m.blockEntityRaw.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Block data is not valid SNBT: " + e.getMessage());
            }
            if (!(n instanceof Snbt.Obj)) {
                throw new IllegalArgumentException("Block data must look like {id:...,SpawnDelay:200}.");
            }
            keys.putAll(((Snbt.Obj) n).m);
        }
        if (spawner) {
            String id = "";
            if (keys.get("id") instanceof Snbt.Str s) id = s.v;
            if (!id.isEmpty() && !id.equals("mob_spawner") && !id.equals("minecraft:mob_spawner")) {
                throw new IllegalArgumentException("Spawner fields need a spawner, not " + id + ".");
            }
            keys.put("id", new Snbt.Str("mob_spawner"));
            putInt(keys, "SpawnDelay", intInRange(m.spawnDelay, "Spawn delay", 0, 32767));
            putInt(keys, "MinSpawnDelay", intInRange(m.minDelay, "Min spawn delay", 0, 32767));
            putInt(keys, "MaxSpawnDelay", intInRange(m.maxDelay, "Max spawn delay", 0, 32767));
            putInt(keys, "SpawnCount", intInRange(m.spawnCount, "Spawn count", 0, 32767));
            putInt(keys, "SpawnRange", intInRange(m.spawnRange, "Spawn range", 0, 32767));
            putInt(keys, "RequiredPlayerRange", intInRange(m.requiredPlayers, "Required player range", 0, 32767));
            putInt(keys, "MaxNearbyEntities", intInRange(m.maxNearby, "Max nearby entities", 0, 32767));
            if (m.spawnerEntity != null && !m.spawnerEntity.isBlank()) {
                String entity = cleanIdWithHint(m.spawnerEntity, "Spawner mob");
                Snbt.Obj entityObj = new Snbt.Obj();
                entityObj.m.put("id", new Snbt.Str(ns(entity)));
                if (m.entityData != null && !m.entityData.isBlank()) {
                    Snbt.Node extra;
                    try {
                        extra = Snbt.parse(m.entityData.trim());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Entity data is not valid SNBT: " + e.getMessage());
                    }
                    if (!(extra instanceof Snbt.Obj)) {
                        throw new IllegalArgumentException("Entity data must look like {Value:100}.");
                    }
                    for (Map.Entry<String, Snbt.Node> en : ((Snbt.Obj) extra).m.entrySet()) {
                        if (!en.getKey().equals("id")) entityObj.m.put(en.getKey(), en.getValue());
                    }
                }
                Snbt.Obj spawnData = new Snbt.Obj();
                spawnData.m.put("entity", entityObj);
                keys.put("SpawnData", spawnData);
            } else if (m.entityData != null && !m.entityData.isBlank()) {
                throw new IllegalArgumentException("Entity data needs a spawner mob first.");
            }
        }
        Snbt.Obj out = new Snbt.Obj();
        out.m.putAll(keys);
        return Snbt.compact(out);
    }

    private static void putInt(Map<String, Snbt.Node> keys, String key, String value) {
        if (value != null) keys.put(key, new Snbt.Num(value));
    }

    /** Splits on a separator that sits outside of {} [] and quotes. */
    static List<String> splitTopLevel(String s, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depthCurly = 0;
        int depthSquare = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && i + 1 < s.length()) {
                    cur.append(s.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                cur.append(c);
            } else if (c == '{') {
                depthCurly++;
                cur.append(c);
            } else if (c == '}') {
                depthCurly = Math.max(0, depthCurly - 1);
                cur.append(c);
            } else if (c == '[') {
                depthSquare++;
                cur.append(c);
            } else if (c == ']') {
                depthSquare = Math.max(0, depthSquare - 1);
                cur.append(c);
            } else if (c == sep && depthCurly == 0 && depthSquare == 0) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /** Index of a separator outside of {} [] and quotes, or -1. */
    private static int indexOfTopLevel(String s, char sep) {
        int depthCurly = 0;
        int depthSquare = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{') {
                depthCurly++;
            } else if (c == '}') {
                depthCurly = Math.max(0, depthCurly - 1);
            } else if (c == '[') {
                depthSquare++;
            } else if (c == ']') {
                depthSquare = Math.max(0, depthSquare - 1);
            } else if (c == sep && depthCurly == 0 && depthSquare == 0) {
                return i;
            }
        }
        return -1;
    }

    // ---- parsing ------------------------------------------------------------------------

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+|[a-z0-9_./-]+");
    private static final Pattern ATTR_BASE = Pattern.compile("[a-z0-9_]+");
    private static final Pattern COUNT_TOKEN = Pattern.compile("x(\\d+)");
    private static final Pattern SLOT_TOKEN = Pattern.compile("@(\\d+)");

    private static String ns(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static String base(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    private static String cleanId(String raw, String what) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (s.isEmpty()) throw new IllegalArgumentException("Enter an " + what.toLowerCase(Locale.ROOT) + ".");
        if (!ID.matcher(s).matches()) throw new IllegalArgumentException(what + " \"" + s + "\" is not a valid id.");
        return s;
    }

    /** Same as cleanId but appends a "did you mean" hint for likely typos. */
    public static String cleanIdWithHint(String raw, String what) {
        try {
            return cleanId(raw, what);
        } catch (IllegalArgumentException e) {
            String hint = Components.correct(raw == null ? "" : raw);
            if (hint != null) throw new IllegalArgumentException(e.getMessage() + " Did you mean " + hint + "?");
            throw e;
        }
    }

    private static int parseCount(String s) {
        if (s == null || s.isBlank()) return 1;
        try {
            return Math.max(1, Math.min(99, Integer.parseInt(s.trim())));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Count must be a whole number.");
        }
    }

    private static List<String> parseLore(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String line : s.split("\\|")) {
            if (!line.isBlank()) out.add(line.trim());
        }
        return out;
    }

    private static List<Child> parseContents(String s) {
        List<Child> out = new ArrayList<>();
        if (s == null) return out;
        int index = 0;
        for (String entry : s.split(";")) {
            if (entry.isBlank()) continue;
            List<String> tok = tokens(entry);
            if (tok.isEmpty()) continue;
            Child it = new Child();
            it.id = cleanId(tok.get(0), "Contents item");
            for (int i = 1; i < tok.size(); i++) {
                String t = tok.get(i);
                String lower = t.toLowerCase(Locale.ROOT);
                Matcher cm = COUNT_TOKEN.matcher(lower);
                Matcher sm = SLOT_TOKEN.matcher(lower);
                if (cm.matches()) {
                    it.count = Math.max(1, Math.min(99, Integer.parseInt(cm.group(1))));
                } else if (sm.matches()) {
                    it.slot = Integer.parseInt(sm.group(1));
                } else if (lower.startsWith("name=")) {
                    it.name = t.substring(5).trim();
                } else if (lower.equals("unbreakable")) {
                    it.unbreakable = true;
                } else if (t.contains("=")) {
                    String[] kv = t.split("=", 2);
                    String id = cleanId(kv[0], "Enchantment");
                    it.enchants.put(id, parseLevel(kv[1], id));
                } else {
                    String id = cleanId(t, "Enchantment");
                    it.enchants.put(id, maxLevel(id));
                }
            }
            if (index >= 27) throw new IllegalArgumentException("A barrel holds 27 items, so the list is too long.");
            if (it.slot < 0) it.slot = index;
            if (it.slot > 26) throw new IllegalArgumentException("Slots go from 0 to 26 (item " + (index + 1) + ").");
            out.add(it);
            index++;
        }
        Set<Integer> seen = new HashSet<>();
        for (Child it : out) {
            if (!seen.add(it.slot)) throw new IllegalArgumentException("Slot " + it.slot + " is used twice.");
        }
        return out;
    }

    /** Splits on whitespace. Text in double quotes stays together and the quotes are dropped. */
    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        boolean hadQuote = false;
        for (char c : s.toCharArray()) {
            if (c == '"') {
                inQuote = !inQuote;
                hadQuote = true;
            } else if (!inQuote && Character.isWhitespace(c)) {
                if (cur.length() > 0 || hadQuote) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    hadQuote = false;
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0 || hadQuote) out.add(cur.toString());
        return out;
    }
}
