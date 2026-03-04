package dev.indrajeeth.papertpa.command;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        if (args.length == 0) {
            requestManager.cancelTeleport(player.getUniqueId());

            boolean cancelled = requestManager.cancelRequest(player.getUniqueId());

            if (cancelled) {
                MessageUtil.sendMessageWithPlaceholders(player, configManager.getPrefix() + configManager.getMessage("requests.cancelled"));
            } else {
                MessageUtil.sendMessageWithPlaceholders(player, configManager.getPrefix() + configManager.getMessage("admin.nothing-to-cancel"));
            }
        } else {
            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);

            if (target == null) {
                MessageUtil.sendMessageWithPlaceholders(player, configManager.getPrefix() + configManager.getMessage("general.player-not-found"));
                return true;
            }

            boolean cancelled = requestManager.cancelRequest(player.getUniqueId(), target.getUniqueId());

            if (cancelled) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", target.getName());
                MessageUtil.sendMessageWithPlaceholders(player, configManager.getPrefix() + configManager.getMessage("requests.cancelled-to", placeholders));

                Map<String, String> targetPlaceholders = new HashMap<>();
                targetPlaceholders.put("player", player.getName());
                MessageUtil.sendMessageWithPlaceholders(target, configManager.getPrefix() + configManager.getMessage("requests.cancelled-target", targetPlaceholders));
            } else {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", target.getName());
                MessageUtil.sendMessageWithPlaceholders(player, configManager.getPrefix() + configManager.getMessage("requests.no-request-to-player", placeholders));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return requestManager.getSentRequestsBy(player.getUniqueId()).stream()
                    .map(Bukkit::getPlayer)
                    .filter(java.util.Objects::nonNull)
                    .map(Player::getName)
                    .collect(java.util.stream.Collectors.toList());
        }
        return null;
    }

}

