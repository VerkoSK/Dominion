package dev.nationsforge.nation;

/**
 * Hierarchical ranks within a Nation.
 *
 * <pre>
 *  SOVEREIGN   – Founder / absolute ruler. One per nation.
 *  CHANCELLOR  – Deputy, can do everything except abolish the nation.
 *  GENERAL     – Manages military: declare war, propose peace, recruit.
 *  DIPLOMAT    – Manages relations: alliances, trade pacts, embargoes.
 *  CITIZEN     – Regular member. Can participate but not govern.
 * </pre>
 */
public enum NationRank {

    SOVEREIGN(0, "Sovereign", 0xFF_FFD700), // gold
    CHANCELLOR(1, "Chancellor", 0xFF_C0C0C0), // silver
    GENERAL(2, "General", 0xFF_B05000), // bronze/rust
    DIPLOMAT(3, "Diplomat", 0xFF_00AAFF), // blue
    CITIZEN(4, "Citizen", 0xFF_AAAAAA); // grey

    public final int level;
    public final String displayName;
    /** Packed ARGB colour used in GUI lists. */
    public final int colour;

    NationRank(int level, String displayName, int colour) {
        this.level = level;
        this.displayName = displayName;
        this.colour = colour;
    }

    /** True if {@code this} outranks {@code other}. */
    public boolean outranks(NationRank other) {
        return this.level < other.level;
    }

    /** True if {@code this} can promote/demote {@code target}. */
    public boolean canManage(NationRank target) {
        return outranks(target) && this.level <= GENERAL.level;
    }

    public boolean canDeclareWar() {
        return this.level <= GENERAL.level;
    }

    public boolean canManageDiplomacy() {
        return this.level <= DIPLOMAT.level;
    }

    public boolean canInvitePlayers() {
        return this.level <= DIPLOMAT.level;
    }

    public boolean canKickMembers() {
        return this.level <= GENERAL.level;
    }

    public boolean canEditSettings() {
        return this.level <= CHANCELLOR.level;
    }

    public boolean isLeadership() {
        return this.level <= CHANCELLOR.level;
    }

    public static NationRank fromLevel(int level) {
        for (NationRank r : values()) {
            if (r.level == level)
                return r;
        }
        return CITIZEN;
    }
}
