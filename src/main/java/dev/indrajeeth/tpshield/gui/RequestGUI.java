package dev.indrajeeth.tpshield.gui;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.model.PlayerStats;
import dev.indrajeeth.tpshield.model.TPARequest;
import dev.indrajeeth.tpshield.util.ItemResolver;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.Bukkit;
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
 * GUI shown to the TPA target when they click the "[View Request]" notification.
 * Displays the requester's player head with current coords, dimension, trap % and
 * rating in the lore, plus Accept / Deny buttons.
 */
public class RequestGUI implements InventoryHolder {

    private final UUID requesterId;
    private final UUID viewerId;
    private final Inventory inventory;

    public RequestGUI(TpShield plugin, Player viewer, UUID requesterId,
                      TPARequest request, PlayerStats requesterStats) {
        this.requesterId = requesterId;
        this.viewerId    = viewer.getUniqueId();

        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.request");

        // Title always comes from messages.yml
        String title = plugin.getConfigManager().getMessage("gui.titles.request");
        int size = cfg != null ? cfg.getInt("size", 27) : 27;

        this.inventory = Bukkit.createInventory(this, size,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(title));

        // Fill background
        ItemStack filler = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("filler-item") : null,
                Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        // Requester head
        int requesterSlot = cfg != null ? cfg.getInt("requester-slot", 4) : 4;
        inventory.setItem(requesterSlot,
                buildRequesterHead(plugin, requesterId, request, requesterStats));

        // Accept button
        int acceptSlot = cfg != null ? cfg.getInt("accept-slot", 11) : 11;
        ItemStack acceptItem = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("accept-item") : null,
                Material.LIME_STAINED_GLASS_PANE);
        ItemResolver.applyText(acceptItem,
                plugin.getConfigManager().getMessage("gui.request.accept-name"),
                List.of(plugin.getConfigManager().getMessage("gui.request.accept-lore")));
        inventory.setItem(acceptSlot, acceptItem);

        // Deny button
        int denySlot = cfg != null ? cfg.getInt("deny-slot", 15) : 15;
        ItemStack denyItem = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("deny-item") : null,
                Material.RED_STAINED_GLASS_PANE);
        ItemResolver.applyText(denyItem,
                plugin.getConfigManager().getMessage("gui.request.deny-name"),
                List.of(plugin.getConfigManager().getMessage("gui.request.deny-lore")));
        inventory.setItem(denySlot, denyItem);
    }

    private static ItemStack buildRequesterHead(TpShield plugin, UUID requesterId,
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

        String displayName = plugin.getConfigManager()
                .getMessage("gui.request.head-name", Map.of("player", name));
        meta.displayName(MessageUtil.toComponent(displayName));

        org.bukkit.Location loc = request.getRequesterLocation();
        String dim = getDimensionName(loc.getWorld());
        int x = (int) loc.getX(), y = (int) loc.getY(), z = (int) loc.getZ();
        String ratingStr = stats.totalRatings > 0 ? String.format("%.1f", stats.averageRating) : "0";
        String trapStr   = String.format("%.1f", stats.trapPercent);

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.request.head-location",
                Map.of("x", String.valueOf(x), "y", String.valueOf(y), "z", String.valueOf(z)))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.request.head-dimension", Map.of("dimension", dim))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.request.head-trap-percent", Map.of("tpa_trap_percent", trapStr))));
        lore.add(MessageUtil.toComponent(plugin.getConfigManager().getMessage(
                "gui.request.head-rating", Map.of("tpa_rating", ratingStr))));
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
    public UUID getViewerId()    { return viewerId; }
}
