package dev.nbtitembuilder;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * An EditBox that clips its text to its own border. Vanilla boxes let long
 * hints and values paint over the neighboring widgets on narrow windows;
 * this one cuts them off at the edge instead.
 */
public class ClipBox extends EditBox {
    public ClipBox(Font font, int x, int y, int w, String hint) {
        super(font, x, y, w, 20, Component.literal(hint));
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        try {
            super.renderWidget(g, mouseX, mouseY, delta);
        } finally {
            g.disableScissor();
        }
    }
}
