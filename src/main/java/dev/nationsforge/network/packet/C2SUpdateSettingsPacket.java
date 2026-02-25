package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: update the name, tag, colour, description and recruitment
 * settings.
 */
public class C2SUpdateSettingsPacket {

    private final String name;
    private final String tag;
    private final int colour;
    private final String description;
    private final boolean open;

    public C2SUpdateSettingsPacket(String name, String tag, int colour,
            String description, boolean open) {
        this.name = name;
        this.tag = tag;
        this.colour = colour;
        this.description = description == null ? "" : description;
        this.open = open;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeUtf(tag);
        buf.writeInt(colour);
        buf.writeUtf(description);
        buf.writeBoolean(open);
    }

    public static C2SUpdateSettingsPacket decode(FriendlyByteBuf buf) {
        return new C2SUpdateSettingsPacket(
                buf.readUtf(64), buf.readUtf(8), buf.readInt(), buf.readUtf(512), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null)
            return;
        ctx.get().enqueueWork(() -> NationManager.updateSettings(player.getServer(), player.getUUID(),
                name, tag, colour, description, open));
        ctx.get().setPacketHandled(true);
    }
}
