package com.archiveaustxn.eco;

import com.archiveaustxn.EconomySMP;
import java.io.File;
import java.text.DecimalFormat;
import java.util.Locale;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Owns economy configuration and the optional Vault provider. */
public final class EconomyModule {
    private final EconomySMP plugin;
    private File configFile;
    private YamlConfiguration config;
    private YamlConfiguration lang;
    private Economy provider;

    public EconomyModule(EconomySMP plugin) { this.plugin = plugin; }

    public void load() {
        File directory = new File(plugin.getDataFolder(), "core/eco");
        directory.mkdirs();
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
        setupProvider();
    }

    public void reload() { load(); }

    private void setupProvider() {
        provider = null;
        if (!"VAULT".equalsIgnoreCase(config.getString("economy.provider", "VAULT"))) {
            plugin.getLogger().warning("Unsupported economy provider; configure VAULT.");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault is not installed; economy commands will report unavailable.");
            return;
        }
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration != null) provider = registration.getProvider();
        if (provider == null) plugin.getLogger().warning("No Vault economy provider is registered.");
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
            matcher.appendReplacement(out, code.toString());
        }
        matcher.appendTail(out);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', out.toString());
    }
    public String messageFor(String key, String fallback) { return color(message(key, fallback)); }
    public String prefix() { return lang.getBoolean("prefix-enable", false) ? color(lang.getString("prefix", "")) : ""; }
    public boolean validAmount(double amount) { return Double.isFinite(amount) && amount > 0 && amount <= 1_000_000_000_000.0; }
}
