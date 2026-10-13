package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

/** The in-game item builder. Open it with the /nbtbuilder command. */
public class BuilderScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xFFDDDDDD;
    private static final int GREEN = 0xFF7FF2A0;
    private static final int RED = 0xFFFF8080;

    private static final String[] TABS = {"Item", "Style", "Enchants", "Attrs", "Food", "Block", "More"};
    private static final String[] MORE_SUBS = {"Potion", "Banner", "Wear", "Blocks"};
    private static final String[] MODE_NAMES = {"Default", "Solid", "Gradient"};
    private static final int ROW = 24;
    private static final int PICK_W = 38;
    private static final int SIDE_W = 104;
    private static final int COL_TOP = 52;
    /** Tab key, for autocomplete. */
    private static final int KEY_TAB = 258;

    // Kept between openings so the screen reopens where you left it. It starts empty.
    private static final ItemModel MODEL = new ItemModel();
    private static int tab = 0;
    private static int target = 0; // 0 name, 1 name end, 2 lore, 3 lore end
    private static float hue = 0.1f;
    private static float sat = 1f;
    private static float val = 1f;
    private static int enchPage = 0;
    private static int attrPage = 0;
    private static int effPage = 0;
    private static int banPage = 0;
    private static int moreSub = 0;

    private int cw;
    private int left;
    private int top;
    private int limit;
    private int statusY;
    private Button nameModeButton;
    private Button loreModeButton;
    private Button boldButton;
    private Button underButton;
    private Button strikeButton;
    private Button italicButton;
    private EditBox hexBox;
    private EditBox customBox;
    private EditBox countBox;
    private EditBox maxBox;
    private NumSlider countSlider;
    private NumSlider maxSlider;
    private final EditBox[] dyeBoxRef = new EditBox[1];
    private final NumSlider[] dyeSliders = new NumSlider[3];
    private final EditBox[] potionBoxRef = new EditBox[1];
    private final NumSlider[] potionSliders = new NumSlider[3];
    private final List<SuggestBox> suggestBoxes = new ArrayList<>();
    private String pendingNote = "";
    private int pendingColor = GREEN;
    private boolean updatingHex;
    private String lastCommand;
    private String statusText = "";
    private String statusDetail = "";
    private int statusColor = SOFT;
    private String tabHint = "";
    private ItemStack previewStack;
    private String previewNbt;
    private ItemStack potionPreview;
    private ItemStack bannerPreview;
    private ItemStack armorPreview;
    private ArmorStand previewStand;
    private ItemStack trimTemplateIcon;
    private final List<ItemStack> bannerIcons = new ArrayList<>();
    private final List<ItemStack> matIcons = new ArrayList<>();
    private final List<ItemStack> pieceIcons = new ArrayList<>();
    private final List<int[]> bannerRowGeom = new ArrayList<>();
    private int matStripX;
    private int matStripY;
    private int matCellW = 20;
    private int pieceStripX;
    private int pieceStripY;
    private int trimIconX;
    private int trimIconY;
    private int morePrevX;
    private int morePrevY;

    // Colour wheel geometry (Style tab).
    private int wheelCx;
    private int wheelCy;
    private int wheelR;
    private int barX;
    private int barTop;
    private int barH;
    private int dragging; // 0 none, 1 wheel, 2 brightness bar
    private int swatchX;
    private int swatchY;

    /** The item being edited, shared with the saved items screen. */
    static ItemModel model() {
        return MODEL;
    }

    static void resetPages() {
        enchPage = 0;
        attrPage = 0;
        effPage = 0;
        banPage = 0;
    }

    public BuilderScreen() {
        super(Component.literal("NBT Item Builder"));
    }

    // ---- layout -------------------------------------------------------------------------

    @Override
    protected void init() {
        // Content in the middle, tabs down the left, commands down the right.
        int assembly = Math.min(this.width - 16, 560 + 2 * (SIDE_W + 8));
        cw = Math.max(160, assembly - 2 * (SIDE_W + 8));
        int outer = (this.width - (cw + 2 * (SIDE_W + 8))) / 2;
        int tabX = outer;
        left = outer + SIDE_W + 8;
        int cmdX = left + cw + 8;
        top = COL_TOP;
        limit = this.height - 48;
        statusY = this.height - 18;
        customBox = null;
        countBox = null;
        maxBox = null;
        countSlider = null;
        maxSlider = null;
        suggestBoxes.clear();
        dyeBoxRef[0] = null;
        dyeSliders[0] = null;
        dyeSliders[1] = null;
        dyeSliders[2] = null;
        potionBoxRef[0] = null;
        potionSliders[0] = null;
        potionSliders[1] = null;
        potionSliders[2] = null;

        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            String text = i == tab ? "[" + TABS[i] + "]" : TABS[i];
            addRenderableWidget(Button.builder(Component.literal(text), b -> {
                tab = index;
                rebuildWidgets();
            }).bounds(tabX, COL_TOP + i * ROW, SIDE_W, 20).build());
        }

        switch (tab) {
            case 0 -> buildItemTab();
            case 1 -> buildStyleTab();
            case 2 -> buildEnchantTab();
            case 3 -> buildAttributeTab();
            case 4 -> buildFoodTab();
            case 5 -> buildBlockTab();
            default -> buildMoreTab();
        }
        buildRight(cmdX);
        refresh();
    }

    /** The command buttons, down the right side. Saved items sits top-left, like in the inventory. */
    private void buildRight(int cmdX) {
        addCmd(cmdX, 0, "Load held item", this::loadHeld);
        addCmd(cmdX, 1, "Apply to held", this::applyHeld);
        addCmd(cmdX, 2, "Item NBT", () -> {
            Minecraft.getInstance().setScreen(new NbtViewScreen(this, MODEL));
        });
        addCmd(cmdX, 3, "Give to me", this::give);
        addCmd(cmdX, 4, "Copy command", this::copy);
        addCmd(cmdX, 5, "Reset", () -> {
            MODEL.reset();
            enchPage = 0;
            attrPage = 0;
            rebuildWidgets();
        });
        addCmd(cmdX, 6, "Close", this::onClose);
        addRenderableWidget(Button.builder(Component.literal("Saved items"), b -> {
            Minecraft.getInstance().setScreen(new SavedScreen(this));
        }).bounds(4, 4, 80, 16).build());
    }

    private void addCmd(int cmdX, int row, String text, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(text), b -> action.run())
                .bounds(cmdX, COL_TOP + row * ROW, SIDE_W, 20).build());
    }

    private EditBox box(int x, int y, int w, String hint, String value, Consumer<String> onChange) {
        EditBox b = new ClipBox(this.font, x, y, w, hint);
        b.setMaxLength(4000);
        b.setTextColor(0xFFFFFFFF);
        b.setHint(Component.literal(hint).withStyle(ChatFormatting.GRAY));
        b.setValue(value);
        b.setResponder(v -> {
            onChange.accept(v);
            refresh();
        });
        addRenderableWidget(b);
        return b;
    }

    /** True when a line of text at height y stays clear of the footer status. */
    private boolean fits(int y) {
        return y + 10 < statusY - 2;
    }

    /** A button that cycles through short option lists such as rarity or equip slot. */
    private void cycle(int x, int y, int w, String[] keys, String[] labels, String prefix,
            Supplier<String> get, Consumer<String> set) {
        addRenderableWidget(Button.builder(cycleLabel(prefix, keys, labels, get.get()), b -> {
            int i = indexOf(keys, get.get());
            String next = keys[(i + 1) % keys.length];
            set.accept(next);
            b.setMessage(cycleLabel(prefix, keys, labels, next));
            refresh();
        }).bounds(x, y, w, 20).build());
    }

    private static Component cycleLabel(String prefix, String[] keys, String[] labels, String value) {
        return Component.literal(prefix + labels[indexOf(keys, value)]);
    }

    // ---- Item tab -----------------------------------------------------------------------

    private void buildItemTab() {
        int countBoxW = 44;
        int countSliderW = 88;
        int idW = cw - countBoxW - 4 - countSliderW - 4;
        suggest(left, top + 12, idW, "diamond_sword", MODEL.id, items(), v -> MODEL.id = v);
        countBox = box(left + idW + 4, top + 12, countBoxW, "1", MODEL.count, v -> {
            MODEL.count = v;
            syncCount();
            refresh();
        });
        countSlider = new NumSlider(left + idW + 4 + countBoxW + 4, top + 12, countSliderW,
                1, this::effMaxStack, this::effCount, v -> {
                    MODEL.count = String.valueOf(clampInt((int) Math.round(v), 1, 99));
                    if (countBox != null) countBox.setValue(MODEL.count);
                    refresh();
                });
        addRenderableWidget(countSlider);
        int half = (cw - 4) / 2;
        cycle(left, top + 40, half, Components.RARITY_KEYS, Components.RARITY_LABELS,
                "Rarity: ", () -> MODEL.rarity, v -> MODEL.rarity = v);
        maxBox = box(left + half + 4, top + 40, half - 84, "Max",
                MODEL.maxStack, v -> {
                    MODEL.maxStack = v;
                    syncMax();
                    syncCount();
                    refresh();
                });
        maxSlider = new NumSlider(left + cw - 80, top + 40, 80,
                1, this::effMaxStackCap, this::effMaxStack, v -> {
                    MODEL.maxStack = String.valueOf(clampInt((int) Math.round(v), 1, 99));
                    if (maxBox != null) maxBox.setValue(MODEL.maxStack);
                    syncMax();
                    syncCount();
                    refresh();
                }).named("Max");
        addRenderableWidget(maxSlider);
        int third = (cw - 8) / 3;
        numPair(left, top + 64, third, "Dmg", "Damage", () -> MODEL.damage, v -> MODEL.damage = v,
                0, this::effMaxDamage, 1);
        numPair(left + third + 4, top + 64, third, "Rep", "Repair", () -> MODEL.repairCost,
                v -> MODEL.repairCost = v, 0, () -> 100d, 1);
        numPair(left + 2 * (third + 4), top + 64, cw - 2 * (third + 4), "Max", "Max dmg",
                () -> MODEL.maxDamage, v -> MODEL.maxDamage = v, 1, () -> 5000d, 1);
        suggest(left, top + 88, third, "Model", MODEL.itemModelId, items(), v -> MODEL.itemModelId = v);
        cycle(left + third + 4, top + 88, third, Components.GLINT_KEYS, Components.GLINT_LABELS,
                "Glint: ", () -> MODEL.glint, v -> MODEL.glint = v);
        addRenderableWidget(Button.builder(unbreakableLabel(), b -> {
            MODEL.unbreakable = !MODEL.unbreakable;
            b.setMessage(unbreakableLabel());
            refresh();
        }).bounds(left + 2 * (third + 4), top + 88, cw - 2 * (third + 4), 20).build());
        box(left, top + 112, cw, "Custom model data {floats:[1]} (empty=none)",
                MODEL.customModelData, v -> MODEL.customModelData = v);
        box(left, top + 136, cw, "Custom data {key:value} (empty=none)", MODEL.customData, v -> MODEL.customData = v);
    }

    /** Highest damage the item takes: the field, the plain item, or a guess. */
    private double effMaxDamage() {
        int field = parseIntOr(MODEL.maxDamage, -1);
        if (field > 0) return field;
        int base = Held.baseMaxDamage(MODEL.id);
        return base < 0 ? 2031d : Math.max(1, base);
    }

    /** True when the item takes damage: the field says so, or the plain item does. */
    private boolean baseDamageable() {
        if (parseIntOr(MODEL.maxDamage, 0) > 0) return true;
        return Held.baseMaxDamage(MODEL.id) > 0;
    }

    /** Damageable items never stack, so their slider stops at 1. */
    private double effMaxStackCap() {
        return baseDamageable() ? 1 : 99;
    }

    /** Rules the game itself enforces, explained before the game has to complain. */
    private String vanillaCheck() {
        boolean damageable = baseDamageable();
        if (damageable && MODEL.maxStack != null && !MODEL.maxStack.isBlank()) {
            if (parseIntOr(MODEL.maxStack, 1) > 1) {
                return "That item takes damage, so its max stack must stay 1.";
            }
        }
        if (!damageable && MODEL.damage != null && !MODEL.damage.isBlank()) {
            if (parseIntOr(MODEL.damage, 0) > 0) {
                return "Only items with durability (tools, armor...) can have damage.";
            }
        }
        return null;
    }

    private Component unbreakableLabel() {
        return Component.literal("Unbreakable: " + (MODEL.unbreakable ? "yes" : "no"));
    }

    // ---- number helpers (sliders + boxes) -------------------------------------------------

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Effective max stack size: the field, or 64 when empty. */
    private double effMaxStack() {
        return clampInt(parseIntOr(MODEL.maxStack, 64), 1, 99);
    }

    private double effCount() {
        return clampInt(parseIntOr(MODEL.count, 1), 1, 99);
    }

    /** Effective enchantment level: the box, or the vanilla maximum when empty. */
    private static double effLevel(ItemModel.Ench e) {
        int fallback = e.id.isBlank() ? 1 : ItemSpec.maxLevel(e.id);
        return clampInt(parseIntOr(e.level, fallback), 1, 255);
    }

    private void syncCount() {
        if (countSlider != null) countSlider.sync();
    }

    private void syncMax() {
        if (maxSlider != null) maxSlider.sync();
    }

    /**
     * One number as a labeled slider next to its box: drag for speed, type for exact.
     * The box stays the source of truth; bad text just flags an error below.
     * Keep the box hint tiny (the slider carries the name).
     */
    private void numPair(int x, int y, int w, String hint, String label, Supplier<String> getV,
            Consumer<String> setV, double min, DoubleSupplier maxSup, double step) {
        int boxW = 46;
        final EditBox[] boxRef = new EditBox[1];
        NumSlider slider = new NumSlider(x, y, w - boxW - 4, min, maxSup,
                () -> Components.parseDoubleOr(getV.get(), min), dv -> {
                    String s = Components.fmtStep(dv, step);
                    setV.accept(s);
                    if (boxRef[0] != null) boxRef[0].setValue(s);
                    refresh();
                }, step).named(label);
        addRenderableWidget(slider);
        boxRef[0] = box(x + w - boxW, y, boxW, hint, getV.get(), v -> {
            setV.accept(v);
            slider.sync();
            refresh();
        });
    }

    // ---- Style tab ----------------------------------------------------------------------

    private ItemModel.ColorStyle targetStyle() {
        return target < 2 ? MODEL.nameStyle : MODEL.loreStyle;
    }

    private int targetColor() {
        ItemModel.ColorStyle s = targetStyle();
        return target % 2 == 1 ? s.c2 : s.c1;
    }

    private void setTargetColor(int rgb) {
        ItemModel.ColorStyle s = targetStyle();
        if (target % 2 == 1) {
            s.c2 = rgb;
            s.mode = ItemModel.ColorStyle.GRADIENT;
        } else {
            s.c1 = rgb;
            if (s.mode == ItemModel.ColorStyle.DEFAULT) s.mode = ItemModel.ColorStyle.SOLID;
        }
        updateModeButtons();
    }

    private void syncHsv() {
        float[] hsv = Colors.rgbToHsv(targetColor());
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    private Component modeLabel(String what, ItemModel.ColorStyle s) {
        return Component.literal(what + " colour: " + MODE_NAMES[s.mode]);
    }

    private void updateModeButtons() {
        if (nameModeButton != null) nameModeButton.setMessage(modeLabel("Name", MODEL.nameStyle));
        if (loreModeButton != null) loreModeButton.setMessage(modeLabel("Lore", MODEL.loreStyle));
        updateFormatButtons();
    }

    /** A toggle for bold, underline, strikethrough or italics. On shows [brackets]. */
    private Button fmtButton(int x, int y, int w, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        Button b = Button.builder(fmtLabel(name, get.get()), btn -> {
            set.accept(!get.get());
            updateFormatButtons();
            refresh();
        }).bounds(x, y, w, 20).build();
        addRenderableWidget(b);
        return b;
    }

    private static Component fmtLabel(String name, boolean on) {
        return Component.literal(on ? "[" + name + "]" : name);
    }

    private void updateFormatButtons() {
        if (boldButton != null) boldButton.setMessage(fmtLabel("Bold", targetStyle().bold));
        if (underButton != null) underButton.setMessage(fmtLabel("Under", targetStyle().underline));
        if (strikeButton != null) strikeButton.setMessage(fmtLabel("Strike", targetStyle().strikethrough));
        if (italicButton != null) italicButton.setMessage(fmtLabel("Italic", targetStyle().italic));
    }

    private void buildStyleTab() {
        int gap = 8;
        int lw = (cw - gap) / 2;
        int rx = left + lw + gap;
        int rw = cw - lw - gap;

        box(left, top + 12, lw, "Leave empty for the default name", MODEL.name, v -> MODEL.name = v);
        box(left, top + 48, lw, "First line | Second line", MODEL.lore, v -> MODEL.lore = v);

        nameModeButton = Button.builder(modeLabel("Name", MODEL.nameStyle), b -> {
            MODEL.nameStyle.mode = (MODEL.nameStyle.mode + 1) % 3;
            updateModeButtons();
            refresh();
        }).bounds(left, top + 74, lw, 20).build();
        loreModeButton = Button.builder(modeLabel("Lore", MODEL.loreStyle), b -> {
            MODEL.loreStyle.mode = (MODEL.loreStyle.mode + 1) % 3;
            updateModeButtons();
            refresh();
        }).bounds(left, top + 98, lw, 20).build();
        addRenderableWidget(nameModeButton);
        addRenderableWidget(loreModeButton);
        int fw = (lw - 12) / 4;
        boldButton = fmtButton(left, top + 134, fw, "Bold",
                () -> targetStyle().bold, v -> targetStyle().bold = v);
        underButton = fmtButton(left + fw + 4, top + 134, fw, "Under",
                () -> targetStyle().underline, v -> targetStyle().underline = v);
        strikeButton = fmtButton(left + 2 * (fw + 4), top + 134, fw, "Strike",
                () -> targetStyle().strikethrough, v -> targetStyle().strikethrough = v);
        italicButton = fmtButton(left + 3 * (fw + 4), top + 134, lw - 3 * (fw + 4), "Italic",
                () -> targetStyle().italic, v -> targetStyle().italic = v);

        int pw = (lw - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Add picture..."), b -> importPicture())
                .bounds(left, top + 158, pw, 20).build());
        Button clearPic = Button.builder(
                Component.literal(MODEL.picture.isEmpty() ? "No picture" : "Clear picture"), b -> {
                    MODEL.picture.clear();
                    rebuildWidgets();
                }).bounds(left + pw + 4, top + 158, lw - pw - 4, 20).build();
        clearPic.active = !MODEL.picture.isEmpty();
        addRenderableWidget(clearPic);

        // Right column: which colour is being edited, then the wheel.
        String[] names = {"Name", "Name end", "Lore", "Lore end"};
        int half = (rw - 4) / 2;
        for (int i = 0; i < 4; i++) {
            final int index = i;
            String text = i == target ? "[" + names[i] + "]" : names[i];
            addRenderableWidget(Button.builder(Component.literal(text), b -> {
                target = index;
                rebuildWidgets();
            }).bounds(rx + (i % 2) * (half + 4), top + (i / 2) * 22, half, 20).build());
        }
        syncHsv();
        int wy = top + 48;
        wheelR = Math.max(16, Math.min(40, (limit - wy - 26) / 2));
        int blockW = 2 * wheelR + 16;
        wheelCx = rx + Math.max(0, (rw - blockW) / 2) + wheelR;
        wheelCy = wy + wheelR;
        barX = wheelCx + wheelR + 8;
        barTop = wy;
        barH = 2 * wheelR;

        int hy = wy + 2 * wheelR + 4;
        hexBox = new EditBox(this.font, rx, hy, rw - 26, 20, Component.literal("Hex colour"));
        hexBox.setMaxLength(7);
        hexBox.setTextColor(0xFFFFFFFF);
        hexBox.setHint(Component.literal("#rrggbb").withStyle(ChatFormatting.GRAY));
        hexBox.setValue(Colors.hex(targetColor()));
        hexBox.setResponder(v -> {
            if (updatingHex) return;
            Integer rgb = Colors.parseHex(v);
            if (rgb == null) return;
            setTargetColor(rgb);
            float[] hsv = Colors.rgbToHsv(rgb);
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            refresh();
        });
        addRenderableWidget(hexBox);
        swatchX = rx + rw - 22;
        swatchY = hy;
    }

    private void commitColor() {
        int rgb = Colors.hsvToRgb(hue, sat, val);
        setTargetColor(rgb);
        updatingHex = true;
        hexBox.setValue(Colors.hex(rgb));
        updatingHex = false;
        refresh();
    }

    private void dragWheel(double mx, double my) {
        double dx = mx - wheelCx;
        double dy = my - wheelCy;
        double dist = Math.min(wheelR, Math.sqrt(dx * dx + dy * dy));
        hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1.0) % 1.0);
        sat = (float) (dist / wheelR);
        commitColor();
    }

    private void dragBar(double my) {
        double t = (my - barTop) / barH;
        val = (float) (1.0 - Math.max(0.0, Math.min(1.0, t)));
        commitColor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (clickStrip(event.x(), event.y())) return true;
            for (SuggestBox s : suggestBoxes) {
                if (s.mouseClicked(event.x(), event.y())) return true;
            }
            boolean onBox = false;
            for (SuggestBox s : suggestBoxes) {
                if (s.boxContains(event.x(), event.y())) {
                    onBox = true;
                    closeOthers(s);
                    s.open();
                }
            }
            if (!onBox) closeOthers(null);
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        if (tab == 1 && event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            double dx = mx - wheelCx;
            double dy = my - wheelCy;
            if (Math.sqrt(dx * dx + dy * dy) <= wheelR + 2) {
                dragging = 1;
                dragWheel(mx, my);
                return true;
            }
            if (mx >= barX - 2 && mx <= barX + 10 && my >= barTop && my <= barTop + barH) {
                dragging = 2;
                dragBar(my);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == 1) {
            dragWheel(event.x(), event.y());
            return true;
        }
        if (dragging == 2) {
            dragBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = 0;
        return super.mouseReleased(event);
    }

    // ---- Enchants tab -------------------------------------------------------------------

    private int rowsPerPage() {
        // Two-line rows: id line, then sliders. The last row ends 16px above the hint line.
        return Math.max(1, (limit - (top + 26) - 16) / (ROW * 2));
    }

    private void buildEnchantTab() {
        int per = rowsPerPage();
        int n = MODEL.enchants.size();
        int pages = Math.max(1, (n + per - 1) / per);
        if (enchPage >= pages) enchPage = pages - 1;
        if (enchPage < 0) enchPage = 0;

        addRenderableWidget(Button.builder(Component.literal("+ Add enchantment"), b -> {
            MODEL.enchants.add(new ItemModel.Ench());
            enchPage = Math.max(0, (MODEL.enchants.size() - 1) / per);
            rebuildWidgets();
        }).bounds(left, top, cw - 68, 20).build());
        addPager(top, true);

        for (int r = 0; r < per; r++) {
            int i = enchPage * per + r;
            if (i >= n) break;
            final ItemModel.Ench e = MODEL.enchants.get(i);
            int y = top + 26 + r * ROW * 2;
            box(left, y, cw - PICK_W - 24, "enchantment", e.id, v -> e.id = v);
            addRenderableWidget(Button.builder(Component.literal("Pick"), b -> {
                Minecraft.getInstance().setScreen(new PickerScreen(this, "Choose an enchantment",
                        ItemSpec.enchantOptions(), id -> e.id = id));
            }).bounds(left + cw - PICK_W - 20, y, PICK_W, 20).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                MODEL.enchants.remove(e);
                rebuildWidgets();
            }).bounds(left + cw - 20, y, 20, 20).build());
            final EditBox[] levelBox = new EditBox[1];
            NumSlider levelSlider = new NumSlider(left, y + ROW, cw - 48, 1, () -> 255d,
                    () -> (double) effLevel(e), v -> {
                        e.level = String.valueOf(clampInt((int) Math.round(v), 1, 255));
                        if (levelBox[0] != null) levelBox[0].setValue(e.level);
                        refresh();
                    }).named("Level");
            addRenderableWidget(levelSlider);
            int max = e.id.isBlank() ? 0 : ItemSpec.maxLevel(e.id);
            levelBox[0] = box(left + cw - 44, y + ROW, 44, max > 0 ? "max " + max : "level", e.level, v -> {
                e.level = v;
                levelSlider.sync();
                refresh();
            });
        }
    }

    // ---- Attributes tab -----------------------------------------------------------------

    private void buildAttributeTab() {
        int per = rowsPerPage();
        int n = MODEL.attrs.size();
        int pages = Math.max(1, (n + per - 1) / per);
        if (attrPage >= pages) attrPage = pages - 1;
        if (attrPage < 0) attrPage = 0;

        addRenderableWidget(Button.builder(Component.literal("+ Add attribute"), b -> {
            MODEL.attrs.add(new ItemModel.Attr());
            attrPage = Math.max(0, (MODEL.attrs.size() - 1) / per);
            rebuildWidgets();
        }).bounds(left, top, cw - 68, 20).build());
        addPager(top, false);

        for (int r = 0; r < per; r++) {
            int i = attrPage * per + r;
            if (i >= n) break;
            final ItemModel.Attr a = MODEL.attrs.get(i);
            int y = top + 26 + r * ROW * 2;
            box(left, y, cw - PICK_W - 24, "attribute", a.id, v -> a.id = v);
            addRenderableWidget(Button.builder(Component.literal("Pick"), b -> {
                Minecraft.getInstance().setScreen(new PickerScreen(this, "Choose an attribute",
                        ItemSpec.attributeOptions(), id -> a.id = id));
            }).bounds(left + cw - PICK_W - 20, y, PICK_W, 20).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                MODEL.attrs.remove(a);
                rebuildWidgets();
            }).bounds(left + cw - 20, y, 20, 20).build());
            int y2 = y + ROW;
            int amtW = 46;
            int rest = cw - amtW - 8;
            int opW = rest / 2;
            int slotW = rest - opW;
            box(left, y2, amtW, "amount", a.amount, v -> a.amount = v);
            addRenderableWidget(Button.builder(Component.literal(ItemSpec.OP_LABELS[indexOf(ItemSpec.OP_KEYS, a.op)]), b -> {
                int next = (indexOf(ItemSpec.OP_KEYS, a.op) + 1) % ItemSpec.OP_KEYS.length;
                a.op = ItemSpec.OP_KEYS[next];
                b.setMessage(Component.literal(ItemSpec.OP_LABELS[next]));
                refresh();
            }).bounds(left + amtW + 4, y2, opW, 20).build());
            addRenderableWidget(Button.builder(Component.literal(ItemSpec.SLOT_LABELS[indexOf(ItemSpec.SLOT_KEYS, a.slot)]), b -> {
                int next = (indexOf(ItemSpec.SLOT_KEYS, a.slot) + 1) % ItemSpec.SLOT_KEYS.length;
                a.slot = ItemSpec.SLOT_KEYS[next];
                b.setMessage(Component.literal(ItemSpec.SLOT_LABELS[next]));
                refresh();
            }).bounds(left + amtW + 4 + opW + 4, y2, slotW, 20).build());
        }
    }

    private static int indexOf(String[] keys, String key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(key)) return i;
        }
        return 0;
    }

    private void addPager(int y, boolean ench) {
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (ench) enchPage--;
            else attrPage--;
            rebuildWidgets();
        }).bounds(left + cw - 64, y, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (ench) enchPage++;
            else attrPage++;
            rebuildWidgets();
        }).bounds(left + cw - 30, y, 30, 20).build());
    }

    // ---- Food & Gear tab ----------------------------------------------------------------

    private void buildFoodTab() {
        int third = (cw - 8) / 3;
        numPair(left, top + 12, third, "Food", "Nutrition", () -> MODEL.nutrition, v -> MODEL.nutrition = v,
                0, () -> 20d, 1);
        numPair(left + third + 4, top + 12, third, "Sat.", "Saturation", () -> MODEL.saturation,
                v -> MODEL.saturation = v, 0, () -> 20d, 0.5);
        addRenderableWidget(Button.builder(Component.literal("Always edible: " + (MODEL.alwaysEat ? "yes" : "no")), b -> {
            MODEL.alwaysEat = !MODEL.alwaysEat;
            b.setMessage(Component.literal("Always edible: " + (MODEL.alwaysEat ? "yes" : "no")));
            refresh();
        }).bounds(left + 2 * (third + 4), top + 12, cw - 2 * (third + 4), 20).build());

        numPair(left, top + 36, third, "Secs", "Eat time", () -> MODEL.eatSeconds, v -> MODEL.eatSeconds = v,
                0.1, () -> 10d, 0.1);
        cycle(left + third + 4, top + 36, third, Components.EAT_ANIM_KEYS, Components.EAT_ANIM_LABELS,
                "Anim: ", () -> MODEL.eatAnimation, v -> MODEL.eatAnimation = v);
        box(left + 2 * (third + 4), top + 36, cw - 2 * (third + 4), "Eat sound",
                MODEL.eatSound, v -> MODEL.eatSound = v);

        int half = (cw - 4) / 2;
        cycle(left, top + 60, half, Components.EQUIP_KEYS, Components.EQUIP_LABELS,
                "Equip: ", () -> MODEL.equipSlot, v -> MODEL.equipSlot = v);
        box(left + half + 4, top + 60, cw - half - 4, "Equip sound",
                MODEL.equipSound, v -> MODEL.equipSound = v);

        numPair(left, top + 84, half, "Cool.", "Cooldown", () -> MODEL.useCooldown, v -> MODEL.useCooldown = v,
                0, () -> 60d, 1);
        suggest(left + half + 4, top + 84, cw - half - 4, "Remainder",
                MODEL.useRemainder, items(), v -> MODEL.useRemainder = v);
    }

    // ---- Block & NBT tab ----------------------------------------------------------------

    private void buildBlockTab() {
        int y = top + 12;
        if (MODEL.rawContainer != null) {
            addRenderableWidget(Button.builder(Component.literal("Clear contents"), b -> {
                MODEL.rawContainer = null;
                MODEL.contents = "";
                rebuildWidgets();
            }).bounds(left, y, cw, 20).build());
        } else {
            int handW = 108;
            box(left, y, cw - handW - 4, "Container: torch x64; pickaxe efficiency=5", MODEL.contents,
                    v -> MODEL.contents = v);
            addRenderableWidget(Button.builder(Component.literal("Add hand item"), b -> appendHand())
                    .bounds(left + cw - handW, y, handW, 20).build());
        }
        y += ROW;
        suggest(left, y, cw, "Spawner mob, e.g. zombie (empty=none)", MODEL.spawnerEntity,
                mobs(), v -> MODEL.spawnerEntity = v);
        y += ROW;
        box(left, y, cw, "Entity data {Value:100} (empty=none)", MODEL.entityData, v -> MODEL.entityData = v);
        y += ROW;
        int pw3 = (cw - 8) / 3;
        numPair(left, y, pw3, "Delay", "Delay", () -> MODEL.spawnDelay, v -> MODEL.spawnDelay = v,
                0, () -> 2000d, 1);
        numPair(left + pw3 + 4, y, pw3, "Min", "Min", () -> MODEL.minDelay, v -> MODEL.minDelay = v,
                0, () -> 2000d, 1);
        numPair(left + 2 * (pw3 + 4), y, cw - 2 * (pw3 + 4), "Max", "Max", () -> MODEL.maxDelay,
                v -> MODEL.maxDelay = v, 0, () -> 2000d, 1);
        y += ROW;
        int pw2 = (cw - 4) / 2;
        numPair(left, y, pw2, "Count", "Count", () -> MODEL.spawnCount, v -> MODEL.spawnCount = v,
                0, () -> 64d, 1);
        numPair(left + pw2 + 4, y, pw2, "Range", "Range", () -> MODEL.spawnRange, v -> MODEL.spawnRange = v,
                0, () -> 32d, 1);
        y += ROW;
        numPair(left, y, pw2, "Play.", "Players", () -> MODEL.requiredPlayers, v -> MODEL.requiredPlayers = v,
                0, () -> 64d, 1);
        numPair(left + pw2 + 4, y, pw2, "Near.", "Nearby", () -> MODEL.maxNearby, v -> MODEL.maxNearby = v,
                0, () -> 64d, 1);
        y += ROW;
        box(left, y, cw, "Extra block data {id:...} (empty=none)", MODEL.blockEntityRaw, v -> MODEL.blockEntityRaw = v);
        y += ROW;
        customBox = box(left, y, cw - PICK_W - 4, "Custom: minecraft:some_thing={...}; ...", MODEL.customComps,
                v -> MODEL.customComps = v);
        addRenderableWidget(Button.builder(Component.literal("Pick"), b -> {
            Minecraft.getInstance().setScreen(new PickerScreen(this, "Choose a component",
                    Components.componentOptions(), id -> {
                        MODEL.customComps = MODEL.customComps.isBlank() ? id + "=" : MODEL.customComps.trim() + "; " + id + "=";
                    }));
        }).bounds(left + cw - PICK_W, y, PICK_W, 20).build());
    }

    /** Appends the held item to the container contents, e.g. for filling a shulker box. */
    private void appendHand() {
        try {
            String entry = Held.handEntry();
            MODEL.contents = MODEL.contents.isBlank() ? entry : MODEL.contents.trim() + "; " + entry;
            rebuildWidgets();
            say("Added " + entry + " to the contents.", GREEN);
        } catch (IllegalArgumentException e) {
            say(e.getMessage() == null ? "Could not read the held item." : e.getMessage(), RED);
        } catch (RuntimeException e) {
            say("Could not read the held item.", RED);
        }
    }

    /** Picks a picture from the pictures folder and appends it to the lore. */
    private void importPicture() {
        List<String[]> options = PictureImport.imageOptions();
        if (options.isEmpty()) {
            String folder = PictureImport.folder() == null ? "config/nbtitembuilder-pictures"
                    : PictureImport.folder().toString();
            sayLong("Put .png or .jpg files in the pictures folder, then try again.", folder, RED);
            return;
        }
        Minecraft.getInstance().setScreen(new PickerScreen(this, "Choose a picture", options, name -> {
            try {
                List<int[]> rows = PictureImport.load(name);
                MODEL.picture.addAll(rows);
                pendingNote = "Added a picture (" + rows.get(0).length + "x" + rows.size() + ") to the lore.";
                pendingColor = GREEN;
            } catch (RuntimeException ex) {
                pendingNote = ex.getMessage() == null ? "Could not read that picture." : ex.getMessage();
                pendingColor = RED;
            }
        }));
    }

    // ---- More tab (potion, banner, wear, blocks) ------------------------------------------

    private int morePages = 1;
    private int morePage = 1;

    private void buildMoreTab() {
        for (int i = 0; i < MORE_SUBS.length; i++) {
            final int index = i;
            String text = i == moreSub ? "[" + MORE_SUBS[i] + "]" : MORE_SUBS[i];
            addRenderableWidget(Button.builder(Component.literal(text), b -> {
                moreSub = index;
                rebuildWidgets();
            }).bounds(left, top + 12 + i * ROW, cw, 20).build());
        }
        int base = top + 12 + MORE_SUBS.length * ROW + 4;
        switch (moreSub) {
            case 0 -> buildMorePotion(base);
            case 1 -> buildMoreBanner(base);
            case 2 -> buildMoreWear(base);
            default -> buildMoreBlocks(base);
        }
    }

    private void buildMorePotion(int y0) {
        int main = cw - 56;
        morePrevX = left + main + 8;
        morePrevY = y0;
        suggest(left, y0, main, "Potion, e.g. swiftness (empty=none)", MODEL.potion,
                potions(), v -> MODEL.potion = v);

        int sw = (main - 8) / 3;
        String[] channels = {"R", "G", "B"};
        for (int i = 0; i < 3; i++) {
            final int ch = i;
            NumSlider slider = new NumSlider(left + i * (sw + 4), y0 + 24, sw, 0, () -> 255d,
                    () -> (double) ((effPotionColor() >> ((2 - ch) * 8)) & 0xFF), dv -> {
                        int rgb = effPotionColor();
                        int v = (int) Math.round(dv);
                        rgb = ch == 0 ? (rgb & 0x00FFFF) | (v << 16)
                                : ch == 1 ? (rgb & 0xFF00FF) | (v << 8) : (rgb & 0xFFFF00) | v;
                        MODEL.potionColor = Colors.hex(rgb);
                        if (potionBoxRef[0] != null) potionBoxRef[0].setValue(MODEL.potionColor);
                        refresh();
                    }).labeled(channels[i]);
            addRenderableWidget(slider);
            potionSliders[ch] = slider;
        }
        potionBoxRef[0] = box(left, y0 + 48, main, "Potion color #rrggbb (empty=vanilla)", MODEL.potionColor, v -> {
            MODEL.potionColor = v;
            syncPotionColor();
            refresh();
        });

        int per = Math.max(1, (limit - (y0 + 98) - 16) / (ROW * 2));
        int n = MODEL.effects.size();
        int pages = Math.max(1, (n + per - 1) / per);
        if (effPage >= pages) effPage = pages - 1;
        if (effPage < 0) effPage = 0;
        morePages = pages;
        morePage = effPage + 1;
        addRenderableWidget(Button.builder(Component.literal("+ Add effect"), b -> {
            MODEL.effects.add(new ItemModel.CustomEffect());
            effPage = Math.max(0, (MODEL.effects.size() - 1) / per);
            rebuildWidgets();
        }).bounds(left, y0 + 72, main - 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            effPage--;
            rebuildWidgets();
        }).bounds(left + main - 64, y0 + 72, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            effPage++;
            rebuildWidgets();
        }).bounds(left + main - 30, y0 + 72, 30, 20).build());

        int delW = 20;
        for (int r = 0; r < per; r++) {
            int i = effPage * per + r;
            if (i >= n) break;
            final ItemModel.CustomEffect ce = MODEL.effects.get(i);
            int y = y0 + 98 + r * ROW * 2;
            suggest(left, y, main - delW - 4, "effect", ce.id, effects(), v -> ce.id = v);
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                MODEL.effects.remove(ce);
                rebuildWidgets();
            }).bounds(left + main - delW, y, delW, 20).build());
            int y2 = y + ROW;
            int ampW = (main - 4) / 2;
            int durW = main - ampW - 4;
            final EditBox[] ampBox = new EditBox[1];
            NumSlider ampSlider = new NumSlider(left, y2, ampW - 50, 0, () -> 255d,
                    () -> Components.parseDoubleOr(ce.amp, 0), dv -> {
                        String s = Components.fmtStep(dv, 1);
                        ce.amp = s;
                        if (ampBox[0] != null) ampBox[0].setValue(s);
                        refresh();
                    }).named("Level");
            addRenderableWidget(ampSlider);
            ampBox[0] = box(left + ampW - 46, y2, 46, "Lvl", ce.amp, v -> {
                ce.amp = v;
                ampSlider.sync();
                refresh();
            });
            final EditBox[] durBox = new EditBox[1];
            NumSlider durSlider = new NumSlider(left + ampW + 4, y2, durW - 50, 0, () -> 600d,
                    () -> Components.parseDoubleOr(ce.dur, 30), dv -> {
                        String s = Components.fmtStep(dv, 5);
                        ce.dur = s;
                        if (durBox[0] != null) durBox[0].setValue(s);
                        refresh();
                    }).named("Secs");
            addRenderableWidget(durSlider);
            durBox[0] = box(left + ampW + 4 + durW - 46, y2, 46, "Secs", ce.dur, v -> {
                ce.dur = v;
                durSlider.sync();
                refresh();
            });
        }
    }

    private static int effPotionColor() {
        Integer rgb = Colors.parseHex(MODEL.potionColor);
        return rgb == null ? 0x3700FF : rgb;
    }

    private void syncPotionColor() {
        for (NumSlider s : potionSliders) {
            if (s != null) s.sync();
        }
    }

    private void buildMoreBanner(int y0) {
        int main = cw - 56;
        morePrevX = left + main + 8;
        morePrevY = y0;
        cycle(left, y0, main, Components.DYE_KEYS, Components.DYE_LABELS,
                "Base color: ", () -> MODEL.bannerBase, v -> MODEL.bannerBase = v);

        int per = Math.max(1, (limit - (y0 + 50) - 16) / (ROW * 2));
        int n = MODEL.bannerPats.size();
        int pages = Math.max(1, (n + per - 1) / per);
        if (banPage >= pages) banPage = pages - 1;
        if (banPage < 0) banPage = 0;
        morePages = pages;
        morePage = banPage + 1;
        addRenderableWidget(Button.builder(Component.literal("+ Add layer"), b -> {
            MODEL.bannerPats.add(new ItemModel.BannerPat());
            banPage = Math.max(0, (MODEL.bannerPats.size() - 1) / per);
            rebuildWidgets();
        }).bounds(left, y0 + 24, main - 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            banPage--;
            rebuildWidgets();
        }).bounds(left + main - 64, y0 + 24, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            banPage++;
            rebuildWidgets();
        }).bounds(left + main - 30, y0 + 24, 30, 20).build());

        int miniW = 20;
        int delW = 20;
        bannerRowGeom.clear();
        for (int r = 0; r < per; r++) {
            int i = banPage * per + r;
            if (i >= n) break;
            final ItemModel.BannerPat p = MODEL.bannerPats.get(i);
            int y = y0 + 50 + r * ROW * 2;
            suggest(left, y, main - delW - 4, "pattern", p.pattern, banners(), v -> p.pattern = v);
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                MODEL.bannerPats.remove(p);
                rebuildWidgets();
            }).bounds(left + main - delW, y, delW, 20).build());
            int y2 = y + ROW;
            bannerRowGeom.add(new int[] {left + 2, y2 + 2, i});
            cycle(left + miniW + 4, y2, main - miniW - 4, Components.DYE_KEYS, Components.DYE_LABELS,
                    "", () -> p.color, v -> p.color = v);
        }
    }

    private static final String[][] TRIM_INGREDIENTS = {
        {"quartz", "quartz"}, {"iron", "iron_ingot"}, {"netherite", "netherite_ingot"},
        {"redstone", "redstone"}, {"copper", "copper_ingot"}, {"gold", "gold_ingot"},
        {"emerald", "emerald"}, {"diamond", "diamond"}, {"lapis", "lapis_lazuli"},
        {"amethyst", "amethyst_shard"}, {"resin", "resin_brick"},
    };

    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};

    private void buildMoreWear(int y0) {
        int main = cw - 56;
        morePrevX = left + main + 8;
        morePrevY = y0;
        // Material: icons of the ingredients, no names. Cells shrink on narrow windows.
        matStripX = left;
        matStripY = y0;
        matCellW = Math.min(20, main / TRIM_INGREDIENTS.length);
        matIcons.clear();
        for (String[] t : TRIM_INGREDIENTS) {
            matIcons.add(SuggestBox.icon("ingredient:" + t[1], "{id:\"minecraft:" + t[1] + "\",count:1}"));
        }
        // Pattern: name box plus its smithing template icon.
        suggest(left, y0 + 24, main - 24, "Pattern", MODEL.trimPattern, trimPats(), v -> MODEL.trimPattern = v);
        trimIconX = left + main - 20;
        trimIconY = y0 + 26;
        // Armor piece the preview wears.
        pieceStripX = left;
        pieceStripY = y0 + 48;
        pieceIcons.clear();
        for (String p : PIECES) {
            pieceIcons.add(SuggestBox.icon("piece:" + p, "{id:\"minecraft:diamond_" + p + "\",count:1}"));
        }

        int sw = (main - 8) / 3;
        String[] channels = {"R", "G", "B"};
        for (int i = 0; i < 3; i++) {
            final int ch = i;
            NumSlider slider = new NumSlider(left + i * (sw + 4), y0 + 72, sw, 0, () -> 255d,
                    () -> (double) ((effDye() >> ((2 - ch) * 8)) & 0xFF), dv -> {
                        int rgb = effDye();
                        int v = (int) Math.round(dv);
                        rgb = ch == 0 ? (rgb & 0x00FFFF) | (v << 16)
                                : ch == 1 ? (rgb & 0xFF00FF) | (v << 8) : (rgb & 0xFFFF00) | v;
                        MODEL.dye = Colors.hex(rgb);
                        if (dyeBoxRef[0] != null) dyeBoxRef[0].setValue(MODEL.dye);
                        refresh();
                    }).labeled(channels[i]);
            addRenderableWidget(slider);
            dyeSliders[ch] = slider;
        }
        dyeBoxRef[0] = box(left, y0 + 96, main, "Leather dye #rrggbb (empty=undyed)", MODEL.dye, v -> {
            MODEL.dye = v;
            syncDye();
            refresh();
        });
    }

    /** Clicks on the material and armor piece icon strips. True when handled. */
    private boolean clickStrip(double mx, double my) {
        if (tab != 6 || moreSub != 2) return false;
        for (int i = 0; i < TRIM_INGREDIENTS.length; i++) {
            if (mx >= matStripX + i * matCellW && mx < matStripX + i * matCellW + matCellW - 2
                    && my >= matStripY && my < matStripY + 20) {
                String id = TRIM_INGREDIENTS[i][0];
                MODEL.trimMaterial = id.equals(normId(MODEL.trimMaterial)) ? "" : id;
                refresh();
                return true;
            }
        }
        for (int i = 0; i < PIECES.length; i++) {
            if (mx >= pieceStripX + i * 20 && mx < pieceStripX + i * 20 + 18
                    && my >= pieceStripY && my < pieceStripY + 20) {
                MODEL.trimPiece = PIECES[i];
                refresh();
                return true;
            }
        }
        return false;
    }

    private static String normId(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(java.util.Locale.ROOT);
        int i = t.indexOf(':');
        return i < 0 ? t : t.substring(i + 1);
    }

    private static int effDye() {
        Integer rgb = Colors.parseHex(MODEL.dye);
        return rgb == null ? 0xA06540 : rgb;
    }

    private void syncDye() {
        for (NumSlider s : dyeSliders) {
            if (s != null) s.sync();
        }
    }

    /** Previews for the More tab: potion bottle, banner, trimmed armor, icons. */
    private void refreshMorePreviews() {
        potionPreview = null;
        bannerPreview = null;
        armorPreview = null;
        trimTemplateIcon = null;
        bannerIcons.clear();
        if (tab != 6) return;
        try {
            if (moreSub == 0) {
                potionPreview = Held.preview(potionPreviewNbt());
            } else if (moreSub == 1) {
                bannerPreview = Held.preview(bannerPreviewNbt());
                for (ItemModel.BannerPat p : MODEL.bannerPats) bannerIcons.add(bannerIcon(p));
            } else if (moreSub == 2) {
                try {
                    armorPreview = Held.preview(armorPreviewNbt());
                } catch (IllegalArgumentException e) {
                    armorPreview = null;
                }
                dressStand();
                trimTemplateIcon = templateIcon();
            }
        } catch (IllegalArgumentException e) {
            // Invalid fields: the previews stay blank and the status explains.
        }
    }

    private static String potionPreviewNbt() {
        ItemModel p = new ItemModel();
        p.id = "potion";
        p.potion = MODEL.potion;
        p.potionColor = MODEL.potionColor;
        p.effects.addAll(MODEL.effects);
        return ItemSpec.itemNbt(p);
    }

    private static String bannerPreviewNbt() {
        ItemModel b = new ItemModel();
        String base = normId(MODEL.bannerBase);
        if (base.isEmpty()) base = "white";
        b.id = base + "_banner";
        b.bannerPats.addAll(MODEL.bannerPats);
        return ItemSpec.itemNbt(b);
    }

    private static String armorPreviewNbt() {
        ItemModel a = new ItemModel();
        String piece = MODEL.trimPiece;
        boolean known = false;
        for (String p : PIECES) {
            if (p.equals(piece)) known = true;
        }
        if (!known) piece = "chestplate";
        boolean dyed = MODEL.dye != null && !MODEL.dye.isBlank();
        a.id = (dyed ? "leather" : "diamond") + "_" + piece;
        a.trimMaterial = MODEL.trimMaterial;
        a.trimPattern = MODEL.trimPattern;
        a.dye = MODEL.dye;
        return ItemSpec.itemNbt(a);
    }

    /** Puts the preview armor on the stand, creating it on first use. */
    private void dressStand() {
        if (previewStand == null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            try {
                previewStand = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            } catch (RuntimeException e) {
                return;
            }
        }
        EquipmentSlot slot = switch (normId(MODEL.trimPiece)) {
            case "helmet" -> EquipmentSlot.HEAD;
            case "leggings" -> EquipmentSlot.LEGS;
            case "boots" -> EquipmentSlot.FEET;
            default -> EquipmentSlot.CHEST;
        };
        previewStand.setItemSlot(slot, armorPreview == null ? ItemStack.EMPTY : armorPreview);
        // One piece at a time: clear the rest so old previews never linger.
        for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            if (s != slot) previewStand.setItemSlot(s, ItemStack.EMPTY);
        }
    }
    /** One banner layer on white, for the mini preview in its row. */
    private static ItemStack bannerIcon(ItemModel.BannerPat p) {
        String pat = normId(p.pattern);
        if (pat.isEmpty()) return null;
        String color = p.color == null || p.color.isBlank() ? "white"
                : p.color.trim().toLowerCase(java.util.Locale.ROOT);
        return SuggestBox.icon("bannerrow:" + pat + "|" + color,
                "{id:\"minecraft:white_banner\",count:1,components:{\"minecraft:banner_patterns\":[{pattern:\"minecraft:"
                        + pat + "\",color:\"" + color + "\"}]}}");
    }

    /** The smithing template for the chosen trim pattern, or null. */
    private static ItemStack templateIcon() {
        String p = normId(MODEL.trimPattern);
        if (p.isEmpty()) return null;
        return SuggestBox.icon("template:" + p,
                "{id:\"minecraft:" + p + "_armor_trim_smithing_template\",count:1}");
    }

    private void buildMoreBlocks(int y0) {
        int y = y0;
        if (MODEL.beesRaw != null) {
            addRenderableWidget(Button.builder(Component.literal("Clear bees"), b -> {
                MODEL.beesRaw = null;
                MODEL.beeCount = "";
                rebuildWidgets();
            }).bounds(left, y, cw, 20).build());
        } else {
            numPair(left, y, cw, "Bees", "Bees", () -> MODEL.beeCount, v -> MODEL.beeCount = v,
                    0, () -> 99d, 1);
        }
        y += ROW;
        int half = (cw - 4) / 2;
        numPair(left, y, half, "Fly", "Flight", () -> MODEL.flight, v -> MODEL.flight = v,
                0, () -> 10d, 1);
        box(left + half + 4, y, half, "Loot table", MODEL.loot, v -> MODEL.loot = v);
        y += ROW;
        box(left, y, cw, "Sherds back,left,right,front (empty=none)", MODEL.sherds, v -> MODEL.sherds = v);
        y += ROW;
        box(left, y, half, "Player name", MODEL.headName, v -> MODEL.headName = v);
        cycle(left + half + 4, y, half, Components.INSTRUMENT_KEYS, Components.INSTRUMENT_LABELS,
                "Horn: ", () -> MODEL.instrument, v -> MODEL.instrument = v);
        y += ROW;
        suggest(left, y, cw, "Jukebox song (empty=none)", MODEL.song, songs(), v -> MODEL.song = v);
    }

    // ---- autocomplete dropdowns ---------------------------------------------------------

    private static List<SuggestBox.Entry> itemEntries;
    private static List<SuggestBox.Entry> mobEntries;
    private static List<SuggestBox.Entry> potionEntries;
    private static List<SuggestBox.Entry> effectEntries;
    private static List<SuggestBox.Entry> bannerEntries;
    private static List<SuggestBox.Entry> trimPatEntries;
    private static List<SuggestBox.Entry> songEntries;

    private static List<SuggestBox.Entry> entries(String[] ids, String kind) {
        List<SuggestBox.Entry> out = new ArrayList<>();
        for (String id : ids) out.add(new SuggestBox.Entry(id, kind));
        return out;
    }

    /** Every item in the game registry, in registry order. Never hardcoded. */
    private static List<SuggestBox.Entry> items() {
        if (itemEntries == null) {
            itemEntries = new ArrayList<>();
            for (Object key : BuiltInRegistries.ITEM.keySet()) {
                String path = pathOf(key);
                if (path.equals("air")) continue;
                itemEntries.add(new SuggestBox.Entry(path, "item"));
            }
        }
        return itemEntries;
    }

    /** "minecraft:stone" to "stone", without version-specific types. */
    private static String pathOf(Object key) {
        String s = key.toString();
        int i = s.indexOf(':');
        return i < 0 ? s : s.substring(i + 1);
    }

    private static Set<String> itemIdSet;

    private static boolean hasItem(String path) {
        try {
            if (itemIdSet == null) {
                itemIdSet = new HashSet<>();
                for (Object key : BuiltInRegistries.ITEM.keySet()) itemIdSet.add(key.toString());
            }
            return itemIdSet.contains("minecraft:" + path);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Eggless mobs borrow their drop (or named item) for the icon. */
    private static final String[][] MOB_DROPS = {
        {"wither", "nether_star"}, {"ender_dragon", "dragon_egg"},
        {"iron_golem", "iron_ingot"}, {"snow_golem", "snowball"},
        {"happy_ghast", "ghast_harness"}, {"ghastling", "dried_ghast"},
    };

    private static String mobIcon(String id) {
        if (hasItem(id + "_spawn_egg")) return "{id:\"minecraft:" + id + "_spawn_egg\",count:1}";
        if (hasItem(id)) return "{id:\"minecraft:" + id + "\",count:1}";
        for (String[] d : MOB_DROPS) {
            if (d[0].equals(id)) return "{id:\"minecraft:" + d[1] + "\",count:1}";
        }
        return null;
    }

    /** Every summonable entity in the game registry, in registry order. */
    private static List<SuggestBox.Entry> mobs() {
        if (mobEntries == null) {
            mobEntries = new ArrayList<>();
            for (Object key : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                String path = pathOf(key);
                if (path.equals("player")) continue;
                mobEntries.add(new SuggestBox.Entry(path, "mob", mobIcon(path)));
            }
        }
        return mobEntries;
    }

    /** Every potion in the game registry, in registry order. */
    private static List<SuggestBox.Entry> potions() {
        if (potionEntries == null) {
            potionEntries = new ArrayList<>();
            for (Object key : BuiltInRegistries.POTION.keySet()) {
                potionEntries.add(new SuggestBox.Entry(pathOf(key), "potion"));
            }
        }
        return potionEntries;
    }

    /** Every mob effect in the game registry, in registry order. */
    private static List<SuggestBox.Entry> effects() {
        if (effectEntries == null) {
            effectEntries = new ArrayList<>();
            for (Object key : BuiltInRegistries.MOB_EFFECT.keySet()) {
                effectEntries.add(new SuggestBox.Entry(pathOf(key), "effect"));
            }
        }
        return effectEntries;
    }

    private static List<SuggestBox.Entry> banners() {
        if (bannerEntries == null) bannerEntries = entries(Components.BANNER_IDS, "banner");
        return bannerEntries;
    }

    private static List<SuggestBox.Entry> trimPats() {
        if (trimPatEntries == null) trimPatEntries = entries(Components.TRIM_PATS, "template");
        return trimPatEntries;
    }

    private static List<SuggestBox.Entry> songs() {
        if (songEntries == null) songEntries = entries(Components.SONG_IDS, "disc");
        return songEntries;
    }

    /** A text box with an icon dropdown. Typing updates the model straight away. */
    private SuggestBox suggest(int x, int y, int w, String hint, String initial,
            List<SuggestBox.Entry> all, Consumer<String> set) {
        SuggestBox s = new SuggestBox(this.font, x, y, w, hint, initial, all, v -> {
            set.accept(v);
            refresh();
        });
        suggestBoxes.add(s);
        addRenderableWidget(s.box());
        return s;
    }

    private void closeOthers(SuggestBox keep) {
        for (SuggestBox s : suggestBoxes) {
            if (s != keep) s.close();
        }
    }

    // ---- autocomplete (Tab key) ---------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        for (SuggestBox s : suggestBoxes) {
            if (s.keyPressed(event.key())) return true;
        }
        if (event.key() == KEY_TAB && tryComplete()) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (SuggestBox s : suggestBoxes) {
            if (s.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Completes the component id being typed. True when a completion was made. */
    private boolean tryComplete() {
        if (customBox != null && customBox.isFocused()) {
            String frag = currentCustomFragment(customBox.getValue());
            if (frag == null || frag.isEmpty()) return false;
            List<String> s = Components.suggestComponents(frag, 1);
            if (s.isEmpty()) return false;
            String text = customBox.getValue();
            int cut = text.lastIndexOf(';');
            customBox.setValue((cut < 0 ? "" : text.substring(0, cut + 1) + " ") + s.get(0) + "=");
            refresh();
            return true;
        }
        return false;
    }

    /** The component id currently being typed after the last ;, or null when typing a value. */
    private static String currentCustomFragment(String text) {
        if (text == null) return null;
        List<String> parts = ItemSpec.splitTopLevel(text, ';');
        String last = parts.get(parts.size() - 1).trim();
        if (last.isEmpty()) return "";
        int eq = last.indexOf('=');
        if (eq >= 0) return null;
        return last;
    }

    // ---- actions ------------------------------------------------------------------------

    private void refresh() {
        tabHint = "";
        statusDetail = "";
        if (MODEL.id.isBlank()) {
            lastCommand = null;
            previewStack = null;
            previewNbt = null;
            statusText = "Start on the Item tab: type an item, or hold one and press Load held item.";
            statusColor = SOFT;
            return;
        }
        try {
            lastCommand = ItemSpec.giveCommand(MODEL, "@s");
            statusText = "Ready. The command is " + lastCommand.length() + " characters.";
            statusColor = GREEN;
        } catch (IllegalArgumentException e) {
            lastCommand = null;
            String msg = e.getMessage() == null ? "Check the fields." : e.getMessage();
            statusText = msg.length() > 100 ? msg.substring(0, 97) + "..." : msg;
            statusColor = RED;
        }
        if (lastCommand != null) {
            String problem = vanillaCheck();
            if (problem != null) {
                lastCommand = null;
                statusText = problem.length() > 100 ? problem.substring(0, 97) + "..." : problem;
                statusColor = RED;
            }
        }
        // Decoding for the preview icon is the slow part, so only redo it when the item changed.
        try {
            String nbt = lastCommand == null ? null : ItemSpec.itemNbt(MODEL);
            if (nbt == null ? previewNbt != null : !nbt.equals(previewNbt)) {
                previewNbt = nbt;
                previewStack = nbt == null ? null : Held.preview(nbt);
            }
        } catch (IllegalArgumentException e) {
            previewNbt = null;
            previewStack = null;
        }
        refreshMorePreviews();
        // What would Tab complete right now?
        if (customBox != null && customBox.isFocused()) {
            String frag = currentCustomFragment(customBox.getValue());
            if (frag != null && !frag.isEmpty()) {
                List<String> s = Components.suggestComponents(frag, 3);
                if (!s.isEmpty()) tabHint = "Tab completes: " + String.join(", ", s);
            } else if (frag != null) {
                tabHint = "Type a component id, Tab completes it. The Pick button lists them all.";
            }
        }
        // Messages from the picture picker arrive after its screen closes.
        if (!pendingNote.isEmpty()) {
            statusText = pendingNote;
            statusColor = pendingColor;
            pendingNote = "";
        }
    }

    private void say(String text, int color) {
        statusText = text;
        statusDetail = "";
        statusColor = color;
    }

    /** A message with a detail (like a folder path) on its own line below. */
    private void sayLong(String text, String detail, int color) {
        statusText = text;
        statusDetail = detail == null ? "" : detail;
        statusColor = color;
    }

    private void loadHeld() {
        try {
            String snbt = Held.snbt();
            MODEL.reset();
            List<String> warnings = new ArrayList<>(ItemLoader.load(MODEL, snbt));
            enchPage = 0;
            attrPage = 0;
            rebuildWidgets();
            if (warnings.isEmpty()) {
                say("Loaded the held item.", GREEN);
            } else {
                say("Loaded with " + warnings.size() + " note(s): " + warnings.get(0), SOFT);
            }
        } catch (IllegalArgumentException e) {
            say(e.getMessage() == null ? "Could not read the held item." : e.getMessage(), RED);
        } catch (RuntimeException e) {
            say("Could not read the held item: " + e.getMessage(), RED);
        }
    }

    private void applyHeld() {
        refresh();
        if (MODEL.id.isBlank()) return;
        try {
            String cmd = ItemSpec.replaceHeldCommand(MODEL);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // Needs cheats on or operator permission, like /give.
                mc.player.connection.sendCommand(cmd);
            }
            this.onClose();
        } catch (IllegalArgumentException e) {
            say(e.getMessage() == null ? "Check the fields." : e.getMessage(), RED);
        }
    }

    private void give() {
        refresh();
        if (lastCommand == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(lastCommand);
        }
        this.onClose();
    }

    private void copy() {
        refresh();
        if (lastCommand == null) return;
        Minecraft.getInstance().keyboardHandler.setClipboard("/" + lastCommand);
        say("Copied the /give command to your clipboard.", GREEN);
    }

    // ---- drawing ------------------------------------------------------------------------

    private void drawStyled(GuiGraphics g, String text, int x, int y, ItemModel.ColorStyle s) {
        if (text.isEmpty()) {
            g.drawString(this.font, "(nothing yet)", x, y, SOFT);
            return;
        }
        StringBuilder codes = new StringBuilder();
        if (s.bold) codes.append("§l");
        if (s.underline) codes.append("§n");
        if (s.strikethrough) codes.append("§m");
        if (s.italic) codes.append("§o");
        String pre = codes.toString();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            int rgb = switch (s.mode) {
                case ItemModel.ColorStyle.SOLID -> s.c1;
                case ItemModel.ColorStyle.GRADIENT -> Colors.lerp(s.c1, s.c2, n == 1 ? 0f : i / (float) (n - 1));
                default -> 0xFFFFFF;
            };
            String ch = String.valueOf(text.charAt(i));
            g.drawString(this.font, pre + ch, x, y, 0xFF000000 | rgb);
            x += this.font.width(pre + ch);
        }
    }

    private void drawWheel(GuiGraphics g) {
        for (int dy = -wheelR; dy < wheelR; dy += 2) {
            for (int dx = -wheelR; dx < wheelR; dx += 2) {
                double cx = dx + 1;
                double cy = dy + 1;
                double dist = Math.sqrt(cx * cx + cy * cy);
                if (dist > wheelR) continue;
                float h = (float) ((Math.atan2(cy, cx) / (2 * Math.PI) + 1.0) % 1.0);
                int rgb = Colors.hsvToRgb(h, (float) (dist / wheelR), val);
                g.fill(wheelCx + dx, wheelCy + dy, wheelCx + dx + 2, wheelCy + dy + 2, 0xFF000000 | rgb);
            }
        }
        int mx = wheelCx + (int) Math.round(Math.cos(hue * 2 * Math.PI) * sat * wheelR);
        int my = wheelCy + (int) Math.round(Math.sin(hue * 2 * Math.PI) * sat * wheelR);
        g.fill(mx - 3, my - 3, mx + 4, my + 4, 0xFFFFFFFF);
        g.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFF000000 | Colors.hsvToRgb(hue, sat, val));

        for (int y = 0; y < barH; y += 2) {
            float v = 1f - (y + 1) / (float) barH;
            g.fill(barX, barTop + y, barX + 8, barTop + y + 2, 0xFF000000 | Colors.hsvToRgb(hue, sat, v));
        }
        int vy = barTop + Math.round((1f - val) * (barH - 2));
        g.fill(barX - 3, vy - 1, barX + 11, vy + 3, 0xFFFFFFFF);
        g.fill(barX - 2, vy, barX + 10, vy + 2, 0xFF000000);
    }

    /** Draws lore picture rows, stopping before the footer. Returns the height after the last row. */
    private int drawPicture(GuiGraphics g, int x, int y) {
        int adv = Math.max(4, this.font.width("█"));
        int rows = MODEL.picture.size();
        for (int r = 0; r < rows; r++) {
            if (!fits(y)) {
                if (fits(y - 2)) g.drawString(this.font, "+" + (rows - r) + " more rows", x, y, SOFT);
                break;
            }
            int cx = x;
            for (int px : MODEL.picture.get(r)) {
                g.drawString(this.font, "█", cx, y, 0xFF000000 | (px & 0xFFFFFF));
                cx += adv;
            }
            y += 10;
        }
        return y;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 8, WHITE);
        drawPreview(g, mouseX, mouseY);

        switch (tab) {
            case 0 -> {
                g.drawString(this.font, "Item id", left, top, WHITE);
                g.drawString(this.font, "Count", left + cw - 136, top, WHITE);
                // Every line below is guarded, so short windows show fewer hints instead of overlap.
                if (fits(top + 168)) {
                    g.drawString(this.font, "1. Pick an item above. 2. Style it on the next tabs. 3. Give to me.",
                            left, top + 168, SOFT);
                }
                if (fits(top + 179)) {
                    g.drawString(this.font, "Give and Apply need cheats (singleplayer) or operator (server).",
                            left, top + 179, SOFT);
                }
                if (!MODEL.extras.isEmpty() && fits(top + 190)) {
                    g.drawString(this.font, MODEL.extras.size() + " other component(s) from the loaded item are kept as they are.",
                            left, top + 190, SOFT);
                }
            }
            case 1 -> {
                g.drawString(this.font, "Custom name", left, top, WHITE);
                g.drawString(this.font, "Lore lines (separate with |)", left, top + 36, WHITE);
                g.drawString(this.font, "Decorations for the " + (target < 2 ? "name" : "lore") + ":",
                        left, top + 122, WHITE);
                g.drawString(this.font, "Preview", left, top + 182, WHITE);
                drawStyled(g, MODEL.name, left, top + 194, MODEL.nameStyle);
                String firstLore = MODEL.lore.split("\\|", -1)[0].trim();
                drawStyled(g, firstLore, left, top + 206, MODEL.loreStyle);
                if (!MODEL.picture.isEmpty()) drawPicture(g, left, top + 218);
                else if (fits(top + 218)) {
                    g.drawString(this.font, "Add picture... turns an image into colored lore rows.", left, top + 218, SOFT);
                }
                drawWheel(g);
                g.fill(swatchX - 1, swatchY - 1, swatchX + 21, swatchY + 21, 0xFFFFFFFF);
                g.fill(swatchX, swatchY, swatchX + 20, swatchY + 20, 0xFF000000 | targetColor());
            }
            case 2 -> {
                if (MODEL.enchants.isEmpty()) {
                    g.drawString(this.font, "No enchantments yet. Press + Add enchantment, then Pick one.", left, top + 34, SOFT);
                }
                if (fits(limit + 8)) {
                    g.drawString(this.font, "Any level from 1 to 255 works. Empty uses the vanilla maximum.", left, limit + 8, SOFT);
                }
                pageLabel(g, enchPage, MODEL.enchants.size());
            }
            case 3 -> {
                if (MODEL.attrs.isEmpty()) {
                    g.drawString(this.font, "No attributes yet. Press + Add attribute, then Pick one.", left, top + 34, SOFT);
                }
                if (fits(limit + 8)) {
                    g.drawString(this.font, "Click the Add / slot buttons to cycle through the options.", left, limit + 8, SOFT);
                }
                pageLabel(g, attrPage, MODEL.attrs.size());
            }
            case 4 -> {
                g.drawString(this.font, "Food: set Nutrition to make anything edible. The rest is optional.", left, top, WHITE);
                if (fits(top + 112)) {
                    g.drawString(this.font, "Drag a slider or type the box. Empty keeps the default.", left, top + 112, SOFT);
                }
                if (fits(top + 123)) {
                    g.drawString(this.font, "Equip: pick a slot to make it wearable. Remainder leaves an item behind.", left, top + 123, SOFT);
                }
            }
            case 5 -> {
                g.drawString(this.font, "Contents, spawners and anything else. Empty boxes are skipped.", left, top, WHITE);
                // The boxes end at top+200, so every hint starts below them: never on top of a field.
                int y = top + 204;
                if (MODEL.rawContainer != null) {
                    if (fits(y)) {
                        g.drawString(this.font, "Contents are too complex to show as text and are kept as they are.", left, y, SOFT);
                    }
                } else if (fits(y)) {
                    g.drawString(this.font, "Hold the item to store and press Add hand item. Slots number from 0.", left, y, SOFT);
                }
                if (fits(y + 11)) {
                    g.drawString(this.font, "Spawner fields need a spawner item. Placing one needs operator.", left, y + 11, SOFT);
                }
                int hintY = Math.max(limit + 8, top + 228);
                if (fits(hintY)) {
                    if (!tabHint.isEmpty()) {
                        g.drawString(this.font, tabHint, left, hintY, GREEN);
                    } else {
                        g.drawString(this.font, "Custom covers every other component. Pick lists them, Tab completes.", left, hintY, SOFT);
                    }
                }
            }
            default -> {
                buildMoreHint(g);
                drawMoreExtras(g, mouseX, mouseY);
            }
        }
        drawStatus(g);
        for (SuggestBox s : suggestBoxes) s.render(g, this.font, this.width, this.height);
    }

    /**
     * The status line(s), centered in the content and never wider than it:
     * the message wraps to two lines at most, with the detail (a folder path
     * or the like) on its own line below.
     */
    private void drawStatus(GuiGraphics g) {
        List<net.minecraft.util.FormattedCharSequence> lines =
                this.font.split(Component.literal(statusText), cw);
        int show = Math.min(2, lines.size());
        int y = statusY - (show - 1) * 11 - (statusDetail.isEmpty() ? 0 : 11);
        for (int i = 0; i < show; i++) {
            int w = this.font.width(lines.get(i));
            g.drawString(this.font, lines.get(i), left + (cw - w) / 2, y + i * 11, statusColor);
        }
        if (!statusDetail.isEmpty()) {
            String detail = statusDetail;
            while (!detail.isEmpty() && this.font.width(detail) > cw) {
                detail = detail.substring(0, detail.length() - 1);
            }
            if (detail.length() < statusDetail.length() && !detail.isEmpty()) {
                detail = detail.substring(0, detail.length() - 1) + "...";
            }
            int w = this.font.width(detail);
            g.drawString(this.font, detail, left + (cw - w) / 2, y + show * 11, statusColor);
        }
    }

    /** Icon strips, mini previews and section previews for the More tab. */
    private void drawMoreExtras(GuiGraphics g, int mouseX, int mouseY) {
        if (tab != 6) return;
        if (moreSub == 1) {
            for (int[] geom : bannerRowGeom) {
                if (geom[2] < 0 || geom[2] >= bannerIcons.size()) continue;
                ItemStack icon = bannerIcons.get(geom[2]);
                if (icon != null && !icon.isEmpty()) g.renderItem(icon, geom[0], geom[1]);
            }
            drawBigPreview(g, mouseX, mouseY, bannerPreview);
        } else if (moreSub == 2) {
            for (int i = 0; i < TRIM_INGREDIENTS.length && i < matIcons.size(); i++) {
                ItemStack icon = matIcons.get(i);
                int x = matStripX + i * matCellW;
                if (icon != null && !icon.isEmpty() && matCellW >= 16) {
                    g.renderItem(icon, x + (matCellW - 16) / 2, matStripY + 2);
                }
                if (TRIM_INGREDIENTS[i][0].equals(normId(MODEL.trimMaterial))) {
                    outline(g, x, matStripY - 1, matCellW, 22, 0xFFFFFFFF);
                }
            }
            for (int i = 0; i < PIECES.length && i < pieceIcons.size(); i++) {
                ItemStack icon = pieceIcons.get(i);
                int x = pieceStripX + i * 20;
                if (icon != null && !icon.isEmpty()) g.renderItem(icon, x + 1, pieceStripY + 2);
                if (PIECES[i].equals(normId(MODEL.trimPiece))) {
                    outline(g, x - 1, pieceStripY - 1, 20, 22, 0xFFFFFFFF);
                }
            }
            if (trimTemplateIcon != null && !trimTemplateIcon.isEmpty()) {
                g.renderItem(trimTemplateIcon, trimIconX, trimIconY);
            }
            drawStandPreview(g, mouseX, mouseY);
        } else if (moreSub == 0) {
            drawBigPreview(g, mouseX, mouseY, potionPreview);
        }
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** The armor stand wearing the preview armor, turning with the cursor. */
    private void drawStandPreview(GuiGraphics g, int mouseX, int mouseY) {
        int px = morePrevX;
        int py = morePrevY;
        g.fill(px - 2, py - 2, px + 50, py + 50, 0xFF000000);
        g.fill(px, py, px + 48, py + 48, 0xFF1A1A1A);
        if (previewStand != null) {
            g.enableScissor(px - 2, py - 2, px + 50, py + 50);
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, py, px + 48, py + 48, 20,
                        0.0625f, (float) mouseX, (float) mouseY, previewStand);
            } finally {
                g.disableScissor();
            }
        }
        if (armorPreview != null && !armorPreview.isEmpty()
                && mouseX >= px - 2 && mouseX < px + 50 && mouseY >= py - 2 && mouseY < py + 50) {
            g.setTooltipForNextFrame(this.font, armorPreview, mouseX, mouseY);
        }
    }

    /** A 48px live preview of what the section is making, with tooltip on hover. */
    private void drawBigPreview(GuiGraphics g, int mouseX, int mouseY, ItemStack stack) {
        int px = morePrevX;
        int py = morePrevY;
        g.fill(px - 2, py - 2, px + 50, py + 50, 0xFF000000);
        g.fill(px, py, px + 48, py + 48, 0xFF1A1A1A);
        if (stack == null || stack.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().scale(3, 3);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
        if (mouseX >= px - 2 && mouseX < px + 50 && mouseY >= py - 2 && mouseY < py + 50) {
            g.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
        }
    }

    /** The live item preview in the top-right corner, with its tooltip on hover. */
    private void drawPreview(GuiGraphics g, int mouseX, int mouseY) {
        int px = this.width - 26;
        int py = 2;
        if (previewStack != null && !previewStack.isEmpty()) {
            g.renderItem(previewStack, px, py);
            g.renderItemDecorations(this.font, previewStack, px, py);
            if (mouseX >= px - 2 && mouseX <= px + 18 && mouseY >= py - 2 && mouseY <= py + 18) {
                g.setTooltipForNextFrame(this.font, previewStack, mouseX, mouseY);
            }
        } else if (!MODEL.id.isBlank()) {
            g.drawString(this.font, "?", px + 5, py + 4, SOFT);
        }
    }

    private void pageLabel(GuiGraphics g, int page, int count) {
        int per = rowsPerPage();
        int pages = Math.max(1, (count + per - 1) / per);
        if (pages > 1) {
            String t = (page + 1) + "/" + pages;
            g.drawString(this.font, t, left + cw - 68 - this.font.width(t) - 4, top + 6, SOFT);
        }
    }

    /** Captions for the More tab, one per section, kept clear of the fields. */
    private void buildMoreHint(GuiGraphics g) {
        String caption = switch (moreSub) {
            case 0 -> "Potion for bottles and tipped arrows. Effects add more.";
            case 1 -> "Banner layers for shields and banners, top first.";
            case 2 -> "Trim and dye for armor and leather.";
            default -> "Beehives, fireworks, loot, pots, heads, horns and songs.";
        };
        g.drawString(this.font, caption, left, top, WHITE);
        if (morePages > 1 && fits(limit + 8)) {
            String t = morePage + "/" + morePages;
            g.drawString(this.font, t, left + cw - this.font.width(t), limit + 8, SOFT);
        }
        // Each section's fields end at a known height; hints start below them.
        int contentEnd = switch (moreSub) {
            case 2 -> top + 236;
            case 3 -> top + 276;
            default -> limit - 16;
        };
        int hintY = Math.max(limit + 8, contentEnd + 8);
        if (!fits(hintY)) return;
        String hint = switch (moreSub) {
            case 0 -> "Levels 0-4 are vanilla-like. Duration is in seconds.";
            case 1 -> "Base paints shields. Layers stack in order.";
            case 2 -> "Sliders mix red, green, blue. The box takes #rrggbb too.";
            default -> "Bees fill a hive. Sherds: back,left,right,front.";
        };
        g.drawString(this.font, hint, left, hintY, SOFT);
    }
}
