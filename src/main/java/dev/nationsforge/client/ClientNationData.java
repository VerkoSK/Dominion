package dev.nationsforge.client;

import dev.nationsforge.nation.DiplomacyRequest;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationRank;
import dev.nationsforge.nation.RelationType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Singleton client-side cache of all nations data received from the server.
 * Updated every time an
 * {@link dev.nationsforge.network.packet.S2CNationsDataPacket} arrives.
 */
public class ClientNationData {

    private static final Map<UUID, Nation> nations = new LinkedHashMap<>();
    private static final Map<UUID, UUID> playerNationMap = new HashMap<>();
    /** Pending diplomacy requests for the local player's nation (server-pushed). */
    private static final List<DiplomacyRequest> pendingRequests = new ArrayList<>();
    /** UUID of the local player (set on world join). */
    private static UUID localPlayerId = null;

    private ClientNationData() {
    }

    // ── Called from packet handler
    // ────────────────────────────────────────────────

    public static void receive(CompoundTag root) {
        nations.clear();
        playerNationMap.clear();

        ListTag list = root.getList("nations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Nation n = Nation.fromNBT(list.getCompound(i));
            nations.put(n.getId(), n);
        }

        CompoundTag pnMap = root.getCompound("playerNation");
        for (String key : pnMap.getAllKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                UUID nationId = pnMap.getUUID(key);
                playerNationMap.put(playerId, nationId);
            } catch (Exception ignored) {
            }
        }
    }

    public static void setLocalPlayer(UUID id) {
        localPlayerId = id;
    }

    // ── Queries ──────────────────────────────────────────────────────────────────

    @Nullable
    public static Nation getLocalNation() {
        if (localPlayerId == null)
            return null;
        UUID nationId = playerNationMap.get(localPlayerId);
        if (nationId == null)
            return null;
        return nations.get(nationId);
    }

    @Nullable
    public static NationRank getLocalRank() {
        Nation nation = getLocalNation();
        if (nation == null || localPlayerId == null)
            return null;
        return nation.getRank(localPlayerId);
    }

    public static Collection<Nation> getAllNations() {
        return Collections.unmodifiableCollection(nations.values());
    }

    @Nullable
    public static Nation getNationById(UUID id) {
        return nations.get(id);
    }

    /** Returns nations that have sent a pending invite to the local player. */
    public static List<Nation> getPendingInvites() {
        if (localPlayerId == null)
            return List.of();
        List<Nation> result = new ArrayList<>();
        for (Nation n : nations.values()) {
            if (n.hasInvite(localPlayerId))
                result.add(n);
        }
        return result;
    }

    @Nullable
    public static Nation getNationOfPlayer(UUID playerId) {
        UUID nationId = playerNationMap.get(playerId);
        return nationId == null ? null : nations.get(nationId);
    }

    public static RelationType getRelationWithLocal(UUID otherNationId) {
        Nation local = getLocalNation();
        if (local == null)
            return RelationType.NEUTRAL;
        return local.getRelationWith(otherNationId);
    }

    public static boolean localPlayerHasNation() {
        return getLocalNation() != null;
    }

    public static UUID getLocalPlayerId() {
        return localPlayerId;
    }

    /** Sorted by score descending (leaderboard). */
    public static List<Nation> getLeaderboard() {
        List<Nation> list = new ArrayList<>(nations.values());
        list.sort(Comparator.comparingLong(Nation::getScore).reversed());
        return list;
    }

    // ── Diplomacy requests ───────────────────────────────────────────────────────

    /** Called from {@link dev.nationsforge.network.packet.S2CDiplomacyNotifyPacket}. */
    public static void setPendingRequests(List<DiplomacyRequest> requests) {
        pendingRequests.clear();
        pendingRequests.addAll(requests);
    }

    /** All pending requests for the local nation (incoming AND outgoing). */
    public static List<DiplomacyRequest> getPendingRequests() {
        return Collections.unmodifiableList(pendingRequests);
    }

    /** Only requests where the local nation is the TARGET (must respond). */
    public static List<DiplomacyRequest> getIncomingRequests() {
        Nation local = getLocalNation();
        if (local == null) return List.of();
        UUID id = local.getId();
        return pendingRequests.stream()
                .filter(r -> r.getStatus() == DiplomacyRequest.Status.PENDING
                        && r.getToNationId().equals(id))
                .toList();
    }

    /** Only requests where the local nation is the PROPOSER (waiting on answer). */
    public static List<DiplomacyRequest> getOutgoingRequests() {
        Nation local = getLocalNation();
        if (local == null) return List.of();
        UUID id = local.getId();
        return pendingRequests.stream()
                .filter(r -> r.getStatus() == DiplomacyRequest.Status.PENDING
                        && r.getFromNationId().equals(id))
                .toList();
    }
}
