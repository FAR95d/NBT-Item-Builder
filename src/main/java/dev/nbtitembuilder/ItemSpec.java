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
            b.comps.add(new String[] {"minecraft:custom_name", styled(name, m.noItalic, m.nameStyle)});
        }
        List<String> lore = parseLore(m.lore);
        if (!lore.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (String l : lore) lines.add(styled(l, m.noItalic, m.loreStyle));
            b.comps.add(new String[] {"minecraft:lore", "[" + String.join(",", lines) + "]"});
        }
        String ench = enchantments(m.enchants);
        if (ench != null) b.comps.add(new String[] {"minecraft:enchantments", ench});
        String attrs = attributes(m.attrs);
        if (attrs != null) b.comps.add(new String[] {"minecraft:attribute_modifiers", attrs});
        if (m.unbreakable) b.comps.add(new String[] {"minecraft:unbreakable", "{}"});

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
        for (Map.Entry<String, String> e : m.extras.entrySet()) {
            b.comps.add(new String[] {e.getKey(), e.getValue()});
        }
        return b;
    }

    /** Text with an optional solid color or a per-character gradient. */
    private static String styled(String s, boolean noItalic, ItemModel.ColorStyle st) {
        StringBuilder b = new StringBuilder();
        if (st.mode == ItemModel.ColorStyle.GRADIENT) {
            int[] cps = s.codePoints().toArray();
            b.append("{text:\"\"");
            if (noItalic) b.append(",italic:0b");
            b.append(",extra:[");
            for (int i = 0; i < cps.length; i++) {
                float t = cps.length == 1 ? 0f : i / (float) (cps.length - 1);
                if (i > 0) b.append(',');
                b.append("{text:").append(Snbt.quote(new String(Character.toChars(cps[i]))))
                        .append(",color:").append(Snbt.quote(Colors.hex(Colors.lerp(st.c1, st.c2, t)))).append('}');
            }
            return b.append("]}").toString();
        }
        b.append("{text:").append(Snbt.quote(s));
        if (noItalic) b.append(",italic:0b");
        if (st.mode == ItemModel.ColorStyle.SOLID) b.append(",color:").append(Snbt.quote(Colors.hex(st.c1)));
        return b.append('}').toString();
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
            comps.add(Snbt.quote("minecraft:custom_name") + ":" + styled(k.name, true, new ItemModel.ColorStyle(0, 0)));
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
