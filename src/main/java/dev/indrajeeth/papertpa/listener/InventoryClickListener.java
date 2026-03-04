package dev.indrajeeth.papertpa.listener;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.gui.SettingsGUI;
import dev.indrajeeth.papertpa.gui.RatingGUI;
import dev.indrajeeth.papertpa.gui.RequestGUI;
import dev.indrajeeth.papertpa.model.RatingSession;
import dev.indrajeeth.papertpa.util.MessageUtil;
import dev.indrajeeth.papertpa.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Routes inventory-click events for all PaperTpa GUIs. */
public class InventoryClickListener implements Listener {

    private final PaperTpa plugin;

    public InventoryClickListener(PaperTpa plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof RequestGUI gui) {
            event.setCancelled(true);
            handleRequestClick(player, gui, event.getRawSlot());
        } else if (holder instanceof RatingGUI gui) {
            event.setCancelled(true);
            handleRatingClick(player, gui, event.getRawSlot());
        } else if (holder instanceof SettingsGUI gui) {
            event.setCancelled(true);
            handleSettingsClick(player, gui, event.getRawSlot());
        } else if (holder instanceof dev.indrajeeth.papertpa.gui.StatsGUI) {
            event.setCancelled(true);
        }
    }

    private void handleRequestClick(Player player, RequestGUI gui, int slot) {
        var cfg = plugin.getConfigManager().getGuiSection("gui.request");
        int acceptSlot = cfg != null ? cfg.getInt("accept-slot", 11) : 11;
        int denySlot   = cfg != null ? cfg.getInt("deny-slot",   15) : 15;

        UUID requesterId = gui.getRequesterId();

        if (slot == acceptSlot) {
            player.closeInventory();
            plugin.getTeleportManager().acceptRequest(player, requesterId).thenAccept(success ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player requester = Bukkit.getPlayer(requesterId);
                    if (success && requester != null && requester.isOnline()) {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("player", player.getName());
                        MessageUtil.sendMessageWithPlaceholders(requester,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage("requests.accepted", ph));
                        ph.put("player", requester.getName());
                        MessageUtil.sendMessageWithPlaceholders(player,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage("requests.accepted-target", ph));
                        SoundUtil.play(player,    "request-accepted");
                        SoundUtil.play(requester, "request-accepted");
                    } else if (!success) {
                        MessageUtil.sendMessageWithPlaceholders(player,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage("requests.no-pending-request"));
                    }
                })
            );

        } else if (slot == denySlot) {
            player.closeInventory();
            plugin.getTeleportManager().denyRequest(player, requesterId).thenAccept(success ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player requester = Bukkit.getPlayer(requesterId);
                    if (success && requester != null && requester.isOnline()) {
                        Map<String, String> ph = new HashMap<>();
                        ph.put("player", player.getName());
                        MessageUtil.sendMessageWithPlaceholders(requester,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage("requests.denied", ph));
                        ph.put("player", requester.getName());
                        MessageUtil.sendMessageWithPlaceholders(player,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage("requests.denied-target", ph));
                        SoundUtil.play(player,    "request-denied");
                        SoundUtil.play(requester, "request-denied");
                    } else {
                        MessageUtil.sendMessageWithPlaceholders(player,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage("requests.no-pending-request"));
                    }
                })
            );
        }
    }

    private void handleRatingClick(Player player, RatingGUI gui, int slot) {
        RatingSession session = gui.getSession();
        if (!session.getRaterUUID().equals(player.getUniqueId())) return;

        int starValue = gui.getStarForSlot(plugin, slot);
        if (starValue != -1) {
            session.setStars(starValue);
            gui.refresh(plugin);
            return;
        }

        if (slot == gui.getTrapSlot(plugin)) {
            session.setTrapReport(!session.isTrapReport());
            gui.refresh(plugin);
            return;
        }

        if (slot == gui.getConfirmSlot(plugin)) {
            if (!session.isReady()) return;
            player.closeInventory();
            plugin.getGUIManager().removeRatingSession(player.getUniqueId());

            plugin.getDatabaseManager().addRating(
                    session.getTargetUUID(), session.getStars(), session.isTrapReport());

            Map<String, String> ph = new HashMap<>();
            ph.put("stars",  String.valueOf(session.getStars()));
            String targetName = Bukkit.getOfflinePlayer(session.getTargetUUID()).getName();
            ph.put("player", targetName != null ? targetName : session.getTargetUUID().toString());
            MessageUtil.sendMessageWithPlaceholders(player,
                    plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("rating.submitted", ph));
            SoundUtil.play(player, "request-accepted");
        }
    }

    private void handleSettingsClick(Player player, SettingsGUI gui, int slot) {
        if (!gui.getViewerId().equals(player.getUniqueId())) return;

        UUID playerId = player.getUniqueId();
        int reqSlot   = gui.getTpRequestsSlot(plugin);
        int autoSlot  = gui.getAutoTpSlot(plugin);
        int notifSlot = gui.getNotificationsSlot(plugin);

        if (slot == reqSlot) {
            boolean next = !gui.isRequestsEnabled();
            gui.setRequestsEnabled(next);
            plugin.getDatabaseManager().setRequestsEnabled(playerId, next).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        refreshSettingsGUI(plugin, player, gui);
                        String key = next ? "toggle.enabled" : "toggle.disabled";
                        MessageUtil.sendMessageWithPlaceholders(player,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage(key));
                    }
                })
            );

        } else if (slot == autoSlot) {
            boolean next = !gui.isAutoAccept();
            gui.setAutoAccept(next);
            plugin.getDatabaseManager().setAutoAcceptEnabled(playerId, next).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        refreshSettingsGUI(plugin, player, gui);
                        String key = next ? "auto.enabled" : "auto.disabled";
                        MessageUtil.sendMessageWithPlaceholders(player,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage(key));
                    }
                })
            );

        } else if (slot == notifSlot) {
            boolean next = !gui.isNotificationsEnabled();
            gui.setNotificationsEnabled(next);
            plugin.getDatabaseManager().setNotificationEnabled(playerId, next).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        refreshSettingsGUI(plugin, player, gui);
                        String key = next ? "notify.enabled" : "notify.disabled";
                        MessageUtil.sendMessageWithPlaceholders(player,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage(key));
                    }
                })
            );
        }
    }

    /** Fetches fresh stats then refreshes the open SettingsGUI for the player. */
    private void refreshSettingsGUI(PaperTpa plugin, Player player, SettingsGUI gui) {
        plugin.getDatabaseManager().getPlayerStats(player.getUniqueId()).thenAccept(stats ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) gui.refresh(plugin, player, stats);
            })
        );
    }
}
