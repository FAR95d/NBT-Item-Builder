package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills an {@link ItemModel} from an item's SNBT, for editing the item in your hand.
 * Anything the builder has no field for is kept and written back unchanged.
 */
public final class ItemLoader {
    private ItemLoader() {}

    /** Replaces everything in the model. Returns warnings about data that was simplified. */
    public static List<String> load(ItemModel m, String snbt) {
        Snbt.Node root = Snbt.parse(snbt);
        if (!(root instanceof Snbt.Obj)) throw new IllegalArgumentException("The item data could not be read.");
        Snbt.Obj o = (Snbt.Obj) root;
        List<String> warn = new ArrayList<>();
        m.reset();
        m.id = stripMc(str(o.m.get("id")));
        m.count = String.valueOf(Math.max(1, (int) Math.round(num(o.m.get("count"), 1))));

        Snbt.Node cn = o.m.get("components");
        if (!(cn instanceof Snbt.Obj)) return warn;

        for (Map.Entry<String, Snbt.Node> e : ((Snbt.Obj) cn).m.entrySet()) {
            String full = e.getKey().contains(":") ? e.getKey() : "minecraft:" + e.getKey();
            Snbt.Node v = e.getValue();
            switch (full) {
                case "minecraft:custom_name" -> {
                    Text t = readText(v);
                    m.name = t.text;
                    apply(m.nameStyle, t);
                    if (t.advanced) warn.add("The name formatting was simplified.");
                }
                case "minecraft:lore" -> {
                    List<String> lines = new ArrayList<>();
                    Text first = null;
                    int pictures = 0;
                    if (v instanceof Snbt.Arr) {
                        for (Snbt.Node line : ((Snbt.Arr) v).items) {
                            int[] pic = pictureRow(line);
                            if (pic != null) {
                                m.picture.add(pic);
                                pictures++;
                                continue;
                            }
                            Text t = readText(line);
                            if (first == null) first = t;
                            lines.add(t.text);
                            if (t.advanced) warn.add("Some lore formatting was simplified.");
                        }
                    }
                    m.lore = String.join(" | ", lines);
                    if (first != null) apply(m.loreStyle, first);
                    if (pictures > 0) warn.add("Kept " + pictures + " picture row(s) as an image.");
                }
                case "minecraft:enchantments" -> readEnchants(m, v);
                case "minecraft:stored_enchantments" -> readEnchants(m, v);
                case "minecraft:attribute_modifiers" -> readAttrs(m, v, warn);
                case "minecraft:unbreakable" -> m.unbreakable = true;
                case "minecraft:container" -> readContainer(m, v, warn);
                case "minecraft:rarity" -> m.rarity = stripMc(str(v));
                case "minecraft:max_stack_size" -> m.maxStack = intText(v);
                case "minecraft:damage" -> m.damage = intText(v);
                case "minecraft:max_damage" -> m.maxDamage = intText(v);
                case "minecraft:repair_cost" -> m.repairCost = intText(v);
                case "minecraft:item_model" -> m.itemModelId = stripMc(str(v));
                case "minecraft:custom_model_data" -> m.customModelData = Snbt.compact(v);
                case "minecraft:custom_data" -> m.customData = Snbt.compact(v);
                case "minecraft:enchantment_glint_override" -> m.glint = boolText(v);
                case "minecraft:food" -> readFood(m, v, warn);
                case "minecraft:consumable" -> readConsumable(m, v, warn);
                case "minecraft:equippable" -> readEquippable(m, v, warn);
                case "minecraft:use_cooldown" -> readCooldown(m, v, warn);
                case "minecraft:use_remainder" -> readRemainder(m, v, warn);
                case "minecraft:block_entity_data" -> readBlockEntity(m, v, warn);
                case "minecraft:potion_contents" -> readPotion(m, v, warn);
                case "minecraft:base_color" -> m.bannerBase = str(v);
                case "minecraft:banner_patterns" -> readBanner(m, v, warn);
                case "minecraft:trim" -> readTrim(m, v, warn);
                case "minecraft:dyed_color" -> readDye(m, v, warn);
                case "minecraft:bees" -> readBees(m, v, warn);
                case "minecraft:fireworks" -> readFlight(m, v, warn);
                case "minecraft:container_loot" -> readLoot(m, v, warn);
                case "minecraft:pot_decorations" -> readSherds(m, v, warn);
                case "minecraft:profile" -> readProfile(m, v, warn);
                case "minecraft:instrument" -> m.instrument = stripMc(str(v));
                case "minecraft:jukebox_playable" -> readSong(m, v, warn);
                default -> m.extras.put(full, Snbt.compact(v));
            }
        }
        // The same warning can repeat for several lore lines.
        List<String> unique = new ArrayList<>();
        for (String w : warn) if (!unique.contains(w)) unique.add(w);
        return unique;
    }

