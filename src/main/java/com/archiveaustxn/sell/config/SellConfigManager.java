package com.archiveaustxn.sell.config;

import com.archiveaustxn.EconomySMP;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owns editable Sell YAML files and safely reloads them. */
public final class SellConfigManager {
    private final EconomySMP plugin;
    private final Map<String, YamlConfiguration> loaded = new LinkedHashMap<>();

    public SellConfigManager(EconomySMP plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        loaded.clear();
        load("sell", "core/sell/sell.yml");
        load("worth", "core/sell/worth.yml");
        load("lang", "core/sell/lang.yml");
        load("level", "core/sell/level.yml");
        load("sell-gui", "core/sell/gui/Sell.gui.yml");
        load("history-gui", "core/sell/gui/Sell.history.yml");
        load("worth-gui", "core/sell/gui/Worth.yml");
        load("progress-gui", "core/sell/gui/Progress.gui.yml");
        load("progress-items", "core/sell/gui/Progress.item.list.yml");
    }

    private void load(String key, String relativePath) {
        File file = new File(plugin.getDataFolder(), relativePath);
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create directory " + parent);
            }
            String resource = relativePath.replace(File.separatorChar, '/');
            try {
                if (plugin.getResource(resource) != null) {
                    plugin.saveResource(resource, false);
                } else if (!file.createNewFile()) {
                    plugin.getLogger().warning("Could not create " + file);
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not create " + file + ": " + exception.getMessage());
            }
        }
        loaded.put(key, YamlConfiguration.loadConfiguration(file));
    }

    public YamlConfiguration get(String key) {
        return loaded.computeIfAbsent(key, ignored -> new YamlConfiguration());
    }

    public void save(String key, String relativePath) throws IOException {
        File file = new File(plugin.getDataFolder(), relativePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        get(key).save(file);
    }
}
