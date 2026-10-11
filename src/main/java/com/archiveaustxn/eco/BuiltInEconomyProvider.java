package com.archiveaustxn.eco;

import com.archiveaustxn.EconomySMP;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Built-in Vault-compatible economy backed by EconomySMP's shared SQLite database. */
public final class BuiltInEconomyProvider extends AbstractEconomy {
    private final EconomySMP plugin;
    private final Connection db;

    public BuiltInEconomyProvider(EconomySMP plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabase().connection();
        try (var statement = db.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS economy_balances (
                  player_uuid TEXT PRIMARY KEY,
                  player_name TEXT NOT NULL,
                  balance REAL NOT NULL DEFAULT 0 CHECK(balance >= 0),
                  updated_at INTEGER NOT NULL
                )
                """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_economy_name ON economy_balances(player_name COLLATE NOCASE)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize built-in economy tables", exception);
        }
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "EconomySMP"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double amount) { return plugin.getEconomyModule().format(amount); }
    @Override public String currencyNamePlural() { return plugin.getEconomyModule().config().getString("economy.currency-name-plural", "Flakes"); }
    @Override public String currencyNameSingular() { return plugin.getEconomyModule().config().getString("economy.currency-name-singular", "Flake"); }

    private UUID uuid(String name) {
        if (name == null || name.isBlank()) return null;
        OfflinePlayer online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        try (PreparedStatement ps = db.prepareStatement("SELECT player_uuid FROM economy_balances WHERE player_name=? COLLATE NOCASE LIMIT 1")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return UUID.fromString(rs.getString(1));
            }
        } catch (SQLException ignored) { }
        OfflinePlayer known = Bukkit.getOfflinePlayerIfCached(name);
        return known == null ? null : known.getUniqueId();
    }

    private boolean ensure(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) return false;
        return ensure(player.getUniqueId(), player.getName() == null ? player.getUniqueId().toString() : player.getName());
    }
    private boolean ensure(UUID id, String name) {
        try (PreparedStatement ps = db.prepareStatement("""
            INSERT INTO economy_balances(player_uuid,player_name,balance,updated_at) VALUES(?,?,0,?)
            ON CONFLICT(player_uuid) DO UPDATE SET player_name=excluded.player_name, updated_at=excluded.updated_at
            """)) {
            ps.setString(1, id.toString()); ps.setString(2, name); ps.setLong(3, System.currentTimeMillis()); ps.executeUpdate(); return true;
        } catch (SQLException exception) { plugin.getLogger().warning("SQLite account initialization failed: " + exception.getMessage()); return false; }
    }
    private double balance(UUID id) {
        if (id == null) return 0;
        try (PreparedStatement ps = db.prepareStatement("SELECT balance FROM economy_balances WHERE player_uuid=?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0; }
        } catch (SQLException exception) { plugin.getLogger().warning("SQLite balance query failed: " + exception.getMessage()); return 0; }
    }
    private EconomyResponse change(OfflinePlayer player, double amount, boolean deposit) {
        if (player == null || !Double.isFinite(amount) || amount < 0) return response(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid player or amount");
        synchronized (db) {
            if (!ensure(player)) return response(0, 0, EconomyResponse.ResponseType.FAILURE, "Could not create account");
            double old = balance(player.getUniqueId());
            if (!deposit && old < amount) return response(old, 0, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
            double next = deposit ? old + amount : old - amount;
            if (!Double.isFinite(next) || next < 0) return response(old, 0, EconomyResponse.ResponseType.FAILURE, "Balance limit exceeded");
            try (PreparedStatement ps = db.prepareStatement("UPDATE economy_balances SET balance=?,player_name=?,updated_at=? WHERE player_uuid=?")) {
                ps.setDouble(1, next); ps.setString(2, player.getName() == null ? player.getUniqueId().toString() : player.getName());
                ps.setLong(3, System.currentTimeMillis()); ps.setString(4, player.getUniqueId().toString());
                ps.executeUpdate(); return response(next, amount, EconomyResponse.ResponseType.SUCCESS, null);
            } catch (SQLException exception) { return response(old, 0, EconomyResponse.ResponseType.FAILURE, exception.getMessage()); }
        }
    }
    private EconomyResponse response(double balance, double amount, EconomyResponse.ResponseType type, String error) {
        return new EconomyResponse(amount, balance, type, error);
    }

    @Override public boolean hasAccount(String name) { UUID id = uuid(name); return id != null && hasAccount(Bukkit.getOfflinePlayer(id)); }
    @Override public boolean hasAccount(String name, String world) { return hasAccount(name); }
    @Override public double getBalance(String name) { return balance(uuid(name)); }
    @Override public double getBalance(String name, String world) { return getBalance(name); }
    @Override public boolean has(String name, double amount) { return Double.isFinite(amount) && amount >= 0 && getBalance(name) >= amount; }
    @Override public boolean has(String name, String world, double amount) { return has(name, amount); }
    @Override public EconomyResponse withdrawPlayer(String name, double amount) { UUID id = uuid(name); return id == null ? response(0,0,EconomyResponse.ResponseType.FAILURE,"Unknown account") : change(Bukkit.getOfflinePlayer(id),amount,false); }
    @Override public EconomyResponse withdrawPlayer(String name, String world, double amount) { return withdrawPlayer(name, amount); }
    @Override public EconomyResponse depositPlayer(String name, double amount) { UUID id = uuid(name); if (id == null) { id = Bukkit.getOfflinePlayer(name).getUniqueId(); } return change(Bukkit.getOfflinePlayer(id),amount,true); }
    @Override public EconomyResponse depositPlayer(String name, String world, double amount) { return depositPlayer(name, amount); }
    @Override public boolean createPlayerAccount(String name) { if (name == null || name.isBlank()) return false; OfflinePlayer p = Bukkit.getOfflinePlayer(name); return ensure(p); }
    @Override public boolean createPlayerAccount(String name, String world) { return createPlayerAccount(name); }

    @Override public EconomyResponse createBank(String name, String player) { return response(0,0,EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Bank accounts are not supported"); }
    @Override public EconomyResponse deleteBank(String name) { return response(0,0,EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Bank accounts are not supported"); }
    @Override public EconomyResponse bankBalance(String name) { return response(0,0,EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Bank accounts are not supported"); }
    @Override public EconomyResponse bankHas(String name, double amount) { return response(0,0,EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Bank accounts are not supported"); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return response(0,0,EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Bank accounts are not supported"); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return response(0,0,EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Bank accounts are not supported"); }
    @Override public EconomyResponse isBankOwner(String name, String player) { return response(0,0,EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Bank accounts are not supported"); }
    @Override public EconomyResponse isBankMember(String name, String player) { return response(0,0,EconomyResponse.ResponseType.NOT_IMPLEMENTED,"Bank accounts are not supported"); }
    @Override public List<String> getBanks() { return List.of(); }
}