    // ---- text ---------------------------------------------------------------------------

    private static final class Text {
        String text = "";
        int mode = ItemModel.ColorStyle.DEFAULT;
        int c1;
        int c2;
        boolean bold;
        boolean underline;
        boolean strikethrough;
        boolean italicOn = true;
        int seenBold = -1;
        int seenUnder = -1;
        int seenStrike = -1;
        boolean advanced;
    }

    private static final class Seg {
        final String text;
        final Integer color;

        Seg(String text, Integer color) {
            this.text = text;
            this.color = color;
        }
    }

    private static void apply(ItemModel.ColorStyle st, Text t) {
        st.mode = t.mode;
        if (t.mode != ItemModel.ColorStyle.DEFAULT) {
            st.c1 = t.c1;
            st.c2 = t.mode == ItemModel.ColorStyle.GRADIENT ? t.c2 : st.c2;
        }
        st.bold = t.bold;
        st.underline = t.underline;
        st.strikethrough = t.strikethrough;
        st.italic = t.italicOn;
    }

    private static Text readText(Snbt.Node n) {
        Text t = new Text();
        List<Seg> segs = new ArrayList<>();
        flatten(n, null, segs, t, true);
        t.bold = t.seenBold == 1;
        t.underline = t.seenUnder == 1;
        t.strikethrough = t.seenStrike == 1;

        StringBuilder all = new StringBuilder();
        for (Seg s : segs) all.append(s.text);
        t.text = all.toString();

        List<Seg> colored = new ArrayList<>();
        boolean anyNull = false;
        boolean anyColor = false;
        for (Seg s : segs) {
            if (s.text.isEmpty()) continue;
            colored.add(s);
            if (s.color == null) anyNull = true;
            else anyColor = true;
        }
        if (!anyColor) return t;

        boolean allSame = true;
        for (Seg s : colored) {
            if (s.color == null || !s.color.equals(colored.get(0).color)) allSame = false;
        }
        if (allSame) {
            t.mode = ItemModel.ColorStyle.SOLID;
            t.c1 = colored.get(0).color;
            return t;
        }
        if (!anyNull && looksLikeGradient(colored)) {
            t.mode = ItemModel.ColorStyle.GRADIENT;
            t.c1 = colored.get(0).color;
            t.c2 = colored.get(colored.size() - 1).color;
            return t;
        }
        t.advanced = true;
        t.mode = ItemModel.ColorStyle.SOLID;
        for (Seg s : colored) {
            if (s.color != null) {
                t.c1 = s.color;
                break;
            }
        }
        return t;
    }

    /** One color per character, moving in a straight line from the first color to the last. */
    private static boolean looksLikeGradient(List<Seg> segs) {
        int n = segs.size();
        if (n < 2) return false;
        int a = segs.get(0).color;
        int b = segs.get(n - 1).color;
        for (int i = 0; i < n; i++) {
            Seg s = segs.get(i);
            if (s.text.codePointCount(0, s.text.length()) != 1) return false;
            int expect = Colors.lerp(a, b, i / (float) (n - 1));
            int got = s.color;
            for (int shift = 0; shift <= 16; shift += 8) {
                if (Math.abs(((expect >> shift) & 0xFF) - ((got >> shift) & 0xFF)) > 6) return false;
            }
        }
        return true;
    }

