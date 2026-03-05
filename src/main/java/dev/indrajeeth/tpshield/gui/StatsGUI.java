package dev.indrajeeth.tpshield.gui;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.model.PlayerStats;
import dev.indrajeeth.tpshield.util.ItemResolver;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Displays lifetime TPA statistics for a target player.
 *
 * Layout (slots, size) is read from {@code config.yml → gui.player-info}.
 * All display text is read from {@code messages.yml → gui.stats}.
 *
 * Built asynchronously; call {@link #createAsync} and open the result on the main thread.
 */
public class StatsGUI implements InventoryHolder {

    private final UUID targetId;
    private final Inventory inventory;

    private StatsGUI(TpShield plugin, UUID targetId, String targetName, PlayerStats stats) {
        this.targetId = targetId;

        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.player-info");

        // Title always comes from messages.yml
        String title = plugin.getConfigManager()
                .getMessage("gui.titles.stats", Map.of("player", targetName));
        int size = cfg != null ? cfg.getInt("size", 27) : 27;

        this.inventory = Bukkit.createInventory(this, size,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(title));

        // Fill background
        ItemStack filler = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("filler-item") : null,
                Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        // Stats head
        int statsSlot = cfg != null ? cfg.getInt("stats-slot", 13) : 13;
        inventory.setItem(statsSlot, buildStatsHead(plugin, targetId, targetName, stats));
    }

    /**
     * Asynchronously fetches stats from the DB, then returns a ready-to-open StatsGUI.
     * Caller must open the inventory on the main thread.
     */
    public static CompletableFuture<StatsGUI> createAsync(TpShield plugin, UUID targetId) {
        return plugin.getDatabaseManager().getPlayerStats(targetId).thenApply(stats -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
            String name = op.getName() != null ? op.getName() : targetId.toString();
            return new StatsGUI(plugin, targetId, name, stats);
        });
    }

    private static ItemStack buildStatsHead(TpShield plugin,
                                             UUID targetId, String name, PlayerStats stats) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        meta.setOwningPlayer(op);

        String ratingStr = stats.totalRatings > 0 ? String.format("%.1f", stats.averageRating) : "0";
        String trapStr   = String.format("%.1f", stats.trapPercent);
        String tierStr   = getReputationTier(plugin, stats);

        meta.displayName(MessageUtil.toComponent(
                plugin.getConfigManager().getMessage("gui.stats.head-name",
                        Map.of("player", name))));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.stats.sent", Map.of("tpa_sent", String.valueOf(stats.totalSent)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.stats.received", Map.of("tpa_received", String.valueOf(stats.totalReceived)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.stats.accepted", Map.of("tpa_accepted", String.valueOf(stats.totalAccepted)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.stats.denied", Map.of("tpa_denied", String.valueOf(stats.totalDenied)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.stats.trap-percent", Map.of("tpa_trap_percent", trapStr))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.stats.rating", Map.of("tpa_rating", ratingStr))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.stats.total-ratings", Map.of("tpa_total_ratings", String.valueOf(stats.totalRatings)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.stats.reputation", Map.of("tpa_reputation_tier", tierStr))));

        meta.lore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private static String getReputationTier(TpShield plugin, PlayerStats stats) {
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
