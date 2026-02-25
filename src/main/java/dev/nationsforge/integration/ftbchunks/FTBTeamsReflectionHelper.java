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
            // getManagerForServer or getManager(server)
            Object manager = ftbApi.getClass().getMethod("getManager", MinecraftServer.class)
                    .invoke(ftbApi, server);
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

        Class<?> managerClass = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
        mGetTeamByName = managerClass.getMethod("getTeamByName", String.class);

        Class<?> teamClass = Class.forName("dev.ftb.mods.ftbteams.api.Team");
        mGetId = teamClass.getMethod("getId");
    }
}