    private static void flatten(Snbt.Node n, Integer inherited, List<Seg> out, Text t, boolean top) {
        if (n instanceof Snbt.Str) {
            out.add(new Seg(((Snbt.Str) n).v, inherited));
        } else if (n instanceof Snbt.Arr) {
            for (Snbt.Node item : ((Snbt.Arr) n).items) flatten(item, inherited, out, t, false);
        } else if (n instanceof Snbt.Obj) {
            Snbt.Obj o = (Snbt.Obj) n;
            Integer color = inherited;
            Snbt.Node c = o.m.get("color");
            if (c instanceof Snbt.Str) {
                Integer parsed = Colors.parseColor(((Snbt.Str) c).v);
                if (parsed == null) t.advanced = true;
                else color = parsed;
            }
            Snbt.Node italic = o.m.get("italic");
            if (italic instanceof Snbt.Num num) {
                boolean on = num.asDouble() != 0d;
                if (top) {
                    t.italicOn = on;
                } else if (on != t.italicOn) {
                    t.advanced = true;
                }
            }
            observeFlag(t, o, "bold", 0);
            observeFlag(t, o, "underlined", 1);
            observeFlag(t, o, "strikethrough", 2);
            for (String k : new String[] {"obfuscated", "font", "shadow_color",
                    "click_event", "hover_event", "insertion", "translate", "keybind", "score", "selector", "nbt"}) {
                if (o.m.containsKey(k)) t.advanced = true;
            }
            Snbt.Node text = o.m.get("text");
            if (text instanceof Snbt.Str) out.add(new Seg(((Snbt.Str) text).v, color));
            Snbt.Node extra = o.m.get("extra");
            if (extra instanceof Snbt.Arr) {
                for (Snbt.Node item : ((Snbt.Arr) extra).items) flatten(item, color, out, t, false);
            }
        } else {
            t.advanced = true;
        }
    }

    /** Reads one lore line as picture pixels, or null when it is normal text. */
    private static int[] pictureRow(Snbt.Node n) {
        if (!(n instanceof Snbt.Obj o)) return null;
        if (!(o.m.get("extra") instanceof Snbt.Arr a) || a.items.isEmpty()) return null;
        Snbt.Node text = o.m.get("text");
        if (text != null && !(text instanceof Snbt.Str s && s.v.isEmpty())) return null;
        for (String k : o.m.keySet()) {
            if (!k.equals("text") && !k.equals("extra") && !k.equals("italic")) return null;
        }
        List<Integer> px = new ArrayList<>();
        for (Snbt.Node item : a.items) {
            if (!(item instanceof Snbt.Obj e)) return null;
            if (!(e.m.get("text") instanceof Snbt.Str t)) return null;
            int[] cps = t.v.codePoints().toArray();
            if (cps.length == 0) return null;
            for (int cp : cps) {
                if (cp != 0x2588) return null;
            }
            if (!(e.m.get("color") instanceof Snbt.Str c)) return null;
            Integer rgb = Colors.parseColor(c.v);
            if (rgb == null) return null;
            for (String k : e.m.keySet()) {
                if (!k.equals("text") && !k.equals("color")) return null;
            }
            for (int i = 0; i < cps.length; i++) px.add(rgb);
        }
        if (px.isEmpty() || px.size() > 64) return null;
        int[] row = new int[px.size()];
        for (int i = 0; i < row.length; i++) row[i] = px.get(i);
        return row;
    }

    /** Notes one bold/underline/strikethrough value; mixed values count as advanced. */
    private static void observeFlag(Text t, Snbt.Obj o, String key, int which) {
        if (!(o.m.get(key) instanceof Snbt.Num n)) return;
        boolean v = n.asDouble() != 0d;
        int cur = which == 0 ? t.seenBold : which == 1 ? t.seenUnder : t.seenStrike;
        if (cur == -1) {
            if (which == 0) t.seenBold = v ? 1 : 0;
            else if (which == 1) t.seenUnder = v ? 1 : 0;
            else t.seenStrike = v ? 1 : 0;
        } else if ((cur == 1) != v) {
            t.advanced = true;
        }
    }

