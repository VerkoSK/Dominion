package dev.nationsforge.nation;

import dev.nationsforge.event.NationCreatedEvent;
import dev.nationsforge.event.NationDisbandedEvent;
import dev.nationsforge.event.NationRelationChangedEvent;
import dev.nationsforge.event.PlayerJoinedNationEvent;
import dev.nationsforge.event.PlayerLeftNationEvent;
import dev.nationsforge.bot.BotNationAI;
import dev.nationsforge.integration.ftbteams.FTBTeamsHelper;
import dev.nationsforge.item.ModItems;
import dev.nationsforge.network.PacketHandler;
import dev.nationsforge.network.packet.S2CDiplomacyNotifyPacket;
import dev.nationsforge.network.packet.S2CNationsDataPacket;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * High-level business logic for NationsForge.
 * All public methods validate invariants, mutate {@link NationSavedData}, then
 * broadcast the updated state to all online players.
 */
public class NationManager {

    // ── Validation constants ─────────────────────────────────────────────────────

    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 32;
    public static final int TAG_MIN = 2;
    public static final int TAG_MAX = 5;

    /** Human‐readable reason codes returned by action methods. */
    public enum Result {
        SUCCESS,
        ALREADY_IN_NATION,
        NOT_IN_NATION,
        NATION_NOT_FOUND,
        NAME_TAKEN,
        TAG_TAKEN,
        INVALID_NAME,
        NO_PERMISSION,
        CANNOT_SELF_TARGET,
        PLAYER_NOT_IN_NATION,
        ALREADY_AT_WAR,
        CANNOT_WAR_ALLY,
        INVITE_NOT_FOUND,
        REQUEST_NOT_FOUND,
        NOT_YOUR_REQUEST,
        ALREADY_PENDING;

        public boolean ok() {
            return this == SUCCESS;
        }
    }

    private NationManager() {
    }

    // ── Create / Disband ─────────────────────────────────────────────────────────

    public static Result createNation(MinecraftServer server, UUID founderId,
            String name, String tag, int colour,
            String description) {
        if (name.length() < NAME_MIN || name.length() > NAME_MAX
                || !name.matches("[\\w\\-. ]+"))
            return Result.INVALID_NAME;
        if (tag.length() < TAG_MIN || tag.length() > TAG_MAX
                || !tag.matches("[A-Za-z0-9]+"))
            return Result.INVALID_NAME;

        NationSavedData data = getData(server);
        if (data.getNationOfPlayer(founderId).isPresent())
            return Result.ALREADY_IN_NATION;
        if (data.isNameTaken(name))
            return Result.NAME_TAKEN;
        if (data.isTagTaken(tag))
            return Result.TAG_TAKEN;

        Nation nation = data.createNation(name, tag.toUpperCase(), colour, founderId);
        if (description != null && !description.isBlank())
            nation.setDescription(description);

        // Sync to FTB Teams: create server team + add founder
        FTBTeamsHelper.onNationCreated(server, nation);
        FTBTeamsHelper.onPlayerJoinedNation(server, founderId, nation);

        NationPowerCalculator.recalculate(nation);
        broadcastAll(server);

        // Fire KubeJS-compatible events
        MinecraftForge.EVENT_BUS.post(new NationCreatedEvent(nation, server));
        ServerPlayer founder = server.getPlayerList().getPlayer(founderId);
        if (founder != null)
            MinecraftForge.EVENT_BUS.post(new PlayerJoinedNationEvent(founder, nation));
        notifyPlayer(server, founderId, Component.literal("§6✦ You have founded ")
                .append(Component.literal("[" + nation.getTag() + "] " + nation.getName())
                        .withStyle(s -> s.withColor(nation.getColour() & 0xFFFFFF)))
                .append(Component.literal("§6!")));
        return Result.SUCCESS;
    }

