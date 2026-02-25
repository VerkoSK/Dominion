package dev.nationsforge.client.gui;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.DiplomacyRelation;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationRank;
import dev.nationsforge.nation.RelationType;
import dev.nationsforge.network.PacketHandler;
import dev.nationsforge.network.packet.C2SDiplomacyPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Tab 2 — "Diplomacy"
 * Shows all active diplomatic relations and lets Diplomats/Leaders
 * change them using action buttons on the right side.
 */
public class DiplomacyPanel extends NationPanel {

    private static final int ROW_H = 24;

    private UUID selectedNation = null;
    private final List<Button> actionButtons = new ArrayList<>();

    public DiplomacyPanel(NationsScreen screen, int x, int y, int w, int h) {
        super(screen, x, y, w, h);
    }

    @Override
    public void addWidgets() {
        rebuildActionButtons();
    }

    @Override
    public void removeWidgets() {
        super.removeWidgets();
        actionButtons.clear();
    }

    private void rebuildActionButtons() {
        for (Button b : actionButtons) {
            ownWidgets.remove(b);
            screen.removeWidget(b);
        }
        actionButtons.clear();

        Nation myNation = ClientNationData.getLocalNation();
        NationRank myRank = ClientNationData.getLocalRank();
        if (myNation == null || myRank == null || selectedNation == null
                || !myRank.canManageDiplomacy())
            return;

        int bx = x + w - 118;
        int by = y + 60;

        RelationType[] options = RelationType.values();
        for (RelationType type : options) {
            RelationType current = myNation.getRelationWith(selectedNation);
            if (type == current)
                continue;
            int col = type.colour & 0xFFFFFF;
            Button btn = add(Button.builder(Component.literal(type.displayName),
                    b -> {
                        PacketHandler.sendToServer(new C2SDiplomacyPacket(selectedNation, type, ""));
                    })
                    .bounds(bx, by, 112, 18).build());
            actionButtons.add(btn);
            by += 20;
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        Nation myNation = ClientNationData.getLocalNation();
        NationRank myRank = ClientNationData.getLocalRank();
        var font = screen.getFont();

        if (myNation == null) {
            gfx.drawCenteredString(font, "§7You are not in a nation.", x + w / 2, y + h / 2,
                    NationGuiHelper.COL_TEXT_DIM);
            return;
        }

        int px = x + 8;
        int py = y + 6;

        gfx.drawString(font, "§7Diplomacy — §f" + myNation.getName(), px, py, NationGuiHelper.COL_TEXT, false);
        py += 14;

        // ── Current relations ──────────────────────────────────────────────────────
        Map<UUID, DiplomacyRelation> relations = myNation.getRelations();
        int listW = w - 130;

        // Also show all nations that have NO explicit relation (as NEUTRAL rows)
        List<Nation> allNations = new ArrayList<>(ClientNationData.getAllNations());
        allNations.removeIf(n -> n.getId().equals(myNation.getId()));

        int startY = py;
        int maxScroll = Math.max(0, allNations.size() * ROW_H - (h - 30));
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < allNations.size(); i++) {
            Nation other = allNations.get(i);
            int rowY = startY + i * ROW_H - scrollOffset;
            if (rowY + ROW_H < startY || rowY > startY + h - 30)
                continue;

            RelationType type = myNation.getRelationWith(other.getId());
            boolean sel = other.getId().equals(selectedNation);

            // Row background
            int bg = sel ? NationGuiHelper.COL_PANEL : (i % 2 == 0 ? 0x12_FFFFFF : 0x00_000000);
            gfx.fill(px, rowY, px + listW, rowY + ROW_H - 2, bg);

            // Relation colour stripe
            gfx.fill(px, rowY, px + 4, rowY + ROW_H - 2, 0xFF_000000 | (type.colour & 0xFFFFFF));

            // Nation colour badge
            gfx.fill(px + 6, rowY + 4, px + 14, rowY + 14, 0xFF_000000 | (other.getColour() & 0xFFFFFF));

            gfx.drawString(font, "[" + other.getTag() + "] " + other.getName(),
                    px + 18, rowY + 8, other.getColour() & 0xFFFFFF, false);

            // Relation label
            gfx.drawString(font, type.displayName, px + listW - 70, rowY + 8,
                    type.colour & 0xFFFFFF, false);

            // Hover highlight
            if (mouseX >= px && mouseX <= px + listW && mouseY >= rowY && mouseY <= rowY + ROW_H - 2) {
                gfx.fill(px, rowY, px + listW, rowY + ROW_H - 2, 0x30_FFFFFF);
            }
        }

        // ── Right panel: selected nation actions ───────────────────────────────────
        if (selectedNation != null) {
            Nation other = ClientNationData.getNationById(selectedNation);
            if (other != null) {
                int rx = x + w - 120;
                gfx.drawString(font, "§7[" + other.getTag() + "]", rx, y + 8,
                        other.getColour() & 0xFFFFFF, false);
                gfx.drawString(font, other.getName(), rx, y + 20, NationGuiHelper.COL_TEXT, false);
                gfx.drawString(font, "§7" + other.getMemberCount() + " members",
                        rx, y + 32, NationGuiHelper.COL_TEXT_DIM, false);
                gfx.drawString(font, "§7Change relation:", rx, y + 50, NationGuiHelper.COL_TEXT_DIM, false);

                if (myRank == null || !myRank.canManageDiplomacy()) {
                    gfx.drawString(font, "§cNo permission.", rx, y + 62, NationGuiHelper.COL_DANGER, false);
                }
            }
        }
    }

    public void onClick(double mouseX, double mouseY) {
        Nation myNation = ClientNationData.getLocalNation();
        if (myNation == null)
            return;

        List<Nation> allNations = new ArrayList<>(ClientNationData.getAllNations());
        allNations.removeIf(n -> n.getId().equals(myNation.getId()));

        int px = x + 8;
        int startY = y + 20;
        int listW = w - 130;

        for (int i = 0; i < allNations.size(); i++) {
            int rowY = startY + i * ROW_H - scrollOffset;
            if (mouseY >= rowY && mouseY <= rowY + ROW_H - 2
                    && mouseX >= px && mouseX <= px + listW) {
                UUID id = allNations.get(i).getId();
                selectedNation = id.equals(selectedNation) ? null : id;
                rebuildActionButtons();
                return;
            }
        }
    }
}
