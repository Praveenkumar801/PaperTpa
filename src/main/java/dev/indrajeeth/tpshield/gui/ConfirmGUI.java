package dev.indrajeeth.tpshield.gui;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.model.PlayerStats;
import dev.indrajeeth.tpshield.util.ItemResolver;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI shown to the TPA sender after the target has accepted their request.
 * Displays the accepter's (target's) player head with their current coords,
 * dimension, trap % and rating in the lore, plus "Accept TP" / "Cancel" buttons.
 *
 * The sender clicks Accept to proceed with the teleport, or Cancel to abort.
 */
public class ConfirmGUI implements InventoryHolder {

    private final UUID requesterId;
    private final UUID accepterId;
    private final Inventory inventory;

    public ConfirmGUI(TpShield plugin, Player requester, UUID accepterId,
                      Location accepterLocation, PlayerStats accepterStats) {
        this.requesterId = requester.getUniqueId();
        this.accepterId  = accepterId;

        // Reuse the same layout config as the request GUI (identical slot positions)
        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.request");

        String title = plugin.getConfigManager().getMessage("gui.titles.confirm");
        int size = cfg != null ? cfg.getInt("size", 27) : 27;

        this.inventory = Bukkit.createInventory(this, size,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(title));

        // Fill background
        ItemStack filler = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("filler-item") : null,
                Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        // Accepter head in the centre slot
        int headSlot = cfg != null ? cfg.getInt("requester-slot", 4) : 4;
        inventory.setItem(headSlot,
                buildAccepterHead(plugin, accepterId, accepterLocation, accepterStats));

        // Accept TP button
        int acceptSlot = cfg != null ? cfg.getInt("accept-slot", 11) : 11;
        ItemStack acceptItem = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("accept-item") : null,
                Material.LIME_STAINED_GLASS_PANE);
        ItemResolver.applyText(acceptItem,
                plugin.getConfigManager().getMessage("gui.confirm.accept-name"),
                List.of(plugin.getConfigManager().getMessage("gui.confirm.accept-lore")));
        inventory.setItem(acceptSlot, acceptItem);

        // Cancel button
        int cancelSlot = cfg != null ? cfg.getInt("deny-slot", 15) : 15;
        ItemStack cancelItem = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("deny-item") : null,
                Material.RED_STAINED_GLASS_PANE);
        ItemResolver.applyText(cancelItem,
                plugin.getConfigManager().getMessage("gui.confirm.cancel-name"),
                List.of(plugin.getConfigManager().getMessage("gui.confirm.cancel-lore")));
        inventory.setItem(cancelSlot, cancelItem);
    }

    private static ItemStack buildAccepterHead(TpShield plugin, UUID accepterId,
                                               Location loc, PlayerStats stats) {
        Player accepter = Bukkit.getPlayer(accepterId);
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        if (accepter != null) {
            meta.setOwningPlayer(accepter);
        } else {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(accepterId));
        }

        String name = accepter != null ? accepter.getName()
                : (Bukkit.getOfflinePlayer(accepterId).getName() != null
                   ? Bukkit.getOfflinePlayer(accepterId).getName()
                   : accepterId.toString());

        meta.displayName(MessageUtil.toComponent(
                plugin.getConfigManager().getMessage("gui.confirm.head-name",
                        Map.of("player", name))));

        String dim   = getDimensionName(loc != null ? loc.getWorld() : null);
        int x = loc != null ? (int) loc.getX() : 0;
        int y = loc != null ? (int) loc.getY() : 0;
        int z = loc != null ? (int) loc.getZ() : 0;
        String ratingStr = stats.totalRatings > 0 ? String.format("%.1f", stats.averageRating) : "0";
        String trapStr   = String.format("%.1f", stats.trapPercent);

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.confirm.head-location",
                Map.of("x", String.valueOf(x), "y", String.valueOf(y), "z", String.valueOf(z)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.confirm.head-dimension", Map.of("dimension", dim))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.confirm.head-trap-percent", Map.of("tpa_trap_percent", trapStr))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.confirm.head-rating", Map.of("tpa_rating", ratingStr))));
        meta.lore(lore);

        skull.setItemMeta(meta);
        return skull;
    }

    private static String getDimensionName(World world) {
        if (world == null) return "Unknown";
        return switch (world.getEnvironment()) {
            case NETHER  -> "Nether";
            case THE_END -> "The End";
            default      -> world.getName();
        };
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public UUID getRequesterId() { return requesterId; }
    public UUID getAccepterId()  { return accepterId; }
}
