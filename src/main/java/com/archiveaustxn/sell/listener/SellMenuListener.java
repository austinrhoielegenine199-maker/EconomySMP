package com.archiveaustxn.sell.listener;

import com.archiveaustxn.sell.gui.SellGuiManager;
import com.archiveaustxn.sell.gui.SellGuiManager.Menu;
import com.archiveaustxn.sell.gui.SellGuiManager.MenuTag;
import com.archiveaustxn.sell.service.SellHistoryService;
import com.archiveaustxn.sell.SellModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Prevents GUI item movement and delegates click actions by typed inventory holder. */
public final class SellMenuListener implements Listener {
    private final SellGuiManager gui;
    private final SellModule module;
    private final SellHistoryService history;
    public SellMenuListener(SellGuiManager gui,SellModule module,SellHistoryService history){this.gui=gui;this.module=module;this.history=history;}
    @EventHandler public void click(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!(e.getView().getTopInventory().getHolder() instanceof MenuTag tag))return;
        if(e.getClickedInventory()==null)return;
        if(e.getClickedInventory()!=e.getView().getTopInventory()) { e.setCancelled(true); return; }
        e.setCancelled(true); int slot=e.getRawSlot();
        switch(tag.menu()){
            case SELL -> {
                if(slot>=36&&slot<=44){
                    String[] cats={"crops","ores","mobs","natural","tools","fish","books","potions","blocks"};
                    int i=slot-36;if(i>=0&&i<cats.length)gui.openProgress(p,cats[i]);
                }
            }
            case PROGRESS -> {
                if(slot==45){gui.openSell(p);return;}
                if(tag.category()!=null)gui.openCategory(p,tag.category(),0);
            }
            case CATEGORY -> { if(slot==45)gui.openSell(p); else if(slot==48)gui.openCategory(p,tag.category(),Math.max(0,tag.page()-1)); else if(slot==50)gui.openCategory(p,tag.category(),tag.page()+1); }
            case WORTH -> { if(slot==45)gui.openWorth(p,Math.max(0,tag.page()-1));else if(slot==53)gui.openWorth(p,tag.page()+1); }
            case HISTORY -> module.openHistory(p,0);
        }
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if(e.getView().getTopInventory().getHolder() instanceof MenuTag) e.setCancelled(true);
    }
}
