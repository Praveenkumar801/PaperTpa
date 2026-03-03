package dev.indrajeeth.papertpa.manager;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.command.*;
import org.bukkit.command.PluginCommand;

public class CommandManager {
    private final PaperTpa plugin;
    private final TPACommand tpaCommand;
    private final TPAcceptCommand tpAcceptCommand;
    private final TPDenyCommand tpDenyCommand;
    private final TPCancelCommand tpCancelCommand;
    private final TPListCommand tpListCommand;
    private final TPToggleCommand tpToggleCommand;
    private final PaperTpaAdminCommand adminCommand;

    public CommandManager(PaperTpa plugin) {
        this.plugin = plugin;
        this.tpaCommand = new TPACommand(plugin);
        this.tpAcceptCommand = new TPAcceptCommand(plugin);
        this.tpDenyCommand = new TPDenyCommand(plugin);
        this.tpCancelCommand = new TPCancelCommand(plugin);
        this.tpListCommand = new TPListCommand(plugin);
        this.tpToggleCommand = new TPToggleCommand(plugin);
        this.adminCommand = new PaperTpaAdminCommand(plugin);
    }

    public void registerCommands() {
        registerCommand("tpa", tpaCommand);
        registerCommand("tpaccept", tpAcceptCommand);
        registerCommand("tpdeny", tpDenyCommand);
        registerCommand("tpcancel", tpCancelCommand);
        registerCommand("tplist", tpListCommand);
        registerCommand("tptoggle", tpToggleCommand);
        registerCommand("papertpa", adminCommand);
        
        plugin.getLogger().info("Registered all commands");
    }

    private void registerCommand(String name, SimpleCommandHandler handler) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            plugin.getLogger().warning("Command " + name + " not found in plugin.yml!");
        }
    }
}

