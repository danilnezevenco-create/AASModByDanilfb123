package com.example.aas.art;


import com.example.aas.config.AASConfig;
import com.example.aas.network.PacketArtNotice;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketPlaceRadialMarker;
import com.example.aas.network.PacketSyncArtillery;
import com.example.aas.uav.UavRules;
import com.example.aas.world.AASWorldData;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Server-side artillery: requests (SL), strike launch (CMD), the strike state machine and the per-player sync.
 * All rules live here; the client only sends intents and draws what PacketSyncArtillery gives it.
 * Friendly fire is ON by design: shells hurt everybody, nothing protects the own team.
 */
public final class ArtManager {
    private ArtManager() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHELL_ID = new ResourceLocation("superbwarfare", "mortar_shell");
    public static final TicketType<ChunkPos> ART_TICKET =
            TicketType.create("aas_art", Comparator.comparingLong(ChunkPos::toLong), 100);

    /** Pause between two spotting shells (ticks). Not configurable on purpose: it only shapes the spotting phase. */
    public static final int SPOT_INTERVAL_TICKS = 40;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final int RESYNC_TICKS = 40;

    private static EntityType<?> shellType;
    private static boolean warned;

    // ------------------------------------------------------------------ availability
    private static EntityType<?> shellType() {
        if (shellType != null) return shellType;
        // getValue() on a defaulted registry returns a fallback entity for unknown ids, so check the key first
        if (ForgeRegistries.ENTITY_TYPES.containsKey(SHELL_ID)) {
            shellType = ForgeRegistries.ENTITY_TYPES.getValue(SHELL_ID);
            return shellType;
        }
        if (!warned) {
            warned = true;
            LOGGER.warn("[AAS] Entity superbwarfare:mortar_shell was not found (Superb Warfare not loaded?). Artillery is disabled.");
        }
        return null;
    }

    public static boolean isAvailable() { return shellType() != null; }

    // ------------------------------------------------------------------ config helpers
    public static double requestRadius() { return AASConfig.ART_REQUEST_RADIUS.get(); }
    private static int circleMin() { return AASConfig.ART_CIRCLE_MIN_RADIUS.get(); }
    private static int circleMax() { return Math.max(circleMin(), AASConfig.ART_CIRCLE_MAX_RADIUS.get()); }
    private static int rectMinLen() { return AASConfig.ART_RECT_MIN_LENGTH.get(); }
    private static int rectMaxLen() { return Math.max(rectMinLen(), AASConfig.ART_RECT_MAX_LENGTH.get()); }
    private static int rectMinWidth() { return AASConfig.ART_RECT_MIN_WIDTH.get(); }
    private static int rectMaxWidth() { return Math.max(rectMinWidth(), AASConfig.ART_RECT_MAX_WIDTH.get()); }

    private static String fmt(long ticks) {
        long sec = (ticks + 19) / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    private static boolean fail(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("aas.art.msg." + key), true);
        return false;
    }

    // ------------------------------------------------------------------ stage 1: request (SL)
    /**
     * Called from PacketPlaceRadialMarker (TYPE_ART_REQUEST). Raycast is server-side only, from the player's
     * eye along his look vector, up to artRequestMaxDistance blocks over loaded chunks.
     */
    public static boolean tryCreateRequest(ServerPlayer p, Vec3 eye, Vec3 look) {
        return tryCreateRequest(p, eye, look, AASConfig.ART_REQUEST_MAX_DISTANCE.get());
    }

    /** Same, but with an explicit ray length: the UAV camera passes PacketPlaceRadialMarker.UAV_REACH. */
    public static boolean tryCreateRequest(ServerPlayer p, Vec3 eye, Vec3 look, double reach) {
        ServerLevel level = p.serverLevel();
        AASWorldData d = AASWorldData.get(level);
        String team = UavRules.teamOf(p);

        if (!ArtRules.isSquadLeader(p)) return fail(p, "not_sl");
        if (!isAvailable()) return fail(p, "disabled");
        if (!d.isGameStarted) return fail(p, "not_started");

        long now = level.getGameTime();
        long cdEnd = ArtRules.requestCooldownEnd(d, p.getUUID());
        if (now < cdEnd) {
            p.displayClientMessage(Component.translatable("aas.art.msg.cooldown", fmt(cdEnd - now)), true);
            return false;
        }
        if (ArtRules.requestOf(d, p.getUUID()) != null) return fail(p, "has_request");

        double len = PacketPlaceRadialMarker.loadedReach(level, eye, look, reach);
        Vec3 end = eye.add(look.scale(len));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) return fail(p, "no_target");

