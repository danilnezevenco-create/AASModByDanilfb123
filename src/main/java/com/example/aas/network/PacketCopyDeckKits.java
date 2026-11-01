package com.example.aas.network;

import com.example.aas.deck.DeckType;
import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Admin (creative) -> server: copy ALL kits of one team from deck X to deck Y (Y is overwritten). */
public class PacketCopyDeckKits {
    private final String team;
    private final int from;
    private final int to;

    public PacketCopyDeckKits(String team, int from, int to) {
        this.team = team;
        this.from = from;
        this.to = to;
    }

    public static void encode(PacketCopyDeckKits m, FriendlyByteBuf b) {
        b.writeUtf(m.team);
        b.writeVarInt(m.from);
        b.writeVarInt(m.to);
    }

    public static PacketCopyDeckKits decode(FriendlyByteBuf b) {
        return new PacketCopyDeckKits(b.readUtf(), b.readVarInt(), b.readVarInt());
    }

    public static void handle(PacketCopyDeckKits msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.isCreative()) return;
            DeckType from = DeckType.byOrdinal(msg.from);
            DeckType to = DeckType.byOrdinal(msg.to);
            if (from == null || to == null || from == to) return;
            if (!(msg.team.equalsIgnoreCase("BLUE") || msg.team.equalsIgnoreCase("RED"))) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());
            Map<String, AASWorldData.KitInfo> source = data.kitsFor(msg.team, from);
            Map<String, AASWorldData.KitInfo> target = data.kitsFor(msg.team, to);

            // deep copy through NBT (items, flags, alternative kit, limits ...)
            Map<String, AASWorldData.KitInfo> copy = new HashMap<>();
            for (Map.Entry<String, AASWorldData.KitInfo> e : source.entrySet()) {
                copy.put(e.getKey(), AASWorldData.KitInfo.load(e.getValue().save()));
            }
            target.clear();
            target.putAll(copy);

            data.setDirty();
            PacketHandler.sendToAllClients(player.serverLevel(), data);
        });
        ctx.get().setPacketHandled(true);
    }
}
