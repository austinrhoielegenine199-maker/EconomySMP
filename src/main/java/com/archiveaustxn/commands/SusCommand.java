package com.archiveaustxn.commands;

import com.archiveaustxn.EconomySMP;
import com.archiveaustxn.menus.BaseMenu;
import com.archiveaustxn.utils.ColorUtils;
import com.archiveaustxn.utils.ItemUtils;
import com.archiveaustxn.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SusCommand implements CommandExecutor {

    private static final String PERMISSION = "economysmp.sus";
    private static final int PAGE_SIZE = 45;
    private static final Map<UUID, Suspect> SUSPECTS = new LinkedHashMap<>();

    private final EconomySMP plugin;

    public SusCommand(EconomySMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Reports mark a player for staff review. This is intentionally a review list,
     * not an automatic cheat verdict.
     */
    public static void recordReport(Player reported, Player reporter, String reason) {
        if (reported == null || reporter == null || reported.getUniqueId().equals(reporter.getUniqueId())) {
            return;
        }
        Suspect suspect = SUSPECTS.computeIfAbsent(reported.getUniqueId(),
                ignored -> new Suspect(reported.getName(), 0, ""));
        suspect.name = reported.getName();
        suspect.reports++;
        suspect.lastReason = reason == null ? "" : reason;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.toComponent("&cOnly players can use this command."));
            return true;
        }
        if (!PermissionUtils.has(player, PERMISSION)) {
            player.sendMessage(ColorUtils.toComponent("&cYou do not have permission."));
            return true;
        }
        new SusMenu(plugin, 0).open(player);
        return true;
    }

    private static final class Suspect {
        private String name;
        private int reports;
        private String lastReason;

        private Suspect(String name, int reports, String lastReason) {
            this.name = name;
            this.reports = reports;
            this.lastReason = lastReason;
        }
    }

    private final class SusMenu extends BaseMenu {
        private final int page;
        private final List<Suspect> suspects;
        private final Map<Integer, UUID> headSlots = new LinkedHashMap<>();

        private SusMenu(EconomySMP plugin, int page) {
            super(plugin, "&7ѕᴜѕ", 54);
            this.page = Math.max(0, page);
            this.suspects = new ArrayList<>();
            for (Map.Entry<UUID, Suspect> entry : SUSPECTS.entrySet()) {
                if (Bukkit.getPlayer(entry.getKey()) != null) {
                    suspects.add(entry.getValue());
                }
            }
        }

        @Override
        public void build(Player viewer) {
            clear();
            headSlots.clear();
            int start = page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, suspects.size());
            for (int index = start; index < end; index++) {
                Suspect suspect = suspects.get(index);
                Player target = findOnlineByName(suspect.name);
                if (target == null) {
                    continue;
                }
                int slot = index - start;
                List<String> lore = new ArrayList<>();
                lore.add("&eClick to teleport ➜");
                ItemStack head = ItemUtils.createPlayerHead(target, target.getName(), lore);
                set(slot, head);
                headSlots.put(slot, target.getUniqueId());
            }

            if (page > 0) {
                set(45, ItemUtils.createItem(Material.ARROW, "&cʙᴀᴄᴋ", List.of("&7Go to the previous page")));
            }
            if (end < suspects.size()) {
                set(53, ItemUtils.createItem(Material.ARROW, "&aɴᴇxᴛ", List.of("&7Go to the next page")));
            }
        }

        @Override
        public void handleClick(int slot, Player player) {
            if (slot == 45 && page > 0) {
                new SusMenu(plugin, page - 1).open(player);
                return;
            }
            if (slot == 53 && (page + 1) * PAGE_SIZE < suspects.size()) {
                new SusMenu(plugin, page + 1).open(player);
                return;
            }
            UUID targetId = headSlots.get(slot);
            if (targetId == null) {
                return;
            }
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) {
                player.sendMessage(ColorUtils.toComponent("&cThat player is no longer online."));
                new SusMenu(plugin, page).open(player);
                return;
            }
            player.teleport(target.getLocation());
            player.sendMessage(ColorUtils.toComponent("&7Teleported to &f" + target.getName() + "&7."));
        }

        private Player findOnlineByName(String name) {
            Player exact = Bukkit.getPlayerExact(name);
            if (exact != null) {
                return exact;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().equalsIgnoreCase(name)) {
                    return online;
                }
            }
            return null;
        }
    }
}
