package com.example.aas.events;

import com.example.aas.block.*;
import com.example.aas.network.*;
import com.example.aas.network.MapPlayerInfo;
import com.example.aas.util.TeamMessageUtil;
import com.example.aas.world.AASWorldData;
import com.example.aas.network.PacketCaptureNotification;
import net.minecraft.server.level.ServerLevel;
import com.example.aas.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.example.aas.config.AASConfig;
import java.util.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import com.example.aas.sound.ModSounds; // Р”РѕР±Р°РІСЊС‚Рµ СЌС‚Сѓ СЃС‚СЂРѕРєСѓ
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionResult;

@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GameLogicEvents {

    public static final Map<UUID, String> pendingRespawnLocations = new HashMap<>();
    public static final Map<UUID, String> pendingTeams = new HashMap<>();
    public static final Map<UUID, Map<Integer, CompoundTag>> PERSISTENT_NBT_STORAGE = new HashMap<>();
    private static final Map<String, Boolean> lastBlueBlockedMap = new HashMap<>();
    private static final Map<String, Boolean> lastRedBlockedMap = new HashMap<>();

    public static void startGameCountdown(ServerLevel level) {
        AASWorldData data = AASWorldData.get(level);
        data.countdownTicks = 100;
        data.countdownActive = true;
        data.setDirty();
    }

    public static void cancelCountdown(ServerLevel level) {
        AASWorldData data = AASWorldData.get(level);
        data.countdownActive = false;
        data.countdownTicks = 0;
        data.setDirty();
    }

    // РџСѓСЃС‚РѕР№ РјРµС‚РѕРґ РґР»СЏ СЃРѕРІРјРµСЃС‚РёРјРѕСЃС‚Рё
    public static void cancelCountdown() {}

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() == null || event.getPlayer().level().isClientSide) return;

        ServerPlayer player = (ServerPlayer) event.getPlayer();
        if (player.isCreative()) return;

        AASWorldData data = AASWorldData.get(player.serverLevel());

        // Р Р°Р±РѕС‚Р°РµС‚ С‚РѕР»СЊРєРѕ РµСЃР»Рё РёРіСЂР° Р·Р°РїСѓС‰РµРЅР°
        if (data.isGameStarted) {

            // РџСЂРѕРІРµСЂСЏРµРј, СЏРІР»СЏРµС‚СЃСЏ Р»Рё РІС‹Р±СЂР°СЃС‹РІР°РµРјС‹Р№ РїСЂРµРґРјРµС‚ fpv-РґСЂРѕРЅРѕРј вЂ” РµРјСѓ РІСЃРµРіРґР° РјРѕР¶РЅРѕ
            ItemStack tossedStack = event.getEntity().getItem();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(tossedStack.getItem());
            boolean isFpvDrone = itemId != null && itemId.toString().equals("fpvdrone:drone");

            if (isFpvDrone) {
                return; // СЂР°Р·СЂРµС€Р°РµРј РІС‹Р±СЂРѕСЃ, РЅРёРєР°РєРёС… РѕРіСЂР°РЅРёС‡РµРЅРёР№
            }

            boolean preventAll = false;
            try {
                preventAll = AASConfig.PREVENT_ALL_ITEM_DROPS.get();
            } catch (Exception ignored) {}

            if (preventAll) {
                event.setCanceled(true);
                // Р’Р°Р¶РЅРѕ: РІРѕР·РІСЂР°С‰Р°РµРј РїСЂРµРґРјРµС‚ РІ РёРЅРІРµРЅС‚Р°СЂСЊ, С‡С‚РѕР±С‹ РѕРЅ РЅРµ РїСЂРѕРїР°Р»
                player.getInventory().add(tossedStack);
                player.displayClientMessage(Component.literal("Item dropping is DISABLED during the game!")
                        .withStyle(ChatFormatting.RED), true);
            }
            else if (isHeavyItem(tossedStack.getItem())) {
                // Р•СЃР»Рё РѕР±С‰Р°СЏ Р±Р»РѕРєРёСЂРѕРІРєР° РІС‹РєР»СЋС‡РµРЅР°, РїСЂРѕРІРµСЂСЏРµРј С‚РѕР»СЊРєРѕ С‚СЏР¶С‘Р»С‹Рµ РїСЂРµРґРјРµС‚С‹
                event.setCanceled(true);
                player.getInventory().add(tossedStack);
                player.displayClientMessage(Component.literal("Cannot drop heavy ammo during combat!")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTickEffects(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player.tickCount % 10 == 0) {
            ServerPlayer player = (ServerPlayer) event.player;
            AASWorldData data = AASWorldData.get(player.serverLevel());
            if (data.isGameStarted && !player.isCreative()) {
                boolean hasHeavyItem = false;
                for (ItemStack stack : player.getInventory().items) {
                    if (!stack.isEmpty() && isHeavyItem(stack.getItem())) { hasHeavyItem = true; break; }
                }
                if (!hasHeavyItem) {
                    for (ItemStack stack : player.getInventory().offhand) {
                        if (!stack.isEmpty() && isHeavyItem(stack.getItem())) { hasHeavyItem = true; break; }
                    }
                }
                if (hasHeavyItem) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false, true));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTickDriveCheck(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        if (!AASConfig.REQUIRE_SPECIALIST_TO_DRIVE.get()) return;

        ServerPlayer player = (ServerPlayer) event.player;
        Entity vehicle = player.getVehicle();

        // Р•СЃР»Рё РёРіСЂРѕРє РІС‹С€РµР» РёР· С‚РµС…РЅРёРєРё вЂ” С‡РёСЃС‚РёРј РІСЃС‘
        if (vehicle == null) {
            player.removeEffect(MobEffects.BLINDNESS);
            player.getPersistentData().remove("AAS_DriveKickTimer");
            player.getPersistentData().remove("AAS_BoardingGrace");
            player.getPersistentData().remove("AAS_LastVehicleId");
            return;
        }

        // --- GRACE PERIOD ---
        // Р—Р°РїРѕРјРёРЅР°РµРј ID С‚РµС…РЅРёРєРё. Р•СЃР»Рё С‚РµС…РЅРёРєР° РЅРѕРІР°СЏ вЂ” РґР°С‘Рј РІСЂРµРјСЏ РЅР° РїРµСЂРµСЃР°РґРєСѓ
        int lastVehicleId = player.getPersistentData().getInt("AAS_LastVehicleId");
        if (lastVehicleId != vehicle.getId()) {
            // РРіСЂРѕРє СЃРµР» РІ РЅРѕРІСѓСЋ (РёР»Рё РґСЂСѓРіСѓСЋ) С‚РµС…РЅРёРєСѓ вЂ” СЃР±СЂР°СЃС‹РІР°РµРј РІСЃС‘ Рё Р¶РґС‘Рј
            player.getPersistentData().putInt("AAS_LastVehicleId", vehicle.getId());
            player.getPersistentData().putInt("AAS_BoardingGrace", 0);
            player.getPersistentData().remove("AAS_DriveKickTimer");
            return; // Р”Р°С‘Рј 1 С‚РёРє РЅР° "СѓСЃС‚СЂРѕР№СЃС‚РІРѕ"
        }

        int boardingGrace = player.getPersistentData().getInt("AAS_BoardingGrace");
        if (boardingGrace < 80) { // 4 СЃРµРєСѓРЅРґС‹ вЂ” РІСЂРµРјСЏ Р·Р°РЅСЏС‚СЊ РЅСѓР¶РЅРѕРµ РјРµСЃС‚Рѕ
            player.getPersistentData().putInt("AAS_BoardingGrace", boardingGrace + 1);
            return; // РџСЂРѕРІРµСЂРєСѓ РЅРµ РґРµР»Р°РµРј, РїРѕРєР° РЅРµ РёСЃС‚С‘Рє grace period
        }
        // --- РљРћРќР•Р¦ GRACE PERIOD ---

        int seatIndex = vehicle.getPassengers().indexOf(player);

        // РџР°СЃСЃР°Р¶РёСЂРѕРІ (РЅРµ РІРѕРґРёС‚РµР»РµР№) РЅРµ С‚СЂРѕРіР°РµРј РЅРёРєРѕРіРґР°
        if (seatIndex > 0) {
            player.removeEffect(MobEffects.BLINDNESS);
            player.getPersistentData().remove("AAS_DriveKickTimer");
            return;
        }

        // РќРёР¶Рµ вЂ” С‚РѕР»СЊРєРѕ РІРѕРґРёС‚РµР»СЊ (seatIndex == 0)
        if (player.isCreative() || player.isSpectator()) return;

        String vType = vehicle.getPersistentData().getString("AAS_VehicleType");
        String pKit = player.getPersistentData().getString("AAS_CurrentKit");

        boolean isAuthorized = true;
        String requiredSpecialist = "";

        if (vType.equalsIgnoreCase("HELICOPTER") || vType.contains("CAS") || vType.contains("Supply Helicopter")) {
            if (!pKit.equals("Pilot") && !pKit.equals("Pilot Officer")) {
                isAuthorized = false;
                requiredSpecialist = "PILOT";
            }
        } else if (vType.equalsIgnoreCase("TANK") || vType.equalsIgnoreCase("APC") ||
                vType.equalsIgnoreCase("Mobile ZU") || vType.equalsIgnoreCase("SPG") ||
                vType.equalsIgnoreCase("Heavy Supply")) {
            if (!pKit.equals("Mechanic") && !pKit.equals("Mechanic Officer")) {
                isAuthorized = false;
                requiredSpecialist = "MECHANIC";
            }
        }

        if (!isAuthorized) {
            int timer = player.getPersistentData().getInt("AAS_DriveKickTimer");
            timer++;

            // РЎР»РµРїРѕС‚Р° РґРµСЂР¶РёС‚СЃСЏ РІРµСЃСЊ РїРµСЂРёРѕРґ, РїРѕРєР° РёРіСЂРѕРє Р±РµР· РЅСѓР¶РЅРѕРіРѕ РєРёС‚Р° СЃРёРґРёС‚ Р·Р° СЂСѓР»С‘Рј.
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));

            if (timer >= 100) {
                player.stopRiding();
                player.removeEffect(MobEffects.BLINDNESS);
                player.getPersistentData().remove("AAS_DriveKickTimer");
                player.getPersistentData().remove("AAS_BoardingGrace");
                player.getPersistentData().remove("AAS_LastVehicleId");
                player.displayClientMessage(Component.literal("EJECTED: You are not a qualified driver!")
                        .withStyle(ChatFormatting.RED), true);
            } else {
                player.getPersistentData().putInt("AAS_DriveKickTimer", timer);
                if (timer % 20 == 0) {
                    // РџСЂР°РІРёР»СЊРЅС‹Р№ РІС‹Р·РѕРІ: СЃРѕР·РґР°РµРј РїРµСЂРµРІРѕРґРёРјС‹Р№ РєРѕРјРїРѕРЅРµРЅС‚, РєСЂР°СЃРёРј РµРіРѕ Рё РѕС‚РїСЂР°РІР»СЏРµРј РІ Action Bar (true)
                    player.displayClientMessage(Component.translatable("aas.msg.ejection_warning", requiredSpecialist, (5 - (timer / 20))).withStyle(ChatFormatting.YELLOW), true);

                    player.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);
                }
            }
        } else {
            player.removeEffect(MobEffects.BLINDNESS);
            player.getPersistentData().remove("AAS_DriveKickTimer");
        }
    }

    @SubscribeEvent
    public static void onPlayerItemLogic(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player.level().getGameTime() % 20 != 0) return;
        Player player = event.player;
        ItemStack mainHandStack = player.getMainHandItem();
        ItemStack offHandStack = player.getOffhandItem();
        Item monitorItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "monitor"));
        Item walkieItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("walkietalkie", "netherite_walkietalkie"));
        if (monitorItem == null || walkieItem == null) return;
        boolean hasMonitor = mainHandStack.getItem() == monitorItem;
        if (hasMonitor) {
            if (offHandStack.getItem() != walkieItem && !offHandStack.isEmpty()) {
                moveItemToInventory(player, offHandStack);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            if (player.getOffhandItem().isEmpty()) {
                int walkieSlot = findItemInInventory(player.getInventory(), walkieItem);
                if (walkieSlot != -1) {
                    ItemStack walkieStack = player.getInventory().getItem(walkieSlot);
                    player.setItemInHand(InteractionHand.OFF_HAND, walkieStack);
                    player.getInventory().setItem(walkieSlot, ItemStack.EMPTY);
                }
            }
        } else {
            if (!offHandStack.isEmpty() && offHandStack.getItem() == walkieItem) {
                moveItemToInventory(player, offHandStack);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public static void onWalkieTalkieUseAttempt(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return; // Р»РѕРіРёРєСѓ РґРµСЂР¶РёРј РЅР° СЃРµСЂРІРµСЂРµ

        GameType gameMode = null;
        if (player instanceof ServerPlayer serverPlayer) {
            gameMode = serverPlayer.gameMode.getGameModeForPlayer();
        }
        if (gameMode != GameType.SURVIVAL && gameMode != GameType.ADVENTURE) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null
                && id.getNamespace().equals("walkietalkie")
                && id.getPath().equals("netherite_walkietalkie")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static boolean isHeavyItem(Item item) {
        if (item == ModItems.AGS_AMMO.get()) return true;
        if (item == ModItems.M2_AMMO.get()) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null) {
            String path = id.getPath();
            if (path.equals("mortar_shell")) return true;
            if (path.equals("medium_anti_ground_missile")) return true;
        }
        return false;
    }

    private static int findItemInInventory(Inventory inventory, Item item) {
        for (int i = 0; i < inventory.items.size(); i++) { if (inventory.items.get(i).getItem() == item) return i; }
        return -1;
    }

    private static void moveItemToInventory(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        int globalTick = server.getTickCount();

        // === 1. Р“Р›РћР‘РђР›Р¬РќРђРЇ Р РђРЎРЎР«Р›РљРђ РџРћР—РР¦РР™ (Р Р°Р±РѕС‚Р°РµС‚ РґР»СЏ Р¶РёРІС‹С… Рё РјРµСЂС‚РІС‹С…) ===
        if (globalTick % 2 == 0) {
            // РЎРЅР°С‡Р°Р»Р° РєСЌС€РёСЂСѓРµРј РґР°РЅРЅС‹Рµ РґР»СЏ РєР°Р¶РґРѕРіРѕ РёР·РјРµСЂРµРЅРёСЏ
            Map<ServerLevel, List<MapPlayerInfo>> dimensionDataCache = new HashMap<>();
            for (ServerLevel level : server.getAllLevels()) {
                AASWorldData data = AASWorldData.get(level);
                // РќРћР’РћР•: Р‘РµСЂРµРј РІСЃРµС… РёРіСЂРѕРєРѕРІ СЃ СЃРµСЂРІРµСЂР° Рё С„РёР»СЊС‚СЂСѓРµРј РїРѕ РёР·РјРµСЂРµРЅРёСЋ, С‡С‚РѕР±С‹ РЅРµ С‚РµСЂСЏС‚СЊ "РјРµСЂС‚РІС‹С…"
                List<ServerPlayer> dimPlayers = new ArrayList<>();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.serverLevel() == level) {
                        dimPlayers.add(p);
                    }
                }
                dimensionDataCache.put(level, buildPlayerInfo(dimPlayers, data));
            }

            // Р Р°СЃСЃС‹Р»Р°РµРј РїР°РєРµС‚С‹ РІСЃРµРј РёРіСЂРѕРєР°Рј РІ СЃРїРёСЃРєРµ СЃРµСЂРІРµСЂР° (РІРєР»СЋС‡Р°СЏ СЌРєСЂР°РЅ СЃРјРµСЂС‚Рё)
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerLevel playerLevel = p.serverLevel();
                List<MapPlayerInfo> playersInMyDim = dimensionDataCache.get(playerLevel);

                if (playersInMyDim == null || playersInMyDim.isEmpty()) continue;

                if (p.isSpectator() || p.isCreative()) {
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PacketSyncMapPlayers(playersInMyDim));
                } else if (p.getTeam() != null) {
                    String myTeamName = p.getTeam().getName();
                    List<MapPlayerInfo> teamOnly = playersInMyDim.stream()
                            .filter(info -> info.team.equalsIgnoreCase(myTeamName))
                            .toList();
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PacketSyncMapPlayers(teamOnly));
                }
            }
        }
        // === 1.5. Р РђРЎРЎР«Р›РљРђ РЎРўРђРўРРЎРўРРљР (Р­РљР РђРќ РЎРўРђРўРРЎРўРРљР, Caps Lock) вЂ” СЂР°Р· РІ СЃРµРєСѓРЅРґСѓ ===
        if (globalTick % 20 == 0) {
            Map<ServerLevel, List<PlayerStatInfo>> statsCache = new HashMap<>();
            for (ServerLevel level : server.getAllLevels()) {
                AASWorldData data = AASWorldData.get(level);
                // РќРћР’РћР•: Р‘РµСЂРµРј РІСЃРµС… РёРіСЂРѕРєРѕРІ СЃ СЃРµСЂРІРµСЂР° Рё С„РёР»СЊС‚СЂСѓРµРј РїРѕ РёР·РјРµСЂРµРЅРёСЋ, С‡С‚РѕР±С‹ РЅРµ С‚РµСЂСЏС‚СЊ "РјРµСЂС‚РІС‹С…"
                List<ServerPlayer> dimPlayers = new ArrayList<>();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.serverLevel() == level) {
                        dimPlayers.add(p);
                    }
                }
                List<PlayerStatInfo> levelStats = buildStatsInfo(dimPlayers, data);
                levelStats.addAll(OfflineStats.buildInfos(server, level.dimension().location().toString(), data)); // РІС‹С€РµРґС€РёРµ РёРіСЂРѕРєРё
                statsCache.put(level, levelStats);
            }

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerLevel pLevel = p.serverLevel();
                List<PlayerStatInfo> full = statsCache.get(pLevel);
                if (full == null) continue;

                AASWorldData pData = AASWorldData.get(pLevel);
                boolean isAdminObserver = p.hasPermissions(2) && (p.isSpectator() || p.isCreative());

                String myTeam = (p.getTeam() != null) ? p.getTeam().getName().toUpperCase() : "";
                List<PlayerStatInfo> toSend = new ArrayList<>();

                for (PlayerStatInfo info : full) {
                    boolean isAlly = !myTeam.isEmpty() && info.team.equalsIgnoreCase(myTeam);

                    if (isAdminObserver || isAlly) {
                        toSend.add(info); // РђРґРјРёРЅС‹ Рё СЃРѕСЋР·РЅРёРєРё РІРёРґСЏС‚ РІСЃС‘ (РѕС‚СЂСЏРґС‹, СЃС‚Р°С‚Сѓ)
                    } else {
                        // Р”Р»СЏ РІСЂР°РіРѕРІ
                        if (!pData.isGameStarted) {
                            // Р”Рѕ СЃС‚Р°СЂС‚Р° РёРіСЂС‹: РѕС‚РїСЂР°РІР»СЏРµРј K/D, РЅРѕ СЃРєСЂС‹РІР°РµРј РѕС‚СЂСЏРґС‹ (-1)
                            toSend.add(info); // РІРЅРµ РјР°С‚С‡Р° (РІ С‚.С‡. РїРѕСЃР»Рµ РїРѕР±РµРґС‹) РѕС‚СЂСЏРґС‹ Рё СЃС‚Р°С‚РёСЃС‚РёРєР° РІСЂР°РіРѕРІ РѕС‚РєСЂС‹С‚С‹ РїРѕР»РЅРѕСЃС‚СЊСЋ
                        } else {
                            // Р’Рѕ РІСЂРµРјСЏ РёРіСЂС‹: СЃРєСЂС‹РІР°РµРј РѕС‚СЂСЏРґС‹ Рё K/D (K/D Р·Р°РјРµРЅСЏРµС‚СЃСЏ РЅР° -1 РІ stripped)
                            toSend.add(info.stripped());
                        }
                    }
                }
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PacketSyncPlayerStats(toSend));
            }
        }
        // === 2. Р›РћР“РРљРђ Р’РќРЈРўР Р РР—РњР•Р Р•РќРР™ (Р—Р°С…РІР°С‚С‹, РўРµС…РЅРёРєР°, РўРёРєРµС‚С‹) ===
        for (ServerLevel level : server.getAllLevels()) {
            AASWorldData data = AASWorldData.get(level);

            handleMainProtectionZones(level, data);
            handleMainProtectionZones(level, data);
            handleLobbyZone(level, data);
            com.example.aas.deck.DeckManager.tick(level, data, globalTick);
            if (globalTick % 400 == 0) {
                handleVehicleAutoReturn(level, data);
            }
            boolean blueIsBleeding = false;
            boolean redIsBleeding = false;

            // РљРѕРґ РіРѕР»РѕСЃРѕРІР°РЅРёСЏ Р·Р° РєРѕРјР°РЅРґРёСЂР°
            if (data.blueCmdVoteActive) {
                data.blueCmdVoteTimer--;
                checkCmdVoteStatus(level, server, data, "BLUE");
            }
            if (data.redCmdVoteActive) {
                data.redCmdVoteTimer--;
                checkCmdVoteStatus(level, server, data, "RED");
            }

            // РљРѕРґ РїСЂРµРґРјР°С‚С‡РµРІРѕРіРѕ РіРѕР»РѕСЃРѕРІР°РЅРёСЏ
            if (data.voteActive && !data.isGameStarted && data.prepPhase == com.example.aas.deck.DeckPhase.NONE) {
                if (globalTick % 20 == 0 && data.voteTimer > 0) {
                    data.voteTimer--;
                    data.setDirty();
                    boolean bReady = isTeamReady(level, "Blue", data);
                    boolean rReady = isTeamReady(level, "Red", data);
                    if (bReady != data.blueReady || rReady != data.redReady || data.voteTimer % 5 == 0 || data.voteTimer < 5) {
                        data.blueReady = bReady;
                        data.redReady = rReady;
                        PacketHandler.sendToAllClients(level, data);
                    }
                }
                if ((data.blueReady && data.redReady) || (data.voteTimer <= 0)) {
                    data.voteActive = false;
                    data.setDirty();
                    startGameCountdown(level);
                    PacketHandler.sendToAllClients(level, data);
                }
            }

            // --- РћР‘РќРћР’Р›Р•РќРР• РўР•РҐРќРРљР РќРђ РљРђР РўР• ---
            if (globalTick % 5 == 0) {
                boolean needsSync = false;
                for (AASWorldData.VehicleRecord record : data.markedVehicles) {
                    Entity vEntity = level.getEntity(record.uuid);
                    if (vEntity != null && vEntity.isAlive()) {
                        float currentYaw = vEntity.getYRot();
                        if (record.x != vEntity.getX() || record.yaw != currentYaw) {
                            record.x = vEntity.getX();
                            record.y = vEntity.getY();
                            record.z = vEntity.getZ();
                            record.yaw = currentYaw;
                            needsSync = true;
                        }
                    }
                }
                if (needsSync) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                }
            }

            // РћС‡РёСЃС‚РєР° РјРµСЂС‚РІС‹С… РјР°СЂРєРµСЂРѕРІ С‚РµС…РЅРёРєРё
            if (globalTick % 40 == 0) {
                boolean changed = false;
                Iterator<AASWorldData.VehicleRecord> it = data.markedVehicles.iterator();
                while (it.hasNext()) {
                    AASWorldData.VehicleRecord record = it.next();
                    Entity vEntity = level.getEntity(record.uuid);
                    if (vEntity != null && !vEntity.isAlive()) {
                        it.remove();
                        changed = true;
                    }
                }
                if (changed) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                }
            }

            // РЈРґР°Р»РµРЅРёРµ СЃС‚Р°СЂС‹С… С‚Р°РєС‚РёС‡РµСЃРєРёС… РјР°СЂРєРµСЂРѕРІ
            if (globalTick % 200 == 0) {
                long time = level.getGameTime();
                if (data.activeMarkers.removeIf(m -> time >= m.expiryTick)) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                }
            }

            // Р›РѕРіРёРєР° Р±Р»РѕРєРёСЂРѕРІРєРё СЂР°Р»Р»Рё-РїРѕРёРЅС‚РѕРІ РІСЂР°РіР°РјРё
            if (globalTick % 20 == 0) {
                int rallyRadius = AASConfig.RALLY_BLOCK_RADIUS.get();
                int rallyEnemiesRequired = AASConfig.RALLY_BLOCK_ENEMY_COUNT.get();
                for (AASWorldData.Squad squad : data.squads) {
                    if (squad.rallyPos != null) {
                        int enemies = getEnemyCount(level, squad.rallyPos, squad.team, rallyRadius);
                        boolean currentlyBlocked = (enemies >= rallyEnemiesRequired);
                        if (squad.isRallyBlocked != currentlyBlocked) {
                            squad.isRallyBlocked = currentlyBlocked;
                            data.setDirty();
                            PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), new PacketSyncSquads(data.squads));
                        }
                    }
                }
            }
            // РџСЂРѕРІРµСЂРєР° РєРёС‚Р° Р»РёРґРµСЂР° РѕС‚СЂСЏРґР° (Р Р°Р· РІ 20 СЃРµРєСѓРЅРґ)
            if (globalTick % 400 == 0 && AASConfig.REQUIRE_OFFICER_FOR_SL.get()) {
                // РСЃРїРѕР»СЊР·СѓРµРј РёС‚РµСЂР°С‚РѕСЂ РёР»Рё РєРѕРїРёСЋ СЃРїРёСЃРєР°, С‚Р°Рє РєР°Рє Р±СѓРґРµРј СѓРґР°Р»СЏС‚СЊ СЌР»РµРјРµРЅС‚С‹ РёР· data.squads
                for (AASWorldData.Squad squad : new ArrayList<>(data.squads)) {
                    ServerPlayer leader = server.getPlayerList().getPlayerByName(squad.leader);

                    if (leader != null) {
                        if (leader.isCreative()) {
                            squad.slNoOfficerSince = -1;
                            continue;
                        }

                        String current = leader.getPersistentData().getString("AAS_CurrentKit");
                        String pending = leader.getPersistentData().getString("AAS_PendingKit");
                        String kitName = !pending.isEmpty() ? pending : current;

                        boolean hasCommandKit = false;
                        if (!kitName.isEmpty() && !kitName.equals("Unassigned")) {
                            String team = squad.team.toUpperCase();
                            AASWorldData.KitInfo kitInfo = data.activeKits(team).get(kitName);
                            if (kitInfo != null && kitInfo.isLeaderOnly) {
                                hasCommandKit = true;
                            }
                        }

                        if (!hasCommandKit) {
                            if (squad.slNoOfficerSince == -1) squad.slNoOfficerSince = level.getGameTime();

                            long remaining = 2400 - (level.getGameTime() - squad.slNoOfficerSince);

                            if (remaining <= 0) {
                                // 1. РћРїРѕРІРµС‰Р°РµРј РјРёСЂ
                                broadcastTeamMessage(level, squad.team, Component.translatable("aas.msg.squad_disbanded_no_officer", squad.name).getString(), ChatFormatting.RED);

                                // 2. РџСЂРѕС…РѕРґРёРј РїРѕ РІСЃРµРј СѓС‡Р°СЃС‚РЅРёРєР°Рј (РґР°Р¶Рµ РѕС„С„Р»Р°Р№РЅ) Рё С‡РёСЃС‚РёРј РёС… РґР°РЅРЅС‹Рµ
                                for (String memberName : new ArrayList<>(squad.members)) {
                                    ServerPlayer m = server.getPlayerList().getPlayerByName(memberName);
                                    if (m != null) {
                                        // РЎР±СЂР°СЃС‹РІР°РµРј РєРёС‚
                                        m.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
                                        m.getPersistentData().remove("AAS_PendingKit");

                                        // РЈРґР°Р»СЏРµРј РїСЂРёРІСЏР·РєСѓ Рє РѕС‚СЂСЏРґСѓ
                                        m.getPersistentData().remove("AAS_SquadID");
                                        m.getPersistentData().remove("AAS_IsSquadLeader");

                                        // Р—Р°Р±РёСЂР°РµРј СЂР°С†РёСЋ
                                        PacketSquadAction.removeRadio(m);

                                        // РћС‡РёС‰Р°РµРј РёРЅРІРµРЅС‚Р°СЂСЊ (С‚Р°Рє РєР°Рє Р±РµР· РѕС‚СЂСЏРґР° РєРёС‚ РЅРµ РїРѕР»РѕР¶РµРЅ)
                                        m.getInventory().clearContent();
                                        ResupplyHandler.clearCurios(m);
                                        m.inventoryMenu.broadcastChanges();
                                        m.containerMenu.broadcastChanges();

                                        m.displayClientMessage(Component.translatable("aas.msg.squad_disbanded_member").withStyle(ChatFormatting.RED), true);
                                    }
                                }

                                // 3. РЈРґР°Р»СЏРµРј СЃР°Рј РѕС‚СЂСЏРґ РёР· РґР°РЅРЅС‹С… СЃРµСЂРІРµСЂР°
                                data.squads.remove(squad);
                                data.setDirty();

                                // 4. РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј СЃРїРёСЃРѕРє РѕС‚СЂСЏРґРѕРІ СЃ РєР»РёРµРЅС‚Р°РјРё, С‡С‚РѕР±С‹ РјРµРЅСЋ "K" РѕР±РЅРѕРІРёР»РѕСЃСЊ
                                PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),
                                        new PacketSyncSquads(data.squads));

                            } else {
                                leader.displayClientMessage(Component.translatable("aas.msg.squad_leader_kit_warning", (remaining / 20)), true);
                            }
                        } else {
                            squad.slNoOfficerSince = -1;
                        }
                    }
                }
            }
            // Р’РЅСѓС‚СЂРё С†РёРєР»Р° for (ServerLevel level : server.getAllLevels()) РІ GameLogicEvents.java

            long now = level.getGameTime();
            boolean squadsChanged = false;

            for (AASWorldData.Squad squad : data.squads) {
                if (squad.rhombusMarkers.removeIf(rm -> now >= rm.expiryTick)) {
                    squadsChanged = true;
                }
                if (squad.bravoRhombusMarkers.removeIf(rm -> now >= rm.expiryTick)) {
                    squadsChanged = true;
                }
                if (squad.charlieRhombusMarkers.removeIf(rm -> now >= rm.expiryTick)) {
                    squadsChanged = true;
                }

                if (squad.rallyPos != null && squad.rallyExpiryTick != -1 && now >= squad.rallyExpiryTick) {

                    // Р•СЃР»Рё С‡Р°РЅРє Р·Р°РіСЂСѓР¶РµРЅ, РїРѕРјРµС‡Р°РµРј СЂР°Р»Р»РёРє РєР°Рє "РёСЃС‚РµРєС€РёР№", С‡С‚РѕР±С‹ РЅРµ Р±С‹Р»Рѕ С€С‚СЂР°С„Р° -20 С‚РёРєРµС‚РѕРІ
                    if (level.isLoaded(squad.rallyPos)) {
                        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(squad.rallyPos);
                        if (be instanceof RallyPointBlockEntity rbe) {
                            rbe.isDecay = true;
                        }
                        level.removeBlock(squad.rallyPos, false);
                    } else {
                        // Р•СЃР»Рё С‡Р°РЅРє РќР• Р·Р°РіСЂСѓР¶РµРЅ, Р±Р»РѕРє СѓРґР°Р»РёС‚СЃСЏ СЃР°Рј, РєРѕРіРґР° РёРіСЂРѕРє РїРѕРґРѕР№РґРµС‚,
                        // РЅРѕ РёР· РґР°РЅРЅС‹С… РєР°СЂС‚С‹ РёРєРѕРЅРєР° РґРѕР»Р¶РЅР° РїСЂРѕРїР°СЃС‚СЊ РїСЂСЏРјРѕ СЃРµР№С‡Р°СЃ:
                        data.blueRallies.remove(squad.rallyPos);
                        data.redRallies.remove(squad.rallyPos);
                        squad.rallyPos = null;
                        squad.rallyExpiryTick = -1;
                        squadsChanged = true;
                    }
                }
            }

            if (squadsChanged) {
                data.setDirty();
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncSquads(data.squads));
            }
            // 2. РџСЂРѕРІРµСЂРєР° РЎРљР’РђР”РќР«РҐ РјРµС‚РѕРє (Move/Attack Рё С‚.Рґ.) - СЂР°Р· РІ 10 СЃРµРєСѓРЅРґ
            // Р›РёС€РЅСЏСЏ СЃС‚СЂРѕРєР° AASWorldData data = ... РЈР”РђР›Р•РќРђ, С‚Р°Рє РєР°Рє data СѓР¶Рµ РµСЃС‚СЊ РІС‹С€Рµ
            if (globalTick % 200 == 0) {
                long currentTick = level.getGameTime();
                boolean anyMarkerRemoved = false;
                for (AASWorldData.Squad squad : data.squads) {
                    if (squad.marker != null && currentTick >= squad.marker.expiryTick) {
                        squad.marker = null; // РЈРґР°Р»СЏРµРј РјРµС‚РєСѓ
                        anyMarkerRemoved = true;
                    }
                }

                if (anyMarkerRemoved) {
                    data.setDirty();
                    // Р Р°СЃСЃС‹Р»Р°РµРј РѕР±РЅРѕРІР»РµРЅРЅС‹Рµ РґР°РЅРЅС‹Рµ Рѕ СЃРєРІР°РґР°С… (С‡С‚РѕР±С‹ РїСЂРѕРїР°Р»Рё Р»РёРЅРёРё)
                    PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),
                            new PacketSyncSquads(data.squads));
                }
            }
            // === 2. Р›РћР“РРљРђ РћРўРЎР§Р•РўРђ Р”Рћ РЎРўРђР РўРђ ===
            if (data.countdownActive) { // <--- РћР‘РЇР—РђРўР•Р›Р¬РќРђРЇ РџР РћР’Р•Р РљРђ
                if (data.countdownTicks > 0) {

                    // РЎРЅР°С‡Р°Р»Р° РїСЂРѕРІРµСЂСЏРµРј Рё РїРѕРєР°Р·С‹РІР°РµРј С†РёС„СЂСѓ, Р° РўРћР›Р¬РљРћ РџРћРўРћРњ СѓРјРµРЅСЊС€Р°РµРј
                    if (data.countdownTicks % 20 == 0) {
                        int seconds = data.countdownTicks / 20;
                        sendTitleToLevel(level, String.valueOf(seconds), ChatFormatting.YELLOW);
                        level.playSound(null, new BlockPos(0, 100, 0), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), net.minecraft.sounds.SoundSource.MASTER, 1f, 1f);
                    }

                    data.countdownTicks--;
                    data.setDirty();

                } else {
                    // Р­С‚РѕС‚ Р±Р»РѕРє СЃСЂР°Р±РѕС‚Р°РµС‚ РћР”РРќ Р РђР—, РєРѕРіРґР° С‚РёРєРё СЃС‚Р°РЅСѓС‚ 0
                    data.countdownActive = false;
                    sendTitleToLevel(level, "GO!", ChatFormatting.GREEN);
                    data.isGameStarted = true;
                    VehicleSpawnerBlockEntity.recheckAll(level);   // check #4
                    com.example.aas.uav.UavManager.onMatchStart(level, data);
                    com.example.aas.deck.DeckManager.onMatchStart(level, data);
                    com.example.aas.art.ArtManager.onMatchStart(level, data);
                    if (data.gameMode.equalsIgnoreCase("INVASION")) {
                        // РЈСЃС‚Р°РЅР°РІР»РёРІР°РµРј С‚Р°Р№РјРµСЂ РїРѕРґРіРѕС‚РѕРІРєРё (РјРёРЅСѓС‚С‹ * 60 СЃРµРє * 20 С‚РёРєРѕРІ)
                        data.invasionPrepTicks = AASConfig.INVASION_PREP_TIME_MINUTES.get() * 1200;
                    }
                    data.setDirty();
                    sendSyncPacket(level, data);
                    // === Р’РЎРўРђР’РРўР¬ РЎР®Р”Рђ: Р’С‹РґР°РµРј РєРёС‚С‹ СЂРѕРІРЅРѕ 1 СЂР°Р· РїСЂРё СЃС‚Р°СЂС‚Рµ РёРіСЂС‹! ===
                    // === РќРћР’РћР•: Р’Р«Р”РђР•Рњ Р’Р•Р©Р Р’РЎР•Рњ РР“Р РћРљРђРњ РџР Р РЎРўРђР РўР• ===
                    for (ServerPlayer p : level.players()) {
                        String pending = p.getPersistentData().getString("AAS_PendingKit");
                        String current = p.getPersistentData().getString("AAS_CurrentKit");

                        String kitToApply = !pending.isEmpty() ? pending : current;

                        String pTeam = p.getTeam() != null ? p.getTeam().getName().toUpperCase() : "NEUTRAL";
                        boolean isAttacker = data.gameMode.equalsIgnoreCase("INVASION") && !pTeam.equals(data.invasionDefender);

                        if (isAttacker) {
                            p.displayClientMessage(Component.literal("Gear will be issued after preparation!").withStyle(ChatFormatting.YELLOW), true);
                            continue;
                        }

                        // Р”РѕР±Р°РІСЊС‚Рµ РІС‹Р·РѕРІ РІС‹РґР°С‡Рё Р·РґРµСЃСЊ, РµСЃР»Рё РµРіРѕ РЅРµС‚
                        ResupplyHandler.tryApplyPendingKit(p, data);
                    }


                    for (BlockPos p : data.triggerBlocks) {
                        if (level.isLoaded(p)) {
                            BlockState st = level.getBlockState(p);
                            if (st.is(ModBlocks.GAME_START_TRIGGER.get())) {
                                level.setBlock(p, st.setValue(GameStartTriggerBlock.POWERED, true), 3);
                                level.scheduleTick(p, ModBlocks.GAME_START_TRIGGER.get(), 20);
                            }
                        }
                    }
                    sendSyncPacket(level, data);
                }
            }
            if (data.isGameStarted && data.invasionPrepTicks > 0) {
                data.invasionPrepTicks--;
                if (data.invasionPrepTicks % 20 == 0) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                }
                if (data.invasionPrepTicks == 0) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                    broadcastMessage(level, Component.translatable("aas.msg.invasion_prep_end").getString(), ChatFormatting.RED);

                    for (ServerPlayer p : level.players()) {
                        String pTeam = p.getTeam() != null ? p.getTeam().getName().toUpperCase() : "NEUTRAL";
                        if (!pTeam.equals(data.invasionDefender) && !pTeam.equals("NEUTRAL")) {
                            ResupplyHandler.tryApplyPendingKit(p, data);
                            p.displayClientMessage(Component.literal("Preparation ended. Gear issued!").withStyle(ChatFormatting.GREEN), false);
                        }
                    }
                    // === РќРћР’Р«Р™ Р‘Р›РћРљ: РЎР РђР‘РђРўР«Р’РђРќРР• INVASION PREP РўР РР“Р“Р•Р Рђ ===
                    for (BlockPos p : data.triggerBlocks) {
                        if (level.isLoaded(p)) {
                            BlockState st = level.getBlockState(p);
                            if (st.is(ModBlocks.INVASION_PREP_TRIGGER.get())) {
                                level.setBlock(p, st.setValue(InvasionPrepTriggerBlock.POWERED, true), 3);
                                level.scheduleTick(p, ModBlocks.INVASION_PREP_TRIGGER.get(), 20); // 20 С‚РёРєРѕРІ = 1 СЃРµРє СЃРёРіРЅР°Р»Р°
                            }
                        }
                    }
                }
            } // <--- Р’РћРў Р­РўРђ РЎРљРћР‘РљРђ Р‘Р«Р›Рђ РџР РћРџРЈР©Р•РќРђ (Р·Р°РєСЂС‹РІР°РµС‚ РїСЂРѕРІРµСЂРєСѓ invasionPrepTicks > 0)
            // Р’РЅСѓС‚СЂРё onServerTick, С‚Р°Рј РіРґРµ РёРґРµС‚ РѕСЃРЅРѕРІРЅР°СЏ Р»РѕРіРёРєР° РёРіСЂС‹
            if (data.isGameStarted && AASConfig.LOW_TICKETS_SIREN.get()) {
                // РџСЂРѕРІРµСЂРєР° РґР»СЏ РЎРёРЅРёС…
                if (data.blueTickets <= 50 && data.blueTickets > 0 && !data.playedBlueSiren) {
                    playSirenForTeam(level, "Blue");
                    data.playedBlueSiren = true;
                    data.setDirty();
                }
                // РџСЂРѕРІРµСЂРєР° РґР»СЏ РљСЂР°СЃРЅС‹С…
                if (data.redTickets <= 50 && data.redTickets > 0 && !data.playedRedSiren) {
                    playSirenForTeam(level, "Red");
                    data.playedRedSiren = true;
                    data.setDirty();
                }
            }
            // === 3. Р’Р«Р§РРЎР›Р•РќРР• TICKET BLITZ (BLEED) ===
            boolean gameEnded = (data.blueTickets <= 0 || data.redTickets <= 0);

            if (data.isGameStarted && !gameEnded && !data.capturePoints.isEmpty()) {
                int totalPoints = data.capturePoints.size();
                long blueOwned = data.capturePoints.stream().filter(p -> p.owner.equalsIgnoreCase("BLUE")).count();
                long redOwned = data.capturePoints.stream().filter(p -> p.owner.equalsIgnoreCase("RED")).count();

                // Р›РћР“РРљРђ: РљРѕРјР°РЅРґР° С‚РµСЂСЏРµС‚ С‚РёРєРµС‚С‹, РµСЃР»Рё Сѓ РЅРµС‘ 0 С‚РѕС‡РµРє, Рђ Сѓ РІСЂР°РіР° Р·Р°С…РІР°С‡РµРЅРѕ (Р’СЃРµРіРѕ - 1) РёР»Рё Р±РѕР»СЊС€Рµ.
                if (data.gameMode.equalsIgnoreCase("INVASION")) {
                    // Р’ РРЅРІР°Р·РёРё РЅРµС‚ РєР»Р°СЃСЃРёС‡РµСЃРєРѕРіРѕ Р±Р»РёС‚С†Р° (РєСЂРѕРІРѕС‚РµС‡РµРЅРёСЏ С‚РёРєРµС‚РѕРІ)
                    blueIsBleeding = false;
                    redIsBleeding = false;

                    // РћРїСЂРµРґРµР»СЏРµРј РєРѕРјР°РЅРґСѓ Р°С‚Р°РєРё
                    String attacker = data.invasionDefender.equalsIgnoreCase("BLUE") ? "RED" : "BLUE";
                    long attackerOwned = (attacker.equals("BLUE")) ? blueOwned : redOwned;

                    // Р•СЃР»Рё Р°С‚Р°РєР° Р·Р°С…РІР°С‚РёР»Р° Р’РЎР• С‚РѕС‡РєРё
                    if (attackerOwned >= totalPoints && totalPoints > 0) {
                        if (data.invasionDefender.equalsIgnoreCase("BLUE")) {
                            data.blueTickets = 0; // Р—Р°С‰РёС‚Р° РїСЂРѕРёРіСЂР°Р»Р°
                        } else {
                            data.redTickets = 0;
                        }
                        checkGameOver(level, data);
                    }
                } else {
                    // РЎС‚Р°СЂР°СЏ Р»РѕРіРёРєР° Р±Р»РёС‚С†Р° С‚РѕР»СЊРєРѕ РґР»СЏ СЂРµР¶РёРјР° AAS
                    blueIsBleeding = (blueOwned == 0) && (redOwned >= totalPoints - 1) && (totalPoints > 0);
                    redIsBleeding = (redOwned == 0) && (blueOwned >= totalPoints - 1) && (totalPoints > 0);
                }

                // РћС‚РЅРёРјР°РµРј С‚РёРєРµС‚С‹ РєР°Р¶РґС‹Рµ 40 С‚РёРєРѕРІ (2 СЃРµРєСѓРЅРґС‹)
                if (globalTick % 40 == 0) {
                    boolean changed = false;
                    if (blueIsBleeding) {
                        data.blueTickets -= 1;
                        changed = true;
                    }
                    if (redIsBleeding) {
                        data.redTickets -= 1;
                        changed = true;
                    }

                    if (changed) {
                        checkGameOver(level, data);
                        data.setDirty();
                    }
                }
            }

            // === 4. Р’РђР›РР”РђР¦РРЇ Р РЎРРќРҐР РћРќРР—РђР¦РРЇ ===
            // РџРµСЂРµРґР°РµРј СЃС‚Р°С‚СѓСЃ Р±Р»РёС‚С†Р° (blueIsBleeding/redIsBleeding) РІ РјРµС‚РѕРґ СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёРё,
            // С‡С‚РѕР±С‹ РєР»РёРµРЅС‚ СѓРІРёРґРµР» РјРёРіР°РЅРёРµ С‚РёРєРµС‚РѕРІ.
            if (globalTick % 20 == 0) {
                validateAndSync(level, false, blueIsBleeding, redIsBleeding);
            }

            // === РћР§РљР Р—Рђ Р’РћР–Р”Р•РќРР• РўР•РҐРќРРљР ===
            if (globalTick % 20 == 0) {
                for (ServerPlayer p : level.players()) {
                    if (p.getVehicle() != null && p.getVehicle().getControllingPassenger() == p) {
                        // РЎС‡РёС‚Р°РµРј РїР°СЃСЃР°Р¶РёСЂРѕРІ-Р»СЋРґРµР№, РєСЂРѕРјРµ РІРѕРґРёС‚РµР»СЏ
                        long passCount = p.getVehicle().getPassengers().stream().filter(e -> e instanceof ServerPlayer).count() - 1;
                        if (passCount > 0) {
                            int driveTimer = p.getPersistentData().getInt("AAS_DriveStatsTimer") + 1;
                            if (driveTimer >= 60) { // РџСЂРѕС€Р»Р° 1 РјРёРЅСѓС‚Р° РІРѕР¶РґРµРЅРёСЏ
                                int transportTp = (int) Math.min(ScoreValues.MAX_ACTION, passCount * ScoreValues.TRANSPORT_TP_PER_PASSENGER);
                                StatsHandler.addScoreCustom(p, ScoreType.TRANSPORT, transportTp, 0);
                                p.getPersistentData().putInt("AAS_DriveStatsTimer", 0);
                            } else {
                                p.getPersistentData().putInt("AAS_DriveStatsTimer", driveTimer);
                            }
                        } else {
                            p.getPersistentData().remove("AAS_DriveStatsTimer");
                        }
                    } else {
                        p.getPersistentData().remove("AAS_DriveStatsTimer");
                    }
                }
            }

        }
    }
    private static boolean isTeamReady(ServerLevel level, String teamName, AASWorldData data) {
        List<ServerPlayer> teamPlayers = level.players().stream()
                .filter(p -> p.getTeam() != null && p.getTeam().getName().equalsIgnoreCase(teamName))
                .toList();

        if (teamPlayers.isEmpty()) return true; // Р•СЃР»Рё РІ РєРѕРјР°РЅРґРµ РЅРёРєРѕРіРѕ, РѕРЅР° РЅРµ РјРµС€Р°РµС‚ СЃС‚Р°СЂС‚Сѓ

        long yesVotes = teamPlayers.stream()
                .filter(p -> data.votes.getOrDefault(p.getUUID(), false))
                .count();

        float percent = (float) yesVotes / teamPlayers.size() * 100f;
        boolean isReady = percent >= AASConfig.VOTE_REQUIRED_PERCENTAGE.get();

        return isReady;
    }

    // === Р’РЎРџРћРњРћР“РђРўР•Р›Р¬РќР«Р™ РњР•РўРћР” Р”Р›РЇ РЎР‘РћР Рђ Р”РђРќРќР«РҐ РћР‘ РР“Р РћРљРђРҐ ===
    private static List<MapPlayerInfo> buildPlayerInfo(List<ServerPlayer> players, AASWorldData data) {
        List<MapPlayerInfo> infoList = new ArrayList<>();

        for (ServerPlayer p : players) {
            String pName = p.getScoreboardName();
            String pTeam = (p.getTeam() != null) ? p.getTeam().getName().toUpperCase() : "NEUTRAL";

            int squadId = p.getPersistentData().getInt("AAS_SquadID");
            if (squadId == 0) squadId = -1;

            boolean isLeader = p.getPersistentData().getBoolean("AAS_IsSquadLeader");

            // РќРћР’РћР•: СЂРѕР»СЊ РІРѕ С„Р°РµСЂС‚РёРјРµ
            String fireteamRole = "";

            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(pName)) {
                    squadId = s.id;
                    if (s.leader.equals(pName)) isLeader = true;
                    if (s.bravoLeader.equals(pName)) fireteamRole = "B";
                    else if (s.charlieLeader.equals(pName)) fireteamRole = "C";
                    break;
                }
            }

            boolean inVehicle = p.getVehicle() != null;
            int vId = -1;
            int seatIdx = -1;

            if (inVehicle) {
                Entity vehicle = p.getVehicle();
                vId = vehicle.getId();
                seatIdx = vehicle.getPassengers().indexOf(p);
            }

            boolean downed = p.getPersistentData().getBoolean("AAS_IsDowned");
            boolean dead = !p.isAlive();
            long shout = p.getPersistentData().getLong("AAS_LastMedicShoutTimeMS");

            // РќРћР’РћР•: СЃРЅР°СЂСЏР¶РµРЅРёРµ РјРµРґРёРєР°
            String pKit = p.getPersistentData().getString("AAS_CurrentKit");
            boolean isMedic = "Medic".equalsIgnoreCase(pKit);

            infoList.add(new MapPlayerInfo(
                    pName, p.getUUID(), p.getX(), p.getZ(), p.getYRot(),
                    squadId, isLeader, downed, dead, shout,
                    inVehicle, vId, seatIdx,
                    pTeam, isMedic, fireteamRole
            ));
        }
        return infoList;
    }
    private static List<PlayerStatInfo> buildStatsInfo(List<ServerPlayer> players, AASWorldData data) {
        List<PlayerStatInfo> list = new ArrayList<>();

        for (ServerPlayer p : players) {
            String pName = p.getScoreboardName();
            String pTeam = (p.getTeam() != null) ? p.getTeam().getName().toUpperCase() : "";

            int squadId = -1;
            boolean isLeader = false;
            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(pName)) {
                    squadId = s.id;
                    isLeader = s.leader.equals(pName);
                    break;
                }
            }

            boolean isCMD = (pTeam.equals("BLUE") && squadId == data.blueCMDId && data.blueCMDId != -1) ||
                    (pTeam.equals("RED") && squadId == data.redCMDId && data.redCMDId != -1);

            int kills = p.getPersistentData().getInt("AAS_Stats_Kills");
            int deaths = p.getPersistentData().getInt("AAS_Stats_Deaths");
            int tp = p.getPersistentData().getInt("AAS_Stats_TeamPoints");
            int sp = p.getPersistentData().getInt("AAS_Stats_SquadPoints");
            int revives = StatsHandler.getRevives(p);
            int vehTotal = StatsHandler.getVehKillsTotal(p);
            int[] vehByCat = StatsHandler.getVehKillsByCategory(p);
            int ping = p.latency;

            list.add(new PlayerStatInfo(pName, pTeam, squadId, isLeader, isCMD, kills, deaths, tp, sp, revives, vehTotal, vehByCat, ping));
        }
        return list;
    }
    // Р’ С„Р°Р№Р»Рµ GameLogicEvents.java Р·Р°РјРµРЅСЏРµРј РјРµС‚РѕРґ onWorldTick РїРѕР»РЅРѕСЃС‚СЊСЋ
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();
        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());

        // РџСЂРѕРІРµСЂСЏРµРј, СЏРІР»СЏРµС‚СЃСЏ Р»Рё СЌРЅС‚РёС‚Рё РјРёРЅРѕР№ РёР»Рё РєР»РµР№РјРѕСЂРѕРј РёР· РјРѕРґР° SuperbWarfare
        if (rl != null && (rl.toString().equals("superbwarfare:tm_62") || rl.toString().equals("superbwarfare:claymore"))) {
            ServerLevel serverLevel = (ServerLevel) event.getLevel();
            AASWorldData data = AASWorldData.get(serverLevel);

            // Р•СЃР»Рё СЌС‚Р° РјРёРЅР° СѓР¶Рµ РµСЃС‚СЊ РІ СЃРїРёСЃРєРµ - РЅРµ РґРѕР±Р°РІР»СЏРµРј РїРѕРІС‚РѕСЂРЅРѕ
            if (data.markedVehicles.stream().anyMatch(v -> v.uuid.equals(entity.getUUID()))) {
                return;
            }

            String team = "NEUTRAL";

            // РџС‹С‚Р°РµРјСЃСЏ РІР·СЏС‚СЊ РєРѕРјР°РЅРґСѓ РёР· СЃРѕС…СЂР°РЅРµРЅРЅС‹С… NBT (РµСЃР»Рё СЌС‚Рѕ РїРµСЂРµР·Р°РіСЂСѓР·РєР° С‡Р°РЅРєР°)
            if (entity.getPersistentData().contains("AAS_VehicleTeam")) {
                team = entity.getPersistentData().getString("AAS_VehicleTeam");
            } else {
                // РС‰РµРј РІР»Р°РґРµР»СЊС†Р° РїСЂРё РїРµСЂРІРёС‡РЅРѕРј СЃРїР°РІРЅРµ
                Player owner = null;

                // РџСЂРѕРІРµСЂСЏРµРј, РµСЃР»Рё СЌРЅС‚РёС‚Рё - Projectile
                if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                    if (proj.getOwner() instanceof Player p) owner = p;
                }
                // РџСЂРѕРІРµСЂСЏРµРј, РµСЃР»Рё СЌРЅС‚РёС‚Рё СЂРµР°Р»РёР·СѓРµС‚ OwnableEntity
                if (owner == null && entity instanceof net.minecraft.world.entity.OwnableEntity ownable) {
                    if (ownable.getOwner() instanceof Player p) owner = p;
                }
                // Р•СЃР»Рё API РјРѕРґР° РЅРµ РґР°Р»Рѕ РІР»Р°РґРµР»СЊС†Р°, Р±РµСЂРµРј Р±Р»РёР¶Р°Р№С€РµРіРѕ РёРіСЂРѕРєР° РІ СЂР°РґРёСѓСЃРµ 10 Р±Р»РѕРєРѕРІ РєР°Рє С‚РѕРіРѕ, РєС‚Рѕ Р±СЂРѕСЃРёР»/РїРѕСЃС‚Р°РІРёР»
                if (owner == null) {
                    owner = serverLevel.getNearestPlayer(entity, 10.0);
                }

                // Р—Р°РїРёСЃС‹РІР°РµРј РєРѕРјР°РЅРґСѓ
                if (owner != null && owner.getTeam() != null) {
                    team = owner.getTeam().getName().toUpperCase();
                    entity.getPersistentData().putString("AAS_VehicleTeam", team);
                    entity.getPersistentData().putString("AAS_VehicleType", "Mine");
                }
            }

            // Р”РѕР±Р°РІР»СЏРµРј РјР°СЂРєРµСЂ РґР»СЏ СЃРѕСЋР·РЅРёРєРѕРІ
            if (!team.equals("NEUTRAL")) {
                data.markedVehicles.add(new AASWorldData.VehicleRecord(
                        entity.getUUID(), team,
                        entity.getPersistentData().getString("AAS_VehicleType"),
                        entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(),
                        null // Сѓ РјРёРЅС‹ РЅРµС‚ СЃРїР°РІРЅРµСЂР°
                ));
                data.setDirty();
                PacketHandler.sendToAllClients(serverLevel, data);
            }
        }
    }
    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.level.isClientSide || event.phase != TickEvent.Phase.END) return;
        ServerLevel level = (ServerLevel) event.level;
        AASWorldData data = AASWorldData.get(level);
        Set<UUID> playersInPreciseZones = new HashSet<>();

        for (AASWorldData.CapturePoint point : data.capturePoints) {
            List<ServerPlayer> playersInBox = level.getEntitiesOfClass(ServerPlayer.class, point.getBoundingBox());
            int blueOnPointLiving = 0;
            int redOnPointLiving = 0;

            for (ServerPlayer p : playersInBox) {
                if (!p.isAlive() || p.isSpectator()) continue;
                if (p.getPersistentData().getBoolean("AAS_IsDowned")) continue;
                if (point.isInside(p.position())) {
                    if (p.getTeam() != null) {
                        if (p.getTeam().getName().equalsIgnoreCase("Blue")) blueOnPointLiving++;
                        else if (p.getTeam().getName().equalsIgnoreCase("Red")) redOnPointLiving++;
                    }
                }
            }

            String dominantTeam = "NONE";
            int alliesOnPoint = 0;
            boolean isContested = false;
            boolean isTimeLocked = level.getGameTime() < point.lockedUntilTick;

            if (blueOnPointLiving > 0 && redOnPointLiving > 0) {
                if (blueOnPointLiving > redOnPointLiving) {
                    dominantTeam = "BLUE";
                    alliesOnPoint = blueOnPointLiving;
                } else if (redOnPointLiving > blueOnPointLiving) {
                    dominantTeam = "RED";
                    alliesOnPoint = redOnPointLiving;
                } else {
                    // Р•СЃР»Рё Р»СЋРґРµР№ РїРѕСЂРѕРІРЅСѓ - С‚РѕС‡РєР° РѕСЃРїРѕСЂРµРЅР° (Contested)
                    isContested = true;
                }
            } else if (blueOnPointLiving > 0) {
                dominantTeam = "BLUE";
                alliesOnPoint = blueOnPointLiving;
            } else if (redOnPointLiving > 0) {
                dominantTeam = "RED";
                alliesOnPoint = redOnPointLiving;
            }

            // РЎС‡РёС‚Р°РµРј РѕРЅР»Р°Р№РЅ РєРѕРјР°РЅРґС‹ (Р¶РёРІС‹Рµ, РЅРµ СЃРїРµРєС‚Р°С‚РѕСЂС‹) РЅР° СЌС‚РѕРј СѓСЂРѕРІРЅРµ
            int teamOnline = 0;
            for (ServerPlayer sp : level.players()) {
                if (!sp.isAlive() || sp.isSpectator()) continue;
                if (sp.getTeam() != null && sp.getTeam().getName().equalsIgnoreCase(dominantTeam)) {
                    teamOnline++;
                }
            }

            float multiplier = 1.0f;
            if (teamOnline > 0) {
                float share = (float) alliesOnPoint / teamOnline;              // РґРѕР»СЏ РєРѕРјР°РЅРґС‹ РЅР° С‚РѕС‡РєРµ
                float shareMultiplier = 1.0f + Math.min(share, 0.5f) / 0.5f * 3.0f;

                float absoluteMultiplier = 1.0f;
                if (alliesOnPoint > 1) absoluteMultiplier += (alliesOnPoint - 1) * 0.5f;
                if (absoluteMultiplier > 4.0f) absoluteMultiplier = 4.0f;

                multiplier = Math.min(shareMultiplier, absoluteMultiplier); // Р±РµСЂС‘Рј РјРёРЅРёРјСѓРј вЂ” СЌС‚Рѕ Рё РµСЃС‚СЊ "РёСЃРєР»СЋС‡РµРЅРёРµ" РґР»СЏ РјР°Р»РµРЅСЊРєРёС… РєРѕРјР°РЅРґ
            }
            if (multiplier > 4.0f) multiplier = 4.0f;

            // --- Р›РћР“РРљРђ РЎРўР Р•Р›РћРљ Р Р¦Р’Р•РўРђ ---
            int currentRate = 0;
            String teamToSync = "NONE";

            if (!dominantTeam.equals("NONE") && !isContested && !isTimeLocked) {
                boolean isOwner = point.owner.equals(dominantTeam);
                boolean isFullyCaptured = (isOwner && point.progress >= 1.0f);

                // РџР РћР’Р•Р РљРђ: РЇРІР»СЏРµС‚СЃСЏ Р»Рё РґРѕРјРёРЅРёСЂСѓСЋС‰Р°СЏ РєРѕРјР°РЅРґР° Р·Р°С‰РёС‚РЅРёРєРѕРј РІ СЂРµР¶РёРјРµ Invasion
                boolean isInvDefenderBlocking = data.gameMode.equalsIgnoreCase("INVASION") && dominantTeam.equals(data.invasionDefender);

                // Р•СЃР»Рё СЌС‚Рѕ РќР• РїРѕР»РЅР°СЏ Р±Р°Р·Р° Рё СЌС‚Рѕ РќР• Р·Р°С‰РёС‚РЅРёРє РРЅРІР°Р·РёРё вЂ” СЂР°Р·СЂРµС€Р°РµРј СЂР°СЃС‡РµС‚ СЃС‚СЂРµР»РѕС‡РµРє
                if (!isFullyCaptured && !isInvDefenderBlocking) {
                    if (canCapture(point, dominantTeam, data)) {
                        teamToSync = dominantTeam;

                        boolean isNeutralizing = (!point.owner.equals("NEUTRAL") && !point.owner.equals(dominantTeam))
                                || (point.owner.equals("NEUTRAL") && !point.capturingTeam.equals("NONE") && !point.capturingTeam.equals(dominantTeam));

                        currentRate = Math.round(multiplier);
                        if (isNeutralizing) currentRate = -currentRate;
                    }
                }
            }

            for (ServerPlayer p : playersInBox) {
                if (point.isInside(p.position())) {
                    playersInPreciseZones.add(p.getUUID());
                    boolean lockedUI = false;
                    String nextObjectiveForPlayer = "";
                    int lockSecondsLeft = 0;

                    if (p.getTeam() != null) {
                        String teamKey = p.getTeam().getName().equalsIgnoreCase("Blue") ? "BLUE" : "RED";
                        if (isTimeLocked) {
                            lockedUI = true;
                            lockSecondsLeft = (int) Math.max(0, (point.lockedUntilTick - level.getGameTime()) / 20);
                        } else if (!canCapture(point, teamKey, data)) {
                            lockedUI = true;
                            nextObjectiveForPlayer = findRequiredPointName(point, teamKey, data);
                        }
                    }

                    // РћС‚РїСЂР°РІР»СЏРµС‚ РїР°РєРµС‚ РёРіСЂРѕРєСѓ РЅР° С‚РѕС‡РєРµ (С‚РµРїРµСЂСЊ teamToSync С‚РѕР¶Рµ РїСѓСЃС‚, РµСЃР»Рё С‚РѕС‡РєР° Р·Р°Р±Р»РѕРєРёСЂРѕРІР°РЅР°)
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p),
                            new PacketSyncPoint(true, point.name, point.owner, point.progress, lockedUI, nextObjectiveForPlayer, isContested, teamToSync, currentRate, lockSecondsLeft));
                }
            }

            // --- Р›РћР“РРљРђ Р—РђРҐР’РђРўРђ Р Р’РћРЎРЎРўРђРќРћР’Р›Р•РќРРЇ (Decay) ---
            boolean isBeingActivelyCaptured = false;

            if (!dominantTeam.equals("NONE") && !isContested && !isTimeLocked) {
                if (canCapture(point, dominantTeam, data)) {

                    // РќРћР’РћР•: Р•СЃР»Рё РІ РРЅРІР°Р·РёРё Р·Р°С‰РёС‚Р° РёРјРµРµС‚ Р±РѕР»СЊС€РёРЅСЃС‚РІРѕ вЂ” РјС‹ РїСЂРѕСЃС‚Рѕ РќР• СЃС‚Р°РІРёРј С„Р»Р°Рі Р·Р°С…РІР°С‚Р°
                    if (data.gameMode.equalsIgnoreCase("INVASION") && dominantTeam.equals(data.invasionDefender)) {
                        isBeingActivelyCaptured = false; // РўРѕС‡РєР° РїСЂРѕСЃС‚Рѕ СЃС‚РѕРёС‚ РЅР° РјРµСЃС‚Рµ (Р·Р°Р±Р»РѕРєРёСЂРѕРІР°РЅР°)
                    } else {
                        isBeingActivelyCaptured = true;
                        float baseSpeed = 1.0f / (point.captureTimeMinutes * 60 * 20);
                        String oldOwner = point.owner;

                        handleTeamInfluence(point, dominantTeam, multiplier, baseSpeed, data, level);

                        if (!point.owner.equals(oldOwner) && !point.owner.equals("NEUTRAL")) {
                            if (point.lockDurationMinutes > 0) {
                                point.lockedUntilTick = level.getGameTime() + (point.lockDurationMinutes * 60L * 20L);
                                data.setDirty();
                            }
                        }
                    }
                }
            }

            // --- РћР§РљР Р—Рђ РџР РРЎРЈРўРЎРўР’РР• РќРђ РђРљРўРР’РќРћР™ РўРћР§РљР• (РєР°Р¶РґС‹Рµ 30 СЃРµРє, СЃ Р°РЅС‚Рё-Р°С„Рє) ---
            if (!playersInBox.isEmpty()) {
                PointPresenceTracker.tick(level, point, playersInBox, dominantTeam, isContested, isBeingActivelyCaptured, isTimeLocked,
                        canCapture(point, "BLUE", data), canCapture(point, "RED", data));
            }

            // Р•СЃР»Рё С‚РѕС‡РєР° РЅРµ Р·Р°Р±Р»РѕРєРёСЂРѕРІР°РЅР° РїРѕ РІСЂРµРјРµРЅРё, РЅРµ РѕСЃРїР°СЂРёРІР°РµС‚СЃСЏ (Contested) Рё Р°РєС‚РёРІРЅРѕ РЅРµ Р·Р°С…РІР°С‚С‹РІР°РµС‚СЃСЏ
            // (РЅР°РїСЂРёРјРµСЂ: РЅРёРєРѕРіРѕ РЅРµС‚, РёР»Рё СЃС‚РѕСЏС‚ РІСЂР°РіРё, Сѓ РєРѕС‚РѕСЂС‹С… РЅРµС‚ СЃРІСЏР·Рё СЃ РїСЂРµРґС‹РґСѓС‰РµР№ С‚РѕС‡РєРѕР№)
            // РС‰РµРј СЌС‚РѕС‚ Р±Р»РѕРє:
            if (!isBeingActivelyCaptured && !isContested && !isTimeLocked) {
                float baseSpeed = 1.0f / (point.captureTimeMinutes * 60 * 20);

                // РР—РњР•РќР•РќРќРћР• РЈРЎР›РћР’РР•: Р”РѕР±Р°РІР»СЏРµРј РїСЂРѕРІРµСЂРєСѓ РЅР° СЂРµР¶РёРј РёРіСЂС‹
                if (point.owner.equals("NEUTRAL") && point.progress > 0) {

                    // Р•СЃР»Рё СЂРµР¶РёРј РќР• Invasion вЂ” С‚РѕС‡РєР° РѕС‚РєР°С‚С‹РІР°РµС‚СЃСЏ РЅР°Р·Р°Рґ (СЃС‚Р°СЂРѕРµ РїРѕРІРµРґРµРЅРёРµ)
                    if (!data.gameMode.equalsIgnoreCase("INVASION")) {
                        point.progress -= (baseSpeed / 2.0f);
                        if (point.progress <= 0) {
                            point.progress = 0;
                            point.capturingTeam = "NONE";
                        }
                    }
                    // Р•СЃР»Рё СЂРµР¶РёРј Invasion вЂ” РЅРёС‡РµРіРѕ РЅРµ РґРµР»Р°РµРј (РїСЂРѕРіСЂРµСЃСЃ РїСЂРѕСЃС‚Рѕ СЃС‚РѕРёС‚ РЅР° РјРµСЃС‚Рµ)
                }

                // Р’РѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёРµ Р·Р°С…РІР°С‡РµРЅРЅРѕР№ С‚РѕС‡РєРё (Р±Р°Р·Р° СЃРІРѕРёС… РґРѕР»Р¶РЅР° С‡РёРЅРёС‚СЊСЃСЏ РІСЃРµРіРґР°)
                else if (!point.owner.equals("NEUTRAL") && point.progress < 1.0f) {
                    point.progress += (baseSpeed / 2.0f);
                    if (point.progress >= 1.0f) {
                        point.progress = 1.0f;
                        point.capturingTeam = "NONE";
                    }
                }
            }

            // === РћРџРўРРњРР—РР РћР’РђРќРќРђРЇ Р РђРЎРЎР«Р›РљРђ Р”Р›РЇ РљРђР РўР« ===
            if (level.getGameTime() % 10 == 0) {
                boolean isPointActive = (point.progress > 0.0f && point.progress < 1.0f) || !point.capturingTeam.equals("NONE");

                if (isPointActive) {
                    for (ServerPlayer player : level.players()) {
                        if (!playersInPreciseZones.contains(player.getUUID())) {
                            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                                    new PacketSyncPoint(false, point.name, point.owner, point.progress, false, "", isContested, point.capturingTeam, 0, 0));
                        }
                    }
                }
            }
        }

        for (ServerPlayer player : level.players()) {
            if (!playersInPreciseZones.contains(player.getUUID())) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncPoint(false, "", "", 0, false, "", false, "NONE", 0, 0));
            }
        }
    }

    private static void handleMainProtectionZones(ServerLevel level, AASWorldData data) {
        if (data.mainZones.isEmpty()) return;

        // 1. РџР•Р Р’Р«Р™ Р¦РРљР›: РћРіСЂР°РЅРёС‡РµРЅРёРµ РІС‹С…РѕРґР° СЃРѕ СЃРІРѕРµР№ Р±Р°Р·С‹ (РўРµР»РµРїРѕСЂС‚Р°С†РёСЏ РЅР°Р·Р°Рґ)
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;
            String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
            if (pTeam.equals("NEUTRAL")) continue;

            for (AASWorldData.MainProtectionZone zone : data.mainZones) {
                if (zone.team.equalsIgnoreCase(pTeam)) {
                    boolean isOutside = !zone.isInside(player.position());
                    boolean shouldRestrict = false;
                    String restrictMessageKey = "aas.msg.main_zone_wait_start";

                    if (!data.isGameStarted) {
                        // Р”Рѕ РёРіСЂС‹ - РѕРіСЂР°РЅРёС‡РµРЅС‹ Р’РЎР•
                        shouldRestrict = isOutside;
                        restrictMessageKey = "aas.msg.main_zone_wait_start";
                    } else if (data.gameMode.equalsIgnoreCase("INVASION") && data.invasionPrepTicks > 0) {
                        // Р’Рѕ РІСЂРµРјСЏ РїРѕРґРіРѕС‚РѕРІРєРё РІ РРЅРІР°Р·РёРё: С‚РѕР»СЊРєРѕ РђРўРђРљРђ РѕРіСЂР°РЅРёС‡РµРЅР°
                        if (!pTeam.equals(data.invasionDefender)) {
                            shouldRestrict = isOutside;
                            restrictMessageKey = "aas.msg.invasion_prep_wait";
                        }
                    }

                    if (shouldRestrict) {
                        String dim = level.dimension().location().toString();
                        BlockPos spawn = pTeam.equals("BLUE") ? data.blueSpawns.get(dim) : data.redSpawns.get(dim);
                        if (spawn != null) {
                            player.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
                            player.displayClientMessage(Component.translatable(restrictMessageKey).withStyle(ChatFormatting.YELLOW), true);
                        }
                    }
                    break;
                }
            }
        }

        // 2. Р’РўРћР РћР™ Р‘Р›РћРљ: Р—Р°С‰РёС‚Р° Р±Р°Р· РѕС‚ Р’Р РђР“РћР’ (РЈР±РёР№СЃС‚РІРѕ Р·Р°С€РµРґС€РёС… РЅР° С‡СѓР¶РѕР№ РјРµР№РЅ)
        // Р Р°РЅСЊС€Рµ С‚СѓС‚ СЃС‚РѕСЏР» РѕС€РёР±РѕС‡РЅС‹Р№ else, С‚РµРїРµСЂСЊ РїСЂР°РІРёР»СЊРЅС‹Р№ if
        if (data.isGameStarted) {
            for (AASWorldData.MainProtectionZone zone : data.mainZones) {
                List<Entity> entitiesInZone = level.getEntitiesOfClass(Entity.class, zone.area);

                for (Entity entity : entitiesInZone) {
                    if (!zone.isInside(entity.position())) continue;

                    // 5 СЃРµРєСѓРЅРґ РёРјРјСѓРЅРёС‚РµС‚Р° РґР»СЏ РЅРѕРІРѕР№ С‚РµС…РЅРёРєРё
                    if (entity.getPersistentData().contains("AAS_SpawnGraceTick")) {
                        long graceTick = entity.getPersistentData().getLong("AAS_SpawnGraceTick");
                        if (level.getGameTime() - graceTick < 100) continue;
                    }

                    // Р›РѕРіРёРєР° РґР»СЏ РёРіСЂРѕРєРѕРІ-РІСЂР°РіРѕРІ
                    if (entity instanceof ServerPlayer player) {
                        if (player.isCreative() || player.isSpectator()) continue;
                        String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";

                        if (!pTeam.equalsIgnoreCase(zone.team)) {
                            int ticks = player.getPersistentData().getInt("AAS_MainZoneTimer");
                            ticks++;
                            if (ticks >= 200) {
                                player.getPersistentData().remove("AAS_MainZoneTimer");
                                player.kill();
                            } else {
                                player.getPersistentData().putInt("AAS_MainZoneTimer", ticks);
                                if (ticks % 20 == 0) {
                                    int secLeft = 10 - (ticks / 20);
                                    player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 0));
                                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("aas.msg.main_zone_die", secLeft).withStyle(ChatFormatting.RED)));
                                    player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("aas.msg.enemy_main_title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                                }
                            }
                        } else {
                            player.getPersistentData().remove("AAS_MainZoneTimer");
                        }
                    }
                    // Р›РѕРіРёРєР° РґР»СЏ РІСЂР°Р¶РµСЃРєРёС… РјР°С€РёРЅ
                    else if (entity.getPersistentData().contains("AAS_VehicleTeam")) {
                        String vTeam = entity.getPersistentData().getString("AAS_VehicleTeam");
                        if (vTeam.equalsIgnoreCase("NEUTRAL") || vTeam.isEmpty()) {
                            entity.discard();
                        } else if (vTeam.equalsIgnoreCase(zone.team)) {
                            entity.getPersistentData().remove("AAS_MainZoneTimer");
                        } else {
                            int ticks = entity.getPersistentData().getInt("AAS_MainZoneTimer");
                            ticks++;
                            if (ticks >= 200) {
                                if (entity instanceof LivingEntity le) le.kill();
                                else entity.discard();
                            } else {
                                entity.getPersistentData().putInt("AAS_MainZoneTimer", ticks);
                                if (ticks % 20 == 0) {
                                    int secLeft = 10 - (ticks / 20);
                                    for (Entity passenger : entity.getPassengers()) {
                                        if (passenger instanceof ServerPlayer p) {
                                            p.displayClientMessage(Component.translatable("aas.msg.main_zone_veh_die", secLeft).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else {
                        if (entity instanceof net.minecraftforge.entity.PartEntity) continue;
                        if (entity instanceof net.minecraft.world.entity.Display) continue;
                        if (entity instanceof net.minecraft.world.entity.item.ItemEntity) continue;
                        entity.discard();
                    }
                }
            }

            // РћС‡РёСЃС‚РєР° С‚Р°Р№РјРµСЂРѕРІ С‚РµС…, РєС‚Рѕ РІС‹РµС…Р°Р»
            for (ServerPlayer player : level.players()) {
                if (player.getPersistentData().contains("AAS_MainZoneTimer")) {
                    boolean inEnemyZone = false;
                    String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
                    for (AASWorldData.MainProtectionZone zone : data.mainZones) {
                        if (!zone.team.equalsIgnoreCase(pTeam) && zone.isInside(player.position())) {
                            inEnemyZone = true; break;
                        }
                    }
                    if (!inEnemyZone) player.getPersistentData().remove("AAS_MainZoneTimer");
                }
            }
            // (РћС‡РёСЃС‚РєР° С‚Р°Р№РјРµСЂРѕРІ РјР°С€РёРЅ Р°РЅР°Р»РѕРіРёС‡РЅРѕ...)
            for (AASWorldData.VehicleRecord record : data.markedVehicles) {
                Entity vehicle = level.getEntity(record.uuid);
                if (vehicle != null && vehicle.getPersistentData().contains("AAS_MainZoneTimer")) {
                    boolean inEnemyZone = false;
                    String vTeam = vehicle.getPersistentData().getString("AAS_VehicleTeam");
                    for (AASWorldData.MainProtectionZone zone : data.mainZones) {
                        if (!zone.team.equalsIgnoreCase(vTeam) && zone.isInside(vehicle.position())) {
                            inEnemyZone = true; break;
                        }
                    }
                    if (!inEnemyZone) vehicle.getPersistentData().remove("AAS_MainZoneTimer");
                }
            }
        }
    }
    private static void handleVehicleAutoReturn(ServerLevel level, AASWorldData data) {
        if (data.markedVehicles.isEmpty()) return;
        long currentTick = level.getGameTime();
        boolean needsSync = false;

        // Р Р°Р±РѕС‚Р°РµРј СЃРѕ СЃРЅРёРјРєРѕРј СЃРїРёСЃРєР°, С‚.Рє. vEntity.discard() РЅРёР¶Рµ СЃРёРЅС…СЂРѕРЅРЅРѕ
        // С‚СЂРёРіРіРµСЂРёС‚ EntityLeaveLevelEvent -> onEntityRemove, РєРѕС‚РѕСЂС‹Р№ СЃР°Рј
        // С‡РёСЃС‚РёС‚ data.markedVehicles.removeIf(...) вЂ” РёР·РјРµРЅРµРЅРёРµ РѕСЂРёРіРёРЅР°Р»СЊРЅРѕРіРѕ
        // СЃРїРёСЃРєР° РїСЂСЏРјРѕ РІРѕ РІСЂРµРјСЏ РµРіРѕ РѕР±С…РѕРґР° РІС‹Р·С‹РІР°Р»Рѕ ConcurrentModificationException.
        for (AASWorldData.VehicleRecord record : new ArrayList<>(data.markedVehicles)) {
            if (!record.autoReturnEnabled) continue;

            long requiredTicks = (long) record.autoReturnTimeSeconds * 20L;
            Entity vEntity = level.getEntity(record.uuid);

            if (vEntity != null && vEntity.isAlive()) {
                if (hasPlayerOccupant(vEntity)) {
                    if (record.emptySinceTick != -1) { record.emptySinceTick = -1; needsSync = true; }
                    continue;
                }
                if (record.emptySinceTick == -1) {
                    record.emptySinceTick = currentTick;
                    needsSync = true;
                    continue;
                }
            } else {
                if (record.emptySinceTick == -1) {
                    record.emptySinceTick = currentTick;
                    needsSync = true;
                    continue;
                }
            }

            long elapsed = currentTick - record.emptySinceTick;
            if (elapsed < requiredTicks) continue;

            if (vEntity == null || !vEntity.isAlive()) {
                int cx = ((int) Math.floor(record.x)) >> 4;
                int cz = ((int) Math.floor(record.z)) >> 4;
                level.getChunk(cx, cz);
                vEntity = level.getEntity(record.uuid);
                if (vEntity == null || !vEntity.isAlive()) continue;
                if (hasPlayerOccupant(vEntity)) {
                    record.emptySinceTick = -1;
                    needsSync = true;
                    continue;
                }
            }

            boolean insideOwnMain = false;
            for (AASWorldData.MainProtectionZone zone : data.mainZones) {
                if (zone.team.equalsIgnoreCase(record.team) && zone.isInside(vEntity.position())) {
                    insideOwnMain = true;
                    break;
                }
            }
            if (insideOwnMain) {
                record.emptySinceTick = -1;
                needsSync = true;
                continue;
            }

            if (record.autoReturnDestroy) {
                vEntity.discard(); // Р±РµР·РѕРїР°СЃРЅРѕ: С‚РµРїРµСЂСЊ РјС‹ РёС‚РµСЂРёСЂСѓРµРјСЃСЏ РїРѕ РєРѕРїРёРё СЃРїРёСЃРєР°
            } else if (record.spawnerPos != null) {
                vEntity.ejectPassengers();
                vEntity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                double tx = record.spawnerPos.getX() + 0.5;
                double ty = record.spawnerPos.getY() + 2.5;
                double tz = record.spawnerPos.getZ() + 0.5;
                vEntity.teleportTo(tx, ty, tz);
                vEntity.getPersistentData().putLong("AAS_SpawnGraceTick", level.getGameTime());
                record.x = tx; record.y = ty; record.z = tz;
            }

            record.emptySinceTick = -1;
            needsSync = true;
        }

        if (needsSync) {
            data.setDirty();
            PacketHandler.sendToAllClients(level, data);
        }
    }

    // Р РµРєСѓСЂСЃРёРІРЅРѕ РїСЂРѕРІРµСЂСЏРµС‚, РµСЃС‚СЊ Р»Рё РёРіСЂРѕРє СЃСЂРµРґРё РїР°СЃСЃР°Р¶РёСЂРѕРІ (РІ С‚.С‡. РІ СЃРёРґРµРЅСЊСЏС…-Р·Р°РіР»СѓС€РєР°С…)
    private static boolean hasPlayerOccupant(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof ServerPlayer) return true;
            if (hasPlayerOccupant(passenger)) return true;
        }
        return false;
    }
    // Р›РµС‡РµРЅРёРµ РІ Р·РѕРЅРµ Р»РѕР±Р±Рё: Р·Р° С‚РёРє РґРѕР±Р°РІР»СЏРµС‚СЃСЏ СЃС‚РѕР»СЊРєРѕ HP, С‡С‚Рѕ С…РІР°С‚Р°РµС‚ РЅР° РјРіРЅРѕРІРµРЅРЅРѕРµ РїРѕР»РЅРѕРµ Р»РµС‡РµРЅРёРµ
    private static final float LOBBY_HEAL_PER_TICK = 100.0f;

    private static void handleLobbyZone(ServerLevel level, AASWorldData data) {
        if (data.lobbyDisabled || data.lobbyZones.isEmpty() || data.lobbyCenter == null) return;

        BlockPos center = data.lobbyCenter;

        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;

            boolean insideAnyZone = false;
            for (AASWorldData.LobbyZone zone : data.lobbyZones) {
                if (zone.isInside(player.position())) {
                    insideAnyZone = true;
                    break;
                }
            }

            if (insideAnyZone) {
                // РЎРёР»СЊРЅРѕРµ Р»РµС‡РµРЅРёРµ: Р¶РёРІРѕР№ Рё РЅРµ СЂР°РЅРµРЅС‹Р№ (СЂР°РЅРµРЅС‹С… РїРѕРґРЅРёРјР°РµС‚ РјРµС…Р°РЅРёРєР° СЂРµР°РЅРёРјР°С†РёРё)
                if (player.isAlive() && !player.getPersistentData().getBoolean("AAS_IsDowned")) {
                    if (player.getHealth() < player.getMaxHealth()) player.heal(LOBBY_HEAL_PER_TICK);
                    if (player.isOnFire()) player.clearFire();
                    if (player.getFoodData().getFoodLevel() < 20) player.getFoodData().setFoodLevel(20);
                }
            } else {
                player.teleportTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
                player.displayClientMessage(Component.translatable("aas.msg.lobby_zone_return").withStyle(ChatFormatting.YELLOW), true);
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerChangeDimension(net.minecraftforge.event.entity.EntityTravelToDimensionEvent event) {
        // РџСЂРѕРІРµСЂСЏРµРј, С‡С‚Рѕ РїРµСЂРµРјРµС‰Р°РµС‚СЃСЏ РёРјРµРЅРЅРѕ РёРіСЂРѕРє Рё СЌС‚Рѕ РїСЂРѕРёСЃС…РѕРґРёС‚ РЅР° СЃРµСЂРІРµСЂРµ
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);

            // РСЃРїРѕР»СЊР·СѓРµРј СѓР¶Рµ РіРѕС‚РѕРІСѓСЋ Р»РѕРіРёРєСѓ РІС‹С…РѕРґР° РёР· РѕС‚СЂСЏРґР°
            // РћРЅР° СѓРґР°Р»РёС‚ РёРіСЂРѕРєР° РёР· СЃРїРёСЃРєР° РѕС‚СЂСЏРґР°, СЃР±СЂРѕСЃРёС‚ РµРіРѕ С‚РµРіРё Рё РєРёС‚
            PacketSquadAction.leaveCurrentSquad(player, data);

            // РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј РёР·РјРµРЅРµРЅРёСЏ СЃРѕ РІСЃРµРјРё РёРіСЂРѕРєР°РјРё, С‡С‚РѕР±С‹ РѕР±РЅРѕРІРёС‚СЊ GUI
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new PacketSyncSquads(data.squads));

            // РћС‚РїСЂР°РІР»СЏРµРј СѓРІРµРґРѕРјР»РµРЅРёРµ РёРіСЂРѕРєСѓ
            player.sendSystemMessage(Component.translatable("aas.msg.squad_left_world")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
    private static String findRequiredPointName(AASWorldData.CapturePoint currentPoint, String team, AASWorldData data) {
        int currentPriority = team.equals("BLUE") ? currentPoint.bluePriority : currentPoint.redPriority;
        int requiredPriority = currentPriority - 1;

        if (requiredPriority < 1) return "";

        for (AASWorldData.CapturePoint p : data.capturePoints) {
            int pPriority = team.equals("BLUE") ? p.bluePriority : p.redPriority;
            if (pPriority == requiredPriority) {
                return p.name;
            }
        }
        return "Unknown";
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    PacketSyncServerConfig.fromConfig());
            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            sendSyncPacket(level, data);
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncSquads(data.squads));
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncDownedState(player.getId(), false));

            // РќРѕРІРѕРјСѓ РёРіСЂРѕРєСѓ СЃРѕРѕР±С‰Р°РµРј, РєС‚Рѕ СѓР¶Рµ Р»РµР¶РёС‚ РІ РЅРѕРєР°СѓС‚Рµ
            for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
                if (other != player && other.getPersistentData().getBoolean("AAS_IsDowned")) {
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                            new PacketSyncDownedState(other.getId(), true));
                }
            }

            if (!data.isGameStarted && player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("AAS Game is paused. /aas gamestart true to start.").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
            ServerLevel level = newPlayer.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            // Р’С‹РґР°РµРј РєРёС‚, РєРѕС‚РѕСЂС‹Р№ Р±С‹Р» РЅР° РёРіСЂРѕРєРµ РёР»Рё СЃС‚РѕРёС‚ РІ РѕС‡РµСЂРµРґРё (Pending)
            ResupplyHandler.tryApplyPendingKit(newPlayer, data);
            if (newPlayer.gameMode.getGameModeForPlayer() != GameType.CREATIVE) newPlayer.setGameMode(GameType.SURVIVAL);
            newPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(newPlayer.getAbilities()));
            pendingRespawnLocations.remove(newPlayer.getUUID());
            pendingTeams.remove(newPlayer.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            processEntityLoss(player);

            player.getPersistentData().putBoolean("AAS_IsDowned", false);
            StatsHandler.addDeath(player);

            boolean wasGivingUp = player.getPersistentData().getBoolean("AAS_GivingUp");
            TeamKillNotifier.onPlayerDeath(player, event.getSource(), wasGivingUp);

            // FIX: РµСЃР»Рё СЌС‚Рѕ "РґРѕР±РёС‚РёРµ" РїРѕСЃР»Рµ РЅРѕРєР° вЂ” РєРёР»Р» СѓР¶Рµ РЅР°С‡РёСЃР»РµРЅ РІ DownedHandler.forceGiveUp().
            // Р—РґРµСЃСЊ РЅР°С‡РёСЃР»СЏРµРј РєРёР»Р» С‚РѕР»СЊРєРѕ РґР»СЏ "РїСЂСЏРјС‹С…" СЃРјРµСЂС‚РµР№ (РЅР°РїСЂРёРјРµСЂ, РІР·СЂС‹РІ РІРѕ РІСЂРµРјСЏ РЅРѕРєР°),
            // С‡С‚РѕР±С‹ РЅРµ Р·Р°СЃС‡РёС‚Р°С‚СЊ РѕРґРёРЅ РєРёР»Р» РґРІР°Р¶РґС‹.
            if (!wasGivingUp && player.getLastHurtByMob() instanceof ServerPlayer killer && killer != player) {
                if (killer.getTeam() != player.getTeam()) {
                    StatsHandler.addScore(killer, ScoreType.ENEMY_KILLED);
                    StatsHandler.addKill(killer);
                }
            }

            if (wasGivingUp) {
                return; // Р’С‹С…РѕРґРёРј, С‡С‚РѕР±С‹ РЅРµ СЃРЅРёРјР°С‚СЊ С‚РёРєРµС‚С‹ РІС‚РѕСЂРѕР№ СЂР°Р·
            }
        }
    }
    private static void saveKitNbtBeforeDeath(ServerPlayer player) {
        AASWorldData data = AASWorldData.get(player.serverLevel());
        String kitName = player.getPersistentData().getString("AAS_CurrentKit");
        String team = (player.getTeam() != null) ? player.getTeam().getName().toUpperCase() : "";

        if (team.isEmpty() || kitName.isEmpty() || kitName.equals("Unassigned")) return;

        boolean isAltVariant = player.getPersistentData().getBoolean("AAS_CurrentKitAlt");
        AASWorldData.KitInfo kit = data.getKitVariant(team, kitName, isAltVariant);
        if (kit == null) return;

        Map<Integer, CompoundTag> savedTags = new HashMap<>();

        for (int i = 0; i < 41; i++) {
            // РџСЂРѕРІРµСЂСЏРµРј, СЃС‚РѕРёС‚ Р»Рё С„Р»Р°Рі СЃРѕС…СЂР°РЅРµРЅРёСЏ NBT РґР»СЏ СЌС‚РѕРіРѕ СЃР»РѕС‚Р°
            if (i < kit.saveNbtFlags.length && kit.saveNbtFlags[i]) {
                ItemStack item = player.getInventory().getItem(i);
                if (!item.isEmpty() && item.hasTag()) {
                    // РЎРѕС…СЂР°РЅСЏРµРј РєРѕРїРёСЋ NBT (Р·Р°С‡Р°СЂРѕРІР°РЅРёСЏ, РїР°С‚СЂРѕРЅС‹ Рё С‚.Рґ.)
                    savedTags.put(i, item.getTag().copy());
                }
            }
        }

        if (!savedTags.isEmpty()) {
            PERSISTENT_NBT_STORAGE.put(player.getUUID(), savedTags);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        ServerPlayer oldPlayer = (ServerPlayer) event.getOriginal();
        ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
        // РћС‡РёС‰Р°РµРј РІСЃРµ С„Р»Р°РіРё РЅРѕРєР° РїСЂРё РїРµСЂРµСЂРѕР¶РґРµРЅРёРё
        newPlayer.getPersistentData().putBoolean("AAS_IsDowned", false);
        newPlayer.getPersistentData().remove("AAS_GivingUp");
        newPlayer.getPersistentData().remove("AAS_DownedYaw");
        newPlayer.getPersistentData().remove("AAS_DownedPitch");
        newPlayer.getPersistentData().putLong("AAS_LastReviveTime", 0);

        // РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј СЃРѕСЃС‚РѕСЏРЅРёРµ "РЅРµ РІ РЅРѕРєРµ" СЃРѕ РІСЃРµРјРё
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncDownedState(newPlayer.getId(), false));
        // РџРµСЂРµРЅРѕСЃРёРј РґР°РЅРЅС‹Рµ Рѕ NBT РёР· С…СЂР°РЅРёР»РёС‰Р° РЅР° РЅРѕРІС‹Р№ UUID (РµСЃР»Рё UUID РІРґСЂСѓРі РјРµРЅСЏРµС‚СЃСЏ, РЅРѕ РѕР±С‹С‡РЅРѕ РѕРЅ С‚РѕС‚ Р¶Рµ)
        // РќРѕ С‚Р°Рє РєР°Рє РјС‹ РёСЃРїРѕР»СЊР·СѓРµРј СЃС‚Р°С‚РёС‡РµСЃРєРёР№ Map Рё UUID, РґР°РЅРЅС‹Рµ СЃРѕС…СЂР°РЅСЏС‚СЃСЏ РІ PERSISTENT_NBT_STORAGE Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё.

        // 1. РљРћРџРР РћР’РђРќРР• РџР•Р РЎРРЎРўР•РќРўРќР«РҐ Р”РђРќРќР«РҐ (Р’Р°С€ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёР№ РєРѕРґ)
        CompoundTag oldData = oldPlayer.getPersistentData();
        CompoundTag newData = newPlayer.getPersistentData();

        // TP, SP, РєРёР»Р»С‹, СЃРјРµСЂС‚Рё Рё СЃС‡С‘С‚С‡РёРєРё СѓРЅРёС‡С‚РѕР¶РµРЅРЅРѕР№ С‚РµС…РЅРёРєРё (РѕР±С‰РёР№ Рё РїРѕ РєР°С‚РµРіРѕСЂРёСЏРј)
        StatsHandler.copyStats(oldData, newData);

        if (oldData.contains("AAS_SquadID")) newData.putInt("AAS_SquadID", oldData.getInt("AAS_SquadID"));
        if (oldData.contains("AAS_IsSquadLeader")) newData.putBoolean("AAS_IsSquadLeader", oldData.getBoolean("AAS_IsSquadLeader"));
        if (oldData.contains("AAS_CurrentKit")) newData.putString("AAS_CurrentKit", oldData.getString("AAS_CurrentKit"));
        if (oldData.contains("AAS_CurrentKitAlt")) newData.putBoolean("AAS_CurrentKitAlt", oldData.getBoolean("AAS_CurrentKitAlt"));
        if (oldData.contains("AAS_PendingKit")) newData.putString("AAS_PendingKit", oldData.getString("AAS_PendingKit"));
        if (oldData.contains("AAS_PendingKitAlt")) newData.putBoolean("AAS_PendingKitAlt", oldData.getBoolean("AAS_PendingKitAlt"));
        if (oldData.contains("AAS_LastFobResupply")) newData.putLong("AAS_LastFobResupply", oldData.getLong("AAS_LastFobResupply"));
        if (oldData.contains("AAS_LastMainResupply")) newData.putLong("AAS_LastMainResupply", oldData.getLong("AAS_LastMainResupply"));

        // 2. Р’РћРЎРЎРўРђРќРћР’Р›Р•РќРР• РљРћРњРђРќР”Р«
        if (event.isWasDeath()) {
            String teamName = (oldPlayer.getTeam() != null) ? oldPlayer.getTeam().getName() : null;
            if (teamName != null) {
                Scoreboard scoreboard = newPlayer.getScoreboard();
                PlayerTeam pTeam = scoreboard.getPlayerTeam(teamName);
                if (pTeam != null) scoreboard.addPlayerToTeam(newPlayer.getScoreboardName(), pTeam);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityRemove(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity) return;
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != null && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) {
            processEntityLoss(entity);
        }
    }

    private static void processEntityLoss(Entity entity) {
        if (entity.level() == null || entity.level().getServer() == null) return;
        ServerLevel level = (ServerLevel) entity.level();
        AASWorldData data = AASWorldData.get(level);

        // === 1. РЈРґР°Р»РµРЅРёРµ РјР°СЂРєРµСЂР° С‚РµС…РЅРёРєРё РёР· СЃРїРёСЃРєР° Р¶РёРІС‹С… ===
        // РџРµСЂРµРґ СѓРґР°Р»РµРЅРёРµРј РЅР°С…РѕРґРёРј СЃР°РјСѓ Р·Р°РїРёСЃСЊ, С‡С‚РѕР±С‹ РґРѕСЃС‚Р°С‚СЊ РїРѕР·РёС†РёСЋ СЃРїР°РІРЅРµСЂР° Рё РІСЂРµРјСЏ СЂРµСЃРїР°РІРЅР° вЂ”
        // СЌС‚Рѕ РЅСѓР¶РЅРѕ РґР»СЏ РќРћР’РћР™ Р»РѕРіРёРєРё РіР»РѕР±Р°Р»СЊРЅРѕРіРѕ С‚Р°Р№РјРµСЂР° РЅРёР¶Рµ.
        AASWorldData.VehicleRecord deadRecord = data.markedVehicles.stream()
                .filter(v -> v.uuid.equals(entity.getUUID()))
                .findFirst()
                .orElse(null);

        boolean markerRemoved = data.markedVehicles.removeIf(v -> v.uuid.equals(entity.getUUID()));

        // === РќРћР’РћР•: СЃСЂР°Р·Сѓ Р¶Рµ, РЅРµР·Р°РІРёСЃРёРјРѕ РѕС‚ С‚РѕРіРѕ, РїСЂРѕРіСЂСѓР¶РµРЅ Р»Рё С‡Р°РЅРє СЃРѕ СЃРїР°РІРЅРµСЂРѕРј,
        // РІС‹СЃС‚Р°РІР»СЏРµРј РіР»РѕР±Р°Р»СЊРЅС‹Р№ РґРµРґР»Р°Р№РЅ СЃР»РµРґСѓСЋС‰РµРіРѕ СЂРµСЃРїР°РІРЅР° ===
        if (deadRecord != null && deadRecord.spawnerPos != null) {
            long delayTicks = deadRecord.respawnTimeSeconds * 20L;
            long targetTick = level.getGameTime() + delayTicks;
            data.pendingSpawnerRespawns.put(deadRecord.spawnerPos, targetTick);
        }

        // Р•СЃР»Рё Р±РёР»РµС‚РѕРІ СѓР¶Рµ РЅРµС‚ вЂ” РґРѕСЃСЂРѕС‡РЅС‹Р№ РІС‹С…РѕРґ, РєСЂРѕРјРµ СЃРѕС…СЂР°РЅРµРЅРёСЏ СЃРЅСЏС‚РѕРіРѕ РјР°СЂРєРµСЂР°
        if (data.blueTickets <= 0 || data.redTickets <= 0) {
            if (markerRemoved || deadRecord != null) {
                data.setDirty();
                sendSyncPacket(level, data);
            }
            return;
        }

        boolean ticketsChanged = false;

        // === 2. РЎРїРёСЃР°РЅРёРµ Р±РёР»РµС‚РѕРІ Р·Р° СЃРјРµСЂС‚СЊ РёРіСЂРѕРєР° ===
        if (entity instanceof ServerPlayer player) {
            if (player.getTeam() != null) {
                String teamName = player.getTeam().getName();
                int cost = data.deathTicketCost;
                if (teamName.equalsIgnoreCase("Blue")) {
                    data.blueTickets = Math.max(0, data.blueTickets - cost);
                    ticketsChanged = true;
                }
                else if (teamName.equalsIgnoreCase("Red")) {
                    data.redTickets = Math.max(0, data.redTickets - cost);
                    ticketsChanged = true;
                }
            }
        }

        // === 3. РЎРїРёСЃР°РЅРёРµ Р±РёР»РµС‚РѕРІ Р·Р° РїРѕС‚РµСЂСЋ С‚РµС…РЅРёРєРё ===
        if (entity.getPersistentData().contains("AAS_TicketPenalty")) {
            int penalty = entity.getPersistentData().getInt("AAS_TicketPenalty");
            String vTeam = entity.getPersistentData().getString("AAS_VehicleTeam");
            String vType = entity.getPersistentData().getString("AAS_VehicleType");

            if (penalty > 0) {
                if (vTeam.equalsIgnoreCase("BLUE")) {
                    data.blueTickets = Math.max(0, data.blueTickets - penalty);
                    broadcastVehicleLossMessage(level, "BLUE", "BLUE lost " + vType + " (-" + penalty + ")", ChatFormatting.BLUE);
                    ticketsChanged = true;
                } else if (vTeam.equalsIgnoreCase("RED")) {
                    data.redTickets = Math.max(0, data.redTickets - penalty);
                    broadcastVehicleLossMessage(level, "RED", "RED lost " + vType + " (-" + penalty + ")", ChatFormatting.RED);
                    ticketsChanged = true;
                }

                // РќР°С‡РёСЃР»РµРЅРёРµ РѕС‡РєРѕРІ СѓР±РёР№С†Рµ Рё Р°СЃСЃРёСЃС‚Р°Рј (РїРѕ РѕС‚СЃР»РµР¶РµРЅРЅРѕРјСѓ СѓСЂРѕРЅСѓ, СЃРј. VehicleDamageTracker)
                VehicleDamageTracker.awardForDestroyedVehicle(entity, level, vTeam, penalty);
            }
            entity.getPersistentData().remove("AAS_TicketPenalty");
        }

        // === 4. РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ ===
        // Р”РѕР±Р°РІРёР»Рё deadRecord != null РІ СѓСЃР»РѕРІРёРµ, С‡С‚РѕР±С‹ СЃРёРЅРє СѓС€С‘Р» РєР»РёРµРЅС‚Р°Рј Рё РІ СЃР»СѓС‡Р°Рµ,
        // РєРѕРіРґР° РёР·РјРµРЅРёР»СЃСЏ С‚РѕР»СЊРєРѕ pendingSpawnerRespawns (Р±РёР»РµС‚С‹ РЅРµ С‚СЂРѕРЅСѓС‚С‹, РјР°СЂРєРµСЂ СѓР¶Рµ РјРѕРі Р±С‹С‚СЊ СЃРЅСЏС‚ СЂР°РЅСЊС€Рµ).
        if (ticketsChanged || markerRemoved || deadRecord != null) {
            if (ticketsChanged) checkGameOver(level, data);
            data.setDirty();
            sendSyncPacket(level, data); // РѕС‚РїСЂР°РІР»СЏРµРј РѕР±РЅРѕРІР»С‘РЅРЅС‹Рµ РґР°РЅРЅС‹Рµ РІСЃРµРј РєР»РёРµРЅС‚Р°Рј
        }
    }

    private static void handleTeamInfluence(AASWorldData.CapturePoint point, String attackingTeam, float multiplier, float baseSpeed, AASWorldData data, ServerLevel level) {
        float speedBoosted = baseSpeed * multiplier;

        if (point.owner.equals("NEUTRAL")) {
            // --- Р›РћР“РРљРђ Р—РђРҐР’РђРўРђ РќР•Р™РўР РђР›Р¬РќРћР™ РўРћР§РљР ---
            if (point.capturingTeam.equals("NONE") || point.capturingTeam.equals(attackingTeam)) {
                point.capturingTeam = attackingTeam;
                point.progress += speedBoosted;

                if (point.progress >= 1.0f) {
                    point.progress = 1.0f;
                    point.owner = attackingTeam;

                    List<ServerPlayer> cappers = level.getEntitiesOfClass(ServerPlayer.class, point.getBoundingBox());
                    for (ServerPlayer p : cappers) {
                        if (p.isAlive() && !p.isSpectator() && p.getTeam() != null && p.getTeam().getName().equalsIgnoreCase(attackingTeam)
                                && !p.getPersistentData().getBoolean("AAS_IsDowned")) {
                            StatsHandler.addScore(p, ScoreType.POINT_CAPTURED);
                        }
                    }

                    // Р“СЂР°С„РёС‡РµСЃРєРѕРµ СѓРІРµРґРѕРјР»РµРЅРёРµ Рѕ Р·Р°С…РІР°С‚Рµ
                    PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                            new PacketCaptureNotification(point.name, attackingTeam, false));

                    // РќР°С‡РёСЃР»СЏРµРј С‚РёРєРµС‚С‹ Р°С‚Р°РєСѓСЋС‰РёРј (РµСЃР»Рё Р·Р°РґР°РЅРѕ РІ РєРѕРЅС„РёРіРµ С‚РѕС‡РєРё)
                    if (point.ticketGainCapture > 0) {
                        if (attackingTeam.equals("BLUE")) data.blueTickets += point.ticketGainCapture;
                        else data.redTickets += point.ticketGainCapture;
                    }

                    // РЁС‚СЂР°С„ РІСЂР°РіСѓ Р·Р° РїРѕС‚РµСЂСЋ С‚РѕС‡РєРё (СѓРЅРёРІРµСЂСЃР°Р»СЊРЅРѕ РґР»СЏ РІСЃРµС… СЂРµР¶РёРјРѕРІ)
                    if (point.captureDeduction > 0) {
                        if (attackingTeam.equals("BLUE")) data.redTickets -= point.captureDeduction;
                        else data.blueTickets -= point.captureDeduction;
                    }

                    checkGameOver(level, data);
                    data.setDirty();
                    sendSyncPacket(level, data);
                }
            } else {
                // РЎР±РёС‚РёРµ РїСЂРѕРіСЂРµСЃСЃР° РґСЂСѓРіРѕР№ РєРѕРјР°РЅРґС‹
                point.progress -= speedBoosted;
                if (point.progress <= 0.0f) {
                    point.progress = 0.0f;
                    point.capturingTeam = "NONE";
                }
            }
        } else if (point.owner.equals(attackingTeam)) {
            // РЎРІРѕСЏ РєРѕРјР°РЅРґР° "РґРѕР»РµС‡РёРІР°РµС‚" С‚РѕС‡РєСѓ РґРѕ 100%
            if (point.progress < 1.0f) {
                point.progress += speedBoosted;
                if (point.progress > 1.0f) point.progress = 1.0f;
            }
        } else {
            // --- Р›РћР“РРљРђ РќР•Р™РўР РђР›РР—РђР¦РР Р’Р РђР–Р•РЎРљРћР™ РўРћР§РљР ---
            point.progress -= speedBoosted;
            if (point.progress <= 0.0f) {
                String oldOwner = point.owner;

                // РЁС‚СЂР°С„ Р·Р° РїРѕС‚РµСЂСЋ РЅРµР№С‚СЂР°Р»РёС‚РµС‚Р° (СЃС‚Р°СЂС‹Р№ РІР»Р°РґРµР»РµС† С‚РµСЂСЏРµС‚ С‚РёРєРµС‚С‹)
                if (oldOwner.equals("BLUE")) data.blueTickets -= point.ticketPenalty;
                else if (oldOwner.equals("RED")) data.redTickets -= point.ticketPenalty;

                // Р‘РѕРЅСѓСЃ Р°С‚Р°РєСѓСЋС‰РёРј Р·Р° РЅРµР№С‚СЂР°Р»РёР·Р°С†РёСЋ (РµСЃР»Рё Р·Р°РґР°РЅРѕ)
                if (point.ticketGainNeutralize > 0) {
                    if (attackingTeam.equals("BLUE")) data.blueTickets += point.ticketGainNeutralize;
                    else data.redTickets += point.ticketGainNeutralize;
                }

                checkGameOver(level, data);

                point.owner = "NEUTRAL";
                point.progress = 0.0f;
                point.capturingTeam = "NONE";

                // Р“СЂР°С„РёС‡РµСЃРєРѕРµ СѓРІРµРґРѕРјР»РµРЅРёРµ Рѕ РЅРµР№С‚СЂР°Р»РёР·Р°С†РёРё
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new PacketCaptureNotification(point.name, attackingTeam, true));

                data.setDirty();
                sendSyncPacket(level, data);
            }
        }
    }
    @SubscribeEvent
    public static void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        if (event.getEntity().level().isClientSide) return;

        Player player = event.getEntity();
        if (player.isCreative()) return;

        // РќРћР’РћР•: Р±РµР· РєРёС‚Р° Rifleman СЃСѓРјРєСѓ СЃ Р·РµРјР»Рё РїРѕРґРѕР±СЂР°С‚СЊ РЅРµР»СЊР·СЏ
        if (event.getItem().getItem().getItem() == ModItems.AMMO_BAG.get()
                && !com.example.aas.block.AmmoBagBlock.canPickupByKit(player)) {
            event.setCanceled(true);
            if (player.tickCount % 20 == 0) {
                player.displayClientMessage(Component.translatable("aas.msg.rifleman_only_bag").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        // Р•СЃР»Рё СЌС‚Рѕ СЃСѓРјРєР° СЃ РїР°С‚СЂРѕРЅР°РјРё Рё РІРєР»СЋС‡РµРЅ Р»РёРјРёС‚ РІ РєРѕРЅС„РёРіРµ
        if (AASConfig.ONE_AMMO_BAG_PER_PLAYER.get()) {
            if (event.getItem().getItem().getItem() == ModItems.AMMO_BAG.get()) {
                // РџСЂРѕРІРµСЂСЏРµРј РёРЅРІРµРЅС‚Р°СЂСЊ РёРіСЂРѕРєР°
                boolean hasBag = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (player.getInventory().getItem(i).getItem() == ModItems.AMMO_BAG.get()) {
                        hasBag = true;
                        break;
                    }
                }

                // Р•СЃР»Рё СЃСѓРјРєР° СѓР¶Рµ РµСЃС‚СЊ, Р·Р°РїСЂРµС‰Р°РµРј РїРѕРґР±РёСЂР°С‚СЊ РїСЂРµРґРјРµС‚ СЃ Р·РµРјР»Рё
                if (hasBag) {
                    event.setCanceled(true);
                    // Р’С‹РґР°РµРј РїСЂРµРґСѓРїСЂРµР¶РґРµРЅРёРµ (РЅРµ СЃРїР°РјРёРј СЃР»РёС€РєРѕРј С‡Р°СЃС‚Рѕ)
                    if (player.tickCount % 20 == 0) {
                        player.displayClientMessage(Component.translatable("aas.msg.one_bag_limit").withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        }
    }
    // === РћР‘РќРћР’Р›Р•РќРќР«Р™ РњР•РўРћР”: РџСЂРёРЅРёРјР°РµС‚ СЃС‚Р°С‚СѓСЃ Р‘Р»РёС‚С†Р° ===
    private static void validateAndSync(ServerLevel level, boolean forceSend, boolean blueBleed, boolean redBleed) {
        AASWorldData data = AASWorldData.get(level);
        boolean changed = false;
        String dimKey = level.dimension().location().toString();

        // 1. Р’Р°Р»РёРґР°С†РёСЏ СЂР°Р»Р»РёРєРѕРІ (СѓРґР°Р»СЏРµРј РёР· СЃРїРёСЃРєР° РєРѕРѕСЂРґРёРЅР°С‚, РµСЃР»Рё Р±Р»РѕРє С„РёР·РёС‡РµСЃРєРё СЃР»РѕРјР°РЅ)
        changed |= validateRallies(level, data.blueRallies);
        changed |= validateRallies(level, data.redRallies);

        // 2. РџСЂРѕРІРµСЂРєР° РҐР°Р±РѕРІ (Р±Р»РѕРєРёСЂРѕРІРєР° РІСЂР°РіР°РјРё Рё РѕР±РЅРѕРІР»РµРЅРёРµ РјР°С‚РµСЂРёР°Р»РѕРІ)
        int hubRadius = AASConfig.HUB_BLOCK_RADIUS.get();
        int hubEnemiesRequired = AASConfig.HUB_BLOCK_ENEMY_COUNT.get();

        for (AASWorldData.HubInfo hub : data.hubs) {
            if (hub.constructed && hub.dimension != null && hub.dimension.equals(dimKey)) {
                if (level.isLoaded(hub.pos)) {
                    boolean nowBlocked = getEnemyCount(level, hub.pos, hub.team, hubRadius) >= hubEnemiesRequired;
                    if (hub.isBlocked != nowBlocked) {
                        hub.isBlocked = nowBlocked;
                        changed = true;
                    }

                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(hub.pos);
                    if (be instanceof com.example.aas.block.HubBlockEntity hubBe) {
                        if (hub.materials != hubBe.getMaterials()) {
                            hub.materials = hubBe.getMaterials();
                            changed = true;
                        }
                    }
                }
            }
        }

        // 3. РџСЂРѕРІРµСЂРєР° Р Р°Р»Р»РёРєРѕРІ (РРЅРґРёРІРёРґСѓР°Р»СЊРЅРѕ РґР»СЏ РєР°Р¶РґРѕРіРѕ РћРўР РЇР”Рђ)
        int rallyRadius = AASConfig.RALLY_BLOCK_RADIUS.get();
        int rallyEnemiesRequired = AASConfig.RALLY_BLOCK_ENEMY_COUNT.get();

        boolean anySquadStatusChanged = false;
        boolean teamBlueBlocked = false; // Р”Р»СЏ СЃРѕРІРјРµСЃС‚РёРјРѕСЃС‚Рё СЃ РѕР±С‰РёРј РїР°РєРµС‚РѕРј
        boolean teamRedBlocked = false;

        for (AASWorldData.Squad squad : data.squads) {
            if (squad.rallyPos != null && squad.rallyDimension.equals(dimKey)) {
                // РЎС‡РёС‚Р°РµРј РІСЂР°РіРѕРІ РІРѕРєСЂСѓРі СЂР°Р»Р»РёРєР° СЌС‚РѕРіРѕ РєРѕРЅРєСЂРµС‚РЅРѕРіРѕ РѕС‚СЂСЏРґР°
                int enemies = getEnemyCount(level, squad.rallyPos, squad.team, rallyRadius);
                boolean currentlyBlocked = (enemies >= rallyEnemiesRequired);

                // Р•СЃР»Рё СЃС‚Р°С‚СѓСЃ РёР·РјРµРЅРёР»СЃСЏ - РїРѕРјРµС‡Р°РµРј РґР»СЏ СЃРёРЅС…СЂРѕРЅРёР·Р°С†РёРё
                if (squad.isRallyBlocked != currentlyBlocked) {
                    squad.isRallyBlocked = currentlyBlocked;
                    anySquadStatusChanged = true;
                }

                // РЎРѕР±РёСЂР°РµРј РѕР±С‰СѓСЋ РёРЅС„РѕСЂРјР°С†РёСЋ РґР»СЏ РёРєРѕРЅРєРё РЅР° РєРѕРјРїР°СЃРµ/РєР°СЂС‚Рµ (РµСЃР»Рё РЅСѓР¶РЅРѕ)
                if (currentlyBlocked) {
                    if (squad.team.equalsIgnoreCase("Blue")) teamBlueBlocked = true;
                    else teamRedBlocked = true;
                }
            }
        }

        // РџСЂРѕРІРµСЂРєР° РєСЌС€Р° РґР»СЏ РѕС‚РїСЂР°РІРєРё РїР°РєРµС‚Р°
        Boolean cachedBlue = lastBlueBlockedMap.getOrDefault(dimKey, false);
        Boolean cachedRed = lastRedBlockedMap.getOrDefault(dimKey, false);

        // 4. РЎРРќРҐР РћРќРР—РђР¦РРЇ
        if (forceSend || changed || anySquadStatusChanged || teamBlueBlocked != cachedBlue || teamRedBlocked != cachedRed || blueBleed || redBleed) {

            // РћС‚РїСЂР°РІР»СЏРµРј РѕР±С‰РёРµ РґР°РЅРЅС‹Рµ (С‚РёРєРµС‚С‹, С…Р°Р±С‹ Рё С‚.Рґ.)
            sendSyncPacket(level, data, blueBleed, redBleed, teamBlueBlocked, teamRedBlocked);

            // Р•СЃР»Рё РёР·РјРµРЅРёР»СЃСЏ СЃС‚Р°С‚СѓСЃ Р±Р»РѕРєРёСЂРѕРІРєРё С…РѕС‚СЊ РѕРґРЅРѕРіРѕ РѕС‚СЂСЏРґР° - С€Р»РµРј РѕР±РЅРѕРІР»РµРЅРёРµ РѕС‚СЂСЏРґРѕРІ
            if (anySquadStatusChanged) {
                PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),
                        new PacketSyncSquads(data.squads));
            }

            lastBlueBlockedMap.put(dimKey, teamBlueBlocked);
            lastRedBlockedMap.put(dimKey, teamRedBlocked);
        }

        if (changed || anySquadStatusChanged) data.setDirty();
    }

    // === РњР•РўРћР” Р”Р›РЇ РЎРћР’РњР•РЎРўРРњРћРЎРўР ===
    // РќСѓР¶РµРЅ, С‡С‚РѕР±С‹ РЅРµ Р±С‹Р»Рѕ РѕС€РёР±РѕРє РІ onEntityDeath Рё РґСЂСѓРіРёС… РјРµСЃС‚Р°С…, РіРґРµ РјС‹ РЅРµ Р·РЅР°РµРј СЃС‚Р°С‚СѓСЃ Р±Р»РёС‚С†Р°.
    // РџРѕ СѓРјРѕР»С‡Р°РЅРёСЋ РїРµСЂРµРґР°РµРј false (РЅРµС‚ Р±Р»РёС‚С†Р°).
    private static void validateAndSync(ServerLevel level, boolean forceSend) {
        validateAndSync(level, forceSend, false, false);
    }

    private static boolean validateRallies(ServerLevel level, List<BlockPos> rallies) {
        return rallies.removeIf(pos -> {
            if (level.isLoaded(pos)) {
                return !(level.getBlockState(pos).getBlock() instanceof RallyPointBlock);
            }
            return false;
        });
    }

    private static boolean isEnemyNearby(ServerLevel level, BlockPos pos, String allyTeamName, int radius, int minCount) {
        return getEnemyCount(level, pos, allyTeamName, radius) >= minCount;
    }

    private static int getEnemyCount(ServerLevel level, BlockPos pos, String allyTeamName, int radius) {
        AABB checkArea = new AABB(pos).inflate(radius);
        List<ServerPlayer> enemies = level.getEntitiesOfClass(ServerPlayer.class, checkArea);
        int count = 0;
        for (ServerPlayer p : enemies) {
            if (p.isSpectator()) continue;
            // Р Р°РЅРµРЅС‹Р№ (downed) РёРіСЂРѕРє РЅРµ РјРѕР¶РµС‚ РІРѕРµРІР°С‚СЊ Рё РЅРµ РґРѕР»Р¶РµРЅ Р±Р»РѕРєРёСЂРѕРІР°С‚СЊ СЂР°Р»Рё/С…Р°Р±
            if (p.getPersistentData().getBoolean("AAS_IsDowned")) continue;
            if (p.getTeam() == null || !p.getTeam().getName().equalsIgnoreCase(allyTeamName)) count++;
        }
        return count;
    }

    public static int getEnemyCount(ServerLevel level, BlockPos pos, String allyTeamName) {
        AABB checkArea = new AABB(pos).inflate(40);
        List<ServerPlayer> enemies = level.getEntitiesOfClass(ServerPlayer.class, checkArea);
        int count = 0;
        for (ServerPlayer p : enemies) {
            if (p.isSpectator()) continue;
            // Р Р°РЅРµРЅС‹Р№ (downed) РёРіСЂРѕРє РЅРµ РјРѕР¶РµС‚ РІРѕРµРІР°С‚СЊ Рё РЅРµ РґРѕР»Р¶РµРЅ Р±Р»РѕРєРёСЂРѕРІР°С‚СЊ СЂР°Р»Рё/С…Р°Р±
            if (p.getPersistentData().getBoolean("AAS_IsDowned")) continue;
            if (p.getTeam() == null || !p.getTeam().getName().equalsIgnoreCase(allyTeamName)) count++;
        }
        return count;
    }

    public static void checkGameOver(ServerLevel level, AASWorldData data) {
        if (data.blueTickets <= 0) {
            data.blueTickets = 0;
            executeVictory(level, data, false); // РљСЂР°СЃРЅС‹Рµ РІС‹РёРіСЂР°Р»Рё
        } else if (data.redTickets <= 0) {
            data.redTickets = 0;
            executeVictory(level, data, true); // РЎРёРЅРёРµ РІС‹РёРіСЂР°Р»Рё
        }
    }

    private static void executeVictory(ServerLevel level, AASWorldData data, boolean blueWon) {
        data.isGameStarted = false;
        com.example.aas.uav.UavManager.onMatchEnd(level, data);
        com.example.aas.deck.DeckManager.onMatchEnd(level, data);
        com.example.aas.art.ArtManager.onMatchEnd(level, data);
        data.setDirty();

        // 1. РћРїСЂРµРґРµР»РµРЅРёРµ РїРѕР±РµРґРёС‚РµР»СЏ
        String blueDisplayName = teamDisplayName(data, true);
        String redDisplayName = teamDisplayName(data, false);

        // 2. РЎРЅРёРјРѕРє РёС‚РѕРіРѕРІ (С‚РёРєРµС‚С‹, Р»СѓС‡С€РёР№ РѕС‚СЂСЏРґ/РјРµРґРёРє/Р»РѕРіРёСЃС‚, С‚Р°Р±Р»РёС†Р° СЃС‡С‘С‚Р°) вЂ” РІСЃРµРј РєР»РёРµРЅС‚Р°Рј
        VictoryData victoryData =
                VictoryStats.build(level, data, blueWon, blueDisplayName, redDisplayName);
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketOpenVictoryScreen(victoryData));

        // 3. Р’С‹РєР»СЋС‡РµРЅРёРµ Friendly Fire РґР»СЏ РєРѕРјР°РЅРґ (РЅРёРєС‚Рѕ РЅРµ СЃРјРѕР¶РµС‚ СѓР±РёС‚СЊ СЃРІРѕРёС…)
        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam blueTeam = scoreboard.getPlayerTeam("Blue");
        PlayerTeam redTeam = scoreboard.getPlayerTeam("Red");
        if (blueTeam != null) blueTeam.setAllowFriendlyFire(false);
        if (redTeam != null) redTeam.setAllowFriendlyFire(false);

        // 4. РћС‡РёСЃС‚РєР° РёРЅРІРµРЅС‚Р°СЂРµР№, РІРѕСЃРєСЂРµС€РµРЅРёРµ РЅРѕРєРЅСѓС‚С‹С… Рё С‚РµР»РµРїРѕСЂС‚ РІСЃРµС… РЅР° РњРµР№РЅ Р‘Р°Р·Сѓ
        String currentDim = level.dimension().location().toString();

        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;

            // Р•СЃР»Рё РёРіСЂРѕРє Р±С‹Р» РІ РЅРѕРєРµ - РїРѕРґРЅРёРјР°РµРј РµРіРѕ
            if (player.getPersistentData().getBoolean("AAS_IsDowned")) {
                com.example.aas.events.DownedHandler.revivePlayer(player);
            }

            // Р—Р°Р±РёСЂР°РµРј РљРёС‚ (РёРЅРІРµРЅС‚Р°СЂСЊ)
            player.setHealth(player.getMaxHealth());
            player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncMyKit("Unassigned"));
            player.getPersistentData().remove("AAS_PendingKit");
            player.getInventory().clearContent();
            ResupplyHandler.clearCurios(player);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();

            // РўРµР»РµРїРѕСЂС‚Р°С†РёСЏ РЅР° РњРµР№РЅ
            String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
            BlockPos spawnPos = null;

            if (pTeam.equals("BLUE")) spawnPos = data.blueSpawns.get(currentDim);
            else if (pTeam.equals("RED")) spawnPos = data.redSpawns.get(currentDim);

            if (spawnPos != null) {
                // Р•СЃР»Рё РёРіСЂРѕРє Р±С‹Р» РјРµСЂС‚РІ (РЅР° СЌРєСЂР°РЅРµ СЃРјРµСЂС‚Рё), СѓСЃС‚Р°РЅР°РІР»РёРІР°РµРј РµРјСѓ С‚РѕС‡РєСѓ РІРѕР·СЂРѕР¶РґРµРЅРёСЏ СЃСЋРґР°
                player.setRespawnPosition(level.dimension(), spawnPos, 0.0f, true, false);

                // Р•СЃР»Рё Р¶РёРІ - РїСЂРѕСЃС‚Рѕ С‚РµР»РµРїРѕСЂС‚РёСЂСѓРµРј
                if (player.isAlive()) {
                    player.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                }
            }
        }
    }

    /** РћС‚РѕР±СЂР°Р¶Р°РµРјРѕРµ РёРјСЏ РєРѕРјР°РЅРґС‹ (С‚Рѕ Р¶Рµ РїСЂР°РІРёР»Рѕ, С‡С‚Рѕ СЂР°РЅСЊС€Рµ РёСЃРїРѕР»СЊР·РѕРІР°Р»РѕСЃСЊ РґР»СЏ РїРѕР±РµРґРёС‚РµР»СЏ). */
    private static String teamDisplayName(AASWorldData data, boolean blue) {
        String faction = blue ? data.blueFaction : data.redFaction;
        String defaultFaction = blue ? "bluefor" : "redfor";
        String name = (faction == null || faction.equals("none") || faction.equals(defaultFaction))
                ? (blue ? AASConfig.BLUE_TEAM_CUSTOM_NAME.get() : AASConfig.RED_TEAM_CUSTOM_NAME.get())
                : formatFactionName(faction);
        if (name == null || name.isEmpty()) name = blue ? "BLUE TEAM" : "RED TEAM";
        return name;
    }

    private static String formatFactionName(String faction) {
        return faction.replace("_", " ").toUpperCase();
    }

    private static void sendTitleToLevel(ServerLevel level, String text, ChatFormatting color) {
        Component title = Component.literal(text).withStyle(color).withStyle(ChatFormatting.BOLD);
        for (ServerPlayer player : level.players()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static void sendTitleToAll(MinecraftServer server, String text, ChatFormatting color) {
        Component title = Component.literal(text).withStyle(color).withStyle(ChatFormatting.BOLD);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static void broadcastMessage(ServerLevel level, String text, ChatFormatting color) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(color), false);
    }

    // Used for vehicle-loss messages: respects AASConfig.SEPARATE_TEAM_DESTRUCTION_MESSAGES,
    // keeping "X lost vehicle" messages within team X (plus players without a Blue/Red team) when enabled.
    private static void broadcastVehicleLossMessage(ServerLevel level, String ownerTeam, String text, ChatFormatting color) {
        TeamMessageUtil.broadcastDestructionMessage(level, ownerTeam, Component.literal(text).withStyle(color));
    }

    private static void sendSyncPacket(ServerLevel level, AASWorldData data) {
        sendSyncPacket(level, data, false, false);
    }

    private static void sendSyncPacket(ServerLevel level, AASWorldData data, boolean blueBleed, boolean redBleed) {
        String dimKey = level.dimension().location().toString();
        boolean bBlocked = lastBlueBlockedMap.getOrDefault(dimKey, false);
        boolean rBlocked = lastRedBlockedMap.getOrDefault(dimKey, false);
        sendSyncPacket(level, data, blueBleed, redBleed, bBlocked, rBlocked);
    }

    private static void sendSyncPacket(ServerLevel level, AASWorldData data, boolean blueBleed, boolean redBleed, boolean bBlocked, boolean rBlocked) {
        PacketSyncGameData packet = createSyncPacket(data, blueBleed, redBleed, bBlocked, rBlocked);
        PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    private static PacketSyncGameData createSyncPacket(AASWorldData data, boolean blueBleed, boolean redBleed, boolean bBlocked, boolean rBlocked) {
        boolean hasBlue = !data.blueRallies.isEmpty();
        boolean hasRed = !data.redRallies.isEmpty();

        String bName = data.blueFaction.equals("none") ? AASConfig.BLUE_TEAM_CUSTOM_NAME.get() : data.blueFaction.toUpperCase();
        String rName = data.redFaction.equals("none") ? AASConfig.RED_TEAM_CUSTOM_NAME.get() : data.redFaction.toUpperCase();

        Map<String, String> pKits = new HashMap<>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String current = p.getPersistentData().getString("AAS_CurrentKit");
                String pending = p.getPersistentData().getString("AAS_PendingKit");
                String displayKit = !pending.isEmpty() ? pending : current;
                pKits.put(p.getScoreboardName(), displayKit.isEmpty() ? "Unassigned" : displayKit);
            }
        }

        // РРЎРџРћР›Р¬Р—РЈР•Рњ BUILDER Р’РњР•РЎРўРћ NEW
        return PacketSyncGameData.builder()
                .tickets(data.blueTickets, data.redTickets)
                .rally(hasBlue, hasRed)
                .bleeding(blueBleed, redBleed)
                .respawnTime(data.respawnTimer)
                .blocked(bBlocked, rBlocked)
                .hubSpawnCost(AASConfig.HUB_SPAWN_COSTS_MATERIALS.get(), AASConfig.HUB_SPAWN_MATERIAL_COST.get())
                .map(data.mapCenterX, data.mapCenterZ, data.mapSizeBlocks, data.currentMapImage)
                .vehicles(new ArrayList<>(data.markedVehicles))
                .hubs(new ArrayList<>(data.hubs))
                .factions(data.blueFaction, data.redFaction)
                .customNames(bName, rName)
                .gameStarted(data.isGameStarted)
                .capturePoints(new ArrayList<>(data.capturePoints))
                .spawns(new HashMap<>(data.blueSpawns), new HashMap<>(data.redSpawns), new HashMap<>(data.neutralSpawns))
                .playerKits(pKits)
                .markers(new ArrayList<>(data.activeMarkers))
                .startVote(data.voteActive, data.voteTimer, new HashMap<>(data.votes))
                .commanderIds(data.blueCMDId, data.redCMDId)
                .blueCommanderVote(data.blueCmdVoteActive, data.blueCmdCandidateName, data.blueCmdCandidateId, data.blueCmdVoteTimer, new HashMap<>(data.blueCmdVotes))
                .redCommanderVote(data.redCmdVoteActive, data.redCmdCandidateName, data.redCmdCandidateId, data.redCmdVoteTimer, new HashMap<>(data.redCmdVotes))
                .ready(data.blueReady, data.redReady)
                .gameMode(data.gameMode)
                .invasion(data.invasionDefender, data.invasionPrepTicks)
                .decks(com.example.aas.deck.DeckSyncPayload.from(data))
                .build();
    }

    private static boolean canCapture(AASWorldData.CapturePoint target, String team, AASWorldData data) {
        int currentPriority = team.equals("BLUE") ? target.bluePriority : target.redPriority;
        if (currentPriority <= 1) return true;
        int requiredPriority = currentPriority - 1;
        for (AASWorldData.CapturePoint p : data.capturePoints) {
            int pPriority = team.equals("BLUE") ? p.bluePriority : p.redPriority;
            if (pPriority == requiredPriority && p.owner.equals(team)) return true;
        }
        return false;
    }
    // === Р’РЎРўРђР’РРўР¬ Р’ РљР›РђРЎРЎ GameLogicEvents ===

    public static void leaveCurrentSquad(ServerPlayer player, AASWorldData data) {
        String pName = player.getScoreboardName();

        // 1. РЈРґР°Р»СЏРµРј РёРіСЂРѕРєР° РёР· РІСЃРµС… РѕС‚СЂСЏРґРѕРІ, РіРґРµ РѕРЅ РјРѕР¶РµС‚ С‡РёСЃР»РёС‚СЊСЃСЏ
        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(pName)) {
                s.members.remove(pName);
                if (s.leader.equals(pName)) {
                    // Р•СЃР»Рё Р±С‹Р» Р»РёРґРµСЂРѕРј вЂ” СѓРґР°Р»СЏРµРј СЂР°С†РёСЋ
                    PacketSquadAction.removeRadio(player);
                    if (!s.members.isEmpty()) {
                        s.removeFromFireteams(s.members.get(0));
                        s.leader = s.members.get(0);
                        ServerPlayer newLeader = player.server.getPlayerList().getPlayerByName(s.leader);
                        if (newLeader != null) {
                            PacketSquadAction.updatePlayerTags(newLeader, s.id, true);
                            if (AASConfig.AUTO_GIVE_SL_RADIO.get()) PacketSquadAction.giveRadio(newLeader);
                        }
                    }
                }
            }
        }
        data.squads.removeIf(s -> s.members.isEmpty());
        data.setDirty();
        PacketHandler.sendToAllClients(player.serverLevel(), data);
        // 2. РџРћР›РќРђРЇ РћР§РРЎРўРљРђ РљРРўРђ (РўРµРіРё Рё РРЅРІРµРЅС‚Р°СЂСЊ)
        // РЎР±СЂР°СЃС‹РІР°РµРј С‚РµРіРё РІ СЃРѕСЃС‚РѕСЏРЅРёРµ "РїРѕ СѓРјРѕР»С‡Р°РЅРёСЋ"
        player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncMyKit("Unassigned"));
        player.getPersistentData().putString("AAS_PendingKit", ""); // РћС‡РёС‰Р°РµРј Р±СЂРѕРЅСЊ

        // РћС‡РёС‰Р°РµРј РёРЅРІРµРЅС‚Р°СЂСЊ РёРіСЂРѕРєР° РїРѕР»РЅРѕСЃС‚СЊСЋ
        player.getInventory().clearContent();
        ResupplyHandler.clearCurios(player);

        // РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј РёРЅРІРµРЅС‚Р°СЂСЊ (С‡С‚РѕР±С‹ РІРµС‰Рё РёСЃС‡РµР·Р»Рё Сѓ РёРіСЂРѕРєР°)
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();

        // РЈР±РёСЂР°РµРј С‚РµРіРё Р»РёРґРµСЂР°/РѕС‚СЂСЏРґР°
        PacketSquadAction.removePlayerTags(player);

        // 3. РЈРІРµРґРѕРјР»РµРЅРёРµ
        player.displayClientMessage(Component.literal("В§eSquad left. Kit and reservations cleared."), true);

        // 4. РЎРРќРҐР РћРќРР—РђР¦РРЇ Р”РђРќРќР«РҐ РњРР Рђ
        // РњС‹ РїРѕРјРµС‡Р°РµРј РґР°РЅРЅС‹Рµ РєР°Рє "РіСЂСЏР·РЅС‹Рµ" Рё РџР РРќРЈР”РРўР•Р›Р¬РќРћ С€Р»РµРј РїР°РєРµС‚ РІСЃРµРј
        data.setDirty();
        PacketHandler.sendToAllClients(player.serverLevel(), data);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);

            // === РЎРћРҐР РђРќР•РќРР• РџР РћР“Р Р•РЎРЎРђ РџР Р Р’Р«Р›Р•РўР• Р’ Р‘РћР® ===
            if (data.isGameStarted) {
                return; // РќРёС‡РµРіРѕ РЅРµ РѕС‡РёС‰Р°РµРј, РµСЃР»Рё РёРіСЂР° РёРґРµС‚!
            }

            // === РћР§РРЎРўРљРђ Р”Рћ РЎРўРђР РўРђ РР“Р Р« ===
            // 1. РЈРґР°Р»СЏРµРј РёР· РѕС‚СЂСЏРґР° Рё С‡РёСЃС‚РёРј С‚РµРіРё SL (РїРµСЂРµРґР°РµРј Р»РёРґРµСЂР° РґСЂСѓРіРѕРјСѓ, РµСЃР»Рё РЅСѓР¶РЅРѕ)
            PacketSquadAction.leaveCurrentSquad(player, data);

            // 2. РџРћР›РќР«Р™ РЎР‘Р РћРЎ РљРРўРђ РџР Р Р’Р«РҐРћР”Р•
            player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncMyKit("Unassigned"));
            player.getPersistentData().remove("AAS_PendingKit");

            // 3. РћС‡РёСЃС‚РєР° РёРЅРІРµРЅС‚Р°СЂСЏ, С‡С‚РѕР±С‹ РЅРµ РґСЋРїР°Р»РёСЃСЊ РІРµС‰Рё
            player.getInventory().clearContent();
            ResupplyHandler.clearCurios(player);
            player.inventoryMenu.broadcastChanges();

            data.setDirty();

            // РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј СЃРїРёСЃРѕРє РѕС‚СЂСЏРґРѕРІ РґР»СЏ РѕСЃС‚Р°Р»СЊРЅС‹С… РёРіСЂРѕРєРѕРІ
            PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), new PacketSyncSquads(data.squads));
        }
    }
    private static void playSirenForTeam(ServerLevel level, String teamName) {
        for (ServerPlayer player : level.players()) {
            if (player.getTeam() != null && player.getTeam().getName().equalsIgnoreCase(teamName)) {
                // Р¤РРљРЎ: РћС‚РїСЂР°РІР»СЏРµРј Р·РІСѓРє РЅР°РїСЂСЏРјСѓСЋ РєР»РёРµРЅС‚Сѓ РёРіСЂРѕРєР°
                player.playNotifySound(ModSounds.SIREN_ALARM.get(), net.minecraft.sounds.SoundSource.MASTER, 1.0F, 1.0F);
            }
        }
    }
    // 2. Р”РѕР±Р°РІР»СЏРµРј РјРµС‚РѕРґ РґР»СЏ РјРіРЅРѕРІРµРЅРЅРѕР№ РїСЂРѕРІРµСЂРєРё СЃРёСЂРµРЅС‹ (РІС‹Р·С‹РІР°С‚СЊ РїСЂРё РёР·РјРµРЅРµРЅРёРё С‚РёРєРµС‚РѕРІ)
    public static void checkSirenManual(ServerLevel level, AASWorldData data) {
        if (!data.isGameStarted || !AASConfig.LOW_TICKETS_SIREN.get()) return;

        if (data.blueTickets <= 50 && data.blueTickets >= 0 && !data.playedBlueSiren) {
            playSirenForTeam(level, "Blue");
            data.playedBlueSiren = true;
            data.setDirty();
        }
        if (data.redTickets <= 50 && data.redTickets >= 0 && !data.playedRedSiren) {
            playSirenForTeam(level, "Red");
            data.playedRedSiren = true;
            data.setDirty();
        }
    }
    @SubscribeEvent
    public static void enforceMortarShellLimit(TickEvent.PlayerTickEvent event) {
        // Р’С‹РїРѕР»РЅСЏРµРј С‚РѕР»СЊРєРѕ РЅР° СЃРµСЂРІРµСЂРµ Рё РІ РєРѕРЅС†Рµ С‚РёРєР°
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        // РџСЂРѕРІРµСЂСЏРµРј СЂР°Р· РІ 10 С‚РёРєРѕРІ (РїРѕР»СЃРµРєСѓРЅРґС‹) РґР»СЏ РѕРїС‚РёРјРёР·Р°С†РёРё
        if (event.player.tickCount % 10 != 0) return;

        Player player = event.player;

        // Р’ РєСЂРµР°С‚РёРІРµ Рё СЂРµР¶РёРјРµ РЅР°Р±Р»СЋРґР°С‚РµР»СЏ РѕРіСЂР°РЅРёС‡РµРЅРёР№ РЅРµС‚
        if (player.isCreative() || player.isSpectator()) return;

        // РС‰РµРј РјРѕСЂС‚РёСЂРЅС‹Р№ СЃРЅР°СЂСЏРґ. Р•СЃР»Рё РјРѕРґР° РЅРµС‚ вЂ” РёСЃРїРѕР»СЊР·СѓРµРј СЃС‚СЂРµР»С‹ (РѕС‚Р»Р°РґРєР°)
        Item mortarItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "mortar_shell"));
        if (mortarItem == null || mortarItem == net.minecraft.world.item.Items.AIR) {
            mortarItem = net.minecraft.world.item.Items.ARROW;
        }

        // РЎС‡РёС‚Р°РµРј РѕР±С‰РµРµ РєРѕР»РёС‡РµСЃС‚РІРѕ СЃРЅР°СЂСЏРґРѕРІ РІ РёРЅРІРµРЅС‚Р°СЂРµ
        int totalCount = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() == mortarItem) {
                totalCount += stack.getCount();
            }
        }

        // Р•СЃР»Рё Р±РѕР»СЊС€Рµ 8 вЂ” Р·Р°Р±РёСЂР°РµРј Рё РІС‹Р±СЂР°СЃС‹РІР°РµРј РёР·Р»РёС€РµРє
        if (totalCount > 8) {
            int toRemove = totalCount - 8;
            int removedSoFar = 0;

            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (removedSoFar >= toRemove) break;

                ItemStack stack = inv.getItem(i);
                if (stack.getItem() == mortarItem) {
                    int shrinkAmount = Math.min(stack.getCount(), toRemove - removedSoFar);
                    stack.shrink(shrinkAmount);
                    removedSoFar += shrinkAmount;

                    // Р’С‹Р±СЂР°СЃС‹РІР°РµРј Р»РёС€РЅРµРµ РЅР° Р·РµРјР»СЋ РїРµСЂРµРґ РёРіСЂРѕРєРѕРј
                    ItemStack dropped = new ItemStack(mortarItem, shrinkAmount);
                    player.drop(dropped, false, true);
                }
            }

            // РЈРІРµРґРѕРјР»СЏРµРј РёРіСЂРѕРєР°
            player.displayClientMessage(Component.literal("You can only carry up to 8 Mortar Shells!").withStyle(ChatFormatting.RED), true);
        }
    }
    private static void checkCmdVoteStatus(ServerLevel level, MinecraftServer server, AASWorldData data, String team) {
        boolean isBlue = team.equals("BLUE");

        // Р’С‹Р±РёСЂР°РµРј РЅСѓР¶РЅС‹Рµ РїРѕР»СЏ РІ Р·Р°РІРёСЃРёРјРѕСЃС‚Рё РѕС‚ РєРѕРјР°РЅРґС‹
        boolean active = isBlue ? data.blueCmdVoteActive : data.redCmdVoteActive;
        String candidateName = isBlue ? data.blueCmdCandidateName : data.redCmdCandidateName;
        int candidateId = isBlue ? data.blueCmdCandidateId : data.redCmdCandidateId;
        int timer = isBlue ? data.blueCmdVoteTimer : data.redCmdVoteTimer;
        Map<UUID, Boolean> votesMap = isBlue ? data.blueCmdVotes : data.redCmdVotes;

        if (!active) return;

        // 1. РЎС‡РёС‚Р°РµРј Р¶РёРІС‹С… SL СЌС‚РѕР№ РєРѕРјР°РЅРґС‹ (РєСЂРѕРјРµ РєР°РЅРґРёРґР°С‚Р°)
        List<ServerPlayer> otherSLs = level.players().stream()
                .filter(p -> p.getTeam() != null && p.getTeam().getName().equalsIgnoreCase(team))
                .filter(p -> p.getPersistentData().getBoolean("AAS_IsSquadLeader"))
                .filter(p -> !p.getScoreboardName().equals(candidateName))
                .toList();

        int totalVoters = otherSLs.size();
        int needed = (totalVoters > 0) ? (int) Math.ceil(totalVoters / 2.0) : 1;

        // 2. РЎС‡РёС‚Р°РµРј РіРѕР»РѕСЃР°
        long yes = votesMap.entrySet().stream()
                .filter(e -> server.getPlayerList().getPlayer(e.getKey()) != null)
                .filter(Map.Entry::getValue).count();

        long no = votesMap.entrySet().stream()
                .filter(e -> server.getPlayerList().getPlayer(e.getKey()) != null)
                .filter(e -> !e.getValue()).count();

        boolean win = (totalVoters > 0 && yes >= needed);
        boolean fail = (totalVoters > 0 && no >= totalVoters) || (timer <= 0);

        if (win || fail) {
            if (win) {
                if (isBlue) data.blueCMDId = candidateId;
                else data.redCMDId = candidateId;
                broadcastTeamMessage(level, team, team + " COMMANDER ASSIGNED: " + candidateName, ChatFormatting.GREEN);
            } else {
                broadcastTeamMessage(level, team, team + " CMD Application rejected or timed out.", ChatFormatting.RED);
            }

            // РћС‡РёСЃС‚РєР°
            if (isBlue) {
                data.blueCmdVoteActive = false;
                data.blueCmdVotes.clear();
            } else {
                data.redCmdVoteActive = false;
                data.redCmdVotes.clear();
            }
            data.setDirty();
            PacketHandler.sendToAllClients(level, data);
        }
    }
    private static void broadcastTeamMessage(ServerLevel level, String team, String text, ChatFormatting color) {
        Component message = Component.literal(text).withStyle(color);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.getTeam() != null && player.getTeam().getName().equalsIgnoreCase(team)) {
                player.sendSystemMessage(message);
            }
        }
    }
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        // Р’С‹РїРѕР»РЅСЏРµРј Р»РѕРіРёРєСѓ С‚РѕР»СЊРєРѕ РЅР° СЃРµСЂРІРµСЂРµ
        if (event.getEntity().level().isClientSide) return;

        Entity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();

        // РџСЂРѕРІРµСЂСЏРµРј, С‡С‚Рѕ РѕР±Р° СѓС‡Р°СЃС‚РЅРёРєР° - РёРіСЂРѕРєРё, Рё СЌС‚Рѕ РЅРµ СѓСЂРѕРЅ СЃР°РјРѕРјСѓ СЃРµР±Рµ (РѕС‚ РїР°РґРµРЅРёСЏ, РѕРіРЅСЏ Рё С‚Рґ)
        if (target instanceof Player && attacker instanceof Player && target != attacker) {
            ServerLevel serverLevel = (ServerLevel) target.level();
            AASWorldData data = AASWorldData.get(serverLevel);

            // Р•СЃР»Рё PvP РѕС‚РєР»СЋС‡РµРЅРѕ РІ СЌС‚РѕРј РјРёСЂРµ - РѕС‚РјРµРЅСЏРµРј СѓСЂРѕРЅ
            if (!data.pvpEnabled) {
                event.setCanceled(true);
            }
        }
    }

}

