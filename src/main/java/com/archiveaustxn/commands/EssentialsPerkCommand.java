package com.archiveaustxn.commands;

import com.archiveaustxn.EconomySMP;
import com.archiveaustxn.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;

public final class EssentialsPerkCommand implements CommandExecutor {
    private final EconomySMP plugin;
    private final Map<String, String> permissions = Map.of(
        "repair", "economysmp.perks.repair",
        "condense", "economysmp.perks.condense",
        "craft", "economysmp.perks.craft",
        "workbench", "economysmp.perks.craft",
        "anvil", "economysmp.perks.anvil",
        "smithingtable", "economysmp.perks.smithingtable",
        "smithing", "economysmp.perks.smithingtable"
    );
    public EssentialsPerkCommand(EconomySMP plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        String name = label.toLowerCase(java.util.Locale.ROOT);
        String permission = permissions.getOrDefault(name, "economysmp.perks." + name);
        if (!player.hasPermission(permission) && !player.hasPermission("economysmp.staff.*")) {
            player.sendMessage(ColorUtils.toComponent("&cYou don't have permission to use /" + label + "."));
            return true;
        }
        switch (name) {
            case "craft", "workbench" -> player.openWorkbench(player.getLocation(), true);
            case "anvil" -> player.openAnvil(player.getLocation(), true);
            case "smithingtable", "smithing" -> player.openSmithingTable(player.getLocation(), true);
            case "repair" -> {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir() || !(item.getItemMeta() instanceof Damageable meta) || !meta.hasDamage()) {
                    player.sendMessage(ColorUtils.toComponent("&cHold a damaged item to repair."));
                    return true;
                }
                meta.setDamage(0);
                item.setItemMeta(meta);
                player.sendMessage(ColorUtils.toComponent("&aItem repaired."));
            }
            case "condense" -> {
                int converted = 0;
                Map<Material, Material> blocks = new HashMap<>();
                blocks.put(Material.IRON_INGOT, Material.IRON_BLOCK);
                blocks.put(Material.GOLD_INGOT, Material.GOLD_BLOCK);
                blocks.put(Material.COPPER_INGOT, Material.COPPER_BLOCK);
                blocks.put(Material.DIAMOND, Material.DIAMOND_BLOCK);
                blocks.put(Material.EMERALD, Material.EMERALD_BLOCK);
                blocks.put(Material.REDSTONE, Material.REDSTONE_BLOCK);
                blocks.put(Material.LAPIS_LAZULI, Material.LAPIS_BLOCK);
                blocks.put(Material.COAL, Material.COAL_BLOCK);
                blocks.put(Material.RAW_IRON, Material.RAW_IRON_BLOCK);
                blocks.put(Material.RAW_GOLD, Material.RAW_GOLD_BLOCK);
                blocks.put(Material.RAW_COPPER, Material.RAW_COPPER_BLOCK);
                for (Map.Entry<Material, Material> entry : blocks.entrySet()) {
                    int count = 0;
                    for (ItemStack stack : player.getInventory().getStorageContents()) {
                        if (stack != null && stack.getType() == entry.getKey()) count += stack.getAmount();
                    }
                    int amount = count / 9;
                    if (amount < 1) continue;
                    int remaining = amount * 9;
                    for (int i = 0; i < player.getInventory().getStorageContents().length && remaining > 0; i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        if (stack == null || stack.getType() != entry.getKey()) continue;
                        int take = Math.min(remaining, stack.getAmount());
                        stack.setAmount(stack.getAmount() - take);
                        remaining -= take;
                        if (stack.getAmount() <= 0) player.getInventory().setItem(i, null);
                    }
                    Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(entry.getValue(), amount));
                    overflow.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
                    converted += amount;
                }
                player.sendMessage(ColorUtils.toComponent(converted > 0 ? "&aCondensed &f" + converted + " &ablock(s)." : "&cYou don't have enough eligible items to condense."));
            }
            default -> player.sendMessage(ColorUtils.toComponent("&cThis perk is not implemented."));
        }
        return true;
    }
}
