package dev.nationsforge.event;

import dev.nationsforge.nation.Nation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** Fired after a player leaves or is kicked from their nation. */
public class PlayerLeftNationEvent extends Event {

    private final ServerPlayer player;
    /** The nation they left (still fully populated at fire time). */
    private final Nation nation;
    private final Reason reason;

    public PlayerLeftNationEvent(ServerPlayer player, Nation nation, Reason reason) {
        this.player = player;
        this.nation = nation;
        this.reason = reason;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Nation getNation() {
        return nation;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        LEAVE, KICKED, DISBANDED
    }
}
