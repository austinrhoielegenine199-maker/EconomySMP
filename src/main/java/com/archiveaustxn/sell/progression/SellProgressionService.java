package com.archiveaustxn.sell.progression;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Config-driven sell level/multiplier calculations using core/sell/level.yml. */
public final class SellProgressionService {
    public static final class Level {
        private final int number;
        private final double required;
        private final double multiplier;
        private Level(int number, double required, double multiplier) {
            this.number = number; this.required = required; this.multiplier = multiplier;
        }
        public int getNumber() { return number; }
        public double getRequired() { return required; }
        public double getMultiplier() { return multiplier; }
    }

    private final List<Level> levels = new ArrayList<>();

    public void reload(YamlConfiguration config) {
        levels.clear();
        ConfigurationSection section = config.getConfigurationSection("levels");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) continue;
            try {
                int number = Integer.parseInt(key);
                double required = row.getDouble("required", 0);
                double multiplier = row.getDouble("multiplier", 1);
                if (required < 0 || !Double.isFinite(required) || multiplier < 0 || !Double.isFinite(multiplier)) continue;
                levels.add(new Level(number, required, multiplier));
            } catch (NumberFormatException ignored) {
                // Ignore non-numeric level keys.
            }
        }
        levels.sort(Comparator.comparingInt(Level::getNumber));
    }

    public Level levelFor(double lifetimeEarnings) {
        Level current = levels.isEmpty() ? new Level(0, 0, 1) : levels.get(0);
        for (Level level : levels) {
            if (lifetimeEarnings < level.getRequired()) break;
            current = level;
        }
        return current;
    }

    public Level nextLevel(double lifetimeEarnings) {
        for (Level level : levels) {
            if (lifetimeEarnings < level.getRequired()) return level;
        }
        return null;
    }

    public double multiplierFor(double lifetimeEarnings) {
        return levelFor(lifetimeEarnings).getMultiplier();
    }

    public List<Level> getLevels() {
        return java.util.Collections.unmodifiableList(levels);
    }
}
