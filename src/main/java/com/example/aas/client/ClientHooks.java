package com.example.aas.client;

import com.example.aas.block.HubBlockEntity;
import com.example.aas.block.ModBlocks;
import com.example.aas.block.VehicleStationBlockEntity;
import com.example.aas.client.gui.HubRadialScreen;
import com.example.aas.client.gui.RadioRadialScreen;
import com.example.aas.client.sound.HubLoopingSound;
import com.example.aas.client.sound.StationLoopingSound;
import com.example.aas.network.PacketSpawnGhost;
import com.example.aas.network.PacketSyncGameData;
import com.example.aas.network.PacketSyncPoint;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.HashMap;
import com.example.aas.block.RallyPointBlockEntity; // Р”РћР‘РђР’Р›Р•РќРћ
import com.example.aas.client.sound.RallyLoopingSound;
import com.example.aas.client.gui.KitTeamSelectScreen;
import com.example.aas.client.gui.PlayerKitSelectScreen;
import com.example.aas.network.PacketOpenPlayerKitMenu;
import java.util.List;

public class ClientHooks {
    public static void handleRecoil(float pitch, float yaw) {
        com.example.aas.client.RecoilHandler.addRecoil(pitch);
        if (yaw != 0 && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.turn(yaw, 0);
        }
    }
    // === Р”Р›РЇ Р РђР›Р›РРљРђ ===
    public static Object playRallySound(RallyPointBlockEntity entity, Object currentSound) {
        Minecraft mc = Minecraft.getInstance();
        if (currentSound != null && mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance) currentSound)) {
            return currentSound;
        }
        RallyLoopingSound newSound = new RallyLoopingSound(entity);
        mc.getSoundManager().play(newSound);
        return newSound;
    }
    public static void openPlayerKitMenu(List<PacketOpenPlayerKitMenu.KitDTO> kits) {
        if (Minecraft.getInstance().screen instanceof PlayerKitSelectScreen screen) {
            screen.updateKits(kits); // РћР±РЅРѕРІР»СЏРµРј РІ СЂРµР°Р»СЊРЅРѕРј РІСЂРµРјРµРЅРё
        } else {
            Minecraft.getInstance().setScreen(new PlayerKitSelectScreen(kits));
        }
    }
    public static void stopRallySound(Object sound) {
        if (sound instanceof RallyLoopingSound s) {
            s.stopSound();
        }
    }

    // === Р”Р›РЇ РҐРђР‘Рђ ===
    public static Object playHubSound(HubBlockEntity entity, Object currentSound) {
        Minecraft mc = Minecraft.getInstance();
        // РџСЂРѕРІРµСЂСЏРµРј, РёРіСЂР°РµС‚ Р»Рё Р·РІСѓРє РЅР° СЃР°РјРѕРј РґРµР»Рµ РІ РґРІРёР¶РєРµ РёРіСЂС‹
        if (currentSound != null && mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance) currentSound)) {
            return currentSound;
        }
        HubLoopingSound newSound = new HubLoopingSound(entity);
        mc.getSoundManager().play(newSound);
        return newSound;
    }

    private static Object playHubSoundInternal(HubBlockEntity entity) {
        HubLoopingSound sound = new HubLoopingSound(entity);
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }

    public static void stopHubSound(Object sound) {
        if (sound instanceof HubLoopingSound s) {
            s.stopSound();
        }
    }
    public static void openKitTeamSelect() {
        Minecraft.getInstance().setScreen(new KitTeamSelectScreen());
    }
    // ... (СЃС‚Р°СЂС‹Рµ РјРµС‚РѕРґС‹ openRadioMenu, openHubMenu Рё Р·РІСѓРєРё Р±РµР· РёР·РјРµРЅРµРЅРёР№) ...
    public static void openRadioMenu() {
        Minecraft.getInstance().setScreen(new RadioRadialScreen());
    }

    public static void openHubMenu(BlockPos pos) {
        Minecraft.getInstance().setScreen(new HubRadialScreen(pos));
    }
    // Р’РЅСѓС‚СЂРё РєР»Р°СЃСЃР° ClientHooks:
    public static void openCrateMenu(int entityId) {
        Minecraft.getInstance().setScreen(new com.example.aas.client.gui.CrateRadialScreen(entityId));
    }
    // Р’ С„Р°Р№Р»Рµ src/main/java/com/example/aas/client/ClientHooks.java

    public static void tryOpenRadioMenu(net.minecraft.world.entity.player.Player player) {
        if (player.isCreative()) {
            openRadioMenu();
            return;
        }

        if (player.getTeam() == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("You must join a TEAM (Blue/Red) first!").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        String playerName = player.getScoreboardName();
        boolean isInSquad = false;
        boolean isLeader = false;

        // Р—РґРµСЃСЊ Р±РµР·РѕРїР°СЃРЅРѕ РёСЃРїРѕР»СЊР·СѓРµРј ClientData, С‚Р°Рє РєР°Рє РјС‹ РІ РєР»РёРµРЅС‚СЃРєРѕРј РєР»Р°СЃСЃРµ
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(playerName)) {
                isInSquad = true;
                if (s.leader.equals(playerName)) {
                    isLeader = true;
                }
                break;
            }
        }

        if (!isInSquad) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("You must join a SQUAD first! Press 'K'.").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        if (!isLeader) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("You must be a Squad Leader to use this!").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        // Р•СЃР»Рё РІСЃС‘ РѕРє - РѕС‚РєСЂС‹РІР°РµРј РјРµРЅСЋ
        openRadioMenu();
    }

    public static void handleSpawnGhost(PacketSpawnGhost msg) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            Display.BlockDisplay ghost = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            if (msg.blockId == 14) {
                CompoundTag tag = new CompoundTag();
                tag.put("block_state", NbtUtils.writeBlockState(ModBlocks.HUB_BLOCK.get().defaultBlockState()));
                ghost.load(tag);
            }
            ghost.setPos(msg.pos.getX(), msg.pos.getY(), msg.pos.getZ());
            level.addFreshEntity(ghost);
        }
    }
    public static void handleDownedState(int entityId, boolean isDowned, boolean died) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (isDowned) {
            ClientData.DOWNED_PLAYERS.add(entityId);
            if (mc.player != null && mc.player.getId() == entityId) {
                mc.player.getPersistentData().putBoolean("AAS_IsDowned", true);

                // Р¤РРљРЎ: РЈСЃС‚Р°РЅР°РІР»РёРІР°РµРј РІСЂРµРјСЏ С‚РѕР»СЊРєРѕ РµСЃР»Рё РѕРЅРѕ РµС‰Рµ РЅРµ СѓСЃС‚Р°РЅРѕРІР»РµРЅРѕ
                if (ClientData.globalDeathTimestamp == 0) {
                    ClientData.globalDeathTimestamp = System.currentTimeMillis();
                }
                mc.setScreen(new com.example.aas.client.gui.DownedScreen());
            }
        } else {

            ClientData.DOWNED_PLAYERS.remove(entityId);

            if (mc.player != null && mc.player.getId() == entityId) {
                mc.player.getPersistentData().putBoolean("AAS_IsDowned", false);

                if (mc.screen instanceof com.example.aas.client.gui.DownedScreen) {
                    mc.setScreen(null);
                }

                // РЎР±СЂР°СЃС‹РІР°РµРј С‚Р°Р№РјРµСЂ РўРћР›Р¬РљРћ РїСЂРё РЅР°СЃС‚РѕСЏС‰РµРј РїРѕРґСЉС‘РјРµ РјРµРґРёРєРѕРј.
                // Р•СЃР»Рё СЌС‚Рѕ Give Up/Р°РІС‚Рѕ-Р±РёРґР°СѓС‚ (died = true) вЂ” РЅРµ С‚СЂРѕРіР°РµРј,
                // РІСЂРµРјСЏ РІ РЅРѕРєРµ РґРѕР»Р¶РЅРѕ РґРѕРЅРµСЃС‚РёСЃСЊ РґРѕ СЌРєСЂР°РЅР° СЃРјРµСЂС‚Рё.
                if (!died) { // Р•СЃР»Рё СЂРµР°Р»СЊРЅРѕ РїРѕРґРЅСЏР»Рё, Р° РЅРµ СѓР±РёР»Рё РѕРєРѕРЅС‡Р°С‚РµР»СЊРЅРѕ
                    ClientData.globalDeathTimestamp = 0;
                }
            }
        }
    }

    public static void handleDragState(boolean isDragging) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.getPersistentData().putBoolean("AAS_IsDraggingAlly", isDragging);
    }

    public static void handleSyncGameData(PacketSyncGameData msg) {
        java.util.Map<String, String> oldKits = new HashMap<>(ClientData.playerKits);
        ClientData.BLUE_TICKETS = msg.blueTickets;
        ClientData.RED_TICKETS = msg.redTickets;
        ClientData.hasBlueRally = msg.hasBlueRally;
        ClientData.hasRedRally = msg.hasRedRally;
        ClientData.blueBleeding = msg.blueBleeding;
        ClientData.redBleeding = msg.redBleeding;
        ClientData.RESPAWN_TIME = msg.respawnTime;
        ClientData.blueRallyBlocked = msg.blueBlocked;
        ClientData.redRallyBlocked = msg.redBlocked;
        ClientData.clientHubs = new ArrayList<>(msg.hubs);
        ClientData.clientStations = new ArrayList<>(msg.stations);
        ClientData.BLUE_FACTION = msg.blueFaction;
        ClientData.RED_FACTION = msg.redFaction;
        ClientData.gameMode = msg.gameMode;
        ClientData.invasionDefender = msg.invasionDefender;
        ClientData.invasionPrepTicks = msg.invasionPrepTicks;
        ClientData.isGameStarted = msg.isGameStarted;
        if (msg.isGameStarted) ClientData.lastVictory = null; // РЅРѕРІС‹Р№ РјР°С‚С‡ вЂ” СЃС‚Р°СЂС‹Р№ СЌРєСЂР°РЅ РїРѕР±РµРґС‹ Р±РѕР»СЊС€Рµ РЅРµ РЅСѓР¶РµРЅ
        ClientData.allCapturePoints = new ArrayList<>(msg.capturePoints);
        ClientData.customBlueName = msg.blueCustomName;
        ClientData.customRedName = msg.redCustomName;
        ClientData.activeMarkers = new ArrayList<>(msg.activeMarkers);
        ClientData.serverHubSpawnCosts = msg.hubSpawnCosts;
        ClientData.serverHubSpawnCostAmount = msg.hubSpawnCost;
        ClientData.mapCenterX = msg.mapCenterX;
        ClientData.mapCenterZ = msg.mapCenterZ;
        ClientData.mapSizeBlocks = msg.mapSizeBlocks;
        ClientData.currentMapImage = msg.currentMapImage;


        // РЎС‚Р°С‚СѓСЃ РіРѕС‚РѕРІРЅРѕСЃС‚Рё (РіРѕР»РѕСЃРѕРІР°РЅРёРµ СЃС‚Р°СЂС‚Р°)
        ClientData.blueReady = msg.blueReady;
        ClientData.redReady = msg.redReady;
        com.example.aas.client.DeckClient.onSync(msg.decks);

        ClientData.clientVehicles = new ArrayList<>(msg.markedVehicles);
        ClientData.blueSpawns = new HashMap<>(msg.blueSpawns);
        ClientData.redSpawns = new HashMap<>(msg.redSpawns);
        ClientData.neutralSpawns = new HashMap<>(msg.neutralSpawns);
        ClientData.playerKits = new HashMap<>(msg.playerKits);

        ClientData.voteActive = msg.voteActive;
        ClientData.voteTimer = msg.voteTimer;
        ClientData.votes = new HashMap<>(msg.votes);

        // --- РЎРРќРҐР РћРќРР—РђР¦РРЇ РљРћРњРђРќР”РР РћР’ (Р РђР—Р”Р•Р›Р¬РќРђРЇ) ---
        ClientData.blueCMDId = msg.blueCMDId;
        ClientData.redCMDId = msg.redCMDId;

        // РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ Blue
        ClientData.blueCmdVoteActive = msg.blueCmdVoteActive;
        ClientData.blueCmdCandidateName = msg.blueCmdCandidateName;
        ClientData.blueCmdVotes = new HashMap<>(msg.blueCmdVotes);

        // РЎРёРЅС…СЂРѕРЅРёР·Р°С†РёСЏ Red
        ClientData.redCmdVoteActive = msg.redCmdVoteActive;
        ClientData.redCmdCandidateName = msg.redCmdCandidateName;
        ClientData.redCmdVotes = new HashMap<>(msg.redCmdVotes);

        // РђСЂС‚РёР»Р»РµСЂРёСЏ Рё РїСЂРѕС‡РµРµ
        if (!oldKits.equals(ClientData.playerKits)) {
            if (Minecraft.getInstance().screen instanceof PlayerKitSelectScreen) {
                com.example.aas.network.PacketHandler.INSTANCE.sendToServer(new com.example.aas.network.PacketRequestKitMenu());
            }
        }
    }

    public static void handleSyncPoint(PacketSyncPoint msg) {
        ClientData.isInsidePoint = msg.isInside;

        // Р•СЃР»Рё РїР°РєРµС‚ РїСѓСЃС‚РѕР№ (СЃРµСЂРІРµСЂ С€Р»РµС‚ РµРіРѕ, С‡С‚РѕР±С‹ СЃРєСЂС‹С‚СЊ РїР»Р°С€РєСѓ HUD Сѓ С‚РµС…, РєС‚Рѕ РІРЅРµ С‚РѕС‡РєРё)
        if (msg.name.isEmpty()) {
            ClientData.pointName = "";
            return;
        }

        // Р•СЃР»Рё РёРіСЂРѕРє С„РёР·РёС‡РµСЃРєРё СЃС‚РѕРёС‚ РЅР° С‚РѕС‡РєРµ - РѕР±РЅРѕРІР»СЏРµРј РґР°РЅРЅС‹Рµ РґР»СЏ С†РµРЅС‚СЂР°Р»СЊРЅРѕРіРѕ HUD
        if (msg.isInside) {
            ClientData.pointName = msg.name;
            ClientData.pointOwner = msg.owner;
            ClientData.pointProgress = msg.progress;
            ClientData.isLocked = msg.isLocked;
            ClientData.nextObjectiveName = msg.nextObjective;
            ClientData.lockSecondsLeft = msg.lockSecondsLeft;
            ClientData.isContested = msg.isContested;
            ClientData.pointCapturingTeam = msg.capturingTeam;
            ClientData.pointCaptureRate = msg.captureRate;
        }

        // === Р“Р›РћР‘РђР›Р¬РќРћР• РћР‘РќРћР’Р›Р•РќРР• РљРђР РўР« ===
        // РћР±РЅРѕРІР»СЏРµРј СЃС‚Р°С‚СѓСЃ С‚РѕС‡РєРё РІ РєСЌС€Рµ РєР°СЂС‚С‹ (СЂР°Р±РѕС‚Р°РµС‚ РґР»СЏ РІСЃРµС… РёРіСЂРѕРєРѕРІ РЅР° СЃРµСЂРІРµСЂРµ!)
        if (ClientData.allCapturePoints != null) {
            for (AASWorldData.CapturePoint cp : ClientData.allCapturePoints) {
                if (cp.name.equals(msg.name)) {
                    cp.owner = msg.owner;
                    cp.progress = msg.progress;
                    cp.capturingTeam = msg.capturingTeam;
                    break;
                }
            }
        }
    }
    public static void openPointEditor(com.example.aas.network.PacketOpenPointEditor msg) {
        Minecraft.getInstance().setScreen(new com.example.aas.client.gui.PointEditorScreen(msg));
    }
    public static void openVictoryScreen(com.example.aas.network.VictoryData data) {
        ClientData.lastVictory = data;
        Minecraft.getInstance().setScreen(
                new com.example.aas.client.gui.VictoryScreen(data, false)
        );
    }
    public static void resetVictoryScreen() {
        ClientData.lastVictory = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.example.aas.client.gui.VictoryScreen
                || (mc.screen instanceof com.example.aas.client.gui.StatisticsScreen st && st.isOpenedFromVictory())) {
            mc.setScreen(null);
        }
    }
    // === Р”Р›РЇ РЎРўРђРќР¦РР ===
    public static Object playStationSound(VehicleStationBlockEntity entity, Object currentSound) {
        Minecraft mc = Minecraft.getInstance();
        System.out.println("[AAS DEBUG] playStationSound called, currentSound=" + currentSound);

        if (currentSound != null && mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance) currentSound)) {
            System.out.println("[AAS DEBUG] sound already active, skip");
            return currentSound;
        }

        StationLoopingSound newSound = new StationLoopingSound(entity);
        System.out.println("[AAS DEBUG] creating new StationLoopingSound and calling play()");
        mc.getSoundManager().play(newSound);
        return newSound;
    }

    public static void stopStationSound(Object sound) {
        // Р‘Р«Р›Рћ: if (sound instanceof StationLoopingSound s) s.stop();
        // РЎРўРђР›Рћ:
        if (sound instanceof StationLoopingSound s) s.stopSound();
    }
    public static void handleRadioVoiceActivity(String playerName) {
        boolean isNewSpeaker = !ClientData.RADIO_SPEAKERS.containsKey(playerName);

        // РћР±РЅРѕРІР»СЏРµРј С‚Р°Р№РјРµСЂ РіРѕРІРѕСЂСЏС‰РµРіРѕ РїРѕ СЂР°С†РёРё
        ClientData.RADIO_SPEAKERS.put(playerName, System.currentTimeMillis());

        // Р•СЃР»Рё РёРіСЂРѕРє С‚РѕР»СЊРєРѕ РЅР°С‡Р°Р» РіРѕРІРѕСЂРёС‚СЊ - РїСЂРѕРёРіСЂС‹РІР°РµРј РїРёРї (РєРѕРґ РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ СЃС‚СЂРѕРіРѕ РЅР° РєР»РёРµРЅС‚Рµ)
        if (isNewSpeaker) {
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.RADIO_BEEP.get(), 1.0F)
            );
        }
    }
    public static void handleReviveProgress(float progress, boolean isBeingRevived) {
        if (isBeingRevived) {
            ClientData.reviveProgressSelf = progress;
        } else {
            ClientData.reviveProgressOther = progress;
        }
    }
}

