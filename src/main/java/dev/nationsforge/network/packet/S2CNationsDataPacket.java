package dev.nationsforge.network.packet;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.DiplomacyRelation;
import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.NationSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client: full snapshot of all nations.
 * Sent on player login and after every mutation.
 */
public class S2CNationsDataPacket {

    private final CompoundTag data;

    public S2CNationsDataPacket(CompoundTag data) {
        this.data = data;
    }

    public static S2CNationsDataPacket create(NationSavedData savedData) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (Nation n : savedData.getAllNations()) {
            list.add(n.toNBT());
        }
        root.put("nations", list);

        // Also serialise playerNation map so client knows which nation each player is
        // in
        CompoundTag pnMap = new CompoundTag();
        savedData.getPlayerNationMap().forEach((playerId, nationId) -> pnMap.putUUID(playerId.toString(), nationId));
        root.put("playerNation", pnMap);

        return new S2CNationsDataPacket(root);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public static S2CNationsDataPacket decode(FriendlyByteBuf buf) {
        return new S2CNationsDataPacket(buf.readNbt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientNationData.receive(data));
        ctx.get().setPacketHandled(true);
    }
}
