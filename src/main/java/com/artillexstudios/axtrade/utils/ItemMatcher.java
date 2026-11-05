package com.artillexstudios.axtrade.utils;

import com.artillexstudios.axapi.items.WrappedItemStack;
import com.artillexstudios.axapi.items.component.DataComponents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

public class ItemMatcher {
    private final ItemStack stack;
    private final WrappedItemStack wrapped;
    private final Map<String, Object> map;
    private final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
    private int needMatch = 0;

    public ItemMatcher(ItemStack stack, Map<String, Object> map) {
        this.stack = stack;
        this.wrapped = WrappedItemStack.wrap(stack);
        this.map = map;
    }

    public boolean isMatching() {
        int matches = 0;
        if (material()) matches++;
        if (name()) matches++;
        if (customModelData()) matches++;
        if (lore()) matches++;
        if (pdc()) matches++;
        // a rule with no recognised keys must not match every item
        return needMatch > 0 && matches >= needMatch;
    }

    public boolean material() {
        Object val = map.getOrDefault("material", map.get("type"));
        if (val == null) return false;
        needMatch++;
        var material = DataComponents.material();
        if (material == null) return false;
        return SimpleRegex.matches((String) val, wrapped.get(material).toString());
    }

    public boolean name() {
        Object val = map.get("name");
        if (val == null) return false;
        needMatch++;
        Component customName = wrapped.get(DataComponents.customName());
        if (customName == null) return false;
        String plain = plainSerializer.serialize(customName);
        return SimpleRegex.matches((String) val, plain);
    }

    public boolean customModelData() {
        Object val = map.get("custom-model-data");
        if (!(val instanceof Integer num)) return false;
        needMatch++;
        var cmd = wrapped.get(DataComponents.customModelData());
        if (cmd == null || cmd.floats().isEmpty()) return false;
        return num == cmd.floats().getFirst().intValue();
    }

    public boolean lore() {
        Object val = map.get("lore");
        if (val == null) return false;
        needMatch++;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;
        List<String> lore = meta.getLore();
        if (lore == null) return false;
        for (String line : lore) {
            if (SimpleRegex.matches(val.toString(), ChatColor.stripColor(line))) return true;
        }
        return false;
    }

    public boolean pdc() {
        Object val = map.get("pdc");
        if (val == null) return false;
        needMatch++;
        NamespacedKey key = NamespacedKey.fromString(val.toString().toLowerCase());
        ItemMeta meta = stack.getItemMeta();
        if (key == null || meta == null) return false;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.getKeys().contains(key)) return false;
        Object expected = map.get("pdc-value");
        if (expected == null) return true;
        if (!container.has(key, PersistentDataType.STRING)) return false;
        return SimpleRegex.matches(expected.toString(), container.get(key, PersistentDataType.STRING));
    }
}
