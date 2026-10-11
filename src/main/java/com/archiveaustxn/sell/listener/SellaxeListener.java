package com.archiveaustxn.sell.listener;

import com.archiveaustxn.sell.config.SellConfig;
import com.archiveaustxn.sell.service.SellPriceService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/** Optional Sellaxe support; enabled and tuned through Sellaxe.yml. */
public final class SellaxeListener implements Listener {
    private final SellConfig config;
    private final SellPriceService prices;
    public SellaxeListener(SellConfig config, SellPriceService prices){this.config=config;this.prices=prices;}
    @EventHandler public void onBreak(BlockBreakEvent event) {
        Player p=event.getPlayer(); ItemStack tool=p.getInventory().getItemInMainHand();
        ConfigurationSection y=config.get("Sellaxe.yml");
        if(!y.getBoolean("enabled",false))return;
        String required=y.getString("tool.material","DIAMOND_AXE");
        if(tool.getType()!=Material.matchMaterial(required))return;
        if(!tool.hasItemMeta()||!tool.getItemMeta().hasDisplayName())return;
        String expected=y.getString("tool.displayname","&aSellaxe");
        if(!org.bukkit.ChatColor.stripColor(tool.getItemMeta().getDisplayName()).equals(org.bukkit.ChatColor.stripColor(expected.replace('&','§'))))return;
        if(!prices.isSellable(new ItemStack(event.getBlock().getType())))return;
        // Actual block selling is deliberately opt-in: never destroy blocks for an unconfigured tool.
        if(!y.getBoolean("sell-blocks",false))return;
        double price=prices.unitPrice(event.getBlock().getType());
        if(price<=0)return;
        event.setDropItems(false);
        p.getInventory().addItem(new ItemStack(event.getBlock().getType()));
        p.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',y.getString("messages.block-collected","&aCollected a sellable block worth $"+price)));
    }
}
