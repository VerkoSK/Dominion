package dev.nationsforge.client.gui;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationRank;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/**
 * Static helpers for consistent UI rendering across all NationsForge screens.
 */
public final class NationGuiHelper {

    // ── Colour palette ───────────────────────────────────────────────────────────
    public static final int COL_BG = 0xD0_101418; // dark panel
    public static final int COL_PANEL = 0xCC_1A2030; // inner panel
    public static final int COL_HEADER = 0xDD_0D1520; // header strip
    public static final int COL_BORDER = 0xFF_2A4060; // border blue
    public static final int COL_BORDER_HOT = 0xFF_5599FF; // hover border
    public static final int COL_TAB = 0xBB_1E2840; // inactive tab
    public static final int COL_TAB_ACTIVE = 0xDD_2A4070; // active tab
    public static final int COL_TEXT = 0xFF_E0E8F0; // normal text
    public static final int COL_TEXT_DIM = 0xFF_7090A0; // dim text
    public static final int COL_ACCENT = 0xFF_5599FF; // highlight
    public static final int COL_WARNING = 0xFF_FF8844;
    public static final int COL_DANGER = 0xFF_FF3344;
    public static final int COL_SUCCESS = 0xFF_44BB66;

    private NationGuiHelper() {
    }

    /** Draw a filled rounded-looking panel with a 1px border. */
    public static void drawPanel(GuiGraphics gfx, int x, int y, int w, int h, int fill, int border) {
        gfx.fill(x, y, x + w, y + h, fill);
        // top / bottom
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        // left / right
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);
    }

    /** Draw a header band with text. */
    public static void drawHeader(GuiGraphics gfx, net.minecraft.client.gui.Font font,
            String text, int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, COL_HEADER);
        gfx.fill(x, y + h - 1, x + w, y + h, COL_BORDER);
        int textW = font.width(text);
        gfx.drawString(font, text, x + (w - textW) / 2, y + (h - 8) / 2, COL_TEXT, false);
    }

    /** Draw a small nation colour badge + tag + name. */
    public static void drawNationBadge(GuiGraphics gfx, net.minecraft.client.gui.Font font,
            Nation nation, int x, int y) {
        int rgb24 = nation.getColour() & 0xFFFFFF;
        // Colour swatch (12×12)
        gfx.fill(x, y, x + 12, y + 12, 0xFF_000000 | rgb24);
        gfx.fill(x, y, x + 12, y + 1, 0x40_FFFFFF); // highlight
        // Tag
        gfx.drawString(font, "§7[§r" + colouredTag(nation) + "§7]", x + 16, y + 2, COL_TEXT, false);
        // Name
        gfx.drawString(font, nation.getName(), x + 16 + font.width("[" + nation.getTag() + "] ") + 2, y + 2, COL_TEXT,
                false);
    }

    public static String colouredTag(Nation nation) {
        int rgb = nation.getColour() & 0xFFFFFF;
        return String.format("§#%06X%s§r", rgb, nation.getTag());
    }

    public static String rankColoured(NationRank rank) {
        int rgb = rank.colour & 0xFFFFFF;
        return String.format("§#%06X%s§r", rgb, rank.displayName);
    }

    /** Abbreviate a number: 1 200 → 1.2K etc. */
    public static String abbreviate(long n) {
        if (n < 1_000)
            return Long.toString(n);
        if (n < 1_000_000)
            return String.format(Locale.ROOT, "%.1fK", n / 1_000.0);
        return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
    }

    /** Integer → #RRGGBB string for EditBox defaults. */
    public static String toHex(int colour) {
        return String.format("%06X", colour & 0xFFFFFF);
    }

    /** Parse "#RRGGBB" or "RRGGBB" → packed int (no alpha). */
    public static int parseHex(String s) {
        try {
            s = s.trim().replace("#", "");
            return (int) (Long.parseLong(s, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return 0xFF5599;
        }
    }
}
