package dev.indrajeeth.papertpa.command;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tpnotify
 * Toggles the post-teleport rating notification on or off.
 * When OFF the player will not receive the "Rate your experience" prompt after teleporting.
 */
public class TPNotifyCommand extends SimpleCommandHandler {

    public TPNotifyCommand(PaperTpa plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }
        if (!checkPermission(player, "papertpa.tpa")) return true;

        plugin.getDatabaseManager().isNotificationEnabled(player.getUniqueId())
            .thenAccept(current -> {
                boolean next = !current;
                plugin.getDatabaseManager().setNotificationEnabled(player.getUniqueId(), next)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                        String key = next ? "notify.enabled" : "notify.disabled";
                        MessageUtil.sendMessageWithPlaceholders(player,
                                configManager.getPrefix() + configManager.getMessage(key));
                    }));
            });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
