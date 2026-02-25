package dev.nationsforge.bot;

import dev.nationsforge.NationsForge;
import dev.nationsforge.nation.DiplomacyRequest;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationFlag;
import dev.nationsforge.nation.NationPowerCalculator;
import dev.nationsforge.nation.NationSavedData;
import dev.nationsforge.nation.RelationType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Drives the AI behaviour of bot nations each server tick-cycle
 * (called from NationTickHandler).
 *
 * Each cycle the AI:
 * <ol>
 * <li>Collects passive income from territory and "members".</li>
 * <li>Rolls a random event: expand, recruit, diplomacy or announcement.</li>
 * <li>Recalculates power.</li>
 * </ol>
 *
 * Event probabilities are adjusted per {@link BotPersonality} stored in
 * the bot nation's description suffix (format: "...|PERSONALITY").
 */
public final class BotNationAI {

    private BotNationAI() {
    }

    // ── Income constants ─────────────────────────────────────────────────────────

    public static final long INCOME_PER_CHUNK = 10L;
    public static final long INCOME_PER_MEMBER_EQUIVALENT = 30L;

    // ── Event thresholds (out of 100) ────────────────────────────────────────────

    private static final int CHANCE_EXPAND = 18;
    private static final int CHANCE_RECRUIT = 22;
    private static final int CHANCE_DIPLOMACY = 12;
    private static final int CHANCE_ANNOUNCE = 10;

    // ── Message pools ────────────────────────────────────────────────────────────

    private static final String[] EXPAND_MSGS = {
            "§8[§b⚑ Expansion§8] §f%s §7has pushed its borders into new territory.",
            "§8[§b⚑ Expansion§8] §7Settlers from §f%s §7are moving %s to claim new land.",
            "§8[§b⚑ Expansion§8] §7The banners of §f%s §7fly over the %s wilderness.",
            "§8[§b⚑ Expansion§8] §7Scouts report: §f%s §7has charted new %s territories."
    };

    private static final String[] RECRUIT_MSGS = {
            "§8[§7✦ Growth§8] §7Settlers flock to §f%s§7, swelling its population.",
            "§8[§7✦ Growth§8] §f%s §7is growing — new citizens answer the call.",
            "§8[§7✦ Growth§8] §7Refugees and pilgrims seek new homes in §f%s§7."
    };

    private static final String[] PROSPERITY_MSGS = {
            "§8[§6⚜ Prosperity§8] §f%s §7is flourishing. The treasury overflows.",
            "§8[§6⚜ Prosperity§8] §7A golden age begins to dawn over §f%s§7.",
            "§8[§6⚜ Prosperity§8] §7Markets in §f%s §7are booming with trade."
    };

    private static final String[] WAR_MSGS = {
            "§8[§c⚔ War§8] §c%s §7has declared war on §c%s§7!",
            "§8[§c⚔ War§8] §7Soldiers march as §c%s §7engages §c%s §7in open conflict!",
            "§8[§c⚔ War§8] §7Smoke rises over the border as §c%s §7and §c%s §7clash!"
    };

    private static final String[] PEACE_MSGS = {
            "§8[§a✦ Peace§8] §a%s §7and §a%s §7have signed a ceasefire.",
            "§8[§a✦ Peace§8] §7Diplomats celebrate as §a%s §7and §a%s §7lay down arms."
    };

    private static final String[] ALLIANCE_MSGS = {
            "§8[§2⚑ Alliance§8] §a%s §7and §a%s §7have forged a powerful alliance!",
            "§8[§2⚑ Alliance§8] §7Banners of two nations stand together: §a%s §7and §a%s§7."
    };

    private static final String[] TRADE_MSGS = {
            "§8[§e♦ Trade§8] §e%s §7and §e%s §7have signed a trade agreement.",
            "§8[§e♦ Trade§8] §7Merchants rejoice as §e%s §7and §e%s §7open trade routes."
    };

    private static final String[] DIRECTIONS = {
            "northern", "southern", "eastern", "western", "northeastern", "northwestern"
    };

    // ── Main tick ────────────────────────────────────────────────────────────────

