package dev.indrajeeth.tpshield.model;

import org.bukkit.Location;

import java.util.UUID;

/** Immutable snapshot of a TPA request captured at send-time. */
public final class TPARequest {

    private final UUID requesterId;
    private final UUID targetId;
    private final long requestTime;
    private final Location requesterLocation;
    /** {@code true} when this is a /tpahere request (target teleports to requester). */
    private final boolean reverse;

    public TPARequest(UUID requesterId, UUID targetId, long requestTime, Location requesterLocation, boolean reverse) {
        this.requesterId = requesterId;
        this.targetId = targetId;
        this.requestTime = requestTime;
        this.requesterLocation = requesterLocation;
        this.reverse = reverse;
    }

    public UUID     getRequesterId()           { return requesterId; }
    public UUID     getTargetId()              { return targetId; }
    public long     getRequestTime()           { return requestTime; }
    public Location getRequesterLocation()     { return requesterLocation; }
    /** Returns {@code true} if this is a /tpahere request (target teleports to requester). */
    public boolean  isReverse()                { return reverse; }
}
