package com.artillexstudios.axtrade.history.gui;

import com.artillexstudios.axapi.config.Config;
import com.artillexstudios.axapi.libs.boostedyaml.block.implementation.Section;
import com.artillexstudios.axapi.utils.ItemBuilder;
import com.artillexstudios.axtrade.history.HistoryCurrency;
import com.artillexstudios.axtrade.history.TradeRecord;
import com.artillexstudios.axtrade.utils.ItemBuilderUtil;
import com.artillexstudios.axtrade.utils.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.artillexstudios.axtrade.AxTrade.CONFIG;
import static com.artillexstudios.axtrade.AxTrade.HISTORY_GUIS;

public class HistoryItems {

    private static final String GIVEN_CURRENCIES = "%given-currencies%";
    private static final String RECEIVED_CURRENCIES = "%received-currencies%";

    public static Map<String, String> getReplacements(TradeRecord record, UUID owner, int index) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("%index%", "" + index);
        replacements.put("%player%", record.getName(owner));
        replacements.put("%partner%", record.getPartnerName(owner));
        replacements.put("%date%", formatDate(record.time()));
        replacements.put("%given-items%", "" + record.getGivenItemCount(owner));
        replacements.put("%received-items%", "" + record.getReceivedItemCount(owner));
        return replacements;
    }

    public static String formatDate(long time) {
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(CONFIG.getString("history.date-format", "dd.MM.yyyy HH:mm"));
        } catch (IllegalArgumentException ex) {
            formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        }
        return formatter.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time));
    }

    public static String replace(String string, Map<String, String> replacements) {
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            string = string.replace(entry.getKey(), entry.getValue());
        }
        return string;
    }

    @Nullable
    public static ItemStack build(Config file, String route, Player viewer, Map<String, String> replacements, @Nullable TradeRecord record, @Nullable UUID owner, @Nullable UUID headOwner) {
        Section section = file.getSection(route);
        if (section == null) return null;
        if (section.getString("material") == null && section.getString("type") == null) return null;

        ItemBuilder builder = ItemBuilderUtil.newBuilder(section, replacements, viewer);
        if (record != null && owner != null) {
            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) builder.setLore(expandCurrencies(lore, record, owner), replacements);
        }

        ItemStack itemStack = builder.get();
        if (headOwner != null && itemStack.getType() == Material.PLAYER_HEAD && section.getString("texture") == null && itemStack.getItemMeta() instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(headOwner));
            itemStack.setItemMeta(skullMeta);
        }
        return itemStack;
    }

    private static List<String> expandCurrencies(List<String> lore, TradeRecord record, UUID owner) {
        return expand(lore, formatCurrencies(record.getGivenCurrencies(owner), true), formatCurrencies(record.getReceivedCurrencies(owner), false));
    }

    private static List<String> expand(List<String> lore, List<String> given, List<String> received) {
        List<String> expanded = new ArrayList<>();
        for (String line : lore) {
            if (line.trim().equals(GIVEN_CURRENCIES)) {
                expanded.addAll(given);
            } else if (line.trim().equals(RECEIVED_CURRENCIES)) {
                expanded.addAll(received);
            } else {
                expanded.add(line.replace(GIVEN_CURRENCIES, String.join(", ", given)).replace(RECEIVED_CURRENCIES, String.join(", ", received)));
            }
        }
        return expanded;
    }

    private static List<String> formatCurrencies(List<HistoryCurrency> currencies, boolean given) {
        List<String> lines = new ArrayList<>();
        String format = HISTORY_GUIS.getString("currency-format", " &7• &f%amount% &#00ffdd%currency%");
        for (HistoryCurrency currency : currencies) {
            lines.add(format
                    .replace("%amount%", NumberUtils.formatNumber(given ? currency.given() : currency.received()))
                    .replace("%currency%", currency.name()));
        }
        if (lines.isEmpty())
            lines.add(HISTORY_GUIS.getString("no-currency-format", " &7• &fNothing"));
        return lines;
    }

    public static List<Integer> getSlots(Config file, String route) {
        List<Integer> slots = new ArrayList<>();
        List<String> list = file.getStringList(route);
        if (list.isEmpty()) {
            if (file.getString(route) != null) slots.add(file.getInt(route));
            return slots;
        }

        // yaml loads entries like "- 13" as integers, so the list can't be iterated as strings
        for (Object object : list) {
            String entry = String.valueOf(object);
            if (com.artillexstudios.axapi.utils.NumberUtils.isInt(entry)) {
                slots.add(Integer.parseInt(entry));
                continue;
            }
            String[] split = entry.split("-");
            int min = Integer.parseInt(split[0]);
            int max = Integer.parseInt(split[1]);
            for (int i = min; i <= max; i++) {
                slots.add(i);
            }
        }
        return slots;
    }

}