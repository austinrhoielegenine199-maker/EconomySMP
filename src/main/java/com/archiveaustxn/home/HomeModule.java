package com.archiveaustxn.home;

import com.archiveaustxn.EconomySMP;
import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * Personal homes and shared team homes for EconomySMP.
 * Data is stored independently in core/home/homes-data.yml and core/team/teams-data.yml.
 */
public final class HomeModule implements CommandExecutor, TabCompleter, Listener {
    private final EconomySMP plugin;
    private final File homeDataFile;
    private final File teamDataFile;
    private YamlConfiguration homeData;
    private YamlConfiguration teamData;
    private YamlConfiguration mainGui, confirmGui, homeConfig, homeLang;
    private YamlConfiguration teamGui, memberGui, teamConfig, teamLang;
    private final Map<UUID, Location> pendingTeleports = new HashMap<>();
    private final Map<UUID, String> pendingDelete = new HashMap<>();
    private final Map<UUID, String> pendingInvites = new HashMap<>();
    private final Set<UUID> pendingRename = new HashSet<>();
    private final Map<UUID, Integer> renameTarget = new HashMap<>();

    public HomeModule(EconomySMP plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.homeDataFile = new File(plugin.getDataFolder(), "core/home/homes-data.yml");
        this.teamDataFile = new File(plugin.getDataFolder(), "core/team/teams-data.yml");
    }

