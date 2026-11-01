package com.example.aas.network;

import com.example.aas.art.ArtManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> server: the CMD launches a strike inside one confirmed request.
 * type 0 = circle: (ax, az) centre, size = radius. type 1 = rectangle: (ax, az) start, (bx, bz) end, size = width.
 * The server clamps and re-checks everything (ArtManager.tryLaunch).
 */
public class PacketArtLaunch {
    private final UUID requestId;
    private final int type;
    private final double ax, az, bx, bz, size;

    public PacketArtLaunch(UUID requestId, int type, double ax, double az, double bx, double bz, double size) {
        this.requestId = requestId; this.type = type;
        this.ax = ax; this.az = az; this.bx = bx; this.bz = bz; this.size = size;
    }

    public static void encode(PacketArtLaunch m, FriendlyByteBuf b) {
        b.writeUUID(m.requestId);
        b.writeVarInt(m.type);
        b.writeDouble(m.ax); b.writeDouble(m.az); b.writeDouble(m.bx); b.writeDouble(m.bz); b.writeDouble(m.size);
    }

    public static PacketArtLaunch decode(FriendlyByteBuf b) {
        return new PacketArtLaunch(b.readUUID(), b.readVarInt(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble());
    }

    public static void handle(PacketArtLaunch m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ArtManager.tryLaunch(p, m.requestId, m.type, m.ax, m.az, m.bx, m.bz, m.size);
        });
        ctx.get().setPacketHandled(true);
    }
}