        BlockPos pos = hit.getBlockPos();
        double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
        if (!level.getWorldBorder().isWithinBounds(x, z)) return fail(p, "bad_position");
        if (d.mapSizeBlocks > 0) {
            double half = d.mapSizeBlocks / 2.0;
            if (Math.abs(x - d.mapCenterX) > half || Math.abs(z - d.mapCenterZ) > half) return fail(p, "bad_position");
        }

        long expire = now + AASConfig.ART_REQUEST_CONFIRM_SECONDS.get() * 20L;
        d.artRequests.add(new ArtRequest(UUID.randomUUID(), p.getUUID(), p.getScoreboardName(), team, pos.immutable(),
                ArtRequest.PENDING, expire));
        d.setDirty();
        syncAll(level);
        p.displayClientMessage(Component.translatable("aas.art.msg.requested"), true);
        return true;
    }

    /** Confirm or cancel of the owner's own PENDING request. Works while dead or downed. */
    public static void handleAction(ServerPlayer p, UUID id, boolean confirm) {
        ServerLevel level = p.serverLevel();
        AASWorldData d = AASWorldData.get(level);
        String team = UavRules.teamOf(p);
        ArtRequest r = ArtRules.findRequest(d, id);
        if (r == null || !UavRules.isTeam(team)) return;
        if (!r.ownerUuid.equals(p.getUUID()) || !r.team.equals(team)) return;
        if (r.status != ArtRequest.PENDING) return;

        long now = level.getGameTime();
        if (!confirm || now >= r.expireTick) {          // cancel, or too late: no cooldown either way
            d.artRequests.remove(r);
            d.setDirty();
            syncAll(level);
            return;
        }
        if (!d.isGameStarted || !isAvailable()) return;

        r.status = ArtRequest.CONFIRMED;
        r.expireTick = now + AASConfig.ART_REQUEST_LIFETIME_SECONDS.get() * 20L;
        // this SL's own REQUEST cooldown starts at the moment of confirmation
        d.artCooldownEnd.put(p.getUUID(), now + AASConfig.ART_REQUEST_COOLDOWN_SECONDS.get() * 20L);
        d.setDirty();
        notifyCommanders(level, d, r);
        syncAll(level);
    }

    private static void notifyCommanders(ServerLevel level, AASWorldData d, ArtRequest r) {
        for (ServerPlayer p : level.players()) {
            if (!UavRules.teamOf(p).equals(r.team) || !ArtRules.isCommander(p, d)) continue;
            if (p.getUUID().equals(r.ownerUuid)) continue;     // the CMD does not need a notice about his own request
            p.sendSystemMessage(Component.translatable("aas.art.msg.confirmed", r.ownerName).withStyle(ChatFormatting.GOLD));
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PacketArtNotice(r.ownerName));
        }
    }

    // ------------------------------------------------------------------ stage 2-4: launch (CMD)
    /** typeOrdinal: 0 = circle, 1 = rectangle. Every number is clamped / re-checked here, never trusted. */
    public static boolean tryLaunch(ServerPlayer p, UUID requestId, int typeOrdinal,
                                    double ax, double az, double bx, double bz, double size) {
        ServerLevel level = p.serverLevel();
        AASWorldData d = AASWorldData.get(level);
        String team = UavRules.teamOf(p);

        if (!UavRules.isTeam(team) || !ArtRules.isCommander(p, d)) return fail(p, "not_commander");
        if (!isAvailable()) return fail(p, "disabled");
        if (!d.isGameStarted) return fail(p, "not_started");

        long now = level.getGameTime();
        long strikeCdEnd = ArtRules.strikeCooldownEnd(d, team);
        if (now < strikeCdEnd) {                       // the strike cooldown is shared by the whole team
            p.displayClientMessage(Component.translatable("aas.art.msg.strike_cooldown", fmt(strikeCdEnd - now)), true);
            return false;
        }
        ArtRequest r = ArtRules.findRequest(d, requestId);
        if (r == null || r.status != ArtRequest.CONFIRMED || !r.team.equals(team) || now >= r.expireTick) return fail(p, "no_request");
        if (typeOrdinal < 0 || typeOrdinal > 1) return fail(p, "bad_geometry");
        if (!Double.isFinite(ax) || !Double.isFinite(az) || !Double.isFinite(bx) || !Double.isFinite(bz) || !Double.isFinite(size)) {
            return fail(p, "bad_geometry");
        }

        double cx = r.centerX(), cz = r.centerZ(), R = requestRadius();
        long seed = level.random.nextLong();
        ArtStrike strike;

        // The request circle limits ONLY the anchor point (circle centre / rectangle start).
        // The strike itself (radius, length, width) may reach outside of it.
        double[] a = ArtGeometry.clampToCircle(ax, az, cx, cz, R);

        if (typeOrdinal == 0) {
            double rad = Mth.clamp(size, circleMin(), circleMax());
            strike = new ArtStrike(UUID.randomUUID(), ArtStrike.Type.CIRCLE, team, p.getUUID(), a[0], a[1], 0, 0, rad, seed);
        } else {
            double dx = bx - a[0], dz = bz - a[1];
            double len = Math.hypot(dx, dz);
            if (len < 1e-3) return fail(p, "bad_geometry");
            double nl = Mth.clamp(len, rectMinLen(), rectMaxLen());
            double ex = a[0] + dx / len * nl, ez = a[1] + dz / len * nl;
            double w = Mth.clamp(size, rectMinWidth(), rectMaxWidth());
            strike = new ArtStrike(UUID.randomUUID(), ArtStrike.Type.RECT, team, p.getUUID(), a[0], a[1], ex, ez, w, seed);
        }

        d.artRequests.remove(r);            // one request = one strike; the circle disappears at once
        if (team.equals("BLUE")) d.blueArtStrikeCdEnd = now + AASConfig.ART_STRIKE_COOLDOWN_MINUTES.get() * 1200L;
        else d.redArtStrikeCdEnd = now + AASConfig.ART_STRIKE_COOLDOWN_MINUTES.get() * 1200L;
        d.activeStrikes.add(strike);
        d.setDirty();
        for (ServerPlayer t : level.players()) {
            if (UavRules.teamOf(t).equals(team)) {
                t.sendSystemMessage(Component.translatable("aas.art.msg.launched").withStyle(ChatFormatting.RED));
            }
        }
        syncAll(level);
        return true;
    }

    // ------------------------------------------------------------------ tick
    public static void tickLevel(ServerLevel level) {
        AASWorldData d = AASWorldData.get(level);
        long now = level.getGameTime();
        boolean changed = false;

        Iterator<ArtRequest> rit = d.artRequests.iterator();
        while (rit.hasNext()) {
            ArtRequest r = rit.next();
            if (now < r.expireTick) continue;
            rit.remove();                    // timed out: PENDING without cooldown, CONFIRMED simply unused
            changed = true;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(r.ownerUuid);
            if (owner != null) owner.displayClientMessage(Component.translatable("aas.art.msg.expired"), true);
        }

        Iterator<ArtStrike> sit = d.activeStrikes.iterator();
        while (sit.hasNext()) {
            ArtStrike s = sit.next();
            if (tickStrike(level, s)) changed = true;
            if (s.stage >= ArtStrike.STAGE_DONE) { sit.remove(); changed = true; }
        }
        if (!d.activeStrikes.isEmpty()) d.setDirty();   // phaseTick / shellsFired are saved state

        if (changed) {
            d.setDirty();
            syncAll(level);
        } else if (now % RESYNC_TICKS == 0) {            // safety resync: role changes, late joins
            syncAll(level);
        }
    }

    /** @return true when the strike moved to the next stage. */
    private static boolean tickStrike(ServerLevel level, ArtStrike s) {
        int len = stageLen(s);
        boolean shooting = s.stage == ArtStrike.STAGE_SPOT || s.isSalvoStage();
        int n = shooting ? shellCount(s) : 0;

        if (shooting) {
            while (s.shellsFired < n && s.phaseTick >= dueTick(s, s.shellsFired, n, len)) {
                fireShell(level, s, s.shellsFired, n);
                s.shellsFired++;
            }
        }
        s.phaseTick++;
        if (s.phaseTick < len) return false;

        if (shooting) {
            while (s.shellsFired < n) { fireShell(level, s, s.shellsFired, n); s.shellsFired++; }
        }
        s.stage++;
        s.phaseTick = 0;
        s.shellsFired = 0;
        return true;
    }

    private static int shellCount(ArtStrike s) {
        boolean circle = s.type == ArtStrike.Type.CIRCLE;
        if (s.stage == ArtStrike.STAGE_SPOT) {
            return circle ? AASConfig.ART_CIRCLE_SPOT_SHELLS.get() : AASConfig.ART_RECT_SPOT_SHELLS.get();
        }
        return circle ? AASConfig.ART_CIRCLE_SALVO_SHELLS.get() : AASConfig.ART_RECT_SALVO_SHELLS.get();
    }

    private static int stageLen(ArtStrike s) {
        return switch (s.stage) {
            case ArtStrike.STAGE_SPOT -> Math.max(1, shellCount(s) * SPOT_INTERVAL_TICKS);
            case ArtStrike.STAGE_SILENCE -> AASConfig.ART_SILENCE_SECONDS.get() * 20;
            case 2, 4, 6 -> Math.max(1, AASConfig.ART_SALVO_SECONDS.get() * 20);
            default -> AASConfig.ART_REST_SECONDS.get() * 20;
        };
    }

    /** Tick (inside the stage) at which shell k is due: spotting is paced, a salvo is spread evenly over its length. */
    private static int dueTick(ArtStrike s, int k, int n, int len) {
        if (s.stage == ArtStrike.STAGE_SPOT) return k * SPOT_INTERVAL_TICKS;
        return (int) ((long) k * len / n);
    }

    // ------------------------------------------------------------------ shells
    private static void fireShell(ServerLevel level, ArtStrike s, int k, int n) {
        double[] p = s.stage == ArtStrike.STAGE_SPOT ? spotPos(level, s) : salvoPos(level, s, k, n);
        spawnShell(level, p[0], p[1]);
    }

    /** Spotting shells land near the centre of a circle / near the start of a rectangle. */
    private static double[] spotPos(ServerLevel level, ArtStrike s) {
        if (s.type == ArtStrike.Type.CIRCLE) {
            double r = s.size * 0.25 * Math.sqrt(level.random.nextDouble());
            double a = level.random.nextDouble() * Math.PI * 2;
            return new double[]{s.ax + Math.cos(a) * r, s.az + Math.sin(a) * r};
        }
        double dx = s.bx - s.ax, dz = s.bz - s.az, len = Math.hypot(dx, dz);
        double ux = dx / len, uz = dz / len;
        double along = level.random.nextDouble() * len * 0.15;
        double across = (level.random.nextDouble() - 0.5) * s.size * 0.5;
        return new double[]{s.ax + ux * along - uz * across, s.az + uz * along + ux * across};
    }

    private static double[] salvoPos(ServerLevel level, ArtStrike s, int k, int n) {
        long sd = s.seed + 31L * s.salvoIndex();
        if (s.type == ArtStrike.Type.CIRCLE) {
            // Vogel spiral = even density over the area; the index is scrambled so that consecutive shells are far apart
            int idx = scramble(k, n, sd);
            double rr = s.size * Math.sqrt((idx + 0.5) / n);
            double th = idx * GOLDEN_ANGLE + hash01(sd) * Math.PI * 2;
            double j = s.size / Math.sqrt(n) * 0.35;
            double x = s.ax + Math.cos(th) * rr + (level.random.nextDouble() - 0.5) * 2 * j;
            double z = s.az + Math.sin(th) * rr + (level.random.nextDouble() - 0.5) * 2 * j;
            return ArtGeometry.clampToCircle(x, z, s.ax, s.az, s.size);
        }
        // rectangle: impacts walk from the start to the end, spread across the width
        double dx = s.bx - s.ax, dz = s.bz - s.az, len = Math.hypot(dx, dz);
        double ux = dx / len, uz = dz / len;
        double along = Mth.clamp((k + 0.5) / n * len + (level.random.nextDouble() - 0.5) * len / n * 0.6, 0, len);
        double frac = ((k + 0.5) * 0.7548776662466927 + hash01(sd)) % 1.0;
        double half = s.size / 2;
        double across = Mth.clamp((frac - 0.5) * s.size + (level.random.nextDouble() - 0.5) * s.size * 0.1, -half, half);
        return new double[]{s.ax + ux * along - uz * across, s.az + uz * along + ux * across};
    }

    private static int scramble(int k, int n, long seed) {
        if (n <= 1) return 0;
        int stride = Math.max(1, (int) (n * 0.6180339887)) | 1;
        while (gcd(stride, n) != 1) stride++;
        int offset = (int) Math.floorMod(seed >>> 8, (long) n);
        return (int) (((long) k * stride + offset) % n);
    }

    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    /** Deterministic 0..1 value from a seed. */
    private static double hash01(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    private static void spawnShell(ServerLevel level, double x, double z) {
        EntityType<?> type = shellType();
        if (type == null) return;
        ChunkPos cp = new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        // keep the target chunk ticking while the shell falls (same idea as the UAV flight tickets)
        level.getChunkSource().addRegionTicket(ART_TICKET, cp, 3, cp);
        level.getChunk(cp.x, cp.z);              // make sure the chunk exists before the entity is added (as UavManager does)

        Entity shell = type.create(level);
        if (shell == null) return;
        double y = Math.min(AASConfig.ART_SHELL_SPAWN_HEIGHT.get(), level.getMaxBuildHeight() - 1);
        shell.setPos(x, y, z);
        shell.setDeltaMovement(Vec3.ZERO);      // from here the SBW physics takes over
        level.addFreshEntity(shell);
    }

    // ------------------------------------------------------------------ match hooks
    public static void onMatchStart(ServerLevel level, AASWorldData d) {
        d.artRequests.clear();
        d.activeStrikes.clear();
        d.artCooldownEnd.clear();
        // requests are available at once (personal cooldowns were cleared above); the STRIKE of both teams starts on cooldown
        // (artInitialCooldownMinutes). Before the match nothing can be thrown or launched: both calls check isGameStarted.
        long end = level.getGameTime() + AASConfig.ART_INITIAL_COOLDOWN_MINUTES.get() * 1200L;
        d.blueArtStrikeCdEnd = end;
        d.redArtStrikeCdEnd = end;
        d.setDirty();
        syncAll(level);
    }

    /** Test command: every cooldown (all SL request cooldowns + both team strike cooldowns) back to zero. */
    public static void resetCooldowns(ServerLevel level) {
        AASWorldData d = AASWorldData.get(level);
        d.artCooldownEnd.clear();
        d.blueArtStrikeCdEnd = 0;
        d.redArtStrikeCdEnd = 0;
        d.setDirty();
        syncAll(level);
    }

    public static void onMatchEnd(ServerLevel level, AASWorldData d) {
        d.artRequests.clear();
        d.activeStrikes.clear();
        d.artCooldownEnd.clear();
        d.blueArtStrikeCdEnd = 0;
        d.redArtStrikeCdEnd = 0;
        d.setDirty();
        syncAll(level);
    }

    // ------------------------------------------------------------------ sync (filtered on the SERVER)
    public static void syncAll(ServerLevel level) {
        AASWorldData d = AASWorldData.get(level);
        for (ServerPlayer p : level.players()) send(p, d);
    }

    public static void syncPlayer(ServerPlayer p) {
        send(p, AASWorldData.get(p.serverLevel()));
    }

    /**
     * What a player may know:
     *  - PENDING request: its owner only.
     *  - CONFIRMED requests: the CMD and the SLs of the same team (observers / creative see all confirmed ones).
     *  - active strikes: the whole own team (observers / creative see all). The enemy gets nothing.
     */
    private static void send(ServerPlayer p, AASWorldData d) {
        String team = UavRules.teamOf(p);
        boolean teamed = UavRules.isTeam(team);
        boolean observer = p.isCreative() || p.isSpectator();
        boolean sl = teamed && ArtRules.isSquadLeader(p);
        boolean cmd = teamed && ArtRules.isCommander(p, d);

        List<ArtRequest> requests = new ArrayList<>();
        for (ArtRequest r : d.artRequests) {
            if (r.status == ArtRequest.PENDING) {
                if (r.ownerUuid.equals(p.getUUID())) requests.add(r);
            } else if (observer || (teamed && r.team.equals(team) && (cmd || sl))) {
                requests.add(r);
            }
        }
        List<ArtStrike> strikes = new ArrayList<>();
        for (ArtStrike s : d.activeStrikes) {
            if (observer || (teamed && s.team.equals(team))) strikes.add(s);
        }
        long cooldownEnd = sl ? ArtRules.requestCooldownEnd(d, p.getUUID()) : 0L;
        long strikeCooldownEnd = teamed ? ArtRules.strikeCooldownEnd(d, team) : 0L;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p),
                new PacketSyncArtillery(isAvailable(), cooldownEnd, strikeCooldownEnd, requests, strikes));
    }
}