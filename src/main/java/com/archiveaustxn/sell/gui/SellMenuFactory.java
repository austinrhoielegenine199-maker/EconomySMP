package com.archiveaustxn.sell.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Creates Sell/Worth inventories from editable GUI YAML configuration. */
public final class SellMenuFactory {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public Inventory createSellMenu(YamlConfiguration config, String playerName,
                                    String progress, String progressBar, String nextMultiplier) {
        String title = color(config.getString("inventory.title", "&8Sell"));
        int rows = Math.max(1, Math.min(6, config.getInt("inventory.rows", 5)));
        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);
        ConfigurationSection categories = config.getConfigurationSection("buttons.categories");
        if (categories == null) return inventory;

        for (String key : categories.getKeys(false)) {
            ConfigurationSection category = categories.getConfigurationSection(key);
            if (category == null) continue;
            int slot = category.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) continue;
            String materialName = category.getString("material", "CHEST");
            Material material = Material.matchMaterial(materialName);
            if (material == null || material.isAir()) material = Material.CHEST;

            String name = category.getString("name", "&f" + key);
            List<String> lore = new ArrayList<>();
            for (String line : category.getStringList("lore")) {
                lore.add(color(line.replace("%player%", playerName)
                    .replace("%progress%", progress)
                    .replace("%progress-bar%", progressBar)
                    .replace("%next_multiplier%", nextMultiplier)));
            }
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(name));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }
        return inventory;
    }

    public Inventory createWorthMenu(YamlConfiguration guiConfig, YamlConfiguration pricesConfig, int page) {
        int rows = Math.max(1, Math.min(6, guiConfig.getInt("worth.rows", 6)));
        Inventory inventory = Bukkit.createInventory(null, rows * 9,
            color(guiConfig.getString("worth.title", "&8Worth")));
        ConfigurationSection prices = pricesConfig.getConfigurationSection("prices");
        if (prices == null) return inventory;

        List<String> keys = new ArrayList<>(prices.getKeys(false));
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        int pageSize = Math.max(1, inventory.getSize() - 9);
        int start = Math.max(0, page - 1) * pageSize;
        for (int i = start; i < Math.min(keys.size(), start + pageSize); i++) {
            String key = keys.get(i);
            Material material = Material.matchMaterial(key);
            if (material == null || material.isAir()) continue;
            double price = prices.getDouble(key);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color("&f" + key));
                meta.setLore(List.of(color("&7Sell price: &a" + price)));
                item.setItemMeta(meta);
            }
            inventory.setItem(i - start, item);
        }
        return inventory;
    }

    private String color(String input) {
        if (input == null) return "";
        Matcher matcher = HEX.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("§x");
            for (char character : matcher.group(1).toCharArray()) replacement.append('§').append(character);
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(output);
        return ChatColor.translateAlternateColorCodes('&', output.toString());
    }
}
