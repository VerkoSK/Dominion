package dev.nationsforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.nationsforge.integration.ftbteams.FTBTeamsHelper;
import dev.nationsforge.nation.NationManager;
import dev.nationsforge.nation.NationRank;
import dev.nationsforge.nation.NationSavedData;
import dev.nationsforge.nation.RelationType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;
import java.util.UUID;

/**
 * /nation commands — mostly admin shortcuts.
 * Players use the GUI; this is for ops and console management.
 *
 * /nation list
 * /nation info <name>
 * /nation create <name> <TAG> <colour_hex>
 * /nation disband <name>
 * /nation join <player> <nation_name>
 * /nation kick <player>
 * /nation setrank <player> <rank>
 * /nation setrelation <nation_a> <nation_b> <type>
 * /nation score <nation_name> <amount>
 * /nation treasury <nation_name> <amount>
 * /nation reload — force sync to all players
 */
@Mod.EventBusSubscriber(modid = dev.nationsforge.NationsForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NationsCommand {

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
                CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

                dispatcher.register(Commands.literal("nation")
                                .then(Commands.literal("list")
                                                .executes(ctx -> listNations(ctx.getSource())))

                                .then(Commands.literal("info")
                                                .then(Commands.argument("name", StringArgumentType.string())
                                                                .executes(ctx -> nationInfo(ctx.getSource(),
                                                                                StringArgumentType.getString(ctx,
                                                                                                "name")))))

                                .then(Commands.literal("create")
                                                .requires(s -> s.hasPermission(2))
                                                .then(Commands.argument("name", StringArgumentType.string())
                                                                .then(Commands.argument("tag",
                                                                                StringArgumentType.word())
                                                                                .then(Commands.argument("player",
                                                                                                EntityArgument.player())
                                                                                                .executes(ctx -> {
                                                                                                        ServerPlayer founder = EntityArgument
                                                                                                                        .getPlayer(ctx, "player");
                                                                                                        String name = StringArgumentType
                                                                                                                        .getString(ctx, "name");
                                                                                                        String tag = StringArgumentType
                                                                                                                        .getString(ctx, "tag");
                                                                                                        var result = NationManager
                                                                                                                        .createNation(
                                                                                                                                        ctx.getSource().getServer(),
                                                                                                                                        founder.getUUID(),
                                                                                                                                        name,
                                                                                                                                        tag,
                                                                                                                                        0x5599FF,
                                                                                                                                        "");
                                                                                                        ctx.getSource().sendSuccess(
                                                                                                                        () -> Component.literal(
                                                                                                                                        result.name()),
                                                                                                                        true);
                                                                                                        return 1;
                                                                                                })))))

                                .then(Commands.literal("disband")
                                                .requires(s -> s.hasPermission(2))
                                                .then(Commands.argument("name", StringArgumentType.string())
                                                                .executes(ctx -> {
                                                                        String name = StringArgumentType.getString(ctx,
                                                                                        "name");
                                                                        MinecraftServer srv = ctx.getSource()
                                                                                        .getServer();
                                                                        NationSavedData data = NationManager
                                                                                        .getData(srv);
                                                                        data.getNationByName(name)
                                                                                        .ifPresentOrElse(nation -> {
                                                                                                String tag = nation
                                                                                                                .getTag();
                                                                                                data.removeNation(nation
                                                                                                                .getId());
                                                                                                FTBTeamsHelper.onNationDisbanded(
                                                                                                                srv,
                                                                                                                tag);
                                                                                                NationManager.broadcastAll(
                                                                                                                srv);
                                                                                                ctx.getSource().sendSuccess(
                                                                                                                () -> Component.literal(
                                                                                                                                "§aDisbanded " + name),
                                                                                                                true);
                                                                                        }, () -> ctx.getSource()
                                                                                                        .sendFailure(Component
                                                                                                                        .literal("§cNation not found.")));
                                                                        return 1;
                                                                })))

                                .then(Commands.literal("join")
                                                .requires(s -> s.hasPermission(2))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                                .then(Commands.argument("nation",
                                                                                StringArgumentType.string())
                                                                                .executes(ctx -> {
                                                                                        ServerPlayer sp = EntityArgument
                                                                                                        .getPlayer(ctx, "player");
                                                                                        String name = StringArgumentType
                                                                                                        .getString(ctx, "nation");
                                                                                        NationSavedData data = NationManager
                                                                                                        .getData(ctx.getSource()
                                                                                                                        .getServer());
                                                                                        data.getNationByName(name)
                                                                                                        .ifPresentOrElse(
                                                                                                                        nation -> {
                                                                                                                                nation.addInvite(
                                                                                                                                                sp.getUUID()); // force-invite
                                                                                                                                var result = NationManager
                                                                                                                                                .joinNation(ctx.getSource()
                                                                                                                                                                .getServer(),
                                                                                                                                                                sp.getUUID(),
                                                                                                                                                                nation.getId());
                                                                                                                                ctx.getSource().sendSuccess(
                                                                                                                                                () -> Component.literal(
                                                                                                                                                                result.name()),
                                                                                                                                                true);
                                                                                                                        },
                                                                                                                        () -> ctx.getSource()
                                                                                                                                        .sendFailure(Component
                                                                                                                                                        .literal("§cNation not found.")));
                                                                                        return 1;
                                                                                }))))

                                .then(Commands.literal("kick")
                                                .requires(s -> s.hasPermission(2))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(ctx -> {
                                                                        ServerPlayer sp = EntityArgument.getPlayer(ctx,
                                                                                        "player");
                                                                        MinecraftServer srv = ctx.getSource()
                                                                                        .getServer();
                                                                        NationSavedData kdata = NationManager
                                                                                        .getData(srv);
                                                                        kdata.getNationOfPlayer(sp.getUUID()).ifPresent(
                                                                                        n -> FTBTeamsHelper
                                                                                                        .onPlayerLeftNation(
                                                                                                                        srv,
                                                                                                                        sp.getUUID(),
                                                                                                                        n.getTag()));
                                                                        kdata.removePlayerFromNation(sp.getUUID());
                                                                        NationManager.broadcastAll(srv);
                                                                        ctx.getSource().sendSuccess(
                                                                                        () -> Component.literal(
                                                                                                        "§aKicked."),
                                                                                        true);
                                                                        return 1;
                                                                })))

                                .then(Commands.literal("setrank")
                                                .requires(s -> s.hasPermission(2))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                                .then(Commands.argument("rank",
                                                                                StringArgumentType.word())
                                                                                .executes(ctx -> {
                                                                                        ServerPlayer sp = EntityArgument
                                                                                                        .getPlayer(ctx, "player");
                                                                                        String rankStr = StringArgumentType
                                                                                                        .getString(ctx, "rank")
                                                                                                        .toUpperCase();
                                                                                        try {
                                                                                                NationRank rank = NationRank
                                                                                                                .valueOf(rankStr);
                                                                                                NationSavedData data = NationManager
                                                                                                                .getData(ctx.getSource()
                                                                                                                                .getServer());
                                                                                                data.getNationOfPlayer(
                                                                                                                sp.getUUID())
                                                                                                                .ifPresentOrElse(
                                                                                                                                nation -> {
                                                                                                                                        nation.setRank(sp
                                                                                                                                                        .getUUID(),
                                                                                                                                                        rank);
                                                                                                                                        data.setDirty();
                                                                                                                                        NationManager.broadcastAll(
                                                                                                                                                        ctx.getSource().getServer());
                                                                                                                                        ctx.getSource()
                                                                                                                                                        .sendSuccess(() -> Component
                                                                                                                                                                        .literal("§aRank set to "
                                                                                                                                                                                        + rank.displayName),
                                                                                                                                                                        true);
                                                                                                                                },
                                                                                                                                () -> ctx.getSource()
                                                                                                                                                .sendFailure(Component
                                                                                                                                                                .literal("§cPlayer has no nation.")));
                                                                                        } catch (IllegalArgumentException e) {
                                                                                                ctx.getSource().sendFailure(
                                                                                                                Component.literal(
                                                                                                                                "§cInvalid rank. Valid: SOVEREIGN CHANCELLOR GENERAL DIPLOMAT CITIZEN"));
                                                                                        }
                                                                                        return 1;
                                                                                }))))

                                .then(Commands.literal("setrelation")
                                                .requires(s -> s.hasPermission(2))
                                                .then(Commands.argument("nationA", StringArgumentType.string())
                                                                .then(Commands.argument("nationB",
                                                                                StringArgumentType.string())
                                                                                .then(Commands.argument("type",
                                                                                                StringArgumentType
                                                                                                                .word())
                                                                                                .executes(ctx -> {
                                                                                                        NationSavedData data = NationManager
                                                                                                                        .getData(ctx.getSource()
                                                                                                                                        .getServer());
                                                                                                        String nameA = StringArgumentType
                                                                                                                        .getString(ctx, "nationA");
                                                                                                        String nameB = StringArgumentType
                                                                                                                        .getString(ctx, "nationB");
                                                                                                        String typeStr = StringArgumentType
                                                                                                                        .getString(ctx, "type")
                                                                                                                        .toUpperCase();
                                                                                                        try {
                                                                                                                RelationType type = RelationType
                                                                                                                                .valueOf(typeStr);
                                                                                                                var optA = data.getNationByName(
                                                                                                                                nameA);
                                                                                                                var optB = data.getNationByName(
                                                                                                                                nameB);
                                                                                                                if (optA.isEmpty()
                                                                                                                                || optB.isEmpty()) {
                                                                                                                        ctx.getSource().sendFailure(
                                                                                                                                        Component.literal(
                                                                                                                                                        "§cNation not found."));
                                                                                                                        return 0;
                                                                                                                }
                                                                                                                optA.get().setRelation(
                                                                                                                                optB.get().getId(),
                                                                                                                                type,
                                                                                                                                "admin");
                                                                                                                optB.get().setRelation(
                                                                                                                                optA.get().getId(),
                                                                                                                                type,
                                                                                                                                "admin");
                                                                                                                data.setDirty();
                                                                                                                NationManager.broadcastAll(
                                                                                                                                ctx.getSource().getServer());
                                                                                                                ctx.getSource().sendSuccess(
                                                                                                                                () -> Component.literal(
                                                                                                                                                "§aRelation set."),
                                                                                                                                true);
                                                                                                        } catch (IllegalArgumentException e) {
                                                                                                                ctx.getSource()
                                                                                                                                .sendFailure(Component
                                                                                                                                                .literal("§cInvalid type."));
                                                                                                        }
                                                                                                        return 1;
                                                                                                })))))

                                .then(Commands.literal("score")
                                                .requires(s -> s.hasPermission(2))
                                                .then(Commands.argument("nation", StringArgumentType.string())
                                                                .then(Commands.argument("amount",
                                                                                IntegerArgumentType.integer())
                                                                                .executes(ctx -> {
                                                                                        NationSavedData data = NationManager
                                                                                                        .getData(ctx.getSource()
                                                                                                                        .getServer());
                                                                                        String name = StringArgumentType
                                                                                                        .getString(ctx, "nation");
                                                                                        int amount = IntegerArgumentType
                                                                                                        .getInteger(ctx, "amount");
                                                                                        data.getNationByName(name)
                                                                                                        .ifPresentOrElse(
                                                                                                                        n -> {
                                                                                                                                n.addScore(amount);
                                                                                                                                data.setDirty();
                                                                                                                                NationManager.broadcastAll(
                                                                                                                                                ctx.getSource().getServer());
                                                                                                                                ctx.getSource().sendSuccess(
                                                                                                                                                () -> Component.literal(
                                                                                                                                                                "§aScore updated."),
                                                                                                                                                true);
                                                                                                                        },
                                                                                                                        () -> ctx.getSource()
                                                                                                                                        .sendFailure(Component
                                                                                                                                                        .literal("§cNation not found.")));
                                                                                        return 1;
                                                                                }))))

                                .then(Commands.literal("reload")
                                                .requires(s -> s.hasPermission(2))
                                                .executes(ctx -> {
                                                        NationManager.broadcastAll(ctx.getSource().getServer());
                                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                                        "§aSynced nation data to all players."),
                                                                        true);
                                                        return 1;
                                                }))

                                // ── Economy ──────────────────────────────────────────────────────────
                                .then(Commands.literal("deposit")
                                                .then(Commands.argument("amount",
                                                                IntegerArgumentType.integer(1))
                                                                .executes(ctx -> {
                                                                        int amount = IntegerArgumentType
                                                                                        .getInteger(ctx, "amount");
                                                                        try {
                                                                                ServerPlayer player = ctx.getSource()
                                                                                                .getPlayerOrException();
                                                                                NationManager.depositCoins(
                                                                                                ctx.getSource().getServer(),
                                                                                                player.getUUID(), amount);
                                                                        } catch (Exception e) {
                                                                                ctx.getSource().sendFailure(
                                                                                                Component.literal("§cMust be run as a player."));
                                                                        }
                                                                        return 1;
                                                                })))

                                .then(Commands.literal("withdraw")
                                                .then(Commands.argument("amount",
                                                                IntegerArgumentType.integer(1))
                                                                .executes(ctx -> {
                                                                        int amount = IntegerArgumentType
                                                                                        .getInteger(ctx, "amount");
                                                                        try {
                                                                                ServerPlayer player = ctx.getSource()
                                                                                                .getPlayerOrException();
                                                                                var r = NationManager.withdrawCoins(
                                                                                                ctx.getSource().getServer(),
                                                                                                player.getUUID(), amount);
                                                                                if (!r.ok())
                                                                                        ctx.getSource().sendFailure(
                                                                                                        Component.literal("§c" + r.name()));
                                                                        } catch (Exception e) {
                                                                                ctx.getSource().sendFailure(
                                                                                                Component.literal("§cMust be run as a player."));
                                                                        }
                                                                        return 1;
                                                                }))));
        }

        private static int listNations(CommandSourceStack source) {
                NationSavedData data = NationManager.getData(source.getServer());
                if (data.getAllNations().isEmpty()) {
                        source.sendSuccess(() -> Component.literal("§7No nations exist."), false);
                        return 0;
                }
                source.sendSuccess(() -> Component.literal("§7=== Nations (" + data.getAllNations().size() + ") ==="),
                                false);
                for (var nation : data.getAllNations()) {
                        source.sendSuccess(() -> Component.literal(
                                        "§7[" + nation.getTag() + "] §f" + nation.getName()
                                                        + " §7(" + nation.getMemberCount() + " members, score: "
                                                        + nation.getScore() + ")"),
                                        false);
                }
                return 1;
        }

        private static int nationInfo(CommandSourceStack source, String name) {
                NationSavedData data = NationManager.getData(source.getServer());
                Optional<dev.nationsforge.nation.Nation> optNation = data.getNationByName(name);
                if (optNation.isEmpty()) {
                        source.sendFailure(Component.literal("§cNation not found."));
                        return 0;
                }
                var n = optNation.get();
                source.sendSuccess(() -> Component.literal(
                                "§e== [" + n.getTag() + "] " + n.getName() + " ==\n"
                                                + "§7ID: §f" + n.getId() + "\n"
                                                + "§7Members: §f" + n.getMemberCount() + "\n"
                                                + "§7Score: §b" + n.getScore() + "\n"
                                                + "§7Treasury: §e" + n.getTreasury() + "\n"
                                                + "§7Open: §f" + n.isOpenRecruitment() + "\n"
                                                + "§7Relations: §f" + n.getRelations().size()),
                                false);
                return 1;
        }
}
