package dev.nationsforge.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationFlag;
import dev.nationsforge.network.PacketHandler;
import dev.nationsforge.network.packet.C2SUpdateFlagPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen editor for the nation's custom banner flag.
 *
 * <pre>
 * ┌─── Nation Flag Editor ─────────────────────────────────────────────────┐
 * │  Preview │  Base Color         │  Layers (max 6)                       │
 * │  [flag]  │  ■■■■               │  [pattern name   ] [colour] [X]       │
 * │          │  ■■■■               │  [pattern name   ] [colour] [X]       │
 * │          │  ■■■■               │  [Add Layer]                          │
 * │          │  ■■■■               │                                       │
 * │                        [  Save  ]  [ Cancel ]                          │
 * └────────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
public class FlagEditorScreen extends Screen {

    // ── Layout ───────────────────────────────────────────────────────────────────
    private static final int W = 330;
    private static final int H = 250;
    private static final int CELL = 13; // colour-cell size
    private static final int GAP = 2; // colour-cell gap

    private final Screen parent;
    /** Working copy — edited in-place; original is not touched until Save. */
    private final NationFlag flag;

    // Dynamic button refs
    private Button btnAddLayer;
    private Button btnSave;
    private Button btnCancel;
    /** One "cycle pattern" button per layer row. */
    private final List<Button> patternBtns = new ArrayList<>();

    // Computed on init
    private int left, top;

    public FlagEditorScreen(Screen parent) {
        super(Component.literal("Nation Flag Editor"));
        this.parent = parent;

        // Start with a copy of the current nation's flag (or default)
        Nation nation = ClientNationData.getLocalNation();
        this.flag = (nation != null) ? new NationFlag(nation.getFlag()) : new NationFlag();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        rebuildButtons();
    }

    // ── Rebuild dynamic layer buttons ────────────────────────────────────────────

    private void rebuildButtons() {
        clearWidgets();
        patternBtns.clear();

        int layerRowX = left + 158;
        int layerStartY = top + 50;
        int rowH = 22;

        List<NationFlag.Layer> layers = new ArrayList<>(flag.getLayers());
        for (int i = 0; i < layers.size(); i++) {
            final int idx = i;
            NationFlag.Layer layer = layers.get(i);

            // Pattern cycle button (click = next, right-click = not possible via Button so
            // use shift=prev later)
            String patName = NationFlag.patternDisplayName(layer.patternCode);
            Button pb = Button.builder(Component.literal("§7" + patName), b -> {
                NationFlag.Layer cur = flag.getLayers().get(idx);
                int pi = patternIndex(cur.patternCode);
                int next = (pi + 1) % NationFlag.PATTERN_CODES.length;
                flag.setLayer(idx, NationFlag.PATTERN_CODES[next], cur.colorId);
                rebuildButtons();
            })
                    .bounds(layerRowX, layerStartY + idx * rowH, 110, 18)
                    .build();
            addRenderableWidget(pb);
            patternBtns.add(pb);

            // Remove button
            final int fi = i;
            addRenderableWidget(Button.builder(Component.literal("§cx"), b -> {
                flag.removeLayer(fi);
                rebuildButtons();
            })
                    .bounds(layerRowX + 144, layerStartY + idx * rowH, 16, 18)
                    .build());
        }

        // Add Layer button
        btnAddLayer = addRenderableWidget(Button.builder(
                Component.literal("§a+ Add Layer"), b -> {
                    flag.addLayer("ts", 0); // default: top stripe, white
                    rebuildButtons();
                })
                .bounds(layerRowX, layerStartY + flag.getLayerCount() * rowH + 4, 110, 18)
                .build());
        btnAddLayer.active = flag.getLayerCount() < NationFlag.MAX_LAYERS;

        // Save / Cancel
        int btnY = top + H - 28;
        btnSave = addRenderableWidget(Button.builder(Component.literal("§aSave Flag"),
                b -> save()).bounds(left + W / 2 - 90, btnY, 85, 20).build());
        btnCancel = addRenderableWidget(Button.builder(Component.literal("§7Cancel"),
                b -> minecraft.setScreen(parent)).bounds(left + W / 2 + 5, btnY, 85, 20).build());
    }

    // ── Render ───────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mx, int my, float partial) {
        renderBackground(gfx);

        // Outer panel + header
        NationGuiHelper.drawPanel(gfx, left, top, W, H, NationGuiHelper.COL_BG, NationGuiHelper.COL_BORDER);
        NationGuiHelper.drawHeader(gfx, font, "✦  Nation Flag Editor", left, top, W, 22);

        // ── Left column: Preview ──────────────────────────────────────────────────
        int lx = left + 8;
        int ly = top + 28;

        gfx.drawString(font, "§8Preview", lx, ly, NationGuiHelper.COL_TEXT_DIM, false);
        // Banner preview at 3× scale
        renderBannerPreview(gfx, lx, ly + 10);

        // ── Left column: Base colour picker ──────────────────────────────────────
        gfx.drawString(font, "§8Base Color", lx, ly + 68, NationGuiHelper.COL_TEXT_DIM, false);
        renderColorGrid(gfx, lx, ly + 78, flag.getBaseColorId(), mx, my, true);

        // ── Divider ───────────────────────────────────────────────────────────────
        gfx.fill(left + 155, top + 28, left + 156, top + H - 30, NationGuiHelper.COL_BORDER);

