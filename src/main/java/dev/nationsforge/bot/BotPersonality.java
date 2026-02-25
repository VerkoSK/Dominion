package dev.nationsforge.bot;

/**
 * Defines the AI personality of a bot nation — influences which
 * diplomatic and economic actions it will prefer each tick cycle.
 */
public enum BotPersonality {

    /**
     * Focuses on war and territorial expansion.
     * High war / rivalry probability, low trade probability.
     */
    AGGRESSIVE,

    /**
     * Focuses on trade routes and growing the treasury.
     * High trade-pact probability, low war probability.
     */
    ECONOMIC,

    /**
     * Balanced approach — forms alliances and expands moderately.
     */
    NEUTRAL,

    /**
     * Stays isolated — rarely interacts, focuses on internal growth.
     */
    ISOLATIONIST
}
