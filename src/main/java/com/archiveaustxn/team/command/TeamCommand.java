package com.archiveaustxn.team.command;

import com.archiveaustxn.home.HomeModule;
import java.util.List;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Team command entry point; team state is owned by the core's shared Home/Team service. */
public final class TeamCommand implements CommandExecutor, TabCompleter {
    private final HomeModule teams;

    public TeamCommand(HomeModule teams) {
        this.teams = Objects.requireNonNull(teams);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return teams.executeTeamCommand(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return teams.onTabComplete(sender, command, alias, args);
    }
}
