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
import java.util.UUID;

public class TPAcceptCommand extends SimpleCommandHandler {
    public TPAcceptCommand(PaperTpa plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!checkPermission(player, "papertpa.tpaccept")) return true;

        if (args.length == 0) {
            List<UUID> pending = requestManager.getPendingRequestsFor(player.getUniqueId());
            if (pending.isEmpty()) {
                MessageUtil.sendMessageWithPlaceholders(player, configManager.getPrefix() + configManager.getMessage("requests.no-pending-requests"));
                return true;
            }
            acceptRequest(player, pending.get(0));
        } else {
            String targetName = args[0];
            Player requester = Bukkit.getPlayer(targetName);
            if (requester == null) {
                MessageUtil.sendMessageWithPlaceholders(player, configManager.getPrefix() + configManager.getMessage("general.player-not-found"));
                return true;
            }
            acceptRequest(player, requester.getUniqueId());
        }
        
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            return requestManager.getPendingRequestsFor(player.getUniqueId()).stream()
                    .map(Bukkit::getPlayer)
                    .filter(java.util.Objects::nonNull)
                    .map(Player::getName)
                    .collect(java.util.stream.Collectors.toList());
        }
        return null;
    }

    private void acceptRequest(Player accepter, UUID requesterId) {
        requestManager.acceptRequest(accepter, requesterId).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player requester = Bukkit.getPlayer(requesterId);
                if (result && requester != null && requester.isOnline()) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", accepter.getName());
                    MessageUtil.sendMessageWithPlaceholders(requester, configManager.getPrefix() + configManager.getMessage("requests.accepted", placeholders));

                    placeholders.put("player", requester.getName());
                    MessageUtil.sendMessageWithPlaceholders(accepter, configManager.getPrefix() + configManager.getMessage("requests.accepted-target", placeholders));

                    dev.indrajeeth.papertpa.util.SoundUtil.play(accepter, "request-accepted");
                    dev.indrajeeth.papertpa.util.SoundUtil.play(requester, "request-accepted");
                } else {
                    MessageUtil.sendMessageWithPlaceholders(accepter, configManager.getPrefix() + configManager.getMessage("requests.no-pending-request"));
                }
            });
        });
    }

}

