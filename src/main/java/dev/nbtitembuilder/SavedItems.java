package dev.nbtitembuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** The list of saved items, kept in one text file. No Minecraft classes. */
public final class SavedItems {
    public static final class Entry {
        public String name;
        /** The item as SNBT: {id:..., count:..., components:{...}}. */
        public String nbt;

        public Entry(String name, String nbt) {
            this.name = name;
            this.nbt = nbt;
        }
    }

    private static SavedItems shared;
    private static Path sharedFile;

    private final Path file;
    private final List<Entry> entries = new ArrayList<>();
    /** Set when the file could not be read or written, for the screen to show. */
    private String problem;

    public SavedItems(Path file) {
        this.file = file;
    }

    /** Called once at startup with the file to use. */
    public static void setFile(Path file) {
        sharedFile = file;
        shared = null;
    }

    /** The list used by the screens. Loaded on first use. */
    public static SavedItems get() {
        if (shared == null) {
            shared = new SavedItems(sharedFile);
            shared.read();
        }
        return shared;
    }

    public List<Entry> list() {
        return entries;
    }

    public String problem() {
        return problem;
    }

    // ---- changing the list --------------------------------------------------------------

    /** Adds an item. A name that is empty or already used gets adjusted. Returns the name used. */
    public String add(String name, String nbt) {
        String n = unique(clean(name, "Item"), -1);
        entries.add(new Entry(n, nbt));
        write();
        return n;
    }

    public void remove(int index) {
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            write();
        }
    }

    public String rename(int index, String name) {
        if (index < 0 || index >= entries.size()) return "";
        String n = unique(clean(name, entries.get(index).name), index);
        entries.get(index).name = n;
        write();
        return n;
    }

    /** Puts the entry one step earlier (-1) or later (+1) in the list. */
    public void move(int index, int delta) {
        int to = index + delta;
        if (index < 0 || index >= entries.size() || to < 0 || to >= entries.size()) return;
        Entry e = entries.remove(index);
        entries.add(to, e);
        write();
    }

    private static String clean(String name, String fallback) {
        String n = name == null ? "" : name.replace('\n', ' ').trim();
        if (n.length() > 40) n = n.substring(0, 40).trim();
        return n.isEmpty() ? fallback : n;
    }

    private String unique(String base, int ignoreIndex) {
        String candidate = base;
        int n = 2;
        while (taken(candidate, ignoreIndex)) {
            candidate = base + " (" + n + ")";
            n++;
        }
        return candidate;
    }

    private boolean taken(String name, int ignoreIndex) {
        for (int i = 0; i < entries.size(); i++) {
            if (i != ignoreIndex && entries.get(i).name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    // ---- file ---------------------------------------------------------------------------

    private void read() {
        entries.clear();
        problem = null;
        if (file == null || !Files.exists(file)) return;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Snbt.Node root = Snbt.parse(text);
            if (root instanceof Snbt.Obj o && o.m.get("items") instanceof Snbt.Arr a) {
                for (Snbt.Node n : a.items) {
                    if (n instanceof Snbt.Obj e && e.m.get("name") instanceof Snbt.Str name
                            && e.m.get("nbt") instanceof Snbt.Str nbt) {
                        entries.add(new Entry(name.v, nbt.v));
                    }
                }
            } else {
                throw new IllegalArgumentException("Not a saved items file");
            }
        } catch (IOException | RuntimeException e) {
            // Keep the unreadable file as a backup instead of overwriting it.
            entries.clear();
            problem = "The saved items file could not be read. It was kept as a .bak copy.";
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Nothing more to do.
            }
        }
    }

    private void write() {
        if (file == null) return;
        Snbt.Obj root = new Snbt.Obj();
        root.m.put("version", new Snbt.Num("1"));
        Snbt.Arr arr = new Snbt.Arr("");
        for (Entry e : entries) {
            Snbt.Obj o = new Snbt.Obj();
            o.m.put("name", new Snbt.Str(e.name));
            o.m.put("nbt", new Snbt.Str(e.nbt));
            arr.items.add(o);
        }
        root.m.put("items", arr);
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, Snbt.compact(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            problem = null;
        } catch (IOException e) {
            problem = "Could not save the list: " + e.getMessage();
        }
    }

    // ---- helpers ------------------------------------------------------------------------

    /** A short description such as "diamond_sword x1", for the list. */
    public static String describe(String nbt) {
        try {
            if (Snbt.parse(nbt) instanceof Snbt.Obj o) {
                String id = o.m.get("id") instanceof Snbt.Str s ? s.v : "?";
                if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
                String count = o.m.get("count") instanceof Snbt.Num n ? n.raw.replaceAll("[bBsSlL]$", "") : "1";
                return id + " x" + count;
            }
        } catch (RuntimeException ignored) {
            // Falls through.
        }
        return "unknown item";
    }

    /** The /give command for a saved item, without a leading slash. */
    public static String giveCommand(String nbt) {
        ItemModel m = new ItemModel();
        ItemLoader.load(m, nbt);
        return ItemSpec.giveCommand(m, "@s");
    }

    /** Puts a saved item into the builder's model. Returns notes about anything not understood. */
    public static List<String> intoModel(String nbt, ItemModel m) {
        m.reset();
        return ItemLoader.load(m, nbt);
    }
}
