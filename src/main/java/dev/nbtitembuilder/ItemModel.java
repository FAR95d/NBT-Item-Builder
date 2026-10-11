package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the builder screen edits. Plain data, no Minecraft classes. */
public final class ItemModel {
    /** How a piece of text is colored. */
    public static final class ColorStyle {
        public static final int DEFAULT = 0;
        public static final int SOLID = 1;
        public static final int GRADIENT = 2;

        public int mode = DEFAULT;
        public int c1;
        public int c2;

        public ColorStyle(int c1, int c2) {
            this.c1 = c1;
            this.c2 = c2;
        }
    }

    public static final class Ench {
        public String id = "";
        /** Any whole number from 1 to 255. Empty means the vanilla maximum. */
        public String level = "";
    }

    public static final class Attr {
        public String id = "";
        public String amount = "1";
        public String op = "add";
        public String slot = "mainhand";
    }

    public String id = "";
    public String count = "1";
    public boolean unbreakable;
    public boolean noItalic = true;

    public String name = "";
    /** Lore lines separated by |. */
    public String lore = "";
    public ColorStyle nameStyle = new ColorStyle(0xFFAA00, 0x55FFFF);
    public ColorStyle loreStyle = new ColorStyle(0xAAAAAA, 0xFFFFFF);

    public List<Ench> enchants = new ArrayList<>();
    public List<Attr> attrs = new ArrayList<>();

    /** Contents typed as text, see ItemSpec for the format. */
    public String contents = "";
    /** Container data this screen cannot express as text. It is written back unchanged. */
    public String rawContainer;
    /** Components the builder has no fields for, kept as they were loaded: full key to SNBT. */
    public Map<String, String> extras = new LinkedHashMap<>();

    public void reset() {
        id = "";
        count = "1";
        unbreakable = false;
        noItalic = true;
        name = "";
        lore = "";
        nameStyle = new ColorStyle(0xFFAA00, 0x55FFFF);
        loreStyle = new ColorStyle(0xAAAAAA, 0xFFFFFF);
        enchants = new ArrayList<>();
        attrs = new ArrayList<>();
        contents = "";
        rawContainer = null;
        extras = new LinkedHashMap<>();
    }
}
