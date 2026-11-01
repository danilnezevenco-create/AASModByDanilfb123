// PATH: src\main\java\com\example\aas\world\AASWorldData.java
package com.example.aas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import com.example.aas.world.AASWorldData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.EnumMap;
import com.example.aas.deck.DeckParams;
import com.example.aas.deck.DeckPhase;
import com.example.aas.deck.DeckType;

public class AASWorldData extends SavedData {
    public List<CapturePoint> capturePoints = new ArrayList<>();
    public List<BlockPos> blueRallies = new ArrayList<>();
    public List<BlockPos> redRallies = new ArrayList<>();
    public List<HubInfo> hubs = new ArrayList<>();
    public List<Squad> squads = new ArrayList<>();
    public int blueCMDId = -1;
    public int redCMDId = -1;
    public boolean blueCmdVoteActive = false;
    public String blueCmdCandidateName = "";
    public int blueCmdCandidateId = -1;
    public int blueCmdVoteTimer = 0;
    public Map<UUID, Boolean> blueCmdVotes = new HashMap<>();
    public int maxBlueHubs = 8;
    public int maxRedHubs = 8;
    public List<StationInfo> vehicleStations = new ArrayList<>();

    public boolean redCmdVoteActive = false;
    public String redCmdCandidateName = "";
    public int redCmdCandidateId = -1;
    public int redCmdVoteTimer = 0;
    public Map<UUID, Boolean> redCmdVotes = new HashMap<>();
    /** Start-of-match artillery cooldown of the whole team (applies to every SL), game time. Saved. */
    public long blueArtStrikeCdEnd = 0;
    public long redArtStrikeCdEnd = 0;
    /** Artillery requests (several per team, at most one per SL) and the per-SL cooldown end (game time). Saved. */
    public List<com.example.aas.art.ArtRequest> artRequests = new ArrayList<>();
    public Map<UUID, Long> artCooldownEnd = new HashMap<>();
    /** Active strikes (circle / rectangle), ticked by ArtManager. Saved. */
    public List<com.example.aas.art.ArtStrike> activeStrikes = new ArrayList<>();
    // --- Recon UAV: cooldown end (game time, saved) and the active UAV (runtime only, the entity is not saved) ---
    public long blueUavCooldownEnd = 0;
    public long redUavCooldownEnd = 0;
    public com.example.aas.uav.UavState blueUav = null;
    public com.example.aas.uav.UavState redUav = null;
    public String shapeType = "CUBE"; // CUBE РёР»Рё CYLINDER
    public int lockDurationMinutes = 0; // РќР° СЃРєРѕР»СЊРєРѕ РјРёРЅСѓС‚ Р±Р»РѕРєРёСЂСѓРµС‚СЃСЏ С‚РѕС‡РєР°
    public long lockedUntilTick = 0; // Р“РµР№Рј-С‚РёРє, РґРѕ РєРѕС‚РѕСЂРѕРіРѕ С‚РѕС‡РєР° Р·Р°РєСЂС‹С‚Р° РґР»СЏ РІСЂР°РіР°
    public Map<String, BlockPos> blueSpawns = new HashMap<>();
    public Map<String, BlockPos> redSpawns = new HashMap<>();
    public Map<String, BlockPos> neutralSpawns = new HashMap<>();
    public List<VehicleRecord> markedVehicles = new ArrayList<>();
    public int blueTickets = 800;
    public int redTickets = 800;
    public int respawnTimer = 10;
    public int deathTicketCost = 2;
    public String blueFaction = "none";
    public String redFaction = "none";
    public boolean isGameStarted = false;
    public String gameMode = "AAS";
    public String invasionDefender = "NONE";
    public int invasionPrepTicks = 0;
    public boolean pvpEnabled = true;
    public int countdownTicks = 0;
    public boolean countdownActive = false;
    public boolean playedBlueSiren = false;
    public boolean playedRedSiren = false;
    // --- decks (preparation + deck choice). Kits are separate for every deck of every team. ---
    public static final int DATA_VERSION = 1;
    public int dataVersion = DATA_VERSION;
    public final Map<DeckType, Map<String, KitInfo>> blueDeckKits = new EnumMap<>(DeckType.class);
    public final Map<DeckType, Map<String, KitInfo>> redDeckKits = new EnumMap<>(DeckType.class);
    public final Map<DeckType, DeckParams> blueDeckParams = new EnumMap<>(DeckType.class);
    public final Map<DeckType, DeckParams> redDeckParams = new EnumMap<>(DeckType.class);
    /** The deck each team won the vote with (null = no preparation was run: behaves like COMBINED). */
    public DeckType blueDeck = null;
    public DeckType redDeck = null;
    public final Map<UUID, DeckType> blueDeckVotes = new HashMap<>();
    public final Map<UUID, DeckType> redDeckVotes = new HashMap<>();
    public DeckPhase prepPhase = DeckPhase.NONE;
    public int prepTimer = 0;
    /** While true the lobby zone does nothing (lobbyZones / lobbyCenter are kept). */
    public boolean lobbyDisabled = false;
    /** maxBlueHubs / maxRedHubs were replaced by the hubLimit of the chosen decks; the old values are kept here. */
    public boolean hubsOverridden = false;
    public int prevMaxBlueHubs = 8;
    public int prevMaxRedHubs = 8;

    public int mapCenterX = 0;
    public int mapCenterZ = 0;
    public int mapSizeBlocks = 2048;
    public String currentMapImage = "map1";

    public static final String[] KIT_NAMES = {
            "Officer", "Pilot Officer", "Mechanic Officer", "Scout", // РќРѕРІС‹Рµ РєРёС‚С‹ Р·РґРµСЃСЊ
            "LAT", "HAT", "Sapper", "Sniper", "Marksman",
            "LMG", "HMG", "Rifleman", "Medic", "Grenadier", "Assault",
            "Pilot", "Mechanic", "Drone Operator", "Anti_air"
    };

    public AASWorldData() {
        // РРЅРёС†РёР°Р»РёР·Р°С†РёСЏ РїСѓСЃС‚С‹С… РєРёС‚РѕРІ РїСЂРё СЃРѕР·РґР°РЅРёРё РјРёСЂР°
        initDecks();

    }
    public List<BlockPos> triggerBlocks = new ArrayList<>();

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("BlueTickets", blueTickets);
        tag.putInt("RedTickets", redTickets);
        tag.putInt("RespawnTimer", respawnTimer);
        tag.putInt("DeathTicketCost", deathTicketCost);
        tag.putString("BlueFaction", blueFaction);
        tag.putString("RedFaction", redFaction);
        tag.putBoolean("IsGameStarted", isGameStarted);
        tag.putString("GameMode", gameMode);
        tag.putString("InvasionDefender", invasionDefender);
        tag.putInt("InvasionPrepTicks", invasionPrepTicks);
        tag.putBoolean("PvpEnabled", pvpEnabled);
        tag.putInt("CountdownTicks", countdownTicks);
        tag.putBoolean("CountdownActive", countdownActive);
        tag.putBoolean("PlayedBlueSiren", playedBlueSiren);
        tag.putBoolean("PlayedRedSiren", playedRedSiren);
        tag.putInt("MapCenterX", mapCenterX);
        tag.putInt("MapCenterZ", mapCenterZ);
        tag.putInt("MapSizeBlocks", mapSizeBlocks);
        tag.putString("CurrentMapImage", currentMapImage);
        tag.putLong("BlueArtCD", blueArtStrikeCdEnd);
        tag.putLong("RedArtCD", redArtStrikeCdEnd);
        tag.putLong("BlueUavCD", blueUavCooldownEnd);
        tag.putLong("RedUavCD", redUavCooldownEnd);
        tag.putInt("MaxBlueHubs", maxBlueHubs);
        tag.putInt("MaxRedHubs", maxRedHubs);
        // --- РЎРћРҐР РђРќР•РќРР• РЎРўРђРўРЈРЎРђ РљРћРњРђРќР”РР РћР’ ---
        tag.putInt("BlueCMDId", blueCMDId);
        tag.putInt("RedCMDId", redCMDId);
        ListTag stationList = new ListTag();
        for (StationInfo s : vehicleStations) {
            stationList.add(s.save());
        }
        tag.put("VehicleStations", stationList);

        // --- РЎРћРҐР РђРќР•РќРР• Р“РћР›РћРЎРћР’РђРќРРЇ BLUE ---
        tag.putBoolean("BlueCmdActive", blueCmdVoteActive);
        tag.putString("BlueCmdCandName", blueCmdCandidateName);
        tag.putInt("BlueCmdCandId", blueCmdCandidateId);
        tag.putInt("BlueCmdTimer", blueCmdVoteTimer);
        CompoundTag blueVotesTag = new CompoundTag();
        blueCmdVotes.forEach((uuid, val) -> blueVotesTag.putBoolean(uuid.toString(), val));
        tag.put("BlueCmdVotesMap", blueVotesTag);

