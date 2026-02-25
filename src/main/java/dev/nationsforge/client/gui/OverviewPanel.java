package dev.nationsforge.client.gui;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationFlag;
import dev.nationsforge.nation.NationPowerCalculator;
import dev.nationsforge.nation.NationRank;
import dev.nationsforge.nation.RelationType;
import dev.nationsforge.network.PacketHandler;
import dev.nationsforge.network.packet.C2SLeaveNationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Tab 0 — "Overview"
 *
 * • Not in a nation → welcome/landing state with Browse/Create buttons.
 * • In a nation → full overview: name, colour, rank, power tier,
 * territory, stats strip, active wars/allies, leaderboard.
 */
public class OverviewPanel extends NationPanel {

    private Button btnLeave;
    private Button btnBrowse;
    private Button btnCreate;

    public OverviewPanel(NationsScreen screen, int x, int y, int w, int h) {
        super(screen, x, y, w, h);
    }

    @Override
    public void addWidgets() {
        Nation nation = ClientNationData.getLocalNation();
        if (nation == null) {
            // No-nation state — show shortcut buttons
            int cx = x + w / 2;
            btnBrowse = add(Button.builder(
                    Component.literal("⚑  Browse Nations"),
                    b -> screen.switchTab(3)) // Browse tab
                    .bounds(cx - 92, y + h - 62, 88, 20).build());
            btnCreate = add(Button.builder(
                    Component.literal("✦  Found a Nation"),
                    b -> {
                        if (screen.getMinecraft() != null)
                            screen.getMinecraft().setScreen(new CreateNationScreen(screen));
                    })
                    .bounds(cx + 4, y + h - 62, 88, 20).build());
        } else {
            btnLeave = add(Button.builder(Component.literal("§cLeave Nation"),
                    b -> {
                        PacketHandler.sendToServer(new C2SLeaveNationPacket());
                        screen.onClose();
                    })
                    .bounds(x + w - 105, y + h - 28, 100, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        Nation nation = ClientNationData.getLocalNation();
        var font = screen.getFont();

        // ── NOT IN A NATION ────────────────────────────────────────────────────────
        if (nation == null) {
            renderNoNation(gfx, font);
            return;
        }

        // ── IN A NATION ────────────────────────────────────────────────────────────
        renderNationOverview(gfx, font, nation);
    }

    // ── No-nation welcome state ──────────────────────────────────────────────────

    private void renderNoNation(GuiGraphics gfx, net.minecraft.client.gui.Font font) {
        int cx = x + w / 2;
        int cy = y + h / 2 - 20;

        // Big icon / title
        gfx.drawCenteredString(font, "§6✦ Dominion", cx, y + 18, NationGuiHelper.COL_ACCENT);
        gfx.drawCenteredString(font, "§7You are not part of any nation.", cx, y + 34, NationGuiHelper.COL_TEXT_DIM);

        // Divider
        gfx.fill(x + 20, y + 46, x + w - 20, y + 47, NationGuiHelper.COL_BORDER);

        // What you can do
        int lx = x + 20;
        int ly = y + 54;
        gfx.drawString(font, "§fWhat you can do:", lx, ly, NationGuiHelper.COL_TEXT, false);
        ly += 14;
        gfx.drawString(font, "§a⚑§7 Browse existing nations and apply to join one.", lx + 4, ly,
                NationGuiHelper.COL_TEXT_DIM, false);
        ly += 12;
        gfx.drawString(font, "§6✦§7 Found your own nation and recruit allies.", lx + 4, ly,
                NationGuiHelper.COL_TEXT_DIM, false);
        ly += 12;
        gfx.drawString(font, "§b⚔§7 Declare war, forge alliances, claim territory.", lx + 4, ly,
                NationGuiHelper.COL_TEXT_DIM, false);
        ly += 12;
        gfx.drawString(font, "§d♦§7 Compete on the global leaderboard.", lx + 4, ly, NationGuiHelper.COL_TEXT_DIM,
                false);

        // divider above buttons
        gfx.fill(x + 20, y + h - 70, x + w - 20, y + h - 69, NationGuiHelper.COL_BORDER);
        gfx.drawCenteredString(font, "§7Choose your path:", cx, y + h - 76, NationGuiHelper.COL_TEXT_DIM);
    }

    // ── Full nation overview
    // ──────────────────────────────────────────────────────

    private void renderNationOverview(GuiGraphics gfx, net.minecraft.client.gui.Font font, Nation nation) {
        NationRank rank = ClientNationData.getLocalRank();
        int px = x + 8;
        int py = y + 6;
        int rgb = nation.getColour() & 0xFFFFFF;

        // Colour swatch
        gfx.fill(px, py, px + 16, py + 16, 0xFF_000000 | rgb);
        gfx.fill(px, py, px + 16, py + 1, 0x40_FFFFFF);
        // Nation name + tag
        gfx.drawString(font, "§f" + nation.getName(), px + 22, py + 1, rgb, false);
        gfx.drawString(font, "§7[" + nation.getTag() + "]", px + 22, py + 11, NationGuiHelper.COL_TEXT_DIM, false);

        // Power tier badge — top-right, just below the header top edge
        NationPowerCalculator.Tier tier = NationPowerCalculator.getTier(nation.getPower());
        int tierCol = tier.colour & 0xFFFFFF;
        String tierLabel = "§7Tier: §f" + tier.displayName;
        gfx.drawString(font, tierLabel, x + w - font.width("Tier: " + tier.displayName) - 6, py + 4, tierCol, false);

        // Nation flag icon — rendered as a standard 16×16 GUI item below the tier label
        NationFlag flag = nation.getFlag();
        if (flag != null) {
            var flagStack = flag.buildBannerStack();
            gfx.renderItem(flagStack, x + w - 22, py + 16);
        }

        py += 24;

        // ── Stats strip ──────────────────────────────────────────────────────────
        NationGuiHelper.drawPanel(gfx, px, py, w - 16, 14, NationGuiHelper.COL_PANEL, NationGuiHelper.COL_BORDER);
        gfx.drawString(font, "§7Members: §f" + nation.getMemberCount(),
                px + 4, py + 3, NationGuiHelper.COL_TEXT, false);
        gfx.drawString(font, "§7Chunks: §a\u2756 " + nation.getTerritory(),
                px + 90, py + 3, NationGuiHelper.COL_TEXT, false);
        gfx.drawString(font, "§7Power: §b" + NationGuiHelper.abbreviate(nation.getPower()),
                px + 190, py + 3, NationGuiHelper.COL_TEXT, false);

        py += 20;

        // Treasury row
        NationGuiHelper.drawPanel(gfx, px, py, w - 16, 14, NationGuiHelper.COL_PANEL, NationGuiHelper.COL_BORDER);
        gfx.drawString(font, "§7Treasury: §e\u26c3 " + NationGuiHelper.abbreviate(nation.getTreasury()),
                px + 4, py + 3, NationGuiHelper.COL_TEXT, false);
        gfx.drawString(font, "§7Score: §b" + NationGuiHelper.abbreviate(nation.getScore()),
                px + 140, py + 3, NationGuiHelper.COL_TEXT, false);

        py += 20;

        // ── Rank ─────────────────────────────────────────────────────────────────
        if (rank != null) {
            int rCol = rank.colour & 0xFFFFFF;
            gfx.drawString(font, "§7Your rank: ", px, py, NationGuiHelper.COL_TEXT_DIM, false);
            gfx.drawString(font, rank.displayName, px + font.width("§7Your rank: "), py, rCol, false);
            py += 14;
        }

        // ── Description ──────────────────────────────────────────────────────────
        if (nation.getDescription() != null && !nation.getDescription().isBlank()) {
            NationGuiHelper.drawPanel(gfx, px, py, w - 16, 22, NationGuiHelper.COL_PANEL, NationGuiHelper.COL_BORDER);
            String desc = nation.getDescription();
            if (font.width(desc) > w - 24)
                desc = font.plainSubstrByWidth(desc, w - 28) + "…";
            gfx.drawString(font, "§o" + desc, px + 4, py + 7, NationGuiHelper.COL_TEXT_DIM, false);
            py += 28;
        }

        py += 4;

        // ── Active wars / alliances ───────────────────────────────────────────────
        long wars = nation.getRelations().values().stream()
                .filter(r -> r.getType() == RelationType.WAR).count();
        long allies = nation.getRelations().values().stream()
                .filter(r -> r.getType() == RelationType.ALLIANCE).count();
        if (wars > 0) {
            gfx.drawString(font, "§c⚔ " + wars + " active war(s) — check Diplomacy!", px, py, 0xFF_FF4444, false);
            py += 12;
        }
        if (allies > 0) {
            gfx.drawString(font, "§a♦ " + allies + " ally(ies)", px, py, 0xFF_44CC66, false);
            py += 12;
        }

        // ── Leaderboard (right column) ────────────────────────────────────────────
        drawLeaderboard(gfx, font, x + w / 2 + 8, y + 90);
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private void drawLeaderboard(GuiGraphics gfx, net.minecraft.client.gui.Font font, int lx, int ly) {
        List<Nation> board = ClientNationData.getLeaderboard();
        gfx.drawString(font, "§7── Leaderboard ──", lx, ly, NationGuiHelper.COL_TEXT_DIM, false);
        int limit = Math.min(5, board.size());
        for (int i = 0; i < limit; i++) {
            Nation n = board.get(i);
            NationPowerCalculator.Tier tier = NationPowerCalculator.getTier(n.getPower());
            int rgb = n.getColour() & 0xFFFFFF;
            String line = (i + 1) + ". [" + n.getTag() + "] " + n.getName();
            gfx.drawString(font, line, lx + 4, ly + 12 + i * 13, rgb, false);
            gfx.drawString(font, "§7" + NationGuiHelper.abbreviate(n.getPower()),
                    lx + 130, ly + 12 + i * 13, tier.colour & 0xFFFFFF, false);
        }
        if (board.isEmpty()) {
            gfx.drawString(font, "§7No nations yet.", lx + 4, ly + 12, NationGuiHelper.COL_TEXT_DIM, false);
        }
    }
}
