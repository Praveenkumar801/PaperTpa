package dev.indrajeeth.tpshield.manager;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.command.*;
import org.bukkit.command.PluginCommand;

import java.util.HashMap;
import java.util.Map;

public class CommandManager {
    private final TpShield plugin;
    private final Map<String, SimpleCommandHandler> handlers = new HashMap<>();

    public CommandManager(TpShield plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        register("tpa",       new TPACommand(plugin));
        register("tpaccept",  new TPAcceptCommand(plugin));
        register("tpdeny",    new TPDenyCommand(plugin));
        register("tptoggle",  new TPToggleCommand(plugin));
        register("tpcancel",  new TPCancelCommand(plugin));
        register("tplist",    new TPListCommand(plugin));
        register("tpaview",   new TPAViewCommand(plugin));
        register("tpconfirm", new TPConfirmCommand(plugin));
        register("tpnotify",  new TPNotifyCommand(plugin));
        register("tpauto",    new TPAutoCommand(plugin));
        register("tprate",    new TPRateCommand(plugin));
        register("tpstats",   new TPStatsCommand(plugin));
        register("tpsettings", new TPSettingsCommand(plugin));
        register("tpshield",  new TpShieldAdminCommand(plugin));

        plugin.getLogger().info("Registered all commands.");
    }

    /** Returns the handler for the given command name, or {@code null} if not registered. */
    public SimpleCommandHandler getHandler(String name) {
        return handlers.get(name);
    }

    private void register(String name, SimpleCommandHandler handler) {
        handlers.put(name, handler);
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        } else {
            plugin.getLogger().warning("Command /" + name + " not found in plugin.yml!");
        }
    }
}
