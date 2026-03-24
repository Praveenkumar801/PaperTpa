package dev.indrajeeth.tpshield.gui;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.model.RatingSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIManager {

    private final TpShield plugin;

    private final Map<UUID, RatingSession> ratingSessions = new ConcurrentHashMap<>();

    public GUIManager(TpShield plugin) {
        this.plugin = plugin;
    }

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

    public void cleanupStaleSessions(long maxAgeMs) {
        long now = System.currentTimeMillis();
        ratingSessions.entrySet().removeIf(e -> now - e.getValue().getCreatedAt() > maxAgeMs);
    }

    public void openStatsGUI(Player viewer, UUID targetId) {
        StatsGUI.createAsync(plugin, targetId).thenAccept(gui ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) viewer.openInventory(gui.getInventory());
            })
        );
    }

    public void openSettingsGUI(Player viewer) {
        SettingsGUI.createAsync(plugin, viewer).thenAccept(gui ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) viewer.openInventory(gui.getInventory());
            })
        );
    }
}

