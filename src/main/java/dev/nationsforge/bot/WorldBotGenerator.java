package dev.nationsforge.bot;

import dev.nationsforge.NationsForge;
import dev.nationsforge.integration.ftbchunks.FTBChunksProxy;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationPowerCalculator;
import dev.nationsforge.nation.NationSavedData;
import dev.nationsforge.nation.RelationType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Generates the world's initial set of bot (AI) nations the first time
 * a server starts on a new world. Called from
 * {@link dev.nationsforge.event.NationServerEvents}.
 *
 * <h3>What is generated:</h3>
 * <ul>
 * <li>{@value #MIN_BOTS}–{@value #MAX_BOTS} bot nations with random names,
 * tags, colours, flags and descriptions seeded from the world's RNG.</li>
 * <li>Spread capital coordinates (±{@value #CAPITAL_RANGE} blocks, at least
 * {@value #CAPITAL_MIN_SPACING} blocks apart).</li>
 * <li>Starting territory ({@value #MIN_TERRITORY}–{@value #MAX_TERRITORY}
 * chunks) claimed via FTBChunks and a starting treasury.</li>
 * <li>Initial diplomacy between bots to seed a living geopolitical
 * ecosystem from day one.</li>
 * </ul>
 */
public final class WorldBotGenerator {

    private WorldBotGenerator() {
    }

    // ── Configuration ────────────────────────────────────────────────────────────

    private static final int MIN_BOTS = 15;
    private static final int MAX_BOTS = 24;
    private static final int MIN_TERRITORY = 12;
    private static final int MAX_TERRITORY = 48;
    private static final long STARTING_TREASURY = 2_500L;

    /** Capitals are placed within ±CAPITAL_RANGE blocks of world origin. */
    private static final int CAPITAL_RANGE = 4000;
    /** Minimum horizontal distance (blocks) between any two bot capitals. */
    private static final int CAPITAL_MIN_SPACING = 400;

    // ── Entry point ──────────────────────────────────────────────────────────────

    /**
     * Runs world bot generation if it hasn't happened yet.
     * Safe to call on every server start — guarded by the saved-data flag.
     */
    public static void generate(MinecraftServer server, NationSavedData data) {
        if (data.isWorldBotGenerated()) {
            NationsForge.LOGGER.debug("[Dominion] Bot nations already generated — skipping.");
            return;
        }

        long worldSeed;
        try {
            worldSeed = server.overworld().getSeed();
        } catch (Exception e) {
            worldSeed = System.currentTimeMillis();
        }
        Random rng = new Random(worldSeed ^ 0x4D494E49L);

        int botCount = MIN_BOTS + rng.nextInt(MAX_BOTS - MIN_BOTS + 1);
        NationsForge.LOGGER.info("[Dominion] Generating {} bot nations for new world…", botCount);

        Set<String> usedTags = new HashSet<>();
        Set<String> usedNames = new HashSet<>();
        for (Nation n : data.getAllNations()) {
            usedTags.add(n.getTag().toUpperCase());
            usedNames.add(n.getName().toLowerCase());
        }

        // Pre-generate spread capitals so they are available during nation creation
        List<int[]> capitals = spreadCapitals(rng, botCount);

        List<Nation> bots = new ArrayList<>();

        for (int i = 0; i < botCount; i++) {
            String name = BotNationNames.randomName(rng, usedNames);
            String tag = BotNationNames.randomTag(rng, usedTags);
            int colour = BotNationNames.BOT_COLOURS[i % BotNationNames.BOT_COLOURS.length];
            String desc = BotNationNames.randomDescription(rng);

            UUID botLeader = UUID.nameUUIDFromBytes(
                    ("dominion_bot_leader_v1:" + tag).getBytes(StandardCharsets.UTF_8));

            Nation bot = data.createBotNation(name, tag.toUpperCase(), colour, botLeader);
            bot.setDescription(desc);

            int territory = MIN_TERRITORY + rng.nextInt(MAX_TERRITORY - MIN_TERRITORY + 1);
            bot.setTerritory(territory);
            bot.addTreasury(STARTING_TREASURY + rng.nextInt(3000));
            bot.setFlag(BotNationAI.randomFlag(rng));

            // Assign capital coordinates
            int[] cap = capitals.get(i);
            bot.setCapital(cap[0], cap[1]);

            bots.add(bot);
        }

        // Seed initial relations between bot nations
        seedRelations(bots, rng, data);

        // Calculate initial power for all bots
        for (Nation bot : bots) {
            NationPowerCalculator.recalculate(bot);
        }

        data.setWorldBotGenerated(true);
        data.setDirty();

        // Create FTB Teams server teams and claim FTBChunks territory for each bot
        setupBotTeamsAndChunks(server, bots);

        // Announce to online players
        announceGeneration(server, bots, rng);
    }

    // ── Capital spread ───────────────────────────────────────────────────────────

    /**
     * Generates {@code count} [x, z] coordinate pairs that are at least
     * {@value #CAPITAL_MIN_SPACING} blocks apart within ±{@value #CAPITAL_RANGE}.
     * Uses rejection sampling with a generous attempt budget.
     */
    private static List<int[]> spreadCapitals(Random rng, int count) {
        List<int[]> placed = new ArrayList<>(count);
        int maxAttempts = 300;

        for (int i = 0; i < count; i++) {
            int[] chosen = null;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                int x = rng.nextInt(CAPITAL_RANGE * 2 + 1) - CAPITAL_RANGE;
                int z = rng.nextInt(CAPITAL_RANGE * 2 + 1) - CAPITAL_RANGE;
                if (isFarEnough(placed, x, z)) {
                    chosen = new int[] { x, z };
                    break;
                }
            }
            if (chosen == null) {
                // Fallback: just place it (very unlikely to be needed)
                chosen = new int[] {
                        rng.nextInt(CAPITAL_RANGE * 2 + 1) - CAPITAL_RANGE,
                        rng.nextInt(CAPITAL_RANGE * 2 + 1) - CAPITAL_RANGE
                };
            }
            placed.add(chosen);
        }
        return placed;
    }

    private static boolean isFarEnough(List<int[]> placed, int x, int z) {
        for (int[] p : placed) {
            long dx = x - p[0];
            long dz = z - p[1];
            if (dx * dx + dz * dz < (long) CAPITAL_MIN_SPACING * CAPITAL_MIN_SPACING)
                return false;
        }
        return true;
    }

    // ── FTB integration
    // ───────────────────────────────────────────────────────────

    /**
     * For every bot nation:
     * 1. Creates the FTB Teams server team "dominion_TAG".
     * 2. Runs {@code ftbchunks admin claim_as} to claim territory around the
     * capital.
     *
     * Both operations are best-effort and logged. If FTB Teams / FTBChunks
     * is absent the commands simply fail silently.
     */
    private static void setupBotTeamsAndChunks(MinecraftServer server, List<Nation> bots) {
        for (Nation bot : bots) {
            String teamName = "dominion_" + bot.getTag().toLowerCase();
            int capX = bot.getCapitalX();
            int capZ = bot.getCapitalZ();

            // 1. Create FTB Teams server team via command (idempotent, calls syncToAll internally)
            runCmd(server, "ftbteams server create " + teamName);

            // 2. Claim territory via direct FTBChunks API (bypasses command-arg parsing issues)
            int territory = (int) bot.getTerritory();
            int radius = Math.max(1, (int) Math.round(Math.sqrt(territory) / 2.0));

            if (FTBChunksProxy.isLoaded()) {
                FTBChunksProxy.claimChunksForBotTeam(server, teamName, capX, capZ, radius);
            } else {
                // FTBChunks not installed — command fallback (no-op if mod absent)
                runCmd(server, "ftbchunks admin claim_as " + teamName
                        + " " + radius + " " + capX + " " + capZ);
            }

            NationsForge.LOGGER.debug("[Dominion] Bot '{}' team+chunks set up: {}×{} radius at ({},{})",
                    bot.getTag(), territory, radius, capX, capZ);
        }
    }

    private static boolean runCmd(MinecraftServer server, String command) {
        try {
            CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput();
            int result = server.getCommands().performPrefixedCommand(src, command);
            NationsForge.LOGGER.debug("[Dominion/WorldGen] '{}' → result={}", command, result);
            return result > 0;
        } catch (Exception e) {
            NationsForge.LOGGER.warn("[Dominion/WorldGen] Failed: '{}' — {}", command, e.getMessage());
            return false;
        }
    }

    // ── Relation seeding ─────────────────────────────────────────────────────────

    private static void seedRelations(List<Nation> bots, Random rng, NationSavedData data) {
        for (int i = 0; i < bots.size(); i++) {
            for (int j = i + 1; j < bots.size(); j++) {
                Nation a = bots.get(i);
                Nation b = bots.get(j);

                int roll = rng.nextInt(100);
                RelationType relAB;

                if (roll < 10)
                    relAB = RelationType.ALLIANCE;
                else if (roll < 22)
                    relAB = RelationType.TRADE_PACT;
                else if (roll < 30)
                    relAB = RelationType.RIVALRY;
                else if (roll < 35)
                    relAB = RelationType.WAR;
                else
                    relAB = RelationType.NEUTRAL;

                if (relAB != RelationType.NEUTRAL) {
                    a.setRelation(b.getId(), relAB, "Historical");
                    b.setRelation(a.getId(), relAB, "Historical");
                }
            }
        }
        data.setDirty();
    }

    // ── Announcement ─────────────────────────────────────────────────────────────

    private static void announceGeneration(MinecraftServer server,
            List<Nation> bots, Random rng) {

        server.getPlayerList().broadcastSystemMessage(
                Component.literal(""), false);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§6§l✦ Dominion §r§7— The world is alive."), false);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§7" + bots.size()
                        + " sovereign nations already walk the land."),
                false);

        List<Nation> shuffled = new ArrayList<>(bots);
        Collections.shuffle(shuffled, rng);
        int show = Math.min(5, shuffled.size());

        for (int i = 0; i < show; i++) {
            Nation n = shuffled.get(i);
            long wars = n.getRelations().values().stream()
                    .filter(r -> r.getType() == RelationType.WAR).count();
            long allies = n.getRelations().values().stream()
                    .filter(r -> r.getType() == RelationType.ALLIANCE).count();

            String extra = "";
            if (wars > 0)
                extra = " §c(at war)";
            else if (allies > 0)
                extra = " §a(allied)";

            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§8  [§f" + n.getTag() + "§8] §f"
                            + n.getName() + extra),
                    false);
        }

        if (bots.size() > show) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§8  …and §7" + (bots.size() - show) + " §8more."), false);
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§7Press §eN §7to open the Nations menu."), false);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(""), false);
    }
}
