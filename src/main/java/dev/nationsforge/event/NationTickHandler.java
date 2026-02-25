package dev.nationsforge.event;

import dev.nationsforge.NationsForge;
import dev.nationsforge.integration.ftbchunks.FTBChunksHelper;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationManager;
import dev.nationsforge.nation.NationPowerCalculator;
import dev.nationsforge.nation.NationSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fires on the Forge server tick event.
 *
 * Every {@link #TICK_INTERVAL} server ticks (≈ 5 minutes):
 * 1. Counts FTB Chunks claimed by each nation's server team.
 * 2. Recalculates each nation's power (NationPowerCalculator).
 * 3. Broadcasts the updated snapshot to all online players.
 *
 * This keeps territory and power values eventually-consistent without
 * requiring a claim event hook or FTB Chunks API at compile time.
 */
@Mod.EventBusSubscriber(modid = NationsForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NationTickHandler {

    /** 300 seconds × 20 ticks/s = 6 000 ticks ≈ 5 minutes */
    private static final int TICK_INTERVAL = 6_000;

    private static int tickCount = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (++tickCount < TICK_INTERVAL)
            return;
        tickCount = 0;

        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;

        NationSavedData data = NationManager.getData(server);
        boolean changed = false;

        for (Nation nation : data.getAllNations()) {
            // 1. Territory count via FTB Chunks reflection
            long chunks = FTBChunksHelper.countClaimedChunks(server, nation);
            nation.setTerritory(chunks);

            // 2. Recalculate power (also updates score for leaderboard)
            NationPowerCalculator.recalculate(nation);
            changed = true;
        }

        if (changed) {
            data.setDirty();
            NationManager.broadcastAll(server);
            NationsForge.LOGGER.debug("[Dominion] Periodic tick: recalculated power for {} nations.",
                    data.getAllNations().size());
        }
    }
}
