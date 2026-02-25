package dev.nationsforge.integration.ftbteams;

import dev.nationsforge.NationsForge;
import dev.nationsforge.nation.Nation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * FTB Teams integration.
 *
 * Each nation gets a server-managed FTB Teams team named "dominion_TAG".
 * Team creation/deletion uses FTB Teams commands (which support server teams
 * and internally call syncToAll). Member add/remove uses Java reflection
 * because FTB Teams has NO commands for server-team membership management.
 *
 * CRITICAL: After calling addMember/removeMember via reflection we MUST call
 * team.markDirty() and manager.syncToAll(team) otherwise clients never learn
 * about the membership change and FTBChunks ignores the player as a member.
 *
 * We reach TeamManagerImpl via its public static INSTANCE field which is
 * simpler and more reliable than going through FTBTeamsAPI.api().getManager().
 */
final class FTBTeamsProxy {

    private FTBTeamsProxy() {}

    // ── Reflection state ─────────────────────────────────────────────────────────

    private static boolean reflectionFailed = false;

    // TeamManagerImpl.INSTANCE field
    private static Field  fTMInstance;

    // Methods looked up lazily on first use
    private static Method mGetTeamByName;  // TeamManagerImpl.getTeamByName(String) → Optional<Team>
    private static Method mAddMember;      // AbstractTeamBase.addMember(UUID, TeamRank)
    private static Method mRemoveMember;   // AbstractTeamBase.removeMember(UUID)
    private static Method mMarkDirty;      // AbstractTeam.markDirty()
    private static Method mSyncToAll;      // TeamManagerImpl.syncToAll(Team...)

    @SuppressWarnings("rawtypes")
    private static Class<Enum> teamRankClass;
    private static Class<?>    teamInterface;
    private static Object      rankMember;

    // ── Name helper ──────────────────────────────────────────────────────────────

    /** FTB server-team name for a nation tag, e.g. "dominion_sky". */
    static String ftbTeamName(String nationTag) {
        return "dominion_" + nationTag.toLowerCase();
    }

    // ── Public operations ────────────────────────────────────────────────────────

    /**
     * Creates the FTB server team for a newly founded (player or bot) nation.
     * Uses "ftbteams server create <name>" which internally calls syncToAll.
     */
    static void createNationTeam(MinecraftServer server, Nation nation) {
        runCmd(server, "ftbteams server create " + ftbTeamName(nation.getTag()));
    }

    /**
     * Ensures the FTB team exists and adds the player to it as a MEMBER.
     * After mutating the team we call markDirty() + syncToAll() so that
     * all clients (and FTBChunks) receive the updated membership.
     */
    static void addPlayerToTeam(MinecraftServer server, String playerName, Nation nation) {
        createNationTeam(server, nation);   // idempotent – no-op if already present
        UUID playerId = resolveUUID(server, playerName);
        if (playerId != null) {
            addMemberReflect(server, ftbTeamName(nation.getTag()), playerId);
        }
    }

    /**
     * Removes a player from their former nation's FTB team.
     * Also calls markDirty() + syncToAll() after mutation.
     */
    static void removePlayerFromTeam(MinecraftServer server, String playerName, String nationTag) {
        UUID playerId = resolveUUID(server, playerName);
        if (playerId != null) {
            removeMemberReflect(server, ftbTeamName(nationTag), playerId);
        }
    }

    /**
     * Deletes the FTB server team when a nation is disbanded.
     */
    static void deleteNationTeam(MinecraftServer server, String nationTag) {
        runCmd(server, "ftbteams server delete " + ftbTeamName(nationTag));
    }

    // ── Reflection helpers ───────────────────────────────────────────────────────

    private static void addMemberReflect(MinecraftServer server, String teamName, UUID playerId) {
        try {
            Object team = getTeamByName(teamName);
            if (team == null) return;
            ensureRankAndMethods(team);
            mAddMember.invoke(team, playerId, rankMember);
            mMarkDirty.invoke(team);
            syncToAll(team);
            NationsForge.LOGGER.debug("[Dominion/FTBTeams] addMember({}, {}) + syncToAll ok", teamName, playerId);
        } catch (Exception e) {
            NationsForge.LOGGER.warn("[Dominion/FTBTeams] addMemberReflect failed for {}: {}", teamName, e.getMessage());
        }
    }