    public static Result disbandNation(MinecraftServer server, UUID requesterId) {
        NationSavedData data = getData(server);
        Optional<Nation> opt = data.getNationOfPlayer(requesterId);
        if (opt.isEmpty())
            return Result.NOT_IN_NATION;
        Nation nation = opt.get();

        if (!nation.getLeaderId().equals(requesterId))
            return Result.NO_PERMISSION;

        String nationName = nation.getName();
        String nationTag = nation.getTag();
        List<UUID> formerMembers = new ArrayList<>(nation.getMembers().keySet());

        // Remove FTB team before data is gone
        FTBTeamsHelper.onNationDisbanded(server, nationTag);

        // Fire events before we wipe the data
        MinecraftForge.EVENT_BUS.post(new NationDisbandedEvent(nation.getId(), nationName, nationTag, server));
        for (UUID memberId : formerMembers) {
            ServerPlayer sp = server.getPlayerList().getPlayer(memberId);
            if (sp != null)
                MinecraftForge.EVENT_BUS
                        .post(new PlayerLeftNationEvent(sp, nation, PlayerLeftNationEvent.Reason.DISBANDED));
        }

        data.removeNation(nation.getId());

        broadcastAll(server);
        for (UUID member : formerMembers) {
            notifyPlayer(server, member, Component.literal(
                    "§c✦ Nation §7" + nationName + "§c has been disbanded."));
        }
        return Result.SUCCESS;
    }

    // ── Join / Leave ─────────────────────────────────────────────────────────────

    public static Result joinNation(MinecraftServer server, UUID playerId, UUID nationId) {
        NationSavedData data = getData(server);
        if (data.getNationOfPlayer(playerId).isPresent())
            return Result.ALREADY_IN_NATION;

        Optional<Nation> opt = data.getNationById(nationId);
        if (opt.isEmpty())
            return Result.NATION_NOT_FOUND;
        Nation nation = opt.get();

        // Check access: open or invited
        if (!nation.isOpenRecruitment() && !nation.hasInvite(playerId))
            return Result.INVITE_NOT_FOUND;

        data.addPlayerToNation(playerId, nationId);
        // Sync to FTB Teams
        FTBTeamsHelper.onPlayerJoinedNation(server, playerId, nation);

        NationPowerCalculator.recalculate(nation);
        broadcastAll(server);

        // Fire KubeJS-compatible event
        ServerPlayer joiner = server.getPlayerList().getPlayer(playerId);
        if (joiner != null)
            MinecraftForge.EVENT_BUS.post(new PlayerJoinedNationEvent(joiner, nation));
        notifyPlayer(server, playerId,
                Component.literal("§aYou joined §7[" + nation.getTag() + "] " + nation.getName() + "§a."));
        notifyLeadership(server, nation, Component.literal(
                "§e" + getPlayerName(server, playerId) + " §7joined the nation."));
        return Result.SUCCESS;
    }

    public static Result leaveNation(MinecraftServer server, UUID playerId) {
        NationSavedData data = getData(server);
        Optional<Nation> opt = data.getNationOfPlayer(playerId);
        if (opt.isEmpty())
            return Result.NOT_IN_NATION;
        Nation nation = opt.get();

        // Leader must transfer or disband first
        if (nation.getLeaderId().equals(playerId) && nation.getMemberCount() > 1)
            return Result.NO_PERMISSION;

        String nationName = nation.getName();
        String nationTag = nation.getTag();

        ServerPlayer leaver = server.getPlayerList().getPlayer(playerId);

        if (nation.getLeaderId().equals(playerId)) {
            // Last member leaving — disband entirely
            if (leaver != null)
                MinecraftForge.EVENT_BUS
                        .post(new PlayerLeftNationEvent(leaver, nation, PlayerLeftNationEvent.Reason.DISBANDED));
            MinecraftForge.EVENT_BUS.post(new NationDisbandedEvent(nation.getId(), nationName, nationTag, server));
            FTBTeamsHelper.onNationDisbanded(server, nationTag);
            data.removeNation(nation.getId());
        } else {
            if (leaver != null)
                MinecraftForge.EVENT_BUS
                        .post(new PlayerLeftNationEvent(leaver, nation, PlayerLeftNationEvent.Reason.LEAVE));
            data.removePlayerFromNation(playerId);
            FTBTeamsHelper.onPlayerLeftNation(server, playerId, nationTag);
            NationPowerCalculator.recalculate(nation);
        }

        broadcastAll(server);
        notifyPlayer(server, playerId, Component.literal("§7You left §c" + nationName + "§7."));
        return Result.SUCCESS;
    }

    // ── Invitations ──────────────────────────────────────────────────────────────

