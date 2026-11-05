package com.artillexstudios.axtrade.utils;

import com.artillexstudios.axtrade.hooks.HookManager;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShulkerUtils {
    private static final int MAX_DEPTH = 8;

    public static List<ItemStack> getStorageContents(ItemStack item, boolean includeSelf) {
        if (item.getType().name().endsWith("BUNDLE")) return getBundleContents(item, includeSelf);
        return getShulkerContents(item, includeSelf);
    }

    /**
     * The item itself plus everything inside it, including containers within containers
     * (a bundle holding a shulker box, for example).
     */
    @NotNull
    public static List<ItemStack> getNestedContents(ItemStack item) {
        List<ItemStack> result = new ArrayList<>();
        collect(item, result, 0);
        return result;
    }

    private static void collect(ItemStack item, List<ItemStack> result, int depth) {
        if (item == null || item.getType().isAir()) return;
        result.add(item);
        if (depth >= MAX_DEPTH) return;
        for (ItemStack inner : getStorageContents(item, false)) {
            if (inner != item) collect(inner, result, depth + 1);
        }
    }

    @NotNull
    private static List<ItemStack> getShulkerContents(ItemStack item, boolean includeSelf) {
        if (item == null) return new ArrayList<>();
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) return List.of(item);
        if (!(meta.getBlockState() instanceof Container container)) return List.of(item);
        List<ItemStack> storage = new ArrayList<>(Arrays.asList(container.getInventory().getStorageContents()));
        if (includeSelf) storage.add(item);
        return storage;
    }

    @NotNull
    private static List<ItemStack> getBundleContents(ItemStack item, boolean includeSelf) {
        if (item == null) return new ArrayList<>();
        if (!(item.getItemMeta() instanceof BundleMeta meta)) return List.of(item);
        List<ItemStack> storage = new ArrayList<>(meta.getItems());
        if (includeSelf) storage.add(item);
        return storage;
    }
}
