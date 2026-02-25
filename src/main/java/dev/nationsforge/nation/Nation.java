package dev.nationsforge.nation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Core data object representing a Nation.
 * All mutation should go through {@link NationManager} to ensure persistence.
 */
public class Nation {

    private final UUID id;
    private String name;
    /** Short tag shown in chat, 2–5 characters. */
    private String tag;
    /** Packed ARGB flag colour (used as nation colour on HUD/map). */
    private int colour;
    private String description;
    private UUID leaderId;
    /** player UUID → rank */
    private final Map<UUID, NationRank> members = new LinkedHashMap<>();
    /** Pending inbound invitations (player UUIDs who have been invited). */
    private final Set<UUID> pendingInvites = new HashSet<>();
    /** Relations keyed by OTHER nation's UUID. */
    private final Map<UUID, DiplomacyRelation> relations = new HashMap<>();
    /** Whether the nation is open (anyone can join without invite). */
    private boolean openRecruitment = false;
    private long treasury = 0L;
    /** Cumulative war-score / territory score for leaderboards. */
    private long score = 0L;
    /**
     * Number of FTB Chunks claimed by this nation's server team. Updated by
     * NationTickHandler.
     */
    private long territory = 0L;
    /**
     * Breakdown: total power calculated by NationPowerCalculator. Cached on server,
     * synced to client.
     */
    private long power = 0L;
    private long createdAt;
    /**
     * Custom banner flag chosen by the nation's leaders. Never null — defaults to
     * white.
     */
    private NationFlag flag = new NationFlag();
    /**
     * True if this nation is AI-controlled (bot). Bot nations are never disbanded
     * by players.
     */
    private boolean bot = false;
    /**
     * Capital block coordinates (X and Z). Used by bot nations to mark their
     * seat of power and anchor FTBChunks territory claiming.
     * Defaults to (0, 0) until set by WorldBotGenerator.
     */
    private int capitalX = 0;
    private int capitalZ = 0;

    // ── Constructor ──────────────────────────────────────────────────────────────

