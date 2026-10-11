package com.archiveaustxn.shop.listener;

import com.archiveaustxn.shop.ShopModule;
import com.archiveaustxn.shop.command.ShopCommand;
import com.archiveaustxn.shop.gui.ShopHolder;

import com.archiveaustxn.EconomySMP;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ShopListener implements Listener {
    private final EconomySMP plugin;
    private final ShopModule shop;
    private final Map<UUID, Purchase> purchases = new HashMap<>();

    public ShopListener(EconomySMP plugin, ShopModule shop) {
        this.plugin = plugin;
        this.shop = shop;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (holder.category().equals("main")) {
            int slot = event.getSlot();
            String category = switch (slot) {
                case 11 -> "end";
                case 12 -> "nether";
                case 13 -> "gear";
                case 14 -> "food";
                default -> null;
            };
            if (category != null) shop.openCategory(player, category, 1);
            return;
        }

        if (holder.category().equals("purchase")) {
            Purchase purchase = purchases.get(player.getUniqueId());
            if (purchase == null) return;
            int slot = event.getSlot();
            if (slot == 21) {
                purchases.remove(player.getUniqueId());
                shop.openCategory(player, purchase.category, purchase.page);
            } else if (slot == 15) changeAmount(player, purchase, 1);
            else if (slot == 16) changeAmount(player, purchase, 10);
            else if (slot == 17) changeAmount(player, purchase, 64);
            else if (slot == 11) changeAmount(player, purchase, -1);
            else if (slot == 10) changeAmount(player, purchase, -10);
            else if (slot == 9) changeAmount(player, purchase, -64);
            else if (slot == 23) {
                player.sendMessage(ShopCommand.color("&cPurchase processing will be enabled with the economy integration."));
            }
            return;
        }

        YamlConfiguration category = shop.category(holder.category());
        if (category == null) return;
        ConfigurationSection items = category.getConfigurationSection("items");
        if (items == null) return;
        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item != null && item.getInt("page", 1) == holder.page() && item.getInt("slot", -1) == event.getSlot()) {
                Material material = Material.matchMaterial(item.getString("material", "STONE"));
                if (material == null) return;
                String name = item.getString("displayname", key);
                double price = item.getDouble("price", 0);
                purchases.put(player.getUniqueId(), new Purchase(holder.category(), holder.page(), key, material, name, price, Math.max(1, item.getInt("amount", 1))));
                openPurchase(player, purchases.get(player.getUniqueId()));
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) event.setCancelled(true);
    }

    private void changeAmount(Player player, Purchase purchase, int delta) {
        purchase.amount = Math.max(1, Math.min(2304, purchase.amount + delta));
        openPurchase(player, purchase);
    }

    private void openPurchase(Player player, Purchase p) {
        YamlConfiguration cfg = shop.buyConfig();
        int rows = Math.max(1, Math.min(6, cfg.getInt("confirm-menu.rows", 3)));
        Inventory inv = Bukkit.createInventory(new ShopHolder("purchase", p.page), rows * 9,
            ShopModule.color(cfg.getString("confirm-menu.title", "&8Buying")));
        place(inv, 9, Material.RED_STAINED_GLASS_PANE, "&c-64", "Remove 64");
        place(inv, 10, Material.RED_STAINED_GLASS_PANE, "&c-10", "Remove 10");
        place(inv, 11, Material.RED_STAINED_GLASS_PANE, "&c-1", "Remove 1");
        ItemStack selected = new ItemStack(p.material, Math.max(1, Math.min(64, p.amount)));
        ItemMeta meta = selected.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ShopModule.color(p.name));
            meta.setLore(java.util.List.of(ShopModule.color("&fAmount: &a" + p.amount), ShopModule.color("&fPrice: &a" + (p.price * p.amount))));
            selected.setItemMeta(meta);
        }
        inv.setItem(13, selected);
        place(inv, 15, Material.LIME_STAINED_GLASS_PANE, "&a+1", "Add 1");
        place(inv, 16, Material.LIME_STAINED_GLASS_PANE, "&a+10", "Add 10");
        place(inv, 17, Material.LIME_STAINED_GLASS_PANE, "&a+64", "Add 64");
        place(inv, 21, Material.RED_STAINED_GLASS_PANE, "§x§f§c§0§4§0§4ᴄᴀɴᴄᴇʟ", "Return to category");
        place(inv, 23, Material.LIME_STAINED_GLASS_PANE, "§x§0§4§f§c§0§4ᴄᴏɴғɪʀᴍ", "Confirm purchase");
        player.openInventory(inv);
    }

    private void place(Inventory inv, int slot, Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ShopModule.color(name));
            meta.setLore(java.util.List.of(ShopModule.color("&f" + lore)));
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private static final class Purchase {
        final String category, key, name;
        final int page;
        final Material material;
        final double price;
        int amount;
        Purchase(String category, int page, String key, Material material, String name, double price, int amount) {
            this.category=category; this.page=page; this.key=key; this.material=material; this.name=name; this.price=price; this.amount=amount;
        }
    }
}
