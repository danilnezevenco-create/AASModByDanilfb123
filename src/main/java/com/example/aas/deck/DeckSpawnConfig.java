package com.example.aas.deck;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Vehicle spawner settings of ONE deck. A spawner block keeps one of these per DeckType; the server uses
 * the config of the deck the spawner's team won with. No config / empty vehicleId / enabled == false means
 * "nothing spawns" for that deck.
 */
public final class DeckSpawnConfig {
    public boolean enabled = true;
    public String vehicleId = "";
    public float yaw = 0;
    public int initialTime = 60;
    public int respawnTime = 60;
    public boolean autoReturnEnabled = false;
    public int autoReturnTime = 60;
    public boolean autoReturnDestroy = false;
    /** Put a redstone block next to the spawner when the match starts, remove it when the match stops. */
    public boolean redstoneOnStart = false;

    /** Slot 0 = modifier (team / class marker: APC, Tank, ...), slots 1..32 = items put into the spawned vehicle. One set PER DECK. */
    public static final int SLOTS = 33;
    public final ItemStack[] items = new ItemStack[SLOTS];

    public DeckSpawnConfig() {
        java.util.Arrays.fill(items, ItemStack.EMPTY);
    }

    public boolean hasVehicle() {
        return enabled && vehicleId != null && !vehicleId.trim().isEmpty();
    }

    public String vehicleIdTrimmed() {
        return vehicleId == null ? "" : vehicleId.trim();
    }

    public DeckSpawnConfig copy() {
        DeckSpawnConfig c = new DeckSpawnConfig();
        c.enabled = enabled;
        c.vehicleId = vehicleId;
        c.yaw = yaw;
        c.initialTime = initialTime;
        c.respawnTime = respawnTime;
        c.autoReturnEnabled = autoReturnEnabled;
        c.autoReturnTime = autoReturnTime;
        c.autoReturnDestroy = autoReturnDestroy;
        c.redstoneOnStart = redstoneOnStart;
        for (int i = 0; i < SLOTS; i++) c.items[i] = items[i].copy();
        return c;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("Enabled", enabled);
        t.putString("VehicleID", vehicleId == null ? "" : vehicleId);
        t.putFloat("Yaw", yaw);
        t.putInt("InitialTime", initialTime);
        t.putInt("RespawnTime", respawnTime);
        t.putBoolean("AutoReturnEnabled", autoReturnEnabled);
        t.putInt("AutoReturnTime", autoReturnTime);
        t.putBoolean("AutoReturnDestroy", autoReturnDestroy);
        t.putBoolean("RedstoneOnStart", redstoneOnStart);
        ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            if (items[i].isEmpty()) continue;
            CompoundTag it = new CompoundTag();
            it.putByte("Slot", (byte) i);
            items[i].save(it);
            list.add(it);
        }
        t.put("Items", list);
        return t;
    }

    public static DeckSpawnConfig load(CompoundTag t) {
        DeckSpawnConfig c = new DeckSpawnConfig();
        if (t.contains("Enabled")) c.enabled = t.getBoolean("Enabled");
        c.vehicleId = t.getString("VehicleID");
        c.yaw = t.getFloat("Yaw");
        if (t.contains("InitialTime")) c.initialTime = t.getInt("InitialTime");
        if (t.contains("RespawnTime")) c.respawnTime = t.getInt("RespawnTime");
        c.autoReturnEnabled = t.getBoolean("AutoReturnEnabled");
        if (t.contains("AutoReturnTime")) c.autoReturnTime = t.getInt("AutoReturnTime");
        c.autoReturnDestroy = t.getBoolean("AutoReturnDestroy");
        c.redstoneOnStart = t.getBoolean("RedstoneOnStart");
        ListTag list = t.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag it = list.getCompound(i);
            int slot = it.getByte("Slot") & 255;
            if (slot < SLOTS) c.items[slot] = ItemStack.of(it);
        }
        return c;
    }
}
