package com.archiveaustxn.sell.listener;

import com.archiveaustxn.sell.SellModule;
import com.archiveaustxn.sell.gui.SellGuiManager;
import com.archiveaustxn.sell.gui.SellGuiManager.Menu;
import com.archiveaustxn.sell.gui.SellGuiManager.MenuTag;
import com.archiveaustxn.sell.service.SellHistoryService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Owns click and drag handling for Sell-module inventories.
 * All custom menus cancel interactions before dispatching a supported action,
 * preventing players from taking menu icons or moving items into the menu.
 */
public final class SellMenuListener implements Listener {
    private final SellGuiManager gui;
    private final SellModule module;
    private final SellHistoryService history;

    public SellMenuListener(SellGuiManager gui, SellModule module, SellHistoryService history) {
        this.gui = gui;
        this.module = module;
        this.history = history;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuTag tag)) return;

        // Cancel shift-clicks and clicks in the player's own inventory too.
        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        int slot = event.getRawSlot();
        if (tag.menu() == Menu.SELL && event.getClickedInventory() == top && slot >= 0 && slot < 36) {
            // Slots 0-35 are a real sell container; category buttons remain protected.
            event.setCancelled(false);
            return;
        }
        if (event.getClickedInventory() != top) return;

        switch (tag.menu()) {
            case SELL -> {
                if (slot >= 36 && slot <= 44) {
                    String[] categories = {"crops", "ores", "mobs", "natural", "tools", "fish", "books", "potions", "blocks"};
                    int index = slot - 36;
                    if (index < categories.length) gui.openProgress(player, categories[index]);
                }
            }
            case PROGRESS -> {
                if (slot == 45) {
                    gui.openSell(player);
                } else if (tag.category() != null && slot >= 0 && slot < top.getSize()) {
                    gui.openCategory(player, tag.category(), 0);
                }
            }
            case CATEGORY -> {
                if (slot == 45) gui.openSell(player);
                else if (slot == 48) gui.openCategory(player, tag.category(), Math.max(0, tag.page() - 1));
                else if (slot == 50) gui.openCategory(player, tag.category(), tag.page() + 1);
            }
            case WORTH -> {
                if (slot == 45) gui.openWorth(player, Math.max(0, tag.page() - 1));
                else if (slot == 53) gui.openWorth(player, tag.page() + 1);
            }
            case HISTORY -> {
                ConfigurationSection navigation = module.getConfigService()
                        .get("gui/Sell.history.yml").getConfigurationSection("source-item.navigation");
                int previous = navigation == null ? 45 : navigation.getInt("previous-page-slot", 45);
                int next = navigation == null ? 53 : navigation.getInt("next-page-slot", 53);
                if (slot == previous) module.openHistory(player, Math.max(0, tag.page() - 1));
                else if (slot == next) module.openHistory(player, tag.page() + 1);
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuTag tag)) return;
        if (tag.menu() == Menu.SELL) {
            // Allow drag/drop only when every touched top slot is in the 36-slot sell area.
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getView().getTopInventory().getSize() && (rawSlot < 0 || rawSlot >= 36)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        event.setCancelled(true);
    }
}
