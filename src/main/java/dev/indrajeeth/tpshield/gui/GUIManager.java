package dev.indrajeeth.tpshield.gui;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.model.RatingSession;
import dev.indrajeeth.tpshield.model.TPARequest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Central manager for opening and tracking TpShield GUIs. */
public class GUIManager {

    private final TpShield plugin;

    private final Map<UUID, RatingSession> ratingSessions = new ConcurrentHashMap<>();

    public GUIManager(TpShield plugin) {
        this.plugin = plugin;
    }

    /**
     * Fetches the requester's stats asynchronously, then opens the TPA request GUI
     * for {@code viewer} on the main thread.
     * Must be called on the main thread (used as a trigger only).
     */
    public void openRequestGUI(Player viewer, UUID requesterId) {
        TPARequest request = plugin.getTeleportManager().getRequest(requesterId);
        if (request == null || !request.getTargetId().equals(viewer.getUniqueId())) {
            viewer.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("requests.gui-not-active"));
            return;
        }
        plugin.getDatabaseManager().getPlayerStats(requesterId).thenAccept(stats ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) {
                    RequestGUI gui = new RequestGUI(plugin, viewer, requesterId, request, stats);
                    viewer.openInventory(gui.getInventory());
                }
            })
        );
    }

    /**
     * Opens the confirmation GUI for the TPA sender after the target has accepted.
     * Shows the accepter's head with their captured location, stats, and
     * Accept TP / Cancel buttons. Must be called on the main thread.
     */
    public void openConfirmGUI(Player requester) {
        UUID requesterId = requester.getUniqueId();
        UUID accepterId  = plugin.getTeleportManager().getAcceptedRequestTarget(requesterId);
        Location dest    = plugin.getTeleportManager().getAcceptedDestination(requesterId);

        if (accepterId == null || dest == null) {
            requester.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("requests.no-accepted-request"));
            return;
        }

        plugin.getDatabaseManager().getPlayerStats(accepterId).thenAccept(stats ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (requester.isOnline()) {
                    ConfirmGUI gui = new ConfirmGUI(plugin, requester, accepterId, dest, stats);
                    requester.openInventory(gui.getInventory());
                }
            })
        );
    }

    /**
     * Opens the rating GUI for {@code rater} using their stored session.
     * Must be called on the main thread.
     */
    public void openRatingGUI(Player rater) {
        RatingSession session = ratingSessions.get(rater.getUniqueId());
        if (session == null) {
            rater.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("rating.expired"));
            return;
        }
        RatingGUI gui = new RatingGUI(plugin, session);
        rater.openInventory(gui.getInventory());
    }

    public void addRatingSession(UUID playerId, RatingSession session) {
        ratingSessions.put(playerId, session);
    }

    public RatingSession getRatingSession(UUID playerId) {
        return ratingSessions.get(playerId);
    }

    public void removeRatingSession(UUID playerId) {
        ratingSessions.remove(playerId);
    }

    /** Removes rating sessions older than {@code maxAgeMs} milliseconds. */
    public void cleanupStaleSessions(long maxAgeMs) {
        long now = System.currentTimeMillis();
        ratingSessions.entrySet().removeIf(e -> now - e.getValue().getCreatedAt() > maxAgeMs);
    }

    /**
     * Fetches stats asynchronously, then opens the stats GUI on the main thread.
     */
    public void openStatsGUI(Player viewer, UUID targetId) {
        StatsGUI.createAsync(plugin, targetId).thenAccept(gui ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) viewer.openInventory(gui.getInventory());
            })
        );
    }

    /**
     * Fetches settings and stats asynchronously, then opens the settings GUI on the main thread.
     */
    public void openSettingsGUI(Player viewer) {
        dev.indrajeeth.tpshield.gui.SettingsGUI.createAsync(plugin, viewer).thenAccept(gui ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) viewer.openInventory(gui.getInventory());
            })
        );
    }
}
