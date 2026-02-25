package dev.nationsforge.network.packet;

import dev.nationsforge.nation.NationFlag;
import dev.nationsforge.nation.NationManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: update the nation's custom flag.
 * The flag is transmitted as a serialised {@link CompoundTag}.
 */
public class C2SUpdateFlagPacket {

    private final CompoundTag flagNbt;

    public C2SUpdateFlagPacket(NationFlag flag) {
        this.flagNbt = flag.toNBT();
    }

    private C2SUpdateFlagPacket(CompoundTag nbt) {
        this.flagNbt = nbt;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(flagNbt);
    }

    public static C2SUpdateFlagPacket decode(FriendlyByteBuf buf) {
        return new C2SUpdateFlagPacket(buf.readNbt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null)
            return;
        NationFlag flag = NationFlag.fromNBT(flagNbt != null ? flagNbt : new CompoundTag());
        ctx.get().enqueueWork(() -> NationManager.updateFlag(player.getServer(), player.getUUID(), flag));
        ctx.get().setPacketHandled(true);
    }
}
