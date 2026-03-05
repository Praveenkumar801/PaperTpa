package dev.indrajeeth.tpshield.command;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tpsettings
 * Opens a GUI where the player can view their stats and toggle TP requests,
 * auto-accept, and rating notification preferences.
 */
public class TPSettingsCommand extends SimpleCommandHandler {

    public TPSettingsCommand(TpShield plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }
        if (!checkPermission(player, "tpshield.tpa")) return true;

        plugin.getGUIManager().openSettingsGUI(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        return List.of();
    }
}
