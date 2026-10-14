package dev.nbtitembuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Shows the item as NBT or as the /give command with colored syntax, and can
 * also edit the raw item NBT with autocomplete and corrections.
 *
 * <p>Editing only changes the builder. Giving or applying the item still runs
 * a command as you, so those need cheats (singleplayer) or operator (server).
 */
public class NbtViewScreen extends Screen {
    private static boolean showCommand = false;
    private static final int KEY_TAB = 258;

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
    private Button editButton;
    private Button actionButton;

    private boolean editing;
    private EditBox editBox;
    private String editText = "";
    private String editError = "";

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
        int bw = (cw - 12) / 4;
        editButton = Button.builder(editLabel(), b -> {
            editing = !editing;
            if (editing) {
                try {
                    editText = editText.isEmpty() ? ItemSpec.itemNbt(model) : editText;
                } catch (IllegalArgumentException e) {
                    editText = editText.isEmpty() ? "{id:\"minecraft:stone\",count:1}" : editText;
                }
                editError = "";
            }
            b.setMessage(editLabel());
            rebuildWidgets();
        }).bounds(panelLeft, by, bw, 20).build();
        addRenderableWidget(editButton);
        modeButton = Button.builder(modeLabel(), b -> {
            showCommand = !showCommand;
            b.setMessage(modeLabel());
            scroll = 0;
            rebuildText();
        }).bounds(panelLeft + (bw + 4), by, bw, 20).build();
        modeButton.active = !editing;
        addRenderableWidget(modeButton);
        actionButton = Button.builder(editing ? Component.literal("Apply") : Component.literal("Copy"), b -> {
            if (editing) applyEdit();
            else {
                Minecraft.getInstance().keyboardHandler.setClipboard(copyText);
                note = "Copied to your clipboard.";
            }
        }).bounds(panelLeft + 2 * (bw + 4), by, bw, 20).build();
        addRenderableWidget(actionButton);
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .bounds(panelLeft + 3 * (bw + 4), by, cw - 3 * (bw + 4), 20).build());

        if (editing) {
            editBox = new ClipBox(this.font, panelLeft + 6, panelTop + 6, cw - 12, "Item NBT");
            editBox.setMaxLength(100000);
            editBox.setTextColor(0xFFFFFFFF);
            editBox.setHint(Component.literal("{id:\"minecraft:stone\",count:1,components:{...}}")
                    .withStyle(ChatFormatting.GRAY));
            editBox.setValue(editText);
            editBox.setResponder(v -> {
                editText = v;
                editError = "";
            });
            addRenderableWidget(editBox);
        }
        rebuildText();
    }

    private Component editLabel() {
        return Component.literal(editing ? "View" : "Edit NBT");
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

    /** Parses the edited NBT back into the builder, with friendly errors. */
    private void applyEdit() {
        String text = editBox == null ? "" : editBox.getValue().trim();
        if (text.isEmpty()) {
            editError = "Paste or type the item data first.";
            return;
        }
        if (text.startsWith("/") || text.toLowerCase(java.util.Locale.ROOT).startsWith("give ")) {
            editError = "That is the /give command. Edit the item {...} data instead (switch to View).";
            return;
        }
        try {
            Snbt.parse(text);
        } catch (IllegalArgumentException e) {
            editError = "Not valid NBT: " + trim(e.getMessage()) + correctionHint(text);
            return;
        }
        try {
            ItemModel fresh = new ItemModel();
            List<String> warnings = ItemLoader.load(fresh, text);
            if (fresh.id.isBlank()) {
                editError = "The data needs an id, e.g. {id:\"minecraft:stone\",count:1}.";
                return;
            }
            model.reset();
            ItemLoader.load(model, text);
            editing = false;
            editText = "";
            rebuildWidgets();
            note = warnings.isEmpty() ? "Applied to the builder." : "Applied: " + warnings.get(0);
        } catch (IllegalArgumentException e) {
            editError = trim(e.getMessage()) + correctionHint(text);
        }
    }

    private static String trim(String s) {
        if (s == null) return "Check the text.";
        return s.length() > 140 ? s.substring(0, 137) + "..." : s;
    }

    /** Guesses what a mistyped key should have been. */
    private static String correctionHint(String text) {
        Matcher m = Pattern.compile("([A-Za-z0-9_.+-]+)\"?\\s*[:=]").matcher(text);
        String last = null;
        while (m.find()) last = m.group(1);
        if (last == null) return "";
        String hint = Components.correct(last);
        if (hint == null) return "";
        if (last.contains(":")) return "";
        return " Did you mean " + hint + "? (Tab completes keys.)";
    }

    // ---- autocomplete (Tab key) ---------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editing && event.key() == KEY_TAB && tryComplete()) return true;
        return super.keyPressed(event);
    }

    private boolean tryComplete() {
        if (editBox == null) return false;
        String value = editBox.getValue();
        int cursor = Math.min(editBox.getCursorPosition(), value.length());
        String before = value.substring(0, cursor);
        Matcher m = Pattern.compile("(\"?)((?:minecraft:)?[A-Za-z0-9_.+-]*)$").matcher(before);
        if (!m.find()) return false;
        String quote = m.group(1);
        String frag = m.group(2);
        if (frag.isEmpty()) return false;
        char prev = before.length() > m.start() ? before.charAt(Math.max(0, m.start() - 1)) : 0;
        if (prev != '{' && prev != ',' && !Character.isWhitespace(prev) && prev != '[' && prev != 0) return false;
        List<String> s = Components.suggestNbtKey(frag, 1);
        if (s.isEmpty()) return false;
        String insert = quote.isEmpty() && s.get(0).contains(":") ? "\"" + s.get(0) + "\"" : s.get(0);
        editBox.setValue(before.substring(0, m.start(2)) + insert + value.substring(cursor));
        return true;
    }

    private String editHint() {
        if (editBox == null) return "";
        String value = editBox.getValue();
        int cursor = Math.min(editBox.getCursorPosition(), value.length());
        Matcher m = Pattern.compile("(\"?)((?:minecraft:)?[A-Za-z0-9_.+-]*)$").matcher(value.substring(0, cursor));
        if (!m.find() || m.group(2).isEmpty()) return "Tab completes keys. Apply writes it back to the builder.";
        List<String> s = Components.suggestNbtKey(m.group(2), 3);
        if (s.isEmpty()) return "No completion matches. Apply checks the text and suggests fixes.";
        return "Tab completes: " + String.join(", ", s);
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
        this.minecraft.setScreenAndShow(parent);
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
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(this.font, editing ? Component.literal("Edit item NBT") : this.title,
                (this.width - this.font.width(editing ? "Edit item NBT" : this.title.getString())) / 2, 10, 0xFFFFFFFF);
        g.fill(panelLeft, panelTop, panelRight, panelBottom, 0xCC000000);
        if (editing) {            int y = panelTop + 32;
            g.text(this.font, "Paste the item {id:...} data above, then Apply.", panelLeft + 6, y, 0xFFDDDDDD);
            y += 12;
            for (var line : this.font.split(Component.literal(editHint()), panelRight - panelLeft - 12)) {
                if (y > panelBottom - 12) break;
                g.text(this.font, line, panelLeft + 6, y, 0xFF7FF2A0);
                y += 10;
            }
            if (!editError.isEmpty()) {
                y += 2;
                for (var line : this.font.split(Component.literal(editError), panelRight - panelLeft - 12)) {
                    if (y > panelBottom - 12) break;
                    g.text(this.font, line, panelLeft + 6, y, 0xFFFF8080);
                    y += 10;
                }
            }
            y += 4;
            if (y <= panelBottom - 24) {
                g.text(this.font, "Editing only changes the builder.", panelLeft + 6, y, 0xFFDDDDDD);
                g.text(this.font, "Give and Apply need cheats or operator, as before.", panelLeft + 6, y + 11, 0xFFDDDDDD);
            }
            return;
        }
        if (error != null) {
            int ey = panelTop + 6;
            int maxW = panelRight - panelLeft - 12;
            for (var line : this.font.split(Component.literal(error), maxW)) {
                if (ey > panelBottom - 12) break;
                int w = this.font.width(line);
                g.text(this.font, line, panelLeft + (panelRight - panelLeft - w) / 2, ey, 0xFFFF8080);
                ey += 11;
            }
            return;
        }
        clampScroll();
        int y = panelTop + 4;
        int end = Math.min(lines.size(), scroll + visibleLines());
        for (int i = scroll; i < end; i++) {
            int x = panelLeft + 6;
            for (Snbt.Span s : lines.get(i)) {
                g.text(this.font, s.text, x, y, colorOf(s.kind));
                x += this.font.width(s.text);
            }
            y += 10;
        }
        if (!note.isEmpty()) {
            g.text(this.font, note, panelLeft, panelBottom + 4, 0xFF7FF2A0);
        } else if (lines.size() > visibleLines()) {
            String info = (scroll + 1) + "-" + end + " of " + lines.size() + " (scroll with the mouse wheel)";
            g.text(this.font, info, panelRight - this.font.width(info) - 4, panelBottom + 4, 0xFFDDDDDD);
        }
    }
}
