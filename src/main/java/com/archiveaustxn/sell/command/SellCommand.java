package com.archiveaustxn.sell.command;

import com.archiveaustxn.sell.SellModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

/** Command adapter; the module remains responsible for actual GUI and transaction wiring. */
public final class SellCommand implements CommandExecutor, TabCompleter {
    private final SellModule module;

    public SellCommand(SellModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("all")) {
            module.sellAll((Player) sender);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("Use the module's registered reload handler for now.");
            return true;
        }
        if (label.equalsIgnoreCase("worth")) {
            module.openWorth((Player) sender, 1);
        } else {
            module.openSell((Player) sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> suggestions = new ArrayList<>();
        suggestions.add("all");
        if (sender.hasPermission("economysmp.sell.admin")) suggestions.add("reload");
        return suggestions;
    }
}
