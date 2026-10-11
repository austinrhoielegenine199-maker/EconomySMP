package com.archiveaustxn.sell;

import com.archiveaustxn.EconomySMP;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.io.File;
import java.util.*;

public final class SellModule implements Listener, CommandExecutor, TabCompleter {
    private final EconomySMP plugin;
    private final Map<String,YamlConfiguration> configs = new HashMap<>();
    private final Map<UUID,Double> earned = new HashMap<>();
    private final Map<UUID,Integer> sold = new HashMap<>();
    private String openMenu = "sell";

    public SellModule(EconomySMP plugin) { this.plugin=plugin; }

    public void load() {
        File root=new File(plugin.getDataFolder(),"core/sell");
        File gui=new File(root,"gui");
        gui.mkdirs();
        loadYaml(root,"sell.yml","core/sell/sell.yml");
        loadYaml(root,"worth.yml","core/sell/worth.yml");
        loadYaml(root,"lang.yml","core/sell/lang.yml");
        loadYaml(root,"level.yml","core/sell/level.yml");
        loadYaml(gui,"Sell.gui.yml","core/sell/gui/Sell.gui.yml");
        loadYaml(gui,"Sell.history.yml","core/sell/gui/Sell.history.yml");
        loadYaml(gui,"Worth.yml","core/sell/gui/Worth.yml");
        loadYaml(gui,"Progress.gui.yml","core/sell/gui/Progress.gui.yml");
        loadYaml(gui,"Progress.item.list.yml","core/sell/gui/Progress.item.list.yml");
        configs.clear();
        for(String name:List.of("sell.yml","worth.yml","lang.yml","level.yml","Sell.gui.yml","Sell.history.yml","Worth.yml","Progress.gui.yml","Progress.item.list.yml")) {
            File f=name.contains(".gui.yml")||name.equals("Sell.history.yml")||name.equals("Worth.yml")||name.equals("Progress.item.list.yml")?new File(gui,name):new File(root,name);
            configs.put(name.toLowerCase(Locale.ROOT),YamlConfiguration.loadConfiguration(f));
        }
    }

    private void loadYaml(File dir,String name,String resource) {
        File f=new File(dir,name);
        if(!f.exists()) try {
            f.getParentFile().mkdirs();
            if(plugin.getResource(resource)!=null) plugin.saveResource(resource,false); else f.createNewFile();
        } catch(Exception e) { plugin.getLogger().warning("Could not initialize "+f+": "+e.getMessage()); }
    }

    private YamlConfiguration cfg(String name){ return configs.getOrDefault(name.toLowerCase(Locale.ROOT),new YamlConfiguration()); }
    private String color(String s){ if(s==null)return ""; java.util.regex.Matcher m=java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(s); StringBuffer b=new StringBuffer(); while(m.find()){StringBuilder c=new StringBuilder("§x");for(char ch:m.group(1).toCharArray())c.append('§').append(ch);m.appendReplacement(b,c.toString());}m.appendTail(b);return ChatColor.translateAlternateColorCodes('&',b.toString());}
    private ItemStack item(String material,String name,List<String> lore){ Material m=Material.matchMaterial(material==null?"STONE":material); if(m==null)m=Material.STONE; ItemStack s=new ItemStack(m); ItemMeta meta=s.getItemMeta(); if(meta!=null){meta.setDisplayName(color(name));meta.setLore(lore.stream().map(this::color).toList());s.setItemMeta(meta);}return s; }

    public void openSell(Player p) {
        YamlConfiguration y=cfg("Sell.gui.yml"); int rows=Math.max(1,Math.min(6,y.getInt("inventory.rows",5)));
        Inventory inv=Bukkit.createInventory(null,rows*9,color(y.getString("inventory.title","&8Sell")));
        ConfigurationSection cats=y.getConfigurationSection("buttons.categories");
        if(cats!=null) for(String key:cats.getKeys(false)){ConfigurationSection c=cats.getConfigurationSection(key);if(c==null)continue;String mat=c.getString("material","WHEAT");String name=c.getString("name","&f"+key);List<String> lore=new ArrayList<>();for(String line:c.getStringList("lore"))lore.add(line.replace("%next_multiplier%",String.valueOf(nextMultiplier(p))).replace("%progress%",String.valueOf(progress(p))).replace("%progress-bar%",progressBar(p)));int slot=c.getInt("slot",-1);if(slot>=0&&slot<inv.getSize())inv.setItem(slot,item(mat,name,lore));}
        player.openInventory(inv); openMenu="sell";
    }

