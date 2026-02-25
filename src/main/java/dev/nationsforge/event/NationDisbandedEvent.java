package dev.nationsforge.event;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/** Fired just before a nation is permanently removed. */
public class NationDisbandedEvent extends Event {
    private final UUID nationId;
    private final String nationName;
    private final String nationTag;
    private final MinecraftServer server;

    public NationDisbandedEvent(UUID nationId, String nationName, String nationTag, MinecraftServer server) {
        this.nationId = nationId;
        this.nationName = nationName;
        this.nationTag = nationTag;
        this.server = server;
    }

    public UUID getNationId() {
        return nationId;
    }

    public String getNationName() {
        return nationName;
    }

    public String getNationTag() {
        return nationTag;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
