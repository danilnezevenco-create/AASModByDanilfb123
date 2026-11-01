package com.example.aas.network;

import com.example.aas.deck.DeckManager;
import com.example.aas.deck.DeckType;
import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Client -> server: data for the "?" window (or for the admin deck-settings screen).
 * A normal player always gets the data of the deck of HIS team; creative players may ask for either team.
 */
public class PacketRequestDeckInfo {
    private final String team;
    private final int deck;

    public PacketRequestDeckInfo(String team, int deck) {
        this.team = team == null ? "" : team;
        this.deck = deck;
    }

    public static void encode(PacketRequestDeckInfo msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.team);
        buf.writeVarInt(msg.deck);
    }

    public static PacketRequestDeckInfo decode(FriendlyByteBuf buf) {
        return new PacketRequestDeckInfo(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(PacketRequestDeckInfo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            String team = DeckManager.teamOf(player);
            if (player.isCreative() && (msg.team.equalsIgnoreCase("BLUE") || msg.team.equalsIgnoreCase("RED"))) {
                team = msg.team.toUpperCase();
            }
            DeckType deck = DeckType.byOrdinal(msg.deck);
            if (team == null || deck == null) return;
            AASWorldData data = AASWorldData.get(player.serverLevel());
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    DeckManager.buildInfo(player.serverLevel(), data, team, deck));
        });
        ctx.get().setPacketHandled(true);
    }
}
