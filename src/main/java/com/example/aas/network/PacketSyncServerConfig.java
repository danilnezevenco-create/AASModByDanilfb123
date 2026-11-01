package com.example.aas.network;

import com.example.aas.client.ClientData;
import com.example.aas.config.AASConfig;
import com.example.aas.uav.UavRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Server -> client config values that the client must use instead of its own local config.
 * Sent on login and on every server config reload (see AASConfigSync).
 */
public class PacketSyncServerConfig {
    private final boolean autoBalance;
    private final String reviveItem;
    private final int minHubDistance;
    private final int hubBuildRadius;
    private final int crateBuildRadius;
    private final boolean hubRequiresCrate; // NEW: hubPlacementRequiresCrate
    // Recon UAV
    private final int uavMinRadius, uavMaxRadius, uavMaxTilt;
    private final float uavZoom1, uavZoom2, uavZoom3;
    // Artillery: request radius + {circleMin, circleMax, rectMinLen, rectMaxLen, rectMinWidth, rectMaxWidth}
    private final float artRequestRadius;
    private final int[] artLimits;

    public PacketSyncServerConfig(boolean autoBalance, String reviveItem,
                                  int minHubDistance, int hubBuildRadius, int crateBuildRadius,
                                  boolean hubRequiresCrate,
                                  int uavMinRadius, int uavMaxRadius, int uavMaxTilt,
                                  float uavZoom1, float uavZoom2, float uavZoom3,
                                  float artRequestRadius, int[] artLimits) {
        this.autoBalance = autoBalance;
        this.reviveItem = reviveItem;
        this.minHubDistance = minHubDistance;
        this.hubBuildRadius = hubBuildRadius;
        this.crateBuildRadius = crateBuildRadius;
        this.hubRequiresCrate = hubRequiresCrate;
        this.uavMinRadius = uavMinRadius;
        this.uavMaxRadius = uavMaxRadius;
        this.uavMaxTilt = uavMaxTilt;
        this.uavZoom1 = uavZoom1;
        this.uavZoom2 = uavZoom2;
        this.uavZoom3 = uavZoom3;
        this.artRequestRadius = artRequestRadius;
        this.artLimits = artLimits;
    }

    /** Snapshot of the CURRENT server-side config values. Call only on the server. */
    public static PacketSyncServerConfig fromConfig() {
        return new PacketSyncServerConfig(
                AASConfig.AUTO_BALANCE_TEAMS.get(),
                AASConfig.REVIVE_ITEM.get(),
                AASConfig.MIN_HUB_DISTANCE.get(),
                AASConfig.HUB_BUILD_RADIUS.get(),
                AASConfig.CRATE_BUILD_RADIUS.get(),
                AASConfig.HUB_PLACEMENT_REQUIRES_CRATE.get(),
                AASConfig.UAV_MIN_RADIUS.get(),
                AASConfig.UAV_MAX_RADIUS.get(),
                AASConfig.UAV_MAX_TILT.get(),
                UavRules.zoomFov(0), UavRules.zoomFov(1), UavRules.zoomFov(2),
                AASConfig.ART_REQUEST_RADIUS.get().floatValue(),
                new int[]{AASConfig.ART_CIRCLE_MIN_RADIUS.get(), AASConfig.ART_CIRCLE_MAX_RADIUS.get(),
                        AASConfig.ART_RECT_MIN_LENGTH.get(), AASConfig.ART_RECT_MAX_LENGTH.get(),
                        AASConfig.ART_RECT_MIN_WIDTH.get(), AASConfig.ART_RECT_MAX_WIDTH.get()});
    }

    public static void encode(PacketSyncServerConfig msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.autoBalance);
        buf.writeUtf(msg.reviveItem);
        buf.writeVarInt(msg.minHubDistance);
        buf.writeVarInt(msg.hubBuildRadius);
        buf.writeVarInt(msg.crateBuildRadius);
        buf.writeBoolean(msg.hubRequiresCrate);
        buf.writeVarInt(msg.uavMinRadius);
        buf.writeVarInt(msg.uavMaxRadius);
        buf.writeVarInt(msg.uavMaxTilt);
        buf.writeFloat(msg.uavZoom1);
        buf.writeFloat(msg.uavZoom2);
        buf.writeFloat(msg.uavZoom3);
        buf.writeFloat(msg.artRequestRadius);
        for (int v : msg.artLimits) buf.writeVarInt(v);
    }

    public static PacketSyncServerConfig decode(FriendlyByteBuf buf) {
        return new PacketSyncServerConfig(buf.readBoolean(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), new int[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()});
    }

    public static void handle(PacketSyncServerConfig msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientData.serverAutoBalance = msg.autoBalance;
                ClientData.serverReviveItem = msg.reviveItem;
                ClientData.serverMinHubDistance = msg.minHubDistance;
                ClientData.serverHubBuildRadius = msg.hubBuildRadius;
                ClientData.serverCrateBuildRadius = msg.crateBuildRadius;
                ClientData.serverHubRequiresCrate = msg.hubRequiresCrate ? 1 : 0;
                ClientData.serverUavMinRadius = msg.uavMinRadius;
                ClientData.serverUavMaxRadius = msg.uavMaxRadius;
                ClientData.serverUavMaxTilt = msg.uavMaxTilt;
                ClientData.serverUavZoomFov = new float[]{msg.uavZoom1, msg.uavZoom2, msg.uavZoom3};
                ClientData.serverArtRequestRadius = msg.artRequestRadius;
                ClientData.serverArtLimits = msg.artLimits.clone();
            });
        });
        ctx.get().setPacketHandled(true);
    }
}