    public static Result invitePlayer(MinecraftServer server, UUID inviterId, UUID targetId) {
        if (inviterId.equals(targetId))
            return Result.CANNOT_SELF_TARGET;
        NationSavedData data = getData(server);
        Optional<Nation> inviterNation = data.getNationOfPlayer(inviterId);
        if (inviterNation.isEmpty())
            return Result.NOT_IN_NATION;

        Nation nation = inviterNation.get();
        if (!nation.getRank(inviterId).canInvitePlayers())
            return Result.NO_PERMISSION;
        if (data.getNationOfPlayer(targetId).isPresent())
            return Result.ALREADY_IN_NATION;

        nation.addInvite(targetId);
        data.setDirty();
        broadcastAll(server);
        notifyPlayer(server, targetId, Component.literal(
                "§aYou have been invited to join §7[" + nation.getTag() + "] " + nation.getName()
                        + "§a. Open the Nations menu to accept."));
        return Result.SUCCESS;
    }

    // ── Rank management ──────────────────────────────────────────────────────────

    public static Result setRank(MinecraftServer server, UUID requesterId,
            UUID targetId, NationRank newRank) {
        if (requesterId.equals(targetId))
            return Result.CANNOT_SELF_TARGET;
        NationSavedData data = getData(server);
        Optional<Nation> opt = data.getNationOfPlayer(requesterId);
        if (opt.isEmpty())
            return Result.NOT_IN_NATION;
        Nation nation = opt.get();
        if (!nation.hasMember(targetId))
            return Result.PLAYER_NOT_IN_NATION;

        NationRank requesterRank = nation.getRank(requesterId);
        NationRank targetCurrentRank = nation.getRank(targetId);

        if (!requesterRank.outranks(targetCurrentRank))
            return Result.NO_PERMISSION;
        if (!requesterRank.outranks(newRank))
            return Result.NO_PERMISSION;
        // Cannot promote to SOVEREIGN unless you ARE the SOVEREIGN (leadership
        // transfer)
        if (newRank == NationRank.SOVEREIGN && requesterRank != NationRank.SOVEREIGN)
            return Result.NO_PERMISSION;

        if (newRank == NationRank.SOVEREIGN) {
            // Demote previous sovereign to CHANCELLOR
            nation.setRank(requesterId, NationRank.CHANCELLOR);
        }
        nation.setRank(targetId, newRank);
        if (newRank == NationRank.SOVEREIGN) {
            nation.setLeaderId(targetId);
        }
        data.setDirty();
        broadcastAll(server);
        notifyPlayer(server, targetId, Component.literal(
                "§aYour rank in §7" + nation.getName() + "§a is now §7" + newRank.displayName + "§a."));
        return Result.SUCCESS;
    }

    public static Result kickMember(MinecraftServer server, UUID requesterId, UUID targetId) {
        if (requesterId.equals(targetId))
            return Result.CANNOT_SELF_TARGET;
        NationSavedData data = getData(server);
        Optional<Nation> opt = data.getNationOfPlayer(requesterId);
        if (opt.isEmpty())
            return Result.NOT_IN_NATION;
        Nation nation = opt.get();
        if (!nation.hasMember(targetId))
            return Result.PLAYER_NOT_IN_NATION;

        NationRank requesterRank = nation.getRank(requesterId);
        NationRank targetRank = nation.getRank(targetId);
        if (!requesterRank.canKickMembers())
            return Result.NO_PERMISSION;
        if (!requesterRank.outranks(targetRank))
            return Result.NO_PERMISSION;

        String kickedFromTag = nation.getTag();
        String kickedFromName = nation.getName();

        // Fire event before removing (nation still populated)
        ServerPlayer kickedSp = server.getPlayerList().getPlayer(targetId);
        if (kickedSp != null)
            MinecraftForge.EVENT_BUS
                    .post(new PlayerLeftNationEvent(kickedSp, nation, PlayerLeftNationEvent.Reason.KICKED));

        data.removePlayerFromNation(targetId);
        FTBTeamsHelper.onPlayerLeftNation(server, targetId, kickedFromTag);
        NationPowerCalculator.recalculate(nation);

        broadcastAll(server);
        notifyPlayer(server, targetId, Component.literal(
                "§cYou have been kicked from §7" + kickedFromName + "§c."));
        return Result.SUCCESS;
    }

