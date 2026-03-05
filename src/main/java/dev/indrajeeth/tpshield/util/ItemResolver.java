package dev.indrajeeth.tpshield.util;

import dev.indrajeeth.tpshield.TpShield;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Resolves an {@link ItemStack} from a config section.
 *
 * <p>Config sections consumed by this class should only contain appearance
 * fields — {@code material}, {@code custom-model-data}, and {@code itemsadder}.
 * All display text (names, lore, titles) lives in {@code messages.yml} and is
 * applied separately via {@link #applyText}.
 *
 * <p>Supported config section structure:
 * <pre>
 * material: PAPER
 * custom-model-data: 1001   # 0 = disabled
 * itemsadder: "namespace:item_id"   # empty = disabled
 * </pre>
 */
public final class ItemResolver {

    private static final boolean ITEMS_ADDER_PRESENT =
            Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;

    private ItemResolver() {}

    /**
     * Resolves only the visual appearance (material, custom-model-data, ItemsAdder)
     * from {@code section}, without applying any display name or lore.
     * Use this together with {@link #applyText} when display text comes from
     * {@code messages.yml} rather than {@code config.yml}.
     *
     * @param section  config section with appearance fields, or {@code null}
     * @param fallback material to use when {@code section} is null or invalid
     * @return the resolved ItemStack (name/lore blank)
     */
    public static ItemStack resolveAppearance(ConfigurationSection section, Material fallback) {
        if (section == null) {
            return new ItemStack(fallback);
        }
        ItemStack item = resolveBase(section);
        return item != null ? item : new ItemStack(fallback);
    }

    /**
     * Applies a display name and lore to an existing {@link ItemStack}, replacing
     * {@code %key%} placeholders in each string with values from {@code placeholders}.
     *
     * @param item         the item to modify in-place
     * @param name         display name string (may be null or empty to skip)
     * @param lore         lore lines (may be null or empty to skip)
     * @param placeholders placeholder map, or an empty map for no substitutions
     */
    public static void applyText(ItemStack item, String name, List<String> lore,
                                  Map<String, String> placeholders) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (name != null && !name.isEmpty()) {
            meta.displayName(MessageUtil.toComponent(applyPlaceholders(name, placeholders)));
        }
        if (lore != null && !lore.isEmpty()) {
            List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(MessageUtil.toComponent(applyPlaceholders(line, placeholders)));
            }
            meta.lore(loreComponents);
        }
        item.setItemMeta(meta);
    }

    /** Convenience overload of {@link #applyText} with no placeholder substitutions. */
    public static void applyText(ItemStack item, String name, List<String> lore) {
        applyText(item, name, lore, Map.of());
    }

    /**
     * Build an ItemStack from a config section, applying name and lore after
     * replacing the given placeholder map.
     *
     * @param section      the config section describing the item
     * @param placeholders map of %key% → value substitutions for name/lore
     * @return the resolved ItemStack, or a barrier if resolution fails
     */
    public static ItemStack resolve(ConfigurationSection section,
                                    Map<String, String> placeholders) {
        if (section == null) {
            return fallback();
        }

        ItemStack item = resolveBase(section);
        if (item == null) {
            return fallback();
        }

        applyMeta(item, section, placeholders);
        return item;
    }

    /** Overload with no placeholder replacements. */
    public static ItemStack resolve(ConfigurationSection section) {
        return resolve(section, Map.of());
    }

    private static ItemStack resolveBase(ConfigurationSection section) {
        String itemsAdderId = section.getString("itemsadder", "").trim();

        if (ITEMS_ADDER_PRESENT && !itemsAdderId.isEmpty()) {
            ItemStack ia = resolveItemsAdder(itemsAdderId);
            if (ia != null) {
                return ia;
            }
            TpShield.getInstance().getLogger().warning(
                    "[ItemResolver] ItemsAdder item not found: " + itemsAdderId
                            + " — falling back to material.");
        }

        String materialName = section.getString("material", "PAPER").toUpperCase();
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            TpShield.getInstance().getLogger().warning(
                    "[ItemResolver] Unknown material: " + materialName + " — using BARRIER.");
            material = Material.BARRIER;
        }

        ItemStack item = new ItemStack(material);

        int cmd = section.getInt("custom-model-data", 0);
        if (cmd > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(cmd);
                item.setItemMeta(meta);
            }
        }

        return item;
    }

    private static ItemStack resolveItemsAdder(String namespaceId) {
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = customStackClass
                    .getMethod("getInstance", String.class)
                    .invoke(null, namespaceId);
            if (customStack == null) {
                return null;
            }
            return (ItemStack) customStackClass.getMethod("getItemStack").invoke(customStack);
        } catch (Exception e) {
            TpShield.getInstance().getLogger().log(Level.WARNING,
                    "[ItemResolver] Failed to load ItemsAdder item: " + namespaceId, e);
            return null;
        }
    }

    private static void applyMeta(ItemStack item, ConfigurationSection section,
                                   Map<String, String> placeholders) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String name = section.getString("name", "");
        if (!name.isEmpty()) {
            meta.displayName(MessageUtil.toComponent(applyPlaceholders(name, placeholders)));
        }

        List<String> loreRaw = section.getStringList("lore");
        if (!loreRaw.isEmpty()) {
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : loreRaw) {
                lore.add(MessageUtil.toComponent(applyPlaceholders(line, placeholders)));
            }
            meta.lore(lore);
        }

        item.setItemMeta(meta);
    }

    private static String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return text;
    }

    private static ItemStack fallback() {
        return new ItemStack(Material.BARRIER);
    }
}
