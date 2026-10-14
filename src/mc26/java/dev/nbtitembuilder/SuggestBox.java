package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;

/**
 * A text box with a dropdown: type a few letters and matching entries appear
 * below with icons and inventory names, in list order. Arrow keys + Enter or
 * a click picks one; Escape closes. Long lists scroll. The box keeps the
 * command-form id; the dropdown is only the translation layer.
 */
public class SuggestBox {
    /** One pickable entry: command id, what kind of icon/name it gets. */
    public static final class Entry {
        public final String id;
        /** item, mob, potion, effect, banner, template, disc or text. */
        public final String kind;
        /** Full icon SNBT, or null to derive it from the kind. */
        public final String icon;

        public Entry(String id, String kind) {
            this(id, kind, null);
        }

        public Entry(String id, String kind, String icon) {
            this.id = id;
            this.kind = kind;
            this.icon = icon;
        }
    }

    private static final int ROW_H = 18;
    private static final int MAX_ROWS = 6;
    // GLFW key codes, kept local so no extra dependency leaks in here.
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_ENTER = 257;
    private static final int KEY_TAB = 258;
    private static final int KEY_UP = 265;
    private static final int KEY_DOWN = 264;

    /** Decoded icons, shared by every box. Failures cache as empty. */
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private final ClipBox box;
    private final List<Entry> all;
    private final Consumer<String> onCommit;
    private final List<Entry> shown = new ArrayList<>();
    private boolean open;
    private int hover = -1;
    private int scrollPx;
    private int dropX;
    private int dropY;
    private int dropW;
    private int dropRows;

    public SuggestBox(Font font, int x, int y, int w, String hint, String initial,
            List<Entry> all, Consumer<String> onCommit) {
        this.all = all;
        this.onCommit = onCommit;
        this.box = new ClipBox(font, x, y, w, hint);
        this.box.setMaxLength(200);
        this.box.setTextColor(0xFFFFFFFF);
        this.box.setValue(initial == null ? "" : initial);
        this.box.setResponder(v -> {
            onCommit.accept(v);
            filter();
            if (this.box.isFocused()) open = true;
        });
        filter();
    }

    public ClipBox box() {
        return box;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    /** Opens the full list, e.g. when the box is clicked. */
    public void open() {
        filter();
        open = true;
    }

    /** Refilter for the current text, keeping list order. */
    private void filter() {
        shown.clear();
        String q = box.getValue() == null ? "" : box.getValue();
        for (Entry e : all) {
            if (Components.matches(e.id, displayName(e), q)) shown.add(e);
        }
        hover = shown.isEmpty() ? -1 : 0;
        scrollPx = 0;
    }

    /** Inventory name for an entry, falling back to a readable id. */
    public static String displayName(Entry e) {
        String base = e.id.contains(":") ? e.id.substring(e.id.indexOf(':') + 1) : e.id;
        String key = switch (e.kind) {
            case "mob" -> "entity.minecraft." + base;
            case "effect" -> "effect.minecraft." + base;
            case "item" -> "item.minecraft." + base;
            default -> null;
        };
        if (key != null) {
            String t = Language.getInstance().getOrDefault(key);
            if (!t.equals(key)) return t;
        }
        if (e.kind.equals("item")) {
            String blockKey = "block.minecraft." + base;
            String t = Language.getInstance().getOrDefault(blockKey);
            if (!t.equals(blockKey)) return t;
        }
        return Components.humanize(base);
    }

    /** Decodes an icon once and caches it. Null when it cannot be decoded. */
    public static ItemStack icon(String key, String snbt) {
        if (ICONS.containsKey(key)) {
            ItemStack cached = ICONS.get(key);
            return cached.isEmpty() ? null : cached;
        }
        ItemStack stack = Held.preview(snbt);
        ICONS.put(key, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack);
        return stack;
    }

    /** The icon SNBT for an entry, or null for text-only rows. */
    public static String iconSnbt(Entry e) {
        String base = e.id.contains(":") ? e.id.substring(e.id.indexOf(':') + 1) : e.id;
        return switch (e.kind) {
            case "item" -> "{id:\"minecraft:" + base + "\",count:1}";
            case "mob" -> "{id:\"minecraft:" + base + "_spawn_egg\",count:1}";
            case "potion" -> "{id:\"minecraft:potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"minecraft:"
                    + base + "\"}}}";
            case "banner" -> "{id:\"minecraft:white_banner\",count:1,components:{\"minecraft:banner_patterns\":[{pattern:\"minecraft:"
                    + base + "\",color:\"black\"}]}}";
            case "template" -> "{id:\"minecraft:" + base + "_armor_trim_smithing_template\",count:1}";
            case "disc" -> "{id:\"minecraft:music_disc_" + base + "\",count:1}";
            default -> null;
        };
    }

    /** Icon for an entry, cached. Null for text-only or undecodable rows. */
    public static ItemStack iconFor(Entry e) {
        if (e.icon != null) return icon(e.kind + ":" + e.id + "#icon", e.icon);
        String snbt = iconSnbt(e);
        if (snbt == null) return null;
        return icon(e.kind + ":" + e.id, snbt);
    }

    private void commit(Entry e) {
        box.setValue(e.id);
        onCommit.accept(e.id);
        open = false;
    }

    /** Keyboard: arrows move, Enter/Tab pick, Escape closes. True when handled. */
    public boolean keyPressed(int key) {
        if (!open || !box.isFocused() || shown.isEmpty()) return false;
        if (key == KEY_DOWN) { // down
            hover = Math.min(shown.size() - 1, hover + 1);
            reveal();
            return true;
        }
        if (key == KEY_UP) { // up
            hover = Math.max(0, hover - 1);
            reveal();
            return true;
        }
        if (key == KEY_ENTER || key == KEY_TAB) { // enter or tab
            commit(shown.get(Math.max(0, hover)));
            return true;
        }
        if (key == KEY_ESCAPE) { // escape
            open = false;
            return true;
        }
        return false;
    }

    private void reveal() {
        int first = scrollPx / ROW_H;
        int last = (scrollPx + dropRows * ROW_H - 1) / ROW_H;
        if (hover < first) scrollPx = hover * ROW_H;
        else if (hover > last) scrollPx = (hover + 1) * ROW_H - dropRows * ROW_H;
        if (scrollPx < 0) scrollPx = 0;
    }

    /** True when the click landed in the dropdown (and picked something). */
    public boolean mouseClicked(double mx, double my) {
        if (!open) return false;
        if (mx < dropX || mx >= dropX + dropW) return false;
        int row = (int) ((my - dropY - 1) / ROW_H);
        int index = scrollPx / ROW_H + row;
        if (my >= dropY + 1 && index >= 0 && index < shown.size()
                && my < dropY + 1 + dropRows * ROW_H) {
            commit(shown.get(index));
            return true;
        }
        return false;
    }

    public boolean contains(double mx, double my) {
        return open && mx >= dropX && mx < dropX + dropW && my >= dropY && my < dropY + 1 + dropRows * ROW_H;
    }

    /** True when the click is inside the text box itself. */
    public boolean boxContains(double mx, double my) {
        return mx >= box.getX() && mx < box.getX() + box.getWidth()
                && my >= box.getY() && my < box.getY() + box.getHeight();
    }

    /** Mouse wheel scrolls a long list. True when handled. */
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        scrollPx -= (int) Math.signum(amount) * ROW_H * 2;
        int max = Math.max(0, shown.size() * ROW_H - dropRows * ROW_H);
        if (scrollPx > max) scrollPx = max;
        if (scrollPx < 0) scrollPx = 0;
        return true;
    }

