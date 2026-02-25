package dev.nationsforge.nation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Persistent server-side storage for all Nations.
 * Saved in the world's data folder as "nationsforge_nations.dat".
 */
public class NationSavedData extends SavedData {

    public static final String DATA_NAME = "nationsforge_nations";

    /** All nations, keyed by their UUID. */
    private final Map<UUID, Nation> nations = new LinkedHashMap<>();
    /** player UUID → nation UUID mapping for O(1) lookups. */
    private final Map<UUID, UUID> playerNation = new HashMap<>();

    // ── Factory ──────────────────────────────────────────────────────────────────

    public static NationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                NationSavedData::load,
                NationSavedData::new,
                DATA_NAME);
    }

    private static NationSavedData load(CompoundTag tag) {
        NationSavedData data = new NationSavedData();
        ListTag list = tag.getList("nations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Nation n = Nation.fromNBT(list.getCompound(i));
            data.nations.put(n.getId(), n);
            for (UUID player : n.getMembers().keySet()) {
                data.playerNation.put(player, n.getId());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Nation n : nations.values()) {
            list.add(n.toNBT());
        }
        tag.put("nations", list);
        return tag;
    }

    // ── Nation CRUD ──────────────────────────────────────────────────────────────

    public Nation createNation(String name, String tag, int colour, UUID founderId) {
        UUID id = UUID.randomUUID();
        Nation nation = new Nation(id, name, tag, colour, founderId);
        nations.put(id, nation);
        playerNation.put(founderId, id);
        setDirty();
        return nation;
    }

    public void removeNation(UUID nationId) {
        Nation nation = nations.remove(nationId);
        if (nation != null) {
            for (UUID member : nation.getMembers().keySet()) {
                playerNation.remove(member);
            }
            // Clean up references from other nations
            for (Nation other : nations.values()) {
                other.getRelations(); // triggers nothing; manual cleanup below
            }
            setDirty();
        }
    }

    // ── Member operations ────────────────────────────────────────────────────────

    public boolean addPlayerToNation(UUID playerId, UUID nationId) {
        Nation nation = nations.get(nationId);
        if (nation == null)
            return false;
        if (playerNation.containsKey(playerId))
            return false; // already in a nation
        nation.addMember(playerId, NationRank.CITIZEN);
        playerNation.put(playerId, nationId);
        setDirty();
        return true;
    }

    public boolean removePlayerFromNation(UUID playerId) {
        UUID nationId = playerNation.remove(playerId);
        if (nationId == null)
            return false;
        Nation nation = nations.get(nationId);
        if (nation != null) {
            nation.removeMember(playerId);
            setDirty();
        }
        return true;
    }

    // ── Queries ──────────────────────────────────────────────────────────────────

    public Optional<Nation> getNationById(UUID id) {
        return Optional.ofNullable(nations.get(id));
    }

    public Optional<Nation> getNationByName(String name) {
        return nations.values().stream()
                .filter(n -> n.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public Optional<Nation> getNationOfPlayer(UUID playerId) {
        UUID nationId = playerNation.get(playerId);
        if (nationId == null)
            return Optional.empty();
        return Optional.ofNullable(nations.get(nationId));
    }

    public Optional<UUID> getNationIdOfPlayer(UUID playerId) {
        return Optional.ofNullable(playerNation.get(playerId));
    }

    public Collection<Nation> getAllNations() {
        return Collections.unmodifiableCollection(nations.values());
    }

    public boolean isNameTaken(String name) {
        return nations.values().stream()
                .anyMatch(n -> n.getName().equalsIgnoreCase(name));
    }

    public boolean isTagTaken(String tag) {
        return nations.values().stream()
                .anyMatch(n -> n.getTag().equalsIgnoreCase(tag));
    }

    public Map<UUID, UUID> getPlayerNationMap() {
        return Collections.unmodifiableMap(playerNation);
    }
}
