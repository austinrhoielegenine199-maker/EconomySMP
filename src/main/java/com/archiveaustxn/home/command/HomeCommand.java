package com.archiveaustxn.home.command;

import com.archiveaustxn.home.HomeModule;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Routes /home, /sethome and /delhome into the shared Home service. */
public final class HomeCommand implements CommandExecutor, TabCompleter {
    private final HomeModule homes;

    public HomeCommand(HomeModule homes) {
        this.homes = Objects.requireNonNull(homes);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return homes.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return homes.onTabComplete(sender, command, alias, args);
    }
}
