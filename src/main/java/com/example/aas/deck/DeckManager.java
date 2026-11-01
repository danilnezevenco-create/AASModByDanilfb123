package com.example.aas.deck;

import com.example.aas.config.AASConfig;
import com.example.aas.events.GameLogicEvents;
import com.example.aas.network.PacketDeckInfo;
import com.example.aas.network.PacketHandler;
import com.example.aas.world.AASWorldData;
import com.example.aas.world.VehicleSpawnerRegistry;
import com.example.aas.block.VehicleSpawnerBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.example.aas.util.VehicleCatalog;

/**
 * Server side of "preparation + deck choice":
 *   /aas prepare start -> TEAM_SELECT (10 s) -> DECK_VOTE (30 s) -> VERSUS (5 s) -> the usual "vote to start" -> countdown + match.
 * All state lives in AASWorldData (saved), the clients only display what PacketSyncGameData tells them.
 */
public final class DeckManager {
    private DeckManager() {}

    public static final int TEAM_SELECT_TICKS = 10 * 20;
    public static final int DECK_VOTE_TICKS = 30 * 20;
    public static final int VERSUS_TICKS = 5 * 20;

    public enum StartResult { OK, GAME_RUNNING, ALREADY_PREPARING }
    public enum CancelResult { OK, NOTHING }

    // ------------------------------------------------------------------ helpers

    /** Creative players and spectators are observers: no team screen, no vote, no VS screen for them. */
    public static boolean isParticipant(ServerPlayer p) {
        return !p.isSpectator() && !p.isCreative();
    }

    /** "BLUE", "RED" or null. */
    public static String teamOf(ServerPlayer p) {
        if (p.getTeam() == null) return null;
        String n = p.getTeam().getName();
        if (n.equalsIgnoreCase("Blue")) return "BLUE";
        if (n.equalsIgnoreCase("Red")) return "RED";
        return null;
    }

    private static void sync(ServerLevel level, AASWorldData data) {
        PacketHandler.sendToAllClients(level, data);
    }

    // ------------------------------------------------------------------ /aas prepare start | cancel

    public static StartResult start(ServerLevel level) {
        AASWorldData data = AASWorldData.get(level);
        if (data.isGameStarted || data.countdownActive) return StartResult.GAME_RUNNING;
        if (data.prepPhase != DeckPhase.NONE) return StartResult.ALREADY_PREPARING;

        data.blueDeck = null;
        data.redDeck = null;
        data.blueDeckVotes.clear();
        data.redDeckVotes.clear();
        // the classic "press to be ready" pre-match vote must not run during the preparation
        data.voteActive = false;
        data.blueReady = false;
        data.redReady = false;

        // 0. creative players become survival players (spectators stay as they are)
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (p.gameMode.getGameModeForPlayer() == GameType.CREATIVE) p.setGameMode(GameType.SURVIVAL);
        }

