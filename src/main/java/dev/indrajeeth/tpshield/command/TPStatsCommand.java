package dev.indrajeeth.tpshield.command;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tpstats [player]
 * Opens the stats GUI showing TPA statistics.
 * Without arguments shows the sender's own stats;
 * with an argument shows the named player's stats (requires tpshield.stats.others).
 */
public class TPStatsCommand extends SimpleCommandHandler {

    public TPStatsCommand(TpShield plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }
        if (!checkPermission(player, "tpshield.tpa")) return true;

        if (args.length == 0) {
            plugin.getGUIManager().openStatsGUI(player, player.getUniqueId());
        } else {
            if (!player.hasPermission("tpshield.stats.others")) {
                MessageUtil.sendMessageWithPlaceholders(player,
                        configManager.getPrefix() + configManager.getMessage("general.no-permission"));
                return true;
            }
            Player onlineTarget = Bukkit.getPlayerExact(args[0]);
            if (onlineTarget != null) {
                plugin.getGUIManager().openStatsGUI(player, onlineTarget.getUniqueId());
            } else {
                org.bukkit.OfflinePlayer offlineTarget = Bukkit.getOfflinePlayerIfCached(args[0]);
                if (offlineTarget == null) {
                    MessageUtil.sendMessageWithPlaceholders(player,
                            configManager.getPrefix() + configManager.getMessage("general.player-not-found"));
                    return true;
                }
                plugin.getGUIManager().openStatsGUI(player, offlineTarget.getUniqueId());
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player p
                && p.hasPermission("tpshield.stats.others")) {
            return getOnlinePlayerNames(sender);
        }
        return List.of();
    }
}
