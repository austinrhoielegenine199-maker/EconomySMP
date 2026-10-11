package com.archiveaustxn.eco.history;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class PaymentHistoryHolder implements InventoryHolder {
    private final UUID targetId;
    private final String targetName;
    private final int page;
    private final String sort;
    public PaymentHistoryHolder(UUID targetId, String targetName, int page, String sort) {
        this.targetId = targetId; this.targetName = targetName; this.page = page; this.sort = sort;
    }
    public UUID targetId() { return targetId; }
    public String targetName() { return targetName; }
    public int page() { return page; }
    public String sort() { return sort; }
    @Override public Inventory getInventory() { return null; }
}
