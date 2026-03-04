package dev.indrajeeth.tpshield;

import dev.indrajeeth.tpshield.command.BrigadierRegistrar;
import dev.indrajeeth.tpshield.gui.GUIManager;
import dev.indrajeeth.tpshield.integration.PlaceholderAPIIntegration;
import dev.indrajeeth.tpshield.listener.ImmunityListener;
import dev.indrajeeth.tpshield.listener.InventoryClickListener;
import dev.indrajeeth.tpshield.manager.CommandManager;
import dev.indrajeeth.tpshield.manager.ConfigManager;
import dev.indrajeeth.tpshield.manager.DatabaseManager;
import dev.indrajeeth.tpshield.manager.TeleportRequestManager;
import dev.indrajeeth.tpshield.util.Metrics;
import dev.indrajeeth.tpshield.util.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class TpShield extends JavaPlugin {

    private static TpShield instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private TeleportRequestManager teleportManager;
    private GUIManager guiManager;
    private CommandManager commandManager;
    private PlaceholderAPIIntegration placeholderIntegration;

    private ExecutorService executorService;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        instance = this;

        new Metrics(this, 28655);

        executorService = Executors.newCachedThreadPool();

        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        PermissionManager.initialize(this);

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        teleportManager = new TeleportRequestManager(this, databaseManager);

        guiManager = new GUIManager(this);

        commandManager = new CommandManager(this);
        commandManager.registerCommands();

        new BrigadierRegistrar(this, commandManager).register();

        Bukkit.getPluginManager().registerEvents(new InventoryClickListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ImmunityListener(this), this);

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

        getLogger().info("TpShield v" + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) cleanupTask.cancel();

        if (teleportManager != null) {
            for (java.util.UUID id : teleportManager.getPendingTeleports()) {
                teleportManager.cancelTeleport(id);
            }
        }

        if (databaseManager != null) databaseManager.close();
        if (executorService != null)  executorService.shutdown();

        getLogger().info("TpShield has been disabled!");
        instance = null;
    }

    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                teleportManager.cleanupExpiredRequests();
                guiManager.cleanupStaleSessions(600_000L);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error during cleanup task", e);
            }
        }, 1200L, 1200L);
    }

    public static TpShield        getInstance()         { return instance; }
    public ConfigManager          getConfigManager()    { return configManager; }
    public DatabaseManager        getDatabaseManager()  { return databaseManager; }
    public TeleportRequestManager getTeleportManager()  { return teleportManager; }
    public GUIManager             getGUIManager()       { return guiManager; }
    public ExecutorService        getExecutor()         { return executorService; }

    public boolean isPlaceholderAPIEnabled() {
        return placeholderIntegration != null
                && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }
}
