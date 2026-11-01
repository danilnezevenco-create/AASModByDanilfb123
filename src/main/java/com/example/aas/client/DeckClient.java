package com.example.aas.client;

import com.example.aas.block.VehicleSpawnerBlockEntity;
import com.example.aas.client.gui.DeckVersusScreen;
import com.example.aas.client.gui.DeckVoteScreen;
import com.example.aas.client.gui.DeckParamsScreen;
import com.example.aas.client.gui.TeamSelectionScreen;
import com.example.aas.deck.DeckPhase;
import com.example.aas.deck.DeckSpawnConfig;
import com.example.aas.deck.DeckSyncPayload;
import com.example.aas.deck.DeckType;
import com.example.aas.item.SupplyTruckMarkerItem;
import com.example.aas.item.VehicleMarkerItem;
import com.example.aas.network.PacketDeckInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/** Client-side state of the preparation (filled from PacketSyncGameData) and the logic that opens / closes its screens. */
public final class DeckClient {
    private DeckClient() {}

    public static DeckSyncPayload state = DeckSyncPayload.EMPTY;
    private static long phaseEndMs = 0;
    private static DeckPhase lastPhase = DeckPhase.NONE;
    /** The deck this player voted for (only remembered on the client, the server counts the real votes). */
    public static int myVote = -1;
    /** The deck the admin edits in the kit menus (the server keeps its own copy, see PacketSetEditDeck). */
    public static DeckType editDeck = DeckType.COMBINED;

    public static void onSync(DeckSyncPayload p) {
        state = p;
        phaseEndMs = System.currentTimeMillis() + p.timerTicks * 50L;
        DeckPhase phase = p.phase();
        if (phase != lastPhase) {
            if (phase == DeckPhase.DECK_VOTE) myVote = -1;
            lastPhase = phase;
        }
        refreshScreens();
    }

    /** Milliseconds left in the current phase (0 when over). */
    public static long msLeft() {
        return Math.max(0L, phaseEndMs - System.currentTimeMillis());
    }

    /** Whole seconds left in the current phase (rounded up). */
    public static int secondsLeft() {
        long ms = phaseEndMs - System.currentTimeMillis();
        return ms <= 0 ? 0 : (int) ((ms + 999) / 1000);
    }

    /** TRUE = blue, FALSE = red, null = no team. */
    public static Boolean myTeamIsBlue(LocalPlayer p) {
        if (p == null || p.getTeam() == null) return null;
        String n = p.getTeam().getName();
        if (n.equalsIgnoreCase("Blue")) return Boolean.TRUE;
        if (n.equalsIgnoreCase("Red")) return Boolean.FALSE;
        return null;
    }

    public static String factionOf(boolean blue) {
        return blue ? ClientData.BLUE_FACTION : ClientData.RED_FACTION;
    }

    private static boolean isPrepScreen(Screen s) {
        return s instanceof DeckVoteScreen || s instanceof DeckVersusScreen
                || (s instanceof TeamSelectionScreen t && t.isPrep());
    }

    private static void refreshScreens() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer pl = mc.player;
        if (pl == null) return;
        boolean participant = !pl.isCreative() && !pl.isSpectator();
        Boolean blue = myTeamIsBlue(pl);
        DeckPhase ph = state.phase();
        Screen cur = mc.screen;

        boolean wantTeam = participant && ph == DeckPhase.TEAM_SELECT && blue == null;
        boolean wantVote = participant && ph == DeckPhase.DECK_VOTE && blue != null && state.enabledCount(blue) > 1;
        boolean wantVs = participant && ph == DeckPhase.VERSUS && blue != null
                && state.chosen(true) != null && state.chosen(false) != null;

        if (wantTeam) {
            if (!(cur instanceof TeamSelectionScreen t && t.isPrep())) mc.setScreen(new TeamSelectionScreen(true));
        } else if (wantVote) {
            if (!(cur instanceof DeckVoteScreen)) mc.setScreen(new DeckVoteScreen(blue));
        } else if (wantVs) {
            if (!(cur instanceof DeckVersusScreen)) mc.setScreen(new DeckVersusScreen(blue));
        } else if (isPrepScreen(cur)) {
            mc.setScreen(null);   // setScreen does not call onClose(), so the "cannot be closed" rule is not in the way
        }
    }

    public static void onInfo(PacketDeckInfo msg) {
        Screen cur = Minecraft.getInstance().screen;
        if (cur instanceof DeckVoteScreen s) s.onInfo(msg);
        else if (cur instanceof DeckParamsScreen s) s.onInfo(msg);
    }

    /** Config shown on the spawner block (floating text): the deck the spawner's team plays with, COMBINED if none is chosen. */
    public static DeckSpawnConfig spawnerConfig(VehicleSpawnerBlockEntity be) {
        String team = be.spawnerTeam();
        DeckType deck = null;
        if (team.equalsIgnoreCase("BLUE")) deck = state.chosen(true);
        else if (team.equalsIgnoreCase("RED")) deck = state.chosen(false);
        if (deck == null) deck = DeckType.COMBINED;
        DeckSpawnConfig c = be.deckConfigs.get(deck);
        return c != null ? c : new DeckSpawnConfig();
    }
}
