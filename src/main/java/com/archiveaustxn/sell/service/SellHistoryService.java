package com.archiveaustxn.sell.service;

import com.archiveaustxn.EconomySMP;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistent sell ledger stored in the shared EconomySMP SQLite database. */
public final class SellHistoryService {
    public record Entry(Instant time, UUID playerId, String playerName, String material, int amount, double total) {}
    private final EconomySMP plugin;

    public SellHistoryService(EconomySMP plugin) { this.plugin = plugin; }

    public void record(UUID id, String name, String material, int amount, double total) {
        if (id == null || material == null || amount <= 0 || !Double.isFinite(total) || total < 0) return;
        String sql = "INSERT INTO sell_history(player_uuid,player_name,item_key,amount,total,timestamp) VALUES(?,?,?,?,?,?)";
        synchronized (plugin.getDatabase().connection()) {
            try (PreparedStatement statement = plugin.getDatabase().connection().prepareStatement(sql)) {
                statement.setString(1, id.toString());
                statement.setString(2, name == null ? "?" : name);
                statement.setString(3, material);
                statement.setInt(4, amount);
                statement.setDouble(5, total);
                statement.setLong(6, Instant.now().toEpochMilli());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not record sell history: " + exception.getMessage());
            }
        }
    }

    public List<Entry> recent(UUID id, int limit) {
        if (id == null || limit <= 0) return List.of();
        String sql = "SELECT player_uuid,player_name,item_key,amount,total,timestamp FROM sell_history WHERE player_uuid=? ORDER BY timestamp DESC,id DESC LIMIT ?";
        List<Entry> result = new ArrayList<>();
        synchronized (plugin.getDatabase().connection()) {
            try (PreparedStatement statement = plugin.getDatabase().connection().prepareStatement(sql)) {
                statement.setString(1, id.toString());
                statement.setInt(2, Math.min(5000, limit));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.add(new Entry(Instant.ofEpochMilli(rows.getLong("timestamp")),
                        UUID.fromString(rows.getString("player_uuid")), rows.getString("player_name"),
                        rows.getString("item_key"), rows.getInt("amount"), rows.getDouble("total")));
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not read sell history: " + exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    public double totalEarned(UUID id) { return recent(id, 5000).stream().mapToDouble(Entry::total).sum(); }
    public int totalItems(UUID id) { return recent(id, 5000).stream().mapToInt(Entry::amount).sum(); }
}
