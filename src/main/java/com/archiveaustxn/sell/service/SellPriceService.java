package com.archiveaustxn.sell.service;

import com.archiveaustxn.sell.config.SellConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Centralized price lookup and valuation; prices are configured in worth.yml. */
public final class SellPriceService {
    private final SellConfig config;
    public SellPriceService(SellConfig config) { this.config = config; }
    public double unitPrice(Material material) {
        ConfigurationSection prices = config.get("worth.yml").getConfigurationSection("prices");
        if (prices == null || material == null || !prices.contains(material.name())) return 0.0;
        double value = prices.getDouble(material.name(), 0.0);
        return Double.isFinite(value) && value > 0 ? value : 0.0;
    }
    public double worth(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return 0.0;
        return unitPrice(stack.getType()) * stack.getAmount();
    }
    public Map<Material, Double> allPrices() {
        Map<Material, Double> result = new LinkedHashMap<>();
        ConfigurationSection prices = config.get("worth.yml").getConfigurationSection("prices");
        if (prices == null) return result;
        for (String key : prices.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            double price = prices.getDouble(key, 0.0);
            if (material != null && Double.isFinite(price) && price > 0) result.put(material, price);
        }
        return Collections.unmodifiableMap(result);
    }
    public boolean isSellable(ItemStack stack) { return unitPrice(stack == null ? null : stack.getType()) > 0; }
}
