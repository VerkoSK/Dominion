package dev.nationsforge;

import dev.nationsforge.client.NationKeybinds;
import dev.nationsforge.item.ModItems;
import dev.nationsforge.network.PacketHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(NationsForge.MOD_ID)
public class NationsForge {

    public static final String MOD_ID = "nationsforge";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public NationsForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register items
        ModItems.ITEMS.register(modBus);

        // Register network packets on the mod event bus (happens early enough)
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeyMappings);

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("[Dominion] Mod loaded. Press [N] in-game to open the nations panel.");
    }

    private void onCommonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // Client-side initialisation (if needed)
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(NationKeybinds.OPEN_NATIONS);
    }
}
