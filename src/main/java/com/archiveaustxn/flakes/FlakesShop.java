package com.archiveaustxn.flakes;

import com.archiveaustxn.shop.ShopModule;
import java.io.File;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Flakes-currency shop driven by core/shop/flakes.yml. */
public final class FlakesShop implements Listener {
    private final JavaPlugin plugin;
    private final FlakesModule flakes;
    private YamlConfiguration config;

    public FlakesShop(JavaPlugin plugin, FlakesModule flakes) {
        this.plugin = plugin;
        this.flakes = flakes;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "core/shop/flakes.yml");
        if (!file.exists()) plugin.saveResource("core/shop/flakes.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void open(Player player) {
        if (!config.getBoolean("flakes-enabled", true)) {
            player.sendMessage(ShopModule.color("&cThe Flakes shop is disabled."));
            return;
        }
        ConfigurationSection menu = config.getConfigurationSection("shop-menu");
        int rows = Math.max(1, Math.min(6, menu == null ? 3 : menu.getInt("rows", 3)));
        String title = menu == null ? "&#009bffFlakes Shop" : menu.getString("title", "&#009bffFlakes Shop");
        Inventory inv = Bukkit.createInventory(new Holder(), rows * 9, ShopModule.color(title));
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item == null) continue;
            Material material = Material.matchMaterial(item.getString("material", "STONE"));
            int slot = item.getInt("slot", -1);
            if (material == null || slot < 0 || slot >= inv.getSize()) continue;
            ItemStack stack = new ItemStack(material, Math.max(1, Math.min(64, item.getInt("amount", 1))));
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ShopModule.color(item.getString("display-name", "&f" + pretty(key))));
                meta.setLore(java.util.List.of(ShopModule.color("&fPrice: &#009bff" + format(item.getDouble("price", 0)) + " Flakes")));
                stack.setItemMeta(meta);
            }
            inv.setItem(slot, stack);
        }
        int backSlot = menu == null ? 18 : menu.getInt("back-button-slot", 18);
        if (backSlot >= 0 && backSlot < inv.getSize()) {
            ItemStack back = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = back.getItemMeta();
            if (meta != null) { meta.setDisplayName(ShopModule.color("&cBack")); back.setItemMeta(meta); }
            inv.setItem(backSlot, back);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getSlot();
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) return;
        ConfigurationSection menu = config.getConfigurationSection("shop-menu");
        if (menu != null && slot == menu.getInt("back-button-slot", 18)) {
            player.closeInventory();
            player.performCommand("shop");
            return;
        }
        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item == null || item.getInt("slot", -1) != slot) continue;
            double price = item.getDouble("price", -1);
            int amount = Math.max(1, item.getInt("amount", 1));
            if (!Double.isFinite(price) || price < 0) {
                player.sendMessage(ShopModule.color("&cThis shop item has an invalid price."));
                return;
            }
            double total = price * amount;
            if (!Double.isFinite(total)) return;
            if (flakes.getBalance(player.getUniqueId()) < total) {
                player.sendMessage(ShopModule.color("&cYou don't have enough Flakes."));
                return;
            }
            String command = item.getString("give-command", "").trim();
            if (command.isEmpty()) {
                player.sendMessage(ShopModule.color("&cThis item has no delivery command configured."));
                return;
            }
            flakes.setBalance(player.getUniqueId(), flakes.getBalance(player.getUniqueId()) - total);
            String dispatch = command.replace("%player%", player.getName())
                .replace("%amount%", Integer.toString(amount));
            boolean success;
            try {
                success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), dispatch);
            } catch (RuntimeException ex) {
                success = false;
                plugin.getLogger().warning("Flakes shop command failed for item " + key + ": " + ex.getMessage());
            }
            if (!success) {
                flakes.setBalance(player.getUniqueId(), flakes.getBalance(player.getUniqueId()) + total);
                player.sendMessage(ShopModule.color("&cPurchase failed; your Flakes were refunded."));
            } else {
                player.sendMessage(ShopModule.color("&aPurchased " + pretty(key) + " for &#009bff" + format(total) + " Flakes&a."));
            }
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private String pretty(String key) {
        String raw = key.replace('-', ' ').replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : raw.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.US, "%.2f", value);
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
