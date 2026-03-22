package dev.indrajeeth.tpshield.listener;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.gui.ConfirmGUI;
import dev.indrajeeth.tpshield.gui.RatingGUI;
import dev.indrajeeth.tpshield.gui.SettingsGUI;
import dev.indrajeeth.tpshield.gui.StatsGUI;
import dev.indrajeeth.tpshield.model.RatingSession;
import dev.indrajeeth.tpshield.util.MessageUtil;
import dev.indrajeeth.tpshield.util.SoundUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryClickListener implements Listener {

    private static final long MILLISECONDS_PER_SECOND = 1000L;

    private final TpShield plugin;

    public InventoryClickListener(TpShield plugin) {
        this.plugin = plugin;
    }

    private static boolean isPluginGUI(InventoryHolder holder) {
        return holder instanceof ConfirmGUI
                || holder instanceof RatingGUI
                || holder instanceof SettingsGUI
                || holder instanceof StatsGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof ConfirmGUI gui) {
            event.setCancelled(true);
            handleConfirmClick(player, gui, event.getRawSlot());
        } else if (holder instanceof RatingGUI gui) {
            event.setCancelled(true);
            handleRatingClick(player, gui, event.getRawSlot());
        } else if (holder instanceof SettingsGUI gui) {
            event.setCancelled(true);
            handleSettingsClick(player, gui, event.getRawSlot());
        } else if (holder instanceof StatsGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!isPluginGUI(holder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(s -> s < topSize)) {
            event.setCancelled(true);
        }
    }

    private void handleConfirmClick(Player player, ConfirmGUI gui, int slot) {
        if (!gui.getRequesterId().equals(player.getUniqueId())) return;

        var cfg = plugin.getConfigManager().getGuiSection("gui.confirm");
        if (cfg == null) cfg = plugin.getConfigManager().getGuiSection("gui.request");
        int acceptSlot = cfg != null ? cfg.getInt("accept-slot", 11) : 11;
        int cancelSlot = cfg != null ? cfg.getInt("deny-slot",   15) : 15;

        if (slot == acceptSlot) {
            player.closeInventory();
            boolean confirmed = plugin.getTeleportManager().confirmTeleport(player);
            if (!confirmed) {
                MessageUtil.sendMessageWithPlaceholders(player,
                        plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("requests.no-accepted-request"));
            }

        } else if (slot == cancelSlot) {
            player.closeInventory();
            UUID accepterId = gui.getAccepterId();
            boolean cancelled = plugin.getTeleportManager().cancelAcceptedRequest(player.getUniqueId());
            if (cancelled) {
                Player accepter = Bukkit.getPlayer(accepterId);
                Map<String, String> ph = new HashMap<>();
                ph.put("player", player.getName());
                MessageUtil.sendMessageWithPlaceholders(player,
                        plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("requests.sender-cancelled"));
                if (accepter != null && accepter.isOnline()) {
                    MessageUtil.sendMessageWithPlaceholders(accepter,
                            plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("requests.sender-cancelled-target", ph));
                    SoundUtil.play(accepter, "request-denied");
                }
            }
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

            UUID raterUUID   = session.getRaterUUID();
            UUID targetUUID  = session.getTargetUUID();
            int  stars       = session.getStars();
            boolean trap     = session.isTrapReport();
            long cooldownMs  = (long) plugin.getConfigManager().getRatingCooldown() * MILLISECONDS_PER_SECOND;

            plugin.getDatabaseManager().getLastRatedTime(raterUUID, targetUUID).thenAccept(lastRated -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    long timeRemaining = (lastRated + cooldownMs) - System.currentTimeMillis();
                    if (cooldownMs > 0 && timeRemaining > 0) {
                        String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
                        Map<String, String> ph = new HashMap<>();
                        ph.put("time",   String.valueOf(timeRemaining / MILLISECONDS_PER_SECOND));
                        ph.put("player", targetName != null ? targetName : targetUUID.toString());
                        MessageUtil.sendMessageWithPlaceholders(player,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage("rating.on-cooldown", ph));
                    } else {
                        player.closeInventory();
                        plugin.getGUIManager().removeRatingSession(player.getUniqueId());
                        plugin.getDatabaseManager().addRating(targetUUID, stars, trap);
                        plugin.getDatabaseManager().recordRatingTime(raterUUID, targetUUID,
                                System.currentTimeMillis());

                        String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
                        Map<String, String> ph = new HashMap<>();
                        ph.put("stars",  String.valueOf(stars));
                        ph.put("player", targetName != null ? targetName : targetUUID.toString());
                        MessageUtil.sendMessageWithPlaceholders(player,
                                plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage("rating.submitted", ph));
                        SoundUtil.play(player, "request-accepted");
                    }
                });
            });
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

    private void refreshSettingsGUI(TpShield plugin, Player player, SettingsGUI gui) {
        plugin.getDatabaseManager().getPlayerStats(player.getUniqueId()).thenAccept(stats ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) gui.refresh(plugin, player, stats);
            })
        );
    }
}
