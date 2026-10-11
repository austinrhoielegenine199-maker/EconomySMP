package com.archiveaustxn.eco.storage;

import com.archiveaustxn.EconomySMP;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Lightweight YAML-backed payment history; each record stores UUIDs and immutable transaction details. */
public final class PaymentHistoryStore {
    private final EconomySMP plugin;
    private final File file;
    private YamlConfiguration data;

    public PaymentHistoryStore(EconomySMP plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "core/eco");
        dir.mkdirs();
        this.file = new File(dir, "payment-history-data.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }
    public synchronized void record(UUID from, String fromName, UUID to, String toName, double amount) {
        long id = System.currentTimeMillis();
        String key = "transactions." + id;
        while (data.contains(key)) key += "-x";
        data.set(key + ".id", key.substring("transactions.".length()));
        data.set(key + ".from-uuid", from.toString());
        data.set(key + ".from", fromName);
        data.set(key + ".to-uuid", to.toString());
        data.set(key + ".to", toName);
        data.set(key + ".amount", amount);
        data.set(key + ".timestamp", Instant.now().toEpochMilli());
        save();
    }
    public synchronized List<Record> find(UUID player, int pageSize, int page, String sort) {
        List<Record> result = new ArrayList<>();
        ConfigurationSection root = data.getConfigurationSection("transactions");
        if (root != null) for (String id : root.getKeys(false)) {
            String path = "transactions." + id;
            if (!player.toString().equals(data.getString(path + ".from-uuid")) && !player.toString().equals(data.getString(path + ".to-uuid"))) continue;
            result.add(new Record(data.getString(path + ".id", id), data.getString(path + ".from", "?"),
                data.getString(path + ".to", "?"), data.getDouble(path + ".amount"),
                data.getLong(path + ".timestamp")));
        }
        Comparator<Record> comparator = switch (sort == null ? "newest" : sort) {
            case "oldest" -> Comparator.comparingLong(Record::timestamp);
            case "highest_price" -> Comparator.comparingDouble(Record::amount).reversed();
            case "lowest_price" -> Comparator.comparingDouble(Record::amount);
            default -> Comparator.comparingLong(Record::timestamp).reversed();
        };
        result.sort(comparator);
        int start = Math.max(0, page - 1) * Math.max(1, pageSize);
        if (start >= result.size()) return List.of();
        return List.copyOf(result.subList(start, Math.min(result.size(), start + Math.max(1, pageSize))));
    }
    private void save() {
        try { data.save(file); }
        catch (IOException exception) { plugin.getLogger().severe("Could not save payment history: " + exception.getMessage()); }
    }
    public record Record(String id, String from, String to, double amount, long timestamp) {
        public String date() { return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp)); }
    }
}
