package dev.nationsforge.nation;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Represents a pending diplomatic negotiation request between two nations.
 * Requests remain PENDING until the target nation accepts or declines,
 * or until a configurable timeout expires.
 */
public class DiplomacyRequest {

    /** Lifecycle state of the request. */
    public enum Status {
        PENDING,
        ACCEPTED,
        DECLINED
    }

    private final UUID id;
    private final UUID fromNationId;
    private final UUID toNationId;
    private final RelationType proposedType;
    /** Optional opening message from the proposing side. */
    private final String message;
    private Status status;
    /** Server timestamp when the request was created (System.currentTimeMillis()). */
    private final long timestamp;

    public DiplomacyRequest(UUID fromNationId, UUID toNationId,
                            RelationType proposedType, String message) {
        this.id = UUID.randomUUID();
        this.fromNationId = fromNationId;
        this.toNationId = toNationId;
        this.proposedType = proposedType;
        this.message = message == null ? "" : message;
        this.status = Status.PENDING;
        this.timestamp = System.currentTimeMillis();
    }

    /** Used for NBT deserialisation — all fields provided explicitly. */
    private DiplomacyRequest(UUID id, UUID fromNationId, UUID toNationId,
                             RelationType proposedType, String message,
                             Status status, long timestamp) {
        this.id = id;
        this.fromNationId = fromNationId;
        this.toNationId = toNationId;
        this.proposedType = proposedType;
        this.message = message;
        this.status = status;
        this.timestamp = timestamp;
    }

    // ── Getters ───────────────────────────────────────────────────────────────────

    public UUID getId()             { return id; }
    public UUID getFromNationId()   { return fromNationId; }
    public UUID getToNationId()     { return toNationId; }
    public RelationType getProposedType() { return proposedType; }
    public String getMessage()      { return message; }
    public Status getStatus()       { return status; }
    public long getTimestamp()      { return timestamp; }

    public void setStatus(Status status) {
        this.status = status;
    }

    // ── NBT ───────────────────────────────────────────────────────────────────────

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("from", fromNationId);
        tag.putUUID("to", toNationId);
        tag.putString("type", proposedType.name());
        tag.putString("message", message);
        tag.putString("status", status.name());
        tag.putLong("timestamp", timestamp);
        return tag;
    }

    public static DiplomacyRequest fromNBT(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        UUID from = tag.getUUID("from");
        UUID to = tag.getUUID("to");
        RelationType type;
        try {
            type = RelationType.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            type = RelationType.NEUTRAL;
        }
        String message = tag.getString("message");
        Status status;
        try {
            status = Status.valueOf(tag.getString("status"));
        } catch (IllegalArgumentException e) {
            status = Status.PENDING;
        }
        long timestamp = tag.getLong("timestamp");
        return new DiplomacyRequest(id, from, to, type, message, status, timestamp);
    }

    // ── FriendlyByteBuf helpers (used by packets) ─────────────────────────────────

    public static DiplomacyRequest fromBuf(net.minecraft.network.FriendlyByteBuf buf) {
        UUID id        = buf.readUUID();
        UUID from      = buf.readUUID();
        UUID to        = buf.readUUID();
        RelationType type   = buf.readEnum(RelationType.class);
        String message = buf.readUtf(512);
        Status status  = buf.readEnum(Status.class);
        long timestamp = buf.readLong();
        return new DiplomacyRequest(id, from, to, type, message, status, timestamp);
    }

    public void toBuf(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUUID(fromNationId);
        buf.writeUUID(toNationId);
        buf.writeEnum(proposedType);
        buf.writeUtf(message);
        buf.writeEnum(status);
        buf.writeLong(timestamp);
    }
}
