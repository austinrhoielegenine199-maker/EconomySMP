package com.archiveaustxn.shop;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomySMP extends JavaPlugin {
    private ShopModule shopModule;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.shopModule = new ShopModule(this);
        shopModule.load();
        PluginCommand command = getCommand("shop");
        if (command != null) {
            ShopCommand handler = new ShopCommand(this, shopModule);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        getLogger().info("EconomySMP Core enabled.");
    }

    @Override
    public void onDisable() {
        if (shopModule != null) shopModule.close();
    }

    public ShopModule getShopModule() {
        return shopModule;
    }
}
