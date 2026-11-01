package com.example.aas.client;

import com.example.aas.deck.DeckType;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketKitAdmin;
import com.example.aas.network.PacketKitAdminInfo;
import com.example.aas.network.PacketSetEditDeck;
import net.minecraft.client.Minecraft;

/** Client side of the kit / deck admin menu: the last PacketKitAdminInfo and small send helpers. */
public final class KitAdminClient {
    private KitAdminClient() {}

    /** Screens that want to be told when fresh admin info arrives. */
    public interface Listener {
        void onKitAdminInfo();
    }

    /** Last info the server sent (null until the first answer). */
    public static PacketKitAdminInfo info = null;

    public static void onInfo(PacketKitAdminInfo msg) {
        info = msg;
        if (Minecraft.getInstance().screen instanceof Listener l) l.onKitAdminInfo();
    }

    /** True when the kit summaries in {@link #info} belong to the deck the admin edits now. */
    public static boolean ready() {
        return info != null && info.editDeck == DeckClient.editDeck.ordinal();
    }

    public static void send(PacketKitAdmin p) {
        PacketHandler.INSTANCE.sendToServer(p);
    }

    public static void request() {
        send(PacketKitAdmin.request());
    }

    /** Tells the server which deck this client edits (all kit packets use it) and asks for fresh info. */
    public static void syncEditDeck() {
        PacketHandler.INSTANCE.sendToServer(new PacketSetEditDeck(DeckClient.editDeck.ordinal()));
        request();
    }

    public static void setEditDeck(DeckType deck) {
        DeckClient.editDeck = deck;
        syncEditDeck();
    }

    /**
     * Stores an undo snapshot on the server. Call it right BEFORE sending one of the old packets
     * that change kits / deck params (save, paste, copy deck, update params ...).
     *
     * @param team "BLUE", "RED" or "BOTH"
     * @param deck the deck that will change, null = all decks
     */
    public static void snapshot(String team, DeckType deck, boolean kits, boolean params, String label) {
        send(PacketKitAdmin.snapshot(team, deck == null ? -1 : deck.ordinal(), kits, params, label));
    }

    public static void undo() {
        send(PacketKitAdmin.undo());
    }

    public static int undoDepth() {
        return info == null ? 0 : info.undoDepth;
    }
}
