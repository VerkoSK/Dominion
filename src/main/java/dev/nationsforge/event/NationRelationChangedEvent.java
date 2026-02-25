package dev.nationsforge.event;

import dev.nationsforge.nation.Nation;
import dev.nationsforge.nation.RelationType;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/** Fired when a diplomatic relation between two nations changes. */
public class NationRelationChangedEvent extends Event {
    private final Nation nationA;
    private final Nation nationB;
    private final RelationType oldType;
    private final RelationType newType;
    private final MinecraftServer server;

    public NationRelationChangedEvent(Nation nationA, Nation nationB,
            RelationType oldType, RelationType newType, MinecraftServer server) {
        this.nationA = nationA;
        this.nationB = nationB;
        this.oldType = oldType;
        this.newType = newType;
        this.server = server;
    }

    public Nation getNationA() {
        return nationA;
    }

    public Nation getNationB() {
        return nationB;
    }

    public RelationType getOldType() {
        return oldType;
    }

    public RelationType getNewType() {
        return newType;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