    // ---- more tab -----------------------------------------------------------------------

    private static void readPotion(ItemModel m, Snbt.Node v, List<String> warn) {        if (!(v instanceof Snbt.Obj o)) {
            m.extras.put("minecraft:potion_contents", Snbt.compact(v));
            return;
        }
        if (o.m.get("potion") instanceof Snbt.Str s) m.potion = stripMc(s.v);
        if (o.m.get("custom_color") instanceof Snbt.Num n) {
            m.potionColor = String.format("#%06x", ((int) Math.round(n.asDouble())) & 0xFFFFFF);
        }
        if (o.m.get("custom_effects") instanceof Snbt.Arr a) {
            for (Snbt.Node item : a.items) {
                if (!(item instanceof Snbt.Obj e) || !(e.m.get("id") instanceof Snbt.Str id)) continue;
                ItemModel.CustomEffect ce = new ItemModel.CustomEffect();
                ce.id = stripMc(id.v);
                if (e.m.get("amplifier") instanceof Snbt.Num n) {
                    ce.amp = String.valueOf((int) Math.round(n.asDouble()));
                }
                if (e.m.get("duration") instanceof Snbt.Num n) {
                    ce.dur = Components.fmtStep(n.asDouble() / 20, 0.5);
                }
                m.effects.add(ce);
            }
        }
        keepWhole(m.extras, "minecraft:potion_contents", o, warn,
                "The potion holds extra data, kept with it.", "potion", "custom_effects");
    }

    private static void readBanner(ItemModel m, Snbt.Node v, List<String> warn) {
        if (!(v instanceof Snbt.Arr a)) {
            m.extras.put("minecraft:banner_patterns", Snbt.compact(v));
            return;
        }
        for (Snbt.Node item : a.items) {
            if (!(item instanceof Snbt.Obj e) || !(e.m.get("pattern") instanceof Snbt.Str)) {
                m.extras.put("minecraft:banner_patterns", Snbt.compact(v));
                warn.add("Some banner layers were kept as-is.");
                return;
            }
            ItemModel.BannerPat p = new ItemModel.BannerPat();
            p.pattern = stripMc(str(e.m.get("pattern")));
            p.color = str(e.m.get("color"));
            if (p.pattern.isEmpty()) continue;
            m.bannerPats.add(p);
        }
    }

    private static void readTrim(ItemModel m, Snbt.Node v, List<String> warn) {
        if (!(v instanceof Snbt.Obj o)) {
            m.extras.put("minecraft:trim", Snbt.compact(v));
            return;
        }
        if (o.m.get("material") instanceof Snbt.Str s) m.trimMaterial = stripMc(s.v);
        if (o.m.get("pattern") instanceof Snbt.Str s) m.trimPattern = stripMc(s.v);
        keepWhole(m.extras, "minecraft:trim", o, warn,
                "The trim holds extra data, kept with it.", "material", "pattern");
    }

    private static void readDye(ItemModel m, Snbt.Node v, List<String> warn) {
        if (v instanceof Snbt.Obj o && o.m.get("rgb") instanceof Snbt.Num n) {
            m.dye = String.format("#%06x", ((int) Math.round(n.asDouble())) & 0xFFFFFF);
            keepWhole(m.extras, "minecraft:dyed_color", o, warn,
                    "The dye holds extra data, kept with it.", "rgb");
        } else {
            m.extras.put("minecraft:dyed_color", Snbt.compact(v));
        }
    }

    private static void readBees(ItemModel m, Snbt.Node v, List<String> warn) {
        if (!(v instanceof Snbt.Arr a)) {
            m.extras.put("minecraft:bees", Snbt.compact(v));
            return;
        }
        boolean plain = true;
        for (Snbt.Node item : a.items) {
            if (!(item instanceof Snbt.Obj e)) {
                plain = false;
                break;
            }
            for (String k : e.m.keySet()) {
                if (!k.equals("entity_data") && !k.equals("ticks_in_hive") && !k.equals("min_ticks_in_hive")) {
                    plain = false;
                }
            }
            if (!(e.m.get("entity_data") instanceof Snbt.Obj data) || data.m.size() != 1
                    || !(data.m.get("id") instanceof Snbt.Str)) {
                plain = false;
            }
        }
        if (plain) {
            m.beeCount = String.valueOf(a.items.size());
        } else {
            m.beesRaw = Snbt.compact(v);
            warn.add("The beehive holds special bees, kept as they are.");
        }
    }

