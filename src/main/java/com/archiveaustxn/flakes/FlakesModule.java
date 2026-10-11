package com.archiveaustxn.flakes;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Initial Flakes module for EconomySMP.
 *
 * This module owns its Flakes data/config files and exposes /flakes without registering
 * a second JavaPlugin entrypoint. It is intentionally independent of the standalone
 * FlakesPlugin class while the remaining GUI/upgrade/booster features are being ported.
 */
public final class FlakesModule implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private File dataFile;
    private YamlConfiguration data;
    private YamlConfiguration language;

    public FlakesModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        saveResourceIfMissing("flake.yml");
        saveResourceIfMissing("lang.yml");
        saveResourceIfMissing("FlakesBooster.yml");

        dataFile = new File(plugin.getDataFolder(), "flakes-data.yml");
        if (!dataFile.exists()) {
            try {
                if (!dataFile.createNewFile()) {
                    throw new IOException("Could not create flakes-data.yml");
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not create Flakes data file", exception);
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        language = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang.yml"));
        plugin.getLogger().info("Flakes module loaded.");
    }

    private void saveResourceIfMissing(String name) {
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) {
            plugin.saveResource(name, false);
        }
    }

    public double getBalance(UUID uuid) {
        return data.getDouble("balances." + uuid, 0.0D);
    }

    public void setBalance(UUID uuid, double amount) {
        data.set("balances." + uuid, Math.max(0.0D, amount));
        saveData();
    }

    public void close() {
        saveData();
    }

    private void saveData() {
        if (data == null || dataFile == null) return;
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save Flakes data: " + exception.getMessage());
        }
    }

    private String message(String key, String fallback) {
        String prefix = language != null && language.getBoolean("prefix-enable", true)
            ? language.getString("prefix", "&#009bffғʟᴀᴋᴇꜱ &7» ") : "";
        String body = language == null ? fallback : language.getString(key, fallback);
        return color(prefix + body);
    }

    private String color(String input) {
        return input.replace('&', '§');
    }

    private void send(CommandSender sender, String key, String fallback) {
        sender.sendMessage(message(key, fallback));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("bal") || args[0].equalsIgnoreCase("balance")) {
            OfflinePlayer target;
            if (args.length >= 2) {
                if (!sender.hasPermission("flakes.bal") && !sender.hasPermission("flakes.admin")) {
                    send(sender, "no-permission", "&cYou have no permission for that!");
                    return true;
                }
                target = Bukkit.getOfflinePlayer(args[1]);
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    send(sender, "player-not-found", "&cPlayer not found");
                    return true;
                }
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage("Usage: /flakes bal <player>");
                return true;
            }
            String key = target.getUniqueId().equals(((target instanceof Player) ? ((Player) target).getUniqueId() : target.getUniqueId()))
                ? "flakes-balance" : "flakes-balance";
            String fallback = "&fBalance: &#009bff" + format(getBalance(target.getUniqueId())) + " Flakes";
            sender.sendMessage(color(message(key, fallback).replace("%flakes%", format(getBalance(target.getUniqueId())))));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("pay")) {
            if (!(sender instanceof Player player)) {
                send(sender, "player-only", "Only players can use this command!");
                return true;
            }
            if (!sender.hasPermission("flakes.pay")) {
                send(sender, "no-permission", "&cYou have no permission for that!");
                return true;
            }
            if (args.length != 3) {
                sender.sendMessage("Usage: /flakes pay <player> <amount>");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if ((!target.hasPlayedBefore() && !target.isOnline()) || target.getUniqueId().equals(player.getUniqueId())) {
                send(sender, target.getUniqueId().equals(player.getUniqueId()) ? "self-pay" : "player-not-found",
                    target.getUniqueId().equals(player.getUniqueId()) ? "&cYou cannot give yourself Flakes!" : "&cPlayer not found");
                return true;
            }
            double amount = parseAmount(args[2]);
            if (amount <= 0 || !Double.isFinite(amount)) {
                send(sender, "invalid-number", "&cInvalid number!");
                return true;
            }
            double balance = getBalance(player.getUniqueId());
            if (balance < amount) {
                send(sender, "not-enough-flakes", "&cYou do not have enough Flakes!");
                return true;
            }
            setBalance(player.getUniqueId(), balance - amount);
            setBalance(target.getUniqueId(), getBalance(target.getUniqueId()) + amount);
            sender.sendMessage(color(message("flakes-send", "&fYou sent &#009bff%flakes_send% Flakes &fto &#00FF00%player%")
                .replace("%flakes_send%", format(amount)).replace("%player%", target.getName() == null ? args[1] : target.getName())));
            if (target.isOnline()) {
                target.getPlayer().sendMessage(color(message("flakes-received", "&fYou received &#009bff%flakes_received% Flakes &ffrom &#00FF00%target_player%")
                    .replace("%flakes_received%", format(amount)).replace("%target_player%", player.getName())));
            }
            return true;
        }

        if (sender.hasPermission("flakes.admin")) {
            if ((sub.equals("give") || sub.equals("take") || sub.equals("set")) && args.length == 3) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                double amount = parseAmount(args[2]);
                if ((!target.hasPlayedBefore() && !target.isOnline()) || amount < 0 || !Double.isFinite(amount)) {
                    send(sender, "invalid-number", "&cInvalid number!");
                    return true;
                }
                double current = getBalance(target.getUniqueId());
                double updated = sub.equals("give") ? current + amount : sub.equals("take") ? Math.max(0, current - amount) : amount;
                setBalance(target.getUniqueId(), updated);
                sender.sendMessage(color("&#009bffFlakes &7» &f" + sub + " " + format(amount) + " Flakes " +
                    (sub.equals("set") ? "to " : "to ") + (target.getName() == null ? args[1] : target.getName()) + "."));
                return true;
            }
            if (sub.equals("reload")) {
                language = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang.yml"));
                send(sender, "reload-success", "&aFlakes reloaded successfully!");
                return true;
            }
        } else if (sub.equals("give") || sub.equals("take") || sub.equals("set") || sub.equals("reload")) {
            send(sender, "no-permission", "&cYou have no permission for that!");
            return true;
        }

        sender.sendMessage(color("&#009bff&lFLAKES &7» &f/flakes [bal] | /flakes pay <player> <amount>"));
        if (sender.hasPermission("flakes.admin")) {
            sender.sendMessage(color("&c/flakes give|take|set <player> <amount>"));
            sender.sendMessage(color("&c/flakes reload"));
        }
        return true;
    }

    private double parseAmount(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String format(double amount) {
        return amount == Math.rint(amount) ? Long.toString((long) amount) : String.format(Locale.US, "%.2f", amount);
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            java.util.List<String> options = new java.util.ArrayList<>(java.util.List.of("bal", "balance", "pay"));
            if (sender.hasPermission("flakes.admin")) options.addAll(java.util.List.of("give", "take", "set", "reload"));
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("pay") || args[0].equalsIgnoreCase("bal")
            || args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("set"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return java.util.Collections.emptyList();
    }
}
