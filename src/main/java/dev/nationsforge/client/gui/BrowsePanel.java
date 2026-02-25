package dev.nationsforge.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationFlag;
import dev.nationsforge.nation.NationPowerCalculator;
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
 * Shows all nations in the world. Players without a nation can join open ones.
 * The right panel shows the selected nation's flag, tier, full stats and relations.
 */
public class BrowsePanel extends NationPanel {

    // List row height (slightly taller for flag swatch + mini-stats)
    private static final int ROW_H = 32;
    // Width of the right detail panel
    private static final int DETAIL_W = 164;

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
        if (selectedNation == null || ClientNationData.localPlayerHasNation()) return;
        Nation n = ClientNationData.getNationById(selectedNation);
        if (n == null) return;
        boolean canJoin = n.isOpenRecruitment() || n.hasInvite(ClientNationData.getLocalPlayerId());
        if (!canJoin) return;
        btnJoin = add(Button.builder(Component.literal("§aJoin"),
                b -> {
                    PacketHandler.sendToServer(new C2SJoinNationPacket(selectedNation));
                    screen.onClose();
                })
                .bounds(x + w - 118, y + h - 50, 113, 20).build());
    }

    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        var font = screen.getFont();
        int px = x + 8;
        int py = y + 6;
        int listW = w - DETAIL_W - 6;

        // ── Pending invites banner ────────────────────────────────────────────────
        List<Nation> invited = ClientNationData.getPendingInvites();
        if (!invited.isEmpty()) {
            NationGuiHelper.drawPanel(gfx, px, py, listW, 14,
                    0xBB_002200, NationGuiHelper.COL_SUCCESS);
            gfx.drawString(font, "§a✉ You have " + invited.size() + " pending invitation(s)!",
                    px + 4, py + 3, NationGuiHelper.COL_SUCCESS, false);
            py += 20;
        }

        // ── Nation list ───────────────────────────────────────────────────────────
        List<Nation> allNations = new ArrayList<>(ClientNationData.getAllNations());
        UUID myId = ClientNationData.getLocalPlayerId();
        allNations.sort(Comparator
                .comparingInt((Nation n) -> n.hasInvite(myId) ? 0 : 1)
                .thenComparingLong(n -> -n.getScore()));

        int startY = py;
        int maxScroll = Math.max(0, allNations.size() * ROW_H - (h - 40));
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < allNations.size(); i++) {
            Nation nation = allNations.get(i);
            int rowY = startY + i * ROW_H - scrollOffset;
            if (rowY + ROW_H < startY || rowY > startY + h - 30) continue;

            boolean sel = nation.getId().equals(selectedNation);
            boolean isMyNation = nation.getId().equals(
                    ClientNationData.getLocalNation() != null
                            ? ClientNationData.getLocalNation().getId() : null);
            boolean hasInvite = nation.hasInvite(myId);
            int rgb24 = nation.getColour() & 0xFFFFFF;

            // Row background
            int bg = sel ? NationGuiHelper.COL_PANEL : (i % 2 == 0 ? 0x18_FFFFFF : 0x00_000000);
            gfx.fill(px, rowY, px + listW, rowY + ROW_H - 2, bg);

            // Colour stripe (left edge)
            gfx.fill(px, rowY, px + 3, rowY + ROW_H - 2, 0xFF_000000 | rgb24);

            // Colour swatch circle-ish (14×14)
            gfx.fill(px + 6, rowY + 5, px + 20, rowY + 19, 0xFF_000000 | rgb24);
            gfx.fill(px + 6, rowY + 5, px + 20, rowY + 6, 0x40_FFFFFF);

            // Bot badge
            if (nation.isBot()) {
                gfx.drawString(font, "§8[AI]", px + 6, rowY + 21, 0xFF_556677, false);
            }

            // Name + tag
            gfx.drawString(font, "§f[" + nation.getTag() + "] " + nation.getName(),
                    px + 24, rowY + 4, rgb24, false);

            // Mini stats row
            gfx.drawString(font, "§7" + nation.getMemberCount() + "§8 mbr  "
                    + "§a" + nation.getTerritory() + "§8 chunks  "
                    + "§b" + NationGuiHelper.abbreviate(nation.getScore()) + "§8 pts",
                    px + 24, rowY + 18, NationGuiHelper.COL_TEXT_DIM, false);

            // Status badges (right-aligned in list)
            if (isMyNation) {
                gfx.drawString(font, "§a[YOURS]", px + listW - 52, rowY + 4,
                        NationGuiHelper.COL_SUCCESS, false);
            } else if (hasInvite) {
                gfx.drawString(font, "§e[INVITED]", px + listW - 62, rowY + 4, 0xFF_FFDD00, false);
            } else if (nation.isOpenRecruitment()) {
                gfx.drawString(font, "§7[OPEN]", px + listW - 44, rowY + 18,
                        NationGuiHelper.COL_TEXT_DIM, false);
            }

            // Hover highlight
            if (mouseX >= px && mouseX <= px + listW && mouseY >= rowY && mouseY <= rowY + ROW_H - 2) {
                gfx.fill(px, rowY, px + listW, rowY + ROW_H - 2, 0x22_FFFFFF);
            }
        }

        if (allNations.isEmpty()) {
            gfx.drawCenteredString(font, "§7No nations yet. Create the first one!",
                    px + listW / 2, y + h / 2, NationGuiHelper.COL_TEXT_DIM);
        }

        // ── Right detail panel ────────────────────────────────────────────────────
        if (selectedNation != null) {
            Nation n = ClientNationData.getNationById(selectedNation);
            if (n != null) renderDetailPanel(gfx, font, n);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private void renderDetailPanel(GuiGraphics gfx, net.minecraft.client.gui.Font font, Nation n) {
        int rx = x + w - DETAIL_W;
        int ry = y + 4;
        int rh = h - 34;
        int rw = DETAIL_W - 4;
        int rgb24 = n.getColour() & 0xFFFFFF;

        // ── Panel background ──────────────────────────────────────────────────────
        NationGuiHelper.drawPanel(gfx, rx, ry, rw, rh, NationGuiHelper.COL_PANEL, NationGuiHelper.COL_BORDER);

        // ── Colour header band ────────────────────────────────────────────────────
        int headerH = 18;
        gfx.fill(rx + 1, ry + 1, rx + rw - 1, ry + headerH, 0xCC_000000 | (rgb24 & 0xFFFFFF));
        gfx.fill(rx + 1, ry + 1, rx + rw - 1, ry + 2, 0x50_FFFFFF); // sheen

        // Nation name in header
        String headerName = n.getName().length() > 16
                ? n.getName().substring(0, 14) + "…" : n.getName();
        gfx.drawString(font, "§f§l" + headerName, rx + 4, ry + 5, 0xFF_FFFFFF, false);

        // ── Flag (32×32 = 2× scaled 16×16 item render) ───────────────────────────
        NationFlag flag = n.getFlag();
        int flagX = rx + 4;
        int flagY = ry + headerH + 4;
        if (flag != null) {
            var flagStack = flag.buildBannerStack();
            PoseStack pose = gfx.pose();
            pose.pushPose();
            pose.translate(flagX, flagY, 0);
            pose.scale(2.0f, 2.0f, 1.0f);
            gfx.renderItem(flagStack, 0, 0);
            pose.popPose();
        }

        // ── Tag + Tier (right of flag) ────────────────────────────────────────────
        int infoX = flagX + 36;
        int infoY = flagY;
        gfx.drawString(font, "§7[§f" + n.getTag() + "§7]", infoX, infoY, rgb24, false);

        NationPowerCalculator.Tier tier = NationPowerCalculator.getTier(n.getPower());
        int tierCol = tier.colour & 0xFFFFFF;
        gfx.drawString(font, "§7Tier: ", infoX, infoY + 11, NationGuiHelper.COL_TEXT_DIM, false);
        gfx.drawString(font, tier.displayName, infoX + font.width("Tier: "), infoY + 11, tierCol, false);

        if (n.isBot()) {
            gfx.drawString(font, "§8[BOT NATION]", infoX, infoY + 22, 0xFF_556677, false);
        }

        // ── Divider ──────────────────────────────────────────────────────────────
        int statsY = flagY + 36;
        gfx.fill(rx + 4, statsY, rx + rw - 4, statsY + 1, NationGuiHelper.COL_BORDER);
        statsY += 4;

        // ── Stats ────────────────────────────────────────────────────────────────
        int tx = rx + 5;
        int lineH = 11;

        gfx.drawString(font, "§7Members: §f" + n.getMemberCount(),
                tx, statsY, NationGuiHelper.COL_TEXT, false);
        statsY += lineH;

        gfx.drawString(font, "§7Territory: §a" + n.getTerritory() + " chunks",
                tx, statsY, NationGuiHelper.COL_TEXT, false);
        statsY += lineH;

        gfx.drawString(font, "§7Treasury: §e⚙ " + NationGuiHelper.abbreviate(n.getTreasury()),
                tx, statsY, NationGuiHelper.COL_TEXT, false);
        statsY += lineH;

        gfx.drawString(font, "§7Score: §b" + NationGuiHelper.abbreviate(n.getScore()),
                tx, statsY, NationGuiHelper.COL_TEXT, false);
        statsY += lineH;

        String recr = n.isOpenRecruitment() ? "§aOpen" : "§cInvite-only";
        gfx.drawString(font, "§7Recruits: " + recr, tx, statsY, NationGuiHelper.COL_TEXT, false);
        statsY += lineH;

        // ── Relations strip ───────────────────────────────────────────────────────
        long wars    = n.getRelations().values().stream().filter(r -> r.getType() == RelationType.WAR).count();
        long allies  = n.getRelations().values().stream().filter(r -> r.getType() == RelationType.ALLIANCE).count();
        long trades  = n.getRelations().values().stream().filter(r -> r.getType() == RelationType.TRADE_PACT).count();
        long rivals  = n.getRelations().values().stream().filter(r -> r.getType() == RelationType.RIVALRY).count();

        statsY += 3;
        gfx.fill(rx + 4, statsY, rx + rw - 4, statsY + 1, NationGuiHelper.COL_BORDER);
        statsY += 4;

        gfx.drawString(font, "§7Relations:", tx, statsY, NationGuiHelper.COL_TEXT_DIM, false);
        statsY += lineH;

        gfx.drawString(font, "§c⚔ " + wars + "  §a★ " + allies + "  §b⚡ " + trades + "  §6≠ " + rivals,
                tx, statsY, NationGuiHelper.COL_TEXT, false);
        statsY += lineH + 2;

        // ── Capital (bot nations only) ────────────────────────────────────────────
        if (n.isBot() && (n.getCapitalX() != 0 || n.getCapitalZ() != 0)) {
            gfx.fill(rx + 4, statsY, rx + rw - 4, statsY + 1, NationGuiHelper.COL_BORDER);
            statsY += 4;
            gfx.drawString(font, "§7Capital: §8(" + n.getCapitalX() + ", " + n.getCapitalZ() + ")",
                    tx, statsY, NationGuiHelper.COL_TEXT_DIM, false);
            statsY += lineH + 2;
        }

        // ── Description ───────────────────────────────────────────────────────────
        String desc = n.getDescription();
        if (desc != null && !desc.isBlank()) {
            gfx.fill(rx + 4, statsY, rx + rw - 4, statsY + 1, NationGuiHelper.COL_BORDER);
            statsY += 4;
            int maxCharsW = rw - 10;
            String[] words = desc.split(" ");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (font.width(line + word) > maxCharsW) {
                    gfx.drawString(font, "§7§o" + line.toString().trim(),
                            tx, statsY, NationGuiHelper.COL_TEXT_DIM, false);
                    statsY += 10;
                    if (statsY > ry + rh - 20) break; // overflow guard
                    line = new StringBuilder();
                }
                line.append(word).append(" ");
            }
            if (!line.isEmpty() && statsY <= ry + rh - 20) {
                gfx.drawString(font, "§7§o" + line.toString().trim(),
                        tx, statsY, NationGuiHelper.COL_TEXT_DIM, false);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────

    public void onClick(double mouseX, double mouseY) {
        List<Nation> allNations = new ArrayList<>(ClientNationData.getAllNations());
        int px = x + 8;
        int startY = y + 6;
        int listW = w - DETAIL_W - 6;
        List<Nation> invited = ClientNationData.getPendingInvites();
        if (!invited.isEmpty()) startY += 20;

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
