package com.archiveaustxn.shop.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ShopHolder implements InventoryHolder {
    private final String category;
    private final int page;
    public ShopHolder(String category, int page) { this.category = category; this.page = page; }
    public String category() { return category; }
    public int page() { return page; }
    @Override public Inventory getInventory() { return null; }
}
