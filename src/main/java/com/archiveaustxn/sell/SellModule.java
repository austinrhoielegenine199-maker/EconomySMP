package com.archiveaustxn.sell;

import com.archiveaustxn.EconomySMP;
import com.archiveaustxn.sell.config.SellConfig;
import com.archiveaustxn.sell.gui.SellGuiManager;
import com.archiveaustxn.sell.gui.SellGuiManager.Menu;
import com.archiveaustxn.sell.gui.SellGuiManager.MenuTag;
import com.archiveaustxn.sell.listener.SellMenuListener;
import com.archiveaustxn.sell.listener.SellaxeListener;
import com.archiveaustxn.sell.service.SellHistoryService;
import com.archiveaustxn.sell.service.SellPriceService;
import com.archiveaustxn.sell.service.SellProgressService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.*;

/**
 * Sell module facade. Commands, menus, pricing, progression and history are split into services.
 * Config resources live under plugins/EconomySMP/core/sell/.
 */
public final class SellModule implements CommandExecutor, TabCompleter, Listener {
    private final EconomySMP plugin;
    private SellConfig config;
    private SellPriceService prices;
    private SellProgressService progress;
    private SellHistoryService history;
    private SellGuiManager gui;

    public SellModule(EconomySMP plugin) { this.plugin = Objects.requireNonNull(plugin); }

    public void load() {
        config = new SellConfig(plugin);
        config.loadAll();
        prices = new SellPriceService(config);
        progress = new SellProgressService(config);
        history = new SellHistoryService();
        gui = new SellGuiManager(config, prices, progress);
        Bukkit.getPluginManager().registerEvents(new SellMenuListener(gui, this, history), plugin);
        Bukkit.getPluginManager().registerEvents(new SellaxeListener(config, prices), plugin);
        plugin.getLogger().info("Sell module loaded: " + prices.allPrices().size() + " configured item prices.");
    }
    public SellConfig getConfigService(){ return config; }
    public SellPriceService getPriceService(){ return prices; }
    public SellProgressService getProgressService(){ return progress; }
    public SellHistoryService getHistoryService(){ return history; }
    public void openSell(Player p){ gui.openSell(p); }
    public void openWorth(Player p,int page){ gui.openWorth(p,page); }

