package dev.nationsforge.client.gui;

import dev.nationsforge.network.PacketHandler;
import dev.nationsforge.network.packet.C2SCreateNationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Modal screen for creating a new nation.
 * Features: live colour preview banner, character counters, field validation.
 */
public class CreateNationScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 240;

    private final Screen parent;

    private EditBox inputName;
    private EditBox inputTag;
    private EditBox inputColour;
    private EditBox inputDesc;
    private Checkbox checkOpen;
    private Button btnCreate;
    private Button btnCancel;

    private String errorMsg = "";

    public CreateNationScreen(Screen parent) {
        super(Component.translatable("screen.nationsforge.create"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int panelLeft = cx - PANEL_W / 2;
        int top = height / 2 - PANEL_H / 2;
        int fieldX = panelLeft + 80;
        int fieldW = PANEL_W - 88;

        inputName = addRenderableWidget(new EditBox(font, fieldX, top + 30, fieldW, 16,
                Component.literal("Nation Name")));
        inputName.setMaxLength(32);
        inputName.setHint(Component.literal("§8e.g. The Ironclad Republic"));

        inputTag = addRenderableWidget(new EditBox(font, fieldX, top + 56, 70, 16,
                Component.literal("Tag")));
        inputTag.setMaxLength(5);
        inputTag.setHint(Component.literal("§8e.g. TIR"));

        inputColour = addRenderableWidget(new EditBox(font, fieldX, top + 82, 80, 16,
                Component.literal("Colour")));
        inputColour.setValue("5599FF");
        inputColour.setMaxLength(6);
        inputColour.setResponder(s -> {
        }); // trigger re-render

        inputDesc = addRenderableWidget(new EditBox(font, fieldX, top + 108, fieldW, 16,
                Component.literal("Description")));
        inputDesc.setMaxLength(256);
        inputDesc.setHint(Component.literal("§8Optional short description"));

        checkOpen = addRenderableWidget(new Checkbox(
                panelLeft + 8, top + 136, PANEL_W - 16, 20,
                Component.literal("Open Recruitment (anyone may join)"), false));

        btnCreate = addRenderableWidget(Button.builder(Component.literal("§aCreate Nation"),
                b -> tryCreate())
                .bounds(cx - 90, top + 210, 85, 20).build());

        btnCancel = addRenderableWidget(Button.builder(Component.literal("§7Cancel"),
                b -> minecraft.setScreen(parent))
                .bounds(cx + 5, top + 210, 85, 20).build());
    }

    private void tryCreate() {
        String name = inputName.getValue().trim();
        String tag = inputTag.getValue().trim();
        int colour = NationGuiHelper.parseHex(inputColour.getValue());
        String desc = inputDesc.getValue().trim();
        boolean open = checkOpen.selected();

        if (name.length() < 3 || name.length() > 32 || !name.matches("[\\w\\-. ]+")) {
            errorMsg = "§cInvalid name (3–32 chars, letters/numbers/space/dash/dot).";
            return;
        }
        if (tag.length() < 2 || tag.length() > 5 || !tag.matches("[A-Za-z0-9]+")) {
            errorMsg = "§cInvalid tag (2–5 alphanumeric chars).";
            return;
        }

        PacketHandler.sendToServer(new C2SCreateNationPacket(name, tag.toUpperCase(), colour, desc));
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);

        int cx = width / 2;
        int panelLeft = cx - PANEL_W / 2;
        int top = height / 2 - PANEL_H / 2;

        // Outer panel
        NationGuiHelper.drawPanel(gfx, panelLeft, top, PANEL_W, PANEL_H,
                NationGuiHelper.COL_BG, NationGuiHelper.COL_BORDER);
        NationGuiHelper.drawHeader(gfx, font, "✦  Found a Nation", panelLeft, top, PANEL_W, 22);

        int labelX = panelLeft + 8;

        // Field labels
        gfx.drawString(font, "§7Name", labelX, top + 34, NationGuiHelper.COL_TEXT_DIM, false);
        gfx.drawString(font, "§7Tag", labelX, top + 60, NationGuiHelper.COL_TEXT_DIM, false);
        gfx.drawString(font, "§7Colour", labelX, top + 86, NationGuiHelper.COL_TEXT_DIM, false);
        gfx.drawString(font, "§7Desc", labelX, top + 112, NationGuiHelper.COL_TEXT_DIM, false);

        // Character counters
        String nameVal = inputName.getValue();
        String tagVal = inputTag.getValue();
        gfx.drawString(font, "§8" + nameVal.length() + "/32",
                panelLeft + PANEL_W - 30, top + 34, NationGuiHelper.COL_TEXT_DIM, false);
        gfx.drawString(font, "§8" + tagVal.length() + "/5",
                panelLeft + PANEL_W - 30, top + 60, NationGuiHelper.COL_TEXT_DIM, false);

        // Live colour preview swatch (24×14) next to hex field
        int previewCol = NationGuiHelper.parseHex(inputColour.getValue());
        int swatchX = panelLeft + 80 + 86;
        gfx.fill(swatchX, top + 82, swatchX + 24, top + 96, 0xFF_000000 | previewCol);
        gfx.fill(swatchX, top + 82, swatchX + 24, top + 83, 0x50_FFFFFF); // inner highlight

        // ── Live preview banner ─────────────────────────────────────────────────
        int previewY = top + 162;
        NationGuiHelper.drawPanel(gfx, panelLeft + 8, previewY, PANEL_W - 16, 38,
                0x40_000000, NationGuiHelper.COL_BORDER);

        String previewName = nameVal.isBlank() ? "Nation Name" : nameVal;
        String previewTag = tagVal.isBlank() ? "TAG" : tagVal.toUpperCase();
        // Colour accent bar
        gfx.fill(panelLeft + 8, previewY, panelLeft + 11, previewY + 38,
                0xFF_000000 | previewCol);
        // Tag + Name line
        gfx.drawString(font, "§7[" + previewTag + "] ",
                panelLeft + 16, previewY + 5, previewCol, false);
        int tagWidth = font.width("§7[" + previewTag + "] ");
        gfx.drawString(font, "§f" + previewName,
                panelLeft + 16 + tagWidth, previewY + 5, 0xFF_FFFFFF, false);
        // Desc preview line
        String previewDesc = inputDesc.getValue().isBlank() ? "§8No description set."
                : "§8" + inputDesc.getValue();
        if (font.width(previewDesc) > PANEL_W - 30)
            previewDesc = font.plainSubstrByWidth(previewDesc, PANEL_W - 34) + "§8…";
        gfx.drawString(font, previewDesc, panelLeft + 16, previewY + 20,
                NationGuiHelper.COL_TEXT_DIM, false);

        // Error message
        if (!errorMsg.isEmpty()) {
            gfx.drawCenteredString(font, errorMsg, cx, top + PANEL_H - 12,
                    NationGuiHelper.COL_DANGER);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
