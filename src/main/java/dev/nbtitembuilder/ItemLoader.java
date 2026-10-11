package dev.nbtitembuilder;

import java.util.ArrayList;
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

        boolean haveText = false;
        boolean italicOff = false;
        for (Map.Entry<String, Snbt.Node> e : ((Snbt.Obj) cn).m.entrySet()) {
            String full = e.getKey().contains(":") ? e.getKey() : "minecraft:" + e.getKey();
            Snbt.Node v = e.getValue();
            switch (full) {
                case "minecraft:custom_name" -> {
                    Text t = readText(v);
                    m.name = t.text;
                    apply(m.nameStyle, t);
                    haveText = true;
                    italicOff |= t.italicOff;
                    if (t.advanced) warn.add("The name formatting was simplified.");
                }
                case "minecraft:lore" -> {
                    List<String> lines = new ArrayList<>();
                    Text first = null;
                    if (v instanceof Snbt.Arr) {
                        for (Snbt.Node line : ((Snbt.Arr) v).items) {
                            Text t = readText(line);
                            if (first == null) first = t;
                            lines.add(t.text);
                            italicOff |= t.italicOff;
                            if (t.advanced) warn.add("Some lore formatting was simplified.");
                        }
                    }
                    m.lore = String.join(" | ", lines);
                    if (first != null) apply(m.loreStyle, first);
                    haveText = true;
                }
                case "minecraft:enchantments" -> readEnchants(m, v);
                case "minecraft:attribute_modifiers" -> readAttrs(m, v, warn);
                case "minecraft:unbreakable" -> m.unbreakable = true;
                case "minecraft:container" -> readContainer(m, v, warn);
                default -> m.extras.put(full, Snbt.compact(v));
            }
        }
        if (haveText) m.noItalic = italicOff;
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
        boolean italicOff;
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
    }

    private static Text readText(Snbt.Node n) {
        Text t = new Text();
        List<Seg> segs = new ArrayList<>();
        flatten(n, null, segs, t, true);

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
            if (top && italic instanceof Snbt.Num && ((Snbt.Num) italic).isZero()) t.italicOff = true;
            for (String k : new String[] {"bold", "underlined", "strikethrough", "obfuscated", "font", "shadow_color",
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
