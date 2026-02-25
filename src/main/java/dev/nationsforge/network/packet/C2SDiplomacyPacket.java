package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationManager;
import dev.nationsforge.nation.RelationType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server: request to set the diplomatic relation with another nation.
 */
public class C2SDiplomacyPacket {

    private final UUID targetNationId;
    private final RelationType type;
    private final String reason;

    public C2SDiplomacyPacket(UUID targetNationId, RelationType type, String reason) {
        this.targetNationId = targetNationId;
        this.type = type;
        this.reason = reason == null ? "" : reason;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetNationId);
        buf.writeEnum(type);
        buf.writeUtf(reason);
    }

    public static C2SDiplomacyPacket decode(FriendlyByteBuf buf) {
        return new C2SDiplomacyPacket(buf.readUUID(), buf.readEnum(RelationType.class), buf.readUtf(256));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null)
            return;
        ctx.get().enqueueWork(() -> NationManager.setRelation(player.getServer(), player.getUUID(),
                targetNationId, type, reason));
        ctx.get().setPacketHandled(true);
    }
}
