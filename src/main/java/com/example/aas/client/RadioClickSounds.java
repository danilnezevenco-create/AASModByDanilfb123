package com.example.aas.client;

import com.example.aas.deck.DeckRegistry;
import com.example.aas.sound.ModSounds;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Radio click sounds of the held voice keys: "start" when the key goes down, "end" when it is released.
 * Squad channel (B): factions insurgency / militia use their own set, all other factions the normal squad set.
 * Command channel (*): one set for every faction. Local chat (V) is silent.
 * Only the speaking player hears the clicks (local UI sound, no world position).
 *
 * Channels are the same numbers as VoiceKeyHandler / VoiceChannelState: 0 none, 1 local, 2 squad, 3 command.
 */
public final class RadioClickSounds {
    private static final int SQUAD = 2, COMMAND = 3;

    private RadioClickSounds() {}

    /** Called by VoiceKeyHandler when the active channel changes (from -> to). */
    public static void onChannelChange(int from, int to) {
        if (from == to) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer pl = mc.player;
        if (pl == null) return;
        boolean ins = isInsurgentFaction(pl);

        if ((from == SQUAD || from == COMMAND) && allowed(pl, from)) play(mc, pick(from, false, ins));
        if ((to == SQUAD || to == COMMAND) && allowed(pl, to)) play(mc, pick(to, true, ins));
    }

    /** Click of ANOTHER player in my channel (sent by the server, who may hear it is decided there). */
    public static void playRemote(int channel, boolean start) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer pl = mc.player;
        if (pl == null) return;
        if (channel != SQUAD && channel != COMMAND) return;
        play(mc, pick(channel, start, isInsurgentFaction(pl)));
    }

    private static RegistryObject<SoundEvent> pick(int channel, boolean start, boolean ins) {
        if (channel == COMMAND) return start ? ModSounds.RADIO_COMMAND_START : ModSounds.RADIO_COMMAND_END;
        if (ins) return start ? ModSounds.RADIO_INSURGENT_START : ModSounds.RADIO_INSURGENT_END;
        return start ? ModSounds.RADIO_SQUAD_START : ModSounds.RADIO_SQUAD_END;
    }

    private static void play(Minecraft mc, RegistryObject<SoundEvent> ev) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ev.get(), 1.0F));
    }

    /** Same rule as the server: squad chat needs a squad, command chat needs a squad leader. */
    private static boolean allowed(LocalPlayer pl, int channel) {
        String me = pl.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            boolean leader = me.equals(s.leader);
            if (channel == COMMAND ? leader : (leader || s.members.contains(me))) return true;
        }
        return false;
    }

    private static boolean isInsurgentFaction(LocalPlayer pl) {
        Boolean blue = DeckClient.myTeamIsBlue(pl);
        if (blue == null) return false;
        String f = DeckRegistry.resolveFaction(DeckClient.factionOf(blue), blue);
        return "insurgency".equals(f) || "militia".equals(f);
    }
}
