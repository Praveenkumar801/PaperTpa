package dev.indrajeeth.papertpa.gui;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.model.PlayerStats;
import dev.indrajeeth.papertpa.util.ItemResolver;
import dev.indrajeeth.papertpa.util.MessageUtil;
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

    private StatsGUI(PaperTpa plugin, UUID targetId, String targetName, PlayerStats stats) {
        this.targetId = targetId;

        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.player-info");
        String rawTitle = cfg != null
                ? cfg.getString("title", "&6Player Info — &e%player%") : "&6Player Info — &e%player%";
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
    public static CompletableFuture<StatsGUI> createAsync(PaperTpa plugin, UUID targetId) {
        return plugin.getDatabaseManager().getPlayerStats(targetId).thenApply(stats -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
            String name = op.getName() != null ? op.getName() : targetId.toString();
            return new StatsGUI(plugin, targetId, name, stats);
        });
    }

    private static ItemStack buildStatsHead(PaperTpa plugin, ConfigurationSection cfg,
                                             UUID targetId, String name, PlayerStats stats) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        OfflinePlayer op = Bukkit.getOfflinePlayer(targetId);
        meta.setOwningPlayer(op);

        ConfigurationSection itemCfg = cfg != null ? cfg.getConfigurationSection("stats-item") : null;
        String displayName = itemCfg != null
                ? itemCfg.getString("name", "&e%player%") : "&e%player%";
        meta.displayName(MessageUtil.toComponent(displayName.replace("%player%", name)));

        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        if (itemCfg != null) {
            for (String line : itemCfg.getStringList("lore")) {
                line = line
                    .replace("%player%",        name)
                    .replace("%tpa_sent%",       String.valueOf(stats.totalSent))
                    .replace("%tpa_received%",   String.valueOf(stats.totalReceived))
                    .replace("%tpa_accepted%",   String.valueOf(stats.totalAccepted))
                    .replace("%tpa_denied%",     String.valueOf(stats.totalDenied))
                    .replace("%tpa_rating%",     "N/A")
                    .replace("%tpa_trap_percent%", "N/A")
                    .replace("%tpa_reputation_tier%", "&7Neutral")
                    .replace("%tpa_total_ratings%",  "0");
                lore.add(MessageUtil.toComponent(line));
            }
        }
        if (lore.isEmpty()) {
            lore.add(MessageUtil.toComponent("&7Sent:     &f" + stats.totalSent));
            lore.add(MessageUtil.toComponent("&7Received: &f" + stats.totalReceived));
            lore.add(MessageUtil.toComponent("&7Accepted: &f" + stats.totalAccepted));
            lore.add(MessageUtil.toComponent("&7Denied:   &f" + stats.totalDenied));
        }
        meta.lore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public UUID getTargetId()       { return targetId; }
}
