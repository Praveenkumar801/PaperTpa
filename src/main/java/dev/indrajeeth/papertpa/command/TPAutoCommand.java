package dev.indrajeeth.papertpa.command;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tpauto
 * Toggles auto-accept mode. When enabled, all incoming TPA requests from other
 * players are automatically accepted without requiring the player to interact.
 */
public class TPAutoCommand extends SimpleCommandHandler {

    public TPAutoCommand(PaperTpa plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }
        if (!checkPermission(player, "papertpa.auto")) return true;

        plugin.getDatabaseManager().isAutoAcceptEnabled(player.getUniqueId())
            .thenAccept(current -> {
                boolean next = !current;
                plugin.getDatabaseManager().setAutoAcceptEnabled(player.getUniqueId(), next)
                    .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                        String key = next ? "auto.enabled" : "auto.disabled";
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
