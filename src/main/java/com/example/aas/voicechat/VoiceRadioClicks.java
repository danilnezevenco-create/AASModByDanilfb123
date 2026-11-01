package com.example.aas.voicechat;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRadioClick;
import com.example.aas.world.AASWorldData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;

/**
 * Server side: when a player starts / stops talking in the squad or command channel, everybody who hears that channel
 * gets a "radio click" packet (the speaker plays his own click locally, so he is not included).
 * Recipients are the same as in the voice routing: squad = members of the speaker's squad,
 * command = leaders of the team's squads (only a squad leader may use the command channel).
 * No Simple Voice Chat classes here.
 */
public final class VoiceRadioClicks {
    private VoiceRadioClicks() {}

    /** Called with the channel before and after the change (VoiceChannelState constants). */
    public static void onChannelChange(ServerPlayer sender, int from, int to) {
        if (from == to) return;
        if (from == VoiceChannelState.SQUAD || from == VoiceChannelState.COMMAND) notifyListeners(sender, from, false);
        if (to == VoiceChannelState.SQUAD || to == VoiceChannelState.COMMAND) notifyListeners(sender, to, true);
    }

    private static void notifyListeners(ServerPlayer sender, int channel, boolean start) {
        MinecraftServer server = sender.getServer();
        if (server == null) return;
        String name = sender.getScoreboardName();
        AASWorldData data = AASWorldData.get(sender.serverLevel());
        PacketRadioClick pkt = new PacketRadioClick(channel, start);

        AASWorldData.Squad own = null;
        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(name)) { own = s; break; }
        }
        if (own == null) return;

        if (channel == VoiceChannelState.SQUAD) {
            for (String member : new ArrayList<>(own.members)) {
                if (member.equals(name)) continue;
                send(server, member, sender, pkt);
            }
        } else if (channel == VoiceChannelState.COMMAND) {
            if (!name.equals(own.leader)) return;   // only squad leaders use the command channel
            for (AASWorldData.Squad s : new ArrayList<>(data.squads)) {
                if (s.leader == null || s.leader.isEmpty() || s.leader.equals(name)) continue;
                send(server, s.leader, sender, pkt);
            }
        }
    }

    private static void send(MinecraftServer server, String playerName, ServerPlayer sender, PacketRadioClick pkt) {
        ServerPlayer p = server.getPlayerList().getPlayerByName(playerName);
        if (p == null || p == sender) return;
        if (sender.getTeam() == null || p.getTeam() == null
                || !sender.getTeam().getName().equals(p.getTeam().getName())) return;   // allies only
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }
}
