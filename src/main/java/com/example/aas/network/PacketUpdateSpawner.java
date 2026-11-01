package com.example.aas.network;

import com.example.aas.block.VehicleSpawnerBlockEntity;
import com.example.aas.deck.DeckSpawnConfig;
import com.example.aas.deck.DeckType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Creative player -> server: the spawner settings of ONE deck (a spawner stores one config per deck). */
public class PacketUpdateSpawner {
    private final BlockPos pos;
    private final int deck;
    private final boolean enabled;
    private final boolean redstoneOnStart;
    private final int respawnTime;
    private final int initialTime;
    private final String vehicleId;
    private final float vehicleYaw;
    private final boolean autoReturnEnabled;
    private final int autoReturnTime;
    private final boolean autoReturnDestroy;

    public PacketUpdateSpawner(BlockPos pos, int deck, boolean enabled, boolean redstoneOnStart,
                               int respawn, int init, String id, float yaw,
                               boolean autoReturnEnabled, int autoReturnTime, boolean autoReturnDestroy) {
        this.pos = pos;
        this.deck = deck;
        this.enabled = enabled;
        this.redstoneOnStart = redstoneOnStart;
        this.respawnTime = respawn;
        this.initialTime = init;
        this.vehicleId = id;
        this.vehicleYaw = yaw;
        this.autoReturnEnabled = autoReturnEnabled;
        this.autoReturnTime = autoReturnTime;
        this.autoReturnDestroy = autoReturnDestroy;
    }

    public static void encode(PacketUpdateSpawner msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.deck);
        buf.writeBoolean(msg.enabled);
        buf.writeBoolean(msg.redstoneOnStart);
        buf.writeInt(msg.respawnTime);
        buf.writeInt(msg.initialTime);
        buf.writeUtf(msg.vehicleId);
        buf.writeFloat(msg.vehicleYaw);
        buf.writeBoolean(msg.autoReturnEnabled);
        buf.writeInt(msg.autoReturnTime);
        buf.writeBoolean(msg.autoReturnDestroy);
    }

    public static PacketUpdateSpawner decode(FriendlyByteBuf buf) {
        return new PacketUpdateSpawner(
                buf.readBlockPos(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readBoolean()
        );
    }

    public static void handle(PacketUpdateSpawner msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.isCreative()) {
                DeckType deck = DeckType.byOrdinal(msg.deck);
                if (deck == null) return;
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (be instanceof VehicleSpawnerBlockEntity spawner) {
                    spawner.setEditDeck(deck);   // the GUI inventory (modifier + vehicle items) now belongs to this deck
                    DeckSpawnConfig c = spawner.cfg(deck);
                    c.enabled = msg.enabled;
                    c.redstoneOnStart = msg.redstoneOnStart;
                    c.respawnTime = Math.max(0, msg.respawnTime);
                    c.initialTime = Math.max(0, msg.initialTime);
                    c.vehicleId = msg.vehicleId == null ? "" : msg.vehicleId;
                    c.yaw = msg.vehicleYaw;
                    c.autoReturnEnabled = msg.autoReturnEnabled;
                    c.autoReturnTime = Math.max(0, msg.autoReturnTime);
                    c.autoReturnDestroy = msg.autoReturnDestroy;
                    spawner.setChanged();
                    player.level().sendBlockUpdated(msg.pos, spawner.getBlockState(), spawner.getBlockState(), 3);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
