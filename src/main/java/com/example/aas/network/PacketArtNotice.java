package com.example.aas.network;

import com.example.aas.client.art.ArtClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> CMD: an SL confirmed an artillery request. Shown as a plate (UavNoticeOverlay), also on the death screen. */
public class PacketArtNotice {
    private final String ownerName;

    public PacketArtNotice(String ownerName) { this.ownerName = ownerName; }

    public static void encode(PacketArtNotice m, FriendlyByteBuf b) { b.writeUtf(m.ownerName); }

    public static PacketArtNotice decode(FriendlyByteBuf b) { return new PacketArtNotice(b.readUtf()); }

    public static void handle(PacketArtNotice m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ArtClientState.onConfirmedNotice(m.ownerName)));
        ctx.get().setPacketHandled(true);
    }
}