    // ── Diplomacy (request/response) ─────────────────────────────────────────────

    /**
     * Proposes a diplomatic relation change to another nation.
     * If the target is a bot nation, the AI responds immediately.
     * Otherwise a pending {@link DiplomacyRequest} is stored and the
     * target nation's online members are notified via
     * {@link S2CDiplomacyNotifyPacket}.
     */
    public static Result requestDiplomacy(MinecraftServer server, UUID requesterId,
            UUID targetNationId, RelationType proposedType, String message) {
        NationSavedData data = getData(server);
        Optional<Nation> optA = data.getNationOfPlayer(requesterId);
        if (optA.isEmpty()) return Result.NOT_IN_NATION;
        Nation nationA = optA.get();
        if (!nationA.getRank(requesterId).canManageDiplomacy()) return Result.NO_PERMISSION;
        Optional<Nation> optB = data.getNationById(targetNationId);
        if (optB.isEmpty()) return Result.NATION_NOT_FOUND;
        Nation nationB = optB.get();
        if (nationA.getId().equals(targetNationId)) return Result.CANNOT_SELF_TARGET;

        // Prevent duplicate pending requests between the same pair
        if (data.hasPendingRequestBetween(nationA.getId(), targetNationId))
            return Result.ALREADY_PENDING;

        DiplomacyRequest req = new DiplomacyRequest(
                nationA.getId(), targetNationId, proposedType,
                message == null ? "" : message);

        if (nationB.isBot()) {
            // Bot nations respond instantly via AI evaluation
            boolean accept = BotNationAI.evaluateIncomingRequest(nationB, req, new java.util.Random());
            if (accept) {
                RelationType oldType = nationA.getRelationWith(targetNationId);
                nationA.setRelation(targetNationId, proposedType, "Bot accepted");
                nationB.setRelation(nationA.getId(), proposedType, "Bot accepted");
                data.setDirty();
                broadcastAll(server);
                MinecraftForge.EVENT_BUS.post(new NationRelationChangedEvent(
                        nationA, nationB, oldType, proposedType, server));
                notifyNation(server, nationA, Component.literal(
                        "§7[§aDiplomacy§7] §f" + nationB.getName()
                        + "§7 accepted your proposal: §r"
                        + coloured(proposedType.displayName, proposedType.colour & 0xFFFFFF) + "§7."));
            } else {
                notifyNation(server, nationA, Component.literal(
                        "§7[§cDiplomacy§7] §f" + nationB.getName()
                        + "§7 has declined your §r"
                        + coloured(proposedType.displayName, proposedType.colour & 0xFFFFFF)
                        + "§7 proposal."));
            }
            return Result.SUCCESS;
        }

        // Player nation: store request and notify target nation's members
        data.addDiplomacyRequest(req);
        notifyNation(server, nationA, Component.literal(
                "§7[Diplomacy] Proposal sent to §f" + nationB.getName() + "§7."));
        notifyNation(server, nationB, Component.literal(
                "§7[§6Diplomacy§7] §f" + nationA.getName()
                + "§7 proposes §r"
                + coloured(proposedType.displayName, proposedType.colour & 0xFFFFFF)
                + "§7. Open Diplomacy tab to respond."));
        // Push updated pending list to both nations' online members
        pushDiplomacyToNation(server, data, nationA);
        pushDiplomacyToNation(server, data, nationB);
        return Result.SUCCESS;
    }

