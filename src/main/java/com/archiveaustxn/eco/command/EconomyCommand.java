package com.archiveaustxn.eco.command;

import com.archiveaustxn.EconomySMP;
import com.archiveaustxn.eco.EconomyModule;
import com.archiveaustxn.eco.storage.PaymentHistoryStore;
import com.archiveaustxn.eco.storage.PaymentHistoryStore.Record;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Implements /money, /pay, and privileged /eco commands through Vault. */
public final class EconomyCommand implements CommandExecutor, TabCompleter {
    private final EconomySMP plugin;
    private final EconomyModule eco;
    private final PaymentHistoryStore history;
    public EconomyCommand(EconomySMP plugin, EconomyModule eco, PaymentHistoryStore history) {
        this.plugin=plugin; this.eco=eco; this.history=history;
    }
    private void send(CommandSender sender, String key, String fallback) { sender.sendMessage(eco.prefix() + eco.messageFor(key, fallback)); }
    private boolean ready(CommandSender sender) {
        if (!eco.available()) { sender.sendMessage(eco.color("&cEconomy unavailable: install Vault and a compatible economy plugin.")); return false; }
        return true;
    }
    private double parse(String input) {
        String value=input.trim().toLowerCase(Locale.ROOT).replace(",", "");
        double multiplier=1;
        if (eco.config().getBoolean("economy.abbreviations.enabled", true) && !value.isEmpty()) {
            char last=value.charAt(value.length()-1);
            String[] suffix=eco.config().getStringList("economy.abbreviations.formats").toArray(String[]::new);
            if (suffix.length==0) suffix=new String[]{"K","M","B","T"};
            for (int i=0;i<suffix.length;i++) if (String.valueOf(last).equalsIgnoreCase(suffix[i])) {
                multiplier=Math.pow(1000,i+1); value=value.substring(0,value.length()-1); break;
            }
        }
        try { double n=Double.parseDouble(value)*multiplier; return eco.validAmount(n)?n:Double.NaN; }
        catch (NumberFormatException e) { return Double.NaN; }
    }
    private OfflinePlayer find(String name) {
        Player online=Bukkit.getPlayerExact(name);
        if(online!=null)return online;
        for(OfflinePlayer p:Bukkit.getOfflinePlayers()) if(p.getName()!=null&&p.getName().equalsIgnoreCase(name)) return p;
        return null;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd=command.getName().toLowerCase(Locale.ROOT);
        if(cmd.equals("payhistory")) {\n            if(!(sender instanceof Player viewer)){send(sender,"player-only","Only players can use this command!");return true;}\n            UUID targetId=viewer.getUniqueId(); String targetName=viewer.getName();\n            if(args.length>0) { if(!sender.hasPermission("economysmp.payhistory.other")){send(sender,"no-permission","&cYou have no permission to use this command!");return true;} OfflinePlayer target=find(args[0]); if(target==null){send(sender,"player-not-found","&cPlayer not found!");return true;} targetId=target.getUniqueId(); targetName=target.getName()==null?args[0]:target.getName(); }\n            openHistory(viewer,targetId,targetName,1,"newest"); return true;\n        }
        if(cmd.equals("money")) {
            if(args.length==0) {
                if(!(sender instanceof Player p)){send(sender,"player-only","Only players can use this command!");return true;}
                if(!ready(sender))return true;
                sender.sendMessage(eco.prefix()+eco.color(eco.message("balance","&7You have %balance%").replace("%balance%",eco.format(eco.provider().getBalance(p)))));
                return true;
            }
            if(!sender.hasPermission("economysmp.bal.other")){send(sender,"no-permission","&cYou have no permission to use this command!");return true;}
            if(!ready(sender))return true;
            OfflinePlayer target=find(args[0]); if(target==null){send(sender,"player-not-found","&cPlayer not found!");return true;}
            sender.sendMessage(eco.prefix()+eco.color(eco.message("money_balance_other","%player% has %balance%").replace("%player%",target.getName()==null?args[0]:target.getName()).replace("%balance%",eco.format(eco.provider().getBalance(target)))));
            return true;
        }
        if(cmd.equals("pay")) {
            if(!(sender instanceof Player from)){send(sender,"player-only","Only players can use this command!");return true;}
            if(args.length!=2){sender.sendMessage(eco.color("&cUsage: /pay <player> <amount>"));return true;}
            if(!ready(sender))return true;
            OfflinePlayer target=find(args[0]); if(target==null||target.getUniqueId()==null){send(sender,"player-not-found","&cPlayer not found!");return true;}
            if(target.getUniqueId().equals(from.getUniqueId())){send(sender,"self-pay","&cYou can't pay yourself!");return true;}
            if(!target.isOnline()&&!eco.config().getBoolean("offline-payment",true)){send(sender,"offline-payment-disabled","&cYou cannot pay offline players!");return true;}
            double amount=parse(args[1]); if(!Double.isFinite(amount)){send(sender,"invalid-number","&cInvalid number!");return true;}
            Economy bank=eco.provider();
            if(!bank.has(from,amount)){send(sender,"not-enough-money","&cYou don't have enough money!");return true;}
            if(!bank.withdrawPlayer(from,amount).transactionSuccess()){send(sender,"transaction-failed","&cTransaction failed! Please try again.");return true;}
            if(!bank.depositPlayer(target,amount).transactionSuccess()) {
                bank.depositPlayer(from,amount);
                send(sender,"transaction-failed","&cTransaction failed! Please try again.");return true;
            }
            history.record(from.getUniqueId(),from.getName(),target.getUniqueId(),target.getName()==null?args[0]:target.getName(),amount);
            sender.sendMessage(eco.prefix()+eco.color(eco.message("send","&7You paid %bal_send% to %player%").replace("%bal_send%",eco.format(amount)).replace("%player%",target.getName()==null?args[0]:target.getName())));
            if(target.isOnline()) target.getPlayer().sendMessage(eco.prefix()+eco.color(eco.message("received","&7You received %bal_received% from %target_player%").replace("%bal_received%",eco.format(amount)).replace("%target_player%",from.getName())));
            return true;
        }
        if(cmd.equals("eco")) {
            if(!sender.hasPermission("economysmp.admin")){send(sender,"no-permission","&cYou have no permission to use this command!");return true;}
            if(args.length==0){sender.sendMessage(eco.color(eco.config().getString("command-usage-admin","/eco give <player> <amount>")));return true;}
            String action=args[0].toLowerCase(Locale.ROOT);
            if(action.equals("reload")) { eco.reload(); send(sender,"reload-success","&aPlugin successfully reloaded!"); return true; }
            if(action.equals("clearall")) {
                if(!ready(sender))return true;
                for(OfflinePlayer p:Bukkit.getOfflinePlayers()) eco.provider().withdrawPlayer(p,eco.provider().getBalance(p));
                send(sender,"clear-all-success","&aAll balances have been cleared!"); return true;
            }
            if((action.equals("giveall")||action.equals("takeall"))&&args.length==2) {
                if(!ready(sender))return true; double amount=parse(args[1]);if(!Double.isFinite(amount)){send(sender,"invalid-number","&cInvalid number!");return true;}
                for(OfflinePlayer p:Bukkit.getOfflinePlayers()) if(action.equals("giveall")) eco.provider().depositPlayer(p,amount); else eco.provider().withdrawPlayer(p,Math.min(amount,eco.provider().getBalance(p)));
                send(sender,action.equals("giveall")?"eco-give-all":"eco-take-all",action.equals("giveall")?"&7You gave %bal_given% to all players!":"&7You took %bal_taken% from all players!");return true;
            }
            if(action.equals("clear")&&args.length==2) {
                if(!ready(sender))return true; OfflinePlayer target=find(args[1]);if(target==null){send(sender,"player-not-found","&cPlayer not found!");return true;}
                boolean ok=eco.provider().withdrawPlayer(target,eco.provider().getBalance(target)).transactionSuccess();
                send(sender,ok?"clear-player-success":"clear-player-failed",ok?"&a%player%'s balance has been cleared!":"&cFailed to clear %player%'s balance!");return true;
            }
            if(args.length!=3||!(action.equals("give")||action.equals("take")||action.equals("set"))) {sender.sendMessage(eco.color("&cUsage: /eco <give|take|set|clear|giveall|takeall|clearall> ..."));return true;}
            if(!ready(sender))return true; OfflinePlayer target=find(args[1]);if(target==null){send(sender,"player-not-found","&cPlayer not found!");return true;}
            double amount=parse(args[2]);if(!Double.isFinite(amount)){send(sender,"invalid-number","&cInvalid number!");return true;}
            boolean ok=switch(action) {
                case "give" -> eco.provider().depositPlayer(target,amount).transactionSuccess();
                case "take" -> eco.provider().withdrawPlayer(target,amount).transactionSuccess();
                default -> eco.provider().withdrawPlayer(target,eco.provider().getBalance(target)).transactionSuccess() && eco.provider().depositPlayer(target,amount).transactionSuccess();
            };
            if(!ok){send(sender,"transaction-failed","&cTransaction failed! Please try again.");return true;}
            String key=action.equals("give")?"given":action.equals("take")?"taken":"set";
            String fallback=action.equals("give")?"&7You gave %bal_given% to %player%":action.equals("take")?"&7You took %bal_taken% from %player%":"&7You set %player%'s money to %bal_set%";
            sender.sendMessage(eco.prefix()+eco.color(eco.message(key,fallback).replace("%player%",target.getName()==null?args[1]:target.getName()).replace("%bal_given%",eco.format(amount)).replace("%bal_taken%",eco.format(amount)).replace("%bal_set%",eco.format(amount))));
            return true;
        }
        return false;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if(command.getName().equalsIgnoreCase("eco")&&args.length==1&&sender.hasPermission("economysmp.admin"))return List.of("give","take","set","clear","giveall","takeall","clearall","reload");
        if(command.getName().equalsIgnoreCase("eco")&&args.length==2&&sender.hasPermission("economysmp.admin")&&List.of("give","take","set","clear").contains(args[0].toLowerCase(Locale.ROOT)))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if(command.getName().equalsIgnoreCase("pay")&&args.length==1)return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
