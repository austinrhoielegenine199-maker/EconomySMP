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
import org.bukkit.plugin.RegisteredServiceProvider;

/** Owns economy configuration and the optional Vault provider. */
public final class EconomyModule implements Listener {
    private final EconomySMP plugin;
    private File configFile;
    private YamlConfiguration config;
    private YamlConfiguration lang;
    private volatile Economy provider;
    private boolean providerListenerRegistered;

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
        if (!"VAULT".equalsIgnoreCase(config.getString("economy.provider", "VAULT"))) {
            provider = null;
            plugin.getLogger().warning("Unsupported economy provider; configure economy.provider: VAULT.");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            provider = null;
            plugin.getLogger().warning("Vault is not installed or has not loaded. Install Vault and an economy provider such as EssentialsX Economy.");
            return;
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        Economy found = registration == null ? null : registration.getProvider();
        if (found == null) {
            provider = null;
            plugin.getLogger().warning("Vault is installed, but no economy plugin has registered a Vault Economy service. Install/enable an economy provider (for example EssentialsX Economy).");
            return;
        }
        boolean newlyAvailable = provider == null;
        provider = found;
        if (newlyAvailable) {
            plugin.getLogger().info("Vault economy hooked successfully: " + found.getName());
        }
    }

    public Economy provider() { return provider; }
    public YamlConfiguration config() { return config; }
    public YamlConfiguration lang() { return lang; }
    public boolean available() { return provider != null; }
    public String message(String key, String fallback) { return lang.getString(key, fallback); }
    public String format(double amount) {
        String pattern = config.getString("economy.currency-format", "#,##0.##");
        String formatted;
        try { formatted = new DecimalFormat(pattern).format(amount); }
        catch (IllegalArgumentException exception) { formatted = new DecimalFormat("#,##0.##").format(amount); }
        if (!config.getBoolean("economy.abbreviations.enabled", true) || Math.abs(amount) < 1000) return formatted;
        String[] suffixes = config.getStringList("economy.abbreviations.formats").toArray(String[]::new);
        if (suffixes.length == 0) suffixes = new String[]{"K","M","B","T"};
        double value = amount;
        int index = -1;
        while (Math.abs(value) >= 1000 && index + 1 < suffixes.length) { value /= 1000; index++; }
        if (index < 0) return formatted;
        return new DecimalFormat("#,##0.##").format(value) + suffixes[index];
    }
    public String color(String input) {
        if (input == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            StringBuilder code = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) code.append('§').append(c);
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(code.toString()));
        }
        matcher.appendTail(out);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', out.toString());
    }
    public String messageFor(String key, String fallback) { return color(message(key, fallback)); }
    public String prefix() { return lang.getBoolean("prefix-enable", false) ? color(lang.getString("prefix", "")) : ""; }
    public boolean validAmount(double amount) { return Double.isFinite(amount) && amount > 0 && amount <= 1_000_000_000_000.0; }
}
