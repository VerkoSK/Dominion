package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → Server: request to leave the player's current nation. */
public class C2SLeaveNationPacket {

    public void encode(FriendlyByteBuf buf) {
    }

    public static C2SLeaveNationPacket decode(FriendlyByteBuf buf) {
        return new C2SLeaveNationPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null)
            return;
        ctx.get().enqueueWork(() -> NationManager.leaveNation(player.getServer(), player.getUUID()));
        ctx.get().setPacketHandled(true);
    }
}
