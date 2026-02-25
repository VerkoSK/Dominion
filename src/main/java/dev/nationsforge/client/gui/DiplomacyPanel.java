package dev.nationsforge.client.gui;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.DiplomacyRequest;
import dev.nationsforge.nation.DiplomacyRelation;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationRank;
import dev.nationsforge.nation.RelationType;
import dev.nationsforge.network.PacketHandler;
import dev.nationsforge.network.packet.C2SDiplomacyRequestPacket;
import dev.nationsforge.network.packet.C2SDiplomacyResponsePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Tab 2 — "Diplomacy"
 *
 * Layout:
 * ┌── Nation list (scrollable) ──┬── Selected nation + propose ──┐
 * │ [▌][◉] [TAG] Name   TYPE    │  [TAG] NationName             │
 * │  ...                         │  N members  •  [bot/player]   │
 * │                              │  Current: ████ ALLIANCE       │
 * │                              │  ─────── Propose ──────────   │
 * │                              │  Message: [_______________]   │
 * │                              │  [Alliance][Trade Pact]...    │
 * └──────────────────────────────┴───────────────────────────────┘
 * ┌── Incoming requests ────────────────────────────────────────┐
 * │ ▶ [NationX] wants ALLIANCE  "msg"   [✓ Accept][✗ Decline]  │
 * └─────────────────────────────────────────────────────────────┘
 */
public class DiplomacyPanel extends NationPanel {

    private static final int ROW_H    = 22;
    private static final int REQ_H    = 30;
    private static final int RIGHT_W  = 220;
    private static final int BOTTOM_H = 100; // reserved for incoming requests

    // State
    private UUID selectedNation = null;
    private final List<Button> proposeButtons  = new ArrayList<>();
    private final List<Button> requestButtons  = new ArrayList<>();
    private EditBox messageBox = null;

    public DiplomacyPanel(NationsScreen screen, int x, int y, int w, int h) {
        super(screen, x, y, w, h);
    }

