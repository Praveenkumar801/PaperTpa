package dev.indrajeeth.papertpa.command;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TPCancelCommand extends SimpleCommandHandler {
    public TPCancelCommand(PaperTpa plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!checkPermission(player, "papertpa.tpa")) return true;

        requestManager.cancelTeleport(player.getUniqueId());

        boolean cancelled = requestManager.cancelRequest(player.getUniqueId());

        if (cancelled) {
            MessageUtil.sendMessageWithPlaceholders(player, configManager.getPrefix() + configManager.getMessage("requests.cancelled"));
        } else {
            MessageUtil.sendMessageWithPlaceholders(player, configManager.getPrefix() + configManager.getMessage("admin.nothing-to-cancel"));
        }

        return true;
    }

}

