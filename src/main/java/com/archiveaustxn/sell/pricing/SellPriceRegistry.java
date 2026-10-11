package com.archiveaustxn.sell.pricing;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Reads prices from the editable core/sell/worth.yml file. */
public final class SellPriceRegistry {
    private final Map<Material, SellPrice> prices = new EnumMap<>(Material.class);

    public void reload(YamlConfiguration config) {
        prices.clear();
        ConfigurationSection section = config.getConfigurationSection("prices");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || material.isAir()) continue;
            double price = section.getDouble(key, -1);
            if (!Double.isFinite(price) || price < 0) continue;
            prices.put(material, new SellPrice(material, price));
        }
    }

    public Optional<SellPrice> find(Material material) {
        return Optional.ofNullable(prices.get(material));
    }

    public boolean isSellable(Material material) {
        return prices.containsKey(material);
    }

    public Map<Material, SellPrice> getAll() {
        return Collections.unmodifiableMap(prices);
    }
}
