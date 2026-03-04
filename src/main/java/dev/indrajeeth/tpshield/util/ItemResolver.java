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
 * Resolves an {@link ItemStack} from a config section that supports:
 * <ul>
 *   <li>Vanilla {@code material} + optional {@code custom-model-data}</li>
 *   <li>ItemsAdder namespace:id via {@code itemsadder} key</li>
 * </ul>
 *
 * Config section structure:
 * <pre>
 * material: PAPER
 * custom-model-data: 1001   # 0 = disabled
 * itemsadder: "namespace:item_id"   # empty = disabled
 * name: "&eItem Name"
 * lore:
 *   - "&7Line one"
 * </pre>
 */
public final class ItemResolver {

    private static final boolean ITEMS_ADDER_PRESENT =
            Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;

    private ItemResolver() {}

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
