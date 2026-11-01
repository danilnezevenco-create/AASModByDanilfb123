package com.example.aas.network;

import com.example.aas.config.AASConfig;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketTeamSelect {
    private final String teamName;

    public PacketTeamSelect(String teamName) {
        this.teamName = teamName;
    }

    public static void encode(PacketTeamSelect msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.teamName);
    }

    public static PacketTeamSelect decode(FriendlyByteBuf buf) {
        return new PacketTeamSelect(buf.readUtf());
    }

    public static void handle(PacketTeamSelect msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            AASWorldData data = AASWorldData.get(player.serverLevel());

            String blueName = getFactionDisplayName(data.blueFaction, true);
            String redName = getFactionDisplayName(data.redFaction, false);
            boolean wantBlue = msg.teamName.equalsIgnoreCase("BLUE");

            // the balance rule lives in ONE place (also used by the automatic assignment of the preparation)
            if (com.example.aas.deck.TeamBalance.blocksJoin(player.server, player, msg.teamName)) {
                if (wantBlue) {
                    player.sendSystemMessage(Component.translatable("aas.msg.unbalanced_teams", blueName, redName)
                            .withStyle(ChatFormatting.RED));
                } else {
                    player.sendSystemMessage(Component.literal("Teams are unbalanced! You cannot join " + redName + ". Please join " + blueName + ".")
                            .withStyle(ChatFormatting.RED));
                }
                return;
            }

            boolean teleport = data.isGameStarted || data.prepPhase != com.example.aas.deck.DeckPhase.NONE;
            com.example.aas.deck.TeamBalance.assign(player, wantBlue ? "BLUE" : "RED", data, teleport);

            player.sendSystemMessage(Component.translatable("aas.msg.joined_team", wantBlue ? blueName : redName)
                    .withStyle(wantBlue ? ChatFormatting.BLUE : ChatFormatting.RED));
            // during the preparation the clients close their team-select screen when they see the new team
            if (data.prepPhase != com.example.aas.deck.DeckPhase.NONE) PacketHandler.sendToAllClients(player.serverLevel(), data);
        });
        ctx.get().setPacketHandled(true);
    }

    private static String getFactionDisplayName(String faction, boolean isBlue) {
        if (faction == null || faction.equals("none") || faction.equals("bluefor") || faction.equals("redfor")) {
            return isBlue ? AASConfig.BLUE_TEAM_CUSTOM_NAME.get() : AASConfig.RED_TEAM_CUSTOM_NAME.get();
        }
        return faction.toUpperCase().replace("_", " ");
    }
}
