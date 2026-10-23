package com.artillexstudios.axtrade.history;

import com.artillexstudios.axapi.database.DatabaseConfig;
import com.artillexstudios.axapi.database.DatabaseHandler;
import com.artillexstudios.axapi.database.impl.MySQLDatabaseType;
import com.artillexstudios.axapi.items.WrappedItemStack;
import com.artillexstudios.axapi.utils.StringUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.artillexstudios.axtrade.AxTrade.CONFIG;

public class TradeHistoryDatabase {

    private static final String SUMMARY_COLUMNS = "t.id, t.time, t.player1_uuid, t.player1_name, t.player2_uuid, t.player2_name, t.player1_item_count, t.player2_item_count, t.player1_currencies, t.player2_currencies";
    private static DatabaseHandler handler = null;
    private static ExecutorService executor = null;
    private static String trades;
    private static String players;

    public static void start() {
        if (!CONFIG.getBoolean("history.enabled", true)) return;

        DatabaseConfig config = new DatabaseConfig();
        config.type = new MySQLDatabaseType();
        config.address = CONFIG.getString("history.database.address", "127.0.0.1");
        config.port = CONFIG.getInt("history.database.port", 3306);
        config.database = CONFIG.getString("history.database.database", "axtrade");
        config.username = CONFIG.getString("history.database.username", "root");
        config.password = CONFIG.getString("history.database.password", "");
        config.pool.maximumPoolSize = Math.max(1, CONFIG.getInt("history.database.pool.maximum-pool-size", 5));
        config.pool.minimumIdle = CONFIG.getInt("history.database.pool.minimum-idle", 2);
        config.pool.maximumLifetime = CONFIG.getInt("history.database.pool.maximum-lifetime", 1800000);
        config.pool.keepaliveTime = CONFIG.getInt("history.database.pool.keepalive-time", 0);
        config.pool.connectionTimeout = CONFIG.getInt("history.database.pool.connection-timeout", 5000);
        String prefix = CONFIG.getString("history.database.table-prefix", "axtrade_");
        config.tablePrefix(prefix);
        trades = prefix + "trades";
        players = prefix + "trade_players";

        try {
            handler = new DatabaseHandler(config);
            createTables();
        } catch (Exception ex) {
            Throwable cause = ex;
            while (cause.getCause() != null) cause = cause.getCause();
            Bukkit.getConsoleSender().sendMessage(StringUtils.formatToString("&#FF3333[AxTrade] Failed to connect to the trade history database (" + config.address + ":" + config.port + "/" + config.database + "), trade history is disabled! Cause: " + cause));
            ex.printStackTrace();
            if (handler != null)
                handler.close();
            handler = null;
            return;
        }

        executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "AxTrade-History-thread"));
    }

    public static void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            executor = null;
        }
        if (handler != null) {
            handler.close();
            handler = null;
        }
    }

    public static boolean isEnabled() {
        return handler != null && executor != null;
    }

    public static void submit(Runnable runnable) {
        if (!isEnabled()) return;
        executor.execute(() -> {
            try {
                runnable.run();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private static void createTables() throws SQLException {
        try (Connection connection = handler.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + trades + "` (" +
                    "`id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "`time` BIGINT NOT NULL, " +
                    "`player1_uuid` CHAR(36) NOT NULL, " +
                    "`player1_name` VARCHAR(32) NOT NULL, " +
                    "`player2_uuid` CHAR(36) NOT NULL, " +
                    "`player2_name` VARCHAR(32) NOT NULL, " +
                    "`player1_item_count` INT NOT NULL, " +
                    "`player2_item_count` INT NOT NULL, " +
                    "`player1_currencies` TEXT NOT NULL, " +
                    "`player2_currencies` TEXT NOT NULL, " +
                    "`player1_items` MEDIUMBLOB NOT NULL, " +
                    "`player2_items` MEDIUMBLOB NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + players + "` (" +
                    "`player_uuid` CHAR(36) NOT NULL, " +
                    "`player_name` VARCHAR(32) NOT NULL, " +
                    "`trade_id` BIGINT NOT NULL, " +
                    "PRIMARY KEY (`player_uuid`, `trade_id`), " +
                    "INDEX (`player_name`), " +
                    "INDEX (`trade_id`))");
        }
    }

    public static void save(TradeRecord record) {
        if (!isEnabled()) return;
        byte[] player1Items = serializeItems(record.player1Items());
        byte[] player2Items = serializeItems(record.player2Items());
        String player1Currencies = serializeCurrencies(record.player1Currencies());
        String player2Currencies = serializeCurrencies(record.player2Currencies());

        submit(() -> {
            try {
                insert(record, player1Items, player2Items, player1Currencies, player2Currencies);
                trim(record.player1Uuid());
                if (!record.player1Uuid().equals(record.player2Uuid())) trim(record.player2Uuid());
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void insert(TradeRecord record, byte[] player1Items, byte[] player2Items, String player1Currencies, String player2Currencies) throws SQLException {
        try (Connection connection = handler.connection()) {
            connection.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `" + trades + "` (`time`, `player1_uuid`, `player1_name`, `player2_uuid`, `player2_name`, `player1_item_count`, `player2_item_count`, `player1_currencies`, `player2_currencies`, `player1_items`, `player2_items`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, record.time());
                    statement.setString(2, record.player1Uuid().toString());
                    statement.setString(3, record.player1Name());
                    statement.setString(4, record.player2Uuid().toString());
                    statement.setString(5, record.player2Name());
                    statement.setInt(6, record.player1ItemCount());
                    statement.setInt(7, record.player2ItemCount());
                    statement.setString(8, player1Currencies);
                    statement.setString(9, player2Currencies);
                    statement.setBytes(10, player1Items);
                    statement.setBytes(11, player2Items);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO `" + players + "` (`player_uuid`, `player_name`, `trade_id`) VALUES (?, ?, ?)")) {
                    statement.setString(1, record.player1Uuid().toString());
                    statement.setString(2, record.player1Name());
                    statement.setLong(3, id);
                    statement.addBatch();
                    statement.setString(1, record.player2Uuid().toString());
                    statement.setString(2, record.player2Name());
                    statement.setLong(3, id);
                    statement.addBatch();
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void trim(UUID uuid) throws SQLException {
        int max = Math.max(1, CONFIG.getInt("history.max-per-player", 500));
        try (Connection connection = handler.connection()) {
            long cutoff;
            try (PreparedStatement statement = connection.prepareStatement("SELECT `trade_id` FROM `" + players + "` WHERE `player_uuid` = ? ORDER BY `trade_id` DESC LIMIT 1 OFFSET ?")) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, max);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) return;
                    cutoff = resultSet.getLong(1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM `" + players + "` WHERE `player_uuid` = ? AND `trade_id` <= ?")) {
                statement.setString(1, uuid.toString());
                statement.setLong(2, cutoff);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM `" + trades + "` WHERE `id` <= ? AND NOT EXISTS (SELECT 1 FROM `" + players + "` p WHERE p.`trade_id` = `" + trades + "`.`id`)")) {
                statement.setLong(1, cutoff);
                statement.executeUpdate();
            }
        }
    }

    @Nullable
    public static HistoryPlayer findPlayer(String name) throws SQLException {
        try (Connection connection = handler.connection(); PreparedStatement statement = connection.prepareStatement("SELECT `player_uuid`, `player_name` FROM `" + players + "` WHERE `player_name` = ? ORDER BY `trade_id` DESC LIMIT 1")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                return new HistoryPlayer(UUID.fromString(resultSet.getString(1)), resultSet.getString(2));
            }
        }
    }

    public static int count(UUID uuid) throws SQLException {
        int max = Math.max(1, CONFIG.getInt("history.max-per-player", 500));
        try (Connection connection = handler.connection(); PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM `" + players + "` WHERE `player_uuid` = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return Math.min(max, resultSet.getInt(1));
            }
        }
    }

    public static List<TradeRecord> list(UUID uuid) throws SQLException {
        int max = Math.max(1, CONFIG.getInt("history.max-per-player", 500));
        try (Connection connection = handler.connection(); PreparedStatement statement = connection.prepareStatement("SELECT " + SUMMARY_COLUMNS + " FROM `" + players + "` p JOIN `" + trades + "` t ON t.`id` = p.`trade_id` WHERE p.`player_uuid` = ? ORDER BY p.`trade_id` DESC LIMIT ?")) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, max);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TradeRecord> records = new ArrayList<>();
                while (resultSet.next()) records.add(read(resultSet, false));
                return records;
            }
        }
    }

    @Nullable
    public static TradeRecord get(UUID uuid, int index) throws SQLException {
        int max = Math.max(1, CONFIG.getInt("history.max-per-player", 500));
        if (index < 1 || index > max) return null;
        try (Connection connection = handler.connection(); PreparedStatement statement = connection.prepareStatement("SELECT " + SUMMARY_COLUMNS + ", t.player1_items, t.player2_items FROM `" + players + "` p JOIN `" + trades + "` t ON t.`id` = p.`trade_id` WHERE p.`player_uuid` = ? ORDER BY p.`trade_id` DESC LIMIT 1 OFFSET ?")) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, index - 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                return read(resultSet, true);
            }
        }
    }

    private static TradeRecord read(ResultSet resultSet, boolean withItems) throws SQLException {
        return new TradeRecord(
                resultSet.getLong("id"),
                resultSet.getLong("time"),
                UUID.fromString(resultSet.getString("player1_uuid")),
                resultSet.getString("player1_name"),
                UUID.fromString(resultSet.getString("player2_uuid")),
                resultSet.getString("player2_name"),
                resultSet.getInt("player1_item_count"),
                resultSet.getInt("player2_item_count"),
                deserializeCurrencies(resultSet.getString("player1_currencies")),
                deserializeCurrencies(resultSet.getString("player2_currencies")),
                withItems ? deserializeItems(resultSet.getBytes("player1_items")) : null,
                withItems ? deserializeItems(resultSet.getBytes("player2_items")) : null
        );
    }

    private static byte[] serializeItems(@Nullable List<ItemStack> items) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(byteStream)) {
            List<byte[]> serialized = new ArrayList<>();
            if (items != null) {
                for (ItemStack item : items) {
                    if (item == null || item.getType().isAir()) continue;
                    try {
                        serialized.add(WrappedItemStack.wrap(item).serialize());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
            output.writeInt(serialized.size());
            for (byte[] bytes : serialized) {
                output.writeInt(bytes.length);
                output.write(bytes);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return byteStream.toByteArray();
    }

    private static List<ItemStack> deserializeItems(byte[] bytes) {
        List<ItemStack> items = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int size = input.readInt();
            for (int i = 0; i < size; i++) {
                byte[] read = new byte[input.readInt()];
                input.readFully(read);
                try {
                    items.add(WrappedItemStack.wrap(read).toBukkit());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return items;
    }

    private static String serializeCurrencies(List<HistoryCurrency> currencies) {
        JsonArray array = new JsonArray();
        for (HistoryCurrency currency : currencies) {
            JsonObject object = new JsonObject();
            object.addProperty("name", currency.name());
            object.addProperty("given", currency.given());
            object.addProperty("received", currency.received());
            array.add(object);
        }
        return array.toString();
    }

    private static List<HistoryCurrency> deserializeCurrencies(String json) {
        List<HistoryCurrency> currencies = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject object = element.getAsJsonObject();
            currencies.add(new HistoryCurrency(object.get("name").getAsString(), object.get("given").getAsDouble(), object.get("received").getAsDouble()));
        }
        return currencies;
    }

}