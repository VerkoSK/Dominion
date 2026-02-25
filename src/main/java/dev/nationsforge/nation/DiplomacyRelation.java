package dev.nationsforge.nation;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * A directed diplomatic relation from one nation to another.
 * Relations are symmetric by convention — the NationManager enforces that
 * both sides agree before a relation takes effect (except WAR which is
 * unilateral).
 */
public class DiplomacyRelation {

    private final UUID fromNationId;
    private final UUID toNationId;
    private RelationType type;
    private long establishedAt;
    private String reason;

    public DiplomacyRelation(UUID from, UUID to, RelationType type, String reason) {
        this.fromNationId = from;
        this.toNationId = to;
        this.type = type;
        this.establishedAt = System.currentTimeMillis();
        this.reason = reason == null ? "" : reason;
    }

    // ── Getters ─────────────────────────────────────────────────────────────────

    public UUID getFromNationId() {
        return fromNationId;
    }

    public UUID getToNationId() {
        return toNationId;
    }

    public RelationType getType() {
        return type;
    }

    public long getEstablishedAt() {
        return establishedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setType(RelationType type) {
        this.type = type;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    // ── NBT serialisation ────────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("from", fromNationId);
        tag.putUUID("to", toNationId);
        tag.putString("type", type.name());
        tag.putLong("at", establishedAt);
        tag.putString("reason", reason);
        return tag;
    }

    public static DiplomacyRelation fromNBT(CompoundTag tag) {
        UUID from = tag.getUUID("from");
        UUID to = tag.getUUID("to");
        RelationType type = RelationType.valueOf(tag.getString("type"));
        String reason = tag.getString("reason");
        DiplomacyRelation rel = new DiplomacyRelation(from, to, type, reason);
        rel.establishedAt = tag.getLong("at");
        return rel;
    }
}
