package com.archiveaustxn.team.manager;

import com.archiveaustxn.home.HomeModule;
import java.util.Objects;
import org.bukkit.command.CommandSender;

/**
 * Public Team service facade for the core's Team feature.
 * Persistent team state and GUI/listener lifecycle remain centralized in HomeModule
 * until the original standalone Team implementation is fully migrated.
 */
public final class TeamManager {
    private final HomeModule service;

    public TeamManager(HomeModule service) {
        this.service = Objects.requireNonNull(service);
    }

    public boolean execute(CommandSender sender, String[] args) {
        return service.executeTeamCommand(sender, args);
    }
}
