package dev.nbtitembuilder;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Color math for the wheel and gradients. RGB values are plain 0xRRGGBB ints. */
public final class Colors {
    private Colors() {}

    public static final Map<String, Integer> NAMED = new LinkedHashMap<>();
    static {
        NAMED.put("black", 0x000000);
        NAMED.put("dark_blue", 0x0000AA);
        NAMED.put("dark_green", 0x00AA00);
        NAMED.put("dark_aqua", 0x00AAAA);
        NAMED.put("dark_red", 0xAA0000);
        NAMED.put("dark_purple", 0xAA00AA);
        NAMED.put("gold", 0xFFAA00);
        NAMED.put("gray", 0xAAAAAA);
        NAMED.put("dark_gray", 0x555555);
        NAMED.put("blue", 0x5555FF);
        NAMED.put("green", 0x55FF55);
        NAMED.put("aqua", 0x55FFFF);
        NAMED.put("red", 0xFF5555);
        NAMED.put("light_purple", 0xFF55FF);
        NAMED.put("yellow", 0xFFFF55);
        NAMED.put("white", 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** h, s and v are all 0 to 1. Hue wraps around. */
    public static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = clamp01(s);
        v = clamp01(v);
        float c = v * s;
        float hp = h * 6f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float m = v - c;
        float r;
        float g;
        float b;
        switch ((int) Math.floor(hp) % 6) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return (Math.round((r + m) * 255f) << 16) | (Math.round((g + m) * 255f) << 8) | Math.round((b + m) * 255f);
    }

    /** Returns {h, s, v}, each 0 to 1. */
    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0f) h += 1f;
        float s = max == 0f ? 0f : d / max;
        return new float[] {h, s, max};
    }

    public static int lerp(int a, int b, float t) {
        t = clamp01(t);
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static String hex(int rgb) {
        return String.format("#%06x", rgb & 0xFFFFFF);
    }

    /** Accepts rrggbb or #rrggbb. Returns null when the text is not a color. */
    public static Integer parseHex(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return null;
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A #rrggbb value or one of the 16 named colors. Returns null otherwise. */
    public static Integer parseColor(String s) {
        if (s == null) return null;
        Integer named = NAMED.get(s.trim().toLowerCase(Locale.ROOT));
        return named != null ? named : (s.trim().startsWith("#") ? parseHex(s) : null);
    }
}
