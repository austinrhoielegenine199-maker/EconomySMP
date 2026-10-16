package com.archiveaustxn;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

/**
 * Restricts the private owner status command to the exact configured username.
 * This command deliberately does not grant operator privileges; OP changes must
 * be performed explicitly from the server console.
 */
public final class ArchiveAustxnCommand implements CommandExecutor, Listener {
    private static final String OWNER_NAME = "ArchiveAustxn";
    private final EconomySMP plugin;

    public ArchiveAustxnCommand(EconomySMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        if (!isOwner(event.getPlayer())) {
            event.getCommands().removeIf(command ->
                command.equalsIgnoreCase("archiveaustxn")
                    || command.equalsIgnoreCase("economysmp:archiveaustxn")
            );
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !isOwner(player)) {
            sender.sendMessage("Unknown command.");
            return true;
        }

        if (player.isOp()) {
            player.sendMessage("§b§lEconomySMP §8» §7Owner access confirmed. You already have operator status.");
        } else {
            player.sendMessage("§b§lEconomySMP §8» §7Owner access confirmed, but this command does not grant OP.");
            player.sendMessage("§7To grant OP, run §f/op ArchiveAustxn §7from the server console.");
            plugin.getLogger().info("ArchiveAustxn used the private owner status command while not OP; no privileges were changed.");
        }
        return true;
    }

    private boolean isOwner(Player player) {
        return player.getName().equalsIgnoreCase(OWNER_NAME);
    }
}
