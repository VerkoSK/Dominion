package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client → Server: request to join a specific nation by UUID. */
public class C2SJoinNationPacket {

    private final UUID nationId;

    public C2SJoinNationPacket(UUID nationId) {
        this.nationId = nationId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(nationId);
    }

    public static C2SJoinNationPacket decode(FriendlyByteBuf buf) {
        return new C2SJoinNationPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null)
            return;
        ctx.get().enqueueWork(() -> NationManager.joinNation(player.getServer(), player.getUUID(), nationId));
        ctx.get().setPacketHandled(true);
    }
}
