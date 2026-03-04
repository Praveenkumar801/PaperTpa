package dev.indrajeeth.papertpa.gui;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.model.RatingSession;
import dev.indrajeeth.papertpa.model.TPARequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Central manager for opening and tracking PaperTpa GUIs. */
public class GUIManager {

    private final PaperTpa plugin;

    private final Map<UUID, RatingSession> ratingSessions = new ConcurrentHashMap<>();

    public GUIManager(PaperTpa plugin) {
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
}
