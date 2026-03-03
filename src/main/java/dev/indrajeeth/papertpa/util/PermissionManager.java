package dev.indrajeeth.papertpa.util;

import dev.indrajeeth.papertpa.PaperTpa;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Central permission checker for PaperTpa.
 *
 * <p>When LuckPerms is present it queries the LuckPerms API directly, which is
 * more reliable than Bukkit's {@code Player#hasPermission} (avoids attachment
 * priority issues and ensures the LuckPerms calculated value is always used).
 * Falls back to {@code Player#hasPermission} if LuckPerms is not installed.
 *
 * <h3>Permission nodes defined by PaperTpa</h3>
 * <pre>
 *  papertpa.tpa             — send, cancel, list, view, notify, rate requests  (default: true)
 *  papertpa.tpaccept        — accept incoming requests                          (default: true)
 *  papertpa.tpdeny          — deny  incoming requests                           (default: true)
 *  papertpa.toggle          — /tptoggle (disable receiving requests)            (default: true)
 *  papertpa.auto            — /tpauto   (auto-accept all requests)              (default: true)
 *  papertpa.admin           — /papertpa reload                                  (default: op)
 *  papertpa.bypass          — bypass targets that have requests disabled        (default: op)
 *  papertpa.cooldown.bypass — skip the send-cooldown                            (default: op)
 *  papertpa.delay.bypass    — skip the warmup countdown                         (default: op)
 *  papertpa.*               — all of the above                                  (default: op)
 * </pre>
 */
public final class PermissionManager {

    private static LuckPerms luckPerms = null;

    private PermissionManager() {}

    /**
     * Called once during {@code onEnable}.
     * Attempts to obtain the LuckPerms API instance; silently ignores if unavailable.
     */
    public static void initialize(PaperTpa plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPerms = LuckPermsProvider.get();
                plugin.getLogger().info("LuckPerms integration enabled.");
            } catch (IllegalStateException e) {
                plugin.getLogger().log(Level.WARNING,
                        "LuckPerms plugin found but API is not available yet — "
                        + "falling back to Bukkit permission system.", e);
            }
        } else {
            plugin.getLogger().info("LuckPerms not found — using Bukkit permission system.");
        }
    }

    /**
     * Returns {@code true} if the player holds the given permission node.
     *
     * <p>When LuckPerms is active the check goes through its cached permission
     * data so the result always reflects the player's current LuckPerms state.
     * If LuckPerms returns {@link Tristate#UNDEFINED} (no explicit entry found)
     * the method falls back to Bukkit's {@code hasPermission}, which also
     * respects the {@code default:} values declared in {@code plugin.yml}.
     *
     * @param player     the online player to check
     * @param permission the fully-qualified permission node
     * @return {@code true} if the player has the permission
     */
    public static boolean hasPermission(Player player, String permission) {
        if (luckPerms != null) {
            try {
                User user = luckPerms.getUserManager().getUser(player.getUniqueId());
                if (user != null) {
                    Tristate result = user.getCachedData()
                            .getPermissionData()
                            .checkPermission(permission);
                    if (result != Tristate.UNDEFINED) {
                        return result == Tristate.TRUE;
                    }
                    // UNDEFINED → no explicit LuckPerms entry → fall through to Bukkit
                }
            } catch (Exception e) {
                // LuckPerms lookup failed unexpectedly — fall through to Bukkit
            }
        }
        return player.hasPermission(permission);
    }

    /** Convenience: sends the "no permission" message and returns {@code false}. */
    public static boolean checkOrDeny(Player player, String permission) {
        if (hasPermission(player, permission)) return true;
        MessageUtil.sendMessageWithPlaceholders(player,
                PaperTpa.getInstance().getConfigManager().getPrefix()
                + PaperTpa.getInstance().getConfigManager().getMessage("general.no-permission"));
        return false;
    }

    /** Returns {@code true} if the LuckPerms API was successfully loaded. */
    public static boolean isLuckPermsActive() {
        return luckPerms != null;
    }
}
