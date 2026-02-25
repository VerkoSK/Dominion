package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → Server: kick a member from the requester's nation. */
public class C2SKickMemberPacket {

    private final UUID targetPlayerId;

    public C2SKickMemberPacket(UUID targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetPlayerId);
    }

    public static C2SKickMemberPacket decode(FriendlyByteBuf buf) {
        return new C2SKickMemberPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null)
            return;
        ctx.get().enqueueWork(() -> NationManager.kickMember(player.getServer(), player.getUUID(), targetPlayerId));
        ctx.get().setPacketHandled(true);
    }
}
