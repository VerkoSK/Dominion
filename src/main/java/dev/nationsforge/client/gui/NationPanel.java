package dev.nationsforge.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for a panel displayed inside the {@link NationsScreen}.
 * Each panel manages its own widget lifecycle and scroll offset.
 */
public abstract class NationPanel {

    protected final NationsScreen screen;
    protected final int x, y, w, h;
    protected int scrollOffset = 0;
    protected final List<AbstractWidget> ownWidgets = new ArrayList<>();

    protected NationPanel(NationsScreen screen, int x, int y, int w, int h) {
        this.screen = screen;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** Called when this panel becomes active — add widgets to the Screen. */
    public abstract void addWidgets();

    /**
     * Called when this panel becomes inactive — remove its widgets from the Screen.
     */
    public void removeWidgets() {
        for (AbstractWidget widget : ownWidgets) {
            screen.removeWidget(widget);
        }
        ownWidgets.clear();
    }

    /**
     * Render panel-specific content (called after super.render in NationsScreen).
     */
    public abstract void render(GuiGraphics gfx, int mouseX, int mouseY, float partial);

    public void onScroll(double delta) {
        scrollOffset = Math.max(0, scrollOffset - (int) (delta * 8));
    }

    protected <W extends AbstractWidget> W add(W widget) {
        ownWidgets.add(widget);
        return screen.addWidget(widget);
    }
}
