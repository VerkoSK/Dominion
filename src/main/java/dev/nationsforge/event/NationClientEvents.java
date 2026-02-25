package dev.nationsforge.event;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.client.gui.NationsScreen;
import dev.nationsforge.client.NationKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = dev.nationsforge.NationsForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class NationClientEvents {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null)
            return;

        // Open Nations screen when the keybind is pressed
        if (NationKeybinds.OPEN_NATIONS.consumeClick()) {
            // Store local player UUID for client cache
            ClientNationData.setLocalPlayer(mc.player.getUUID());
            mc.setScreen(new NationsScreen());
        }
    }
}
