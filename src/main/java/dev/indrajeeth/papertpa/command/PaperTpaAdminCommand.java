package dev.indrajeeth.papertpa.command;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PaperTpaAdminCommand extends SimpleCommandHandler {
    public PaperTpaAdminCommand(PaperTpa plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !checkPermission(player, "papertpa.admin")) return true;

        if (args.length == 0) {
            return false;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            MessageUtil.sendMessage(sender, configManager.getPrefix() + configManager.getMessage("admin.config-reloading"));
            configManager.reload();
            MessageUtil.sendMessage(sender, configManager.getPrefix() + configManager.getMessage("admin.config-reloaded"));
            return true;
        }

        return false;
    }

}

