package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationManager;
import dev.nationsforge.nation.NationRank;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → Server: request to change a member's rank. */
public class C2SSetRankPacket {

    private final UUID targetPlayerId;
    private final NationRank newRank;

    public C2SSetRankPacket(UUID targetPlayerId, NationRank newRank) {
        this.targetPlayerId = targetPlayerId;
        this.newRank = newRank;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetPlayerId);
        buf.writeEnum(newRank);
    }

    public static C2SSetRankPacket decode(FriendlyByteBuf buf) {
        return new C2SSetRankPacket(buf.readUUID(), buf.readEnum(NationRank.class));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null)
            return;
        ctx.get().enqueueWork(
                () -> NationManager.setRank(player.getServer(), player.getUUID(), targetPlayerId, newRank));
        ctx.get().setPacketHandled(true);
    }
}
