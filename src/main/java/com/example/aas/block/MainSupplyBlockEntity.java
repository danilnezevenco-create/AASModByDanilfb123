package com.example.aas.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.List;

public class MainSupplyBlockEntity extends BlockEntity {

    private int checkTimer = 0;

    public MainSupplyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MAIN_SUPPLY_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MainSupplyBlockEntity entity) {
        if (level.isClientSide) return;

        entity.checkTimer++;
        if (entity.checkTimer < 20) return; // РџСЂРѕРІРµСЂРєР° СЂР°Р· РІ СЃРµРєСѓРЅРґСѓ
        entity.checkTimer = 0;
        // === РќРћР’РћР•: Р›Р•Р§Р•РќРР• РР“Р РћРљРћР’ ===
        if (com.example.aas.config.AASConfig.MAIN_SUPPLY_HEALING.get()) {
            int healRad = com.example.aas.config.AASConfig.MAIN_SUPPLY_HEAL_RADIUS.get();
            AABB healArea = new AABB(pos).inflate(healRad);
            List<Player> players = level.getEntitiesOfClass(Player.class, healArea);
            for (Player p : players) {
                if (p.isAlive() && !p.isSpectator()) {
                    // Р’С‹РґР°РµРј СЂРµРіРµРЅРµСЂР°С†РёСЋ РЅР° 3 СЃРµРєСѓРЅРґС‹ (60 С‚РёРєРѕРІ) РєР°Р¶РґСѓСЋ СЃРµРєСѓРЅРґСѓ
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, true));
                }
            }
        }
        // ==============================

        AABB searchArea = new AABB(pos).inflate(15);
        List<Entity> nearbyEntities = level.getEntitiesOfClass(Entity.class, searchArea);

        // === РќРћР’РћР•: РЎРћР РўРР РћР’РљРђ РџРћ РџР РРћР РРўР•РўРЈ ===
        // РЎРЅР°С‡Р°Р»Р° С‚Рµ, Сѓ РєРѕРіРѕ РµСЃС‚СЊ РїР°СЃСЃР°Р¶РёСЂС‹-РёРіСЂРѕРєРё, Р·Р°С‚РµРј РѕСЃС‚Р°Р»СЊРЅС‹Рµ
        nearbyEntities.sort((e1, e2) -> {
            boolean p1HasPlayer = e1.getPassengers().stream().anyMatch(p -> p instanceof Player);
            boolean p2HasPlayer = e2.getPassengers().stream().anyMatch(p -> p instanceof Player);
            if (p1HasPlayer && !p2HasPlayer) return -1;
            if (!p1HasPlayer && p2HasPlayer) return 1;
            return 0;
        });

        long currentTime = level.getGameTime();

        for (Entity vehicle : nearbyEntities) {

            // РЎС‚СЂРѕРєР° СЃС‚Р°С‚СѓСЃР° РґР»СЏ Action Bar (С‡С‚РѕР±С‹ РѕР±СЉРµРґРёРЅРёС‚СЊ С‚Р°Р№РјРµСЂС‹)
            StringBuilder statusMessage = new StringBuilder();
            boolean showActionBar = false;

            // РЎР±СЂРѕСЃ С‚Р°Р№РјРµСЂРѕРІ РїСЂРё РґРѕР»РіРѕРј РѕС‚СЃСѓС‚СЃС‚РІРёРё (РµСЃР»Рё РЅРµ РѕР±РЅРѕРІР»СЏР»РёСЃСЊ Р±РѕР»РµРµ 2 СЃРµРє, Р·РЅР°С‡РёС‚ РІС‹РµР·Р¶Р°Р»Рё)
            long lastSeenTime = vehicle.getPersistentData().getLong("AAS_LastSupplyTime");
            if (vehicle.getPersistentData().contains("AAS_VehicleMaxMats")) {
                int maxMats = vehicle.getPersistentData().getInt("AAS_VehicleMaxMats");
                vehicle.getPersistentData().putInt("AAS_VehicleMats", maxMats);
            }
            if (currentTime - lastSeenTime > 200) { // > 20 СЃРµРєСѓРЅРґ РѕС‚СЃСѓС‚СЃС‚РІРёСЏ
                vehicle.getPersistentData().putInt("AAS_RepairTimer", 0);
                vehicle.getPersistentData().putInt("AAS_TruckReloadTimer", 0);
            }
            vehicle.getPersistentData().putLong("AAS_LastSupplyTime", currentTime);


            // === Р›РћР“РРљРђ 1: SUPPLY TRUCK (РџРћРџРћР›РќР•РќРР• РЇР©РРљРћР’) ===
            // Р­РўРђ Р›РћР“РРљРђ РўР•РџР•Р Р¬ РќР• Р—РђР’РРЎРРў РћРў РљРЈР›Р”РђРЈРќРђ
            if (vehicle.getPersistentData().getBoolean("AAS_IsSupplyTruck")) {
                int currentAmmo = vehicle.getPersistentData().getInt("AAS_SupplyAmmo");
                int maxCrates = com.example.aas.config.AASConfig.SUPPLY_TRUCK_CRATES.get();

                if (currentAmmo < maxCrates) {
                    int supplyTimer = vehicle.getPersistentData().getInt("AAS_TruckReloadTimer");
                    supplyTimer++;

                    if (supplyTimer >= 15) { // 15 СЃРµРєСѓРЅРґ Р·Р°РіСЂСѓР·РєРё
                        vehicle.getPersistentData().putInt("AAS_SupplyAmmo", currentAmmo + 1);
                        vehicle.getPersistentData().putInt("AAS_TruckReloadTimer", 0);

                        // РЈРІРµРґРѕРјР»РµРЅРёРµ РІ Р§РђРў (С‡С‚РѕР±С‹ Р±С‹Р»Рѕ РІС‹С€Рµ Рё РЅРµ РїРµСЂРµРєСЂС‹РІР°Р»РѕСЃСЊ)
                        sendChatMessageToPassengers(vehicle, Component.translatable("aas.msg.crate_loaded", (currentAmmo + 1), maxCrates).getString(), ChatFormatting.GOLD);
                        spawnEffects(level, vehicle);
                    } else {
                        vehicle.getPersistentData().putInt("AAS_TruckReloadTimer", supplyTimer);
                        // Р”РѕР±Р°РІР»СЏРµРј РІ РѕР±С‰РёР№ СЃС‚Р°С‚СѓСЃ Р±Р°СЂ
                        statusMessage.append(ChatFormatting.YELLOW).append(Component.translatable("aas.msg.loading_crate", 15 - supplyTimer).getString()).append(" ");
                        showActionBar = true;
                    }
                } else {
                    // Р•СЃР»Рё С„СѓР»Р» СЏС‰РёРєРѕРІ, СЃР±СЂР°СЃС‹РІР°РµРј С‚Р°Р№РјРµСЂ
                    vehicle.getPersistentData().putInt("AAS_TruckReloadTimer", 0);
                }
            }


            // === Р›РћР“РРљРђ 2: Р Р•РњРћРќРў Р РџР•Р Р•Р—РђР РЇР”РљРђ (REARM) ===
            // Р­РўРђ Р›РћР“РРљРђ РРњР•Р•Рў РљРЈР›Р”РђРЈРќ 5 РњРРќРЈРў
            if (vehicle.getPersistentData().contains("AAS_SpawnerPos")) {

                long nextSupplyTime = vehicle.getPersistentData().getLong("AAS_NextSupplyTime");

                if (currentTime < nextSupplyTime) {
                    // Р•РЎР›Р РљРЈР›Р”РђРЈРќ РђРљРўРР’Р•Рќ
                    long secondsLeft = (nextSupplyTime - currentTime) / 20;
                    vehicle.getPersistentData().putInt("AAS_RepairTimer", 0); // РЎР±СЂР°СЃС‹РІР°РµРј РїСЂРѕРіСЂРµСЃСЃ СЂРµРјРѕРЅС‚Р°, РµСЃР»Рё РљР”

                    // Р”РѕР±Р°РІР»СЏРµРј РёРЅС„Рѕ Рѕ РљР” РІ СЃС‚Р°С‚СѓСЃ Р±Р°СЂ
                    statusMessage.append(ChatFormatting.RED).append(Component.translatable("aas.msg.rearm_cooldown", secondsLeft).getString());
                    showActionBar = true;
                }
                else {
                    // Р•РЎР›Р РљРЈР›Р”РђРЈРќРђ РќР•Рў -> РќРђР§РРќРђР•Рњ Р Р•РњРћРќРў
                    int repairTimer = vehicle.getPersistentData().getInt("AAS_RepairTimer");
                    repairTimer++; // +1 СЃРµРєСѓРЅРґР°

                    if (repairTimer >= 30) { // 30 СЃРµРєСѓРЅРґ РїСЂРѕС†РµСЃСЃ
                        // 1. Р›РµС‡РёРј
                        if (vehicle instanceof LivingEntity living) {
                            living.setHealth(living.getMaxHealth());
                        }

                        // 2. РџРµСЂРµР·Р°СЂСЏР¶Р°РµРј
                        java.util.concurrent.atomic.AtomicBoolean rearmSuccess = new java.util.concurrent.atomic.AtomicBoolean(false);

                        vehicle.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(vehInv -> {
                            // РќРђР”Р•Р–РќРђРЇ РћР§РРЎРўРљРђ РРќР’Р•РќРўРђР РЇ
                            if (vehInv instanceof IItemHandlerModifiable modifiable) {
                                for (int i = 0; i < vehInv.getSlots(); i++) {
                                    modifiable.setStackInSlot(i, ItemStack.EMPTY);
                                }
                            } else {
                                for (int i = 0; i < vehInv.getSlots(); i++) vehInv.extractItem(i, 64, false);
                            }

                            // РќРћР’Р«Р™ РњР•РўРћР”: Р“СЂСѓР·РёРј РёР· NBT РјР°С€РёРЅС‹ (СЂР°Р±РѕС‚Р°РµС‚ РґР°Р¶Рµ РµСЃР»Рё С‡Р°РЅРє РІС‹РіСЂСѓР¶РµРЅ)
                            if (vehicle.getPersistentData().contains("AAS_InitialLoadout")) {
                                net.minecraft.nbt.ListTag loadoutTag = vehicle.getPersistentData().getList("AAS_InitialLoadout", 10);
                                for (int i = 0; i < loadoutTag.size(); i++) {
                                    net.minecraft.nbt.CompoundTag itemTag = loadoutTag.getCompound(i);
                                    int slot = itemTag.getByte("Slot") & 255;
                                    if (slot < vehInv.getSlots()) {
                                        insertItem(vehInv, slot, ItemStack.of(itemTag));
                                    }
                                }
                                rearmSuccess.set(true);
                            }
                            // РЎРўРђР Р«Р™ РњР•РўРћР”: Р•СЃР»Рё РјР°С€РёРЅР° Р±С‹Р»Р° Р·Р°СЃРїР°РІРЅРµРЅР° РґРѕ С„РёРєСЃР° (Р РµР·РµСЂРІ)
                            else {
                                long spawnerPosLong = vehicle.getPersistentData().getLong("AAS_SpawnerPos");
                                BlockPos spawnerPos = BlockPos.of(spawnerPosLong);

                                if (level.isLoaded(spawnerPos)) {
                                    BlockEntity be = level.getBlockEntity(spawnerPos);
                                    if (be instanceof VehicleSpawnerBlockEntity spawner) {
                                        for (int i = 0; i < 32; i++) {
                                            if (i >= vehInv.getSlots()) break;
                                            ItemStack sourceStack = spawner.activeLoadoutStack(i);
                                            if (!sourceStack.isEmpty()) {
                                                insertItem(vehInv, i, sourceStack.copy());
                                            }
                                        }
                                        rearmSuccess.set(true);
                                    }
                                }
                            }

                            // Р‘Р°С‚Р°СЂРµР№РєР°
                            if (rearmSuccess.get()) {
                                Item batteryItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "large_battery"));
                                if (batteryItem != null) {
                                    for (int i = 0; i < vehInv.getSlots(); i++) {
                                        if (vehInv.getStackInSlot(i).isEmpty()) {
                                            insertItem(vehInv, i, new ItemStack(batteryItem));
                                            break;
                                        }
                                    }
                                }
                            }
                        });

                        vehicle.getPersistentData().putInt("AAS_RepairTimer", 0);

                        // Р•РЎР›Р РџР•Р Р•Р—РђР РЇР”РљРђ Р Р•РђР›Р¬РќРћ РЎР›РЈР§РР›РђРЎР¬
                        if (rearmSuccess.get()) {
                            // РСЃРїСЂР°РІР»РµРЅРѕ: 600 С‚РёРєРѕРІ = 30 СЃРµРєСѓРЅРґ
                            vehicle.getPersistentData().putLong("AAS_NextSupplyTime", currentTime + 600);
                            sendChatMessageToPassengers(vehicle, Component.translatable("aas.msg.vehicle_rearmed").getString(), ChatFormatting.GREEN);
                            spawnEffects(level, vehicle);
                        } else {
                            sendChatMessageToPassengers(vehicle, Component.translatable("aas.msg.rearm_error_chunk").getString(), ChatFormatting.RED);
                        }
                    } else {
                        vehicle.getPersistentData().putInt("AAS_RepairTimer", repairTimer);
                        statusMessage.append(ChatFormatting.AQUA).append(Component.translatable("aas.msg.rearming", 30 - repairTimer).getString());
                        showActionBar = true;
                    }
                }
            }

            // РћРўРџР РђР’Р›РЇР•Рњ РћР‘РЄР•Р”РРќР•РќРќР«Р™ РЎРўРђРўРЈРЎ Р’ ACTION BAR (РЅР°Рґ С…РѕС‚Р±Р°СЂРѕРј)
            // Р­С‚Рѕ РїСЂРµРґРѕС‚РІСЂР°С‰Р°РµС‚ РјРёРіР°РЅРёРµ С‚РµРєСЃС‚Р°
            if (showActionBar && statusMessage.length() > 0) {
                for (Entity passenger : vehicle.getPassengers()) {
                    if (passenger instanceof Player player) {
                        player.displayClientMessage(Component.literal(statusMessage.toString()), true);
                    }
                }
            }
        }
    }

    private static void insertItem(IItemHandler handler, int slot, ItemStack stack) {
        if (handler instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, stack);
        } else {
            handler.insertItem(slot, stack, false);
        }
    }

    // РЎРѕРѕР±С‰РµРЅРёРµ РІ Action Bar (РёСЃС‡РµР·Р°РµС‚, РѕР±РЅРѕРІР»СЏРµС‚СЃСЏ)
    private static void sendActionBarToPassengers(Entity vehicle, String msg, ChatFormatting color) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                player.displayClientMessage(Component.literal(msg).withStyle(color), true);
            }
        }
    }

    // РЎРѕРѕР±С‰РµРЅРёРµ РІ Р§РђРў (СЃРѕС…СЂР°РЅСЏРµС‚СЃСЏ РІ РёСЃС‚РѕСЂРёРё, РЅР°С…РѕРґРёС‚СЃСЏ РІС‹С€Рµ Action Bar)
    private static void sendChatMessageToPassengers(Entity vehicle, String msg, ChatFormatting color) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                // false = System Chat Message
                player.displayClientMessage(Component.literal(msg).withStyle(color), false);
            }
        }
    }

    private static void spawnEffects(Level level, Entity vehicle) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    vehicle.getX(), vehicle.getY() + 1.5, vehicle.getZ(),
                    10, 1.0, 1.0, 1.0, 0.1);
        }
    }
}
