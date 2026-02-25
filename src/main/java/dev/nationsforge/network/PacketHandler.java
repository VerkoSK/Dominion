package dev.nationsforge.network;

import dev.nationsforge.NationsForge;
import dev.nationsforge.network.packet.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NationsForge.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int id = 0;

    public static void register() {
        // S2C
        CHANNEL.messageBuilder(S2CNationsDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(S2CNationsDataPacket::decode)
                .encoder(S2CNationsDataPacket::encode)
                .consumerMainThread(S2CNationsDataPacket::handle)
                .add();

        // C2S
        CHANNEL.messageBuilder(C2SCreateNationPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SCreateNationPacket::decode)
                .encoder(C2SCreateNationPacket::encode)
                .consumerMainThread(C2SCreateNationPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SJoinNationPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SJoinNationPacket::decode)
                .encoder(C2SJoinNationPacket::encode)
                .consumerMainThread(C2SJoinNationPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SLeaveNationPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SLeaveNationPacket::decode)
                .encoder(C2SLeaveNationPacket::encode)
                .consumerMainThread(C2SLeaveNationPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SDiplomacyPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SDiplomacyPacket::decode)
                .encoder(C2SDiplomacyPacket::encode)
                .consumerMainThread(C2SDiplomacyPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SSetRankPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SSetRankPacket::decode)
                .encoder(C2SSetRankPacket::encode)
                .consumerMainThread(C2SSetRankPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SInvitePlayerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SInvitePlayerPacket::decode)
                .encoder(C2SInvitePlayerPacket::encode)
                .consumerMainThread(C2SInvitePlayerPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SKickMemberPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SKickMemberPacket::decode)
                .encoder(C2SKickMemberPacket::encode)
                .consumerMainThread(C2SKickMemberPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SUpdateSettingsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SUpdateSettingsPacket::decode)
                .encoder(C2SUpdateSettingsPacket::encode)
                .consumerMainThread(C2SUpdateSettingsPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SUpdateFlagPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(C2SUpdateFlagPacket::decode)
                .encoder(C2SUpdateFlagPacket::encode)
                .consumerMainThread(C2SUpdateFlagPacket::handle)
                .add();
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