    private static void readFlight(ItemModel m, Snbt.Node v, List<String> warn) {
        if (v instanceof Snbt.Obj o && o.m.get("flight_duration") instanceof Snbt.Num n) {
            m.flight = String.valueOf((int) Math.round(n.asDouble()));
            keepWhole(m.extras, "minecraft:fireworks", o, warn,
                    "The firework holds extra data, kept with it.", "flight_duration");
        } else {
            m.extras.put("minecraft:fireworks", Snbt.compact(v));
        }
    }

    private static void readLoot(ItemModel m, Snbt.Node v, List<String> warn) {
        if (v instanceof Snbt.Obj o && o.m.get("loot_table") instanceof Snbt.Str s) {
            m.loot = stripMc(s.v);
            keepWhole(m.extras, "minecraft:container_loot", o, warn,
                    "The loot table holds extra data, kept with it.", "loot_table");
        } else {
            m.extras.put("minecraft:container_loot", Snbt.compact(v));
        }
    }

    private static void readSherds(ItemModel m, Snbt.Node v, List<String> warn) {
        if (v instanceof Snbt.Arr a && a.items.size() == 4) {
            List<String> ids = new ArrayList<>();
            for (Snbt.Node item : a.items) {
                if (!(item instanceof Snbt.Str s)) {
                    m.extras.put("minecraft:pot_decorations", Snbt.compact(v));
                    return;
                }
                ids.add(stripMc(s.v));
            }
            m.sherds = String.join(",", ids);
        } else {
            m.extras.put("minecraft:pot_decorations", Snbt.compact(v));
            warn.add("The pot decorations were kept as-is.");
        }
    }

    private static void readProfile(ItemModel m, Snbt.Node v, List<String> warn) {
        if (v instanceof Snbt.Obj o && o.m.get("name") instanceof Snbt.Str s) {
            m.headName = s.v;
            keepWhole(m.extras, "minecraft:profile", o, warn,
                    "The head holds extra data, kept with it.", "name");
        } else {
            m.extras.put("minecraft:profile", Snbt.compact(v));
        }
    }

    private static void readSong(ItemModel m, Snbt.Node v, List<String> warn) {
        if (v instanceof Snbt.Obj o && o.m.get("song") instanceof Snbt.Str s) {
            m.song = stripMc(s.v);
            keepWhole(m.extras, "minecraft:jukebox_playable", o, warn,
                    "The song holds extra data, kept with it.", "song");
        } else if (v instanceof Snbt.Str s) {
            m.song = stripMc(s.v);
        } else {
            m.extras.put("minecraft:jukebox_playable", Snbt.compact(v));
        }
    }

    // ---- other components ---------------------------------------------------------------

    private static void readEnchants(ItemModel m, Snbt.Node v) {
        if (!(v instanceof Snbt.Obj)) return;
        Snbt.Obj o = (Snbt.Obj) v;
        if (o.m.get("levels") instanceof Snbt.Obj) o = (Snbt.Obj) o.m.get("levels");
        for (Map.Entry<String, Snbt.Node> e : o.m.entrySet()) {
            if (!(e.getValue() instanceof Snbt.Num)) continue;
            ItemModel.Ench en = new ItemModel.Ench();
            en.id = stripMc(e.getKey());
            en.level = String.valueOf((int) Math.round(((Snbt.Num) e.getValue()).asDouble()));
            m.enchants.add(en);
        }
    }

