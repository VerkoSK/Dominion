package dev.nationsforge.integration.ftbchunks;

import dev.nationsforge.NationsForge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Direct FTBChunks + FTBTeams API integration for bot nation territory claiming.
 *
 * Avoids command-string parsing by calling the Java APIs directly via
 * reflection. This is more reliable for the bulk claiming done during world
 * generation because:
 * — No command argument serialisation / deserialisation round-trips.
 * — TeamArgument lookup quirks do not apply.
 * — Server teams bypass the per-team chunk limit entirely (checked in
 *   ChunkTeamDataImpl.claim: "if (!team.isServerTeam() && size >= maxClaims)").
 *
 * Usage:
 *   FTBChunksProxy.claimChunksForBotTeam(server, "dominion_sky",
 *       capitalBlockX, capitalBlockZ, radius);
 *
 * All errors are logged and silently suppressed so world generation is never
 * aborted by a missing soft-dependency.
 */
public final class FTBChunksProxy {

    private static Boolean ftbChunksLoaded = null;
    private static boolean failed          = false;

    // ── FTBTeams reflection ──────────────────────────────────────────────────────
    private static Method mTeamsApi;       // FTBTeamsAPI.api()
    private static Method mTeamsGetMgr;   // teamsApi.getManager()
    private static Method mGetTeamByName; // teamsManager.getTeamByName(String) → Optional<Team>

    // ── FTBChunks reflection ─────────────────────────────────────────────────────
    private static Method mChunksApi;      // FTBChunksAPI.api()
    private static Method mChunksGetMgr;  // chunksApi.getManager()  — NO ARGS
    private static Method mGetOrCreate;   // ClaimedChunkManager.getOrCreateData(Team)
    private static Method mSetExtraClaim; // ChunkTeamData.setExtraClaimChunks(int)
    private static Method mClaim;         // ChunkTeamData.claim(CSS, ChunkDimPos, boolean)
    private static Method mMarkDirty;     // ChunkTeamData.markDirty()

    // ── ChunkDimPos ──────────────────────────────────────────────────────────────
    private static Constructor<?> cdpCtor; // ChunkDimPos(ResourceKey<Level>, int, int)

    private FTBChunksProxy() {}

    // ── Public entry point ───────────────────────────────────────────────────────

    /**
     * Claims a (2*radius+1) × (2*radius+1) square of chunks centred on
     * (blockX, blockZ) in the Overworld for the named FTB server team.
     *
     * @param server   running server
     * @param teamName the FTB team short name (e.g. "dominion_sky")
     * @param blockX   capital block X
     * @param blockZ   capital block Z
     * @param radius   chunk radius (radius=3 → 7×7 = 49 chunks)
     */
    public static void claimChunksForBotTeam(MinecraftServer server,
            String teamName, int blockX, int blockZ, int radius) {
        if (!isLoaded() || failed) return;
        try {
            ensureInit();

            // ── 1. Resolve the FTBTeams server team ───────────────────────────
            Object teamsApi     = mTeamsApi.invoke(null);
            Object teamsMgr     = mTeamsGetMgr.invoke(teamsApi);
            @SuppressWarnings("unchecked")
            Optional<Object> teamOpt = (Optional<Object>) mGetTeamByName.invoke(teamsMgr, teamName);
            if (teamOpt == null || teamOpt.isEmpty()) {
                NationsForge.LOGGER.warn("[Dominion/FTBChunks] Team '{}' not found — skipping chunk claim", teamName);
                return;
            }
            Object team = teamOpt.get();

            // ── 2. Resolve ChunkTeamData for the team ─────────────────────────
            Object chunksApi = mChunksApi.invoke(null);
            Object chunksMgr = mChunksGetMgr.invoke(chunksApi);
            Object teamData  = mGetOrCreate.invoke(chunksMgr, team);
            if (teamData == null) {
                NationsForge.LOGGER.warn("[Dominion/FTBChunks] No ChunkTeamData for team '{}'", teamName);
                return;
            }

            // Give extra claim allowance (server teams bypass limits,
            // but setting this avoids any edge-case early returns)
            mSetExtraClaim.invoke(teamData, 4096);

            // ── 3. Claim each chunk in the square ─────────────────────────────
            CommandSourceStack src = server.createCommandSourceStack().withPermission(4);
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            int claimed = 0;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Object cdp = cdpCtor.newInstance(Level.OVERWORLD, chunkX + dx, chunkZ + dz);
                    mClaim.invoke(teamData, src, cdp, false);
                    claimed++;
                }
            }

            mMarkDirty.invoke(teamData);
            NationsForge.LOGGER.info("[Dominion/FTBChunks] Claimed {} chunks for team '{}' at chunk ({},{})",
                    claimed, teamName, chunkX, chunkZ);

        } catch (Exception e) {
            failed = true;
            NationsForge.LOGGER.warn("[Dominion/FTBChunks] Direct claim failed ({}), future calls skipped: {}",
                    teamName, e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    public static boolean isLoaded() {
        if (ftbChunksLoaded == null)
            ftbChunksLoaded = ModList.get().isLoaded("ftbchunks");
        return ftbChunksLoaded;
    }

    private static void ensureInit() throws Exception {
        if (mTeamsApi != null) return;   // already initialised

        // ── FTBTeams API ──────────────────────────────────────────────────────
        Class<?> teamsApiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
        mTeamsApi = teamsApiClass.getMethod("api");
        Object teamsApiInst = mTeamsApi.invoke(null);
        mTeamsGetMgr   = teamsApiInst.getClass().getMethod("getManager");
        Object teamsMgr = mTeamsGetMgr.invoke(teamsApiInst);
        mGetTeamByName  = teamsMgr.getClass().getMethod("getTeamByName", String.class);

        Class<?> teamInterface = Class.forName("dev.ftb.mods.ftbteams.api.Team");

        // ── FTBChunks API ─────────────────────────────────────────────────────
        Class<?> chunksApiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
        mChunksApi = chunksApiClass.getMethod("api");
        Object chunksApiInst = mChunksApi.invoke(null);
        mChunksGetMgr   = chunksApiInst.getClass().getMethod("getManager"); // NO ARGS
        Object chunksMgr = mChunksGetMgr.invoke(chunksApiInst);

        // ClaimedChunkManager.getOrCreateData(Team)
        mGetOrCreate = chunksMgr.getClass().getMethod("getOrCreateData", teamInterface);

        // ChunkTeamData methods (look up from interface for stability)
        Class<?> chunkTeamDataInterface = Class.forName("dev.ftb.mods.ftbchunks.api.ChunkTeamData");
        mSetExtraClaim = chunkTeamDataInterface.getMethod("setExtraClaimChunks", int.class);
        mMarkDirty     = chunkTeamDataInterface.getMethod("markDirty");

        // ChunkDimPos class & constructor
        Class<?> cdpClass = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
        mClaim  = chunkTeamDataInterface.getMethod("claim", CommandSourceStack.class, cdpClass, boolean.class);
        cdpCtor = cdpClass.getDeclaredConstructor(ResourceKey.class, int.class, int.class);
        cdpCtor.setAccessible(true);

        NationsForge.LOGGER.info("[Dominion/FTBChunks] FTBChunksProxy reflection initialised OK");
    }
}
