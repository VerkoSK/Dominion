package dev.nationsforge.event;

import dev.nationsforge.NationsForge;
import dev.nationsforge.bot.BotNationAI;
import dev.nationsforge.integration.ftbchunks.FTBChunksHelper;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationManager;
import dev.nationsforge.nation.NationPowerCalculator;
import dev.nationsforge.nation.NationSavedData;
import dev.nationsforge.nation.RelationType;

import java.util.Random;
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

    // ── Passive income rates (coins per tick-cycle) ──────────────────────────────
    /** Earned per online member per cycle. */
    public static final long INCOME_PER_ONLINE_MEMBER = 50L;
    /** Earned per claimed FTB chunk per cycle. */
    public static final long INCOME_PER_CHUNK = 10L;
    /** Earned per active trade pact per cycle. */
    public static final long INCOME_PER_TRADE_PACT = 100L;
    /** Earned per active alliance per cycle. */
    public static final long INCOME_PER_ALLIANCE = 25L;

    private static int tickCount = 0;
    private static final Random rng = new Random();

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
            if (nation.isBot()) {
                // Bot nations — fully managed by AI (income + diplomacy + expansion)
                BotNationAI.tick(server, nation, data, rng);
            } else {
                // Player nations — update territory via FTB Chunks, then collect passive income
                long chunks = FTBChunksHelper.countClaimedChunks(server, nation);
                nation.setTerritory(chunks);

                long online = nation.getMembers().keySet().stream()
                        .filter(uid -> server.getPlayerList().getPlayer(uid) != null)
                        .count();
                long trades = nation.getRelations().values().stream()
                        .filter(r -> r.getType() == RelationType.TRADE_PACT).count();
                long allies = nation.getRelations().values().stream()
                        .filter(r -> r.getType() == RelationType.ALLIANCE).count();

                nation.addTreasury(online * INCOME_PER_ONLINE_MEMBER);
                nation.addTreasury(nation.getTerritory() * INCOME_PER_CHUNK);
                nation.addTreasury(trades * INCOME_PER_TRADE_PACT);
                nation.addTreasury(allies * INCOME_PER_ALLIANCE);

                NationPowerCalculator.recalculate(nation);
            }
            changed = true;
        }

        if (changed) {
            data.setDirty();
            NationManager.broadcastAll(server);
            NationsForge.LOGGER.debug("[Dominion] Tick: processed {} nations.", data.getAllNations().size());
        }
    }
}
