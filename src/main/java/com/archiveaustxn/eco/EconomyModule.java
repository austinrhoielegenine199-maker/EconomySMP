package com.archiveaustxn.eco;

import com.archiveaustxn.EconomySMP;
import java.io.File;
import java.text.DecimalFormat;
import java.util.Locale;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;\nimport org.bukkit.plugin.ServicePriority;

/** Owns economy configuration and a Vault provider, falling back to the built-in SQLite economy. */
public final class EconomyModule implements Listener {
    private final EconomySMP plugin;
    private File configFile;
    private YamlConfiguration config;
    private YamlConfiguration lang;
    private volatile Economy provider;
    private boolean providerListenerRegistered;\n    private BuiltInEconomyProvider builtInProvider;\n    private boolean builtInRegistered;

    public EconomyModule(EconomySMP plugin) { this.plugin = plugin; }

    public void load() {
        File directory = new File(plugin.getDataFolder(), "core/eco");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            plugin.getLogger().severe("Could not create economy config directory: " + directory);
        }
        configFile = new File(directory, "eco.yml");
        if (!configFile.exists()) {
            try {
                if (plugin.getResource("core/eco/eco.yml") != null) plugin.saveResource("core/eco/eco.yml", false);
                else configFile.createNewFile();
            } catch (Exception exception) { plugin.getLogger().severe("Could not create eco config: " + exception.getMessage()); }
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        File langFile = new File(directory, "lang.yml");
        if (!langFile.exists()) {
            try {
                if (plugin.getResource("core/eco/lang.yml") != null) plugin.saveResource("core/eco/lang.yml", false);
                else langFile.createNewFile();
            } catch (Exception exception) { plugin.getLogger().severe("Could not create eco language file: " + exception.getMessage()); }
        }
        lang = YamlConfiguration.loadConfiguration(langFile);

        if (!providerListenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            providerListenerRegistered = true;
        }
        setupProvider();
        // Some economy plugins register their Vault service after this plugin's onEnable.
        Bukkit.getScheduler().runTaskLater(plugin, this::setupProvider, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, this::setupProvider, 40L);
    }

    public void reload() { load(); }

    @EventHandler
    public void onEconomyServiceRegistered(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            Bukkit.getScheduler().runTask(plugin, this::setupProvider);
        }
    }

    private void setupProvider() {
        // EconomySMP's provider is the authoritative source so every built-in module
        // uses the same SQLite balances, regardless of other installed economy plugins.
        if (builtInProvider == null) {
            try {
                builtInProvider = new BuiltInEconomyProvider(plugin);
            } catch (RuntimeException exception) {
                provider = null;
                plugin.getLogger().severe("Could not start built-in SQLite economy: " + exception.getMessage());
                return;
            }
        }
        if (!builtInRegistered) {
            Bukkit.getServicesManager().register(Economy.class, builtInProvider, plugin, ServicePriority.Highest);
            builtInRegistered = true;
        }
        boolean newlyAvailable = provider != builtInProvider;
        provider = builtInProvider;
        if (newlyAvailable) plugin.getLogger().info("Built-in SQLite economy provider enabled (shared economy.db).");
    }