    private static void readAttrs(ItemModel m, Snbt.Node v, List<String> warn) {
        Snbt.Node list = v;
        if (v instanceof Snbt.Obj && ((Snbt.Obj) v).m.get("modifiers") != null) list = ((Snbt.Obj) v).m.get("modifiers");
        if (!(list instanceof Snbt.Arr)) return;
        for (Snbt.Node item : ((Snbt.Arr) list).items) {
            if (!(item instanceof Snbt.Obj)) continue;
            Snbt.Obj a = (Snbt.Obj) item;
            ItemModel.Attr at = new ItemModel.Attr();
            String id = stripMc(str(a.m.get("type")));
            for (String prefix : new String[] {"generic.", "player."}) {
                if (id.startsWith(prefix)) id = id.substring(prefix.length());
            }
            at.id = id;
            at.amount = plain(num(a.m.get("amount"), 0));
            String slot = a.m.get("slot") == null ? "any" : str(a.m.get("slot"));
            at.slot = contains(ItemSpec.SLOT_KEYS, slot) ? slot : "any";
            String op = str(a.m.get("operation"));
            at.op = switch (op) {
                case "add_multiplied_base" -> "multiply_base";
                case "add_multiplied_total" -> "multiply_total";
                default -> "add";
            };
            if (!op.isEmpty() && !op.equals("add_value") && !op.startsWith("add_multiplied")) {
                warn.add("An attribute used an unknown operation and was set to Add.");
            }
            m.attrs.add(at);
        }
    }

    private static void readContainer(ItemModel m, Snbt.Node v, List<String> warn) {
        List<String> dsl = new ArrayList<>();
        boolean ok = v instanceof Snbt.Arr;
        if (ok) {
            for (Snbt.Node entry : ((Snbt.Arr) v).items) {
                String line = childToText(entry);
                if (line == null) {
                    ok = false;
                    break;
                }
                dsl.add(line);
            }
        }
        if (ok) {
            m.contents = String.join("; ", dsl);
        } else {
            m.rawContainer = Snbt.compact(v);
            warn.add("The contents hold extra data, so they are kept as they are.");
        }
    }

    /** Writes one container entry in the contents field format, or null if the format cannot hold it. */
    private static String childToText(Snbt.Node entry) {
        if (!(entry instanceof Snbt.Obj)) return null;
        Snbt.Obj e = (Snbt.Obj) entry;
        if (!(e.m.get("item") instanceof Snbt.Obj) || !(e.m.get("slot") instanceof Snbt.Num)) return null;
        Snbt.Obj item = (Snbt.Obj) e.m.get("item");
        int slot = (int) Math.round(((Snbt.Num) e.m.get("slot")).asDouble());
        int count = Math.max(1, (int) Math.round(num(item.m.get("count"), 1)));
        StringBuilder sb = new StringBuilder(stripMc(str(item.m.get("id"))));
        if (count > 1) sb.append(" x").append(count);
        sb.append(" @").append(slot);

        Snbt.Node comps = item.m.get("components");
        if (comps instanceof Snbt.Obj) {
            for (Map.Entry<String, Snbt.Node> c : ((Snbt.Obj) comps).m.entrySet()) {
                String key = stripMc(c.getKey());
                Snbt.Node val = c.getValue();
                switch (key) {
                    case "custom_name" -> {
                        Text t = readText(val);
                        if (t.advanced || t.mode != ItemModel.ColorStyle.DEFAULT || t.text.contains("\"") || t.text.contains(";")) return null;
                        sb.append(" name=\"").append(t.text).append('"');
                    }
                    case "enchantments" -> {
                        if (!(val instanceof Snbt.Obj)) return null;
                        Snbt.Obj eo = (Snbt.Obj) val;
                        if (eo.m.get("levels") instanceof Snbt.Obj) eo = (Snbt.Obj) eo.m.get("levels");
                        for (Map.Entry<String, Snbt.Node> en : eo.m.entrySet()) {
                            if (!(en.getValue() instanceof Snbt.Num)) return null;
                            sb.append(' ').append(stripMc(en.getKey())).append('=')
                                    .append((int) Math.round(((Snbt.Num) en.getValue()).asDouble()));
                        }
                    }
                    case "unbreakable" -> sb.append(" unbreakable");
                    default -> {
                        return null;
                    }
                }
            }
        }
        return sb.toString();
    }

