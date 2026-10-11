package com.archiveaustxn.eco.storage;

import com.archiveaustxn.EconomySMP;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Plugin-owned SQLite database. The connection is shared by storage services and closed on disable. */
public final class SQLiteDatabase implements AutoCloseable {
    private final EconomySMP plugin;
    private Connection connection;

    public SQLiteDatabase(EconomySMP plugin) {
        this.plugin = plugin;
    }

    public synchronized void open() throws SQLException {
        if (connection != null && !connection.isClosed()) return;
        File directory = new File(plugin.getDataFolder(), "data");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new SQLException("Could not create database directory: " + directory);
        }
        File databaseFile = new File(directory, "economysmp.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS payment_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sender_uuid TEXT NOT NULL,
                    sender_name TEXT NOT NULL,
                    receiver_uuid TEXT NOT NULL,
                    receiver_name TEXT NOT NULL,
                    amount REAL NOT NULL CHECK(amount > 0),
                    timestamp_ms INTEGER NOT NULL
                )
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_payment_sender_time ON payment_history(sender_uuid, timestamp_ms DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_payment_receiver_time ON payment_history(receiver_uuid, timestamp_ms DESC)");
        }
        plugin.getLogger().info("SQLite database ready: plugins/EconomySMP/data/economysmp.db");
    }

    public synchronized Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) open();
        return connection;
    }

    @Override public synchronized void close() {
        if (connection == null) return;
        try {
            if (!connection.isClosed()) connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not close SQLite database: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }
}
