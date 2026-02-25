package dev.nationsforge.client.gui;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationRank;
import dev.nationsforge.network.PacketHandler;
import dev.nationsforge.network.packet.C2SUpdateSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Tab 4 — "Settings"
 * Lets SOVEREIGN / CHANCELLOR edit nation name, tag, colour, description
 * and open-recruitment flag.
 */
public class SettingsPanel extends NationPanel {

    private EditBox inputName;
    private EditBox inputTag;
    private EditBox inputColour;
    private EditBox inputDesc;
    private Checkbox checkOpen;
    private Button btnSave;

    public SettingsPanel(NationsScreen screen, int x, int y, int w, int h) {
        super(screen, x, y, w, h);
    }

    @Override
    public void addWidgets() {
        Nation nation = ClientNationData.getLocalNation();
        NationRank rank = ClientNationData.getLocalRank();
        if (nation == null || rank == null || !rank.canEditSettings())
            return;

        var font = screen.getFont();
        int lx = x + 10;
        int ly = y + 20;
        int fieldW = 150;

        inputName = add(new EditBox(font, lx + 80, ly, fieldW, 16, Component.literal("Name")));
        inputName.setValue(nation.getName());
        inputName.setMaxLength(32);
        ly += 22;

        inputTag = add(new EditBox(font, lx + 80, ly, 60, 16, Component.literal("Tag")));
        inputTag.setValue(nation.getTag());
        inputTag.setMaxLength(5);
        ly += 22;

        inputColour = add(new EditBox(font, lx + 80, ly, 80, 16, Component.literal("Colour")));
        inputColour.setValue(NationGuiHelper.toHex(nation.getColour()));
        inputColour.setMaxLength(6);
        ly += 22;

        inputDesc = add(new EditBox(font, lx + 80, ly, fieldW + 30, 16, Component.literal("Description")));
        inputDesc.setValue(nation.getDescription());
        inputDesc.setMaxLength(256);
        ly += 22;

        checkOpen = add(new Checkbox(lx, ly, 160, 20,
                Component.literal("Open Recruitment"), nation.isOpenRecruitment()));
        ly += 26;

        btnSave = add(Button.builder(Component.literal("§aSave Changes"), b -> saveSettings())
                .bounds(lx, ly, 120, 20).build());
        // Flag editor button
        add(Button.builder(Component.literal("\u00a7b\u2691 Edit Flag"), b -> {
                    var mc = screen.getMinecraft();
                    if (mc != null) mc.setScreen(new FlagEditorScreen(screen));
                })
                .bounds(lx + 128, ly, 100, 20).build());    }

    private void saveSettings() {
        if (inputName == null)
            return;
        String name = inputName.getValue().trim();
        String tag = inputTag.getValue().trim();
        int colour = NationGuiHelper.parseHex(inputColour.getValue());
        String desc = inputDesc.getValue().trim();
        boolean open = checkOpen.selected();
        PacketHandler.sendToServer(new C2SUpdateSettingsPacket(name, tag, colour, desc, open));
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        Nation nation = ClientNationData.getLocalNation();
        NationRank rank = ClientNationData.getLocalRank();
        var font = screen.getFont();

        if (nation == null) {
            gfx.drawCenteredString(font, "§7You are not in a nation.", x + w / 2, y + h / 2,
                    NationGuiHelper.COL_TEXT_DIM);
            return;
        }
        if (rank == null || !rank.canEditSettings()) {
            gfx.drawCenteredString(font, "§cOnly Sovereign or Chancellor can edit settings.",
                    x + w / 2, y + h / 2, NationGuiHelper.COL_DANGER);
            return;
        }

        int lx = x + 10;
        int ly = y + 8;

        gfx.drawString(font, "§7Nation Settings", lx, ly, NationGuiHelper.COL_TEXT, false);
        ly += 18;

        // Labels
        gfx.drawString(font, "§7Name:", lx, ly + 4, NationGuiHelper.COL_TEXT_DIM, false);
        ly += 22;
        gfx.drawString(font, "§7Tag:", lx, ly + 4, NationGuiHelper.COL_TEXT_DIM, false);
        ly += 22;

        // Colour preview swatch next to input
        if (inputColour != null) {
            int col = NationGuiHelper.parseHex(inputColour.getValue());
            gfx.fill(lx + 80 + 86, ly + 2, lx + 80 + 98, ly + 14, 0xFF_000000 | col);
        }
        gfx.drawString(font, "§7Colour §7(hex):", lx, ly + 4, NationGuiHelper.COL_TEXT_DIM, false);
        ly += 22;
        gfx.drawString(font, "§7Description:", lx, ly + 4, NationGuiHelper.COL_TEXT_DIM, false);
    }
}