    private static void removeMemberReflect(MinecraftServer server, String teamName, UUID playerId) {
        try {
            Object team = getTeamByName(teamName);
            if (team == null) return;
            ensureRankAndMethods(team);
            mRemoveMember.invoke(team, playerId);
            mMarkDirty.invoke(team);
            syncToAll(team);
            NationsForge.LOGGER.debug("[Dominion/FTBTeams] removeMember({}, {}) + syncToAll ok", teamName, playerId);
        } catch (Exception e) {
            NationsForge.LOGGER.warn("[Dominion/FTBTeams] removeMemberReflect failed for {}: {}", teamName, e.getMessage());
        }
    }

    /** Returns the live TeamManagerImpl.INSTANCE (re-fetched every call — safe across server restarts). */
    private static Object getManagerInstance() throws Exception {
        if (reflectionFailed) return null;
        if (fTMInstance == null) {
            try {
                Class<?> tmClass = Class.forName("dev.ftb.mods.ftbteams.data.TeamManagerImpl");
                fTMInstance = tmClass.getField("INSTANCE");  // public static field
                mGetTeamByName = tmClass.getMethod("getTeamByName", String.class);
                mSyncToAll = tmClass.getMethod("syncToAll",
                        Array.newInstance(Class.forName("dev.ftb.mods.ftbteams.api.Team"), 0).getClass());
            } catch (Exception e) {
                reflectionFailed = true;
                NationsForge.LOGGER.warn("[Dominion/FTBTeams] Reflection init failed: {}", e.getMessage());
                throw e;
            }
        }
        return fTMInstance.get(null);   // fresh read every time
    }

    @SuppressWarnings("unchecked")
    private static Object getTeamByName(String teamName) throws Exception {
        Object manager = getManagerInstance();
        if (manager == null) return null;
        Optional<?> opt = (Optional<?>) mGetTeamByName.invoke(manager, teamName);
        return (opt != null && opt.isPresent()) ? opt.get() : null;
    }

    /** Sync the given team to all connected players (required after any member mutation). */
    private static void syncToAll(Object team) throws Exception {
        Object manager = getManagerInstance();
        if (manager == null || mSyncToAll == null) return;
        // syncToAll(Team... teams) — varargs == Team[]
        if (teamInterface == null)
            teamInterface = Class.forName("dev.ftb.mods.ftbteams.api.Team");
        Object arr = Array.newInstance(teamInterface, 1);
        Array.set(arr, 0, team);
        mSyncToAll.invoke(manager, arr);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void ensureRankAndMethods(Object team) throws Exception {
        if (mAddMember != null) return;   // already initialised
        // TeamRank enum
        teamRankClass = (Class<Enum>) Class.forName("dev.ftb.mods.ftbteams.api.TeamRank");
        rankMember = Enum.valueOf(teamRankClass, "MEMBER");
        // addMember / removeMember — declared on AbstractTeamBase (public)
        mAddMember    = findMethod(team.getClass(), "addMember",    UUID.class, teamRankClass);
        mRemoveMember = findMethod(team.getClass(), "removeMember", UUID.class);
        // markDirty — declared on AbstractTeam (public)
        mMarkDirty    = findMethod(team.getClass(), "markDirty");
        if (mAddMember != null)    mAddMember.setAccessible(true);
        if (mRemoveMember != null) mRemoveMember.setAccessible(true);
        if (mMarkDirty != null)    mMarkDirty.setAccessible(true);
    }

    /** Walk the class hierarchy to find the first matching declared method. */
    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredMethod(name, params); }
            catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static UUID resolveUUID(MinecraftServer server, String playerName) {
        try {
            Optional<com.mojang.authlib.GameProfile> profile = server.getProfileCache().get(playerName);
            return profile.map(com.mojang.authlib.GameProfile::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Command runner
    // ────────────────────────────────────────────────────────────

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
