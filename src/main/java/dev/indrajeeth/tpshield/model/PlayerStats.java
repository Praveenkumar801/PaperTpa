package dev.indrajeeth.tpshield.model;

/** Snapshot of a player's lifetime TPA statistics read from the database. */
public final class PlayerStats {

    public final int totalSent;
    public final int totalReceived;
    public final int totalAccepted;
    public final int totalDenied;
    public final int totalRatings;
    public final int ratingSum;
    public final int totalTrapReports;

    /** Average star rating (0.0 if no ratings yet). */
    public final double averageRating;
    /** Percentage of accepted teleports that were reported as traps (0.0 if no accepted TPs). */
    public final double trapPercent;

    public PlayerStats(int totalSent, int totalReceived, int totalAccepted, int totalDenied,
                       int totalRatings, int ratingSum, int totalTrapReports) {
        this.totalSent        = totalSent;
        this.totalReceived    = totalReceived;
        this.totalAccepted    = totalAccepted;
        this.totalDenied      = totalDenied;
        this.totalRatings     = totalRatings;
        this.ratingSum        = ratingSum;
        this.totalTrapReports = totalTrapReports;
        this.averageRating    = totalRatings > 0 ? (double) ratingSum / totalRatings : 0.0;
        this.trapPercent      = totalAccepted > 0 ? (double) totalTrapReports / totalAccepted * 100.0 : 0.0;
    }

    public static final PlayerStats EMPTY = new PlayerStats(0, 0, 0, 0, 0, 0, 0);
}
