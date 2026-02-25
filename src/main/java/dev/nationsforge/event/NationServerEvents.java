package dev.nationsforge.event;

import dev.nationsforge.bot.WorldBotGenerator;
import dev.nationsforge.nation.NationManager;
import dev.nationsforge.nation.NationSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = dev.nationsforge.NationsForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NationServerEvents {

    /** Generate bot nations on the first server start for a new world. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        NationSavedData data = NationManager.getData(server);
        WorldBotGenerator.generate(server, data);
        // Sync newly generated bots to any already-connected players
        NationManager.broadcastAll(server);
    }

    /** When a player logs in, push the full nation data snapshot to them. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp))
            return;
        NationManager.syncToPlayer(sp.getServer(), sp);
        // Also send any pending diplomacy requests for their nation
        NationManager.syncDiplomacyToPlayer(sp.getServer(), sp);
    }

    /** When a player respawns (dimension change), re-sync their data. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp))
            return;
        NationManager.syncToPlayer(sp.getServer(), sp);
    }
}