    public void openHistory(Player p,int page) {
        var y=config.get("gui/Sell.history.yml");
        int rows=Math.max(1,Math.min(6,y.getInt("sellhistory.rows",6)));
        Inventory inv=Bukkit.createInventory(new MenuTag(Menu.HISTORY,null,page),rows*9,gui.color(y.getString("sellhistory.title","&8Sell History")));
        ConfigurationSection source=y.getConfigurationSection("source-item");
        int pageSize=45;
        List<SellHistoryService.Entry> entries=history.recent(p.getUniqueId(),500);
        int start=Math.max(0,page)*pageSize;
        for(int i=0;i<pageSize&&start+i<entries.size();i++){
            SellHistoryService.Entry e=entries.get(start+i);
            Material material=Material.matchMaterial(e.material()); if(material==null)material=Material.PAPER;
            String display=source==null?"&f%item_name%":source.getString("displayname","&f%item_name%");
            List<String> lore=source==null?List.of("&fTotal price: &a%total_price%","&fTotal amount: &a%total_amount%"):source.getStringList("lore");
            ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();
            if(meta!=null){meta.setDisplayName(gui.color(display.replace("%item_name%",pretty(e.material()))));meta.setLore(lore.stream().map(s->gui.color(s.replace("%item_lore%","").replace("%total_price%",String.format(Locale.US,"%.2f",e.total())).replace("%total_amount%",String.valueOf(e.amount())))).toList());item.setItemMeta(meta);}
            inv.setItem(i,item);
        }
        p.openInventory(inv);
    }
    public void sellAll(Player player) {
        Economy economy = getEconomy();
        if(economy==null){player.sendMessage(message("economy-unavailable","&cEconomy is unavailable."));return;}
        ItemStack[] inventory=player.getInventory().getStorageContents();
        Map<Integer,Integer> remove=new LinkedHashMap<>();
        double total=0;int amount=0;
        for(int slot=0;slot<inventory.length;slot++){
            ItemStack stack=inventory[slot]; if(stack==null||stack.getType().isAir())continue;
            double unit=prices.unitPrice(stack.getType());if(unit<=0)continue;
            double line=unit*stack.getAmount();
            if(!Double.isFinite(line)||!Double.isFinite(total+line)){player.sendMessage(message("transaction-failed","&cSell transaction failed."));return;}
            total+=line;amount+=stack.getAmount();remove.put(slot,stack.getAmount());
        }
        if(amount==0){player.sendMessage(message("nothing-to-sell","&cNo configured items found in your inventory."));return;}
        double multiplier=progress.current(player.getUniqueId()).multiplier();
        total*=multiplier;
        if(!Double.isFinite(total)||total<=0){player.sendMessage(message("transaction-failed","&cSell transaction failed."));return;}
        if(!economy.depositPlayer(player,total).transactionSuccess()){player.sendMessage(message("transaction-failed","&cSell transaction failed."));return;}
        for(var e:remove.entrySet()){
            ItemStack stack=player.getInventory().getItem(e.getKey());
            if(stack!=null){int left=stack.getAmount()-e.getValue();if(left<=0)player.getInventory().setItem(e.getKey(),null);else stack.setAmount(left);}
        }
        progress.add(player.getUniqueId(),total);
        for(var e:remove.entrySet()){
            ItemStack stack=inventory[e.getKey()];
            if(stack!=null)history.record(player.getUniqueId(),player.getName(),stack.getType().name(),e.getValue(),prices.unitPrice(stack.getType())*e.getValue()*multiplier);
        }
        player.sendMessage(message("sell-success","&aSold %amount% items for $%total%.")
                .replace("%amount%",String.valueOf(amount)).replace("%total%",String.format(Locale.US,"%.2f",total)));
    }
    private Economy getEconomy(){
        if(plugin.getEconomyModule()!=null&&plugin.getEconomyModule().provider()!=null)return plugin.getEconomyModule().provider();
        RegisteredServiceProvider<Economy> registration=Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration==null?null:registration.getProvider();
    }
    public String message(String key,String fallback){return gui.color(config.get("lang.yml").getString("messages."+key,fallback));}
    private String pretty(String raw){StringJoiner j=new StringJoiner(" ");for(String s:raw.toLowerCase(Locale.ROOT).split("_"))j.add(s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1));return j.toString();}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length>0&&args[0].equalsIgnoreCase("reload")){
            if(!sender.hasPermission("economysmp.sell.admin")){sender.sendMessage(message("no-permission","&cNo permission."));return true;}
            config.reload();sender.sendMessage(message("reload-success","&aSell configuration reloaded."));return true;
        }
        if(!(sender instanceof Player p)){sender.sendMessage(message("player-only","&cOnly players can use this command."));return true;}
        if(command.getName().equalsIgnoreCase("worth")){openWorth(p,args.length>1?parsePage(args[1]):0);return true;}
        if(args.length>0){
            switch(args[0].toLowerCase(Locale.ROOT)){
                case "all" -> sellAll(p);
                case "history" -> openHistory(p,args.length>1?parsePage(args[1]):0);
                case "worth" -> openWorth(p,args.length>1?parsePage(args[1]):0);
                case "progress" -> gui.openProgress(p,args.length>1?args[1]:"crops");
                default -> openSell(p);
            }
            return true;
        }
        openSell(p);return true;
    }
    private int parsePage(String raw){try{return Math.max(0,Integer.parseInt(raw));}catch(NumberFormatException e){return 0;}}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1){List<String> choices=new ArrayList<>(List.of("all","history","worth","progress"));if(sender.hasPermission("economysmp.sell.admin"))choices.add("reload");return choices.stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();}
        if(args.length==2&&args[0].equalsIgnoreCase("progress"))return List.of("crops","ores","mobs","natural","tools","fish","books","potions","blocks");
        return List.of();
    }
}
