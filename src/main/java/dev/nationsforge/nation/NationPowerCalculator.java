package dev.nationsforge.nation;

import java.util.concurrent.TimeUnit;

/**
 * Calculates a nation's total POWER from multiple factors.
 *
 * Power Formula
 * ─────────────
 * members × 100 (each member contributes)
 * territory × 50 (each FTB-Chunks claimed chunk)
 * alliances × 200 (diplomacy pays off)
 * trade_pacts × 75
 * treasury / 5000 (wealth converted to power, rounded down)
 * age_days × 5 (longevity bonus)
 * active_wars × -100 (war is costly — territory gain should compensate)
 *
 * Power Tiers (shown in HUD / overview)
 * ──────────────────────────────────────
 * 0 – 499 → Tribe
 * 500 – 1 999 → Village
 * 2 000 – 4 999 → Town
 * 5 000 – 9 999 → City-State
 * 10 000 – 24 999→ Kingdom
 * 25 000 – 49 999→ Empire
 * 50 000 + → Superpower
 */
public final class NationPowerCalculator {

    private NationPowerCalculator() {
    }

    // ── Constants ────────────────────────────────────────────────────────────────

    public static final long PER_MEMBER = 100L;
    public static final long PER_CHUNK = 50L;
    public static final long PER_ALLIANCE = 200L;
    public static final long PER_TRADE = 75L;
    public static final long WAR_PENALTY = 100L;
    public static final long PER_AGE_DAY = 5L;
    public static final long TREASURY_DIVISOR = 5_000L;

    // ── Main calculation ─────────────────────────────────────────────────────────

    /**
     * Computes and caches the power of one nation.
     * Also updates {@link Nation#setScore(long)} so the server-side leaderboard
     * stays in sync.
     *
     * @return the newly computed power value
     */
    public static long recalculate(Nation nation) {
        long power = 0;

        // Members
        power += nation.getMemberCount() * PER_MEMBER;

        // Territory (chunks)
        power += nation.getTerritory() * PER_CHUNK;

        // Diplomacy
        long alliances = nation.getRelations().values().stream()
                .filter(r -> r.getType() == RelationType.ALLIANCE).count();
        long trades = nation.getRelations().values().stream()
                .filter(r -> r.getType() == RelationType.TRADE_PACT).count();
        long wars = nation.getRelations().values().stream()
                .filter(r -> r.getType() == RelationType.WAR).count();

        power += alliances * PER_ALLIANCE;
        power += trades * PER_TRADE;
        power -= wars * WAR_PENALTY;

        // Treasury wealth
        power += nation.getTreasury() / TREASURY_DIVISOR;

        // Longevity bonus
        long ageMs = System.currentTimeMillis() - nation.getCreatedAt();
        long ageDays = TimeUnit.MILLISECONDS.toDays(ageMs);
        power += ageDays * PER_AGE_DAY;

        power = Math.max(0, power);

        nation.setPower(power);
        nation.setScore(power); // keep score in sync for backwards-compat display

        return power;
    }

    // ── Tier system ──────────────────────────────────────────────────────────────

    public static Tier getTier(long power) {
        if (power >= 50_000)
            return Tier.SUPERPOWER;
        if (power >= 25_000)
            return Tier.EMPIRE;
        if (power >= 10_000)
            return Tier.KINGDOM;
        if (power >= 5_000)
            return Tier.CITY_STATE;
        if (power >= 2_000)
            return Tier.TOWN;
        if (power >= 500)
            return Tier.VILLAGE;
        return Tier.TRIBE;
    }

    public enum Tier {
        TRIBE("Tribe", 0xFF_888888),
        VILLAGE("Village", 0xFF_88BB66),
        TOWN("Town", 0xFF_66AAEE),
        CITY_STATE("City-State", 0xFF_EEDD44),
        KINGDOM("Kingdom", 0xFF_CC8833),
        EMPIRE("Empire", 0xFF_CC4444),
        SUPERPOWER("Superpower", 0xFF_FF44FF);

        public final String displayName;
        /** Packed ARGB colour (alpha=FF). */
        public final int colour;

        Tier(String displayName, int colour) {
            this.displayName = displayName;
            this.colour = colour;
        }
    }
}
