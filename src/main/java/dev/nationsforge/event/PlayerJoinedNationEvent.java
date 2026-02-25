package dev.nationsforge.event;

import dev.nationsforge.nation.Nation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the Forge event bus after a player successfully joins or creates a
 * nation.
 * KubeJS can listen: {@code NationEvents.playerJoined(event => { ... })}
 */
public class PlayerJoinedNationEvent extends Event {

    private final ServerPlayer player;
    private final Nation nation;

    public PlayerJoinedNationEvent(ServerPlayer player, Nation nation) {
        this.player = player;
        this.nation = nation;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Nation getNation() {
        return nation;
    }
}
