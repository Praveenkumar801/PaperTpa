package dev.indrajeeth.tpshield.gui;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.model.PlayerStats;
import dev.indrajeeth.tpshield.util.ItemResolver;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
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
 * Per-player settings GUI opened by /tpsettings.
 * Displays the player's own stats and toggle buttons for:
 *   - TP Requests (tptoggle)
 *   - Auto-Accept TP (tpauto)
 *   - Rating Notifications (tpnotify)
 *
 * Layout (slot positions, materials) is read from {@code config.yml → gui.settings}.
 * All display text is read from {@code messages.yml → gui.settings}.
 */
public class SettingsGUI implements InventoryHolder {

    public static final int DEFAULT_HEAD_SLOT          = 4;
    public static final int DEFAULT_TP_REQUESTS_SLOT   = 10;
    public static final int DEFAULT_AUTOTP_SLOT        = 13;
    public static final int DEFAULT_NOTIFICATIONS_SLOT = 16;

    private final UUID     viewerId;
    private final Inventory inventory;

    private boolean requestsEnabled;
    private boolean autoAccept;
    private boolean notificationsEnabled;

    private SettingsGUI(TpShield plugin, Player viewer,
                        PlayerStats stats,
                        boolean requestsEnabled,
                        boolean autoAccept,
                        boolean notificationsEnabled) {
        this.viewerId             = viewer.getUniqueId();
        this.requestsEnabled      = requestsEnabled;
        this.autoAccept           = autoAccept;
        this.notificationsEnabled = notificationsEnabled;

        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.settings");

        String rawTitle = plugin.getConfigManager().getMessage("gui.titles.settings");
        int size = cfg != null ? cfg.getInt("size", 27) : 27;

        this.inventory = Bukkit.createInventory(this, size,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(rawTitle));

        buildContents(plugin, viewer, stats);
    }

    /**
     * Asynchronously fetches the player's DB settings and stats, then resolves
     * with a ready-to-open SettingsGUI. Open the inventory on the main thread.
     */
    public static CompletableFuture<SettingsGUI> createAsync(TpShield plugin, Player viewer) {
        UUID id = viewer.getUniqueId();
        CompletableFuture<Boolean>     reqF   = plugin.getDatabaseManager().areRequestsEnabled(id);
        CompletableFuture<Boolean>     autoF  = plugin.getDatabaseManager().isAutoAcceptEnabled(id);
        CompletableFuture<Boolean>     notifF = plugin.getDatabaseManager().isNotificationEnabled(id);
        CompletableFuture<PlayerStats> statsF = plugin.getDatabaseManager().getPlayerStats(id);
        return CompletableFuture.allOf(reqF, autoF, notifF, statsF)
                .thenApply(v -> new SettingsGUI(plugin, viewer,
                        statsF.join(), reqF.join(), autoF.join(), notifF.join()));
    }

    private void buildContents(TpShield plugin, Player viewer, PlayerStats stats) {
        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.settings");
        int size = inventory.getSize();

        ItemStack filler = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("filler-item") : null,
                Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        int headSlot = cfg != null ? cfg.getInt("head-slot", DEFAULT_HEAD_SLOT) : DEFAULT_HEAD_SLOT;
        inventory.setItem(headSlot, buildHeadItem(plugin, cfg, viewer, stats));

        int reqSlot = cfg != null
                ? cfg.getInt("tp-requests-slot", DEFAULT_TP_REQUESTS_SLOT) : DEFAULT_TP_REQUESTS_SLOT;
        inventory.setItem(reqSlot, buildToggleItem(plugin, cfg, requestsEnabled,
                "tp-requests-enabled-item", "tp-requests-disabled-item",
                Material.LIME_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
                "gui.settings.tp-requests-on",   "gui.settings.tp-requests-off",
                "gui.settings.tp-requests-lore-on", "gui.settings.tp-requests-lore-off"));

        int autoSlot = cfg != null
                ? cfg.getInt("autotp-slot", DEFAULT_AUTOTP_SLOT) : DEFAULT_AUTOTP_SLOT;
        inventory.setItem(autoSlot, buildToggleItem(plugin, cfg, autoAccept,
                "autotp-enabled-item", "autotp-disabled-item",
                Material.LIME_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
                "gui.settings.autotp-on",   "gui.settings.autotp-off",
                "gui.settings.autotp-lore-on", "gui.settings.autotp-lore-off"));

        int notifSlot = cfg != null
                ? cfg.getInt("notifications-slot", DEFAULT_NOTIFICATIONS_SLOT) : DEFAULT_NOTIFICATIONS_SLOT;
        inventory.setItem(notifSlot, buildToggleItem(plugin, cfg, notificationsEnabled,
                "notifications-enabled-item", "notifications-disabled-item",
                Material.LIME_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
                "gui.settings.notifications-on",   "gui.settings.notifications-off",
                "gui.settings.notifications-lore-on", "gui.settings.notifications-lore-off"));
    }

