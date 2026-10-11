package com.archiveaustxn.sell.listener;

import com.archiveaustxn.EconomySMP;
import com.archiveaustxn.sell.config.SellConfig;
import com.archiveaustxn.sell.service.SellPriceService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/** Sellaxe: prevents block breaking and sells priced contents from a chest on left-click. */
public final class SellaxeListener implements Listener {
    private final EconomySMP plugin;
    private final SellConfig config;
    private final SellPriceService prices;

    public SellaxeListener(EconomySMP plugin, SellConfig config, SellPriceService prices) {
        this.plugin = plugin;
        this.config = config;
        this.prices = prices;
    }

    private ConfigurationSection settings() { return config.get("Sellaxe.yml"); }

    private boolean isSellaxe(Player player) {
        ConfigurationSection y = settings();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir() || !tool.hasItemMeta()) return false;
        ItemMeta meta = tool.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String expected = y.getString("displayname", "&bSellaxe");
        String actualName = ChatColor.stripColor(meta.getDisplayName());
        String expectedName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', expected));
        if (!actualName.equals(expectedName)) return false;
        if (y.contains("custom-model-data") && (!meta.hasCustomModelData()
                || meta.getCustomModelData() != y.getInt("custom-model-data"))) return false;
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isSellaxe(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChestClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (!isSellaxe(player)) return;
        Material type = event.getClickedBlock().getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) return;

        BlockState state = event.getClickedBlock().getState();
        if (!(state instanceof Chest chest)) return;
        Inventory inventory = chest.getBlockInventory();
        if (chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            inventory = doubleChest.getInventory();
        }

        Economy economy = plugin.getEconomyModule() == null ? null : plugin.getEconomyModule().provider();
        if (economy == null) {
            player.sendMessage(color(settings().getString("messages.economy-unavailable", "&cEconomy is unavailable.")));
            return;
        }

        double total = 0.0;
        int soldItems = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) continue;
            Sale sale = valueAndRemove(stack);
            if (sale.value() > 0) {
                total += sale.value();
                soldItems += sale.amount();
                contents[slot] = sale.remaining();
            }
        }
        if (soldItems == 0 || total <= 0 || !Double.isFinite(total)) {
            player.sendMessage(color(settings().getString("messages.nothing-to-sell", "&eNo items with a configured price were found.")));
            return;
        }
        if (!economy.depositPlayer(player, total).transactionSuccess()) {
            player.sendMessage(color(settings().getString("messages.transaction-failed", "&cPayment failed; chest contents were not changed.")));
            return;
        }
        inventory.setContents(contents);
        player.sendMessage(color("&a+" + String.format(Locale.US, "%.2f", total)));
    }

    private Sale valueAndRemove(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return new Sale(0, 0, stack);
        if (stack.getItemMeta() instanceof BlockStateMeta blockMeta
                && blockMeta.getBlockState() instanceof ShulkerBox shulker) {
            Inventory inner = shulker.getInventory();
            ItemStack[] nested = inner.getContents();
            double nestedTotal = 0;
            int nestedAmount = 0;
            for (int i = 0; i < nested.length; i++) {
                Sale child = valueAndRemove(nested[i]);
                nestedTotal += child.value();
                nestedAmount += child.amount();
                nested[i] = child.remaining();
            }
            if (nestedTotal > 0) {
                inner.setContents(nested);
                blockMeta.setBlockState(shulker);
                stack.setItemMeta(blockMeta);
                return new Sale(nestedTotal, nestedAmount, stack);
            }
        }
        double unit = prices.unitPrice(stack.getType());
        if (!Double.isFinite(unit) || unit <= 0) return new Sale(0, 0, stack);
        return new Sale(unit * stack.getAmount(), stack.getAmount(), null);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private record Sale(double value, int amount, ItemStack remaining) {}
}
