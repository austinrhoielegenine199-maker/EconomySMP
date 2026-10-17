package com.archiveaustxn.managers;

import com.archiveaustxn.EconomySMP;
import com.archiveaustxn.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

public final class CommandWhitelistManager implements Listener, CommandExecutor {
    private final EconomySMP plugin;
    private final File file;
    private FileConfiguration config;
    private Set<String> allowedCommands = Collections.emptySet();

    public CommandWhitelistManager(EconomySMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "core/commandwhitelist.yml");
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getCommand("commandwhitelist") != null) {
            plugin.getCommand("commandwhitelist").setExecutor(this);
        } else {
            plugin.getLogger().warning("/commandwhitelist is not declared in plugin.yml.");
        }
    }

    public void reload() {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create command whitelist directory.");
            }
            try {
                plugin.saveResource("core/commandwhitelist.yml", false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.WARNING, "Missing bundled core/commandwhitelist.yml; command whitelist is disabled.", exception);
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
        Set<String> commands = new TreeSet<>();
        for (String command : config.getStringList("default")) {
            if (command != null && !command.isBlank()) {
                String normalized = normalize(command);
                if (!normalized.isEmpty()) commands.add(normalized);
            }
        }
        allowedCommands = Collections.unmodifiableSet(commands);
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("economysmp.commandwhitelist.manage")) {
            sender.sendMessage(ColorUtils.toComponent("&cYou do not have permission to manage the command whitelist."));
            return true;
        }
        if (args.length != 1 || !(args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable") || args[0].equalsIgnoreCase("reload"))) {
            sender.sendMessage(ColorUtils.toComponent("&cUsage: /commandwhitelist <enable|disable|reload>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reload();
            sender.sendMessage(ColorUtils.toComponent("&aCommand whitelist configuration reloaded."));
            return true;
        }

        boolean enabled = args[0].equalsIgnoreCase("enable");
        config.set("enabled", enabled);
        try {
            config.save(file);
        } catch (java.io.IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save command whitelist settings.", exception);
            sender.sendMessage(ColorUtils.toComponent("&cCould not save command whitelist settings."));
            return true;
        }
        sender.sendMessage(ColorUtils.toComponent(enabled
                ? "&aCommand whitelist enabled. Unlisted commands are hidden and blocked for normal players."
                : "&eCommand whitelist disabled. All command suggestions and commands are restored."));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("enabled", true)) return;
        Player player = event.getPlayer();
        String bypassPermission = config.getString("bypass-permission", "economysmp.commandwhitelist.bypass");
        if (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission)) return;

        String command = rootCommand(event.getMessage());
        if (command.isEmpty() || allowedCommands.contains(command)) return;

        event.setCancelled(true);
        String message = config.getString("unknown-message", "&c%command% unknown command.")
                .replace("%command%", "/" + command);
        player.sendMessage(ColorUtils.toComponent(message));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSuggestions(PlayerCommandSendEvent event) {
        if (!config.getBoolean("enabled", true)) return;
        Player player = event.getPlayer();
        String bypassPermission = config.getString("bypass-permission", "economysmp.commandwhitelist.bypass");
        if (bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission)) return;
        event.getCommands().removeIf(command -> !allowedCommands.contains(normalize(command)));
    }

    private static String rootCommand(String message) {
        if (message == null) return "";
        String trimmed = message.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        if (trimmed.isBlank()) return "";
        String root = trimmed.split("\\s+", 2)[0];
        // Namespaced labels intentionally are not allowed unless explicitly listed.
        return normalize(root);
    }

    private static String normalize(String command) {
        String value = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        while (value.startsWith("/")) value = value.substring(1);
        return value;
    }
}
