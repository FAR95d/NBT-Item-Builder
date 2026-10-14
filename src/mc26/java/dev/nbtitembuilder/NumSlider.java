package dev.nbtitembuilder;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * A slider that picks a whole number between min and a (possibly changing)
 * max. The number itself shows in the box next to the slider; the slider knob
 * only shows the position. Dragging commits through the callback, typing in
 * the box syncs the knob back through {@link #sync()}.
 */
public class NumSlider extends AbstractSliderButton {
    private final double min;
    private final DoubleSupplier max;
    private final DoubleSupplier get;
    private final DoubleConsumer commit;
    private String label = "";
    private String name = "";
    private final double step;
    private boolean syncing;

    /** Shows "R: 128" style text on the knob; empty keeps it blank. */
    public NumSlider labeled(String s) {
        label = s == null ? "" : s;
        updateMessage();
        return this;
    }

    /** Shows a static name on the bar; the number stays in the box beside it. */
    public NumSlider named(String s) {
        name = s == null ? "" : s;
        updateMessage();
        return this;
    }

    public NumSlider(int x, int y, int w, double min, DoubleSupplier max,
            DoubleSupplier get, DoubleConsumer commit) {
        this(x, y, w, min, max, get, commit, 1);
    }

    /** Step 1 means whole numbers; smaller steps (0.5, 0.1) allow decimals. */
    public NumSlider(int x, int y, int w, double min, DoubleSupplier max,
            DoubleSupplier get, DoubleConsumer commit, double step) {
        super(x, y, w, 20, Component.literal(""), fraction(get.getAsDouble(), min, max.getAsDouble()));
        this.min = min;
        this.max = max;
        this.get = get;
        this.commit = commit;
        this.step = step <= 0 ? 1 : step;
        updateMessage();
    }

    private static double fraction(double v, double min, double max) {
        if (max <= min) return 0d;
        return Math.max(0d, Math.min(1d, (v - min) / (max - min)));
    }

    /** Moves the knob to the current value, e.g. after typing in the box. */
    public void sync() {
        syncing = true;
        // Direct field write: 1.21.10 has no setValue method, 1.21.11 does; the field works on both.
        this.value = fraction(get.getAsDouble(), min, max.getAsDouble());
        updateMessage();
        syncing = false;
    }

    @Override
    protected void updateMessage() {
        // Runs in the superclass constructor too, before our fields exist.
        if (label != null && !label.isEmpty()) {
            double m = max == null ? min : max.getAsDouble();
            double units = min + (m - min) * this.value;
            setMessage(Component.literal(label + ": " + Components.fmtStep(units, step)));
            return;
        }
        if (name != null && !name.isEmpty()) {
            setMessage(Component.literal(name));
            return;
        }
        setMessage(Component.literal(""));
    }

    @Override
    protected void applyValue() {
        if (syncing) return;
        double m = max.getAsDouble();
        double raw = min + (m - min) * this.value;
        commit.accept(min + Math.round((raw - min) / step) * step);
    }
}