    // ---- helpers ------------------------------------------------------------------------

    private static String intText(Snbt.Node n) {
        return n instanceof Snbt.Num num ? String.valueOf((int) Math.round(num.asDouble())) : "";
    }

    private static String boolText(Snbt.Node n) {
        if (n instanceof Snbt.Num num) return num.asDouble() != 0d ? "true" : "false";
        if (n instanceof Snbt.Str s && (s.v.equals("true") || s.v.equals("false"))) return s.v;
        return "";
    }

    private static void readFood(ItemModel m, Snbt.Node v, List<String> warn) {
        if (!(v instanceof Snbt.Obj o)) {
            m.extras.put("minecraft:food", Snbt.compact(v));
            return;
        }
        m.nutrition = o.m.get("nutrition") instanceof Snbt.Num n
                ? String.valueOf((int) Math.round(n.asDouble())) : "";
        m.saturation = o.m.get("saturation") instanceof Snbt.Num s ? plain(s.asDouble()) : "";
        m.alwaysEat = o.m.get("can_always_eat") instanceof Snbt.Num b && b.asDouble() != 0d;
        keepWhole(m.extras, "minecraft:food", o, warn,
                "The food has extra data, kept with it.", "nutrition", "saturation", "can_always_eat");
    }

    private static void readConsumable(ItemModel m, Snbt.Node v, List<String> warn) {
        if (!(v instanceof Snbt.Obj o)) {
            m.extras.put("minecraft:consumable", Snbt.compact(v));
            return;
        }
        if (o.m.get("consume_seconds") instanceof Snbt.Num n) m.eatSeconds = plain(n.asDouble());
        if (o.m.get("animation") instanceof Snbt.Str s) m.eatAnimation = s.v;
        if (o.m.get("sound") instanceof Snbt.Str s) {
            m.eatSound = s.v;
        } else if (o.m.get("sound") != null) {
            m.eatSound = Snbt.compact(o.m.get("sound"));
        }
        keepWhole(m.extras, "minecraft:consumable", o, warn,
                "The consumable settings hold extra data, kept with them.",
                "consume_seconds", "animation", "sound");
    }

    private static void readEquippable(ItemModel m, Snbt.Node v, List<String> warn) {
        if (!(v instanceof Snbt.Obj o)) {
            m.extras.put("minecraft:equippable", Snbt.compact(v));
            return;
        }
        if (o.m.get("slot") instanceof Snbt.Str s) m.equipSlot = s.v;
        if (o.m.get("equip_sound") instanceof Snbt.Str s) {
            m.equipSound = s.v;
        } else if (o.m.get("equip_sound") != null) {
            m.equipSound = Snbt.compact(o.m.get("equip_sound"));
        }
        keepWhole(m.extras, "minecraft:equippable", o, warn,
                "The equipable settings hold extra data, kept with them.", "slot", "equip_sound");
    }

    private static void readCooldown(ItemModel m, Snbt.Node v, List<String> warn) {
        if (v instanceof Snbt.Obj o && o.m.get("seconds") instanceof Snbt.Num n) {
            m.useCooldown = plain(n.asDouble());
            keepWhole(m.extras, "minecraft:use_cooldown", o, warn,
                    "The use cooldown holds extra data, kept with it.", "seconds");
        } else {
            m.extras.put("minecraft:use_cooldown", Snbt.compact(v));
            warn.add("The use cooldown was kept as-is.");
        }
    }

    private static void readRemainder(ItemModel m, Snbt.Node v, List<String> warn) {
        if (v instanceof Snbt.Obj o && o.m.get("id") instanceof Snbt.Str s) {
            m.useRemainder = stripMc(s.v);
            keepWhole(m.extras, "minecraft:use_remainder", o, warn,
                    "The use remainder holds extra data, kept with it.", "id", "count");
        } else {
            m.extras.put("minecraft:use_remainder", Snbt.compact(v));
        }
    }

