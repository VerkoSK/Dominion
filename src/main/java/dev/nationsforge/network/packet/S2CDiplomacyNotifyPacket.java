package dev.nationsforge.network.packet;

import dev.nationsforge.client.ClientNationData;
import dev.nationsforge.nation.DiplomacyRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client: push the full list of pending diplomacy requests for
 * the receiving player's nation. The client replaces its local cache.
 *
 * Sent when:
 * <ul>
 *   <li>A new request arrives (notify target nation members)</li>
 *   <li>A request is accepted or declined (update both sides)</li>
 *   <li>The player joins the server (initial sync of open requests)</li>
 * </ul>
 */
public class S2CDiplomacyNotifyPacket {

    private final List<DiplomacyRequest> requests;

    public S2CDiplomacyNotifyPacket(List<DiplomacyRequest> requests) {
        this.requests = requests == null ? List.of() : List.copyOf(requests);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(requests.size());
        for (DiplomacyRequest r : requests) {
            r.toBuf(buf);
        }
    }

    public static S2CDiplomacyNotifyPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<DiplomacyRequest> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(DiplomacyRequest.fromBuf(buf));
        }
        return new S2CDiplomacyNotifyPacket(list);
    }

    @SuppressWarnings("Convert2MethodRef")
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientNationData.setPendingRequests(requests));
        ctx.get().setPacketHandled(true);
    }
}
