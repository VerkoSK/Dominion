package dev.nationsforge.client.gui;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.RelationType;
import dev.nationsforge.network.PacketHandler;
import dev.nationsforge.network.packet.C2SJoinNationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Tab 3 — "Browse"
 * Shows all nations in the world.
 * Players without a nation can join open ones or accept pending invitations.
 * Shows a "Create Nation" button for players without a nation.
 */
public class BrowsePanel extends NationPanel {

    private static final int ROW_H = 28;

    private UUID selectedNation = null;
    private Button btnJoin;
    private Button btnCreate;

    public BrowsePanel(NationsScreen screen, int x, int y, int w, int h) {
        super(screen, x, y, w, h);
    }

    @Override
    public void addWidgets() {
        boolean hasNation = ClientNationData.localPlayerHasNation();

        if (!hasNation) {
            btnCreate = add(Button.builder(Component.literal("§a+ Create Nation"),
                    b -> screen.mc().setScreen(new CreateNationScreen(screen)))
                    .bounds(x + w - 118, y + h - 26, 113, 20).build());
        }

        rebuildJoinButton();
    }

    private void rebuildJoinButton() {
        if (btnJoin != null) {
            screen.removeWidget(btnJoin);
            ownWidgets.remove(btnJoin);
            btnJoin = null;
        }

        if (selectedNation == null || ClientNationData.localPlayerHasNation())
            return;
        Nation n = ClientNationData.getNationById(selectedNation);
        if (n == null)
            return;

        boolean canJoin = n.isOpenRecruitment() || n.hasInvite(ClientNationData.getLocalPlayerId());
        if (!canJoin)
            return;

        btnJoin = add(Button.builder(Component.literal("§aJoin"),
                b -> {
                    PacketHandler.sendToServer(new C2SJoinNationPacket(selectedNation));
                    screen.onClose();
                })
                .bounds(x + w - 118, y + h - 50, 113, 20).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        var font = screen.getFont();
        int px = x + 8;
        int py = y + 6;

        // Pending invites banner
        List<Nation> invited = ClientNationData.getPendingInvites();
        if (!invited.isEmpty()) {
            NationGuiHelper.drawPanel(gfx, px, py, w - 130, 14,
                    0xBB_002200, NationGuiHelper.COL_SUCCESS);
            gfx.drawString(font, "§a✉ You have " + invited.size() + " pending invitation(s)!",
                    px + 4, py + 3, NationGuiHelper.COL_SUCCESS, false);
            py += 20;
        }

        List<Nation> allNations = new ArrayList<>(ClientNationData.getAllNations());
        // Sort: invited first, then by score
        UUID myId = ClientNationData.getLocalPlayerId();
        allNations.sort(Comparator
                .comparingInt((Nation n) -> n.hasInvite(myId) ? 0 : 1)
                .thenComparingLong(n -> -n.getScore()));

        int startY = py;
        int listW = w - 130;
        int maxScroll = Math.max(0, allNations.size() * ROW_H - (h - 40));
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < allNations.size(); i++) {
            Nation nation = allNations.get(i);
            int rowY = startY + i * ROW_H - scrollOffset;
            if (rowY + ROW_H < startY || rowY > startY + h - 30)
                continue;

            boolean sel = nation.getId().equals(selectedNation);
            boolean isMyNation = nation.getId().equals(
                    ClientNationData.getLocalNation() != null ? ClientNationData.getLocalNation().getId() : null);
            boolean hasInvite = nation.hasInvite(myId);

            int bg = sel ? NationGuiHelper.COL_PANEL : (i % 2 == 0 ? 0x12_FFFFFF : 0x00_000000);
            gfx.fill(px, rowY, px + listW, rowY + ROW_H - 2, bg);

            // Nation colour stripe
            gfx.fill(px, rowY, px + 4, rowY + ROW_H - 2, 0xFF_000000 | (nation.getColour() & 0xFFFFFF));

            // Colour swatch
            gfx.fill(px + 6, rowY + 6, px + 18, rowY + 18, 0xFF_000000 | (nation.getColour() & 0xFFFFFF));

            // Name + tag
            gfx.drawString(font, "[" + nation.getTag() + "] " + nation.getName(),
                    px + 22, rowY + 4, nation.getColour() & 0xFFFFFF, false);
            gfx.drawString(font, "§7" + nation.getMemberCount() + " members  •  Score: "
                    + NationGuiHelper.abbreviate(nation.getScore()),
                    px + 22, rowY + 16, NationGuiHelper.COL_TEXT_DIM, false);

            // Tags
            if (isMyNation) {
                gfx.drawString(font, "§a[YOUR NATION]", px + listW - 90, rowY + 4, NationGuiHelper.COL_SUCCESS, false);
            } else if (hasInvite) {
                gfx.drawString(font, "§e[INVITED]", px + listW - 70, rowY + 4, 0xFF_FFEE00, false);
            } else if (nation.isOpenRecruitment()) {
                gfx.drawString(font, "§7[OPEN]", px + listW - 45, rowY + 16, NationGuiHelper.COL_TEXT_DIM, false);
            }

            if (mouseX >= px && mouseX <= px + listW && mouseY >= rowY && mouseY <= rowY + ROW_H - 2) {
                gfx.fill(px, rowY, px + listW, rowY + ROW_H - 2, 0x28_FFFFFF);
            }
        }

        // Right panel — selected nation info
        if (selectedNation != null) {
            Nation n = ClientNationData.getNationById(selectedNation);
            if (n != null) {
                int rx = x + w - 120;
                gfx.fill(rx, y + 4, rx + 112, y + h - 30, NationGuiHelper.COL_PANEL);

                gfx.fill(rx, y + 4, rx + 112, y + 20, 0xFF_000000 | (n.getColour() & 0xFFFFFF));
                gfx.drawString(font, n.getName(), rx + 4, y + 8, 0xFF_FFFFFF, false);

                gfx.drawString(font, "§7[" + n.getTag() + "]", rx + 4, y + 24, n.getColour() & 0xFFFFFF, false);
                gfx.drawString(font, "§7Members: §f" + n.getMemberCount(), rx + 4, y + 36, NationGuiHelper.COL_TEXT,
                        false);
                gfx.drawString(font, "§7Score: §b" + NationGuiHelper.abbreviate(n.getScore()), rx + 4, y + 48,
                        NationGuiHelper.COL_TEXT, false);

                String recr = n.isOpenRecruitment() ? "§aOpen" : "§cInvite-only";
                gfx.drawString(font, "§7Recruits: " + recr, rx + 4, y + 60, NationGuiHelper.COL_TEXT, false);

                if (!n.getDescription().isBlank()) {
                    String d = n.getDescription();
                    // wrap at ~16 chars per line (approx)
                    String[] words = d.split(" ");
                    StringBuilder line = new StringBuilder();
                    int ly = y + 78;
                    for (String word : words) {
                        if (font.width(line + word) > 108) {
                            gfx.drawString(font, "§7§o" + line, rx + 4, ly, NationGuiHelper.COL_TEXT_DIM, false);
                            ly += 10;
                            line = new StringBuilder();
                        }
                        line.append(word).append(" ");
                    }
                    if (!line.isEmpty()) {
                        gfx.drawString(font, "§7§o" + line, rx + 4, ly, NationGuiHelper.COL_TEXT_DIM, false);
                    }
                }
            }
        }

        if (allNations.isEmpty()) {
            gfx.drawCenteredString(font, "§7No nations exist yet. Create the first one!",
                    x + (w - 130) / 2 + x, y + h / 2, NationGuiHelper.COL_TEXT_DIM);
        }
    }

    public void onClick(double mouseX, double mouseY) {
        List<Nation> allNations = new ArrayList<>(ClientNationData.getAllNations());
        int px = x + 8;
        int startY = y + 6;
        int listW = w - 130;
        UUID myId = ClientNationData.getLocalPlayerId();
        List<Nation> invited = ClientNationData.getPendingInvites();
        if (!invited.isEmpty())
            startY += 20;

        for (int i = 0; i < allNations.size(); i++) {
            int rowY = startY + i * ROW_H - scrollOffset;
            if (mouseY >= rowY && mouseY <= rowY + ROW_H - 2
                    && mouseX >= px && mouseX <= px + listW) {
                UUID id = allNations.get(i).getId();
                selectedNation = id.equals(selectedNation) ? null : id;
                rebuildJoinButton();
                return;
            }
        }
    }
}
