package dev.nationsforge.event;

import dev.nationsforge.nation.NationManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = dev.nationsforge.NationsForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NationServerEvents {

    /** When a player logs in, push the full nation data snapshot to them. */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp))
            return;
        NationManager.syncToPlayer(sp.getServer(), sp);
    }

    /** When a player respawns (dimension change), re-sync their data. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp))
            return;
        NationManager.syncToPlayer(sp.getServer(), sp);
    }
}
