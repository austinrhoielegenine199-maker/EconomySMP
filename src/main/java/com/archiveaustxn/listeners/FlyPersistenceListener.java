package com.archiveaustxn.listeners;

import com.archiveaustxn.EconomySMP;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class FlyPersistenceListener implements Listener {
    private final EconomySMP plugin;
    private final File file;
    private YamlConfiguration data;
    public FlyPersistenceListener(EconomySMP plugin) {
        this.plugin=plugin;
        this.file=new File(plugin.getDataFolder(),"fly-states.yml");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.data=YamlConfiguration.loadConfiguration(file);
    }
    private String key(Player p) { return p.getUniqueId().toString()+".enabled"; }
    private void saveFile() {
        try { data.save(file); } catch(IOException e) { plugin.getLogger().severe("Could not save fly states: "+e.getMessage()); }
    }
    private void save(Player p) {
        if (p.getGameMode()==GameMode.CREATIVE || p.getGameMode()==GameMode.SPECTATOR) return;
        data.set(key(p), p.getAllowFlight() || p.isFlying());
        saveFile();
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { save(e.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p=e.getPlayer();
        save(p);
        new BukkitRunnable() { @Override public void run() { restore(p); } }.runTask(plugin);
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p=e.getPlayer();
        new BukkitRunnable() { @Override public void run() { restore(p); } }.runTask(plugin);
    }
    private void restore(Player p) {
        if (!p.isOnline() || p.getGameMode()==GameMode.CREATIVE || p.getGameMode()==GameMode.SPECTATOR) return;
        if (!data.contains(key(p))) return;
        boolean enabled=data.getBoolean(key(p));
        boolean allowed=p.hasPermission("economysmp.player.fly") || p.hasPermission("economysmp.perks.fly") || p.hasPermission("economysmp.staff.fly") || p.hasPermission("economysmp.staff.*");
        if (enabled && allowed) { p.setAllowFlight(true); p.setFlying(true); }
        else { p.setFlying(false); p.setAllowFlight(false); }
        data.set(key(p), enabled && allowed);
        saveFile();
    }
}
