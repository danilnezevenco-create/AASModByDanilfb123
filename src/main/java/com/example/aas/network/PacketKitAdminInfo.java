package com.example.aas.network;

import com.example.aas.client.KitAdminClient;
import com.example.aas.deck.DeckManager;
import com.example.aas.deck.DeckParams;
import com.example.aas.deck.DeckType;
import com.example.aas.deck.KitUndo;
import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Server -> admin client: everything the kit / deck admin screens show.
 *  - deck params of ALL decks of both teams (index [team][deck], team 0 = BLUE, 1 = RED)
 *  - how many kits of each deck are filled
 *  - a short summary of every kit of the deck the admin edits right now
 *  - the undo history depth
 */
public class PacketKitAdminInfo {

    public record KitSummary(int items, int altItems, boolean hasAlt, boolean leader,
                             int maxTeam, int maxSquad, int minSquad, String displayName) {}

    public static final int TEAMS = 2;
    public static final String[] TEAM_IDS = {"BLUE", "RED"};

    public final int editDeck;
    public final int undoDepth;
    public final String undoLabel;
    public final boolean[][] enabled;
    public final int[][] hub;
    public final int[][] crate;
    public final int[][] tickets;
    public final int[][] filled;
    public final KitSummary[][] kits;

    public PacketKitAdminInfo(int editDeck, int undoDepth, String undoLabel,
                              boolean[][] enabled, int[][] hub, int[][] crate, int[][] tickets,
                              int[][] filled, KitSummary[][] kits) {
        this.editDeck = editDeck;
        this.undoDepth = undoDepth;
        this.undoLabel = undoLabel;
        this.enabled = enabled;
        this.hub = hub;
        this.crate = crate;
        this.tickets = tickets;
        this.filled = filled;
        this.kits = kits;
    }

    // ------------------------------------------------------------------ server side

    private static int countItems(AASWorldData.KitInfo k) {
        if (k == null) return 0;
        int n = 0;
        for (int i = 0; i < 49; i++) if (!k.inventory.get(i).isEmpty()) n++;
        return n;
    }

    public static PacketKitAdminInfo build(ServerPlayer player) {
        AASWorldData data = AASWorldData.get(player.serverLevel());
        DeckType edit = DeckManager.editDeckOf(player);
        int kitCount = AASWorldData.KIT_NAMES.length;

        boolean[][] enabled = new boolean[TEAMS][DeckType.COUNT];
        int[][] hub = new int[TEAMS][DeckType.COUNT];
        int[][] crate = new int[TEAMS][DeckType.COUNT];
        int[][] tickets = new int[TEAMS][DeckType.COUNT];
        int[][] filled = new int[TEAMS][DeckType.COUNT];
        KitSummary[][] kits = new KitSummary[TEAMS][kitCount];

        for (int t = 0; t < TEAMS; t++) {
            String team = TEAM_IDS[t];
            for (DeckType d : DeckType.VALUES) {
                DeckParams p = data.paramsFor(team, d);
                int di = d.ordinal();
                enabled[t][di] = p.enabled;
                hub[t][di] = p.hubLimit;
                crate[t][di] = p.crateBonus;
                tickets[t][di] = p.tickets;
                int f = 0;
                Map<String, AASWorldData.KitInfo> deckKits = data.kitsFor(team, d);
                for (String name : AASWorldData.KIT_NAMES) {
                    if (countItems(deckKits.get(name)) > 0) f++;
                }
                filled[t][di] = f;
            }
            Map<String, AASWorldData.KitInfo> editKits = data.kitsFor(team, edit);
            for (int i = 0; i < kitCount; i++) {
                AASWorldData.KitInfo k = editKits.get(AASWorldData.KIT_NAMES[i]);
                if (k == null) {
                    kits[t][i] = new KitSummary(0, 0, false, false, -1, -1, 0, "");
                } else {
                    String dn = k.displayName == null ? "" : k.displayName;
                    kits[t][i] = new KitSummary(countItems(k), countItems(k.altKit),
                            k.hasAlt && k.altKit != null, k.isLeaderOnly,
                            k.maxPerTeam, k.maxPerSquad, k.minSquadPlayers, dn);
                }
            }
        }
        return new PacketKitAdminInfo(edit.ordinal(), KitUndo.depth(player), KitUndo.peekLabel(player),
                enabled, hub, crate, tickets, filled, kits);
    }

    public static void send(ServerPlayer player) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), build(player));
    }

    // ------------------------------------------------------------------ network

    public static void encode(PacketKitAdminInfo m, FriendlyByteBuf b) {
        b.writeVarInt(m.editDeck);
        b.writeVarInt(m.undoDepth);
        b.writeUtf(m.undoLabel == null ? "" : m.undoLabel);
        for (int t = 0; t < TEAMS; t++) {
            for (int d = 0; d < DeckType.COUNT; d++) {
                b.writeBoolean(m.enabled[t][d]);
                b.writeVarInt(m.hub[t][d]);
                b.writeVarInt(m.crate[t][d]);
                b.writeVarInt(m.tickets[t][d]);
                b.writeVarInt(m.filled[t][d]);
            }
        }
        int kitCount = AASWorldData.KIT_NAMES.length;
        for (int t = 0; t < TEAMS; t++) {
            for (int i = 0; i < kitCount; i++) {
                KitSummary s = m.kits[t][i];
                b.writeVarInt(s.items());
                b.writeVarInt(s.altItems());
                b.writeBoolean(s.hasAlt());
                b.writeBoolean(s.leader());
                b.writeInt(s.maxTeam());
                b.writeInt(s.maxSquad());
                b.writeInt(s.minSquad());
                b.writeUtf(s.displayName() == null ? "" : s.displayName(), 64);
            }
        }
    }

    public static PacketKitAdminInfo decode(FriendlyByteBuf b) {
        int edit = b.readVarInt();
        int undo = b.readVarInt();
        String label = b.readUtf();
        boolean[][] enabled = new boolean[TEAMS][DeckType.COUNT];
        int[][] hub = new int[TEAMS][DeckType.COUNT];
        int[][] crate = new int[TEAMS][DeckType.COUNT];
        int[][] tickets = new int[TEAMS][DeckType.COUNT];
        int[][] filled = new int[TEAMS][DeckType.COUNT];
        for (int t = 0; t < TEAMS; t++) {
            for (int d = 0; d < DeckType.COUNT; d++) {
                enabled[t][d] = b.readBoolean();
                hub[t][d] = b.readVarInt();
                crate[t][d] = b.readVarInt();
                tickets[t][d] = b.readVarInt();
                filled[t][d] = b.readVarInt();
            }
        }
        int kitCount = AASWorldData.KIT_NAMES.length;
        KitSummary[][] kits = new KitSummary[TEAMS][kitCount];
        for (int t = 0; t < TEAMS; t++) {
            for (int i = 0; i < kitCount; i++) {
                int items = b.readVarInt();
                int altItems = b.readVarInt();
                boolean hasAlt = b.readBoolean();
                boolean leader = b.readBoolean();
                int maxTeam = b.readInt();
                int maxSquad = b.readInt();
                int minSquad = b.readInt();
                String dn = b.readUtf(64);
                kits[t][i] = new KitSummary(items, altItems, hasAlt, leader, maxTeam, maxSquad, minSquad, dn);
            }
        }
        return new PacketKitAdminInfo(edit, undo, label, enabled, hub, crate, tickets, filled, kits);
    }

    public static void handle(PacketKitAdminInfo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KitAdminClient.onInfo(msg)));
        ctx.get().setPacketHandled(true);
    }
}