    /** Draws the dropdown below (or above, when short on room) the box. */
    public void render(GuiGraphicsExtractor g, Font font, int screenW, int screenH) {
        if (!open || shown.isEmpty()) return;
        dropW = Math.min(Math.max(box.getWidth(), 180), screenW - 16);
        dropW = Math.min(dropW, screenW - box.getX() - 8);
        if (dropW < 120) dropW = Math.min(box.getWidth(), screenW - 16);
        dropX = Math.min(box.getX(), screenW - 8 - dropW);
        dropRows = Math.min(MAX_ROWS, shown.size());
        int h = dropRows * ROW_H + 2;
        dropY = box.getY() + box.getHeight() + 2;
        if (dropY + h > screenH - 8) dropY = box.getY() - 2 - h;
        int max = Math.max(0, shown.size() * ROW_H - dropRows * ROW_H);
        if (scrollPx > max) scrollPx = max;

        g.fill(dropX, dropY, dropX + dropW, dropY + h, 0xFF000000);
        g.fill(dropX, dropY, dropX + dropW, dropY + 1, 0xFFFFFFFF);
        g.fill(dropX, dropY + h - 1, dropX + dropW, dropY + h, 0xFFFFFFFF);
        g.fill(dropX, dropY, dropX + 1, dropY + h, 0xFFFFFFFF);
        g.fill(dropX + dropW - 1, dropY, dropX + dropW, dropY + h, 0xFFFFFFFF);
        int first = scrollPx / ROW_H;
        int off = -(scrollPx % ROW_H);
        for (int r = 0; r <= dropRows && first + r < shown.size(); r++) {
            Entry e = shown.get(first + r);
            int ry = dropY + 1 + off + r * ROW_H;
            if (ry + ROW_H <= dropY + 1 || ry >= dropY + h - 1) continue;
            if (first + r == hover) g.fill(dropX + 1, Math.max(ry, dropY + 1), dropX + dropW - 1,
                    Math.min(ry + ROW_H, dropY + h - 1), 0xFF404040);
            ItemStack icon = iconFor(e);
            int tx = dropX + 4;
            if (icon != null) {
                g.item(icon, dropX + 3, ry + 1);
                tx += 18;
            }
            String name = displayName(e);
            while (!name.isEmpty() && font.width(name) > dropX + dropW - 6 - tx) {
                name = name.substring(0, name.length() - 1);
            }
            g.text(font, name, tx, ry + 5, 0xFFFFFFFF);
        }
        if (shown.size() * ROW_H > dropRows * ROW_H) {
            int barH = Math.max(8, dropRows * ROW_H * dropRows / shown.size());
            int barY = dropY + 1 + (dropRows * ROW_H - barH) * scrollPx / max;
            g.fill(dropX + dropW - 4, barY, dropX + dropW - 2, barY + barH, 0xFF808080);
        }
    }
}
