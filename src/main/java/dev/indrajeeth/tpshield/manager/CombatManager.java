package dev.indrajeeth.tpshield.manager;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are currently in combat and for how long.
 * A player is tagged when they deal or receive PvP damage.
 * The tag expires after the duration configured at
 * {@code settings.combat-tag.duration} in config.yml.
 *
 * <p>Use {@link #isInCombat(UUID)} before allowing a teleport command.
 * Players holding the {@code tpshield.combat.bypass} permission are
 * exempt from this check and should be filtered by the caller.
 */
public class CombatManager {

    private final Map<UUID, Long> combatTags = new ConcurrentHashMap<>();

    /**
     * Tags a player as being in combat for the given number of seconds.
     * Calling this while the player is already tagged simply resets the timer.
     *
     * @param player          the player to tag
     * @param durationSeconds how long the combat tag should last
     */
    public void tag(Player player, int durationSeconds) {
        combatTags.put(player.getUniqueId(),
                System.currentTimeMillis() + (durationSeconds * 1000L));
    }

    /**
     * Returns {@code true} if the player currently has an active combat tag.
     * Expired entries are cleaned up lazily when this method is called.
     */
    public boolean isInCombat(UUID playerId) {
        Long expiry = combatTags.get(playerId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() < expiry) return true;
        combatTags.remove(playerId);
        return false;
    }

    /**
     * Returns the whole seconds remaining on the player's combat tag, or 0 if
     * the player is not in combat.
     */
    public long getRemainingSeconds(UUID playerId) {
        Long expiry = combatTags.get(playerId);
        if (expiry == null) return 0L;
        long remaining = (expiry - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, remaining);
    }

    /** Removes the combat tag for a player (e.g. on disconnect). */
    public void clearTag(UUID playerId) {
        combatTags.remove(playerId);
    }
}
