package com.example.aas.deck;

import com.example.aas.world.AASWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory undo history of the kit / deck admin menu (one stack per admin, lost on server restart).
 * A snapshot holds the full kit map and / or the deck params of one or more (team, deck) pairs
 * and is taken BEFORE a destructive change.
 */
public final class KitUndo {
    private KitUndo() {}

    public static final int MAX_ENTRIES = 40;

    private record Snap(String team, DeckType deck, Map<String, CompoundTag> kits, DeckParams params) {}

    private record Entry(String label, List<Snap> snaps) {}

    private static final Map<UUID, ArrayDeque<Entry>> STACKS = new HashMap<>();

    /**
     * @param team   "BLUE", "RED" or "BOTH"
     * @param deck   one deck, or null = all decks
     * @param kits   store the kits
     * @param params store the deck params
     */
    public static void push(ServerPlayer player, AASWorldData data, String label, String team,
                            DeckType deck, boolean kits, boolean params) {
        if (player == null || data == null || (!kits && !params)) return;
        List<String> teams = new ArrayList<>();
        if ("BOTH".equalsIgnoreCase(team)) {
            teams.add("BLUE");
            teams.add("RED");
        } else if ("BLUE".equalsIgnoreCase(team) || "RED".equalsIgnoreCase(team)) {
            teams.add(team.toUpperCase(Locale.ROOT));
        } else {
            return;
        }

        List<Snap> snaps = new ArrayList<>();
        for (String t : teams) {
            for (DeckType d : DeckType.VALUES) {
                if (deck != null && d != deck) continue;
                Map<String, CompoundTag> kitCopy = null;
                if (kits) {
                    kitCopy = new LinkedHashMap<>();
                    for (Map.Entry<String, AASWorldData.KitInfo> e : data.kitsFor(t, d).entrySet()) {
                        kitCopy.put(e.getKey(), e.getValue().save());
                    }
                }
                DeckParams paramCopy = params ? data.paramsFor(t, d).copy() : null;
                snaps.add(new Snap(t, d, kitCopy, paramCopy));
            }
        }

        String l = label == null ? "change" : label;
        if (l.length() > 120) l = l.substring(0, 120);
        ArrayDeque<Entry> stack = STACKS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        stack.addLast(new Entry(l, snaps));
        while (stack.size() > MAX_ENTRIES) stack.pollFirst();
    }

    public static int depth(ServerPlayer player) {
        ArrayDeque<Entry> s = STACKS.get(player.getUUID());
        return s == null ? 0 : s.size();
    }

    /** Label of the change that the next undo would revert, "" if there is nothing to undo. */
    public static String peekLabel(ServerPlayer player) {
        ArrayDeque<Entry> s = STACKS.get(player.getUUID());
        return s == null || s.isEmpty() ? "" : s.peekLast().label();
    }

    /** Reverts the last change. @return its label, or null if the history is empty. */
    public static String undo(ServerPlayer player, AASWorldData data) {
        ArrayDeque<Entry> s = STACKS.get(player.getUUID());
        if (s == null || s.isEmpty()) return null;
        Entry e = s.pollLast();
        for (Snap snap : e.snaps()) {
            if (snap.kits() != null) {
                Map<String, AASWorldData.KitInfo> target = data.kitsFor(snap.team(), snap.deck());
                target.clear();
                for (Map.Entry<String, CompoundTag> k : snap.kits().entrySet()) {
                    target.put(k.getKey(), AASWorldData.KitInfo.load(k.getValue()));
                }
            }
            if (snap.params() != null) {
                DeckParams p = data.paramsFor(snap.team(), snap.deck());
                p.enabled = snap.params().enabled;
                p.hubLimit = snap.params().hubLimit;
                p.crateBonus = snap.params().crateBonus;
                p.tickets = snap.params().tickets;
            }
        }
        return e.label();
    }
}