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
        if (args.length > 0 && args[0].equalsIgnoreCase("addhanditem")) {
            if (!sender.hasPermission("shop.admin")) {
                sender.sendMessage(color(shop.message("no-permission", "&cYou don't have permission.")));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color(shop.message("player-only", "&cOnly players can use this command.")));
                return true;
            }
            if (args.length != 4) {
                sender.sendMessage(color("&cUsage: /shop addhanditem <category> <slot> <price>"));
                sender.sendMessage(color("&7Categories: &f" + String.join(", ", shop.categoryNames())));
                return true;
            }
            int slot;
            double price;
            try {
                slot = Integer.parseInt(args[2]);
                price = Double.parseDouble(args[3]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(color("&cSlot must be a number and price must be a valid number."));
                return true;
            }
            if (slot < 0 || slot > 53 || !Double.isFinite(price) || price < 0) {
                sender.sendMessage(color("&cSlot must be 0-53 and price must be zero or greater."));
                return true;
            }
            if (shop.addHandItem(player, args[1], slot, price)) {
                sender.sendMessage(color("&aAdded your held item to category &f" + args[1] + " &aat slot &f" + slot + " &afor &f" + price + "&a."));
            } else {
                sender.sendMessage(color("&cCould not add item. Hold an item and use an existing category name."));
            }
            return true;
        }
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
        if (args.length == 1 && sender.hasPermission("shop.admin")) return List.of("addhanditem", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("addhanditem") && sender.hasPermission("shop.admin")) return shop.categoryNames().stream().sorted().toList();
        if (args.length == 1 && sender.hasPermission("shop.reload")) return List.of("reload");
        return List.of();
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
