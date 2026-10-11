package com.archiveaustxn;

import com.archiveaustxn.eco.EconomyModule;
import com.archiveaustxn.eco.command.EconomyCommand;
import com.archiveaustxn.eco.history.PaymentHistoryListener;
import com.archiveaustxn.eco.storage.PaymentHistoryStore;
import com.archiveaustxn.sell.SellModule;
import com.archiveaustxn.shop.ShopModule;
import com.archiveaustxn.shop.command.ShopCommand;
import com.archiveaustxn.storage.CoreDatabase;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Main entry point; owns and wires the shared database and all EconomySMP modules. */
public final class EconomySMP extends JavaPlugin {
    private CoreDatabase database;
    private ShopModule shopModule;
    private EconomyModule economyModule;
    private PaymentHistoryStore paymentHistoryStore;
    private SellModule sellModule;

    @Override
    public void onEnable() {
        if (!getDataFolder().isDirectory() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data directory: " + getDataFolder());
        }

        // Initialize one SQLite database before modules so they can share its tables/connection.
        database = new CoreDatabase(this);
        economyModule = new EconomyModule(this);
        economyModule.load();

        paymentHistoryStore = new PaymentHistoryStore(this);
        getServer().getPluginManager().registerEvents(
            new PaymentHistoryListener(this, paymentHistoryStore, economyModule), this);

        shopModule = new ShopModule(this);
        shopModule.load();

        sellModule = new SellModule(this);
        sellModule.load();
        getServer().getPluginManager().registerEvents(sellModule, this);

        ShopCommand shopHandler = new ShopCommand(this, shopModule);
        bind("shop", shopHandler, shopHandler);

        EconomyCommand economyHandler = new EconomyCommand(this, economyModule, paymentHistoryStore);
        bind("pay", economyHandler, economyHandler);
        bind("money", economyHandler, economyHandler);
        bind("eco", economyHandler, economyHandler);
        bind("payhistory", economyHandler, economyHandler);
        bind("paytoggle", economyHandler, economyHandler);

        bind("sell", sellModule, sellModule);
        bind("worth", sellModule, sellModule);
        bind("sellaxe", sellModule, sellModule);
        bind("worthtoggle", sellModule, sellModule);
        bind("sellhistory", sellModule, sellModule);
        bind("setworth", sellModule, sellModule);

        getLogger().info("EconomySMP Core enabled.");
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor,
                      org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command /" + name + " is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    @Override
    public void onDisable() {
        if (shopModule != null) shopModule.close();\n        if (economyModule != null) economyModule.close();
        if (paymentHistoryStore != null) paymentHistoryStore.close();
        if (database != null) database.close();
    }

    public ShopModule getShopModule() { return shopModule; }
    public EconomyModule getEconomyModule() { return economyModule; }
    public PaymentHistoryStore getPaymentHistoryStore() { return paymentHistoryStore; }
    public CoreDatabase getDatabase() { return database; }
    public SellModule getSellModule() { return sellModule; }
}
