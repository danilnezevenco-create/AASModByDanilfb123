package com.example.aas.client.art;

import com.example.aas.art.ArtRequest;
import com.example.aas.art.ArtStrike;
import com.example.aas.client.ClientData;
import com.example.aas.client.uav.UavNoticeOverlay;
import com.example.aas.network.PacketSyncArtillery;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client copy of the artillery state. The server already filtered it for THIS player
 * (own PENDING request, confirmed requests for CMD/SL, strikes of the own team), so nothing is filtered here.
 */
public final class ArtClientState {
    private ArtClientState() {}

    public static boolean available = true;
    /** Own REQUEST cooldown (SL) and the team-wide STRIKE cooldown, game time. */
    public static long cooldownEnd;
    public static long strikeCooldownEnd;
    public static List<ArtRequest> requests = new ArrayList<>();
    public static List<ArtStrike> strikes = new ArrayList<>();
    /** The confirmed request the CMD works with (clicked circle). Client-side only. */
    public static UUID selectedRequest;

    public static void apply(PacketSyncArtillery m) {
        available = m.available;
        cooldownEnd = m.cooldownEnd;
        strikeCooldownEnd = m.strikeCooldownEnd;
        requests = new ArrayList<>(m.requests);
        strikes = new ArrayList<>(m.strikes);
        if (selectedRequest != null && confirmedRequest(selectedRequest) == null) selectedRequest = null;
    }

    public static void clear() {
        available = true;
        cooldownEnd = 0;
        strikeCooldownEnd = 0;
        requests = new ArrayList<>();
        strikes = new ArrayList<>();
        selectedRequest = null;
    }

    public static ArtRequest confirmedRequest(UUID id) {
        for (ArtRequest r : requests) if (r.status == ArtRequest.CONFIRMED && r.id.equals(id)) return r;
        return null;
    }

    public static boolean hasConfirmedRequests() {
        for (ArtRequest r : requests) if (r.status == ArtRequest.CONFIRMED) return true;
        return false;
    }

    /** The own (PENDING or CONFIRMED) request of this player, if any. */
    public static ArtRequest ownRequest(Minecraft mc) {
        if (mc.player == null) return null;
        for (ArtRequest r : requests) if (r.ownerUuid.equals(mc.player.getUUID())) return r;
        return null;
    }

    /** Squad leader (not a fireteam leader), from the synced squad list. */
    public static boolean isSquadLeader(Minecraft mc) {
        if (mc.player == null) return false;
        String name = mc.player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) if (s.leader.equals(name)) return true;
        return false;
    }

    public static long cooldownTicksLeft(Minecraft mc) {
        if (mc.level == null) return 0;
        return Math.max(0, cooldownEnd - mc.level.getGameTime());
    }

    public static long strikeCooldownTicksLeft(Minecraft mc) {
        if (mc.level == null) return 0;
        return Math.max(0, strikeCooldownEnd - mc.level.getGameTime());
    }

    public static String formatTicks(long ticks) {
        long sec = (ticks + 19) / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    /** null = a request can be thrown, otherwise the reason (shown in the ping menu). The server checks it again. */
    public static Component requestBlockReason(Minecraft mc) {
        if (!available) return Component.translatable("aas.art.reason.disabled");
        if (!ClientData.isGameStarted) return Component.translatable("aas.art.reason.not_started");
        if (ownRequest(mc) != null) return Component.translatable("aas.art.reason.has_request");
        long left = cooldownTicksLeft(mc);
        if (left > 0) return Component.translatable("aas.art.reason.cooldown", formatTicks(left));
        return null;
    }

    /** null = the CMD can start a strike, otherwise the reason (tooltip of the disabled panel buttons). */
    public static Component strikeBlockReason() {
        if (!available) return Component.translatable("aas.art.reason.disabled");
        if (!ClientData.isGameStarted) return Component.translatable("aas.art.reason.not_started");
        long left = strikeCooldownTicksLeft(Minecraft.getInstance());
        if (left > 0) return Component.translatable("aas.art.reason.strike_cooldown", formatTicks(left));
        if (!hasConfirmedRequests()) return Component.translatable("aas.art.reason.no_requests");
        return null;
    }

    public static void onConfirmedNotice(String ownerName) {
        UavNoticeOverlay.push(Component.translatable("aas.art.notice.confirmed", ownerName));
    }
}