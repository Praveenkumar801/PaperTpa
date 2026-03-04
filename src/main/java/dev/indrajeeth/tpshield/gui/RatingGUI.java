package dev.indrajeeth.tpshield.gui;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.model.RatingSession;
import dev.indrajeeth.tpshield.util.ItemResolver;
import dev.indrajeeth.tpshield.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Post-teleport rating GUI. Allows the teleported player to pick 1–5 stars
 * and optionally report a trap, then confirm their rating.
 */
public class RatingGUI implements InventoryHolder {

    private final RatingSession session;
    private final Inventory inventory;

    private static final int[] DEFAULT_STAR_SLOTS   = {10, 11, 12, 13, 14};
    private static final int   DEFAULT_TRAP_SLOT    = 16;
    private static final int   DEFAULT_CONFIRM_SLOT = 22;

    public RatingGUI(TpShield plugin, RatingSession session) {
        this.session = session;

        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.rating");
        String title = cfg != null
                ? cfg.getString("title", plugin.getConfigManager().getMessage("gui.titles.rating"))
                : plugin.getConfigManager().getMessage("gui.titles.rating");
        int size     = cfg != null ? cfg.getInt("size", 27) : 27;

        this.inventory = Bukkit.createInventory(this, size,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(title));

        refresh(plugin);
    }

    /** Rebuild all items to reflect the current session state. */
    public void refresh(TpShield plugin) {
        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.rating");
        int size = inventory.getSize();

        ItemStack filler = cfg != null
                ? ItemResolver.resolve(cfg.getConfigurationSection("filler-item"))
                : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        int[] starSlots = DEFAULT_STAR_SLOTS;
        if (cfg != null) {
            List<?> raw = cfg.getList("star-slots");
            if (raw != null && !raw.isEmpty()) {
                starSlots = raw.stream().mapToInt(o -> (int) o).toArray();
            }
        }
        for (int i = 0; i < starSlots.length; i++) {
            int stars = i + 1;
            inventory.setItem(starSlots[i], buildStarItem(plugin, cfg, stars, session.getStars() >= stars));
        }

        int trapSlot = cfg != null ? cfg.getInt("trap-report-slot", DEFAULT_TRAP_SLOT) : DEFAULT_TRAP_SLOT;
        inventory.setItem(trapSlot, buildTrapItem(plugin, cfg, session.isTrapReport()));

        int confirmSlot = cfg != null ? cfg.getInt("confirm-slot", DEFAULT_CONFIRM_SLOT) : DEFAULT_CONFIRM_SLOT;
        inventory.setItem(confirmSlot, buildConfirmItem(plugin, cfg, session.isReady()));
    }

    private static ItemStack buildStarItem(TpShield plugin, ConfigurationSection cfg,
                                            int stars, boolean filled) {
        ConfigurationSection starCfg = cfg != null ? cfg.getConfigurationSection("star-item") : null;

        if (starCfg != null) {
            return ItemResolver.resolve(starCfg, Map.of("stars", String.valueOf(stars)));
        }

        String nameKey = filled ? "gui.rating.star-filled" : "gui.rating.star-empty";
        String name = plugin.getConfigManager().getMessage(nameKey,
                Map.of("stars", String.valueOf(stars)));
        String loreStr = plugin.getConfigManager().getMessage("gui.rating.star-lore",
                Map.of("stars", String.valueOf(stars)));

        Material mat  = filled ? Material.GOLDEN_SWORD : Material.STONE_SWORD;
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.toComponent(name));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(MessageUtil.toComponent(loreStr));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack buildTrapItem(TpShield plugin, ConfigurationSection cfg, boolean trapOn) {
        ConfigurationSection trapCfg = cfg != null ? cfg.getConfigurationSection("trap-report-item") : null;
        String stateMsg = plugin.getConfigManager().getMessage(trapOn ? "gui.state.on" : "gui.state.off");
        if (trapCfg != null) {
            return ItemResolver.resolve(trapCfg, Map.of("state", stateMsg));
        }
        Material mat  = trapOn ? Material.RED_CONCRETE : Material.GREEN_CONCRETE;
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            String trapName = plugin.getConfigManager().getMessage("gui.rating.trap-name",
                    Map.of("state", stateMsg));
            meta.displayName(MessageUtil.toComponent(trapName));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack buildConfirmItem(TpShield plugin, ConfigurationSection cfg, boolean ready) {
        ConfigurationSection confirmCfg = cfg != null ? cfg.getConfigurationSection("confirm-item") : null;
        Material mat = ready ? Material.EMERALD : Material.BARRIER;
        if (confirmCfg != null && ready) {
            return ItemResolver.resolve(confirmCfg);
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            String msgKey = ready ? "gui.rating.confirm-ready" : "gui.rating.confirm-not-ready";
            meta.displayName(MessageUtil.toComponent(plugin.getConfigManager().getMessage(msgKey)));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public RatingSession getSession() { return session; }

    /** Returns the star value (1-5) for the clicked slot, or -1 if not a star slot. */
    public int getStarForSlot(TpShield plugin, int slot) {
        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.rating");
        int[] starSlots = DEFAULT_STAR_SLOTS;
        if (cfg != null) {
            List<?> raw = cfg.getList("star-slots");
            if (raw != null && !raw.isEmpty()) starSlots = raw.stream().mapToInt(o -> (int) o).toArray();
        }
        for (int i = 0; i < starSlots.length; i++) {
            if (starSlots[i] == slot) return i + 1;
        }
        return -1;
    }

    public int getTrapSlot(TpShield plugin) {
        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.rating");
        return cfg != null ? cfg.getInt("trap-report-slot", DEFAULT_TRAP_SLOT) : DEFAULT_TRAP_SLOT;
    }

    public int getConfirmSlot(TpShield plugin) {
        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.rating");
        return cfg != null ? cfg.getInt("confirm-slot", DEFAULT_CONFIRM_SLOT) : DEFAULT_CONFIRM_SLOT;
    }
}
