package dev.indrajeeth.tpshield.manager;

import dev.indrajeeth.tpshield.TpShield;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {
    private final TpShield plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File configFile;
    private File messagesFile;

    public ConfigManager(TpShield plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }
        config = plugin.getConfig();
        applyBundledDefaults(config, "config.yml");

        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveDefaultMessages();
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        applyBundledDefaults(messages, "messages.yml");
    }

    /**
     * Loads the bundled resource YAML and sets it as the default provider on
     * {@code cfg}. This means any key absent from the server's on-disk file will
     * transparently fall back to the value shipped with the plugin jar, so old
     * installs continue to work correctly after the plugin is updated with new keys.
     */
    private void applyBundledDefaults(FileConfiguration cfg, String resourceName) {
        try (InputStream in = plugin.getResource(resourceName)) {
            if (in == null) {
                plugin.getLogger().warning(
                        "Bundled resource '" + resourceName + "' not found in jar — "
                        + "default fallback values will not be available.");
                return;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            cfg.setDefaults(bundled);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not load bundled defaults from " + resourceName, e);
        }
    }

    private void saveDefaultMessages() {
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        if (!messagesFile.exists()) {
            try (InputStream in = plugin.getResource("messages.yml")) {
                if (in != null) {
                    Files.copy(in, messagesFile.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save default messages.yml", e);
            }
        }
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        applyBundledDefaults(config, "config.yml");
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        applyBundledDefaults(messages, "messages.yml");
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public int getRequestTimeout() {
        return config.getInt("settings.request-timeout", 60);
    }

    public int getCooldown() {
        return config.getInt("settings.cooldown", 10);
    }

    public int getTeleportDelay() {
        return config.getInt("settings.teleport-delay", 5);
    }

    public int getRatingDelay() {
        return config.getInt("settings.rating-delay", 30);
    }

    public boolean areSoundsEnabled() {
        return config.getBoolean("settings.enable-sounds", true);
    }

    public int getTpImmunity() {
        return config.getInt("settings.tp-immunity", 0);
    }

    public boolean isTpIdleEnabled() {
        return config.getBoolean("settings.tp-idle.enabled", false);
    }

    public int getTpIdleTime() {
        return config.getInt("settings.tp-idle.time", 5);
    }

    /**
     * Returns the ConfigurationSection for a GUI item path, e.g.
     * {@code "gui.request.accept-item"}.
     * Returns {@code null} when the path doesn't exist in either the server
     * config or the bundled defaults.
     */
    public ConfigurationSection getGuiSection(String path) {
        return config.getConfigurationSection(path);
    }

    public String getMessage(String path) {
        String value = messages.getString(path);
        return value != null ? value : "&cMessage not found: " + path;
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return message;
    }

    public String getPrefix() {
        return getMessage("general.prefix", null);
    }
}

