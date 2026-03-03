package dev.indrajeeth.papertpa.util;

import dev.indrajeeth.papertpa.PaperTpa;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Plays configurable sounds from the {@code sounds} section of config.yml.
 *
 * <p>Each sound entry supports:
 * <pre>
 * sounds:
 *   request-sent:
 *     enabled: true
 *     sound: ENTITY_EXPERIENCE_ORB_PICKUP
 *     volume: 1.0
 *     pitch: 1.0
 * </pre>
 */
public final class SoundUtil {

    private SoundUtil() {}

    /**
     * Plays the named sound event to a player if sounds are globally enabled
     * and the specific event is enabled in config.
     *
     * @param player    the player to play the sound to
     * @param eventKey  the config key under {@code sounds.*}, e.g. {@code "request-sent"}
     */
    public static void play(Player player, String eventKey) {
        PaperTpa plugin = PaperTpa.getInstance();
        if (plugin == null || player == null) return;

        // Respect global sounds toggle
        if (!plugin.getConfigManager().areSoundsEnabled()) return;

        ConfigurationSection section = plugin.getConfigManager()
                .getConfig().getConfigurationSection("sounds." + eventKey);
        if (section == null) return;
        if (!section.getBoolean("enabled", true)) return;

        String soundName = section.getString("sound", "").toUpperCase().trim();
        if (soundName.isEmpty()) return;

        float volume = (float) section.getDouble("volume", 1.0);
        float pitch  = (float) section.getDouble("pitch",  1.0);

        Sound sound = parseSound(soundName, eventKey, plugin);
        if (sound == null) return;

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    // ──────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────

    private static Sound parseSound(String name, String eventKey, PaperTpa plugin) {
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING,
                    "[SoundUtil] Unknown sound '" + name + "' for event '" + eventKey
                            + "'. Check config.yml sounds section.");
            return null;
        }
    }
}
