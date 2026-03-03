package dev.indrajeeth.papertpa.gui;

import dev.indrajeeth.papertpa.PaperTpa;
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

import java.util.Map;
import java.util.UUID;

/**
 * GUI shown to the TPA target when they click the "[View Request]" notification.
 * Displays the requester's player head, current coords, and dimension,
 * plus Accept / View Stats / Deny buttons.
 */
public class RequestGUI implements InventoryHolder {

    private final UUID requesterId;
    private final UUID viewerId;
    private final Inventory inventory;

    public RequestGUI(PaperTpa plugin, Player viewer, UUID requesterId, TPARequest request) {
        this.requesterId = requesterId;
        this.viewerId    = viewer.getUniqueId();

        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.request");
        String title = cfg != null ? cfg.getString("title", "&6Teleport Request") : "&6Teleport Request";
        int size     = cfg != null ? cfg.getInt("size", 27) : 27;

        this.inventory = Bukkit.createInventory(this, size,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(title));

        // Fill with filler
        ItemStack filler = cfg != null
                ? ItemResolver.resolve(cfg.getConfigurationSection("filler-item"))
                : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        // ── Requester info item (player head) ────────────────────────────────
        int requesterSlot = cfg != null ? cfg.getInt("requester-slot", 4) : 4;
        inventory.setItem(requesterSlot, buildRequesterHead(plugin, requesterId, request));

        // ── Accept ──────────────────────────────────────────────────────────
        int acceptSlot = cfg != null ? cfg.getInt("accept-slot", 11) : 11;
        if (cfg != null) {
            inventory.setItem(acceptSlot, ItemResolver.resolve(cfg.getConfigurationSection("accept-item")));
        } else {
            inventory.setItem(acceptSlot, quickItem(Material.LIME_STAINED_GLASS_PANE, "&aAccept"));
        }

        // ── View Stats ───────────────────────────────────────────────────────
        int infoSlot = cfg != null ? cfg.getInt("info-slot", 13) : 13;
        if (cfg != null) {
            inventory.setItem(infoSlot, ItemResolver.resolve(cfg.getConfigurationSection("info-item")));
        } else {
            inventory.setItem(infoSlot, quickItem(Material.PAPER, "&eView Stats"));
        }

        // ── Deny ─────────────────────────────────────────────────────────────
        int denySlot = cfg != null ? cfg.getInt("deny-slot", 15) : 15;
        if (cfg != null) {
            inventory.setItem(denySlot, ItemResolver.resolve(cfg.getConfigurationSection("deny-item")));
        } else {
            inventory.setItem(denySlot, quickItem(Material.RED_STAINED_GLASS_PANE, "&cDeny"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

    private static ItemStack buildRequesterHead(PaperTpa plugin, UUID requesterId, TPARequest request) {
        Player requester = Bukkit.getPlayer(requesterId);
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        // Use online player profile for texture; fall back to offline
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
        String displayName = cfg != null ? cfg.getString("name", "&e%player%") : "&e%player%";
        displayName = displayName.replace("%player%", name);
        meta.displayName(MessageUtil.toComponent(displayName));

        // Build lore with location + dimension
        org.bukkit.Location loc = request.getRequesterLocation();
        String dim = getDimensionName(loc.getWorld());
        int x = (int) loc.getX(), y = (int) loc.getY(), z = (int) loc.getZ();

        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        if (cfg != null) {
            for (String line : cfg.getStringList("lore")) {
                line = line.replace("%x%", String.valueOf(x))
                           .replace("%y%", String.valueOf(y))
                           .replace("%z%", String.valueOf(z))
                           .replace("%dimension%", dim)
                           .replace("%world%", loc.getWorld() != null ? loc.getWorld().getName() : "?");
                lore.add(MessageUtil.toComponent(line));
            }
        }
        if (lore.isEmpty()) {
            lore.add(MessageUtil.toComponent("&7Location: &f" + x + ", " + y + ", " + z));
            lore.add(MessageUtil.toComponent("&7Dimension: &f" + dim));
        }
        meta.lore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    private static String getDimensionName(World world) {
        if (world == null) return "Unknown";
        return switch (world.getEnvironment()) {
            case NORMAL  -> "Overworld";
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
