package com.archiveaustxn.eco.storage;

import com.archiveaustxn.EconomySMP;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * SQLite-backed payment history. The JDBC driver is bundled in the plugin jar,
 * so server owners do not need to install SQLite separately.
 */
public final class PaymentHistoryStore implements AutoCloseable {
    private final EconomySMP plugin;
    private final Connection connection;

    public PaymentHistoryStore(EconomySMP plugin) {
        this.plugin = plugin;
        this.connection = plugin.getDatabase().connection();
        try {
            migrateYamlHistory();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not migrate SQLite payment history", exception);
        }
    }

    /** Imports legacy YAML records once; rows are ignored if their IDs already exist. */
    private void migrateYamlHistory() throws SQLException {
        File legacy = new File(plugin.getDataFolder(), "core/eco/payment-history-data.yml");
        if (!legacy.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection transactions = yaml.getConfigurationSection("transactions");
        if (transactions == null) return;
        String sql = "INSERT OR IGNORE INTO payment_history(id,from_uuid,from_name,to_uuid,to_name,amount,timestamp) VALUES(?,?,?,?,?,?,?)";
        connection.setAutoCommit(false);
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (String key : transactions.getKeys(false)) {
                String path = "transactions." + key;
                String fromUuid = yaml.getString(path + ".from-uuid");
                String toUuid = yaml.getString(path + ".to-uuid");
                if (fromUuid == null || toUuid == null) continue;
                insert.setString(1, yaml.getString(path + ".id", key));
                insert.setString(2, fromUuid);
                insert.setString(3, yaml.getString(path + ".from", "?"));
                insert.setString(4, toUuid);
                insert.setString(5, yaml.getString(path + ".to", "?"));
                insert.setDouble(6, yaml.getDouble(path + ".amount"));
                insert.setLong(7, yaml.getLong(path + ".timestamp", System.currentTimeMillis()));
                insert.addBatch();
            }
            insert.executeBatch();
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized void record(UUID from, String fromName, UUID to, String toName, double amount) {
        if (from == null || to == null || !Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Invalid payment history record");
        }
        String id = Long.toString(System.currentTimeMillis()) + "-" + UUID.randomUUID().toString().substring(0, 8);
        String sql = "INSERT INTO payment_history(id,from_uuid,from_name,to_uuid,to_name,amount,timestamp) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, from.toString());
            statement.setString(3, fromName == null ? "?" : fromName);
            statement.setString(4, to.toString());
            statement.setString(5, toName == null ? "?" : toName);
            statement.setDouble(6, amount);
            statement.setLong(7, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not record payment history: " + exception.getMessage());
        }
    }

    public synchronized List<Record> find(UUID player, int pageSize, int page, String sort) {
        int size = Math.max(1, Math.min(100, pageSize));
        int safePage = Math.max(1, page);
        long offset = (long) (safePage - 1) * size;
        String orderBy = switch (sort == null ? "newest" : sort) {
            case "oldest" -> "timestamp ASC, id ASC";
            case "highest_price" -> "amount DESC, timestamp DESC";
            case "lowest_price" -> "amount ASC, timestamp DESC";
            case "name_az" -> "from_name COLLATE NOCASE ASC, to_name COLLATE NOCASE ASC";
            case "name_za" -> "from_name COLLATE NOCASE DESC, to_name COLLATE NOCASE DESC";
            default -> "timestamp DESC, id DESC";
        };
        String sql = "SELECT id,from_name,to_name,amount,timestamp FROM payment_history " +
            "WHERE from_uuid=? OR to_uuid=? ORDER BY " + orderBy + " LIMIT ? OFFSET ?";
        List<Record> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, player.toString());
            statement.setString(2, player.toString());
            statement.setInt(3, size);
            statement.setLong(4, offset);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(new Record(rows.getString("id"), rows.getString("from_name"),
                    rows.getString("to_name"), rows.getDouble("amount"), rows.getLong("timestamp")));
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not query payment history: " + exception.getMessage());
        }
        return List.copyOf(result);
    }

    /** Connection lifecycle belongs to CoreDatabase, shared by every module. */
    @Override public synchronized void close() { }

    public java.io.File getDatabaseFile() { return plugin.getDatabase().file(); }

    public record Record(String id, String from, String to, double amount, long timestamp) {
        public String date() {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(timestamp));
        }
    }
}