        // 1. everybody who has a team goes to the main base of that team (teamless players stay where they are, in the lobby)
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            String team = teamOf(p);
            if (team != null) TeamBalance.teleportToMain(p, data, team);
        }

        // 2. the lobby zone stops pulling players back (lobbyZones / lobbyCenter stay untouched)
        data.lobbyDisabled = true;

        // 3. full health and food
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(20.0F);
        }

        // 4. team selection for those without a team, then (5.) the deck vote
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("aas.msg.prepare_started").withStyle(ChatFormatting.GOLD));
        }
        if (unassignedPlayers(level).isEmpty()) {
            beginDeckVote(level, data);
        } else {
            data.prepPhase = DeckPhase.TEAM_SELECT;
            data.prepTimer = TEAM_SELECT_TICKS;
            data.setDirty();
            sync(level, data);
        }
        VehicleSpawnerBlockEntity.recheckAll(level);   // check #2
        return StartResult.OK;
    }

    public static CancelResult cancel(ServerLevel level) {
        AASWorldData data = AASWorldData.get(level);
        boolean wasPreparing = data.prepPhase != DeckPhase.NONE;
        // the countdown that the preparation started (decks chosen, match not running yet)
        boolean prepCountdown = !data.isGameStarted && data.countdownActive && (data.blueDeck != null || data.redDeck != null);
        // the "vote to start" that follows the deck vote (decks are chosen, match not started yet)
        boolean prepStartVote = !data.isGameStarted && data.voteActive && (data.blueDeck != null || data.redDeck != null);
        if (!wasPreparing && !prepCountdown && !prepStartVote) return CancelResult.NOTHING;

        if (prepStartVote) {
            data.voteActive = false;
            data.votes.clear();
            data.blueReady = false;
            data.redReady = false;
        }

        if (prepCountdown) GameLogicEvents.cancelCountdown(level);
        resetPreparation(data);
        data.lobbyDisabled = false;
        data.setDirty();
        sync(level, data);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("aas.msg.prepare_cancelled").withStyle(ChatFormatting.RED));
        }
        return CancelResult.OK;
    }

    private static void resetPreparation(AASWorldData data) {
        data.prepPhase = DeckPhase.NONE;
        data.prepTimer = 0;
        data.blueDeck = null;
        data.redDeck = null;
        data.blueDeckVotes.clear();
        data.redDeckVotes.clear();
    }

    // ------------------------------------------------------------------ per-tick state machine

    public static void tick(ServerLevel level, AASWorldData data, int globalTick) {
        DeckPhase phase = data.prepPhase;
        if (phase == DeckPhase.NONE) return;

        // somebody started the match by hand (/aas gamestart true) - the preparation is over
        if (data.isGameStarted) {
            data.prepPhase = DeckPhase.NONE;
            data.prepTimer = 0;
            data.setDirty();
            sync(level, data);
            return;
        }

        data.prepTimer--;
        boolean everySecond = data.prepTimer > 0 && data.prepTimer % 20 == 0;

        switch (phase) {
            case TEAM_SELECT -> {
                List<ServerPlayer> unassigned = unassignedPlayers(level);
                if (unassigned.isEmpty() || data.prepTimer <= 0) {
                    autoAssign(level, data, unassigned);
                    beginDeckVote(level, data);
                } else if (everySecond) {
                    data.setDirty();
                    sync(level, data);
                }
            }
            case DECK_VOTE -> {
                if (everySecond) {
                    // players who joined during the vote are put into a team, so they can vote too
                    autoAssign(level, data, unassignedPlayers(level));
                }
                if (data.prepTimer <= 0) {
                    finishVote(level, data);
                } else if (everySecond) {
                    data.setDirty();
                    sync(level, data);
                }
            }
            case VERSUS -> {
                if (data.prepTimer <= 0) finishPreparation(level, data);
                else if (everySecond) {
                    data.setDirty();
                    sync(level, data);
                }
            }
            default -> { }
        }
    }

    private static List<ServerPlayer> unassignedPlayers(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (isParticipant(p) && teamOf(p) == null) out.add(p);
        }
        return out;
    }

    /** Smaller team first, a coin flip on equality. Uses the same TeamBalance as PacketTeamSelect. */
    private static void autoAssign(ServerLevel level, AASWorldData data, List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        MinecraftServer server = level.getServer();
        for (ServerPlayer p : shuffled) {
            String team = TeamBalance.pickTeam(server, p);
            TeamBalance.assign(p, team, data, true);
            boolean blue = team.equals("BLUE");
            p.sendSystemMessage(Component.translatable("aas.msg.joined_team", TeamBalance.displayName(data, blue))
                    .withStyle(blue ? ChatFormatting.BLUE : ChatFormatting.RED));
        }
    }

    private static void beginDeckVote(ServerLevel level, AASWorldData data) {
        data.prepPhase = DeckPhase.DECK_VOTE;
        data.prepTimer = DECK_VOTE_TICKS;
        data.blueDeckVotes.clear();
        data.redDeckVotes.clear();
        data.setDirty();
        // nothing to vote about when both teams have one deck only
        if (data.enabledDecks("BLUE").size() <= 1 && data.enabledDecks("RED").size() <= 1) {
            finishVote(level, data);
            return;
        }
        sync(level, data);
    }

    private static void finishVote(ServerLevel level, AASWorldData data) {
        data.blueDeck = pickWinner(level, data, "BLUE");
        data.redDeck = pickWinner(level, data, "RED");
        data.prepPhase = DeckPhase.VERSUS;
        data.prepTimer = VERSUS_TICKS;
        data.setDirty();
        sync(level, data);

        for (ServerPlayer p : level.players()) {
            String team = teamOf(p);
            if (team == null) continue;
            boolean blue = team.equals("BLUE");
            DeckType d = blue ? data.blueDeck : data.redDeck;
            if (d == null) continue;
            p.sendSystemMessage(Component.translatable("aas.msg.prepare_deck_result",
                            Component.translatable(DeckRegistry.nameKey(blue ? data.blueFaction : data.redFaction, blue, d)))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Most votes wins; a tie or no votes at all = random among the leaders (all enabled decks when nobody voted). */
    private static DeckType pickWinner(ServerLevel level, AASWorldData data, String team) {
        List<DeckType> enabled = data.enabledDecks(team);
        int[] votes = countVotes(data, team);
        int max = 0;
        for (DeckType d : enabled) max = Math.max(max, votes[d.ordinal()]);
        List<DeckType> leaders = new ArrayList<>();
        for (DeckType d : enabled) if (votes[d.ordinal()] == max) leaders.add(d);
        if (leaders.isEmpty()) return DeckType.COMBINED;
        return leaders.get(level.getRandom().nextInt(leaders.size()));
    }

    /** The VS screen is over: the usual "vote to start" (F9 / F10) begins; the countdown starts when both teams are ready. */
    private static void finishPreparation(ServerLevel level, AASWorldData data) {
        data.prepPhase = DeckPhase.NONE;
        data.prepTimer = 0;
        VehicleSpawnerBlockEntity.recheckAll(level);   // check #3
        data.playedBlueSiren = false;
        data.playedRedSiren = false;
        data.setDirty();

        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            com.example.aas.events.StatsHandler.resetStats(p);
        }
        com.example.aas.events.OfflineStats.resetAll();

        // same as "/aas votestart true": GameLogicEvents starts the countdown when both teams are ready or the timer ends
        data.voteActive = true;
        data.voteTimer = AASConfig.VOTE_AUTO_START_TIME.get() * 60;
        data.votes.clear();
        data.blueReady = false;
        data.redReady = false;
        data.setDirty();
        sync(level, data);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable("aas.msg.prepare_vote_start").withStyle(ChatFormatting.GREEN));
        }
    }

    // ------------------------------------------------------------------ votes

    /** A player clicked a deck card. Can be changed until the timer is over. */
    public static void handleVote(ServerPlayer player, int deckOrdinal) {
        ServerLevel level = player.serverLevel();
        AASWorldData data = AASWorldData.get(level);
        if (data.prepPhase != DeckPhase.DECK_VOTE) return;
        String team = teamOf(player);
        DeckType deck = DeckType.byOrdinal(deckOrdinal);
        if (team == null || deck == null || !isParticipant(player)) return;
        if (!data.paramsFor(team, deck).enabled) return;

        Map<UUID, DeckType> votes = team.equals("BLUE") ? data.blueDeckVotes : data.redDeckVotes;
        votes.put(player.getUUID(), deck);
        data.setDirty();
        sync(level, data);
    }

    /**
     * Vote counters of one team. Only players who are online, still in that team and still participants are counted
     * (logged out players do not break the count and do not keep their vote).
     */
    public static int[] countVotes(AASWorldData data, String team) {
        int[] out = new int[DeckType.COUNT];
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return out;
        boolean blue = team.equalsIgnoreCase("BLUE");
        Map<UUID, DeckType> votes = blue ? data.blueDeckVotes : data.redDeckVotes;
        for (Map.Entry<UUID, DeckType> e : votes.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !isParticipant(p)) continue;
            String t = teamOf(p);
            if (t == null || !t.equals(blue ? "BLUE" : "RED")) continue;
            if (!data.paramsFor(t, e.getValue()).enabled) continue;
            out[e.getValue().ordinal()]++;
        }
        return out;
    }

    // ------------------------------------------------------------------ match start / end hooks

    /**
     * Called from GameLogicEvents exactly when isGameStarted becomes true (both /aas gamestart true and the automatic
     * start after the preparation). Applies the hub limit and the starting tickets of the chosen decks.
     * No chosen deck (match started without preparation) = nothing is touched.
     */
    public static void onMatchStart(ServerLevel level, AASWorldData data) {
        DeckType b = data.blueDeck;
        DeckType r = data.redDeck;
        if (b == null && r == null) return;

        if (!data.hubsOverridden) {
            data.prevMaxBlueHubs = data.maxBlueHubs;
            data.prevMaxRedHubs = data.maxRedHubs;
            data.hubsOverridden = true;
        }
        if (b != null) {
            DeckParams p = data.paramsFor("BLUE", b);
            data.maxBlueHubs = p.hubLimit;
            data.blueTickets = p.tickets;
        }
        if (r != null) {
            DeckParams p = data.paramsFor("RED", r);
            data.maxRedHubs = p.hubLimit;
            data.redTickets = p.tickets;
        }
        data.playedBlueSiren = false;
        data.playedRedSiren = false;
        data.setDirty();
        // the caller sends the sync packet right after this (sendSyncPacket in the countdown block)
    }

    /**
     * Called when the match stops (victory, "/aas gamestart false"): restores the hub limits, forgets the chosen decks,
     * re-enables the lobby zone. The spawner redstone blocks are removed by the spawner block entities themselves.
     */
    public static void onMatchEnd(ServerLevel level, AASWorldData data) {
        if (data.hubsOverridden) {
            data.maxBlueHubs = data.prevMaxBlueHubs;
            data.maxRedHubs = data.prevMaxRedHubs;
            data.hubsOverridden = false;
        }
        resetPreparation(data);
        data.lobbyDisabled = false;
        data.setDirty();
        sync(level, data);
    }

    // ------------------------------------------------------------------ data for the "?" window

    /** Everything the "?" window shows for one deck of one team. Computed on the server, the client never reads AASWorldData. */
    public static PacketDeckInfo buildInfo(ServerLevel level, AASWorldData data, String team, DeckType deck) {
        DeckParams p = data.paramsFor(team, deck);

        // vehicle id -> {count, penalty, marker type}
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String> markers = new HashMap<>();
        VehicleSpawnerRegistry registry = VehicleSpawnerRegistry.get(level);
        for (Map.Entry<BlockPos, VehicleSpawnerRegistry.Snapshot> en : registry.copy().entrySet()) {
            VehicleSpawnerRegistry.Snapshot s = en.getValue();
            if (level.isLoaded(en.getKey())) {
                BlockEntity be = level.getBlockEntity(en.getKey());
                if (be instanceof VehicleSpawnerBlockEntity spawner) s = VehicleSpawnerRegistry.Snapshot.of(spawner);
            }
            if (!s.team().equalsIgnoreCase(team)) continue;
            String id = s.vehicleId(deck);
            if (id.isEmpty()) continue;
            int[] c = counts.computeIfAbsent(id, k -> new int[2]);
            c[0]++;
            c[1] = Math.max(c[1], s.penalty(deck));
            String mt = s.markerType(deck);
            markers.putIfAbsent(id, mt == null ? "" : mt);
        }
        List<PacketDeckInfo.VehicleLine> lines = new ArrayList<>();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
            String key = e.getKey();
            if (rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
                if (type != null) key = type.getDescriptionId();
            }
            lines.add(new PacketDeckInfo.VehicleLine(key, e.getValue()[0], markers.get(e.getKey()), e.getValue()[1]));
        }
        // same order as the vehicle list: by category, then by name key
        lines.sort((x, y) -> {
            int c = Integer.compare(VehicleCatalog.resolve(null, x.markerType()).ordinal(),
                    VehicleCatalog.resolve(null, y.markerType()).ordinal());
            return c != 0 ? c : x.nameKey().compareTo(y.nameKey());
        });

        AASWorldData.KitInfo aa = data.kitsFor(team, deck).get("Anti_air");
        AASWorldData.KitInfo hat = data.kitsFor(team, deck).get("HAT");
        return new PacketDeckInfo(deck.ordinal(), p.enabled, p.hubLimit, p.crateBonus, p.tickets,
                aa == null ? -1 : aa.maxPerTeam, hat == null ? -1 : hat.maxPerTeam,
                AASConfig.SUPPLY_CRATE_MATERIALS.get() + p.crateBonus, lines);
    }

    // ------------------------------------------------------------------ admin: which deck is being edited

    /** The deck the admin currently edits in the kit / deck menus (kept on the server, so every kit packet knows it). */
    public static DeckType editDeckOf(ServerPlayer p) {
        DeckType d = DeckType.fromId(p.getPersistentData().getString("AAS_EditDeck"));
        return d == null ? DeckType.COMBINED : d;
    }

    public static void setEditDeck(ServerPlayer p, DeckType d) {
        p.getPersistentData().putString("AAS_EditDeck", d.id);
    }
}