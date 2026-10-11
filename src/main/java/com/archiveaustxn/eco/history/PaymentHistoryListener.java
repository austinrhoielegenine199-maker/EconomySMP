package com.archiveaustxn.eco.history;

import com.archiveaustxn.EconomySMP;
import com.archiveaustxn.eco.EconomyModule;
import com.archiveaustxn.eco.storage.PaymentHistoryStore;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class PaymentHistoryListener implements Listener {
    private final EconomySMP plugin;
    private final PaymentHistoryStore history;
    private final EconomyModule eco;
    private final Map<UUID, String> filters = new ConcurrentHashMap<>();
    public PaymentHistoryListener(EconomySMP plugin, PaymentHistoryStore history, EconomyModule eco) {
        this.plugin=plugin; this.history=history; this.eco=eco;
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PaymentHistoryHolder) event.setCancelled(true);
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof PaymentHistoryHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "core/eco/gui/pay-history.yml"));
        int slot = event.getSlot();
        if (slot == cfg.getInt("pay-history-gui.refresh.slot", 49)) {
            open(player, holder, holder.page(), holder.sort()); return;
        }
        if (slot == cfg.getInt("pay-history-gui.previous.slot", 45) && holder.page() > 1) {
            open(player, holder, holder.page()-1, holder.sort()); return;
        }
        if (slot == cfg.getInt("pay-history-gui.next.slot", 53)) {
            if (history.find(holder.targetId(), 45, holder.page()+1, holder.sort()).size() > 0) open(player, holder, holder.page()+1, holder.sort());
            return;
        }
        if (slot == cfg.getInt("pay-history-gui.sort.slot", 48)) {
            String[] sorts={"newest","oldest","highest_price","lowest_price"};
            int i=0; for (int n=0;n<sorts.length;n++) if (sorts[n].equals(holder.sort())) i=n;
            open(player, holder, 1, sorts[(i+1)%sorts.length]); return;
        }
        if (slot == cfg.getInt("pay-history-gui.filter.slot", 50)) {
            String[] values={"all","sent","received"};
            String current=filters.getOrDefault(player.getUniqueId(),"all");
            int i=0; for(int n=0;n<values.length;n++) if(values[n].equals(current)) i=n;
            filters.put(player.getUniqueId(), values[(i+1)%values.length]);
            open(player, holder, 1, holder.sort());
        }
    }
    private void open(Player player, PaymentHistoryHolder holder, int page, String sort) {
        YamlConfiguration cfg=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"core/eco/gui/pay-history.yml"));
        int rows=Math.max(1,Math.min(6,cfg.getInt("pay-history-gui.rows",6)));
        String title=eco.color(cfg.getString("pay-history-gui.title","&8Payment Logs %player% (Page %page%)")
            .replace("%player%",holder.targetName()).replace("%page%",String.valueOf(page)));
        PaymentHistoryHolder next=new PaymentHistoryHolder(holder.targetId(),holder.targetName(),page,sort);
        org.bukkit.inventory.Inventory inv=Bukkit.createInventory(next,rows*9,title);
        var records=history.find(holder.targetId(),45,page,sort);
        String filter=filters.getOrDefault(player.getUniqueId(),"all");
        int index=0;
        for(var record:records) {
            boolean received=record.to().equalsIgnoreCase(holder.targetName());
            if(!filter.equals("all") && filter.equals("sent") && received) continue;
            if(!filter.equals("all") && filter.equals("received") && !received) continue;
            String path=received?"pay-history-gui.receive-pay":"pay-history-gui.gift-pay";
            Material mat=Material.matchMaterial(cfg.getString(path+".material",received?"LIME_STAINED_GLASS_PANE":"RED_STAINED_GLASS_PANE"));
            ItemStack item=new ItemStack(mat==null?Material.PAPER:mat);
            ItemMeta meta=item.getItemMeta();
            if(meta!=null) {
                String amount=eco.format(record.amount());
                meta.setDisplayName(eco.color(cfg.getString(path+".displayname",(received?"&a+":"&c-")+"%amount%").replace("%amount%",amount)));
                meta.setLore(cfg.getStringList(path+".lore").stream().map(line->eco.color(line.replace("%id%",record.id()).replace("%amount%",amount).replace("%from%",record.from()).replace("%to%",record.to()).replace("%date%",record.date()))).toList());
                item.setItemMeta(meta);
            }
            if(index<45) inv.setItem(index++,item);
        }
        put(inv,cfg,"previous","&ePrevious page",page>1);
        put(inv,cfg,"sort","&eSort: "+sort,true);
        put(inv,cfg,"refresh","&aRefresh",true);
        put(inv,cfg,"filter","&eFilter: "+filter,true);
        put(inv,cfg,"next","&eNext page",history.find(holder.targetId(),45,page+1,sort).size()>0);
        player.openInventory(inv);
    }
    private void put(org.bukkit.inventory.Inventory inv,YamlConfiguration cfg,String id,String fallback,boolean enabled) {
        if(!enabled)return;
        String path="pay-history-gui."+id;
        int slot=cfg.getInt(path+".slot",switch(id){case "previous"->45;case "sort"->48;case "refresh"->49;case "filter"->50;default->53;});
        if(slot<0||slot>=inv.getSize())return;
        Material mat=Material.matchMaterial(cfg.getString(path+".material",switch(id){case "previous","next"->"ARROW";case "sort"->"HOPPER";case "refresh"->"ANVIL";default->"COMPARATOR";}));
        ItemStack item=new ItemStack(mat==null?Material.PAPER:mat);
        ItemMeta meta=item.getItemMeta();
        if(meta!=null){meta.setDisplayName(eco.color(cfg.getString(path+".displayname",fallback)));meta.setLore(cfg.getStringList(path+".lore").stream().map(eco::color).toList());item.setItemMeta(meta);}
        inv.setItem(slot,item);
    }
}
