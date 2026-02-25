package dev.nationsforge.integration.ftbchunks;

import dev.nationsforge.NationsForge;
import dev.nationsforge.nation.Nation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * Soft-dependency integration with FTB Chunks.
 *
 * FTB Chunks lets players claim chunk ownership tied to their FTB Team.
 * Since Dominion links each nation to an FTB server team named
 * "dominion_TAG", chunks claimed by that team automatically belong to the
 * nation — no extra work required for the basic flow.
 *
 * This helper counts those claimed chunks and exposes the count so the
 * NationPowerCalculator can factor territory into a nation's power score.
 *
 * Reflection strategy
 * ───────────────────
 * We never depend on FTB Chunks at compile time. Instead we try to call:
 * dev.ftb.mods.ftbchunks.api.FTBChunksAPI#api()
 * → FTBChunksAPIInstance#getManager(LevelAccessor)
 * → ClaimedChunkManager#getAllClaimedChunks()
 * → each ClaimedChunk#getTeamId() (UUID)
 *
 * We then cross-reference the FTB Teams team UUID for "dominion_TAG" to
 * count chunks. If any step fails (FTB Chunks not installed, API changed)
 * we silently return 0 and log a single WARN.
 */
public final class FTBChunksHelper {

    private static Boolean ftbChunksLoaded = null;
    private static boolean reflectionFailed = false;

    // Cached reflection handles (loaded once on first use)
    private static Method mApiGet;
    private static Method mGetManager;
    private static Method mGetAllClaimed;
    private static Method mGetTeamId;

    private FTBChunksHelper() {
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    public static boolean isLoaded() {
        if (ftbChunksLoaded == null)
            ftbChunksLoaded = ModList.get().isLoaded("ftbchunks");
        return ftbChunksLoaded;
    }

    /**
     * Counts the number of chunks claimed by the nation's FTB server team
     * across all dimensions.
     *
     * @return chunk count, or 0 if FTBChunks is absent or unavailable.
     */
    public static long countClaimedChunks(MinecraftServer server, Nation nation) {
        if (!isLoaded() || reflectionFailed)
            return 0;
        try {
            return countViaReflection(server, nation);
        } catch (Exception e) {
            reflectionFailed = true;
            NationsForge.LOGGER.warn("[Dominion/FTBChunks] Reflection failed (will not retry): {}", e.getMessage());
            return 0;
        }
    }

    // ── Reflection
    // ────────────────────────────────────────────────────────────────

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static long countViaReflection(MinecraftServer server, Nation nation) throws Exception {
        initReflection();

        // Resolve the FTB Teams team UUID for "dominion_<tag>"
        // We get it via FTBTeams reflection as well
        java.util.UUID teamId = FTBTeamsReflectionHelper.getServerTeamId(server,
                "dominion_" + nation.getTag().toLowerCase());
        if (teamId == null)
            return 0;

        // Walk all loaded dimensions and sum claimed chunks for this team
        long count = 0;
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            Object api = mApiGet.invoke(null);
            Object manager = mGetManager.invoke(api, level);
            if (manager == null)
                continue;
            java.util.Collection<?> claimed = (java.util.Collection<?>) mGetAllClaimed.invoke(manager);
            for (Object chunk : claimed) {
                Object chunkTeamId = mGetTeamId.invoke(chunk);
                if (teamId.equals(chunkTeamId))
                    count++;
            }
        }
        return count;
    }

    private static void initReflection() throws Exception {
        if (mApiGet != null)
            return; // already initialised
        Class<?> apiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
        mApiGet = apiClass.getMethod("api");

        Class<?> instanceClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPIInstance");
        // getManager accepts a LevelAccessor (parent of ServerLevel)
        Class<?> levelAccessor = Class.forName("net.minecraft.world.level.LevelAccessor");
        mGetManager = instanceClass.getMethod("getManager", levelAccessor);

        Class<?> managerClass = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunkManager");
        mGetAllClaimed = managerClass.getMethod("getAllClaimedChunks");

        Class<?> chunkClass = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunk");
        mGetTeamId = chunkClass.getMethod("getTeamId");
    }
}
