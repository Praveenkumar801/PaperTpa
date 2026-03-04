package dev.indrajeeth.papertpa.command;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tpstats [player]
 * Opens the stats GUI showing TPA statistics.
 * Without arguments shows the sender's own stats;
 * with an argument shows the named player's stats (requires papertpa.stats.others).
 */
public class TPStatsCommand extends SimpleCommandHandler {

    public TPStatsCommand(PaperTpa plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }
        if (!checkPermission(player, "papertpa.tpa")) return true;

        if (args.length == 0) {
            // Show own stats
            plugin.getGUIManager().openStatsGUI(player, player.getUniqueId());
        } else {
            // Show another player's stats
            if (!player.hasPermission("papertpa.stats.others")) {
                MessageUtil.sendMessageWithPlaceholders(player,
                        configManager.getPrefix() + configManager.getMessage("general.no-permission"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                MessageUtil.sendMessageWithPlaceholders(player,
                        configManager.getPrefix() + configManager.getMessage("general.player-not-found"));
                return true;
            }
            plugin.getGUIManager().openStatsGUI(player, target.getUniqueId());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player p
                && p.hasPermission("papertpa.stats.others")) {
            return getOnlinePlayerNames(sender);
        }
        return List.of();
    }
}
