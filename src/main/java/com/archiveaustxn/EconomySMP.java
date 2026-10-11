package com.archiveaustxn;

import com.archiveaustxn.shop.ShopModule;
import com.archiveaustxn.shop.command.ShopCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for EconomySMP Core.
 * Feature modules are initialized and shut down through this class.
 */
public final class EconomySMP extends JavaPlugin {
    private ShopModule shopModule;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        shopModule = new ShopModule(this);
        shopModule.load();

        PluginCommand command = getCommand("shop");
        if (command != null) {
            ShopCommand handler = new ShopCommand(this, shopModule);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().severe("The /shop command is missing from plugin.yml.");
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
