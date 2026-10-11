package com.archiveaustxn.sell.gui;

import com.archiveaustxn.sell.config.SellConfig;
import com.archiveaustxn.sell.service.SellPriceService;
import com.archiveaustxn.sell.service.SellProgressService;
import com.archiveaustxn.sell.service.SellProgressService.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Creates Sell, Worth and Progress inventories entirely from the shipped YAML layouts. */
public final class SellGuiManager {
    public enum Menu { SELL, WORTH, PROGRESS, HISTORY, CATEGORY }
    public record MenuTag(Menu menu, String category, int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    private final SellConfig config;
    private final SellPriceService prices;
    private final SellProgressService progress;
    public SellGuiManager(SellConfig config, SellPriceService prices, SellProgressService progress) {
        this.config=config; this.prices=prices; this.progress=progress;
    }
    public String color(String input) {
        if (input == null) return "";
        Matcher m=Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(input); StringBuffer b=new StringBuffer();
        while(m.find()){StringBuilder c=new StringBuilder("§x");for(char ch:m.group(1).toCharArray())c.append('§').append(ch);m.appendReplacement(b,Matcher.quoteReplacement(c.toString()));}
        m.appendTail(b); return ChatColor.translateAlternateColorCodes('&', b.toString());
    }
    private ItemStack make(String material, String name, List<String> lore) {
        Material type=Material.matchMaterial(material==null?"STONE":material); if(type==null)type=Material.STONE;
        ItemStack stack=new ItemStack(type); ItemMeta meta=stack.getItemMeta();
        if(meta!=null){meta.setDisplayName(color(name));meta.setLore(lore.stream().map(this::color).toList());stack.setItemMeta(meta);}
        return stack;
    }
    private List<String> lore(ConfigurationSection s,String path) { return s.getStringList(path); }
    private Inventory inventory(Player p, int rows, String title, Menu menu, String category, int page) {
        Inventory inv=Bukkit.createInventory(new MenuTag(menu,category,page),Math.max(9,Math.min(54,rows*9)),color(title));
        return inv;
    }
    private void fill(Inventory inv, String path) {
        ConfigurationSection c=config.get(path);
        if(!c.getBoolean("filler.enabled",false))return;
        ItemStack pane=make(c.getString("filler.material","GRAY_STAINED_GLASS_PANE"),c.getString("filler.displayname","&7 "),List.of(c.getString("filler.lore","")));
        for(int i=0;i<inv.getSize();i++)if(inv.getItem(i)==null)inv.setItem(i,pane);
    }
    public void openSell(Player p) {
        var y=config.get("gui/sell.gui.yml");
        Inventory inv=inventory(p,y.getInt("inventory.rows",5),y.getString("inventory.title","&8Sell"),Menu.SELL,null,0);
        ConfigurationSection cats=y.getConfigurationSection("buttons.categories");
        if(cats!=null) for(String key:cats.getKeys(false)){
            ConfigurationSection c=cats.getConfigurationSection(key);if(c==null)continue;
            Level next=progress.next(p.getUniqueId(), key); String mult=next==null?"MAX":String.valueOf(next.multiplier());
            List<String> lore=new ArrayList<>(); for(String line:c.getStringList("lore"))
                lore.add(line.replace("%next_multiplier%",mult).replace("%progress%",String.valueOf(progress.progressPercent(p.getUniqueId(), key))).replace("%progress-bar%",bar(progress.progressPercent(p.getUniqueId()),10)));
            int slot=c.getInt("slot",-1); if(slot>=0&&slot<inv.getSize())inv.setItem(slot,make(c.getString("material"),c.getString("name","&f"+key),lore));
        }
        p.openInventory(inv);
    }
    public void openWorth(Player p,int page) {
        var y=config.get("gui/worth.yml"); int rows=y.getInt("worth.rows",6);
        Inventory inv=inventory(p,rows,y.getString("worth.title","&8Worth"),Menu.WORTH,null,page);
        List<Map.Entry<Material,Double>> all=new ArrayList<>(prices.allPrices().entrySet());
        int start=Math.max(0,page)*45;
        for(int i=0;i<45&&start+i<all.size()&&i<inv.getSize();i++){
            var e=all.get(start+i); ConfigurationSection item=y.getConfigurationSection("worth.item");
            String name=(item==null?"&f%item-name%":item.getString("displayname","&f%item-name%")).replace("%item-name%",pretty(e.getKey().name()));
            List<String> lore=item==null?List.of("&fPrice: &a%price%"):item.getStringList("lore");
            inv.setItem(i,make(e.getKey().name(),name,lore.stream().map(s->s.replace("%price%",String.format(Locale.US,"%.2f",e.getValue()))).toList()));
        }
        p.openInventory(inv);
    }
    public void openProgress(Player p,String category) {
        var y=config.get("gui/progress.gui.yml"); ConfigurationSection titles=y.getConfigurationSection("titles-progress-gui");
        String title=titles==null?"&8Progress":titles.getString(iconFor(category),"&8Progress");
        Inventory inv=inventory(p,y.getInt("rows",6),title,Menu.PROGRESS,category,0); fill(inv,"gui/progress.gui.yml");
        List<Integer> slots=y.getIntegerList("progress-slots"); List<Level> levels=progress.levels(category);
        for(int i=0;i<slots.size();i++){int slot=slots.get(i);if(slot<0||slot>=inv.getSize())continue;
            Level next=i<levels.size()?levels.get(i):null; int pct=next==null?100:(int)Math.max(0,Math.min(100,progress.total(p.getUniqueId(),category)/Math.max(1,next.required())*100));
            String key=pct>=100?"complete":pct>0?"working":"incomplete"; ConfigurationSection item=y.getConfigurationSection(key);if(item==null)continue;
            String bar=bar(pct,10),mult=next==null?"MAX":String.valueOf(next.multiplier());
            List<String> lore=item.getStringList("lore").stream().map(s->s.replace("%loading-bar%",bar).replace("%multiplier%",mult).replace("%progress%",String.valueOf(pct)).replace("%amount-have%",String.format(Locale.US,"%.2f",progress.total(p.getUniqueId(),category))).replace("%amount-needed%",next==null?"MAX":String.format(Locale.US,"%.2f",next.required()))).toList();
            inv.setItem(slot,make(item.getString("material","WHITE_STAINED_GLASS_PANE"),item.getString("displayname","&fProgress"),lore));
        }
        ConfigurationSection back=y.getConfigurationSection("back-button");if(back!=null&&back.getBoolean("enabled",true)){int slot=back.getInt("slot",45);if(slot>=0&&slot<inv.getSize())inv.setItem(slot,make(back.getString("material","RED_STAINED_GLASS_PANE"),back.getString("displayname","&cBack"),back.getStringList("lore")));}
        p.openInventory(inv);
    }
    private String iconFor(String cat){return switch(cat==null?"":cat.toLowerCase(Locale.ROOT)){case "crops"->"WHEAT";case "ores"->"DIAMOND";case "mobs"->"BONE";case "natural"->"OAK_LEAVES";case "tools"->"NETHERITE_HELMET";case "fish"->"TROPICAL_FISH";case "books"->"ENCHANTED_BOOK";case "potions"->"POTION";case "blocks"->"BRICK";default->"WHEAT";};}
    public void openCategory(Player p,String category,int page) {
        var y=config.get("gui/progress.item.list.yml"); ConfigurationSection root=y.getConfigurationSection("category-item-worth-list");
        String title=root==null?"&8Items":root.getString("titles."+category,"&8Items");
        Inventory inv=inventory(p,root==null?6:root.getInt("rows",6),title,Menu.CATEGORY,category,page);
        int per=45,start=Math.max(0,page)*per,i=0; for(var e:prices.allPrices().entrySet()){
            if(i++<start)continue; if(i-start>per)break;
            inv.setItem(i-start-1,make(e.getKey().name(),pretty(e.getKey().name()),List.of("&fPrice: &a"+String.format(Locale.US,"%.2f",e.getValue()))));
        } p.openInventory(inv);
    }
    private String bar(int percent,int length){int n=Math.max(0,Math.min(length,percent*length/100));return color("&a"+"|".repeat(n)+"&7"+"|".repeat(length-n));}
    private String pretty(String s){String[] words=s.toLowerCase(Locale.ROOT).split("_");StringJoiner j=new StringJoiner(" ");for(String w:words)j.add(w.substring(0,1).toUpperCase(Locale.ROOT)+w.substring(1));return j.toString();}
}