    /**
     * Accept or decline a pending diplomacy request.
     * The responder must be in the TARGET nation and have diplomacy rank.
     */
    public static Result respondDiplomacy(MinecraftServer server, UUID responderId,
            UUID requestId, boolean accepted, String responseMessage) {
        NationSavedData data = getData(server);
        Optional<Nation> optResponder = data.getNationOfPlayer(responderId);
        if (optResponder.isEmpty()) return Result.NOT_IN_NATION;
        Nation responderNation = optResponder.get();
        if (!responderNation.getRank(responderId).canManageDiplomacy()) return Result.NO_PERMISSION;

        Optional<DiplomacyRequest> optReq = data.getRequestById(requestId);
        if (optReq.isEmpty()) return Result.REQUEST_NOT_FOUND;
        DiplomacyRequest req = optReq.get();

        // Ensure the responder is the target nation (not the proposer)
        if (!req.getToNationId().equals(responderNation.getId())) return Result.NOT_YOUR_REQUEST;

        Optional<Nation> optFrom = data.getNationById(req.getFromNationId());
        if (optFrom.isEmpty()) {
            data.removeDiplomacyRequest(requestId);
            return Result.NATION_NOT_FOUND;
        }
        Nation fromNation = optFrom.get();

        data.removeDiplomacyRequest(requestId);
        req.setStatus(accepted ? DiplomacyRequest.Status.ACCEPTED : DiplomacyRequest.Status.DECLINED);

        if (accepted) {
            RelationType oldType = fromNation.getRelationWith(responderNation.getId());
            RelationType newType = req.getProposedType();
            fromNation.setRelation(responderNation.getId(), newType, "Diplomatic agreement");
            responderNation.setRelation(fromNation.getId(), newType, "Diplomatic agreement");
            data.setDirty();
            broadcastAll(server);
            MinecraftForge.EVENT_BUS.post(new NationRelationChangedEvent(
                    fromNation, responderNation, oldType, newType, server));
            notifyNation(server, fromNation, Component.literal(
                    "§7[§aDiplomacy§7] §f" + responderNation.getName()
                    + "§7 accepted: relation is now §r"
                    + coloured(newType.displayName, newType.colour & 0xFFFFFF) + "§7."));
            notifyNation(server, responderNation, Component.literal(
                    "§7[§aDiplomacy§7] Agreement with §f" + fromNation.getName()
                    + "§7: §r" + coloured(newType.displayName, newType.colour & 0xFFFFFF) + "§7."));
        } else {
            data.setDirty();
            String rm = (responseMessage != null && !responseMessage.isBlank())
                    ? " §7(\"" + responseMessage + "\")" : "";
            notifyNation(server, fromNation, Component.literal(
                    "§7[§cDiplomacy§7] §f" + responderNation.getName()
                    + "§7 declined your §r"
                    + coloured(req.getProposedType().displayName, req.getProposedType().colour & 0xFFFFFF)
                    + "§7 proposal." + rm));
            notifyNation(server, responderNation, Component.literal(
                    "§7[Diplomacy] Proposal from §f" + fromNation.getName() + "§7 declined."));
        }

        pushDiplomacyToNation(server, data, fromNation);
        pushDiplomacyToNation(server, data, responderNation);
        return Result.SUCCESS;
    }

    // ── Diplomacy (instant — kept for bot-to-bot AI events) ──────────────────────

    public static Result setRelation(MinecraftServer server, UUID requesterId,
            UUID targetNationId, RelationType type,
            String reason) {
        NationSavedData data = getData(server);
        Optional<Nation> optA = data.getNationOfPlayer(requesterId);
        if (optA.isEmpty())
            return Result.NOT_IN_NATION;
        Nation nationA = optA.get();

        if (!nationA.getRank(requesterId).canManageDiplomacy())
            return Result.NO_PERMISSION;

        Optional<Nation> optB = data.getNationById(targetNationId);
        if (optB.isEmpty())
            return Result.NATION_NOT_FOUND;
        Nation nationB = optB.get();

        // For WAR: unilateral declaration is allowed.
        // For ALLIANCE / TRADE_PACT: requires mutual consent OR just sets as pending.
        // For simplicity: we set it immediately from both sides (server-authoritative).
        // Alliance must be mutual — if nationB hasn't sent an alliance request, set
        // RIVALRY instead.
        // WAR is always unilateral.

        if (type == RelationType.WAR) {
            RelationType currentA = nationA.getRelationWith(targetNationId);
            if (currentA == RelationType.WAR)
                return Result.ALREADY_AT_WAR;
        }

        // Capture old type before mutating
        RelationType oldType = nationA.getRelationWith(targetNationId);

        // Set relation on both sides
        nationA.setRelation(targetNationId, type, reason);
        nationB.setRelation(nationA.getId(), type, reason);
        data.setDirty();

        broadcastAll(server);
        MinecraftForge.EVENT_BUS.post(new NationRelationChangedEvent(nationA, nationB, oldType, type, server));
        notifyNation(server, nationA, Component.literal(
                "§7[Diplomacy] Relation with §e" + nationB.getName() + "§7 is now §r"
                        + coloured(type.displayName, type.colour & 0xFFFFFF) + "§7."));
        notifyNation(server, nationB, Component.literal(
                "§7[Diplomacy] §e" + nationA.getName() + "§7 changed relation to §r"
                        + coloured(type.displayName, type.colour & 0xFFFFFF) + "§7."));
        return Result.SUCCESS;
    }

