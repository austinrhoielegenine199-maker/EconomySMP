package com.archiveaustxn.flakes;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

/** PlaceholderAPI expansion for the shared Flakes currency balance. */
public final class FlakesPlaceholderExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final FlakesModule flakes;

    public FlakesPlaceholderExpansion(JavaPlugin plugin, FlakesModule flakes) {
        this.plugin = plugin;
        this.flakes = flakes;
    }

    @Override public String getIdentifier() { return "flakes"; }
    @Override public String getAuthor() { return String.join(", ", plugin.getDescription().getAuthors()); }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || player.getUniqueId() == null) return "";
        double balance = flakes.getBalance(player.getUniqueId());
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "balance", "bal", "amount", "balance_formatted" -> format(balance);
            case "balance_raw", "raw" -> Double.toString(balance);
            case "name" -> player.getName() == null ? "" : player.getName();
            default -> null;
        };
    }

    private String format(double amount) {
        return amount == Math.rint(amount)
                ? Long.toString((long) amount)
                : String.format(java.util.Locale.US, "%.2f", amount);
    }
}