    public static void tick(MinecraftServer server, Nation bot,
            NationSavedData data, Random rng) {

        // 1. Passive income: territory + virtual population
        bot.addTreasury(bot.getTerritory() * INCOME_PER_CHUNK);
        bot.addTreasury((long) bot.getMemberCount() * INCOME_PER_MEMBER_EQUIVALENT);

        // 2. Trade-pact income bonus
        long tradeCount = bot.getRelations().values().stream()
                .filter(r -> r.getType() == RelationType.TRADE_PACT).count();
        bot.addTreasury(tradeCount * 80L);

        // 3. Random event roll
        int roll = rng.nextInt(100);

        if (roll < CHANCE_EXPAND && bot.getTreasury() > 400) {
            handleExpand(server, bot, rng);
        } else if (roll < CHANCE_EXPAND + CHANCE_RECRUIT) {
            handleRecruit(server, bot, rng);
        } else if (roll < CHANCE_EXPAND + CHANCE_RECRUIT + CHANCE_DIPLOMACY) {
            handleDiplomacy(server, bot, data, rng);
        } else if (roll < CHANCE_EXPAND + CHANCE_RECRUIT + CHANCE_DIPLOMACY + CHANCE_ANNOUNCE) {
            handleAnnounce(server, bot, rng);
        }

        // 4. Recalculate power
        NationPowerCalculator.recalculate(bot);
    }

    // ── Event handlers ───────────────────────────────────────────────────────────

    private static void handleExpand(MinecraftServer server, Nation bot, Random rng) {
        long cost = 200L + rng.nextInt(300);
        if (bot.getTreasury() < cost)
            return;
        long gain = 1 + rng.nextInt(4);
        bot.setTerritory(bot.getTerritory() + gain);
        bot.addTreasury(-cost);

        if (rng.nextInt(100) < 35) {
            String dir = DIRECTIONS[rng.nextInt(DIRECTIONS.length)];
            String template = EXPAND_MSGS[rng.nextInt(EXPAND_MSGS.length)];
            broadcast(server, String.format(template, bot.getName(), dir));
        }
    }

    private static void handleRecruit(MinecraftServer server, Nation bot, Random rng) {
        // Bots grow territory slightly to represent population growth
        if (rng.nextBoolean() && bot.getTreasury() > 100) {
            bot.setTerritory(bot.getTerritory() + 1);
            bot.addTreasury(-50L);
        }
        if (rng.nextInt(100) < 25) {
            broadcast(server, String.format(
                    RECRUIT_MSGS[rng.nextInt(RECRUIT_MSGS.length)], bot.getName()));
        }
    }

    private static void handleDiplomacy(MinecraftServer server, Nation bot,
            NationSavedData data, Random rng) {
        List<Nation> others = new ArrayList<>(data.getAllNations());
        others.remove(bot);
        if (others.isEmpty())
            return;

        Nation target = others.get(rng.nextInt(others.size()));
        RelationType current = bot.getRelationWith(target.getId());

        if (current == RelationType.WAR && rng.nextInt(100) < 45) {
            // Ceasefire
            bot.setRelation(target.getId(), RelationType.NEUTRAL, "Ceasefire");
            target.setRelation(bot.getId(), RelationType.NEUTRAL, "Ceasefire");
            broadcast(server, String.format(PEACE_MSGS[rng.nextInt(PEACE_MSGS.length)],
                    bot.getName(), target.getName()));

        } else if (current == RelationType.NEUTRAL && rng.nextInt(100) < 22) {
            if (rng.nextBoolean()) {
                // Alliance
                bot.setRelation(target.getId(), RelationType.ALLIANCE, "AI diplomacy");
                target.setRelation(bot.getId(), RelationType.ALLIANCE, "AI diplomacy");
                broadcast(server, String.format(ALLIANCE_MSGS[rng.nextInt(ALLIANCE_MSGS.length)],
                        bot.getName(), target.getName()));
            } else {
                // Trade pact
                bot.setRelation(target.getId(), RelationType.TRADE_PACT, "AI trade");
                target.setRelation(bot.getId(), RelationType.TRADE_PACT, "AI trade");
                broadcast(server, String.format(TRADE_MSGS[rng.nextInt(TRADE_MSGS.length)],
                        bot.getName(), target.getName()));
            }

        } else if (current == RelationType.ALLIANCE && rng.nextInt(100) < 6) {
            // Alliance dissolves into rivalry
            bot.setRelation(target.getId(), RelationType.RIVALRY, "Diplomatic falling out");
            target.setRelation(bot.getId(), RelationType.RIVALRY, "Diplomatic falling out");
            broadcast(server, "§8[§7Diplomacy§8] §7The alliance between §f"
                    + bot.getName() + " §7and §f" + target.getName() + " §7has dissolved.");

        } else if (current == RelationType.RIVALRY && rng.nextInt(100) < 18) {
            // Rivalry escalates to war
            bot.setRelation(target.getId(), RelationType.WAR, "AI war declaration");
            target.setRelation(bot.getId(), RelationType.WAR, "AI war declaration");
            broadcast(server, String.format(WAR_MSGS[rng.nextInt(WAR_MSGS.length)],
                    bot.getName(), target.getName()));
        }

        data.setDirty();
    }

