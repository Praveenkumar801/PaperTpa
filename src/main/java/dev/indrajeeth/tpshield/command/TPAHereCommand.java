package dev.indrajeeth.tpshield.command;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /tpahere <player> — requests the target to teleport to the requester.
 * The target accepts/denies with the same /tpaccept and /tpdeny commands.
 */
public class TPAHereCommand extends SimpleCommandHandler {

    public TPAHereCommand(TpShield plugin) {
        super(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendMessage(sender, configManager.getMessage("general.player-only"));
            return true;
        }

        if (args.length < 1) {
            MessageUtil.sendMessageWithPlaceholders((Player) sender,
                    configManager.getPrefix() + configManager.getMessage("commands.tpahere.usage"));
            return true;
        }

        Player player = (Player) sender;
        if (!checkPermission(player, "tpshield.tpahere")) return true;
        if (!checkNotInCombat(player)) return true;

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            MessageUtil.sendMessageWithPlaceholders(player,
                    configManager.getPrefix() + configManager.getMessage("general.player-not-found"));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.sendMessageWithPlaceholders(player,
                    configManager.getPrefix() + configManager.getMessage("commands.tpahere.cannot-teleport-self"));
            return true;
        }

        requestManager.sendHereRequest(player, target).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS, AUTO_ACCEPTED -> {
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("player", target.getName());
                        MessageUtil.sendMessageWithPlaceholders(player,
                                configManager.getPrefix() + configManager.getMessage("requests.here-sent", placeholders));

                        placeholders.put("player", player.getName());
                        MessageUtil.sendMessageWithPlaceholders(target,
                                configManager.getPrefix() + configManager.getMessage("requests.here-received", placeholders));
                    }
                    case ALREADY_HAS_REQUEST ->
                        MessageUtil.sendMessageWithPlaceholders(player,
                                configManager.getPrefix() + configManager.getMessage("requests.already-has-request"));
                    case ON_COOLDOWN -> {
                        long lastRequest = requestManager.getCooldown(player.getUniqueId());
                        long cooldownMs = configManager.getCooldown() * 1000L;
                        long remaining = (cooldownMs - (System.currentTimeMillis() - lastRequest)) / 1000;
                        Map<String, String> cooldownPlaceholders = new HashMap<>();
                        cooldownPlaceholders.put("time", String.valueOf(remaining));
                        MessageUtil.sendMessageWithPlaceholders(player,
                                configManager.getPrefix() + configManager.getMessage("cooldown.message", cooldownPlaceholders));
                    }
                    case REQUESTS_DISABLED -> {
                        Map<String, String> disabledPlaceholders = new HashMap<>();
                        disabledPlaceholders.put("player", target.getName());
                        MessageUtil.sendMessageWithPlaceholders(player,
                                configManager.getPrefix() + configManager.getMessage("toggle.target-has-tp-disabled", disabledPlaceholders));
                    }
                    default -> {}
                }
            });
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return getOnlinePlayerNames(sender);
        }
        return null;
    }
}
