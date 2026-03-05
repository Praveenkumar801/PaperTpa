package dev.indrajeeth.tpshield.gui;

import dev.indrajeeth.tpshield.TpShield;
import dev.indrajeeth.tpshield.model.RatingSession;
import dev.indrajeeth.tpshield.util.ItemResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Post-teleport rating GUI. Allows the teleported player to pick 1–5 stars
 * and optionally report a trap, then confirm their rating.
 *
 * Layout (slots, size) is read from {@code config.yml → gui.rating}.
 * All display text is read from {@code messages.yml → gui.rating}.
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

        // Title always comes from messages.yml
        String title = plugin.getConfigManager().getMessage("gui.titles.rating");
        int size = cfg != null ? cfg.getInt("size", 27) : 27;

        this.inventory = Bukkit.createInventory(this, size,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(title));

        refresh(plugin);
    }

    /** Rebuild all items to reflect the current session state. */
    public void refresh(TpShield plugin) {
        ConfigurationSection cfg = plugin.getConfigManager().getGuiSection("gui.rating");
        int size = inventory.getSize();

        // Fill background
        ItemStack filler = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("filler-item") : null,
                Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        // Star buttons (1–5)
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

        // Trap-report toggle
        int trapSlot = cfg != null ? cfg.getInt("trap-report-slot", DEFAULT_TRAP_SLOT) : DEFAULT_TRAP_SLOT;
        inventory.setItem(trapSlot, buildTrapItem(plugin, cfg, session.isTrapReport()));

        // Confirm button
        int confirmSlot = cfg != null ? cfg.getInt("confirm-slot", DEFAULT_CONFIRM_SLOT) : DEFAULT_CONFIRM_SLOT;
        inventory.setItem(confirmSlot, buildConfirmItem(plugin, cfg, session.isReady()));
    }

    private static ItemStack buildStarItem(TpShield plugin, ConfigurationSection cfg,
                                            int stars, boolean filled) {
        Material fallback = filled ? Material.GOLDEN_SWORD : Material.STONE_SWORD;
        ItemStack item = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("star-item") : null, fallback);

        String nameKey = filled ? "gui.rating.star-filled" : "gui.rating.star-empty";
        Map<String, String> ph = Map.of("stars", String.valueOf(stars));
        ItemResolver.applyText(item,
                plugin.getConfigManager().getMessage(nameKey, ph),
                List.of(plugin.getConfigManager().getMessage("gui.rating.star-lore", ph)),
                ph);
        return item;
    }

    private static ItemStack buildTrapItem(TpShield plugin, ConfigurationSection cfg,
                                            boolean trapOn) {
        Material fallback = trapOn ? Material.RED_CONCRETE : Material.GREEN_CONCRETE;
        ItemStack item = ItemResolver.resolveAppearance(
                cfg != null ? cfg.getConfigurationSection("trap-report-item") : null, fallback);

        String stateMsg = plugin.getConfigManager().getMessage(trapOn ? "gui.state.on" : "gui.state.off");
        ItemResolver.applyText(item,
                plugin.getConfigManager().getMessage("gui.rating.trap-name",
                        Map.of("state", stateMsg)),
                List.of(plugin.getConfigManager().getMessage("gui.rating.trap-lore")));
        return item;
    }

    private static ItemStack buildConfirmItem(TpShield plugin, ConfigurationSection cfg,
                                               boolean ready) {
        Material fallback = ready ? Material.EMERALD : Material.BARRIER;
        ItemStack item = ItemResolver.resolveAppearance(
                ready && cfg != null ? cfg.getConfigurationSection("confirm-item") : null, fallback);

        String nameKey = ready ? "gui.rating.confirm-ready" : "gui.rating.confirm-not-ready";
        ItemResolver.applyText(item,
                plugin.getConfigManager().getMessage(nameKey),
                ready ? List.of(plugin.getConfigManager().getMessage("gui.rating.confirm-lore"))
                      : List.of());
        return item;
    }

    @Override
    public Inventory getInventory() { return inventory; }
    public RatingSession getSession() { return session; }

    /** Returns the star value (1–5) for the clicked slot, or -1 if not a star slot. */
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
