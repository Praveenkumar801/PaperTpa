package dev.indrajeeth.tpshield.util;

import dev.indrajeeth.tpshield.TpShield;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Central permission checker for TpShield.
 *
 * <p>When LuckPerms is present it queries the LuckPerms API directly, which is
 * more reliable than Bukkit's {@code Player#hasPermission} (avoids attachment
 * priority issues and ensures the LuckPerms calculated value is always used).
 * Falls back to {@code Player#hasPermission} if LuckPerms is not installed.
 */
public final class PermissionManager {

    private static LuckPerms luckPerms = null;

    private PermissionManager() {}

    /** Attempts to bind the LuckPerms API on startup; no-op if unavailable. */
    public static void initialize(TpShield plugin) {
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

    /** Returns {@code true} if the player holds the given permission node. */
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
                }
            } catch (Exception e) {
                // fall through to Bukkit
            }
        }
        return player.hasPermission(permission);
    }
}
