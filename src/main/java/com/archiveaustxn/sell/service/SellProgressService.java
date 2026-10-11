package com.archiveaustxn.sell.service;

import com.archiveaustxn.sell.config.SellConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks configurable progression by player and category. */
public final class SellProgressService {
    public record Level(int number, double required, double multiplier) {}
    private final SellConfig config;
    private final Map<String, Double> totals = new ConcurrentHashMap<>();
    public SellProgressService(SellConfig config) { this.config = config; }
    private String key(UUID id,String category){return id+":"+(category==null?"all":category.toLowerCase(Locale.ROOT));}
    public void add(UUID id,double amount){add(id,"all",amount);}
    public void add(UUID id,String category,double amount){if(amount>0&&Double.isFinite(amount))totals.merge(key(id,category),amount,Double::sum);}
    public double total(UUID id){return total(id,"all");}
    public double total(UUID id,String category){return totals.getOrDefault(key(id,category),0.0);}
    public List<Level> levels(){return levels("crops");}
    public List<Level> levels(String category){
        List<Level> result=new ArrayList<>();
        ConfigurationSection root=config.get("level.yml").getConfigurationSection("categories");
        ConfigurationSection section=root==null?null:root.getConfigurationSection(category==null?"crops":category.toLowerCase(Locale.ROOT));
        if(section!=null){for(String k:section.getKeys(false))try{
            int n=Integer.parseInt(k);ConfigurationSection l=section.getConfigurationSection(k);if(l==null)continue;
            double req=l.getDouble("amountNeeded",l.getDouble("required",0)),mult=l.getDouble("multiplier",1);
            if(req>=0&&Double.isFinite(req)&&mult>0&&Double.isFinite(mult))result.add(new Level(n,req,mult));
        }catch(NumberFormatException ignored){}}
        else {ConfigurationSection legacy=config.get("level.yml").getConfigurationSection("levels");if(legacy!=null)for(String k:legacy.getKeys(false))try{
            int n=Integer.parseInt(k);ConfigurationSection l=legacy.getConfigurationSection(k);if(l!=null)result.add(new Level(n,l.getDouble("required",0),l.getDouble("multiplier",1)));
        }catch(NumberFormatException ignored){}}
        result.sort(Comparator.comparingInt(Level::number));return result;
    }
    public Level current(UUID id){return current(id,"crops");}
    public Level current(UUID id,String category){Level current=new Level(0,0,config.get("level.yml").getDouble("base-multiplier",1));double total=total(id,category);for(Level l:levels(category))if(total>=l.required())current=l;return current;}
    public Level next(UUID id){return next(id,"crops");}
    public Level next(UUID id,String category){double total=total(id,category);for(Level l:levels(category))if(total<l.required())return l;return null;}
    public int progressPercent(UUID id){return progressPercent(id,"crops");}
    public int progressPercent(UUID id,String category){Level n=next(id,category);if(n==null)return 100;double span=n.required()-current(id,category).required();if(span<=0)return 100;return (int)Math.max(0,Math.min(100,(total(id,category)-current(id,category).required())/span*100));}
    public String categoryFor(Material m){
        if(m==null)return "natural";String n=m.name();
        if(n.contains("WHEAT")||n.contains("CARROT")||n.contains("POTATO")||n.contains("BEETROOT")||n.contains("SEEDS")||n.contains("SUGAR_CANE")||n.contains("PUMPKIN")||n.contains("MELON"))return "crops";
        if(n.contains("ORE")||n.contains("INGOT")||n.contains("DIAMOND")||n.contains("EMERALD")||n.contains("RAW_")||n.equals("COAL")||n.equals("LAPIS_LAZULI")||n.equals("REDSTONE"))return "ores";
        if(n.contains("BONE")||n.contains("FLESH")||n.contains("STRING")||n.contains("GUNPOWDER")||n.contains("SPIDER_EYE"))return "mobs";
        if(n.contains("COD")||n.contains("SALMON")||n.contains("FISH"))return "fish";
        if(n.contains("BOOK"))return "books";if(n.contains("POTION"))return "potions";
        if(n.contains("SWORD")||n.contains("AXE")||n.contains("PICKAXE")||n.contains("SHOVEL")||n.contains("HOE")||n.contains("HELMET")||n.contains("CHESTPLATE")||n.contains("LEGGINGS")||n.contains("BOOTS")||n.contains("BOW")||n.contains("CROSSBOW"))return "tools";
        if(n.endsWith("_LOG")||n.endsWith("_LEAVES")||n.contains("SAPLING")||n.contains("FLOWER")||n.contains("MUSHROOM"))return "natural";
        return "blocks";
    }
}
