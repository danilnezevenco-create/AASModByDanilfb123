// PATH: src/main/java/com/example/aas/network/PacketPlaceRadialMarker.java
package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Дегар нового выборочного 3D-вещания меток (WorldMarkerRadialScreen).
 * В отличие от PacketSquadMarker (бьёт по x/z с карты, Y всегда 64), этот пакет
 * не несёт никаких координат вообще — сервер сам считает raycast из глаз игрока
 * по его текущему направлению взгляда (точно так же, как это уже делает PacketPlacePing).
 * Это корректно, потому что пока открыт WorldMarkerRadialScreen (Screen),
 * ванильный лоок Minecraft'а не вращает камеру игрока — взгляд заморожен на том
 * направлении, которое было в момент открытия меню.
 *
 * type: 0-Move, 1-Attack, 2-Defend, 3-Build, 4-Eye (кратковременный пинг, как у PacketPlacePing), 5-Artillery request (SL, only on the map).
 */
public class PacketPlaceRadialMarker {
    public static final int TYPE_MOVE = 0;
    public static final int TYPE_ATTACK = 1;
    public static final int TYPE_DEFEND = 2;
    public static final int TYPE_BUILD = 3;
    public static final int TYPE_EYE = 4;
    /** Artillery request (SL only). Never placed in the 3D world: the request exists on the map only (see ArtManager). */
    public static final int TYPE_ART_REQUEST = 5;

    /** Обычная дальность установки метки (блоков). */
    public static final double DEFAULT_REACH = 300.0;
    /** Дальность установки метки из БПЛА: практически не ограничена (луч идёт только по загруженным чанкам). */
    public static final double UAV_REACH = 8192.0;

    private final int type;

    public PacketPlaceRadialMarker(int type) {
        this.type = type;
    }

    public static void encode(PacketPlaceRadialMarker msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.type);
    }

    public static PacketPlaceRadialMarker decode(FriendlyByteBuf buf) {
        return new PacketPlaceRadialMarker(buf.readInt());
    }

    public static void handle(PacketPlaceRadialMarker msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.type == TYPE_ART_REQUEST) {
                // own server-side raycast up to artRequestMaxDistance; the client sends no coordinates at all
                com.example.aas.art.ArtManager.tryCreateRequest(player, player.getEyePosition(), player.getLookAngle());
                return;
            }
            place(player, msg.type, player.getEyePosition(), player.getLookAngle());
        });
        ctx.get().setPacketHandled(true);
    }

    /** Shared placement: raycast from eyePos along look. Used by the radial menu and by the UAV camera (PacketUavMarker). */
    public static void place(ServerPlayer player, int type, Vec3 eyePos, Vec3 look) {
        place(player, type, eyePos, look, DEFAULT_REACH);
    }

    /**
     * Длина луча, которую можно безопасно пройти: останавливаемся на первом незагруженном чанке,
     * иначе level.clip() принудительно подгружал/генерировал бы чанки на сервере.
     */
    public static double loadedReach(ServerLevel level, Vec3 from, Vec3 look, double max) {
        final double step = 2.0;
        for (double d = 0; d <= max; d += step) {
            BlockPos bp = BlockPos.containing(from.add(look.scale(d)));
            if (!level.hasChunkAt(bp)) return Math.max(0.0, d - step);
        }
        return max;
    }

    /** Общая установка метки с заданной дальностью луча (для БПЛА - UAV_REACH). */
    public static void place(ServerPlayer player, int type, Vec3 eyePos, Vec3 look, double reach) {
        {
            if (type < TYPE_MOVE || type > TYPE_EYE) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());
            String pName = player.getScoreboardName();

            // Сохраняем ту же логику raycast'а, что и у PacketPlacePing
            double len = reach > DEFAULT_REACH ? loadedReach(player.serverLevel(), eyePos, look, reach) : reach;
            Vec3 reachVec = eyePos.add(look.scale(len));
            BlockHitResult hit = player.level().clip(new ClipContext(eyePos, reachVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) return;

            BlockPos target = hit.getBlockPos().relative(hit.getDirection());
            long time = player.level().getGameTime();

            for (AASWorldData.Squad s : data.squads) {
                if (!s.members.contains(pName)) continue;

                boolean isSL = s.leader.equals(pName);
                boolean isBravoFTL = s.bravoLeader.equals(pName);
                boolean isCharlieFTL = s.charlieLeader.equals(pName);

                // Только SL и FTL могут ставить эти метки (как и раньше у ping/squad markers)
                if (!isSL && !isBravoFTL && !isCharlieFTL) break;

                if (type == TYPE_EYE) {
                    // Короткий "глаз-пинг" — идентично PacketPlacePing(isMoveMarker=false)
                    if (isSL) {
                        s.pingPos = target;
                        s.pingExpiry = time + 400;
                    } else if (isBravoFTL) {
                        s.bravoPingPos = target;
                        s.bravoPingExpiry = time + 400;
                    } else {
                        s.charliePingPos = target;
                        s.charliePingExpiry = time + 400;
                    }
                } else {
                    // Move / Attack / Defend / Build — долгоживущая метка с реальным Y из raycast'а
                    AASWorldData.SquadMarker marker = new AASWorldData.SquadMarker(
                            target.getX(), target.getY(), target.getZ(), type, time + 12000, true);

                    if (isSL) {
                        s.marker = marker;
                    } else if (isBravoFTL) {
                        s.bravoMarker = marker;
                    } else {
                        s.charlieMarker = marker;
                    }
                }

                data.setDirty();
                PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(player.level()::dimension),
                        new PacketSyncSquads(data.squads));
                PacketHandler.playMarkerSoundForSquad(player.serverLevel(), s.members, player);
                break;
            }
        }
    }
}
