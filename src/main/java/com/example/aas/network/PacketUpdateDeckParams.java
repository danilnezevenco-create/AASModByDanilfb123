package com.example.aas.network;

import com.example.aas.deck.DeckParams;
import com.example.aas.deck.DeckType;
import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Admin (creative) -> server: enabled / hubLimit / crateBonus / tickets of one deck of one team. */
public class PacketUpdateDeckParams {
    private final String team;
    private final int deck;
    private final boolean enabled;
    private final int hubLimit;
    private final int crateBonus;
    private final int tickets;

    public PacketUpdateDeckParams(String team, int deck, boolean enabled, int hubLimit, int crateBonus, int tickets) {
        this.team = team;
        this.deck = deck;
        this.enabled = enabled;
        this.hubLimit = hubLimit;
        this.crateBonus = crateBonus;
        this.tickets = tickets;
    }

    public static void encode(PacketUpdateDeckParams m, FriendlyByteBuf b) {
        b.writeUtf(m.team);
        b.writeVarInt(m.deck);
        b.writeBoolean(m.enabled);
        b.writeInt(m.hubLimit);
        b.writeInt(m.crateBonus);
        b.writeInt(m.tickets);
    }

    public static PacketUpdateDeckParams decode(FriendlyByteBuf b) {
        return new PacketUpdateDeckParams(b.readUtf(), b.readVarInt(), b.readBoolean(), b.readInt(), b.readInt(), b.readInt());
    }

    public static void handle(PacketUpdateDeckParams msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.isCreative()) return;
            DeckType deck = DeckType.byOrdinal(msg.deck);
            if (deck == null || !(msg.team.equalsIgnoreCase("BLUE") || msg.team.equalsIgnoreCase("RED"))) return;
            AASWorldData data = AASWorldData.get(player.serverLevel());

            DeckParams p = data.paramsFor(msg.team, deck);
            boolean enabled = msg.enabled;
            if (!enabled) {
                // at least one deck must stay enabled
                int others = 0;
                for (DeckType d : DeckType.VALUES) if (d != deck && data.paramsFor(msg.team, d).enabled) others++;
                if (others == 0) enabled = true;
            }
            p.enabled = enabled;
            p.hubLimit = msg.hubLimit;
            p.crateBonus = msg.crateBonus;
            p.tickets = msg.tickets;
            p.clamp();

            data.setDirty();
            PacketHandler.sendToAllClients(player.serverLevel(), data);
        });
        ctx.get().setPacketHandled(true);
    }
}
