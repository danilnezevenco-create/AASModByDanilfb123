package com.example.aas.art;

import com.example.aas.uav.UavRules;
import com.example.aas.world.AASWorldData;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Server-side permission and cooldown rules (two separate cooldowns: request = per SL, strike = per team) of the artillery. Mirrors UavRules, with ONE difference:
 * nothing here looks at isAlive / downed, so the CMD can call a strike and an SL can confirm or cancel
 * a request from the death screen.
 */
public final class ArtRules {
    private ArtRules() {}

    /** Squad leader (SL), not a fireteam leader. */
    public static boolean isSquadLeader(ServerPlayer p) {
        return UavRules.isTeam(UavRules.teamOf(p)) && p.getPersistentData().getBoolean("AAS_IsSquadLeader");
    }

    /** CMD = leader of the team's CMD squad. */
    public static boolean isCommander(ServerPlayer p, AASWorldData d) {
        return UavRules.isCommander(p, d);
    }

    /** Personal cooldown of one SL for throwing a REQUEST (starts at the confirmation). */
    public static long requestCooldownEnd(AASWorldData d, UUID sl) {
        return d.artCooldownEnd.getOrDefault(sl, 0L);
    }

    /** Team-wide cooldown of the STRIKE itself (starts when the CMD launches a strike). */
    public static long strikeCooldownEnd(AASWorldData d, String team) {
        return team.equals("BLUE") ? d.blueArtStrikeCdEnd : d.redArtStrikeCdEnd;
    }

    public static ArtRequest findRequest(AASWorldData d, UUID id) {
        for (ArtRequest r : d.artRequests) if (r.id.equals(id)) return r;
        return null;
    }

    public static ArtRequest requestOf(AASWorldData d, UUID owner) {
        for (ArtRequest r : d.artRequests) if (r.ownerUuid.equals(owner)) return r;
        return null;
    }
}