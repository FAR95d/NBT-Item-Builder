package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A small SNBT reader and writer: parse, compact output, pretty output and syntax spans. */
public final class Snbt {
    private Snbt() {}

    public abstract static class Node {}

    public static final class Str extends Node {
        public final String v;

        public Str(String v) {
            this.v = v;
        }
    }

    /** A number as written, including its type suffix, for example 5, 1b, 14.0d. */
    public static final class Num extends Node {
        public final String raw;

        public Num(String raw) {
            this.raw = raw;
        }

        public double asDouble() {
            String t = raw;
            char last = t.charAt(t.length() - 1);
            if ("bBsSlLfFdD".indexOf(last) >= 0 && t.length() > 1) t = t.substring(0, t.length() - 1);
            return Double.parseDouble(t);
        }

        /** True for the 0b written when a flag is false. */
        public boolean isZero() {
            return asDouble() == 0d;
        }
    }

    public static final class Obj extends Node {
        public final Map<String, Node> m = new LinkedHashMap<>();
    }

    public static final class Arr extends Node {
        /** Empty for a normal list, or I, B or L for a typed array such as [I;1,2]. */
        public final String prefix;
        public final List<Node> items = new ArrayList<>();

        public Arr(String prefix) {
            this.prefix = prefix;
        }
    }

    // ---- reading ------------------------------------------------------------------------

    private static final Pattern NUM = Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?[bBsSlLfFdD]?");

    public static Node parse(String s) {
        Reader r = new Reader(s);
        Node n = r.value();
        r.ws();
        if (r.i != s.length()) throw r.err("Unexpected text");
        return n;
    }

    private static final class Reader {
        final String s;
        int i;

        Reader(String s) {
            this.s = s;
        }