    /**
     * When a component holds keys the builder has no field for, the whole
     * original value is kept in extras. The builder merges those keys back
     * when writing, so nothing is lost.
     */
    private static void keepWhole(Map<String, String> extras, String key, Snbt.Obj o,
            List<String> warn, String message, String... known) {
        for (String k : o.m.keySet()) {
            boolean knownKey = false;
            for (String knownName : known) {
                if (knownName.equals(k)) knownKey = true;
            }
            if (!knownKey) {
                extras.put(key, Snbt.compact(o));
                warn.add(message);
                return;
            }
        }
    }

    /**
     * Splits spawner numbers and a plain spawn entity into the quick fields.
     * Anything else stays in the block data box so nothing is lost.
     */
    private static void readBlockEntity(ItemModel m, Snbt.Node v, List<String> warn) {
        if (!(v instanceof Snbt.Obj o)) {
            m.extras.put("minecraft:block_entity_data", Snbt.compact(v));
            return;
        }
        Map<String, Snbt.Node> rest = new LinkedHashMap<>(o.m);
        String id = rest.get("id") instanceof Snbt.Str s ? s.v : "";
        boolean spawner = id.equals("mob_spawner") || id.equals("minecraft:mob_spawner");
        if (spawner) {
            rest.remove("id");
            takeInt(rest, "SpawnDelay", val -> m.spawnDelay = val);
            takeInt(rest, "MinSpawnDelay", val -> m.minDelay = val);
            takeInt(rest, "MaxSpawnDelay", val -> m.maxDelay = val);
            takeInt(rest, "SpawnCount", val -> m.spawnCount = val);
            takeInt(rest, "SpawnRange", val -> m.spawnRange = val);
            takeInt(rest, "RequiredPlayerRange", val -> m.requiredPlayers = val);
            takeInt(rest, "MaxNearbyEntities", val -> m.maxNearby = val);
            Snbt.Node spawnData = rest.get("SpawnData");
            if (spawnData instanceof Snbt.Obj sd && sd.m.size() == 1
                    && sd.m.get("entity") instanceof Snbt.Obj en
                    && en.m.get("id") instanceof Snbt.Str eid) {
                m.spawnerEntity = stripMc(eid.v);
                Map<String, Snbt.Node> extra = new LinkedHashMap<>(en.m);
                extra.remove("id");
                if (!extra.isEmpty()) {
                    Snbt.Obj left = new Snbt.Obj();
                    left.m.putAll(extra);
                    m.entityData = Snbt.compact(left);
                }
                rest.remove("SpawnData");
            } else if (spawnData != null) {
                warn.add("The spawner has extra spawn data, kept in the block data box.");
            }
            if (!rest.isEmpty()) {
                Snbt.Obj left = new Snbt.Obj();
                left.m.putAll(rest);
                m.blockEntityRaw = Snbt.compact(left);
                warn.add("Extra block data is kept in the block data box.");
            }
        } else {
            m.blockEntityRaw = Snbt.compact(v);
            if (!id.isEmpty()) warn.add("Block data for " + stripMc(id) + " is kept in the block data box.");
            else warn.add("Block data is kept in the block data box.");
        }
    }

    private interface StringSink {
        void put(String value);
    }

    private static void takeInt(Map<String, Snbt.Node> rest, String key, StringSink sink) {
        if (rest.get(key) instanceof Snbt.Num n) {
            sink.put(String.valueOf((int) Math.round(n.asDouble())));
            rest.remove(key);
        }
    }

    private static boolean contains(String[] arr, String v) {
        for (String s : arr) if (s.equals(v)) return true;
        return false;
    }

    private static String stripMc(String s) {
        return s.startsWith("minecraft:") ? s.substring(10) : s;
    }

    private static String str(Snbt.Node n) {
        return n instanceof Snbt.Str ? ((Snbt.Str) n).v : "";
    }

    private static double num(Snbt.Node n, double fallback) {
        return n instanceof Snbt.Num ? ((Snbt.Num) n).asDouble() : fallback;
    }

    private static String plain(double d) {
        return new java.math.BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
    }
}
