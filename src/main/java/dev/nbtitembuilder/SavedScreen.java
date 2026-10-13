package dev.nbtitembuilder;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The saved items list: save, load, give, rename, reorder and delete. */
public class SavedScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xFFDDDDDD;
    private static final int GREEN = 0xFF7FF2A0;
    private static final int RED = 0xFFFF8080;
    private static final int ROW = 24;

    private static int page = 0;
    private static int selected = -1;
    private static String newName = "";

    private final Screen parent;
    private int cw;
    private int left;
    private int listTop;
    private int listBottom;
    private int confirmDelete = -1;
    private String statusText = "";
    private int statusColor = SOFT;

    /** parent is the screen to return to, or null to close back to the game. */
    public SavedScreen(Screen parent) {
        super(Component.literal("Saved items"));
        this.parent = parent;
        String problem = SavedItems.get().problem();
        if (problem != null) {
            statusText = problem;
            statusColor = RED;
        }
    }

    @Override
    protected void init() {
        cw = Math.min(this.width - 24, 460);
        left = (this.width - cw) / 2;
        listTop = 76;
        listBottom = this.height - 70;

        int gap = 4;
        // Name box and the two save buttons.
        EditBox name = new EditBox(this.font, left, 34, cw, 20, Component.literal("Name"));
        name.setMaxLength(40);
        name.setTextColor(0xFFFFFFFF);
        name.setHint(Component.literal("Name (for saving or renaming)").withStyle(ChatFormatting.GRAY));
        name.setValue(newName);
        name.setResponder(v -> newName = v);
        addRenderableWidget(name);

        int half = (cw - gap) / 2;
        addRenderableWidget(Button.builder(Component.literal("Save builder item"), b -> saveBuilder())
                .bounds(left, 56, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Save held item"), b -> saveHeld())
                .bounds(left + half + gap, 56, cw - half - gap, 18).build());

        List<SavedItems.Entry> list = SavedItems.get().list();
        int per = rowsPerPage();
        int pages = Math.max(1, (list.size() + per - 1) / per);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        int loadW = 46;
        int giveW = 40;
        int delW = 44;
        int nameW = cw - loadW - giveW - delW - 3 * gap;
        for (int r = 0; r < per; r++) {
            int i = page * per + r;
            if (i >= list.size()) break;
            final int index = i;
            SavedItems.Entry e = list.get(i);
            int y = listTop + r * ROW;
            String label = e.name + "  (" + SavedItems.describe(e.nbt) + ")";
            if (i == selected) label = "> " + label;
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                selected = selected == index ? -1 : index;
                confirmDelete = -1;
                rebuildWidgets();
            }).bounds(left, y, nameW, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Load"), b -> load(index))
                    .bounds(left + nameW + gap, y, loadW, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Give"), b -> give(index))
                    .bounds(left + nameW + loadW + 2 * gap, y, giveW, 20).build());
            String del = confirmDelete == i ? "Sure?" : "×";
            addRenderableWidget(Button.builder(Component.literal(del), b -> {
                if (confirmDelete == index) {
                    SavedItems.get().remove(index);
                    confirmDelete = -1;
                    selected = -1;
                } else {
                    confirmDelete = index;
                }
                rebuildWidgets();
            }).bounds(left + nameW + loadW + giveW + 3 * gap, y, delW, 20).build());
        }

        // Row for the selected item, then paging and close.
        int y1 = this.height - 50;
        int w3 = (cw - 2 * gap) / 3;
        addRenderableWidget(Button.builder(Component.literal("Rename selected"), b -> rename())
                .bounds(left, y1, w3, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Move up"), b -> move(-1))
                .bounds(left + w3 + gap, y1, w3, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Move down"), b -> move(1))
                .bounds(left + 2 * (w3 + gap), y1, cw - 2 * (w3 + gap), 20).build());

        int y2 = this.height - 26;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page--;
            confirmDelete = -1;
            rebuildWidgets();
        }).bounds(left, y2, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            confirmDelete = -1;
            rebuildWidgets();
        }).bounds(left + 34, y2, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
                .bounds(left + cw - 100, y2, 100, 20).build());
    }

    private int rowsPerPage() {
        return Math.max(1, (listBottom - listTop) / ROW);
    }

    private void say(String text, int color) {
        statusText = text;
        statusColor = color;
    }

    private String chosenName(String fallback) {
        return newName.isBlank() ? fallback : newName;
    }

    private void saveBuilder() {
        ItemModel m = BuilderScreen.model();
        if (m.id.isBlank()) {
            say("The builder is empty. Fill it in or load an item first.", RED);
            return;
        }
        try {
            String fallback = m.name.isBlank() ? m.id : m.name;
            String used = SavedItems.get().add(chosenName(fallback), ItemSpec.itemNbt(m));
            afterSave(used);
        } catch (IllegalArgumentException e) {
            say(e.getMessage() == null ? "Check the builder fields." : e.getMessage(), RED);
        }
    }

    private void saveHeld() {
        try {
            String nbt = Held.snbt();
            String fallback = SavedItems.describe(nbt).replaceAll(" x\\d+$", "");
            afterSave(SavedItems.get().add(chosenName(fallback), nbt));
        } catch (IllegalArgumentException e) {
            say(e.getMessage() == null ? "Could not read the held item." : e.getMessage(), RED);
        }
    }

    private void afterSave(String used) {
        newName = "";
        confirmDelete = -1;
        List<SavedItems.Entry> list = SavedItems.get().list();
        selected = list.size() - 1;
        page = Math.max(0, selected / rowsPerPage());
        rebuildWidgets();
        String problem = SavedItems.get().problem();
        if (problem != null) say(problem, RED);
        else say("Saved as \"" + used + "\".", GREEN);
    }

    private void load(int index) {
        List<SavedItems.Entry> list = SavedItems.get().list();
        if (index < 0 || index >= list.size()) return;
        SavedItems.intoModel(list.get(index).nbt, BuilderScreen.model());
        BuilderScreen.resetPages();
        Minecraft.getInstance().setScreen(new BuilderScreen());
    }

    private void give(int index) {
        List<SavedItems.Entry> list = SavedItems.get().list();
        if (index < 0 || index >= list.size()) return;
        try {
            String cmd = SavedItems.giveCommand(list.get(index).nbt);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // Runs /give as you, so it needs cheats on or operator permission.
                mc.player.connection.sendCommand(cmd);
            }
            this.onClose();
        } catch (IllegalArgumentException e) {
            say(e.getMessage() == null ? "Could not build the command." : e.getMessage(), RED);
        }
    }

    private void rename() {
        if (selected < 0 || selected >= SavedItems.get().list().size()) {
            say("Click an item in the list first.", RED);
            return;
        }
        if (newName.isBlank()) {
            say("Type the new name in the box first.", RED);
            return;
        }
        String used = SavedItems.get().rename(selected, newName);
        newName = "";
        rebuildWidgets();
        say("Renamed to \"" + used + "\".", GREEN);
    }

    private void move(int delta) {
        if (selected < 0 || selected >= SavedItems.get().list().size()) {
            say("Click an item in the list first.", RED);
            return;
        }
        SavedItems.get().move(selected, delta);
        int to = selected + delta;
        if (to >= 0 && to < SavedItems.get().list().size()) selected = to;
        page = Math.max(0, selected / rowsPerPage());
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 10, WHITE);
        g.drawString(this.font, "Saved in config/nbtitembuilder-saved.snbt. Name is optional when saving.", left, 22, SOFT);
        int count = SavedItems.get().list().size();
        if (count == 0) {
            g.drawString(this.font, "Nothing saved yet. Use the buttons above to save an item.", left, listTop + 4, SOFT);
        }
        int per = rowsPerPage();
        int pages = Math.max(1, (count + per - 1) / per);
        g.drawString(this.font, "Page " + (page + 1) + " of " + pages + "  (" + count + " saved)", left + 72, this.height - 21, SOFT);
        List<net.minecraft.util.FormattedCharSequence> bits =
                this.font.split(Component.literal(statusText), cw);
        int show = Math.min(2, bits.size());
        int y = this.height - 62 - (show - 1) * 11;
        for (int i = 0; i < show; i++) {
            int w = this.font.width(bits.get(i));
            g.drawString(this.font, bits.get(i), left + (cw - w) / 2, y + i * 11, statusColor);
        }
    }
}
