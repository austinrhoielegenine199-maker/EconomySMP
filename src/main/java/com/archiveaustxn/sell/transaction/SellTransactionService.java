package com.archiveaustxn.sell.transaction;

import com.archiveaustxn.sell.pricing.SellPrice;
import com.archiveaustxn.sell.pricing.SellPriceRegistry;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.LinkedHashMap;
import java.util.Map;

/** Calculates a sell-all transaction, deposits the money, then removes sold items. */
public final class SellTransactionService {
    public static final class Result {
        private final boolean success;
        private final int itemCount;
        private final double total;
        private final String reason;

        private Result(boolean success, int itemCount, double total, String reason) {
            this.success = success;
            this.itemCount = itemCount;
            this.total = total;
            this.reason = reason;
        }

        public boolean isSuccess() { return success; }
        public int getItemCount() { return itemCount; }
        public double getTotal() { return total; }
        public String getReason() { return reason; }
    }

    private final Economy economy;
    private final SellPriceRegistry prices;

    public SellTransactionService(Economy economy, SellPriceRegistry prices) {
        this.economy = economy;
        this.prices = prices;
    }

    public Result sellAll(Player player) {
        if (economy == null) return new Result(false, 0, 0, "economy-unavailable");
        Map<Integer, Integer> removals = new LinkedHashMap<>();
        double total = 0;
        int count = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) continue;
            SellPrice price = prices.find(stack.getType()).orElse(null);
            if (price == null || price.getUnitPrice() <= 0) continue;

            double value = price.valueOf(stack.getAmount());
            if (!Double.isFinite(value) || !Double.isFinite(total + value)) {
                return new Result(false, 0, 0, "invalid-total");
            }
            total += value;
            count += stack.getAmount();
            removals.put(slot, stack.getAmount());
        }

        if (count == 0) return new Result(false, 0, 0, "nothing-to-sell");
        EconomyResponse response = economy.depositPlayer(player, total);
        if (response == null || !response.transactionSuccess()) {
            return new Result(false, 0, 0, "deposit-failed");
        }

        for (Map.Entry<Integer, Integer> entry : removals.entrySet()) {
            ItemStack current = player.getInventory().getItem(entry.getKey());
            if (current == null || current.getType() == Material.AIR) continue;
            int remaining = current.getAmount() - entry.getValue();
            if (remaining <= 0) player.getInventory().setItem(entry.getKey(), null);
            else current.setAmount(remaining);
        }
        return new Result(true, count, total, "success");
    }
}
