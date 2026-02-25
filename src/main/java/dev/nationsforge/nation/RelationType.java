package dev.nationsforge.nation;

/**
 * The diplomatic standing between two nations.
 *
 * <pre>
 *  ALLIANCE    – Full military alliance. Nation members can be friendly-fire-exempt.
 *  TRADE_PACT  – Economic friendship — no war, shared market bonus.
 *  NEUTRAL     – Default state: no commitment.
 *  RIVALRY     – Publicly declared cold hostility. No active combat, but PvP enabled.
 *  WAR         – Active state of war — full PvP enabled between members.
 * </pre>
 */
public enum RelationType {

    ALLIANCE("Alliance", 0xFF_00DD44, true, false),
    TRADE_PACT("Trade Pact", 0xFF_00AAFF, true, false),
    NEUTRAL("Neutral", 0xFF_AAAAAA, false, false),
    RIVALRY("Rivalry", 0xFF_FF8800, false, true),
    WAR("War", 0xFF_FF2222, false, true);

    public final String displayName;
    /** Hex ARGB colour for GUI rendering. */
    public final int colour;
    /** Whether this relation is considered friendly (no PvP). */
    public final boolean friendly;
    /** Whether this relation enables PvP between members. */
    public final boolean hostile;

    RelationType(String displayName, int colour, boolean friendly, boolean hostile) {
        this.displayName = displayName;
        this.colour = colour;
        this.friendly = friendly;
        this.hostile = hostile;
    }

    public boolean isPeaceful() {
        return !hostile;
    }
}
