package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shows the item as NBT, or as the /give command, with colored syntax. */
public class NbtViewScreen extends Screen {
    private static boolean showCommand = false;

    private final Screen parent;
    private final ItemModel model;
    private final List<List<Snbt.Span>> lines = new ArrayList<>();
    private String copyText = "";
    private String error;
    private String note = "";
    private int scroll;
    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private Button modeButton;

    public NbtViewScreen(Screen parent, ItemModel model) {
        super(Component.literal("Item NBT"));
        this.parent = parent;
        this.model = model;
    }

    @Override
    protected void init() {
        int cw = Math.min(this.width - 24, 560);
        panelLeft = (this.width - cw) / 2;
        panelRight = panelLeft + cw;
        panelTop = 28;
        panelBottom = this.height - 44;

        int by = this.height - 28;
        int bw = (cw - 8) / 3;
        modeButton = Button.builder(modeLabel(), b -> {
            showCommand = !showCommand;
            b.setMessage(modeLabel());
            scroll = 0;
            rebuildText();
        }).bounds(panelLeft, by, bw, 20).build();
        addRenderableWidget(modeButton);
        addRenderableWidget(Button.builder(Component.literal("Copy"), b -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(copyText);
            note = "Copied to your clipboard.";
        }).bounds(panelLeft + bw + 4, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .bounds(panelLeft + 2 * (bw + 4), by, cw - 2 * (bw + 4), 20).build());
        rebuildText();
    }

    private Component modeLabel() {
        return Component.literal(showCommand ? "Showing: /give command" : "Showing: item NBT");
    }

    private void rebuildText() {
        lines.clear();
        error = null;
        int maxW = panelRight - panelLeft - 12;
        try {
            if (showCommand) {
                String cmd = "/" + ItemSpec.giveCommand(model, "@s");
                copyText = cmd;
                wrap(Snbt.commandSpans(cmd), maxW);
            } else {
                copyText = ItemSpec.itemNbt(model);
                for (String line : ItemSpec.itemNbtPretty(model).split("\n")) {
                    wrap(Snbt.spans(line), maxW);
                }
            }
        } catch (IllegalArgumentException e) {
            error = e.getMessage() == null ? "Check the fields." : e.getMessage();
            copyText = "";
        }
        note = "";
    }

    /** Adds one source line, split into screen lines no wider than maxW. */
    private void wrap(List<Snbt.Span> spans, int maxW) {
        List<Snbt.Span> cur = new ArrayList<>();
        int x = 0;
        for (Snbt.Span s : spans) {
            String rest = s.text;
            while (!rest.isEmpty()) {
                int n = 0;
                while (n < rest.length() && x + this.font.width(rest.substring(0, n + 1)) <= maxW) n++;
                if (n == 0) {
                    if (x > 0) {
                        lines.add(cur);
                        cur = new ArrayList<>();
                        x = 0;
                        continue;
                    }
                    n = 1;
                }
                String piece = rest.substring(0, n);
                cur.add(new Snbt.Span(piece, s.kind));
                x += this.font.width(piece);
                rest = rest.substring(n);
                if (!rest.isEmpty()) {
                    lines.add(cur);
                    cur = new ArrayList<>();
                    x = 0;
                }
            }
        }
        lines.add(cur);
    }

    private int visibleLines() {
        return Math.max(1, (panelBottom - panelTop - 8) / 10);
    }

    private void clampScroll() {
        int max = Math.max(0, lines.size() - visibleLines());
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY) * 3;
        clampScroll();
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private static int colorOf(int kind) {
        return switch (kind) {
            case Snbt.KEY -> 0xFF7FD4FF;
            case Snbt.STRING -> 0xFF9FE79F;
            case Snbt.NUMBER -> 0xFFFFC66D;
            default -> 0xFFFFFFFF;
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawString(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 10, 0xFFFFFFFF);
        g.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCC000000);
        if (error != null) {
            g.drawString(this.font, error, panelLeft + 6, panelTop + 6, 0xFFFF8080);
            return;
        }
        clampScroll();
        int y = panelTop + 4;
        int end = Math.min(lines.size(), scroll + visibleLines());
        for (int i = scroll; i < end; i++) {
            int x = panelLeft + 6;
            for (Snbt.Span s : lines.get(i)) {
                g.drawString(this.font, s.text, x, y, colorOf(s.kind));
                x += this.font.width(s.text);
            }
            y += 10;
        }
        if (lines.size() > visibleLines()) {
            String info = (scroll + 1) + "-" + end + " of " + lines.size() + " (scroll with the mouse wheel)";
            g.drawString(this.font, info, panelRight - this.font.width(info) - 4, panelBottom + 4, 0xFFDDDDDD);
        }
        if (!note.isEmpty()) {
            g.drawString(this.font, note, panelLeft, panelBottom + 4, 0xFF7FF2A0);
        }
    }
}
