package com.archiveaustxn.shop;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

final class HandlerListUtil {
    private HandlerListUtil() {}
    static void unregister(Listener listener) { HandlerList.unregisterAll(listener); }
}
