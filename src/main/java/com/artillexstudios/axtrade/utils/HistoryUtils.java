package com.artillexstudios.axtrade.utils;

import com.artillexstudios.axapi.utils.file.FileUtils;
import com.artillexstudios.axtrade.AxTrade;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.artillexstudios.axtrade.AxTrade.CONFIG;

public class HistoryUtils {
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Path logsDir;

    static {
        logsDir = FileUtils.getInstance().getFolder().toPath().resolve("logs");
        try {
            Files.createDirectories(logsDir);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void writeToHistory(String text) {
        if (CONFIG.getBoolean("logging.console", true)) {
            AxTrade.getInstance().getLogger().info(text);
        }
        ZonedDateTime now = ZonedDateTime.now();
        Path logFile = logsDir.resolve("%s.log".formatted(now.format(FILE_FORMAT)));
        String line = "[%s] %s%s".formatted(now.format(TIME_FORMAT), text, System.lineSeparator());
        try {
            Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Describes an item for the logs: amount, name, material, tracked item data and contents.
     * Call this before handing the item over, as adding it to an inventory can change its amount.
     */
    public static String describeItem(ItemStack item) {
        StringBuilder text = new StringBuilder("%dx %s (%s)".formatted(item.getAmount(), Utils.getFormattedItemName(item), item.getType()));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            List<String> tracked = CONFIG.getStringList("logging.pdc-keys");
            for (NamespacedKey key : container.getKeys()) {
                if (!tracked.contains(key.getKey())) continue;
                String value = container.has(key, PersistentDataType.STRING) ? container.get(key, PersistentDataType.STRING) : "?";
                text.append(" {").append(key.getKey()).append('=').append(value).append('}');
            }
        }

        List<String> contents = new ArrayList<>();
        for (ItemStack inner : ShulkerUtils.getStorageContents(item, false)) {
            if (inner == null || inner == item || inner.getType().isAir()) continue;
            contents.add(describeItem(inner));
        }
        if (!contents.isEmpty()) text.append(" <contains: ").append(String.join(", ", contents)).append('>');
        return text.toString();
    }
}
