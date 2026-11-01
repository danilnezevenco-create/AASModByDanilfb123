package com.example.aas.block;

import com.example.aas.item.SupplyTruckMarkerItem;
import com.example.aas.item.VehicleMarkerItem;
import com.example.aas.menu.VehicleSpawnerMenu;
import com.example.aas.world.AASWorldData;
import com.example.aas.world.VehicleSpawnerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import com.example.aas.network.PacketHandler;
import java.util.UUID;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import com.example.aas.deck.DeckSpawnConfig;
import com.example.aas.deck.DeckType;

public class VehicleSpawnerBlockEntity extends BlockEntity implements MenuProvider {

    /**
     * The GUI buffer: shows the modifier + vehicle inventory of the deck that is being edited ({@link #editDeck}).
     * Every change is mirrored into that deck's {@link DeckSpawnConfig#items}, so each deck keeps its OWN modifier
     * (team / vehicle class) and its own vehicle inventory.
     */
    public final ItemStackHandler inventory = new ItemStackHandler(DeckSpawnConfig.SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            if (!bufferLoading) cfg(editDeck).items[slot] = getStackInSlot(slot).copy();
            setChanged();
        }
    };
    private boolean bufferLoading = false;
    /** The deck whose modifier / inventory the GUI buffer currently shows (set by the admin in the spawner GUI). */
    public DeckType editDeck = DeckType.COMBINED;

    /** One vehicle config per deck. The server uses the config of the deck the spawner's team plays with. */
    public final Map<DeckType, DeckSpawnConfig> deckConfigs = new EnumMap<>(DeckType.class);
    private boolean migratedLegacyConfig = false;

    public long targetSpawnTick = 0;
    public boolean hasSpawnedOnce = false;
    private UUID lastVehicleUUID = null;
    private int loadTimer = 60;

    // === AUTO-RETURN OF VEHICLES ===
    // If the vehicle is empty (no players) for longer than autoReturnTime seconds and is outside the main zone
    // of its team, it is teleported back to the spawn or (if autoReturnDestroy == true) destroyed. The check
    // itself runs globally in GameLogicEvents#handleVehicleAutoReturn, not here, because the tick of this
    // block entity does not run in unloaded chunks.

    // "redstone on start": position of the redstone block this spawner placed (null = none) and whether it was tried this match
    private BlockPos redstonePos = null;
    private boolean redstoneTried = false;

    /**
     * Server-side cached redstone state of the spawner. It is NOT read live every tick, it is refreshed only at
     * the check points (see {@link #recheckRedstone()} / {@link #recheckAll(ServerLevel)}):
     *   1. the block is placed / a neighbor block updates   (VehicleSpawnerBlock#onPlace, #neighborChanged, onLoad)
     *   2. /aas prepare start                               (DeckManager#start)
     *   3. the deck-pick screen is over, squad selection    (DeckManager#finishPreparation)
     *   4. the game starts                                  (GameLogicEvents, after isGameStarted = true)
     */
    private boolean redstoneSignal = false;

    // Once, on the first tick after the block appears in the world, the landscape around it is scanned
    // and the camouflage is chosen. After that the flag is saved in NBT and the scan never runs again
    // (even after the chunk is unloaded / loaded).
    private boolean camoScanned = false;

    public VehicleSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.VEHICLE_SPAWNER_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.loadTimer = 60;
        if (migratedLegacyConfig) setChanged();

        // check: the chunk / block entity is loaded
        recheckRedstone();

        // Register this spawner in the persistent registry used by the vehicle list panel
        if (level instanceof ServerLevel serverLevel) {
            VehicleSpawnerRegistry.get(serverLevel).put(worldPosition, VehicleSpawnerRegistry.Snapshot.of(this));
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VehicleSpawnerBlockEntity be) {
        if (level.isClientSide) return;

        if (!be.camoScanned) {
            be.camoScanned = true;
            VehicleSpawnerBlock.CamoType camo = VehicleSpawnerBlock.computeCamoType(level, pos);
            if (state.getValue(VehicleSpawnerBlock.CAMO) != camo) {
                level.setBlock(pos, state.setValue(VehicleSpawnerBlock.CAMO, camo), 3);
            }
            be.setChanged();
        }

        if (be.loadTimer > 0) {
            be.loadTimer--;
            return;
        }

        AASWorldData data = AASWorldData.get((ServerLevel) level);

        if (!data.isGameStarted) {
            be.removeStartRedstone(level);
            if (be.hasSpawnedOnce || be.targetSpawnTick != 0 || be.lastVehicleUUID != null) {
                be.hasSpawnedOnce = false;
                be.targetSpawnTick = 0;
                be.lastVehicleUUID = null;
                data.pendingSpawnerRespawns.remove(pos);
                be.setChanged();
                be.syncToClient();
            }
            return;
        }

        long currentTick = level.getGameTime();
        String spawnerTeam = be.spawnerTeam();

        DeckSpawnConfig activeCfg = be.activeConfig(data, spawnerTeam);


        if (data.gameMode.equalsIgnoreCase("INVASION") && data.invasionPrepTicks > 0) {
            if (!spawnerTeam.equals(data.invasionDefender) && !spawnerTeam.equals("NEUTRAL")) {
                return;
            }
        }

        // 1. Is the vehicle of this spawner alive
        // no config / empty vehicle id / disabled for the deck of this team = nothing spawns
        if (!be.isSpawnActive(activeCfg)) {
            if (be.targetSpawnTick != 0) {   // timer was running (deck disabled / redstone gone): stop it
                be.targetSpawnTick = 0;
                be.setChanged();
                be.syncToClient();
            }
            return;
        }

        boolean vehicleExistsGlobally = data.markedVehicles.stream()
                .anyMatch(v -> v.spawnerPos != null && v.spawnerPos.equals(pos));

        if (vehicleExistsGlobally) {
            if (be.targetSpawnTick != 0) {
                be.targetSpawnTick = 0;
                be.setChanged();
                be.syncToClient();
            }
            return;
        }

        // 2. No vehicle: take the GLOBAL deadline, which is counted independently of the chunk loading.
        //    It is set at the moment the vehicle dies, in GameLogicEvents#processEntityLoss.
        Long globalDeadline = data.pendingSpawnerRespawns.get(pos);
        if (globalDeadline != null) {
            if (be.targetSpawnTick != globalDeadline) {
                be.targetSpawnTick = globalDeadline;
                be.setChanged();
                be.syncToClient();
            }
        } else if (be.targetSpawnTick == 0 && !be.hasSpawnedOnce) {
            // 3. Initialization of the very first spawn (the vehicle has not spawned even once yet)
            int delay = activeCfg.initialTime * 20;
            be.targetSpawnTick = currentTick + delay;
            be.setChanged();
            be.syncToClient();
        }

        // 4. THE VEHICLE SPAWN ITSELF
        if (be.targetSpawnTick != 0 && currentTick >= be.targetSpawnTick) {
            be.spawnVehicle(activeCfg);
            data.pendingSpawnerRespawns.remove(pos);
            be.targetSpawnTick = 0;
            be.setChanged();
            be.syncToClient();
        }
    }

    // PATH: src\main\java\com\example\aas\block\VehicleSpawnerBlockEntity.java

    private void spawnVehicle(DeckSpawnConfig cfg) {
        if (level == null || level.isClientSide) return;
        if (cfg == null || !cfg.hasVehicle()) return;

        ResourceLocation resLoc = ResourceLocation.tryParse(cfg.vehicleIdTrimmed());
        if (resLoc == null) return;

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(resLoc);
        if (type == null) return;

        Entity entity = type.create(level);
        if (entity != null) {
            entity.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 2.5, worldPosition.getZ() + 0.5);
            entity.getPersistentData().putLong("AAS_SpawnerPos", worldPosition.asLong());
            entity.setYRot(cfg.yaw);
            entity.setYHeadRot(cfg.yaw);

            String vTeam = spawnerTeam();
            String vType = "DEFAULT";
            int penalty = 0;
            int maxMats = 0;

            ItemStack modifierStack = cfg.items[0];   // modifier of the deck that is being spawned
            if (!modifierStack.isEmpty()) {
                if (modifierStack.getItem() instanceof VehicleMarkerItem marker) {
                    vTeam = marker.getTeam();
                    vType = marker.getType();
                    penalty = marker.getPenalty();
                    maxMats = marker.getMaxMats();
                }
                else if (modifierStack.getItem() instanceof SupplyTruckMarkerItem supply) {
                    vTeam = supply.getTeam();
                    vType = supply.getVehicleType();
                    penalty = supply.getPenalty();
                    maxMats = supply.getMaxMats();

                    // make sure the tag is set right at the spawn
                    entity.getPersistentData().putBoolean("AAS_IsSupplyTruck", true);
                    // take the maximum number of crates from the config
                    int maxCrates = com.example.aas.config.AASConfig.SUPPLY_TRUCK_CRATES.get();
                    entity.getPersistentData().putInt("AAS_SupplyAmmo", maxCrates);
                }
            }

            // === IMPORTANT: ALWAYS set the team tag (even if it is NEUTRAL) ===
            entity.getPersistentData().putString("AAS_VehicleTeam", vTeam);
            entity.getPersistentData().putString("AAS_VehicleType", vType);
            entity.getPersistentData().putInt("AAS_TicketPenalty", penalty);

            if (maxMats > 0) {
                entity.getPersistentData().putInt("AAS_VehicleMaxMats", maxMats);
                entity.getPersistentData().putInt("AAS_VehicleMats", maxMats);
            }

            net.minecraft.nbt.ListTag loadoutTag = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < 32; i++) {
                ItemStack contentStack = cfg.items[i + 1];
                if (!contentStack.isEmpty()) {
                    net.minecraft.nbt.CompoundTag itemTag = new net.minecraft.nbt.CompoundTag();
                    itemTag.putByte("Slot", (byte) i);
                    contentStack.save(itemTag);
                    loadoutTag.add(itemTag);
                }
            }
            entity.getPersistentData().put("AAS_InitialLoadout", loadoutTag);

            AASWorldData worldData = AASWorldData.get((ServerLevel) level);
            worldData.markedVehicles.removeIf(v -> v.spawnerPos != null && v.spawnerPos.equals(this.worldPosition));

            // === pass the auto-return settings into the vehicle record ===
            worldData.markedVehicles.add(new AASWorldData.VehicleRecord(
                    entity.getUUID(), vTeam, vType,
                    entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(),
                    this.worldPosition,
                    cfg.autoReturnEnabled, cfg.autoReturnTime, cfg.autoReturnDestroy,
                    cfg.respawnTime // <-- NEW
            ));
            worldData.setDirty();
            PacketHandler.sendToAllClients((ServerLevel)level, worldData);

            entity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                for (int i = 0; i < 32; i++) {
                    ItemStack contentStack = cfg.items[i + 1];
                    if (!contentStack.isEmpty()) {
                        if (handler instanceof net.minecraftforge.items.IItemHandlerModifiable modifiable) {
                            modifiable.setStackInSlot(i, contentStack.copy());
                        }
                    }
                }
            });

            if (entity instanceof LivingEntity living) {
                living.setHealth(living.getMaxHealth());
            }

            // === 5 SECONDS OF IMMUNITY TO THE MAIN ZONE ===
            entity.getPersistentData().putLong("AAS_SpawnGraceTick", level.getGameTime());

            level.addFreshEntity(entity);
            this.lastVehicleUUID = entity.getUUID();
            this.hasSpawnedOnce = true;
            this.setChanged();
        }
    }
    private void insertItemIntoSlot(IItemHandler handler, int slot, ItemStack stack) {
        if (slot < handler.getSlots()) {
            if (handler instanceof net.minecraftforge.items.IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(slot, stack);
            } else {
                handler.insertItem(slot, stack, false);
            }
        }
    }

    // ===================================================================== decks

    private static String teamOfMarker(ItemStack s) {
        if (s.getItem() instanceof VehicleMarkerItem vm) return vm.getTeam();
        if (s.getItem() instanceof SupplyTruckMarkerItem stm) return stm.getTeam();
        return null;
    }

    /** Team of this spawner = team of the first modifier found (COMBINED deck first, then the other decks); NEUTRAL if none. */
    public String spawnerTeam() {
        DeckSpawnConfig c = deckConfigs.get(DeckType.COMBINED);
        if (c != null) {
            String t = teamOfMarker(c.items[0]);
            if (t != null) return t;
        }
        for (DeckType d : DeckType.VALUES) {
            c = deckConfigs.get(d);
            if (c == null) continue;
            String t = teamOfMarker(c.items[0]);
            if (t != null) return t;
        }
        return "NEUTRAL";
    }

    /** Vehicle inventory item (0..31) of the deck this spawner's team plays with. Used when a vehicle is re-armed. */
    public ItemStack activeLoadoutStack(int i) {
        if (!(level instanceof ServerLevel sl)) return ItemStack.EMPTY;
        DeckSpawnConfig c = activeConfig(AASWorldData.get(sl), spawnerTeam());
        if (c == null || i < 0 || i + 1 >= DeckSpawnConfig.SLOTS) return ItemStack.EMPTY;
        return c.items[i + 1];
    }

    /** Switch the deck whose modifier / inventory the GUI buffer shows. */
    public void setEditDeck(DeckType deck) {
        if (deck == null || deck == editDeck) return;
        editDeck = deck;
        loadBuffer();
        setChanged();
    }

    private void loadBuffer() {
        bufferLoading = true;
        try {
            DeckSpawnConfig c = cfg(editDeck);
            for (int i = 0; i < DeckSpawnConfig.SLOTS; i++) inventory.setStackInSlot(i, c.items[i].copy());
        } finally {
            bufferLoading = false;
        }
    }

    /** Config for editing (created on demand). */
    public DeckSpawnConfig cfg(DeckType deck) {
        return deckConfigs.computeIfAbsent(deck, k -> new DeckSpawnConfig());
    }

    /** Config of the deck the spawner's team plays with (COMBINED if the team has no deck); null = nothing configured. */
    public DeckSpawnConfig activeConfig(AASWorldData data, String team) {
        return deckConfigs.get(data.effectiveDeck(team));
    }

    // ===================================================================== redstone

    private static final Direction[] REDSTONE_ORDER = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN, Direction.UP };

    /**
     * Spawner works now: the deck config is enabled and has a vehicle, and (if "redstone on start" is on)
     * a redstone signal reaches the block. Used by the server tick and by the client renderer.
     */
    public boolean isSpawnActive(DeckSpawnConfig cfg) {
        if (cfg == null || !cfg.hasVehicle()) return false;
        if (cfg.redstoneOnStart && !hasRedstoneSignal()) return false;
        return true;
    }

    /**
     * Server: the cached value (refreshed only at the check points).
     * Client: the live value, because the cache is not synced to the client.
     */
    public boolean hasRedstoneSignal() {
        if (level == null) return false;
        return level.isClientSide ? level.hasNeighborSignal(worldPosition) : redstoneSignal;
    }

    /**
     * Rechecks the redstone signal around THIS spawner and updates the cache + the registry snapshot.
     * Called on: placement / neighbor update, chunk load, prepare start, end of the deck-pick screen, game start.
     */
    public void recheckRedstone() {
        if (level == null || level.isClientSide) return;
        boolean now = level.hasNeighborSignal(worldPosition);
        if (now != redstoneSignal) {
            redstoneSignal = now;
            setChanged();
            if (level instanceof ServerLevel sl) {
                VehicleSpawnerRegistry.get(sl).put(worldPosition, VehicleSpawnerRegistry.Snapshot.of(this));
            }
        }
    }

    /** Rechecks ALL spawners in loaded chunks. Unloaded ones are rechecked in onLoad() when the chunk loads. */
    public static void recheckAll(ServerLevel level) {
        for (BlockPos p : VehicleSpawnerRegistry.get(level).copy().keySet()) {
            if (!level.isLoaded(p)) continue;
            if (level.getBlockEntity(p) instanceof VehicleSpawnerBlockEntity be) be.recheckRedstone();
        }
    }

    /** Match is running: once per match put a redstone block next to the spawner (only into AIR, never over other blocks). */
    private void tickStartRedstone(Level level, BlockPos pos, DeckSpawnConfig cfg) {
        if (redstoneTried) return;
        redstoneTried = true;
        if (redstonePos != null) return;   // already placed (server restarted during the match)
        if (cfg == null || !cfg.redstoneOnStart || !cfg.hasVehicle()) return;
        for (Direction dir : REDSTONE_ORDER) {
            BlockPos p = pos.relative(dir);
            if (level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                redstonePos = p;
                setChanged();
                return;
            }
        }
    }

    /** Match stopped: take our redstone block away again (only if it is still a redstone block). */
    private void removeStartRedstone(Level level) {
        redstoneTried = false;
        if (redstonePos == null) return;
        if (level.isLoaded(redstonePos) && level.getBlockState(redstonePos).is(Blocks.REDSTONE_BLOCK)) {
            level.removeBlock(redstonePos, false);
        }
        redstonePos = null;
        setChanged();
    }

    private void loadDeckConfigs(CompoundTag tag) {
        deckConfigs.clear();
        if (tag.contains("DeckConfigs", 10)) {
            CompoundTag dc = tag.getCompound("DeckConfigs");
            for (DeckType deck : DeckType.VALUES) {
                if (dc.contains(deck.id, 10)) deckConfigs.put(deck, DeckSpawnConfig.load(dc.getCompound(deck.id)));
            }
        } else if (tag.contains("VehicleID") || tag.contains("RespawnTime") || tag.contains("InitialTime")) {
            // old block: the single config becomes the COMBINED deck's config
            DeckSpawnConfig c = new DeckSpawnConfig();
            c.respawnTime = tag.getInt("RespawnTime");
            c.initialTime = tag.getInt("InitialTime");
            c.vehicleId = tag.getString("VehicleID");
            c.yaw = tag.getFloat("VehicleYaw");
            c.autoReturnEnabled = tag.getBoolean("AutoReturnEnabled");
            c.autoReturnTime = tag.contains("AutoReturnTime") ? tag.getInt("AutoReturnTime") : 60;
            c.autoReturnDestroy = tag.getBoolean("AutoReturnDestroy");
            deckConfigs.put(DeckType.COMBINED, c);
            migratedLegacyConfig = true;
        }
        redstonePos = tag.contains("RedstonePos") ? BlockPos.of(tag.getLong("RedstonePos")) : null;
    }

    private void saveDeckConfigs(CompoundTag tag) {
        CompoundTag dc = new CompoundTag();
        for (Map.Entry<DeckType, DeckSpawnConfig> e : deckConfigs.entrySet()) dc.put(e.getKey().id, e.getValue().save());
        tag.put("DeckConfigs", dc);
        if (redstonePos != null) tag.putLong("RedstonePos", redstonePos.asLong());
    }

    public void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loadDeckConfigs(tag);
        if (tag.contains("Inventory")) {
            // old block: the single shared inventory (modifier + vehicle items) becomes the COMBINED deck's
            CompoundTag comb = tag.getCompound("DeckConfigs").getCompound(DeckType.COMBINED.id);
            if (!comb.contains("Items")) {
                ItemStackHandler old = new ItemStackHandler(DeckSpawnConfig.SLOTS);
                old.deserializeNBT(tag.getCompound("Inventory"));
                DeckSpawnConfig c = cfg(DeckType.COMBINED);
                for (int i = 0; i < DeckSpawnConfig.SLOTS; i++) c.items[i] = old.getStackInSlot(i).copy();
                migratedLegacyConfig = true;
            }
        }
        DeckType ed = DeckType.fromId(tag.getString("EditDeck"));
        editDeck = ed != null ? ed : DeckType.COMBINED;
        loadBuffer();
        targetSpawnTick = tag.getLong("TargetSpawnTick");
        hasSpawnedOnce = tag.getBoolean("HasSpawnedOnce");
        camoScanned = tag.getBoolean("CamoScanned");
        if (tag.hasUUID("LastVehicle")) lastVehicleUUID = tag.getUUID("LastVehicle");

        // === AUTO-RETURN ===
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("EditDeck", editDeck.id);
        saveDeckConfigs(tag);
        tag.putLong("TargetSpawnTick", targetSpawnTick);
        tag.putBoolean("HasSpawnedOnce", hasSpawnedOnce);
        tag.putBoolean("CamoScanned", camoScanned);
        if (lastVehicleUUID != null) tag.putUUID("LastVehicle", lastVehicleUUID);

        // === AUTO-RETURN ===
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Vehicle Spawner Config");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return new VehicleSpawnerMenu(id, playerInv, this);
    }
}