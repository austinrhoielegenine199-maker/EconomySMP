package com.archiveaustxn.storage;

import com.archiveaustxn.EconomySMP;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One shared SQLite database for all EconomySMP modules.
 * Feature tables share the same economy.db file and connection.
 */
public final class CoreDatabase implements AutoCloseable {
    private final EconomySMP plugin;
    private final File file;
    private Connection connection;

    public CoreDatabase(EconomySMP plugin) {
        this.plugin = plugin;
        File directory = new File(plugin.getDataFolder(), "database");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create database directory: " + directory);
        }
        file = new File(directory, "economysmp.db");
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS payment_history (
                      id TEXT PRIMARY KEY, from_uuid TEXT NOT NULL, from_name TEXT NOT NULL,
                      to_uuid TEXT NOT NULL, to_name TEXT NOT NULL,
                      amount REAL NOT NULL CHECK(amount >= 0), timestamp INTEGER NOT NULL
                    )
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_payment_from_time ON payment_history(from_uuid, timestamp DESC)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_payment_to_time ON payment_history(to_uuid, timestamp DESC)");
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS sell_history (
                      id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL,
                      player_name TEXT NOT NULL, item_key TEXT NOT NULL, amount INTEGER NOT NULL,
                      total REAL NOT NULL, timestamp INTEGER NOT NULL
                    )
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_sell_player_time ON sell_history(player_uuid, timestamp DESC)");
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS sell_prices (
                      item_key TEXT PRIMARY KEY, unit_price REAL NOT NULL CHECK(unit_price >= 0),
                      updated_by TEXT, updated_at INTEGER NOT NULL
                    )
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS shop_items (
                      item_key TEXT PRIMARY KEY, category TEXT NOT NULL, material TEXT NOT NULL,
                      price REAL NOT NULL CHECK(price >= 0), amount INTEGER NOT NULL DEFAULT 1,
                      page INTEGER NOT NULL DEFAULT 1, slot INTEGER NOT NULL DEFAULT 0,
                      updated_at INTEGER NOT NULL
                    )
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_preferences (
                      player_uuid TEXT NOT NULL, preference_key TEXT NOT NULL, preference_value TEXT NOT NULL,
                      updated_at INTEGER NOT NULL, PRIMARY KEY(player_uuid, preference_key)
                    )
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_data (
                      player_uuid TEXT NOT NULL, data_key TEXT NOT NULL, data_value TEXT NOT NULL,
                      updated_at INTEGER NOT NULL, PRIMARY KEY(player_uuid, data_key)
                    )
                    """);
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS rtp_history (
                      id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL,
                      world_name TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
                      timestamp INTEGER NOT NULL
                    )
                    """);
                statement.execute("CREATE INDEX IF NOT EXISTS idx_rtp_player_time ON rtp_history(player_uuid, timestamp DESC)");
            }
            plugin.getLogger().info("Shared SQLite database ready: " + file.getName());
        } catch (Exception exception) {
            close();
            throw new IllegalStateException("Could not initialize EconomySMP SQLite database", exception);
        }
    }

    public synchronized Connection connection() {
        if (connection == null) throw new IllegalStateException("SQLite database is closed");
        return connection;
    }

    public File file() { return file; }

    @Override public synchronized void close() {
        if (connection == null) return;
        try { connection.close(); }
        catch (SQLException exception) {
            plugin.getLogger().warning("Could not close SQLite database cleanly: " + exception.getMessage());
        } finally { connection = null; }
    }
}
