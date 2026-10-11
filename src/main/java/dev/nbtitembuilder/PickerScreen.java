package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A searchable grid of choices. Picking one calls back and returns to the screen you came from. */
public class PickerScreen extends Screen {
    private static final int COLS = 3;

    private final Screen parent;
    private final String heading;
    private final List<String[]> options;
    private final Consumer<String> onPick;
    private final List<String[]> filtered = new ArrayList<>();
    private final List<Button> cells = new ArrayList<>();
    private EditBox search;
    private Button prev;
    private Button next;
    private int page;
    private int perPage = 1;
    private String query = "";

    public PickerScreen(Screen parent, String heading, List<String[]> options, Consumer<String> onPick) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.options = options;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        cells.clear();
        int cw = Math.min(this.width - 24, 440);
        int left = (this.width - cw) / 2;
        int gap = 4;
        int cellW = (cw - gap * (COLS - 1)) / COLS;
        int top = 50;
        int bottom = this.height - 34;
        int rows = Math.max(1, (bottom - top) / 22);
        perPage = rows * COLS;

        search = new EditBox(this.font, left, 24, cw, 20, Component.literal("Search"));
        search.setHint(Component.literal("Type to search").withStyle(ChatFormatting.GRAY));
        search.setTextColor(0xFFFFFFFF);
        search.setValue(query);
        search.setResponder(v -> {
            query = v;
            page = 0;
            applyFilter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        for (int i = 0; i < perPage; i++) {
            final int slot = i;
            int x = left + (i % COLS) * (cellW + gap);
            int y = top + (i / COLS) * 22;
            Button b = Button.builder(Component.literal(""), btn -> choose(slot))
                    .bounds(x, y, cellW, 20).build();
            cells.add(b);
            addRenderableWidget(b);
        }

        int by = this.height - 28;
        prev = Button.builder(Component.literal("<"), b -> {
            page--;
            applyFilter();
        }).bounds(left, by, 30, 20).build();
        next = Button.builder(Component.literal(">"), b -> {
            page++;
            applyFilter();
        }).bounds(left + 34, by, 30, 20).build();
        addRenderableWidget(prev);
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(left + cw - 90, by, 90, 20).build());
        applyFilter();
    }

    private void applyFilter() {
        filtered.clear();
        String q = query.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        for (String[] o : options) {
            if (q.isEmpty() || o[0].contains(q) || o[1].toLowerCase(Locale.ROOT).replace(' ', '_').contains(q)) {
                filtered.add(o);
            }
        }
        int pages = Math.max(1, (filtered.size() + perPage - 1) / perPage);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        for (int i = 0; i < cells.size(); i++) {
            Button b = cells.get(i);
            int idx = page * perPage + i;
            if (idx < filtered.size()) {
                b.setMessage(Component.literal(filtered.get(idx)[1]));
                b.visible = true;
                b.active = true;
            } else {
                b.visible = false;
                b.active = false;
            }
        }
        prev.active = page > 0;
        next.active = page < pages - 1;
    }

    private void choose(int slot) {
        int idx = page * perPage + slot;
        if (idx < 0 || idx >= filtered.size()) return;
        onPick.accept(filtered.get(idx)[0]);
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(this.font, heading, (this.width - this.font.width(heading)) / 2, 8, 0xFFFFFFFF);
        int pages = Math.max(1, (filtered.size() + perPage - 1) / perPage);
        int cw = Math.min(this.width - 24, 440);
        int left = (this.width - cw) / 2;
        g.drawString(this.font, "Page " + (page + 1) + " of " + pages + "  (" + filtered.size() + " found)",
                left + 72, this.height - 22, 0xFFDDDDDD);
    }
}
