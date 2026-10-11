package com.archiveaustxn.shop.listener;

import com.archiveaustxn.shop.ShopModule;
import com.archiveaustxn.shop.gui.ShopHolder;
import com.archiveaustxn.EconomySMP;
import net.milkbowl.vault.economy.Economy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ShopListener implements Listener {
    private final EconomySMP plugin;
    private final ShopModule shop;
    private final Map<UUID, Purchase> purchases = new HashMap<>();
    private final Set<UUID> awaitingQuantity = new HashSet<>();

    public ShopListener(EconomySMP plugin, ShopModule shop) {
        this.plugin = plugin;
        this.shop = shop;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) return;
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() == null) { event.setCancelled(true); return; }
        if (event.getClickedInventory() != top) { event.setCancelled(event.isShiftClick()); return; }
        event.setCancelled(true);

        if (holder.category().equals("main")) {
            ConfigurationSection configured = shop.mainGuiCategories();
            if (configured == null) return;
            for (String category : configured.getKeys(false)) {
                ConfigurationSection entry = configured.getConfigurationSection(category);
                if (entry != null && entry.getInt("slot", -1) == event.getSlot()) {
                    shop.openCategory(player, category, 1);
                    return;
                }
            }
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
            else if (slot == 23) completePurchase(player, purchase);
            return;
        }

        YamlConfiguration category = shop.category(holder.category());
        if (category == null) return;
        ConfigurationSection items = category.getConfigurationSection("items");
        if (items == null) return;
        ConfigurationSection back = category.getConfigurationSection("back");
        if (back != null && back.getBoolean("enabled", true) && back.getInt("slot", 22) == event.getSlot()) {
            shop.openMain(player);
            return;
        }
        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item == null || item.getInt("page", 1) != holder.page() || item.getInt("slot", -1) != event.getSlot()) continue;
            Material material = Material.matchMaterial(item.getString("material", "STONE"));
            if (material == null) return;
            String name = item.getString("display-name", item.getString("displayname", key));
            Purchase purchase = new Purchase(holder.category(), holder.page(), key, material, name,
                    item.getDouble("price", 0), Math.max(1, item.getInt("amount", 1)));
            purchases.put(player.getUniqueId(), purchase);
            if (material == Material.TOTEM_OF_UNDYING) {
                awaitingQuantity.add(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(ShopModule.color("&eEnter the number of Totems you want to buy in chat, or type &ccancel&e."));
            } else {
                openPurchase(player, purchase);
            }
            return;
        }
    }

    @EventHandler
    public void onQuantityChat(AsyncPlayerChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!awaitingQuantity.contains(id)) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (!awaitingQuantity.remove(id)) return;
            if (input.equalsIgnoreCase("cancel")) {
                purchases.remove(id);
                player.sendMessage(ShopModule.color("&cPurchase cancelled."));
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(input);
                if (amount < 1 || amount > 2304) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                player.sendMessage(ShopModule.color("&cEnter a whole number from 1 to 2304. Click the Totem again to retry."));
                purchases.remove(id);
                return;
            }
            Purchase purchase = purchases.get(id);
            if (purchase == null) return;
            purchase.amount = amount;
            openPurchase(player, purchase);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) { event.setCancelled(true); return; }
        }
    }

    private void completePurchase(Player player, Purchase purchase) {
        Economy economy = plugin.getEconomyModule() == null ? null : plugin.getEconomyModule().provider();
        if (economy == null) {
            player.sendMessage(ShopModule.color(shop.message("economy-unavailable", "&cThe shop economy is unavailable right now.")));
            return;
        }
        if (!Double.isFinite(purchase.price) || purchase.price < 0 || purchase.amount < 1) {
            player.sendMessage(ShopModule.color("&cThis item has an invalid price."));
            return;
        }
        double total = purchase.price * purchase.amount;
        if (!Double.isFinite(total)) { player.sendMessage(ShopModule.color("&cThe total price is too large.")); return; }
        if (!economy.has(player, total)) {
            player.sendMessage(ShopModule.color(shop.message("not-enough-money", "&cYou don't have enough money.")));
            return;
        }

        ItemStack similarItem = createPurchaseItem(purchase, 1);
        int maxStack = purchase.material.getMaxStackSize();
        int free = 0;
        for (ItemStack contents : player.getInventory().getStorageContents()) {
            if (contents == null || contents.getType().isAir()) free += maxStack;
            else if (contents.isSimilar(similarItem) && contents.getAmount() < contents.getMaxStackSize())
                free += contents.getMaxStackSize() - contents.getAmount();
        }
        if (free < purchase.amount) {
            player.sendMessage(ShopModule.color("&4Your inventory is full!"));
            return;
        }

        java.util.List<ItemStack> delivery = new java.util.ArrayList<>();
        int left = purchase.amount;
        while (left > 0) {
            int stackAmount = Math.min(left, purchase.material.getMaxStackSize());
            delivery.add(createPurchaseItem(purchase, stackAmount));
            left -= stackAmount;
        }
        if (!economy.withdrawPlayer(player, total).transactionSuccess()) {
            player.sendMessage(ShopModule.color("&cPayment failed. Please try again."));
            return;
        }
        java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(delivery.toArray(new ItemStack[0]));
        if (!leftovers.isEmpty()) {
            economy.depositPlayer(player, total);
            player.sendMessage(ShopModule.color("&cInventory delivery issue; your payment was refunded."));
            return;
        }
        player.sendMessage(ShopModule.color("&aYou've bought x" + purchase.amount + " "
                + prettyName(purchase.name, purchase.key) + " for $" + String.format(java.util.Locale.US, "%.2f", total) + "!"));
        openPurchase(player, purchase);
    }

    private void changeAmount(Player player, Purchase purchase, int delta) {
        purchase.amount = Math.max(1, Math.min(purchase.material.getMaxStackSize(), purchase.amount + delta));
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (!(inv.getHolder() instanceof ShopHolder holder) || !holder.category().equals("purchase")) {
            openPurchase(player, purchase);
            return;
        }
        ItemStack selected = createPurchaseItem(purchase, Math.min(purchase.material.getMaxStackSize(), purchase.amount));
        ItemMeta meta = selected.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ShopModule.color(displayName(purchase)));
            meta.setLore(java.util.List.of(ShopModule.color("&fAmount: &a" + purchase.amount),
                    ShopModule.color("&fPrice: &a" + (purchase.price * purchase.amount))));
            selected.setItemMeta(meta);
        }
        inv.setItem(13, selected);
    }

    private void openPurchase(Player player, Purchase p) {
        YamlConfiguration cfg = shop.buyConfig();
        int rows = Math.max(1, Math.min(6, cfg.getInt("confirm-menu.rows", 3)));
        Inventory inv = Bukkit.createInventory(new ShopHolder("purchase", p.page), rows * 9,
                ShopModule.color(cfg.getString("confirm-menu.title", "&8Buying")));
        boolean customQuantity = p.material == Material.TOTEM_OF_UNDYING;
        if (!customQuantity) {
            place(inv, 9, Material.RED_STAINED_GLASS_PANE, "&c-64", "Remove 64");
            place(inv, 10, Material.RED_STAINED_GLASS_PANE, "&c-10", "Remove 10");
            place(inv, 11, Material.RED_STAINED_GLASS_PANE, "&c-1", "Remove 1");
            place(inv, 15, Material.LIME_STAINED_GLASS_PANE, "&a+1", "Add 1");
            place(inv, 16, Material.LIME_STAINED_GLASS_PANE, "&a+10", "Add 10");
            place(inv, 17, Material.LIME_STAINED_GLASS_PANE, "&a+64", "Add 64");
        }
        ItemStack selected = createPurchaseItem(p, Math.min(p.material.getMaxStackSize(), p.amount));
        ItemMeta meta = selected.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ShopModule.color(displayName(p)));
            meta.setLore(java.util.List.of(ShopModule.color("&fAmount: &a" + p.amount),
                    ShopModule.color("&fPrice: &a" + String.format(java.util.Locale.US, "%.2f", p.price * p.amount))));
            selected.setItemMeta(meta);
        }
        inv.setItem(13, selected);
        place(inv, 21, Material.RED_STAINED_GLASS_PANE, "&#fc0404ᴄᴀɴᴄᴇʟ", "ᴄʟɪᴄᴋ ᴛᴏ ᴄᴀɴᴄᴇʟ");
        place(inv, 23, Material.LIME_STAINED_GLASS_PANE, "&#04fc04ᴄᴏɴꜰɪʀᴍ", "ᴄʟɪᴄᴋ ᴛᴏ ʙᴜʏ");
        player.openInventory(inv);
    }

    private String displayName(Purchase p) {
        if (p.name != null && !p.name.isBlank() && !p.name.equalsIgnoreCase(p.key)) return p.name;
        String pretty = prettyName(p.name, p.key);
        if (p.material == Material.TOTEM_OF_UNDYING) return "&#FFD700" + pretty;
        return "&f" + pretty;
    }

    private ItemStack createPurchaseItem(Purchase p, int amount) {
        ItemStack stack = new ItemStack(p.material, Math.max(1, Math.min(p.material.getMaxStackSize(), amount)));
        String name = displayName(p);
        if (name != null && !name.isBlank()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) { meta.setDisplayName(ShopModule.color(name)); stack.setItemMeta(meta); }
        }
        return stack;
    }

    private String prettyName(String displayName, String key) {
        String raw = displayName == null || displayName.isBlank() || displayName.equalsIgnoreCase(key)
                ? key : org.bukkit.ChatColor.stripColor(ShopModule.color(displayName));
        String cleaned = raw.replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
        java.util.StringJoiner words = new java.util.StringJoiner(" ");
        for (String word : cleaned.split(" ")) if (!word.isEmpty())
            words.add(word.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + word.substring(1).toLowerCase(java.util.Locale.ROOT));
        return words.toString();
    }

    private void place(Inventory inv, int slot, Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(ShopModule.color(name)); meta.setLore(java.util.List.of(ShopModule.color("&f" + lore))); item.setItemMeta(meta); }
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
