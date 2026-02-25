package dev.nationsforge.client.gui;

import dev.nationsforge.client.ClientNationData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Primary Nations GUI — accessible via the [N] keybind.
 * Tabbed layout:
 *
 * ┌─────────────────────────────────────────┐
 * │ ✦ NationsForge │
 * ├────────┬──────────┬───────────┬─────────┤
 * │Overview│ Members │ Diplomacy │ Browse │ [Settings]
 * ├────────┴──────────┴───────────┴─────────┤
 * │ │
 * │ (active tab content) │
 * │ │
 * └─────────────────────────────────────────┘
 */
public class NationsScreen extends Screen {

    // Layout constants
    private static final int TAB_H = 20;
    private static final int TABS = 5; // Overview, Members, Diplomacy, Browse, Settings

    private int guiW, guiH;      // computed from screen size in init()
    private int left, top;       // top-left corner of the GUI

    private int activeTab = 0;
    private final List<Button> tabButtons = new ArrayList<>();

    // Keep sub-panels so they can maintain their own scroll state
    private OverviewPanel overviewPanel;
    private MembersPanel membersPanel;
    private DiplomacyPanel diplomacyPanel;
    private BrowsePanel browsePanel;
    private SettingsPanel settingsPanel;

    public NationsScreen() {
        super(Component.translatable("screen.nationsforge.main"));
    }

    @Override
    protected void init() {
        guiW = Math.min(820, Math.max(360, width  - 40));
        guiH = Math.min(500, Math.max(260, height - 40));
        left = (width  - guiW) / 2;
        top  = (height - guiH) / 2;

        // Store local player ID so ClientNationData lookups work
        if (minecraft != null && minecraft.player != null) {
            ClientNationData.setLocalPlayer(minecraft.player.getUUID());
        }

        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        tabButtons.clear();

        String[] tabLabels = { "Overview", "Members", "Diplomacy", "Browse", "Settings" };
        int tabW = guiW / TABS;
        for (int i = 0; i < TABS; i++) {
            final int idx = i;
            Button btn = Button.builder(Component.literal(tabLabels[i]),
                    b -> selectTab(idx))
                    .bounds(left + i * tabW, top + 20, tabW, TAB_H)
                    .build();
            addRenderableWidget(btn);
            tabButtons.add(btn);
        }

        int panelY = top + 20 + TAB_H;
        int panelH = guiH - 20 - TAB_H;

        overviewPanel  = new OverviewPanel(this, left, panelY, guiW, panelH);
        membersPanel   = new MembersPanel(this, left, panelY, guiW, panelH);
        diplomacyPanel = new DiplomacyPanel(this, left, panelY, guiW, panelH);
        browsePanel    = new BrowsePanel(this, left, panelY, guiW, panelH);
        settingsPanel  = new SettingsPanel(this, left, panelY, guiW, panelH);

        activePanel().addWidgets();
    }

    private NationPanel activePanel() {
        return switch (activeTab) {
            case 1 -> membersPanel;
            case 2 -> diplomacyPanel;
            case 3 -> browsePanel;
            case 4 -> settingsPanel;
            default -> overviewPanel;
        };
    }

    private void selectTab(int idx) {
        // Remove panel widgets before switching
        activePanel().removeWidgets();
        activeTab = idx;
        activePanel().addWidgets();
    }

    /**
     * Public accessor so sub-panels can switch tabs (e.g. Browse button on
     * Overview).
     */
    public void switchTab(int idx) {
        selectTab(idx);
    }

    /** Exposes the Minecraft instance for sub-panels. */
    public Minecraft getMinecraft() {
        return minecraft;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Dark world background
        renderBackground(gfx);

        // Outer panel
        NationGuiHelper.drawPanel(gfx, left, top, guiW, guiH,
                NationGuiHelper.COL_BG, NationGuiHelper.COL_BORDER);

        // Top title bar
        NationGuiHelper.drawHeader(gfx, font, "✦  Dominion", left, top, guiW, 20);

        // Tab strip background
        gfx.fill(left, top + 20, left + guiW, top + 20 + TAB_H, NationGuiHelper.COL_TAB);

        // Highlight active tab
        int tabW = guiW / TABS;
        gfx.fill(left + activeTab * tabW, top + 20,
                left + (activeTab + 1) * tabW, top + 20 + TAB_H,
                NationGuiHelper.COL_TAB_ACTIVE);
        gfx.fill(left + activeTab * tabW, top + 20 + TAB_H - 2,
                left + (activeTab + 1) * tabW, top + 20 + TAB_H,
                NationGuiHelper.COL_ACCENT);

        // Render super (buttons etc.)
        super.render(gfx, mouseX, mouseY, partialTick);

        // Render active panel content
        activePanel().render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        activePanel().onScroll(delta);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Let panels intercept row-selection clicks first
        if (button == 0) {
            switch (activeTab) {
                case 1 -> membersPanel.onClick(mouseX, mouseY);
                case 2 -> diplomacyPanel.onClick(mouseX, mouseY);
                case 3 -> browsePanel.onClick(mouseX, mouseY);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Expose Minecraft internals to sub-panels
    // ──────────────────────────────────

    public Minecraft mc() {
        return minecraft;
    }

    public net.minecraft.client.gui.Font getFont() {
        return font;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getGuiW() {
        return guiW;
    }

    public int getGuiH() {
        return guiH;
    }

    public int getTabH() {
        return TAB_H;
    }

    public <W extends net.minecraft.client.gui.components.AbstractWidget> W addWidget(W w) {
        return addRenderableWidget(w);
    }

    public void removeWidget(net.minecraft.client.gui.components.AbstractWidget w) {
        super.removeWidget(w);
    }
}
