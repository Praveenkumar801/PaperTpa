package dev.indrajeeth.papertpa.command;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tpaview <player>
 * Opens the TPA Request GUI for the pending request from <player>.
 * This command is triggered by clicking the "[View Request]" chat notification.
 */
public class TPAViewCommand extends SimpleCommandHandler {

    public TPAViewCommand(PaperTpa plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }
        if (!checkPermission(player, "papertpa.tpa")) return true;

        if (args.length < 1) {
            MessageUtil.sendMessageWithPlaceholders(player,
                    configManager.getPrefix() + "&cUsage: /tpaview <player>");
            return true;
        }

        Player requester = Bukkit.getPlayer(args[0]);
        if (requester == null) {
            MessageUtil.sendMessageWithPlaceholders(player,
                    configManager.getPrefix() + configManager.getMessage("general.player-not-found"));
            return true;
        }

        var request = requestManager.getRequest(requester.getUniqueId());
        if (request == null || !request.getTargetId().equals(player.getUniqueId())) {
            MessageUtil.sendMessageWithPlaceholders(player,
                    configManager.getPrefix() + configManager.getMessage("requests.gui-not-active"));
            return true;
        }

        plugin.getGUIManager().openRequestGUI(player, requester.getUniqueId());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return requestManager.getPendingRequestsFor(player.getUniqueId()).stream()
                    .map(uuid -> {
                        Player p = Bukkit.getPlayer(uuid);
                        return p != null ? p.getName() : null;
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return List.of();
    }
}