    private static ItemStack buildHeadItem(TpShield plugin, ConfigurationSection cfg,
                                            Player viewer, PlayerStats stats) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;
        meta.setOwningPlayer(viewer);

        String ratingStr = stats.totalRatings > 0 ? String.format("%.1f", stats.averageRating) : "0";
        String playerName = viewer.getName();

        meta.displayName(MessageUtil.toComponent(
                plugin.getConfigManager().getMessage("gui.settings.head-name",
                        Map.of("player", playerName))));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.settings.head-sent", Map.of("tpa_sent", String.valueOf(stats.totalSent)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.settings.head-received", Map.of("tpa_received", String.valueOf(stats.totalReceived)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.settings.head-accepted", Map.of("tpa_accepted", String.valueOf(stats.totalAccepted)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.settings.head-denied", Map.of("tpa_denied", String.valueOf(stats.totalDenied)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.settings.head-rating", Map.of("tpa_rating", ratingStr))));
        meta.lore(lore);

        skull.setItemMeta(meta);
        return skull;
    }

    /**
     * Builds a toggle button whose material and text come from messages.yml.
     * Material is resolved from config.yml when available, otherwise a plain fallback is used.
     */
    private static ItemStack buildToggleItem(TpShield plugin, ConfigurationSection cfg,
                                              boolean enabled,
                                              String enabledCfgKey, String disabledCfgKey,
                                              Material enabledMat, Material disabledMat,
                                              String nameOnKey, String nameOffKey,
                                              String loreOnKey, String loreOffKey) {
        String cfgKey = enabled ? enabledCfgKey : disabledCfgKey;
        Material fallback = enabled ? enabledMat : disabledMat;

        ItemStack item = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection(cfgKey) : null, fallback);

        ItemResolver.applyText(item,
                plugin.getConfigManager().getMessage(enabled ? nameOnKey : nameOffKey),
                List.of(plugin.getConfigManager().getMessage(enabled ? loreOnKey : loreOffKey)));
        return item;
    }

    public void setRequestsEnabled(boolean v)      { this.requestsEnabled      = v; }
    public void setAutoAccept(boolean v)            { this.autoAccept           = v; }
    public void setNotificationsEnabled(boolean v)  { this.notificationsEnabled = v; }

    /** Rebuild the inventory contents to reflect the current toggle states. */
    public void refresh(TpShield plugin, Player viewer, PlayerStats stats) {
        buildContents(plugin, viewer, stats);
    }

    public int getTpRequestsSlot(TpShield plugin) {
        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.settings");
        return cfg != null ? cfg.getInt("tp-requests-slot", DEFAULT_TP_REQUESTS_SLOT)
                           : DEFAULT_TP_REQUESTS_SLOT;
    }

    public int getAutoTpSlot(TpShield plugin) {
        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.settings");
        return cfg != null ? cfg.getInt("autotp-slot", DEFAULT_AUTOTP_SLOT) : DEFAULT_AUTOTP_SLOT;
    }

    public int getNotificationsSlot(TpShield plugin) {
        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.settings");
        return cfg != null ? cfg.getInt("notifications-slot", DEFAULT_NOTIFICATIONS_SLOT)
                           : DEFAULT_NOTIFICATIONS_SLOT;
    }

    @Override public Inventory getInventory()       { return inventory; }
    public UUID     getViewerId()                   { return viewerId; }
    public boolean  isRequestsEnabled()             { return requestsEnabled; }
    public boolean  isAutoAccept()                  { return autoAccept; }
    public boolean  isNotificationsEnabled()        { return notificationsEnabled; }
}
