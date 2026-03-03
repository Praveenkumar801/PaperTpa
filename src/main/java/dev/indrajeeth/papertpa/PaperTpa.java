package dev.indrajeeth.papertpa;

import dev.indrajeeth.papertpa.integration.PlaceholderAPIIntegration;
import dev.indrajeeth.papertpa.manager.CommandManager;
import dev.indrajeeth.papertpa.manager.ConfigManager;
import dev.indrajeeth.papertpa.manager.DatabaseManager;
import dev.indrajeeth.papertpa.manager.TeleportRequestManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import dev.indrajeeth.papertpa.util.Metrics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class PaperTpa extends JavaPlugin {
    private static PaperTpa instance;
    
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private TeleportRequestManager teleportManager;
    private CommandManager commandManager;
    private PlaceholderAPIIntegration placeholderIntegration;
    
    private ExecutorService executorService;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        instance = this;

        int pluginId = 28655;
        Metrics metrics = new Metrics(this, pluginId);
        
        executorService = Executors.newCachedThreadPool();
        
        saveDefaultConfig();
        
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        
        teleportManager = new TeleportRequestManager(this, databaseManager);
        
        commandManager = new CommandManager(this);
        commandManager.registerCommands();
        
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderIntegration = new PlaceholderAPIIntegration(this);
            if (placeholderIntegration.register()) {
                getLogger().info("PlaceholderAPI integration enabled!");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion");
            }
        } else {
            getLogger().info("PlaceholderAPI not found, skipping integration");
        }
        
        startCleanupTask();
        
        getLogger().info("PaperTpa v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        
        if (teleportManager != null) {
            for (java.util.UUID playerId : teleportManager.getPendingTeleports()) {
                teleportManager.cancelTeleport(playerId);
            }
        }
        
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        getLogger().info("PaperTpa has been disabled!");
        instance = null;
    }

    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                teleportManager.cleanupExpiredRequests();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error during cleanup task", e);
            }
        }, 1200L, 1200L); // 1 minute (1200 ticks)
    }

    public static PaperTpa getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public TeleportRequestManager getTeleportManager() {
        return teleportManager;
    }

    public ExecutorService getExecutor() {
        return executorService;
    }

    public boolean isPlaceholderAPIEnabled() {
        return placeholderIntegration != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }
}