        // --- РЎРћРҐР РђРќР•РќРР• Р“РћР›РћРЎРћР’РђРќРРЇ RED ---
        tag.putBoolean("RedCmdActive", redCmdVoteActive);
        tag.putString("RedCmdCandName", redCmdCandidateName);
        tag.putInt("RedCmdCandId", redCmdCandidateId);
        tag.putInt("RedCmdTimer", redCmdVoteTimer);
        CompoundTag redVotesTag = new CompoundTag();
        redCmdVotes.forEach((uuid, val) -> redVotesTag.putBoolean(uuid.toString(), val));
        tag.put("RedCmdVotesMap", redVotesTag);
        ListTag vehicleList = new ListTag();
        for (VehicleRecord v : markedVehicles) {
            vehicleList.add(v.save());
        }
        CompoundTag pendingRespawnsTag = new CompoundTag();
        pendingSpawnerRespawns.forEach((p, t) -> pendingRespawnsTag.putLong(String.valueOf(p.asLong()), t));
        tag.put("PendingSpawnerRespawns", pendingRespawnsTag);
        ListTag triggerList = new ListTag();
        for (BlockPos p : triggerBlocks) triggerList.add(LongTag.valueOf(p.asLong()));
        tag.put("TriggerBlocks", triggerList);
        tag.put("MarkedVehicles", vehicleList);
        ListTag markerList = new ListTag();
        for (MapMarker m : activeMarkers) markerList.add(m.save());
        tag.put("TacticalMarkers", markerList);
        ListTag blueList = new ListTag();
        for (BlockPos pos : blueRallies) blueList.add(LongTag.valueOf(pos.asLong()));
        tag.put("BlueRallies", blueList);

        ListTag mainZoneList = new ListTag();
        for (MainProtectionZone zone : mainZones) mainZoneList.add(zone.save());
        tag.put("MainZones", mainZoneList);

        if (lobbyCenter != null) {
            CompoundTag centerTag = new CompoundTag();
            centerTag.putInt("X", lobbyCenter.getX());
            centerTag.putInt("Y", lobbyCenter.getY());
            centerTag.putInt("Z", lobbyCenter.getZ());
            tag.put("LobbyCenter", centerTag);
        }

        ListTag lobbyZoneList = new ListTag();
        for (LobbyZone zone : lobbyZones) lobbyZoneList.add(zone.save());
        tag.put("LobbyZones", lobbyZoneList);

        ListTag redList = new ListTag();
        for (BlockPos pos : redRallies) redList.add(LongTag.valueOf(pos.asLong()));
        tag.put("RedRallies", redList);

        ListTag hubList = new ListTag();
        for (HubInfo h : hubs) hubList.add(h.save());
        tag.put("HubsData", hubList);

        CompoundTag blueSpawnsTag = new CompoundTag();
        blueSpawns.forEach((dim, pos) -> blueSpawnsTag.putLong(dim, pos.asLong()));
        tag.put("BlueSpawnsMap", blueSpawnsTag);

        CompoundTag redSpawnsTag = new CompoundTag();
        redSpawns.forEach((dim, pos) -> redSpawnsTag.putLong(dim, pos.asLong()));
        tag.put("RedSpawnsMap", redSpawnsTag);

        CompoundTag neutralSpawnsTag = new CompoundTag();
        neutralSpawns.forEach((dim, pos) -> neutralSpawnsTag.putLong(dim, pos.asLong()));
        tag.put("NeutralSpawnsMap", neutralSpawnsTag);

        ListTag pointsList = new ListTag();
        for (CapturePoint point : capturePoints) pointsList.add(point.save());
        tag.put("CapturePoints", pointsList);

        ListTag squadList = new ListTag();
        for (Squad s : squads) squadList.add(s.save());
        tag.put("Squads", squadList);

        // === РЎРћРҐР РђРќР•РќРР• РљРРўРћР’ ===
        saveDeckData(tag);
        ListTag reqList = new ListTag();
        for (com.example.aas.art.ArtRequest r : artRequests) reqList.add(r.save());
        tag.put("ArtRequests", reqList);

        ListTag cdList = new ListTag();
        for (Map.Entry<UUID, Long> e : artCooldownEnd.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", e.getKey());
            c.putLong("End", e.getValue());
            cdList.add(c);
        }
        tag.put("ArtCooldowns", cdList);

        ListTag artList = new ListTag();
        for (com.example.aas.art.ArtStrike s : activeStrikes) artList.add(s.save());
        tag.put("ActiveStrikesList", artList);

