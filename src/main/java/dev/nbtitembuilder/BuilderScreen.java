package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The in-game item builder. Open it with the /nbtbuilder command. */
public class BuilderScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xFFDDDDDD;
    private static final int GREEN = 0xFF7FF2A0;
    private static final int RED = 0xFFFF8080;

    private static final String[] TABS = {"Item", "Style", "Enchants", "Attributes", "Contents"};
    private static final String[] MODE_NAMES = {"Default", "Solid", "Gradient"};
    private static final int ROW = 24;

    // Kept between openings so the screen reopens where you left it. It starts empty.
    private static final ItemModel MODEL = new ItemModel();
    private static int tab = 0;
    private static int target = 0; // 0 name, 1 name end, 2 lore, 3 lore end
    private static float hue = 0.1f;
    private static float sat = 1f;
    private static float val = 1f;
    private static int enchPage = 0;
    private static int attrPage = 0;

    private int cw;
    private int left;
    private int top;
    private int limit;
    private Button nameModeButton;
    private Button loreModeButton;
    private EditBox hexBox;
    private boolean updatingHex;
    private String lastCommand;
    private String statusText = "";
    private int statusColor = SOFT;

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

    public BuilderScreen() {
        super(Component.literal("NBT Item Builder"));
    }

    // ---- layout -------------------------------------------------------------------------

    @Override
    protected void init() {
        cw = Math.min(this.width - 24, 460);
        left = (this.width - cw) / 2;
        top = 52;
        limit = this.height - 84;

        int gap = 4;
        int tw = (cw - gap * (TABS.length - 1)) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            String text = i == tab ? "[" + TABS[i] + "]" : TABS[i];
            addRenderableWidget(Button.builder(Component.literal(text), b -> {
                tab = index;
                rebuildWidgets();
            }).bounds(left + i * (tw + gap), 22, tw, 20).build());
        }

        switch (tab) {
            case 0 -> buildItemTab();
            case 1 -> buildStyleTab();
            case 2 -> buildEnchantTab();
            case 3 -> buildAttributeTab();
            default -> buildContentsTab();
        }
        buildBottom();
        refresh();
    }

    private void buildBottom() {
        int gap = 4;
        int y1 = this.height - 50;
        int w3 = (cw - 2 * gap) / 3;
        addRenderableWidget(Button.builder(Component.literal("Load held item"), b -> loadHeld())
                .bounds(left, y1, w3, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Apply to held item"), b -> applyHeld())
                .bounds(left + w3 + gap, y1, w3, 20).build());
        addRenderableWidget(Button.builder(Component.literal("View NBT"), b -> {
            Minecraft.getInstance().setScreen(new NbtViewScreen(this, MODEL));
        }).bounds(left + 2 * (w3 + gap), y1, cw - 2 * (w3 + gap), 20).build());

        int y2 = this.height - 26;
        int w4 = (cw - 3 * gap) / 4;
        addRenderableWidget(Button.builder(Component.literal("Give"), b -> give())
                .bounds(left, y2, w4, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Copy command"), b -> copy())
                .bounds(left + (w4 + gap), y2, w4, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            MODEL.reset();
            enchPage = 0;
            attrPage = 0;
            rebuildWidgets();
        }).bounds(left + 2 * (w4 + gap), y2, w4, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(left + 3 * (w4 + gap), y2, cw - 3 * (w4 + gap), 20).build());
    }

    private EditBox box(int x, int y, int w, String hint, String value, Consumer<String> onChange) {
        EditBox b = new EditBox(this.font, x, y, w, 20, Component.literal(hint));
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

    // ---- Item tab -----------------------------------------------------------------------

    private void buildItemTab() {
        box(left, top + 12, cw - 54, "diamond_sword", MODEL.id, v -> MODEL.id = v);
        box(left + cw - 50, top + 12, 50, "1", MODEL.count, v -> MODEL.count = v);
        addRenderableWidget(Button.builder(unbreakableLabel(), b -> {
            MODEL.unbreakable = !MODEL.unbreakable;
            b.setMessage(unbreakableLabel());
            refresh();
        }).bounds(left, top + 44, cw, 20).build());
    }

    private Component unbreakableLabel() {
        return Component.literal("Unbreakable: " + (MODEL.unbreakable ? "yes" : "no"));
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
        addRenderableWidget(Button.builder(Component.literal("Remove italics: " + (MODEL.noItalic ? "yes" : "no")), b -> {
            MODEL.noItalic = !MODEL.noItalic;
            b.setMessage(Component.literal("Remove italics: " + (MODEL.noItalic ? "yes" : "no")));
            refresh();
        }).bounds(left, top + 122, lw, 20).build());

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
        return Math.max(1, (limit - (top + 26)) / ROW);
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

        int idW = cw - 20 - 60 - 20 - 12;
        for (int r = 0; r < per; r++) {
            int i = enchPage * per + r;
            if (i >= n) break;
            final ItemModel.Ench e = MODEL.enchants.get(i);
            int y = top + 26 + r * ROW;
            box(left, y, idW, "enchantment", e.id, v -> e.id = v);
            addRenderableWidget(Button.builder(Component.literal("v"), b -> {
                Minecraft.getInstance().setScreen(new PickerScreen(this, "Choose an enchantment",
                        ItemSpec.enchantOptions(), id -> e.id = id));
            }).bounds(left + idW + 4, y, 20, 20).build());
            int max = e.id.isBlank() ? 0 : ItemSpec.maxLevel(e.id);
            box(left + idW + 28, y, 60, max > 0 ? "max " + max : "level", e.level, v -> e.level = v);
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                MODEL.enchants.remove(e);
                rebuildWidgets();
            }).bounds(left + cw - 20, y, 20, 20).build());
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

        int amtW = 46;
        int opW = 74;
        int slotW = 70;
        int idW = Math.max(60, cw - (20 + amtW + opW + slotW + 20) - 5 * 4);
        for (int r = 0; r < per; r++) {
            int i = attrPage * per + r;
            if (i >= n) break;
            final ItemModel.Attr a = MODEL.attrs.get(i);
            int y = top + 26 + r * ROW;
            int x = left;
            box(x, y, idW, "attribute", a.id, v -> a.id = v);
            x += idW + 4;
            addRenderableWidget(Button.builder(Component.literal("v"), b -> {
                Minecraft.getInstance().setScreen(new PickerScreen(this, "Choose an attribute",
                        ItemSpec.attributeOptions(), id -> a.id = id));
            }).bounds(x, y, 20, 20).build());
            x += 24;
            box(x, y, amtW, "amount", a.amount, v -> a.amount = v);
            x += amtW + 4;
            addRenderableWidget(Button.builder(Component.literal(ItemSpec.OP_LABELS[indexOf(ItemSpec.OP_KEYS, a.op)]), b -> {
                int next = (indexOf(ItemSpec.OP_KEYS, a.op) + 1) % ItemSpec.OP_KEYS.length;
                a.op = ItemSpec.OP_KEYS[next];
                b.setMessage(Component.literal(ItemSpec.OP_LABELS[next]));
                refresh();
            }).bounds(x, y, opW, 20).build());
            x += opW + 4;
            addRenderableWidget(Button.builder(Component.literal(ItemSpec.SLOT_LABELS[indexOf(ItemSpec.SLOT_KEYS, a.slot)]), b -> {
                int next = (indexOf(ItemSpec.SLOT_KEYS, a.slot) + 1) % ItemSpec.SLOT_KEYS.length;
                a.slot = ItemSpec.SLOT_KEYS[next];
                b.setMessage(Component.literal(ItemSpec.SLOT_LABELS[next]));
                refresh();
            }).bounds(x, y, slotW, 20).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                MODEL.attrs.remove(a);
                rebuildWidgets();
            }).bounds(left + cw - 20, y, 20, 20).build());
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

    // ---- Contents tab -------------------------------------------------------------------

    private void buildContentsTab() {
        if (MODEL.rawContainer != null) {
            addRenderableWidget(Button.builder(Component.literal("Clear contents"), b -> {
                MODEL.rawContainer = null;
                MODEL.contents = "";
                rebuildWidgets();
            }).bounds(left, top + 40, cw, 20).build());
        } else {
            box(left, top + 12, cw, "netherite_pickaxe efficiency=5; torch x64", MODEL.contents, v -> MODEL.contents = v);
        }
    }

    // ---- actions ------------------------------------------------------------------------

    private void refresh() {
        if (MODEL.id.isBlank()) {
            lastCommand = null;
            statusText = "Empty. Type an item id on the Item tab, or hold an item and press Load held item.";
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
    }

    private void say(String text, int color) {
        statusText = text;
        statusColor = color;
    }

    private void loadHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) {
            say("Your main hand is empty. Hold the item you want to edit first.", RED);
            return;
        }
        try {
            Tag tag = ItemStack.CODEC
                    .encodeStart(mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack)
                    .result().orElse(null);
            if (tag == null) {
                say("Could not read the held item.", RED);
                return;
            }
            MODEL.reset();
            List<String> warnings = new ArrayList<>(ItemLoader.load(MODEL, tag.toString()));
            enchPage = 0;
            attrPage = 0;
            rebuildWidgets();
            if (warnings.isEmpty()) {
                say("Loaded the held item.", GREEN);
            } else {
                String w = warnings.get(0);
                say("Loaded with " + warnings.size() + " note(s): " + w, SOFT);
            }
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
        int n = text.length();
        for (int i = 0; i < n; i++) {
            int rgb = switch (s.mode) {
                case ItemModel.ColorStyle.SOLID -> s.c1;
                case ItemModel.ColorStyle.GRADIENT -> Colors.lerp(s.c1, s.c2, n == 1 ? 0f : i / (float) (n - 1));
                default -> 0xFFFFFF;
            };
            String ch = String.valueOf(text.charAt(i));
            g.drawString(this.font, ch, x, y, 0xFF000000 | rgb);
            x += this.font.width(ch);
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 8, WHITE);

        switch (tab) {
            case 0 -> {
                g.drawString(this.font, "Item id", left, top, WHITE);
                g.drawString(this.font, "Count", left + cw - 50, top, WHITE);
                int y = top + 76;
                g.drawString(this.font, "The builder starts empty. To edit an item you already have, hold it", left, y, SOFT);
                g.drawString(this.font, "and press Load held item. Apply to held item swaps it for your changes.", left, y + 11, SOFT);
                if (!MODEL.extras.isEmpty()) {
                    g.drawString(this.font, MODEL.extras.size() + " other component(s) from the loaded item are kept as they are.",
                            left, y + 28, SOFT);
                }
            }
            case 1 -> {
                g.drawString(this.font, "Custom name", left, top, WHITE);
                g.drawString(this.font, "Lore lines (separate with |)", left, top + 36, WHITE);
                g.drawString(this.font, "Preview", left, top + 148, WHITE);
                drawStyled(g, MODEL.name, left, top + 160, MODEL.nameStyle);
                String firstLore = MODEL.lore.split("\\|", -1)[0].trim();
                drawStyled(g, firstLore, left, top + 172, MODEL.loreStyle);
                drawWheel(g);
                g.fill(swatchX - 1, swatchY - 1, swatchX + 21, swatchY + 21, 0xFFFFFFFF);
                g.fill(swatchX, swatchY, swatchX + 20, swatchY + 20, 0xFF000000 | targetColor());
            }
            case 2 -> {
                if (MODEL.enchants.isEmpty()) {
                    g.drawString(this.font, "No enchantments yet. Press + Add enchantment.", left, top + 34, SOFT);
                }
                g.drawString(this.font, "Any level from 1 to 255 works. Empty uses the vanilla maximum.", left, limit + 8, SOFT);
                pageLabel(g, enchPage, MODEL.enchants.size());
            }
            case 3 -> {
                if (MODEL.attrs.isEmpty()) {
                    g.drawString(this.font, "No attributes yet. Press + Add attribute.", left, top + 34, SOFT);
                }
                g.drawString(this.font, "Click the Add / slot buttons to cycle through the options.", left, limit + 8, SOFT);
                pageLabel(g, attrPage, MODEL.attrs.size());
            }
            default -> {
                g.drawString(this.font, "Contents (barrels, chests, shulker boxes)", left, top, WHITE);
                if (MODEL.rawContainer != null) {
                    g.drawString(this.font, "This item's contents are too complex to show as text.", left, top + 12, SOFT);
                    g.drawString(this.font, "They are kept as they are. Clear them to type new contents.", left, top + 24, SOFT);
                } else {
                    int y = top + 40;
                    g.drawString(this.font, "item [xN] [@slot] [name=\"Text\"] [enchant=level] [unbreakable]", left, y, SOFT);
                    g.drawString(this.font, "Separate items with ;   Slots 0 to 26, up to 27 items.", left, y + 11, SOFT);
                    g.drawString(this.font, "Example: netherite_pickaxe efficiency=5 unbreaking=3; torch x64", left, y + 22, SOFT);
                }
            }
        }
        g.drawString(this.font, statusText, left, this.height - 62, statusColor);
    }

    private void pageLabel(GuiGraphics g, int page, int count) {
        int per = rowsPerPage();
        int pages = Math.max(1, (count + per - 1) / per);
        if (pages > 1) {
            String t = (page + 1) + "/" + pages;
            g.drawString(this.font, t, left + cw - 68 - this.font.width(t) - 4, top + 6, SOFT);
        }
    }
}
