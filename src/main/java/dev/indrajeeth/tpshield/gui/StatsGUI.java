package dev.indrajeeth.tpshield.gui;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.model.PlayerStats;
import dev.indrajeeth.tpshield.util.ItemResolver;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Displays lifetime TPA statistics for a target player.
 * Built asynchronously; call {@link #createAsync} and open the result on the main thread.
 */
public class StatsGUI implements InventoryHolder {

    private final UUID targetId;
    private final Inventory inventory;

    private StatsGUI(TpShield plugin, UUID targetId, String targetName, PlayerStats stats) {
        this.targetId = targetId;

        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.player-info");
        String rawTitle = cfg != null
                ? cfg.getString("title", plugin.getConfigManager().getMessage("gui.titles.stats"))
                : plugin.getConfigManager().getMessage("gui.titles.stats");
        String title = rawTitle.replace("%player%", targetName);
        int size = cfg != null ? cfg.getInt("size", 27) : 27;

        this.inventory = Bukkit.createInventory(this, size,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(title));

        ItemStack filler = cfg != null
                ? ItemResolver.resolve(cfg.getConfigurationSection("filler-item"))
                : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        int statsSlot = cfg != null ? cfg.getInt("stats-slot", 13) : 13;
        inventory.setItem(statsSlot, buildStatsHead(plugin, cfg, targetId, targetName, stats));
    }

    /**
     * Asynchronously fetches stats from the DB, then returns a ready-to-open StatsGUI
     * on the calling (async) thread. Caller must open the inventory on the main thread.
     */
    public static CompletableFuture<StatsGUI> createAsync(TpShield plugin, UUID targetId) {
        return plugin.getDatabaseManager().getPlayerStats(targetId).thenApply(stats -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
            String name = op.getName() != null ? op.getName() : targetId.toString();
            return new StatsGUI(plugin, targetId, name, stats);
        });
    }

    private static ItemStack buildStatsHead(TpShield plugin, ConfigurationSection cfg,
                                             UUID targetId, String name, PlayerStats stats) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        meta.setOwningPlayer(op);

        ConfigurationSection itemCfg = cfg != null ? cfg.getConfigurationSection("stats-item") : null;
        String displayName = itemCfg != null
                ? itemCfg.getString("name", plugin.getConfigManager().getMessage("gui.stats.head-name"))
                : plugin.getConfigManager().getMessage("gui.stats.head-name");
        meta.displayName(MessageUtil.toComponent(displayName.replace("%player%", name)));

        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        if (itemCfg != null) {
            String ratingStr = stats.totalRatings > 0
                    ? String.format("%.1f", stats.averageRating) : "0";
            String trapStr   = String.format("%.1f", stats.trapPercent);
            String tierStr   = getReputationTier(plugin, stats);
            for (String line : itemCfg.getStringList("lore")) {
                line = line
                    .replace("%player%",               name)
                    .replace("%tpa_sent%",              String.valueOf(stats.totalSent))
                    .replace("%tpa_received%",          String.valueOf(stats.totalReceived))
                    .replace("%tpa_accepted%",          String.valueOf(stats.totalAccepted))
                    .replace("%tpa_denied%",            String.valueOf(stats.totalDenied))
                    .replace("%tpa_rating%",            ratingStr)
                    .replace("%tpa_trap_percent%",      trapStr)
                    .replace("%tpa_reputation_tier%",   tierStr)
                    .replace("%tpa_total_ratings%",     String.valueOf(stats.totalRatings));
                lore.add(MessageUtil.toComponent(line));
            }
        }
        if (lore.isEmpty()) {
            String sent     = plugin.getConfigManager().getMessage("gui.stats.sent",
                    java.util.Map.of("tpa_sent", String.valueOf(stats.totalSent)));
            String received = plugin.getConfigManager().getMessage("gui.stats.received",
                    java.util.Map.of("tpa_received", String.valueOf(stats.totalReceived)));
            String accepted = plugin.getConfigManager().getMessage("gui.stats.accepted",
                    java.util.Map.of("tpa_accepted", String.valueOf(stats.totalAccepted)));
            String denied   = plugin.getConfigManager().getMessage("gui.stats.denied",
                    java.util.Map.of("tpa_denied", String.valueOf(stats.totalDenied)));
            lore.add(MessageUtil.toComponent(sent));
            lore.add(MessageUtil.toComponent(received));
            lore.add(MessageUtil.toComponent(accepted));
            lore.add(MessageUtil.toComponent(denied));
        }
        meta.lore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private static String getReputationTier(TpShield plugin,
                                             dev.indrajeeth.tpshield.model.PlayerStats stats) {
        if (stats.totalRatings == 0)
            return plugin.getConfigManager().getMessage("gui.reputation.unrated");
        if (stats.trapPercent >= 30.0 || stats.averageRating < 2.0)
            return plugin.getConfigManager().getMessage("gui.reputation.suspicious");
        if (stats.averageRating >= 4.0 && stats.trapPercent < 5.0)
            return plugin.getConfigManager().getMessage("gui.reputation.trusted");
        return plugin.getConfigManager().getMessage("gui.reputation.neutral");
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public UUID getTargetId()       { return targetId; }
}
