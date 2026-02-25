package dev.nationsforge.integration.ftbchunks;

import dev.nationsforge.NationsForge;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Tiny reflection shim to resolve an FTB Teams server-team UUID from its name.
 * Used by FTBChunksHelper to cross-reference chunk owners.
 */
final class FTBTeamsReflectionHelper {

    private static boolean failed = false;
    private static Method mFTBTeamsApi;
    private static Method mGetManager;
    private static Method mGetTeamByName;
    private static Method mGetId;

    private FTBTeamsReflectionHelper() {
    }

    /**
     * Returns the UUID of the FTB server team with the given name,
     * or {@code null} if not found or if FTB Teams is not installed.
     */
    static UUID getServerTeamId(MinecraftServer server, String teamName) {
        if (failed)
            return null;
        try {
            init();
            Object ftbApi = mFTBTeamsApi.invoke(null);
            // getManager() with NO args — the old code wrongly passed MinecraftServer
            Object manager = mGetManager.invoke(ftbApi);
            if (manager == null)
                return null;

            // Try getTeamByName(String) → Optional<Team>
            java.util.Optional<?> opt = (java.util.Optional<?>) mGetTeamByName.invoke(manager, teamName);
            if (opt == null || opt.isEmpty())
                return null;

            return (UUID) mGetId.invoke(opt.get());
        } catch (Exception e) {
            failed = true;
            NationsForge.LOGGER.debug("[Dominion/FTBTeamsRef] UUID lookup failed ({})", e.getMessage());
            return null;
        }
    }

    private static void init() throws Exception {
        if (mFTBTeamsApi != null)
            return;
        Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
        mFTBTeamsApi = apiClass.getMethod("api");

        // Resolve getManager() on the actual API instance (no-arg version)
        Object apiInstance = mFTBTeamsApi.invoke(null);
        mGetManager = apiInstance.getClass().getMethod("getManager");
        Object manager = mGetManager.invoke(apiInstance);

        mGetTeamByName = manager.getClass().getMethod("getTeamByName", String.class);

        Class<?> teamClass = Class.forName("dev.ftb.mods.ftbteams.api.Team");
        mGetId = teamClass.getMethod("getId");
    }
}