    public void load() {
        loadResource("core/home/main.gui.yml");
        loadResource("core/home/confirm.gui.yml");
        loadResource("core/home/home.yml");
        loadResource("core/home/lang.yml");
        loadResource("core/team/main.gui.yml");
        loadResource("core/team/member.manager.yml");
        loadResource("core/team/team.yml");
        loadResource("core/team/lang.yml");
        mainGui = yaml("core/home/main.gui.yml");
        confirmGui = yaml("core/home/confirm.gui.yml");
        homeConfig = yaml("core/home/home.yml");
        homeLang = yaml("core/home/lang.yml");
        teamGui = yaml("core/team/main.gui.yml");
        memberGui = yaml("core/team/member.manager.yml");
        teamConfig = yaml("core/team/team.yml");
        teamLang = yaml("core/team/lang.yml");
        homeData = YamlConfiguration.loadConfiguration(homeDataFile);
        teamData = YamlConfiguration.loadConfiguration(teamDataFile);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new HomeTeamExpansion("home").register();
            new HomeTeamExpansion("team").register();
            plugin.getLogger().info("Home and Team PlaceholderAPI expansions registered.");
        }
        plugin.getLogger().info("Home/TeamHome module loaded.");
    }

    private void loadResource(String path) {
        File target = new File(plugin.getDataFolder(), path);
        if (target.exists()) return;
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        try {
            if (plugin.getResource(path) != null) plugin.saveResource(path, false);
            else target.createNewFile();
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not create " + path + ": " + exception.getMessage());
        }
    }

    private YamlConfiguration yaml(String path) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), path));
    }

    private void saveHomes() {
        try { homeData.save(homeDataFile); }
        catch (IOException exception) { plugin.getLogger().severe("Could not save homes: " + exception.getMessage()); }
    }

    private void saveTeams() {
        try { teamData.save(teamDataFile); }
        catch (IOException exception) { plugin.getLogger().severe("Could not save teams: " + exception.getMessage()); }
    }

    private static String color(String text) {
        if (text == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(out, replacement.toString());
        }
        matcher.appendTail(out);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    private String homeMessage(String key, String fallback) {
        return color(homeLang.getString("messages." + key, fallback));
    }
    private String teamMessage(String key, String fallback) {
        return color(teamLang.getString("team." + key, homeLang.getString("messages." + key, fallback)));
    }
    private String prefix(String message) {
        return homeLang.getBoolean("prefix-enable", false)
                ? color(homeLang.getString("prefix", "")) + " " + message : message;
    }
    private void send(Player player, String key, String fallback) { player.sendMessage(prefix(homeMessage(key, fallback))); }

    private String playerKey(Player player) { return player.getUniqueId().toString(); }
    private String homePath(Player player, int number) { return "players." + playerKey(player) + ".homes." + number; }
    private int maxHomes(Player player) {
        int max = Math.max(1, Math.min(5, homeConfig.getInt("homes.default-max-homes", 3)));
        for (int i = 5; i >= 1; i--) if (player.hasPermission("home.limit." + i)) return i;
        return max;
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    private void writeLocation(ConfigurationSection section, Location location) {
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX()); section.set("y", location.getY()); section.set("z", location.getZ());
        section.set("yaw", location.getYaw()); section.set("pitch", location.getPitch());
    }

    private boolean setPersonalHome(Player player, int number) {
        if (number < 1 || number > maxHomes(player)) {
            send(player, "max-homes", "&cYou have reached the maximum number of homes! (%max_homes%)");
            return false;
        }
        String world = player.getWorld().getName();
        if (homeConfig.getStringList("block-world").contains(world)) {
            player.sendMessage(homeMessage("world-blocked", "&cYou can't set a home here!").replace("%world%", world));
            return false;
        }
        ConfigurationSection blocked = homeData.getConfigurationSection("players." + playerKey(player) + ".homes");
        String path = homePath(player, number);
        if (homeData.isConfigurationSection(path)) {
            send(player, "home-already-set", "&cThat home is already set."); return false;
        }
        ConfigurationSection section = homeData.createSection(path);
        writeLocation(section, player.getLocation());
        saveHomes();
        send(player, "home-set", "&fHome &a%home% &fhas been set".replace("%home%", String.valueOf(number)));
        play(player, "sounds.click-sound", "UI_BUTTON_CLICK");
        return true;
    }

    private void teleport(Player player, Location target, String messageKey, String fallback) {
        if (target == null || target.getWorld() == null) { send(player, "home-not-found", "&cThat location is unavailable."); return; }
        if (pendingTeleports.containsKey(player.getUniqueId())) {
            send(player, "already-teleporting", "&cYou are already teleporting!"); return;
        }
        if (player.hasPermission("home.bypass") || player.hasPermission("team.home.bypass")
                || homeConfig.getInt("homes.teleport-delay", 5) <= 0) {
            player.teleportAsync(target);
            player.sendMessage(messageKey.equals("team") ? teamMessage("teleport-completed", fallback)
                    : homeMessage("teleport-completed", fallback));
            play(player, "sounds.teleport-complete", "ENTITY_ENDERMAN_TELEPORT");
            return;
        }
        Location start = player.getLocation().clone();
        pendingTeleports.put(player.getUniqueId(), start);
        int delay = Math.max(1, homeConfig.getInt("homes.teleport-delay", 5));
        player.sendMessage((messageKey.equals("team") ? teamMessage("teleport-countdown", "&fTeleport in %count% seconds")
                : color(homeLang.getString("cooldown.module.chat.message.0", "&fTeleport in &b%count% &fseconds")))
                .replace("%count%", String.valueOf(delay)));
        final int[] remaining = {delay};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!player.isOnline() || !pendingTeleports.containsKey(player.getUniqueId())) { task.cancel(); return; }
            Location origin = pendingTeleports.get(player.getUniqueId());
            if (origin == null || player.getLocation().distanceSquared(origin) > Math.pow(homeConfig.getDouble("homes.teleport-distance", 0.3), 2)) {
                pendingTeleports.remove(player.getUniqueId());
                player.sendMessage(messageKey.equals("team") ? teamMessage("teleport-cancelled", "&cTeleport cancelled!")
                        : homeMessage("teleport-cancelled", "&cTeleport cancelled!"));
                play(player, "sounds.teleport-cancel", "ENTITY_VILLAGER_NO");
                task.cancel(); return;
            }
            if (--remaining[0] <= 0) {
                pendingTeleports.remove(player.getUniqueId());
                player.teleportAsync(target);
                player.sendMessage(messageKey.equals("team") ? teamMessage("teleport-completed", fallback)
                        : homeMessage("teleport-completed", fallback));
                play(player, "sounds.teleport-complete", "ENTITY_ENDERMAN_TELEPORT");
                task.cancel();
            } else {
                String countdown = messageKey.equals("team")
                        ? teamMessage("teleport-countdown", "&fTeleport in %count% seconds")
                        : color(homeLang.getString("cooldown.module.chat.message.0", "&fTeleport in &b%count% &fseconds"));
                player.sendActionBar(countdown.replace("%count%", String.valueOf(remaining[0])));
                play(player, "sounds.teleport-cooldown", "BLOCK_NOTE_BLOCK_PLING");
            }
        }, 20L, 20L);
    }

    @EventHandler public void onTeamDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;
        String attackerTeam = teamName(attacker);
        if (attackerTeam == null || !attackerTeam.equalsIgnoreCase(teamName(victim))) return;
        if (!teamData.getBoolean(attackerTeam + ".pvp", false)) {
            event.setCancelled(true);
            attacker.sendMessage(teamMessage("pvp-disabled-damage", "&cYou cannot damage team members!"));
        }
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (!pendingTeleports.containsKey(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            pendingTeleports.remove(event.getPlayer().getUniqueId());
            event.getPlayer().sendMessage(homeMessage("teleport-cancelled", "&cTeleport cancelled!"));
        }
    }

    private void play(Player player, String path, String fallback) {
        try { player.playSound(player.getLocation(), Sound.valueOf(homeConfig.getString(path, fallback).toUpperCase(Locale.ROOT).replace('.', '_')), 1f, 1f); }
        catch (IllegalArgumentException ignored) { }
    }

    private String teamName(Player player) {
        for (String name : teamData.getKeys(false)) {
            if (teamData.getString(name + ".leader", "").equals(playerKey(player))
                    || teamData.getStringList(name + ".members").contains(playerKey(player))) return name;
        }
        return null;
    }
    private String teamPath(String team) { return team; }
    private boolean isLeader(Player player, String team) {
        return teamData.getString(teamPath(team) + ".leader", "").equals(playerKey(player));
    }
    private boolean teamHomeEnabled() { return teamConfig.getBoolean("team-home.enabled", true); }

    public void openHomes(Player player) {
        int rows = Math.max(1, Math.min(6, mainGui.getInt("rows", 4)));
        String title = color(mainGui.getString("title", "&8Homes"));
        Inventory inventory = Bukkit.createInventory(new HomeHolder("homes", null), rows * 9, title);
        String[] bedSlots = mainGui.getString("homes.slot-beds", "11,12,13,14,15").split(",");
        String[] dyeSlots = mainGui.getString("homes.slot-dyes", "20,21,22,23,24").split(",");
        for (int i = 0; i < 5; i++) {
            int number = i + 1;
            if (i < bedSlots.length) putHomeIcon(player, inventory, parseSlot(bedSlots[i]), number, true);
            if (i < dyeSlots.length) putHomeIcon(player, inventory, parseSlot(dyeSlots[i]), number, false);
        }
        ConfigurationSection teamButton = mainGui.getConfigurationSection("teamhome");
        int teamSlot = teamButton == null ? 10 : teamButton.getInt("slot", 10);
        String team = teamName(player);
        boolean hasHome = team != null && teamData.isConfigurationSection(team + ".home");
        String state = team == null ? "no-team" : (hasHome ? "set" : "no-home");
        ConfigurationSection button = teamButton == null ? null : teamButton.getConfigurationSection(state);
        if (button != null) putConfigured(inventory, teamSlot, button, Map.of());
        player.openInventory(inventory);
    }

    private int parseSlot(String raw) { try { return Integer.parseInt(raw.trim()); } catch (Exception e) { return -1; } }
    private void putHomeIcon(Player player, Inventory inv, int slot, int number, boolean bed) {
        if (slot < 0 || slot >= inv.getSize()) return;
        String path = homePath(player, number);
        boolean set = homeData.isConfigurationSection(path);
        boolean permitted = number <= maxHomes(player);
        String sectionPath = !permitted ? "no-permission." + (bed ? "bed" : "dye")
                : (set ? "home-set." + (bed ? "bed" : "dye") : "home-not-set." + (bed ? "bed" : "dye"));
        ConfigurationSection item = mainGui.getConfigurationSection(sectionPath);
        if (item != null) {
            putConfigured(inv, slot, item, Map.of("%home%", String.valueOf(number)));
            if (bed && set) {
                String customName = homeData.getString(path + ".name", "");
                if (!customName.isBlank()) {
                    ItemStack icon = inv.getItem(slot);
                    if (icon != null && icon.hasItemMeta()) {
                        ItemMeta meta = icon.getItemMeta();
                        meta.setDisplayName(color(customName));
                        icon.setItemMeta(meta);
                        inv.setItem(slot, icon);
                    }
                }
            }
        }
    }

    private void putConfigured(Inventory inv, int slot, ConfigurationSection config, Map<String,String> replacements) {
        if (slot < 0 || slot >= inv.getSize()) return;
        Material material = Material.matchMaterial(config.getString("material", "STONE"));
        if (material == null) material = Material.STONE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = config.getString("name", config.getString("title", config.getString("display-name", "")));
            for (var e : replacements.entrySet()) name = name.replace(e.getKey(), e.getValue());
            meta.setDisplayName(color(name));
            List<String> lore = config.getStringList("lore").stream().map(line -> {
                String result = line;
                for (var e : replacements.entrySet()) result = result.replace(e.getKey(), e.getValue());
                return color(result);
            }).toList();
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private void openConfirm(Player player, int number) {
        pendingDelete.put(player.getUniqueId(), String.valueOf(number));
        int rows = Math.max(1, Math.min(6, confirmGui.getInt("rows", 3)));
        Inventory inv = Bukkit.createInventory(new HomeHolder("confirm", String.valueOf(number)), rows * 9,
                color(confirmGui.getString("title", "&8Confirm")));
        ConfigurationSection items = confirmGui.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item != null) putConfigured(inv, item.getInt("slot", -1), item, Map.of("%home%", String.valueOf(number)));
        }
        player.openInventory(inv);
    }

    private void openTeamMenu(Player player) {
        String team = teamName(player);
        if (team == null) { send(player, "team-no-team", "&cYou don't have a team!"); return; }
        int rows = Math.max(1, Math.min(6, teamGui.getInt("gui.rows", 6)));
        String title = color(teamGui.getString("gui.title", "&8Team").replace("%current_page%", "1"));
        Inventory inv = Bukkit.createInventory(new HomeHolder("team", team), rows * 9, title);
        ConfigurationSection items = teamGui.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) continue;
                String display = item.getString("display-name", item.getString("name", item.getString("title", key)));
                display = display.replace("%team%", team).replace("%pvp-status%", teamData.getBoolean(team + ".pvp", false)
                        ? item.getString("status.enabled", "&aON") : item.getString("status.disabled", "&cOFF"));
                ConfigurationSection clone = item;
                Material material = Material.matchMaterial(item.getString("material", "STONE"));
                if (material == null) continue;
                ItemStack stack = new ItemStack(material);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(color(display));
                    meta.setLore(item.getStringList("lore").stream().map(s -> color(s.replace("%team%", team))).toList());
                    stack.setItemMeta(meta);
                }
                int slot = item.getInt("slot", -1);
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, stack);
            }
        }
        List<String> members = new ArrayList<>();
        String leaderId = teamData.getString(team + ".leader", "");
        if (!leaderId.isBlank()) members.add(leaderId);
        members.addAll(teamData.getStringList(team + ".members"));
        for (int i = 0; i < Math.min(27, members.size()); i++) {
            String memberId = members.get(i);
            UUID memberUuid;
            try { memberUuid = UUID.fromString(memberId); } catch (IllegalArgumentException ex) { continue; }
            Player online = Bukkit.getPlayer(memberUuid);
            String memberName = online != null ? online.getName() : Bukkit.getOfflinePlayer(memberUuid).getName();
            if (memberName == null) memberName = memberId;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta headMeta = head.getItemMeta();
            if (headMeta instanceof SkullMeta skull && online != null) skull.setOwningPlayer(online);
            if (headMeta != null) { headMeta.setDisplayName(color("&a" + memberName)); headMeta.setLore(List.of(color("&fClick to manage member"))); head.setItemMeta(headMeta); }
            inv.setItem(i, head);
        }
        player.openInventory(inv);
    }

    private void openMemberManager(Player viewer, String team, UUID targetId) {
        if (targetId.toString().equals(teamData.getString(team + ".leader", ""))) { viewer.sendMessage(teamMessage("cannot-edit-owner", "&cYou cannot manage the team owner.")); return; }
        Inventory inv = Bukkit.createInventory(new HomeHolder("member-manager", team + "|" + targetId), 27, color(memberGui.getString("gui.title", "&8Member Manager")));
        ConfigurationSection items = memberGui.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key); if (item == null) continue;
            String path = team + ".permissions." + targetId;
            boolean enabled = switch (key) {
                case "pvp_toggle" -> teamData.getBoolean(team + ".pvp", false);
                case "team_home_access" -> teamData.getBoolean(path + ".home-access", false);
                case "team_home_set" -> teamData.getBoolean(path + ".home-set", false);
                case "create_delete_teams" -> teamData.getBoolean(path + ".team-admin", false);
                case "manage_members" -> teamData.getBoolean(path + ".manage-members", false);
                default -> false;
            };
            String status = enabled ? item.getString("enabled_status", "&aAllowed") : item.getString("disabled_status", "&cDenied");
            String display = item.getString("title", item.getString("name", key));
            List<String> lore = item.getStringList("lore").stream().map(line -> color(line.replace("%status%", status))).toList();
            Material material = Material.matchMaterial(item.getString("material", "STONE")); if (material == null) continue;
            ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
            if (meta != null) { meta.setDisplayName(color(display)); meta.setLore(lore); stack.setItemMeta(meta); }
            int slot = item.getInt("slot", -1); if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, stack);
        }
        viewer.openInventory(inv);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("sethome") || name.equals("delhome") || name.equals("home")) {
            if (!(sender instanceof Player player)) { sender.sendMessage(homeMessage("player-only", "&cOnly players can use this command!")); return true; }
            if (name.equals("home")) {
                if (args.length == 0) { openHomes(player); return true; }
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!player.hasPermission("home.reload")) { send(player, "no-permission", "&cNo permission."); return true; }
                    load(); send(player, "reload-success", "&aHomes reloaded."); return true;
                }
                if (args[0].equalsIgnoreCase("team")) { openTeamMenu(player); return true; }
                int number = parseSlot(args[0]);
                if (number < 1 || number > 5) { send(player, "invalid-home", "&cInvalid home number!"); return true; }
                Location location = readLocation(homeData.getConfigurationSection(homePath(player, number)));
                if (location == null) { send(player, "home-not-found", "&fHome %home% was not found!".replace("%home%", args[0])); return true; }
                teleport(player, location, "home", "&aYou have been teleported to home " + number + "!");
                return true;
            }
            int number = args.length == 0 ? 1 : parseSlot(args[0]);
            if (number < 1 || number > 5) { send(player, "invalid-home", "&cUsage: /" + label + " <1-5>"); return true; }
            if (name.equals("sethome")) setPersonalHome(player, number);
            else {
                String path = homePath(player, number);
                if (!homeData.isConfigurationSection(path)) { send(player, "home-not-found", "&fHome %home% was not found!".replace("%home%", String.valueOf(number))); return true; }
                homeData.set(path, null); saveHomes();
                send(player, "home-deleted", "&fHome %home% has been deleted".replace("%home%", String.valueOf(number)));
            }
            return true;
        }
        if (name.equals("team")) return teamCommand(sender, args);
        return false;
    }

    private boolean teamCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) openTeamMenu(p);
            else sender.sendMessage(color("&e/team <create|invite|join|leave|delete|sethome|home|info>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("join")) {
            if (!(sender instanceof Player p)) return playerOnly(sender);
            if (args.length < 2) { sender.sendMessage(color("&cUsage: /team join <team>")); return true; }
            String team = findTeam(args[1]);
            if (team == null) { send(p, "team-not-found", "&cTeam not found!"); return true; }
            if (teamName(p) != null) { send(p, "team-already-in", "&cYou are already in a team!"); return true; }
            if (!pendingInvites.getOrDefault(p.getUniqueId(), "").equalsIgnoreCase(team)) {
                send(p, "team-no-invite", "&cYou don't have an invite to that team."); return true;
            }
            List<String> members = new ArrayList<>(teamData.getStringList(team + ".members"));
            if (!members.contains(p.getUniqueId().toString())) members.add(p.getUniqueId().toString());
            teamData.set(team + ".members", members); saveTeams(); pendingInvites.remove(p.getUniqueId());
            p.sendMessage(teamMessage("joined", "&fYou have joined team %team%").replace("%team%", team)); return true;
        }
        if (!(sender instanceof Player p)) return playerOnly(sender);
        String current = teamName(p);
        switch (sub) {
            case "create" -> {
                if (current != null) { send(p, "team-already-in", "&cYou are already in a team!"); return true; }
                if (args.length < 2) { p.sendMessage(color("&cUsage: /team create <name>")); return true; }
                String team = args[1].toLowerCase(Locale.ROOT);
                int min = teamConfig.getInt("min-team-name-length", 3), max = teamConfig.getInt("max-team-name-length", 9);
                if (!team.matches("[a-zA-Z0-9_]+") || team.length() < min || team.length() > max
                        || teamConfig.getStringList("blocked-team-names").stream().anyMatch(team::equalsIgnoreCase)) {
                    p.sendMessage(color("&cInvalid team name.")); return true;
                }
                if (findTeam(team) != null) { send(p, "team-already-exists", "&cA team with this name already exists!"); return true; }
                teamData.set(team + ".leader", playerKey(p)); teamData.set(team + ".members", new ArrayList<String>());
                teamData.set(team + ".pvp", false); saveTeams();
                p.sendMessage(teamMessage("created", "&fYour team %team% has been created").replace("%team%", team)); return true;
            }
            case "invite" -> {
                if (current == null) { send(p, "team-not-in", "&cYou are not in a team!"); return true; }
                if (!isLeader(p, current)) { send(p, "team-not-leader", "&cOnly the team leader can do that!"); return true; }
                if (args.length < 2) { p.sendMessage(color("&cUsage: /team invite <player>")); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { send(p, "team-player-not-found", "&cPlayer not found or offline."); return true; }
                if (teamName(target) != null) { send(p, "team-already-in", "&cThat player is already in a team."); return true; }
                pendingInvites.put(target.getUniqueId(), current);
                target.sendMessage(teamMessage("invite-received", "&fYou have been invited to team %team%. Use /team join %team%").replace("%team%", current));
                p.sendMessage(teamMessage("invite-sent", "&aInvitation sent to %player%.").replace("%player%", target.getName())); return true;
            }
            case "leave" -> {
                if (current == null) { send(p, "team-not-in", "&cYou are not in a team!"); return true; }
                if (isLeader(p, current)) { p.sendMessage(color("&cThe team leader must use /team delete.")); return true; }
                List<String> members = new ArrayList<>(teamData.getStringList(current + ".members"));
                members.remove(p.getUniqueId().toString()); teamData.set(current + ".members", members); saveTeams();
                send(p, "team-left", "&cYou have left your team."); return true;
            }
            case "delete" -> {
                if (current == null) { send(p, "team-not-in", "&cYou are not in a team!"); return true; }
                if (!isLeader(p, current) && !p.hasPermission("team.admin")) { send(p, "team-not-leader", "&cOnly the team leader can do that!"); return true; }
                teamData.set(current, null); saveTeams();
                p.sendMessage(teamMessage("deleted", "&cTeam %team% has been deleted!").replace("%team%", current)); return true;
            }
            case "sethome" -> {
                if (current == null) { send(p, "team-no-team", "&cYou don't have a team!"); return true; }
                if (!isLeader(p, current) && !p.hasPermission("team.home.set")) { send(p, "team-not-leader", "&cOnly the team leader can set the team home!"); return true; }
                if (!teamHomeEnabled()) { send(p, "team-home-disabled", "&cTeam homes are disabled."); return true; }
                ConfigurationSection section = teamData.getConfigurationSection(current + ".home");
                if (section == null) section = teamData.createSection(current + ".home");
                writeLocation(section, p.getLocation()); saveTeams();
                p.sendMessage(teamMessage("home-set", "&aTeam home has been set!")); return true;
            }
            case "home" -> {
                if (current == null) { send(p, "team-no-team", "&cYou don't have a team!"); return true; }
                if (!teamHomeEnabled()) { send(p, "team-home-disabled", "&cTeam homes are disabled."); return true; }
                if (p.hasPermission("team.home.access") || isLeader(p, current) || p.hasPermission("team.home.bypass")) {
                    Location loc = readLocation(teamData.getConfigurationSection(current + ".home"));
                    if (loc == null) { send(p, "team-no-home", "&cYour team has not set a home!"); return true; }
                    teleport(p, loc, "team", "&aYou have been teleported to Team home!"); return true;
                }
                send(p, "no-permission", "&cYou don't have permission for this!"); return true;
            }
            case "info" -> {
                if (current == null) { send(p, "team-not-in", "&cYou are not in a team!"); return true; }
                p.sendMessage(color("&aTeam: &f" + current + " &7| Members: " + (teamData.getStringList(current + ".members").size() + 1)
                        + " &7| Home: " + (teamData.isConfigurationSection(current + ".home") ? "&aSet" : "&cNot set")));
                return true;
            }
            case "reload" -> {
                if (!p.hasPermission("team.admin")) { send(p, "no-permission", "&cNo permission."); return true; }
                load(); p.sendMessage(color("&aHome and Team configuration reloaded.")); return true;
            }
            default -> { p.sendMessage(teamLang.getString("command-usage", "&c/team <create|invite|join|leave|delete|sethome|home|info>")); return true; }
        }
    }

    /** PlaceholderAPI identifiers: %home_count%, %home_max_homes%, %home_team_has_team%,
     * %home_teamhome_set%, %team_name%, %team_player%, %team_rank%, %team_leader%,
     * %team_count%, %team_home_set%, and %team_pvp%. */
    private final class HomeTeamExpansion extends PlaceholderExpansion {
        private final String identifier;
        private HomeTeamExpansion(String identifier) { this.identifier = identifier; }
        @Override public String getIdentifier() { return identifier; }
        @Override public String getAuthor() { return String.join(", ", plugin.getDescription().getAuthors()); }
        @Override public String getVersion() { return plugin.getDescription().getVersion(); }
        @Override public boolean persist() { return true; }

        @Override public String onPlaceholderRequest(Player player, String params) {
            if (player == null) return "";
            String key = params.toLowerCase(Locale.ROOT);
            if (identifier.equals("team")) {
                key = switch (key) {
                    case "name" -> "team_name"; case "player" -> "team_player"; case "rank" -> "team_rank";
                    case "leader" -> "team_leader"; case "count", "members" -> "team_count";
                    case "home_set", "homeset" -> "team_home_set"; case "pvp" -> "team_pvp"; default -> key;
                };
            }
            String team = teamName(player);
            switch (key) {
                case "count":
                case "homes_count":
                case "home_count": {
                    int count = 0;
                    for (int i = 1; i <= 5; i++) if (homeData.isConfigurationSection(homePath(player, i))) count++;
                    return String.valueOf(count);
                }
                case "max_homes": return String.valueOf(maxHomes(player));
                case "team_has_team": return String.valueOf(team != null);
                case "teamhome_set": return String.valueOf(team != null && teamData.isConfigurationSection(team + ".home"));
                case "team_name": return team == null ? "" : team;
                case "team_player": return player.getName();
                case "team_rank": return team == null ? "" : (isLeader(player, team) ? "leader" : "member");
                case "team_leader": {
                    if (team == null) return "";
                    String leaderId = teamData.getString(team + ".leader", "");
                    try {
                        org.bukkit.OfflinePlayer leader = Bukkit.getOfflinePlayer(UUID.fromString(leaderId));
                        return leader.getName() == null ? "" : leader.getName();
                    } catch (IllegalArgumentException ex) { return ""; }
                }
                case "team_count": return team == null ? "0" : String.valueOf(teamData.getStringList(team + ".members").size() + 1);
                case "team_home_set": return String.valueOf(team != null && teamData.isConfigurationSection(team + ".home"));
                case "team_pvp": return team == null ? "false" : String.valueOf(teamData.getBoolean(team + ".pvp", false));
                default: return null;
            }
        }
    }

    private String findTeam(String wanted) {
        for (String team : teamData.getKeys(false)) if (team.equalsIgnoreCase(wanted)) return team;
        return null;
    }
    private boolean playerOnly(CommandSender sender) { sender.sendMessage(homeMessage("player-only", "&cOnly players can use this command!")); return true; }

    @EventHandler public void onRenameChat(AsyncPlayerChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!pendingRename.contains(id)) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingRename.remove(id);
            Integer number = renameTarget.remove(id);
            Player player = event.getPlayer();
            if (number == null || !player.isOnline()) return;
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(homeMessage("rename-cancelled", "&cHome rename cancelled."));
                openHomes(player);
                return;
            }
            String name = color(input);
            if (name.length() > 32) name = name.substring(0, 32);
            if (name.isBlank()) name = String.valueOf(number);
            homeData.set(homePath(player, number) + ".name", name);
            saveHomes();
            player.sendMessage(homeMessage("rename-success", "&aHome renamed to %name%.").replace("%name%", name));
            openHomes(player);
        });
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof HomeHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (holder.type.equals("homes")) {
            int teamSlot = mainGui.getInt("teamhome.slot", 10);
            if (slot == teamSlot) {
                String team = teamName(player);
                if (team == null) { send(player, "team-no-team", "&cYou dont have a team!"); return; }
                Location loc = readLocation(teamData.getConfigurationSection(team + ".home"));
                if (loc == null) { send(player, "team-no-home", "&cYour team has not set a home!"); return; }
                if (!teamHomeEnabled()) { send(player, "team-home-disabled", "&cTeam homes are disabled."); return; }
                player.closeInventory(); teleport(player, loc, "team", "&aYou have been teleported to Team home!"); return;
            }
            String[] beds = mainGui.getString("homes.slot-beds", "11,12,13,14,15").split(",");
            String[] dyes = mainGui.getString("homes.slot-dyes", "20,21,22,23,24").split(",");
            for (int i = 0; i < 5; i++) {
                int number = i + 1;
                if (i < beds.length && parseSlot(beds[i]) == slot) {
                    if (number > maxHomes(player)) { send(player, "premium-locked", "&fThis home is premium locked!"); return; }
                    String path = homePath(player, number);
                    if (!homeData.isConfigurationSection(path)) {
                        setPersonalHome(player, number);
                        player.closeInventory();
                    } else if (event.getClick().isRightClick()) {
                        pendingRename.add(player.getUniqueId());
                        renameTarget.put(player.getUniqueId(), number);
                        player.closeInventory();
                        player.sendMessage(homeMessage("rename-prompt", "&eType the new name for home %home% in chat, or type cancel.")
                                .replace("%home%", String.valueOf(number)));
                    } else if (event.getClick().isLeftClick()) {
                        openConfirm(player, number);
                    }
                    return;
                }
                if (i < dyes.length && parseSlot(dyes[i]) == slot) {
                    if (homeData.isConfigurationSection(homePath(player, number))) openConfirm(player, number);
                    else if (number <= maxHomes(player)) { setPersonalHome(player, number); player.closeInventory(); }
                    else send(player, "premium-locked", "&fThis home is premium locked!");
                    return;
                }
            }
        } else if (holder.type.equals("confirm")) {
            ConfigurationSection items = confirmGui.getConfigurationSection("items");
            int cancel = items == null ? 11 : items.getInt("cancel.slot", 11);
            int confirm = items == null ? 15 : items.getInt("confirm.slot", 15);
            int number = parseSlot(holder.data);
            if (slot == cancel) { pendingDelete.remove(player.getUniqueId()); openHomes(player); }
            else if (slot == confirm) {
                homeData.set(homePath(player, number), null); saveHomes(); pendingDelete.remove(player.getUniqueId());
                send(player, "home-deleted", "&fHome %home% has been deleted".replace("%home%", String.valueOf(number)));
                openHomes(player);
            }
        } else if (holder.type.equals("team")) {
            String team = teamName(player);
            if (team == null) { player.closeInventory(); send(player, "team-no-team", "&cYou don't have a team!"); return; }
            int homeSlot = teamGui.getInt("items.team-home.slot", 52);
            int pvpSlot = teamGui.getInt("items.pvp.slot", 53);
            if (slot == pvpSlot) {
                if (!isLeader(player, team) && !player.hasPermission("team.admin")) {
                    send(player, "team-not-leader", "&cOnly the team leader can change team PvP."); return;
                }
                boolean enabled = !teamData.getBoolean(team + ".pvp", false);
                teamData.set(team + ".pvp", enabled);
                saveTeams();
                player.sendMessage(teamMessage(enabled ? "pvp-enabled" : "pvp-disabled",
                        enabled ? "&aTeam-PVP has been enabled!" : "&cTeam-PVP has been disabled!"));
                openTeamMenu(player);
                return;
            }
            if (slot == homeSlot) {
                if (!teamHomeEnabled()) { send(player, "team-home-disabled", "&cTeam homes are disabled."); return; }
                Location loc = readLocation(teamData.getConfigurationSection(team + ".home"));
                if (loc == null) {
                    if (isLeader(player, team)) send(player, "team-home-set", "&aUse /team sethome to set the home.");
                    else send(player, "team-no-home", "&cYour team has not set a home!");
                    return;
                }
                if (!teamData.getBoolean(team + ".home-access", true)
                        && !isLeader(player, team) && !player.hasPermission("team.home.bypass")) {
                    send(player, "no-permission", "&cYou don't have permission for this!"); return;
                }
                player.closeInventory(); teleport(player, loc, "team", "&aYou have been teleported to Team home!");
            }
        }
    }

    @EventHandler public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            pendingDelete.remove(player.getUniqueId());
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("team") && args.length == 1) return List.of("create","invite","join","leave","delete","sethome","home","info","reload").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if ((name.equals("home") || name.equals("sethome") || name.equals("delhome")) && args.length == 1)
            return name.equals("home") ? List.of("1","2","3","4","5","team","reload") : List.of("1","2","3","4","5");
        return Collections.emptyList();
    }

    private static final class HomeHolder implements InventoryHolder {
        private final String type;
        private final String data;
        private HomeHolder(String type, String data) { this.type = type; this.data = data; }
        @Override public Inventory getInventory() { return null; }
    }
}