    // ── Settings ─────────────────────────────────────────────────────────────────

    public static Result updateSettings(MinecraftServer server, UUID requesterId,
            String newName, String newTag, int newColour,
            String newDesc, boolean open) {
        NationSavedData data = getData(server);
        Optional<Nation> opt = data.getNationOfPlayer(requesterId);
        if (opt.isEmpty())
            return Result.NOT_IN_NATION;
        Nation nation = opt.get();
        if (!nation.getRank(requesterId).canEditSettings())
            return Result.NO_PERMISSION;

        if (newName != null && !newName.equals(nation.getName())) {
            if (newName.length() < NAME_MIN || newName.length() > NAME_MAX
                    || !newName.matches("[\\w\\-. ]+"))
                return Result.INVALID_NAME;
            if (data.isNameTaken(newName))
                return Result.NAME_TAKEN;
            nation.setName(newName);
        }
        if (newTag != null && !newTag.equalsIgnoreCase(nation.getTag())) {
            if (newTag.length() < TAG_MIN || newTag.length() > TAG_MAX
                    || !newTag.matches("[A-Za-z0-9]+"))
                return Result.INVALID_NAME;
            if (data.isTagTaken(newTag))
                return Result.TAG_TAKEN;
            nation.setTag(newTag.toUpperCase());
        }
        nation.setColour(newColour);
        if (newDesc != null)
            nation.setDescription(newDesc);
        nation.setOpenRecruitment(open);
        data.setDirty();
        broadcastAll(server);
        return Result.SUCCESS;
    }

    // ── Flag ─────────────────────────────────────────────────────────────────────

    public static Result updateFlag(MinecraftServer server, UUID requesterId, NationFlag flag) {
        NationSavedData data = getData(server);
        Optional<Nation> opt = data.getNationOfPlayer(requesterId);
        if (opt.isEmpty())
            return Result.NOT_IN_NATION;
        Nation nation = opt.get();
        if (!nation.getRank(requesterId).canEditSettings())
            return Result.NO_PERMISSION;
        nation.setFlag(flag);
        data.setDirty();
        broadcastAll(server);
        return Result.SUCCESS;
    }

    // ── Treasury ─────────────────────────────────────────────────────────────────

    /**
     * Transfers Nation Coins from a player's inventory into their nation's
     * treasury.
     * The player must be online. Returns SUCCESS even if they lack the coins — they
     * are notified in chat instead.
     */
    public static Result depositCoins(MinecraftServer server, UUID playerId, int amount) {
        NationSavedData data = getData(server);
        Optional<Nation> opt = data.getNationOfPlayer(playerId);
        if (opt.isEmpty())
            return Result.NOT_IN_NATION;
        Nation nation = opt.get();

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null)
            return Result.NOT_IN_NATION;

