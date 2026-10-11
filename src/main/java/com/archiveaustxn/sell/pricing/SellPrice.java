package com.archiveaustxn.sell.pricing;

import org.bukkit.Material;

/** Immutable price entry for one material. */
public final class SellPrice {
    private final Material material;
    private final double unitPrice;

    public SellPrice(Material material, double unitPrice) {
        if (material == null || material.isAir()) throw new IllegalArgumentException("Material must be sellable");
        if (!Double.isFinite(unitPrice) || unitPrice < 0) throw new IllegalArgumentException("Price must be finite and non-negative");
        this.material = material;
        this.unitPrice = unitPrice;
    }

    public Material getMaterial() { return material; }
    public double getUnitPrice() { return unitPrice; }

    public double valueOf(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative");
        return unitPrice * amount;
    }
}
