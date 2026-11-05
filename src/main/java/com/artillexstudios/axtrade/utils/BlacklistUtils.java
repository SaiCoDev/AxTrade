package com.artillexstudios.axtrade.utils;

import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.artillexstudios.axtrade.AxTrade.CONFIG;

public class BlacklistUtils {

    public static boolean isBlacklisted(@Nullable ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        try {
            List<Map<String, Object>> list = CONFIG.getMapList("blacklist-items");
            for (ItemStack stack : ShulkerUtils.getNestedContents(it)) {
                if (checkLegacy(stack)) return true;
                if (list == null) continue;
                for (Map<String, Object> map : list) {
                    if (new ItemMatcher(stack, map).isMatching()) return true;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    public static boolean isAnyBlacklisted(Collection<ItemStack> items) {
        for (ItemStack item : items) {
            if (isBlacklisted(item)) return true;
        }
        return false;
    }

    private static boolean checkLegacy(ItemStack it) {
        final Section section = CONFIG.getSection("blacklisted-items");
        if (section == null) return false;
        for (String s : section.getRoutesAsStrings(false)) {
            if (CONFIG.getString("blacklisted-items." + s + ".material") != null) {
                if (!it.getType().toString().equalsIgnoreCase(CONFIG.getString("blacklisted-items." + s + ".material"))) continue;
                return true;
            }

            if (CONFIG.getString("blacklisted-items." + s + ".name-contains") != null) {
                if (it.getItemMeta() == null) continue;
                if (!it.getItemMeta().getDisplayName().contains(CONFIG.getString("blacklisted-items." + s + ".name-contains"))) continue;
                return true;
            }
        }
        return false;
    }
}