        // ── Right column: Layers ──────────────────────────────────────────────────
        int rx = left + 160;
        gfx.drawString(font, "§8Layers  §7(max " + NationFlag.MAX_LAYERS + ")", rx, top + 32,
                NationGuiHelper.COL_TEXT_DIM, false);

        List<NationFlag.Layer> layers = new ArrayList<>(flag.getLayers());
        int layerY = top + 50;
        for (int i = 0; i < layers.size(); i++) {
            NationFlag.Layer layer = layers.get(i);
            // Color swatch (click handled in mouseClicked)
            int swatchX = rx + 114;
            int swatchY = layerY + i * 22;
            int colRgb = NationFlag.DYE_COLORS_RGB[layer.colorId];
            gfx.fill(swatchX, swatchY, swatchX + 24, swatchY + 18, 0xFF_000000 | colRgb);
            gfx.fill(swatchX, swatchY, swatchX + 24, swatchY + 1, 0x50_FFFFFF);
            // Tooltip: color name on hover
            if (mx >= swatchX && mx <= swatchX + 24 && my >= swatchY && my <= swatchY + 18) {
                gfx.drawString(font, "§7" + NationFlag.DYE_COLOR_NAMES[layer.colorId],
                        swatchX - 30, swatchY - 10, NationGuiHelper.COL_TEXT, false);
            }
        }

        gfx.drawString(font, "§8Click swatch: cycle color  |  Click pattern: cycle pattern",
                rx, top + H - 42, 0xFF_445566, false);

        super.render(gfx, mx, my, partial);
    }

    /** Renders the banner ItemStack at 3× scale. */
    private void renderBannerPreview(GuiGraphics gfx, int x, int y) {
        var stack = flag.buildBannerStack();
        var pose = gfx.pose();
        pose.pushPose();
        pose.translate(x + 3, y, 200f);
        pose.scale(3f, 3f, 1f);
        gfx.renderItem(stack, 0, 0);
        pose.popPose();
    }

    /**
     * Renders a 4×4 colour grid; highlights the selected index with a white border.
     */
    private void renderColorGrid(GuiGraphics gfx, int x, int y, int selected, int mx, int my, boolean isBase) {
        for (int ci = 0; ci < 16; ci++) {
            int col = ci % 4;
            int row = ci / 4;
            int cx2 = x + col * (CELL + GAP);
            int cy2 = y + row * (CELL + GAP);
            int rgb = NationFlag.DYE_COLORS_RGB[ci];
            gfx.fill(cx2, cy2, cx2 + CELL, cy2 + CELL, 0xFF_000000 | rgb);
            // Highlight selected
            if (ci == selected) {
                gfx.fill(cx2 - 1, cy2 - 1, cx2 + CELL + 1, cy2 - 1 + 1, 0xFF_FFFFFF);
                gfx.fill(cx2 - 1, cy2 + CELL, cx2 + CELL + 1, cy2 + CELL + 1, 0xFF_FFFFFF);
                gfx.fill(cx2 - 1, cy2 - 1, cx2 - 1 + 1, cy2 + CELL + 1, 0xFF_FFFFFF);
                gfx.fill(cx2 + CELL, cy2 - 1, cx2 + CELL + 1, cy2 + CELL + 1, 0xFF_FFFFFF);
            }
            // Tooltip
            if (mx >= cx2 && mx < cx2 + CELL && my >= cy2 && my < cy2 + CELL) {
                gfx.drawString(font, "§7" + NationFlag.DYE_COLOR_NAMES[ci],
                        cx2 + CELL + 2, cy2 + 2, NationGuiHelper.COL_TEXT, false);
            }
        }
    }

    // ── Input ────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Base colour grid click
        int lx = left + 8;
        int ly = top + 28 + 78; // y offset of base colour grid
        int colorIdx = hitColorGrid(lx, ly, (int) mx, (int) my);
        if (colorIdx >= 0) {
            flag.setBaseColorId(colorIdx);
            return true;
        }

        // Layer colour swatches
        int rx = left + 160;
        int layerY = top + 50;
        List<NationFlag.Layer> layers = new ArrayList<>(flag.getLayers());
        for (int i = 0; i < layers.size(); i++) {
            int swatchX = rx + 114;
            int swatchY = layerY + i * 22;
            if ((int) mx >= swatchX && (int) mx <= swatchX + 24
                    && (int) my >= swatchY && (int) my <= swatchY + 18) {
                NationFlag.Layer cur = layers.get(i);
                int nextColor = (cur.colorId + (button == 1 ? 15 : 1)) % 16;
                flag.setLayer(i, cur.patternCode, nextColor);
                rebuildButtons();
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    /** Returns colour index 0–15 if the mouse is inside the 4×4 grid, else -1. */
    private int hitColorGrid(int x, int y, int mx, int my) {
        for (int ci = 0; ci < 16; ci++) {
            int cx2 = x + (ci % 4) * (CELL + GAP);
            int cy2 = y + (ci / 4) * (CELL + GAP);
            if (mx >= cx2 && mx < cx2 + CELL && my >= cy2 && my < cy2 + CELL)
                return ci;
        }
        return -1;
    }

    // ── Save ─────────────────────────────────────────────────────────────────────

    private void save() {
        PacketHandler.sendToServer(new C2SUpdateFlagPacket(flag));
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static int patternIndex(String code) {
        for (int i = 0; i < NationFlag.PATTERN_CODES.length; i++) {
            if (NationFlag.PATTERN_CODES[i].equals(code))
                return i;
        }
        return 0;
    }
}
