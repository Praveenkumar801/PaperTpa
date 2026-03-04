package dev.indrajeeth.papertpa.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.manager.TeleportRequestManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PlaceholderAPIIntegration extends PlaceholderExpansion {
    private final PaperTpa plugin;

    public PlaceholderAPIIntegration(PaperTpa plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "papertpa";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        TeleportRequestManager manager = plugin.getTeleportManager();

        switch (identifier.toLowerCase()) {
            case "pending_requests_count":
                return String.valueOf(manager.getPendingRequestsFor(player.getUniqueId()).size());

            case "sent_requests_count":
                return String.valueOf(manager.getSentRequestsBy(player.getUniqueId()).size());

            case "has_pending_requests":
                return manager.getPendingRequestsFor(player.getUniqueId()).isEmpty() ? "false" : "true";

            case "cooldown_remaining":
                long lastRequest = manager.getCooldown(player.getUniqueId());
                if (lastRequest == 0) {
                    return "0";
                }
                long cooldownMs = plugin.getConfigManager().getCooldown() * 1000L;
                long remaining = Math.max(0, (cooldownMs - (System.currentTimeMillis() - lastRequest)) / 1000);
                return String.valueOf(remaining);

            default:
                return null;
        }
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        if (player.isOnline()) {
            return onPlaceholderRequest(player.getPlayer(), identifier);
        }
        return "";
    }
}

