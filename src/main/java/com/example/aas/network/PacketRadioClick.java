package com.example.aas.network;

import com.example.aas.client.RadioClickSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: somebody in my channel pressed (start = true) or released (start = false) the radio key.
 * channel: 2 = squad, 3 = command. The client picks the sound set of its own faction (recipients are teammates).
 */
public class PacketRadioClick {
    private final int channel;
    private final boolean start;

    public PacketRadioClick(int channel, boolean start) {
        this.channel = channel;
        this.start = start;
    }

    public static void encode(PacketRadioClick msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.channel);
        buf.writeBoolean(msg.start);
    }

    public static PacketRadioClick decode(FriendlyByteBuf buf) {
        return new PacketRadioClick(buf.readByte(), buf.readBoolean());
    }

    public static void handle(PacketRadioClick msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RadioClickSounds.playRemote(msg.channel, msg.start)));
        ctx.get().setPacketHandled(true);
    }
}
