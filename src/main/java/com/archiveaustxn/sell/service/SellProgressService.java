package com.archiveaustxn.sell.service;

import com.archiveaustxn.sell.config.SellConfig;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks session progression. Persistence can be added without changing GUI/command code. */
public final class SellProgressService {
    public record Level(int number, double required, double multiplier) {}
    private final SellConfig config;
    private final Map<UUID, Double> totals = new ConcurrentHashMap<>();
    public SellProgressService(SellConfig config) { this.config = config; }
    public void add(UUID player, double amount) { if (amount > 0 && Double.isFinite(amount)) totals.merge(player, amount, Double::sum); }
    public double total(UUID player) { return totals.getOrDefault(player, 0.0); }
    public List<Level> levels() {
        List<Level> result = new ArrayList<>();
        ConfigurationSection section = config.get("level.yml").getConfigurationSection("levels");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            try {
                int number = Integer.parseInt(key);
                ConfigurationSection level = section.getConfigurationSection(key);
                if (level == null) continue;
                double required = level.getDouble("required", 0);
                double multiplier = level.getDouble("multiplier", 1);
                if (required >= 0 && Double.isFinite(required) && multiplier > 0 && Double.isFinite(multiplier))
                    result.add(new Level(number, required, multiplier));
            } catch (NumberFormatException ignored) { }
        }
        result.sort(Comparator.comparingInt(Level::number));
        return result;
    }
    public Level current(UUID player) {
        Level current = new Level(0, 0, 1);
        for (Level level : levels()) if (total(player) >= level.required()) current = level;
        return current;
    }
    public Level next(UUID player) {
        double total = total(player);
        for (Level level : levels()) if (total < level.required()) return level;
        return null;
    }
    public int progressPercent(UUID player) {
        Level next = next(player);
        if (next == null || next.required() <= 0) return 100;
        Level current = current(player);
        double start = current.required();
        return (int)Math.max(0, Math.min(100, (total(player) - start) / (next.required() - start) * 100));
    }
}
