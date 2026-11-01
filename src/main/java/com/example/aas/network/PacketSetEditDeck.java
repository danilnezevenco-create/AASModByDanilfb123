package com.example.aas.network;

import com.example.aas.deck.DeckManager;
import com.example.aas.deck.DeckType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Admin (creative) -> server: "I edit the kits of THIS deck now". Every kit packet (open editor, save, copy, paste,
 * key export / import) works with the deck stored here, so none of them needs a new field.
 */
public class PacketSetEditDeck {
    private final int deck;

    public PacketSetEditDeck(int deck) {
        this.deck = deck;
    }

    public static void encode(PacketSetEditDeck m, FriendlyByteBuf b) {
        b.writeVarInt(m.deck);
    }

    public static PacketSetEditDeck decode(FriendlyByteBuf b) {
        return new PacketSetEditDeck(b.readVarInt());
    }

    public static void handle(PacketSetEditDeck msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.isCreative()) return;
            DeckType deck = DeckType.byOrdinal(msg.deck);
            if (deck != null) DeckManager.setEditDeck(player, deck);
        });
        ctx.get().setPacketHandled(true);
    }
}
