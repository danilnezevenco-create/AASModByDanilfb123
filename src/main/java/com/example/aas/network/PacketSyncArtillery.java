package com.example.aas.network;

import com.example.aas.art.ArtRequest;
import com.example.aas.art.ArtStrike;
import com.example.aas.client.art.ArtClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> ONE player: the artillery state that this player is allowed to know. The server filters per recipient
 * (see ArtManager.send), so PENDING requests and enemy strikes never reach the wrong client.
 */
public class PacketSyncArtillery {
    public final boolean available;
    /** Own REQUEST cooldown (SL) and the team-wide STRIKE cooldown (game time). */
    public final long cooldownEnd;
    public final long strikeCooldownEnd;
    public final List<ArtRequest> requests;
    public final List<ArtStrike> strikes;

    public PacketSyncArtillery(boolean available, long cooldownEnd, long strikeCooldownEnd, List<ArtRequest> requests, List<ArtStrike> strikes) {
        this.available = available;
        this.cooldownEnd = cooldownEnd;
        this.strikeCooldownEnd = strikeCooldownEnd;
        this.requests = requests;
        this.strikes = strikes;
    }

    public static void encode(PacketSyncArtillery m, FriendlyByteBuf b) {
        b.writeBoolean(m.available);
        b.writeLong(m.cooldownEnd);
        b.writeLong(m.strikeCooldownEnd);
        b.writeVarInt(m.requests.size());
        for (ArtRequest r : m.requests) r.write(b);
        b.writeVarInt(m.strikes.size());
        for (ArtStrike s : m.strikes) s.write(b);
    }

    public static PacketSyncArtillery decode(FriendlyByteBuf b) {
        boolean available = b.readBoolean();
        long cooldownEnd = b.readLong();
        long strikeCooldownEnd = b.readLong();
        int rn = Math.min(b.readVarInt(), 256);
        List<ArtRequest> requests = new ArrayList<>(rn);
        for (int i = 0; i < rn; i++) requests.add(ArtRequest.read(b));
        int sn = Math.min(b.readVarInt(), 256);
        List<ArtStrike> strikes = new ArrayList<>(sn);
        for (int i = 0; i < sn; i++) strikes.add(ArtStrike.read(b));
        return new PacketSyncArtillery(available, cooldownEnd, strikeCooldownEnd, requests, strikes);
    }

    public static void handle(PacketSyncArtillery m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ArtClientState.apply(m)));
        ctx.get().setPacketHandled(true);
    }
}