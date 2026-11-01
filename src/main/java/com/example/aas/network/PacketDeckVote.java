package com.example.aas.network;

import com.example.aas.deck.DeckManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: "I vote for this deck" (DeckType ordinal). Counted on the server only. */
public class PacketDeckVote {
    private final int deck;

    public PacketDeckVote(int deck) {
        this.deck = deck;
    }

    public static void encode(PacketDeckVote msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.deck);
    }

    public static PacketDeckVote decode(FriendlyByteBuf buf) {
        return new PacketDeckVote(buf.readVarInt());
    }

    public static void handle(PacketDeckVote msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) DeckManager.handleVote(player, msg.deck);
        });
        ctx.get().setPacketHandled(true);
    }
}
