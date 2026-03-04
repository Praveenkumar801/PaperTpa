package dev.indrajeeth.tpshield.model;

import org.bukkit.Location;

import java.util.UUID;

/** Immutable snapshot of a TPA request captured at send-time. */
public final class TPARequest {

    private final UUID requesterId;
    private final UUID targetId;
    private final long requestTime;
    private final Location requesterLocation; // snapshot on main thread when /tpa was sent

    public TPARequest(UUID requesterId, UUID targetId, long requestTime, Location requesterLocation) {
        this.requesterId = requesterId;
        this.targetId = targetId;
        this.requestTime = requestTime;
        this.requesterLocation = requesterLocation;
    }

    public UUID getRequesterId()           { return requesterId; }
    public UUID getTargetId()              { return targetId; }
    public long getRequestTime()           { return requestTime; }
    public Location getRequesterLocation() { return requesterLocation; }
}
