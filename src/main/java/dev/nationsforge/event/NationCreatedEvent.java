package dev.nationsforge.event;

import dev.nationsforge.nation.Nation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/** Fired when a new nation is founded. */
public class NationCreatedEvent extends Event {
    private final Nation nation;
    private final MinecraftServer server;

    public NationCreatedEvent(Nation nation, MinecraftServer server) {
        this.nation = nation;
        this.server = server;
    }

    public Nation getNation() {
        return nation;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
