package com.example.aas.art;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * An active artillery strike. Ticked on the server (ArtManager) and saved in AASWorldData.
 *
 * Geometry (all in world blocks):
 *  CIRCLE: (ax, az) = centre, size = radius, (bx, bz) unused.
 *  RECT:   (ax, az) = start, (bx, bz) = end, size = width. Shells fall from the start towards the end.
 *
 * Stages: 0 spotting, 1 silence, 2 salvo 1, 3 rest, 4 salvo 2, 5 rest, 6 salvo 3, 7 finished.
 */
public class ArtStrike {
    public enum Type { CIRCLE, RECT }

    public static final int STAGE_SPOT = 0;
    public static final int STAGE_SILENCE = 1;
    public static final int STAGE_DONE = 7;

    public final UUID id;
    public final Type type;
    public final String team;
    public final UUID ownerUuid;
    public final double ax, az, bx, bz, size;
    /** Seeds the shell scattering, saved so a restart continues the same pattern. */
    public final long seed;

    public int stage = STAGE_SPOT;
    /** Ticks spent in the current stage. */
    public int phaseTick = 0;
    /** Shells already fired in the current stage (spotting or salvo). */
    public int shellsFired = 0;

    public ArtStrike(UUID id, Type type, String team, UUID ownerUuid,
                     double ax, double az, double bx, double bz, double size, long seed) {
        this.id = id;
        this.type = type;
        this.team = team;
        this.ownerUuid = ownerUuid;
        this.ax = ax; this.az = az; this.bx = bx; this.bz = bz;
        this.size = size;
        this.seed = seed;
    }

    public boolean isSalvoStage() { return stage == 2 || stage == 4 || stage == 6; }
    public int salvoIndex() { return (stage - 2) / 2; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        t.putString("Type", type.name());
        t.putString("Team", team);
        t.putUUID("Owner", ownerUuid);
        t.putDouble("Ax", ax); t.putDouble("Az", az);
        t.putDouble("Bx", bx); t.putDouble("Bz", bz);
        t.putDouble("Size", size);
        t.putLong("Seed", seed);
        t.putInt("Stage", stage);
        t.putInt("PhaseTick", phaseTick);
        t.putInt("Fired", shellsFired);
        return t;
    }

    /** @return null for entries written by the old strike system (no "Type" key). */
    public static ArtStrike load(CompoundTag t) {
        if (!t.contains("Type") || !t.hasUUID("Id")) return null;
        Type type;
        try { type = Type.valueOf(t.getString("Type")); } catch (IllegalArgumentException e) { return null; }
        ArtStrike s = new ArtStrike(t.getUUID("Id"), type, t.getString("Team"), t.getUUID("Owner"),
                t.getDouble("Ax"), t.getDouble("Az"), t.getDouble("Bx"), t.getDouble("Bz"), t.getDouble("Size"), t.getLong("Seed"));
        s.stage = t.getInt("Stage");
        s.phaseTick = t.getInt("PhaseTick");
        s.shellsFired = t.getInt("Fired");
        return s;
    }

    /** Network form: only what the client draws. */
    public void write(FriendlyByteBuf b) {
        b.writeUUID(id);
        b.writeVarInt(type.ordinal());
        b.writeUtf(team);
        b.writeDouble(ax); b.writeDouble(az);
        b.writeDouble(bx); b.writeDouble(bz);
        b.writeDouble(size);
        b.writeVarInt(stage);
    }

    public static ArtStrike read(FriendlyByteBuf b) {
        UUID id = b.readUUID();
        Type type = Type.values()[Math.floorMod(b.readVarInt(), Type.values().length)];
        String team = b.readUtf();
        ArtStrike s = new ArtStrike(id, type, team, new UUID(0, 0),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), 0L);
        s.stage = b.readVarInt();
        return s;
    }
}
