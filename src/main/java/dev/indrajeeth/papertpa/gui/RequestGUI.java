package dev.indrajeeth.papertpa.gui;

import dev.indrajeeth.papertpa.PaperTpa;
import dev.indrajeeth.papertpa.model.PlayerStats;
import dev.indrajeeth.papertpa.model.TPARequest;
import dev.indrajeeth.papertpa.util.ItemResolver;
import dev.indrajeeth.papertpa.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * GUI shown to the TPA target when they click the "[View Request]" notification.
 * Displays the requester's player head with current coords, dimension, trap % and
 * rating in the lore, plus Accept / Deny buttons.
 * The former "View Stats" button has been removed; stats are now shown inline.
 */
public class RequestGUI implements InventoryHolder {

    private final UUID requesterId;
    private final UUID viewerId;
    private final Inventory inventory;

    public RequestGUI(PaperTpa plugin, Player viewer, UUID requesterId,
                      TPARequest request, PlayerStats requesterStats) {
        this.requesterId = requesterId;
        this.viewerId    = viewer.getUniqueId();

        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.request");
        String title = cfg != null
                ? cfg.getString("title", plugin.getConfigManager().getMessage("gui.titles.request"))
                : plugin.getConfigManager().getMessage("gui.titles.request");
        int size     = cfg != null ? cfg.getInt("size", 27) : 27;

        this.inventory = Bukkit.createInventory(this, size,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(title));

        ItemStack filler = cfg != null
                ? ItemResolver.resolve(cfg.getConfigurationSection("filler-item"))
                : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        int requesterSlot = cfg != null ? cfg.getInt("requester-slot", 4) : 4;
        inventory.setItem(requesterSlot, buildRequesterHead(plugin, requesterId, request, requesterStats));

        int acceptSlot = cfg != null ? cfg.getInt("accept-slot", 11) : 11;
        if (cfg != null) {
            inventory.setItem(acceptSlot, ItemResolver.resolve(cfg.getConfigurationSection("accept-item")));
        } else {
            inventory.setItem(acceptSlot, quickItem(Material.LIME_STAINED_GLASS_PANE,
                    plugin.getConfigManager().getMessage("gui.request.accept-name")));
        }

        int denySlot = cfg != null ? cfg.getInt("deny-slot", 15) : 15;
        if (cfg != null) {
            inventory.setItem(denySlot, ItemResolver.resolve(cfg.getConfigurationSection("deny-item")));
        } else {
            inventory.setItem(denySlot, quickItem(Material.RED_STAINED_GLASS_PANE,
                    plugin.getConfigManager().getMessage("gui.request.deny-name")));
        }
    }

    private static ItemStack buildRequesterHead(PaperTpa plugin, UUID requesterId,
                                                TPARequest request, PlayerStats stats) {
        Player requester = Bukkit.getPlayer(requesterId);
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        if (requester != null) {
            meta.setOwningPlayer(requester);
        } else {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(requesterId));
        }

        String name = requester != null ? requester.getName()
                : (Bukkit.getOfflinePlayer(requesterId).getName() != null
                   ? Bukkit.getOfflinePlayer(requesterId).getName()
                   : requesterId.toString());

        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.request.requester-item");
        String displayName = cfg != null
                ? cfg.getString("name", plugin.getConfigManager().getMessage("gui.request.head-name"))
                : plugin.getConfigManager().getMessage("gui.request.head-name");
        displayName = displayName.replace("%player%", name);
        meta.displayName(MessageUtil.toComponent(displayName));

        org.bukkit.Location loc = request.getRequesterLocation();
        String dim = getDimensionName(loc.getWorld());
        int x = (int) loc.getX(), y = (int) loc.getY(), z = (int) loc.getZ();

        String ratingStr   = stats.totalRatings > 0 ? String.format("%.1f", stats.averageRating) : "0";
        String trapStr     = String.format("%.1f", stats.trapPercent);

        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        if (cfg != null) {
            for (String line : cfg.getStringList("lore")) {
                line = line.replace("%x%", String.valueOf(x))
                           .replace("%y%", String.valueOf(y))
                           .replace("%z%", String.valueOf(z))
                           .replace("%dimension%", dim)
                           .replace("%world%", loc.getWorld() != null ? loc.getWorld().getName() : "?")
                           .replace("%tpa_trap_percent%", trapStr)
                           .replace("%tpa_rating%", ratingStr);
                lore.add(MessageUtil.toComponent(line));
            }
        }
        if (lore.isEmpty()) {
            lore.add(MessageUtil.toComponent(
                    plugin.getConfigManager().getMessage("gui.request.location",
                            java.util.Map.of("x", String.valueOf(x),
                                             "y", String.valueOf(y),
                                             "z", String.valueOf(z)))));
            lore.add(MessageUtil.toComponent(
                    plugin.getConfigManager().getMessage("gui.request.dimension",
                            java.util.Map.of("dimension", dim))));
            lore.add(MessageUtil.toComponent(
                    plugin.getConfigManager().getMessage("gui.request.trap-percent",
                            java.util.Map.of("tpa_trap_percent", trapStr))));
            lore.add(MessageUtil.toComponent(
                    plugin.getConfigManager().getMessage("gui.request.rating",
                            java.util.Map.of("tpa_rating", ratingStr))));
        }
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

    private static ItemStack quickItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.toComponent(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public UUID getRequesterId() { return requesterId; }
    public UUID getViewerId()    { return viewerId; }
}
