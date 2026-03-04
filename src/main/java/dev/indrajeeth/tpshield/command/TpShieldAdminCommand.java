package dev.indrajeeth.tpshield.command;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpShieldAdminCommand extends SimpleCommandHandler {
    public TpShieldAdminCommand(TpShield plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !checkPermission(player, "tpshield.admin")) return true;

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

