package com.example.aas.network;

import com.example.aas.client.DeckClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> client: the numbers shown in the "?" window of one deck (limits: -1 = unlimited, 0 = none). */
public class PacketDeckInfo {
    /** markerType = class marker (APC / Tank / ...) -> map icon; penalty = tickets lost when one is destroyed. */
    public record VehicleLine(String nameKey, int count, String markerType, int penalty) {}

    private static final int MAX_LINES = 256;

    public final int deck;
    public final boolean enabled;
    public final int hubLimit;
    public final int crateBonus;
    public final int tickets;
    public final int antiAirMax;
    public final int hatMax;
    public final int crateTotal;
    public final List<VehicleLine> vehicles;

    public PacketDeckInfo(int deck, boolean enabled, int hubLimit, int crateBonus, int tickets,
                          int antiAirMax, int hatMax, int crateTotal, List<VehicleLine> vehicles) {
        this.deck = deck;
        this.enabled = enabled;
        this.hubLimit = hubLimit;
        this.crateBonus = crateBonus;
        this.tickets = tickets;
        this.antiAirMax = antiAirMax;
        this.hatMax = hatMax;
        this.crateTotal = crateTotal;
        this.vehicles = vehicles;
    }

    public static void encode(PacketDeckInfo m, FriendlyByteBuf b) {
        b.writeVarInt(m.deck);
        b.writeBoolean(m.enabled);
        b.writeInt(m.hubLimit);
        b.writeInt(m.crateBonus);
        b.writeInt(m.tickets);
        b.writeInt(m.antiAirMax);
        b.writeInt(m.hatMax);
        b.writeInt(m.crateTotal);
        b.writeVarInt(m.vehicles.size());
        for (VehicleLine l : m.vehicles) {
            b.writeUtf(l.nameKey());
            b.writeVarInt(l.count());
            b.writeUtf(l.markerType());
            b.writeVarInt(l.penalty());
        }
    }

    public static PacketDeckInfo decode(FriendlyByteBuf b) {
        int deck = b.readVarInt();
        boolean enabled = b.readBoolean();
        int hub = b.readInt();
        int bonus = b.readInt();
        int tickets = b.readInt();
        int aa = b.readInt();
        int hat = b.readInt();
        int crate = b.readInt();
        int n = b.readVarInt();
        if (n < 0 || n > MAX_LINES) throw new IllegalArgumentException("PacketDeckInfo: refusing " + n + " vehicle lines");
        List<VehicleLine> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(new VehicleLine(b.readUtf(), b.readVarInt(), b.readUtf(), b.readVarInt()));
        return new PacketDeckInfo(deck, enabled, hub, bonus, tickets, aa, hat, crate, lines);
    }

    public static void handle(PacketDeckInfo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DeckClient.onInfo(msg)));
        ctx.get().setPacketHandled(true);
    }
}