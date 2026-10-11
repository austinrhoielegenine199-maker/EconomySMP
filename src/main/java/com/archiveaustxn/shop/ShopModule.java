package com.archiveaustxn.shop;

import com.archiveaustxn.EconomySMP;
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
import com.archiveaustxn.shop.gui.ShopHolder;
import com.archiveaustxn.shop.listener.ShopListener;

public final class ShopModule {
    private final EconomySMP plugin;
    private final Map<String, YamlConfiguration> categories = new LinkedHashMap<>();
    private YamlConfiguration shopConfig, mainGui, buyConfig, flakesBuy;
    private ShopListener listener;

    public ShopModule(EconomySMP plugin) { this.plugin = plugin; }

    public void load() {
        File root = new File(plugin.getDataFolder(), "core/shop");
        File gui = new File(root, "gui");
        File categoryDir = new File(root, "categories");
        gui.mkdirs();
        categoryDir.mkdirs();
        shopConfig = loadYaml(root, "shop.yml", "core/shop/shop.yml");
        mainGui = loadYaml(gui, "maingui.yml", "core/shop/gui/maingui.yml");
        buyConfig = loadYaml(gui, "buy.yml", "core/shop/gui/buy.yml");
        flakesBuy = loadYaml(gui, "flakesbuy.yml", "core/shop/gui/flakesbuy.yml");
        loadYaml(root, "flakes.yml", "core/shop/flakes.yml");
        categories.clear();
        ConfigurationSection configured = mainGui.getConfigurationSection("main-menu.categories");
        if (configured != null) {
            for (String key : configured.getKeys(false)) {
                ConfigurationSection entry = configured.getConfigurationSection(key);
                if (entry == null) continue;
                String fileType = entry.getString("file-type", key + ".yml");
                if (!fileType.matches("[A-Za-z0-9_-]+\\.yml")) {
                    plugin.getLogger().warning("Ignoring unsafe category file-type for " + key);
                    continue;
                }
                categories.put(key.toLowerCase(), loadYaml(categoryDir, fileType, "core/shop/categories/" + fileType));
            }
        }
        if (listener != null) HandlerListUtil.unregister(listener);
        listener = new ShopListener(plugin, this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    private YamlConfiguration loadYaml(File directory, String fileName, String resourcePath) {
        File file = new File(directory, fileName);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                if (plugin.getResource(resourcePath) != null) plugin.saveResource(resourcePath, false);
                else file.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("Unable to initialize " + file + ": " + e.getMessage());
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public void openMain(Player player) {
        int rows = Math.max(1, Math.min(6, mainGui.getInt("main-menu.rows", 3)));
        Inventory inv = Bukkit.createInventory(new ShopHolder("main", 1), rows * 9, color(mainGui.getString("main-menu.title", "&8Shop")));
        ConfigurationSection section = mainGui.getConfigurationSection("main-menu.categories");
        if (section != null) for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) continue;
            Material material = Material.matchMaterial(item.getString("material", "STONE"));
            if (material == null) continue;
            ItemStack stack = new ItemStack(material);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(item.getString("display-name", item.getString("displayname", key))));
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
        String title = color(shopConfig.getString("gui-titles." + category, "&8Shop %page%")).replace("%page%", String.valueOf(page));
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
                meta.setDisplayName(color(item.getString("display-name", item.getString("displayname", "&f" + prettyName(key)))));
                meta.setLore(java.util.List.of(color(shopConfig.getString("price-lore", "&a%price%").replace("%price%", String.valueOf(item.getDouble("price"))))));
                stack.setItemMeta(meta);
            }
            int slot = item.getInt("slot", -1);
            if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, stack);
        }
        ConfigurationSection back = yaml.getConfigurationSection("back");
        if (back != null && back.getBoolean("enabled", true)) {
            int slot = back.getInt("slot", 22);
            Material material = Material.matchMaterial(back.getString("material", "RED_STAINED_GLASS_PANE"));
            if (material != null && slot >= 0 && slot < inv.getSize()) {
                ItemStack button = new ItemStack(material);
                ItemMeta meta = button.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(color(back.getString("display-name", "&#FF0000ʙᴀᴄᴋ")));
                    meta.setLore(back.getStringList("lore").stream().map(ShopModule::color).toList());
                    button.setItemMeta(meta);
                }
                inv.setItem(slot, button);
            }
        }
        player.openInventory(inv);
    }

    private String prettyName(String raw) {
        if (raw == null || raw.isBlank()) return "Item";
        String cleaned = raw.replace('_', ' ').replace('-', ' ').trim().replaceAll("\\s+", " ");
        java.util.StringJoiner words = new java.util.StringJoiner(" ");
        for (String word : cleaned.split(" ")) {
            if (word.isEmpty()) continue;
            words.add(word.substring(0, 1).toUpperCase(java.util.Locale.ROOT)
                    + word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return words.toString();
    }

    /**
     * Adds the player's held item to an existing category file.
     * All user-provided values are validated before touching the YAML file.
     */
    public boolean addHandItem(Player player, String categoryName, int slot, double price) {
        if (player == null || categoryName == null || !categoryName.matches("[A-Za-z0-9_-]{1,32}")) return false;
        if (!Double.isFinite(price) || price < 0 || slot < 0 || slot > 53) return false;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return false;
        YamlConfiguration yaml = categories.get(categoryName.toLowerCase(java.util.Locale.ROOT));
        if (yaml == null) return false;
        String itemKey = hand.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items == null) items = yaml.createSection("items");
        String uniqueKey = itemKey;
        int suffix = 2;
        while (items.contains(uniqueKey)) uniqueKey = itemKey + "-" + suffix++;
        ConfigurationSection entry = items.createSection(uniqueKey);
        entry.set("material", hand.getType().name());
        entry.set("price", price);
        entry.set("page", 1);
        entry.set("slot", slot);
        entry.set("amount", hand.getAmount());
        if (hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
            entry.set("display-name", hand.getItemMeta().getDisplayName());
        }
        if (hand.hasItemMeta() && hand.getItemMeta().hasLore()) {
            entry.set("item-lore", hand.getItemMeta().getLore());
        }
        File file = new File(new File(plugin.getDataFolder(), "core/shop/categories"), categoryName.toLowerCase(java.util.Locale.ROOT) + ".yml");
        try {
            yaml.save(file);
            categories.put(categoryName.toLowerCase(java.util.Locale.ROOT), YamlConfiguration.loadConfiguration(file));
            return true;
        } catch (java.io.IOException exception) {
            plugin.getLogger().severe("Failed saving category " + categoryName + ": " + exception.getMessage());
            return false;
        }
    }

    public java.util.Set<String> categoryNames() {
        return java.util.Collections.unmodifiableSet(categories.keySet());
    }

    public ConfigurationSection mainGuiCategories() { return mainGui.getConfigurationSection("main-menu.categories"); }

    public YamlConfiguration category(String name) { return categories.get(name.toLowerCase(java.util.Locale.ROOT)); }
    public YamlConfiguration buyConfig() { return buyConfig; }
    public YamlConfiguration flakesBuyConfig() { return flakesBuy; }
    public String message(String key, String fallback) { return shopConfig.getString("messages." + key, fallback); }
    public static String color(String text) {
        if (text == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(out, replacement.toString());
        }
        matcher.appendTail(out);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', out.toString());
    }
    public void close() {}
}
