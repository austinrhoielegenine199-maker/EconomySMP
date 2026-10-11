package com.archiveaustxn;

import com.archiveaustxn.eco.EconomyModule;
import com.archiveaustxn.eco.command.EconomyCommand;
import com.archiveaustxn.eco.storage.PaymentHistoryStore;
import com.archiveaustxn.eco.history.PaymentHistoryListener;
import com.archiveaustxn.shop.ShopModule;
import com.archiveaustxn.shop.command.ShopCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Main entry point; owns and wires the EconomySMP Core modules. */
public final class EconomySMP extends JavaPlugin {
    private ShopModule shopModule;
    private EconomyModule economyModule;
    private PaymentHistoryStore paymentHistoryStore;

    @Override public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        economyModule = new EconomyModule(this);
        economyModule.load();
        paymentHistoryStore = new PaymentHistoryStore(this);
        getServer().getPluginManager().registerEvents(new PaymentHistoryListener(this, paymentHistoryStore, economyModule), this);
        shopModule = new ShopModule(this);
        shopModule.load();

        ShopCommand shopHandler = new ShopCommand(this, shopModule);
        bind("shop", shopHandler, shopHandler);
        EconomyCommand economyHandler = new EconomyCommand(this, economyModule, paymentHistoryStore);
        bind("pay", economyHandler, economyHandler);
        bind("money", economyHandler, economyHandler);
        bind("eco", economyHandler, economyHandler);
        bind("payhistory", economyHandler, economyHandler);
        bind("paytoggle", economyHandler, economyHandler);
        getLogger().info("EconomySMP Core enabled.");
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor,
                      org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) { getLogger().severe("Command /" + name + " is missing from plugin.yml."); return; }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    @Override public void onDisable() {
        if (shopModule != null) shopModule.close();
    }
    public ShopModule getShopModule() { return shopModule; }
    public EconomyModule getEconomyModule() { return economyModule; }
    public PaymentHistoryStore getPaymentHistoryStore() { return paymentHistoryStore; }
}
