package dev.nationsforge.integration.ftbteams;

import dev.nationsforge.NationsForge;
import dev.nationsforge.nation.Nation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * FTB Teams integration.
 *
 * Each nation gets a server-managed FTB Teams team named "dominion_TAG".
 * Team creation/deletion uses FTB Teams commands (which support server teams).
 * Member add/remove uses Java reflection because FTB Teams has NO commands
 * for managing server team membership — only the Java API supports it.
 *
 * Reflection cache is initialised lazily on first use and fails-safe
 * (falls back to no-op) if FTB Teams is absent or the API changes.
 */
final class FTBTeamsProxy {

    private FTBTeamsProxy() {
    }

    // ── Reflection state ─────────────────────────────────────────────────────────

    private static boolean reflectionFailed = false;
    private static Method mFtbApi;
    private static Method mGetManager;
    private static Method mGetTeamByName;
    @SuppressWarnings("rawtypes")
    private static Class<Enum> teamRankClass;
    private static Object rankMember;

    // ── Name helper ──────────────────────────────────────────────────────────────

    /** FTB server-team name for a nation tag, e.g. "dominion_sky". */
    static String ftbTeamName(String nationTag) {
        return "dominion_" + nationTag.toLowerCase();
    }

    // ── Public operations ────────────────────────────────────────────────────────

    /**
     * Creates the FTB server team for a newly founded nation.
     * Idempotent — FTB Teams ignores the call if the team already exists.
     */
    static void createNationTeam(MinecraftServer server, Nation nation) {
        String name = ftbTeamName(nation.getTag());
        if (!runCmd(server, "ftbteams server create " + name)) {
            runCmd(server, "ftbteams server create_team " + name);
        }
    }

    /**
     * Ensures the FTB team exists and adds the player to it.
     * Uses Java reflection (addMember) because no command exists for this.
     */
    static void addPlayerToTeam(MinecraftServer server, String playerName, Nation nation) {
        // Ensure the server team exists first
        createNationTeam(server, nation);
        String teamName = ftbTeamName(nation.getTag());

        // Resolve player UUID from name
        UUID playerId = resolveUUID(server, playerName);
        if (playerId != null) {
            if (addMemberReflect(server, teamName, playerId)) return;
        }
        // Fallback: attempt commands (works on some FTB Teams builds)
        if (!runCmd(server, "ftbteams server join " + teamName + " " + playerName)) {
            runCmd(server, "ftbteams server join_player " + teamName + " " + playerName);
        }
    }

    /**
     * Removes a player from their former nation's FTB team.
     * Uses Java reflection (removeMember) because no command exists for this.
     */
    static void removePlayerFromTeam(MinecraftServer server, String playerName, String nationTag) {
        String teamName = ftbTeamName(nationTag);
        UUID playerId = resolveUUID(server, playerName);
        if (playerId != null) {
            if (removeMemberReflect(server, teamName, playerId)) return;
        }
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

    // ── Reflection helpers ────────────────────────────────────────────────────────

    private static boolean addMemberReflect(MinecraftServer server, String teamName, UUID playerId) {
        try {
            Object team = getTeamByName(server, teamName);
            if (team == null) return false;
            ensureTeamRank();
            Method m = findMethod(team.getClass(), "addMember", UUID.class, teamRankClass);
            if (m == null) return false;
            m.setAccessible(true);
            m.invoke(team, playerId, rankMember);
            NationsForge.LOGGER.debug("[Dominion/FTBTeams] addMember({}, {}) ok", teamName, playerId);
            return true;
        } catch (Exception e) {
            NationsForge.LOGGER.debug("[Dominion/FTBTeams] addMemberReflect failed: {}", e.getMessage());
            return false;
        }
    }

    private static boolean removeMemberReflect(MinecraftServer server, String teamName, UUID playerId) {
        try {
            Object team = getTeamByName(server, teamName);
            if (team == null) return false;
            Method m = findMethod(team.getClass(), "removeMember", UUID.class);
            if (m == null) return false;
            m.setAccessible(true);
            m.invoke(team, playerId);
            NationsForge.LOGGER.debug("[Dominion/FTBTeams] removeMember({}, {}) ok", teamName, playerId);
            return true;
        } catch (Exception e) {
            NationsForge.LOGGER.debug("[Dominion/FTBTeams] removeMemberReflect failed: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object getTeamByName(MinecraftServer server, String teamName) throws Exception {
        if (reflectionFailed) return null;
        initReflection(server);
        Object api = mFtbApi.invoke(null);
        Object manager = mGetManager.invoke(api);
        if (manager == null) return null;
        Optional<?> opt = (Optional<?>) mGetTeamByName.invoke(manager, teamName);
        return (opt != null && opt.isPresent()) ? opt.get() : null;
    }

    private static void initReflection(MinecraftServer server) throws Exception {
        if (mFtbApi != null) return;
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            mFtbApi = apiClass.getMethod("api");
            Object api = mFtbApi.invoke(null);
            // getManager() with no args — NOT getManager(MinecraftServer)!
            mGetManager = api.getClass().getMethod("getManager");
            Object manager = mGetManager.invoke(api);
            mGetTeamByName = manager.getClass().getMethod("getTeamByName", String.class);
        } catch (Exception e) {
            reflectionFailed = true;
            throw e;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void ensureTeamRank() throws Exception {
        if (teamRankClass != null) return;
        teamRankClass = (Class<Enum>) Class.forName("dev.ftb.mods.ftbteams.api.TeamRank");
        rankMember = Enum.valueOf(teamRankClass, "MEMBER");
    }

    /** Walk the class hierarchy to find the first matching declared method. */
    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static UUID resolveUUID(MinecraftServer server, String playerName) {
        try {
            Optional<com.mojang.authlib.GameProfile> profile =
                    server.getProfileCache().get(playerName);
            return profile.map(com.mojang.authlib.GameProfile::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Command runner ────────────────────────────────────────────────────────────

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
