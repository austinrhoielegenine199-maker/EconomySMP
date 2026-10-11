package com.archiveaustxn.sell.history;

import com.archiveaustxn.EconomySMP;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Simple persistent per-player sell history stored in plugins/EconomySMP/core/sell/history.yml. */
public final class SellHistoryService {
    public static final class Entry {
        private final long timestamp;
        private final int itemCount;
        private final double total;

        public Entry(long timestamp, int itemCount, double total) {
            this.timestamp = timestamp;
            this.itemCount = itemCount;
            this.total = total;
        }

        public long getTimestamp() { return timestamp; }
        public int getItemCount() { return itemCount; }
        public double getTotal() { return total; }
        public String getIsoTime() { return Instant.ofEpochMilli(timestamp).toString(); }
    }

    private final File file;
    private YamlConfiguration data;

    public SellHistoryService(EconomySMP plugin) {
        File directory = new File(plugin.getDataFolder(), "core/sell");
        if (!directory.exists()) directory.mkdirs();
        this.file = new File(directory, "history.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void record(UUID playerId, int itemCount, double total) throws IOException {
        String base = "players." + playerId;
        List<MapEntry> entries = readRaw(base + ".entries");
        entries.add(new MapEntry(System.currentTimeMillis(), itemCount, total));
        while (entries.size() > 50) entries.remove(0);
        data.set(base + ".entries", null);
        for (int i = 0; i < entries.size(); i++) {
            MapEntry entry = entries.get(i);
            String path = base + ".entries." + i;
            data.set(path + ".timestamp", entry.timestamp);
            data.set(path + ".items", entry.items);
            data.set(path + ".total", entry.total);
        }
        data.save(file);
    }

    public synchronized List<Entry> getRecent(UUID playerId) {
        List<Entry> result = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("players." + playerId + ".entries");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) continue;
            result.add(new Entry(row.getLong("timestamp"), row.getInt("items"), row.getDouble("total")));
        }
        result.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return Collections.unmodifiableList(result);
    }

    private List<MapEntry> readRaw(String path) {
        List<MapEntry> entries = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection(path);
        if (section == null) return entries;
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row != null) entries.add(new MapEntry(row.getLong("timestamp"), row.getInt("items"), row.getDouble("total")));
        }
        entries.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
        return entries;
    }

    private static final class MapEntry {
        private final long timestamp;
        private final int items;
        private final double total;
        private MapEntry(long timestamp, int items, double total) {
            this.timestamp = timestamp; this.items = items; this.total = total;
        }
    }
}