    public Nation(UUID id, String name, String tag, int colour, UUID founderId) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.colour = colour;
        this.leaderId = founderId;
        this.createdAt = System.currentTimeMillis();
        members.put(founderId, NationRank.SOVEREIGN);
    }

    // ── Member management ────────────────────────────────────────────────────────

    public void addMember(UUID player, NationRank rank) {
        members.put(player, rank);
        pendingInvites.remove(player);
    }

    public void removeMember(UUID player) {
        members.remove(player);
        pendingInvites.remove(player);
    }

    public boolean hasMember(UUID player) {
        return members.containsKey(player);
    }

    public NationRank getRank(UUID player) {
        return members.getOrDefault(player, NationRank.CITIZEN);
    }

    public void setRank(UUID player, NationRank rank) {
        if (members.containsKey(player)) {
            members.put(player, rank);
            if (rank == NationRank.SOVEREIGN) {
                // Transfer leadership
                this.leaderId = player;
            }
        }
    }

    public int getMemberCount() {
        return members.size();
    }

    public Map<UUID, NationRank> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    // ── Invitations ──────────────────────────────────────────────────────────────

    public void addInvite(UUID player) {
        pendingInvites.add(player);
    }

    public boolean hasInvite(UUID player) {
        return pendingInvites.contains(player);
    }

    public Set<UUID> getPendingInvites() {
        return Collections.unmodifiableSet(pendingInvites);
    }

    // ── Diplomacy ────────────────────────────────────────────────────────────────

    public RelationType getRelationWith(UUID otherNation) {
        DiplomacyRelation rel = relations.get(otherNation);
        return rel == null ? RelationType.NEUTRAL : rel.getType();
    }

    public void setRelation(UUID otherNation, RelationType type, String reason) {
        if (type == RelationType.NEUTRAL) {
            relations.remove(otherNation);
        } else {
            relations.put(otherNation, new DiplomacyRelation(this.id, otherNation, type, reason));
        }
    }

    public Map<UUID, DiplomacyRelation> getRelations() {
        return Collections.unmodifiableMap(relations);
    }

    // ── Economy / scoring ────────────────────────────────────────────────────────

    public void addTreasury(long amount) {
        treasury = Math.max(0, treasury + amount);
    }

    public void addScore(long amount) {
        score = Math.max(0, score + amount);
    }

    public void setTerritory(long chunks) {
        this.territory = Math.max(0, chunks);
    }

    public void setPower(long power) {
        this.power = power;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public int getColour() {
        return colour;
    }

    public String getDescription() {
        return description;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public boolean isOpenRecruitment() {
        return openRecruitment;
    }

    public long getTreasury() {
        return treasury;
    }

    public long getScore() {
        return score;
    }

    public long getTerritory() {
        return territory;
    }

    public long getPower() {
        return power;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public void setColour(int colour) {
        this.colour = colour;
    }

    public void setDescription(String desc) {
        this.description = desc == null ? "" : desc;
    }

    public void setLeaderId(UUID leaderId) {
        this.leaderId = leaderId;
    }

    public void setOpenRecruitment(boolean open) {
        this.openRecruitment = open;
    }

    public void setTreasury(long treasury) {
        this.treasury = treasury;
    }

    public void setScore(long score) {
        this.score = score;
    }

    public NationFlag getFlag() {
        return flag;
    }

    public void setFlag(NationFlag flag) {
        this.flag = flag == null ? new NationFlag() : flag;
    }

    public boolean isBot() {
        return bot;
    }

    public void setBot(boolean bot) {
        this.bot = bot;
    }

    public int getCapitalX() {
        return capitalX;
    }

    public int getCapitalZ() {
        return capitalZ;
    }

    public void setCapital(int x, int z) {
        this.capitalX = x;
        this.capitalZ = z;
    }

    // ── NBT serialisation ────────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("tag", this.tag);
        tag.putInt("colour", colour);
        tag.putString("description", description == null ? "" : description);
        tag.putUUID("leader", leaderId);
        tag.putBoolean("open", openRecruitment);
        tag.putLong("treasury", treasury);
        tag.putLong("score", score);
        tag.putLong("territory", territory);
        tag.putLong("power", power);
        tag.putLong("createdAt", createdAt);
        tag.put("flag", flag.toNBT());
        tag.putBoolean("bot", bot);
        tag.putInt("capitalX", capitalX);
        tag.putInt("capitalZ", capitalZ);

        // Members
        ListTag memberList = new ListTag();
        for (Map.Entry<UUID, NationRank> entry : members.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("uuid", entry.getKey());
            m.putInt("rank", entry.getValue().level);
            memberList.add(m);
        }
        tag.put("members", memberList);

        // Invites
        ListTag inviteList = new ListTag();
        for (UUID inv : pendingInvites) {
            CompoundTag i = new CompoundTag();
            i.putUUID("uuid", inv);
            inviteList.add(i);
        }
        tag.put("invites", inviteList);

        // Relations
        ListTag relList = new ListTag();
        for (DiplomacyRelation rel : relations.values()) {
            relList.add(rel.toNBT());
        }
        tag.put("relations", relList);

        return tag;
    }

    public static Nation fromNBT(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        String name = tag.getString("name");
        String nTag = tag.getString("tag");
        int colour = tag.getInt("colour");
        UUID leader = tag.getUUID("leader");

        Nation nation = new Nation(id, name, nTag, colour, leader);
        // Clear auto-added member since we'll reload from NBT
        nation.members.clear();
        nation.description = tag.getString("description");
        nation.openRecruitment = tag.getBoolean("open");
        nation.treasury = tag.getLong("treasury");
        nation.score = tag.getLong("score");
        nation.territory = tag.getLong("territory");
        nation.power = tag.getLong("power");
        nation.createdAt = tag.getLong("createdAt");
        if (tag.contains("flag"))
            nation.flag = NationFlag.fromNBT(tag.getCompound("flag"));
        nation.bot = tag.getBoolean("bot");
        nation.capitalX = tag.getInt("capitalX");
        nation.capitalZ = tag.getInt("capitalZ");

        ListTag memberList = tag.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            CompoundTag m = memberList.getCompound(i);
            nation.members.put(m.getUUID("uuid"), NationRank.fromLevel(m.getInt("rank")));
        }

        ListTag inviteList = tag.getList("invites", Tag.TAG_COMPOUND);
        for (int i = 0; i < inviteList.size(); i++) {
            nation.pendingInvites.add(inviteList.getCompound(i).getUUID("uuid"));
        }

        ListTag relList = tag.getList("relations", Tag.TAG_COMPOUND);
        for (int i = 0; i < relList.size(); i++) {
            DiplomacyRelation rel = DiplomacyRelation.fromNBT(relList.getCompound(i));
            nation.relations.put(rel.getToNationId(), rel);
        }

        return nation;
    }

    @Override
    public String toString() {
        return "[" + tag + "] " + name + " (" + members.size() + " members)";
    }
}
