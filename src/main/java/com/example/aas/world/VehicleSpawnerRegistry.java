package com.example.aas.world;

import com.example.aas.block.VehicleSpawnerBlockEntity;
import com.example.aas.deck.DeckSpawnConfig;
import com.example.aas.deck.DeckType;
import com.example.aas.item.SupplyTruckMarkerItem;
import com.example.aas.item.VehicleMarkerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent list of every vehicle spawner block known in a level, with a snapshot of its config.
 *
 * WHY: spawner block entities only exist (and tick) in loaded chunks, and the mod has no global
 * list of them (only markedVehicles / pendingSpawnerRespawns, which hold spawners that ALREADY
 * spawned a vehicle). The panel needs the "total" count per type, so we keep our own registry.
 *
 * Spawners register themselves in VehicleSpawnerBlockEntity#onLoad and are removed in
 * VehicleSpawnerBlock#onRemove. While a chunk is loaded the snapshot is refreshed from the live
 * block entity by VehicleListService; for unloaded chunks the last snapshot is used.
 *
 * Decks: a spawner has one vehicle id PER DECK (deckVehicles: deck id -> vehicle id). Old worlds stored a single
 * "VehicleId"; it is read as the COMBINED deck's vehicle and only the new format is written.
 */
public class VehicleSpawnerRegistry extends SavedData {

    private static final String DATA_NAME = "aas_vehicle_spawner_registry";

    /** Last known config of one spawner. */
    public record Snapshot(String team, String markerType, int penalty, Map<String, String> deckVehicles,
                           boolean hasSpawnedOnce, long targetSpawnTick,
                           Map<String, String> deckTypes, Map<String, Integer> deckPenalties) {

        /** Vehicle class marker (APC / Tank / ...) of the given deck; every deck has its own modifier. */
        public String markerType(DeckType deck) {
            String v = deckTypes.get(deck.id);
            return v != null ? v : markerType;
        }

        public int penalty(DeckType deck) {
            Integer v = deckPenalties.get(deck.id);
            return v != null ? v : penalty;
        }

        /** Vehicle id of the given deck, "" if the spawner spawns nothing for it. */
        public String vehicleId(DeckType deck) {
            String v = deckVehicles.get(deck.id);
            return v == null ? "" : v;
        }

        /**
         * Reads the same data the spawner uses when it spawns a vehicle:
         * team / type / penalty come from the marker item in slot 0 (see VehicleSpawnerBlockEntity#spawnVehicle).
         */
        public static Snapshot of(VehicleSpawnerBlockEntity be) {
            String team = be.spawnerTeam();
            String type = "";
            int penalty = 0;
            boolean baseSet = false;
            Map<String, String> vehicles = new HashMap<>();
            Map<String, String> types = new HashMap<>();
            Map<String, Integer> penalties = new HashMap<>();
            boolean powered = be.hasRedstoneSignal();   // cached, refreshed at the 4 check points
            for (DeckType deck : DeckType.VALUES) {
                DeckSpawnConfig c = be.deckConfigs.get(deck);
                if (c == null) continue;
                // "redstone on start" + no redstone signal = the spawner is off, it is not listed (at any time, not only after start)
                boolean offNoRedstone = c.redstoneOnStart && !powered;
                if (c.hasVehicle() && !offNoRedstone) vehicles.put(deck.id, c.vehicleIdTrimmed());
                ItemStack modifier = c.items[0];
                String mType = null;
                int mPenalty = 0;
                if (modifier.getItem() instanceof VehicleMarkerItem vm) {
                    mType = vm.getType();
                    mPenalty = vm.getPenalty();
                } else if (modifier.getItem() instanceof SupplyTruckMarkerItem stm) {
                    mType = stm.getVehicleType();
                    mPenalty = stm.getPenalty();
                }
                if (mType != null) {
                    types.put(deck.id, mType);
                    penalties.put(deck.id, mPenalty);
                    if (!baseSet || deck == DeckType.COMBINED) {
                        type = mType;
                        penalty = mPenalty;
                        baseSet = true;
                    }
                }
            }
            return new Snapshot(team, type, penalty, Map.copyOf(vehicles), be.hasSpawnedOnce, be.targetSpawnTick,
                    Map.copyOf(types), Map.copyOf(penalties));
        }
    }

    private final Map<BlockPos, Snapshot> spawners = new HashMap<>();

    public static VehicleSpawnerRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VehicleSpawnerRegistry::load, VehicleSpawnerRegistry::new, DATA_NAME);
    }

    /** Adds the spawner, or refreshes its snapshot if it changed. */
    public void put(BlockPos pos, Snapshot snapshot) {
        Snapshot old = spawners.put(pos.immutable(), snapshot);
        if (!snapshot.equals(old)) setDirty();
    }

    public void unregister(BlockPos pos) {
        if (spawners.remove(pos) != null) setDirty();
    }

    /** Copy, so callers can modify the registry while iterating. */
    public Map<BlockPos, Snapshot> copy() {
        return new HashMap<>(spawners);
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Snapshot> e : spawners.entrySet()) {
            Snapshot s = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey().asLong());
            t.putString("Team", s.team());
            t.putString("Type", s.markerType());
            t.putInt("Penalty", s.penalty());
            CompoundTag dv = new CompoundTag();
            s.deckVehicles().forEach(dv::putString);
            t.put("DeckVehicles", dv);
            CompoundTag dt = new CompoundTag();
            s.deckTypes().forEach(dt::putString);
            t.put("DeckTypes", dt);
            CompoundTag dp = new CompoundTag();
            s.deckPenalties().forEach(dp::putInt);
            t.put("DeckPenalties", dp);
            t.putBoolean("Spawned", s.hasSpawnedOnce());
            t.putLong("Target", s.targetSpawnTick());
            list.add(t);
        }
        tag.put("Spawners", list);
        return tag;
    }

    public static VehicleSpawnerRegistry load(CompoundTag tag) {
        VehicleSpawnerRegistry reg = new VehicleSpawnerRegistry();
        ListTag list = tag.getList("Spawners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Map<String, String> vehicles = new HashMap<>();
            if (t.contains("DeckVehicles", Tag.TAG_COMPOUND)) {
                CompoundTag dv = t.getCompound("DeckVehicles");
                for (String k : dv.getAllKeys()) vehicles.put(k, dv.getString(k));
            } else {
                // old format: one vehicle id = the COMBINED deck
                String legacy = t.getString("VehicleId");
                if (!legacy.isEmpty()) vehicles.put(DeckType.COMBINED.id, legacy);
            }
            Map<String, String> types = new HashMap<>();
            Map<String, Integer> penalties = new HashMap<>();
            if (t.contains("DeckTypes", Tag.TAG_COMPOUND)) {
                CompoundTag dt = t.getCompound("DeckTypes");
                for (String k : dt.getAllKeys()) types.put(k, dt.getString(k));
            }
            if (t.contains("DeckPenalties", Tag.TAG_COMPOUND)) {
                CompoundTag dp = t.getCompound("DeckPenalties");
                for (String k : dp.getAllKeys()) penalties.put(k, dp.getInt(k));
            }
            reg.spawners.put(BlockPos.of(t.getLong("Pos")), new Snapshot(
                    t.getString("Team"), t.getString("Type"), t.getInt("Penalty"),
                    Map.copyOf(vehicles), t.getBoolean("Spawned"), t.getLong("Target"),
                    Map.copyOf(types), Map.copyOf(penalties)));
        }
        return reg;
    }
}
