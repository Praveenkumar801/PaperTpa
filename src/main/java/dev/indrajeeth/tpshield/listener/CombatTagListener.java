package dev.indrajeeth.tpshield.listener;

import dev.indrajeeth.tpshield.TpShield;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Tags both the attacker and the victim in combat whenever PvP damage occurs,
 * provided the {@code settings.combat-tag.enabled} flag is {@code true}.
 *
 * <p>The tag duration comes from {@code settings.combat-tag.duration} and
 * prevents either player from using teleport commands until the tag expires.
 * Players with the {@code tpshield.combat.bypass} permission bypass the block
 * at the command level.
 *
 * <p>Combat tags are also cleared when a player disconnects so that stale
 * entries do not accumulate in memory.
 */
public class CombatTagListener implements Listener {

    private final TpShield plugin;

    public CombatTagListener(TpShield plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigManager().isCombatTagEnabled()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        int duration = plugin.getConfigManager().getCombatTagDuration();
        plugin.getCombatManager().tag(attacker, duration);
        plugin.getCombatManager().tag(victim, duration);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getCombatManager().clearTag(event.getPlayer().getUniqueId());
    }
}
