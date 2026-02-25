package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → Server: invite a player (by UUID) to the requester's nation. */
public class C2SInvitePlayerPacket {

    private final UUID targetPlayerId;

    public C2SInvitePlayerPacket(UUID targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetPlayerId);
    }

    public static C2SInvitePlayerPacket decode(FriendlyByteBuf buf) {
        return new C2SInvitePlayerPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null)
            return;
        ctx.get().enqueueWork(() -> NationManager.invitePlayer(player.getServer(), player.getUUID(), targetPlayerId));
        ctx.get().setPacketHandled(true);
    }
}
