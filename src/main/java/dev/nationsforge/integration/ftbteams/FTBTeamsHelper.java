package dev.nationsforge.integration.ftbteams;

import dev.nationsforge.NationsForge;
import dev.nationsforge.nation.Nation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.util.UUID;

/**
 * Public façade for the FTB Teams integration.
 *
 * Every method is guarded by {@link #isLoaded()} so the main mod loads
 * cleanly even when FTB Teams is absent. Actual work is delegated to
 * {@link FTBTeamsProxy} which is only class-loaded after the guard check,
 * preventing any NoClassDefFoundError at runtime.
 *
 * Integration behaviour:
 * • Nation founded → FTB server team "dominion_TAG" is created
 * • Player joins → added to that server team
 * • Player leaves / kicked → removed from that server team
 * • Nation disbanded → server team is deleted
 */
public final class FTBTeamsHelper {

    private static Boolean cached = null;

    private FTBTeamsHelper() {
    }

    /** Returns true if FTB Teams is present in the current game instance. */
    public static boolean isLoaded() {
        if (cached == null)
            cached = ModList.get().isLoaded("ftbteams");
        return cached;
    }

    // ── Nation lifecycle ─────────────────────────────────────────────────────────

    /**
     * Called immediately after a nation is created.
     * Creates the matching FTB server team.
     */
    public static void onNationCreated(MinecraftServer server, Nation nation) {
        if (!isLoaded())
            return;
        try {
            FTBTeamsProxy.createNationTeam(server, nation);
            NationsForge.LOGGER.info("[Dominion/FTBTeams] Created team for nation '{}'", nation.getName());
        } catch (Exception e) {
            NationsForge.LOGGER.warn("[Dominion/FTBTeams] onNationCreated failed: {}", e.getMessage());
        }
    }

    /**
     * Called immediately before or after a nation is disbanded.
     * Deletes the FTB server team.
     *
     * @param nationTag the tag of the disbanded nation (name may already be gone)
     */
    public static void onNationDisbanded(MinecraftServer server, String nationTag) {
        if (!isLoaded())
            return;
        try {
            FTBTeamsProxy.deleteNationTeam(server, nationTag);
            NationsForge.LOGGER.info("[Dominion/FTBTeams] Deleted team for tag '{}'", nationTag);
        } catch (Exception e) {
            NationsForge.LOGGER.warn("[Dominion/FTBTeams] onNationDisbanded failed: {}", e.getMessage());
        }
    }

    // ── Player lifecycle ─────────────────────────────────────────────────────────

    /**
     * Called after a player successfully joins or creates a nation.
     * Adds them to the matching FTB server team (creates it if needed).
     *
     * @param playerId UUID of the joining player (works for offline players)
     */
    public static void onPlayerJoinedNation(MinecraftServer server, UUID playerId, Nation nation) {
        if (!isLoaded())
            return;
        String name = resolvePlayerName(server, playerId);
        if (name == null)
            return;
        try {
            FTBTeamsProxy.addPlayerToTeam(server, name, nation);
        } catch (Exception e) {
            NationsForge.LOGGER.warn("[Dominion/FTBTeams] onPlayerJoinedNation failed: {}", e.getMessage());
        }
    }

    /**
     * Called after a player leaves or is kicked from a nation.
     * Removes them from the FTB server team.
     *
     * @param nationTag tag of the nation being left
     */
    public static void onPlayerLeftNation(MinecraftServer server, UUID playerId, String nationTag) {
        if (!isLoaded())
            return;
        String name = resolvePlayerName(server, playerId);
        if (name == null)
            return;
        try {
            FTBTeamsProxy.removePlayerFromTeam(server, name, nationTag);
        } catch (Exception e) {
            NationsForge.LOGGER.warn("[Dominion/FTBTeams] onPlayerLeftNation failed: {}", e.getMessage());
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────────

    /**
     * Resolves a player UUID to their Minecraft name.
     * Works for both online and offline players via the server's profile cache.
     */
    private static String resolvePlayerName(MinecraftServer server, UUID playerId) {
        // Online player (fastest)
        var online = server.getPlayerList().getPlayer(playerId);
        if (online != null)
            return online.getName().getString();
        // Offline – look up in profile cache
        return server.getProfileCache()
                .get(playerId)
                .map(com.mojang.authlib.GameProfile::getName)
                .orElse(null);
    }
}
