package com.example.aas.deck;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The deck part of PacketSyncGameData: preparation phase, its timer (ticks), the chosen decks, which decks
 * are enabled for each team and the current vote counters. Chosen decks / counters are -1 / 0 when unknown.
 */
public final class DeckSyncPayload {
    public static final DeckSyncPayload EMPTY = new DeckSyncPayload(0, 0, -1, -1, 0, 0, new int[DeckType.COUNT], new int[DeckType.COUNT]);

    public final int phase;
    public final int timerTicks;
    public final int blueChosen, redChosen;
    public final int blueEnabledMask, redEnabledMask;
    public final int[] blueVotes, redVotes;

    public DeckSyncPayload(int phase, int timerTicks, int blueChosen, int redChosen,
                           int blueEnabledMask, int redEnabledMask, int[] blueVotes, int[] redVotes) {
        this.phase = phase;
        this.timerTicks = timerTicks;
        this.blueChosen = blueChosen;
        this.redChosen = redChosen;
        this.blueEnabledMask = blueEnabledMask;
        this.redEnabledMask = redEnabledMask;
        this.blueVotes = blueVotes;
        this.redVotes = redVotes;
    }

    public static DeckSyncPayload from(AASWorldData d) {
        int bm = 0, rm = 0;
        for (DeckType t : DeckType.VALUES) {
            if (d.paramsFor("BLUE", t).enabled) bm |= 1 << t.ordinal();
            if (d.paramsFor("RED", t).enabled) rm |= 1 << t.ordinal();
        }
        return new DeckSyncPayload(
                d.prepPhase.ordinal(), Math.max(0, d.prepTimer),
                d.blueDeck == null ? -1 : d.blueDeck.ordinal(),
                d.redDeck == null ? -1 : d.redDeck.ordinal(),
                bm, rm,
                DeckManager.countVotes(d, "BLUE"), DeckManager.countVotes(d, "RED"));
    }

    public DeckPhase phase() {
        return DeckPhase.byOrdinal(phase);
    }

    public boolean isEnabled(boolean blue, DeckType t) {
        return ((blue ? blueEnabledMask : redEnabledMask) & (1 << t.ordinal())) != 0;
    }

    public int enabledCount(boolean blue) {
        return Integer.bitCount(blue ? blueEnabledMask : redEnabledMask);
    }

    public DeckType chosen(boolean blue) {
        return DeckType.byOrdinal(blue ? blueChosen : redChosen);
    }

    public int[] votes(boolean blue) {
        return blue ? blueVotes : redVotes;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(phase);
        buf.writeVarInt(timerTicks);
        buf.writeVarInt(blueChosen + 1);
        buf.writeVarInt(redChosen + 1);
        buf.writeVarInt(blueEnabledMask);
        buf.writeVarInt(redEnabledMask);
        for (int i = 0; i < DeckType.COUNT; i++) buf.writeVarInt(blueVotes[i]);
        for (int i = 0; i < DeckType.COUNT; i++) buf.writeVarInt(redVotes[i]);
    }

    public static DeckSyncPayload read(FriendlyByteBuf buf) {
        int phase = buf.readVarInt();
        int timer = buf.readVarInt();
        int bc = buf.readVarInt() - 1;
        int rc = buf.readVarInt() - 1;
        int bm = buf.readVarInt();
        int rm = buf.readVarInt();
        int[] bv = new int[DeckType.COUNT];
        int[] rv = new int[DeckType.COUNT];
        for (int i = 0; i < DeckType.COUNT; i++) bv[i] = buf.readVarInt();
        for (int i = 0; i < DeckType.COUNT; i++) rv[i] = buf.readVarInt();
        return new DeckSyncPayload(phase, timer, bc, rc, bm, rm, bv, rv);
    }
}
