package dev.indrajeeth.tpshield.command;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.manager.ConfigManager;
import dev.indrajeeth.tpshield.manager.TeleportRequestManager;
import dev.indrajeeth.tpshield.util.MessageUtil;
import dev.indrajeeth.tpshield.util.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class SimpleCommandHandler implements CommandExecutor, TabCompleter {
    protected final TpShield plugin;
    protected final TeleportRequestManager requestManager;
    protected final ConfigManager configManager;

    public SimpleCommandHandler(TpShield plugin) {
        this.plugin = plugin;
        this.requestManager = plugin.getTeleportManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return null;
    }

    /**
     * Checks the permission via LuckPerms (or Bukkit fallback).
     * Sends the configured "no-permission" message and returns {@code false} if denied.
     */
    protected boolean checkPermission(Player player, String node) {
        if (PermissionManager.hasPermission(player, node)) return true;
        MessageUtil.sendMessageWithPlaceholders(player,
                configManager.getPrefix() + configManager.getMessage("general.no-permission"));
        return false;
    }

    protected List<String> getOnlinePlayerNames(CommandSender sender) {
        UUID senderUuid = sender instanceof Player p ? p.getUniqueId() : null;
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> senderUuid == null || !p.getUniqueId().equals(senderUuid))
                .map(Player::getName)
                .collect(java.util.stream.Collectors.toList());
    }
}

