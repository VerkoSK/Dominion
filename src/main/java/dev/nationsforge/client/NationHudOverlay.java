package dev.nationsforge.client;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationPowerCalculator;
import dev.nationsforge.nation.NationRank;
import dev.nationsforge.nation.RelationType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders a compact nations status badge in the top-right corner of the HUD.
 *
 * ┌─────────────────────┐
 * │ [NTG] Nation Name │
 * │ Rank: General │
 * │ ⚔ At war: 2 │
 * │ ♦ Allies: 1 │
 * └─────────────────────┘
 */
@Mod.EventBusSubscriber(modid = dev.nationsforge.NationsForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class NationHudOverlay {

    private static final int PAD = 4;
    private static final int LINE = 10;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui)
            return;

        // Set local player if not already set
        ClientNationData.setLocalPlayer(mc.player.getUUID());

        Nation nation = ClientNationData.getLocalNation();
        if (nation == null)
            return;

        NationRank rank = ClientNationData.getLocalRank();
        GuiGraphics gfx = event.getGuiGraphics();
        var font = mc.font;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int panelW = 130;
        int px = screenW - panelW - 4;
        int py = 4;

        // Count wars and allies
        long wars = nation.getRelations().values().stream()
                .filter(r -> r.getType() == RelationType.WAR).count();
        long allies = nation.getRelations().values().stream()
                .filter(r -> r.getType() == RelationType.ALLIANCE).count();

        // Power tier
        NationPowerCalculator.Tier tier = NationPowerCalculator.getTier(nation.getPower());
        long territory = nation.getTerritory();

        int lines = 1 + 1 + (rank != null ? 1 : 0) + (territory > 0 ? 1 : 0) + (wars > 0 ? 1 : 0)
                + (allies > 0 ? 1 : 0);
        int panelH = PAD * 2 + lines * LINE + (lines - 1) * 2;

        // Background
        gfx.fill(px - PAD, py - PAD, px + panelW, py + panelH, 0xB0_101418);
        // Border
        gfx.fill(px - PAD, py - PAD, px + panelW, py - PAD + 1, 0xFF_2A4060);
        gfx.fill(px - PAD, py + panelH - 1, px + panelW, py + panelH, 0xFF_2A4060);
        gfx.fill(px - PAD, py - PAD, px - PAD + 1, py + panelH, 0xFF_2A4060);
        gfx.fill(px + panelW - 1, py - PAD, px + panelW, py + panelH, 0xFF_2A4060);

        // Nation colour accent bar (left edge)
        int accent = 0xFF_000000 | (nation.getColour() & 0xFFFFFF);
        gfx.fill(px - PAD, py - PAD, px - PAD + 3, py + panelH, accent);

        int row = 0;

        // Nation tag + name
        int tagCol = nation.getColour() & 0xFFFFFF;
        gfx.drawString(font, "§7[" + nation.getTag() + "] §f" + nation.getName(),
                px, py + row * (LINE + 2), tagCol, true);
        row++;

        // Power tier badge
        gfx.drawString(font, "✦ " + tier.displayName,
                px, py + row * (LINE + 2), tier.colour & 0xFFFFFF, true);
        row++;

        // Rank
        if (rank != null) {
            gfx.drawString(font, "§7" + rank.displayName, px, py + row * (LINE + 2),
                    rank.colour & 0xFFFFFF, true);
            row++;
        }

        // Territory
        if (territory > 0) {
            gfx.drawString(font, "§7⬛ " + territory + " chunk(s)", px, py + row * (LINE + 2),
                    0xFF_AAAAAA, true);
            row++;
        }

        // Wars
        if (wars > 0) {
            gfx.drawString(font, "§c⚔ At war: " + wars, px, py + row * (LINE + 2),
                    0xFF_FF4444, true);
            row++;
        }

        // Allies
        if (allies > 0) {
            gfx.drawString(font, "§a♦ Allies: " + allies, px, py + row * (LINE + 2),
                    0xFF_44BB66, true);
        }
    }
}
