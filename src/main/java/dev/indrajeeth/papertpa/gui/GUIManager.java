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

    // Active rating sessions waiting for player input: raterUUID → session
    private final Map<UUID, RatingSession> ratingSessions = new ConcurrentHashMap<>();

    public GUIManager(PaperTpa plugin) {
        this.plugin = plugin;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Request GUI
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Opens the TPA request GUI for {@code viewer}, showing info about
     * the incoming request from {@code requesterId}.
     * Must be called on the main thread.
     */
    public void openRequestGUI(Player viewer, UUID requesterId) {
        TPARequest request = plugin.getTeleportManager().getRequest(requesterId);
        if (request == null || !request.getTargetId().equals(viewer.getUniqueId())) {
            viewer.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("requests.gui-not-active"));
            return;
        }
        RequestGUI gui = new RequestGUI(plugin, viewer, requesterId, request);
        viewer.openInventory(gui.getInventory());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rating GUI
    // ──────────────────────────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────────────────────────
    // Stats GUI (async load)
    // ──────────────────────────────────────────────────────────────────────────

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
