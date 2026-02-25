package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → Server: request to create a new nation. */
public class C2SCreateNationPacket {

    private final String name;
    private final String tag;
    private final int colour;
    private final String description;

    public C2SCreateNationPacket(String name, String tag, int colour, String description) {
        this.name = name;
        this.tag = tag;
        this.colour = colour;
        this.description = description;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeUtf(tag);
        buf.writeInt(colour);
        buf.writeUtf(description == null ? "" : description);
    }

    public static C2SCreateNationPacket decode(FriendlyByteBuf buf) {
        return new C2SCreateNationPacket(buf.readUtf(64), buf.readUtf(8), buf.readInt(), buf.readUtf(256));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null)
            return;
        ctx.get().enqueueWork(() -> NationManager.createNation(player.getServer(), player.getUUID(),
                name, tag, colour, description));
        ctx.get().setPacketHandled(true);
    }
}
