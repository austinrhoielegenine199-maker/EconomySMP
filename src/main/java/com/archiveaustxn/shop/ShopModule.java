package com.archiveaustxn.shop;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShopModule {
    private final EconomySMP plugin;
    private final Map<String, YamlConfiguration> categories = new LinkedHashMap<>();
    private YamlConfiguration shopConfig, mainGui, buyConfig, flakesBuy;
    private ShopListener listener;

    public ShopModule(EconomySMP plugin) { this.plugin = plugin; }

    public void load() {
        File root = new File(plugin.getDataFolder(), "core/shop");
        if (!root.exists()) root.mkdirs();
        shopConfig = loadYaml(root, "shop.yml");
        mainGui = loadYaml(root, "maingui.yml");
        buyConfig = loadYaml(root, "buy.yml");
        flakesBuy = loadYaml(root, "flakesbuy.yml");
        loadIfMissing(root, "flakes.yml");
        categories.clear();
        for (String name : new String[]{"end", "food", "gear", "nether"}) {
            YamlConfiguration yaml = loadYaml(root, name + ".yml");
            categories.put(name, yaml);
        }
        if (listener != null) HandlerListUtil.unregister(listener);
        listener = new ShopListener(plugin, this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    private void loadIfMissing(File root, String name) {
        File file = new File(root, name);
        if (!file.exists()) {
            try { file.createNewFile(); } catch (Exception e) { plugin.getLogger().warning("Could not create " + file); }
        }
    }

    private YamlConfiguration loadYaml(File root, String name) {
        File file = new File(root, name);
        if (!file.exists()) {
            try { file.createNewFile(); } catch (Exception e) { plugin.getLogger().warning("Could not create " + file); }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public void openMain(Player player) {
        int rows = Math.max(1, Math.min(6, mainGui.getInt("main-menu.rows", 3)));
        String title = color(mainGui.getString("main-menu.title", "&8Shop"));
        Inventory inv = Bukkit.createInventory(new ShopHolder("main", 1), rows * 9, title);
        ConfigurationSection section = mainGui.getConfigurationSection("main-menu.categories");
        if (section != null) for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) continue;
            Material material = Material.matchMaterial(item.getString("material", "STONE"));
            if (material == null) material = Material.STONE;
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(item.getString("displayname", key)));
                meta.setLore(item.getStringList("lore").stream().map(ShopModule::color).toList());
                stack.setItemMeta(meta);
            }
            int slot = item.getInt("slot", -1);
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, stack);
        }
        player.openInventory(inv);
    }

    public void openCategory(Player player, String category, int page) {
        YamlConfiguration yaml = categories.get(category.toLowerCase());
        if (yaml == null) { player.sendMessage(color(message("category-unavailable", "&cThis shop is not available!"))); return; }
        int rows = Math.max(1, Math.min(6, yaml.getInt("rows", 3)));
        String title = color(shopConfig.getString("gui-titles." + category, "&8Shop")) .replace("%page%", String.valueOf(page));
        Inventory inv = Bukkit.createInventory(new ShopHolder(category, page), rows * 9, title);
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item == null || item.getInt("page", 1) != page) continue;
            Material material = Material.matchMaterial(item.getString("material", "STONE"));
            if (material == null) continue;
            ItemStack stack = new ItemStack(material, Math.max(1, Math.min(64, item.getInt("amount", 1))));
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(item.getString("displayname", "&f" + key)));
                meta.setLore(java.util.List.of(color(shopConfig.getString("price-lore", "&a%price%").replace("%price%", String.valueOf(item.getDouble("price"))))));
                stack.setItemMeta(meta);
            }
            int slot = item.getInt("slot", -1);
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, stack);
        }
        player.openInventory(inv);
    }

    public YamlConfiguration category(String name) { return categories.get(name); }
    public YamlConfiguration buyConfig() { return buyConfig; }
    public YamlConfiguration flakesBuyConfig() { return flakesBuy; }
    public String message(String key, String fallback) { return shopConfig.getString("messages." + key, fallback); }
    static String color(String text) {
        if (text == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(out, replacement.toString());
        }
        matcher.appendTail(out);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    public void close() {}
}
