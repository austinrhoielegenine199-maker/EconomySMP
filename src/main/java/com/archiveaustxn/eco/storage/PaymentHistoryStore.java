package com.archiveaustxn.eco.storage;

import com.archiveaustxn.EconomySMP;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** SQLite-backed payment history. */
public final class PaymentHistoryStore {
    private final EconomySMP plugin;
    private final SQLiteDatabase database;

    public PaymentHistoryStore(EconomySMP plugin, SQLiteDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    public synchronized void record(UUID from, String fromName, UUID to, String toName, double amount) {
        if (from == null || to == null || !Double.isFinite(amount) || amount <= 0) return;
        String sql = "INSERT INTO payment_history(sender_uuid,sender_name,receiver_uuid,receiver_name,amount,timestamp_ms) VALUES(?,?,?,?,?,?)";
        try {
            Connection connection = database.connection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, from.toString());
                statement.setString(2, fromName == null ? "?" : fromName);
                statement.setString(3, to.toString());
                statement.setString(4, toName == null ? "?" : toName);
                statement.setDouble(5, amount);
                statement.setLong(6, Instant.now().toEpochMilli());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not record payment history: " + exception.getMessage());
        }
    }

    public synchronized List<Record> find(UUID player, int pageSize, int page, String sort) {
        if (player == null) return List.of();
        List<Record> result = new ArrayList<>();
        String sql = "SELECT id,sender_name,receiver_name,amount,timestamp_ms FROM payment_history " +
            "WHERE sender_uuid=? OR receiver_uuid=?";
        try {
            Connection connection = database.connection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, player.toString());
                statement.setString(2, player.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(new Record(String.valueOf(rows.getLong("id")),
                        rows.getString("sender_name"), rows.getString("receiver_name"),
                        rows.getDouble("amount"), rows.getLong("timestamp_ms")));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not read payment history: " + exception.getMessage());
            return List.of();
        }
        Comparator<Record> comparator = switch (sort == null ? "newest" : sort) {
            case "oldest" -> Comparator.comparingLong(Record::timestamp);
            case "highest_price" -> Comparator.comparingDouble(Record::amount).reversed();
            case "lowest_price" -> Comparator.comparingDouble(Record::amount);
            case "name_az" -> Comparator.comparing(Record::from, String.CASE_INSENSITIVE_ORDER).thenComparing(Record::to, String.CASE_INSENSITIVE_ORDER);
            case "name_za" -> Comparator.comparing(Record::from, String.CASE_INSENSITIVE_ORDER.reversed()).thenComparing(Record::to, String.CASE_INSENSITIVE_ORDER.reversed());
            default -> Comparator.comparingLong(Record::timestamp).reversed();
        };
        result.sort(comparator);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int safePage = Math.max(1, page);
        long offsetLong = (long) (safePage - 1) * safePageSize;
        if (offsetLong >= result.size()) return List.of();
        int start = (int) offsetLong;
        return List.copyOf(result.subList(start, Math.min(result.size(), start + safePageSize)));
    }

    public record Record(String id, String from, String to, double amount, long timestamp) {
        public String date() {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(timestamp));
        }
    }
}
