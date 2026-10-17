package com.archiveaustxn.managers;

import com.archiveaustxn.EconomySMP;
import com.archiveaustxn.utils.ColorUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class CommandWhitelistManager implements Listener {
    private final EconomySMP plugin;
    private final File file;
    private FileConfiguration config;
    private Set<String> allowedCommands = Collections.emptySet();

    public CommandWhitelistManager(EconomySMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "core/commandwhitelist.yml");
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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
