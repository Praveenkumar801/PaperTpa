package dev.indrajeeth.papertpa.command;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tprate <player>
 * Opens the post-teleport rating GUI for the most recent teleport to <player>.
 * Triggered by clicking the "[Rate]" notification sent after the rating delay.
 */
public class TPRateCommand extends SimpleCommandHandler {

    public TPRateCommand(PaperTpa plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }

        var session = plugin.getGUIManager().getRatingSession(player.getUniqueId());
        if (session == null) {
            MessageUtil.sendMessageWithPlaceholders(player,
                    configManager.getPrefix() + configManager.getMessage("rating.expired"));
            return true;
        }

        plugin.getGUIManager().openRatingGUI(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
