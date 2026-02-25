package dev.nationsforge.bot;

import java.util.Random;
import java.util.Set;

/**
 * Provides randomised names, tags, descriptions and colour palettes
 * for procedurally generated bot nations.
 */
public final class BotNationNames {

    private BotNationNames() {
    }

    private static final String[] ADJECTIVES = {
            "Iron", "Golden", "Silver", "Stone", "Shadow", "Azure", "Crimson", "Emerald",
            "Obsidian", "Burning", "Frozen", "Ancient", "Eternal", "Storm", "Hollow",
            "Shining", "Dark", "Bright", "Lost", "Forgotten", "Rising", "Fallen",
            "Sacred", "Wild", "Blazing", "Ash", "Jade", "Coral", "Void", "Amber"
    };

    private static final String[] NOUNS = {
            "Empire", "Republic", "Kingdom", "Confederation", "Alliance", "League",
            "Domain", "Realm", "Dominion", "Commonwealth", "Union", "Sovereignty",
            "Covenant", "Dynasty", "Order", "Collective", "Pact", "Council",
            "Principality", "Compact", "State", "Nation", "Accord", "Hegemony"
    };

    private static final String[] TAG_POOL = {
            "IRN", "GLD", "SLV", "STN", "SHD", "AZR", "CRM", "EMR",
            "OBS", "FRZ", "ANC", "ETR", "STM", "HLW", "RSN", "FLN",
            "NTH", "STH", "EST", "WST", "CTR", "BLZ", "ASH", "JAD",
            "COR", "VID", "AMB", "SUN", "MNT", "DST", "SEA", "FOR"
    };

    private static final String[] DESCRIPTIONS = {
            "A proud nation forged in the fires of ancient wars.",
            "Traders and diplomats who have maintained peace for generations.",
            "Warriors sworn to defend their mountain homeland.",
            "A seafaring culture known for exploration and commerce.",
            "Scholars and builders who have raised great cities from the wilderness.",
            "Nomadic clans united under a single banner of conquest.",
            "An industrious people known for their underground fortresses and vast wealth.",
            "A wise council that has guided their people through ages of darkness.",
            "Former conquerors who now seek peace and lasting prosperity.",
            "A secretive order that controls the crossroads of trade.",
            "Survivors of a great catastrophe, rebuilding their civilization.",
            "A federation of free cities bound by mutual defense and shared law.",
            "Champions of the old ways — fierce in battle, generous in peace.",
            "A merchant republic whose treasury rivals the gods themselves.",
            "Miners and smiths whose mastery of stone is unrivalled.",
            "Scholars who inherited the ruins of an older and mightier civilization."
    };

    /** Packed ARGB colours used for bot nation colour assignment (cycled by index). */
    public static final int[] BOT_COLOURS = {
            0xCC4444, // Red
            0x3C44AA, // Blue
            0x5E7C16, // Green
            0xFED83D, // Yellow
            0x8932B8, // Purple
            0x169C9C, // Cyan
            0xF9801D, // Orange
            0x835432, // Brown
            0x9D9D97, // Light Gray
            0xC74EBD, // Magenta
            0x80C71F, // Lime
            0x3AB3DA, // Light Blue
            0xB02E26, // Dark Red
            0x474F52, // Dark Gray
            0xF38BAA, // Pink
            0x1D1D21  // Black
    };

    public static String randomName(Random rng, Set<String> usedNames) {
        for (int attempt = 0; attempt < 300; attempt++) {
            String name = ADJECTIVES[rng.nextInt(ADJECTIVES.length)]
                    + " " + NOUNS[rng.nextInt(NOUNS.length)];
            if (!usedNames.contains(name.toLowerCase())) {
                usedNames.add(name.toLowerCase());
                return name;
            }
        }
        return "Nation-" + (usedNames.size() + 1);
    }

    public static String randomDescription(Random rng) {
        return DESCRIPTIONS[rng.nextInt(DESCRIPTIONS.length)];
    }

    /**
     * Returns a unique 2–4 character tag drawn from the pool,
     * or generates a random alpha tag when the pool is exhausted.
     */
    public static String randomTag(Random rng, Set<String> usedTags) {
        for (int i = 0; i < TAG_POOL.length; i++) {
            String tag = TAG_POOL[i];
            if (!usedTags.contains(tag)) {
                usedTags.add(tag);
                return tag;
            }
        }
        // Fallback: random 3-letter tag
        for (int attempt = 0; attempt < 500; attempt++) {
            char a = (char) ('A' + rng.nextInt(26));
            char b = (char) ('A' + rng.nextInt(26));
            char c = (char) ('A' + rng.nextInt(26));
            String tag = "" + a + b + c;
            if (!usedTags.contains(tag)) {
                usedTags.add(tag);
                return tag;
            }
        }
        return "B" + (rng.nextInt(89) + 10);
    }
}
