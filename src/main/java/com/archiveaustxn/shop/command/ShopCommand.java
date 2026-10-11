package com.archiveaustxn.shop.command;

import com.archiveaustxn.shop.ShopModule;

import com.archiveaustxn.EconomySMP;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ShopCommand implements CommandExecutor, TabCompleter {
    private final EconomySMP plugin;
    private final ShopModule shop;

    public ShopCommand(EconomySMP plugin, ShopModule shop) {
        this.plugin = plugin;
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("shop.reload")) {
                sender.sendMessage(color(shop.message("no-permission", "&cYou have no permission for that!")));
                return true;
            }
            shop.load();
            sender.sendMessage(color(shop.message("reload-success", "&aShop reloaded!")));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(shop.message("player-only", "Only players can use this command!")));
            return true;
        }
        shop.openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("shop.reload")) return List.of("reload");
        return List.of();
    }

    static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
