package dev.nationsforge.client.gui;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationRank;
import dev.nationsforge.network.PacketHandler;
import dev.nationsforge.network.packet.C2SKickMemberPacket;
import dev.nationsforge.network.packet.C2SSetRankPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Tab 1 — "Members"
 * Shows all nation members with their ranks. Leadership can
 * promote/demote/kick.
 */
public class MembersPanel extends NationPanel {

    private static final int ROW_H = 22;

    // Selected member
    private UUID selectedMember = null;
    private final List<Button> actionButtons = new ArrayList<>();

    public MembersPanel(NationsScreen screen, int x, int y, int w, int h) {
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

        Nation nation = ClientNationData.getLocalNation();
        NationRank myRank = ClientNationData.getLocalRank();
        UUID myId = ClientNationData.getLocalPlayerId();
        if (nation == null || myRank == null || selectedMember == null)
            return;

        NationRank targetRank = nation.getRank(selectedMember);
        if (!myRank.outranks(targetRank))
            return; // can't do anything to peers/superiors

        int bx = x + w - 115;
        int by = y + h - 100;

        // Promote
        if (targetRank.level > 0) {
            NationRank higher = NationRank.fromLevel(targetRank.level - 1);
            if (myRank.outranks(higher) || (higher == NationRank.SOVEREIGN && myRank == NationRank.SOVEREIGN)) {
                Button promote = add(Button.builder(Component.literal("§aPromote"),
                        b -> {
                            PacketHandler.sendToServer(new C2SSetRankPacket(selectedMember, higher));
                            selectedMember = null;
                            rebuildActionButtons();
                        })
                        .bounds(bx, by, 110, 20).build());
                actionButtons.add(promote);
                by += 22;
            }
        }
        // Demote
        if (targetRank.level < NationRank.CITIZEN.level && myRank.outranks(targetRank)) {
            NationRank lower = NationRank.fromLevel(targetRank.level + 1);
            Button demote = add(Button.builder(Component.literal("§6Demote"),
                    b -> {
                        PacketHandler.sendToServer(new C2SSetRankPacket(selectedMember, lower));
                        selectedMember = null;
                        rebuildActionButtons();
                    })
                    .bounds(bx, by, 110, 20).build());
            actionButtons.add(demote);
            by += 22;
        }
        // Kick
        if (myRank.canKickMembers()) {
            Button kick = add(Button.builder(Component.literal("§cKick"),
                    b -> {
                        PacketHandler.sendToServer(new C2SKickMemberPacket(selectedMember));
                        selectedMember = null;
                        rebuildActionButtons();
                    })
                    .bounds(bx, by, 110, 20).build());
            actionButtons.add(kick);
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        Nation nation = ClientNationData.getLocalNation();
        var font = screen.getFont();

        if (nation == null) {
            gfx.drawCenteredString(font, "§7You are not in a nation.", x + w / 2, y + h / 2,
                    NationGuiHelper.COL_TEXT_DIM);
            return;
        }

        int px = x + 8;
        int py = y + 6;

        gfx.drawString(font, "§7Members of §f" + nation.getName()
                + " §7(" + nation.getMemberCount() + ")", px, py, NationGuiHelper.COL_TEXT, false);
        py += 14;

        // List area
        int listH = h - 30;
        int maxScroll = Math.max(0, nation.getMemberCount() * ROW_H - listH);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        // Clip / scissor would be ideal; we approximate with a draw bounds check
        int startY = py;
        int row = 0;
        for (Map.Entry<UUID, NationRank> entry : nation.getMembers().entrySet()) {
            int rowY = startY + row * ROW_H - scrollOffset;
            row++;
            if (rowY + ROW_H < startY || rowY > startY + listH)
                continue;

            UUID uid = entry.getKey();
            NationRank rank = entry.getValue();
            boolean selected = uid.equals(selectedMember);
            boolean isMe = uid.equals(ClientNationData.getLocalPlayerId());

            int rowBg = selected ? NationGuiHelper.COL_PANEL : (row % 2 == 0 ? 0x18_FFFFFF : 0x00_000000);
            gfx.fill(px, rowY, px + w - 130, rowY + ROW_H - 2, rowBg);

            // Rank colour badge
            int rCol = rank.colour & 0xFFFFFF;
            gfx.fill(px, rowY + 3, px + 4, rowY + ROW_H - 5, 0xFF_000000 | rCol);

            String name = getPlayerName(uid);
            if (isMe)
                name = "§e" + name + " §7(you)";
            gfx.drawString(font, name, px + 8, rowY + 7, NationGuiHelper.COL_TEXT, false);
            gfx.drawString(font, rank.displayName, px + 180, rowY + 7, rCol, false);

            // Click detection
            if (mouseX >= px && mouseX <= px + w - 130
                    && mouseY >= rowY && mouseY <= rowY + ROW_H - 2
                    && !isMe) {
                gfx.fill(px, rowY, px + w - 130, rowY + ROW_H - 2, 0x30_FFFFFF);
            }
        }

        // Right-side: selected member info
        if (selectedMember != null) {
            Nation n = nation;
            NationRank sr = n.getRank(selectedMember);
            int rx = x + w - 120;
            gfx.drawString(font, "§7Selected:", rx, y + 10, NationGuiHelper.COL_TEXT_DIM, false);
            gfx.drawString(font, getPlayerName(selectedMember), rx, y + 22, NationGuiHelper.COL_TEXT, false);
            gfx.drawString(font, "§7Rank: §r" + sr.displayName, rx, y + 34, sr.colour & 0xFFFFFF, false);
        }
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    // Handle clicks on member rows (called from NationsScreen mouse click)
    public void onClick(double mouseX, double mouseY) {
        Nation nation = ClientNationData.getLocalNation();
        if (nation == null)
            return;
        int px = x + 8;
        int startY = y + 20;
        int row = 0;
        for (UUID uid : nation.getMembers().keySet()) {
            int rowY = startY + row * ROW_H - scrollOffset;
            row++;
            if (mouseY >= rowY && mouseY <= rowY + ROW_H - 2
                    && mouseX >= px && mouseX <= px + w - 130) {
                if (!uid.equals(ClientNationData.getLocalPlayerId())) {
                    selectedMember = uid.equals(selectedMember) ? null : uid;
                    rebuildActionButtons();
                }
                return;
            }
        }
    }

    private String getPlayerName(UUID uid) {
        Minecraft mc = Minecraft.getInstance();
        var profile = mc.getConnection() == null ? null
                : mc.getConnection().getPlayerInfo(uid);
        return profile != null ? profile.getProfile().getName()
                : uid.toString().substring(0, 8) + "…";
    }
}
