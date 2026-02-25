package dev.nationsforge.integration.ftbteams;

import dev.nationsforge.NationsForge;
import dev.nationsforge.nation.Nation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/**
 * FTB Teams integration via server-side console commands.
 *
 * Each nation gets a server-managed team inside FTB Teams named
 * "dominion_TAG" (lower-case). Server teams are admin-managed so players
 * can be added/removed even while offline.
 *
 * Correct FTB Teams 2001.x command set:
 * ftbteams server create_team <name>
 * ftbteams server join_player <name> <player>
 * ftbteams server kick_player <name> <player>
 * ftbteams server delete_team <name>
 *
 * Every call is wrapped in try-catch — a failure is logged at WARN but
 * never propagates to the caller.
 */
final class FTBTeamsProxy {

    private FTBTeamsProxy() {
    }

    // ── Name helper ──────────────────────────────────────────────────────────────

    /** FTB server-team name for a nation tag, e.g. "dominion_sky". */
    static String ftbTeamName(String nationTag) {
        return "dominion_" + nationTag.toLowerCase();
    }

    // ── Public operations ────────────────────────────────────────────────────────

    /**
     * Creates the FTB server team for a newly founded nation.
     * Idempotent — FTB Teams silently ignores the call if the team already exists.
     */
    static void createNationTeam(MinecraftServer server, Nation nation) {
        String name = ftbTeamName(nation.getTag());
        // Try both known FTB Teams command syntaxes (version differences)
        if (!runCmd(server, "ftbteams server create " + name)) {
            runCmd(server, "ftbteams server create_team " + name);
        }
    }

    /**
     * Ensures the FTB team exists and adds the player to it.
     */
    static void addPlayerToTeam(MinecraftServer server, String playerName, Nation nation) {
        String teamName = ftbTeamName(nation.getTag());
        // Ensure team exists first (idempotent)
        if (!runCmd(server, "ftbteams server create " + teamName)) {
            runCmd(server, "ftbteams server create_team " + teamName);
        }
        // Add player — try modern syntax first, fall back to legacy
        if (!runCmd(server, "ftbteams server join " + teamName + " " + playerName)) {
            runCmd(server, "ftbteams server join_player " + teamName + " " + playerName);
        }
    }

    /**
     * Removes a player from their former nation's FTB team.
     */
    static void removePlayerFromTeam(MinecraftServer server, String playerName, String nationTag) {
        String teamName = ftbTeamName(nationTag);
        if (!runCmd(server, "ftbteams server kick " + teamName + " " + playerName)) {
            runCmd(server, "ftbteams server kick_player " + teamName + " " + playerName);
        }
    }

    /**
     * Deletes the FTB server team when a nation is disbanded.
     */
    static void deleteNationTeam(MinecraftServer server, String nationTag) {
        String teamName = ftbTeamName(nationTag);
        if (!runCmd(server, "ftbteams server delete " + teamName)) {
            runCmd(server, "ftbteams server delete_team " + teamName);
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────────

    /**
     * Executes a command and returns true if the result code is > 0 (success).
     */
    private static boolean runCmd(MinecraftServer server, String command) {
        try {
            CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
            int result = server.getCommands().performPrefixedCommand(src, command);
            NationsForge.LOGGER.debug("[Dominion/FTBTeams] '{}' → result={}", command, result);
            return result > 0;
        } catch (Exception e) {
            NationsForge.LOGGER.warn("[Dominion/FTBTeams] Failed: '{}' — {}", command, e.getMessage());
            return false;
        }
    }
}
