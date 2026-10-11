package com.archiveaustxn.sell.service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Thread-safe in-memory sell ledger. Entries are intentionally immutable. */
public final class SellHistoryService {
    public record Entry(Instant time, UUID playerId, String playerName, String material, int amount, double total) {}
    private static final int MAX_ENTRIES_PER_PLAYER = 500;
    private final Map<UUID, Deque<Entry>> entries = new ConcurrentHashMap<>();
    public void record(UUID id, String name, String material, int amount, double total) {
        Deque<Entry> list = entries.computeIfAbsent(id, ignored -> new ConcurrentLinkedDeque<>());
        list.addFirst(new Entry(Instant.now(), id, name, material, amount, total));
        while (list.size() > MAX_ENTRIES_PER_PLAYER) list.pollLast();
    }
    public List<Entry> recent(UUID id, int limit) {
        if (limit <= 0) return List.of();
        Deque<Entry> list = entries.get(id);
        if (list == null) return List.of();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : list) { if (result.size() >= limit) break; result.add(entry); }
        return List.copyOf(result);
    }
    public double totalEarned(UUID id) { return recent(id, Integer.MAX_VALUE).stream().mapToDouble(Entry::total).sum(); }
    public int totalItems(UUID id) { return recent(id, Integer.MAX_VALUE).stream().mapToInt(Entry::amount).sum(); }
}
