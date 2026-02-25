package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server: accept or decline a pending diplomacy request.
 */
public class C2SDiplomacyResponsePacket {

    private final UUID requestId;
    private final boolean accepted;
    /** Optional response message shown to the proposing side. */
    private final String message;

    public C2SDiplomacyResponsePacket(UUID requestId, boolean accepted, String message) {
        this.requestId = requestId;
        this.accepted = accepted;
        this.message = message == null ? "" : message;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(requestId);
        buf.writeBoolean(accepted);
        buf.writeUtf(message.length() > 256 ? message.substring(0, 256) : message);
    }

    public static C2SDiplomacyResponsePacket decode(FriendlyByteBuf buf) {
        return new C2SDiplomacyResponsePacket(
                buf.readUUID(),
                buf.readBoolean(),
                buf.readUtf(256));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ctx.get().enqueueWork(() ->
                NationManager.respondDiplomacy(player.getServer(),
                        player.getUUID(), requestId, accepted, message));
        ctx.get().setPacketHandled(true);
    }
}
