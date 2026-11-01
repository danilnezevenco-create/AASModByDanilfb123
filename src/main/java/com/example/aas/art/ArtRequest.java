package com.example.aas.art;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * One artillery request of one squad leader. One request = one strike: it is deleted as soon as the strike starts.
 * PENDING: waits for the owner's confirmation (config: artRequestConfirmSeconds), visible to the owner only.
 * CONFIRMED: visible to the CMD and the squad leaders of the team until it is used or expireTick passes.
 */
public class ArtRequest {
    public static final int PENDING = 0;
    public static final int CONFIRMED = 1;

    public final UUID id;
    public final UUID ownerUuid;
    public final String ownerName;
    public final String team;
    public final BlockPos pos;
    public int status;
    /** Game time (ticks) at which the request disappears in its current status. */
    public long expireTick;

    public ArtRequest(UUID id, UUID ownerUuid, String ownerName, String team, BlockPos pos, int status, long expireTick) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.team = team;
        this.pos = pos;
        this.status = status;
        this.expireTick = expireTick;
    }

    public double centerX() { return pos.getX() + 0.5; }
    public double centerZ() { return pos.getZ() + 0.5; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        t.putUUID("Owner", ownerUuid);
        t.putString("OwnerName", ownerName);
        t.putString("Team", team);
        t.putLong("Pos", pos.asLong());
        t.putInt("Status", status);
        t.putLong("Expire", expireTick);
        return t;
    }

    public static ArtRequest load(CompoundTag t) {
        return new ArtRequest(t.getUUID("Id"), t.getUUID("Owner"), t.getString("OwnerName"), t.getString("Team"),
                BlockPos.of(t.getLong("Pos")), t.getInt("Status"), t.getLong("Expire"));
    }

    public void write(FriendlyByteBuf b) {
        b.writeUUID(id);
        b.writeUUID(ownerUuid);
        b.writeUtf(ownerName);
        b.writeUtf(team);
        b.writeBlockPos(pos);
        b.writeVarInt(status);
        b.writeLong(expireTick);
    }

    public static ArtRequest read(FriendlyByteBuf b) {
        return new ArtRequest(b.readUUID(), b.readUUID(), b.readUtf(), b.readUtf(), b.readBlockPos(), b.readVarInt(), b.readLong());
    }
}
