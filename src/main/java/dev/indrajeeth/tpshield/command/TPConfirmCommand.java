package dev.indrajeeth.tpshield.command;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tpconfirm
 * Used by the original TPA sender to confirm they want to proceed with the
 * teleport after the target has accepted.  The teleport will not fire until
 * the sender explicitly runs this command (or clicks the "[Accept TP]" button
 * in the confirmation message).
 */
public class TPConfirmCommand extends SimpleCommandHandler {

    public TPConfirmCommand(TpShield plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }
        if (!checkPermission(player, "tpshield.tpa")) return true;

        boolean confirmed = requestManager.confirmTeleport(player);
        if (!confirmed) {
            MessageUtil.sendMessageWithPlaceholders(player,
                    configManager.getPrefix() + configManager.getMessage("requests.no-accepted-request"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