        IllegalArgumentException err(String what) {
            return new IllegalArgumentException(what + " at position " + i + " of the item data.");
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        char peek() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        void expect(char c) {
            if (peek() != c) throw err("Expected '" + c + "'");
            i++;
        }

        Node value() {
            ws();
            char c = peek();
            if (c == '\0') throw err("Unexpected end");
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"' || c == '\'') return new Str(quoted());
            return bare();
        }

        Obj obj() {
            i++;
            Obj o = new Obj();
            ws();
            if (peek() == '}') {
                i++;
                return o;
            }
            while (true) {
                ws();
                String k = peek() == '"' || peek() == '\'' ? quoted() : token();
                if (k.isEmpty()) throw err("Expected a key");
                ws();
                expect(':');
                o.m.put(k, value());
                ws();
                if (peek() == ',') {
                    i++;
                    continue;
                }
                expect('}');
                return o;
            }
        }

        Arr arr() {
            i++;
            String prefix = "";
            if (i + 1 < s.length() && "BIL".indexOf(s.charAt(i)) >= 0 && s.charAt(i + 1) == ';') {
                prefix = String.valueOf(s.charAt(i));
                i += 2;
            }
            Arr a = new Arr(prefix);
            ws();
            if (peek() == ']') {
                i++;
                return a;
            }
            while (true) {
                a.items.add(value());
                ws();
                if (peek() == ',') {
                    i++;
                    continue;
                }
                expect(']');
                return a;
            }
        }

        String token() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '+' || c == '.') i++;
                else break;
            }
            return s.substring(start, i);
        }

        Node bare() {
            String t = token();
            if (t.isEmpty()) throw err("Unexpected character '" + peek() + "'");
            if (t.equals("true")) return new Num("1b");
            if (t.equals("false")) return new Num("0b");
            if (NUM.matcher(t).matches()) return new Num(t);
            return new Str(t);
        }

        String quoted() {
            char q = s.charAt(i++);
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw err("Unclosed string");
                char c = s.charAt(i++);
                if (c == q) return b.toString();
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                if (i >= s.length()) throw err("Unclosed string");
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 't' -> b.append('\t');
                    case 'r' -> b.append('\r');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw err("Bad escape");
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            }
        }
    }

    // ---- writing ------------------------------------------------------------------------

    private static final Pattern KEY_OK = Pattern.compile("[A-Za-z0-9_.+-]+");

    public static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String key(String k) {
        return KEY_OK.matcher(k).matches() ? k : quote(k);
    }

    public static String compact(Node n) {
        StringBuilder sb = new StringBuilder();
        write(n, sb);
        return sb.toString();
    }

    private static void write(Node n, StringBuilder sb) {
        if (n instanceof Str s) {
            sb.append(quote(s.v));
        } else if (n instanceof Num num) {
            sb.append(num.raw);
        } else if (n instanceof Obj o) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Node> e : o.m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(key(e.getKey())).append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (n instanceof Arr a) {
            sb.append('[');
            if (!a.prefix.isEmpty()) sb.append(a.prefix).append(';');
            for (int i = 0; i < a.items.size(); i++) {
                if (i > 0) sb.append(',');
                write(a.items.get(i), sb);
            }
            sb.append(']');
        }
    }

    /** Indented output. Short containers that only hold plain values stay on one line. */
    public static String pretty(Node n) {
        return pretty(n, 0);
    }

    private static String pretty(Node n, int ind) {
        boolean isObj = n instanceof Obj;
        if (!isObj && !(n instanceof Arr)) return compact(n);
        if (!isObj && !((Arr) n).prefix.isEmpty()) return compact(n);
        List<String> keys = new ArrayList<>();
        List<Node> vals = new ArrayList<>();
        if (isObj) {
            for (Map.Entry<String, Node> e : ((Obj) n).m.entrySet()) {
                keys.add(e.getKey());
                vals.add(e.getValue());
            }
        } else {
            for (Node v : ((Arr) n).items) {
                keys.add(null);
                vals.add(v);
            }
        }
        String open = isObj ? "{" : "[";
        String close = isObj ? "}" : "]";
        if (vals.isEmpty()) return open + close;

        boolean plain = true;
        for (Node v : vals) {
            if (!(v instanceof Str) && !(v instanceof Num)) plain = false;
        }
        if (plain) {
            StringBuilder flat = new StringBuilder(open);
            for (int i = 0; i < vals.size(); i++) {
                if (i > 0) flat.append(", ");
                if (keys.get(i) != null) flat.append(key(keys.get(i))).append(": ");
                flat.append(compact(vals.get(i)));
            }
            flat.append(close);
            if (flat.length() <= 72) return flat.toString();
        }

        String pad = "  ".repeat(ind + 1);
        StringBuilder sb = new StringBuilder(open).append('\n');
        for (int i = 0; i < vals.size(); i++) {
            sb.append(pad);
            if (keys.get(i) != null) sb.append(key(keys.get(i))).append(": ");
            sb.append(pretty(vals.get(i), ind + 1));
            if (i < vals.size() - 1) sb.append(',');
            sb.append('\n');
        }
        return sb.append("  ".repeat(ind)).append(close).toString();
    }

    // ---- syntax coloring ----------------------------------------------------------------

    public static final int PLAIN = 0;
    public static final int KEY = 1;
    public static final int STRING = 2;
    public static final int NUMBER = 3;

    public static final class Span {
        public final String text;
        public final int kind;

        public Span(String text, int kind) {
            this.text = text;
            this.kind = kind;
        }
    }

    private static final Pattern TOKEN = Pattern.compile(
            "(\"(?:\\\\.|[^\"\\\\])*\")(?=\\s*:)"
            + "|(\"(?:\\\\.|[^\"\\\\])*\")"
            + "|([A-Za-z_][A-Za-z0-9_]*)(?=\\s*[:=])"
            + "|((?<![A-Za-z0-9_])-?\\d+(?:\\.\\d+)?[bsLfd]?(?![A-Za-z0-9_]))");

    /** Splits text into colored pieces. Put the whole text in, the pieces join back to it exactly. */
    public static List<Span> spans(String text) {
        List<Span> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.add(new Span(text.substring(last, m.start()), PLAIN));
            int kind = m.group(1) != null ? KEY : m.group(2) != null ? STRING : m.group(3) != null ? KEY : NUMBER;
            out.add(new Span(m.group(), kind));
            last = m.end();
        }
        if (last < text.length()) out.add(new Span(text.substring(last), PLAIN));
        return out;
    }

    /** Like spans, but leaves "give @s minecraft:item" uncolored. */
    public static List<Span> commandSpans(String command) {
        int i = indexOfOpen(command);
        if (i < 0) {
            List<Span> out = new ArrayList<>();
            out.add(new Span(command, PLAIN));
            return out;
        }
        List<Span> out = new ArrayList<>();
        out.add(new Span(command.substring(0, i), PLAIN));
        out.addAll(spans(command.substring(i)));
        return out;
    }

    private static int indexOfOpen(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '{') return i;
        }
        return -1;
    }
}