        // Count coins currently in inventory
        int held = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.NATION_COIN.get()))
                held += s.getCount();
        }

        if (held < amount) {
            notifyPlayer(server, playerId,
                    Component.literal("§cYou only have §6" + held + " §cNation Coins."));
            return Result.SUCCESS;
        }

        // Consume coins
        int remaining = amount;
        for (ItemStack s : player.getInventory().items) {
            if (remaining <= 0)
                break;
            if (s.is(ModItems.NATION_COIN.get())) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }

        nation.addTreasury(amount);
        data.setDirty();
        broadcastAll(server);
        notifyPlayer(server, playerId, Component.literal(
                "§aDeposited §6" + amount + " §acoins into §f[" + nation.getTag() + "] "
                        + nation.getName() + "§a."));
        return Result.SUCCESS;
    }

    /**
     * Withdraws coins from the national treasury into a player's inventory.
     * Only Sovereign / Chancellor rank may withdraw.
     */
    public static Result withdrawCoins(MinecraftServer server, UUID playerId, int amount) {
        NationSavedData data = getData(server);
        Optional<Nation> opt = data.getNationOfPlayer(playerId);
        if (opt.isEmpty())
            return Result.NOT_IN_NATION;
        Nation nation = opt.get();

        if (!nation.getRank(playerId).canEditSettings())
            return Result.NO_PERMISSION;

        if (nation.getTreasury() < amount) {
            notifyPlayer(server, playerId,
                    Component.literal("§cTreasury only holds §6" + nation.getTreasury() + " §ccoins."));
            return Result.SUCCESS;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null)
            return Result.NOT_IN_NATION;

        nation.addTreasury(-amount);
        data.setDirty();

        // Give coins in 64-stacks
        int rem = amount;
        while (rem > 0) {
            int give = Math.min(rem, 64);
            ItemStack stack = new ItemStack(ModItems.NATION_COIN.get(), give);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false); // drop if no space
            }
            rem -= give;
        }

        broadcastAll(server);
        notifyPlayer(server, playerId, Component.literal(
                "§aWithdrew §6" + amount + " §acoins from §f[" + nation.getTag() + "]§a."));
        return Result.SUCCESS;
    }

    /** Push the pending-request list for a nation to all its online members. */
    public static void pushDiplomacyToNation(MinecraftServer server, NationSavedData data, Nation nation) {
        List<DiplomacyRequest> list = data.getAllRequestsForNation(nation.getId());
        S2CDiplomacyNotifyPacket pkt = new S2CDiplomacyNotifyPacket(list);
        for (UUID uid : nation.getMembers().keySet()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uid);
            if (sp != null) PacketHandler.sendToPlayer(pkt, sp);
        }
    }

    /** Push pending requests to a single player (used on login). */
    public static void syncDiplomacyToPlayer(MinecraftServer server, ServerPlayer player) {
        NationSavedData data = getData(server);
        Optional<Nation> optNation = data.getNationOfPlayer(player.getUUID());
        if (optNation.isEmpty()) {
            PacketHandler.sendToPlayer(new S2CDiplomacyNotifyPacket(List.of()), player);
            return;
        }
        List<DiplomacyRequest> list = data.getAllRequestsForNation(optNation.get().getId());
        PacketHandler.sendToPlayer(new S2CDiplomacyNotifyPacket(list), player);
    }

    public static NationSavedData getData(MinecraftServer server) {
        return NationSavedData.get(server.getLevel(Level.OVERWORLD));
    }

    /** Push complete nation data to all online players. */
    public static void broadcastAll(MinecraftServer server) {
        S2CNationsDataPacket packet = S2CNationsDataPacket.create(getData(server));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketHandler.sendToPlayer(packet, player);
        }
    }

    /** Push complete nation data to a single player (e.g. on join). */
    public static void syncToPlayer(MinecraftServer server, ServerPlayer player) {
        S2CNationsDataPacket packet = S2CNationsDataPacket.create(getData(server));
        PacketHandler.sendToPlayer(packet, player);
    }

    private static void notifyPlayer(MinecraftServer server, UUID playerId, Component msg) {
        ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
        if (sp != null)
            sp.sendSystemMessage(msg);
    }

    private static void notifyNation(MinecraftServer server, Nation nation, Component msg) {
        for (UUID uid : nation.getMembers().keySet()) {
            notifyPlayer(server, uid, msg);
        }
    }

    private static void notifyLeadership(MinecraftServer server, Nation nation, Component msg) {
        for (Map.Entry<UUID, NationRank> e : nation.getMembers().entrySet()) {
            if (e.getValue().isLeadership())
                notifyPlayer(server, e.getKey(), msg);
        }
    }

    private static String getPlayerName(MinecraftServer server, UUID playerId) {
        ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
        if (sp != null)
            return sp.getName().getString();
        // Fall back to a UUID-based profile name
        return server.getProfileCache()
                .get(playerId)
                .map(p -> p.getName())
                .orElse(playerId.toString().substring(0, 8));
    }

    private static Component coloured(String text, int rgb) {
        return Component.literal(text)
                .withStyle(s -> s.withColor(rgb));
    }
}
