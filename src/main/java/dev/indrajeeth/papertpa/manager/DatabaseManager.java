package dev.indrajeeth.papertpa.manager;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.model.PlayerStats;

import java.io.File;
import java.sql.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

public class DatabaseManager {

    private final PaperTpa plugin;
    private Connection connection;
    private final File databaseFile;

    // ── Column whitelists (prevent SQL injection via dynamic column names) ──────
    private static final Set<String> VALID_STAT_COLUMNS = Set.of(
            "total_sent", "total_received", "total_accepted", "total_denied",
            "total_ratings", "rating_sum", "total_trap_reports");

    private static final Set<String> VALID_PREF_COLUMNS = Set.of(
            "requests_enabled", "last_request_time", "notification_enabled", "auto_accept");

    public DatabaseManager(PaperTpa plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "papertpa.db");
    }

    public void initialize() {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            createTables();
            migrateDatabase();
            plugin.getLogger().info("Database initialised successfully.");
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialise database", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close database connection", e);
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) initialize();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reconnect to database", e);
        }
        return connection;
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS user_preferences (
                    uuid                 TEXT    PRIMARY KEY,
                    requests_enabled     INTEGER NOT NULL DEFAULT 1,
                    last_request_time    INTEGER          DEFAULT 0,
                    notification_enabled INTEGER NOT NULL DEFAULT 1,
                    auto_accept          INTEGER NOT NULL DEFAULT 0
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid               TEXT    PRIMARY KEY,
                    total_sent         INTEGER NOT NULL DEFAULT 0,
                    total_received     INTEGER NOT NULL DEFAULT 0,
                    total_accepted     INTEGER NOT NULL DEFAULT 0,
                    total_denied       INTEGER NOT NULL DEFAULT 0,
                    total_ratings      INTEGER NOT NULL DEFAULT 0,
                    rating_sum         INTEGER NOT NULL DEFAULT 0,
                    total_trap_reports INTEGER NOT NULL DEFAULT 0
                )
                """);
        }
    }

    private void migrateDatabase() {
        tryAlter("ALTER TABLE user_preferences ADD COLUMN notification_enabled INTEGER NOT NULL DEFAULT 1");
        tryAlter("ALTER TABLE user_preferences ADD COLUMN auto_accept          INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE players ADD COLUMN total_ratings      INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE players ADD COLUMN rating_sum         INTEGER NOT NULL DEFAULT 0");
        tryAlter("ALTER TABLE players ADD COLUMN total_trap_reports INTEGER NOT NULL DEFAULT 0");
    }

    private void tryAlter(String sql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException ignored) { /* column already exists */ }
    }

    public CompletableFuture<Void>    setRequestsEnabled(UUID uuid, boolean v) {
        return runAsync(() -> upsertPref(uuid, "requests_enabled", v ? 1 : 0));
    }
    public CompletableFuture<Boolean> areRequestsEnabled(UUID uuid) {
        return supplyAsync(() -> readIntPref(uuid, "requests_enabled", 1) == 1);
    }
    public CompletableFuture<Void>    updateLastRequestTime(UUID uuid, long t) {
        return runAsync(() -> upsertPref(uuid, "last_request_time", t));
    }

    public CompletableFuture<Boolean> isNotificationEnabled(UUID uuid) {
        return supplyAsync(() -> readIntPref(uuid, "notification_enabled", 1) == 1);
    }
    public CompletableFuture<Void>    setNotificationEnabled(UUID uuid, boolean v) {
        return runAsync(() -> upsertPref(uuid, "notification_enabled", v ? 1 : 0));
    }

    public CompletableFuture<Boolean> isAutoAcceptEnabled(UUID uuid) {
        return supplyAsync(() -> readIntPref(uuid, "auto_accept", 0) == 1);
    }
    public CompletableFuture<Void>    setAutoAcceptEnabled(UUID uuid, boolean v) {
        return runAsync(() -> upsertPref(uuid, "auto_accept", v ? 1 : 0));
    }

    public CompletableFuture<PlayerStats> getPlayerStats(UUID uuid) {
        return supplyAsync(() -> {
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT total_sent,total_received,total_accepted,total_denied,"
                  + "total_ratings,rating_sum,total_trap_reports "
                  + "FROM players WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new PlayerStats(
                            rs.getInt("total_sent"), rs.getInt("total_received"),
                            rs.getInt("total_accepted"), rs.getInt("total_denied"),
                            rs.getInt("total_ratings"), rs.getInt("rating_sum"),
                            rs.getInt("total_trap_reports"));
                }
                return PlayerStats.EMPTY;
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to read player stats", e);
                return PlayerStats.EMPTY;
            }
        });
    }

    /**
     * Persists a rating submission: increments total_ratings, adds stars to rating_sum,
     * and optionally increments total_trap_reports.
     */
    public CompletableFuture<Void> addRating(UUID targetUuid, int stars, boolean trapReport) {
        return runAsync(() -> {
            String sql = "INSERT INTO players (uuid, total_ratings, rating_sum, total_trap_reports) "
                       + "VALUES (?, 1, ?, ?) "
                       + "ON CONFLICT(uuid) DO UPDATE SET "
                       + "total_ratings=total_ratings+1, "
                       + "rating_sum=rating_sum+excluded.rating_sum, "
                       + "total_trap_reports=total_trap_reports+excluded.total_trap_reports";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, targetUuid.toString());
                ps.setInt(2, stars);
                ps.setInt(3, trapReport ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save rating", e);
            }
        });
    }

    /**
     * Increments a stat column by 1.
     * Column name is validated against a whitelist to prevent SQL injection.
     */
    public CompletableFuture<Void> incrementStat(UUID uuid, String column) {
        if (!VALID_STAT_COLUMNS.contains(column)) {
            plugin.getLogger().severe("[DB] Blocked invalid stat column: '" + column + "'");
            return CompletableFuture.completedFuture(null);
        }
        return runAsync(() -> {
            String sql = "INSERT INTO players (uuid, " + column + ") VALUES (?,1) "
                       + "ON CONFLICT(uuid) DO UPDATE SET " + column + "=" + column + "+1";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to increment stat " + column, e);
            }
        });
    }

    /** Upsert a single column in user_preferences. Column validated against whitelist. */
    private void upsertPref(UUID uuid, String column, long value) {
        if (!VALID_PREF_COLUMNS.contains(column)) {
            plugin.getLogger().severe("[DB] Blocked invalid pref column: '" + column + "'");
            return;
        }
        String sql = "INSERT INTO user_preferences (uuid," + column + ") VALUES (?,?) "
                   + "ON CONFLICT(uuid) DO UPDATE SET " + column + "=excluded." + column;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to upsert pref " + column, e);
        }
    }

    private int readIntPref(UUID uuid, String column, int defaultValue) {
        if (!VALID_PREF_COLUMNS.contains(column)) {
            plugin.getLogger().severe("[DB] Blocked invalid pref column read: '" + column + "'");
            return defaultValue;
        }
        String sql = "SELECT " + column + " FROM user_preferences WHERE uuid=?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(column);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read pref " + column, e);
        }
        return defaultValue;
    }

    private CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, plugin.getExecutor());
    }
    private <T> CompletableFuture<T> supplyAsync(Supplier<T> s) {
        return CompletableFuture.supplyAsync(s, plugin.getExecutor());
    }
}
