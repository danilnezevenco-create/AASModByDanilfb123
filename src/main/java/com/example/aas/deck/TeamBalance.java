package com.example.aas.deck;

import com.example.aas.config.AASConfig;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSquadAction;
import com.example.aas.network.PacketSyncMyKit;
import com.example.aas.network.PacketSyncSquads;
import com.example.aas.network.ResupplyHandler;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.PacketDistributor;

import java.util.Random;

/**
 * ONE server-side place for everything about team balancing and joining a team. Used by PacketTeamSelect
 * (the player clicked a flag) and by DeckManager (preparation: the server throws undecided players into a team).
 */
public final class TeamBalance {
    private TeamBalance() {}

    private static final Random RNG = new Random();

    /** {blue, red} head count (everybody with a team; {@code exclude} is not counted). */
    public static int[] counts(MinecraftServer server, ServerPlayer exclude) {
        int blue = 0, red = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == exclude || p.getTeam() == null) continue;
            String n = p.getTeam().getName();
            if (n.equalsIgnoreCase("Blue")) blue++;
            else if (n.equalsIgnoreCase("Red")) red++;
        }
        return new int[]{blue, red};
    }

    /**
     * The auto-balance rule: a player may not join the team that already has MORE players than the other.
     * Creative players and a disabled AUTO_BALANCE_TEAMS config are exempt (same as before the refactoring).
     */
    public static boolean blocksJoin(MinecraftServer server, ServerPlayer player, String requestedTeam) {
        if (!AASConfig.AUTO_BALANCE_TEAMS.get() || player.isCreative()) return false;
        int[] c = counts(server, player);
        if (requestedTeam.equalsIgnoreCase("BLUE")) return c[0] > c[1];
        return c[1] > c[0];
    }

    /** The team with fewer players; on a tie a random one ("BLUE" / "RED"). */
    public static String pickTeam(MinecraftServer server, ServerPlayer exclude) {
        int[] c = counts(server, exclude);
        if (c[0] < c[1]) return "BLUE";
        if (c[1] < c[0]) return "RED";
        return RNG.nextBoolean() ? "BLUE" : "RED";
    }

    /**
     * Puts the player into the team: leaves the squad, resets kit and inventory, joins the scoreboard team.
     * @param teleportToMain teleport to the team's main base afterwards (match running or preparation)
     */
    public static void assign(ServerPlayer player, String teamName, AASWorldData data, boolean teleportToMain) {
        boolean blue = teamName.equalsIgnoreCase("BLUE");

        PacketSquadAction.leaveCurrentSquad(player, data);

        player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncMyKit("Unassigned"));
        player.getPersistentData().remove("AAS_PendingKit");
        player.getInventory().clearContent();
        ResupplyHandler.clearCurios(player);
        player.inventoryMenu.broadcastChanges();

        data.setDirty();
        PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(player.level()::dimension), new PacketSyncSquads(data.squads));

        Scoreboard scoreboard = player.getServer().getScoreboard();
        String internalName = blue ? "Blue" : "Red";
        PlayerTeam team = scoreboard.getPlayerTeam(internalName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(internalName);
            team.setColor(blue ? ChatFormatting.BLUE : ChatFormatting.RED);
            team.setSeeFriendlyInvisibles(true);
        }
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);

        if (teleportToMain) {
            boolean ok = teleportToMain(player, data, blue ? "BLUE" : "RED");
            if (!ok) {
                player.sendSystemMessage(Component.literal("Warning: Main Base spawn point is not set for this team!")
                        .withStyle(ChatFormatting.RED));
            } else if (data.isGameStarted) {
                player.sendSystemMessage(Component.literal("Match in progress! Teleporting to Main Base...")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    /** Teleports to the main spawn ("mainSpawn", /aas teamspawn) of the team in the player's dimension. */
    public static boolean teleportToMain(ServerPlayer player, AASWorldData data, String team) {
        String dim = player.level().dimension().location().toString();
        BlockPos pos = team.equalsIgnoreCase("BLUE") ? data.blueSpawns.get(dim) : data.redSpawns.get(dim);
        if (pos == null) return false;
        player.stopRiding();
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return true;
    }

    /** The name shown to players for the team (faction name or the configured custom team name). */
    public static String displayName(AASWorldData data, boolean blue) {
        String faction = blue ? data.blueFaction : data.redFaction;
        if (faction == null || faction.equals("none") || faction.equals("bluefor") || faction.equals("redfor")) {
            return blue ? AASConfig.BLUE_TEAM_CUSTOM_NAME.get() : AASConfig.RED_TEAM_CUSTOM_NAME.get();
        }
        return faction.toUpperCase().replace("_", " ");
    }
}
