package dev.nationsforge.bot;

import dev.nationsforge.NationsForge;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationPowerCalculator;
import dev.nationsforge.nation.NationSavedData;
import dev.nationsforge.nation.RelationType;
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
 * a server starts on a new world. Called from {@link dev.nationsforge.event.NationServerEvents}.
 *
 * <h3>What is generated:</h3>
 * <ul>
 *   <li>{@value #MIN_BOTS}–{@value #MAX_BOTS} bot nations with random names, tags,
 *       colours, flags and descriptions seeded from the world's RNG.</li>
 *   <li>Starting territory ({@value #MIN_TERRITORY}–{@value #MAX_TERRITORY} chunks)
 *       and a starting treasury so bots can act immediately.</li>
 *   <li>Initial diplomacy between bots: alliances, trade pacts, rivalries and wars
 *       to seed a living geopolitical ecosystem from day one.</li>
 * </ul>
 *
 * <h3>Idempotency:</h3>
 * The flag {@link NationSavedData#isWorldBotGenerated()} prevents regeneration
 * on subsequent server starts.
 */
public final class WorldBotGenerator {

    private WorldBotGenerator() {
    }

    // ── Configuration ────────────────────────────────────────────────────────────

    private static final int MIN_BOTS = 8;
    private static final int MAX_BOTS = 14;
    private static final int MIN_TERRITORY = 12;
    private static final int MAX_TERRITORY = 48;
    private static final long STARTING_TREASURY = 2_500L;

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

        // Seed from world seed so bots are consistent across reloads in theory
        // (generation only runs once anyway; the seed just makes it feel deterministic)
        long worldSeed;
        try {
            worldSeed = server.overworld().getSeed();
        } catch (Exception e) {
            worldSeed = System.currentTimeMillis();
        }
        Random rng = new Random(worldSeed ^ 0x4D494E49L); // "MINI" ASCII mask

        int botCount = MIN_BOTS + rng.nextInt(MAX_BOTS - MIN_BOTS + 1);
        NationsForge.LOGGER.info("[Dominion] Generating {} bot nations for new world…", botCount);

        // Collect existing names/tags to avoid collisions with player nations
        Set<String> usedTags = new HashSet<>();
        Set<String> usedNames = new HashSet<>();
        for (Nation n : data.getAllNations()) {
            usedTags.add(n.getTag().toUpperCase());
            usedNames.add(n.getName().toLowerCase());
        }

        List<Nation> bots = new ArrayList<>();

        for (int i = 0; i < botCount; i++) {
            String name = BotNationNames.randomName(rng, usedNames);
            String tag  = BotNationNames.randomTag(rng, usedTags);
            int colour  = BotNationNames.BOT_COLOURS[i % BotNationNames.BOT_COLOURS.length];
            String desc = BotNationNames.randomDescription(rng);

            // Deterministic fake UUID — never matches a real player UUID
            UUID botLeader = UUID.nameUUIDFromBytes(
                    ("dominion_bot_leader_v1:" + tag).getBytes(StandardCharsets.UTF_8));

            Nation bot = data.createBotNation(name, tag.toUpperCase(), colour, botLeader);
            bot.setDescription(desc);
            bot.setTerritory(MIN_TERRITORY + rng.nextInt(MAX_TERRITORY - MIN_TERRITORY + 1));
            bot.addTreasury(STARTING_TREASURY + rng.nextInt(3000));
            bot.setFlag(BotNationAI.randomFlag(rng));

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

        // Announce to online players
        announceGeneration(server, bots, rng);
    }

    // ── Relation seeding ─────────────────────────────────────────────────────────

    private static void seedRelations(List<Nation> bots, Random rng, NationSavedData data) {
        for (int i = 0; i < bots.size(); i++) {
            for (int j = i + 1; j < bots.size(); j++) {
                Nation a = bots.get(i);
                Nation b = bots.get(j);

                int roll = rng.nextInt(100);
                RelationType relAB;

                if      (roll < 10) relAB = RelationType.ALLIANCE;
                else if (roll < 22) relAB = RelationType.TRADE_PACT;
                else if (roll < 30) relAB = RelationType.RIVALRY;
                else if (roll < 35) relAB = RelationType.WAR;
                else                relAB = RelationType.NEUTRAL;

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
                        + " sovereign nations already walk the land."), false);

        // Announce a sample of interesting bot nations (shuffle so it's different each time)
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
            if (wars > 0)      extra = " §c(at war)";
            else if (allies > 0) extra = " §a(allied)";

            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§8  [§f" + n.getTag() + "§8] §f"
                            + n.getName() + extra), false);
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