    private static void handleAnnounce(MinecraftServer server, Nation bot, Random rng) {
        if (bot.getTreasury() > 3_000) {
            broadcast(server, String.format(
                    PROSPERITY_MSGS[rng.nextInt(PROSPERITY_MSGS.length)], bot.getName()));
        }
    }
    // ── Incoming request evaluation ──────────────────────────────────────────────

    /**
     * Evaluates a player-sent diplomacy request directed at a bot nation.
     * Returns {@code true} if the bot accepts, {@code false} to decline.
     *
     * Decision logic:
     * <ul>
     *   <li>WAR status: only peace proposals considered (60 % base accept).</li>
     *   <li>NEUTRAL: trade pacts accepted ~70 %, alliances ~40 %.</li>
     *   <li>RIVALRY: peace at 50 %, alliance at 15 %.</li>
     *   <li>ALLY: worsening proposals accepted at only 10 %.</li>
     *   <li>AGGRESSIVE personality: +20 % reject; DIPLOMATIC: +20 % accept.</li>
     * </ul>
     */
    public static boolean evaluateIncomingRequest(Nation bot, DiplomacyRequest req, Random rng) {
        RelationType current = bot.getRelationWith(req.getFromNationId());
        RelationType proposed = req.getProposedType();

        // Personality modifier: parse from description suffix "...|PERSONALITY"
        int personalityAcceptBonus = 0;
        String desc = bot.getDescription();
        if (desc != null) {
            int pipe = desc.lastIndexOf('|');
            if (pipe >= 0) {
                String pStr = desc.substring(pipe + 1).trim();
                if ("DIPLOMATIC".equalsIgnoreCase(pStr))  personalityAcceptBonus =  20;
                if ("AGGRESSIVE".equalsIgnoreCase(pStr))  personalityAcceptBonus = -20;
                if ("ISOLATIONIST".equalsIgnoreCase(pStr)) personalityAcceptBonus = -10;
            }
        }

        int chance; // base accept chance out of 100
        if (current == RelationType.WAR) {
            // In war: only accept a return to neutral, refuse everything else
            if (proposed == RelationType.NEUTRAL) {
                chance = 60;
            } else {
                chance = 5;
            }
        } else if (current == RelationType.ALLIANCE) {
            // Already allied: reject any demotion
            if (proposed == RelationType.ALLIANCE || proposed == RelationType.TRADE_PACT) {
                chance = 90; // redundant upgrade
            } else {
                chance = 10;
            }
        } else if (current == RelationType.TRADE_PACT) {
            if (proposed == RelationType.ALLIANCE) {
                chance = 55;
            } else if (proposed == RelationType.TRADE_PACT) {
                chance = 90;
            } else {
                chance = 20;
            }
        } else if (current == RelationType.RIVALRY) {
            if (proposed == RelationType.NEUTRAL) {
                chance = 50;
            } else if (proposed == RelationType.ALLIANCE) {
                chance = 15;
            } else if (proposed == RelationType.TRADE_PACT) {
                chance = 30;
            } else {
                chance = 10;
            }
        } else {
            // NEUTRAL baseline
            if (proposed == RelationType.TRADE_PACT) {
                chance = 70;
            } else if (proposed == RelationType.ALLIANCE) {
                chance = 40;
            } else if (proposed == RelationType.RIVALRY || proposed == RelationType.WAR) {
                chance = 8;
            } else {
                chance = 60;
            }
        }

        chance = Math.max(2, Math.min(98, chance + personalityAcceptBonus));
        return rng.nextInt(100) < chance;
    }
    // ── Utilities ────────────────────────────────────────────────────────────────

    /**
     * Broadcasts a world-event message to all online players and the server log.
     */
    static void broadcast(MinecraftServer server, String msg) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(msg), false);
        NationsForge.LOGGER.info("[Dominion/World] {}", msg.replaceAll("§.", ""));
    }

    /**
     * Generates a random banner flag for a bot nation — random base colour plus
     * 1–3 randomly chosen overlay layers.
     */
    public static NationFlag randomFlag(Random rng) {
        NationFlag flag = new NationFlag();
        flag.setBaseColorId(rng.nextInt(16));
        int layerCount = 1 + rng.nextInt(3); // 1–3 layers
        for (int i = 0; i < layerCount; i++) {
            String pattern = NationFlag.PATTERN_CODES[rng.nextInt(NationFlag.PATTERN_CODES.length)];
            int colour = rng.nextInt(16);
            flag.addLayer(pattern, colour);
        }
        return flag;
    }
}
