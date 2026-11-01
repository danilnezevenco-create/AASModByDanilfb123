package com.example.aas.network;

import com.example.aas.art.ArtManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Client -> server: the SL confirms or cancels his own PENDING artillery request. The server re-validates ownership. */
public class PacketArtRequestAction {
    private final UUID requestId;
    private final boolean confirm;

    public PacketArtRequestAction(UUID requestId, boolean confirm) { this.requestId = requestId; this.confirm = confirm; }

    public static void encode(PacketArtRequestAction m, FriendlyByteBuf b) { b.writeUUID(m.requestId); b.writeBoolean(m.confirm); }

    public static PacketArtRequestAction decode(FriendlyByteBuf b) { return new PacketArtRequestAction(b.readUUID(), b.readBoolean()); }

    public static void handle(PacketArtRequestAction m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ArtManager.handleAction(p, m.requestId, m.confirm);
        });
        ctx.get().setPacketHandled(true);
    }
}
