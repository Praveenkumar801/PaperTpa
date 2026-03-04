package dev.indrajeeth.papertpa.listener;

import dev.indrajeeth.papertpa.PaperTpa;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Cancels incoming entity damage for players who have post-teleport immunity
 * active (see {@code settings.tp-immunity} in config.yml).
 * Also cleans up pending TP requests and warmup teleports when a player quits.
 */
public class ImmunityListener implements Listener {

    private final PaperTpa plugin;

    public ImmunityListener(PaperTpa plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        // Immunity blocks all entity damage (players and mobs alike).
        // Players with papertpa.immunity.bypass (default: op) can still hit immune players.
        if (event.getDamager() instanceof Player attacker
                && attacker.hasPermission("papertpa.immunity.bypass")) return;
        if (plugin.getTeleportManager().isImmune(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getTeleportManager().handlePlayerQuit(event.getPlayer());
    }
}