    private double nextMultiplier(Player p){return Math.max(1.0,cfg("level.yml").getDouble("levels.1.multiplier",1.0));}
    private int progress(Player p){double target=cfg("level.yml").getDouble("levels.1.required",1000);if(target<=0)return 100;return (int)Math.max(0,Math.min(100,earned.getOrDefault(p.getUniqueId(),0.0)/target*100));}
    private String progressBar(Player p){int pct=progress(p),len=10,filled=pct*len/100;return color("&a"+"|".repeat(filled)+"&7"+"|".repeat(len-filled));}
    public void openWorth(Player p,int page){YamlConfiguration y=cfg("Worth.yml");int rows=Math.max(1,Math.min(6,y.getInt("worth.rows",6)));Inventory inv=Bukkit.createInventory(null,rows*9,color(y.getString("worth.title","&8Worth")));ConfigurationSection prices=cfg("worth.yml").getConfigurationSection("prices");if(prices!=null){int slot=0;for(String key:prices.getKeys(false)){if(slot>=45)break;Material m=Material.matchMaterial(key);if(m==null)continue;double price=prices.getDouble(key);inv.setItem(slot++,item(key,color("&f"+key),List.of(color("&fPrice: &a"+price))));}}p.openInventory(inv);openMenu="worth";}
    public void sellAll(Player p){
        Economy eco=plugin.getEconomyModule()==null?null:plugin.getEconomyModule().provider();
        if(eco==null){p.sendMessage(msg("economy-unavailable","&cEconomy is unavailable."));return;}
        ConfigurationSection prices=cfg("worth.yml").getConfigurationSection("prices");
        if(prices==null){p.sendMessage(msg("no-prices","&cNo sell prices are configured."));return;}
        double total=0; int count=0; Map<Integer,Integer> toRemove=new LinkedHashMap<>();
        ItemStack[] contents=p.getInventory().getStorageContents();
        for(int i=0;i<contents.length;i++){
            ItemStack stack=contents[i]; if(stack==null||stack.getType().isAir())continue;
            String key=stack.getType().name(); if(!prices.contains(key))continue;
            double unit=prices.getDouble(key);
            if(!Double.isFinite(unit)||unit<=0)continue;
            double value=unit*stack.getAmount();
            if(!Double.isFinite(value)||!Double.isFinite(total+value)){p.sendMessage(msg("transaction-failed","&cSell transaction failed."));return;}
            total+=value; count+=stack.getAmount(); toRemove.put(i,stack.getAmount());
        }
        if(count==0){p.sendMessage(msg("nothing-to-sell","&cYou have no configured items to sell."));return;}
        if(!eco.depositPlayer(p,total).transactionSuccess()){p.sendMessage(msg("transaction-failed","&cSell transaction failed."));return;}
        for(Map.Entry<Integer,Integer> entry:toRemove.entrySet()){
            ItemStack stack=p.getInventory().getItem(entry.getKey());
            if(stack!=null){int remaining=stack.getAmount()-entry.getValue();if(remaining<=0)p.getInventory().setItem(entry.getKey(),null);else stack.setAmount(remaining);}
        }
        earned.merge(p.getUniqueId(),total,Double::sum); sold.merge(p.getUniqueId(),count,Integer::sum);
        p.sendMessage(msg("sell-success","&aSold %amount% items for $%total%.").replace("%amount%",String.valueOf(count)).replace("%total%",String.format(Locale.US,"%.2f",total)));
    }
    private String msg(String key,String fallback){return color(cfg("lang.yml").getString("messages."+key,fallback));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;}if(label.equalsIgnoreCase("worth")){openWorth(p,1);return true;}if(args.length>0&&args[0].equalsIgnoreCase("reload")){if(!sender.hasPermission("economysmp.sell.admin")){p.sendMessage(msg("no-permission","&cNo permission."));return true;}load();p.sendMessage(msg("reload-success","&aSell config reloaded."));return true;}if(args.length>0&&args[0].equalsIgnoreCase("all")){sellAll(p);return true;}openSell(p);return true;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){if(args.length==1){if(sender.hasPermission("economysmp.sell.admin"))return List.of("all","reload");return List.of("all");}return List.of();}\n    @EventHandler public void onClick(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||e.getView().getTopInventory().getHolder()!=null)return;String title=e.getView().getTitle();if(!title.equals(color(cfg("Sell.gui.yml").getString("inventory.title","&8Sell")))&&!title.equals(color(cfg("Worth.yml").getString("worth.title","&8Worth"))))return;e.setCancelled(true);if(title.equals(color(cfg("Sell.gui.yml").getString("inventory.title","&8Sell"))))p.sendMessage(msg("sell-instruction","&7Use &f/sell all &7to sell configured items."));}
    @EventHandler public void onDrag(InventoryDragEvent e){String t=e.getView().getTitle();if(t.equals(color(cfg("Sell.gui.yml").getString("inventory.title","&8Sell")))||t.equals(color(cfg("Worth.yml").getString("worth.title","&8Worth"))))e.setCancelled(true);}
}
