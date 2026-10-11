package com.archiveaustxn.sell.config;

import com.archiveaustxn.EconomySMP;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/** Owns the Sell module's editable YAML resources and reload lifecycle. */
public final class SellConfig {
    private final EconomySMP plugin;
    private final Map<String, YamlConfiguration> loaded = new LinkedHashMap<>();
    public SellConfig(EconomySMP plugin) { this.plugin = plugin; }

    public void loadAll() {
        loaded.clear();
        load("sell.yml", "core/sell/sell.yml");
        load("prices.yml", "core/sell/prices.yml");
        load("lang.yml", "core/sell/lang.yml");
        load("level.yml", "core/sell/level.yml");
        load("gui/sell.gui.yml", "core/sell/gui/sell.gui.yml");
        load("gui/sell.history.yml", "core/sell/gui/sell.history.yml");
        load("gui/worth.yml", "core/sell/gui/worth.yml");
        load("gui/progress.gui.yml", "core/sell/gui/progress.gui.yml");
        load("gui/progress.item.list.yml", "core/sell/gui/progress.item.list.yml");
        load("sellaxe.yml", "core/sell/sellaxe.yml");
    }
    private void load(String key, String resource) {
        File file = new File(plugin.getDataFolder(), "core/sell/" + key);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                if (plugin.getResource(resource) != null) plugin.saveResource(resource, false);
                else { file.createNewFile(); plugin.getLogger().warning("Created empty Sell config: " + file); }
            } catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "Could not create " + file, ex); }
        }
        loaded.put(key.toLowerCase(java.util.Locale.ROOT), YamlConfiguration.loadConfiguration(file));
    }
    public YamlConfiguration get(String key) {
        return loaded.getOrDefault(key.toLowerCase(java.util.Locale.ROOT), new YamlConfiguration());
    }
    public void reload() { loadAll(); }
    public File file(String key) { return new File(plugin.getDataFolder(), "core/sell/" + key); }
}
