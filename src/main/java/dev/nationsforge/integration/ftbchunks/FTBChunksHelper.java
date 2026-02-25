package dev.nationsforge.integration.ftbchunks;

import dev.nationsforge.NationsForge;
import dev.nationsforge.nation.Nation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Soft-dependency integration with FTB Chunks.
 *
 * FTBChunks has ONE global ClaimedChunkManager (not per-dimension).
 * Chunks owned by the nation's FTB server team "dominion_TAG" are counted
 * here and exposed to NationPowerCalculator.
 *
 * Reflection chain (corrected):
 *   FTBChunksAPI.api()                    → API instance
 *   api.getManager()                      → ClaimedChunkManager (NO-ARG!)
 *   manager.getAllClaimedChunks()          → Collection<ClaimedChunk>
 *   chunk.getTeamData()                   → ChunkTeamData
 *   teamData.getTeam()                    → Team
 *   team.getId()                          → UUID  → compare to our team's UUID
 */
public final class FTBChunksHelper {

    private static Boolean ftbChunksLoaded = null;
    private static boolean reflectionFailed = false;

    // FTBChunks reflection handles
    private static Method mApiGet;         // FTBChunksAPI.api()
    private static Method mGetManager;    // api.getManager()  — NO ARGS
    private static Method mGetAllClaimed; // manager.getAllClaimedChunks()
    private static Method mGetTeamData;   // chunk.getTeamData()
    private static Method mGetTeam;       // teamData.getTeam()
    private static Method mGetTeamId;     // team.getId()

    private FTBChunksHelper() {}

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
        if (!isLoaded() || reflectionFailed) return 0;
        try {
            return countViaReflection(server, nation);
        } catch (Exception e) {
            reflectionFailed = true;
            NationsForge.LOGGER.warn("[Dominion/FTBChunks] countClaimedChunks reflection failed (will not retry): {}", e.getMessage());
            return 0;
        }
    }

    // ── Reflection ───────────────────────────────────────────────────────────────

    private static long countViaReflection(MinecraftServer server, Nation nation) throws Exception {
        initReflection();

        // Look up the FTB Teams team UUID for "dominion_<tag>" via FTBTeams reflection
        UUID teamId = FTBTeamsReflectionHelper.getServerTeamId(server,
                "dominion_" + nation.getTag().toLowerCase());
        if (teamId == null) return 0;

        // ONE global manager for all dimensions
        Object api      = mApiGet.invoke(null);
        Object manager  = mGetManager.invoke(api);       // no-arg getManager()
        if (manager == null) return 0;

        java.util.Collection<?> claimed = (java.util.Collection<?>) mGetAllClaimed.invoke(manager);
        long count = 0;
        for (Object chunk : claimed) {
            try {
                Object teamData  = mGetTeamData.invoke(chunk);
                if (teamData == null) continue;
                Object team      = mGetTeam.invoke(teamData);
                if (team == null) continue;
                Object chunkTeamId = mGetTeamId.invoke(team);
                if (teamId.equals(chunkTeamId)) count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    private static void initReflection() throws Exception {
        if (mApiGet != null) return;   // already initialised

        // FTBChunksAPI.api() → API instance
        Class<?> apiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
        mApiGet = apiClass.getMethod("api");
        Object apiInstance = mApiGet.invoke(null);

        // api.getManager() — NO ARGUMENT (not per-level!)
        mGetManager = apiInstance.getClass().getMethod("getManager");
        Object manager = mGetManager.invoke(apiInstance);

        // manager.getAllClaimedChunks()
        mGetAllClaimed = manager.getClass().getMethod("getAllClaimedChunks");

        // chunk.getTeamData() → ChunkTeamData
        Class<?> claimedChunkInterface = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunk");
        mGetTeamData = claimedChunkInterface.getMethod("getTeamData");

        // teamData.getTeam() → Team
        Class<?> chunkTeamDataInterface = Class.forName("dev.ftb.mods.ftbchunks.api.ChunkTeamData");
        mGetTeam = chunkTeamDataInterface.getMethod("getTeam");

        // team.getId() → UUID
        Class<?> teamInterface = Class.forName("dev.ftb.mods.ftbteams.api.Team");
        mGetTeamId = teamInterface.getMethod("getId");

        NationsForge.LOGGER.info("[Dominion/FTBChunks] FTBChunksHelper reflection initialised OK");
    }
}
