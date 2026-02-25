package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationManager;
import dev.nationsforge.nation.RelationType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server: propose a diplomatic negotiation request to another nation.
 * Unlike the instant {@link C2SDiplomacyPacket}, this creates a PENDING request
 * that the target nation's diplomats must accept or decline.
 */
public class C2SDiplomacyRequestPacket {

    private final UUID targetNationId;
    private final RelationType proposedType;
    /** Optional opening message (max 256 chars). */
    private final String message;

    public C2SDiplomacyRequestPacket(UUID targetNationId, RelationType proposedType, String message) {
        this.targetNationId = targetNationId;
        this.proposedType = proposedType;
        this.message = message == null ? "" : message;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetNationId);
        buf.writeEnum(proposedType);
        buf.writeUtf(message.length() > 256 ? message.substring(0, 256) : message);
    }

    public static C2SDiplomacyRequestPacket decode(FriendlyByteBuf buf) {
        return new C2SDiplomacyRequestPacket(
                buf.readUUID(),
                buf.readEnum(RelationType.class),
                buf.readUtf(256));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ctx.get().enqueueWork(() ->
                NationManager.requestDiplomacy(player.getServer(),
                        player.getUUID(), targetNationId, proposedType, message));
        ctx.get().setPacketHandled(true);
    }
}
