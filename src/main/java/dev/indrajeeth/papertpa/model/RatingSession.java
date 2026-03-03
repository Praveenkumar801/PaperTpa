package dev.indrajeeth.papertpa.model;

import java.util.UUID;

/**
 * Mutable session that tracks a player's pending post-teleport vote.
 * Created after a successful teleport once the rating-delay has elapsed.
 */
public final class RatingSession {

    private final UUID raterUUID;   // the player who was teleported (rates the host)
    private final UUID targetUUID;  // the host/accepter being rated
    private final long createdAt;   // when this session was opened

    private int stars = 0;          // 0 = not yet chosen; 1–5 = chosen
    private boolean trapReport = false;

    public RatingSession(UUID raterUUID, UUID targetUUID, long createdAt) {
        this.raterUUID  = raterUUID;
        this.targetUUID = targetUUID;
        this.createdAt  = createdAt;
    }

    public UUID getRaterUUID()   { return raterUUID; }
    public UUID getTargetUUID()  { return targetUUID; }
    public long getCreatedAt()   { return createdAt; }

    public int  getStars()       { return stars; }
    public void setStars(int s)  { this.stars = s; }

    public boolean isTrapReport()          { return trapReport; }
    public void    setTrapReport(boolean t) { this.trapReport = t; }

    /** Whether the player has chosen at least one star. */
    public boolean isReady() { return stars >= 1 && stars <= 5; }
}