    // ── Widget lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void addWidgets() {
        rebuildPropose();
        rebuildRequestButtons();
    }

    @Override
    public void removeWidgets() {
        super.removeWidgets();
        proposeButtons.clear();
        requestButtons.clear();
        messageBox = null;
    }

    // ── Rebuild helpers ───────────────────────────────────────────────────────────

    private void rebuildPropose() {
        for (Button b : proposeButtons) { ownWidgets.remove(b); screen.removeWidget(b); }
        proposeButtons.clear();
        if (messageBox != null)        { ownWidgets.remove(messageBox); screen.removeWidget(messageBox); messageBox = null; }

        Nation myNation = ClientNationData.getLocalNation();
        NationRank myRank = ClientNationData.getLocalRank();
        if (myNation == null || myRank == null || selectedNation == null
                || !myRank.canManageDiplomacy())
            return;

        int rxStart = x + (w - RIGHT_W) + 8;
        int baseY   = y + 70; // below the static info lines

        // Message input box
        messageBox = add(new EditBox(
                screen.getFont(), rxStart, baseY, RIGHT_W - 16, 16,
                Component.literal("Optional message")));
        messageBox.setMaxLength(256);
        messageBox.setHint(Component.literal("§7Optional message…"));
        baseY += 22;

        // One button per RelationType (except current relation)
        RelationType current = myNation.getRelationWith(selectedNation);
        for (RelationType type : RelationType.values()) {
            if (type == current) continue;
            final RelationType t = type;
            Button btn = add(Button.builder(
                    Component.literal(type.displayName)
                            .withStyle(s -> s.withColor(type.colour & 0xFFFFFF)),
                    b -> {
                        String m = messageBox != null ? messageBox.getValue() : "";
                        PacketHandler.sendToServer(new C2SDiplomacyRequestPacket(selectedNation, t, m));
                        if (messageBox != null) messageBox.setValue("");
                    })
                    .bounds(rxStart, baseY, RIGHT_W - 16, 17)
                    .build());
            proposeButtons.add(btn);
            baseY += 19;
        }
    }

    private void rebuildRequestButtons() {
        for (Button b : requestButtons) { ownWidgets.remove(b); screen.removeWidget(b); }
        requestButtons.clear();

        Nation myNation = ClientNationData.getLocalNation();
        NationRank myRank = ClientNationData.getLocalRank();
        if (myNation == null || myRank == null || !myRank.canManageDiplomacy()) return;

        List<DiplomacyRequest> incoming = ClientNationData.getIncomingRequests();
        int listH   = h - BOTTOM_H;
        int reqAreaY = y + listH + 26;
        int bx = x + w - 160;

        for (int i = 0; i < incoming.size(); i++) {
            DiplomacyRequest req = incoming.get(i);
            int by = reqAreaY + i * REQ_H + 4;

            Button accept = add(Button.builder(Component.literal("§a✓ Accept"),
                    b -> {
                        PacketHandler.sendToServer(
                                new C2SDiplomacyResponsePacket(req.getId(), true, ""));
                        rebuildRequestButtons();
                    })
                    .bounds(bx, by, 72, 16).build());
            requestButtons.add(accept);

            Button decline = add(Button.builder(Component.literal("§c✗ Decline"),
                    b -> {
                        PacketHandler.sendToServer(
                                new C2SDiplomacyResponsePacket(req.getId(), false, ""));
                        rebuildRequestButtons();
                    })
                    .bounds(bx + 74, by, 72, 16).build());
            requestButtons.add(decline);
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        var font = screen.getFont();
        Nation myNation = ClientNationData.getLocalNation();

        if (myNation == null) {
            gfx.drawCenteredString(font, "§7You are not in a nation.", x + w / 2, y + h / 2,
                    NationGuiHelper.COL_TEXT_DIM);
            return;
        }

        int listW   = w - RIGHT_W - 4;
        int listH   = h - BOTTOM_H;

        // ── Column divider ─────────────────────────────────────────────────────────
        gfx.fill(x + listW + 2, y + 2, x + listW + 3, y + listH - 2,
                NationGuiHelper.COL_BORDER);

        // ── Nation list (left column) ──────────────────────────────────────────────
        renderNationList(gfx, font, myNation, mouseX, mouseY, listW, listH);

        // ── Right panel ────────────────────────────────────────────────────────────
        renderRightPanel(gfx, font, myNation, listW);

        // ── Incoming requests (bottom strip) ───────────────────────────────────────
        renderRequests(gfx, font, myNation, listH);

        // Render the message box on top of everything
        if (messageBox != null) {
            messageBox.render(gfx, mouseX, mouseY, partial);
        }
    }

    private void renderNationList(GuiGraphics gfx, net.minecraft.client.gui.Font font,
            Nation myNation, int mouseX, int mouseY, int listW, int listH) {
        int px = x + 6;
        int headerY = y + 4;
        gfx.drawString(font, "§7Nations", px, headerY, NationGuiHelper.COL_TEXT_DIM, false);

        List<Nation> all = new ArrayList<>(ClientNationData.getAllNations());
        all.removeIf(n -> n.getId().equals(myNation.getId()));

        int startY  = y + 16;
        int maxScroll = Math.max(0, all.size() * ROW_H - (listH - 18));
        scrollOffset = Math.min(scrollOffset, maxScroll);

        // Clip rendering to list area
        for (int i = 0; i < all.size(); i++) {
            Nation other = all.get(i);
            int rowY = startY + i * ROW_H - scrollOffset;
            if (rowY + ROW_H < startY || rowY > startY + listH - 18) continue;

            RelationType rel = myNation.getRelationWith(other.getId());
            boolean sel = other.getId().equals(selectedNation);
            boolean hovered = mouseX >= px && mouseX <= px + listW - 4
                    && mouseY >= rowY && mouseY <= rowY + ROW_H - 2;

            // Row background
            int bg = sel    ? 0x40_FFFFFF
                    : hovered ? 0x20_FFFFFF
                    : (i % 2 == 0 ? 0x0A_FFFFFF : 0x00_000000);
            gfx.fill(px - 2, rowY, px + listW - 4, rowY + ROW_H - 2, bg);

            // Relation colour stripe
            gfx.fill(px - 2, rowY, px + 2, rowY + ROW_H - 2, 0xFF_000000 | (rel.colour & 0xFFFFFF));

            // Nation colour badge
            gfx.fill(px + 4, rowY + 5, px + 12, rowY + 13, 0xFF_000000 | (other.getColour() & 0xFFFFFF));

            // Bot icon (small ◈)
            if (other.isBot()) {
                gfx.drawString(font, "§8◈", px + 4, rowY + 4, 0xFFAAAAAA, false);
            }

            // Name
            gfx.drawString(font, "§f[" + other.getTag() + "] " + other.getName(),
                    px + 15, rowY + 6, other.getColour() & 0xFFFFFF, false);

            // Relation right-aligned
            String relLabel = rel.displayName;
            int labelW = font.width(relLabel);
            gfx.drawString(font, relLabel, px + listW - 10 - labelW, rowY + 6,
                    rel.colour & 0xFFFFFF, false);
        }
    }

    private void renderRightPanel(GuiGraphics gfx, net.minecraft.client.gui.Font font,
            Nation myNation, int listW) {
        int rx = x + listW + 8;
        int ry = y + 4;

        if (selectedNation == null) {
            gfx.drawCenteredString(font, "§7Select a nation", rx + (RIGHT_W - 16) / 2, y + 40,
                    NationGuiHelper.COL_TEXT_DIM);
            return;
        }

        Nation other = ClientNationData.getNationById(selectedNation);
        if (other == null) {
            selectedNation = null;
            return;
        }

        // Nation header
        int nameCol = other.getColour() & 0xFFFFFF;
        gfx.drawString(font, "§f[" + other.getTag() + "] " + other.getName(), rx, ry, nameCol, false);
        ry += 12;
        gfx.drawString(font, "§7" + other.getMemberCount() + " members"
                + (other.isBot() ? "  §8◈ Bot" : "  §7Player"), rx, ry, NationGuiHelper.COL_TEXT_DIM, false);
        ry += 12;

        // Current relation
        RelationType current = myNation.getRelationWith(selectedNation);
        gfx.drawString(font, "§7Relation: §r" + current.displayName, rx, ry,
                current.colour & 0xFFFFFF, false);
        ry += 4;

        // Divider
        gfx.fill(rx, ry + 10, rx + RIGHT_W - 16, ry + 11, NationGuiHelper.COL_BORDER);
        ry += 14;
        gfx.drawString(font, "§7— Propose ——", rx, ry, NationGuiHelper.COL_TEXT_DIM, false);
        ry += 12;

        NationRank myRank = ClientNationData.getLocalRank();
        if (myRank == null || !myRank.canManageDiplomacy()) {
            gfx.drawString(font, "§cNo permission", rx, ry, NationGuiHelper.COL_DANGER, false);
        }
        // (propose buttons + edit box are rendered as widgets)

        // Check for outgoing request
        List<DiplomacyRequest> outgoing = ClientNationData.getOutgoingRequests();
        outgoing.stream()
                .filter(r -> r.getToNationId().equals(selectedNation))
                .findFirst()
                .ifPresent(r -> {
                    int pendingY = y + h - BOTTOM_H - 50;
                    gfx.fill(rx, pendingY, rx + RIGHT_W - 16, pendingY + 24, 0x30_FFAA00);
                    gfx.drawString(font, "§6⌛ Awaiting response…", rx + 4, pendingY + 4,
                            0xFFAAAA00, false);
                    gfx.drawString(font, "§7Proposed: §r" + r.getProposedType().displayName,
                            rx + 4, pendingY + 14, r.getProposedType().colour & 0xFFFFFF, false);
                });
    }

    private void renderRequests(GuiGraphics gfx, net.minecraft.client.gui.Font font,
            Nation myNation, int listH) {
        int reqY = y + listH;

        // Bottom strip background
        gfx.fill(x, reqY, x + w, y + h, 0x18_000000);
        gfx.fill(x, reqY, x + w, reqY + 1, NationGuiHelper.COL_BORDER);

        List<DiplomacyRequest> incoming = ClientNationData.getIncomingRequests();
        if (incoming.isEmpty()) {
            gfx.drawString(font, "§7No incoming diplomacy requests.", x + 8, reqY + 8,
                    NationGuiHelper.COL_TEXT_DIM, false);
            return;
        }

        gfx.drawString(font, "§6§l⚑ Incoming Requests §7(" + incoming.size() + ")",
                x + 8, reqY + 5, 0xFFFFAA00, false);

        int ry = reqY + 18;
        for (int i = 0; i < Math.min(incoming.size(), 2); i++) {
            DiplomacyRequest req = incoming.get(i);
            Nation from = ClientNationData.getNationById(req.getFromNationId());
            String fromName = from != null ? "[" + from.getTag() + "] " + from.getName() : "Unknown";
            RelationType rt = req.getProposedType();

            gfx.drawString(font, "§f" + fromName + " §7→ §r" + rt.displayName,
                    x + 8, ry, rt.colour & 0xFFFFFF, false);

            String msg = req.getMessage();
            if (!msg.isBlank()) {
                String truncated = msg.length() > 40 ? msg.substring(0, 37) + "…" : msg;
                gfx.drawString(font, "§7\"" + truncated + "\"", x + 8, ry + 10,
                        NationGuiHelper.COL_TEXT_DIM, false);
            }
            ry += REQ_H;
        }

        if (incoming.size() > 2) {
            gfx.drawString(font, "§7+" + (incoming.size() - 2) + " more…",
                    x + 8, ry, NationGuiHelper.COL_TEXT_DIM, false);
        }
    }

    // ── Mouse click ───────────────────────────────────────────────────────────────

    public void onClick(double mouseX, double mouseY) {
        Nation myNation = ClientNationData.getLocalNation();
        if (myNation == null) return;

        List<Nation> all = new ArrayList<>(ClientNationData.getAllNations());
        all.removeIf(n -> n.getId().equals(myNation.getId()));

        int listW  = w - RIGHT_W - 4;
        int listH  = h - BOTTOM_H;
        int startY = y + 16;
        int px = x + 6;

        for (int i = 0; i < all.size(); i++) {
            int rowY = startY + i * ROW_H - scrollOffset;
            if (mouseY >= rowY && mouseY <= rowY + ROW_H - 2
                    && mouseX >= px - 2 && mouseX <= px + listW - 4
                    && rowY >= startY && rowY < y + listH) {
                UUID id = all.get(i).getId();
                selectedNation = id.equals(selectedNation) ? null : id;
                rebuildPropose();
                return;
            }
        }
    }
}
