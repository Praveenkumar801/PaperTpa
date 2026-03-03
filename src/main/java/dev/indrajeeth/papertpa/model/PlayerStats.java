package dev.indrajeeth.papertpa.model;

/** Snapshot of a player's lifetime TPA statistics read from the database. */
public final class PlayerStats {

    public final int totalSent;
    public final int totalReceived;
    public final int totalAccepted;
    public final int totalDenied;

    public PlayerStats(int totalSent, int totalReceived, int totalAccepted, int totalDenied) {
        this.totalSent     = totalSent;
        this.totalReceived = totalReceived;
        this.totalAccepted = totalAccepted;
        this.totalDenied   = totalDenied;
    }

    public static final PlayerStats EMPTY = new PlayerStats(0, 0, 0, 0);
}