        return tag;
    }

    public static AASWorldData load(CompoundTag tag) {
        AASWorldData data = new AASWorldData();
        data.blueTickets = tag.getInt("BlueTickets");
        data.redTickets = tag.getInt("RedTickets");
        data.respawnTimer = tag.getInt("RespawnTimer");
        data.deathTicketCost = tag.getInt("DeathTicketCost");
        data.blueFaction = tag.getString("BlueFaction");
        data.redFaction = tag.getString("RedFaction");
        data.isGameStarted = tag.getBoolean("IsGameStarted");
        data.gameMode = tag.contains("GameMode") ? tag.getString("GameMode") : "AAS";
        data.invasionDefender = tag.getString("InvasionDefender");
        data.invasionPrepTicks = tag.getInt("InvasionPrepTicks");
        data.pvpEnabled = tag.contains("PvpEnabled") ? tag.getBoolean("PvpEnabled") : true;
        data.countdownTicks = tag.getInt("CountdownTicks");
        data.countdownActive = tag.getBoolean("CountdownActive");
        data.playedBlueSiren = tag.getBoolean("PlayedBlueSiren");
        data.playedRedSiren = tag.getBoolean("PlayedRedSiren");
        data.mapCenterX = tag.getInt("MapCenterX");
        data.mapCenterZ = tag.getInt("MapCenterZ");
        data.mapSizeBlocks = tag.contains("MapSizeBlocks") ? tag.getInt("MapSizeBlocks") : 2048;
        data.currentMapImage = tag.contains("CurrentMapImage") ? tag.getString("CurrentMapImage") : "map1";
        data.blueArtStrikeCdEnd = tag.getLong("BlueArtCD");
        data.redArtStrikeCdEnd = tag.getLong("RedArtCD");
        data.blueUavCooldownEnd = tag.getLong("BlueUavCD");
        data.redUavCooldownEnd = tag.getLong("RedUavCD");
        data.maxBlueHubs = tag.contains("MaxBlueHubs") ? tag.getInt("MaxBlueHubs") : 8;
        data.maxRedHubs = tag.contains("MaxRedHubs") ? tag.getInt("MaxRedHubs") : 8;
        data.blueCMDId = tag.getInt("BlueCMDId");
        data.redCMDId = tag.getInt("RedCMDId");

        // --- Р—РђР“Р РЈР—РљРђ Р“РћР›РћРЎРћР’РђРќРРЇ BLUE ---
        data.blueCmdVoteActive = tag.getBoolean("BlueCmdActive");
        data.blueCmdCandidateName = tag.getString("BlueCmdCandName");
        data.blueCmdCandidateId = tag.getInt("BlueCmdCandId");
        data.blueCmdVoteTimer = tag.getInt("BlueCmdTimer");
        if (tag.contains("BlueCmdVotesMap")) {
            CompoundTag cv = tag.getCompound("BlueCmdVotesMap");
            for (String key : cv.getAllKeys()) data.blueCmdVotes.put(UUID.fromString(key), cv.getBoolean(key));
        }

        // --- Р—РђР“Р РЈР—РљРђ Р“РћР›РћРЎРћР’РђРќРРЇ RED ---
        data.redCmdVoteActive = tag.getBoolean("RedCmdActive");
        data.redCmdCandidateName = tag.getString("RedCmdCandName");
        data.redCmdCandidateId = tag.getInt("RedCmdCandId");
        data.redCmdVoteTimer = tag.getInt("RedCmdTimer");
        if (tag.contains("RedCmdVotesMap")) {
            CompoundTag cv = tag.getCompound("RedCmdVotesMap");
            for (String key : cv.getAllKeys()) data.redCmdVotes.put(UUID.fromString(key), cv.getBoolean(key));
        }
        if (tag.contains("TacticalMarkers")) {
            ListTag list = tag.getList("TacticalMarkers", 10);
            for (int i = 0; i < list.size(); i++) data.activeMarkers.add(MapMarker.load(list.getCompound(i)));
        }
        if (tag.contains("TriggerBlocks")) {
            ListTag list = tag.getList("TriggerBlocks", Tag.TAG_LONG);
            for (Tag t : list) data.triggerBlocks.add(BlockPos.of(((LongTag) t).getAsLong()));
        }
        if (tag.contains("MainZones")) {
            ListTag list = tag.getList("MainZones", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.mainZones.add(MainProtectionZone.load(list.getCompound(i)));
        }
        if (tag.contains("LobbyCenter")) {
            CompoundTag centerTag = tag.getCompound("LobbyCenter");
            data.lobbyCenter = new BlockPos(centerTag.getInt("X"), centerTag.getInt("Y"), centerTag.getInt("Z"));
        }
        if (tag.contains("LobbyZones")) {
            ListTag list = tag.getList("LobbyZones", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.lobbyZones.add(LobbyZone.load(list.getCompound(i)));
        }
        if (tag.contains("MarkedVehicles")) {
            ListTag list = tag.getList("MarkedVehicles", 10);
            for (int i = 0; i < list.size(); i++) {
                data.markedVehicles.add(VehicleRecord.load(list.getCompound(i)));
            }
        }
        if (tag.contains("PendingSpawnerRespawns")) {
            CompoundTag pendingTag = tag.getCompound("PendingSpawnerRespawns");
            for (String key : pendingTag.getAllKeys()) {
                long posLong = Long.parseLong(key);
                data.pendingSpawnerRespawns.put(BlockPos.of(posLong), pendingTag.getLong(key));
            }
        }
        if (tag.contains("BlueRallies")) {
            ListTag list = tag.getList("BlueRallies", Tag.TAG_LONG);
            for (Tag t : list) data.blueRallies.add(BlockPos.of(((LongTag) t).getAsLong()));
        }
        if (tag.contains("RedRallies")) {
            ListTag list = tag.getList("RedRallies", Tag.TAG_LONG);
            for (Tag t : list) data.redRallies.add(BlockPos.of(((LongTag) t).getAsLong()));
        }

        if (tag.contains("HubsData")) {
            ListTag list = tag.getList("HubsData", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                data.hubs.add(HubInfo.load(list.getCompound(i)));
            }
        }

        if (tag.contains("BlueSpawnsMap")) {
            CompoundTag map = tag.getCompound("BlueSpawnsMap");
            for(String key : map.getAllKeys()) data.blueSpawns.put(key, BlockPos.of(map.getLong(key)));
        }
        if (tag.contains("RedSpawnsMap")) {
            CompoundTag map = tag.getCompound("RedSpawnsMap");
            for(String key : map.getAllKeys()) data.redSpawns.put(key, BlockPos.of(map.getLong(key)));
        }
        if (tag.contains("NeutralSpawnsMap")) {
            CompoundTag map = tag.getCompound("NeutralSpawnsMap");
            for(String key : map.getAllKeys()) data.neutralSpawns.put(key, BlockPos.of(map.getLong(key)));
        }
        if (tag.contains("CapturePoints")) {
            ListTag list = tag.getList("CapturePoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.capturePoints.add(CapturePoint.load(list.getCompound(i)));
        }
        if (tag.contains("Squads")) {
            ListTag list = tag.getList("Squads", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.squads.add(Squad.load(list.getCompound(i)));
        }

        if (tag.contains("VehicleStations")) {
            ListTag list = tag.getList("VehicleStations", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                // Р”РћР‘РђР’Р¬ "data." РїРµСЂРµРґ vehicleStations
                data.vehicleStations.add(StationInfo.load(list.getCompound(i)));
            }
        }

        // === Р—РђР“Р РЈР—РљРђ РљРРўРћР’ ===
        data.loadDeckData(tag);

        if (tag.contains("ArtRequests")) {
            ListTag list = tag.getList("ArtRequests", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.artRequests.add(com.example.aas.art.ArtRequest.load(list.getCompound(i)));
        }
        if (tag.contains("ArtCooldowns")) {
            ListTag list = tag.getList("ArtCooldowns", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                data.artCooldownEnd.put(c.getUUID("Id"), c.getLong("End"));
            }
        }
        if (tag.contains("ActiveStrikesList")) {
            ListTag list = tag.getList("ActiveStrikesList", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                com.example.aas.art.ArtStrike s = com.example.aas.art.ArtStrike.load(list.getCompound(i));
                if (s != null) data.activeStrikes.add(s);   // entries of the old strike system are skipped
            }
        }
        data.activeMarkers.removeIf(m -> "Artillery Request".equals(m.type));   // old map-marker request flow is gone
        if (tag.contains("VehicleStations")) {
            ListTag list = tag.getList("VehicleStations", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.vehicleStations.add(StationInfo.load(list.getCompound(i)));
        }
        return data;
    }

    // ====================================================================== DECKS

    private static boolean isBlueTeam(String team) {
        return "BLUE".equalsIgnoreCase(team);
    }

    private void initDecks() {
        for (DeckType deck : DeckType.VALUES) {
            blueDeckKits.put(deck, newKitMap());
            redDeckKits.put(deck, newKitMap());
            blueDeckParams.put(deck, DeckParams.defaultsFor(deck));
            redDeckParams.put(deck, DeckParams.defaultsFor(deck));
        }
    }

    /** Empty KitInfo for every name in KIT_NAMES. */
    private static Map<String, KitInfo> newKitMap() {
        Map<String, KitInfo> m = new HashMap<>();
        for (String name : KIT_NAMES) m.put(name, new KitInfo(name));
        return m;
    }

    /** The kits of one deck of one team ("BLUE" / "RED"). Never falls back to another deck. */
    public Map<String, KitInfo> kitsFor(String team, DeckType deck) {
        Map<DeckType, Map<String, KitInfo>> all = isBlueTeam(team) ? blueDeckKits : redDeckKits;
        return all.computeIfAbsent(deck, k -> newKitMap());
    }

    public DeckParams paramsFor(String team, DeckType deck) {
        Map<DeckType, DeckParams> all = isBlueTeam(team) ? blueDeckParams : redDeckParams;
        return all.computeIfAbsent(deck, DeckParams::defaultsFor);
    }

    /** The deck the team won with, null if none (also for NEUTRAL). */
    public DeckType chosenDeck(String team) {
        if ("BLUE".equalsIgnoreCase(team)) return blueDeck;
        if ("RED".equalsIgnoreCase(team)) return redDeck;
        return null;
    }

    /** Chosen deck, or COMBINED when there is none (old worlds, match started without preparation). */
    public DeckType effectiveDeck(String team) {
        DeckType d = chosenDeck(team);
        return d != null ? d : DeckType.COMBINED;
    }

    /** The kits players of this team use right now = kits of the effective deck. */
    public Map<String, KitInfo> activeKits(String team) {
        return kitsFor(team, effectiveDeck(team));
    }

    /** Enabled decks of the team (never empty: COMBINED is returned if the admin disabled everything). */
    public List<DeckType> enabledDecks(String team) {
        List<DeckType> out = new ArrayList<>();
        for (DeckType deck : DeckType.VALUES) if (paramsFor(team, deck).enabled) out.add(deck);
        if (out.isEmpty()) out.add(DeckType.COMBINED);
        return out;
    }

    private static CompoundTag saveKits(Map<DeckType, Map<String, KitInfo>> all) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<DeckType, Map<String, KitInfo>> e : all.entrySet()) {
            CompoundTag kits = new CompoundTag();
            for (KitInfo k : e.getValue().values()) kits.put(k.name, k.save());
            out.put(e.getKey().id, kits);
        }
        return out;
    }

    private static void loadKits(CompoundTag in, Map<DeckType, Map<String, KitInfo>> all) {
        for (DeckType deck : DeckType.VALUES) {
            Map<String, KitInfo> kits = newKitMap();
            if (in.contains(deck.id, Tag.TAG_COMPOUND)) {
                CompoundTag t = in.getCompound(deck.id);
                for (String key : t.getAllKeys()) kits.put(key, KitInfo.load(t.getCompound(key)));
            }
            all.put(deck, kits);
        }
    }

    private static CompoundTag saveParams(Map<DeckType, DeckParams> all) {
        CompoundTag out = new CompoundTag();
        for (Map.Entry<DeckType, DeckParams> e : all.entrySet()) out.put(e.getKey().id, e.getValue().save());
        return out;
    }

    private static void loadParams(CompoundTag in, Map<DeckType, DeckParams> all) {
        for (DeckType deck : DeckType.VALUES) {
            all.put(deck, in.contains(deck.id, Tag.TAG_COMPOUND) ? DeckParams.load(in.getCompound(deck.id), deck) : DeckParams.defaultsFor(deck));
        }
    }

    private static CompoundTag saveVotes(Map<UUID, DeckType> votes) {
        CompoundTag out = new CompoundTag();
        votes.forEach((uuid, deck) -> out.putString(uuid.toString(), deck.id));
        return out;
    }

    private static void loadVotes(CompoundTag in, Map<UUID, DeckType> votes) {
        votes.clear();
        for (String key : in.getAllKeys()) {
            DeckType deck = DeckType.fromId(in.getString(key));
            if (deck == null) continue;
            try { votes.put(UUID.fromString(key), deck); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void saveDeckData(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);
        CompoundTag dd = new CompoundTag();
        dd.put("BlueKits", saveKits(blueDeckKits));
        dd.put("RedKits", saveKits(redDeckKits));
        dd.put("BlueParams", saveParams(blueDeckParams));
        dd.put("RedParams", saveParams(redDeckParams));
        if (blueDeck != null) dd.putString("BlueDeck", blueDeck.id);
        if (redDeck != null) dd.putString("RedDeck", redDeck.id);
        dd.putString("Phase", prepPhase.name());
        dd.putInt("PhaseTimer", prepTimer);
        dd.put("BlueVotes", saveVotes(blueDeckVotes));
        dd.put("RedVotes", saveVotes(redDeckVotes));
        dd.putBoolean("LobbyDisabled", lobbyDisabled);
        dd.putBoolean("HubsOverridden", hubsOverridden);
        dd.putInt("PrevBlueHubs", prevMaxBlueHubs);
        dd.putInt("PrevRedHubs", prevMaxRedHubs);
        tag.put("DeckData", dd);
    }

    /** New format, or migration of an old world (no DataVersion / DeckData tag). */
    private void loadDeckData(CompoundTag tag) {
        if (tag.contains("DataVersion") && tag.contains("DeckData", Tag.TAG_COMPOUND)) {
            CompoundTag dd = tag.getCompound("DeckData");
            loadKits(dd.getCompound("BlueKits"), blueDeckKits);
            loadKits(dd.getCompound("RedKits"), redDeckKits);
            loadParams(dd.getCompound("BlueParams"), blueDeckParams);
            loadParams(dd.getCompound("RedParams"), redDeckParams);
            blueDeck = DeckType.fromId(dd.getString("BlueDeck"));
            redDeck = DeckType.fromId(dd.getString("RedDeck"));
            prepPhase = DeckPhase.fromName(dd.getString("Phase"));
            prepTimer = dd.getInt("PhaseTimer");
            loadVotes(dd.getCompound("BlueVotes"), blueDeckVotes);
            loadVotes(dd.getCompound("RedVotes"), redDeckVotes);
            lobbyDisabled = dd.getBoolean("LobbyDisabled");
            hubsOverridden = dd.getBoolean("HubsOverridden");
            prevMaxBlueHubs = dd.contains("PrevBlueHubs") ? dd.getInt("PrevBlueHubs") : maxBlueHubs;
            prevMaxRedHubs = dd.contains("PrevRedHubs") ? dd.getInt("PrevRedHubs") : maxRedHubs;
            dataVersion = tag.getInt("DataVersion");
        } else {
            migrateLegacy(tag);
        }
    }

    /**
     * Old world: everything that existed before decks goes into the COMBINED deck of both teams
     * (kits, starting tickets = the saved ticket counts, hub limit = maxBlueHubs / maxRedHubs).
     * The other 6 decks stay empty with default settings. Saved in the new format at the next save, so it runs once.
     */
    private void migrateLegacy(CompoundTag tag) {
        Map<String, KitInfo> bk = kitsFor("BLUE", DeckType.COMBINED);
        Map<String, KitInfo> rk = kitsFor("RED", DeckType.COMBINED);
        if (tag.contains("BlueKits")) {
            CompoundTag t = tag.getCompound("BlueKits");
            for (String key : t.getAllKeys()) bk.put(key, KitInfo.load(t.getCompound(key)));
        }
        if (tag.contains("RedKits")) {
            CompoundTag t = tag.getCompound("RedKits");
            for (String key : t.getAllKeys()) rk.put(key, KitInfo.load(t.getCompound(key)));
        }
        DeckParams bp = paramsFor("BLUE", DeckType.COMBINED);
        bp.tickets = blueTickets;
        bp.hubLimit = maxBlueHubs;
        bp.clamp();
        DeckParams rp = paramsFor("RED", DeckType.COMBINED);
        rp.tickets = redTickets;
        rp.hubLimit = maxRedHubs;
        rp.clamp();
        dataVersion = DATA_VERSION;
        setDirty();
    }

    public static class StationInfo {
        public BlockPos pos;
        public String team;
        public String dimension;

        public StationInfo(BlockPos pos, String team, String dimension) {
            this.pos = pos;
            this.team = team;
            this.dimension = dimension;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putString("Team", team);
            tag.putString("Dim", dimension);
            return tag;
        }

        public static StationInfo load(CompoundTag tag) {
            return new StationInfo(BlockPos.of(tag.getLong("Pos")), tag.getString("Team"), tag.getString("Dim"));
        }
    }

    public static AASWorldData get(ServerLevel level) {
        String dimId = level.dimension().location().toString().replace(":", "_");
        String dataName = "aas_data_" + dimId;

        return level.getDataStorage().computeIfAbsent(AASWorldData::load, AASWorldData::new, dataName);
    }
    public static class HubInfo {
        public BlockPos pos;
        public String team;
        public boolean constructed;
        public String dimension;
        public boolean isBlocked;
        public int materials;
        public String builderName; // <--- РќРћР’РћР• РџРћР›Р•: РёРјСЏ СЃС‚СЂРѕРёС‚РµР»СЏ РґР»СЏ РѕС‡РєРѕРІ

        // РРЎРџР РђР’Р›Р•РќРћ: РљРѕРЅСЃС‚СЂСѓРєС‚РѕСЂ С‚РµРїРµСЂСЊ РїСЂРёРЅРёРјР°РµС‚ 5 Р°СЂРіСѓРјРµРЅС‚РѕРІ (builderName РґРѕР±Р°РІР»РµРЅ РІ РєРѕРЅРµС†)
        public HubInfo(BlockPos pos, String team, boolean constructed, String dimension, String builderName) {
            this.pos = pos;
            this.team = team;
            this.constructed = constructed;
            this.dimension = dimension;
            this.isBlocked = false;
            this.materials = 0;
            this.builderName = builderName != null ? builderName : "";
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putString("Team", team);
            tag.putBoolean("Constructed", constructed);
            tag.putString("Dimension", dimension != null ? dimension : "minecraft:overworld");
            tag.putInt("Materials", materials);
            tag.putString("Builder", builderName); // <--- РЎРћРҐР РђРќРЇР•Рњ РЎРўР РћРРўР•Р›РЇ
            return tag;
        }

        public static HubInfo load(CompoundTag tag) {
            BlockPos p = BlockPos.of(tag.getLong("Pos"));
            String t = tag.getString("Team");
            boolean c = tag.getBoolean("Constructed");
            String d = tag.contains("Dimension") ? tag.getString("Dimension") : "minecraft:overworld";
            String b = tag.contains("Builder") ? tag.getString("Builder") : ""; // <--- Р§РРўРђР•Рњ РЎРўР РћРРўР•Р›РЇ

            // РРЎРџР РђР’Р›Р•РќРћ: РџРµСЂРµРґР°РµРј 'b' РїСЏС‚С‹Рј Р°СЂРіСѓРјРµРЅС‚РѕРј, РѕС€РёР±РєР° РёСЃС‡РµР·РЅРµС‚!
            HubInfo h = new HubInfo(p, t, c, d, b);

            if (tag.contains("Materials")) h.materials = tag.getInt("Materials");
            return h;
        }
    }
    public static class SquadMarker {
        public int x, y, z; // РЎРѕС…СЂР°РЅСЏРµРј СЂРµР°Р»СЊРЅСѓСЋ Y
        public int type;    // 0-Move, 1-Attack, 2-Defend, 3-Build, 6-Rhombus (СЃРІРѕР±РѕРґРЅР°СЏ РјРµС‚РєР° SL/FTL)
        public long expiryTick;
        public boolean isPhysical;
        public UUID id; // РЈРЅРёРєР°Р»СЊРЅС‹Р№ ID РјРµС‚РєРё вЂ” РЅСѓР¶РµРЅ, С‡С‚РѕР±С‹ СѓРґР°Р»РёС‚СЊ РєРѕРЅРєСЂРµС‚РЅСѓСЋ РјРµС‚РєСѓ РїРѕ РєР»РёРєСѓ РЅР° РєР°СЂС‚Рµ
        // NEW: РєРѕРЅРµС† СЃС‚СЂРµР»РєРё (РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїСЂРё type == 7)
        public int endX, endZ;
        public SquadMarker(int x, int y, int z, int type, long expiryTick, boolean isPhysical) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.expiryTick = expiryTick;
            this.isPhysical = isPhysical;
            this.id = UUID.randomUUID();
        }
        // NEW
        public static SquadMarker arrow(int startX, int startZ, int endX, int endZ, long expiryTick) {
            SquadMarker m = new SquadMarker(startX, 64, startZ, 7, expiryTick, false);
            m.endX = endX;
            m.endZ = endZ;
            return m;
        }
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("X", x);
            tag.putInt("Y", y); // РЎРѕС…СЂР°РЅСЏРµРј Y
            tag.putInt("Z", z);
            tag.putInt("Type", type);
            tag.putLong("Expiry", expiryTick);
            tag.putBoolean("IsPhysical", isPhysical);
            tag.putUUID("Id", id);
            if (type == 7) {
                tag.putInt("EndX", endX);
                tag.putInt("EndZ", endZ);
            }
            return tag;
        }

        public static SquadMarker load(CompoundTag tag) {
            // РСЃРїСЂР°РІР»РµРЅРѕ: РІСЃРµ Р°СЂРіСѓРјРµРЅС‚С‹ С‚РµРїРµСЂСЊ РІРЅСѓС‚СЂРё СЃРєРѕР±РѕРє РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂР°
            SquadMarker m = new SquadMarker(
                    tag.getInt("X"),
                    tag.getInt("Y"),
                    tag.getInt("Z"),
                    tag.getInt("Type"),
                    tag.getLong("Expiry"),
                    tag.getBoolean("IsPhysical")
            );
            // РЎС‚Р°СЂС‹Рµ СЃРѕС…СЂР°РЅРµРЅРёСЏ РјРѕРіР»Рё РЅРµ СЃРѕРґРµСЂР¶Р°С‚СЊ Id вЂ” С‚РѕРіРґР° РѕСЃС‚Р°РІР»СЏРµРј СЃРіРµРЅРµСЂРёСЂРѕРІР°РЅРЅС‹Р№ РІ РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂРµ
            if (tag.hasUUID("Id")) {
                m.id = tag.getUUID("Id");
            }
            if (tag.contains("EndX")) {
                m.endX = tag.getInt("EndX");
                m.endZ = tag.getInt("EndZ");
            }
            return m;
        }
    }

    public static class Squad {
        public int id;
        public String name;
        public String team;
        public String leader;
        public boolean isLocked = false;
        public BlockPos rallyPos = null;
        public String dimension;
        public String rallyDimension;
        public List<String> members = new ArrayList<>();

        // --- Р¤РђР•Р РўРРњР« ---
        public String bravoLeader = "";
        public String charlieLeader = "";
        public List<String> bravoMembers = new ArrayList<>();
        public List<String> charlieMembers = new ArrayList<>();

        public SquadMarker bravoMarker = null;
        public SquadMarker charlieMarker = null;
        public BlockPos bravoPingPos = null;
        public long bravoPingExpiry = -1;
        public BlockPos charliePingPos = null;
        public long charliePingExpiry = -1;
        // -----------------

        // Р›РёРјРёС‚С‹ РЅР° РєРѕР»РёС‡РµСЃС‚РІРѕ СЃРІРѕР±РѕРґРЅС‹С… ("СЂРѕРјР±РѕРІС‹С…") РјРµС‚РѕРє, РєРѕС‚РѕСЂС‹Рµ РјРѕР¶РЅРѕ РєРёРґР°С‚СЊ РЅР° РєР°СЂС‚Сѓ.
        // РџСЂРё РїСЂРµРІС‹С€РµРЅРёРё Р»РёРјРёС‚Р° СЃР°РјР°СЏ СЃС‚Р°СЂР°СЏ РјРµС‚РєР° СѓРґР°Р»СЏРµС‚СЃСЏ Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё (FIFO).
        public static final int SL_MARKER_LIMIT = 10;
        public static final int FTL_MARKER_LIMIT = 5;

        public List<SquadMarker> rhombusMarkers = new ArrayList<>();       // РјРµС‚РєРё, РєРёРґР°РµРјС‹Рµ Р»РёРґРµСЂРѕРј РѕС‚СЂСЏРґР° (SL)
        public List<SquadMarker> bravoRhombusMarkers = new ArrayList<>();  // РјРµС‚РєРё, РєРёРґР°РµРјС‹Рµ Р»РёРґРµСЂРѕРј Bravo (FTL)
        public List<SquadMarker> charlieRhombusMarkers = new ArrayList<>();// РјРµС‚РєРё, РєРёРґР°РµРјС‹Рµ Р»РёРґРµСЂРѕРј Charlie (FTL)
        public SquadMarker marker = null;
        public long rallyExpiryTick = -1;
        public long slNoOfficerSince = -1;
        public long nextRallyAvailableTick = -1;
        public BlockPos pingPos = null;
        public long pingExpiry = -1;
        public boolean isRallyBlocked = false;

        public Squad(int id, String name, String team, String leader, String dimension) {
            this.id = id;
            this.name = name;
            this.team = team;
            this.leader = leader;
            this.dimension = dimension;
            this.rallyDimension = dimension;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("ID", id);
            tag.putString("Name", name);
            tag.putString("Team", team);
            tag.putString("Leader", leader);
            tag.putBoolean("IsLocked", isLocked);
            tag.putString("Dimension", dimension != null ? dimension : "minecraft:overworld");
            tag.putLong("RallyExpiry", rallyExpiryTick);
            tag.putLong("SLNoOfficerSince", slNoOfficerSince);
            tag.putLong("NextRallyAvailable", nextRallyAvailableTick);
            tag.putBoolean("RallyBlocked", isRallyBlocked);

            if (pingPos != null) {
                tag.putLong("PingPos", pingPos.asLong());
                tag.putLong("PingExpiry", pingExpiry);
            }
            if (rallyPos != null) {
                tag.putLong("RallyPos", rallyPos.asLong());
                tag.putString("RallyDim", rallyDimension != null ? rallyDimension : "minecraft:overworld");
            }

            // Р¤Р°РµСЂС‚РёРјС‹: РЎРѕС…СЂР°РЅРµРЅРёРµ
            tag.putString("BravoLeader", bravoLeader);
            tag.putString("CharlieLeader", charlieLeader);
            ListTag bList = new ListTag();
            for (String m : bravoMembers) bList.add(StringTag.valueOf(m));
            tag.put("BravoMembers", bList);
            ListTag cList = new ListTag();
            for (String m : charlieMembers) cList.add(StringTag.valueOf(m));
            tag.put("CharlieMembers", cList);

            if (bravoPingPos != null) {
                tag.putLong("BravoPingPos", bravoPingPos.asLong());
                tag.putLong("BravoPingExp", bravoPingExpiry);
            }
            if (charliePingPos != null) {
                tag.putLong("CharliePingPos", charliePingPos.asLong());
                tag.putLong("CharliePingExp", charliePingExpiry);
            }
            if (bravoMarker != null) tag.put("BravoMarker", bravoMarker.save());
            if (charlieMarker != null) tag.put("CharlieMarker", charlieMarker.save());

            ListTag rhombusList = new ListTag();
            for (SquadMarker rm : rhombusMarkers) rhombusList.add(rm.save());
            tag.put("RhombusList", rhombusList);

            ListTag bravoRhombusList = new ListTag();
            for (SquadMarker rm : bravoRhombusMarkers) bravoRhombusList.add(rm.save());
            tag.put("BravoRhombusList", bravoRhombusList);

            ListTag charlieRhombusList = new ListTag();
            for (SquadMarker rm : charlieRhombusMarkers) charlieRhombusList.add(rm.save());
            tag.put("CharlieRhombusList", charlieRhombusList);

            ListTag memList = new ListTag();
            for (String m : members) memList.add(StringTag.valueOf(m));
            tag.put("Members", memList);

            if (marker != null) tag.put("SquadMarker", marker.save());

            return tag;
        }

        public static Squad load(CompoundTag tag) {
            String l = tag.contains("Leader") ? tag.getString("Leader") : "";
            String dim = tag.contains("Dimension") ? tag.getString("Dimension") : "minecraft:overworld";

            Squad s = new Squad(tag.getInt("ID"), tag.getString("Name"), tag.getString("Team"), l, dim);
            s.rallyExpiryTick = tag.getLong("RallyExpiry");
            s.slNoOfficerSince = tag.getLong("SLNoOfficerSince");
            s.isRallyBlocked = tag.getBoolean("RallyBlocked");
            if (tag.contains("NextRallyAvailable")) s.nextRallyAvailableTick = tag.getLong("NextRallyAvailable");
            if (tag.contains("PingPos")) {
                s.pingPos = BlockPos.of(tag.getLong("PingPos"));
                s.pingExpiry = tag.getLong("PingExpiry");
            }
            if (tag.contains("IsLocked")) s.isLocked = tag.getBoolean("IsLocked");
            if (tag.contains("RallyPos")) {
                s.rallyPos = BlockPos.of(tag.getLong("RallyPos"));
                s.rallyDimension = tag.contains("RallyDim") ? tag.getString("RallyDim") : dim;
            }

            // Р¤Р°РµСЂС‚РёРјС‹: Р—Р°РіСЂСѓР·РєР°
            s.bravoLeader = tag.getString("BravoLeader");
            s.charlieLeader = tag.getString("CharlieLeader");
            if (tag.contains("BravoMembers")) {
                ListTag bList = tag.getList("BravoMembers", Tag.TAG_STRING);
                for (Tag t : bList) s.bravoMembers.add(t.getAsString());
            }
            if (tag.contains("CharlieMembers")) {
                ListTag cList = tag.getList("CharlieMembers", Tag.TAG_STRING);
                for (Tag t : cList) s.charlieMembers.add(t.getAsString());
            }
            if (tag.contains("BravoPingPos")) {
                s.bravoPingPos = BlockPos.of(tag.getLong("BravoPingPos"));
                s.bravoPingExpiry = tag.getLong("BravoPingExp");
            }
            if (tag.contains("CharliePingPos")) {
                s.charliePingPos = BlockPos.of(tag.getLong("CharliePingPos"));
                s.charliePingExpiry = tag.getLong("CharliePingExp");
            }
            if (tag.contains("BravoMarker")) s.bravoMarker = SquadMarker.load(tag.getCompound("BravoMarker"));
            if (tag.contains("CharlieMarker")) s.charlieMarker = SquadMarker.load(tag.getCompound("CharlieMarker"));

            if (tag.contains("RhombusList")) {
                ListTag list = tag.getList("RhombusList", 10);
                s.rhombusMarkers.clear();
                for (int i = 0; i < list.size(); i++) s.rhombusMarkers.add(SquadMarker.load(list.getCompound(i)));
            }
            if (tag.contains("BravoRhombusList")) {
                ListTag list = tag.getList("BravoRhombusList", 10);
                s.bravoRhombusMarkers.clear();
                for (int i = 0; i < list.size(); i++) s.bravoRhombusMarkers.add(SquadMarker.load(list.getCompound(i)));
            }
            if (tag.contains("CharlieRhombusList")) {
                ListTag list = tag.getList("CharlieRhombusList", 10);
                s.charlieRhombusMarkers.clear();
                for (int i = 0; i < list.size(); i++) s.charlieRhombusMarkers.add(SquadMarker.load(list.getCompound(i)));
            }

            if (tag.contains("Members")) {
                ListTag memList = tag.getList("Members", Tag.TAG_STRING);
                for (Tag t : memList) s.members.add(t.getAsString());
            }

            if (s.leader.isEmpty() && !s.members.isEmpty()) s.leader = s.members.get(0);
            if (!s.leader.isEmpty()) s.removeFromFireteams(s.leader);   // РќРћР’РћР•
            if (tag.contains("SquadMarker")) s.marker = SquadMarker.load(tag.getCompound("SquadMarker"));

            return s;
        }

        // Р’СЃРїРѕРјРѕРіР°С‚РµР»СЊРЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ РѕС‡РёСЃС‚РєРё РёРіСЂРѕРєР° РёР· РіСЂСѓРїРї
        public void removeFromFireteams(String pName) {
            if (bravoLeader.equals(pName)) bravoLeader = "";
            if (charlieLeader.equals(pName)) charlieLeader = "";
            bravoMembers.remove(pName);
            charlieMembers.remove(pName);
        }

        // РќРћР’РћР•: РЅР°Р·РЅР°С‡РµРЅРёРµ SL РІСЃРµРіРґР° СЃРЅРёРјР°РµС‚ РёРіСЂРѕРєР° СЃ СЂРѕР»РµР№ FTL/С‡Р»РµРЅР° С„Р°Р№СЂС‚РёРјР°
        public void setLeader(String name) {
            removeFromFireteams(name);
            this.leader = name;
        }
    }
    public List<MapMarker> activeMarkers = new ArrayList<>();

    public static class MapMarker {
        public BlockPos pos;
        public String type;
        public String team;
        public long expiryTick;
        public String placedBy = ""; // РќРћР’РћР•: РёРјСЏ РёРіСЂРѕРєР°, РїРѕСЃС‚Р°РІРёРІС€РµРіРѕ РјРµС‚РєСѓ

        public MapMarker(BlockPos pos, String type, String team, long expiryTick) {
            this.pos = pos;
            this.type = type;
            this.team = team;
            this.expiryTick = expiryTick;
        }

        // РќРћР’Р«Р™ РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂ СЃ placedBy
        public MapMarker(BlockPos pos, String type, String team, long expiryTick, String placedBy) {
            this.pos = pos;
            this.type = type;
            this.team = team;
            this.expiryTick = expiryTick;
            this.placedBy = placedBy;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putString("Type", type);
            tag.putString("Team", team);
            tag.putLong("Expiry", expiryTick);
            tag.putString("PlacedBy", placedBy); // РќРћР’РћР•
            return tag;
        }

        public static MapMarker load(CompoundTag tag) {
            MapMarker m = new MapMarker(
                    BlockPos.of(tag.getLong("Pos")),
                    tag.getString("Type"),
                    tag.getString("Team"),
                    tag.getLong("Expiry")
            );
            m.placedBy = tag.contains("PlacedBy") ? tag.getString("PlacedBy") : ""; // РќРћР’РћР•
            return m;
        }
    }
    public static class CapturePoint {
        public String name; public AABB area; public int bluePriority; public int redPriority; public int captureTimeMinutes; public int ticketPenalty; public int captureDeduction;
        public String owner = "NEUTRAL"; public float progress = 0.0f; public String capturingTeam = "NONE";

        public String shapeType = "CUBE";
        public int lockDurationMinutes = 0;
        public long lockedUntilTick = 0;
        public int ticketGainNeutralize = 0;
        public int ticketGainCapture = 0;

        // РќРћР’РћР•: РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅС‹Рµ Р·РѕРЅС‹, РєРѕС‚РѕСЂС‹Рµ СЃС‡РёС‚Р°СЋС‚СЃСЏ С‚РѕР№ Р¶Рµ СЃР°РјРѕР№ С‚РѕС‡РєРѕР№ (С‚РѕР»СЊРєРѕ XYZ РѕС‚Р»РёС‡Р°РµС‚СЃСЏ)
        public List<AABB> linkedAreas = new ArrayList<>();

        public CapturePoint(String name, AABB area, int bp, int rp, int time, int penalty, int deduct, String shape, int lockMin, int gainNeut, int gainCap) {
            this.name = name; this.area = area; this.bluePriority = bp; this.redPriority = rp; this.captureTimeMinutes = time; this.ticketPenalty = penalty; this.captureDeduction = deduct;
            this.shapeType = shape;
            this.lockDurationMinutes = lockMin;
            this.ticketGainNeutralize = gainNeut;
            this.ticketGainCapture = gainCap;
        }

        // РЎСЂР°РІРЅРµРЅРёРµ С…Р°СЂР°РєС‚РµСЂРёСЃС‚РёРє Р‘Р•Р— СѓС‡С‘С‚Р° РёРјРµРЅРё Рё РєРѕРѕСЂРґРёРЅР°С‚ вЂ” РїРѕ СЌС‚РѕРјСѓ РїСЂРёР·РЅР°РєСѓ СЂРµС€Р°РµРј, СЃР»РёРІР°С‚СЊ Р·РѕРЅС‹ РёР»Рё РЅРµС‚
        public boolean sameCharacteristics(int bp, int rp, int time, int penalty, int deduct, String shape, int lockMin, int gainNeut, int gainCap) {
            return this.bluePriority == bp && this.redPriority == rp && this.captureTimeMinutes == time
                    && this.ticketPenalty == penalty && this.captureDeduction == deduct
                    && this.shapeType.equalsIgnoreCase(shape) && this.lockDurationMinutes == lockMin
                    && this.ticketGainNeutralize == gainNeut && this.ticketGainCapture == gainCap;
        }

        public void addLinkedArea(AABB newArea) {
            linkedAreas.add(newArea);
        }

        // Р’СЃРµ Р·РѕРЅС‹ СЌС‚РѕР№ С‚РѕС‡РєРё (РѕСЃРЅРѕРІРЅР°СЏ + СЃРІСЏР·Р°РЅРЅС‹Рµ) вЂ” РјРѕР¶РЅРѕ РґРѕР±Р°РІР»СЏС‚СЊ СЃРєРѕР»СЊРєРѕ СѓРіРѕРґРЅРѕ
        public List<AABB> getAllAreas() {
            List<AABB> all = new ArrayList<>(linkedAreas.size() + 1);
            all.add(area);
            all.addAll(linkedAreas);
            return all;
        }

        // РћР±С‰РёР№ bounding box РІСЃРµС… Р·РѕРЅ вЂ” РґР»СЏ Р±С‹СЃС‚СЂРѕРіРѕ (broad-phase) РїРѕРёСЃРєР° СЃСѓС‰РЅРѕСЃС‚РµР№
        public AABB getBoundingBox() {
            AABB box = area;
            for (AABB a : linkedAreas) box = box.minmax(a);
            return box;
        }

        public boolean intersectsAny(AABB box) {
            if (box.intersects(area)) return true;
            for (AABB a : linkedAreas) if (box.intersects(a)) return true;
            return false;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putDouble("minX", area.minX); tag.putDouble("minY", area.minY); tag.putDouble("minZ", area.minZ);
            tag.putDouble("maxX", area.maxX); tag.putDouble("maxY", area.maxY); tag.putDouble("maxZ", area.maxZ);
            tag.putInt("BluePriority", bluePriority); tag.putInt("RedPriority", redPriority); tag.putInt("Time", captureTimeMinutes);
            tag.putInt("Penalty", ticketPenalty); tag.putInt("Deduct", captureDeduction);
            tag.putString("Owner", owner); tag.putFloat("Progress", progress); tag.putString("CapturingTeam", capturingTeam);
            tag.putString("Shape", shapeType);
            tag.putInt("LockMin", lockDurationMinutes);
            tag.putLong("LockedUntil", lockedUntilTick);
            tag.putInt("GainNeut", ticketGainNeutralize);
            tag.putInt("GainCap", ticketGainCapture);

            // РќРћР’РћР•: СЃРѕС…СЂР°РЅСЏРµРј СЃРІСЏР·Р°РЅРЅС‹Рµ Р·РѕРЅС‹
            ListTag linkedList = new ListTag();
            for (AABB a : linkedAreas) {
                CompoundTag aTag = new CompoundTag();
                aTag.putDouble("minX", a.minX); aTag.putDouble("minY", a.minY); aTag.putDouble("minZ", a.minZ);
                aTag.putDouble("maxX", a.maxX); aTag.putDouble("maxY", a.maxY); aTag.putDouble("maxZ", a.maxZ);
                linkedList.add(aTag);
            }
            tag.put("LinkedAreas", linkedList);
            return tag;
        }

        public static CapturePoint load(CompoundTag tag) {
            AABB area = new AABB(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"), tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ"));
            CapturePoint point = new CapturePoint(
                    tag.getString("Name"), area,
                    tag.getInt("BluePriority"), tag.getInt("RedPriority"),
                    tag.getInt("Time"), tag.getInt("Penalty"),
                    tag.getInt("Deduct"),
                    tag.contains("Shape") ? tag.getString("Shape") : "CUBE",
                    tag.contains("LockMin") ? tag.getInt("LockMin") : 0,
                    tag.contains("GainNeut") ? tag.getInt("GainNeut") : 0,
                    tag.contains("GainCap") ? tag.getInt("GainCap") : 0
            );
            if (tag.contains("Owner")) point.owner = tag.getString("Owner");
            if (tag.contains("Progress")) point.progress = tag.getFloat("Progress");
            if (tag.contains("CapturingTeam")) point.capturingTeam = tag.getString("CapturingTeam");
            if (tag.contains("LockedUntil")) point.lockedUntilTick = tag.getLong("LockedUntil");

            // РќРћР’РћР•: С‡РёС‚Р°РµРј СЃРІСЏР·Р°РЅРЅС‹Рµ Р·РѕРЅС‹
            if (tag.contains("LinkedAreas")) {
                ListTag linkedList = tag.getList("LinkedAreas", Tag.TAG_COMPOUND);
                for (int i = 0; i < linkedList.size(); i++) {
                    CompoundTag aTag = linkedList.getCompound(i);
                    point.linkedAreas.add(new AABB(
                            aTag.getDouble("minX"), aTag.getDouble("minY"), aTag.getDouble("minZ"),
                            aTag.getDouble("maxX"), aTag.getDouble("maxY"), aTag.getDouble("maxZ")
                    ));
                }
            }
            return point;
        }

        private boolean isInsideArea(net.minecraft.world.phys.Vec3 pos, AABB a) {
            if ("CYLINDER".equalsIgnoreCase(shapeType)) {
                double centerX = (a.minX + a.maxX) / 2.0;
                double centerZ = (a.minZ + a.maxZ) / 2.0;
                double radius = (a.maxX - a.minX) / 2.0;
                double dx = pos.x - centerX;
                double dz = pos.z - centerZ;
                boolean inCircle = (dx * dx + dz * dz) <= (radius * radius);
                boolean inHeight = (pos.y >= a.minY && pos.y <= a.maxY);
                return inCircle && inHeight;
            }
            return a.contains(pos);
        }

        // РўРµРїРµСЂСЊ РїСЂРѕРІРµСЂСЏРµС‚ РІС…РѕР¶РґРµРЅРёРµ Р’Рћ Р’РЎР• Р·РѕРЅС‹ С‚РѕС‡РєРё (РѕСЃРЅРѕРІРЅСѓСЋ + СЃРІСЏР·Р°РЅРЅС‹Рµ)
        public boolean isInside(net.minecraft.world.phys.Vec3 pos) {
            if (isInsideArea(pos, area)) return true;
            for (AABB a : linkedAreas) {
                if (isInsideArea(pos, a)) return true;
            }
            return false;
        }
    }
    public static class MainProtectionZone {
        public String team;
        public String shape;
        public AABB area;

        public MainProtectionZone(String team, String shape, AABB area) {
            this.team = team;
            this.shape = shape;
            this.area = area;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Team", team);
            tag.putString("Shape", shape);
            tag.putDouble("minX", area.minX); tag.putDouble("minY", area.minY); tag.putDouble("minZ", area.minZ);
            tag.putDouble("maxX", area.maxX); tag.putDouble("maxY", area.maxY); tag.putDouble("maxZ", area.maxZ);
            return tag;
        }

        public static MainProtectionZone load(CompoundTag tag) {
            AABB aabb = new AABB(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"),
                    tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ"));
            return new MainProtectionZone(tag.getString("Team"), tag.getString("Shape"), aabb);
        }

        public boolean isInside(net.minecraft.world.phys.Vec3 pos) {
            if ("CYLINDER".equalsIgnoreCase(shape)) {
                double centerX = (area.minX + area.maxX) / 2.0;
                double centerZ = (area.minZ + area.maxZ) / 2.0;
                double radius = (area.maxX - area.minX) / 2.0;
                double dx = pos.x - centerX;
                double dz = pos.z - centerZ;
                return (dx * dx + dz * dz) <= (radius * radius) && pos.y >= area.minY && pos.y <= area.maxY;
            }
            return area.contains(pos);
        }
    }
    public static class LobbyZone {
        public String shape;
        public AABB area;

        public LobbyZone(String shape, AABB area) {
            this.shape = shape;
            this.area = area;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Shape", shape);
            tag.putDouble("minX", area.minX); tag.putDouble("minY", area.minY); tag.putDouble("minZ", area.minZ);
            tag.putDouble("maxX", area.maxX); tag.putDouble("maxY", area.maxY); tag.putDouble("maxZ", area.maxZ);
            return tag;
        }

        public static LobbyZone load(CompoundTag tag) {
            AABB aabb = new AABB(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"),
                    tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ"));
            return new LobbyZone(tag.getString("Shape"), aabb);
        }

        public boolean isInside(net.minecraft.world.phys.Vec3 pos) {
            if ("CYLINDER".equalsIgnoreCase(shape)) {
                double centerX = (area.minX + area.maxX) / 2.0;
                double centerZ = (area.minZ + area.maxZ) / 2.0;
                double radius = (area.maxX - area.minX) / 2.0;
                double dx = pos.x - centerX;
                double dz = pos.z - centerZ;
                return (dx * dx + dz * dz) <= (radius * radius) && pos.y >= area.minY && pos.y <= area.maxY;
            }
            return area.contains(pos);
        }
    }

    public BlockPos lobbyCenter = null;
    public List<LobbyZone> lobbyZones = new ArrayList<>();
    public List<MainProtectionZone> mainZones = new ArrayList<>();
    // === РќРћР’Р«Р™ Р’РќРЈРўР Р•РќРќРР™ РљР›РђРЎРЎ Р”Р›РЇ РљРРўРћР’ ===
    public static class KitInfo {
        public String name;
        public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> inventory = net.minecraft.core.NonNullList.withSize(49, net.minecraft.world.item.ItemStack.EMPTY);
        public boolean[] resupplyFlags = new boolean[49];
        public boolean[] saveNbtFlags = new boolean[49];
        public boolean isLeaderOnly = false;
        public int maxPerTeam = -1; // -1 = Р±РµСЃРєРѕРЅРµС‡РЅРѕ, 0 = РІС‹РєР»СЋС‡РµРЅ
        public int maxPerSquad = -1;
        public int minSquadPlayers = 0;

        // === РђР›Р¬РўР•Р РќРђРўРР’РќР«Р™ РљРРў (Р’РўРћР РђРЇ Р’Р•Р РЎРРЇ Р—РђР“Р РЈР—РљР) ===
        // Р•СЃР»Рё hasAlt == true Рё altKit != null вЂ” Сѓ РёРіСЂРѕРєРѕРІ РїРѕСЏРІР»СЏРµС‚СЃСЏ РІС‹Р±РѕСЂ STANDARD/ALTERNATIVE
        // РїСЂРё РІС‹Р±РѕСЂРµ РєР»Р°СЃСЃР°. РћРіСЂР°РЅРёС‡РµРЅРёСЏ (maxPerTeam/maxPerSquad/minSquadPlayers/isLeaderOnly)
        // РІСЃРµРіРґР° Р±РµСЂСѓС‚СЃСЏ РёР· РЎРўРђРќР”РђР РўРќРћР“Рћ (Р±Р°Р·РѕРІРѕРіРѕ) KitInfo вЂ” РѕРЅРё РѕР±С‰РёРµ РґР»СЏ РѕР±РµРёС… РІРµСЂСЃРёР№ РєР»Р°СЃСЃР°,
        // С‚Р°Рє РєР°Рє Р°Р»СЊС‚РµСЂРЅР°С‚РёРІР° СЌС‚Рѕ РїСЂРѕСЃС‚Рѕ РґСЂСѓРіРѕР№ РЅР°Р±РѕСЂ СЃРЅР°СЂСЏР¶РµРЅРёСЏ РІРЅСѓС‚СЂРё С‚РѕРіРѕ Р¶Рµ РєР»Р°СЃСЃР°.
        public boolean hasAlt = false;
        public KitInfo altKit = null;

        public String displayName = ""; // РїСѓСЃС‚Рѕ = РїРѕРєР°Р·С‹РІР°РµРј СЃС‚Р°РЅРґР°СЂС‚РЅРѕРµ kitName

        public KitInfo(String name) { this.name = name; }

        /** Р’РѕР·РІСЂР°С‰Р°РµС‚ (СЃРѕР·РґР°РІР°СЏ РїСЂРё РЅРµРѕР±С…РѕРґРёРјРѕСЃС‚Рё) Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРЅС‹Р№ РєРёС‚. РўРѕР»СЊРєРѕ РґР»СЏ Р±Р°Р·РѕРІРѕРіРѕ KitInfo. */
        public KitInfo getOrCreateAlt() {
            if (altKit == null) {
                altKit = new KitInfo(this.name);
            }
            return altKit;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Name", name);
            t.putString("DisplayName", displayName);
            t.putBoolean("LeaderOnly", isLeaderOnly);
            t.putInt("MaxTeam", maxPerTeam);
            t.putInt("MaxSquad", maxPerSquad);
            t.putInt("MinSquadPlayers", minSquadPlayers);
            ListTag items = new ListTag();
            for (int i = 0; i < 49; i++) {
                if (!inventory.get(i).isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putByte("Slot", (byte) i);
                    itemTag.putBoolean("Resupply", resupplyFlags[i]);
                    itemTag.putBoolean("SaveNbt", saveNbtFlags[i]);
                    inventory.get(i).save(itemTag); // РЎРѕС…СЂР°РЅСЏРµС‚ РїСЂРµРґРјРµС‚ РІРјРµСЃС‚Рµ СЃ NBT
                    items.add(itemTag);
                }
            }
            t.put("Items", items);

            // === РЎРћРҐР РђРќРЇР•Рњ РђР›Р¬РўР•Р РќРђРўРР’РќРЈР® Р’Р•Р РЎРР® (РµСЃР»Рё РµСЃС‚СЊ) ===
            t.putBoolean("HasAlt", hasAlt);
            if (altKit != null) {
                t.put("AltKit", altKit.save());
            }
            return t;
        }

        public static KitInfo load(CompoundTag t) {
            KitInfo k = new KitInfo(t.getString("Name"));
            k.displayName = t.getString("DisplayName");
            k.isLeaderOnly = t.getBoolean("LeaderOnly");
            k.maxPerTeam = t.getInt("MaxTeam");
            k.maxPerSquad = t.getInt("MaxSquad");
            if (t.contains("MinSquadPlayers")) {
                k.minSquadPlayers = t.getInt("MinSquadPlayers");
            }
            ListTag items = t.getList("Items", 10);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot >= 0 && slot < 49) {
                    k.inventory.set(slot, net.minecraft.world.item.ItemStack.of(itemTag));
                    k.resupplyFlags[slot] = itemTag.getBoolean("Resupply");
                    k.saveNbtFlags[slot] = itemTag.getBoolean("SaveNbt");
                }
            }

            // === Р—РђР“Р РЈР–РђР•Рњ РђР›Р¬РўР•Р РќРђРўРР’РќРЈР® Р’Р•Р РЎРР® (РµСЃР»Рё РµСЃС‚СЊ) ===
            k.hasAlt = t.getBoolean("HasAlt");
            if (t.contains("AltKit")) {
                k.altKit = KitInfo.load(t.getCompound("AltKit"));
            }
            return k;
        }
    }

    /**
     * Р’РѕР·РІСЂР°С‰Р°РµС‚ РЅСѓР¶РЅС‹Р№ РІР°СЂРёР°РЅС‚ РєРёС‚Р° (СЃС‚Р°РЅРґР°СЂС‚РЅС‹Р№ РёР»Рё Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРЅС‹Р№) РїРѕ РёРјРµРЅРё РєР»Р°СЃСЃР°.
     * Р•СЃР»Рё Р·Р°РїСЂРѕС€РµРЅ Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРЅС‹Р№, РЅРѕ РµРіРѕ РЅРµС‚ (hasAlt=false РёР»Рё altKit=null) вЂ” РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ СЃС‚Р°РЅРґР°СЂС‚РЅС‹Р№.
     */
    public KitInfo getKitVariant(String team, String kitName, boolean isAlt) {
        Map<String, KitInfo> source = activeKits(team);
        KitInfo base = source.get(kitName);
        if (base == null) return null;
        if (isAlt && base.hasAlt && base.altKit != null) return base.altKit;
        return base;
    }

    public boolean voteActive = false;
    public int voteTimer = 0;
    public boolean blueReady = false;
    public boolean redReady = false;
    public Map<UUID, Boolean> votes = new HashMap<>();
    // Р’РЅСѓС‚СЂРё AASWorldData.java
    public static class VehicleRecord {
        public UUID uuid;
        public String team;
        public String type;
        public double x, y, z;
        public float yaw;
        public BlockPos spawnerPos;
        public int respawnTimeSeconds = 60;

        public boolean autoReturnEnabled = false;
        public int autoReturnTimeSeconds = 60;
        public boolean autoReturnDestroy = false;
        public long emptySinceTick = -1;

        // === Р’РћРў Р­РўРћРў РљРћРќРЎРўР РЈРљРўРћР  РќРЈР–РќРћ Р’Р•Р РќРЈРўР¬ ===
        public VehicleRecord(UUID uuid, String team, String type, double x, double y, double z, float yaw, BlockPos spawnerPos) {
            this(uuid, team, type, x, y, z, yaw, spawnerPos, false, 60, false, 60);
        }

        public VehicleRecord(UUID uuid, String team, String type, double x, double y, double z, float yaw, BlockPos spawnerPos,
                             boolean autoReturnEnabled, int autoReturnTimeSeconds, boolean autoReturnDestroy, int respawnTimeSeconds) {
            this.uuid = uuid;
            this.team = team;
            this.type = type;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw;
            this.spawnerPos = spawnerPos;
            this.autoReturnEnabled = autoReturnEnabled;
            this.autoReturnTimeSeconds = autoReturnTimeSeconds;
            this.autoReturnDestroy = autoReturnDestroy;
            this.respawnTimeSeconds = respawnTimeSeconds;
        }

        public VehicleRecord(UUID uuid, String team, String type, double x, double y, double z, float yaw, BlockPos spawnerPos,
                             boolean autoReturnEnabled, int autoReturnTimeSeconds, boolean autoReturnDestroy) {
            this(uuid, team, type, x, y, z, yaw, spawnerPos, autoReturnEnabled, autoReturnTimeSeconds, autoReturnDestroy, 60);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("UUID", uuid);
            tag.putString("Team", team);
            tag.putString("Type", type);
            tag.putDouble("X", x); tag.putDouble("Y", y); tag.putDouble("Z", z);
            tag.putFloat("Yaw", yaw);
            if (spawnerPos != null) tag.putLong("SpawnerPos", spawnerPos.asLong());
            tag.putBoolean("AutoReturnEnabled", autoReturnEnabled);
            tag.putInt("AutoReturnTime", autoReturnTimeSeconds);
            tag.putBoolean("AutoReturnDestroy", autoReturnDestroy);
            tag.putInt("RespawnTimeSeconds", respawnTimeSeconds); // РќРћР’РћР•
            return tag;
        }

        public static VehicleRecord load(CompoundTag tag) {
            BlockPos sPos = tag.contains("SpawnerPos") ? BlockPos.of(tag.getLong("SpawnerPos")) : null;
            return new VehicleRecord(tag.getUUID("UUID"), tag.getString("Team"), tag.getString("Type"),
                    tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"), tag.getFloat("Yaw"), sPos,
                    tag.getBoolean("AutoReturnEnabled"),
                    tag.contains("AutoReturnTime") ? tag.getInt("AutoReturnTime") : 60,
                    tag.getBoolean("AutoReturnDestroy"),
                    tag.contains("RespawnTimeSeconds") ? tag.getInt("RespawnTimeSeconds") : 60); // РќРћР’РћР•
        }
    }
    public Map<BlockPos, Long> pendingSpawnerRespawns = new HashMap<>();
}

